package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ChunkTags {
   private static final Set<String> HEAVY = Set.of(
      "BlockLight",
      "SkyLight",
      "block_states",
      "biomes",
      "Heightmaps",
      "block_ticks",
      "fluid_ticks",
      "PostProcessing",
      "structures",
      "block_entities",
      "entities",
      "Lights",
      "CarvingMasks",
      "UpgradeData",
      "blending_data",
      "below_zero_retrogen",
      "Level",
      "Sections",
      "TileEntities",
      "TileTicks",
      "LiquidTicks",
      "ToBeTicked",
      "LiquidsToBeTicked"
   );

   private ChunkTags() {
   }

   public static List<String> split(String path) {
      List<String> out = new ArrayList<>();
      if (path == null) {
         return out;
      }

      for (String s : path.replace('›', '.').replace('/', '.').split("\\.")) {
         String t = s.trim();
         if (!t.isEmpty()) {
            out.add(t);
         }
      }

      return out;
   }

   public static String join(List<String> path) {
      return String.join(".", path);
   }

   public static Object get(Object root, List<String> path) {
      Object at = root;
      for (String s : path) {
         if (at instanceof Map<?, ?> m) {
            at = m.get(s);
         } else if (at instanceof List<?> l) {
            int i;
            try {
               i = Integer.parseInt(s);
            } catch (NumberFormatException e) {
               return null;
            }

            at = i >= 0 && i < l.size() ? l.get(i) : null;
         } else {
            return null;
         }

         if (at == null) {
            return null;
         }
      }

      return at;
   }

   public static double number(Object v) {
      return v instanceof Number n ? n.doubleValue() : Double.NaN;
   }

   public static int count(Object v) {
      return switch (v) {
         case List<?> l -> l.size();
         case Map<?, ?> m -> m.size();
         case byte[] a -> a.length;
         case int[] a -> a.length;
         case long[] a -> a.length;
         case Nbt.Count c -> c.size();
         case null, default -> -1;
      };
   }

   public static String kind(Object v) {
      return switch (v) {
         case Byte b -> "Byte";
         case Short s -> "Short";
         case Integer i -> "Int";
         case Long l -> "Long";
         case Float f -> "Float";
         case Double d -> "Double";
         case String s -> "String";
         case byte[] a -> "Byte array";
         case int[] a -> "Int array";
         case long[] a -> "Long array";
         case List<?> l -> "List";
         case Map<?, ?> m -> "Compound";
         case null, default -> "?";
      };
   }

   public static final class Facts {
      public final byte[] has = new byte[1024];
      public final double[] value = new double[1024];
      public final int[] count = new int[1024];

      public int present;
      public int found;
      public boolean numeric;

      public Facts() {
         java.util.Arrays.fill(this.value, Double.NaN);
         java.util.Arrays.fill(this.count, -1);
      }

      public long[] mask() {
         long[] bits = new long[ChunkSelection.WORDS];
         for (int i = 0; i < 1024; i++) {
            if (this.has[i] != 0) {
               bits[i >> 6] |= 1L << (i & 63);
            }
         }

         return bits;
      }
   }

   public static Facts scan(Path regionFile, List<String> path) throws IOException {
      Facts out = new Facts();
      Set<String> skip = new HashSet<>(HEAVY);
      path.forEach(skip::remove);
      try (RegionFile region = RegionFile.open(regionFile)) {
         RegionFile.Header h = region.header();
         for (int i = 0; i < 1024; i++) {
            if (!h.present(i)) {
               continue;
            }

            out.present++;
            RegionFile.Chunk c = region.chunk(i);
            if (!c.ok()) {
               continue;
            }

            Object v;
            try {
               v = get(Nbt.read(c.data(), skip, Set.of()), path);
            } catch (IOException | RuntimeException e) {
               continue;
            }

            if (v != null) {
               out.has[i] = 1;
               out.found++;
               out.value[i] = number(v);
               out.count[i] = count(v);
               if (!Double.isNaN(out.value[i])) {
                  out.numeric = true;
               }
            }
         }
      }

      return out;
   }

   public static void paths(Object value, String prefix, Map<String, String> out, int depth) {
      if (depth > 12 || out.size() > 4000) {
         return;
      }

      if (value instanceof Map<?, ?> m) {
         for (Map.Entry<?, ?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey());
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            out.put(path, kind(e.getValue()));
            paths(e.getValue(), path, out, depth + 1);
         }
      } else if (value instanceof List<?> l && !l.isEmpty()) {
         String path = prefix + ".0";
         out.put(path, kind(l.get(0)));
         paths(l.get(0), path, out, depth + 1);
      }
   }

   public static Map<String, String> samplePaths(Path regionFile, int picks) throws IOException {
      Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      try (RegionFile region = RegionFile.open(regionFile)) {
         RegionFile.Header h = region.header();
         int taken = 0;
         int last = -1;

         for (int step = 0; step < 1024 && taken < picks; step += Math.max(1, 1024 / Math.max(1, picks))) {
            int i = -1;
            for (int k = Math.max(step, last + 1); k < 1024; k++) {
               if (h.present(k)) {
                  i = k;
                  break;
               }
            }

            if (i < 0) {
               break;
            }

            last = i;
            RegionFile.Chunk c = region.chunk(i);
            if (c.ok()) {
               try {
                  paths(Nbt.read(c.data()), "", out, 0);
                  taken++;
               } catch (IOException | RuntimeException ignored) {
               }
            }
         }
      }

      return out;
   }
}
