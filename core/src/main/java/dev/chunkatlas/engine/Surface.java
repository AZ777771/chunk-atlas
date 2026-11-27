package dev.chunkatlas.engine;

import java.util.Arrays;

public final class Surface {
   public static final int LEAVES = 1 << 27;

   public static final int LAVA = 1 << 28;

   public static final int GLOW = 1 << 29;
   private static final int DRAWN = 1 << 31;

   public static final int ABOVE = 1 << 31;

   public final int side;

   public final int[] top;

   public final int[] cover;

   public final short[] height;

   public final byte[] depth;

   public final short[] biome;

   public final byte[] light;

   public Surface(int side) {
      this.side = side;
      int n = side * side;
      this.top = new int[n];
      this.cover = new int[n];
      this.height = new short[n];
      this.depth = new byte[n];
      this.biome = new short[n];
      this.light = new byte[n];
      Arrays.fill(this.biome, (short)-1);
   }

   public void clear() {
      Arrays.fill(this.top, 0);
      Arrays.fill(this.cover, 0);
      Arrays.fill(this.height, (short)0);
      Arrays.fill(this.depth, (byte)0);
      Arrays.fill(this.biome, (short)-1);
      Arrays.fill(this.light, (byte)0);
   }

   public boolean drawn(int i) {
      return this.top[i] != 0 || this.depth[i] != 0 || this.cover[i] != 0;
   }

   public static int top(long look) {
      if (look == 0L) {
         return 0;
      }

      int flags = DRAWN;
      if (Look.is(look, Look.LEAVES)) {
         flags |= LEAVES;
      }

      if (Look.is(look, Look.LAVA)) {
         flags |= LAVA | GLOW;
      } else if (Look.emission(look) >= 10) {
         flags |= GLOW;
      }

      return Look.rgb(look) | Look.tint(look) << 24 | flags;
   }

   public static int cover(long look, boolean above) {
      int fill = Look.fill(look);
      int strength = Look.is(look, Look.CLEAR) ? Math.max(3, Math.min(13, fill * 15 / 255 + 3)) : Math.max(2, Math.min(12, fill * 12 / 255));
      return Look.rgb(look) | Look.tint(look) << 24 | strength << 27 | (above ? ABOVE : 0);
   }

   public Surface copy() {
      Surface c = new Surface(this.side);
      System.arraycopy(this.top, 0, c.top, 0, this.top.length);
      System.arraycopy(this.cover, 0, c.cover, 0, this.cover.length);
      System.arraycopy(this.height, 0, c.height, 0, this.height.length);
      System.arraycopy(this.depth, 0, c.depth, 0, this.depth.length);
      System.arraycopy(this.biome, 0, c.biome, 0, this.biome.length);
      System.arraycopy(this.light, 0, c.light, 0, this.light.length);
      return c;
   }

   public void putChunk(int ox, int oz, int[] top, int[] cover, short[] height, byte[] depth, short[] biome, byte[] light) {
      for (int z = 0; z < 16; z++) {
         int row = (oz + z) * this.side + ox;
         System.arraycopy(top, z * 16, this.top, row, 16);
         System.arraycopy(cover, z * 16, this.cover, row, 16);
         System.arraycopy(height, z * 16, this.height, row, 16);
         System.arraycopy(depth, z * 16, this.depth, row, 16);
         System.arraycopy(biome, z * 16, this.biome, row, 16);
         System.arraycopy(light, z * 16, this.light, row, 16);
      }
   }
}
