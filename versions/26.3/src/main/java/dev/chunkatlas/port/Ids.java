package dev.chunkatlas.port;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public final class Ids {
   private Ids() {
   }

   public static String of(ResourceKey<?> key) {
      return key.identifier().toString();
   }

   public static String namespace(ResourceKey<?> key) {
      return key.identifier().getNamespace();
   }

   public static String path(ResourceKey<?> key) {
      return key.identifier().getPath();
   }

   public static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String id) {
      Identifier parsed = id == null ? null : Identifier.tryParse(id);
      return parsed == null ? null : ResourceKey.create(registry, parsed);
   }

   public static ResourceKey<Level> dimension(String id) {
      return key(Registries.DIMENSION, id);
   }

   public static ResourceKey<Biome> biome(String id) {
      return key(Registries.BIOME, id);
   }
}
