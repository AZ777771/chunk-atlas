package dev.chunkatlas.seed;

import dev.chunkatlas.engine.BiomeIndex;
import dev.chunkatlas.engine.BlockColours;
import dev.chunkatlas.engine.Look;
import dev.chunkatlas.engine.Surface;
import dev.chunkatlas.port.Heights;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Reg;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;

final class SeedChunks {
   private static final int CAVE_ROOF = 8;
   private final NoiseBasedChunkGenerator generator;
   private final NoiseGeneratorSettings settings;
   private final RandomState random;
   private final BiomeSource source;
   private final LevelHeightAccessor height;
   private final PalettedContainerFactory containers;
   private final Aquifer.FluidPicker fluids;
   private final MaterialRule rule;
   private final Set<Holder<Biome>> possible;
   private final long zoomSeed;
   private final BlockColours colours;
   private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

   private SeedChunks(
      NoiseBasedChunkGenerator generator, RandomState random, LevelHeightAccessor height, PalettedContainerFactory containers,
      Aquifer.FluidPicker fluids, long seed, BlockColours colours
   ) {
      this.generator = generator;
      this.settings = generator.generatorSettings().value();
      this.random = random;
      this.source = generator.getBiomeSource();
      this.height = height;
      this.containers = containers;
      this.fluids = fluids;
      this.rule = this.settings.materialRule().value();
      this.possible = Set.copyOf(this.source.possibleBiomes());
      this.zoomSeed = BiomeManager.obfuscateSeed(seed);
      this.colours = colours;
   }

   static SeedChunks of(ChunkGenerator generator, RandomState random, LevelHeightAccessor height, HolderLookup.Provider access, long seed, BlockColours colours) {
      if (!(generator instanceof NoiseBasedChunkGenerator noise) || !(access instanceof RegistryAccess registries)) {
         return null;
      }

      try {
         return new SeedChunks(noise, random, height, PalettedContainerFactory.create(registries), noise.globalFluidPicker.get(), seed, colours);
      } catch (RuntimeException | LinkageError e) {
         return null;
      }
   }

   ProtoChunk read(int cx, int cz, int caveY, int lo, int hi, BiomeResolver outside, Surface into, MapColumns map, int ox, int oz) {
      ChunkPos pos = new ChunkPos(cx, cz);
      ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, this.height, this.containers, null);
      DensityBufferPool pool = this.random.acquireDensityBufferPool();
      try {
         Climate.Sampler sampler = this.random.createClimateSampler(SamplerContext.builder().enableCaches().useBufferArena(pool).build());
         chunk.fillBiomesFromNoise(this.source.createResolverForChunk(
            sampler, QuartPos.fromBlock(pos.getMinBlockX()), QuartPos.fromBlock(Heights.min(chunk)), QuartPos.fromBlock(pos.getMinBlockZ()),
            QuartPos.fromBlock(16), QuartPos.fromBlock(chunk.getHeight()), QuartPos.fromBlock(16)
         ));
      } finally {
         this.random.releaseDensityBufferPool(pool);
      }

      chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
      NoiseSettings ns = this.settings.noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());

      int low = Math.max(ns.minY(), Math.floorDiv(lo, 8) * 8);
      int high = Math.min(ns.minY() + ns.height(), Math.floorDiv(hi + 7, 8) * 8);
      DensityVolume volume = new DensityVolume(16, Math.max(8, high - low), 16, pos.getMinBlockX(), Math.min(low, high - 8), pos.getMinBlockZ());
      int qx0 = QuartPos.fromBlock(pos.getMinBlockX());
      int qz0 = QuartPos.fromBlock(pos.getMinBlockZ());
      BiomeManager biomes = new BiomeManager(
         (qx, qy, qz) -> qx >= qx0 && qx < qx0 + 4 && qz >= qz0 && qz < qz0 + 4 ? chunk.getNoiseBiome(qx, qy, qz) : outside.getNoiseBiome(qx, qy, qz),
         this.zoomSeed
      );
      try (NoiseChunk noise = new NoiseChunk(this.random, Beardifier.EMPTY, this.settings, this.fluids, Blender.empty(), volume)) {
         this.generator.doFill(noise, chunk);
         this.generator.buildSurface(chunk, noise, this.random, biomes, this.possible, this.rule);
         chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.TERRAIN);
      }

      this.columns(chunk, caveY, into, map, ox, oz);
      return chunk;
   }

   private void columns(ProtoChunk chunk, int caveY, Surface into, MapColumns map, int ox, int oz) {
      Scratch s = this.scratch.get();
      s.surface = caveY == SeedWorld.NO_CAVE;
      int minY = Heights.min(chunk);
      int sky = minY;
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            sky = Math.max(sky, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz));
         }
      }

      sky = Math.min(Heights.maxInclusive(chunk), sky + 40);
      int x0 = chunk.getPos().getMinBlockX();
      int z0 = chunk.getPos().getMinBlockZ();
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int i = lz * 16 + lx;
            int start = caveY == SeedWorld.NO_CAVE ? sky : this.caveStart(chunk, x0 + lx, z0 + lz, caveY, minY, s);
            s.clear(i, minY);
            if (start != Integer.MIN_VALUE) {
               this.mapColumn(chunk, x0 + lx, z0 + lz, start, minY, i, s);
               this.column(chunk, x0 + lx, z0 + lz, start, minY, i, s);
            }
         }
      }

      into.putChunk(ox, oz, s.top, s.cover, s.height, s.depth, s.biome, s.light);
      if (map != null) {
         map.putChunk(ox, oz, s.map, s.mapHeight, s.mapWater);
      }
   }

   private void mapColumn(ProtoChunk chunk, int x, int z, int startY, int minY, int i, Scratch s) {
      int waterTop = Integer.MIN_VALUE;
      int waterColour = 0;
      for (int y = startY; y >= minY; y--) {
         int c = this.mapColour(chunk.getBlockState(s.pos.set(x, y, z)).getBlock(), s);
         if (c == 0) {
            continue;
         }

         if ((c & WET) == 0) {
            if (waterTop != Integer.MIN_VALUE) {
               s.map[i] = waterColour;
               s.mapHeight[i] = (short)waterTop;
               s.mapWater[i] = (byte)Math.min(255, waterTop - y);
            } else {
               s.map[i] = 0xFF000000 | c & 0xFFFFFF;
               s.mapHeight[i] = (short)y;
            }

            return;
         }

         if (waterTop == Integer.MIN_VALUE) {
            waterTop = y;
            waterColour = 0xFF000000 | c & 0xFFFFFF;
         } else if (waterTop - y >= 255) {
            break;
         }
      }

      if (waterTop != Integer.MIN_VALUE) {
         s.map[i] = waterColour;
         s.mapHeight[i] = (short)waterTop;
         s.mapWater[i] = (byte)255;
      }
   }

   private static final int WET = 0x01000000;
   private static final int DRY = 0x02000000;

   private int mapColour(Block block, Scratch s) {
      Integer known = s.mapColours.get(block);
      if (known == null) {
         String name = Reg.blockId(block);
         int colour = this.colours.colour(name);
         boolean wet = name.equals("minecraft:water") || name.equals("minecraft:bubble_column") || name.endsWith("seagrass") || name.equals("minecraft:kelp")
            || name.equals("minecraft:kelp_plant");
         if (wet && colour == 0) {
            colour = -12566273;
         }

         known = colour == 0 ? 0 : colour & 0xFFFFFF | (wet ? WET : DRY);
         s.mapColours.put(block, known);
      }

      return known;
   }

   private int caveStart(ProtoChunk chunk, int x, int z, int caveY, int minY, Scratch s) {
      int y = Math.max(minY, caveY);
      int roof = y - CAVE_ROOF;
      while (y > roof && this.solid(chunk, x, y, z, s)) {
         y--;
      }

      return this.solid(chunk, x, y, z, s) ? Integer.MIN_VALUE : y;
   }

   private boolean solid(ProtoChunk chunk, int x, int y, int z, Scratch s) {
      long look = this.look(chunk.getBlockState(s.pos.set(x, y, z)).getBlock(), s);
      return look != 0L && !Look.is(look, Look.WATER);
   }

   private void column(ProtoChunk chunk, int x, int z, int startY, int minY, int i, Scratch s) {
      int waterTop = Integer.MIN_VALUE;
      long coverLook = 0L;
      boolean coverDry = false;
      int coverY = Integer.MIN_VALUE;
      int topY = Integer.MIN_VALUE;
      for (int y = startY; y >= minY; y--) {
         long look = this.look(chunk.getBlockState(s.pos.set(x, y, z)).getBlock(), s);
         if (look == 0L) {
            continue;
         }

         if (Look.is(look, Look.COVER) || Look.is(look, Look.CLEAR)) {
            if (coverLook == 0L) {
               coverLook = look;
               coverDry = waterTop == Integer.MIN_VALUE;
               coverY = y;
            }

            if (Look.is(look, Look.WATER) && waterTop == Integer.MIN_VALUE) {
               waterTop = y;
            }

            continue;
         }

         if (Look.is(look, Look.WATER)) {
            if (waterTop == Integer.MIN_VALUE) {
               waterTop = y;
            }

            if (waterTop - y >= 254) {
               break;
            }

            continue;
         }

         topY = waterTop != Integer.MIN_VALUE ? waterTop : y;
         s.top[i] = Surface.top(look);
         s.depth[i] = (byte)(waterTop != Integer.MIN_VALUE ? Math.min(254, waterTop - y) : 0);
         break;
      }

      if (topY == Integer.MIN_VALUE) {
         if (waterTop != Integer.MIN_VALUE) {
            topY = waterTop;
            s.depth[i] = (byte)255;
         } else if (coverLook != 0L) {
            topY = coverY;
         } else {
            return;
         }
      }

      s.height[i] = (short)topY;
      if (coverLook != 0L) {
         s.cover[i] = Surface.cover(coverLook, coverDry && waterTop != Integer.MIN_VALUE);
      }

      Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.min(Heights.maxInclusive(chunk), topY + 1)), QuartPos.fromBlock(z));
      Short n = s.biomes.get(biome.value());
      if (n == null) {
         n = biome.unwrapKey().map(k -> BiomeIndex.of(Ids.of(k))).orElse((short)-1);
         s.biomes.put(biome.value(), n);
      }

      s.biome[i] = n;
      if (s.surface && biome.value().coldEnoughToSnow(s.pos.set(x, topY + 1, z), this.settings.seaLevel())) {
         if (s.depth[i] != 0) {
            s.cover[i] = Surface.cover(this.look(net.minecraft.world.level.block.Blocks.ICE, s), true);
            int ice = this.mapColour(net.minecraft.world.level.block.Blocks.ICE, s);
            if (ice != 0) {
               s.map[i] = 0xFF000000 | ice & 0xFFFFFF;
               s.mapWater[i] = 0;
            }
         } else {
            int snowMap = this.mapColour(net.minecraft.world.level.block.Blocks.SNOW, s);
            if (snowMap != 0 && s.mapWater[i] == 0) {
               s.map[i] = 0xFF000000 | snowMap & 0xFFFFFF;
            }

            if (s.cover[i] == 0) {
               long snow = this.look(net.minecraft.world.level.block.Blocks.SNOW, s);
               if (Look.is(snow, Look.COVER) || Look.is(snow, Look.CLEAR)) {
                  s.cover[i] = Surface.cover(snow, false);
               } else {
                  s.top[i] = Surface.top(snow);
               }
            }
         }
      }
   }

   private long look(Block block, Scratch s) {
      Long known = s.looks.get(block);
      if (known == null) {
         known = this.colours instanceof dev.chunkatlas.GameColours game ? game.look(block) : this.colours.look(Reg.blockId(block));
         s.looks.put(block, known);
      }

      return known;
   }

   private static final class Scratch {
      final int[] top = new int[256];
      final int[] cover = new int[256];
      final short[] height = new short[256];
      final byte[] depth = new byte[256];
      final short[] biome = new short[256];
      final byte[] light = new byte[256];
      final int[] map = new int[256];
      final short[] mapHeight = new short[256];
      final byte[] mapWater = new byte[256];
      final IdentityHashMap<Block, Long> looks = new IdentityHashMap<>();
      final IdentityHashMap<Block, Integer> mapColours = new IdentityHashMap<>();
      final IdentityHashMap<Biome, Short> biomes = new IdentityHashMap<>();
      final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      boolean surface;

      void clear(int i, int minY) {
         this.top[i] = 0;
         this.cover[i] = 0;
         this.height[i] = (short)minY;
         this.depth[i] = 0;
         this.biome[i] = -1;
         this.light[i] = 0;
         this.map[i] = 0;
         this.mapHeight[i] = (short)minY;
         this.mapWater[i] = 0;
      }
   }
}
