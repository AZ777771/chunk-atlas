package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ProtectionCountTest {
   @Test
   void unprotectedMatchesTheCopy() {
      ChunkSelection sel = new ChunkSelection();
      ChunkSelection guard = new ChunkSelection();
      for (int x = -40; x < 40; x += 3) {
         for (int z = -40; z < 40; z += 5) {
            sel.add(x, z);
            if ((x + z) % 2 == 0) {
               guard.add(x, z);
            }
         }
      }

      guard.add(500, 500);
      assertEquals(Protection.without(sel, guard).size(), Protection.unprotected(sel, guard));
      assertEquals(sel.size(), Protection.unprotected(sel, new ChunkSelection()));
      assertEquals(0, Protection.unprotected(new ChunkSelection(), guard));
   }

   @Test
   void changesCountEveryChange() {
      ChunkSelection sel = new ChunkSelection();
      int c0 = sel.changes();
      sel.add(1, 1);
      int c1 = sel.changes();
      assertNotEquals(c0, c1);
      sel.add(1, 1);
      assertEquals(c1, sel.changes());
      sel.remove(1, 1);
      assertNotEquals(c1, sel.changes());
      int c2 = sel.changes();
      sel.add(0, 0, new long[16]);
      assertNotEquals(c2, sel.changes());
      int c3 = sel.changes();
      sel.clear();
      assertNotEquals(c3, sel.changes());
   }
}
