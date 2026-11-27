package dev.chunkatlas.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

public final class ChunkClips {
   public static final String INFO = "clip.properties";

   private ChunkClips() {
   }

   public record Clip(String name, Path folder, String sourceWorld, String dimension, int chunks, int dataVersion, long createdMillis) {
   }

   public static Path dir(Path home) {
      return home.resolve("exports");
   }

   public static Path clipboard(Path home) {
      return dir(home).resolve(".clipboard");
   }

   public static Path freshClipboard(Path home) throws IOException {
      Path folder = clipboard(home);
      delete(new Clip("", folder, "", "", 0, 0, 0L));
      Files.createDirectories(folder.resolve("region"));
      return folder;
   }

   public static Path freshScratch(Path home, String name) throws IOException {
      Path folder = dir(home).resolve("." + name);
      delete(new Clip("", folder, "", "", 0, 0, 0L));
      Files.createDirectories(folder.resolve("region"));
      return folder;
   }

   public static Clip info(Path folder) {
      return read(folder);
   }

   public static Clip clipboardInfo(Path home) {
      return read(clipboard(home));
   }

   public static ChunkSelection chunks(Path folder) {
      ChunkSelection out = new ChunkSelection();
      for (WorldLayout.RegionInfo r : WorldLayout.list(folder.resolve("region"))) {
         try {
            RegionFile.Header h = RegionFile.header(r.file());
            for (int i = 0; i < 1024; i++) {
               if (h.present(i)) {
                  out.add(r.rx() * 32 + (i & 31), r.rz() * 32 + (i >> 5));
               }
            }
         } catch (IOException | RuntimeException ignored) {
         }
      }

      return out;
   }

   public static Path create(Path home, String name) throws IOException {
      Path dir = dir(home);
      Files.createDirectories(dir);
      Path folder = free(dir, SelectionFiles.cleanName(name));
      Files.createDirectories(folder.resolve("region"));
      return folder;
   }

   public static WorldLayout.Dimension dimension(Path folder) {
      return new WorldLayout.Dimension(
         "minecraft:overworld", "Overworld", folder.resolve("region"), Optional.of(folder.resolve("entities")), Optional.of(folder.resolve("poi")), 0
      );
   }

   public static void describe(Path folder, String name, String sourceWorld, String dimension, int chunks, int dataVersion) throws IOException {
      Properties p = new Properties();
      p.setProperty("name", name);
      p.setProperty("source", sourceWorld);
      p.setProperty("dimension", dimension);
      p.setProperty("chunks", Integer.toString(chunks));
      p.setProperty("data_version", Integer.toString(dataVersion));
      p.setProperty("created", Long.toString(System.currentTimeMillis()));
      try (OutputStream out = Files.newOutputStream(folder.resolve(INFO))) {
         p.store(out, "Chunk Atlas clip");
      }
   }

   public static List<Clip> list(Path home) {
      List<Clip> out = new ArrayList<>();
      Path dir = dir(home);
      if (!Safe.plainDir(dir)) {
         return out;
      }

      try (DirectoryStream<Path> folders = Files.newDirectoryStream(dir)) {
         for (Path f : folders) {
            Clip c = f.getFileName().toString().startsWith(".") ? null : read(f);
            if (c != null) {
               out.add(c);
            }
         }
      } catch (IOException ignored) {
      }

      out.sort(Comparator.comparingLong(Clip::createdMillis).reversed());
      return out;
   }

   private static Clip read(Path f) {
      Path info = f.resolve(INFO);
      if (!Files.isDirectory(f, LinkOption.NOFOLLOW_LINKS) || !Safe.plainFile(info)) {
         return null;
      }

      Properties p = new Properties();
      try (InputStream in = Files.newInputStream(info, LinkOption.NOFOLLOW_LINKS)) {
         p.load(in);
         return new Clip(
            p.getProperty("name", f.getFileName().toString()),
            f,
            p.getProperty("source", ""),
            p.getProperty("dimension", "minecraft:overworld"),
            number(p, "chunks"),
            number(p, "data_version"),
            longNumber(p, "created")
         );
      } catch (IOException | RuntimeException e) {
         return null;
      }
   }

   public static void delete(Clip clip) throws IOException {
      if (!Files.isDirectory(clip.folder(), LinkOption.NOFOLLOW_LINKS)) {
         return;
      }

      try (var walk = Files.walk(clip.folder())) {
         for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
            Files.deleteIfExists(p);
         }
      }
   }

   static final List<String> WORLD_FILES = List.of("data/minecraft/world_gen_settings.dat", "data/minecraft/game_rules.dat");

   static final String DATA_PACKS = "datapacks";

   public static Path newWorld(Path saves, Path sourceWorld, String name) throws IOException {
      Path folder = free(saves, SelectionFiles.cleanName(name));
      Files.createDirectories(folder);
      Path level = sourceWorld.resolve("level.dat");
      if (Safe.plainFile(level)) {
         Map<String, Object> root;
         try (InputStream in = Files.newInputStream(level, LinkOption.NOFOLLOW_LINKS)) {
            root = Nbt.read(Packed.gunzip(in, 33554432));
         }

         Map<String, Object> data = Nbt.map(root, "Data");
         if (data != null) {
            data.put("LevelName", name);
         }

         try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(folder.resolve("level.dat")))) {
            out.write(Nbt.write(root));
         }
      }

      for (String file : WORLD_FILES) {
         Path from = sourceWorld.resolve(file);
         if (Safe.plainFile(from) && plainParents(sourceWorld, from)) {
            Path to = folder.resolve(file);
            Files.createDirectories(to.getParent());
            Files.copy(from, to, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
         }
      }

      copyTree(sourceWorld.resolve(DATA_PACKS), folder.resolve(DATA_PACKS), 0);
      return folder;
   }

   private static boolean plainParents(Path world, Path file) {
      for (Path p = file.getParent(); p != null && !p.equals(world); p = p.getParent()) {
         if (!Safe.plainDir(p)) {
            return false;
         }
      }

      return true;
   }

   private static void copyTree(Path from, Path to, int depth) throws IOException {
      if (depth > 16 || !Safe.plainDir(from)) {
         return;
      }

      Files.createDirectories(to);
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(from)) {
         for (Path e : entries) {
            Path dest = to.resolve(e.getFileName().toString());
            if (Safe.plainDir(e)) {
               copyTree(e, dest, depth + 1);
            } else if (Safe.plainFile(e)) {
               Files.copy(e, dest, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
            }
         }
      }
   }

   public static WorldLayout.Dimension sameDimensionIn(Path world, Path sourceWorld, WorldLayout.Dimension d) {
      Path region = world.resolve(sourceWorld.relativize(d.region()).toString());
      return new WorldLayout.Dimension(
         d.id(), d.label(), region, Optional.of(region.resolveSibling("entities")), Optional.of(region.resolveSibling("poi")), 0
      );
   }

   private static Path free(Path dir, String base) {
      String clean = base.isBlank() ? "clip" : base;
      Path p = dir.resolve(clean);
      for (int n = 2; Files.exists(p, LinkOption.NOFOLLOW_LINKS); n++) {
         p = dir.resolve(clean + " (" + n + ")");
      }

      return p;
   }

   private static long longNumber(Properties p, String key) {
      try {
         return Long.parseLong(p.getProperty(key, "0").trim());
      } catch (NumberFormatException e) {
         return 0L;
      }
   }

   private static int number(Properties p, String key) {
      try {
         return (int)Long.parseLong(p.getProperty(key, "0").trim());
      } catch (NumberFormatException e) {
         return 0;
      }
   }
}
