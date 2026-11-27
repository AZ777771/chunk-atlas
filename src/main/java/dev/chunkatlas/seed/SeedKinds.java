package dev.chunkatlas.seed;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class SeedKinds {
   static final List<String> ORDER = List.of(
      "minecraft:village", "minecraft:pillager_outpost", "minecraft:stronghold", "minecraft:mansion", "minecraft:monument",
      "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:swamp_hut", "minecraft:igloo", "minecraft:ancient_city",
      "minecraft:trial_chambers", "minecraft:trail_ruins", "minecraft:abandoned_camp", "minecraft:ruined_portal", "minecraft:shipwreck", "minecraft:ocean_ruin",
      "minecraft:buried_treasure", "minecraft:mineshaft", "minecraft:fortress", "minecraft:bastion_remnant", "minecraft:nether_fossil",
      "minecraft:end_city", "minecraft:desert_well", "minecraft:amethyst_geode", "minecraft:fossil", "minecraft:lava_pool",
      "minecraft:end_gateway"
   );

   static final Comparator<String> LIST_ORDER = Comparator.<String>comparingInt(k -> ORDER.contains(k) ? ORDER.indexOf(k) : ORDER.size()).thenComparing(k -> k);

   static final Map<String, String> FEATURES = Map.of(
      "minecraft:desert_well", "minecraft:desert_well",
      "minecraft:amethyst_geode", "minecraft:amethyst_geode",
      "minecraft:fossil_upper", "minecraft:fossil",
      "minecraft:fossil_lower", "minecraft:fossil",
      "minecraft:lake_lava_surface", "minecraft:lava_pool",
      "minecraft:end_gateway_return", "minecraft:end_gateway"
   );

   private SeedKinds() {
   }

   static boolean isFeature(String kind) {
      return FEATURES.containsValue(kind);
   }

   static String kindOf(String structureId) {
      if (structureId.startsWith("minecraft:village_")) {
         return "minecraft:village";
      } else if (structureId.startsWith("minecraft:ruined_portal")) {
         return "minecraft:ruined_portal";
      } else if (structureId.startsWith("minecraft:shipwreck")) {
         return "minecraft:shipwreck";
      } else if (structureId.startsWith("minecraft:abandoned_camp")) {
         return "minecraft:abandoned_camp";
      } else if (structureId.startsWith("minecraft:ocean_ruin")) {
         return "minecraft:ocean_ruin";
      } else {
         return structureId.startsWith("minecraft:mineshaft") ? "minecraft:mineshaft" : structureId;
      }
   }

   static String topBlock(String biome) {
      return "minecraft:" + switch (biome) {
         case "desert", "beach", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean" -> "sand";
         case "snowy_beach" -> "sand";
         case "badlands", "eroded_badlands" -> "red_sand";
         case "wooded_badlands" -> "terracotta";
         case "snowy_plains", "ice_spikes", "snowy_taiga", "snowy_slopes", "grove", "frozen_peaks", "jagged_peaks" -> "snow_block";
         case "stony_peaks", "stony_shore" -> "stone";
         case "windswept_gravelly_hills" -> "gravel";
         case "mushroom_fields" -> "mycelium";
         case "mangrove_swamp" -> "mud";
         case "old_growth_pine_taiga", "old_growth_spruce_taiga" -> "podzol";
         case "forest", "flower_forest", "windswept_forest" -> "oak_leaves";
         case "dark_forest" -> "dark_oak_leaves";
         case "pale_garden" -> "pale_oak_leaves";
         case "birch_forest", "old_growth_birch_forest" -> "birch_leaves";
         case "taiga" -> "spruce_leaves";
         case "jungle", "bamboo_jungle", "sparse_jungle" -> "jungle_leaves";
         case "cherry_grove" -> "cherry_leaves";
         case "dripstone_caves" -> "dripstone_block";
         case "lush_caves" -> "moss_block";
         case "deep_dark" -> "sculk";
         case "nether_wastes" -> "netherrack";
         case "crimson_forest" -> "crimson_nylium";
         case "warped_forest" -> "warped_nylium";
         case "soul_sand_valley" -> "soul_sand";
         case "basalt_deltas" -> "basalt";
         case "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens" -> "end_stone";
         case "the_void" -> "air";
         case "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean", "river", "frozen_river" -> "water";
         default -> "grass_block";
      };
   }
}
