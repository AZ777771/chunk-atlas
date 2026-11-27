package dev.chunkatlas.engine;

import java.util.HashMap;
import java.util.Map;

public final class BiomePalette {
   private static final Map<String, Integer> COLOURS = new HashMap<>();

   static {
      put(0x8DB360, "plains", "sunflower_plains", "meadow");
      put(0x056621, "forest", "flower_forest");
      put(0x307444, "birch_forest", "old_growth_birch_forest");
      put(0x40511A, "dark_forest", "pale_garden");
      put(0x0B6659, "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga");
      put(0x31554A, "snowy_taiga");
      put(0x537B09, "jungle", "bamboo_jungle");
      put(0x628B17, "sparse_jungle");
      put(0xBDB25F, "savanna", "savanna_plateau", "windswept_savanna");
      put(0xFA9418, "desert");
      put(0xD94515, "badlands", "eroded_badlands", "wooded_badlands");
      put(0x07F9B2, "swamp");
      put(0x2C4205, "mangrove_swamp");
      put(0xFFFFFF, "snowy_plains", "ice_spikes");
      put(0xA0A0A0, "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "stony_shore", "stony_peaks");
      put(0xD0E0F0, "snowy_slopes", "frozen_peaks", "jagged_peaks", "grove");
      put(0xFAF0C0, "beach", "snowy_beach");
      put(0xFF00FF, "mushroom_fields");
      put(0xF5B8D0, "cherry_grove");
      put(0x000070, "ocean");
      put(0x000030, "deep_ocean");
      put(0x0000AC, "warm_ocean");
      put(0x000090, "lukewarm_ocean", "deep_lukewarm_ocean");
      put(0x202070, "cold_ocean", "deep_cold_ocean");
      put(0x7070D6, "frozen_ocean", "deep_frozen_ocean");
      put(0x0000FF, "river");
      put(0xA0A0FF, "frozen_river");
      put(0x6B4F3A, "dripstone_caves");
      put(0x7BA331, "lush_caves");
      put(0x0E1F2E, "deep_dark");
      put(0xBF3B3B, "nether_wastes");
      put(0x5E3830, "soul_sand_valley");
      put(0xDD0808, "crimson_forest");
      put(0x49907B, "warped_forest");
      put(0x403636, "basalt_deltas");
      put(0x8080FF, "the_end", "end_highlands", "end_midlands", "end_barrens", "small_end_islands");
      put(0x000000, "the_void");
   }

   private BiomePalette() {
   }

   private static void put(int rgb, String... paths) {
      for (String p : paths) {
         COLOURS.put("minecraft:" + p, 0xFF000000 | rgb);
      }
   }

   public static int colour(String id) {
      Integer known = COLOURS.get(id);
      if (known != null) {
         return known;
      }

      double h = (id.hashCode() & 0xFFFF) / 65535.0 * 6.0;
      double s = 0.55;
      double v = 0.75;
      int sector = (int)h % 6;
      double f = h - Math.floor(h);
      double p = v * (1 - s);
      double q = v * (1 - s * f);
      double t = v * (1 - s * (1 - f));
      double[] rgb = switch (sector) {
         case 0 -> new double[]{v, t, p};
         case 1 -> new double[]{q, v, p};
         case 2 -> new double[]{p, v, t};
         case 3 -> new double[]{p, q, v};
         case 4 -> new double[]{t, p, v};
         default -> new double[]{v, p, q};
      };
      return 0xFF000000 | (int)(rgb[0] * 255) << 16 | (int)(rgb[1] * 255) << 8 | (int)(rgb[2] * 255);
   }
}
