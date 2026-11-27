package dev.chunkatlas.gui;

final class MarkClusters {
   private int[] lead = new int[16];
   private int[] count = new int[16];
   private int groups;

   int group(float[] xs, float[] ys, int n, float radius) {
      if (this.lead.length < n) {
         this.lead = new int[n];
         this.count = new int[n];
      }

      this.groups = 0;
      float r2 = radius * radius;
      for (int i = 0; i < n; i++) {
         int joined = -1;
         if (radius > 0.0F) {
            for (int c = 0; c < this.groups; c++) {
               float dx = xs[i] - xs[this.lead[c]];
               float dy = ys[i] - ys[this.lead[c]];
               if (dx * dx + dy * dy < r2) {
                  joined = c;
                  break;
               }
            }
         }

         if (joined < 0) {
            this.lead[this.groups] = i;
            this.count[this.groups] = 1;
            this.groups++;
         } else {
            this.count[joined]++;
         }
      }

      return this.groups;
   }

   int lead(int c) {
      return this.lead[c];
   }

   int count(int c) {
      return this.count[c];
   }
}
