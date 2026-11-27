package dev.chunkatlas.port;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;

public final class ChunkIo {
   private ChunkIo() {
   }

   public static CompletableFuture<Optional<CompoundTag>> read(ServerLevel level, int cx, int cz) {
      return level.getChunkSource().chunkMap.read(new ChunkPos(cx, cz));
   }

   public static void write(ServerLevel level, int cx, int cz, CompoundTag tag) {
      ChunkMap map = level.getChunkSource().chunkMap;
      ChunkPos pos = new ChunkPos(cx, cz);
      if (tag == null) {
         map.write(pos, IOWorker.STORE_EMPTY);
      } else {
         map.write(pos, tag);
      }
   }

   public static CompletableFuture<Optional<CompoundTag>> readEntities(ServerLevel level, int cx, int cz) {
      return entities(level).simpleRegionStorage.read(new ChunkPos(cx, cz));
   }

   public static void writeEntities(ServerLevel level, int cx, int cz, CompoundTag tag) {
      EntityStorage owner = entities(level);
      SimpleRegionStorage storage = owner.simpleRegionStorage;
      ChunkPos pos = new ChunkPos(cx, cz);

      if (tag == null) {
         owner.emptyChunks.add(pos.pack());
         storage.write(pos, IOWorker.STORE_EMPTY);
      } else {
         owner.emptyChunks.remove(pos.pack());
         storage.write(pos, tag);
      }
   }

   private static EntityStorage entities(ServerLevel level) {
      return (EntityStorage)level.entityManager.permanentStorage;
   }

   public static CompletableFuture<Optional<CompoundTag>> readPoi(ServerLevel level, int cx, int cz) {
      return level.getPoiManager().simpleRegionStorage.read(new ChunkPos(cx, cz));
   }

   public static CompletableFuture<Void> flush(ServerLevel level) {
      return level.getChunkSource().chunkMap.synchronize(false);
   }

   public static boolean savedFull(CompoundTag tag) {
      return SerializableChunkData.getChunkStatusFromTag(tag) == ChunkStatus.FULL;
   }
}
