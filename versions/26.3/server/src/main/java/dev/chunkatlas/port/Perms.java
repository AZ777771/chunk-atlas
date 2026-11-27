package dev.chunkatlas.port;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class Perms {
   private Perms() {
   }

   public static boolean admin(CommandSourceStack source) {
      return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
   }

   public static boolean admin(ServerPlayer player) {
      return player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
   }
}
