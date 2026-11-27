package dev.chunkatlas.engine;

public final class Tile {
   public static final int LEVELS = 5;
   public static final int SIZE = 512;
   public final String dimension;
   public final int rx;
   public final int rz;
   public final int level;
   public final int size;
   public final int[] argb;
   public final RegionFacts facts;
   public final long regionSize;
   public final long regionModified;

   public int cave = Integer.MIN_VALUE;

   public int style;

   public boolean stale;

   Tile(String dimension, int rx, int rz, int level, int[] argb, RegionFacts facts, long regionSize, long regionModified) {
      this.dimension = dimension;
      this.rx = rx;
      this.rz = rz;
      this.level = level;
      this.size = 512 >> level;
      this.argb = argb;
      this.facts = facts;
      this.regionSize = regionSize;
      this.regionModified = regionModified;
   }

   static int[] half(int[] src, int size) {
      int h = size / 2;
      int[] out = new int[h * h];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < h; x++) {
            int r = 0;
            int g = 0;
            int b = 0;
            int n = 0;

            for (int k = 0; k < 4; k++) {
               int c = src[(y * 2 + (k >> 1)) * size + x * 2 + (k & 1)];
               if (c >>> 24 != 0) {
                  r += c >> 16 & 0xFF;
                  g += c >> 8 & 0xFF;
                  b += c & 0xFF;
                  n++;
               }
            }

            out[y * h + x] = n == 0 ? 0 : 0xFF000000 | r / n << 16 | g / n << 8 | b / n;
         }
      }

      return out;
   }
}
