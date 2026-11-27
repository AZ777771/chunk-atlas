package dev.chunkatlas.port;

import net.minecraft.SharedConstants;

public final class Version {
   private Version() {
   }

   public static int dataVersion() {
      return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
   }

   public static String name() {
      return SharedConstants.getCurrentVersion().getName();
   }

   public static String id() {
      return SharedConstants.getCurrentVersion().getId();
   }
}
