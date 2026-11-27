package dev.chunkatlas.port;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.AbstractVillager;

public final class MobLook {
   private static final Map<EntityType<?>, String> IDS = new IdentityHashMap<>();
   private static final Map<Object, String> TEXTURES = new HashMap<>();

   private static final Map<String, String> PATHS = Map.ofEntries(
      Map.entry("blaze/blaze", "blaze"),
      Map.entry("cat/cat_tabby", "cat/tabby"),
      Map.entry("chicken/chicken_temperate", "chicken"),
      Map.entry("cow/cow_cold", "cow/cow"),
      Map.entry("cow/cow_temperate", "cow/cow"),
      Map.entry("cow/mooshroom_red", "cow/red_mooshroom"),
      Map.entry("llama/llama_creamy", "llama/creamy"),
      Map.entry("phantom/phantom", "phantom"),
      Map.entry("phantom/phantom_eyes", "phantom_eyes"),
      Map.entry("pig/pig_temperate", "pig/pig"),
      Map.entry("rabbit/rabbit_brown", "rabbit/brown"),
      Map.entry("skeleton/parched", "skeleton/skeleton"),
      Map.entry("spider/spider_eyes", "spider_eyes"),
      Map.entry("wandering_trader/wandering_trader", "wandering_trader"),
      Map.entry("witch/witch", "witch")
   );

   private MobLook() {
   }

   public record Face(String texture, int texW, int texH, int u, int v, int w, int h, String over, float[] parts) {
   }

   public static String id(Entity e) {
      EntityType<?> type = e.getType();
      String id = IDS.get(type);
      if (id == null) {
         id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
         IDS.put(type, id);
      }

      return id;
   }

   public static EntityType<?> type(String id) {
      ResourceLocation key = ResourceLocation.tryParse(id);
      return key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
   }

   public static Face fix(String id, Face face) {
      String texture = PATHS.getOrDefault(face.texture(), face.texture());
      String over = face.over() == null ? null : PATHS.getOrDefault(face.over(), face.over());
      return switch (id) {
         case "minecraft:cow", "minecraft:mooshroom", "minecraft:cow#cold" -> new Face(texture, 64, 32, 6, 6, 8, 8, null, new float[0]);

         case "minecraft:pig" -> new Face(texture, 64, 32, face.u(), face.v(), face.w(), face.h(), over, face.parts());

         case "minecraft:rabbit" -> new Face(texture, 64, 32, 37, 5, 5, 4, null, new float[]{33, 10, 1, 1, 2, 1.5F});
         case "minecraft:parched" -> new Face(texture, 64, 32, face.u(), face.v(), face.w(), face.h(), over, face.parts());
         default -> new Face(texture, face.texW(), face.texH(), face.u(), face.v(), face.w(), face.h(), over, face.parts());
      };
   }

   public static String entityTexture(String path) {
      return "minecraft:textures/entity/" + path + ".png";
   }

   public static boolean neutral(Entity e) {
      return e instanceof NeutralMob || e instanceof AbstractVillager || e instanceof Piglin || e instanceof Llama || e instanceof Panda || e instanceof Dolphin;
   }

   public static boolean coldCow(Entity e) {
      return false;
   }

   public static String variant(Entity e) {
      Object id = switch (e) {
         case Cat c -> c.getVariant().value().texture();
         case Wolf w -> w.getTexture();
         case MushroomCow m -> m.getVariant();
         case Fox x -> x.getVariant();
         case Rabbit r -> r.getVariant();
         case Axolotl a -> a.getVariant();
         case Llama l -> l.getVariant();
         case Panda p -> p.getVariant();
         default -> null;
      };
      return id == null ? null : texture(id);
   }

   private static String texture(Object v) {
      String id = TEXTURES.get(v);
      if (id == null) {
         id = v instanceof Enum<?> e ? entityTexture(variantPath(e)) : v.toString();
         TEXTURES.put(v, id);
      }

      return id;
   }

   private static String variantPath(Enum<?> v) {
      String name = v.name().toLowerCase(Locale.ROOT);
      return switch (v) {
         case MushroomCow.MushroomType m -> "cow/" + name + "_mooshroom";
         case Fox.Type x -> x == Fox.Type.SNOW ? "fox/snow_fox" : "fox/fox";
         case Rabbit.Variant r -> r == Rabbit.Variant.EVIL ? "rabbit/caerbannog" : "rabbit/" + name;
         case Axolotl.Variant a -> "axolotl/axolotl_" + name;
         case Llama.Variant l -> "llama/" + name;
         case Panda.Gene p -> p == Panda.Gene.NORMAL ? "panda/panda" : "panda/" + name + "_panda";
         default -> throw new IllegalArgumentException(name);
      };
   }
}
