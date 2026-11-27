package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MovedStructuresTest {
   private static final int DX = 7;
   private static final int DZ = -3;

   @AfterEach
   void back() {
      ChunkCopy.keepStructures(true);
   }

   private static long pack(int x, int z) {
      return x & 0xFFFFFFFFL | ((long)z & 0xFFFFFFFFL) << 32;
   }

   private static Map<String, Object> piece(String id, int[] bb) {
      Map<String, Object> p = new LinkedHashMap<>();
      p.put("id", id);
      p.put("BB", bb);
      p.put("O", 2);
      return p;
   }

   private static void fortress(Map<String, Object> c, String structuresKey, String startsKey) {
      Map<String, Object> start = new LinkedHashMap<>();
      start.put("id", "minecraft:fortress");
      start.put("ChunkX", 10);
      start.put("ChunkZ", -20);
      start.put("references", 3);
      List<Object> children = new ArrayList<>();
      children.add(piece("minecraft:nebs", new int[]{160, 64, -320, 178, 73, -302}));
      children.add(piece("minecraft:nesc", new int[]{179, 64, -320, 185, 72, -310}));
      start.put("Children", children);
      Map<String, Object> starts = new LinkedHashMap<>();
      starts.put("minecraft:fortress", start);
      Map<String, Object> refs = new LinkedHashMap<>();
      refs.put("minecraft:fortress", new long[]{pack(10, -20), pack(11, -20)});
      Map<String, Object> structures = new LinkedHashMap<>();
      structures.put("References", refs);
      structures.put(startsKey, starts);
      c.put(structuresKey, structures);
   }

   private static Map<String, Object> chunk() {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("xPos", 10);
      root.put("zPos", -20);
      root.put("Status", "minecraft:full");
      fortress(root, "structures", "starts");
      return root;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> start(Map<String, Object> c, String structuresKey, String startsKey) {
      return (Map<String, Object>)Nbt.map(Nbt.map(c, structuresKey), startsKey).get("minecraft:fortress");
   }

   @Test
   void aMovedFortressStartMovesItsBoxesPiecesAndReferences() {
      Map<String, Object> root = chunk();
      ChunkCopy.move(root, "region", DX, DZ, "minecraft:the_nether");
      assertEquals(10 + DX, root.get("xPos"));
      Map<String, Object> start = start(root, "structures", "starts");
      assertEquals(10 + DX, start.get("ChunkX"));
      assertEquals(-20 + DZ, start.get("ChunkZ"));
      assertEquals(3, start.get("references"));
      List<Object> children = Nbt.list(start, "Children");
      assertArrayEquals(new int[]{160 + DX * 16, 64, -320 + DZ * 16, 178 + DX * 16, 73, -302 + DZ * 16}, (int[])((Map<?, ?>)children.get(0)).get("BB"));
      assertArrayEquals(new int[]{179 + DX * 16, 64, -320 + DZ * 16, 185 + DX * 16, 72, -310 + DZ * 16}, (int[])((Map<?, ?>)children.get(1)).get("BB"));
      long[] refs = (long[])Nbt.map(Nbt.map(root, "structures"), "References").get("minecraft:fortress");
      assertArrayEquals(new long[]{pack(10 + DX, -20 + DZ), pack(11 + DX, -20 + DZ)}, refs);
   }

   @Test
   void jigsawAndTemplatePiecesMoveTheirOwnPositionsAndJunctions() {
      Map<String, Object> root = chunk();
      Map<String, Object> start = start(root, "structures", "starts");
      Map<String, Object> jigsaw = piece("minecraft:jigsaw", new int[]{0, 60, 0, 15, 70, 15});
      jigsaw.put("pos_x", 3);
      jigsaw.put("pos_y", 61);
      jigsaw.put("pos_z", 4);
      Map<String, Object> junction = new LinkedHashMap<>();
      junction.put("source_x", 5);
      junction.put("source_ground_y", 62);
      junction.put("source_z", 6);
      List<Object> junctions = new ArrayList<>();
      junctions.add(junction);
      jigsaw.put("junctions", junctions);
      Map<String, Object> template = piece("minecraft:iglu", new int[]{20, 70, 20, 30, 80, 30});
      template.put("TPX", 20);
      template.put("TPY", 70);
      template.put("TPZ", 20);
      Nbt.list(start, "Children").add(jigsaw);
      Nbt.list(start, "Children").add(template);
      ChunkCopy.move(root, "region", DX, DZ, "minecraft:overworld");
      assertEquals(3 + DX * 16, jigsaw.get("pos_x"));
      assertEquals(61, jigsaw.get("pos_y"));
      assertEquals(4 + DZ * 16, jigsaw.get("pos_z"));
      assertEquals(5 + DX * 16, junction.get("source_x"));
      assertEquals(62, junction.get("source_ground_y"));
      assertEquals(6 + DZ * 16, junction.get("source_z"));
      assertEquals(20 + DX * 16, template.get("TPX"));
      assertEquals(70, template.get("TPY"));
      assertEquals(20 + DZ * 16, template.get("TPZ"));
   }

   @Test
   void aMonumentKeepsItsFinishedChunksInStep() {
      Map<String, Object> root = chunk();
      Map<String, Object> start = start(root, "structures", "starts");
      Map<String, Object> done = new LinkedHashMap<>();
      done.put("X", 10);
      done.put("Z", -20);
      List<Object> processed = new ArrayList<>();
      processed.add(done);
      start.put("Processed", processed);
      ChunkCopy.move(root, "region", DX, DZ, "minecraft:overworld");
      assertEquals(10 + DX, done.get("X"));
      assertEquals(-20 + DZ, done.get("Z"));
   }

   @Test
   void theOldLevelLayoutMovesTheSameWay() {
      Map<String, Object> level = new LinkedHashMap<>();
      level.put("xPos", 10);
      level.put("zPos", -20);
      fortress(level, "Structures", "Starts");
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("Level", level);
      ChunkCopy.move(root, "region", DX, DZ, "minecraft:the_nether");
      Map<String, Object> start = start(level, "Structures", "Starts");
      assertEquals(10 + DX, start.get("ChunkX"));
      assertArrayEquals(new int[]{160 + DX * 16, 64, -320 + DZ * 16, 178 + DX * 16, 73, -302 + DZ * 16}, (int[])((Map<?, ?>)Nbt.list(start, "Children").get(0)).get("BB"));
      long[] refs = (long[])Nbt.map(Nbt.map(level, "Structures"), "References").get("minecraft:fortress");
      assertEquals(pack(11 + DX, -20 + DZ), refs[1]);
   }

   @Test
   void turnedOffTheStructuresAreRemovedAsBefore() {
      ChunkCopy.keepStructures(false);
      Map<String, Object> root = chunk();
      ChunkCopy.move(root, "region", DX, DZ, "minecraft:the_nether");
      assertTrue(Nbt.map(Nbt.map(root, "structures"), "starts").isEmpty());
      assertTrue(Nbt.map(Nbt.map(root, "structures"), "References").isEmpty());
   }

   @Test
   void aReferenceWhoseStartStayedBehindStillMovesWithItsChunk() {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("xPos", 11);
      root.put("zPos", -20);
      Map<String, Object> refs = new LinkedHashMap<>();
      refs.put("minecraft:fortress", new long[]{pack(10, -20)});
      Map<String, Object> structures = new LinkedHashMap<>();
      structures.put("References", refs);
      structures.put("starts", new LinkedHashMap<String, Object>());
      root.put("structures", structures);
      ChunkCopy.move(root, "region", DX, DZ, "minecraft:the_nether");
      assertArrayEquals(new long[]{pack(10 + DX, -20 + DZ)}, (long[])refs.get("minecraft:fortress"));
      assertTrue(Nbt.map(structures, "starts").isEmpty());
   }
}
