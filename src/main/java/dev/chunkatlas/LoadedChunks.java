package dev.chunkatlas;

import dev.chunkatlas.engine.Background;
import dev.chunkatlas.engine.ChunkLoads;
import dev.chunkatlas.net.ServerLoads;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;

public final class LoadedChunks {
   private static final long EVERY = 500L;

   private static final long GIVE_UP = 5000L;
   private static final ExecutorService WORK = Executors.newSingleThreadExecutor(Background.named("Chunk Atlas loaded chunks"));
   private static volatile Got latest;
   private static volatile boolean asking;
   private static long askedAt;

   private record Got(WeakReference<Object> from, ChunkLoads.Snapshot loads) {
   }

   private LoadedChunks() {
   }

   public static ChunkLoads.Snapshot get(String dimension) {
      Minecraft mc = Minecraft.getInstance();
      Object from = source(mc);
      long now = System.currentTimeMillis();
      if (from != null && now - askedAt >= (asking ? GIVE_UP : EVERY)) {
         askedAt = now;
         asking = true;
         if (!ask(from, dimension)) {
            asking = false;
         }
      }

      Got got = latest;
      return got != null && from != null && got.from().get() == from && got.loads().dimension().equals(dimension) ? got.loads() : null;
   }

   private static Object source(Minecraft mc) {
      IntegratedServer server = mc.getSingleplayerServer();
      return server != null ? server : mc.getConnection();
   }

   private static boolean ask(Object from, String dimension) {
      if (!(from instanceof IntegratedServer server)) {
         return ServerLink.loads(dimension, s -> take(from, s));
      }

      server.execute(() -> {
         ServerLevel level = ServerLoads.level(server, dimension);
         ChunkLoads.Raw raw = level == null ? null : ServerLoads.capture(level);
         WORK.execute(() -> take(from, raw == null ? null : ChunkLoads.build(dimension, raw, Integer.MAX_VALUE)));
      });
      return true;
   }

   private static void take(Object from, ChunkLoads.Snapshot loads) {
      WORK.execute(() -> {
         if (loads != null) {
            loads.prepare();
         }

         latest = loads == null ? null : new Got(new WeakReference<>(from), loads);
         asking = false;
      });
   }
}
