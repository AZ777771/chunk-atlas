package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChunkLiftTest {
   private static final int LO = -4;
   private static final int SECTIONS = 4;
   private static final int MIN_Y = LO * 16;
   private static final int HEIGHT = SECTIONS * 16;

   private static String block(int x, int y, int z) {
      if (y == MIN_Y) {
         return "minecraft:bedrock";
      }

      return y > MIN_Y + 50 ? "minecraft:air" : "test:b" + ((y - MIN_Y) * 3 + x % 3);
   }

   private static String biome(int cellY) {
      return cellY < 6 ? "minecraft:deep_dark" : "minecraft:plains";
   }

   private static Map<String, Object> state(String name) {
      Map<String, Object> s = new LinkedHashMap<>();
      s.put("Name", name);
      return s;
   }

   private static long[] pack(int[] values, int bits) {
      int per = 64 / bits;
      long[] out = new long[(values.length + per - 1) / per];
      for (int i = 0; i < values.length; i++) {
         out[i / per] |= (long)values[i] << (i % per * bits);
      }

      return out;
   }

   private static Map<String, Object> container(List<String> names, int[] values, boolean blocks) {
      List<String> palette = new ArrayList<>();
      int[] index = new int[values.length];
      for (int i = 0; i < values.length; i++) {
         String n = names.get(values[i]);
         int at = palette.indexOf(n);
         if (at < 0) {
            at = palette.size();
            palette.add(n);
         }

         index[i] = at;
      }

      Map<String, Object> out = new LinkedHashMap<>();
      List<Object> p = new ArrayList<>();
      for (String n : palette) {
         p.add(blocks ? state(n) : n);
      }

      out.put("palette", p);
      if (palette.size() > 1) {
         out.put("data", pack(index, ChunkLift.bits(palette.size(), blocks)));
      }

      return out;
   }

   private static Map<String, Object> chunk() {
      List<String> names = new ArrayList<>();
      Map<String, Object> root = new LinkedHashMap<>();
      List<Object> sections = new ArrayList<>();
      Map<String, Object> below = new LinkedHashMap<>();
      below.put("Y", (byte)(LO - 1));
      below.put("SkyLight", new byte[2048]);
      sections.add(below);
      for (int s = 0; s < SECTIONS; s++) {
         int[] values = new int[4096];
         for (int i = 0; i < 4096; i++) {
            String b = block(i & 15, (LO + s) * 16 + (i >> 8), i >> 4 & 15);
            int at = names.indexOf(b);
            if (at < 0) {
               at = names.size();
               names.add(b);
            }

            values[i] = at;
         }

         List<String> biomeNames = List.of("minecraft:deep_dark", "minecraft:plains");
         int[] cells = new int[64];
         for (int c = 0; c < 64; c++) {
            cells[c] = biomeNames.indexOf(biome(s * 4 + (c >> 4)));
         }

         Map<String, Object> section = new LinkedHashMap<>();
         section.put("Y", (byte)(LO + s));
         section.put("block_states", container(names, values, true));
         section.put("biomes", container(biomeNames, cells, false));
         section.put("BlockLight", new byte[2048]);
         sections.add(section);
      }

      root.put("DataVersion", 4000);
      root.put("xPos", 3);
      root.put("zPos", -2);
      root.put("yPos", LO);
      root.put("Status", "minecraft:full");
      root.put("isLightOn", (byte)1);
      root.put("sections", sections);
      Map<String, Object> heightmaps = new LinkedHashMap<>();
      heightmaps.put("WORLD_SURFACE", new long[37]);
      root.put("Heightmaps", heightmaps);
      List<Object> entities = new ArrayList<>();
      entities.add(blockEntity(53, -50, -30));
      entities.add(blockEntity(53, MIN_Y + HEIGHT - 2, -30));
      root.put("block_entities", entities);
      List<Object> ticks = new ArrayList<>();
      Map<String, Object> tick = new LinkedHashMap<>();
      tick.put("i", "minecraft:water");
      tick.put("x", 50);
      tick.put("y", -40);
      tick.put("z", -20);
      ticks.add(tick);
      root.put("fluid_ticks", ticks);
      return root;
   }

   private static Map<String, Object> blockEntity(int x, int y, int z) {
      Map<String, Object> b = new LinkedHashMap<>();
      b.put("id", "minecraft:chest");
      b.put("x", x);
      b.put("y", y);
      b.put("z", z);
      return b;
   }

   private static String[] blocks(Map<String, Object> root) {
      String[] out = new String[HEIGHT * 256];
      List<Object> sections = Nbt.list(root, "sections");
      assertEquals(SECTIONS, sections.size());
      for (Object o : sections) {
         int s = (int)Nbt.number(o, "Y", 0) - LO;
         Map<String, Object> bs = Nbt.map(o, "block_states");
         List<Object> palette = Nbt.list(bs, "palette");
         long[] data = (long[])bs.get("data");
         int[] packing = data == null ? null : ChunkDecoder.packing(palette.size(), data.length, 4000);
         if (data != null) {
            assertNotNull(packing, "data length fits the palette");
            assertEquals(1, packing[1]);
            assertEquals(ChunkLift.bits(palette.size(), true), packing[0]);
         }

         for (int i = 0; i < 4096; i++) {
            int p = data == null ? 0 : ChunkDecoder.unpack(data, packing[0], true, i);
            out[s * 4096 + i] = Nbt.string(palette.get(p), "Name");
         }
      }

      return out;
   }

   private static String biomeAt(Map<String, Object> root, int cellY) {
      Object section = Nbt.list(root, "sections").get(cellY / 4);
      Map<String, Object> bm = Nbt.map(section, "biomes");
      List<Object> palette = Nbt.list(bm, "palette");
      long[] data = (long[])bm.get("data");
      int p = data == null ? 0 : ChunkDecoder.unpack(data, ChunkLift.bits(palette.size(), false), true, (cellY % 4) << 4);
      return (String)palette.get(p);
   }

   private static Map<String, Object> roundTrip(Map<String, Object> root) throws Exception {
      return Nbt.read(Nbt.write(root));
   }

   @Test
   void movesBlocksUpByLessThanASection() throws Exception {
      Map<String, Object> root = chunk();
      assertTrue(ChunkLift.lift(root, "region", 5, "minecraft:overworld"));
      root = roundTrip(root);
      String[] b = blocks(root);
      for (int i = 0; i < HEIGHT * 256; i++) {
         int y = MIN_Y + (i >> 8);
         int x = i & 15;
         int z = i >> 4 & 15;
         String want = y == MIN_Y ? "minecraft:bedrock" : y < MIN_Y + 5 ? block(x, MIN_Y + 1, z) : block(x, y - 5, z);
         assertEquals(want, b[i], "block at y " + y);
      }

      assertFalse(root.containsKey("Heightmaps"));
      assertEquals((byte)0, root.get("isLightOn"));
      for (Object s : Nbt.list(root, "sections")) {
         assertFalse(Nbt.has(s, "BlockLight"));
      }

      assertEquals("minecraft:deep_dark", biomeAt(root, 6));
      assertEquals("minecraft:plains", biomeAt(root, 8));
   }

   @Test
   void movesBlocksDownAndKeepsTheFloor() throws Exception {
      Map<String, Object> root = chunk();
      assertTrue(ChunkLift.lift(root, "region", -21, "minecraft:overworld"));
      String[] b = blocks(roundTrip(root));
      for (int i = 0; i < HEIGHT * 256; i++) {
         int y = MIN_Y + (i >> 8);
         int x = i & 15;
         int z = i >> 4 & 15;
         String want = y == MIN_Y ? "minecraft:bedrock" : y + 21 >= MIN_Y + HEIGHT ? "minecraft:air" : block(x, y + 21, z);
         assertEquals(want, b[i], "block at y " + y);
      }
   }

   @Test
   void movesBlockEntitiesAndTicks() {
      Map<String, Object> root = chunk();
      ChunkLift.lift(root, "region", 5, "minecraft:overworld");
      List<Object> kept = Nbt.list(root, "block_entities");

      assertEquals(1, kept.size());
      assertEquals(-45, ((Map<?, ?>)kept.get(0)).get("y"));
      assertEquals(53, ((Map<?, ?>)kept.get(0)).get("x"));
      assertEquals(-35, ((Map<?, ?>)Nbt.list(root, "fluid_ticks").get(0)).get("y"));

      assertTrue(Nbt.map(Nbt.map(root, "structures"), "starts").isEmpty());
   }

   @Test
   void movesMobsAndVillagerPlaces() {
      Map<String, Object> entities = new LinkedHashMap<>();
      entities.put("Position", new int[]{3, -2});
      List<Object> list = new ArrayList<>();
      Map<String, Object> mob = new LinkedHashMap<>();
      mob.put("id", "minecraft:villager");
      mob.put("Pos", new ArrayList<Object>(List.of(52.5, 70.0, -27.5)));
      mob.put("sleeping_pos", new int[]{50, 69, -28});
      list.add(mob);
      entities.put("Entities", list);
      ChunkLift.lift(entities, "entities", 12, "minecraft:overworld");
      Map<?, ?> moved = (Map<?, ?>)Nbt.list(entities, "Entities").get(0);
      assertEquals(List.of(52.5, 82.0, -27.5), moved.get("Pos"));
      assertArrayEquals(new int[]{50, 81, -28}, (int[])moved.get("sleeping_pos"));
      assertArrayEquals(new int[]{3, -2}, (int[])entities.get("Position"));

      Map<String, Object> poi = new LinkedHashMap<>();
      Map<String, Object> sections = new LinkedHashMap<>();
      Map<String, Object> section = new LinkedHashMap<>();
      Map<String, Object> bed = new LinkedHashMap<>();
      bed.put("type", "minecraft:home");
      bed.put("pos", new int[]{50, 60, -28});
      bed.put("free_tickets", 0);
      section.put("Valid", (byte)1);
      section.put("Records", new ArrayList<Object>(List.of(bed)));
      sections.put("3", section);
      poi.put("Sections", sections);
      ChunkLift.lift(poi, "poi", 12, "minecraft:overworld");
      Map<String, Object> after = Nbt.map(poi, "Sections");

      assertFalse(after.containsKey("3"));
      int[] pos = (int[])((Map<?, ?>)Nbt.list(after.get("4"), "Records").get(0)).get("pos");
      assertArrayEquals(new int[]{50, 72, -28}, pos);
   }

   @Test
   void leavesOldChunksAlone() {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("Level", new LinkedHashMap<String, Object>());
      assertFalse(ChunkLift.lift(root, "region", 8, "minecraft:overworld"));
      assertTrue(Nbt.map(root, "Level").isEmpty());
   }
}
