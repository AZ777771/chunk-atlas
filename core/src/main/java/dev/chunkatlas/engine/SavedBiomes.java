package dev.chunkatlas.engine;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class SavedBiomes {
   public static final int SIDE = 64;

   private SavedBiomes() {
   }

   public static short[] read(WorldLayout.RegionInfo region, BlockColours colours, BooleanSupplier cancelled) {
      short[] out = new short[SIDE * SIDE];
      Arrays.fill(out, (short)-1);
      ChunkDecoder decoder = new ChunkDecoder(colours);
      decoder.detailed = true;
      try (RegionFile file = RegionFile.open(region.file())) {
         RegionFile.Header header = file.header();
         for (int index = 0; index < 1024; index++) {
            if (!header.present(index)) {
               continue;
            }

            if ((index & 15) == 0 && cancelled.getAsBoolean()) {
               return null;
            }

            RegionFile.Chunk chunk = file.chunk(index);
            if (!chunk.ok()) {
               continue;
            }

            try {
               Map<String, Object> root = Nbt.read(chunk.data(), ChunkDecoder.SKIP_BUT_BIOMES, ChunkDecoder.COUNT);
               if (decoder.decode(root, ChunkDecoder.facts(root))) {
                  int ox = (index & 31) * 2;
                  int oz = (index >> 5) * 2;
                  for (int z = 0; z < 2; z++) {
                     for (int x = 0; x < 2; x++) {
                        out[(oz + z) * SIDE + ox + x] = decoder.biome[(z * 8 + 4) * 16 + x * 8 + 4];
                     }
                  }
               }
            } catch (IOException | RuntimeException ignored) {
            }
         }
      } catch (IOException | RuntimeException e) {
         return null;
      }

      return out;
   }
}
