package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateEstimateTest {
   private static final long S = 1_000_000_000L;

   @Test
   void aSlowStartDoesNotStretchTheFirstEstimate() {
      RateEstimate e = new RateEstimate();
      long done = 0;

      for (long t = 0; t <= 3 * S; t += S / 2) {
         e.sample(t, done);
      }

      done = 1;
      e.sample(3 * S + S / 2, done);
      for (long t = 4 * S; t <= 12 * S; t += S / 2) {
         done += 50;
         e.sample(t, done);
      }

      int left = e.secondsLeft(12 * S, 100_000);

      assertTrue(left > 800 && left < 1250, "left " + left);
   }

   @Test
   void followsAChangeOfPaceWithinSeconds() {
      RateEstimate e = new RateEstimate();
      long done = 0;
      long t = 0;
      for (; t <= 10 * S; t += S / 2) {
         e.sample(t, done);
         done += 10;
      }

      for (long end = t + 10 * S; t <= end; t += S / 2) {
         e.sample(t, done);
         done += 50;
      }

      assertEquals(100.0, e.rate(), 10.0);
   }

   @Test
   void nothingKnownBeforeAnythingIsDone() {
      RateEstimate e = new RateEstimate();
      e.sample(0, 0);
      e.sample(5 * S, 0);
      assertEquals(-1, e.secondsLeft(5 * S, 1000));
   }

   @Test
   void aPauseCountsForNothing() {
      RateEstimate e = new RateEstimate();
      long done = 0;
      for (long t = 0; t <= 6 * S; t += S / 2) {
         e.sample(t, done);
         done += 50;
      }

      double before = e.rate();
      e.resume(60 * S, done);
      e.sample(60 * S + S / 2, done + 50);
      assertEquals(before, e.rate(), 1.0);
   }
}
