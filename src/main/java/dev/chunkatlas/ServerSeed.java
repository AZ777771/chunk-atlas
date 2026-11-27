package dev.chunkatlas;

import dev.chunkatlas.engine.SeedMapSettings;
import dev.chunkatlas.minimap.Config;
import java.nio.file.Path;

public final class ServerSeed {
   private ServerSeed() {
   }

   public static Long get() {
      Path folder = LiveMap.folder();
      Long typed = folder == null ? null : SeedMapSettings.load(ChunkAtlas.home(), folder).seed();
      if (typed != null) {
         return typed;
      }

      long old = Config.get().slimeSeed;
      return old == 0L ? null : old;
   }

   public static void set(Long seed) {
      Path folder = LiveMap.folder();
      if (folder == null) {
         return;
      }

      SeedMapSettings s = SeedMapSettings.load(ChunkAtlas.home(), folder);
      s.seed(seed);
      s.save();
   }
}
