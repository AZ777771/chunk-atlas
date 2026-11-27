package dev.chunkatlas.seed;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class SeedCache implements AutoCloseable {
   private static final long MAX_PIXELS = 4L << 20;

   private static final int MAX_PICTURES = 20000;
   private static final int MAX_STRUCTURES = 3000;

   public static final int SHEET = 16;
   private final SeedSampler world;
   private final List<Thread> threads = new ArrayList<>();
   private final Object lock = new Object();
   private List<Job> queue = new ArrayList<>();
   private Set<Job> wanted = new HashSet<>();
   private final Set<Job> running = new HashSet<>();
   private final Map<Job, SeedSampler.Terrain> biomes = new LinkedHashMap<>(256, 0.75F, true);
   private final Map<Job, List<SeedSampler.Found>> structures = new LinkedHashMap<>(256, 0.75F, true);
   private final AtomicInteger changes = new AtomicInteger();
   private long pixels;
   private volatile boolean closed;
   private volatile long busyNanos;
   private volatile int jobsDone;

   private final java.util.concurrent.atomic.AtomicLongArray typeNanos = new java.util.concurrent.atomic.AtomicLongArray(Type.values().length);
   private final java.util.concurrent.atomic.AtomicIntegerArray typeDone = new java.util.concurrent.atomic.AtomicIntegerArray(Type.values().length);

   public SeedCache(SeedSampler world) {
      this.world = world;

      int n = Math.max(1, Math.min(6, dev.chunkatlas.engine.Background.cores() - 1));
      for (int i = 0; i < n; i++) {
         int index = i;
         Thread t = new Thread(() -> this.work(index), "Chunk Atlas seed map " + (i + 1));
         t.setDaemon(true);
         t.setPriority(Thread.MIN_PRIORITY);
         this.threads.add(t);
         t.start();
      }

      Thread rings = new Thread(() -> {
         try {
            world.rings();
            this.changes.incrementAndGet();
         } catch (RuntimeException ignored) {
         }
      }, "Chunk Atlas seed map rings");
      rings.setDaemon(true);
      rings.setPriority(Thread.MIN_PRIORITY);
      rings.start();
   }

   private volatile int most = 6;

   public void makeWay(boolean on) {
      this.most = on ? 2 : 6;
   }

   public SeedSampler world() {
      return this.world;
   }

   public int changes() {
      return this.changes.get();
   }

   public void want(List<Job> jobs) {
      synchronized (this.lock) {
         List<Job> next = new ArrayList<>(jobs.size());
         for (Job j : jobs) {
            if (!this.has(j) && !this.running.contains(j)) {
               next.add(j);
            }
         }

         this.queue = next;
         this.wanted = new HashSet<>(jobs);
         this.lock.notifyAll();
      }
   }

   public boolean idle() {
      synchronized (this.lock) {
         return this.queue.isEmpty() && this.running.isEmpty();
      }
   }

   public int waiting() {
      synchronized (this.lock) {
         return this.queue.size() + this.running.size();
      }
   }

   public String stats() {
      StringBuilder b = new StringBuilder().append(this.jobsDone).append(" tiles in ").append(this.busyNanos / 1000000L).append(" ms of background work (");
      for (Type t : Type.values()) {
         b.append(t.ordinal() == 0 ? "" : ", ").append(t.name().toLowerCase(java.util.Locale.ROOT)).append(' ')
            .append(this.typeDone.get(t.ordinal())).append(" in ").append(this.typeNanos.get(t.ordinal()) / 1000000L).append(" ms");
      }

      return b.append(')').toString();
   }

   private boolean has(Job j) {
      return j.type() == Type.DETAIL ? false : j.type().picture() ? this.biomes.containsKey(j) : this.structures.containsKey(j);
   }

   private static final int MAX_DETAILS = 12;

   private static final int APRON = 6;
   private final Map<Job, Detail> details = new LinkedHashMap<>(16, 0.75F, true);

   private volatile int[] view = new int[]{0, 0, -1, -1, 0, 0};

   private final Map<Long, long[]> explored = new java.util.concurrent.ConcurrentHashMap<>();

   private static final class Detail {
      final dev.chunkatlas.engine.Surface surface = new dev.chunkatlas.engine.Surface(512 + 2 * APRON);
      final MapColumns map = new MapColumns(512 + 2 * APRON);
      final long[] done = new long[16];
      final java.util.Set<Integer> making = new java.util.HashSet<>();

      final List<SeedSampler.Tree> trees = new ArrayList<>();
      volatile SeedSampler.Terrain shown;
   }

   public void view(int[] chunks) {
      this.view = chunks.clone();
   }

   public void explored(int rx, int rz, long[] mask) {
      long key = (long)rx << 32 | rz & 0xFFFFFFFFL;
      if (mask == null) {
         this.explored.remove(key);
      } else {
         this.explored.put(key, mask);
      }
   }

   public SeedSampler.Terrain detail(int rx, int rz, int caveY) {
      synchronized (this.lock) {
         Detail d = this.details.get(new Job(Type.DETAIL, rx, rz, 0, caveY));
         return d == null ? null : d.shown;
      }
   }

   private void makeChunks(Job job, java.util.function.BooleanSupplier cancelled) {
      Job key = new Job(Type.DETAIL, job.rx(), job.rz(), 0, job.y());
      Detail d;
      synchronized (this.lock) {
         d = this.details.computeIfAbsent(key, k -> new Detail());
         int[] v = this.view;
         var it = this.details.entrySet().iterator();
         while (this.details.size() > MAX_DETAILS && it.hasNext()) {
            var e = it.next();
            Job k = e.getKey();

            boolean inView = k.rx() * 32 + 31 >= v[0] && k.rx() * 32 <= v[2] && k.rz() * 32 + 31 >= v[1] && k.rz() * 32 <= v[3];
            if (e.getValue() != d && !inView) {
               it.remove();
            }
         }
      }

      int made = 0;
      long published = System.nanoTime();
      while (!cancelled.getAsBoolean()) {
         int next = this.nextChunk(job, d);
         if (next < 0) {
            break;
         }

         int lx = next & 31;
         int lz = next >> 5;
         List<SeedSampler.Tree> trees = new ArrayList<>();
         try {
            if (!this.world.makeChunk(job.rx() * 32 + lx, job.rz() * 32 + lz, job.y(), d.surface, d.map, APRON + lx * 16, APRON + lz * 16, trees)) {
               break;
            }
         } catch (RuntimeException e) {
         } finally {
            synchronized (d) {
               d.trees.addAll(trees);
            }

            synchronized (d) {
               d.making.remove(next);
               d.done[next >> 6] |= 1L << (next & 63);
            }
         }

         made++;
         if (System.nanoTime() - published > 300_000_000L) {
            this.publish(key, d);
            published = System.nanoTime();
         }
      }

      if (made > 0) {
         this.publish(key, d);
      }
   }

   private int nextChunk(Job job, Detail d) {
      int[] v = this.view;
      long[] skip = this.explored.get((long)job.rx() << 32 | job.rz() & 0xFFFFFFFFL);
      int best = -1;
      long bestD = Long.MAX_VALUE;
      synchronized (d) {
         int x0 = Math.max(0, v[0] - job.rx() * 32);
         int z0 = Math.max(0, v[1] - job.rz() * 32);
         int x1 = Math.min(31, v[2] - job.rx() * 32);
         int z1 = Math.min(31, v[3] - job.rz() * 32);
         for (int lz = z0; lz <= z1; lz++) {
            for (int lx = x0; lx <= x1; lx++) {
               int i = lz * 32 + lx;
               if ((d.done[i >> 6] & 1L << (i & 63)) != 0L || d.making.contains(i) || skip != null && (skip[i >> 6] & 1L << (i & 63)) != 0L) {
                  continue;
               }

               long dx = job.rx() * 32 + lx - v[4];
               long dz = job.rz() * 32 + lz - v[5];
               long dist = dx * dx + dz * dz;
               if (dist < bestD) {
                  bestD = dist;
                  best = i;
               }
            }
         }

         if (best >= 0) {
            d.making.add(best);
         }
      }

      return best;
   }

   private void publish(Job key, Detail d) {
      dev.chunkatlas.engine.Surface copy;
      MapColumns map;
      List<SeedSampler.Tree> trees = new ArrayList<>();
      synchronized (d) {
         copy = d.surface.copy();
         map = d.map.copy();
         trees.addAll(d.trees);
      }

      List<Detail> near = new ArrayList<>();
      synchronized (this.lock) {
         for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
               Detail n = (dx | dz) == 0 ? null : this.details.get(new Job(Type.DETAIL, key.rx() + dx, key.rz() + dz, 0, key.y()));
               if (n != null) {
                  near.add(n);
               }
            }
         }
      }

      for (Detail n : near) {
         synchronized (n) {
            trees.addAll(n.trees);
         }
      }

      int x0 = key.rx() * 512 - APRON;
      int z0 = key.rz() * 512 - APRON;
      int side = copy.side;
      for (SeedSampler.Tree t : trees) {
         int r = t.radius();
         int cx = t.x() - x0;
         int cz = t.z() - z0;
         if (cx < -r || cz < -r || cx >= side + r || cz >= side + r) {
            continue;
         }

         for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
               int x = cx + dx;
               int z = cz + dz;

               if (x < 0 || z < 0 || x >= side || z >= side || dx * dx + dz * dz > r * r + ((t.x() * 31 + t.z() * 17 + dx * 7 + dz * 13 & 3) == 0 ? 0 : r)) {
                  continue;
               }

               int i = z * side + x;
               if (copy.drawn(i) && copy.height[i] < t.top()) {
                  copy.top[i] = t.leaves();
                  copy.height[i] = (short)t.top();
                  copy.depth[i] = 0;
                  copy.cover[i] = 0;
                  if (t.map() != 0) {
                     map.colour[i] = t.map();
                     map.height[i] = (short)t.top();
                     map.water[i] = 0;
                  }
               }
            }
         }
      }

      d.shown = new SeedSampler.Terrain(map.shade(APRON), null, 0, 0, copy, 1);
      this.changes.incrementAndGet();
   }

   public SeedSampler.Terrain terrain(int rx, int rz, int n) {
      synchronized (this.lock) {
         SeedSampler.Terrain t = this.biomes.get(new Job(Type.BIOMES, rx, rz, n));
         return t != null ? t : this.biomes.get(new Job(Type.BASE, rx, rz, n));
      }
   }

   public SeedSampler.Terrain picture(Job job) {
      synchronized (this.lock) {
         return this.biomes.get(job);
      }
   }

   public void again(Job job) {
      synchronized (this.lock) {
         SeedSampler.Terrain t = this.biomes.get(job);
         if (t != null && t.slim()) {
            this.biomes.remove(job);
         }
      }
   }

   public List<SeedSampler.Found> structures(int rx, int rz) {
      synchronized (this.lock) {
         List<SeedSampler.Found> full = this.structures.get(new Job(Type.CHECKED, rx, rz, 0));
         return full != null ? full : this.structures.get(new Job(Type.QUICK, rx, rz, 0));
      }
   }

   private void work(int index) {
      while (!this.closed) {
         Job job;
         synchronized (this.lock) {
            while (!this.closed && (this.queue.isEmpty() || index >= Math.min(this.most, dev.chunkatlas.engine.Background.threads(6)))) {
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
            Job j = job;
            java.util.function.BooleanSupplier cancelled = () -> this.closed || !this.stillWanted(j);
            if (job.type() == Type.DETAIL) {
               this.makeChunks(job, cancelled);
            } else if (job.type().picture()) {
               SeedSampler.Terrain t = job.type() == Type.SHEET
                  ? this.world.sheet(job.rx(), job.rz(), SHEET, job.n(), job.y(), cancelled)
                  : this.world.terrain(job.rx(), job.rz(), job.n(), job.y(), job.type() == Type.BASE, cancelled);
               if (t != null) {
                  synchronized (this.lock) {
                     SeedSampler.Terrain old = this.biomes.put(job, t);
                     this.pixels += t.weight() - (old == null ? 0L : old.weight());
                     var it = this.biomes.entrySet().iterator();
                     while (this.pixels > MAX_PIXELS && it.hasNext()) {
                        var e = it.next();
                        SeedSampler.Terrain v = e.getValue();
                        if (v != t && !v.slim()) {
                           this.pixels -= v.weight();
                           e.setValue(v.slimmed());
                        }
                     }

                     it = this.biomes.entrySet().iterator();
                     while (this.biomes.size() > MAX_PICTURES && it.hasNext()) {
                        this.pixels -= it.next().getValue().weight();
                        it.remove();
                     }
                  }
               }
            } else {
               List<SeedSampler.Found> found = this.world.structures(job.rx(), job.rz(), job.type() == Type.CHECKED, cancelled);
               if (found != null) {
                  synchronized (this.lock) {
                     this.structures.put(job, List.copyOf(found));
                     trim(this.structures, MAX_STRUCTURES);
                  }
               }
            }
         } catch (RuntimeException ignored) {
         } finally {
            synchronized (this.lock) {
               this.running.remove(job);
            }

            long took = System.nanoTime() - start;
            this.busyNanos += took;
            this.jobsDone++;
            this.typeNanos.addAndGet(job.type().ordinal(), took);
            this.typeDone.incrementAndGet(job.type().ordinal());
            this.changes.incrementAndGet();
         }
      }
   }

   private boolean stillWanted(Job j) {
      synchronized (this.lock) {
         return this.wanted.contains(j);
      }
   }

   private static <V> void trim(Map<Job, V> map, int max) {
      var it = map.entrySet().iterator();
      while (map.size() > max && it.hasNext()) {
         it.next();
         it.remove();
      }
   }

   @Override
   public void close() {
      this.closed = true;
      synchronized (this.lock) {
         this.queue = new ArrayList<>();
         this.lock.notifyAll();
      }
   }

   public enum Type {
      BIOMES,

      BASE,

      SHEET,

      DETAIL,
      QUICK,
      CHECKED;

      boolean picture() {
         return this == BIOMES || this == BASE || this == SHEET;
      }
   }

   public record Job(Type type, int rx, int rz, int n, int y) {
      public Job(Type type, int rx, int rz, int n) {
         this(type, rx, rz, n, SeedSampler.NO_CAVE);
      }
   }
}
