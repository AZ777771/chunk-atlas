package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdTextTest {
   @Test
   void splitsLikeTheGame() {
      assertEquals("minecraft", IdText.namespace("minecraft:plains"));
      assertEquals("plains", IdText.path("minecraft:plains"));
      assertEquals("minecraft", IdText.namespace("plains"));
      assertEquals("plains", IdText.path("plains"));
      assertEquals("minecraft", IdText.namespace(":plains"));
      assertEquals("terralith", IdText.namespace("terralith:cave/deep"));
      assertEquals("cave/deep", IdText.path("terralith:cave/deep"));
   }

   @Test
   void refusesWhatTheGameRefuses() {
      assertFalse(IdText.valid("Minecraft:plains"));
      assertFalse(IdText.valid("a/b:c"));
      assertFalse(IdText.valid("a:b:c"));
      assertFalse(IdText.valid(null));
      assertNull(IdText.path("x y"));
      assertTrue(IdText.valid("minecraft:textures/entity/cow.png"));
   }
}
