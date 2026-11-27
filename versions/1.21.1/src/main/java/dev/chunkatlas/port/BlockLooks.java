package dev.chunkatlas.port;

import com.mojang.blaze3d.platform.NativeImage;
import dev.chunkatlas.engine.Look;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

public final class BlockLooks {
   private BlockLooks() {
   }

   public record Top(String texture, int tintIndex, boolean translucent) {
   }

   public record Tint(int kind, int constant) {
   }

   public record Picture(int width, int height, int[] argb) {
   }

   public static Object models(Minecraft mc) {
      return mc.getModelManager().getBlockModelShaper();
   }

   public static Top top(Minecraft mc, BlockState state) {
      BakedModel model = mc.getModelManager().getBlockModelShaper().getBlockModel(state);
      RandomSource random = RandomSource.create(42L);
      List<BakedQuad> quads = model.getQuads(state, Direction.UP, random);
      if (quads.isEmpty()) {
         quads = model.getQuads(state, null, random);
      }

      TextureAtlasSprite sprite = null;
      int tintIndex = -1;
      if (!quads.isEmpty()) {
         BakedQuad q = quads.get(0);
         sprite = q.getSprite();
         tintIndex = q.isTinted() ? q.getTintIndex() : -1;
      }

      if (sprite == null) {
         sprite = model.getParticleIcon();
      }

      boolean translucent = ItemBlockRenderTypes.getChunkRenderType(state) == RenderType.translucent();
      return new Top(sprite == null ? null : sprite.contents().name().toString(), tintIndex, translucent);
   }

   public static Tint tint(Minecraft mc, BlockState state, int tintIndex) {
      Probe probe = new Probe();
      int constant = mc.getBlockColors().getColor(state, probe, BlockPos.ZERO, tintIndex);
      ColorResolver asked = probe.asked;
      if (asked == BiomeColors.GRASS_COLOR_RESOLVER) {
         return new Tint(Look.TINT_GRASS, -1);
      } else if (asked == BiomeColors.FOLIAGE_COLOR_RESOLVER) {
         return new Tint(Look.TINT_FOLIAGE, -1);
      } else if (asked == BiomeColors.WATER_COLOR_RESOLVER) {
         return new Tint(Look.TINT_WATER, -1);
      }

      return asked == null && constant == -1 ? null : new Tint(Look.TINT_NONE, asked == null ? constant : -1);
   }

   public static Picture picture(Minecraft mc, String texture) {
      ResourceLocation id = ResourceLocation.parse(texture);
      ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
      Optional<Resource> resource = mc.getResourceManager().getResource(file);
      if (resource.isEmpty()) {
         return null;
      }

      try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
         int w = image.getWidth();
         int h = image.getHeight();
         int[] argb = new int[w * h];
         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               int p = image.getPixelRGBA(x, y);
               argb[y * w + x] = p & 0xFF00FF00 | (p & 0xFF) << 16 | p >> 16 & 0xFF;
            }
         }

         return new Picture(w, h, argb);
      } catch (Exception e) {
         return null;
      }
   }

   public static int mapColour(Block block) {
      MapColor colour = block.defaultMapColor();
      return colour == MapColor.NONE ? 0 : 0xFF000000 | colour.col;
   }

   public static int biomeColour(Biome b, int kind) {
      return switch (kind) {
         case Look.TINT_GRASS -> b.getGrassColor(0.0, 0.0);
         case Look.TINT_FOLIAGE, Look.TINT_DRY_FOLIAGE -> b.getFoliageColor();
         case Look.TINT_WATER -> b.getWaterColor();
         default -> 0xFFFFFF;
      };
   }

   public static HolderLookup.Provider vanilla() {
      return VanillaRegistries.createLookup();
   }

   private static final class Probe implements BlockAndTintGetter {
      ColorResolver asked;

      @Override
      public int getBlockTint(BlockPos pos, ColorResolver resolver) {
         this.asked = resolver;
         return -1;
      }

      @Override
      public float getShade(Direction direction, boolean shade) {
         return 1.0F;
      }

      @Override
      public LevelLightEngine getLightEngine() {
         return null;
      }

      @Override
      public BlockEntity getBlockEntity(BlockPos pos) {
         return null;
      }

      @Override
      public BlockState getBlockState(BlockPos pos) {
         return Blocks.AIR.defaultBlockState();
      }

      @Override
      public FluidState getFluidState(BlockPos pos) {
         return Fluids.EMPTY.defaultFluidState();
      }

      @Override
      public int getHeight() {
         return 0;
      }

      @Override
      public int getMinBuildHeight() {
         return 0;
      }
   }
}
