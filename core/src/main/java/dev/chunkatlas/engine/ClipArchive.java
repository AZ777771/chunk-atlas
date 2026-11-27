package dev.chunkatlas.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class ClipArchive {
   private static final int MAGIC = 0x43415458;

   private static final int VERSION = 2;

   public static final int MAX_CHUNK = 16 << 20;

   public record Header(String source, String dimension, int dataVersion, int count) {
   }

   public record Entry(int cx, int cz, byte[] region, byte[] entities, byte[] poi) {
      public Entry(int cx, int cz, byte[] region, byte[] entities) {
         this(cx, cz, region, entities, null);
      }
   }

   public record Archive(Header header, List<Entry> entries) {
   }

   private ClipArchive() {
   }

   public static byte[] write(Header header, List<Entry> entries) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 16);
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(MAGIC);
      out.writeInt(VERSION);
      out.writeUTF(header.source());
      out.writeUTF(header.dimension());
      out.writeInt(header.dataVersion());
      out.writeInt(entries.size());
      for (Entry e : entries) {
         out.writeInt(e.cx());
         out.writeInt(e.cz());
         out.writeInt(e.region().length);
         out.write(e.region());
         out.writeInt(e.entities() == null ? 0 : e.entities().length);
         if (e.entities() != null) {
            out.write(e.entities());
         }

         out.writeInt(e.poi() == null ? 0 : e.poi().length);
         if (e.poi() != null) {
            out.write(e.poi());
         }
      }

      return bytes.toByteArray();
   }

   public static Archive read(byte[] data, int maxChunks) throws IOException {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
      int version = in.readInt() == MAGIC ? in.readInt() : -1;
      if (version < 1 || version > VERSION) {
         throw new IOException("not a Chunk Atlas archive");
      }

      String source = in.readUTF();
      String dimension = in.readUTF();
      int dataVersion = in.readInt();
      int count = in.readInt();
      if (count < 0 || count > maxChunks) {
         throw new IOException("too many chunks: " + count);
      }

      List<Entry> entries = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         int cx = in.readInt();
         int cz = in.readInt();
         byte[] region = bytes(in);
         byte[] entities = bytes(in);
         byte[] poi = version >= 2 ? bytes(in) : null;
         if (region == null) {
            throw new IOException("a chunk without data");
         }

         entries.add(new Entry(cx, cz, region, entities, poi));
      }

      return new Archive(new Header(source, dimension, dataVersion, count), entries);
   }

   private static byte[] bytes(DataInputStream in) throws IOException {
      int n = in.readInt();
      if (n < 0 || n > RegionWriter.MAX_PACKED) {
         throw new IOException("a chunk too big: " + n);
      }

      if (n == 0) {
         return null;
      }

      byte[] b = new byte[n];
      in.readFully(b);
      return b;
   }

   public static byte[] fromClip(Path clip, String source, String dimension, int dataVersion, int maxChunks) throws IOException {
      List<Entry> entries = new ArrayList<>();
      for (WorldLayout.RegionInfo r : WorldLayout.list(clip.resolve("region"))) {
         Path entitiesFile = clip.resolve("entities").resolve(RegionFile.name(r.rx(), r.rz()));
         Path poiFile = clip.resolve("poi").resolve(RegionFile.name(r.rx(), r.rz()));
         try (
            RegionFile region = RegionFile.open(r.file());
            RegionFile entities = Files.isRegularFile(entitiesFile) ? RegionFile.open(entitiesFile) : null;
            RegionFile poi = Files.isRegularFile(poiFile) ? RegionFile.open(poiFile) : null
         ) {
            for (int i = 0; i < 1024; i++) {
               if (!region.header().present(i)) {
                  continue;
               }

               RegionFile.Chunk c = region.chunk(i);
               if (!c.ok()) {
                  continue;
               }

               byte[] e = null;
               if (entities != null && entities.header().present(i)) {
                  RegionFile.Chunk ec = entities.chunk(i);
                  e = ec.ok() ? deflate(ec.data()) : null;
               }

               byte[] p = null;
               if (poi != null && poi.header().present(i)) {
                  RegionFile.Chunk pc = poi.chunk(i);
                  p = pc.ok() ? deflate(pc.data()) : null;
               }

               entries.add(new Entry(r.rx() * 32 + (i & 31), r.rz() * 32 + (i >> 5), deflate(c.data()), e, p));
               if (entries.size() > maxChunks) {
                  throw new IOException("too many chunks");
               }
            }
         }
      }

      return write(new Header(source, dimension, dataVersion, entries.size()), entries);
   }

   public static void toClip(Archive archive, Path folder, String name) throws IOException {
      Map<Long, Map<Integer, byte[]>> regions = new HashMap<>();
      Map<Long, Map<Integer, byte[]>> entities = new HashMap<>();
      Map<Long, Map<Integer, byte[]>> poi = new HashMap<>();
      for (Entry e : archive.entries()) {
         long key = WorldLayout.key(e.cx() >> 5, e.cz() >> 5);
         int index = ChunkSelection.index(e.cx(), e.cz());
         regions.computeIfAbsent(key, k -> new HashMap<>()).put(index, e.region());
         if (e.entities() != null) {
            entities.computeIfAbsent(key, k -> new HashMap<>()).put(index, e.entities());
         }

         if (e.poi() != null) {
            poi.computeIfAbsent(key, k -> new HashMap<>()).put(index, e.poi());
         }
      }

      int now = (int)(System.currentTimeMillis() / 1000L);
      for (Map.Entry<Long, Map<Integer, byte[]>> r : regions.entrySet()) {
         int rx = (int)(r.getKey() >> 32);
         int rz = (int)(long)r.getKey();
         RegionWriter.write(folder.resolve("region").resolve(RegionFile.name(rx, rz)), r.getValue(), now);
      }

      for (Map.Entry<Long, Map<Integer, byte[]>> r : entities.entrySet()) {
         int rx = (int)(r.getKey() >> 32);
         int rz = (int)(long)r.getKey();
         RegionWriter.write(folder.resolve("entities").resolve(RegionFile.name(rx, rz)), r.getValue(), now);
      }

      for (Map.Entry<Long, Map<Integer, byte[]>> r : poi.entrySet()) {
         int rx = (int)(r.getKey() >> 32);
         int rz = (int)(long)r.getKey();
         RegionWriter.write(folder.resolve("poi").resolve(RegionFile.name(rx, rz)), r.getValue(), now);
      }

      Header h = archive.header();
      ChunkClips.describe(folder, name, h.source(), h.dimension(), archive.entries().size(), h.dataVersion());
   }

   public static byte[] deflate(byte[] raw) {
      Deflater d = new Deflater(6);
      try {
         d.setInput(raw);
         d.finish();
         ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4 + 64);
         byte[] buf = new byte[16384];
         while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
         }

         return out.toByteArray();
      } finally {
         d.end();
      }
   }

   public static byte[] inflate(byte[] packed) throws IOException {
      Inflater i = new Inflater();
      try {
         i.setInput(packed);
         ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 4);
         byte[] buf = new byte[16384];
         while (!i.finished()) {
            int n = i.inflate(buf);
            if (n == 0 && (i.needsInput() || i.needsDictionary())) {
               throw new IOException("cut short");
            }

            out.write(buf, 0, n);
            if (out.size() > MAX_CHUNK) {
               throw new IOException("a chunk too big");
            }
         }

         return out.toByteArray();
      } catch (java.util.zip.DataFormatException e) {
         throw new IOException(e);
      } finally {
         i.end();
      }
   }
}
