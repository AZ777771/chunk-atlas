package dev.chunkatlas.engine;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class LiveRegion {
   public static final String INFO = "live.properties";
   private static final int MAGIC = 0x43414C31;
   private static final int FORMAT = 2;
   private static final int SIZE = 512;
   private static final Pattern NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.live");

   public final int rx;
   public final int rz;
   public final int[] colour = new int[SIZE * SIZE];
   public final short[] height = new short[SIZE * SIZE];
   public final byte[] water = new byte[SIZE * SIZE];
   public final long[] known = new long[16];

   public final int[] top = new int[SIZE * SIZE];
   public final int[] cover = new int[SIZE * SIZE];
   public final short[] biome = new short[SIZE * SIZE];
   public final byte[] light = new byte[SIZE * SIZE];

   boolean old;

   private long changes;
   private long saved;

   public LiveRegion(int rx, int rz) {
      this.rx = rx;
      this.rz = rz;
      java.util.Arrays.fill(this.biome, (short)-1);
   }

   public synchronized Surface surface() {
      Surface s = new Surface(SIZE);
      System.arraycopy(this.top, 0, s.top, 0, this.top.length);
      System.arraycopy(this.cover, 0, s.cover, 0, this.cover.length);
      System.arraycopy(this.height, 0, s.height, 0, this.height.length);
      System.arraycopy(this.water, 0, s.depth, 0, this.water.length);
      System.arraycopy(this.biome, 0, s.biome, 0, this.biome.length);
      System.arraycopy(this.light, 0, s.light, 0, this.light.length);
      return s;
   }

   public static String name(int rx, int rz) {
      return "r." + rx + "." + rz + ".live";
   }

   public static Optional<int[]> coords(String fileName) {
      Matcher m = NAME.matcher(fileName);
      return m.matches() ? Optional.of(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))}) : Optional.empty();
   }

   public static String folder(String dimensionId) {
      return dimensionId.replace(":", "@").replace("/", "+");
   }

   public static String dimensionOf(String folder) {
      return folder.replace("@", ":").replace("+", "/");
   }

   public synchronized void put(int chunkX, int chunkZ, int[] colour, short[] height, byte[] water, int[] top, int[] cover, short[] biome, byte[] light) {
      int ox = (chunkX & 31) * 16;
      int oz = (chunkZ & 31) * 16;
      int i = (chunkZ & 31) * 32 + (chunkX & 31);
      if ((this.known[i >> 6] & 1L << (i & 63)) != 0L && this.same(ox, oz, colour, height, water, top, cover, biome, light)) {
         return;
      }

      for (int z = 0; z < 16; z++) {
         int row = (oz + z) * SIZE + ox;
         System.arraycopy(colour, z * 16, this.colour, row, 16);
         System.arraycopy(height, z * 16, this.height, row, 16);
         System.arraycopy(water, z * 16, this.water, row, 16);
         System.arraycopy(top, z * 16, this.top, row, 16);
         System.arraycopy(cover, z * 16, this.cover, row, 16);
         System.arraycopy(biome, z * 16, this.biome, row, 16);
         System.arraycopy(light, z * 16, this.light, row, 16);
      }

      this.known[i >> 6] |= 1L << (i & 63);
      this.changes++;
   }

   private boolean same(int ox, int oz, int[] colour, short[] height, byte[] water, int[] top, int[] cover, short[] biome, byte[] light) {
      for (int z = 0; z < 16; z++) {
         int row = (oz + z) * SIZE + ox;
         int from = z * 16;
         if (!java.util.Arrays.equals(colour, from, from + 16, this.colour, row, row + 16)
            || !java.util.Arrays.equals(height, from, from + 16, this.height, row, row + 16)
            || !java.util.Arrays.equals(water, from, from + 16, this.water, row, row + 16)
            || !java.util.Arrays.equals(top, from, from + 16, this.top, row, row + 16)
            || !java.util.Arrays.equals(cover, from, from + 16, this.cover, row, row + 16)
            || !java.util.Arrays.equals(biome, from, from + 16, this.biome, row, row + 16)
            || !java.util.Arrays.equals(light, from, from + 16, this.light, row, row + 16)) {
            return false;
         }
      }

      return true;
   }

   public synchronized boolean dirty() {
      return this.changes != this.saved;
   }

   public void fillFrom(LiveRegion saved) {
      synchronized (saved) {
         synchronized (this) {
            for (int i = 0; i < 1024; i++) {
               long bit = 1L << (i & 63);
               if ((saved.known[i >> 6] & bit) == 0L || (this.known[i >> 6] & bit) != 0L) {
                  continue;
               }

               int ox = (i & 31) * 16;
               int oz = (i >> 5) * 16;
               for (int z = 0; z < 16; z++) {
                  int row = (oz + z) * SIZE + ox;
                  System.arraycopy(saved.colour, row, this.colour, row, 16);
                  System.arraycopy(saved.height, row, this.height, row, 16);
                  System.arraycopy(saved.water, row, this.water, row, 16);
                  System.arraycopy(saved.top, row, this.top, row, 16);
                  System.arraycopy(saved.cover, row, this.cover, row, 16);
                  System.arraycopy(saved.biome, row, this.biome, row, 16);
                  System.arraycopy(saved.light, row, this.light, row, 16);
               }

               this.known[i >> 6] |= bit;
            }

            this.old |= saved.old;
         }
      }
   }

   record Copy(long changes, int[] colour, short[] height, byte[] water, long[] known, int[] top, int[] cover, short[] biome, byte[] light) {
   }

   synchronized LiveRegion.Copy copy() {
      return new LiveRegion.Copy(
         this.changes, this.colour.clone(), this.height.clone(), this.water.clone(), this.known.clone(), this.top.clone(), this.cover.clone(),
         this.biome.clone(), this.light.clone()
      );
   }

   public void write(Path file) throws IOException {
      if (this.dirty()) {
         this.write(file, this.copy());
      }
   }

   void write(Path file, LiveRegion.Copy copy) throws IOException {
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      try {
         Files.createDirectories(file.getParent());
         writeFile(tmp, copy);
         try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException | RuntimeException e) {
         try {
            Files.deleteIfExists(tmp);
         } catch (IOException | RuntimeException ignored) {
         }

         throw e instanceof IOException io ? io : new IOException(e);
      }

      synchronized (this) {
         if (copy.changes() > this.saved) {
            this.saved = copy.changes();
         }
      }
   }

   private static void writeFile(Path tmp, LiveRegion.Copy copy) throws IOException {
      int[] c = copy.colour();
      short[] h = copy.height();
      byte[] w = copy.water();
      long[] k = copy.known();
      int[] t = copy.top();
      int[] cv = copy.cover();
      short[] bi = copy.biome();
      byte[] li = copy.light();
      try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16))) {
         out.writeInt(MAGIC);
         out.writeInt(FORMAT);
         for (long bits : k) {
            out.writeLong(bits);
         }

         DeflaterOutputStream deflate = new DeflaterOutputStream(out);
         DataOutputStream body = new DataOutputStream(new BufferedOutputStream(deflate, 1 << 16));
         for (int v : c) {
            body.writeInt(v);
         }

         for (short v : h) {
            body.writeShort(v);
         }

         body.write(w);
         for (int v : t) {
            body.writeInt(v);
         }

         for (int v : cv) {
            body.writeInt(v);
         }

         body.write(li);

         java.util.Map<Short, Short> local = new java.util.LinkedHashMap<>();
         for (short b : bi) {
            if (b >= 0 && !local.containsKey(b)) {
               local.put(b, (short)local.size());
            }
         }

         body.writeShort(local.size());
         for (short b : local.keySet()) {
            String name = BiomeIndex.name(b);
            body.writeUTF(name == null ? "" : name);
         }

         for (short b : bi) {
            body.writeShort(b < 0 ? -1 : local.get(b));
         }

         body.flush();
         deflate.finish();
      }
   }

   public static LiveRegion read(Path file, int rx, int rz) throws IOException {
      LiveRegion r = new LiveRegion(rx, rz);
      try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS), 1 << 16))) {
         int format = readHeader(in, r.known);
         DataInputStream body = new DataInputStream(new BufferedInputStream(new InflaterInputStream(in), 1 << 16));
         for (int i = 0; i < r.colour.length; i++) {
            r.colour[i] = body.readInt();
         }

         for (int i = 0; i < r.height.length; i++) {
            r.height[i] = body.readShort();
         }

         body.readFully(r.water);
         if (format >= 2) {
            for (int i = 0; i < r.top.length; i++) {
               r.top[i] = body.readInt();
            }

            for (int i = 0; i < r.cover.length; i++) {
               r.cover[i] = body.readInt();
            }

            body.readFully(r.light);
            int names = body.readUnsignedShort();
            short[] ids = new short[names];
            for (int i = 0; i < names; i++) {
               String name = body.readUTF();
               ids[i] = name.isEmpty() ? -1 : BiomeIndex.of(name);
            }

            for (int i = 0; i < r.biome.length; i++) {
               short b = body.readShort();
               r.biome[i] = b < 0 || b >= names ? -1 : ids[b];
            }
         } else {
            r.old = true;
            java.util.Arrays.fill(r.biome, (short)-1);
            for (int i = 0; i < r.top.length; i++) {
               r.top[i] = r.colour[i] >>> 24 == 0 ? 0 : Surface.top(Look.of(r.colour[i] & 0xFFFFFF, 255, Look.TINT_NONE, 0L, 0));
            }
         }
      }

      return r;
   }

   public static long[] known(Path file) throws IOException {
      long[] bits = new long[16];
      try (DataInputStream in = new DataInputStream(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS))) {
         readHeader(in, bits);
      }

      return bits;
   }

   private static int readHeader(DataInputStream in, long[] bits) throws IOException {
      int format;
      if (in.readInt() != MAGIC || (format = in.readInt()) < 1 || format > FORMAT) {
         throw new IOException("not a live map region");
      }

      for (int i = 0; i < 16; i++) {
         bits[i] = in.readLong();
      }

      return format;
   }

   public static String title(Path folder) {
      java.util.Properties p = new java.util.Properties();
      try (java.io.InputStream in = Files.newInputStream(folder.resolve(INFO), LinkOption.NOFOLLOW_LINKS)) {
         p.load(in);
      } catch (IOException | RuntimeException ignored) {
      }

      return p.getProperty("name", folder.getFileName().toString());
   }

   public static boolean isLiveMap(Path folder) {
      return Safe.plainFile(folder.resolve(INFO));
   }

   public static WorldLayout.Layout layout(Path folder) {
      List<WorldLayout.Dimension> dims = new ArrayList<>();
      try (DirectoryStream<Path> sub = Files.newDirectoryStream(folder)) {
         for (Path d : sub) {
            if (Files.isDirectory(d, LinkOption.NOFOLLOW_LINKS)) {
               String id = dimensionOf(d.getFileName().toString());
               int files = list(d).size();
               if (files > 0) {
                  dims.add(new WorldLayout.Dimension(id, id, d, Optional.empty(), Optional.empty(), files));
               }
            }
         }
      } catch (IOException ignored) {
      }

      dims.sort((a, b) -> Integer.compare(rank(a.id()), rank(b.id())));
      return new WorldLayout.Layout(folder, List.copyOf(dims), false, dims.isEmpty() ? WorldLayout.Note.NO_TERRAIN : WorldLayout.Note.NONE);
   }

   private static int rank(String id) {
      return switch (id) {
         case "minecraft:overworld" -> 0;
         case "minecraft:the_nether" -> 1;
         case "minecraft:the_end" -> 2;
         default -> 3;
      };
   }

   public static List<WorldLayout.RegionInfo> list(Path dir) {
      List<WorldLayout.RegionInfo> out = new ArrayList<>();
      try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "r.*.live")) {
         for (Path f : files) {
            Optional<int[]> at = coords(f.getFileName().toString());
            if (at.isPresent() && Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS)) {
               out.add(new WorldLayout.RegionInfo(at.get()[0], at.get()[1], f, Files.size(f), Files.getLastModifiedTime(f, LinkOption.NOFOLLOW_LINKS).toMillis(), false));
            }
         }
      } catch (IOException ignored) {
      }

      return out;
   }
}
