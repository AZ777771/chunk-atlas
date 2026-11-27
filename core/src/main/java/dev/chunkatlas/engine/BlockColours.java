package dev.chunkatlas.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface BlockColours {
   int colour(String blockId);

   String id();

   default long look(String blockName) {
      return Look.fromColour(blockName, this.colour(blockName));
   }

   default int tint(String biome, int kind) {
      return defaultTint(kind);
   }

   default String lookId() {
      return this.id() + "-d1";
   }

   static int defaultTint(int kind) {
      return switch (kind) {
         case Look.TINT_GRASS -> 0x91BD59;
         case Look.TINT_FOLIAGE -> 0x77AB2F;
         case Look.TINT_WATER -> 0x3F76E4;
         case Look.TINT_DRY_FOLIAGE -> 0xA0773F;
         default -> 0xFFFFFF;
      };
   }

   static BlockColours builtIn() {
      return BlockColours.BuiltIn.INSTANCE;
   }

   static String legacyName(int blockId, int data) {
      return BlockColours.BuiltIn.INSTANCE.legacy(blockId, data);
   }

   static int neutral(String name) {
      int h = name.hashCode() * -1640531535;
      int r = 116 + (h >>> 8 & 31);
      int g = 116 + (h >>> 14 & 31);
      int b = 116 + (h >>> 20 & 31);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   final class BuiltIn implements BlockColours {
      static final BlockColours.BuiltIn INSTANCE = new BlockColours.BuiltIn();
      private static final String[] DYES = new String[]{
         "white",
         "orange",
         "magenta",
         "light_blue",
         "yellow",
         "lime",
         "pink",
         "gray",
         "light_gray",
         "cyan",
         "purple",
         "blue",
         "brown",
         "green",
         "red",
         "black",
         "silver"
      };
      private static final int[] DYE = new int[]{
         16777215,
         14188339,
         11685080,
         6724056,
         15066419,
         8375321,
         15892389,
         5000268,
         10066329,
         5013401,
         8339378,
         3361970,
         6704179,
         6717235,
         10040115,
         1644825,
         10066329
      };
      private static final int[] TERRACOTTA = new int[]{
         13742497,
         10441252,
         9787244,
         7367818,
         12223780,
         6780213,
         10505550,
         3746083,
         8874850,
         5725276,
         8014168,
         4996700,
         4993571,
         5001770,
         9321518,
         2430480,
         8874850
      };
      private final Map<String, Integer> exact = new HashMap<>();
      private final List<BlockColours.BuiltIn.Pattern> patterns = new ArrayList<>();
      private final Map<String, Integer> seen = new ConcurrentHashMap<>();
      private final String[][] legacy = new String[4096][];

      private BuiltIn() {
         try {
            for (String line : lines("colours.txt")) {
               int eq = line.indexOf('=');
               int colour = line.startsWith("0=") ? 0 : 0xFF000000 | Integer.parseInt(line.substring(0, eq), 16);

               for (String name : line.substring(eq + 1).split(",")) {
                  boolean open = name.startsWith("*");
                  boolean close = name.endsWith("*") && name.length() > 1;
                  if (!open && !close) {
                     this.exact.putIfAbsent(name, colour);
                  } else {
                     this.patterns.add(new BlockColours.BuiltIn.Pattern(name.substring(open ? 1 : 0, name.length() - (close ? 1 : 0)), open, close, colour));
                  }
               }
            }

            for (String line : lines("legacy-ids.txt")) {
               String[] parts = line.split(";");
               int eq = parts[0].indexOf('=');
               int id = Integer.parseInt(parts[0].substring(0, eq));
               String[] names = new String[16];
               Arrays.fill(names, parts[0].substring(eq + 1));

               for (int i = 1; i < parts.length; i++) {
                  int e = parts[i].indexOf('=');
                  names[Integer.parseInt(parts[i].substring(0, e)) & 15] = parts[i].substring(e + 1);
               }

               this.legacy[id & 4095] = names;
            }
         } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("the built-in block colour tables are damaged", e);
         }
      }

      private static List<String> lines(String resource) throws IOException {
         List<String> out = new ArrayList<>();

         try (InputStream in = BlockColours.class.getResourceAsStream(resource)) {
            if (in == null) {
               throw new IOException("missing " + resource);
            }

            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            for (String line = r.readLine(); line != null; line = r.readLine()) {
               line = line.trim();
               if (!line.isEmpty() && !line.startsWith("#") && line.indexOf('=') > 0) {
                  out.add(line);
               }
            }
         }

         return out;
      }

      String legacy(int blockId, int data) {
         String[] names = this.legacy[blockId & 4095];
         return names == null ? null : names[data & 15];
      }

      @Override
      public String id() {
         return "builtin-1";
      }

      @Override
      public int colour(String blockName) {
         Integer known = this.seen.get(blockName);
         if (known != null) {
            return known;
         }

         int c = this.work(blockName);
         if (this.seen.size() < 20000) {
            this.seen.put(blockName, c);
         }

         return c;
      }

      private int work(String full) {
         int colon = full.indexOf(':');
         String name = colon < 0 ? full : full.substring(colon + 1);
         Integer hit = this.exact.get(name);
         if (hit != null) {
            return hit;
         }

         for (int i = 0; i < DYES.length; i++) {
            if (name.startsWith(DYES[i] + "_")) {
               String rest = name.substring(DYES[i].length() + 1);
               if (rest.contains("terracotta") && !rest.contains("glazed") || rest.equals("stained_hardened_clay")) {
                  return 0xFF000000 | TERRACOTTA[i];
               }

               if (!rest.contains("banner") && !rest.contains("candle_cake")) {
                  return 0xFF000000 | DYE[i];
               }
               break;
            }
         }

         for (BlockColours.BuiltIn.Pattern p : this.patterns) {
            if (p.matches(name)) {
               return p.colour();
            }
         }

         return BlockColours.neutral(full);
      }

      private record Pattern(String text, boolean open, boolean close, int colour) {
         boolean matches(String name) {
            if (this.open && this.close) {
               return name.contains(this.text);
            } else {
               return this.open ? name.endsWith(this.text) : name.startsWith(this.text);
            }
         }
      }
   }
}
