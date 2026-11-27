package dev.chunkatlas.port;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class Perms {
   private Perms() {
   }

   public static boolean admin(CommandSourceStack source) {
      return source.hasPermission(3);
   }

   public static boolean admin(ServerPlayer player) {
      return player.hasPermissions(3);
   }
}
