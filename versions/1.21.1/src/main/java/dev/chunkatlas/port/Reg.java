package dev.chunkatlas.port;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class Reg {
   private Reg() {
   }

   public static String blockId(Block block) {
      return BuiltInRegistries.BLOCK.getKey(block).toString();
   }

   public static Block block(String id) {
      ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
      return key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
   }

   public static String blockName(String id) {
      return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).getName().getString();
   }

   public static List<String> blockIds() {
      List<String> out = new ArrayList<>();
      for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
         out.add(id.toString());
      }

      return out;
   }

   public static String itemId(Item item) {
      return BuiltInRegistries.ITEM.getKey(item).toString();
   }

   public static boolean itemReady(Item item) {
      return true;
   }

   public static List<String> ids(RegistryAccess access, ResourceKey<? extends Registry<?>> registry) {
      List<String> out = new ArrayList<>();
      access.registry(registry).ifPresent(r -> r.keySet().forEach(id -> out.add(id.toString())));
      return out;
   }
}
