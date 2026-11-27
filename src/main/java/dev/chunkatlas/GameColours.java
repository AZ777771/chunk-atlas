package dev.chunkatlas;

import dev.chunkatlas.engine.BlockColours;
import dev.chunkatlas.engine.Look;
import dev.chunkatlas.port.BlockLooks;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Reg;
import dev.chunkatlas.port.Version;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class GameColours implements BlockColours {
   private final BlockColours fallback = BlockColours.builtIn();
   private final ConcurrentHashMap<String, Integer> seen = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<String, Long> looks = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<String, Integer> tints = new ConcurrentHashMap<>();
   private final String id;
   private volatile Object models;
   private volatile String lookId;
   private volatile int generation;
   private static volatile HolderLookup.Provider vanilla;
   private static GameColours shared;

   public static synchronized GameColours get() {
      if (shared == null) {
         shared = new GameColours();
      }

      return shared;
   }

   public GameColours() {
      String version = Version.id().replaceAll("[^A-Za-z0-9._-]", "_");
      this.id = "game-" + version + "-" + BuiltInRegistries.BLOCK.size() + "-vmap3";
      this.lookId = this.id + "-d1";
   }

   @Override
   public String id() {
      return this.id;
   }

   @Override
   public String lookId() {
      this.fresh();
      return this.lookId;
   }

   @Override
   public int colour(String blockName) {
      Integer known = this.seen.get(blockName);
      if (known != null) {
         return known;
      }

      int colour = this.work(blockName);
      if (this.seen.size() < 20000) {
         this.seen.put(blockName, colour);
      }

      return colour;
   }

   private int work(String name) {
      Block block = block(name);
      if (block == null) {
         return this.fallback.colour(name);
      }

      return BlockLooks.mapColour(block);
   }

   private static Block block(String name) {
      return Reg.block(name);
   }

   private void fresh() {
      Minecraft mc = Minecraft.getInstance();
      Object now = mc == null ? null : BlockLooks.models(mc);
      if (now != this.models) {
         synchronized (this) {
            if (now != this.models) {
               this.looks.clear();
               this.tints.clear();
               StringBuilder packs = new StringBuilder();
               if (mc != null) {
                  for (String p : mc.getResourcePackRepository().getSelectedIds()) {
                     packs.append(p).append('|');
                  }
               }

               this.lookId = this.id + "-d1-" + Integer.toHexString(packs.toString().hashCode());
               this.models = now;
               this.generation++;
            }
         }
      }
   }

   public int generation() {
      this.fresh();
      return this.generation;
   }

   @Override
   public long look(String blockName) {
      this.fresh();
      Long known = this.looks.get(blockName);
      if (known != null) {
         return known;
      }

      long look;
      try {
         look = this.workLook(blockName);
      } catch (RuntimeException e) {
         look = Look.fromColour(blockName, this.colour(blockName));
      }

      if (this.looks.size() < 20000) {
         this.looks.put(blockName, look);
      }

      return look;
   }

   public long look(Block block) {
      return this.look(Reg.blockId(block));
   }

   private long workLook(String name) {
      Block block = block(name);
      Minecraft mc = Minecraft.getInstance();
      if (block == null || mc == null) {
         return Look.fromColour(name, this.colour(name));
      }

      BlockState state = block.defaultBlockState();
      FluidState fluid = state.getFluidState();
      if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN || fluid.is(Fluids.WATER) && state.getRenderShape() == RenderShape.INVISIBLE) {
         int[] avg = average(mc, "minecraft:block/water_still");
         return Look.of(avg == null ? 0xB0B0B0 : avg[0], 255, Look.TINT_WATER, Look.WATER, 0);
      }

      if (block == Blocks.LAVA || fluid.is(Fluids.LAVA) && state.getRenderShape() == RenderShape.INVISIBLE) {
         int[] avg = average(mc, "minecraft:block/lava_still");
         return Look.of(avg == null ? 0xD45A12 : avg[0], 255, Look.TINT_NONE, Look.LAVA, 15);
      }

      if (state.getRenderShape() == RenderShape.INVISIBLE || state.isAir()) {
         return 0L;
      }

      BlockLooks.Top top = BlockLooks.top(mc, state);
      int[] avg = top.texture() == null ? null : average(mc, top.texture());
      if (avg == null) {
         return Look.fromColour(name, this.colour(name));
      }

      int tintIndex = top.tintIndex();
      boolean translucent = top.translucent();
      int rgb = avg[0];
      int fill = avg[1];
      int tint = Look.TINT_NONE;
      if (tintIndex >= 0) {
         BlockLooks.Tint asked = BlockLooks.tint(mc, state, tintIndex);
         if (asked != null && asked.kind() != Look.TINT_NONE) {
            tint = asked.kind();
         } else if (asked != null && asked.constant() != -1) {
            int constant = asked.constant();
            rgb = (rgb >> 16 & 255) * (constant >> 16 & 255) / 255 << 16 | (rgb >> 8 & 255) * (constant >> 8 & 255) / 255 << 8
               | (rgb & 255) * (constant & 255) / 255;
         }
      }

      long kinds = 0L;
      if (state.is(BlockTags.LEAVES)) {
         kinds |= Look.LEAVES;
      }

      boolean noCollision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
      if (fluid.is(Fluids.WATER) && noCollision) {
         kinds |= Look.COVER | Look.WATER;
      } else if (noCollision && block != Blocks.SNOW) {
         kinds |= Look.COVER;
      } else if (translucent || fill < 128 && (kinds & Look.LEAVES) == 0L) {
         kinds |= Look.CLEAR;
      }

      return Look.of(rgb, fill, tint, kinds, state.getLightEmission());
   }

   private static int[] average(Minecraft mc, String texture) {
      BlockLooks.Picture image = BlockLooks.picture(mc, texture);
      if (image == null) {
         return null;
      }

      int w = image.width();

      int h = Math.min(image.height(), w);
      int[] argb = image.argb();
      long r = 0, g = 0, b = 0, a = 0;
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int p = argb[y * w + x];
            int alpha = p >>> 24;
            if (alpha == 0) {
               continue;
            }

            r += (long)(p >> 16 & 255) * alpha;
            g += (long)(p >> 8 & 255) * alpha;
            b += (long)(p & 255) * alpha;
            a += alpha;
         }
      }

      if (a == 0) {
         return null;
      }

      int rgb = (int)(r / a) << 16 | (int)(g / a) << 8 | (int)(b / a);
      return new int[]{rgb, (int)(a / Math.max(1, w * h))};
   }

   @Override
   public int tint(String biome, int kind) {
      this.fresh();
      if (biome == null) {
         return defaultBiomeTint(kind);
      }

      String key = biome + "#" + kind;
      Integer known = this.tints.get(key);
      if (known != null) {
         return known;
      }

      int c;
      try {
         Biome b = biome(biome);
         c = b == null ? defaultBiomeTint(kind) : BlockLooks.biomeColour(b, kind);
      } catch (RuntimeException e) {
         c = defaultBiomeTint(kind);
      }

      c &= 0xFFFFFF;
      this.tints.put(key, c);
      return c;
   }

   private static int defaultBiomeTint(int kind) {
      Biome plains = biome("minecraft:plains");
      if (plains != null && kind >= Look.TINT_GRASS && kind <= Look.TINT_DRY_FOLIAGE) {
         return BlockLooks.biomeColour(plains, kind) & 0xFFFFFF;
      }

      return BlockColours.defaultTint(kind);
   }

   private static Biome biome(String name) {
      ResourceKey<Biome> id = Ids.biome(name);
      if (id == null) {
         return null;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.level != null) {
         Optional<Biome> b = mc.level.registryAccess().lookupOrThrow(Registries.BIOME).get(id).map(h -> h.value());
         if (b.isPresent()) {
            return b.get();
         }
      }

      HolderLookup.Provider lookup = vanilla;
      if (lookup == null) {
         synchronized (GameColours.class) {
            if (vanilla == null) {
               vanilla = BlockLooks.vanilla();
            }

            lookup = vanilla;
         }
      }

      return lookup.lookupOrThrow(Registries.BIOME).get(id).map(h -> h.value()).orElse(null);
   }
}
