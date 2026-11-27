package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class PasteTerrain {
   public static final long MAX_PIXELS = 4L << 20;

   public static final int MAX_PICTURE_CHUNKS = 40_000;
   private static final int GROUND_CHUNKS = 256;

   private PasteTerrain() {
   }

   public record Picture(int step, Map<Long, int[]> regions) {
      public int side() {
         return 512 / this.step;
      }
   }

   public interface SeedGround {
      double at(int blockX, int blockZ);
   }

   public record Lift(int dy, int pasted, int land, int min, int max) {
   }

   public static PasteTerrain.Picture picture(WorldLayout.Dimension dimension, ChunkSelection chunks, BlockColours colours, int style, BooleanSupplier cancelled) {
      if (chunks.size() > MAX_PICTURE_CHUNKS) {
         return null;
      }

      int regions = chunks.regions().size();
      int step = 1;
      while (step < 16 && regions * (512L / step) * (512L / step) > MAX_PIXELS) {
         step *= 2;
      }

      style = style & ~TileRenderer.SLICE | TileRenderer.DETAILED;
      ChunkDecoder decoder = decoder(colours, style);
      Map<Long, int[]> out = new HashMap<>();
      for (long region : chunks.regions()) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         long[] bits = chunks.bits(rx, rz);
         Path file = dimension.region().resolve(RegionFile.name(rx, rz));
         if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            continue;
         }

         Surface surface = new Surface(512);
         boolean[] drawn = new boolean[1024];
         try (RegionFile f = RegionFile.open(file)) {
            for (int i = 0; i < 1024; i++) {
               if ((bits[i >> 6] & 1L << (i & 63)) == 0L || !f.header().present(i)) {
                  continue;
               }

               if (cancelled.getAsBoolean()) {
                  return null;
               }

               Map<String, Object> root = read(f, i, decoder);
               if (root == null) {
                  continue;
               }

               int ox = (i & 31) * 16;
               int oz = (i >> 5) * 16;
               for (int z = 0; z < 16; z++) {
                  int row = (oz + z) * 512 + ox;
                  for (int x = 0; x < 16; x++) {
                     int at = z * 16 + x;
                     surface.top[row + x] = decoder.top[at];
                     surface.cover[row + x] = decoder.cover[at];
                     surface.height[row + x] = (short)decoder.height[at];
                     surface.depth[row + x] = (byte)decoder.water[at];
                     surface.biome[row + x] = decoder.biome[at];
                     surface.light[row + x] = decoder.light[at];
                  }
               }

               drawn[i] = true;
            }
         } catch (IOException | RuntimeException e) {
            continue;
         }

         int[] shaded = Shader.shade(surface, 0, TileRenderer.options(style, 1, colours), new int[512 * 512]);
         int side = 512 / step;
         int[] px = new int[side * side];
         for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
               int bx = x * step;
               int bz = z * step;
               px[z * side + x] = drawn[(bz >> 4) * 32 + (bx >> 4)] ? shaded[bz * 512 + bx] : 0;
            }
         }

         out.put(region, px);
      }

      return cancelled.getAsBoolean() ? null : new PasteTerrain.Picture(step, out);
   }

   private static final int GROUND_ABOVE_BOTTOM = 16;

   public static PasteTerrain.Lift lift(
      WorldLayout.Dimension source, ChunkSelection chunks, WorldLayout.Dimension dest, BlendRing.Saved saved, int dx, int dz, int blend,
      PasteTerrain.SeedGround seed, BlockColours colours, BooleanSupplier cancelled
   ) {
      ChunkDecoder decoder = decoder(colours, TileRenderer.DETAILED);
      List<int[]> edge = new ArrayList<>();
      List<int[]> inside = new ArrayList<>();
      chunks.forEach((x, z) -> {
         boolean onEdge = !chunks.contains(x - 1, z) || !chunks.contains(x + 1, z) || !chunks.contains(x, z - 1) || !chunks.contains(x, z + 1);
         (onEdge ? edge : inside).add(new int[]{x, z});
      });
      Columns pasted = new Columns();
      try (OpenRegions files = new OpenRegions(source.region())) {
         for (int[] c : spread(edge, GROUND_CHUNKS)) {
            if (cancelled.getAsBoolean()) {
               return null;
            }

            files.read(c[0], c[1], decoder, pasted, true);
         }

         Columns tops = new Columns();
         for (int[] c : spread(inside, GROUND_CHUNKS / 2)) {
            if (cancelled.getAsBoolean()) {
               return null;
            }

            files.read(c[0], c[1], decoder, tops, false);
         }

         pasted.top = Math.max(pasted.top, tops.top);
         pasted.low = Math.min(pasted.low, tops.low);
         pasted.minY = Math.min(pasted.minY, tops.minY);
         pasted.maxY = Math.max(pasted.maxY, tops.maxY);
      }

      ChunkSelection moved = new ChunkSelection();
      chunks.forEach((x, z) -> moved.add(x + dx, z + dz));
      int b = Math.min(Math.max(blend, 0), ChunkCopy.MAX_BLEND);
      List<int[]> round = new ArrayList<>();
      BlendRing.around(moved, b + 1, b + 1, null, null).forEach((x, z) -> round.add(new int[]{x, z}));
      Columns land = new Columns();
      try (OpenRegions files = new OpenRegions(dest.region())) {
         for (int[] c : spread(round, GROUND_CHUNKS)) {
            if (cancelled.getAsBoolean()) {
               return null;
            }

            if (saved != null && saved.has(c[0], c[1])) {
               files.read(c[0], c[1], decoder, land, true);
            } else if (seed != null) {
               for (int k = 0; k < 4; k++) {
                  double y = seed.at(c[0] * 16 + 4 + (k & 1) * 8, c[1] * 16 + 4 + (k >> 1) * 8);
                  if (!Double.isNaN(y)) {
                     land.add((int)Math.round(y));
                  }
               }
            }
         }
      }

      if (pasted.size == 0 || land.size == 0) {
         return null;
      }

      int p = pasted.median();
      int l = land.median();

      int minY = land.minY != Integer.MAX_VALUE ? land.minY : pasted.minY;
      int maxY = land.maxY != Integer.MIN_VALUE ? land.maxY : pasted.maxY;

      int lo = minY == Integer.MAX_VALUE ? Integer.MIN_VALUE / 2 : minY + GROUND_ABOVE_BOTTOM - p;
      int hi = maxY == Integer.MIN_VALUE ? Integer.MAX_VALUE / 2 : maxY - pasted.top;
      int dy = l - p;
      if (lo <= hi) {
         dy = Math.max(lo, Math.min(hi, dy));
      } else {
         dy = 0;
      }

      return new PasteTerrain.Lift(dy, p, l, lo, hi);
   }

   private static ChunkDecoder decoder(BlockColours colours, int style) {
      ChunkDecoder decoder = new ChunkDecoder(colours);
      decoder.detailed = true;
      decoder.night = (style & TileRenderer.NIGHT) != 0;
      decoder.biomes = (style & TileRenderer.BIOMES) != 0;
      return decoder;
   }

   private static Map<String, Object> read(RegionFile f, int i, ChunkDecoder decoder) {
      try {
         RegionFile.Chunk chunk = f.chunk(i);
         if (!chunk.ok()) {
            return null;
         }

         Map<String, Object> root = Nbt.read(chunk.data(), decoder.night ? ChunkDecoder.SKIP_BUT_BIOMES_AND_LIGHT : ChunkDecoder.SKIP_BUT_BIOMES, ChunkDecoder.COUNT);
         return decoder.decode(root, ChunkDecoder.facts(root)) ? root : null;
      } catch (IOException | RuntimeException e) {
         return null;
      }
   }

   private static List<int[]> spread(List<int[]> all, int n) {
      if (all.size() <= n) {
         return all;
      }

      List<int[]> out = new ArrayList<>(n);
      for (int k = 0; k < n; k++) {
         out.add(all.get((int)((long)k * all.size() / n)));
      }

      return out;
   }

   private static final class Columns {
      int[] values = new int[1024];
      int size;
      int top = Integer.MIN_VALUE;
      int low = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxY = Integer.MIN_VALUE;

      void add(int y) {
         if (this.size == this.values.length) {
            this.values = Arrays.copyOf(this.values, this.size * 2);
         }

         this.values[this.size++] = y;
      }

      int median() {
         int[] v = Arrays.copyOf(this.values, this.size);
         Arrays.sort(v);
         return v[v.length / 2];
      }
   }

   private static final class OpenRegions implements AutoCloseable {
      private final Path folder;
      private final Map<Long, RegionFile> open = new HashMap<>();

      OpenRegions(Path folder) {
         this.folder = folder;
      }

      void read(int cx, int cz, ChunkDecoder decoder, Columns into, boolean ground) {
         long key = WorldLayout.key(cx >> 5, cz >> 5);
         RegionFile f = this.open.get(key);
         if (f == null && !this.open.containsKey(key)) {
            Path file = this.folder.resolve(RegionFile.name(cx >> 5, cz >> 5));
            try {
               f = Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? RegionFile.open(file) : null;
            } catch (IOException | RuntimeException e) {
               f = null;
            }

            this.open.put(key, f);
         }

         int i = (cz & 31) * 32 + (cx & 31);
         if (f == null || !f.header().present(i)) {
            return;
         }

         Map<String, Object> root = PasteTerrain.read(f, i, decoder);
         if (root == null) {
            return;
         }

         if (Nbt.list(root, "sections") instanceof List<?> sections) {
            for (Object s : sections) {
               long y = Nbt.number(s, "Y", Long.MIN_VALUE);
               if (y != Long.MIN_VALUE && Nbt.has(s, "block_states")) {
                  into.minY = (int)Math.min(into.minY, y * 16);
                  into.maxY = (int)Math.max(into.maxY, y * 16 + 15);
               }
            }
         }

         for (int at = 0; at < 256; at++) {
            if (decoder.top[at] == 0 && decoder.water[at] == 0) {
               continue;
            }

            into.top = Math.max(into.top, decoder.height[at]);
            if ((decoder.top[at] & Surface.LEAVES) != 0) {
               continue;
            }

            int y = decoder.height[at];
            into.low = Math.min(into.low, y - Math.min(decoder.water[at], 254));
            if (ground) {
               into.add(y);
            }
         }
      }

      @Override
      public void close() {
         for (RegionFile f : this.open.values()) {
            try {
               if (f != null) {
                  f.close();
               }
            } catch (IOException ignored) {
            }
         }
      }
   }
}
