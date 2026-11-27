package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class ChunkBiomes {
   private ChunkBiomes() {
   }

   public static short[] read(Path regionFile, int chunkX, int chunkZ, BlockColours colours) {
      int index = (chunkZ & 31) << 5 | chunkX & 31;
      try (RegionFile file = RegionFile.open(regionFile)) {
         if (!file.header().present(index)) {
            return null;
         }

         RegionFile.Chunk chunk = file.chunk(index);
         if (!chunk.ok()) {
            return null;
         }

         Map<String, Object> root = Nbt.read(chunk.data(), ChunkDecoder.SKIP_BUT_BIOMES, ChunkDecoder.COUNT);
         ChunkDecoder decoder = new ChunkDecoder(colours);
         decoder.detailed = true;
         boolean decoded = decoder.decode(root, ChunkDecoder.facts(root));
         short[] out = decoded ? decoder.biome.clone() : new short[256];
         if (!decoded || out[0] < 0) {
            legacy(root, decoded ? decoder.height : null, out);
         }

         return out[0] < 0 && !decoded ? null : out;
      } catch (IOException | RuntimeException e) {
         return null;
      }
   }

   public static int[] heights(Path regionFile, int chunkX, int chunkZ, BlockColours colours) {
      int index = (chunkZ & 31) << 5 | chunkX & 31;
      try (RegionFile file = RegionFile.open(regionFile)) {
         if (!file.header().present(index)) {
            return null;
         }

         RegionFile.Chunk chunk = file.chunk(index);
         if (!chunk.ok()) {
            return null;
         }

         Map<String, Object> root = Nbt.read(chunk.data(), ChunkDecoder.SKIP_BUT_BIOMES, ChunkDecoder.COUNT);
         ChunkDecoder decoder = new ChunkDecoder(colours);
         decoder.detailed = true;
         return decoder.decode(root, ChunkDecoder.facts(root)) ? decoder.height.clone() : null;
      } catch (IOException | RuntimeException e) {
         return null;
      }
   }

   private static void legacy(Map<String, Object> root, int[] heights, short[] out) {
      java.util.Arrays.fill(out, (short)-1);
      if (!(root.get("Level") instanceof Map<?, ?> level)) {
         return;
      }

      Object b = level.get("Biomes");
      int[] numbers = b instanceof int[] ints ? ints : null;
      if (b instanceof byte[] bytes) {
         numbers = new int[bytes.length];
         for (int i = 0; i < bytes.length; i++) {
            numbers[i] = bytes[i] & 255;
         }
      }

      if (numbers == null) {
         return;
      }

      for (int z = 0; z < 16; z++) {
         for (int x = 0; x < 16; x++) {
            int at = z * 16 + x;
            int n;
            if (numbers.length == 256) {
               n = numbers[at];
            } else if (numbers.length == 1024) {
               int y = heights == null ? 64 : Math.max(0, Math.min(255, heights[at]));
               n = numbers[(y >> 2) * 16 + (z >> 2) * 4 + (x >> 2)];
            } else {
               continue;
            }

            String name = RuleIds.legacyBiome(n);
            if (name != null) {
               out[at] = BiomeIndex.of(name);
            }
         }
      }
   }
}
