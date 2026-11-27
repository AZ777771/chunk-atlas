package dev.chunkatlas.gui;

import dev.chunkatlas.Settings;
import dev.chunkatlas.SpawnLight;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Heights;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Tex;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

final class SpawnLayer {
   private static final long BUDGET_NS = 2_000_000L;
   private static final long NEAR_MS = 2000L;
   private static final long FAR_MS = 8000L;

   private static final long PAINT_MS = 300L;
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final SpawnLight light = new SpawnLight();

   private final Long2ObjectOpenHashMap<byte[]> chunks = new Long2ObjectOpenHashMap<>();
   private final Long2LongOpenHashMap readAt = new Long2LongOpenHashMap();
   private final LongOpenHashSet dirty = new LongOpenHashSet();
   private final Map<Long, Tex> pictures = new HashMap<>();
   private ClientLevel level;
   private long paintedAt;

   SpawnLayer(MapScreen map) {
      this.map = map;
   }

   String unavailable() {
      return this.map.live() == null || this.mc.level == null || this.mc.player == null ? "chunkatlas.map.layers.spawnable.playing" : null;
   }

   Ui.Item layerItem() {
      String why = this.unavailable();
      return Ui.Item.check(Ui.t("chunkatlas.map.layers.spawnable"), Settings.spawnable(), why == null ? () -> Settings.spawnable(!Settings.spawnable()) : null)
         .withTip(Ui.t(why == null ? "chunkatlas.map.layers.spawnable.tip" : why));
   }

   private boolean on() {
      return Settings.spawnable() && this.unavailable() == null && Ids.of(this.mc.level.dimension()).equals(this.map.seedDimension());
   }

   void draw(Gfx g, int baseRx, int baseRz) {
      if (!this.on()) {
         this.release();
         return;
      }

      if (this.mc.level != this.level) {
         this.release();
         this.level = this.mc.level;
      }

      long now = System.currentTimeMillis();
      this.read(now);
      if (!this.dirty.isEmpty() && now - this.paintedAt >= PAINT_MS) {
         this.paint();
         this.paintedAt = now;
      }

      for (Map.Entry<Long, Tex> e : this.pictures.entrySet()) {
         int x = ((int)(e.getKey() >> 32) - baseRx) * 512;
         int z = ((int)e.getKey().longValue() - baseRz) * 512;
         g.blit(e.getValue(), x, z, 0.0F, 0.0F, 512, 512, 512, 512, 512, 512);
      }
   }

   private void read(long now) {
      ClientLevel level = this.level;
      Entity viewer = this.mc.getCameraEntity() == null ? this.mc.player : this.mc.getCameraEntity();
      int pcx = viewer.getBlockX() >> 4;
      int pcz = viewer.getBlockZ() >> 4;
      int radius = this.mc.options.getEffectiveRenderDistance() + 1;

      List<Long> gone = new ArrayList<>();
      for (long key : this.chunks.keySet()) {
         int cx = (int)(key >> 32);
         int cz = (int)key;
         if (Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) > radius || !level.getChunkSource().hasChunk(cx, cz)) {
            gone.add(key);
         }
      }

      for (long key : gone) {
         this.chunks.remove(key);
         this.readAt.remove(key);
         this.dirty.add(region(key));
      }

      long start = System.nanoTime();
      for (int pass = 0; pass < 2; pass++) {
         for (int ring = 0; ring <= radius; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
               for (int dx = -ring; dx <= ring; dx++) {
                  if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                     continue;
                  }

                  if (System.nanoTime() - start > BUDGET_NS) {
                     return;
                  }

                  int cx = pcx + dx;
                  int cz = pcz + dz;
                  long key = key(cx, cz);
                  boolean known = this.chunks.containsKey(key);

                  if (pass == 0 ? known : !known || now - this.readAt.get(key) < (ring <= 2 ? NEAR_MS : FAR_MS)) {
                     continue;
                  }

                  if (!level.getChunkSource().hasChunk(cx, cz)) {
                     continue;
                  }

                  this.chunks.put(key, this.column(level, level.getChunk(cx, cz)));
                  this.readAt.put(key, now);
                  this.dirty.add(region(key));
               }
            }
         }
      }
   }

   private byte[] column(ClientLevel level, LevelChunk chunk) {
      byte[] out = new byte[256];
      int x0 = chunk.getPos().getMinBlockX();
      int z0 = chunk.getPos().getMinBlockZ();
      int minY = Heights.min(level);
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            out[lz << 4 | lx] = top <= minY ? SpawnLight.NONE : this.light.column(level, chunk, x0 + lx, z0 + lz, top, minY, true);
         }
      }

      return out;
   }

   private void paint() {
      int[] abgr = new int[512 * 512];
      for (long region : this.dirty) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         java.util.Arrays.fill(abgr, 0);
         boolean any = false;
         for (int cz = 0; cz < 32; cz++) {
            for (int cx = 0; cx < 32; cx++) {
               byte[] col = this.chunks.get(key(rx * 32 + cx, rz * 32 + cz));
               if (col == null) {
                  continue;
               }

               for (int i = 0; i < 256; i++) {
                  int tint = SpawnLight.tint(col[i]);
                  if (tint != 0) {
                     any = true;

                     abgr[(cz * 16 + (i >> 4)) * 512 + cx * 16 + (i & 15)] = tint & 0xFF00FF00 | (tint >> 16 & 0xFF) | (tint & 0xFF) << 16;
                  }
               }
            }
         }

         Tex p = this.pictures.get(region);
         if (!any) {
            if (p != null) {
               p.close();
               this.pictures.remove(region);
            }

            continue;
         }

         if (p == null) {
            p = Tex.create("Chunk Atlas spawnable blocks", 512, 512);
            this.pictures.put(region, p);
         }

         p.putAbgr(abgr);
         p.upload();
      }

      this.dirty.clear();
   }

   void release() {
      for (Tex p : this.pictures.values()) {
         p.close();
      }

      this.pictures.clear();
      this.chunks.clear();
      this.readAt.clear();
      this.dirty.clear();
      this.level = null;
   }

   private static long key(int cx, int cz) {
      return (long)cx << 32 | cz & 0xFFFFFFFFL;
   }

   private static long region(long chunk) {
      return key((int)(chunk >> 32) >> 5, (int)chunk >> 5);
   }
}
