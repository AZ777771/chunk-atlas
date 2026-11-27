package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkFilterTest {
   @Test
   void encodeAndDecodeKeepRulesAndGroups() {
      ChunkFilter f = new ChunkFilter(List.of(
         new ChunkFilter.Condition(ChunkFilter.Field.TIME_PLAYED, ChunkFilter.Op.AT_MOST, 5L, null, 0),
         new ChunkFilter.Condition(ChunkFilter.Field.BLOCK, ChunkFilter.Op.AT_LEAST, 8L, "minecraft:diamond_ore", 1),
         new ChunkFilter.Condition(ChunkFilter.Field.BIOME, ChunkFilter.Op.HAS, 0L, "minecraft:plains", 1)
      ), true);
      ChunkFilter back = ChunkFilter.decode(f.encode());
      assertEquals(f.encode(), back.encode());
      assertEquals(3, back.conditions().size());
      assertEquals(1, back.conditions().get(1).group());
      assertEquals("minecraft:diamond_ore", back.conditions().get(1).id());
      assertEquals(ChunkFilter.Op.AT_MOST, back.conditions().get(0).op());
   }

   @Test
   void oldStringsStillRead() {
      ChunkFilter f = ChunkFilter.decode("f1;all;TIME_PLAYED:AT_MOST:5");
      assertEquals(1, f.conditions().size());
      assertEquals(ChunkFilter.Field.TIME_PLAYED, f.conditions().get(0).field());
   }
}
