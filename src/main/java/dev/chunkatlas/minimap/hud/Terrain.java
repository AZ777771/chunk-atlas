package dev.chunkatlas.minimap.hud;

import dev.chunkatlas.Perf;
import dev.chunkatlas.SpawnLight;
import dev.chunkatlas.minimap.Colours;
import dev.chunkatlas.minimap.map.FarTiles;
import dev.chunkatlas.minimap.map.MapCache;
import dev.chunkatlas.minimap.map.SeedTiles;
import dev.chunkatlas.minimap.map.Tile;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Tex;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

final class Terrain {
   private static final int MAX_SIDE = 1024;
   private static final long EMPTY = Long.MIN_VALUE;

   private Tex tex;

   private int side;
   private int shift = -1;
   private int cellBits;
   private long[] keys = new long[0];
   private int options = Integer.MIN_VALUE;
   private int mapGeneration = -1;
   private int seedGeneration = -1;
   private int farGeneration = -1;

   private int a0x = 1;
   private int a0z = 1;
   private int a1x;
   private int a1z;
   private final IntArrayList dirty = new IntArrayList();
   private int drawnTexels;
   private float[] quads = new float[64 * 16];

   static final class Look {
      MapCache cache;
      SeedTiles seed;
      boolean seeded;
      FarTiles far;
      boolean useFar;
      Slimes slimes;
      boolean slimesShown;
      boolean light;
      boolean grid;
      int blank;

      double blocksPerTexel;

      int line;
   }

   static int shiftFor(double density) {
      double perChunk = density * 16.0;
      int s = 0;
      if (density >= 1.0) {
         while (s < 8 && (1 << s + 1) <= perChunk) {
            s++;
         }
      } else {
         while (s < 4 && (1 << s) < perChunk) {
            s++;
         }
      }

      return s;
   }

   void update(Look look, double px, double pz, double reach, double density, int options) {
      int s = shiftFor(density);
      int chunks = ((int)Math.ceil(2.0 * reach) >> 4) + 4;
      int want = Integer.highestOneBit(Math.max(1, (chunks << s) - 1)) << 1;
      while (want > MAX_SIDE && s > 0) {
         s--;
         want = Integer.highestOneBit(Math.max(1, (chunks << s) - 1)) << 1;
      }

      want = Math.min(MAX_SIDE, Math.max(16, want));
      if (this.tex == null || this.side != want) {
         if (this.tex != null) {
            this.tex.close();
         }

         this.side = want;
         this.tex = Tex.create("Minimap terrain", want, want);
         this.shift = -1;
      }

      look.line = look.grid ? Math.max(1, (int)Math.ceil(Math.max(look.blocksPerTexel, 1.0 / 16.0) * (1 << s) / 16.0 - 1.0E-6)) : 0;
      options = options * 31 + look.line;
      int mapGen = look.cache.generation();
      int seedGen = look.seed.generation();
      int farGen = look.far.generation();
      if (s != this.shift || options != this.options || mapGen != this.mapGeneration || seedGen != this.seedGeneration || farGen != this.farGeneration) {
         this.shift = s;
         this.options = options;
         this.mapGeneration = mapGen;
         this.seedGeneration = seedGen;
         this.farGeneration = farGen;
         this.cellBits = Integer.numberOfTrailingZeros(this.side) - s;
         int cells = 1 << this.cellBits * 2;
         if (this.keys.length != cells) {
            this.keys = new long[cells];
         }

         java.util.Arrays.fill(this.keys, EMPTY);
         this.a0x = 1;
         this.a1x = 0;
         this.a0z = 1;
         this.a1z = 0;
      }

      int c0x = (int)Math.floor(px - reach) >> 4;
      int c0z = (int)Math.floor(pz - reach) >> 4;
      int c1x = (int)Math.floor(px + reach) >> 4;
      int c1z = (int)Math.floor(pz + reach) >> 4;
      this.dirty.clear();
      this.drawnTexels = 0;

      for (int cz = c0z; cz <= c1z; cz++) {
         boolean rowSeen = cz >= this.a0z && cz <= this.a1z;
         for (int cx = c0x; cx <= c1x; cx++) {
            if (rowSeen && cx >= this.a0x && cx <= this.a1x) {
               cx = this.a1x;
               continue;
            }

            if (this.keys[this.cell(cx, cz)] != Tile.key(cx, cz)) {
               this.draw(look, cx, cz);
            }
         }
      }

      LongArrayList changed = look.cache.takeChanged();
      for (int i = 0; i < changed.size(); i++) {
         long key = changed.getLong(i);
         this.touch(look, (int)(key >> 32), (int)key, c0x, c0z, c1x, c1z);
      }

      this.regions(look, look.far.takeFresh(), c0x, c0z, c1x, c1z);
      this.regions(look, look.seed.takeFresh(), c0x, c0z, c1x, c1z);
      if (look.seeded) {
         for (int rz = c0z >> 5; rz <= c1z >> 5; rz++) {
            for (int rx = c0x >> 5; rx <= c1x >> 5; rx++) {
               look.seed.looked(rx, rz);
            }
         }
      }

      this.a0x = c0x;
      this.a0z = c0z;
      this.a1x = c1x;
      this.a1z = c1z;
      int uploaded = this.upload();
      Perf.terrain(this.drawnTexels, uploaded, this.dirty.size());
   }

