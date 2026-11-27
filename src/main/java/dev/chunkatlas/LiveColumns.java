package dev.chunkatlas;

import dev.chunkatlas.engine.BiomeIndex;
import dev.chunkatlas.engine.Look;
import dev.chunkatlas.engine.Surface;
import dev.chunkatlas.port.Ids;
import java.util.IdentityHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

public final class LiveColumns {
   private final IdentityHashMap<Block, Long> looks = new IdentityHashMap<>();
   private final IdentityHashMap<Biome, Short> biomes = new IdentityHashMap<>();
   private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
   private int generation = -1;

   private final boolean stored;

   public LiveColumns() {
      this(false);
   }

   public LiveColumns(boolean stored) {
      this.stored = stored;
   }

   public boolean scan(Level level, LevelChunk chunk, int lx, int lz, int startY, int minY, int i, int[] top, int[] cover, short[] height, byte[] depth,
      short[] biome, byte[] light) {
      GameColours colours = GameColours.get();
      if (colours.generation() != this.generation) {
         this.looks.clear();
         this.generation = colours.generation();
      }

      int x = chunk.getPos().getMinBlockX() + lx;
      int z = chunk.getPos().getMinBlockZ() + lz;
      top[i] = 0;
      cover[i] = 0;
      depth[i] = 0;
      biome[i] = -1;
      light[i] = 0;
      height[i] = (short)minY;
      int waterTop = Integer.MIN_VALUE;
      long coverLook = 0L;
      boolean coverDry = false;
      int coverY = Integer.MIN_VALUE;
      int topY = Integer.MIN_VALUE;
      for (int y = startY; y >= minY; y--) {
         Block block = chunk.getBlockState(this.pos.set(x, y, z)).getBlock();
         long look = this.look(colours, block);
         if (look == 0L) {
            continue;
         }

         if (Look.is(look, Look.COVER) || Look.is(look, Look.CLEAR)) {
            if (coverLook == 0L) {
               coverLook = look;
               coverDry = waterTop == Integer.MIN_VALUE;
               coverY = y;
            }

            if (Look.is(look, Look.WATER) && waterTop == Integer.MIN_VALUE) {
               waterTop = y;
            }

            continue;
         }

         if (Look.is(look, Look.WATER)) {
            if (waterTop == Integer.MIN_VALUE) {
               waterTop = y;
            }

            if (waterTop - y >= 254) {
               break;
            }

            continue;
         }

         topY = waterTop != Integer.MIN_VALUE ? waterTop : y;
         top[i] = Surface.top(look);
         depth[i] = (byte)(waterTop != Integer.MIN_VALUE ? Math.min(254, waterTop - y) : 0);
         break;
      }

      if (topY == Integer.MIN_VALUE) {
         if (waterTop != Integer.MIN_VALUE) {
            topY = waterTop;
            depth[i] = (byte)255;
         } else if (coverLook != 0L) {
            topY = coverY;
         } else {
            return false;
         }
      }

      height[i] = (short)topY;
      if (coverLook != 0L) {
         cover[i] = Surface.cover(coverLook, coverDry && waterTop != Integer.MIN_VALUE);
      }

      this.pos.set(x, topY + 1, z);
      biome[i] = this.biome(this.stored ? chunk.getNoiseBiome(x >> 2, topY + 1 >> 2, z >> 2) : level.getBiome(this.pos));
      light[i] = (byte)level.getBrightness(LightLayer.BLOCK, this.pos);
      return true;
   }

   private long look(GameColours colours, Block block) {
      Long known = this.looks.get(block);
      if (known == null) {
         known = colours.look(block);
         this.looks.put(block, known);
      }

      return known;
   }

   private short biome(Holder<Biome> holder) {
      Short known = this.biomes.get(holder.value());
      if (known == null) {
         known = holder.unwrapKey().map(k -> BiomeIndex.of(Ids.of(k))).orElse((short)-1);
         this.biomes.put(holder.value(), known);
      }

      return known;
   }
}
