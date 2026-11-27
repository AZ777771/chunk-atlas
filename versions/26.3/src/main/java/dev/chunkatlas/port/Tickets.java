package dev.chunkatlas.port;

import dev.chunkatlas.engine.ChunkLoads;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
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
         sink.chunk(pos.x(), pos.z(), lvl, distances.getChunkLevel(pos.pack(), true), s == null ? 0 : s.getIndex());
         k++;
      }
   }

   public static void tickets(ServerLevel level, TicketSink sink) {
      TicketStorage storage = level.getDataStorage().get(TicketStorage.TYPE);
      if (storage == null) {
         return;
      }

      for (Long2ObjectMap.Entry<List<Ticket>> e : storage.tickets.long2ObjectEntrySet()) {
         for (Ticket ticket : e.getValue()) {
            if (ticket.getType().doesLoad()) {
               sink.ticket(ChunkPos.getX(e.getLongKey()), ChunkPos.getZ(e.getLongKey()), ticket.getTicketLevel(), kind(ticket.getType()));
            }
         }
      }
   }

   private static int kind(TicketType type) {
      if (type == TicketType.PLAYER_LOADING) {
         return ChunkLoads.PLAYER;
      } else if (type == TicketType.PORTAL) {
         return ChunkLoads.PORTAL;
      } else if (type == TicketType.ENDER_PEARL) {
         return ChunkLoads.PEARL;
      } else {
         return type == TicketType.FORCED ? ChunkLoads.FORCED : ChunkLoads.OTHER;
      }
   }

   public static boolean force(ServerLevel level, int cx, int cz, boolean on) {
      return level.getChunkSource().updateChunkForced(new ChunkPos(cx, cz), on);
   }

   public static long[] forced(ServerLevel level) {
      return level.getForceLoadedChunks().toLongArray();
   }

   public static final class Own {
      private final TicketType type = new TicketType(0L, TicketType.FLAG_LOADING);
   }

   public static Own own() {
      return new Own();
   }

   public static CompletableFuture<Boolean> load(ServerLevel level, Own ticket, long pos) {
      return level.getChunkSource().addTicketAndLoadWithRadius(ticket.type, ChunkPos.unpack(pos), 0)
         .thenApply(r -> r instanceof ChunkResult<?> c && c.isSuccess());
   }

   public static void release(ServerLevel level, Own ticket, long pos) {
      level.getChunkSource().removeTicketWithRadius(ticket.type, ChunkPos.unpack(pos), 0);
   }

   public static boolean full(ServerLevel level, long pos) {
      return level.getChunkSource().chunkMap.getLatestStatus(pos) == ChunkStatus.FULL;
   }

   public static boolean busy(ServerLevel level, int cx, int cz) {
      long key = ChunkPos.pack(cx, cz);
      ChunkMap map = level.getChunkSource().chunkMap;
      return map.getUpdatingChunkIfPresent(key) != null || map.pendingUnloads.containsKey(key) || level.entityManager.areEntitiesLoaded(key);
   }

   public static void unloadNow(ServerLevel level, BooleanSupplier haveTime) {
      level.getChunkSource().chunkMap.tick(haveTime);
      level.entityManager.tick();
   }
}
