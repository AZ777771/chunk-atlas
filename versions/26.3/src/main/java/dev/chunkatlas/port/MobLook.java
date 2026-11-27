package dev.chunkatlas.port;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

public final class MobLook {
   private static final Map<EntityType<?>, String> IDS = new IdentityHashMap<>();
   private static final Map<Object, String> TEXTURES = new HashMap<>();

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
      Identifier key = Identifier.tryParse(id);
      return key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
   }

   public static Face fix(String id, Face face) {
      return face;
   }

   public static String entityTexture(String path) {
      return "minecraft:textures/entity/" + path + ".png";
   }

   public static boolean neutral(Entity e) {
      return e instanceof NeutralMob || e instanceof AbstractVillager || e instanceof Piglin || e instanceof Llama || e instanceof Panda || e instanceof Dolphin;
   }

   public static boolean coldCow(Entity e) {
      return e instanceof Cow c && !c.isBaby() && c.getVariant().value().modelAndTexture().model() == CowVariant.ModelType.COLD;
   }

   public static String variant(Entity e) {
      Object id = switch (e) {
         case Cow c -> c.getVariant().value().modelAndTexture().asset().texturePath();
         case Pig p -> p.getVariant().value().modelAndTexture().asset().texturePath();
         case Chicken c -> c.getVariant().value().modelAndTexture().asset().texturePath();
         case Cat c -> c.getVariant().value().adultAssetInfo().texturePath();
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
         case MushroomCow.Variant m -> "cow/mooshroom_" + name;
         case Fox.Variant x -> x == Fox.Variant.SNOW ? "fox/fox_snow" : "fox/fox";
         case Rabbit.Variant r -> r == Rabbit.Variant.EVIL ? "rabbit/rabbit_caerbannog" : "rabbit/rabbit_" + name;
         case Axolotl.Variant a -> "axolotl/axolotl_" + name;
         case Llama.Variant l -> "llama/llama_" + name;
         case Panda.Gene p -> p == Panda.Gene.NORMAL ? "panda/panda" : "panda/panda_" + name;
         default -> throw new IllegalArgumentException(name);
      };
   }
}
