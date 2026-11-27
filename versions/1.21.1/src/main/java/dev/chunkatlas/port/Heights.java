package dev.chunkatlas.port;

import net.minecraft.world.level.LevelHeightAccessor;

public final class Heights {
   private Heights() {
   }

   public static int min(LevelHeightAccessor level) {
      return level.getMinBuildHeight();
   }

   public static int maxInclusive(LevelHeightAccessor level) {
      return level.getMaxBuildHeight() - 1;
   }
}
