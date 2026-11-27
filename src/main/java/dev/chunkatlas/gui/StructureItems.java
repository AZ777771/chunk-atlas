package dev.chunkatlas.gui;

import dev.chunkatlas.engine.IdText;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Reg;
import dev.chunkatlas.port.Tex;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class StructureItems {
   private static final Map<String, Item> ITEMS = Map.ofEntries(
      Map.entry("village", Items.BELL),
      Map.entry("pillager_outpost", Items.CROSSBOW),
      Map.entry("shipwreck", Items.OAK_BOAT),
      Map.entry("ocean_ruin", Items.TRIDENT),
      Map.entry("monument", Items.PRISMARINE_SHARD),
      Map.entry("buried_treasure", Items.HEART_OF_THE_SEA),
      Map.entry("ruined_portal", Items.FLINT_AND_STEEL),
      Map.entry("mineshaft", Items.CHEST_MINECART),
      Map.entry("stronghold", Items.ENDER_EYE),
      Map.entry("mansion", Items.TOTEM_OF_UNDYING),
      Map.entry("desert_pyramid", Items.PRIZE_POTTERY_SHERD),
      Map.entry("jungle_pyramid", Items.ARROW),
      Map.entry("swamp_hut", Items.CAULDRON),
      Map.entry("igloo", Items.SNOWBALL),
      Map.entry("ancient_city", Items.ECHO_SHARD),
      Map.entry("trial_chambers", Items.TRIAL_KEY),
      Map.entry("trail_ruins", Items.BRUSH),
      Map.entry("abandoned_camp", Items.CAMPFIRE),
      Map.entry("fortress", Items.BLAZE_ROD),
      Map.entry("bastion_remnant", Items.GOLD_INGOT),
      Map.entry("nether_fossil", Items.BONE_BLOCK),
      Map.entry("end_city", Items.ELYTRA),
      Map.entry("end_gateway", Items.ENDER_PEARL),
      Map.entry("desert_well", Items.WATER_BUCKET),
      Map.entry("amethyst_geode", Items.AMETHYST_SHARD),
      Map.entry("fossil", Items.BONE),
      Map.entry("lava_pool", Items.LAVA_BUCKET)
   );

   private static final Map<Item, ItemStack> STACKS = new java.util.HashMap<>();
   private static final Map<String, Boolean> HAS_TEXTURE = new java.util.HashMap<>();

   private StructureItems() {
   }

   static Item item(String kind) {
      return "minecraft".equals(IdText.namespace(kind)) ? ITEMS.get(IdText.path(kind)) : null;
   }

   static void draw(Gfx g, Item item, int x, int y) {
      if (drawable(item)) {
         g.item(STACKS.computeIfAbsent(item, ItemStack::new), x, y);
         return;
      }

      String key = Reg.itemId(item);
      String namespace = IdText.namespace(key);
      String path = IdText.path(key);
      String texture = namespace + ":textures/item/" + path + ".png";
      if (!has(texture)) {
         texture = namespace + ":textures/block/" + path + "_side.png";
      }

      if (has(texture)) {
         g.blit(texture, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
      }
   }

   static boolean drawable(Item item) {
      return Reg.itemReady(item);
   }

   private static boolean has(String texture) {
      return HAS_TEXTURE.computeIfAbsent(texture, Tex::exists);
   }
}
