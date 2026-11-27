package dev.chunkatlas.port;

import com.mojang.blaze3d.platform.NativeImage;
import dev.chunkatlas.engine.Look;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.CardinalLighting;
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
      return mc.getModelManager().getBlockStateModelSet();
   }

   public static Top top(Minecraft mc, BlockState state) {
      BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
      List<BlockStateModelPart> parts = new ArrayList<>();
      model.collectParts(RandomSource.create(42L), parts);
      TextureAtlasSprite sprite = null;
      int tintIndex = -1;
      boolean translucent = false;
      for (BlockStateModelPart part : parts) {
         List<BakedQuad> quads = part.getQuads(Direction.UP);
         if (quads.isEmpty()) {
            quads = part.getQuads(null);
         }

         for (BakedQuad q : quads) {
            if (sprite == null) {
               sprite = q.materialInfo().sprite();
               tintIndex = q.materialInfo().isTinted() ? q.materialInfo().tintIndex() : -1;
               translucent = q.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT;
            }
         }
      }

      if (sprite == null) {
         sprite = model.particleMaterial().sprite();
      }

      return new Top(sprite == null ? null : sprite.contents().name().toString(), tintIndex, translucent);
   }

   public static Tint tint(Minecraft mc, BlockState state, int tintIndex) {
      BlockTintSource source = mc.getBlockColors().getTintSource(state, tintIndex);
      if (source == null) {
         return null;
      }

      Probe probe = new Probe();
      int constant = source.colorInWorld(state, probe, BlockPos.ZERO);
      ColorResolver asked = probe.asked;
      if (asked == BiomeColors.GRASS_COLOR_RESOLVER) {
         return new Tint(Look.TINT_GRASS, -1);
      } else if (asked == BiomeColors.FOLIAGE_COLOR_RESOLVER) {
         return new Tint(Look.TINT_FOLIAGE, -1);
      } else if (asked == BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER) {
         return new Tint(Look.TINT_DRY_FOLIAGE, -1);
      } else if (asked == BiomeColors.WATER_COLOR_RESOLVER) {
         return new Tint(Look.TINT_WATER, -1);
      }

      return new Tint(Look.TINT_NONE, asked == null ? constant : -1);
   }

   public static Picture picture(Minecraft mc, String texture) {
      Identifier id = Identifier.parse(texture);
      Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
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
               argb[y * w + x] = image.getPixel(x, y);
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
         case Look.TINT_FOLIAGE -> b.getFoliageColor();
         case Look.TINT_DRY_FOLIAGE -> b.getDryFoliageColor();
         case Look.TINT_WATER -> b.getWaterColor();
         default -> 0xFFFFFF;
      };
   }

   public static HolderLookup.Provider vanilla() {
      return VanillaRegistries.createWorldLookup();
   }

   private static final class Probe implements BlockAndTintGetter {
      ColorResolver asked;

      @Override
      public int getBlockTint(BlockPos pos, ColorResolver resolver) {
         this.asked = resolver;
         return -1;
      }

      @Override
      public CardinalLighting cardinalLighting() {
         return BlockAndTintGetter.EMPTY.cardinalLighting();
      }

      @Override
      public LevelLightEngine getLightEngine() {
         return BlockAndTintGetter.EMPTY.getLightEngine();
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
      public int getMinY() {
         return 0;
      }
   }
}
