package dev.chunkatlas.engine;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class Safe {
   public static final int DATALESS = 1073741824;

   private static final Safe.Flags PLATFORM = null;
   private static volatile Safe.Flags flags = PLATFORM;
   private static final AtomicLong COUNTER = new AtomicLong();

   private Safe() {
   }

   static Safe.Flags flags(Safe.Flags other) {
      Safe.Flags before = flags;
      flags = other;
      return before;
   }

   static Safe.Flags platformFlags() {
      return PLATFORM;
   }

   public static boolean isLink(BasicFileAttributes a) {
      return a.isSymbolicLink() || a.isOther();
   }

   public static boolean isLink(Path p) {
      try {
         return isLink(Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
      } catch (IOException e) {
         return true;
      }
   }

   public static boolean plainDir(Path p) {
      try {
         BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
         return a.isDirectory() && !isLink(a) && !away(p);
      } catch (IOException e) {
         return false;
      }
   }

   public static boolean plainFile(Path p) {
      try {
         BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
         return a.isRegularFile() && !isLink(a) && !away(p);
      } catch (IOException e) {
         return false;
      }
   }

   public static boolean[] away(List<Path> files) {
      boolean[] out = new boolean[files.size()];
      Safe.Flags reader = flags;
      if (reader != null && !files.isEmpty()) {
         int[] got;
         try {
            got = reader.read(files);
         } catch (RuntimeException e) {
            got = null;
         }

         for (int i = 0; i < out.length; i++) {
            out[i] = got == null || i >= got.length || got[i] == -1 || (got[i] & DATALESS) != 0;
         }

         return out;
      } else {
         return out;
      }
   }

   public static boolean away(Path file) {
      return flags != null && away(List.of(file))[0];
   }

   public static void write(Path file, byte[] data) throws IOException {
      Files.createDirectories(file.getParent());
      Path temp = temp(file);

      try {
         try (FileChannel ch = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.wrap(data);

            while (buf.hasRemaining()) {
               ch.write(buf);
            }

            ch.force(true);
         }

         replace(temp, file);
      } finally {
         Files.deleteIfExists(temp);
      }
   }

   static Path temp(Path file) {
      return file.resolveSibling(file.getFileName() + "." + Long.toHexString(System.nanoTime()) + "-" + COUNTER.incrementAndGet() + ".tmp");
   }

   public static void replace(Path from, Path to) throws IOException {
      try {
         Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
         Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public static long free(Path dir) {
      Path p = dir.toAbsolutePath();

      while (p != null && !Files.exists(p)) {
         p = p.getParent();
      }

      try {
         return p == null ? -1L : Files.getFileStore(p).getUsableSpace();
      } catch (IOException | RuntimeException e) {
         return -1L;
      }
   }

   public static Message size(long bytes) {
      if (bytes >= 1073741824L) {
         return Message.of("chunkatlas.size.gb", String.format(Locale.ROOT, "%.1f", bytes / 1.0737418E9F));
      } else if (bytes >= 10485760L) {
         return Message.of("chunkatlas.size.mb", String.valueOf(bytes >> 20));
      } else {
         return bytes >= 1048576L
            ? Message.of("chunkatlas.size.mb", String.format(Locale.ROOT, "%.1f", bytes / 1048576.0))
            : Message.of("chunkatlas.size.kb", String.valueOf(Math.max(1L, bytes >> 10)));
      }
   }

   public interface Flags {
      int[] read(List<Path> files);
   }
}
