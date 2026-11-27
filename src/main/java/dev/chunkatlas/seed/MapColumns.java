package dev.chunkatlas.seed;

import dev.chunkatlas.engine.TileRenderer;

public final class MapColumns {
   public final int side;
   public final int[] colour;
   public final short[] height;
   public final byte[] water;

   public MapColumns(int side) {
      this.side = side;
      this.colour = new int[side * side];
      this.height = new short[side * side];
      this.water = new byte[side * side];
   }

   void putChunk(int ox, int oz, int[] colour, short[] height, byte[] water) {
      for (int z = 0; z < 16; z++) {
         int row = (oz + z) * this.side + ox;
         System.arraycopy(colour, z * 16, this.colour, row, 16);
         System.arraycopy(height, z * 16, this.height, row, 16);
         System.arraycopy(water, z * 16, this.water, row, 16);
      }
   }

   MapColumns copy() {
      MapColumns c = new MapColumns(this.side);
      System.arraycopy(this.colour, 0, c.colour, 0, this.colour.length);
      System.arraycopy(this.height, 0, c.height, 0, this.height.length);
      System.arraycopy(this.water, 0, c.water, 0, this.water.length);
      return c;
   }

   int[] shade(int apron) {
      return crop(TileRenderer.shadeVanilla(this.colour, this.height, this.water, this.side, false), this.side, apron);
   }

   static int[] crop(int[] all, int side, int apron) {
      int n = side - 2 * apron;
      int[] out = new int[n * n];
      for (int z = 0; z < n; z++) {
         System.arraycopy(all, (z + apron) * side + apron, out, z * n, n);
      }

      return out;
   }
}
