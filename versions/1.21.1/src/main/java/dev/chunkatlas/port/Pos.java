package dev.chunkatlas.port;

import net.minecraft.world.level.ChunkPos;

public final class Pos {
   private Pos() {
   }

   public static int cx(ChunkPos pos) {
      return pos.x;
   }

   public static int cz(ChunkPos pos) {
      return pos.z;
   }

   public static long pack(ChunkPos pos) {
      return pos.toLong();
   }

   public static long pack(int cx, int cz) {
      return ChunkPos.asLong(cx, cz);
   }

   public static ChunkPos unpack(long packed) {
      return new ChunkPos(packed);
   }
}
