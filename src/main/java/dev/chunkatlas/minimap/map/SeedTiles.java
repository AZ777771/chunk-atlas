package dev.chunkatlas.minimap.map;

import dev.chunkatlas.ChunkAtlas;
import dev.chunkatlas.GameColours;
import dev.chunkatlas.LiveMap;
import dev.chunkatlas.engine.Background;
import dev.chunkatlas.engine.SeedMapSettings;
import dev.chunkatlas.engine.Shader;
import dev.chunkatlas.engine.TileRenderer;
import dev.chunkatlas.minimap.Config;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Seeds;
import dev.chunkatlas.seed.SeedSampler;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

public final class SeedTiles {
   private static final int COARSE = 32;
   private static final int FINE = 128;

   private static final int KEEP_PIXELS = 1 << 22;

   private static final int TAKE = 4;

   private static final int CAVE_STEP = 16;

   private static final int RECHECK_TICKS = 40;

   private Run run;

   private String source;
   private boolean failed;
   private int ticks;
   private int triedAt = -RECHECK_TICKS;

   private Long typed;
   private Path typedFor;
   private int typedAt = -RECHECK_TICKS;

   private final LinkedHashMap<Long, Picture> pictures = new LinkedHashMap<>(64, 0.75F, true);
   private long held;
   private int version;

   private int rx0;
   private int rz0;
   private int rw;
   private int rh;
   private Picture[] area = new Picture[0];
   private boolean[] looked = new boolean[0];
   private int caveY = SeedSampler.NO_CAVE;
   private int look;
   private int fine = FINE;
   private int drawn;
   private List<Job> lastJobs = List.of();

   public int version() {
      return this.version;
   }

   public int generation() {
      return this.generation;
   }

   public it.unimi.dsi.fastutil.longs.LongArrayList takeFresh() {
      it.unimi.dsi.fastutil.longs.LongArrayList out = new it.unimi.dsi.fastutil.longs.LongArrayList(this.fresh);
      this.fresh.clear();
      return out;
   }

   private int generation;
   private final it.unimi.dsi.fastutil.longs.LongArrayList fresh = new it.unimi.dsi.fastutil.longs.LongArrayList();

   public void tick(Minecraft mc) {
      this.ticks++;
      Config cfg = Config.get();
      String want = cfg.enabled && cfg.seedMap && mc.level != null && mc.player != null ? this.source(mc) : null;
      if (!Objects.equals(want, this.source)) {
         this.close();
         this.source = want;
         this.failed = false;
      }

      if (want != null && this.run == null && !this.failed && this.ticks - this.triedAt >= RECHECK_TICKS) {
         this.triedAt = this.ticks;
         this.run = this.open(mc);
      }
   }

   private String source(Minecraft mc) {
      if (!Seeds.available()) {
         return null;
      }

      String dim = Ids.of(mc.level.dimension());
      IntegratedServer server = mc.getSingleplayerServer();
      if (server != null) {
         return Seeds.unsupported(server, dim) == null ? "game " + System.identityHashCode(server) + " " + dim : null;
      }

      Path folder = LiveMap.folder();
      if (!Objects.equals(folder, this.typedFor) || this.ticks - this.typedAt >= RECHECK_TICKS) {
         this.typedFor = folder;
         this.typedAt = this.ticks;
         this.typed = folder == null ? null : SeedMapSettings.load(ChunkAtlas.home(), folder).seed();
      }

      return this.typed == null ? null : "seed " + this.typed + " " + dim;
   }

   private Run open(Minecraft mc) {
      String dim = Ids.of(mc.level.dimension());
      IntegratedServer server = mc.getSingleplayerServer();
      try {
         SeedSampler world = server != null ? Seeds.open(server, dim, GameColours.get()) : Seeds.forSeed(this.typed, dim, GameColours.get());
         this.failed = world == null && (server != null || Seeds.vanillaFailed());
         return world == null ? null : new Run(world);
      } catch (RuntimeException e) {
         this.failed = true;
         return null;
      }
   }

   public void close() {
      if (this.run != null) {
         this.run.close();
         this.run = null;
      }

      this.pictures.clear();
      java.util.Arrays.fill(this.area, null);
      this.held = 0L;
      this.lastJobs = List.of();
      this.fresh.clear();
      this.version++;
      this.generation++;
   }

