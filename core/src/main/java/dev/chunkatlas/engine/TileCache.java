package dev.chunkatlas.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.Deflater;

final class TileCache {
   private static final long MAGIC = 0x4348_4154_4C41_5331L;
   private static final int VERSION = 1;
   private static final int FILE_HEAD = 16;
   private static final int LEVELS = 5;
   private static final int MAX_BLOB = 8 << 20;

   private final Path file;
   private final Object lock = new Object();
   private final Map<String, Entry> index = new HashMap<>();
   private FileChannel channel;
   private boolean opened;
   private long garbage;

   private record Entry(long size, long modified, long entities, int look, int[] lengths, long data) {
   }

   TileCache(Path home, Path worldReal) {
      this.file = home == null ? null : root(home).resolve(worldKey(worldReal) + ".atlas");
      if (home != null) {
         Path old = root(home).resolve(worldKey(worldReal));
         if (Files.isDirectory(old, LinkOption.NOFOLLOW_LINKS)) {
            Thread.ofVirtual().start(() -> deleteTree(old));
         }
      }
   }

   static String worldKey(Path worldReal) {
      return sha(worldReal.toString()).substring(0, 20);
   }

   static Path root(Path home) {
      return home.resolve("cache");
   }

   private static String sha(String text) {
      try {
         StringBuilder sb = new StringBuilder();
         for (byte b : MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8))) {
            sb.append(Character.forDigit(b >> 4 & 15, 16)).append(Character.forDigit(b & 15, 16));
         }

         return sb.toString();
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   private static String key(String dimension, int rx, int rz, int look) {
      return dimension + '|' + rx + '|' + rz + '|' + look;
   }

   private static int look(String coloursId) {
      return (coloursId + "|shader" + Shader.VERSION).hashCode();
   }

