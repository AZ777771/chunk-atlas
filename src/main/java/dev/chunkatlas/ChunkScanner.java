package dev.chunkatlas;

import dev.chunkatlas.port.Heights;
import dev.chunkatlas.port.Pos;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ChunkScanner {
   private static final int MAX_READERS = 4;

   private static final int QUICK_MAX = 3;

   private static final int CURSOR_TICKS = 20;

   public interface Reader {
      boolean prepare(Minecraft mc, ClientLevel level, Entity viewer);

      int radius();

      long nearMs();

      long farMs();

      long budgetNs();

      boolean quickCheck();

      void read(ClientLevel level, LevelChunk chunk, Columns shared, long now);

      default long owed() {
         return 0L;
      }

      default int finish() {
         return 0;
      }
   }

   private static final Reader[] readers = new Reader[MAX_READERS];
   private static int count;
   private static ClientLevel level;
   private static final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
   private static final List<Entry> all = new ArrayList<>();
   private static final List<Entry> pending = new ArrayList<>();
   private static final ArrayDeque<Entry> due = new ArrayDeque<>();
   private static final Columns shared = new Columns();
   private static int active;
   private static int wasActive;
   private static int cursor;
   private static int sweepRow;
   private static boolean sweepAll = true;
   private static int lastPcx = Integer.MIN_VALUE;
   private static int lastPcz;
   private static final int[] lastRadius = new int[MAX_READERS];
   private static final int[] radius = new int[MAX_READERS];

   private static final int[] WORK = new int[2];

   private ChunkScanner() {
   }

   private static final class Entry {
      final int cx;
      final int cz;
      final long key;
      int index;
      final long[] read = new long[MAX_READERS];
      final int[] print = new int[MAX_READERS];
      final byte[] quick = new byte[MAX_READERS];
      int pendingMask;
      boolean listed;
      int dueMask;
      boolean queued;
      boolean gone;

      Entry(int cx, int cz) {
         this.cx = cx;
         this.cz = cz;
         this.key = key(cx, cz);
      }
   }

   public static void add(Reader reader) {
      if (count == 0) {
         ClientTickEvents.END_CLIENT_TICK.register(ChunkScanner::tick);
         ClientChunkEvents.CHUNK_LOAD.register(ChunkScanner::loaded);
         ClientChunkEvents.CHUNK_UNLOAD.register(ChunkScanner::unloaded);
      }

      readers[count++] = reader;
   }

   public static void again(Reader reader) {
      int bit = bit(reader);
      if (bit == 0) {
         return;
      }

      for (Entry e : all) {
         wantNew(e, bit);
      }
   }

   private static int bit(Reader reader) {
      for (int r = 0; r < count; r++) {
         if (readers[r] == reader) {
            return 1 << r;
         }
      }

      return 0;
   }

   private static long key(int cx, int cz) {
      return (long)cx << 32 | cz & 0xFFFFFFFFL;
   }

   private static void reset(ClientLevel now) {
      level = now;
      for (Entry e : all) {
         e.gone = true;
      }

      entries.clear();
      all.clear();
      pending.clear();
      due.clear();
      cursor = 0;
      sweepAll = true;
      lastPcx = Integer.MIN_VALUE;
   }

   private static void loaded(ClientLevel at, LevelChunk chunk) {
      if (at != level) {
         reset(at);
      }

      Entry e = entry(Pos.cx(chunk.getPos()), Pos.cz(chunk.getPos()));

      wantNew(e, active);
   }

   private static void unloaded(ClientLevel at, LevelChunk chunk) {
      if (at != level) {
         return;
      }

      Entry e = entries.remove(key(Pos.cx(chunk.getPos()), Pos.cz(chunk.getPos())));
      if (e != null) {
         drop(e);
      }
   }

   private static Entry entry(int cx, int cz) {
      long k = key(cx, cz);
      Entry e = entries.get(k);
      if (e == null) {
         e = new Entry(cx, cz);
         entries.put(k, e);
         e.index = all.size();
         all.add(e);
      }

      return e;
   }

   private static void drop(Entry e) {
      e.gone = true;
      int last = all.size() - 1;
      Entry moved = all.get(last);
      all.set(e.index, moved);
      moved.index = e.index;
      all.remove(last);
   }

   private static void wantNew(Entry e, int bits) {
      if (bits == 0) {
         return;
      }

      e.pendingMask |= bits;
      if (!e.listed) {
         e.listed = true;
         pending.add(e);
      }
   }

   private static boolean near(Entry e, int pcx, int pcz, int r) {
      return Math.max(Math.abs(e.cx - pcx), Math.abs(e.cz - pcz)) <= r;
   }

   private static void tick(Minecraft mc) {
      long t0 = System.nanoTime();
      ClientLevel now = mc.level;
      Entity viewer = mc.getCameraEntity();
      if (now == null || viewer == null) {
         if (level != null) {
            reset(null);
         }

         active = 0;
         wasActive = 0;
         return;
      }

      if (now != level) {
         reset(now);
      }

      long budget = 0L;
      active = 0;
      for (int r = 0; r < count; r++) {
         if (readers[r].prepare(mc, now, viewer)) {
            active |= 1 << r;
            budget += readers[r].budgetNs();
            radius[r] = readers[r].radius();
         }
      }

      int woke = active & ~wasActive;
      wasActive = active;
      if (active == 0) {
         Perf.scan(0, 0, 0, 0, System.nanoTime() - t0);
         return;
      }

      long ms = System.currentTimeMillis();
      int pcx = viewer.getBlockX() >> 4;
      int pcz = viewer.getBlockZ() >> 4;
      int checked = 0;
      boolean moved = pcx != lastPcx || pcz != lastPcz;
      for (int r = 0; r < count; r++) {
         if ((active & 1 << r) != 0 && radius[r] != lastRadius[r]) {
            lastRadius[r] = radius[r];
            moved = true;
         }
      }

      lastPcx = pcx;
      lastPcz = pcz;
      checked += sweep(now, mc, pcx, pcz);
      if (woke != 0) {
         for (Entry e : all) {
            wantNew(e, woke);
         }
      }

      if (moved) {
         for (Entry e : all) {
            for (int r = 0; r < count; r++) {
               int bit = 1 << r;
               if ((active & bit) != 0 && e.read[r] == 0L && (e.pendingMask & bit) == 0 && near(e, pcx, pcz, radius[r])) {
                  wantNew(e, bit);
               }
            }
         }

         checked += all.size();
      }

      checked += look(pcx, pcz, ms);
      long deadline = t0 + budget;
      int[] work = WORK;
      work[0] = 0;
      work[1] = 0;
      readNew(now, pcx, pcz, ms, deadline, work);
      readDue(now, pcx, pcz, ms, deadline, work);
      int shaded = 0;
      for (int r = 0; r < count; r++) {
         if ((active & 1 << r) != 0) {
            shaded += readers[r].finish();
         }
      }

      Perf.scan(checked, work[0], work[1], shaded, System.nanoTime() - t0);
   }

   private static int sweep(ClientLevel now, Minecraft mc, int pcx, int pcz) {
      int reach = mc.options.getEffectiveRenderDistance() + 3;
      int side = 2 * reach + 1;
      int rows = sweepAll ? side : 1;
      sweepAll = false;
      int checked = 0;
      for (int n = 0; n < rows; n++) {
         int z = pcz - reach + Math.floorMod(sweepRow++, side);
         for (int x = pcx - reach; x <= pcx + reach; x++) {
            checked++;
            boolean has = now.getChunkSource().hasChunk(x, z);
            Entry e = entries.get(key(x, z));
            if (has && e == null) {
               wantNew(entry(x, z), active);
            } else if (!has && e != null) {
               entries.remove(e.key);
               drop(e);
            }
         }
      }

      return checked;
   }

   private static int look(int pcx, int pcz, long ms) {
      int n = all.size();
      if (n == 0) {
         return 0;
      }

      int k = Math.min(n, Math.max(16, (n + CURSOR_TICKS - 1) / CURSOR_TICKS));
      for (int i = 0; i < k; i++) {
         Entry e = all.get(Math.floorMod(cursor++, n));
         int ring = Math.max(Math.abs(e.cx - pcx), Math.abs(e.cz - pcz));
         for (int r = 0; r < count; r++) {
            int bit = 1 << r;
            if ((active & bit) == 0 || ring > radius[r]) {
               continue;
            }

            if (e.read[r] == 0L) {
               if ((e.pendingMask & bit) == 0) {
                  wantNew(e, bit);
               }
            } else if ((e.dueMask & bit) == 0 && ms - e.read[r] > (ring <= 2 ? readers[r].nearMs() : readers[r].farMs())) {
               e.dueMask |= bit;
               if (!e.queued) {
                  e.queued = true;
                  due.add(e);
               }
            }
         }
      }

      return k;
   }

   private static long owed() {
      long n = 0L;
      for (int r = 0; r < count; r++) {
         if ((active & 1 << r) != 0) {
            n += readers[r].owed();
         }
      }

      return n;
   }

   private static void readNew(ClientLevel now, int pcx, int pcz, long ms, long deadline, int[] work) {
      if (pending.isEmpty()) {
         return;
      }

      int n = pending.size();
      long[] order = new long[n];
      for (int i = 0; i < n; i++) {
         Entry e = pending.get(i);
         long dx = e.cx - pcx;
         long dz = e.cz - pcz;
         order[i] = Math.min(dx * dx + dz * dz, 0x7FFFFFFFL) << 32 | i;
      }

      java.util.Arrays.sort(order);
      for (long o : order) {
         Entry e = pending.get((int)o);
         if (e.gone) {
            e.pendingMask = 0;
            continue;
         }

         int mask = e.pendingMask & active;

         e.pendingMask = mask;
         for (int r = 0; r < count; r++) {
            int bit = 1 << r;
            if ((mask & bit) != 0 && !near(e, pcx, pcz, radius[r])) {
               mask &= ~bit;
               e.pendingMask &= ~bit;
            }
         }

         if (mask == 0) {
            continue;
         }

         if (System.nanoTime() + owed() > deadline) {
            break;
         }

         e.pendingMask = 0;
         if (!now.getChunkSource().hasChunk(e.cx, e.cz)) {
            continue;
         }

         shared.reset(now, now.getChunk(e.cx, e.cz));
         for (int r = 0; r < count; r++) {
            if ((mask & 1 << r) != 0) {
               readers[r].read(now, shared.chunk, shared, ms);
               e.read[r] = ms;
               e.quick[r] = 0;
               if (readers[r].quickCheck()) {
                  e.print[r] = shared.print();
               }
            }
         }

         work[0]++;
      }

      int kept = 0;
      for (int i = 0; i < n; i++) {
         Entry e = pending.get(i);
         if (e.pendingMask != 0 && !e.gone) {
            pending.set(kept++, e);
         } else {
            e.listed = false;
         }
      }

      pending.subList(kept, n).clear();
   }

   private static void readDue(ClientLevel now, int pcx, int pcz, long ms, long deadline, int[] work) {
      while (!due.isEmpty() && System.nanoTime() + owed() < deadline) {
         Entry e = due.poll();
         e.queued = false;
         int mask = e.dueMask & active;
         e.dueMask = 0;
         if (e.gone || mask == 0 || !now.getChunkSource().hasChunk(e.cx, e.cz)) {
            continue;
         }

         shared.reset(now, now.getChunk(e.cx, e.cz));
         boolean full = false;
         for (int r = 0; r < count; r++) {
            if ((mask & 1 << r) == 0 || !near(e, pcx, pcz, radius[r])) {
               continue;
            }

            Reader reader = readers[r];
            if (reader.quickCheck() && e.quick[r] < QUICK_MAX && shared.print() == e.print[r]) {
               e.read[r] = ms;
               e.quick[r]++;
               work[1]++;
               continue;
            }

            reader.read(now, shared.chunk, shared, ms);
            e.read[r] = ms;
            e.quick[r] = 0;
            if (reader.quickCheck()) {
               e.print[r] = shared.print();
            }

            full = true;
         }

         if (full) {
            work[0]++;
         }
      }
   }

   public static String stats() {
      return all.size() + " chunks known, " + pending.size() + " waiting, " + due.size() + " due";
   }

   public static final class Columns {
      public final int[] top = new int[256];
      public final int[] cover = new int[256];
      public final short[] height = new short[256];
      public final byte[] depth = new byte[256];
      public final short[] biome = new short[256];
      public final byte[] light = new byte[256];

      public final boolean[] has = new boolean[256];
      private final LiveColumns columns = new LiveColumns();
      private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      private ClientLevel level;
      LevelChunk chunk;
      private boolean detailed;
      private boolean printed;
      private int print;

      void reset(ClientLevel level, LevelChunk chunk) {
         this.level = level;
         this.chunk = chunk;
         this.detailed = false;
         this.printed = false;
      }

      public Columns detail() {
         if (!this.detailed) {
            this.detailed = true;
            int minY = Heights.min(this.level);
            for (int lz = 0; lz < 16; lz++) {
               for (int lx = 0; lx < 16; lx++) {
                  int i = lz << 4 | lx;
                  int top = this.chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                  this.has[i] = this.columns.scan(this.level, this.chunk, lx, lz, top, minY, i, this.top, this.cover, this.height, this.depth, this.biome, this.light);
               }
            }
         }

         return this;
      }

      int print() {
         if (!this.printed) {
            this.printed = true;
            int h = 1;
            int x0 = this.chunk.getPos().getMinBlockX();
            int z0 = this.chunk.getPos().getMinBlockZ();
            for (int lz = 0; lz < 16; lz++) {
               for (int lx = 0; lx < 16; lx++) {
                  int y = this.chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                  h = h * 31 + y;
                  h = h * 31 + System.identityHashCode(this.chunk.getBlockState(this.pos.set(x0 + lx, y, z0 + lz)));
               }
            }

            this.print = h;
         }

         return this.print;
      }
   }
}
