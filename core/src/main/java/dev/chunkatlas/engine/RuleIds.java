package dev.chunkatlas.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RuleIds {
   public static final List<String> VANILLA_BIOMES = ids(
      "badlands bamboo_jungle basalt_deltas beach birch_forest cherry_grove cold_ocean crimson_forest dappled_forest dark_forest deep_cold_ocean "
         + "deep_dark deep_frozen_ocean deep_lukewarm_ocean deep_ocean desert dripstone_caves end_barrens end_highlands end_midlands eroded_badlands "
         + "flower_forest forest frozen_ocean frozen_peaks frozen_river grove ice_spikes jagged_peaks jungle lukewarm_ocean lush_caves mangrove_swamp "
         + "meadow mushroom_fields nether_wastes ocean old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga pale_garden plains river "
         + "savanna savanna_plateau small_end_islands snowy_beach snowy_plains snowy_slopes snowy_taiga soul_sand_valley sparse_jungle stony_peaks "
         + "stony_shore sulfur_caves sunflower_plains swamp taiga the_end the_void warm_ocean warped_forest windswept_forest windswept_gravelly_hills "
         + "windswept_hills windswept_savanna wooded_badlands"
   );

   public static final List<String> VANILLA_STRUCTURES = ids(
      "abandoned_camp_bamboo_jungle abandoned_camp_birch_forest abandoned_camp_cherry_grove abandoned_camp_dappled_forest abandoned_camp_flower_forest "
         + "abandoned_camp_forest abandoned_camp_meadow abandoned_camp_old_growth_birch_forest abandoned_camp_old_growth_pine_taiga "
         + "abandoned_camp_old_growth_spruce_taiga abandoned_camp_pale_garden abandoned_camp_savanna abandoned_camp_snowy_taiga "
         + "abandoned_camp_sparse_jungle abandoned_camp_swamp abandoned_camp_taiga abandoned_camp_windswept_forest abandoned_camp_wooded_badlands "
         + "ancient_city bastion_remnant buried_treasure desert_pyramid end_city fortress igloo jungle_pyramid mansion mineshaft mineshaft_mesa "
         + "monument nether_fossil ocean_ruin_cold ocean_ruin_warm pillager_outpost ruined_portal ruined_portal_desert ruined_portal_jungle "
         + "ruined_portal_mountain ruined_portal_nether ruined_portal_ocean ruined_portal_swamp shipwreck shipwreck_beached stronghold swamp_hut "
         + "trail_ruins trial_chambers village_desert village_plains village_savanna village_snowy village_taiga"
   );

   private static final Map<Integer, String> LEGACY_BIOMES = legacyBiomes();

   private RuleIds() {
   }

   private static List<String> ids(String paths) {
      List<String> out = new ArrayList<>();
      for (String p : paths.trim().split("\\s+")) {
         out.add("minecraft:" + p);
      }

      return List.copyOf(out);
   }

   public static String normalize(String typed) {
      if (typed == null) {
         return "";
      }

      String s = typed.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
      boolean group = s.startsWith("#");
      if (group) {
         s = s.substring(1);
      }

      s = s.replaceAll("[^a-z0-9_.:/-]", "");
      int colon = s.indexOf(':');
      if (colon >= 0) {
         String ns = s.substring(0, colon);
         String path = s.substring(colon + 1).replace(":", "");
         s = (ns.isEmpty() ? "minecraft" : ns) + ":" + path;
         if (path.isEmpty()) {
            return "";
         }
      } else {
         if (s.isEmpty()) {
            return "";
         }

         s = "minecraft:" + s;
      }

      if (group) {
         return s.startsWith("minecraft:") ? "#" + s.substring(10) : "#" + s;
      }

      return s;
   }

   public static boolean isGroup(String id) {
      return id != null && id.startsWith("#");
   }

   private static String full(String id) {
      String s = isGroup(id) ? id.substring(1) : id;
      return s.indexOf(':') >= 0 ? s : "minecraft:" + s;
   }

   public static String plain(String id) {
      String f = full(id);
      String words = f.startsWith("minecraft:") ? f.substring(10) : f;
      return words.replace('_', ' ');
   }

   public static Message describe(String id) {
      return Message.literal(id.startsWith("minecraft:") ? id.substring(10) : id);
   }

   public static boolean matches(String wanted, String actual, boolean block) {
      if (!isGroup(wanted)) {
         return wanted.equals(actual);
      }

      String g = full(wanted);
      if (actual.equals(g)) {
         return true;
      }

      int colon = g.indexOf(':');
      String ns = g.substring(0, colon + 1);
      if (!actual.startsWith(ns)) {
         return false;
      }

      String path = actual.substring(ns.length());
      String gp = g.substring(colon + 1);
      return block ? path.equals("deepslate_" + gp) : path.startsWith(gp + "_");
   }

   private static final List<String> STRUCTURE_GROUPS = List.of("#village", "#ruined_portal", "#ocean_ruin", "#shipwreck", "#mineshaft", "#abandoned_camp");

   public static List<String> groups(List<String> ids, boolean block) {
      List<String> out = new ArrayList<>();
      if (block) {
         for (String id : ids) {
            if (id.startsWith("minecraft:deepslate_") && id.endsWith("_ore") && ids.contains("minecraft:" + id.substring(20))) {
               out.add("#" + id.substring(20));
            }
         }
      } else {
         for (String g : STRUCTURE_GROUPS) {
            if (ids.stream().filter(id -> matches(g, id, false)).count() >= 2) {
               out.add(g);
            }
         }
      }

      return out;
   }

   public static String structure(String saved) {
      String s = saved.toLowerCase(Locale.ROOT);
      if (s.indexOf(':') < 0) {
         s = "minecraft:" + s;
      }

      return s.equals("minecraft:endcity") ? "minecraft:end_city" : s;
   }

   public static String legacyBiome(int number) {
      return LEGACY_BIOMES.get(number);
   }

   private static Map<Integer, String> legacyBiomes() {
      String[] table = {
         "0 ocean", "1 plains", "2 desert", "3 windswept_hills", "4 forest", "5 taiga", "6 swamp", "7 river", "8 nether_wastes", "9 the_end",
         "10 frozen_ocean", "11 frozen_river", "12 snowy_plains", "13 snowy_plains", "14 mushroom_fields", "15 mushroom_fields", "16 beach",
         "17 desert", "18 forest", "19 taiga", "20 windswept_hills", "21 jungle", "22 jungle", "23 sparse_jungle", "24 deep_ocean",
         "25 stony_shore", "26 snowy_beach", "27 birch_forest", "28 birch_forest", "29 dark_forest", "30 snowy_taiga", "31 snowy_taiga",
         "32 old_growth_pine_taiga", "33 old_growth_pine_taiga", "34 windswept_forest", "35 savanna", "36 savanna_plateau", "37 badlands",
         "38 wooded_badlands", "39 badlands", "40 small_end_islands", "41 end_midlands", "42 end_highlands", "43 end_barrens", "44 warm_ocean",
         "45 lukewarm_ocean", "46 cold_ocean", "47 deep_lukewarm_ocean", "48 deep_lukewarm_ocean", "49 deep_cold_ocean", "50 deep_frozen_ocean",
         "127 the_void", "129 sunflower_plains", "130 desert", "131 windswept_gravelly_hills", "132 flower_forest", "133 taiga", "134 swamp",
         "140 ice_spikes", "149 jungle", "151 sparse_jungle", "155 old_growth_birch_forest", "156 old_growth_birch_forest", "157 dark_forest",
         "158 snowy_taiga", "160 old_growth_spruce_taiga", "161 old_growth_spruce_taiga", "162 windswept_gravelly_hills", "163 windswept_savanna",
         "164 windswept_savanna", "165 eroded_badlands", "166 wooded_badlands", "167 badlands", "168 bamboo_jungle", "169 bamboo_jungle",
         "170 soul_sand_valley", "171 crimson_forest", "172 warped_forest", "173 basalt_deltas", "174 dripstone_caves", "175 lush_caves"
      };
      Map<Integer, String> out = new java.util.HashMap<>();
      for (String row : table) {
         int space = row.indexOf(' ');
         out.put(Integer.parseInt(row.substring(0, space)), "minecraft:" + row.substring(space + 1));
      }

      return Map.copyOf(out);
   }
}
