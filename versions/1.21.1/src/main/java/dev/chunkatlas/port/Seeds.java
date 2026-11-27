package dev.chunkatlas.port;

import dev.chunkatlas.engine.BlockColours;
import dev.chunkatlas.seed.SeedSampler;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class Seeds {
   private Seeds() {
   }

   public static boolean available() {
      return false;
   }

   public static SeedSampler open(MinecraftServer server, String dimension, BlockColours colours) {
      return null;
   }

   public static SeedSampler forSeed(long seed, String dimension, BlockColours colours) {
      return null;
   }

   public static SeedSampler preview(WorldCreationContext settings, long seed, String dimension, BlockColours colours) {
      return null;
   }

   public static String unsupported(MinecraftServer server, String dimension) {
      return "chunkatlas.seed.unavailable.version";
   }

   public static boolean vanillaFailed() {
      return false;
   }

   public static String gameBiome(ServerLevel level, int blockX, int blockZ) {
      return null;
   }
}
