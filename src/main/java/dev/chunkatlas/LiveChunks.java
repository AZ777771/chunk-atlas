package dev.chunkatlas;

import dev.chunkatlas.engine.Surface;
import dev.chunkatlas.port.Heights;
import dev.chunkatlas.port.Hooks;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Pos;
import dev.chunkatlas.port.Reg;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class LiveChunks {
   public static final int APRON = 3;
   public static final int SIDE = 16 + 2 * APRON;

   private static final long CLIENT_BUDGET = 3_000_000L;
   private static final long SERVER_BUDGET = 4_000_000L;
   private static final long SERVER_WINDOW = 50_000_000L;

   private static final int MAX_WAITING = 4096;
   private static final int MAX_READY = 2048;

   private static final long WATCH_MS = 1500L;
   private static final int WATER = -12566273;

   public record Columns(String dimension, int cx, int cz, long at, Surface surface, int[] colour, short[] height, byte[] water) {
   }

   private static volatile String watched;
   private static volatile long watchedAt;
   private static final ConcurrentLinkedQueue<Columns> READY = new ConcurrentLinkedQueue<>();
   private static final AtomicInteger READY_COUNT = new AtomicInteger();

   private static final LinkedHashSet<Long> clientWaiting = new LinkedHashSet<>();
   private static final Reader clientReader = new Reader();

   private static final ArrayDeque<Waiting> serverWaiting = new ArrayDeque<>();
   private static Reader serverReader;
   private static long windowStart;
   private static long spent;

   private static final long RETRY_NS = 100_000_000L;

   private static volatile int serverQueued;
   private static volatile boolean readAsked;
   private static volatile long readNs;
   private static final AtomicInteger seen = new AtomicInteger();
   private static final AtomicInteger read = new AtomicInteger();

   private record Waiting(ServerLevel level, WeakReference<LevelChunk> chunk, long pos, long at) {
   }

   private LiveChunks() {
   }

   static void register() {
      ClientChunkEvents.CHUNK_LOAD.register(LiveChunks::clientLoaded);
      ClientTickEvents.END_CLIENT_TICK.register(LiveChunks::clientTick);
      Hooks.serverChunkLoaded(LiveChunks::serverLoaded);
      ServerTickEvents.END_SERVER_TICK.register(LiveChunks::serverTick);
      ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
         clientWaiting.clear();
         watched = null;
         READY.clear();
         READY_COUNT.set(0);
      }));
   }

   public static void watch(String dimension) {
      watched = dimension;
      watchedAt = System.currentTimeMillis();
   }

   private static String watching() {
      return System.currentTimeMillis() - watchedAt < WATCH_MS ? watched : null;
   }

   public static Columns poll() {
      Columns c = READY.poll();
      if (c != null) {
         READY_COUNT.decrementAndGet();
      }

      return c;
   }

   private static void hand(Columns c) {
      READY.add(c);
      if (READY_COUNT.incrementAndGet() > MAX_READY) {
         poll();
      }
   }

   private static String id(Level level) {
      return Ids.of(level.dimension());
   }

   private static long key(int x, int z) {
      return (long)x << 32 | z & 0xFFFFFFFFL;
   }

   private static void clientLoaded(ClientLevel level, LevelChunk chunk) {
      if (Minecraft.getInstance().getSingleplayerServer() != null || !id(level).equals(watching())) {
         return;
      }

      clientWaiting.add(key(Pos.cx(chunk.getPos()), Pos.cz(chunk.getPos())));
      seen.incrementAndGet();
      if (clientWaiting.size() > MAX_WAITING) {
         Iterator<Long> it = clientWaiting.iterator();
         it.next();
         it.remove();
      }
   }

   private static void clientTick(Minecraft mc) {
      askPausedServer(mc);
      if (clientWaiting.isEmpty()) {
         return;
      }

      ClientLevel level = mc.level;
      String dim = watching();
      if (level == null || dim == null || !dim.equals(id(level))) {
         clientWaiting.clear();
         return;
      }

      long start = System.nanoTime();
      Iterator<Long> it = clientWaiting.iterator();
      while (it.hasNext() && System.nanoTime() - start < CLIENT_BUDGET) {
         long k = it.next();
         it.remove();
         int x = (int)(k >> 32);
         int z = (int)k;
         if (level.getChunkSource().hasChunk(x, z)) {
            hand(clientReader.read(level, level.getChunk(x, z), dim, (nx, nz) -> level.getChunkSource().hasChunk(nx, nz) ? level.getChunk(nx, nz) : null));
            read.incrementAndGet();
         }
      }
   }

   private static void serverLoaded(ServerLevel level, LevelChunk chunk) {
      String dim = watching();
      if (dim == null || level.getServer() != Minecraft.getInstance().getSingleplayerServer() || !dim.equals(id(level))) {
         return;
      }

      serverWaiting.addLast(new Waiting(level, new WeakReference<>(chunk), key(Pos.cx(chunk.getPos()), Pos.cz(chunk.getPos())), System.nanoTime()));
      if (serverWaiting.size() > MAX_WAITING) {
         serverWaiting.pollFirst();
      }

      serverQueued = serverWaiting.size();
      seen.incrementAndGet();
   }

   private static void serverTick(MinecraftServer server) {
      if (!serverWaiting.isEmpty()) {
         readOnServer();
      }
   }

   private static void askPausedServer(Minecraft mc) {
      MinecraftServer server = mc.getSingleplayerServer();
      if (server != null && mc.isPaused() && serverQueued > 0 && !readAsked) {
         readAsked = true;
         server.execute(() -> {
            readAsked = false;
            readOnServer();
         });
      }
   }

   private static void readOnServer() {
      long now = System.nanoTime();
      if (now - windowStart > SERVER_WINDOW) {
         windowStart = now;
         spent = 0L;
      }

      String dim = watching();
      if (dim == null) {
         serverWaiting.clear();
         serverQueued = 0;
         return;
      }

      if (serverReader == null) {
         serverReader = new Reader();
      }

      ArrayDeque<Waiting> later = new ArrayDeque<>();
      while (!serverWaiting.isEmpty() && spent < SERVER_BUDGET) {
         Waiting w = serverWaiting.pollLast();
         if (!dim.equals(id(w.level()))) {
            continue;
         }

         int x = (int)(w.pos() >> 32);
         int z = (int)w.pos();
         LevelChunk chunk = w.level().getChunkSource().getChunkNow(x, z);
         if (chunk == null) {
            boolean young = now - w.at() < RETRY_NS;
            chunk = young ? null : w.chunk().get();
            if (chunk == null) {
               if (young) {
                  later.addFirst(w);
               }

               continue;
            }
         }

         long t0 = System.nanoTime();
         try {
            hand(serverReader.read(w.level(), chunk, dim, (nx, nz) -> w.level().getChunkSource().getChunkNow(nx, nz)));
            read.incrementAndGet();
         } catch (RuntimeException ignored) {
         }

         long took = System.nanoTime() - t0;
         spent += took;
         readNs += took;
      }

      serverWaiting.addAll(later);
      serverQueued = serverWaiting.size();
   }

   public static String tourCounts() {
      int n = read.get();
      return seen.get() + " chunks finished while watched, " + n + " read (" + (n == 0 ? 0 : readNs / n / 1000) + " us each), " + serverQueued
         + " waiting";
   }

   private interface Near {
      LevelChunk at(int cx, int cz);
   }

   private static final class Reader {
      private final LiveColumns columns = new LiveColumns(true);
      private final IdentityHashMap<Block, Integer> mapColours = new IdentityHashMap<>();
      private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      private final LevelChunk[] near = new LevelChunk[9];

      Columns read(Level level, LevelChunk chunk, String dim, Near lookup) {
         int cx = Pos.cx(chunk.getPos());
         int cz = Pos.cz(chunk.getPos());
         int minY = Heights.min(level);
         Surface s = new Surface(SIDE);
         int[] colour = new int[SIDE * SIDE];
         short[] height = new short[SIDE * SIDE];
         byte[] water = new byte[SIDE * SIDE];
         java.util.Arrays.fill(height, (short)minY);
         boolean[] had = new boolean[SIDE * SIDE];
         for (int n = 0; n < 9; n++) {
            int dx = n % 3 - 1;
            int dz = n / 3 - 1;
            this.near[n] = (dx | dz) == 0 ? chunk : lookup.at(cx + dx, cz + dz);
         }

         for (int gz = 0; gz < SIDE; gz++) {
            for (int gx = 0; gx < SIDE; gx++) {
               int bx = cx * 16 - APRON + gx;
               int bz = cz * 16 - APRON + gz;
               LevelChunk c = this.near[((bz >> 4) - cz + 1) * 3 + (bx >> 4) - cx + 1];
               if (c == null) {
                  continue;
               }

               int lx = bx & 15;
               int lz = bz & 15;
               int i = gz * SIDE + gx;
               int top = c.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
               this.columns.scan(level, c, lx, lz, top, minY, i, s.top, s.cover, s.height, s.depth, s.biome, s.light);
               this.vanilla(c, bx, bz, top, minY, i, colour, height, water);
               had[i] = true;
            }
         }

         for (int gz = 0; gz < SIDE; gz++) {
            for (int gx = 0; gx < SIDE; gx++) {
               int i = gz * SIDE + gx;
               if (had[i]) {
                  continue;
               }

               int j = Math.max(APRON, Math.min(APRON + 15, gz)) * SIDE + Math.max(APRON, Math.min(APRON + 15, gx));
               s.top[i] = s.top[j];
               s.cover[i] = s.cover[j];
               s.height[i] = s.height[j];
               s.depth[i] = s.depth[j];
               s.biome[i] = s.biome[j];
               s.light[i] = s.light[j];
               colour[i] = colour[j];
               height[i] = height[j];
               water[i] = water[j];
            }
         }

         java.util.Arrays.fill(this.near, null);
         return new Columns(dim, cx, cz, System.currentTimeMillis(), s, colour, height, water);
      }

      private void vanilla(LevelChunk chunk, int x, int z, int top, int minY, int i, int[] colour, short[] height, byte[] water) {
         int waterTop = Integer.MIN_VALUE;
         int waterColour = 0;
         for (int y = top; y >= minY; y--) {
            int c = this.mapColour(chunk.getBlockState(this.pos.set(x, y, z)).getBlock());
            if (c == 0) {
               continue;
            }

            boolean wet = (c & 0x01000000) != 0;
            int argb = 0xFF000000 | c & 0xFFFFFF;
            if (!wet) {
               if (waterTop != Integer.MIN_VALUE) {
                  colour[i] = waterColour;
                  height[i] = (short)waterTop;
                  water[i] = (byte)Math.min(255, waterTop - y);
               } else {
                  colour[i] = argb;
                  height[i] = (short)y;
               }

               return;
            }

            if (waterTop == Integer.MIN_VALUE) {
               waterTop = y;
               waterColour = argb;
            } else if (waterTop - y >= 255) {
               break;
            }
         }

         if (waterTop != Integer.MIN_VALUE) {
            colour[i] = waterColour;
            height[i] = (short)waterTop;
            water[i] = (byte)255;
         }
      }

      private int mapColour(Block block) {
         Integer known = this.mapColours.get(block);
         if (known == null) {
            String name = Reg.blockId(block);
            int c = GameColours.get().colour(name);
            boolean wet = name.equals("minecraft:water") || name.equals("minecraft:bubble_column") || name.endsWith("seagrass") || name.equals("minecraft:kelp")
               || name.equals("minecraft:kelp_plant");
            if (wet && c == 0) {
               c = WATER;
            }

            known = c == 0 ? 0 : c & 0xFFFFFF | (wet ? 0x01000000 : 0x02000000);
            this.mapColours.put(block, known);
         }

         return known;
      }
   }
}
