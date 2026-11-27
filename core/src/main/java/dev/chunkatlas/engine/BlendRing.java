package dev.chunkatlas.engine;

public final class BlendRing {
   public static final int BAND = 2;

   private BlendRing() {
   }

   public interface Saved {
      boolean has(int chunkX, int chunkZ);
   }

   public static ChunkSelection ring(ChunkSelection placed, int blend, ChunkSelection protectedChunks, BlendRing.Saved saved) {
      return around(placed, 1, Math.min(blend, ChunkCopy.MAX_BLEND), protectedChunks, saved);
   }

   public static ChunkSelection band(ChunkSelection placed, int blend, ChunkSelection protectedChunks, BlendRing.Saved saved) {
      int b = Math.min(blend, ChunkCopy.MAX_BLEND);
      return b <= 0 ? new ChunkSelection() : around(placed, b + 1, b + BAND, protectedChunks, saved);
   }

   static ChunkSelection around(ChunkSelection placed, int from, int to, ChunkSelection protectedChunks, BlendRing.Saved saved) {
      ChunkSelection out = new ChunkSelection();
      if (to < from || to <= 0) {
         return out;
      }

      ChunkSelection near = from <= 1 ? placed : square(placed, from - 1);
      ChunkSelection far = square(placed, to);
      far.forEach((x, z) -> {
         if (!near.contains(x, z) && !placed.contains(x, z) && (protectedChunks == null || !protectedChunks.contains(x, z)) && (saved == null || saved.has(x, z))) {
            out.add(x, z);
         }
      });
      return out;
   }

   private static ChunkSelection square(ChunkSelection sel, int r) {
      ChunkSelection out = new ChunkSelection();
      sel.forEach((cx, cz) -> {
         if (sel.contains(cx - 1, cz) && sel.contains(cx + 1, cz) && sel.contains(cx, cz - 1) && sel.contains(cx, cz + 1)
            && sel.contains(cx - 1, cz - 1) && sel.contains(cx + 1, cz - 1) && sel.contains(cx - 1, cz + 1) && sel.contains(cx + 1, cz + 1)) {
            out.add(cx, cz);
            return;
         }

         for (int ox = -r; ox <= r; ox++) {
            for (int oz = -r; oz <= r; oz++) {
               out.add(cx + ox, cz + oz);
            }
         }
      });
      return out;
   }
}
