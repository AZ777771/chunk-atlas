package dev.chunkatlas.engine;

public final class Look {
   public static final int TINT_NONE = 0;
   public static final int TINT_GRASS = 1;
   public static final int TINT_FOLIAGE = 2;
   public static final int TINT_WATER = 3;
   public static final int TINT_DRY_FOLIAGE = 4;

   public static final long WATER = 1L << 35;

   public static final long LAVA = 1L << 36;

   public static final long CLEAR = 1L << 37;

   public static final long COVER = 1L << 38;

   public static final long LEAVES = 1L << 39;
   private static final long DRAWN = 1L << 47;

   private Look() {
   }

   public static long of(int rgb, int fill, int tint, long kinds, int emission) {
      return DRAWN | rgb & 0xFFFFFFL | (long)(Math.max(0, Math.min(255, fill)) << 24) & 0xFF000000L | (long)(tint & 7) << 32 | kinds
         | (long)(emission & 15) << 40;
   }

   public static int rgb(long look) {
      return (int)look & 0xFFFFFF;
   }

   public static int fill(long look) {
      return (int)(look >>> 24) & 0xFF;
   }

   public static int tint(long look) {
      return (int)(look >>> 32) & 7;
   }

   public static int emission(long look) {
      return (int)(look >>> 40) & 15;
   }

   public static boolean is(long look, long kind) {
      return (look & kind) != 0L;
   }

   public static long fromColour(String name, int argb) {
      if (argb >>> 24 == 0) {
         return 0L;
      }

      String path = name.substring(name.indexOf(':') + 1);
      long kinds = 0L;
      int tint = TINT_NONE;
      if (path.equals("water") || path.equals("flowing_water") || path.equals("bubble_column")) {
         return of(0xB0B0B0, 255, TINT_WATER, WATER, 0);
      }

      if (path.equals("lava") || path.equals("flowing_lava")) {
         return of(argb, 255, TINT_NONE, LAVA, 15);
      }

      if (path.endsWith("leaves")) {
         kinds |= LEAVES;
      }

      if (path.endsWith("glass") || path.endsWith("glass_pane") || path.equals("ice") || path.equals("frosted_ice")) {
         kinds |= CLEAR;
      }

      if (path.endsWith("seagrass") || path.startsWith("kelp") || path.equals("short_grass") || path.equals("tall_grass") || path.endsWith("fern")
         || path.endsWith("torch") || path.endsWith("rail") || path.equals("redstone_wire")) {
         kinds |= COVER;
         if (path.endsWith("seagrass") || path.startsWith("kelp")) {
            kinds |= WATER;
         }
      }

      return of(argb & 0xFFFFFF, (kinds & (COVER | CLEAR)) != 0L ? 128 : 255, tint, kinds, 0);
   }
}
