package dev.chunkatlas.seed;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;

final class SeedLevel implements WorldGenLevel {
   private final SeedWorld world;
   private final int minY;
   private final int height;
   private final int seaLevel;
   private final long seed;

   SeedLevel(SeedWorld world, int minY, int maxY, int seaLevel, long seed) {
      this.world = world;
      this.minY = minY;
      this.height = maxY - minY;
      this.seaLevel = seaLevel;
      this.seed = seed;
   }

   private static UnsupportedOperationException no(String what) {
      return new UnsupportedOperationException(what);
   }

   @Override
   public int getMinY() {
      return this.minY;
   }

   @Override
   public int getHeight() {
      return this.height;
   }

   @Override
   public int getHeight(Heightmap.Types type, int x, int z) {
      return this.world.placingHeight(type, x, z);
   }

   @Override
   public int getSeaLevel() {
      return this.seaLevel;
   }

   @Override
   public long getSeed() {
      return this.seed;
   }

   @Override
   public Holder<Biome> getBiome(BlockPos pos) {
      return this.world.placingBiome(pos);
   }

   @Override
   public BlockState getBlockState(BlockPos pos) {
      return this.world.placingBlock(pos);
   }

   @Override
   public FluidState getFluidState(BlockPos pos) {
      return this.world.placingBlock(pos).getFluidState();
   }

   @Override
   public String toString() {
      return "Chunk Atlas seed map level";
   }

   @Override
   public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB box, Predicate<? super T> filter) {
      throw no("getEntities");
   }

   @Override
   public List<Entity> getEntities(Entity except, AABB box, Predicate<? super Entity> filter) {
      throw no("getEntities");
   }

   @Override
   public List<? extends Player> players() {
      throw no("players");
   }

   @Override
   public boolean isClientSide() {
      throw no("isClientSide");
   }

   @Override
   public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
      throw no("isFluidAtPosition");
   }

   @Override
   public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
      throw no("isStateAtPosition");
   }

   @Override
   public boolean destroyBlock(BlockPos pos, boolean drop, Entity breaker, int updateLimit) {
      throw no("destroyBlock");
   }

   @Override
   public boolean removeBlock(BlockPos pos, boolean moving) {
      throw no("removeBlock");
   }

   @Override
   public boolean setBlock(BlockPos pos, BlockState state, int flags, int updateLimit) {
      throw no("setBlock");
   }

   @Override
   public int getSkyDarken() {
      throw no("getSkyDarken");
   }

   @Override
   public long nextSubTickCount() {
      throw no("nextSubTickCount");
   }

   @Override
   public Holder<Biome> getUncachedNoiseBiome(int qx, int qy, int qz) {
      throw no("getUncachedNoiseBiome");
   }

   @Override
   public RegistryAccess registryAccess() {
      throw no("registryAccess");
   }

   @Override
   public MinecraftServer getServer() {
      throw no("getServer");
   }

   @Override
   public ServerLevel getLevel() {
      throw no("getLevel");
   }

   @Override
   public RandomSource getRandom() {
      throw no("getRandom");
   }

   @Override
   public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
      throw no("getCurrentDifficultyAt");
   }

   @Override
   public EnvironmentAttributeReader environmentAttributes() {
      throw no("environmentAttributes");
   }

   @Override
   public FeatureFlagSet enabledFeatures() {
      throw no("enabledFeatures");
   }

   @Override
   public BiomeManager getBiomeManager() {
      throw no("getBiomeManager");
   }

   @Override
   public BlockEntity getBlockEntity(BlockPos pos) {
      throw no("getBlockEntity");
   }

   @Override
   public WorldBorder getWorldBorder() {
      throw no("getWorldBorder");
   }

   @Override
   public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
      throw no("getChunk");
   }

   @Override
   public ChunkSource getChunkSource() {
      throw no("getChunkSource");
   }

   @Override
   public DimensionType dimensionType() {
      throw no("dimensionType");
   }

   @Override
   public LevelLightEngine getLightEngine() {
      throw no("getLightEngine");
   }

   @Override
   public LevelData getLevelData() {
      throw no("getLevelData");
   }

   @Override
   public LevelTickAccess<Block> getBlockTicks() {
      throw no("getBlockTicks");
   }

   @Override
   public LevelTickAccess<Fluid> getFluidTicks() {
      throw no("getFluidTicks");
   }

   @Override
   public void addParticle(ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
      throw no("addParticle");
   }

   @Override
   public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
      throw no("gameEvent");
   }

   @Override
   public void levelEvent(Entity source, int type, BlockPos pos, int data) {
      throw no("levelEvent");
   }

   @Override
   public void playSound(Entity except, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
      throw no("playSound");
   }
}
