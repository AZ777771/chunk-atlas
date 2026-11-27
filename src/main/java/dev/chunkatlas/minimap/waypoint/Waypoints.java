package dev.chunkatlas.minimap.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.chunkatlas.minimap.Minimap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.storage.LevelResource;

public final class Waypoints {
   public static final String DEFAULT_SET = "Default";

   public static final Pattern SHARED = Pattern.compile("\\[Waypoint\\] (.{1,32}) @ (-?\\d{1,8}), (-?\\d{1,4}), (-?\\d{1,8}) \\(([a-z0-9_.-]+:[a-z0-9_/.-]+)\\)");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private static final List<Waypoint> ALL = new ArrayList<>();
   private static String worldKey;

   private Waypoints() {
   }

   public static List<Waypoint> all() {
      return ALL;
   }

   public static void sync(Minecraft mc) {
      String key = mc.level == null ? null : key(mc);
      if (key == null ? worldKey == null : key.equals(worldKey)) {
         return;
      }

      ALL.clear();
      worldKey = key;
      if (key == null) {
         return;
      }

      Path file = file(key);
      if (Files.isRegularFile(file)) {
         try {
            List<Waypoint> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Waypoint>>() {}.getType());
            if (read != null) {
               for (Waypoint w : read) {
                  if (w != null) {
                     ALL.add(w.checked());
                  }
               }
            }
         } catch (IOException | RuntimeException e) {
            Minimap.LOG.warn("Could not read waypoints {}: {}", file.getFileName(), e.toString());
         }
      }
   }

   public static void save() {
      if (worldKey == null) {
         return;
      }

      Path file = file(worldKey);
      try {
         Files.createDirectories(file.getParent());
         Files.writeString(file, GSON.toJson(ALL), StandardCharsets.UTF_8);
      } catch (IOException e) {
         Minimap.LOG.warn("Could not save waypoints {}: {}", file.getFileName(), e.toString());
      }
   }

   public static void add(Waypoint w) {
      ALL.add(w.checked());
      save();
   }

   public static void remove(Waypoint w) {
      ALL.remove(w);
      save();
   }

   public static List<String> sets() {
      TreeSet<String> sets = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      sets.add(DEFAULT_SET);

      for (Waypoint w : ALL) {
         sets.add(w.set);
      }

      return new ArrayList<>(sets);
   }

   private static final String[] XAERO_COLOURS = {
      "#000000", "#0000AA", "#00AA00", "#00AAAA", "#AA0000", "#AA00AA", "#FFAA00", "#AAAAAA",
      "#555555", "#5555FF", "#55FF55", "#55FFFF", "#FF5555", "#FF55FF", "#FFFF55", "#FFFFFF"
   };

   public static int importXaero(Minecraft mc) {
      Path root = mc.gameDirectory.toPath().resolve("XaeroWaypoints");
      String want;
      IntegratedServer server = mc.getSingleplayerServer();
      if (server != null) {
         want = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
      } else {
         ServerData data = mc.getCurrentServer();
         want = data == null ? null : "Multiplayer_" + data.ip.replace(':', '_');
      }

      Path folder = null;
      if (want != null && Files.isDirectory(root)) {
         try (var dirs = Files.list(root)) {
            for (Path p : dirs.toList()) {
               if (p.getFileName().toString().equalsIgnoreCase(want)) {
                  folder = p;
               }
            }
         } catch (IOException ignored) {
         }
      }

      if (folder == null) {
         return -1;
      }

      int added = 0;
      try (var walk = Files.walk(folder, 3)) {
         for (Path f : walk.filter(p -> p.getFileName().toString().endsWith(".txt")).toList()) {
            String dim = xaeroDimension(f.getParent().getFileName().toString());
            if (dim == null) {
               continue;
            }

            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
               Waypoint w = xaeroLine(line, dim);
               if (w != null && ALL.stream().noneMatch(o -> o.name.equals(w.name) && o.x == w.x && o.z == w.z && o.dimension.equals(w.dimension))) {
                  ALL.add(w.checked());
                  added++;
               }
            }
         }
      } catch (IOException | RuntimeException e) {
         Minimap.LOG.warn("Could not read Xaero's waypoints: {}", e.toString());
      }

      if (added > 0) {
         save();
      }

      return added;
   }

   private static String xaeroDimension(String folder) {
      return switch (folder) {
         case "dim%0" -> "minecraft:overworld";
         case "dim%-1" -> "minecraft:the_nether";
         case "dim%1" -> "minecraft:the_end";
         default -> folder.startsWith("dim%") && folder.indexOf('$') > 0 ? folder.substring(4).replace('$', ':') : null;
      };
   }

   private static Waypoint xaeroLine(String line, String dimension) {
      if (!line.startsWith("waypoint:")) {
         return null;
      }

      String[] f = line.split(":", -1);
      if (f.length < 10) {
         return null;
      }

      try {
         String name = f[1].replace("§§", ":");
         int x = Integer.parseInt(f[3]);
         int y = f[4].equals("~") ? 64 : Integer.parseInt(f[4]);
         int z = Integer.parseInt(f[5]);
         boolean death = f[8].equals("1") || name.equals("gui.xaero_deathpoint");
         Waypoint w = new Waypoint(death ? "Deathpoint" : name.startsWith("gui.xaero_") ? "Waypoint" : name, x, y, z, dimension);
         w.initials = f[2].length() <= 2 ? f[2] : f[2].substring(0, 2);
         int colour = Integer.parseInt(f[6]);
         w.colour = XAERO_COLOURS[Math.floorMod(colour, 16)];
         w.visible = !Boolean.parseBoolean(f[7]);
         w.death = death;
         w.set = f[9].isBlank() || f[9].startsWith("gui.xaero_") ? DEFAULT_SET : f[9];
         return w;
      } catch (NumberFormatException e) {
         return null;
      }
   }

   public static void died(int x, int y, int z, String dimension) {
      ALL.removeIf(w -> w.death);
      Waypoint w = new Waypoint("Death", x, y, z, dimension);
      w.initials = "X";
      w.death = true;
      w.colour = DyeColor.RED.getSerializedName();
      add(w);
   }

   public static String shareText(Waypoint w) {
      String name = w.name.replace('[', '(').replace(']', ')');
      if (name.length() > 32) {
         name = name.substring(0, 32);
      }

      return String.format(Locale.ROOT, "[Waypoint] %s @ %d, %d, %d (%s)", name.isBlank() ? "Waypoint" : name, w.x, w.y, w.z, w.dimension);
   }

   public static Waypoint parse(String text) {
      Matcher m = SHARED.matcher(text);
      if (!m.find()) {
         return null;
      }

      try {
         return new Waypoint(m.group(1).trim(), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), m.group(5));
      } catch (NumberFormatException e) {
         return null;
      }
   }

   private static String key(Minecraft mc) {
      IntegratedServer server = mc.getSingleplayerServer();
      if (server != null) {
         return "world-" + clean(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString());
      }

      ServerData data = mc.getCurrentServer();
      return data == null ? "server-unknown" : "server-" + clean(data.ip);
   }

   private static String clean(String s) {
      String out = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
      return out.isEmpty() ? "_" : out;
   }

   private static Path file(String key) {
      return FabricLoader.getInstance().getConfigDir().resolve("chunkatlas-minimap").resolve("waypoints").resolve(key + ".json");
   }
}
