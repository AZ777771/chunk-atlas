package dev.chunkatlas.minimap.map;

import dev.chunkatlas.ChunkAtlas;
import dev.chunkatlas.GameColours;
import dev.chunkatlas.LiveMap;
import dev.chunkatlas.engine.Tile;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.engine.WorldMap;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Ids;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

public final class FarTiles {
   private static final long KEEP_PIXELS = 1L << 23;

   private static final int TAKE = 4;

   private static final long INDEX_MS = 30_000L;

   private WorldMap map;

   private boolean owned;
   private boolean opening;
   private Path world;
   private String dimension;
   private volatile WorldMap.Index index;
   private long indexAt;
   private boolean indexing;
   private final LinkedHashMap<Long, Held> held = new LinkedHashMap<>(64, 0.75F, true);
   private long pixels;
   private final LongArrayList fresh = new LongArrayList();
   private int generation;
   private List<Long> asked = List.of();
   private int askedLevel = -1;

   private long lastKey = Long.MIN_VALUE;
   private Held last;

   private record Held(int level, int size, int[] argb, boolean stale) {
   }

   public int generation() {
      return this.generation;
   }

   public LongArrayList takeFresh() {
      LongArrayList out = new LongArrayList(this.fresh);
      this.fresh.clear();
      return out;
   }

   public void release() {
      if (this.owned && this.map != null) {
         this.map.close();
      }

      if (this.owned) {
         this.map = null;
         this.index = null;
      }
   }

   public void frame(Minecraft mc) {
      Path want = null;
      boolean server = false;
      IntegratedServer sp = mc.getSingleplayerServer();
      if (mc.level != null) {
         if (sp != null) {
            want = sp.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
         } else {
            want = LiveMap.folder();
            server = true;
         }
      }

      String dim = mc.level == null ? null : Ids.of(mc.level.dimension());
      if (!Objects.equals(want, this.world) || !Objects.equals(dim, this.dimension)) {
         this.forget();
         this.world = want;
         this.dimension = dim;
      }

      if (want == null) {
         return;
      }

      if (!server) {
         WorldMap kept = dev.chunkatlas.gui.MapScreen.keptMap(want);
         if (kept != this.map) {
            this.map = kept;
            this.index = null;
            this.asked = List.of();
         }
      } else if (Game.screen(mc) instanceof dev.chunkatlas.gui.MapScreen) {
         this.release();
      } else if (this.map == null && !this.opening) {
         this.opening = true;
         Path folder = want;
         Thread.ofVirtual().start(() -> {
            WorldMap opened = null;
            try {
               opened = WorldMap.open(folder, ChunkAtlas.home(), GameColours.get());
               opened.style(dev.chunkatlas.Settings.lookBits());
            } catch (RuntimeException ignored) {
            }

            WorldMap done = opened;
            mc.execute(() -> {
               this.opening = false;
               if (done == null) {
                  return;
               }

               if (this.map == null && folder.equals(this.world) && !(Game.screen(mc) instanceof dev.chunkatlas.gui.MapScreen)) {
                  this.map = done;
                  this.owned = true;
                  this.index = null;
                  this.asked = List.of();
               } else {
                  done.close();
               }
            });
         });
      }

      WorldMap m = this.map;
      if (m == null) {
         return;
      }

      long now = System.currentTimeMillis();
      if (!this.indexing && (this.index == null || now - this.indexAt > INDEX_MS)) {
         this.indexing = true;
         this.indexAt = now;
         String d = dim;
         Thread.ofVirtual().start(() -> {
            WorldMap.Index read = null;
            try {
               read = m.index(d);
            } catch (RuntimeException ignored) {
            }

            WorldMap.Index done = read;
            mc.execute(() -> {
               this.indexing = false;
               if (m == this.map && d.equals(this.dimension) && done != null) {
                  this.index = done;
                  this.asked = List.of();
               }
            });
         });
      }

      if (m.cave() != Integer.MIN_VALUE) {
         return;
      }

      Tile t;
      int taken = 0;
      while (taken < TAKE && (t = m.poll()) != null) {
         if (!t.dimension.equals(this.dimension) || t.cave != Integer.MIN_VALUE || t.argb == null) {
            continue;
         }

         long key = WorldLayout.key(t.rx, t.rz);
         Held old = this.held.get(key);

         if (t.stale ? old == null : old == null || old.stale() || t.level <= old.level() || t.level == this.askedLevel) {
            this.pixels += (long)t.size * t.size - (old == null ? 0L : (long)old.size() * old.size());
            this.held.put(key, new Held(t.level, t.size, t.argb, t.stale));
            this.fresh.add(key);
            this.lastKey = Long.MIN_VALUE;
         }

         taken++;
      }

      if (this.pixels > KEEP_PIXELS) {
         Iterator<Map.Entry<Long, Held>> it = this.held.entrySet().iterator();
         while (this.pixels > KEEP_PIXELS && this.held.size() > 1 && it.hasNext()) {
            Map.Entry<Long, Held> e = it.next();
            this.pixels -= (long)e.getValue().size() * e.getValue().size();
            this.fresh.add(e.getKey());
            it.remove();
         }

         this.lastKey = Long.MIN_VALUE;
      }
   }

