package dev.chunkatlas.gui;

import dev.chunkatlas.ChunkAtlas;
import dev.chunkatlas.engine.Background;
import dev.chunkatlas.engine.ChunkEdits;
import dev.chunkatlas.engine.WorldMap;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class DiskFacts {
   private static final long AGAIN = 4000L;
   private static final AtomicBoolean COUNTING = new AtomicBoolean();
   private static final AtomicBoolean DELETING = new AtomicBoolean();
   private static volatile List<ChunkEdits.Backup> backups;
   private static volatile long countedAt;
   private static volatile Path worldCounted;
   private static volatile long worldBytes = -1L;

   private DiskFacts() {
   }

   static List<ChunkEdits.Backup> backups() {
      long now = System.currentTimeMillis();
      if (now - countedAt > AGAIN && COUNTING.compareAndSet(false, true)) {
         countedAt = now;
         Path home = ChunkAtlas.home();
         Thread t = Background.named("Chunk Atlas backup sizes").newThread(() -> {
            try {
               backups = ChunkEdits.listBackups(home);
            } finally {
               countedAt = System.currentTimeMillis();
               COUNTING.set(false);
            }
         });
         t.start();
      }

      return backups;
   }

   static long cacheAll() {
      return CacheSize.bytes();
   }

   static long cacheWorld(Path world) {
      if (world == null) {
         return -1L;
      }

      long now = System.currentTimeMillis();
      if (!world.equals(worldCounted)) {
         worldCounted = world;
         worldBytes = -1L;
         worldAt = 0L;
      }

      if (now - worldAt > AGAIN && WORLD_COUNTING.compareAndSet(false, true)) {
         worldAt = now;
         Path home = ChunkAtlas.home();
         Thread t = Background.named("Chunk Atlas cache size").newThread(() -> {
            try {
               worldBytes = WorldMap.cacheBytes(home, world);
            } finally {
               worldAt = System.currentTimeMillis();
               WORLD_COUNTING.set(false);
            }
         });
         t.start();
      }

      return worldBytes;
   }

   private static final AtomicBoolean WORLD_COUNTING = new AtomicBoolean();
   private static volatile long worldAt;

   static void recount() {
      countedAt = 0L;
      worldAt = 0L;
      CacheSize.recount();
   }

   static boolean busy() {
      return DELETING.get();
   }

   static void deleteBackups(List<ChunkEdits.Backup> which, Runnable done) {
      if (!DELETING.compareAndSet(false, true)) {
         return;
      }

      Path home = ChunkAtlas.home();
      List<ChunkEdits.Backup> copy = List.copyOf(which);
      Thread t = Background.named("Chunk Atlas delete backups").newThread(() -> {
         try {
            for (ChunkEdits.Backup b : copy) {
               ChunkEdits.deleteBackup(home, b.worldKey(), b.id());
            }
         } finally {
            recount();
            DELETING.set(false);
            if (done != null) {
               done.run();
            }
         }
      });
      t.start();
   }

   static void clearCache(Path world, Path open, Runnable emptyOpen, Runnable done) {
      if (!DELETING.compareAndSet(false, true)) {
         return;
      }

      Path home = ChunkAtlas.home();
      Thread t = Background.named("Chunk Atlas clear cache").newThread(() -> {
         try {
            boolean opened = open != null && (world == null || world.equals(open));
            WorldMap.clearCache(home, world, opened ? open : null);
            if (opened && emptyOpen != null) {
               emptyOpen.run();
            }
         } finally {
            recount();
            DELETING.set(false);
            if (done != null) {
               done.run();
            }
         }
      });
      t.start();
   }
}
