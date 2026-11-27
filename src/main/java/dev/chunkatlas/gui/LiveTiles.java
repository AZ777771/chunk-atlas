package dev.chunkatlas.gui;

import dev.chunkatlas.GameColours;
import dev.chunkatlas.LiveChunks;
import dev.chunkatlas.engine.BiomeIndex;
import dev.chunkatlas.engine.BiomePalette;
import dev.chunkatlas.engine.Look;
import dev.chunkatlas.engine.RegionFacts;
import dev.chunkatlas.engine.Shader;
import dev.chunkatlas.engine.Surface;
import dev.chunkatlas.engine.TileRenderer;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Tex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;

final class LiveTiles {
   private static final int MAX_CHUNKS = 16384;

   private static final long TAKE_NS = 2_500_000L;

   private static final int PAINTS = 4;
   private static final long REPAINT_MS = 250L;

   private static final long REREAD_MS = 10_000L;
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final Map<Long, Region> regions = new LinkedHashMap<>(16, 0.75F, true);
   private final Set<Long> fresh = new HashSet<>();
   private int chunks;
   private long rereadAt;

   private int taken;

   private static final class Region {
      final int rx;
      final int rz;
      final int[][] pixels = new int[1024][];
      final long[] at = new long[1024];
      int count;
      Tex texture;
      int size;
      boolean dirty;
      long paintedAt;

      Region(int rx, int rz) {
         this.rx = rx;
         this.rz = rz;
      }
   }

   LiveTiles(MapScreen map) {
      this.map = map;
   }

   private static long key(int x, int z) {
      return (long)x << 32 | z & 0xFFFFFFFFL;
   }

   void draw(Gfx g, int baseRx, int baseRz, int level) {
      String dim = this.map.seedDimension();
      int style = this.map.liveStyle();

      if (this.map.live() == null || dim == null || style < 0 || this.map.liveCave()) {
         return;
      }

      LiveChunks.watch(dim);
      this.take(dim, style);
      this.reread();
      if (this.regions.isEmpty()) {
         return;
      }

      double zoom = this.map.seedZoom();
      double halfW = this.map.width / 2.0 / zoom;
      double halfH = (this.map.seedBottom() - this.map.seedTop()) / 2.0 / zoom;
      int rx0 = Math.floorDiv((int)Math.floor(this.map.seedCx() - halfW), 512);
      int rx1 = Math.floorDiv((int)Math.floor(this.map.seedCx() + halfW), 512);
      int rz0 = Math.floorDiv((int)Math.floor(this.map.seedCz() - halfH), 512);
      int rz1 = Math.floorDiv((int)Math.floor(this.map.seedCz() + halfH), 512);
      int size = 512 >> Math.max(0, Math.min(4, level));
      int paints = PAINTS;
      long now = System.currentTimeMillis();
      for (Region r : new ArrayList<>(this.regions.values())) {
         boolean seen = r.rx >= rx0 && r.rx <= rx1 && r.rz >= rz0 && r.rz <= rz1;
         if (!seen) {
            this.release(r);
            continue;
         }

         if ((r.texture == null || r.size != size || r.dirty && now - r.paintedAt >= REPAINT_MS) && paints > 0) {
            this.paint(r, size);
            r.paintedAt = now;
            paints--;
         }

         if (r.texture != null) {
            g.blit(r.texture, (r.rx - baseRx) * 512, (r.rz - baseRz) * 512, 0.0F, 0.0F, 512, 512, r.size, r.size, r.size, r.size);
         }
      }
   }

   private void take(String dim, int style) {
      long start = System.nanoTime();
      Shader.Options options = (style & TileRenderer.DETAILED) != 0 ? TileRenderer.options(style, 1, GameColours.get()) : null;
      LiveChunks.Columns c;
      while (System.nanoTime() - start < TAKE_NS && (c = LiveChunks.poll()) != null) {
         if (!c.dimension().equals(dim)) {
            continue;
         }

         int[] px;
         try {
            px = shade(c, style, options);
         } catch (RuntimeException e) {
            continue;
         }

         long k = key(c.cx() >> 5, c.cz() >> 5);
         int rx = c.cx() >> 5;
         int rz = c.cz() >> 5;
         Region r = this.regions.computeIfAbsent(k, x -> new Region(rx, rz));
         int i = (c.cz() & 31) * 32 + (c.cx() & 31);
         if (r.pixels[i] == null) {
            r.count++;
            this.chunks++;
         }

         r.pixels[i] = px;
         r.at[i] = c.at();
         r.dirty = true;
         this.fresh.add(k);
         this.taken++;
      }

      Iterator<Region> it = this.regions.values().iterator();
      while (this.chunks > MAX_CHUNKS && it.hasNext()) {
         Region r = it.next();
         this.chunks -= r.count;
         this.release(r);
         it.remove();
      }
   }

