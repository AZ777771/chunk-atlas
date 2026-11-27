package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyChunkTest {
   private static final int STONE = 1;
   private static final int GRASS = 2;

   private static Map<String, Object> oldChunk() {
      byte[] blocks = new byte[4096];
      for (int y = 0; y <= 4; y++) {
         for (int i = 0; i < 256; i++) {
            blocks[y * 256 + i] = (byte)(y == 4 ? GRASS : STONE);
         }
      }

      Map<String, Object> section = new HashMap<>();
      section.put("Y", (byte)0);
      section.put("Blocks", blocks);
      section.put("Data", new byte[2048]);
      List<Object> sections = new ArrayList<>();
      sections.add(section);
      byte[] biomes = new byte[256];
      Arrays.fill(biomes, (byte)1);
      Map<String, Object> level = new HashMap<>();
      level.put("xPos", 3);
      level.put("zPos", -2);
      level.put("TerrainPopulated", (byte)1);
      level.put("Sections", sections);
      level.put("Biomes", biomes);
      Map<String, Object> root = new HashMap<>();
      root.put("DataVersion", 1343);
      root.put("Level", level);
      return root;
   }

   @Test
   void numericIdsHaveNames() {
      assertEquals("minecraft:stone", BlockColours.legacyName(STONE, 0));
      assertEquals("minecraft:granite", BlockColours.legacyName(STONE, 1));
      assertEquals("minecraft:grass_block", BlockColours.legacyName(GRASS, 0));
      assertEquals("minecraft:plains", RuleIds.legacyBiome(1));
   }

   @Test
   void factsComeFromTheLevelTag() {
      ChunkDecoder.Facts facts = ChunkDecoder.facts(oldChunk());
      assertEquals(3, facts.chunkX());
      assertEquals(-2, facts.chunkZ());
      assertEquals(1343, facts.dataVersion());
      assertTrue(facts.fullyGenerated());
      assertEquals(1, facts.sections());
   }

   @Test
   void oldChunkDraws() {
      Map<String, Object> root = oldChunk();
      ChunkDecoder plain = new ChunkDecoder(BlockColours.builtIn());
      assertTrue(plain.decode(root, ChunkDecoder.facts(root)));
      for (int i = 0; i < 256; i++) {
         assertNotEquals(0, plain.colour[i]);
         assertEquals(plain.colour[0], plain.colour[i]);
         assertEquals(plain.height[0], plain.height[i]);
      }
   }

   @Test
   void oldChunkDrawsDetailed() {
      Map<String, Object> root = oldChunk();
      ChunkDecoder detailed = new ChunkDecoder(BlockColours.builtIn());
      detailed.detailed = true;
      detailed.biomes = true;
      assertTrue(detailed.decode(root, ChunkDecoder.facts(root)));
      short plains = BiomeIndex.of("minecraft:plains");
      for (int i = 0; i < 256; i++) {
         assertNotEquals(0, detailed.top[i]);
         assertEquals(detailed.top[0], detailed.top[i]);
         assertEquals(plains, detailed.biome[i]);
      }
   }

   @Test
   void oldChunkIsMarkedTooOldForBlockRules() {
      assertTrue(ChunkContents.of(oldChunk()).tooOld());
   }
}
