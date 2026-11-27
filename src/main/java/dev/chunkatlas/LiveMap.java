package dev.chunkatlas;

import dev.chunkatlas.engine.LiveRegion;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.port.Heights;
import dev.chunkatlas.port.Ids;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

public final class LiveMap {
   private static final long BUDGET_NS = 2_000_000L;
   private static final long NEAR_MS = 4_000L;
   private static final long FAR_MS = 20_000L;
   private static final long FLUSH_MS = 10_000L;
   private static final long UNLOAD_MS = 120_000L;
   private static final int WATER = -12566273;

   private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Chunk Atlas live map");
      t.setDaemon(true);
      return t;
   });

   private static Path folder;
   private static String dimension;
   private static ClientLevel level;
   private static final Map<Long, LiveRegion> regions = new HashMap<>();
   private static final Map<Long, Long> regionUsed = new HashMap<>();
   private static long lastFlush;

   private static CompletableFuture<Void> writing = CompletableFuture.completedFuture(null);

   private static final int[] colour = new int[256];
   private static final short[] height = new short[256];
   private static final byte[] water = new byte[256];
   private static int radius = 8;
   private static final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

   public static volatile boolean testInSingleplayer;

   private LiveMap() {
   }

   static void register() {
      ChunkScanner.add(READER);

      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         if (folder != null && (mc.level == null || mc.hasSingleplayerServer() && !testInSingleplayer)) {
            leave();
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(LiveMap::leave));
   }

   public static Path folder() {
      return folder;
   }

   public static void flushNow() {
      try {
         save().get(2, TimeUnit.SECONDS);
      } catch (Exception ignored) {
      }
   }

   public static CompletableFuture<Void> save() {
      flush();
      return writing.handle((done, failed) -> null);
   }

   private static boolean prepare(Minecraft mc, ClientLevel now) {
      if (mc.hasSingleplayerServer() && !testInSingleplayer) {
         if (folder != null) {
            leave();
         }

         return false;
      }

      if (folder == null) {
         folder = folderFor(mc.getCurrentServer());
      }

      String dim = Ids.of(now.dimension());
      if (now != level || !dim.equals(dimension)) {
         flush();
         regions.clear();
         regionUsed.clear();
         level = now;
         dimension = dim;
         ChunkScanner.again(READER);
      }

      radius = mc.options.getEffectiveRenderDistance() + 1;
      long ms = System.currentTimeMillis();
      if (ms - lastFlush > FLUSH_MS) {
         flush();
         unloadOld(ms);
      }

      return true;
   }

   private static final ChunkScanner.Reader READER = new ChunkScanner.Reader() {
      @Override
      public boolean prepare(Minecraft mc, ClientLevel now, Entity viewer) {
         return LiveMap.prepare(mc, now);
      }

      @Override
      public int radius() {
         return radius;
      }

      @Override
      public long nearMs() {
         return NEAR_MS;
      }

      @Override
      public long farMs() {
         return FAR_MS;
      }

      @Override
      public long budgetNs() {
         return BUDGET_NS;
      }

      @Override
      public boolean quickCheck() {
         return true;
      }

      @Override
      public void read(ClientLevel now, LevelChunk chunk, ChunkScanner.Columns shared, long ms) {
         if (scan(now, chunk, shared.detail())) {
            int cx = dev.chunkatlas.port.Pos.cx(chunk.getPos());
            int cz = dev.chunkatlas.port.Pos.cz(chunk.getPos());
            region(cx >> 5, cz >> 5, ms).put(cx, cz, colour, height, water, shared.top, shared.cover, shared.biome, shared.light);
         }
      }
   };

   private static boolean scan(ClientLevel level, LevelChunk chunk, ChunkScanner.Columns d) {
      int minY = Heights.min(level);
      int x0 = chunk.getPos().getMinBlockX();
      int z0 = chunk.getPos().getMinBlockZ();
      boolean any = false;
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int i = lz << 4 | lx;
            colour[i] = 0;
            height[i] = (short)minY;
            water[i] = 0;
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            boolean detail = d.has[i];
            int waterTop = Integer.MIN_VALUE;
            int waterColour = 0;
            for (int y = top; y >= minY; y--) {
               BlockState state = chunk.getBlockState(pos.set(x0 + lx, y, z0 + lz));
               Block block = state.getBlock();
               boolean wet = wet(block);
               MapColor mc = block.defaultMapColor();
               int c = mc == MapColor.NONE ? (wet ? WATER : 0) : 0xFF000000 | mc.col;
               if (c == 0) {
                  continue;
               }

               if (!wet) {
                  if (waterTop != Integer.MIN_VALUE) {
                     colour[i] = waterColour;
                     height[i] = (short)waterTop;
                     water[i] = (byte)Math.min(255, waterTop - y);
                  } else {
                     colour[i] = c;
                     height[i] = (short)y;
                  }

                  break;
               }

               if (waterTop == Integer.MIN_VALUE) {
                  waterTop = y;
                  waterColour = c;
               }
            }

            if (colour[i] == 0 && waterTop != Integer.MIN_VALUE) {
               colour[i] = waterColour;
               height[i] = (short)waterTop;
               water[i] = (byte)255;
            }

            if (detail) {
               height[i] = d.height[i];
               water[i] = d.depth[i];
            }

            any |= colour[i] != 0 || detail;
         }
      }

      return any;
   }

   private static boolean wet(Block b) {
      return b == Blocks.WATER || b == Blocks.BUBBLE_COLUMN || b == Blocks.SEAGRASS || b == Blocks.TALL_SEAGRASS || b == Blocks.KELP || b == Blocks.KELP_PLANT;
   }

   private static LiveRegion region(int rx, int rz, long ms) {
      long k = WorldLayout.key(rx, rz);
      regionUsed.put(k, ms);
      LiveRegion r = regions.get(k);
      if (r == null) {
         LiveRegion fresh = new LiveRegion(rx, rz);
         r = fresh;
         regions.put(k, r);
         Path file = file(rx, rz);
         WRITER.execute(() -> {
            try {
               if (Files.isRegularFile(file)) {
                  fresh.fillFrom(LiveRegion.read(file, rx, rz));
               }
            } catch (IOException | RuntimeException e) {
            }
         });
      }

      return r;
   }

   private static Path file(int rx, int rz) {
      return folder.resolve(LiveRegion.folder(dimension)).resolve(LiveRegion.name(rx, rz));
   }

   private static void flush() {
      lastFlush = System.currentTimeMillis();
      if (folder == null || dimension == null) {
         return;
      }

      List<LiveRegion> dirty = new ArrayList<>();
      for (LiveRegion r : regions.values()) {
         if (r.dirty()) {
            dirty.add(r);
         }
      }

      if (dirty.isEmpty()) {
         return;
      }

      Path dimFolder = folder.resolve(LiveRegion.folder(dimension));
      writing = CompletableFuture.runAsync(() -> {
         for (LiveRegion r : dirty) {
            try {
               r.write(dimFolder.resolve(LiveRegion.name(r.rx, r.rz)));
            } catch (IOException | RuntimeException ignored) {
            }
         }
      }, WRITER);
   }

   private static void unloadOld(long ms) {
      Iterator<Map.Entry<Long, Long>> it = regionUsed.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Long, Long> e = it.next();
         LiveRegion r = regions.get(e.getKey());
         if (ms - e.getValue() > UNLOAD_MS && (r == null || !r.dirty())) {
            regions.remove(e.getKey());
            it.remove();
         }
      }
   }

   private static void leave() {
      flushNow();
      folder = null;
      dimension = null;
      level = null;
      regions.clear();
      regionUsed.clear();
   }

   private static Path folderFor(ServerData server) {
      String name = server == null ? "server" : server.name;
      String id = server == null ? "unknown" : server.isRealm() ? "realm " + server.name : server.ip;
      String clean = id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._ -]", "_").trim();
      Path f = ChunkAtlas.home().resolve("live").resolve(clean.isEmpty() ? "server" : clean);
      try {
         Files.createDirectories(f);
         Properties p = new Properties();
         p.setProperty("name", name == null ? clean : name);
         try (OutputStream out = Files.newOutputStream(f.resolve(LiveRegion.INFO))) {
            p.store(out, "Chunk Atlas live map");
         }
      } catch (IOException ignored) {
      }

      return f;
   }

   private static long key(int cx, int cz) {
      return (long)cx << 32 | cz & 0xFFFFFFFFL;
   }
}
