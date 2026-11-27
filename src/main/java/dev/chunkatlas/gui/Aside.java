package dev.chunkatlas.gui;

import dev.chunkatlas.engine.ChunkClips;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.EditSnapshots;
import dev.chunkatlas.engine.Message;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.engine.WorldLock;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

final class Aside {
   private final Path home;
   private final Path sourceWorld;
   private final WorldLayout.Dimension source;
   private final ChunkSelection chunks;

   private final Path lock;
   private volatile Path folder;

   private Aside(Path home, Path sourceWorld, WorldLayout.Dimension source, ChunkSelection chunks, Path lock, Path folder) {
      this.home = home;
      this.sourceWorld = sourceWorld;
      this.source = source;
      this.chunks = chunks;
      this.lock = lock;
      this.folder = folder;
   }

   static Aside of(Path home, Path sourceWorld, WorldLayout.Dimension source, ChunkSelection chunks, boolean isWorld) {
      return new Aside(home, sourceWorld, source, chunks.copy(), isWorld ? sourceWorld : null, null);
   }

   static Aside own(Path home, Path folder) {
      return new Aside(home, folder, ChunkClips.dimension(folder), new ChunkSelection(), null, folder);
   }

   Path world() {
      Path f = this.folder;
      return f != null ? f : this.sourceWorld;
   }

   WorldLayout.Dimension dimension() {
      Path f = this.folder;
      return f != null ? ChunkClips.dimension(f) : this.source;
   }

   Message keep() {
      if (this.folder != null) {
         return null;
      }

      if (this.lock == null) {
         try {
            this.folder = EditSnapshots.take(this.home, this.source, this.chunks);
            return null;
         } catch (IOException | RuntimeException e) {
            return Message.of("chunkatlas.pending.aside_failed");
         }
      }

      boolean[] failed = new boolean[1];
      Optional<Path> taken = WorldLock.with(this.lock, held -> {
         try {
            return EditSnapshots.take(this.home, this.source, this.chunks);
         } catch (IOException | RuntimeException e) {
            failed[0] = true;
            return null;
         }
      });
      if (taken.isPresent()) {
         this.folder = taken.get();
         return null;
      }

      return Message.of(failed[0] ? "chunkatlas.pending.aside_failed" : "chunkatlas.copy.source_open");
   }

   void drop() {
      Path f = this.folder;
      this.folder = null;

      EditSnapshots.drop(this.home, f);
   }
}
