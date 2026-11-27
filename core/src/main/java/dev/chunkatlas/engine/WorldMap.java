package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public final class WorldMap implements AutoCloseable {
   public static final int FACTS_TO_LEVEL = 3;
   private static volatile int workerLimit;
   private static volatile long cacheBudget = 384L << 20;

   public static void limits(int workers, long cacheBytes) {
      workerLimit = Math.max(0, Math.min(16, workers));
      cacheBudget = Math.max(64L << 20, cacheBytes);
   }

   public static long cacheBytes(Path home) {
      return TileCache.size(home);
   }

   public static long cacheBytes(Path home, Path world) {
      return TileCache.size(home, realOf(world));
   }

   public static void clearCache(Path home, Path world, Path open) {
      TileCache.deleteFiles(home, world == null ? null : realOf(world), open == null ? null : realOf(open));
   }

   public void dropCache() {
      this.cache.clear();
   }

   private static Path realOf(Path world) {
      try {
         return world.toRealPath();
      } catch (IOException e) {
         return world.toAbsolutePath().normalize();
      }
   }

   private final Path world;
   private final Path home;
   private final BlockColours colours;
   private final WorldLayout.Layout layout;
   private final LevelMarkers.Markers markers;
   private final TileCache cache;
   private final Object lock = new Object();
   private final ArrayDeque<WorldMap.Job> queue = new ArrayDeque<>();
   private final Map<WorldMap.Key, WorldMap.Job> flying = new HashMap<>();
   private final ConcurrentLinkedQueue<Tile> ready = new ConcurrentLinkedQueue<>();
   private final List<Thread> workers = new ArrayList<>();
   private final AtomicBoolean pruned = new AtomicBoolean();
   private volatile boolean closed;

   private WorldMap(Path world, Path home, BlockColours colours, WorldLayout.Layout layout, LevelMarkers.Markers markers, Path real) {
      this.world = world;
      this.home = home;
      this.colours = colours;
      this.layout = layout;
      this.markers = markers;
      this.cache = new TileCache(home, real);
   }

   public static WorldMap open(Path world, Path home, BlockColours colours) {
      if (LiveRegion.isLiveMap(world)) {
         return new WorldMap(world, home, colours, LiveRegion.layout(world), LevelMarkers.Markers.NONE, world.toAbsolutePath().normalize());
      }

      WorldLayout.Layout layout = WorldLayout.read(world);
      boolean there = Files.isDirectory(layout.world())
         && layout.note() != WorldLayout.Note.AWAY;
      LevelMarkers.Markers markers = there ? LevelMarkers.read(layout.world()) : LevelMarkers.Markers.NONE;
      GameVersions.learn(markers.versionName(), markers.dataVersion());

      Path real;
      try {
         real = world.toRealPath();
      } catch (IOException e) {
         real = world.toAbsolutePath().normalize();
      }

      return new WorldMap(world, home, colours, layout, markers, real);
   }

   public Path world() {
      return this.world;
   }

   public WorldLayout.Layout layout() {
      return this.layout;
   }

   public boolean fromLiveMap() {
      Boolean known = this.liveMap;
      if (known == null) {
         known = LiveRegion.isLiveMap(this.world);
         this.liveMap = known;
      }

      return known;
   }

   private volatile Boolean liveMap;
   private volatile int caveY = Integer.MIN_VALUE;
   private volatile int style;

   public void style(int bits) {
      synchronized (this.lock) {
         this.style = bits;
         this.queue.clear();
         for (WorldMap.Job job : this.flying.values()) {
            job.cancelled = true;
         }
      }

      this.ready.clear();
   }

   public int style() {
      return this.style;
   }

   public void cave(int y) {
      synchronized (this.lock) {
         this.caveY = y;
         this.queue.clear();
         for (WorldMap.Job job : this.flying.values()) {
            job.cancelled = true;
         }
      }

      this.ready.clear();
   }

   public int cave() {
      return this.caveY;
   }

   private static int roof(String dimension) {
      return "minecraft:the_nether".equals(dimension) ? 40 : 8;
   }

   public LevelMarkers.Markers markers() {
      return this.markers;
   }

   public WorldMap.Index index(String dimensionId) {
      WorldLayout.Dimension d = this.layout.dimension(dimensionId).orElse(null);
      if (d == null) {
         return null;
      }

      boolean live = this.fromLiveMap();
      Map<Long, WorldLayout.RegionInfo> regions = new java.util.LinkedHashMap<>();
      if (live) {
         for (WorldLayout.RegionInfo r : LiveRegion.list(d.region())) {
            regions.put(WorldLayout.key(r.rx(), r.rz()), r);
         }
      } else {
         regions = WorldLayout.regions(d);
      }

      Map<Long, WorldLayout.RegionInfo> entities = new HashMap<>();
      if (d.entities().isPresent()) {
         for (WorldLayout.RegionInfo r : WorldLayout.list(d.entities().get())) {
            entities.put(WorldLayout.key(r.rx(), r.rz()), r);
         }
      }

      Map<Long, long[]> present = new HashMap<>();
      int chunks = 0;
      int away = 0;
      int minX = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (Entry<Long, WorldLayout.RegionInfo> e : regions.entrySet()) {
         WorldLayout.RegionInfo r = e.getValue();
         minX = Math.min(minX, r.rx());
         maxX = Math.max(maxX, r.rx());
         minZ = Math.min(minZ, r.rz());
         maxZ = Math.max(maxZ, r.rz());
         if (live) {
            try {
               long[] bits = LiveRegion.known(r.file());
               int n = 0;
               for (long b : bits) {
                  n += Long.bitCount(b);
               }

               if (n > 0) {
                  present.put(e.getKey(), bits);
                  chunks += n;
               }
            } catch (IOException | RuntimeException ignored) {
            }
         } else if (r.away()) {
            away++;
         } else if (r.size() >= 8192L) {
            try {
               RegionFile.Header h = RegionFile.header(r.file());
               long[] bits = new long[16];
               int n = 0;

               for (int i = 0; i < 1024; i++) {
                  if (h.present(i)) {
                     bits[i >> 6] = bits[i >> 6] | 1L << (i & 63);
                     n++;
                  }
               }

               if (n > 0) {
                  present.put(e.getKey(), bits);
                  chunks += n;
               }
            } catch (IOException | RuntimeException ignored) {
            }
         }
      }

      if (regions.isEmpty()) {
         maxZ = 0;
         maxX = 0;
         minZ = 0;
         minX = 0;
      }

      return new WorldMap.Index(d, Map.copyOf(regions), present, chunks, minX, minZ, maxX, maxZ, Map.copyOf(entities), away);
   }

   static int workers() {
      return workerLimit > 0 ? workerLimit : autoWorkers();
   }

   public static int autoWorkers() {
      long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
      int byMemory = (int)Math.max(1L, heapMb / 384L);
      return Math.max(1, Math.min(Background.threads(8), byMemory));
   }

   public void request(WorldMap.Index index, int level, List<Long> nearestFirst) {
      int lv = Math.max(0, Math.min(4, level));
      synchronized (this.lock) {
         if (!this.closed) {
            Set<WorldMap.Key> wanted = new HashSet<>();
            this.queue.clear();

            for (Long region : nearestFirst) {
               WorldLayout.RegionInfo info = index.regions().get(region);
               if (info != null) {
                  WorldMap.Key key = new WorldMap.Key(index.dimension().id(), region, lv, this.caveY, this.style);
                  WorldMap.Job flying = this.flying.get(key);
                  if (wanted.add(key) && (flying == null || flying.cancelled)) {
                     this.queue.add(new WorldMap.Job(key, index.dimension(), info, index.entities().get(region)));
                  }
               }
            }

            for (WorldMap.Job job : this.flying.values()) {
               if (!wanted.contains(job.key)) {
                  job.cancelled = true;
               }
            }

            if (!this.queue.isEmpty() && this.workers.isEmpty()) {
               int n = workers();

               for (int i = 0; i < n; i++) {
                  Thread t = new Thread(this::work, "World map " + (i + 1));
                  t.setDaemon(true);
                  t.setPriority(Thread.MIN_PRIORITY + 1);
                  this.workers.add(t);
                  t.start();
               }
            }

            this.lock.notifyAll();
         }
      }
   }

   public Tile poll() {
      return this.ready.poll();
   }

   public int busy() {
      synchronized (this.lock) {
         return this.queue.size() + this.flying.size();
      }
   }

   private void work() {
      while (true) {
         WorldMap.Job job;
         synchronized (this.lock) {
            while (!this.closed && this.queue.isEmpty()) {
               try {
                  this.lock.wait();
               } catch (InterruptedException e) {
                  return;
               }
            }

            if (this.closed) {
               return;
            }

            job = this.queue.poll();
            this.flying.put(job.key, job);
         }

         try {
            Tile tile = job.cancelled ? null : this.load(job);
            if (tile != null && !job.cancelled) {
               this.ready.add(tile);
            }
         } catch (RuntimeException | OutOfMemoryError e) {
            this.ready.add(blank(job, (byte)3));
         } finally {
            synchronized (this.lock) {
               this.flying.remove(job.key, job);
            }
         }

         if (this.pruned.compareAndSet(false, true)) {
            TileCache.prune(this.home, cacheBudget);
         }
      }
   }

   private Tile load(WorldMap.Job job) {
      WorldLayout.RegionInfo listed = job.region;
      WorldLayout.RegionInfo now = stat(listed);
      if (now == null) {
         return blank(job, (byte)0);
      }

      if (now.away()) {
         return blank(job, (byte)4);
      }

      int level = job.key.level();
      boolean facts = level <= 3;
      if (this.fromLiveMap()) {
         return this.loadLive(job, now, level, facts);
      }

      Optional<Path> entitiesFile = entitiesFile(job.entities);
      long entities = entitiesKey(entitiesFile);
      int cave = job.cave;
      int style = job.key.style();
      String colourId = ((style & TileRenderer.DETAILED) != 0 ? this.colours.lookId() : this.colours.id()) + (cave == Integer.MIN_VALUE ? "" : "-cave" + cave) + (style == 0 ? "" : "-s" + style)

         + ((style & TileRenderer.BIOMES) != 0 ? "-b3" : "");
      Tile cached = this.cache.load(job.key.dimension(), now, entities, colourId, level, true, facts);
      if (cached != null) {
         cached.cave = cave;
         cached.style = style;
      }
      if (cached != null) {
         return cached;
      }

      if (awayNow(now, entitiesFile)) {
         return blank(job, (byte)4);
      }

      Tile old = this.cache.load(job.key.dimension(), now, entities, colourId, level, true, false, true);
      if (old != null && !job.cancelled) {
         old.cave = cave;
         old.style = style;
         old.stale = true;
         this.ready.add(old);
      }

      TileRenderer.Drawn drawn = TileRenderer.draw(
         now, entitiesFile, this.colours, () -> job.cancelled || this.closed, cave, (style & TileRenderer.SLICE) != 0 ? -1 : roof(job.key.dimension()), style
      );
      if (drawn == null) {
         return null;
      }

      int[][] levels = new int[5][];
      levels[0] = drawn.argb();

      for (int i = 1; i < 5; i++) {
         levels[i] = Tile.half(levels[i - 1], 512 >> i - 1);
      }

      WorldLayout.RegionInfo after = stat(listed);
      if (after != null
         && after.size() == now.size()
         && after.modified() == now.modified()
         && drawn.unreadable() < 1024
         && entities == entitiesKey(entitiesFile)) {
         this.cache.store(job.key.dimension(), now, entities, colourId, levels, drawn.facts());
      }

      Tile out = new Tile(job.key.dimension(), listed.rx(), listed.rz(), level, levels[level], facts ? drawn.facts() : null, now.size(), now.modified());
      out.cave = cave;
      out.style = style;
      return out;
   }

   private Tile loadLive(WorldMap.Job job, WorldLayout.RegionInfo now, int level, boolean facts) {
      LiveRegion region;
      try {
         region = LiveRegion.read(now.file(), now.rx(), now.rz());
      } catch (IOException e) {
         return blank(job, (byte)0);
      }

      TileRenderer.Drawn drawn = TileRenderer.drawLive(region, job.key.style(), this.colours);
      int[] argb = drawn.argb();
      for (int i = 0; i < level; i++) {
         argb = Tile.half(argb, 512 >> i);
      }

      return new Tile(job.key.dimension(), now.rx(), now.rz(), level, argb, facts ? drawn.facts() : null, now.size(), now.modified());
   }

   private static Tile blank(WorldMap.Job job, byte state) {
      int side = 512 >> job.key.level();
      RegionFacts facts = new RegionFacts();
      int[] argb = new int[side * side];
      if (state == 3) {
         Arrays.fill(facts.state, state);
         Arrays.fill(argb, -10738652);
      } else if (state == 4) {
         Arrays.fill(facts.state, state);
         int step = Math.max(1, 16 >> job.key.level());

         for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
               argb[y * side + x] = ((x + y) / step & 1) == 0 ? -14010812 : -14537672;
            }
         }
      }

      return new Tile(job.key.dimension(), job.region.rx(), job.region.rz(), job.key.level(), argb, facts, job.region.size(), job.region.modified());
   }

   private static WorldLayout.RegionInfo stat(WorldLayout.RegionInfo r) {
      try {
         BasicFileAttributes a = Files.readAttributes(r.file(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
         return a.isRegularFile() ? new WorldLayout.RegionInfo(r.rx(), r.rz(), r.file(), a.size(), a.lastModifiedTime().toMillis(), r.away()) : null;
      } catch (IOException e) {
         return null;
      }
   }

   private static boolean awayNow(WorldLayout.RegionInfo region, Optional<Path> entitiesFile) {
      List<Path> files = new ArrayList<>(2);
      files.add(region.file());
      entitiesFile.ifPresent(files::add);

      for (boolean away : Safe.away(files)) {
         if (away) {
            return true;
         }
      }

      return false;
   }

   private static Optional<Path> entitiesFile(WorldLayout.RegionInfo listed) {
      return listed != null && !listed.away() ? Optional.of(listed.file()) : Optional.empty();
   }

   private static long entitiesKey(Optional<Path> file) {
      if (file.isEmpty()) {
         return 0L;
      }

      try {
         BasicFileAttributes a = Files.readAttributes(file.get(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
         return a.isRegularFile() ? a.size() * 31L + a.lastModifiedTime().toMillis() : 0L;
      } catch (IOException e) {
         return 0L;
      }
   }

   public Tile tileNow(WorldMap.Index index, long region, int level) {
      WorldLayout.RegionInfo info = index.regions().get(region);
      if (info == null) {
         return null;
      }

      WorldMap.Job job = new WorldMap.Job(new WorldMap.Key(index.dimension().id(), region, Math.max(0, Math.min(4, level)), this.caveY, this.style), index.dimension(), info, index.entities().get(region));
      try {
         return this.load(job);
      } catch (RuntimeException e) {
         return null;
      }
   }

   public ChunkSelection select(WorldMap.Index index, ChunkRule rule, IntConsumer regionsDone, BooleanSupplier cancelled) {
      ChunkSelection out = new ChunkSelection();
      List<Entry<Long, long[]>> regions = new ArrayList<>(index.present().entrySet());
      if (!rule.needsFacts()) {
         int done = 0;

         for (Entry<Long, long[]> e : regions) {
            out.add((int)(e.getKey() >> 32), (int)e.getKey().longValue(), pick(rule, e.getKey(), e.getValue(), null));
            regionsDone.accept(++done);
         }

         return out;
      } else {
         int threads = workers();
         ExecutorService pool = Executors.newFixedThreadPool(threads, Background.named("World map select"));

         try {
            List<Future<long[]>> results = new ArrayList<>();

            for (Entry<Long, long[]> e : regions) {
               results.add(pool.submit(() -> {
                  WorldLayout.RegionInfo listed = index.regions().get(e.getKey());
                  WorldLayout.RegionInfo now = listed == null ? null : stat(listed);
                  if (now != null && !now.away() && !cancelled.getAsBoolean()) {
                     Optional<Path> entitiesFile = entitiesFile(index.entities().get(e.getKey()));
                     Tile cached = this.cache.load(index.dimension().id(), now, entitiesKey(entitiesFile), this.colours.id(), 0, false, true);
                     if (cached == null && awayNow(now, entitiesFile)) {
                        return null;
                     }

                     RegionFacts facts = cached != null ? cached.facts : TileRenderer.facts(now, entitiesFile, cancelled);
                     return facts == null ? null : pick(rule, e.getKey(), e.getValue(), facts);
                  } else {
                     return null;
                  }
               }));
            }

            for (int i = 0; i < results.size(); i++) {
               long[] mask;
               try {
                  mask = results.get(i).get();
               } catch (ExecutionException x) {
                  mask = null;
               } catch (InterruptedException x) {
                  Thread.currentThread().interrupt();
                  return null;
               }

               if (cancelled.getAsBoolean()) {
                  return null;
               }

               if (mask != null) {
                  long key = regions.get(i).getKey();
                  out.add((int)(key >> 32), (int)key, mask);
               }

               regionsDone.accept(i + 1);
            }

            return out;
         } finally {
            pool.shutdownNow();
         }
      }
   }

   public interface Progress {
      void stage(boolean reading, int total);

      void done(int n);
   }

   public record Found(ChunkSelection chunks, int tooOld) {
   }

   public WorldMap.Found find(WorldMap.Index index, ChunkFilter.Search search, WorldMap.Progress progress, BooleanSupplier cancelled) {
      Map<Long, RegionFacts> known = new ConcurrentHashMap<>();
      ChunkRule maybe = search.maybe();
      boolean needFacts = search.needsFacts();
      ChunkRule collect = new ChunkRule() {
         @Override
         public boolean matches(int x, int z, RegionFacts f, int i) {
            if (f != null) {
               known.putIfAbsent(WorldLayout.key(x >> 5, z >> 5), f);
            }

            return maybe.matches(x, z, f, i);
         }

         @Override
         public boolean needsFacts() {
            return needFacts;
         }
      };
      progress.stage(false, needFacts ? index.regions().size() : 0);
      ChunkSelection could = this.select(index, collect, progress::done, cancelled);
      if (could == null || cancelled.getAsBoolean()) {
         return null;
      }

      ChunkRule sure = search.sure();
      boolean deep = search.deep();
      ChunkSelection out = new ChunkSelection();
      Map<Long, long[]> toRead = new HashMap<>();
      int total = 0;
      for (Long key : could.regions()) {
         int rx = (int)(key >> 32);
         int rz = (int)key.longValue();
         long[] bits = could.bits(rx, rz);
         RegionFacts f = known.get(key);
         long[] taken = new long[16];
         long[] read = new long[16];
         boolean some = false;
         for (int i = 0; i < 1024; i++) {
            if ((bits[i >> 6] & 1L << (i & 63)) == 0L) {
               continue;
            }

            if (!deep || sure.matches((rx << 5) + (i & 31), (rz << 5) + (i >> 5), f, i)) {
               taken[i >> 6] |= 1L << (i & 63);
            } else {
               read[i >> 6] |= 1L << (i & 63);
               some = true;
               total++;
            }
         }

         out.add(rx, rz, taken);
         if (some) {
            toRead.put(key, read);
         }
      }

      if (toRead.isEmpty()) {
         return new WorldMap.Found(out, 0);
      }

      progress.stage(true, total);
      java.util.concurrent.atomic.AtomicInteger read = new java.util.concurrent.atomic.AtomicInteger();
      java.util.concurrent.atomic.AtomicInteger old = new java.util.concurrent.atomic.AtomicInteger();
      Set<String> skip = search.need().skip();
      List<Entry<Long, long[]>> regions = new ArrayList<>(toRead.entrySet());
      int threads = workers();
      ExecutorService pool = Executors.newFixedThreadPool(threads, Background.named("World map find"));

      try {
         List<Future<long[]>> results = new ArrayList<>();
         for (Entry<Long, long[]> e : regions) {
            results.add(pool.submit(() -> {
               long[] wanted = e.getValue();
               int count = 0;
               for (long w : wanted) {
                  count += Long.bitCount(w);
               }

               WorldLayout.RegionInfo listed = index.regions().get(e.getKey());
               WorldLayout.RegionInfo now = listed == null ? null : stat(listed);
               if (now == null || now.away() || cancelled.getAsBoolean() || awayNow(now, Optional.empty())) {
                  progress.done(read.addAndGet(count));
                  return null;
               }

               long[] mask = new long[16];
               RegionFacts facts = known.get(e.getKey());
               int rx = (int)(e.getKey() >> 32);
               int rz = (int)e.getKey().longValue();
               try (RegionFile file = RegionFile.open(now.file())) {
                  for (int i = 0; i < 1024; i++) {
                     if ((wanted[i >> 6] & 1L << (i & 63)) == 0L) {
                        continue;
                     }

                     if (cancelled.getAsBoolean()) {
                        return null;
                     }

                     RegionFile.Chunk chunk = file.chunk(i);
                     if (chunk.ok()) {
                        try {
                           ChunkContents c = ChunkContents.read(chunk.data(), skip);
                           if (c.tooOld()) {
                              old.incrementAndGet();
                           } else if (search.matches(c, (rx << 5) + (i & 31), (rz << 5) + (i >> 5), facts, i)) {
                              mask[i >> 6] |= 1L << (i & 63);
                           }
                        } catch (IOException | RuntimeException ignored) {
                        }
                     }

                     progress.done(read.incrementAndGet());
                     count--;
                  }
               } catch (IOException | RuntimeException ignored) {
                  progress.done(read.addAndGet(count));
               }

               return mask;
            }));
         }

         for (int i = 0; i < results.size(); i++) {
            long[] mask;
            try {
               mask = results.get(i).get();
            } catch (ExecutionException x) {
               mask = null;
            } catch (InterruptedException x) {
               Thread.currentThread().interrupt();
               return null;
            }

            if (cancelled.getAsBoolean()) {
               return null;
            }

            if (mask != null) {
               long key = regions.get(i).getKey();
               out.add((int)(key >> 32), (int)key, mask);
            }
         }

         return new WorldMap.Found(out, old.get());
      } finally {
         pool.shutdownNow();
      }
   }

   private static long[] pick(ChunkRule rule, long key, long[] present, RegionFacts facts) {
      int rx = (int)(key >> 32);
      int rz = (int)key;
      long[] mask = new long[16];

      for (int i = 0; i < 1024; i++) {
         if ((present[i >> 6] & 1L << (i & 63)) != 0L
            && (facts == null || facts.state[i] != 0 && (facts.state[i] != 3 || rule.includesUnreadable()))
            && rule.matches((rx << 5) + (i & 31), (rz << 5) + (i >> 5), facts, i)) {
            mask[i >> 6] = mask[i >> 6] | 1L << (i & 63);
         }
      }

      return mask;
   }

   @Override
   public void close() {
      synchronized (this.lock) {
         this.closed = true;
         this.queue.clear();

         for (WorldMap.Job job : this.flying.values()) {
            job.cancelled = true;
         }

         this.lock.notifyAll();
      }

      this.ready.clear();
      this.cache.close();
   }

   public record Index(
      WorldLayout.Dimension dimension,
      Map<Long, WorldLayout.RegionInfo> regions,
      Map<Long, long[]> present,
      int chunks,
      int minRx,
      int minRz,
      int maxRx,
      int maxRz,
      Map<Long, WorldLayout.RegionInfo> entities,
      int away
   ) {
      public boolean isEmpty() {
         return this.regions.isEmpty();
      }

      public boolean has(int chunkX, int chunkZ) {
         long[] bits = this.present.get(WorldLayout.key(chunkX >> 5, chunkZ >> 5));
         int i = ChunkSelection.index(chunkX, chunkZ);
         return bits != null && (bits[i >> 6] & 1L << (i & 63)) != 0L;
      }
   }

   private static final class Job {
      final WorldMap.Key key;
      final WorldLayout.Dimension dimension;
      final WorldLayout.RegionInfo region;
      final WorldLayout.RegionInfo entities;
      final int cave;
      volatile boolean cancelled;

      Job(WorldMap.Key key, WorldLayout.Dimension dimension, WorldLayout.RegionInfo region, WorldLayout.RegionInfo entities) {
         this.key = key;
         this.dimension = dimension;
         this.region = region;
         this.entities = entities;
         this.cave = key.cave();
      }
   }

   private record Key(String dimension, long region, int level, int cave, int style) {
   }
}