   public void want(int c0x, int c0z, int c1x, int c1z, int level, double px, double pz) {
      WorldMap m = this.map;
      WorldMap.Index i = this.index;
      if (m == null || i == null || m.cave() != Integer.MIN_VALUE) {
         return;
      }

      int rx0 = c0x >> 5;
      int rz0 = c0z >> 5;
      int rx1 = c1x >> 5;
      int rz1 = c1z >> 5;
      List<Long> regions = new ArrayList<>();
      for (int rz = rz0; rz <= rz1; rz++) {
         for (int rx = rx0; rx <= rx1; rx++) {
            long key = WorldLayout.key(rx, rz);
            Held h = this.held.get(key);
            if (i.regions().containsKey(key) && (h == null || h.level() > level || h.stale())) {
               regions.add(key);
            }
         }
      }

      double fx = px / 512.0 - 0.5;
      double fz = pz / 512.0 - 0.5;
      regions.sort(java.util.Comparator.comparingDouble(k -> {
         double dx = (int)(k >> 32) - fx;
         double dz = (int)(long)k - fz;
         return dx * dx + dz * dz;
      }));
      if (level != this.askedLevel || !regions.equals(this.asked)) {
         this.asked = regions;
         this.askedLevel = level;
         if (!regions.isEmpty()) {
            m.request(i, level, regions);
         }
      }
   }

   public int colour(int bx, int bz) {
      long key = WorldLayout.key(bx >> 9, bz >> 9);
      Held h;
      if (key == this.lastKey) {
         h = this.last;
      } else {
         h = this.held.get(key);
         this.lastKey = key;
         this.last = h;
      }

      if (h == null) {
         return 0;
      }

      int c = h.argb()[((bz & 511) >> h.level()) * h.size() + ((bx & 511) >> h.level())];
      return c >>> 24 == 0 ? 0 : c;
   }

   public boolean any() {
      return !this.held.isEmpty();
   }

   private void forget() {
      this.release();
      this.map = null;
      this.owned = false;
      this.index = null;
      this.held.clear();
      this.pixels = 0L;
      this.fresh.clear();
      this.asked = List.of();
      this.askedLevel = -1;
      this.lastKey = Long.MIN_VALUE;
      this.last = null;
      this.generation++;
   }

   public String stats() {
      return "far tiles: " + this.held.size() + " regions held (" + this.pixels + " px), " + (this.map == null ? "no source" : this.owned ? "live map" : "kept world")
         + ", " + (this.index == null ? "no index" : this.index.regions().size() + " regions on disk") + ", asked " + this.asked.size() + " at level " + this.askedLevel;
   }
}
