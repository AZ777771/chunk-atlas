package dev.chunkatlas.port;

import net.minecraft.SharedConstants;

public final class Version {
   private Version() {
   }

   public static int dataVersion() {
      return SharedConstants.getCurrentVersion().dataVersion().version();
   }

   public static String name() {
      return SharedConstants.getCurrentVersion().name();
   }

   public static String id() {
      return SharedConstants.getCurrentVersion().id();
   }
}
