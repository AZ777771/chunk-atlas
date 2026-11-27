package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlendRingTest {
   private static ChunkSelection placed() {
      ChunkSelection s = new ChunkSelection();
      for (int x = 0; x < 3; x++) {
         for (int z = 0; z < 3; z++) {
            s.add(x, z);
         }
      }

      return s;
   }

   private static int distance(int x, int z) {
      return Math.max(Math.max(-x, x - 2), Math.max(-z, z - 2));
   }

   @Test
   void ringAndBandFollowTheDistanceToThePaste() {
      ChunkSelection placed = placed();
      ChunkSelection guard = new ChunkSelection();
      guard.add(-1, 1);
      guard.add(6, 1);

      BlendRing.Saved saved = (x, z) -> !(x == -3 && z == -3);
      ChunkSelection ring = BlendRing.ring(placed, 2, guard, saved);
      ChunkSelection band = BlendRing.band(placed, 2, guard, saved);
      for (int x = -8; x <= 10; x++) {
         for (int z = -8; z <= 10; z++) {
            int d = distance(x, z);
            boolean free = !guard.contains(x, z) && saved.has(x, z);
            assertEquals(d >= 1 && d <= 2 && free, ring.contains(x, z), "ring at " + x + ", " + z);
            assertEquals(d >= 3 && d <= 2 + BlendRing.BAND && free, band.contains(x, z), "band at " + x + ", " + z);
         }
      }

      assertEquals(49 - 9 - 1, ring.size());

      assertEquals(121 - 49 - 2, band.size());
      assertFalse(band.contains(1, 1));
      assertTrue(ring.contains(3, 3));
   }

   @Test
   void noBlendNoBand() {
      assertTrue(BlendRing.band(placed(), 0, null, null).isEmpty());
      assertTrue(BlendRing.ring(placed(), 0, null, null).isEmpty());
   }

   @Test
   void hollowShapesKeepTheirInsideRing() {
      ChunkSelection placed = new ChunkSelection();
      for (int x = 0; x < 5; x++) {
         for (int z = 0; z < 5; z++) {
            if (x != 2 || z != 2) {
               placed.add(x, z);
            }
         }
      }

      assertTrue(BlendRing.ring(placed, 4, null, null).contains(2, 2));
      assertFalse(BlendRing.band(placed, 4, null, null).contains(2, 2));
   }
}
