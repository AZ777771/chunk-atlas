package dev.chunkatlas.port;

import dev.chunkatlas.engine.ChunkLoads;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class Tickets {
   private Tickets() {
   }

   public interface ChunkSink {
      void chunk(int cx, int cz, int load, int sim, int status);
   }

   public interface TicketSink {
      void ticket(int cx, int cz, int level, int kind);
   }

   public static int maxLevel() {
      return ChunkLevel.MAX_LEVEL;
   }

   public static void chunks(ServerLevel level, int most, ChunkSink sink) {
      ChunkMap map = level.getChunkSource().chunkMap;
      DistanceManager distances = map.getDistanceManager();
      int k = 0;
      for (ChunkHolder h : map.visibleChunkMap.values()) {
         int lvl = h.getTicketLevel();
         if (k == most) {
            break;
         } else if (lvl > ChunkLevel.MAX_LEVEL) {
            continue;
         }

         ChunkPos pos = h.getPos();
         ChunkStatus s = h.getPersistedStatus();
         sink.chunk(pos.x, pos.z, lvl, distances.tickingTracker().getLevel(pos), s == null ? 0 : s.getIndex());
         k++;
      }
   }

   public static void tickets(ServerLevel level, TicketSink sink) {
      DistanceManager distances = level.getChunkSource().chunkMap.getDistanceManager();
      for (Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> e : distances.tickets.long2ObjectEntrySet()) {
         for (Ticket<?> ticket : e.getValue()) {
            sink.ticket(ChunkPos.getX(e.getLongKey()), ChunkPos.getZ(e.getLongKey()), ticket.getTicketLevel(), kind(ticket.getType()));
         }
      }
   }

   private static int kind(TicketType<?> type) {
      if (type == TicketType.PLAYER) {
         return ChunkLoads.PLAYER;
      } else if (type == TicketType.PORTAL) {
         return ChunkLoads.PORTAL;
      } else {
         return type == TicketType.FORCED ? ChunkLoads.FORCED : ChunkLoads.OTHER;
      }
   }

   public static boolean force(ServerLevel level, int cx, int cz, boolean on) {
      ForcedChunksSavedData data = level.getDataStorage().computeIfAbsent(ForcedChunksSavedData.factory(), "chunks");
      ChunkPos pos = new ChunkPos(cx, cz);
      boolean changed = on ? data.getChunks().add(pos.toLong()) : data.getChunks().remove(pos.toLong());
      data.setDirty(changed);
      if (changed) {
         level.getChunkSource().updateChunkForced(pos, on);
      }

      return changed;
   }

   public static long[] forced(ServerLevel level) {
      return level.getForcedChunks().toLongArray();
   }

   public static final class Own {
      private final TicketType<ChunkPos> type = TicketType.create("chunkatlas_generate", Comparator.comparingLong(ChunkPos::toLong));
   }

   public static Own own() {
      return new Own();
   }

   public static CompletableFuture<Boolean> load(ServerLevel level, Own ticket, long pos) {
      ServerChunkCache source = level.getChunkSource();
      ChunkPos at = new ChunkPos(pos);
      source.addRegionTicket(ticket.type, at, 0, at);

      source.runDistanceManagerUpdates();
      ChunkHolder holder = source.chunkMap.getVisibleChunkIfPresent(pos);
      if (holder == null) {
         return CompletableFuture.completedFuture(false);
      }

      return holder.getFullChunkFuture().thenApply(r -> r.isSuccess());
   }

   public static void release(ServerLevel level, Own ticket, long pos) {
      ChunkPos at = new ChunkPos(pos);
      level.getChunkSource().removeRegionTicket(ticket.type, at, 0, at);
   }

   public static boolean full(ServerLevel level, long pos) {
      ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(pos);
      return holder != null && holder.getLatestStatus() == ChunkStatus.FULL;
   }

   public static boolean busy(ServerLevel level, int cx, int cz) {
      long key = ChunkPos.asLong(cx, cz);
      ChunkMap map = level.getChunkSource().chunkMap;
      return map.getUpdatingChunkIfPresent(key) != null || map.pendingUnloads.containsKey(key) || level.entityManager.areEntitiesLoaded(key);
   }

   public static void unloadNow(ServerLevel level, BooleanSupplier haveTime) {
      level.getChunkSource().chunkMap.tick(haveTime);
      level.entityManager.tick();
   }
}
