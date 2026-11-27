package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PasteLiftBlendTest {
   @TempDir
   Path dir;

   private static byte[] chunk(int cx, int cz, String tag) throws Exception {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("DataVersion", 4000);
      root.put("xPos", cx);
      root.put("zPos", cz);
      root.put("yPos", 0);
      root.put("Status", "minecraft:full");
      root.put("test", tag);
      List<Object> sections = new ArrayList<>();
      for (int y = 0; y < 2; y++) {
         Map<String, Object> s = new LinkedHashMap<>();
         s.put("Y", (byte)y);
         Map<String, Object> states = new LinkedHashMap<>();
         List<Object> palette = new ArrayList<>();
         palette.add(state("minecraft:air"));
         if (y == 0) {
            palette.add(state("minecraft:stone"));
            long[] data = new long[256];
            for (int i = 0; i < 4096; i++) {
               if (i >> 8 < 10) {
                  data[i / 16] |= 1L << (i % 16 * 4);
               }
            }

            states.put("data", data);
         }

         states.put("palette", palette);
         s.put("block_states", states);
         Map<String, Object> biomes = new LinkedHashMap<>();
         biomes.put("palette", new ArrayList<Object>(List.of("minecraft:plains")));
         s.put("biomes", biomes);
         sections.add(s);
      }

      root.put("sections", sections);
      return ClipArchive.deflate(Nbt.write(root));
   }

   private static Map<String, Object> state(String name) {
      Map<String, Object> s = new LinkedHashMap<>();
      s.put("Name", name);
      return s;
   }

   private static Map<String, Object> read(Path regionFile, int cx, int cz) throws Exception {
      try (RegionFile f = RegionFile.open(regionFile)) {
         int i = ChunkSelection.index(cx, cz);
         if (!f.header().present(i)) {
            return null;
         }

         RegionFile.Chunk c = f.chunk(i);
         assertTrue(c.ok());
         return Nbt.read(c.data());
      }
   }

   @Test
   void liftedPasteClearsTheRingAndMarksTheBand() throws Exception {
      Path clip = this.dir.resolve("clip");
      Path world = this.dir.resolve("world");
      Map<Integer, byte[]> src = new HashMap<>();
      ChunkSelection picked = new ChunkSelection();
      for (int x = 0; x < 2; x++) {
         for (int z = 0; z < 2; z++) {
            src.put(ChunkSelection.index(x, z), chunk(x, z, "pasted"));
            picked.add(x, z);
         }
      }

      Files.createDirectories(clip.resolve("region"));
      RegionWriter.write(clip.resolve("region").resolve("r.0.0.mca"), src, 1);
      Map<Integer, byte[]> old = new HashMap<>();
      for (int x = 0; x < 12; x++) {
         for (int z = 0; z < 12; z++) {
            old.put(ChunkSelection.index(x, z), chunk(x, z, "old"));
         }
      }

      Files.createDirectories(world.resolve("region"));
      Path region = world.resolve("region").resolve("r.0.0.mca");
      RegionWriter.write(region, old, 1);

      ChunkEdits.Plan plan = ChunkCopy.plan(new ChunkCopy.Request(
         clip, "clip", ChunkClips.dimension(clip), picked, world, "world", ChunkClips.dimension(world), 4, 4, new ChunkSelection(), 4000, 4000
      ).blended(2).lifted(3));
      assertTrue(plan.copy().refused() == null, String.valueOf(plan.copy().refused()));
      assertEquals(4, plan.chunks());

      assertEquals(32, plan.copy().blendRing());
      assertEquals(64, plan.copy().blendBand());
      assertEquals(3, plan.copy().dy());

      ChunkEdits.Result r = ChunkEdits.apply(plan, new ChunkEdits.Context(this.dir.resolve("home"), () -> true, () -> true), null, false);
      assertTrue(r.ok(), String.valueOf(r.message()));

      Map<String, Object> pasted = read(region, 4, 4);
      assertEquals("pasted", pasted.get("test"));
      assertEquals(4, pasted.get("xPos"));
      assertNotNull(pasted.get("blending_data"));

      Map<String, Object> bottom = (Map<String, Object>)Nbt.list(pasted, "sections").get(0);
      List<Object> palette = Nbt.list(Nbt.map(bottom, "block_states"), "palette");
      long[] data = (long[])Nbt.map(bottom, "block_states").get("data");
      int bits = ChunkLift.bits(palette.size(), true);
      assertEquals("minecraft:stone", Nbt.string(palette.get(ChunkDecoder.unpack(data, bits, true, 12 << 8)), "Name"));
      assertEquals("minecraft:air", Nbt.string(palette.get(ChunkDecoder.unpack(data, bits, true, 13 << 8)), "Name"));

      for (int x = 0; x < 12; x++) {
         for (int z = 0; z < 12; z++) {
            int d = Math.max(Math.max(4 - x, x - 5), Math.max(4 - z, z - 5));
            Map<String, Object> c = read(region, x, z);
            if (d <= 0) {
               continue;
            } else if (d <= 2) {
               assertEquals(null, c, "ring chunk " + x + ", " + z + " cleared");
            } else if (d <= 4) {
               assertEquals("old", c.get("test"));
               assertNotNull(c.get("blending_data"), "band chunk " + x + ", " + z + " marked");
            } else {
               assertEquals("old", c.get("test"));
               assertFalse(c.containsKey("blending_data"), "chunk " + x + ", " + z + " left alone");
            }
         }
      }
   }
}
