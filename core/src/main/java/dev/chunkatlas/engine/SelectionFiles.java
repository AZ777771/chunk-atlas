package dev.chunkatlas.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class SelectionFiles {
   public static final String EXTENSION = ".csv";
   private static final int MAX_NAME = 64;
   private static final long MAX_READ = 256L * 1024L * 1024L;
   private static final Pattern UNSAFE = Pattern.compile("[^\\p{L}\\p{N} ._()'&+,-]");
   private static final Set<String> RESERVED = Set.of(
      "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
      "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
   );

   private SelectionFiles() {
   }

   public static Path dir(Path home, Path world, String dimensionId) {
      Path real;
      try {
         real = world.toRealPath();
      } catch (IOException e) {
         real = world.toAbsolutePath().normalize();
      }

      String dimension = (dimensionId == null || dimensionId.isEmpty() ? "unknown" : dimensionId).replaceAll("[^A-Za-z0-9._-]", "_");
      return home.resolve("selections").resolve(TileCache.worldKey(real)).resolve(dimension);
   }

   public static String cleanName(String name) {
      String s = name == null ? "" : UNSAFE.matcher(name.strip()).replaceAll("_");
      s = s.replaceAll("\\s+", " ").strip();

      while (s.startsWith(".")) {
         s = s.substring(1);
      }

      while (s.endsWith(".") || s.endsWith(" ")) {
         s = s.substring(0, s.length() - 1);
      }

      if (s.length() > MAX_NAME) {
         s = s.substring(0, MAX_NAME).strip();
      }

      if (s.isEmpty()) {
         s = "selection";
      }

      String stem = s.contains(".") ? s.substring(0, s.indexOf('.')) : s;
      if (RESERVED.contains(stem.toLowerCase(Locale.ROOT))) {
         s = "_" + s;
      }

      return s;
   }

   public static Path save(Path dir, String name, ChunkSelection selection) throws IOException {
      Files.createDirectories(dir);
      Path file = dir.resolve(cleanName(name) + EXTENSION);
      write(file, selection);
      return file;
   }

   public static void write(Path file, ChunkSelection selection) throws IOException {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
         Files.createDirectories(parent);
      }

      Path temp = (parent == null ? Path.of(".") : parent).resolve(".saving-" + System.nanoTime() + ".tmp");
      try {
         try (BufferedWriter out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            TreeSet<Long> keys = new TreeSet<>(Comparator.<Long>comparingInt(k -> (int)k.longValue()).thenComparingInt(k -> (int)(k >> 32)));
            keys.addAll(selection.regions());

            for (long key : keys) {
               int rx = (int)(key >> 32);
               int rz = (int)key;
               long[] bits = selection.bits(rx, rz);
               if (bits == null) {
                  continue;
               }

               if (selection.count(rx, rz) == 1024) {
                  out.write(rx + ";" + rz + "\n");
                  continue;
               }

               for (int i = 0; i < 1024; i++) {
                  if ((bits[i >> 6] & 1L << (i & 63)) != 0L) {
                     out.write(rx + ";" + rz + ";" + ((rx << 5) + (i & 31)) + ";" + ((rz << 5) + (i >> 5)) + "\n");
                  }
               }
            }
         }

         try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
         }
      } finally {
         Files.deleteIfExists(temp);
      }
   }

   public static ChunkSelection load(Path file) throws IOException {
      if (Files.size(file) > MAX_READ) {
         throw new IOException("too big");
      }

      ChunkSelection out = new ChunkSelection();
      long[] whole = new long[ChunkSelection.WORDS];
      Arrays.fill(whole, -1L);

      try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
         String line;
         while ((line = in.readLine()) != null) {
            String[] f = line.strip().split("\\s*;\\s*");
            try {
               if (f.length == 2) {
                  out.add(Integer.parseInt(f[0]), Integer.parseInt(f[1]), whole);
               } else if (f.length == 4) {
                  out.add(Integer.parseInt(f[2]), Integer.parseInt(f[3]));
               }
            } catch (NumberFormatException ignored) {
            }
         }
      }

      return out;
   }

   public static List<SelectionFiles.Saved> list(Path dir) {
      List<SelectionFiles.Saved> out = new ArrayList<>();
      if (!Files.isDirectory(dir)) {
         return out;
      }

      try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + EXTENSION)) {
         for (Path f : files) {
            try {
               BasicFileAttributes a = Files.readAttributes(f, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
               if (a.isRegularFile()) {
                  String name = f.getFileName().toString();
                  name = name.substring(0, name.length() - EXTENSION.length());
                  out.add(new SelectionFiles.Saved(name, f, load(f).size(), a.lastModifiedTime().toMillis()));
               }
            } catch (IOException | RuntimeException ignored) {
            }
         }
      } catch (IOException ignored) {
      }

      out.sort(Comparator.comparingLong(SelectionFiles.Saved::modifiedMillis).reversed().thenComparing(SelectionFiles.Saved::name));
      return out;
   }

   public static void delete(Path file) throws IOException {
      Files.deleteIfExists(file);
   }

   public static Path export(Path file, Path toDir) throws IOException {
      Files.createDirectories(toDir);
      Path to = toDir.resolve(file.getFileName().toString());
      Files.copy(file, to, StandardCopyOption.REPLACE_EXISTING);
      return to;
   }

   public record Saved(String name, Path file, int chunks, long modifiedMillis) {
   }
}
