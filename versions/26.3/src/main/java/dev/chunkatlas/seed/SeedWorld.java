package dev.chunkatlas.seed;

import dev.chunkatlas.engine.BlockColours;
import dev.chunkatlas.engine.IdText;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Heights;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Pos;
import dev.chunkatlas.port.Reg;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.DimensionOriginStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class SeedWorld implements SeedSampler {
   private final MinecraftServer server;
   private final ServerLevel level;

   private final net.minecraft.world.level.LevelHeightAccessor height;
   private final boolean ceiling;
   private final String dimension;
   private final ChunkGenerator generator;
   private final BiomeSource source;
   private final RandomState random;
   private final ChunkGeneratorStructureState state;
   private final long seed;
   private final net.minecraft.core.HolderLookup.Provider access;
   private final StructureTemplateManager templates;
   private final Registry<Biome> biomes;
   private final DensityFunction surface;

   private final DensityFunction depth;

   private DensityFunction density;
   private final int seaLevel;
   private final int minY;
   private final int maxY;
   private final boolean slime;
   private final List<SetInfo> sets = new ArrayList<>();
   private final List<String> kinds;
   private final ThreadLocal<Worker> workers;
   private volatile List<Found> rings;

   private static final int HEIGHT_STEP = 8;
   private final BlockColours colours;
   private final Registry<PlacedFeature> placed;
   private final List<FeatureInfo> featureList = new ArrayList<>();
   private volatile boolean featureIndexed;

   public volatile String lastFeatureError;
   private final WorldGenLevel fakeLevel;

   private final int flatColour;
   private final long flatLook;

   private SeedWorld(MinecraftServer server, ServerLevel level, String dimension, BlockColours colours) {
      this(
         server, level, dimension, colours, level.getChunkSource().getGenerator(), level.getChunkSource().randomState(),
         level.getChunkSource().getGeneratorState(), level.getSeed(), level.registryAccess(), level.getStructureTemplateManager(), level,
         level.dimensionType().hasCeiling(), level.dimension() == Level.OVERWORLD, server.getWorldGenSettings().options().generateStructures()
      );
   }

   public static SeedWorld preview(
      net.minecraft.client.gui.screens.worldselection.WorldCreationContext ctx, long seed, String dimension, BlockColours colours
   ) {
      Identifier id = Identifier.tryParse(dimension);
      if (id == null) {
         return null;
      }

      net.minecraft.world.level.dimension.LevelStem stem = ctx.selectedDimensions()
         .get(ResourceKey.create(Registries.LEVEL_STEM, id))
         .orElse(null);
      if (stem == null || stem.generator() instanceof DebugLevelSource) {
         return null;
      }

      RegistryAccess.Frozen access = ctx.worldgenLoadContext();
      ChunkGenerator generator = stem.generator();
      net.minecraft.world.level.dimension.DimensionType type = stem.type().value();
      RandomState random = generator instanceof NoiseBasedChunkGenerator noise
         ? RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, noise.generatorSettings().value())
         : RandomState.create(
            access.lookupOrThrow(Registries.NOISE), seed,
            access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD).value()
         );
      ChunkGeneratorStructureState state = generator.createState(access.lookupOrThrow(Registries.STRUCTURE_SET), random, seed);
      net.minecraft.world.level.LevelHeightAccessor height = net.minecraft.world.level.LevelHeightAccessor.create(type.minY(), type.height());
      return new SeedWorld(
         null, null, dimension, colours, generator, random, state, seed, access, null, height, type.hasCeiling(), id.getPath().equals("overworld"),
         ctx.options().generateStructures()
      );
   }

   private SeedWorld(
      MinecraftServer server, ServerLevel level, String dimension, BlockColours colours, ChunkGenerator generator, RandomState random,
      ChunkGeneratorStructureState state, long seed, net.minecraft.core.HolderLookup.Provider access, StructureTemplateManager templates,
      net.minecraft.world.level.LevelHeightAccessor height, boolean ceiling, boolean overworld, boolean structures
   ) {
      this.colours = colours;
      this.server = server;
      this.level = level;
      this.height = height;
      this.ceiling = ceiling;
      this.dimension = dimension;
      this.generator = generator;
      this.source = this.generator.getBiomeSource();
      this.random = random;
      this.state = state;
      this.seed = seed;
      this.access = access;
      this.templates = templates;
      this.biomes = registry(this.access, Registries.BIOME);
      this.minY = Heights.min(height);
      this.maxY = Heights.maxInclusive(height);
      DensityFunction s = null;
      DensityFunction d = null;
      int sea = 63;
      if (this.generator instanceof NoiseBasedChunkGenerator noise) {
         sea = noise.getSeaLevel();
         if (!ceiling) {
            s = noise.generatorSettings().value().noiseRouter().chunkSurfaceLevel();
            d = noise.generatorSettings().value().noiseRouter().depth();
         }

         this.density = noise.generatorSettings().value().noiseRouter().finalDensity();
      }

      this.surface = s;
      this.depth = d;
      this.seaLevel = sea;
      int flat = -1;
      long flatLook = 0L;
      if (this.generator instanceof FlatLevelSource f) {
         flat = 0;
         List<BlockState> layers = f.settings().getLayers();
         for (int i = layers.size() - 1; i >= 0; i--) {
            BlockState b = layers.get(i);
            if (b != null && !b.isAir()) {
               flat = colours.colour(Reg.blockId(b.getBlock()));
               flatLook = colours.look(Reg.blockId(b.getBlock()));
               break;
            }
         }
      }

      this.flatColour = flat;
      this.flatLook = flatLook;
      this.slime = overworld;
      this.workers = ThreadLocal.withInitial(Worker::new);
      Set<Holder<Biome>> possible = this.source.possibleBiomes();

      Set<String> found = new HashSet<>();
      if (structures) {
         for (Holder<StructureSet> set : this.state.possibleStructureSets()) {
            StructureSet value = set.value();
            List<Entry> entries = new ArrayList<>();
            boolean any = false;
            for (StructureSet.StructureSelectionEntry e : value.structures()) {
               String id = e.structure().unwrapKey().map(k -> Ids.of(k)).orElse("unknown:structure");
               boolean can = e.structure().value().biomes().stream().anyMatch(possible::contains);
               entries.add(new Entry(e.structure(), id, SeedKinds.kindOf(id), e.weight(), can));
               any |= can;
            }

            if (any) {
               int every = value.placement() instanceof RandomSpreadStructurePlacement spread ? spread.spacing() : 0;
               this.sets.add(new SetInfo(value.placement(), entries, every));
               for (Entry e : entries) {
                  if (e.possible()) {
                     found.add(e.kind());
                  }
               }
            }
         }
      }

      List<String> list = new ArrayList<>(found);
      list.sort(SeedKinds.LIST_ORDER);
      this.placed = registry(this.access, Registries.PLACED_FEATURE);
      for (Map.Entry<String, String> f : SeedKinds.FEATURES.entrySet()) {
         Identifier id = Identifier.tryParse(f.getKey());
         PlacedFeature feature = id == null ? null : this.placed.getOptional(id).orElse(null);
         if (feature != null && possible.stream().anyMatch(b -> this.generator.getBiomeGenerationSettings(b).hasFeature(feature))) {
            this.featureList.add(new FeatureInfo(f.getValue(), f.getKey(), feature));
            if (!list.contains(f.getValue())) {
               list.add(f.getValue());
            }
         }
      }

      list.sort(SeedKinds.LIST_ORDER);
      this.kinds = List.copyOf(list);
      this.fakeLevel = new SeedLevel(this, this.minY, this.maxY, this.seaLevel, this.seed);
   }

   public static String unsupported(MinecraftServer server, String dimension) {
      ResourceKey<Level> id = Ids.dimension(dimension);
      ServerLevel level = server == null || id == null ? null : server.getLevel(id);
      if (level == null) {
         return null;
      }

      return level.getChunkSource().getGenerator() instanceof DebugLevelSource ? "chunkatlas.seed.unavailable.debug" : null;
   }

   private static java.util.concurrent.CompletableFuture<RegistryAccess.Frozen> vanilla;

   private static synchronized RegistryAccess.Frozen vanilla() {
      if (vanilla == null) {
         net.minecraft.server.packs.repository.PackRepository repo = net.minecraft.server.packs.repository.ServerPacksSource.createVanillaTrustedRepository();
         net.minecraft.server.WorldLoader.InitConfig init = new net.minecraft.server.WorldLoader.InitConfig(
            new net.minecraft.server.WorldLoader.PackConfig(repo, net.minecraft.world.level.WorldDataConfiguration.DEFAULT, false, true),
            net.minecraft.commands.Commands.CommandSelection.INTEGRATED,
            net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS
         );
         vanilla = net.minecraft.server.WorldLoader.<Boolean, RegistryAccess.Frozen>load(
            init,
            ctx -> new net.minecraft.server.WorldLoader.DataLoadOutput<>(Boolean.TRUE, ctx.datapackDimensions()),
            (resources, reloadable, layered, cookie) -> {
               resources.close();
               return layered.compositeAccess();
            },
            Game.background(),
            Game.background()
         );
      }

      return vanilla.isDone() && !vanilla.isCompletedExceptionally() ? vanilla.join() : null;
   }

   public static synchronized boolean vanillaFailed() {
      return vanilla != null && vanilla.isCompletedExceptionally();
   }

   public static SeedWorld forSeed(long seed, String dimension, BlockColours colours) {
      Identifier id = dimension == null ? null : Identifier.tryParse(dimension);
      if (id == null) {
         return null;
      }

      RegistryAccess.Frozen access = vanilla();
      if (access == null) {
         return null;
      }

      net.minecraft.world.level.dimension.LevelStem stem = net.minecraft.world.level.levelgen.presets.WorldPresets.createNormalWorldDimensions(access)
         .get(ResourceKey.create(Registries.LEVEL_STEM, id))
         .orElse(null);
      if (stem == null) {
         return null;
      }

      ChunkGenerator generator = stem.generator();
      net.minecraft.world.level.dimension.DimensionType type = stem.type().value();
      RandomState random = generator instanceof NoiseBasedChunkGenerator noise
         ? RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, noise.generatorSettings().value())
         : RandomState.create(
            access.lookupOrThrow(Registries.NOISE), seed,
            access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD).value()
         );
      ChunkGeneratorStructureState state = generator.createState(access.lookupOrThrow(Registries.STRUCTURE_SET), random, seed);
      net.minecraft.world.level.LevelHeightAccessor height = net.minecraft.world.level.LevelHeightAccessor.create(type.minY(), type.height());
      return new SeedWorld(null, null, dimension, colours, generator, random, state, seed, access, null, height, type.hasCeiling(), id.getPath().equals("overworld"), true);
   }

   private static <T> Registry<T> registry(net.minecraft.core.HolderLookup.Provider access, ResourceKey<? extends Registry<? extends T>> key) {
      if (access instanceof RegistryAccess r) {
         @SuppressWarnings("unchecked")
         Registry<T> real = (Registry<T>)r.lookupOrThrow(key);
         return real;
      }

      synchronized (BUILT) {
         @SuppressWarnings("unchecked")
         Registry<T> made = (Registry<T>)BUILT.computeIfAbsent(key, k -> {
            @SuppressWarnings("unchecked")
            ResourceKey<? extends Registry<T>> typed = (ResourceKey<? extends Registry<T>>)k;
            net.minecraft.core.MappedRegistry<T> reg = new net.minecraft.core.MappedRegistry<>(typed, com.mojang.serialization.Lifecycle.stable());
            access.lookupOrThrow(typed).listElements().forEach(h -> reg.register(h.key(), h.value(), net.minecraft.core.RegistrationInfo.BUILT_IN));
            return reg.freeze();
         });
         return made;
      }
   }

   private static final Map<ResourceKey<?>, Registry<?>> BUILT = new java.util.HashMap<>();

   public long seed() {
      return this.seed;
   }

   public static SeedWorld open(MinecraftServer server, String dimension, BlockColours colours) {
      if (server == null || dimension == null) {
         return null;
      }

      ResourceKey<Level> id = Ids.dimension(dimension);
      ServerLevel level = id == null ? null : server.getLevel(id);
      return level == null ? null : new SeedWorld(server, level, dimension, colours);
   }

   public MinecraftServer server() {
      return this.server;
   }

   public String dimension() {
      return this.dimension;
   }

   public boolean hasSlime() {
      return this.slime;
   }

   public List<String> kinds() {
      return this.kinds;
   }

   private volatile SeedChunks chunks;
   private volatile boolean chunksTried;

   public boolean canMakeChunks() {
      return this.chunks() != null;
   }

   private SeedChunks chunks() {
      if (!this.chunksTried) {
         synchronized (this) {
            if (!this.chunksTried) {
               this.chunks = SeedChunks.of(this.generator, this.random, this.height, this.access, this.seed, this.colours);
               this.chunksTried = true;
            }
         }
      }

      return this.chunks;
   }

   @Override
   public boolean makeChunk(int cx, int cz, int caveY, dev.chunkatlas.engine.Surface into, MapColumns map, int ox, int oz, List<Tree> trees) {
      SeedChunks c = this.chunks();
      if (c == null) {
         return false;
      }

      int lo;
      int hi;
      if (caveY == NO_CAVE) {
         int min = Integer.MAX_VALUE;
         int max = Integer.MIN_VALUE;
         for (int i = 0; i < 9; i++) {
            int g = this.surfaceGuess(cx * 16 + i % 3 * 8, cz * 16 + i / 3 * 8);
            min = Math.min(min, g);
            max = Math.max(max, g);
         }

         lo = min - 40;
         hi = Math.max(max + 100, this.seaLevel + 8);
      } else {
         lo = caveY - 64;
         hi = caveY + 16;
      }

      net.minecraft.world.level.chunk.ProtoChunk chunk = c.read(cx, cz, caveY, lo, hi, this.workers.get().resolver, into, map, ox, oz);
      if (trees != null && caveY == NO_CAVE) {
         try {
            this.trees(chunk, trees);
         } catch (RuntimeException e) {
            this.lastFeatureError = "trees: " + e;
         }
      }

      return true;
   }

   private final ThreadLocal<net.minecraft.world.level.chunk.ChunkAccess> placing = new ThreadLocal<>();

   private final Map<PlacedFeature, Optional<TreeLook>> treeLooks = new java.util.concurrent.ConcurrentHashMap<>();
   private volatile List<FeatureSorter.StepFeatureData> steps;

   private record TreeLook(int leaves, int radius, int height, int map) {
   }

   private void trees(net.minecraft.world.level.chunk.ChunkAccess chunk, List<Tree> out) {
      int step = net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
      List<FeatureSorter.StepFeatureData> all = this.steps;
      if (all == null) {
         all = FeatureSorter.buildFeaturesPerStep(List.copyOf(this.source.possibleBiomes()), b -> this.generator.getBiomeGenerationSettings(b).features(), true);
         this.steps = all;
      }

      if (step >= all.size()) {
         return;
      }

      ChunkPos pos = chunk.getPos();
      Set<Holder<Biome>> here = new HashSet<>();
      for (int qz = 0; qz < 4; qz++) {
         for (int qx = 0; qx < 4; qx++) {
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, qx * 4 + 2, qz * 4 + 2);
            here.add(chunk.getNoiseBiome(QuartPos.fromBlock(pos.getMinBlockX()) + qx, QuartPos.fromBlock(y), QuartPos.fromBlock(pos.getMinBlockZ()) + qz));
         }
      }

      FeatureSorter.StepFeatureData data = all.get(step);
      java.util.TreeMap<Integer, PlacedFeature> features = new java.util.TreeMap<>();
      for (Holder<Biome> b : here) {
         List<HolderSet<PlacedFeature>> lists = this.generator.getBiomeGenerationSettings(b).features();
         if (step < lists.size()) {
            for (Holder<PlacedFeature> f : lists.get(step)) {
               if (this.treeLook(f.value(), 0) != null) {
                  features.put(data.indexMapping().applyAsInt(f.value()), f.value());
               }
            }
         }
      }

      if (features.isEmpty()) {
         return;
      }

      WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
      BlockPos origin = new BlockPos(pos.getMinBlockX(), this.minY, pos.getMinBlockZ());
      long decoration = random.setDecorationSeed(this.seed, origin.getX(), origin.getZ());
      this.placing.set(chunk);
      try {
         for (Map.Entry<Integer, PlacedFeature> e : features.entrySet()) {
            random.setFeatureSeed(decoration, e.getKey(), step);
            for (BlockPos at : this.place(e.getValue(), random, origin)) {
               TreeLook look = this.treeLook(e.getValue(), at.asLong() * 0x9E3779B97F4A7C15L);
               if (look != null) {
                  out.add(new Tree(at.getX(), at.getZ(), at.getY() + look.height(), look.leaves(), look.radius(), look.map()));
               }
            }
         }
      } finally {
         this.placing.remove();
      }
   }

   private TreeLook treeLook(PlacedFeature placed, long pick) {
      net.minecraft.world.level.levelgen.feature.Feature f = placed.feature().value();
      if (f instanceof net.minecraft.world.level.levelgen.feature.TreeFeature tree) {
         return this.treeLooks.computeIfAbsent(placed, p -> Optional.ofNullable(this.lookOf(tree))).orElse(null);
      }

      java.util.Random r = new java.util.Random(pick);
      if (f instanceof net.minecraft.world.level.levelgen.feature.RandomSelectorFeature sel) {
         for (net.minecraft.world.level.levelgen.feature.WeightedPlacedFeature w : sel.features()) {
            if (pick == 0L) {
               TreeLook any = this.treeLook(w.feature().value(), 0L);
               if (any != null) {
                  return any;
               }
            } else if (r.nextFloat() < w.chance()) {
               return this.treeLook(w.feature().value(), r.nextLong());
            }
         }

         return this.treeLook(sel.defaultFeature().value(), pick == 0L ? 0L : r.nextLong());
      } else if (f instanceof net.minecraft.world.level.levelgen.feature.RandomBooleanSelectorFeature sel) {
         if (pick == 0L) {
            TreeLook a = this.treeLook(sel.featureTrue().value(), 0L);
            return a != null ? a : this.treeLook(sel.featureFalse().value(), 0L);
         }

         return this.treeLook((r.nextBoolean() ? sel.featureTrue() : sel.featureFalse()).value(), r.nextLong());
      } else if (f instanceof net.minecraft.world.level.levelgen.feature.SimpleRandomSelectorFeature sel) {
         List<Holder<PlacedFeature>> list = sel.features().stream().toList();
         if (list.isEmpty()) {
            return null;
         }

         if (pick == 0L) {
            for (Holder<PlacedFeature> h : list) {
               TreeLook any = this.treeLook(h.value(), 0L);
               if (any != null) {
                  return any;
               }
            }

            return null;
         }

         return this.treeLook(list.get(r.nextInt(list.size())).value(), r.nextLong());
      } else if (f instanceof net.minecraft.world.level.levelgen.feature.WeightedRandomSelectorFeature sel) {
         List<Holder<PlacedFeature>> list = new ArrayList<>();
         sel.features().unwrap().forEach(w -> list.add(w.value()));
         if (list.isEmpty()) {
            return null;
         }

         if (pick == 0L) {
            for (Holder<PlacedFeature> h : list) {
               TreeLook any = this.treeLook(h.value(), 0L);
               if (any != null) {
                  return any;
               }
            }

            return null;
         }

         return this.treeLook(list.get(r.nextInt(list.size())).value(), r.nextLong());
      }

      return null;
   }

   private TreeLook lookOf(net.minecraft.world.level.levelgen.feature.TreeFeature tree) {
      BlockState leaves;
      try {
         leaves = tree.foliageProvider().value() instanceof net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider simple
            ? simple.state()
            : tree.foliageProvider().value().getState(this.fakeLevel, RandomSource.create(0L), BlockPos.ZERO);
      } catch (RuntimeException e) {
         leaves = Blocks.OAK_LEAVES.defaultBlockState();
      }

      int radius = 2;
      net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer placer = tree.foliagePlacer();
      try {
         radius = Math.max(1, Math.min(5, placer.radius.maxInclusive()));
      } catch (RuntimeException ignored) {
      }

      if (placer instanceof net.minecraft.world.level.levelgen.feature.foliageplacers.DarkOakFoliagePlacer
         || placer instanceof net.minecraft.world.level.levelgen.feature.foliageplacers.MegaPineFoliagePlacer
         || placer instanceof net.minecraft.world.level.levelgen.feature.foliageplacers.CherryFoliagePlacer
         || placer instanceof net.minecraft.world.level.levelgen.feature.foliageplacers.RandomSpreadFoliagePlacer) {
         radius = Math.max(radius, 3);
      }

      String block = Reg.blockId(leaves.getBlock());
      int top = dev.chunkatlas.engine.Surface.top(this.colours.look(block));
      int map = this.colours.colour(block);
      return top == 0 ? null : new TreeLook(top, radius, Math.max(3, tree.trunkPlacer().getBaseHeight()) + 1, map == 0 ? 0 : 0xFF000000 | map);
   }

   public String bench() {
      Worker w = this.workers.get();
      int sink = 0;
      long t0 = System.nanoTime();
      for (int i = 0; i < 20000; i++) {
         sink += System.identityHashCode(w.resolver.getNoiseBiome(QuartPos.fromBlock(i * 37), QuartPos.fromBlock(64), QuartPos.fromBlock(i * 53 + 9000)));
      }

      long t1 = System.nanoTime();
      for (int i = 0; i < 20000; i++) {
         sink += this.surfaceGuess(i * 37 + 100000, i * 53);
      }

      long t2 = System.nanoTime();
      for (int i = 0; i < 300; i++) {
         sink += this.generator.getBaseHeight(i * 97 - 50000, i * 31, Heightmap.Types.WORLD_SURFACE_WG, this.height, this.random);
      }

      long t3 = System.nanoTime();
      String made;
      try {
         dev.chunkatlas.engine.Surface into = new dev.chunkatlas.engine.Surface(64);
         int n = 0;
         for (int i = 0; i < 16; i++) {
            if (this.makeChunk(i % 4 + 300, i / 4 + 300, NO_CAVE, into, i % 4 * 16, i / 4 * 16)) {
               n++;
            }
         }

         long t4 = System.nanoTime();
         int drawn = 0;
         for (int i = 0; i < 64 * 64; i++) {
            drawn += into.drawn(i) ? 1 : 0;
         }

         made = n == 0 ? "chunks: can't be made here" : String.format(java.util.Locale.ROOT, "chunk %.1f ms (%d of 4096 columns drawn)", (t4 - t3) / (double)n / 1000000.0, drawn);
      } catch (RuntimeException e) {
         made = "chunks failed: " + e;
      }

      return String.format(java.util.Locale.ROOT, "biome %.2f us, surface guess %.2f us, height %.1f us, %s (%d)",
         (t1 - t0) / 20000.0 / 1000.0, (t2 - t1) / 20000.0 / 1000.0, (t3 - t2) / 300.0 / 1000.0, made, sink & 1);
   }

   public int biomeAt(int blockX, int blockZ) {
      int y = Math.max(this.surfaceGuess(blockX, blockZ), this.seaLevel);
      Holder<Biome> b = this.workers.get().resolver.getNoiseBiome(QuartPos.fromBlock(blockX), QuartPos.fromBlock(y), QuartPos.fromBlock(blockZ));
      return this.biomes.getId(b.value());
   }

   public int biomeAt(int blockX, int blockY, int blockZ) {
      Holder<Biome> b = this.workers.get().resolver.getNoiseBiome(QuartPos.fromBlock(blockX), QuartPos.fromBlock(blockY), QuartPos.fromBlock(blockZ));
      return this.biomes.getId(b.value());
   }

   public ServerLevel level() {
      return this.level;
   }

   public int biomeNumber(Holder<Biome> biome) {
      return this.biomes.getId(biome.value());
   }

   public String biomeId(int id) {
      Biome b = this.biomes.byId(id);
      Identifier key = b == null ? null : this.biomes.getKey(b);
      return key == null ? null : key.toString();
   }

   public boolean flat() {
      return this.flatColour >= 0;
   }

   public boolean hasTerrain() {
      return this.generator instanceof NoiseBasedChunkGenerator && !this.ceiling;
   }

   public double groundAt(int blockX, int blockZ) {
      return this.hasTerrain() ? this.ground(blockX, blockZ) : Double.NaN;
   }

   private final java.util.concurrent.ConcurrentHashMap<Long, Long> heights = new java.util.concurrent.ConcurrentHashMap<>();

   private static final int HEIGHTS_KEPT = 500_000;

   private long height(int bx, int bz) {
      long key = (long)bx << 32 ^ bz & 0xFFFFFFFFL;
      Long known = this.heights.get(key);
      if (known != null) {
         return known;
      }

      int top = this.generator.getBaseHeight(bx, bz, Heightmap.Types.WORLD_SURFACE_WG, this.height, this.random);
      int floor = top <= this.seaLevel + 1 ? this.generator.getBaseHeight(bx, bz, Heightmap.Types.OCEAN_FLOOR_WG, this.height, this.random) : top;
      long packed = (long)top << 32 | floor & 0xFFFFFFFFL;
      if (this.heights.size() > HEIGHTS_KEPT) {
         this.heights.clear();
      }

      this.heights.put(key, packed);
      return packed;
   }

   private long estimate(int bx, int bz) {
      int floor = this.ground(bx, bz);
      int top = Math.max(floor, this.seaLevel);
      return (long)top << 32 | floor & 0xFFFFFFFFL;
   }

   private int ground(int bx, int bz) {
      Worker w = this.workers.get();
      if (w.depthSlope <= 0.0) {
         return this.surfaceGuess(bx, bz);
      }

      int y;
      try {
         y = (int)Math.floor(this.seaLevel + w.depth.sampleValue(bx, this.seaLevel, bz) / w.depthSlope);
      } catch (RuntimeException e) {
         return this.surfaceGuess(bx, bz);
      }

      return Math.max(this.minY, Math.min(this.maxY - 1, y));
   }

   public String groundCheck(int cx, int cz) {
      java.util.Random r = new java.util.Random(7L);
      int n = 1500;
      int agree = 0;
      int agreeGuess = 0;
      int land = 0;
      double off = 0.0;
      double offGuess = 0.0;
      for (int i = 0; i < n; i++) {
         int x = cx + r.nextInt(16000) - 8000;
         int z = cz + r.nextInt(16000) - 8000;
         int real = this.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.height, this.random);
         int quick = this.ground(x, z);
         int guess = this.surfaceGuess(x, z);
         boolean isLand = real > this.seaLevel;
         land += isLand ? 1 : 0;
         agree += quick >= this.seaLevel == isLand ? 1 : 0;
         agreeGuess += guess >= this.seaLevel == isLand ? 1 : 0;
         off += quick - real;
         offGuess += guess - real;
      }

      return String.format(java.util.Locale.ROOT, "%d%% land; land/sea right at %.1f%% of spots (the game's guess: %.1f%%); mean height off by %.1f (guess: %.1f)",
         land * 100 / n, agree * 100.0 / n, agreeGuess * 100.0 / n, off / n, offGuess / n);
   }

   public int[] tourLand(int cx, int cz) {
      int[] best = {cx, cz, -1};
      for (int i = 0; i < 40 && best[2] < 85; i++) {
         int x = cx + 12000 + (i % 8) * 1500;
         int z = cz - 6000 + (i / 8) * 2500;
         int land = 0;
         for (int k = 0; k < 25; k++) {
            int top = this.generator.getBaseHeight(x + (k % 5 - 2) * 300, z + (k / 5 - 2) * 300, Heightmap.Types.OCEAN_FLOOR_WG, this.height, this.random);
            land += top > this.seaLevel ? 4 : 0;
         }

         if (land > best[2]) {
            best = new int[]{x, z, land};
         }
      }

      return best;
   }

   private static final int CAVE_ROOF = 8;

   private static final int CAVE_STEP = 4;

   private static final int CAVE_DROP = 96;

   private boolean solidAt(Worker w, int bx, int y, int bz) {
      if (y <= this.minY) {
         return true;
      }

      if (y >= this.maxY) {
         return false;
      }

      try {
         return w.density.sampleValue(bx, y, bz) > 0.0;
      } catch (RuntimeException e) {
         return true;
      }
   }

   private int surfaceGuess(int bx, int bz) {
      Worker w = this.workers.get();
      int y = this.seaLevel;
      if (w.surface != null) {
         try {
            y = (int)Math.floor(w.surface.sampleValue(bx, 0, bz));
         } catch (RuntimeException e) {
            y = this.seaLevel;
         }
      }

      return Math.max(this.minY, Math.min(this.maxY - 1, y));
   }

   private long[] caveLook(Holder<Biome> biome, int y) {
      String path = biome.unwrapKey().map(k -> Ids.path(k)).orElse("");
      String block = switch (path) {
         case "lush_caves" -> "minecraft:moss_block";
         case "dripstone_caves" -> "minecraft:dripstone_block";
         case "deep_dark" -> "minecraft:sculk";
         default -> y < 0 ? "minecraft:deepslate" : "minecraft:stone";
      };
      return new long[]{this.colours.look(block), this.colours.colour(block)};
   }

   private volatile java.nio.file.Path heightsDir;

   public void keepHeightsIn(java.nio.file.Path home) {
      this.heightsDir = home == null ? null : home.resolve("seed-heights").resolve(Long.toHexString(this.seed)).resolve(this.dimension.replace(':', '@').replace('/', '+'));
   }

   private boolean loadHeights(int rx, int rz) {
      java.nio.file.Path dir = this.heightsDir;
      if (dir == null) {
         return false;
      }

      java.nio.file.Path f = dir.resolve("r." + rx + "." + rz + ".h8");
      try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(
         new java.util.zip.InflaterInputStream(java.nio.file.Files.newInputStream(f))))) {
         int w = in.readUnsignedShort();
         int off = in.readUnsignedShort();
         for (int gz = 0; gz < w; gz++) {
            for (int gx = 0; gx < w; gx++) {
               int top = in.readShort();
               int floor = in.readShort();
               int bx = rx * 512 + (gx - off) * HEIGHT_STEP;
               int bz = rz * 512 + (gz - off) * HEIGHT_STEP;
               this.heights.putIfAbsent((long)bx << 32 ^ bz & 0xFFFFFFFFL, (long)top << 32 | floor & 0xFFFFFFFFL);
            }
         }

         return true;
      } catch (java.io.IOException | RuntimeException e) {
         return false;
      }
   }

   private void saveHeights(int rx, int rz, Grid g) {
      java.nio.file.Path dir = this.heightsDir;
      if (dir == null || g.hs() != HEIGHT_STEP) {
         return;
      }

      java.nio.file.Path f = dir.resolve("r." + rx + "." + rz + ".h8");
      try {
         java.nio.file.Files.createDirectories(dir);
         java.nio.file.Path tmp = dir.resolve(f.getFileName() + ".tmp");
         try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(
            new java.util.zip.DeflaterOutputStream(java.nio.file.Files.newOutputStream(tmp))))) {
            out.writeShort(g.w());
            out.writeShort(g.off());
            for (int i = 0; i < g.w() * g.w(); i++) {
               out.writeShort((int)g.tops()[i]);
               out.writeShort((int)g.floors()[i]);
            }
         }

         java.nio.file.Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      } catch (java.io.IOException | RuntimeException ignored) {
      }
   }

   private record Grid(float[] tops, float[] floors, int w, int off, int hs) {
      double top(double ox, double oz) {
         return this.blend(this.tops, ox, oz);
      }

      double floor(double ox, double oz) {
         return this.blend(this.floors, ox, oz);
      }

      private double blend(float[] grid, double ox, double oz) {
         double fx = Math.max(0.0, Math.min(this.w - 1, ox / this.hs + this.off));
         double fz = Math.max(0.0, Math.min(this.w - 1, oz / this.hs + this.off));
         int x0 = Math.min(this.w - 2, (int)fx);
         int z0 = Math.min(this.w - 2, (int)fz);
         double tx = fx - x0;
         double tz = fz - z0;
         double a = grid[z0 * this.w + x0] * (1 - tx) + grid[z0 * this.w + x0 + 1] * tx;
         double b = grid[(z0 + 1) * this.w + x0] * (1 - tx) + grid[(z0 + 1) * this.w + x0 + 1] * tx;
         return a * (1 - tz) + b * tz;
      }
   }

   public Terrain terrain(int rx, int rz, int n, int caveY, boolean quick, BooleanSupplier cancelled) {
      return this.area(rx * 512, rz * 512, 512, n, caveY, quick, rx, rz, cancelled);
   }

   public Terrain sheet(int sx, int sz, int regions, int n, int caveY, BooleanSupplier cancelled) {
      return this.area(sx * regions * 512, sz * regions * 512, regions * 512, n * regions, caveY, true, 0, 0, cancelled);
   }

   private static boolean thin(Holder<Biome> b) {
      return b.unwrapKey().map(k -> Ids.path(k).contains("river")).orElse(false);
   }

   private Terrain area(int x0, int z0, int size, int n, int caveY, boolean quick, int rx, int rz, BooleanSupplier cancelled) {
      int step = size / n;
      int apron = SeedSampler.apron(step);
      int ext = apron * step;
      boolean cave = caveY != NO_CAVE;
      boolean sample = this.hasTerrain() && !cave;

      int hs = quick ? Math.max(4, step) : Math.max(HEIGHT_STEP, Math.min(32, step * 4));
      int g = size / hs;
      int gOff = (ext + hs - 1) / hs + 1;
      int gw = g + 1 + 2 * gOff;
      float[] tops = new float[gw * gw];
      float[] floors = new float[gw * gw];

      boolean saved = sample && !quick && this.loadHeights(rx, rz);
      for (int gz = 0; gz < gw; gz++) {
         if (cancelled.getAsBoolean()) {
            return null;
         }

         for (int gx = 0; gx < gw; gx++) {
            int at = gz * gw + gx;
            if (!sample) {
               tops[at] = this.seaLevel;
               floors[at] = this.seaLevel;
               continue;
            }

            long h = quick ? this.estimate(x0 + (gx - gOff) * hs, z0 + (gz - gOff) * hs) : this.height(x0 + (gx - gOff) * hs, z0 + (gz - gOff) * hs);
            tops[at] = (int)(h >> 32);
            floors[at] = (int)h;
         }
      }

      Grid hg = new Grid(tops, floors, gw, gOff, hs);
      if (sample && !saved && !quick && hs == HEIGHT_STEP) {
         this.saveHeights(rx, rz, hg);
      }

      Worker w = this.workers.get();
      int bs = Math.max(4, step);
      int bOff = (ext + bs - 1) / bs + 1;
      int bw = size / bs + 2 * bOff;
      int qx0 = x0 - bOff * bs;
      int qz0 = z0 - bOff * bs;

      @SuppressWarnings("unchecked")
      Holder<Biome>[] grid = new Holder[bw * bw];
      for (int z = 0; z < bw; z++) {
         if (cancelled.getAsBoolean()) {
            return null;
         }

         for (int x = 0; x < bw; x++) {
            int ox = (x - bOff) * bs + bs / 2;
            int oz = (z - bOff) * bs + bs / 2;
            int y = cave ? caveY : this.seaLevel;
            if (sample) {
               y = (int)Math.floor(Math.max(hg.floor(ox, oz), Math.min(hg.top(ox, oz), this.seaLevel)));
            }

            grid[z * bw + x] = w.resolver.getNoiseBiome(QuartPos.fromBlock(qx0 + x * bs + bs / 2), QuartPos.fromBlock(y), QuartPos.fromBlock(qz0 + z * bs + bs / 2));
         }
      }

      if (quick && sample && bs >= 32) {
         Map<Holder<Biome>, Boolean> thins = new java.util.IdentityHashMap<>();
         @SuppressWarnings("unchecked")
         Holder<Biome>[] from = grid.clone();
         @SuppressWarnings("unchecked")
         Holder<Biome>[] around = new Holder[8];
         for (int z = 1; z < bw - 1; z++) {
            for (int x = 1; x < bw - 1; x++) {
               Holder<Biome> b = from[z * bw + x];
               int like = 0;
               int k = 0;
               for (int dz = -1; dz <= 1; dz++) {
                  for (int dx = -1; dx <= 1; dx++) {
                     if ((dx | dz) != 0) {
                        Holder<Biome> o = from[(z + dz) * bw + x + dx];
                        around[k++] = o;
                        if (o == b) {
                           like++;
                        }
                     }
                  }
               }

               if (like >= 2 || like == 1 && !thins.computeIfAbsent(b, SeedWorld::thin)) {
                  continue;
               }

               Holder<Biome> best = b;
               int bestN = 0;
               for (int i = 0; i < 8; i++) {
                  int count = 0;
                  for (int j = 0; j < 8; j++) {
                     if (around[j] == around[i]) {
                        count++;
                     }
                  }

                  if (count > bestN) {
                     best = around[i];
                     bestN = count;
                  }
               }

               grid[z * bw + x] = best;
            }
         }
      }

      int side = size / bs + 2;
      short[] ids = new short[side * side];
      for (int z = 0; z < side; z++) {
         for (int x = 0; x < side; x++) {
            ids[z * side + x] = (short)this.biomes.getId(grid[(z + bOff - 1) * bw + x + bOff - 1].value());
         }
      }

      BiomeManager blended = null;
      if (step <= 2) {
         int q0x = QuartPos.fromBlock(qx0);
         int q0z = QuartPos.fromBlock(qz0);
         BiomeResolver fromGrid = (qx, qy, qz) -> {
            int gx = Math.max(0, Math.min(bw - 1, qx - q0x));
            int gz = Math.max(0, Math.min(bw - 1, qz - q0z));
            return grid[gz * bw + gx];
         };
         blended = new BiomeManager(fromGrid, BiomeManager.obfuscateSeed(this.seed));
      }

      int[] argb = new int[n * n];
      int dside = n + 2 * apron;
      dev.chunkatlas.engine.Surface detail = new dev.chunkatlas.engine.Surface(dside);
      double scale = 4.0 / (step + 4.0);
      Map<Holder<Biome>, int[]> looks = new java.util.IdentityHashMap<>();
      Map<Holder<Biome>, long[]> details = new java.util.IdentityHashMap<>();
      Map<Holder<Biome>, long[]> caves = new java.util.IdentityHashMap<>();
      Map<Holder<Biome>, long[]> cavesDeep = new java.util.IdentityHashMap<>();

      boolean caveSampled = cave && w.density != null && step <= 16;
      for (int z = -apron; z < n + apron; z++) {
         if (cancelled.getAsBoolean()) {
            return null;
         }

         int oz = z * step + step / 2;
         boolean innerZ = z >= 0 && z < n;
         for (int x = -apron; x < n + apron; x++) {
            int ox = x * step + step / 2;
            boolean inner = innerZ && x >= 0 && x < n;
            double top = hg.top(ox, oz);
            double floor = hg.floor(ox, oz);
            Holder<Biome> biome = blended != null
               ? blended.getBiome(x0 + ox, (int)floor, z0 + oz)
               : grid[(Math.floorDiv(oz, bs) + bOff) * bw + Math.floorDiv(ox, bs) + bOff];
            long[] dl = details.computeIfAbsent(biome, this::detailLook);
            int at = (z + apron) * dside + x + apron;
            detail.biome[at] = (short)dl[3];
            if (cave) {
               if (caveSampled) {
                  int wx = x0 + ox;
                  int wz = z0 + oz;
                  int y = caveY;
                  boolean solid = this.solidAt(w, wx, y, wz);
                  for (int k = 0; solid && k < CAVE_ROOF / CAVE_STEP; k++) {
                     y -= CAVE_STEP;
                     solid = this.solidAt(w, wx, y, wz);
                  }

                  if (solid) {
                     continue;
                  }

                  int stop = Math.max(this.minY + 1, y - CAVE_DROP);
                  while (y > stop && !this.solidAt(w, wx, y - CAVE_STEP, wz)) {
                     y -= CAVE_STEP;
                  }

                  if (y <= stop) {
                     continue;
                  }

                  int floorY = y - CAVE_STEP;
                  long[] ground = (floorY < 0 ? cavesDeep : caves).computeIfAbsent(biome, b -> this.caveLook(b, floorY));
                  detail.top[at] = dev.chunkatlas.engine.Surface.top(ground[0]);
                  detail.height[at] = (short)floorY;
                  if (inner) {
                     int drop = caveY - floorY;
                     argb[z * n + x] = shade((int)ground[1], drop <= 8 ? 2 : drop <= 24 ? 1 : 0);
                  }

                  continue;
               }

               long[] rock = caves.computeIfAbsent(biome, b -> this.caveLook(b, caveY));
               detail.top[at] = dev.chunkatlas.engine.Surface.top(rock[0]);
               detail.height[at] = (short)caveY;
               if (inner) {
                  int c = shade((int)rock[1], (x + z & 1) == 1 ? 1 : 0);
                  argb[z * n + x] = c == 0 ? 0 : 0xFF000000 | (c >> 16 & 0xFF) * 2 / 5 << 16 | (c >> 8 & 0xFF) * 2 / 5 << 8 | (c & 0xFF) * 2 / 5;
               }

               continue;
            }
            if (this.flatColour >= 0) {
               if (inner) {
                  argb[z * n + x] = shade(this.flatColour, 1);
               }

               detail.top[at] = dev.chunkatlas.engine.Surface.top(this.flatLook);
               continue;
            }

            int[] look = looks.computeIfAbsent(biome, this::look);
            if (sample && top <= this.minY + 1) {
               continue;
            }

            if (sample && (int)Math.round(top - floor) > 0 && top <= this.seaLevel + 0.5) {
               detail.top[at] = dev.chunkatlas.engine.Surface.top(dl[1]);
               detail.height[at] = (short)Math.floor(top);
               detail.depth[at] = (byte)Math.min(254, (int)Math.round(top - floor));
               if (dl[2] != 0L) {
                  detail.cover[at] = dev.chunkatlas.engine.Surface.cover(dl[2], true);
               }
            } else {
               detail.top[at] = dev.chunkatlas.engine.Surface.top(dl[0]);
               detail.height[at] = (short)(sample ? Math.floor(top) : this.seaLevel);
            }

            if (!inner) {
               continue;
            }

            boolean odd = (x + z & 1) == 1;
            int depth = (int)Math.round(top - floor);
            int level;
            int colour;

            if (sample && depth > 0 && top <= this.seaLevel + 0.5) {
               colour = look[1];
               double d = depth * 0.1 + (odd ? 0.2 : 0.0);
               level = d < 0.5 ? 2 : d > 0.9 ? 0 : 1;
            } else if (!sample) {
               colour = look[0];
               level = 1;
            } else {
               colour = look[0];
               int north = (int)Math.floor(hg.top(ox, oz - step));
               double d = ((int)Math.floor(top) - north) * scale + ((odd ? 1 : 0) - 0.5) * 0.4;
               level = d > 0.6 ? 2 : d < -0.6 ? 0 : 1;
            }

            argb[z * n + x] = shade(colour, level);
         }
      }

      return new Terrain(argb, ids, side, bs, cave && !caveSampled ? null : detail, step);
   }

   private long[] detailLook(Holder<Biome> biome) {
      String name = biome.unwrapKey().map(k -> Ids.of(k)).orElse("");
      String path = name.substring(name.indexOf(':') + 1);
      String ground = SeedKinds.topBlock(path);
      boolean frozen = path.startsWith("frozen") || path.equals("deep_frozen_ocean");
      boolean warm = path.contains("warm") || path.equals("beach") || path.equals("desert");
      long top = this.colours.look(ground.equals("minecraft:water") ? (warm ? "minecraft:sand" : "minecraft:gravel") : ground);
      long bed = this.colours.look(warm || !ground.equals("minecraft:water") && ground.contains("sand") ? "minecraft:sand" : "minecraft:gravel");
      long ice = frozen ? this.colours.look("minecraft:ice") : 0L;
      return new long[]{top, bed, ice, name.isEmpty() ? -1 : dev.chunkatlas.engine.BiomeIndex.of(name)};
   }

   public int biomeColour(int id) {
      Biome b = this.biomes.byId(id);
      Identifier key = b == null ? null : this.biomes.getKey(b);
      if (key == null) {
         return 0;
      }

      boolean wet = SeedKinds.topBlock(key.getPath()).equals("minecraft:water");
      return this.look(this.biomes.wrapAsHolder(b))[wet ? 1 : 0];
   }

   private int[] look(Holder<Biome> biome) {
      String path = biome.unwrapKey().map(k -> Ids.path(k)).orElse("");
      boolean frozen = path.startsWith("frozen") || path.equals("deep_frozen_ocean");
      return new int[]{this.colours.colour(SeedKinds.topBlock(path)), this.colours.colour(frozen ? "minecraft:ice" : "minecraft:water")};
   }

   private static int shade(int colour, int level) {
      if ((colour >>> 24) == 0) {
         return 0;
      }

      int f = level == 2 ? 255 : level == 1 ? 220 : 180;
      return 0xFF000000 | (colour >> 16 & 0xFF) * f / 255 << 16 | (colour >> 8 & 0xFF) * f / 255 << 8 | (colour & 0xFF) * f / 255;
   }

   private void indexFeatures() {
      if (this.featureIndexed) {
         return;
      }

      synchronized (this.featureList) {
         if (this.featureIndexed) {
            return;
         }

         List<FeatureSorter.StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(
            List.copyOf(this.source.possibleBiomes()), b -> this.generator.getBiomeGenerationSettings(b).features(), true
         );
         for (FeatureInfo f : this.featureList) {
            for (int step = 0; step < steps.size(); step++) {
               int index = steps.get(step).features().indexOf(f.feature());
               if (index >= 0) {
                  f.step = step;
                  f.index = index;
                  break;
               }
            }
         }

         this.featureIndexed = true;
      }
   }

   public String featureState() {
      this.indexFeatures();
      StringBuilder b = new StringBuilder();
      for (FeatureInfo f : this.featureList) {
         b.append(f.id()).append(" step ").append(f.step).append(" index ").append(f.index).append("; ");
      }

      return b.append("last error ").append(this.lastFeatureError).toString();
   }

   @Override
   public String tourCheck(int px, int pz, int water) {
      if (this.level == null) {
         return "no game to check against";
      }

      ServerLevel level = this.level;
      SeedWorld w = this;
      int same = 0;
      int surface = 0;
      int wetSame = 0;
      int wetReal = 0;
      int wetMine = 0;
      int wetAll = 0;
      Map<Long, Terrain> pictures = new java.util.HashMap<>();
      StringBuilder samples = new StringBuilder();
      int all = 0;
      for (int dz = -12; dz <= 12; dz += 4) {
         for (int dx = -12; dx <= 12; dx += 4) {
            int cx = (px >> 4) + dx;
            int cz = (pz >> 4) + dz;
            net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
            if (chunk == null) {
               continue;
            }

            int x = cx * 16 + 8;
            int z = cz * 16 + 8;
            int y = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            int real = w.biomeNumber(chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2));
            if (all < 4) {
               var vanilla = level.getChunkSource().getGenerator().getBiomeSource().createUncachedResolver(level.getChunkSource().randomState()).getNoiseBiome(x >> 2, y >> 2, z >> 2);
               samples.append(" [").append(x).append(',').append(y).append(',').append(z).append(" real ").append(w.biomeId(real))
                  .append(" mine ").append(w.biomeId(w.biomeAt(x, y, z))).append(" vanilla ").append(vanilla.getRegisteredName()).append(']');
            }

            all++;
            if (w.biomeAt(x, y, z) == real) {
               same++;
            }

            if (w.biomeAt(x, z) == real) {
               surface++;
            }

            for (int k = 0; k < 4; k++) {
               int sx = cx * 16 + 2 + (k & 1) * 8;
               int sz = cz * 16 + 2 + (k >> 1) * 8;
               int top = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, sx & 15, sz & 15);
               boolean realWet = chunk.getFluidState(new net.minecraft.core.BlockPos(sx, top, sz)).is(net.minecraft.tags.FluidTags.WATER);
               int rx = Math.floorDiv(sx, 512);
               int rz = Math.floorDiv(sz, 512);
               Terrain t = pictures.computeIfAbsent((long)rx << 32 | rz & 0xFFFFFFFFL, k2 -> w.terrain(rx, rz, 128, () -> false));
               int pixel = t.argb()[(Math.floorMod(sz, 512) / 4) * 128 + Math.floorMod(sx, 512) / 4];
               boolean mineWet = water != 0 && isShadeOf(pixel, water);
               wetAll++;
               wetReal += realWet ? 1 : 0;
               wetMine += mineWet ? 1 : 0;
               wetSame += realWet == mineWet ? 1 : 0;
            }
         }
      }

      String villages = "";
      try {
         var tag = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getOrThrow(net.minecraft.tags.StructureTags.VILLAGE);
         var game = level.getChunkSource().getGenerator().findNearestMapStructure(level, tag, new net.minecraft.core.BlockPos(px, 64, pz), 100, false);
         Found mine = w.nearest("minecraft:village", px, pz, 30000, () -> false);
         villages = ", nearest village: game " + (game == null ? "none" : game.getFirst().getX() + "," + game.getFirst().getZ())
            + ", seed map " + (mine == null ? "none" : mine.x() + "," + mine.z());
      } catch (RuntimeException e) {
         villages = ", village check failed: " + e;
      }

      return "biome check in " + all + " newly generated chunks: " + same + " match at the real height, " + surface + " at the estimated surface"
         + "; water at " + wetAll + " spots: real " + wetReal + ", seed map " + wetMine + ", agree " + wetSame
         + villages + samples + "; features: " + w.featureState()
         + "; in this region: " + w.features(Math.floorDiv(px, 512), Math.floorDiv(pz, 512), () -> false).size();
   }

   private static boolean isShadeOf(int pixel, int colour) {
      for (int f : new int[]{180, 220, 255}) {
         int c = (colour >> 16 & 0xFF) * f / 255 << 16 | (colour >> 8 & 0xFF) * f / 255 << 8 | (colour & 0xFF) * f / 255;
         if ((pixel & 0xFFFFFF) == c) {
            return true;
         }
      }

      return false;
   }

   public List<Found> features(int rx, int rz, BooleanSupplier cancelled) {
      List<Found> out = new ArrayList<>();
      if (this.featureList.isEmpty()) {
         return out;
      }

      this.indexFeatures();
      WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
      for (int cz = rz * 32; cz < rz * 32 + 32; cz++) {
         if (cancelled.getAsBoolean()) {
            return null;
         }

         for (int cx = rx * 32; cx < rx * 32 + 32; cx++) {
            BlockPos origin = new BlockPos(cx * 16, this.minY, cz * 16);
            long decoration = random.setDecorationSeed(this.seed, origin.getX(), origin.getZ());
            for (FeatureInfo f : this.featureList) {
               if (f.step < 0) {
                  continue;
               }

               random.setFeatureSeed(decoration, f.index, f.step);
               try {
                  for (BlockPos at : this.place(f.feature(), random, origin)) {
                     out.add(new Found(f.kind(), f.id(), at.getX(), at.getZ(), false));
                  }
               } catch (RuntimeException e) {
                  this.lastFeatureError = f.id() + ": " + e;
               }
            }
         }
      }

      return out;
   }

   private List<BlockPos> place(PlacedFeature feature, RandomSource random, BlockPos origin) {
      PlacementContext context = new PlacementContext(this.fakeLevel, this.generator, Optional.of(feature));
      List<PlacementModifier> modifiers = feature.placement();
      List<BlockPos> out = new ArrayList<>();
      List<BlockPos> positions = new ArrayList<>();
      List<Integer> indices = new ArrayList<>();
      List<BlockPos> next = new ArrayList<>();
      positions.add(origin);
      indices.add(0);
      while (!positions.isEmpty()) {
         BlockPos pos = positions.removeLast();
         int index = indices.removeLast();
         next.clear();
         PlacementModifier modifier = modifiers.get(index);
         try {
            modifier.modify(context, random, pos, next::add);
         } catch (UnsupportedOperationException e) {
            if (!(modifier instanceof PlacementFilter)) {
               throw e;
            }

            next.clear();
            next.add(pos);
         }
         if (index + 1 < modifiers.size()) {
            for (int i = next.size() - 1; i >= 0; i--) {
               positions.add(next.get(i));
               indices.add(index + 1);
            }
         } else {
            out.addAll(next);
         }
      }

      return out;
   }

   int placingHeight(Heightmap.Types type, int x, int z) {
      Heightmap.Types wg = type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG
         ? Heightmap.Types.OCEAN_FLOOR_WG
         : Heightmap.Types.WORLD_SURFACE_WG;
      net.minecraft.world.level.chunk.ChunkAccess chunk = this.placing.get();
      if (chunk != null && x >> 4 == Pos.cx(chunk.getPos()) && z >> 4 == Pos.cz(chunk.getPos())) {
         return chunk.getHeight(wg, x & 15, z & 15);
      }

      return this.generator.getBaseHeight(x, z, wg, this.height, this.random);
   }

   Holder<Biome> placingBiome(BlockPos pos) {
      return this.workers.get().resolver.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
   }

   BlockState placingBlock(BlockPos pos) {
      net.minecraft.world.level.chunk.ChunkAccess chunk = this.placing.get();
      return chunk != null && pos.getX() >> 4 == Pos.cx(chunk.getPos()) && pos.getZ() >> 4 == Pos.cz(chunk.getPos())
         ? chunk.getBlockState(pos)
         : this.guessBlock(pos);
   }

   private BlockState guessBlock(BlockPos pos) {
      int top = this.generator.getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, this.height, this.random);
      if (pos.getY() >= top) {
         return pos.getY() < this.seaLevel ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
      } else if (pos.getY() == top - 1) {
         String biome = this.biomeId(this.biomeAt(pos.getX(), pos.getY(), pos.getZ()));
         Block b = Reg.block(SeedKinds.topBlock(biome == null ? "" : IdText.path(biome)));
         return b == null || b == Blocks.AIR ? Blocks.STONE.defaultBlockState() : b.defaultBlockState();
      } else {
         return Blocks.STONE.defaultBlockState();
      }
   }

   private static final class FeatureInfo {
      final String kind;
      final String id;
      final PlacedFeature feature;
      int step = -1;
      int index = -1;

      FeatureInfo(String kind, String id, PlacedFeature feature) {
         this.kind = kind;
         this.id = id;
         this.feature = feature;
      }

      String kind() {
         return this.kind;
      }

      String id() {
         return this.id;
      }

      PlacedFeature feature() {
         return this.feature;
      }
   }

   public List<Integer> possibleBiomes() {
      List<Integer> out = new ArrayList<>();
      for (Holder<Biome> b : this.source.possibleBiomes()) {
         out.add(this.biomes.getId(b.value()));
      }

      out.sort(Comparator.comparing(i -> String.valueOf(this.biomeId(i))));
      return out;
   }

   public int[] nearestBiome(int biome, int blockX, int blockZ, int maxBlocks) {
      Biome wanted = this.biomes.byId(biome);
      String id = this.biomeId(biome);
      String path = id == null ? "" : IdText.path(id);
      int y = path.equals("deep_dark") ? -40 : path.endsWith("_caves") ? 0 : this.seaLevel;
      var found = this.source.findBiomeHorizontal(blockX, y, blockZ, maxBlocks, 8, b -> b.value() == wanted, new LegacyRandomSource(0L), true, this.random);
      return found == null ? null : new int[]{found.getFirst().getX(), found.getFirst().getZ()};
   }

   public boolean slimeChunk(int chunkX, int chunkZ) {
      return this.slime && WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, this.seed, 987234911L).nextInt(10) == 0;
   }

   public List<Found> structures(int rx, int rz, boolean full, BooleanSupplier cancelled) {
      List<Found> out = new ArrayList<>();
      int cx0 = rx * 32;
      int cz0 = rz * 32;
      int cx1 = cx0 + 31;
      int cz1 = cz0 + 31;
      for (SetInfo set : this.sets) {
         if (cancelled.getAsBoolean()) {
            return null;
         }

         try {
            if (set.placement() instanceof RandomSpreadStructurePlacement spread) {
               int s = spread.spacing();
               for (int gz = Math.floorDiv(cz0, s); gz <= Math.floorDiv(cz1, s); gz++) {
                  for (int gx = Math.floorDiv(cx0, s); gx <= Math.floorDiv(cx1, s); gx++) {
                     ChunkPos c = spread.getPotentialStructureChunk(this.seed, gx * s, gz * s);
                     if (Pos.cx(c) >= cx0 && Pos.cx(c) <= cx1 && Pos.cz(c) >= cz0 && Pos.cz(c) <= cz1 && spread.isStructureChunk(this.state, Pos.cx(c), Pos.cz(c))) {
                        Found f = this.pick(set, Pos.cx(c), Pos.cz(c), full);
                        if (f != null) {
                           out.add(f);
                        }
                     }
                  }
               }
            } else if (set.placement() instanceof ConcentricRingsStructurePlacement) {
            } else if (set.placement() instanceof DimensionOriginStructurePlacement) {
               ChunkPos o = this.state.getDimensionOrigin();
               if (Pos.cx(o) >= cx0 && Pos.cx(o) <= cx1 && Pos.cz(o) >= cz0 && Pos.cz(o) <= cz1) {
                  Found f = this.pick(set, Pos.cx(o), Pos.cz(o), full);
                  if (f != null) {
                     out.add(f);
                  }
               }
            } else {
               for (int cz = cz0; cz <= cz1; cz++) {
                  for (int cx = cx0; cx <= cx1; cx++) {
                     if (set.placement().isStructureChunk(this.state, cx, cz)) {
                        Found f = this.pick(set, cx, cz, full);
                        if (f != null) {
                           out.add(f);
                        }
                     }
                  }
               }
            }
         } catch (RuntimeException ignored) {
         }
      }

      List<Found> features = this.features(rx, rz, cancelled);
      if (features == null) {
         return null;
      }

      out.addAll(features);
      return out;
   }

   public List<Found> rings() {
      List<Found> known = this.rings;
      if (known != null) {
         return known;
      }

      List<Found> out = new ArrayList<>();
      for (SetInfo set : this.sets) {
         if (set.placement() instanceof ConcentricRingsStructurePlacement rings) {
            try {
               List<ChunkPos> at = this.state.getRingPositionsFor(rings);
               if (at != null) {
                  for (ChunkPos c : at) {
                     Found f = this.pick(set, Pos.cx(c), Pos.cz(c), false);
                     if (f != null) {
                        out.add(new Found(f.kind(), f.structure(), f.x(), f.z(), true));
                     }
                  }
               }
            } catch (RuntimeException ignored) {
            }
         }
      }

      this.rings = List.copyOf(out);
      return this.rings;
   }

   public Found nearest(String kind, int blockX, int blockZ, int maxBlocks, BooleanSupplier cancelled) {
      if (this.featureList.stream().anyMatch(f -> f.kind().equals(kind))) {
         return this.nearestFeature(kind, blockX, blockZ, Math.min(maxBlocks, 4096), cancelled);
      }

      Found best = null;
      double bestD = Double.MAX_VALUE;
      for (Found f : this.rings()) {
         double d = dist(f, blockX, blockZ);
         if (f.kind().equals(kind) && d < bestD) {
            best = f;
            bestD = d;
         }
      }

      int chunkX = Math.floorDiv(blockX, 16);
      int chunkZ = Math.floorDiv(blockZ, 16);
      for (SetInfo set : this.sets) {
         if (!(set.placement() instanceof RandomSpreadStructurePlacement spread) || set.entries().stream().noneMatch(e -> e.possible() && e.kind().equals(kind))) {
            if (set.placement() instanceof DimensionOriginStructurePlacement && set.entries().stream().anyMatch(e -> e.kind().equals(kind))) {
               ChunkPos o = this.state.getDimensionOrigin();
               Found f = this.pick(set, Pos.cx(o), Pos.cz(o), true);
               if (f != null && f.kind().equals(kind) && dist(f, blockX, blockZ) < bestD) {
                  best = f;
                  bestD = dist(f, blockX, blockZ);
               }
            }

            continue;
         }

         int s = spread.spacing();
         int gx0 = Math.floorDiv(chunkX, s);
         int gz0 = Math.floorDiv(chunkZ, s);
         int most = Math.max(1, maxBlocks / (s * 16));
         for (int r = 0; r <= most; r++) {
            if (best != null && (r - 1) * s * 16.0 > Math.sqrt(bestD)) {
               break;
            }

            if (cancelled.getAsBoolean()) {
               return null;
            }

            for (int dz = -r; dz <= r; dz++) {
               for (int dx = -r; dx <= r; dx++) {
                  if (Math.abs(dx) != r && Math.abs(dz) != r) {
                     continue;
                  }

                  try {
                     ChunkPos c = spread.getPotentialStructureChunk(this.seed, (gx0 + dx) * s, (gz0 + dz) * s);
                     if (spread.isStructureChunk(this.state, Pos.cx(c), Pos.cz(c))) {
                        Found f = this.pick(set, Pos.cx(c), Pos.cz(c), true);
                        if (f != null && f.kind().equals(kind)) {
                           double d = dist(f, blockX, blockZ);
                           if (d < bestD) {
                              best = f;
                              bestD = d;
                           }
                        }
                     }
                  } catch (RuntimeException ignored) {
                  }
               }
            }
         }
      }

      return best;
   }

   private Found nearestFeature(String kind, int blockX, int blockZ, int maxBlocks, BooleanSupplier cancelled) {
      int rx0 = Math.floorDiv(blockX, 512);
      int rz0 = Math.floorDiv(blockZ, 512);
      Found best = null;
      double bestD = Double.MAX_VALUE;
      for (int r = 0; r <= maxBlocks / 512 + 1; r++) {
         if (best != null && (r - 1) * 512.0 > Math.sqrt(bestD)) {
            break;
         }

         for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
               if (Math.abs(dx) != r && Math.abs(dz) != r) {
                  continue;
               }

               List<Found> here = this.features(rx0 + dx, rz0 + dz, cancelled);
               if (here == null) {
                  return null;
               }

               for (Found f : here) {
                  double d = dist(f, blockX, blockZ);
                  if (f.kind().equals(kind) && d < bestD) {
                     best = f;
                     bestD = d;
                  }
               }
            }
         }
      }

      return best;
   }

   private static double dist(Found f, int x, int z) {
      double dx = f.x() - x;
      double dz = f.z() - z;
      return dx * dx + dz * dz;
   }

   private Found pick(SetInfo set, int cx, int cz, boolean full) {
      List<Entry> entries = set.entries();
      if (entries.size() == 1) {
         return this.test(entries.get(0), cx, cz, full);
      }

      List<Entry> options = new ArrayList<>(entries);
      WorldgenRandom r = new WorldgenRandom(new LegacyRandomSource(0L));
      r.setLargeFeatureSeed(this.seed, cx, cz);
      int total = 0;
      for (Entry e : options) {
         total += e.weight();
      }

      while (!options.isEmpty() && total > 0) {
         int choice = r.nextInt(total);
         int index = 0;
         for (Entry e : options) {
            choice -= e.weight();
            if (choice < 0) {
               break;
            }

            index++;
         }

         index = Math.min(index, options.size() - 1);
         Entry selected = options.get(index);
         Found f = this.test(selected, cx, cz, full);
         if (f != null) {
            return f;
         }

         options.remove(index);
         total -= selected.weight();
      }

      return null;
   }

   private Found test(Entry e, int cx, int cz, boolean full) {
      if (!e.possible()) {
         return null;
      }

      Worker w = this.workers.get();
      Structure structure = e.structure().value();
      HolderSet<Biome> allowed = structure.biomes();
      ChunkPos pos = new ChunkPos(cx, cz);
      if (full) {
         try {
            Structure.GenerationContext context = new Structure.GenerationContext(
               this.access instanceof RegistryAccess r ? r : RegistryAccess.EMPTY, this.generator, this.source, w.sampler, this.random, this.templates, this.seed, pos,
               this.height, allowed::contains
            );
            Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(context);
            return stub.map(s -> new Found(e.kind(), e.id(), s.position().getX(), s.position().getZ(), true)).orElse(null);
         } catch (RuntimeException ignored) {
         }
      }

      int qx = QuartPos.fromBlock(pos.getMiddleBlockX());
      int qz = QuartPos.fromBlock(pos.getMiddleBlockZ());
      for (int qy = QuartPos.fromBlock(this.minY); qy <= QuartPos.fromBlock(this.maxY - 1); qy += 2) {
         if (allowed.contains(w.resolver.getNoiseBiome(qx, qy, qz))) {
            return new Found(e.kind(), e.id(), pos.getMiddleBlockX(), pos.getMiddleBlockZ(), false);
         }
      }

      return null;
   }

   private record Entry(Holder<Structure> structure, String id, String kind, int weight, boolean possible) {
   }

   private record SetInfo(StructurePlacement placement, List<Entry> entries, int spacing) {
   }

   private final class Worker {
      final Climate.Sampler sampler;
      final BiomeResolver resolver;
      final DensitySampler.Bound surface;
      final DensitySampler.Bound depth;

      final double depthSlope;
      final DensitySampler.Bound density;

      Worker() {
         SamplerContext context = SamplerContext.builder().enableCaches().build();
         this.sampler = SeedWorld.this.random.createClimateSampler(context);
         this.resolver = SeedWorld.this.source.createResolver(this.sampler);
         this.surface = SeedWorld.this.surface == null ? null : SeedWorld.this.random.samplersWithContext(context).get(SeedWorld.this.surface);
         DensitySampler.Bound depth = null;
         double slope = 0.0;
         if (SeedWorld.this.depth != null) {
            try {
               depth = SeedWorld.this.random.samplersWithContext(context).get(SeedWorld.this.depth);
               slope = (depth.sampleValue(0, 0, 0) - depth.sampleValue(0, 128, 0)) / 128.0;
            } catch (RuntimeException e) {
               slope = 0.0;
            }
         }

         DensitySampler.Bound density = null;
         if (SeedWorld.this.density != null) {
            try {
               density = SeedWorld.this.random.samplersWithContext(context).get(SeedWorld.this.density);
               density.sampleValue(0, 0, 0);
            } catch (RuntimeException e) {
               density = null;
            }
         }

         this.density = density;
         this.depth = depth;
         this.depthSlope = slope > 1.0E-4 ? slope : 0.0;
      }
   }
}
