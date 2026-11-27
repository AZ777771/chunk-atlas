package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EditSnapshots {
   private static final Pattern REGION = Pattern.compile("r\\.(-?\\d{1,9})\\.(-?\\d{1,9})\\.mca");
   private static final Pattern EXTERNAL = Pattern.compile("c\\.(-?\\d{1,9})\\.(-?\\d{1,9})\\.mcc");

   private static final String RUN = Long.toHexString(ThreadLocalRandom.current().nextLong() >>> 1);
   private static final AtomicInteger NEXT = new AtomicInteger();
   private static final String DIR = "waiting";

   private EditSnapshots() {
   }

   public static Path dir(Path home) {
      return home.resolve(DIR);
   }

   public static Path fresh(Path home) throws IOException {
      Path dir = dir(home);
      Files.createDirectories(dir);
      Path folder;
      do {
         folder = dir.resolve(RUN + "-" + NEXT.incrementAndGet());
      } while (Files.exists(folder, LinkOption.NOFOLLOW_LINKS));

      Files.createDirectories(folder.resolve("region"));
      return folder;
   }

   public static void empty(Path folder) throws IOException {
      Path parent = folder.toAbsolutePath().normalize().getParent();
      if (parent == null || !parent.getFileName().toString().equals(DIR) || !Safe.plainDir(folder)) {
         throw new IOException("not a waiting edit's folder");
      }

      try (var walk = Files.walk(folder)) {
         for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
            if (!p.equals(folder)) {
               Files.deleteIfExists(p);
            }
         }
      }

      Files.createDirectories(folder.resolve("region"));
   }

   public static Path take(Path home, WorldLayout.Dimension source, ChunkSelection chunks) throws IOException {
      Path folder = fresh(home);
      try {
         copyRole(source.region(), folder.resolve("region"), chunks);
         if (source.entities().isPresent()) {
            copyRole(source.entities().get(), folder.resolve("entities"), chunks);
         }

         if (source.poi().isPresent()) {
            copyRole(source.poi().get(), folder.resolve("poi"), chunks);
         }

         return folder;
      } catch (IOException | RuntimeException e) {
         drop(home, folder);
         throw e;
      }
   }

   private static void copyRole(Path from, Path to, ChunkSelection chunks) throws IOException {
      if (!Safe.plainDir(from)) {
         return;
      }

      Files.createDirectories(to);
      try (DirectoryStream<Path> files = Files.newDirectoryStream(from)) {
         for (Path f : files) {
            String name = f.getFileName().toString();
            Matcher r = REGION.matcher(name);
            Matcher c = EXTERNAL.matcher(name);
            boolean wanted;
            if (r.matches()) {
               int rx = Integer.parseInt(r.group(1));
               int rz = Integer.parseInt(r.group(2));
               wanted = chunks.count(rx, rz) > 0;
            } else if (c.matches()) {
               int cx = Integer.parseInt(c.group(1));
               int cz = Integer.parseInt(c.group(2));
               wanted = chunks.count(cx >> 5, cz >> 5) > 0;
            } else {
               wanted = false;
            }

            if (wanted && Safe.plainFile(f)) {
               Files.copy(f, to.resolve(name), StandardCopyOption.COPY_ATTRIBUTES);
            }
         }
      }
   }

   public static void drop(Path home, Path folder) {
      if (folder == null || !folder.toAbsolutePath().normalize().startsWith(dir(home).toAbsolutePath().normalize()) || !Safe.plainDir(folder)) {
         return;
      }

      try (var walk = Files.walk(folder)) {
         for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
            Files.deleteIfExists(p);
         }
      } catch (IOException | RuntimeException ignored) {
      }
   }

   public static void clearOld(Path home) {
      Path dir = dir(home);
      if (Safe.plainDir(dir)) {
         try (DirectoryStream<Path> folders = Files.newDirectoryStream(dir)) {
            for (Path f : folders) {
               if (!f.getFileName().toString().startsWith(RUN + "-")) {
                  drop(home, f);
               }
            }
         } catch (IOException | RuntimeException ignored) {
         }
      }

      Path oldFields = ChunkClips.dir(home).resolve(".fields");
      if (Safe.plainDir(oldFields)) {
         try {
            ChunkClips.delete(new ChunkClips.Clip("", oldFields, "", "", 0, 0, 0L));
         } catch (IOException | RuntimeException ignored) {
         }
      }
   }

   static long count(Path home) throws IOException {
      Path dir = dir(home);
      if (!Safe.plainDir(dir)) {
         return 0L;
      }

      try (var list = Files.list(dir)) {
         return list.count();
      }
   }
}
