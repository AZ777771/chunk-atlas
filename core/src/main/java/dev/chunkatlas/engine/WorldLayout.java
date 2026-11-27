package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WorldLayout {
   private WorldLayout() {
   }

   public static long key(int rx, int rz) {
      return (long)rx << 32 | rz & 4294967295L;
   }

   public static WorldLayout.Layout read(Path given) {
      Map<String, WorldLayout.Dimension> found = new LinkedHashMap<>();
      boolean[] mcr = new boolean[1];

      Path world;
      try {
         world = given.toRealPath();
      } catch (IOException e) {
         return new WorldLayout.Layout(given, List.of(), false, WorldLayout.Note.MISSING);
      }

      if (!Files.isDirectory(world)) {
         return new WorldLayout.Layout(given, List.of(), false, WorldLayout.Note.MISSING);
      }

      if (Safe.away(world)) {
         return new WorldLayout.Layout(world, List.of(), false, WorldLayout.Note.AWAY);
      }

      add(found, mcr, "minecraft:overworld", world);
      add(found, mcr, "minecraft:the_nether", world.resolve("DIM-1"));
      add(found, mcr, "minecraft:the_end", world.resolve("DIM1"));

      try (DirectoryStream<Path> top = Files.newDirectoryStream(world, "DIM*")) {
         for (Path p : top) {
            String n = p.getFileName().toString();
            if (!n.equals("DIM-1") && !n.equals("DIM1") && n.matches("DIM-?\\d{1,9}")) {
               add(found, mcr, n, p);
            }
         }
      } catch (IOException ignored) {
      }

      Path dims = world.resolve("dimensions");
      if (plainDir(dims)) {
         walk(found, mcr, dims, new ArrayList<>(), 0);
      }

      List<WorldLayout.Dimension> out = new ArrayList<>(found.values());
      out.sort(Comparator.<WorldLayout.Dimension>comparingInt(d -> rank(d.id())).thenComparing(WorldLayout.Dimension::id));
      return out.isEmpty()
         ? new WorldLayout.Layout(
            world,
            List.of(),
            mcr[0],
            mcr[0] ? WorldLayout.Note.TOO_OLD : WorldLayout.Note.NO_TERRAIN
         )
         : new WorldLayout.Layout(world, List.copyOf(out), false, WorldLayout.Note.NONE);
   }

   public static Map<Long, WorldLayout.RegionInfo> regions(WorldLayout.Dimension d) {
      Map<Long, WorldLayout.RegionInfo> out = new LinkedHashMap<>();

      for (WorldLayout.RegionInfo r : list(d.region())) {
         out.put(key(r.rx(), r.rz()), r);
      }

      return out;
   }

   public static List<WorldLayout.RegionInfo> list(Path dir) {
      List<WorldLayout.RegionInfo> out = new ArrayList<>();
      if (!plainDir(dir)) {
         return out;
      }

      List<Path> paths = new ArrayList<>();

      try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "r.*.mca")) {
         for (Path f : files) {
            Optional<int[]> at = RegionFile.coords(f.getFileName().toString());
            if (!at.isEmpty()) {
               try {
                  BasicFileAttributes a = Files.readAttributes(f, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                  if (a.isRegularFile() && !Safe.isLink(a)) {
                     out.add(new WorldLayout.RegionInfo(at.get()[0], at.get()[1], f, a.size(), a.lastModifiedTime().toMillis(), false));
                     paths.add(f);
                  }
               } catch (IOException ignored) {
               }
            }
         }
      } catch (IOException ignored) {
      }

      boolean[] away = Safe.away(paths);

      for (int i = 0; i < away.length; i++) {
         if (away[i]) {
            WorldLayout.RegionInfo r = out.get(i);
            out.set(i, new WorldLayout.RegionInfo(r.rx(), r.rz(), r.file(), r.size(), r.modified(), true));
         }
      }

      return out;
   }

   private static int rank(String id) {
      return switch (id) {
         case "minecraft:overworld" -> 0;
         case "minecraft:the_nether" -> 1;
         case "minecraft:the_end" -> 2;
         default -> 3;
      };
   }

   public static String label(String id) {
      return switch (id) {
         case "minecraft:overworld" -> "Overworld";
         case "minecraft:the_nether" -> "Nether";
         case "minecraft:the_end" -> "The End";
         default -> id.startsWith("DIM") ? "Dimension " + id.substring(3) : id;
      };
   }

   private static boolean plainDir(Path p) {
      return Safe.plainDir(p);
   }

   private static void walk(Map<String, WorldLayout.Dimension> found, boolean[] mcr, Path dir, List<String> parts, int depth) {
      if (parts.size() >= 2) {
         add(found, mcr, parts.get(0) + ":" + String.join("/", parts.subList(1, parts.size())), dir);
      }

      if (depth < 6) {
         try (DirectoryStream<Path> kids = Files.newDirectoryStream(dir)) {
            for (Path k : kids) {
               String n = k.getFileName().toString();
               if ((parts.size() < 2 || !n.equals("region") && !n.equals("entities") && !n.equals("poi") && !n.equals("data")) && plainDir(k)) {
                  parts.add(n);
                  walk(found, mcr, k, parts, depth + 1);
                  parts.remove(parts.size() - 1);
               }
            }
         } catch (IOException ignored) {
         }
      }
   }

   private static void add(Map<String, WorldLayout.Dimension> found, boolean[] mcr, String id, Path folder) {
      Path region = folder.resolve("region");
      if (plainDir(folder) && plainDir(region)) {
         int n = 0;

         try (DirectoryStream<Path> files = Files.newDirectoryStream(region)) {
            for (Path f : files) {
               String name = f.getFileName().toString();
               if (RegionFile.coords(name).isPresent()) {
                  n++;
               } else if (name.endsWith(".mcr")) {
                  mcr[0] = true;
               }
            }
         } catch (IOException e) {
            return;
         }

         WorldLayout.Dimension old = found.get(id);
         if (n != 0 && (old == null || old.regionFiles() < n)) {
            Path entities = folder.resolve("entities");
            Path poi = folder.resolve("poi");
            found.put(
               id,
               new WorldLayout.Dimension(
                  id, label(id), region, plainDir(entities) ? Optional.of(entities) : Optional.empty(), plainDir(poi) ? Optional.of(poi) : Optional.empty(), n
               )
            );
         }
      }
   }

   public record Dimension(String id, String label, Path region, Optional<Path> entities, Optional<Path> poi, int regionFiles) {
   }

   public enum Note {
      NONE(""),
      MISSING("chunkatlas.layout.missing"),
      AWAY("chunkatlas.layout.away"),
      TOO_OLD("chunkatlas.layout.too_old"),
      NO_TERRAIN("chunkatlas.layout.no_terrain");

      private final String key;

      Note(String key) {
         this.key = key;
      }

      public String key() {
         return this.key;
      }
   }

   public record Layout(Path world, List<WorldLayout.Dimension> dimensions, boolean tooOld, WorldLayout.Note note) {
      public Optional<WorldLayout.Dimension> dimension(String id) {
         return this.dimensions.stream().filter(d -> d.id().equals(id)).findFirst();
      }
   }

   public record RegionInfo(int rx, int rz, Path file, long size, long modified, boolean away) {
   }
}
