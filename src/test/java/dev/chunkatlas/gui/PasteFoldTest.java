package dev.chunkatlas.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class PasteFoldTest {
   private static final ToIntFunction<String> WIDE = s -> {
      int w = 0;
      for (char c : s.toCharArray()) {
         w += c == ' ' ? 4 : 6;
      }

      return w;
   };

   private static final int BAR_H = 2 * Glass.INSET + Glass.TILE;
   private static final int CARD_ROW = Glass.ICON + 4;

   private static PasteFold.Words words(String world, int chunks, int dx, int dz, int dy) {
      return new PasteFold.Words(chunks + " chunks from " + world, chunks + " chunks", "X " + dx, "Z " + dz, "Y " + dy, "Existing", "Replace", "Keep", "Blend",
         "Off", "Smart", "Cancel", "Place");
   }

   private static int scaled(int px, int scale) {
      return (px + scale - 1) / scale;
   }

   private static void fits(int screenW, int screenH, PasteFold.Words w, boolean server) {
      int room = screenW - 2 * Glass.MARGIN;
      int level = PasteFold.fit(room, w, server, WIDE);
      List<PasteFold.Part> bar = PasteFold.bar(level, w, server);
      int barW = PasteFold.width(bar, WIDE);
      assertTrue(barW <= room, "bar " + barW + " wide on " + screenW + " (level " + level + ")");
      List<List<PasteFold.Part>> card = PasteFold.card(level, w, server);
      assertEquals(PasteFold.folds(level), !card.isEmpty(), "the card holds what the bar folded");
      if (!card.isEmpty()) {
         assertTrue(bar.stream().anyMatch(p -> p.id().equals("options")), "a folded bar has the tile that opens the card");
         int inner = 0;
         for (List<PasteFold.Part> row : card) {
            inner = Math.max(inner, PasteFold.rowWidth(row, WIDE));
         }

         int cardW = inner + 2 * Glass.INSET + 4;
         int cardH = card.size() * CARD_ROW + 2 * Glass.INSET;
         assertTrue(cardW <= room, "card " + cardW + " wide on " + screenW);
         assertTrue(Glass.MARGIN + BAR_H + 3 + cardH <= screenH, "card " + cardH + " high above the bar on " + screenH);
      }

      assertTrue(bar.stream().anyMatch(p -> p.id().equals("cancel")));
      assertTrue(bar.stream().anyMatch(p -> p.id().equals("place")));
   }

   @Test
   void fitsTheSmallestWindowAtEveryScale() {
      PasteFold.Words plain = words("New World", 12, 3, -2, 0);
      PasteFold.Words long_ = words("A Survival World With A Very Long Name 2", 1234, -12345, 23456, -64);
      for (int scale : new int[]{2, 3, 4}) {
         int w = scaled(854, scale);
         int h = scaled(480, scale);
         for (boolean server : new boolean[]{false, true}) {
            fits(w, h, plain, server);
            fits(w, h, long_, server);
         }
      }
   }

   @Test
   void foldsStepByStepAsTheWindowNarrows() {
      PasteFold.Words w = words("New World", 12, 3, -2, 0);
      int full = PasteFold.width(PasteFold.bar(PasteFold.FULL, w, false), WIDE);
      assertEquals(PasteFold.FULL, PasteFold.fit(full, w, false, WIDE), "the whole row when it fits");
      assertTrue(PasteFold.fit(full - 1, w, false, WIDE) >= PasteFold.OPTIONS);

      int at2 = PasteFold.fit(scaled(854, 2) - 2 * Glass.MARGIN, w, false, WIDE);
      assertTrue(at2 >= PasteFold.OPTIONS && at2 < PasteFold.MOVE, "level " + at2);

      int last = Integer.MAX_VALUE;
      for (int level = PasteFold.FULL; level < PasteFold.LEVELS; level++) {
         int width = PasteFold.width(PasteFold.bar(level, w, false), WIDE);
         assertTrue(width < last, "level " + level + " is " + width);
         last = width;
      }
   }

   @Test
   void theCardHoldsWhatTheBarLeftOut() {
      PasteFold.Words w = words("New World", 12, 3, -2, 0);
      for (int level = PasteFold.FULL; level < PasteFold.LEVELS; level++) {
         List<PasteFold.Part> bar = PasteFold.bar(level, w, false);
         List<List<PasteFold.Part>> card = PasteFold.card(level, w, false);
         for (String id : new String[]{"x", "z", "y", "replace", "keep", "blend.0", "blend.2", "blend.4", "blend.8", "smart"}) {
            boolean onBar = bar.stream().anyMatch(p -> p.id().equals(id));
            boolean inCard = card.stream().anyMatch(row -> row.stream().anyMatch(p -> p.id().equals(id)));
            assertTrue(onBar != inCard, id + " at level " + level + " is on the bar or in the card, never both or neither");
         }
      }
   }

   @Test
   void noRulesAtTheEndsOrTwiceInARow() {
      PasteFold.Words w = words("New World", 12, 3, -2, 0);
      for (int level = PasteFold.FULL; level < PasteFold.LEVELS; level++) {
         for (boolean server : new boolean[]{false, true}) {
            List<PasteFold.Part> bar = PasteFold.bar(level, w, server);
            assertFalse(bar.get(0).kind() == PasteFold.Kind.RULE);
            assertFalse(bar.get(bar.size() - 1).kind() == PasteFold.Kind.RULE);
            for (int i = 1; i < bar.size(); i++) {
               assertFalse(bar.get(i).kind() == PasteFold.Kind.RULE && bar.get(i - 1).kind() == PasteFold.Kind.RULE);
            }
         }
      }
   }
}