   public void frame() {
      if (this.run == null) {
         return;
      }

      List<Picture> done = this.run.take(TAKE);
      for (Picture p : done) {
         long key = key(p.job().rx(), p.job().rz());
         Picture old = this.pictures.get(key);
         if (old == null || this.score(p) >= this.score(old)) {
            this.held += p.argb().length - (old == null ? 0 : old.argb().length);
            this.pictures.put(key, p);
            this.fresh.add(key);
         }
      }

      if (!done.isEmpty()) {
         Iterator<Picture> it = this.pictures.values().iterator();
         while (this.held > KEEP_PIXELS && this.pictures.size() > 1 && it.hasNext()) {
            Picture gone = it.next();
            this.held -= gone.argb().length;
            this.fresh.add(key(gone.job().rx(), gone.job().rz()));
            it.remove();
         }

         this.version++;
      }
   }

   private int score(Picture p) {
      Job j = p.job();
      return ((j.caveY() == SeedSampler.NO_CAVE) == (this.caveY == SeedSampler.NO_CAVE) ? 4 : 0) + (j.caveY() == this.caveY && j.look() == this.look ? 2 : 0)
         + (j.n() >= this.fine ? 1 : 0);
   }

   public boolean begin(int c0x, int c0z, int c1x, int c1z, int layer, int bits, double blocksPerPixel) {
      this.drawn = 0;
      if (this.run == null || bits < 0) {
         return false;
      }

      this.fine = COARSE;
      while (this.fine < FINE && this.fine * blocksPerPixel < 512.0) {
         this.fine *= 2;
      }

      boolean cave = layer != Tile.SURFACE;
      this.caveY = cave ? Math.floorDiv(layer, CAVE_STEP) * CAVE_STEP : SeedSampler.NO_CAVE;

      this.look = (bits & TileRenderer.DETAILED) == 0 ? 0 : cave ? bits & (TileRenderer.DETAILED | TileRenderer.NIGHT) : bits & ~TileRenderer.CONTOURS;
      this.rx0 = c0x >> 5;
      this.rz0 = c0z >> 5;
      this.rw = (c1x >> 5) - this.rx0 + 1;
      this.rh = (c1z >> 5) - this.rz0 + 1;
      int size = this.rw * this.rh;
      if (this.area.length < size) {
         this.area = new Picture[size];
         this.looked = new boolean[size];
      }

      for (int i = 0; i < size; i++) {
         Picture p = this.pictures.get(key(this.rx0 + i % this.rw, this.rz0 + i / this.rw));
         this.area[i] = p != null && (p.job().caveY() == SeedSampler.NO_CAVE) == !cave ? p : null;
         this.looked[i] = false;
      }

      return true;
   }

   public void looked(int rx, int rz) {
      int x = rx - this.rx0;
      int z = rz - this.rz0;
      if (x >= 0 && z >= 0 && x < this.rw && z < this.rh) {
         this.looked[z * this.rw + x] = true;
      }
   }

   public int colour(int bx, int bz) {
      int x = (bx >> 9) - this.rx0;
      int z = (bz >> 9) - this.rz0;
      if (x < 0 || z < 0 || x >= this.rw || z >= this.rh) {
         return 0;
      }

      int i = z * this.rw + x;
      this.looked[i] = true;
      Picture p = this.area[i];
      if (p == null) {
         return 0;
      }

      int n = p.job().n();
      int c = p.argb()[((bz & 511) * n >> 9) * n + ((bx & 511) * n >> 9)];
      if (c != 0) {
         this.drawn++;
      }

      return c;
   }

   public void end(double px, double pz) {
      List<Integer> wanted = new ArrayList<>();
      for (int i = 0; i < this.rw * this.rh; i++) {
         if (this.looked[i]) {
            wanted.add(i);
         }
      }

      double fx = px / 512.0 - 0.5 - this.rx0;
      double fz = pz / 512.0 - 0.5 - this.rz0;
      wanted.sort(java.util.Comparator.comparingDouble(i -> {
         double dx = i % this.rw - fx;
         double dz = i / this.rw - fz;
         return dx * dx + dz * dz;
      }));
      List<Job> jobs = new ArrayList<>();
      for (int i : wanted) {
         if (this.area[i] == null) {
            jobs.add(new Job(this.rx0 + i % this.rw, this.rz0 + i / this.rw, COARSE, this.caveY, this.look));
         }
      }

      for (int i : wanted) {
         Picture p = this.area[i];
         boolean fits = p != null && p.job().caveY() == this.caveY && p.job().look() == this.look && p.job().n() >= this.fine;
         if (!fits && (p != null || this.fine > COARSE)) {
            jobs.add(new Job(this.rx0 + i % this.rw, this.rz0 + i / this.rw, this.fine, this.caveY, this.look));
         }
      }

      if (!jobs.equals(this.lastJobs)) {
         this.lastJobs = jobs;
         this.run.want(jobs);
      }
   }

