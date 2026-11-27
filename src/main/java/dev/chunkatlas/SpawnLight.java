package dev.chunkatlas;

import dev.chunkatlas.port.MobLook;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;

public final class SpawnLight {
   public static final byte NONE = 0;

   public static final byte NIGHT = 1;

   public static final byte ALWAYS = 2;

   public static final int ALWAYS_TINT = 0x80FF2828;
   public static final int NIGHT_TINT = 0x70FFC81E;

   private static final EntityType<?> ZOMBIE = MobLook.type("minecraft:zombie");

   private static final int ROOM = 2;

   private static final int DARK_SKY = 7;

   private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

   public static int tint(byte spawn) {
      return spawn == ALWAYS ? ALWAYS_TINT : spawn == NIGHT ? NIGHT_TINT : 0;
   }

   public byte column(Level level, LevelChunk chunk, int x, int z, int top, int bottom, boolean surface) {
      int room = surface ? ROOM : 0;
      BlockState over = null;
      for (int y = top; y >= bottom; y--) {
         BlockState s = chunk.getBlockState(this.pos.set(x, y, z));
         if (!s.getFluidState().isEmpty()) {
            if (surface) {
               return NONE;
            }

            room = 0;
            over = null;
            continue;
         }

         if (s.isAir() || s.getCollisionShape(level, this.pos).isEmpty()) {
            room++;
            over = s;
            continue;
         }

         if (room >= ROOM) {
            return this.floor(level, s, over, x, y, z);
         }

         if (surface) {
            return NONE;
         }

         room = 0;
         over = null;
      }

      return NONE;
   }

   private byte floor(Level level, BlockState floor, BlockState over, int x, int y, int z) {
      this.pos.set(x, y, z);
      if (!floor.isValidSpawn(level, this.pos, ZOMBIE)) {
         return NONE;
      }

      if (over != null && (over.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE) || over.isSignalSource())) {
         return NONE;
      }

      this.pos.set(x, y + 1, z);
      DimensionType type = level.dimensionType();
      if (level.getBrightness(LightLayer.BLOCK, this.pos) > type.monsterSpawnBlockLightLimit()) {
         return NONE;
      }

      if (!type.hasSkyLight()) {
         return ALWAYS;
      }

      return level.getBrightness(LightLayer.SKY, this.pos) <= DARK_SKY ? ALWAYS : NIGHT;
   }
}
