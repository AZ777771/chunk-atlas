package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlendingMarkTest {
   private static Map<String, Object> section(int y, boolean blocks) {
      Map<String, Object> s = new HashMap<>();
      s.put("Y", (byte)y);
      if (blocks) {
         s.put("block_states", new HashMap<String, Object>());
      }

      return s;
   }

   @Test
   void markCoversTheSectionsWithBlocks() {
      Map<String, Object> root = new HashMap<>();
      List<Object> sections = new ArrayList<>();

      sections.add(section(-5, false));
      for (int y = -4; y < 20; y++) {
         sections.add(section(y, true));
      }

      sections.add(section(20, false));
      root.put("sections", sections);
      root.put("yPos", -4);
      ChunkFields.markForBlending(root);
      @SuppressWarnings("unchecked")
      Map<String, Object> blending = (Map<String, Object>)root.get("blending_data");
      assertEquals(-4, ((Number)blending.get("min_section")).intValue());
      assertEquals(20, ((Number)blending.get("max_section")).intValue());
   }

   @Test
   void oldLayoutIsLeftForTheGame() {
      Map<String, Object> root = new HashMap<>();
      root.put("Level", new HashMap<String, Object>());
      ChunkFields.markForBlending(root);
      assertFalse(root.containsKey("blending_data"));
   }
}
