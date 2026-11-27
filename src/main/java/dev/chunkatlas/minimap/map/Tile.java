package dev.chunkatlas.minimap.map;

public final class Tile {
   public static final int SURFACE = Integer.MIN_VALUE;

   public final int cx;
   public final int cz;

   final int[] base = new int[256];
   final short[] height = new short[256];

   final byte[] depth = new byte[256];

   final byte[] spawn = new byte[256];

   final int[] top = new int[256];
   final int[] cover = new int[256];
   final short[] topHeight = new short[256];
   final byte[] topDepth = new byte[256];
   final short[] biome = new short[256];
   final byte[] topLight = new byte[256];

   public final int[] pixels = new int[256];
   int layer = SURFACE;
   long scanned;

   Tile(int cx, int cz) {
      this.cx = cx;
      this.cz = cz;
   }

   public byte spawn(int i) {
      return this.spawn[i];
   }

   public boolean seen(int i) {
      return this.base[i] != 0;
   }

   public int height(int i) {
      return this.height[i];
   }

   public static long key(int cx, int cz) {
      return (long)cx << 32 | cz & 0xFFFFFFFFL;
   }
}