   public boolean idle() {
      return this.run != null && this.run.idle();
   }

   public String stats() {
      int shown = 0;
      for (int i = 0; i < this.rw * this.rh; i++) {
         shown += this.looked[i] && this.area[i] != null ? 1 : 0;
      }

      Run r = this.run;
      return (r == null ? "seed map off" : "seed map: " + r.made + " pictures in " + r.busyNanos / 1000000L + " ms of background work on " + r.threads + " threads")
         + ", " + this.pictures.size() + " held, last frame drew " + shown + " seed tiles (" + this.drawn + " pixels), cave y "
         + (this.caveY == SeedSampler.NO_CAVE ? "none" : this.caveY) + ", look " + this.look;
   }

   private static long key(int rx, int rz) {
      return (long)rx << 32 | rz & 0xFFFFFFFFL;
   }

   private record Job(int rx, int rz, int n, int caveY, int look) {
   }

   private record Picture(Job job, int[] argb) {
   }

   private static final class Run {
      final SeedSampler world;
      final int threads;
      final Object lock = new Object();
      List<Job> queue = new ArrayList<>();
      Set<Job> wanted = Set.of();
      final Set<Job> running = new HashSet<>();
      final Set<Job> failed = new HashSet<>();
      final Map<Job, Picture> done = new LinkedHashMap<>();
      volatile boolean closed;
      volatile long busyNanos;
      volatile int made;

      Run(SeedSampler world) {
         this.world = world;
         this.threads = Background.threads(2);
         ThreadFactory make = Background.named("Chunk Atlas minimap seed map");
         for (int i = 0; i < this.threads; i++) {
            make.newThread(this::work).start();
         }
      }

      void want(List<Job> jobs) {
         synchronized (this.lock) {
            List<Job> next = new ArrayList<>(jobs.size());
            for (Job j : jobs) {
               if (!this.running.contains(j) && !this.done.containsKey(j) && !this.failed.contains(j)) {
                  next.add(j);
               }
            }

            this.queue = next;
            this.wanted = new HashSet<>(jobs);
            this.lock.notifyAll();
         }
      }

      List<Picture> take(int most) {
         synchronized (this.lock) {
            List<Picture> out = new ArrayList<>();
            Iterator<Picture> it = this.done.values().iterator();
            while (out.size() < most && it.hasNext()) {
               out.add(it.next());
               it.remove();
            }

            return out;
         }
      }

      boolean idle() {
         synchronized (this.lock) {
            return this.queue.isEmpty() && this.running.isEmpty() && this.done.isEmpty();
         }
      }

      void close() {
         synchronized (this.lock) {
            this.closed = true;
            this.queue = new ArrayList<>();
            this.lock.notifyAll();
         }
      }

      private boolean stillWanted(Job j) {
         synchronized (this.lock) {
            return this.wanted.contains(j);
         }
      }

      private void work() {
         while (!this.closed) {
            Job job;
            synchronized (this.lock) {
               while (!this.closed && this.queue.isEmpty()) {
                  try {
                     this.lock.wait(1000L);
                  } catch (InterruptedException e) {
                     return;
                  }
               }

               if (this.closed) {
                  return;
               }

               job = this.queue.remove(0);
               this.running.add(job);
            }

            long start = System.nanoTime();
            try {
               SeedSampler.Terrain t = this.world.terrain(job.rx(), job.rz(), job.n(), job.caveY(), true, () -> this.closed || !this.stillWanted(job));
               if (t != null) {
                  Picture p = new Picture(job, shade(t, job.look()));
                  synchronized (this.lock) {
                     this.done.put(job, p);
                  }

                  this.made++;
               }
            } catch (RuntimeException e) {
               synchronized (this.lock) {
                  this.failed.add(job);
               }
            } finally {
               synchronized (this.lock) {
                  this.running.remove(job);
               }

               this.busyNanos += System.nanoTime() - start;
            }
         }
      }

      private static int[] shade(SeedSampler.Terrain t, int look) {
         int[] argb = t.pixels(look, GameColours.get());
         if ((look & TileRenderer.NIGHT) != 0) {
            for (int i = 0; i < argb.length; i++) {
               if (argb[i] != 0) {
                  argb[i] = 0xFF000000 | Shader.night(argb[i], 0);
               }
            }
         }

         return argb;
      }
   }
}