   private static int[] shade(LiveChunks.Columns c, int style, Shader.Options options) {
      int a = LiveChunks.APRON;
      int side = LiveChunks.SIDE;
      boolean biomes = (style & TileRenderer.BIOMES) != 0;
      int[] out;
      if (options != null) {
         Surface s = c.surface();
         if (biomes) {
            s = s.copy();
            for (int i = 0; i < s.top.length; i++) {
               int colour = s.drawn(i) ? biomeColour(s.biome[i]) : 0;
               if (colour != 0) {
                  s.top[i] = Surface.top(Look.of(colour, 255, Look.TINT_NONE, (s.top[i] & Surface.LEAVES) != 0 ? Look.LEAVES : 0L, 0));
                  s.depth[i] = 0;
                  s.cover[i] = 0;
               }
            }
         }

         out = Shader.shade(s, a, options, new int[256]);
      } else {
         int[] base = c.colour();
         if (biomes) {
            base = base.clone();
            for (int i = 0; i < base.length; i++) {
               int colour = base[i] != 0 ? biomeColour(c.surface().biome[i]) : 0;
               if (colour != 0) {
                  base[i] = 0xFF000000 | colour;
               }
            }
         }

         int[] all = TileRenderer.shadeVanilla(base, c.height(), c.water(), side, (style & TileRenderer.CONTOURS) != 0);
         out = new int[256];
         for (int z = 0; z < 16; z++) {
            System.arraycopy(all, (z + a) * side + a, out, z * 16, 16);
         }
      }

      return out;
   }

   private static int biomeColour(int biome) {
      String name = biome < 0 ? null : BiomeIndex.name(biome);
      return name == null ? 0 : BiomePalette.colour(name);
   }

   private void paint(Region r, int size) {
      if (r.texture == null || r.size != size) {
         this.release(r);
         r.size = size;
         r.texture = Tex.create("Chunk Atlas live chunks", size, size);
      }

      int per = size / 32;
      int step = 16 / per;
      int[] abgr = new int[size * size];
      for (int i = 0; i < 1024; i++) {
         int[] px = r.pixels[i];
         if (px == null) {
            continue;
         }

         int ox = (i & 31) * per;
         int oz = (i >> 5) * per;
         for (int z = 0; z < per; z++) {
            for (int x = 0; x < per; x++) {
               abgr[(oz + z) * size + ox + x] = abgr(average(px, x * step, z * step, step));
            }
         }
      }

      r.texture.putAbgr(abgr);
      r.texture.upload();
      r.dirty = false;
   }

   private static int average(int[] px, int x0, int z0, int step) {
      if (step == 1) {
         return px[z0 * 16 + x0];
      }

      int red = 0;
      int green = 0;
      int blue = 0;
      int n = 0;
      for (int z = z0; z < z0 + step; z++) {
         for (int x = x0; x < x0 + step; x++) {
            int c = px[z * 16 + x];
            if (c >>> 24 != 0) {
               red += c >> 16 & 0xFF;
               green += c >> 8 & 0xFF;
               blue += c & 0xFF;
               n++;
            }
         }
      }

      return n == 0 ? 0 : 0xFF000000 | red / n << 16 | green / n << 8 | blue / n;
   }

   private static int abgr(int c) {
      return c & 0xFF00FF00 | (c >> 16 & 0xFF) | (c & 0xFF) << 16;
   }

   private void reread() {
      long now = System.currentTimeMillis();
      if (this.fresh.isEmpty() || now - this.rereadAt < REREAD_MS) {
         return;
      }

      this.rereadAt = now;
      this.map.reread(Set.copyOf(this.fresh));
      this.fresh.clear();
   }

   void saved(long region, long modified, long[] drawn, RegionFacts facts) {
      Region r = this.regions.get(region);
      if (r == null) {
         return;
      }

      for (int i = 0; i < 1024; i++) {
         if (r.pixels[i] != null && (drawn[i >> 6] & 1L << (i & 63)) != 0L && modified >= r.at[i] && (facts == null || facts.state[i] == RegionFacts.FULL)) {
            r.pixels[i] = null;
            r.count--;
            this.chunks--;
            r.dirty = true;
         }
      }

      if (r.count == 0) {
         this.release(r);
         this.regions.remove(region);
      }
   }

   int[] bounds() {
      if (this.regions.isEmpty()) {
         return null;
      }

      int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
      for (Region r : this.regions.values()) {
         b[0] = Math.min(b[0], r.rx);
         b[1] = Math.min(b[1], r.rz);
         b[2] = Math.max(b[2], r.rx);
         b[3] = Math.max(b[3], r.rz);
      }

      return b;
   }

   private void release(Region r) {
      if (r.texture != null) {
         r.texture.close();
         r.texture = null;
      }
   }

   void clear() {
      for (Region r : this.regions.values()) {
         this.release(r);
      }

      this.regions.clear();
      this.fresh.clear();
      this.chunks = 0;
   }

   String tourSummary() {
      int pictures = 0;
      List<String> where = new ArrayList<>();
      for (Region r : this.regions.values()) {
         pictures += r.texture == null ? 0 : 1;
         if (where.size() < 6) {
            where.add(r.rx + "," + r.rz + " (" + r.count + ")");
         }
      }

      return LiveChunks.tourCounts() + "; " + this.taken + " chunks taken live, " + this.chunks + " held in " + this.regions.size() + " regions " + where + ", " + pictures + " pictures";
   }

   boolean tourHas(int cx, int cz) {
      Region r = this.regions.get(key(cx >> 5, cz >> 5));
      return r != null && r.pixels[(cz & 31) * 32 + (cx & 31)] != null;
   }
}