   private boolean open() {
      if (this.opened) {
         return this.channel != null;
      }

      this.opened = true;
      if (this.file == null) {
         return false;
      }

      try {
         Files.createDirectories(this.file.getParent());
         this.channel = FileChannel.open(this.file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
         long end = this.channel.size();
         if (end < FILE_HEAD || !this.headerOk()) {
            this.channel.truncate(0L);
            ByteBuffer head = ByteBuffer.allocate(FILE_HEAD);
            head.putLong(MAGIC).putInt(VERSION).putInt(0).flip();
            write(this.channel, head, 0L);
            return true;
         }

         long at = FILE_HEAD;
         ByteBuffer len = ByteBuffer.allocate(4);
         while (at + 4 <= end) {
            len.clear();
            read(this.channel, len, at);
            int length = len.flip().getInt();
            if (length <= 0 || at + 4 + length > end) {
               break;
            }

            ByteBuffer head = ByteBuffer.allocate(Math.min(length, 512));
            read(this.channel, head, at + 4);
            head.flip();
            Entry e = this.readHead(head, at + 4, length);
            if (e == null) {
               break;
            }

            Entry old = this.index.put(this.lastKey, e);
            if (old != null) {
               this.garbage += recordSize(old);
            }

            at += 4 + length;
         }

         if (at < end) {
            this.channel.truncate(at);
         }

         return true;
      } catch (IOException | RuntimeException e) {
         this.close();
         return false;
      }
   }

   private boolean headerOk() throws IOException {
      ByteBuffer head = ByteBuffer.allocate(FILE_HEAD);
      read(this.channel, head, 0L);
      head.flip();
      return head.getLong() == MAGIC && head.getInt() == VERSION;
   }

   private String lastKey;

   private Entry readHead(ByteBuffer b, long start, int length) {
      try {
         int dimLength = b.getShort() & 0xFFFF;
         byte[] dim = new byte[dimLength];
         b.get(dim);
         int rx = b.getInt();
         int rz = b.getInt();
         long size = b.getLong();
         long modified = b.getLong();
         long entities = b.getLong();
         int look = b.getInt();
         int[] lengths = new int[LEVELS + 1];
         long total = 0L;
         for (int i = 0; i <= LEVELS; i++) {
            lengths[i] = b.getInt();
            if (lengths[i] < 0 || lengths[i] > MAX_BLOB) {
               return null;
            }

            total += lengths[i];
         }

         long data = start + b.position();
         if (data + total > start + length) {
            return null;
         }

         this.lastKey = key(new String(dim, StandardCharsets.UTF_8), rx, rz, look);
         return new Entry(size, modified, entities, look, lengths, data);
      } catch (RuntimeException e) {
         return null;
      }
   }

   private static long recordSize(Entry e) {
      long n = 64L;
      for (int l : e.lengths()) {
         n += l;
      }

      return n;
   }

   Tile load(String dimension, WorldLayout.RegionInfo region, long entitiesKey, String coloursId, int level, boolean pixels, boolean facts) {
      return this.load(dimension, region, entitiesKey, coloursId, level, pixels, facts, false);
   }

   Tile load(String dimension, WorldLayout.RegionInfo region, long entitiesKey, String coloursId, int level, boolean pixels, boolean facts, boolean stale) {
      Entry e;
      FileChannel ch;
      synchronized (this.lock) {
         if (!this.open()) {
            return null;
         }

         e = this.index.get(key(dimension, region.rx(), region.rz(), look(coloursId)));
         ch = this.channel;
      }

      boolean current = e != null && e.size() == region.size() && e.modified() == region.modified() && e.entities() == entitiesKey;
      if (e == null || !current && !stale || level < 0 || level >= LEVELS) {
         return null;
      }

      try {
         int[] argb = null;
         if (pixels) {
            int side = 512 >> level;
            byte[] packed = new byte[e.lengths()[level]];
            read(ch, ByteBuffer.wrap(packed), e.data() + offset(e, level));
            byte[] raw = Packed.inflate(packed, 0, packed.length, side * side * 4 + 16);
            if (raw.length != side * side * 4) {
               return null;
            }

            argb = unpack(raw);
         }

         RegionFacts rf = null;
         if (facts) {
            byte[] packed = new byte[e.lengths()[LEVELS]];
            read(ch, ByteBuffer.wrap(packed), e.data() + offset(e, LEVELS));
            byte[] raw = Packed.inflate(packed, 0, packed.length, 1 << 20);
            rf = RegionFacts.read(new DataInputStream(new ByteArrayInputStream(raw)));
         }

         return new Tile(dimension, region.rx(), region.rz(), level, argb, rf, region.size(), region.modified());
      } catch (IOException | RuntimeException ex) {
         return null;
      }
   }

   private static long offset(Entry e, int part) {
      long n = 0L;
      for (int i = 0; i < part; i++) {
         n += e.lengths()[i];
      }

      return n;
   }

   void store(String dimension, WorldLayout.RegionInfo region, long entitiesKey, String coloursId, int[][] levels, RegionFacts facts) {
      if (this.file == null) {
         return;
      }

      try {
         byte[][] blobs = new byte[LEVELS + 1][];
         for (int i = 0; i < LEVELS; i++) {
            blobs[i] = deflate(pack(levels[i]));
         }

         ByteArrayOutputStream fb = new ByteArrayOutputStream(32768);
         facts.write(new DataOutputStream(fb));
         blobs[LEVELS] = deflate(fb.toByteArray());
         byte[] dim = dimension.getBytes(StandardCharsets.UTF_8);
         int look = look(coloursId);
         ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
         DataOutputStream d = new DataOutputStream(out);
         d.writeInt(0);
         d.writeShort(dim.length);
         d.write(dim);
         d.writeInt(region.rx());
         d.writeInt(region.rz());
         d.writeLong(region.size());
         d.writeLong(region.modified());
         d.writeLong(entitiesKey);
         d.writeInt(look);
         for (byte[] blob : blobs) {
            d.writeInt(blob.length);
         }

         int headLength = out.size() - 4;
         for (byte[] blob : blobs) {
            d.write(blob);
         }

         byte[] record = out.toByteArray();
         ByteBuffer.wrap(record).putInt(0, record.length - 4);
         int[] lengths = new int[LEVELS + 1];
         for (int i = 0; i <= LEVELS; i++) {
            lengths[i] = blobs[i].length;
         }

         synchronized (this.lock) {
            if (!this.open()) {
               return;
            }

            long at = this.channel.size();
            write(this.channel, ByteBuffer.wrap(record), at);
            Entry e = new Entry(region.size(), region.modified(), entitiesKey, look, lengths, at + 4 + headLength);
            Entry old = this.index.put(key(dimension, region.rx(), region.rz(), look), e);
            if (old != null) {
               this.garbage += recordSize(old);
            }

            if (this.garbage > 32L << 20 && this.garbage * 2 > this.channel.size()) {
               this.compact();
            }
         }
      } catch (IOException | RuntimeException ignored) {
      }
   }

   void compact() throws IOException {
      Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
      Map<String, Entry> moved = new HashMap<>();
      try (FileChannel out = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
         ByteBuffer head = ByteBuffer.allocate(FILE_HEAD);
         head.putLong(MAGIC).putInt(VERSION).putInt(0).flip();
         write(out, head, 0L);
         long at = FILE_HEAD;
         for (Map.Entry<String, Entry> kv : this.index.entrySet()) {
            Entry e = kv.getValue();
            String[] parts = kv.getKey().split("\\|");
            byte[] dim = parts[0].getBytes(StandardCharsets.UTF_8);
            long dataLength = offset(e, LEVELS + 1);
            byte[] blobs = new byte[(int)dataLength];
            read(this.channel, ByteBuffer.wrap(blobs), e.data());
            ByteArrayOutputStream rec = new ByteArrayOutputStream(blobs.length + 96);
            DataOutputStream d = new DataOutputStream(rec);
            d.writeInt(0);
            d.writeShort(dim.length);
            d.write(dim);
            d.writeInt(Integer.parseInt(parts[1]));
            d.writeInt(Integer.parseInt(parts[2]));
            d.writeLong(e.size());
            d.writeLong(e.modified());
            d.writeLong(e.entities());
            d.writeInt(e.look());
            for (int l : e.lengths()) {
               d.writeInt(l);
            }

            int headLength = rec.size() - 4;
            d.write(blobs);
            byte[] record = rec.toByteArray();
            ByteBuffer.wrap(record).putInt(0, record.length - 4);
            write(out, ByteBuffer.wrap(record), at);
            moved.put(kv.getKey(), new Entry(e.size(), e.modified(), e.entities(), e.look(), e.lengths(), at + 4 + headLength));
            at += record.length;
         }
      }

      this.channel.close();
      Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      this.channel = FileChannel.open(this.file, StandardOpenOption.READ, StandardOpenOption.WRITE);
      this.index.clear();
      this.index.putAll(moved);
      this.garbage = 0L;
   }

   void close() {
      synchronized (this.lock) {
         if (this.channel != null) {
            try {
               this.channel.close();
            } catch (IOException ignored) {
            }

            this.channel = null;
         }
      }
   }

   void clear() {
      synchronized (this.lock) {
         this.index.clear();
         this.garbage = 0L;
         if (this.channel != null) {
            try {
               this.channel.truncate(0L);
               ByteBuffer head = ByteBuffer.allocate(FILE_HEAD);
               head.putLong(MAGIC).putInt(VERSION).putInt(0).flip();
               write(this.channel, head, 0L);
            } catch (IOException e) {
               this.close();
            }
         }
      }
   }

   static long size(Path home, Path worldReal) {
      try {
         Path f = root(home).resolve(worldKey(worldReal) + ".atlas");
         return Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS) ? Files.size(f) : 0L;
      } catch (IOException | RuntimeException e) {
         return 0L;
      }
   }

