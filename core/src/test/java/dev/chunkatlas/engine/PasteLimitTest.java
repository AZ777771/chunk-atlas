package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class PasteLimitTest {
   @TempDir
   Path dir;

   private static ChunkSelection square(int x0, int z0, int x1, int z1) {
      ChunkSelection s = new ChunkSelection();
      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            s.add(x, z);
         }
      }

      return s;
   }

   @Test
   void protectedChunksAreNeitherPlacedNorInTheRingOrBand() {
      ChunkSelection shown = square(0, 0, 2, 2);
      ChunkSelection guard = new ChunkSelection();
      guard.add(1, 1);
      guard.add(-1, 0);
      guard.add(5, 0);
      guard.add(40, 40);
      PasteLimit limit = PasteLimit.of(shown, 2, false, guard, (x, z) -> true, true);
      ChunkSelection placed = PasteLimit.placed(shown, false, guard, (x, z) -> true);
      assertEquals(8, placed.size());
      assertFalse(placed.contains(1, 1));
      assertFalse(limit.ring().contains(-1, 0));
      assertFalse(limit.band().contains(5, 0));

      assertFalse(limit.ring().contains(1, 1));

      assertTrue(limit.protects(1, 1) && limit.protects(-1, 0) && limit.protects(5, 0));
      assertFalse(limit.protects(40, 40));

      assertEquals(limit.ring().size(), limit.ring(placed, 2).size());
      limit.ring(placed, 2).forEach((x, z) -> assertTrue(limit.ring().contains(x, z)));
   }

   @Test
   void theServerNeverClearsMoreThanTheMapShowed() {
      ChunkSelection shown = square(0, 0, 2, 2);

      BlendRing.Saved map = (x, z) -> !(x == 4 && z == 4) && !(x == 0 && z == 0);
      PasteLimit limit = PasteLimit.of(shown, 2, true, new ChunkSelection(), map, true);
      assertFalse(limit.ring().contains(4, 4));

      ChunkSelection serverPlaced = square(0, 0, 2, 2);
      serverPlaced.remove(0, 0);
      ChunkSelection ring = limit.ring(serverPlaced, 2);
      assertFalse(ring.contains(4, 4), "unseen chunk left alone");
      assertFalse(ring.contains(0, 0), "a kept chunk isn't cleared");
      ring.forEach((x, z) -> assertTrue(limit.ring().contains(x, z)));
      limit.band(serverPlaced, 2).forEach((x, z) -> assertTrue(limit.band().contains(x, z)));
   }

   @Test
   void noBandWhenLeftOut() {
      PasteLimit limit = PasteLimit.of(square(0, 0, 1, 1), 3, false, null, (x, z) -> true, false);
      assertTrue(limit.band().isEmpty());
      assertFalse(limit.ring().isEmpty());
   }

   @Test
   void uploadRoundTrips() throws Exception {
      ChunkSelection guard = new ChunkSelection();
      guard.add(1, 1);
      PasteLimit limit = PasteLimit.of(square(0, 0, 2, 2), 2, false, guard, (x, z) -> true, true);
      byte[] archive = {1, 2, 3, 4, 5};
      PasteLimit.Upload up = PasteLimit.unwrap(PasteLimit.wrap(limit, archive));
      assertEquals(5, up.archive().length);
      assertEquals(limit.ring().size(), up.limit().ring().size());
      assertEquals(limit.band().size(), up.limit().band().size());
      assertTrue(up.limit().protects(1, 1));
      assertEquals(1, up.limit().protect().size());
   }

   private static byte[] chunk(int cx, int cz, String tag) {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("DataVersion", 4000);
      root.put("xPos", cx);
      root.put("zPos", cz);
      root.put("yPos", 0);
      root.put("Status", "minecraft:full");
      root.put("test", tag);
      List<Object> sections = new ArrayList<>();
      root.put("sections", sections);
      try {
         return ClipArchive.deflate(Nbt.write(root));
      } catch (java.io.IOException e) {
         throw new java.io.UncheckedIOException(e);
      }
   }

   private static Map<String, Object> read(Path regionFile, int cx, int cz) throws Exception {
      try (RegionFile f = RegionFile.open(regionFile)) {
         int i = ChunkSelection.index(cx, cz);
         if (!f.header().present(i)) {
            return null;
         }

         return Nbt.read(f.chunk(i).data());
      }
   }

   @Test
   void aServerPasteKeepsProtectedAndUnseenChunks() throws Exception {
      Path clip = this.dir.resolve("clip");
      Path world = this.dir.resolve("world");
      Map<Integer, byte[]> src = new HashMap<>();
      ChunkSelection picked = square(0, 0, 1, 1);
      picked.forEach((x, z) -> src.put(ChunkSelection.index(x, z), chunk(x, z, "pasted")));
      Files.createDirectories(clip.resolve("region"));
      RegionWriter.write(clip.resolve("region").resolve("r.0.0.mca"), src, 1);
      Map<Integer, byte[]> old = new HashMap<>();
      square(0, 0, 11, 11).forEach((x, z) -> old.put(ChunkSelection.index(x, z), chunk(x, z, "old")));
      Files.createDirectories(world.resolve("region"));
      Path region = world.resolve("region").resolve("r.0.0.mca");
      RegionWriter.write(region, old, 1);

      ChunkSelection shown = square(4, 4, 5, 5);
      ChunkSelection guard = new ChunkSelection();
      guard.add(5, 5);
      guard.add(3, 3);
      PasteLimit limit = PasteLimit.of(shown, 2, false, guard, (x, z) -> !(x == 6 && z == 6), true);
      ChunkEdits.Plan plan = ChunkCopy.plan(new ChunkCopy.Request(
         clip, "clip", ChunkClips.dimension(clip), picked, world, "world", ChunkClips.dimension(world), 4, 4, limit.protect(), 4000, 4000
      ).blended(2).limited(limit));
      assertNull(plan.copy().refused());
      assertEquals(3, plan.chunks());
      assertEquals(1, plan.copy().protectedSkipped());
      assertEquals(limit.ring().size(), plan.copy().blendRing());

      ChunkEdits.Result r = ChunkEdits.apply(plan, new ChunkEdits.Context(this.dir.resolve("home"), () -> true, () -> true), null, false);
      assertTrue(r.ok(), String.valueOf(r.message()));
      assertEquals("old", read(region, 5, 5).get("test"), "protected chunk in the paste kept");
      assertEquals("old", read(region, 3, 3).get("test"), "protected chunk in the ring kept");
      assertEquals("old", read(region, 6, 6).get("test"), "chunk the map didn't show kept");
      assertEquals("pasted", read(region, 4, 4).get("test"));
      for (int x = 0; x < 12; x++) {
         for (int z = 0; z < 12; z++) {
            if (limit.ring().contains(x, z)) {
               assertNull(read(region, x, z), "ring chunk " + x + ", " + z + " cleared");
            } else {
               assertNotNull(read(region, x, z), "chunk " + x + ", " + z + " kept");
            }
         }
      }
   }
}