   private int cell(int cx, int cz) {
      int mask = (1 << this.cellBits) - 1;
      return (cz & mask) << this.cellBits | cx & mask;
   }

   private void touch(Look look, int cx, int cz, int c0x, int c0z, int c1x, int c1z) {
      if (cx >= c0x && cx <= c1x && cz >= c0z && cz <= c1z) {
         this.draw(look, cx, cz);
      } else {
         int c = this.cell(cx, cz);
         if (this.keys[c] == Tile.key(cx, cz)) {
            this.keys[c] = EMPTY;
         }
      }
   }

   private void regions(Look look, LongArrayList fresh, int c0x, int c0z, int c1x, int c1z) {
      for (int i = 0; i < fresh.size(); i++) {
         long key = fresh.getLong(i);
         int rx = (int)(key >> 32);
         int rz = (int)key;
         int x0 = Math.max(c0x, rx << 5);
         int x1 = Math.min(c1x, (rx << 5) + 31);
         int z0 = Math.max(c0z, rz << 5);
         int z1 = Math.min(c1z, (rz << 5) + 31);
         for (int cz = z0; cz <= z1; cz++) {
            for (int cx = x0; cx <= x1; cx++) {
               if (look.cache.tile(cx, cz) == null) {
                  this.draw(look, cx, cz);
               }
            }
         }

         for (int cz = rz << 5; cz < (rz << 5) + 32; cz++) {
            for (int cx = rx << 5; cx < (rx << 5) + 32; cx++) {
               if (cx < x0 || cx > x1 || cz < z0 || cz > z1) {
                  int c = this.cell(cx, cz);
                  if (this.keys[c] == Tile.key(cx, cz)) {
                     this.keys[c] = EMPTY;
                  }
               }
            }
         }
      }
   }

   private void draw(Look look, int cx, int cz) {
      int c = this.cell(cx, cz);
      this.keys[c] = Tile.key(cx, cz);
      this.dirty.add(c);
      Tile tile = look.cache.tile(cx, cz);
      int mask = (1 << this.cellBits) - 1;
      int s = this.shift;
      int n = 1 << s;
      int u0 = (cx & mask) << s;
      int v0 = (cz & mask) << s;
      boolean slime = tile != null && look.slimesShown && look.slimes.slime(cx, cz);
      int line = look.line;
      int bx0 = cx << 4;
      int bz0 = cz << 4;
      Tex image = this.tex;
      for (int v = 0; v < n; v++) {
         int lz = s >= 4 ? v >> s - 4 : (v << 4 - s) + (1 << 4 - s >> 1);
         for (int u = 0; u < n; u++) {
            int lx = s >= 4 ? u >> s - 4 : (u << 4 - s) + (1 << 4 - s >> 1);
            int i = lz << 4 | lx;
            int colour;
            if (tile != null) {
               colour = tile.pixels[i];
            } else {
               colour = look.useFar ? look.far.colour(bx0 + lx, bz0 + lz) : 0;
               if (colour == 0 && look.seeded) {
                  colour = look.seed.colour(bx0 + lx, bz0 + lz);
               }
            }

            if (colour == 0) {
               colour = look.blank;
            }

            if (tile != null) {
               if (look.light) {
                  int tint = SpawnLight.tint(tile.spawn(i));
                  if (tint != 0) {
                     colour = Colours.blend(colour, tint);
                  }
               }

               if (slime) {
                  colour = Colours.blend(colour, 0x6040E040);
               }
            }

            if (u < line || v < line) {
               colour = Colours.blend(colour, 0x60000000);
            }

            image.set(u0 + u, v0 + v, colour);
         }
      }

      this.drawnTexels += n * n;
   }

