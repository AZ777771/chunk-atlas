package dev.chunkatlas.server;

import dev.chunkatlas.port.ChunkIo;
import dev.chunkatlas.port.Tickets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

final class LiveEdits {
   private LiveEdits() {
   }

   static boolean available() {
      return true;
   }

   static boolean busy(ServerLevel level, int cx, int cz) {
      try {
         return Tickets.busy(level, cx, cz);
      } catch (RuntimeException e) {
         return true;
      }
   }

   static CompletableFuture<Optional<CompoundTag>> readChunk(ServerLevel level, int cx, int cz) {
      return ChunkIo.read(level, cx, cz);
   }

   static CompletableFuture<Optional<CompoundTag>> readEntities(ServerLevel level, int cx, int cz) {
      try {
         return ChunkIo.readEntities(level, cx, cz);
      } catch (RuntimeException e) {
         return CompletableFuture.completedFuture(Optional.empty());
      }
   }

   static CompletableFuture<Optional<CompoundTag>> readPoi(ServerLevel level, int cx, int cz) {
      try {
         return ChunkIo.readPoi(level, cx, cz);
      } catch (RuntimeException e) {
         return CompletableFuture.completedFuture(Optional.empty());
      }
   }

   static void writeChunk(ServerLevel level, int cx, int cz, CompoundTag tag) {
      ChunkIo.write(level, cx, cz, tag);
   }

   static void writeEntities(ServerLevel level, int cx, int cz, CompoundTag tag) {
      ChunkIo.writeEntities(level, cx, cz, tag);
   }
}
