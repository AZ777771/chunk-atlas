package dev.chunkatlas.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MarkClustersTest {
   @Test
   void crowdedMarksMergeIntoTheNearest() {
      float[] xs = {10.0F, 11.0F, 10.0F, 30.0F};
      float[] ys = {10.0F, 10.0F, 11.5F, 10.0F};
      MarkClusters c = new MarkClusters();
      assertEquals(2, c.group(xs, ys, 4, 2.4F));
      assertEquals(0, c.lead(0));
      assertEquals(3, c.count(0));
      assertEquals(3, c.lead(1));
      assertEquals(1, c.count(1));
   }

   @Test
   void marksThatOnlyTouchStayApart() {
      float[] xs = {0.0F, 4.0F, 8.0F};
      float[] ys = {0.0F, 0.0F, 0.0F};
      MarkClusters c = new MarkClusters();
      assertEquals(3, c.group(xs, ys, 3, 2.4F));
      assertEquals(3, c.group(xs, ys, 3, 0.0F));
   }

   @Test
   void growsForManyMarks() {
      int n = 300;
      float[] xs = new float[n];
      float[] ys = new float[n];
      for (int i = 0; i < n; i++) {
         xs[i] = i * 10.0F;
      }

      assertEquals(n, new MarkClusters().group(xs, ys, n, 2.0F));
   }

   @Test
   void markSizeFollowsTheZoom() {
      assertEquals(MobMarks.SMALL, MobMarks.size(0.25));
      assertEquals(4, MobMarks.size(1.0));
      assertEquals(6, MobMarks.size(2.0));
      assertEquals(MobMarks.LARGE, MobMarks.size(4.0));
      assertEquals(MobMarks.LARGE, MobMarks.size(64.0));
   }
}
