package dev.chunkatlas.port;

import dev.chunkatlas.engine.BlockColours;
import dev.chunkatlas.seed.SeedSampler;
import dev.chunkatlas.seed.SeedWorld;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

public final class Seeds {
   private Seeds() {
   }

   public static boolean available() {
      return true;
   }

   public static SeedSampler open(MinecraftServer server, String dimension, BlockColours colours) {
      return SeedWorld.open(server, dimension, colours);
   }

   public static SeedSampler forSeed(long seed, String dimension, BlockColours colours) {
      return SeedWorld.forSeed(seed, dimension, colours);
   }

   public static SeedSampler preview(WorldCreationContext settings, long seed, String dimension, BlockColours colours) {
      return SeedWorld.preview(settings, seed, dimension, colours);
   }

   public static String unsupported(MinecraftServer server, String dimension) {
      return SeedWorld.unsupported(server, dimension);
   }

   public static boolean vanillaFailed() {
      return SeedWorld.vanillaFailed();
   }

   public static String gameBiome(ServerLevel level, int blockX, int blockZ) {
      var gen = level.getChunkSource().getGenerator();
      var rs = level.getChunkSource().randomState();
      int y = gen.getBaseHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
      Holder<Biome> h = gen.getBiomeSource()
         .createResolver(rs.createClimateSampler(SamplerContext.builder().build()))
         .getNoiseBiome(QuartPos.fromBlock(blockX), QuartPos.fromBlock(y), QuartPos.fromBlock(blockZ));
      return h.unwrapKey().map(Ids::of).orElse(null);
   }
}
