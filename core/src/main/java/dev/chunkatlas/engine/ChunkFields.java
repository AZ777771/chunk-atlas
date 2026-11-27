package dev.chunkatlas.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChunkFields {
   public static final String FORCE_STATUS = "status";
   public static final String FORCE_INHABITED = "inhabited";
   public static final String FORCE_LAST_UPDATE = "last_update";
   public static final String FORCE_DATA_VERSION = "data_version";
   public static final String FORCE_BIOME = "biome";

   public static final List<String> STATUSES = List.of(
      "minecraft:full",
      "minecraft:empty",
      "minecraft:structure_starts",
      "minecraft:structure_references",
      "minecraft:biomes",
      "minecraft:terrain",
      "minecraft:noise",
      "minecraft:surface",
      "minecraft:carvers",
      "minecraft:liquid_carvers",
      "minecraft:features",
      "minecraft:initialize_light",
      "minecraft:light",
      "minecraft:heightmaps",
      "minecraft:spawn"
   );

   private ChunkFields() {
   }

   public record Changes(
      String status,
      Long inhabitedTicks,
      Long lastUpdate,
      Integer dataVersion,
      String biome,
      Map<String, String> replace,
      Integer deleteFrom,
      Integer deleteTo,
      boolean structures,
      boolean lighting,
      boolean heightmaps,
      Set<String> force,
      boolean preventRetrogen,
      boolean forceBlending
   ) {
      public Changes(
         String status,
         Long inhabitedTicks,
         Long lastUpdate,
         Integer dataVersion,
         String biome,
         Map<String, String> replace,
         Integer deleteFrom,
         Integer deleteTo,
         boolean structures,
         boolean lighting,
         boolean heightmaps
      ) {
         this(status, inhabitedTicks, lastUpdate, dataVersion, biome, replace, deleteFrom, deleteTo, structures, lighting, heightmaps, Set.of(), false, false);
      }

      public boolean isEmpty() {
         return this.status == null
            && this.inhabitedTicks == null
            && this.lastUpdate == null
            && this.dataVersion == null
            && this.biome == null
            && (this.replace == null || this.replace.isEmpty())
            && this.deleteFrom == null
            && !this.structures
            && !this.lighting
            && !this.heightmaps
            && !this.preventRetrogen
            && !this.forceBlending;
      }

      boolean forced(String key) {
         return this.force != null && this.force.contains(key);
      }
   }

   public static ChunkCopy.Transform transform(Changes c) {
      return (role, root) -> {
         if (role.equals("region")) {
            apply(c, root);
         }
      };
   }

   private static void put(Changes c, Map<String, Object> root, String tag, Object value, String forceKey) {
      if (value != null && (c.forced(forceKey) || root.containsKey(tag))) {
         root.put(tag, value);
      }
   }

   @SuppressWarnings("unchecked")
   static void apply(Changes c, Map<String, Object> root) {
      put(c, root, "Status", c.status(), FORCE_STATUS);
      put(c, root, "InhabitedTime", c.inhabitedTicks(), FORCE_INHABITED);
      put(c, root, "LastUpdate", c.lastUpdate(), FORCE_LAST_UPDATE);
      put(c, root, "DataVersion", c.dataVersion(), FORCE_DATA_VERSION);

      if (c.structures()) {
         Map<String, Object> s = new HashMap<>();
         s.put("References", new HashMap<String, Object>());
         s.put("starts", new HashMap<String, Object>());
         root.put("structures", s);
      }

      if (c.heightmaps()) {
         root.remove("Heightmaps");
      }

      if (c.lighting()) {
         root.put("isLightOn", (byte)0);
      }

      if (c.preventRetrogen()) {
         preventRetrogen(root);
      }

      List<Object> sections = Nbt.list(root, "sections");
      if (c.forceBlending()) {
         forceBlending(root, sections);
      }

      if (sections == null) {
         return;
      }

      if (c.deleteFrom() != null && c.deleteTo() != null) {
         int lo = Math.min(c.deleteFrom(), c.deleteTo());
         int hi = Math.max(c.deleteFrom(), c.deleteTo());
         for (Iterator<Object> it = sections.iterator(); it.hasNext(); ) {
            long y = Nbt.number(it.next(), "Y", Long.MIN_VALUE);
            if (y >= lo && y <= hi) {
               it.remove();
            }
         }
      }

      for (Object o : sections) {
         if (!(o instanceof Map)) {
            continue;
         }

         Map<String, Object> section = (Map<String, Object>)o;
         if (c.lighting()) {
            section.remove("BlockLight");
            section.remove("SkyLight");
         }

         if (c.biome() != null && (c.forced(FORCE_BIOME) || section.containsKey("biomes"))) {
            Map<String, Object> biomes = new HashMap<>();
            List<Object> palette = new ArrayList<>();
            palette.add(c.biome());
            biomes.put("palette", palette);
            section.put("biomes", biomes);
         }

         if (c.replace() != null && !c.replace().isEmpty()) {
            List<Object> palette = Nbt.list(Nbt.map(section, "block_states"), "palette");
            if (palette != null) {
               for (Object entry : palette) {
                  if (entry instanceof Map) {
                     Map<String, Object> state = (Map<String, Object>)entry;
                     String to = c.replace().get(String.valueOf(state.get("Name")));
                     if (to != null) {
                        state.put("Name", to);
                        state.remove("Properties");
                     }
                  }
               }
            }
         }
      }
   }

   private static void preventRetrogen(Map<String, Object> root) {
      Object retro = root.remove("below_zero_retrogen");
      String target = Nbt.string(retro, "target_status");
      String status = Nbt.string(root, "Status");
      if (status != null && status.substring(status.indexOf(':') + 1).equals("empty")) {
         root.put("Status", target == null || target.isEmpty() ? "minecraft:full" : (target.contains(":") ? target : "minecraft:" + target));
      }
   }

   public static void markForBlending(Map<String, Object> root) {
      if (!root.containsKey("Level") && root.get("sections") instanceof List<?> sections) {
         @SuppressWarnings("unchecked")
         List<Object> list = (List<Object>)sections;
         forceBlending(root, list);
      }
   }

   private static void forceBlending(Map<String, Object> root, List<Object> sections) {
      int lo = Integer.MAX_VALUE;
      int hi = Integer.MIN_VALUE;
      if (sections != null) {
         for (Object s : sections) {
            long y = Nbt.number(s, "Y", Long.MIN_VALUE);
            if (y != Long.MIN_VALUE && Nbt.has(s, "block_states")) {
               lo = (int)Math.min(lo, y);
               hi = (int)Math.max(hi, y);
            }
         }
      }

      Map<String, Object> blending = new HashMap<>();
      blending.put("min_section", lo == Integer.MAX_VALUE ? -4 : lo);
      blending.put("max_section", hi == Integer.MIN_VALUE ? 20 : hi + 1);
      root.put("blending_data", blending);
   }

   public static Map<String, String> parseReplace(String text) {
      Map<String, String> out = new HashMap<>();
      for (String part : text.split(",")) {
         String[] kv = part.split("=");
         if (kv.length == 2 && !kv[0].isBlank() && !kv[1].isBlank()) {
            out.put(id(kv[0]), id(kv[1]));
         }
      }

      return out;
   }

   private static String id(String s) {
      String t = s.trim().toLowerCase(java.util.Locale.ROOT);
      return t.contains(":") ? t : "minecraft:" + t;
   }
}