   private int upload() {
      int count = this.dirty.size();
      if (count == 0) {
         return 0;
      }

      int n = 1 << this.shift;
      if ((long)count * n * n * 4L > (long)this.side * this.side || count > 512) {
         this.tex.upload();
         return this.side * this.side;
      }

      int[] cells = this.dirty.toIntArray();
      java.util.Arrays.sort(cells);
      int mask = (1 << this.cellBits) - 1;
      int sent = 0;
      for (int i = 0; i < cells.length; ) {
         int start = cells[i];
         int end = start;
         int j = i + 1;
         while (j < cells.length && (cells[j] == end || cells[j] == end + 1 && (cells[j] & mask) != 0)) {
            end = cells[j];
            j++;
         }

         int x = (start & mask) * n;
         int y = (start >> this.cellBits) * n;
         int w = (end - start + 1) * n;
         this.tex.upload(x, y, w, n);
         sent += w * n;
         i = j;
      }

      return sent;
   }

   void draw(Gfx g, float[] strips, int rows, float centreX, float centreY, double px, double pz, double rx, double rz, double fx, double fz, double scale, int argb) {
      if (this.tex == null || rows == 0) {
         return;
      }

      if (this.quads.length < rows * 16) {
         this.quads = new float[rows * 16];
      }

      double r = (1 << this.shift) / 16.0;
      double baseU = Math.floor(px * r / this.side) * this.side;
      double baseV = Math.floor(pz * r / this.side) * this.side;
      float[] q = this.quads;
      int k = 0;
      for (int row = 0; row < rows; row++) {
         float x0 = strips[row * 4];
         float x1 = strips[row * 4 + 1];
         float y0 = strips[row * 4 + 2];
         float y1 = strips[row * 4 + 3];
         k = this.corner(q, k, x0, y0, centreX, centreY, px, pz, rx, rz, fx, fz, scale, r, baseU, baseV);
         k = this.corner(q, k, x0, y1, centreX, centreY, px, pz, rx, rz, fx, fz, scale, r, baseU, baseV);
         k = this.corner(q, k, x1, y1, centreX, centreY, px, pz, rx, rz, fx, fz, scale, r, baseU, baseV);
         k = this.corner(q, k, x1, y0, centreX, centreY, px, pz, rx, rz, fx, fz, scale, r, baseU, baseV);
      }

      g.quads(this.tex, q, rows, argb);
   }

   private int corner(float[] q, int k, float sx, float sy, float centreX, float centreY, double px, double pz, double rx, double rz, double fx, double fz,
      double scale, double r, double baseU, double baseV) {
      double ox = sx - centreX;
      double oy = sy - centreY;
      double wx = px + (rx * ox - fx * oy) / scale;
      double wz = pz + (rz * ox - fz * oy) / scale;
      q[k] = sx;
      q[k + 1] = sy;
      q[k + 2] = (float)((wx * r - baseU) / this.side);
      q[k + 3] = (float)((wz * r - baseV) / this.side);
      return k + 4;
   }

   void close() {
      if (this.tex != null) {
         this.tex.close();
         this.tex = null;
      }
   }
}
