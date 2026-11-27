package dev.chunkatlas.engine;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegionFile implements Closeable {
   public static final int SECTOR = 4096;
   public static final int HEADER = 8192;
   public static final int CHUNKS = 1024;
   public static final int MAX_CHUNK = 67108864;
   private static final int MAX_EXTERNAL = 67108864;
   private static final Pattern NAME = Pattern.compile("r\\.(-?\\d{1,9})\\.(-?\\d{1,9})\\.mca");
   private final Path file;
   private final FileChannel channel;
   private final RegionFile.Header header;
   private final int rx;
   private final int rz;

   public static Optional<int[]> coords(String fileName) {
      Matcher m = NAME.matcher(fileName);
      return m.matches() ? Optional.of(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))}) : Optional.empty();
   }

   public static String name(int rx, int rz) {
      return "r." + rx + "." + rz + ".mca";
   }

   public static byte[] rawHeader(Path file) throws IOException {
      try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
         return rawHeader(ch);
      }
   }

   static byte[] rawHeader(FileChannel ch) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(8192);
      long pos = 0L;

      while (buf.hasRemaining()) {
         int n = ch.read(buf, pos);
         if (n < 0) {
            break;
         }

         pos += n;
      }

      return buf.array();
   }

   public static RegionFile.Header header(Path file) throws IOException {
      BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!a.isRegularFile()) {
         throw new IOException("not a plain file");
      } else {
         return new RegionFile.Header(rawHeader(file), a.size(), a.lastModifiedTime().toMillis());
      }
   }

   private RegionFile(Path file, FileChannel channel, RegionFile.Header header, int rx, int rz) {
      this.file = file;
      this.channel = channel;
      this.header = header;
      this.rx = rx;
      this.rz = rz;
   }

   public static RegionFile open(Path file) throws IOException {
      int[] at = coords(file.getFileName().toString()).orElseThrow(() -> new IOException("not a region file"));
      BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!a.isRegularFile()) {
         throw new IOException("not a plain file");
      }

      FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

      try {
         return new RegionFile(file, ch, new RegionFile.Header(rawHeader(ch), a.size(), a.lastModifiedTime().toMillis()), at[0], at[1]);
      } catch (IOException | RuntimeException e) {
         ch.close();
         throw e;
      }
   }

   public RegionFile.Header header() {
      return this.header;
   }

   public int rx() {
      return this.rx;
   }

   public int rz() {
      return this.rz;
   }

   public static Path external(Path regionFile, int rx, int rz, int index) {
      int cx = rx * 32 + (index & 31);
      int cz = rz * 32 + (index >> 5);
      return regionFile.resolveSibling("c." + cx + "." + cz + ".mcc");
   }

   public RegionFile.Chunk chunk(int index) {
      try {
         if (!this.header.present(index)) {
            return new RegionFile.Chunk(null, "no chunk saved here");
         }

         long at = this.header.sector(index) * 4096L;
         int room = this.header.sectors(index) * 4096;
         ByteBuffer head = ByteBuffer.allocate(5);
         if (this.readFully(head, at) < 5) {
            return new RegionFile.Chunk(null, "the file ends before this chunk");
         }

         int length = head.getInt(0);
         int kind = head.get(4) & 255;
         if (length >= 1 && length <= room - 4) {
            byte[] packed;
            if ((kind & 128) != 0) {
               Path ext = external(this.file, this.rx, this.rz, index);
               BasicFileAttributes a = Files.readAttributes(ext, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
               if (!a.isRegularFile() || Safe.isLink(a) || a.size() > 67108864L || Safe.away(ext)) {
                  return new RegionFile.Chunk(null, "its separate data file cannot be used");
               }

               packed = Files.readAllBytes(ext);
               kind &= 127;
            } else {
               ByteBuffer body = ByteBuffer.allocate(length - 1);
               if (this.readFully(body, at + 5L) < length - 1) {
                  return new RegionFile.Chunk(null, "the file ends before this chunk");
               }

               packed = body.array();
            }

            switch (kind) {
               case 1:
                  return new RegionFile.Chunk(Packed.gunzip(new ByteArrayInputStream(packed), 67108864), null);
               case 2:
                  return new RegionFile.Chunk(Packed.inflate(packed, 0, packed.length, 67108864), null);
               case 3:
                  return new RegionFile.Chunk(packed, null);
               case 4:
                  return new RegionFile.Chunk(Packed.lz4(packed, 0, packed.length, 67108864), null);
               default:
                  return new RegionFile.Chunk(null, "packed in a way this viewer does not know (" + kind + ")");
            }
         } else {
            return new RegionFile.Chunk(null, "the chunk's size makes no sense");
         }
      } catch (IOException | RuntimeException e) {
         return new RegionFile.Chunk(null, e.getMessage() == null ? "could not be read" : e.getMessage());
      }
   }

   private int readFully(ByteBuffer buf, long pos) throws IOException {
      int total = 0;

      while (buf.hasRemaining()) {
         int n = this.channel.read(buf, pos + total);
         if (n < 0) {
            break;
         }

         total += n;
      }

      return total;
   }

   @Override
   public void close() throws IOException {
      this.channel.close();
   }

   public record Chunk(byte[] data, String problem) {
      public boolean ok() {
         return this.data != null;
      }
   }

   public static final class Header {
      public final int[] locations = new int[1024];
      public final int[] timestamps = new int[1024];
      public final long fileSize;
      public final long modified;

      Header(byte[] raw, long fileSize, long modified) {
         ByteBuffer b = ByteBuffer.wrap(raw);

         for (int i = 0; i < 1024; i++) {
            this.locations[i] = b.getInt(i * 4);
            this.timestamps[i] = b.getInt(4096 + i * 4);
         }

         this.fileSize = fileSize;
         this.modified = modified;
      }

      public int sector(int index) {
         return this.locations[index] >>> 8;
      }

      public int sectors(int index) {
         return this.locations[index] & 0xFF;
      }

      public boolean present(int index) {
         int loc = this.locations[index];
         if (loc == 0) {
            return false;
         }

         int sector = loc >>> 8;
         int count = loc & 0xFF;
         return sector >= 2 && count > 0 && sector * 4096L < this.fileSize;
      }

      public int count() {
         int n = 0;

         for (int i = 0; i < 1024; i++) {
            if (this.present(i)) {
               n++;
            }
         }

         return n;
      }
   }
}
