package dev.chunkatlas.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class LevelMarkers {
   private static final int CAP = 33554432;

   private LevelMarkers() {
   }

   public static LevelMarkers.Markers read(Path world) {
      try {
         Map<String, Object> data = Nbt.map(gz(world.resolve("level.dat")), "Data");
         if (data == null) {
            return LevelMarkers.Markers.NONE;
         }

         Optional<LevelMarkers.Point> spawn = Optional.empty();
         Map<String, Object> s = Nbt.map(data, "spawn");
         if (s != null && s.get("pos") instanceof int[] p && p.length >= 3) {
            spawn = Optional.of(new LevelMarkers.Point(dimension(s.get("dimension")), p[0], p[1], p[2]));
         } else if (Nbt.has(data, "SpawnX")) {
            spawn = Optional.of(
               new LevelMarkers.Point("minecraft:overworld", Nbt.number(data, "SpawnX", 0L), Nbt.number(data, "SpawnY", 64L), Nbt.number(data, "SpawnZ", 0L))
            );
         }

         Optional<LevelMarkers.Point> player = player(Nbt.map(data, "Player"));
         Path mainFile = player.isEmpty() ? mainPlayerFile(world, data) : null;
         if (mainFile != null) {
            player = player(gz(mainFile));
         }

         Map<String, Object> version = Nbt.map(data, "Version");
         String name = version == null ? "" : String.valueOf(version.getOrDefault("Name", ""));
         return new LevelMarkers.Markers(
            spawn, player, (int)Nbt.number(data, "DataVersion", 0L), name, others(world, data, mainFile), Nbt.number(data, "allowCommands", 0L) != 0L
         );
      } catch (IOException | RuntimeException e) {
         return LevelMarkers.Markers.NONE;
      }
   }

   public static String name(Path world) {
      String folder = world.getFileName() == null ? world.toString() : world.getFileName().toString();

      try {
         Map<String, Object> data = Nbt.map(gz(world.resolve("level.dat")), "Data");
         return (data == null ? null : data.get("LevelName")) instanceof String s && !s.isBlank() ? s : folder;
      } catch (IOException | RuntimeException e) {
         return folder;
      }
   }

   public record Generation(Optional<Long> seed, Map<String, String> generators) {
      public static final LevelMarkers.Generation NONE = new LevelMarkers.Generation(Optional.empty(), Map.of());

      public String generator(String dimension) {
         return this.generators.get(dimension);
      }
   }

   public static LevelMarkers.Generation generation(Path world) {
      try {
         Map<String, Object> data = Nbt.map(gz(world.resolve("level.dat")), "Data");
         Map<String, Object> gen = Nbt.map(data, "WorldGenSettings");

         Path own = world.resolve("data/minecraft/world_gen_settings.dat");
         if (gen == null && java.nio.file.Files.isRegularFile(own)) {
            gen = Nbt.map(gz(own), "data");
         }

         if (gen == null) {
            return LevelMarkers.Generation.NONE;
         }

         Optional<Long> seed = Nbt.has(gen, "seed") ? Optional.of(Nbt.number(gen, "seed", 0L)) : Optional.empty();
         Map<String, String> generators = new java.util.HashMap<>();
         Map<String, Object> dims = Nbt.map(gen, "dimensions");
         if (dims != null) {
            for (Map.Entry<String, Object> e : dims.entrySet()) {
               String type = Nbt.string(Nbt.map(e.getValue(), "generator"), "type");
               if (type != null) {
                  generators.put(e.getKey(), type);
               }
            }
         }

         return new LevelMarkers.Generation(seed, Map.copyOf(generators));
      } catch (IOException | RuntimeException e) {
         return LevelMarkers.Generation.NONE;
      }
   }

   public static double[] border(Path world, String dimension) {
      int colon = dimension.indexOf(':');
      String ns = colon < 0 ? "minecraft" : dimension.substring(0, colon);
      String path = colon < 0 ? dimension : dimension.substring(colon + 1);
      double[] out = null;
      try {
         Path file = world.resolve("dimensions").resolve(ns).resolve(path).resolve("data").resolve("minecraft").resolve("world_border.dat");
         if (Safe.plainFile(file)) {
            Map<String, Object> d = Nbt.map(gz(file), "data");
            if (d != null) {
               out = new double[]{number(d, "center_x"), number(d, "center_z"), number(d, "size")};
            }
         } else if (dimension.equals("minecraft:overworld")) {
            Map<String, Object> d = Nbt.map(gz(world.resolve("level.dat")), "Data");
            if (d != null && d.containsKey("BorderSize")) {
               out = new double[]{number(d, "BorderCenterX"), number(d, "BorderCenterZ"), number(d, "BorderSize")};
            }
         }
      } catch (IOException | RuntimeException ignored) {
      }

      return out == null || out[2] >= 59_000_000.0 ? null : out;
   }

   private static double number(Map<String, Object> m, String key) {
      return m.get(key) instanceof Number n ? n.doubleValue() : 0.0;
   }

   private static Map<String, Object> gz(Path file) throws IOException {
      if (Safe.plainFile(file) && Files.size(file) <= 33554432L) {
         try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return Nbt.read(Packed.gunzip(in, 33554432));
         }
      } else {
         throw new IOException("not readable");
      }
   }

   private static Optional<LevelMarkers.Point> player(Map<String, Object> p) {
      List<Object> pos = p == null ? null : Nbt.list(p, "Pos");
      return pos != null && pos.size() >= 3 && pos.get(0) instanceof Number x && pos.get(1) instanceof Number y && pos.get(2) instanceof Number z
         ? Optional.of(new LevelMarkers.Point(dimension(p.get("Dimension")), x.doubleValue(), y.doubleValue(), z.doubleValue(), yaw(p)))
         : Optional.empty();
   }

   private static float yaw(Map<String, Object> p) {
      List<Object> r = Nbt.list(p, "Rotation");
      return r != null && !r.isEmpty() && r.get(0) instanceof Number n ? n.floatValue() : Float.NaN;
   }

   private static Path mainPlayerFile(Path world, Map<String, Object> data) {
      for (String folder : new String[]{"players/data", "playerdata"}) {
         Path dir = world.resolve(folder);
         if ((dir.getParent() == null || dir.getParent().equals(world) || Safe.plainDir(dir.getParent())) && Safe.plainDir(dir)) {
            try {
               if (data.get("singleplayer_uuid") instanceof int[] u && u.length == 4) {
                  UUID id = new UUID((long)u[0] << 32 | u[1] & 4294967295L, (long)u[2] << 32 | u[3] & 4294967295L);
                  Path f = dir.resolve(id + ".dat");
                  if (Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS)) {
                     return f;
                  }
               }

               Path newest = null;
               long newestAt = Long.MIN_VALUE;
               int n = 0;
               try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.dat")) {
                  for (Path f : files) {
                     if (++n > MAX_PLAYERS) {
                        break;
                     }

                     if (Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS)) {
                        long at = Files.getLastModifiedTime(f, LinkOption.NOFOLLOW_LINKS).toMillis();
                        if (at > newestAt) {
                           newest = f;
                           newestAt = at;
                        }
                     }
                  }
               }

               if (newest != null) {
                  return newest;
               }
            } catch (IOException | RuntimeException ignored) {
            }
         }
      }

      return null;
   }

   private static String dimension(Object d) {
      if (d instanceof String s && !s.isEmpty()) {
         return s.indexOf(':') < 0 ? "minecraft:" + s : s;
      } else if (d instanceof Number n) {
         return switch (n.intValue()) {
            case -1 -> "minecraft:the_nether";
            case 0 -> "minecraft:overworld";
            case 1 -> "minecraft:the_end";
            default -> "DIM" + n.intValue();
         };
      } else {
         return "minecraft:overworld";
      }
   }

   private static List<LevelMarkers.Player> others(Path world, Map<String, Object> data, Path mainFile) {
      List<LevelMarkers.Player> out = new java.util.ArrayList<>();
      String main = mainFile == null ? "" : mainFile.getFileName().toString().replace(".dat", "");
      String host = data.get("singleplayer_uuid") instanceof int[] u && u.length == 4
         ? new UUID((long)u[0] << 32 | u[1] & 4294967295L, (long)u[2] << 32 | u[3] & 4294967295L).toString()
         : main;
      for (String folder : new String[]{"players/data", "playerdata"}) {
         Path dir = world.resolve(folder);
         if (!Safe.plainDir(dir)) {
            continue;
         }

         try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.dat")) {
            for (Path f : files) {
               if (out.size() >= MAX_PLAYERS) {
                  break;
               }

               String id = f.getFileName().toString();
               id = id.substring(0, id.length() - 4);
               if (id.equals(host)) {
                  continue;
               }

               try {
                  Optional<LevelMarkers.Point> at = player(gz(f));
                  if (at.isPresent()) {
                     out.add(new LevelMarkers.Player(id, at.get()));
                  }
               } catch (IOException | RuntimeException ignored) {
               }
            }
         } catch (IOException | RuntimeException ignored) {
         }

         if (!out.isEmpty()) {
            break;
         }
      }

      return List.copyOf(out);
   }

   private static final int MAX_PLAYERS = 500;

   public record Markers(
      Optional<LevelMarkers.Point> spawn, Optional<LevelMarkers.Point> player, int dataVersion, String versionName, List<LevelMarkers.Player> players, boolean commands
   ) {
      public static final LevelMarkers.Markers NONE = new LevelMarkers.Markers(Optional.empty(), Optional.empty(), 0, "", List.of(), false);
   }

   public record Player(String id, LevelMarkers.Point at) {
   }

   public record Point(String dimension, double x, double y, double z, float yaw) {
      public Point(String dimension, double x, double y, double z) {
         this(dimension, x, y, z, Float.NaN);
      }
   }
}
