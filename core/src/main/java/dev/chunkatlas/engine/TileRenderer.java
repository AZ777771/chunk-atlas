package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class TileRenderer {
   private static final int NOTHING_FULL = -15987438;
   private static final int PARTIAL_A = -13881289;
   private static final int PARTIAL_B = -14276048;
   private static final int BAD_A = -8769488;
   private static final int BAD_B = -10738652;

   private TileRenderer() {
   }

   public static TileRenderer.Drawn draw(WorldLayout.RegionInfo region, Optional<Path> entitiesFile, BlockColours colours, BooleanSupplier cancelled) {
      return draw(region, entitiesFile, colours, cancelled, Integer.MIN_VALUE, 0, 0);
   }

   public static final int DRY = 1;
   public static final int CONTOURS = 2;
   public static final int BIOMES = 4;

   public static final int SLICE = 8;

   public static final int DETAILED = 16;
   public static final int SOLID_WATER = 32;
   public static final int SHADE_STRONG = 64;
   public static final int SHADE_OFF = 128;
   public static final int NO_BIOME_TINT = 256;
   public static final int HEIGHT_TINT = 512;
   public static final int NIGHT = 1024;

   public static final int HEIGHTMAP = 2048;

   public static final int DEPTHMAP = 4096;

   public static Shader.Options options(int style, int step, BlockColours colours) {
      int shading = (style & SHADE_OFF) != 0 ? Shader.SHADE_OFF : (style & SHADE_STRONG) != 0 ? Shader.SHADE_STRONG : Shader.SHADE_SOFT;
      long water = colours.look("minecraft:water");
      return new Shader.Options(
         shading,
         (style & SOLID_WATER) == 0,
         (style & NO_BIOME_TINT) == 0,
         (style & HEIGHT_TINT) != 0,
         (style & NIGHT) != 0,
         (style & CONTOURS) != 0,
         (style & DRY) != 0,
         step,
         water == 0L ? 0xB0B0B0 : Look.rgb(water),
         (biome, kind) -> colours.tint(BiomeIndex.name(biome), kind),
         (style & HEIGHTMAP) != 0 ? Shader.RAMP_HEIGHT : (style & DEPTHMAP) != 0 ? Shader.RAMP_DEPTH : Shader.RAMP_NONE
      );
   }

   public static TileRenderer.Drawn draw(
      WorldLayout.RegionInfo region, Optional<Path> entitiesFile, BlockColours colours, BooleanSupplier cancelled, int caveY, int caveRoof, int style
   ) {
      int size = 512;
      int[] base = new int[size * size];
      short[] height = new short[size * size];
      byte[] water = new byte[size * size];
      RegionFacts facts = new RegionFacts();
      int bad = 0;
      ChunkDecoder decoder = new ChunkDecoder(colours);
      boolean detailed = (style & DETAILED) != 0;
      decoder.caveY = caveY;
      decoder.caveRoof = caveRoof;
      decoder.detailed = detailed;
      decoder.night = detailed && (style & NIGHT) != 0;
      decoder.dry = !detailed && (style & DRY) != 0;
      decoder.biomes = (style & BIOMES) != 0;
      Surface surface = detailed ? new Surface(size) : null;
      java.util.Set<String> skip = decoder.night
         ? ChunkDecoder.SKIP_BUT_BIOMES_AND_LIGHT
         : decoder.biomes || detailed ? ChunkDecoder.SKIP_BUT_BIOMES : ChunkDecoder.SKIP;

      try (RegionFile file = RegionFile.open(region.file())) {
         RegionFile.Header header = file.header();

         for (int index = 0; index < 1024; index++) {
            if (header.present(index)) {
               if ((index & 15) == 0 && cancelled.getAsBoolean()) {
                  return null;
               }

               facts.savedAt[index] = header.timestamps[index];
               facts.kb[index] = (short)Math.min(32767, header.sectors(index) * 4);
               RegionFile.Chunk chunk = file.chunk(index);
               boolean drew = false;
               if (chunk.ok()) {
                  try {
                     Map<String, Object> root = Nbt.read(chunk.data(), skip, ChunkDecoder.COUNT);
                     ChunkDecoder.Facts f = ChunkDecoder.facts(root);
                     facts.set(index, f);
                     boolean decoded = decoder.decode(root, f);
                     if (decoded && surface != null) {
                        int ox = (index & 31) * 16;
                        int oz = (index >> 5) * 16;
                        for (int z = 0; z < 16; z++) {
                           int row = (oz + z) * size + ox;
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
                     } else if (decoded) {
                        int ox = (index & 31) * 16;
                        int oz = (index >> 5) * 16;

                        for (int z = 0; z < 16; z++) {
                           int row = (oz + z) * size + ox;

                           for (int x = 0; x < 16; x++) {
                              int at = z * 16 + x;
                              base[row + x] = decoder.colour[at];
                              height[row + x] = (short)decoder.height[at];
                              water[row + x] = (byte)decoder.water[at];
                           }
                        }
                     }

                     drew = true;
                  } catch (IOException | RuntimeException e) {
                     drew = false;
                  }
               }

               if (!drew) {
                  facts.state[index] = 3;
                  bad++;
               }
            }
         }
      } catch (IOException | RuntimeException e) {
         Arrays.fill(facts.state, (byte)3);
         bad = 1024;
      }

      if (entitiesFile.isPresent() && bad < 1024) {
         countEntities(entitiesFile.get(), facts, cancelled);
      }

      if (cancelled.getAsBoolean()) {
         return null;
      }

      if (surface != null) {
         return new TileRenderer.Drawn(marked(Shader.shade(surface, 0, options(style, 1, colours), new int[size * size]), facts), facts, bad);
      }

      return new TileRenderer.Drawn(shade(base, height, water, facts, (style & CONTOURS) != 0), facts, bad);
   }

   private static int[] marked(int[] out, RegionFacts facts) {
      int size = 512;
      for (int z = 0; z < size; z++) {
         for (int x = 0; x < size; x++) {
            int state = facts.state[(z >> 4) * 32 + (x >> 4)];
            if (state == 0) {
               out[z * size + x] = 0;
            } else if (state == 3) {
               out[z * size + x] = (x + z & 7) < 4 ? BAD_A : BAD_B;
            }
         }
      }

      return out;
   }

   public static RegionFacts facts(WorldLayout.RegionInfo region, Optional<Path> entitiesFile, BooleanSupplier cancelled) {
      RegionFacts facts = new RegionFacts();

      try (RegionFile file = RegionFile.open(region.file())) {
         RegionFile.Header header = file.header();

         for (int index = 0; index < 1024; index++) {
            if (header.present(index)) {
               if ((index & 31) == 0 && cancelled.getAsBoolean()) {
                  return null;
               }

               facts.savedAt[index] = header.timestamps[index];
               RegionFile.Chunk chunk = file.chunk(index);

               try {
                  if (!chunk.ok()) {
                     throw new IOException(chunk.problem());
                  }

                  facts.set(index, ChunkDecoder.facts(Nbt.read(chunk.data(), ChunkDecoder.SKIP_BLOCKS, ChunkDecoder.COUNT)));
               } catch (IOException | RuntimeException e) {
                  facts.state[index] = 3;
               }
            }
         }
      } catch (IOException | RuntimeException e) {
         Arrays.fill(facts.state, (byte)3);
         return facts;
      }

      if (entitiesFile.isPresent()) {
         countEntities(entitiesFile.get(), facts, cancelled);
      }

      return facts;
   }

   private static void countEntities(Path file, RegionFacts facts, BooleanSupplier cancelled) {
      if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
         try (RegionFile region = RegionFile.open(file)) {
            for (int index = 0; index < 1024; index++) {
               if (facts.state[index] != 0 && facts.state[index] != 3) {
                  if (!region.header().present(index)) {
                     facts.entities[index] = (short)Math.max(0, facts.entities[index]);
                  } else {
                     if (cancelled.getAsBoolean()) {
                        return;
                     }

                     RegionFile.Chunk chunk = region.chunk(index);
                     if (chunk.ok()) {
                        try {
                           int n = Nbt.count(Nbt.read(chunk.data(), ChunkDecoder.SKIP, ChunkDecoder.COUNT), "Entities");
                           if (n >= 0) {
                              facts.entities[index] = (short)Math.min(32767, Math.max(0, facts.entities[index]) + n);
                           }
                        } catch (IOException | RuntimeException ignored) {
                        }
                     }
                  }
               }
            }
         } catch (IOException | RuntimeException ignored) {
         }
      }
   }

   public static TileRenderer.Drawn drawLive(LiveRegion region, int style, BlockColours colours) {
      RegionFacts facts = new RegionFacts();
      for (int i = 0; i < 1024; i++) {
         if ((region.known[i >> 6] & 1L << (i & 63)) != 0L) {
            facts.state[i] = 1;
         }
      }

      if ((style & DETAILED) != 0) {
         return new TileRenderer.Drawn(marked(Shader.shade(region.surface(), 0, options(style, 1, colours), new int[512 * 512]), facts), facts, 0);
      }

      return new TileRenderer.Drawn(shade(region.colour, region.height, region.water, facts, false), facts, 0);
   }

   private static final int[] MAP_BRIGHTNESS = {180, 220, 255};

   public static int[] shadeVanilla(int[] base, short[] height, byte[] water, int size, boolean contours) {
      return shade(base, height, water, null, contours, size);
   }

   private static int[] shade(int[] base, short[] height, byte[] water, RegionFacts facts, boolean contours) {
      return shade(base, height, water, facts, contours, 512);
   }

   private static int[] shade(int[] base, short[] height, byte[] water, RegionFacts facts, boolean contours, int size) {
      int[] out = new int[size * size];

      for (int z = 0; z < size; z++) {
         for (int x = 0; x < size; x++) {
            int state = facts == null ? 1 : facts.state[(z >> 4) * 32 + (x >> 4)];
            if (state != 0) {
               int at = z * size + x;
               boolean stripe = (x + z & 7) < 4;
               if (state == 3) {
                  out[at] = stripe ? -8769488 : -10738652;
               } else {
                  int c = base[at];
                  if (c == 0) {
                     out[at] = 0;
                  } else {
                     int depth = water[at] & 255;
                     int odd = (x + z) & 1;
                     int step;
                     if (depth > 0) {
                        double d = depth * 0.1 + odd * 0.2;
                        step = d < 0.5 ? 2 : (d > 0.9 ? 0 : 1);
                     } else {
                        int h = height[at];
                        int north = z > 0 && base[at - size] != 0 ? height[at - size] : h;
                        double d = (h - north) * 4.0 / 5.0 + (odd - 0.5) * 0.4;
                        step = d > 0.6 ? 2 : (d < -0.6 ? 0 : 1);
                     }

                     double f = MAP_BRIGHTNESS[step] / 255.0;
                     if (contours && depth == 0) {
                        int band = height[at] >> 3;
                        boolean line = x > 0 && base[at - 1] != 0 && height[at - 1] >> 3 != band
                           || z > 0 && base[at - size] != 0 && height[at - size] >> 3 != band;
                        if (line) {
                           f *= 0.62;
                        }
                     }

                     int r = (int)Math.min(255.0, (c >> 16 & 0xFF) * f);
                     int g = (int)Math.min(255.0, (c >> 8 & 0xFF) * f);
                     int b = (int)Math.min(255.0, (c & 0xFF) * f);

                     out[at] = 0xFF000000 | r << 16 | g << 8 | b;
                  }
               }
            }
         }
      }

      return out;
   }

   public record Drawn(int[] argb, RegionFacts facts, int unreadable) {
   }
}