   static void deleteFiles(Path home, Path worldReal, Path keepReal) {
      Path root = root(home);
      String keep = keepReal == null ? null : worldKey(keepReal) + ".atlas";
      if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
         return;
      }

      if (worldReal != null) {
         try {
            Files.deleteIfExists(root.resolve(worldKey(worldReal) + ".atlas"));
         } catch (IOException ignored) {
         }

         return;
      }

      try (Stream<Path> list = Files.list(root)) {
         for (Path p : (Iterable<Path>) list::iterator) {
            String name = p.getFileName().toString();
            if (name.endsWith(".atlas") && !name.equals(keep) && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
               try {
                  Files.deleteIfExists(p);
               } catch (IOException ignored) {
               }
            }
         }
      } catch (IOException | RuntimeException ignored) {
      }
   }

   private static byte[] pack(int[] argb) {
      ByteBuffer b = ByteBuffer.allocate(argb.length * 4);
      b.asIntBuffer().put(argb);
      return b.array();
   }

   private static int[] unpack(byte[] raw) {
      int[] out = new int[raw.length / 4];
      ByteBuffer.wrap(raw).asIntBuffer().get(out);
      return out;
   }

   private static byte[] deflate(byte[] raw) {
      Deflater d = new Deflater(6);
      try {
         d.setInput(raw);
         d.finish();
         ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 6 + 64);
         byte[] buf = new byte[16384];
         while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
         }

         return out.toByteArray();
      } finally {
         d.end();
      }
   }

   private static void read(FileChannel ch, ByteBuffer b, long at) throws IOException {
      while (b.hasRemaining()) {
         if (ch.read(b, at + b.position()) < 0) {
            throw new IOException("short cache file");
         }
      }
   }

   private static void write(FileChannel ch, ByteBuffer b, long at) throws IOException {
      while (b.hasRemaining()) {
         ch.write(b, at + b.position());
      }
   }

   private static void deleteTree(Path dir) {
      try (Stream<Path> walk = Files.walk(dir)) {
         List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
         for (Path p : all) {
            Files.deleteIfExists(p);
         }
      } catch (IOException | RuntimeException ignored) {
      }
   }

   static long size(Path home) {
      if (home == null || !Files.isDirectory(root(home))) {
         return 0L;
      }

      long total = 0L;
      try (Stream<Path> walk = Files.walk(root(home))) {
         for (Path p : (Iterable<Path>) walk::iterator) {
            if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
               try {
                  total += Files.size(p);
               } catch (IOException ignored) {
               }
            }
         }
      } catch (IOException | RuntimeException ignored) {
      }

      return total;
   }

   static void prune(Path home, long budget) {
      if (home == null) {
         return;
      }

      Path root = root(home);
      if (!Files.isDirectory(root)) {
         return;
      }

      record Item(Path file, long size, long time) {
      }

      List<Item> all = new ArrayList<>();
      long total = 0L;
      try (Stream<Path> list = Files.list(root)) {
         for (Path p : (Iterable<Path>) list::iterator) {
            if (p.getFileName().toString().endsWith(".atlas") && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
               long size = Files.size(p);
               all.add(new Item(p, size, Files.getLastModifiedTime(p).toMillis()));
               total += size;
            }
         }
      } catch (IOException | RuntimeException e) {
         return;
      }

      all.sort(Comparator.comparingLong(Item::time));

      for (int i = 0; i < all.size() - 1 && total > budget; i++) {
         try {
            Files.deleteIfExists(all.get(i).file());
            total -= all.get(i).size();
         } catch (IOException ignored) {
         }
      }
   }
}
