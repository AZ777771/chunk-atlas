package dev.chunkatlas.gui;

import dev.chunkatlas.ChunkAtlas;
import dev.chunkatlas.engine.Background;
import dev.chunkatlas.engine.WorldMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class CacheSize {
   private static final long AGAIN = 5000L;
   private static final AtomicBoolean COUNTING = new AtomicBoolean();
   private static volatile long bytes = -1L;
   private static volatile long countedAt;

   private CacheSize() {
   }

   static void recount() {
      countedAt = 0L;
   }

   static long bytes() {
      long now = System.currentTimeMillis();
      if (now - countedAt > AGAIN && COUNTING.compareAndSet(false, true)) {
         countedAt = now;
         Thread t = Background.named("Chunk Atlas cache size").newThread(() -> {
            try {
               bytes = WorldMap.cacheBytes(ChunkAtlas.home());
            } finally {
               countedAt = System.currentTimeMillis();
               COUNTING.set(false);
            }
         });
         t.start();
      }

      return bytes;
   }
}
