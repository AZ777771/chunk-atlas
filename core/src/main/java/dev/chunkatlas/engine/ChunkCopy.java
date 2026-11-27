package dev.chunkatlas.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.Deflater;

public final class ChunkCopy {
   static final List<String> ROLES = List.of("region", "entities", "poi");

   public static final int MAX_OFFSET = 1_000_000;

   public static final int MAX_BLEND = 8;
   private static final int MAX_SECTORS = 255;

   private ChunkCopy() {
   }

   private static volatile boolean keepStructures = true;

   public static void keepStructures(boolean keep) {
      keepStructures = keep;
   }

   public static boolean keepStructures() {
      return keepStructures;
   }

   public interface Transform {
      void apply(String role, Map<String, Object> root);
   }

   public record Request(
      Path sourceWorld,
      String sourceName,
      WorldLayout.Dimension sourceDimension,
      ChunkSelection selection,
      Path destWorld,
      String destName,
      WorldLayout.Dimension destDimension,
      int dx,
      int dz,
      ChunkSelection protectedChunks,
      int sourceVersion,
      int destVersion,
      ChunkCopy.Transform transform,
      int blend,
      int dy,

      PasteLimit limit
   ) {
      public Request(
         Path sourceWorld, String sourceName, WorldLayout.Dimension sourceDimension, ChunkSelection selection, Path destWorld, String destName,
         WorldLayout.Dimension destDimension, int dx, int dz, ChunkSelection protectedChunks, int sourceVersion, int destVersion, ChunkCopy.Transform transform,
         int blend, int dy
      ) {
         this(sourceWorld, sourceName, sourceDimension, selection, destWorld, destName, destDimension, dx, dz, protectedChunks, sourceVersion, destVersion, transform, blend, dy, null);
      }

      public ChunkCopy.Request limited(PasteLimit limit) {
         return new ChunkCopy.Request(
            this.sourceWorld, this.sourceName, this.sourceDimension, this.selection, this.destWorld, this.destName, this.destDimension, this.dx, this.dz,
            this.protectedChunks, this.sourceVersion, this.destVersion, this.transform, this.blend, this.dy, limit
         );
      }

      public Request(
         Path sourceWorld, String sourceName, WorldLayout.Dimension sourceDimension, ChunkSelection selection, Path destWorld, String destName,
         WorldLayout.Dimension destDimension, int dx, int dz, ChunkSelection protectedChunks, int sourceVersion, int destVersion, ChunkCopy.Transform transform
      ) {
         this(sourceWorld, sourceName, sourceDimension, selection, destWorld, destName, destDimension, dx, dz, protectedChunks, sourceVersion, destVersion, transform, 0, 0);
      }

      public ChunkCopy.Request blended(int blend) {
         return new ChunkCopy.Request(
            this.sourceWorld, this.sourceName, this.sourceDimension, this.selection, this.destWorld, this.destName, this.destDimension, this.dx, this.dz,
            this.protectedChunks, this.sourceVersion, this.destVersion, this.transform, blend, this.dy, this.limit
         );
      }

      public ChunkCopy.Request lifted(int dy) {
         return new ChunkCopy.Request(
            this.sourceWorld, this.sourceName, this.sourceDimension, this.selection, this.destWorld, this.destName, this.destDimension, this.dx, this.dz,
            this.protectedChunks, this.sourceVersion, this.destVersion, this.transform, this.blend, dy, this.limit
         );
      }

      public Request(
         Path sourceWorld, String sourceName, WorldLayout.Dimension sourceDimension, ChunkSelection selection, Path destWorld, String destName,
         WorldLayout.Dimension destDimension, int dx, int dz, ChunkSelection protectedChunks, int sourceVersion, int destVersion
      ) {
         this(sourceWorld, sourceName, sourceDimension, selection, destWorld, destName, destDimension, dx, dz, protectedChunks, sourceVersion, destVersion, null, 0, 0);
      }
   }

   public record Details(
      Path sourceWorld,
      String sourceName,
      String destName,
      String sourceDimension,
      String destDimension,
      int dx,
      int dz,
      int selected,
      int replaced,
      int protectedSkipped,
      int tooBig,
      int sourceVersion,
      int destVersion,
      Message refused,
      Map<Path, ChunkCopy.Incoming> incoming,
      Map<Path, long[]> sources,
      ChunkCopy.Transform transform,

      int blendRing,
      int blend,

      boolean fields,

      int dy,

      int blendBand
   ) {
      public boolean moved() {
         return this.dx != 0 || this.dz != 0;
      }

      public boolean lifted() {
         return this.dy != 0;
      }

      public boolean olderSource() {
         return this.sourceVersion > 0 && this.destVersion > 0 && this.sourceVersion < this.destVersion;
      }

      public boolean otherDimension() {
         return !this.sourceDimension.equals(this.destDimension);
      }
   }

   record Incoming(boolean created, int[] written, Path[] from, int[] fromIndex, boolean[] marked) {
   }

   record Payload(byte[] sectors, int timestamp) {
   }

   public static ChunkEdits.Plan plan(ChunkCopy.Request r) {
      boolean fields = r.transform() != null;
      if (r.dy() != 0) {
         ChunkCopy.Transform after = r.transform();
         ChunkCopy.Transform lift = ChunkLift.transform(r.dy(), r.sourceDimension().id());
         r = new ChunkCopy.Request(
            r.sourceWorld(), r.sourceName(), r.sourceDimension(), r.selection(), r.destWorld(), r.destName(), r.destDimension(), r.dx(), r.dz(),
            r.protectedChunks(), r.sourceVersion(), r.destVersion(), after == null ? lift : (role, root) -> {
               lift.apply(role, root);
               after.apply(role, root);
            }, r.blend(), r.dy(), r.limit()
         );
      }

      if (r.blend() > 0) {
         ChunkCopy.Transform before = r.transform();
         ChunkCopy.Transform mark = (role, root) -> {
            if (before != null) {
               before.apply(role, root);
            }

            if (role.equals("region")) {
               ChunkFields.markForBlending(root);
            }
         };
         r = new ChunkCopy.Request(
            r.sourceWorld(), r.sourceName(), r.sourceDimension(), r.selection(), r.destWorld(), r.destName(), r.destDimension(), r.dx(), r.dz(),
            r.protectedChunks(), r.sourceVersion(), r.destVersion(), mark, r.blend(), r.dy(), r.limit()
         );
      }

      WorldLayout.Dimension sd = r.sourceDimension();
      WorldLayout.Dimension dd = r.destDimension();
      try {
         if (r.sourceWorld().toRealPath().equals(r.destWorld().toRealPath())) {
            return refused(r, Message.of("chunkatlas.copy.refuse.same_world"));
         }
      } catch (IOException e) {
         return refused(r, Message.of("chunkatlas.copy.refuse.missing"));
      }

      if (r.sourceVersion() > r.destVersion()) {
         return refused(
            r,
            Message.of("chunkatlas.copy.refuse.newer", r.sourceName(), GameVersions.describe(r.sourceVersion()), r.destName(), GameVersions.describe(r.destVersion()))
         );
      }

      if (Math.abs(r.dx()) > MAX_OFFSET || Math.abs(r.dz()) > MAX_OFFSET || Math.abs(r.dy()) > ChunkLift.MAX_DY) {
         return refused(r, Message.of("chunkatlas.copy.refuse.too_far"));
      }

      if (r.dy() != 0 && r.sourceVersion() > 0 && r.sourceVersion() < ChunkLift.SINCE) {
         return refused(r, Message.of("chunkatlas.copy.refuse.lift_old"));
      }

      Path[] srcFolders = new Path[]{sd.region(), sd.entities().orElse(null), sd.poi().orElse(null)};
      Path[] dstFolders = new Path[]{
         dd.region(), dd.entities().orElse(dd.region().resolveSibling("entities")), dd.poi().orElse(dd.region().resolveSibling("poi"))
      };
      List<Map<Long, WorldLayout.RegionInfo>> srcListed = new ArrayList<>();
      List<Map<Long, WorldLayout.RegionInfo>> dstListed = new ArrayList<>();
      List<Map<Long, ChunkEdits.External>> dstExternals = new ArrayList<>();
      for (int role = 0; role < 3; role++) {
         srcListed.add(listed(srcFolders[role]));
         dstListed.add(listed(dstFolders[role]));
         dstExternals.add(Safe.plainDir(dstFolders[role]) ? ChunkEdits.externals(dstFolders[role]) : Map.of());
      }

      Map<Path, ChunkCopy.Src> srcFiles = new HashMap<>();
      Map<Path, ChunkCopy.Dst> dstFiles = new LinkedHashMap<>();
      Set<Long> unreadable = new HashSet<>();
      Set<Long> away = new HashSet<>();
      int selected = 0;
      int chunks = 0;
      int replaced = 0;
      int guarded = 0;
      int big = 0;
      int ring = 0;
      int band = 0;
      ChunkSelection placed = new ChunkSelection();

      try {
         for (long region : new TreeSet<>(r.selection().regions())) {
            int srx = (int)(region >> 32);
            int srz = (int)region;
            long[] bits = r.selection().bits(srx, srz);
            ChunkCopy.Src[] src = new ChunkCopy.Src[3];
            boolean usable = true;
            for (int role = 0; role < 3; role++) {
               WorldLayout.RegionInfo info = srcListed.get(role).get(region);
               src[role] = info == null ? null : srcFiles.computeIfAbsent(info.file(), f -> new ChunkCopy.Src(info));
               if (src[role] != null && src[role].away) {
                  away.add(key(0, region));
                  usable = false;
               } else if (src[role] != null && src[role].bad) {
                  unreadable.add(key(0, region));
                  usable = false;
               }
            }

            for (int i = 0; i < 1024; i++) {
               if ((bits[i >> 6] & 1L << (i & 63)) == 0L) {
                  continue;
               }

               selected++;
               if (!usable || src[0] == null || !src[0].header.present(i)) {
                  continue;
               }

               boolean tooBig = false;
               boolean broken = false;
               int[] from = new int[]{-1, -1, -1};
               for (int role = 0; role < 3; role++) {
                  if (src[role] != null && src[role].header.present(i)) {
                     int kind = src[role].kind(i);
                     if (kind < 0) {
                        broken = true;
                     } else if ((kind & 128) != 0) {
                        tooBig = true;
                     } else {
                        from[role] = i;
                     }
                  }
               }

               if (broken) {
                  unreadable.add(key(0, region));
                  continue;
               }

               if (tooBig) {
                  big++;
                  continue;
               }

               int cx = (srx << 5) + (i & 31);
               int cz = (srz << 5) + (i >> 5);
               int tx = cx + r.dx();
               int tz = cz + r.dz();
               if (r.protectedChunks().contains(tx, tz)) {
                  guarded++;
                  continue;
               }

               int trx = tx >> 5;
               int trz = tz >> 5;
               long target = WorldLayout.key(trx, trz);
               int j = ChunkSelection.index(tx, tz);
               ChunkCopy.Dst[] dst = new ChunkCopy.Dst[3];
               boolean ok = true;
               for (int role = 0; role < 3 && ok; role++) {
                  WorldLayout.RegionInfo info = dstListed.get(role).get(target);
                  boolean writes = from[role] >= 0;
                  Path file = info != null ? info.file() : dstFolders[role].resolve(RegionFile.name(trx, trz));
                  ChunkCopy.Dst d = dstFiles.get(file);
                  if (d == null && (info != null || writes)) {
                     d = new ChunkCopy.Dst(file, ROLES.get(role), trx, trz, info, dstExternals.get(role));
                     dstFiles.put(file, d);
                  }

                  if (d != null && d.away) {
                     away.add(key(1, target));
                     ok = false;
                  } else if (d != null && d.bad) {
                     unreadable.add(key(1, target));
                     ok = false;
                  }

                  dst[role] = d;
               }

               if (!ok) {
                  continue;
               }

               for (int role = 0; role < 3; role++) {
                  ChunkCopy.Dst d = dst[role];
                  if (from[role] >= 0) {
                     d.writes.put(j, new ChunkCopy.Ref(src[role].file, from[role], src[role].header.sectors(from[role]), false));
                  } else if (d != null && d.header != null && d.header.present(j)) {
                     d.clears.add(j);
                  }
               }

               if (dst[0].header != null && dst[0].header.present(j)) {
                  replaced++;
               }

               placed.add(tx, tz);
               chunks++;
            }
         }

         if (r.blend() > 0 && chunks > 0) {
            ChunkSelection around = r.limit() != null ? r.limit().ring(placed, r.blend()) : BlendRing.ring(placed, r.blend(), r.protectedChunks(), null);
            for (long region : around.regions()) {
               int trx = (int)(region >> 32);
               int trz = (int)region;
               long[] bits = around.bits(trx, trz);
               ChunkCopy.Dst[] dst = new ChunkCopy.Dst[3];
               boolean ok = true;
               for (int role = 0; role < 3 && ok; role++) {
                  WorldLayout.RegionInfo info = dstListed.get(role).get(region);
                  if (info == null) {
                     continue;
                  }

                  ChunkCopy.Dst d = dstFiles.get(info.file());
                  if (d == null) {
                     d = new ChunkCopy.Dst(info.file(), ROLES.get(role), trx, trz, info, dstExternals.get(role));
                     dstFiles.put(info.file(), d);
                  }

                  if (d.away || d.bad) {
                     ok = role > 0;
                     continue;
                  }

                  dst[role] = d;
               }

               if (!ok || dst[0] == null || dst[0].header == null) {
                  continue;
               }

               for (int i = 0; i < 1024; i++) {
                  if ((bits[i >> 6] & 1L << (i & 63)) == 0L || !dst[0].header.present(i)) {
                     continue;
                  }

                  for (ChunkCopy.Dst d : dst) {
                     if (d != null && d.header != null && d.header.present(i)) {
                        d.clears.add(i);
                     }
                  }

                  ring++;
               }
            }

            ChunkSelection outside = r.limit() != null ? r.limit().band(placed, r.blend()) : BlendRing.band(placed, r.blend(), r.protectedChunks(), null);
            for (long region : outside.regions()) {
               WorldLayout.RegionInfo info = dstListed.get(0).get(region);
               if (info == null || info.away()) {
                  continue;
               }

               ChunkCopy.Dst d = dstFiles.get(info.file());
               if (d == null) {
                  d = new ChunkCopy.Dst(info.file(), ROLES.get(0), (int)(region >> 32), (int)region, info, dstExternals.get(0));
                  dstFiles.put(info.file(), d);
               }

               ChunkCopy.Src self = srcFiles.computeIfAbsent(info.file(), f -> new ChunkCopy.Src(info));
               if (d.away || d.bad || d.header == null || self.away || self.bad || self.header == null) {
                  continue;
               }

               long[] bits = outside.bits(d.rx, d.rz);
               for (int i = 0; i < 1024; i++) {
                  if ((bits[i >> 6] & 1L << (i & 63)) == 0L || !d.header.present(i) || d.writes.containsKey(i) || d.clears.contains(i)) {
                     continue;
                  }

                  int kind = self.kind(i);
                  if (kind < 0 || (kind & 128) != 0) {
                     continue;
                  }

                  d.writes.put(i, new ChunkCopy.Ref(self.file, i, d.header.sectors(i), true));
                  band++;
               }
            }
         }
      } finally {
         for (ChunkCopy.Src s : srcFiles.values()) {
            s.close();
         }
      }

      List<ChunkEdits.FilePlan> files = new ArrayList<>();
      Map<Path, ChunkCopy.Incoming> incoming = new HashMap<>();
      Map<Path, long[]> sources = new HashMap<>();
      int regionFiles = 0;
      int otherFiles = 0;
      long before = 0L;
      long after = 0L;
      long externalBytes = 0L;
      for (ChunkCopy.Dst d : dstFiles.values()) {
         if (d.writes.isEmpty() && d.clears.isEmpty()) {
            continue;
         }

         TreeSet<Integer> dropped = new TreeSet<>();
         int survivors = 0;
         long sectors = 0L;
         List<Path> external = new ArrayList<>();
         if (d.header != null) {
            for (int i = 0; i < 1024; i++) {
               if (d.header.present(i)) {
                  if (d.writes.containsKey(i) || d.clears.contains(i)) {
                     dropped.add(i);
                     ChunkEdits.External x = d.externals.get(WorldLayout.key((d.rx << 5) + (i & 31), (d.rz << 5) + (i >> 5)));
                     if (x != null) {
                        external.add(x.file());
                        externalBytes += x.size();
                     }
                  } else {
                     survivors++;
                     sectors += d.header.sectors(i);
                  }
               }
            }
         }

         int[] written = new int[d.writes.size()];
         Path[] fromFile = new Path[written.length];
         int[] fromIndex = new int[written.length];
         boolean[] marked = new boolean[written.length];
         int n = 0;
         for (Map.Entry<Integer, ChunkCopy.Ref> w : d.writes.entrySet()) {
            written[n] = w.getKey();
            fromFile[n] = w.getValue().file();
            fromIndex[n] = w.getValue().index();
            marked[n] = w.getValue().mark();
            sectors += w.getValue().sectors();
            ChunkCopy.Src s = srcFiles.get(w.getValue().file());
            sources.put(s.file, new long[]{s.size, s.modified});
            n++;
         }

         survivors += written.length;
         if (survivors == 0 && d.header == null) {
            continue;
         }

         int[] indices = dropped.stream().mapToInt(Integer::intValue).toArray();
         long size = d.header == null ? 0L : d.header.fileSize;
         long newSize = 8192L + sectors * 4096L;
         files.add(new ChunkEdits.FilePlan(d.file, d.role, d.rx, d.rz, indices, survivors, size, d.header == null ? 0L : d.header.modified, newSize, List.copyOf(external)));
         incoming.put(d.file, new ChunkCopy.Incoming(d.header == null, written, fromFile, fromIndex, marked));
         before += size;
         after += newSize;
         if (d.role.equals("region")) {
            regionFiles++;
         } else {
            otherFiles++;
         }
      }

      ChunkCopy.Details details = details(r, selected, replaced, guarded, big, null, Map.copyOf(incoming), Map.copyOf(sources), ring, band, fields);
      return new ChunkEdits.Plan(
         r.destWorld(),
         dd.id(),
         dd.label(),
         ChunkEdits.Kind.COPY,
         chunks,
         0,
         regionFiles,
         otherFiles,
         before,
         after,
         externalBytes,
         unreadable.size(),
         away.size(),
         chunks == 0 ? List.of() : List.copyOf(files),
         false,
         details
      );
   }

   private static long key(int side, long region) {
      return region * 2L + side;
   }

   private static ChunkCopy.Details details(
      ChunkCopy.Request r, int selected, int replaced, int guarded, int big, Message refused, Map<Path, ChunkCopy.Incoming> incoming, Map<Path, long[]> sources,
      int ring, int band, boolean fields
   ) {
      return new ChunkCopy.Details(
         r.sourceWorld(),
         r.sourceName(),
         r.destName(),
         r.sourceDimension().id(),
         r.destDimension().id(),
         r.dx(),
         r.dz(),
         selected,
         replaced,
         guarded,
         big,
         r.sourceVersion(),
         r.destVersion(),
         refused,
         incoming,
         sources,
         r.transform(),
         ring,
         r.blend(),
         fields,
         r.dy(),
         band
      );
   }

   private static ChunkEdits.Plan refused(ChunkCopy.Request r, Message why) {
      WorldLayout.Dimension dd = r.destDimension();
      return new ChunkEdits.Plan(
         r.destWorld(), dd.id(), dd.label(), ChunkEdits.Kind.COPY, 0, 0, 0, 0, 0L, 0L, 0L, 0, 0, List.of(), false,
         details(r, r.selection().size(), 0, 0, 0, why, Map.of(), Map.of(), 0, 0, r.transform() != null)
      );
   }

   private static Map<Long, WorldLayout.RegionInfo> listed(Path folder) {
      Map<Long, WorldLayout.RegionInfo> out = new HashMap<>();
      if (folder != null) {
         for (WorldLayout.RegionInfo info : WorldLayout.list(folder)) {
            out.put(WorldLayout.key(info.rx(), info.rz()), info);
         }
      }

      return out;
   }

   static Message sourceChanged(ChunkCopy.Details d) {
      for (Map.Entry<Path, long[]> e : d.sources().entrySet()) {
         if (!same(e.getKey(), e.getValue())) {
            return Message.of("chunkatlas.copy.source_changed", String.valueOf(e.getKey().getFileName()));
         }
      }

      return null;
   }

   private static boolean same(Path file, long[] stat) {
      try {
         BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
         return a.isRegularFile() && !Safe.isLink(a) && a.size() == stat[0] && a.lastModifiedTime().toMillis() == stat[1];
      } catch (IOException e) {
         return false;
      }
   }

   static ChunkCopy.Payload payload(ChunkCopy.Details d, String role, Path file, int index, boolean mark) throws IOException {
      long[] stat = d.sources().get(file);
      if (stat == null || !same(file, stat)) {
         throw new ChunkEdits.Problem("chunkatlas.copy.source_changed", file.getFileName());
      }

      RegionFile.Header header;
      byte[] raw;
      try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
         header = new RegionFile.Header(RegionFile.rawHeader(ch), stat[0], stat[1]);
         if (!header.present(index)) {
            throw new ChunkEdits.Problem("chunkatlas.copy.source_changed", file.getFileName());
         }

         raw = read(ch, header.sector(index), header.sectors(index));
      }

      int length = ByteBuffer.wrap(raw).getInt(0);
      int kind = raw[4] & 0xFF;
      if (length < 1 || length > raw.length - 4 || (kind & 128) != 0) {
         throw new ChunkEdits.Problem("chunkatlas.copy.source_unreadable", file.getFileName());
      }

      int timestamp = header.timestamps[index];
      if (!mark && !d.moved() && d.transform() == null) {
         return new ChunkCopy.Payload(raw, timestamp);
      }

      RegionFile.Chunk chunk;
      try (RegionFile region = RegionFile.open(file)) {
         chunk = region.chunk(index);
      }

      if (!chunk.ok()) {
         throw new ChunkEdits.Problem("chunkatlas.copy.source_unreadable", file.getFileName());
      }

      Map<String, Object> root = Nbt.read(chunk.data());
      if (mark) {
         if (role.equals("region") && String.valueOf(root.get("Status")).endsWith("full")) {
            ChunkFields.markForBlending(root);
         }
      } else if (d.moved()) {
         move(root, role, d.dx(), d.dz(), d.sourceDimension());
      }

      if (!mark && d.transform() != null) {
         d.transform().apply(role, root);
      }

      byte[] packed = deflate(Nbt.write(root));
      int sectors = (packed.length + 5 + 4095) / 4096;
      if (sectors > MAX_SECTORS) {
         throw new ChunkEdits.Problem("chunkatlas.copy.too_big_moved", file.getFileName());
      }

      ByteBuffer out = ByteBuffer.allocate(sectors * 4096);
      out.putInt(packed.length + 1);
      out.put((byte)2);
      out.put(packed);
      return new ChunkCopy.Payload(out.array(), timestamp);
   }

   private static byte[] read(FileChannel ch, int sector, int count) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(count * 4096);
      long at = sector * 4096L;
      while (buf.hasRemaining()) {
         if (ch.read(buf, at + buf.position()) < 0) {
            break;
         }
      }

      return buf.array();
   }

   private static byte[] deflate(byte[] data) {
      Deflater def = new Deflater();
      try {
         def.setInput(data);
         def.finish();
         ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, data.length / 4));
         byte[] buf = new byte[65536];
         while (!def.finished()) {
            int n = def.deflate(buf);
            out.write(buf, 0, n);
         }

         return out.toByteArray();
      } finally {
         def.end();
      }
   }

   private static final Set<String> INT_TRIPLES = Set.of(
      "block_pos", "sleeping_pos", "home_pos", "hive_pos", "flower_pos", "exit_portal", "beam_target", "patrol_target", "wander_target", "leash", "bound_pos", "anchor_pos"
   );
   private static final Set<String> XYZ_COMPOUNDS = Set.of("HivePos", "FlowerPos", "ExitPortal", "Leash", "BeamTarget", "PatrolTarget", "WanderTarget");

   private static final String[][] INT_SPLIT = new String[][]{
      {"TileX", "TileY", "TileZ"}, {"HomePosX", "HomePosY", "HomePosZ"}, {"TravelPosX", "TravelPosY", "TravelPosZ"}, {"BoundX", "BoundY", "BoundZ"},
      {"AX", "AY", "AZ"}, {"SleepingX", "SleepingY", "SleepingZ"}
   };

   public static void move(Map<String, Object> root, String role, int dx, int dz, String dimension) {
      switch (role) {
         case "region" -> {
            Map<String, Object> c = Nbt.map(root, "Level") != null ? Nbt.map(root, "Level") : root;
            addInt(c, "xPos", dx);
            addInt(c, "zPos", dz);

            if (c.containsKey("structures")) {
               if (keepStructures) {
                  moveStructures(Nbt.map(c, "structures"), "starts", dx, dz);
               } else {
                  c.put("structures", emptyStructures("starts"));
               }
            }

            if (c.containsKey("Structures")) {
               if (keepStructures) {
                  moveStructures(Nbt.map(c, "Structures"), "Starts", dx, dz);
               } else {
                  c.put("Structures", emptyStructures("Starts"));
               }
            }
         }
         case "entities" -> {
            if (root.get("Position") instanceof int[] p && p.length == 2) {
               root.put("Position", new int[]{p[0] + dx, p[1] + dz});
            }
         }
         default -> {
         }
      }

      shift(root, role, dx * 16, 0, dz * 16, dimension);
   }

   static void shift(Map<String, Object> root, String role, int bx, int by, int bz, String dimension) {
      switch (role) {
         case "region" -> {
            Map<String, Object> c = Nbt.map(root, "Level") != null ? Nbt.map(root, "Level") : root;
            for (String list : new String[]{"block_entities", "TileEntities"}) {
               for (Object b : listOf(c, list)) {
                  if (b instanceof Map<?, ?> m) {
                     blockEntity(cast(m), bx, by, bz, dimension);
                  }
               }
            }

            for (String list : new String[]{"block_ticks", "fluid_ticks", "TileTicks", "LiquidTicks"}) {
               for (Object t : listOf(c, list)) {
                  if (t instanceof Map<?, ?> m) {
                     addInt(cast(m), "x", bx);
                     addInt(cast(m), "y", by);
                     addInt(cast(m), "z", bz);
                  }
               }
            }

            for (Object e : listOf(c, "Entities")) {
               if (e instanceof Map<?, ?> m) {
                  entity(cast(m), bx, by, bz, dimension);
               }
            }
         }
         case "entities" -> {
            for (Object e : listOf(root, "Entities")) {
               if (e instanceof Map<?, ?> m) {
                  entity(cast(m), bx, by, bz, dimension);
               }
            }
         }
         case "poi" -> {
            Map<String, Object> sections = Nbt.map(root, "Sections");
            if (sections != null) {
               for (Object s : sections.values()) {
                  for (Object rec : listOf(s, "Records")) {
                     if (rec instanceof Map<?, ?> m) {
                        triple(cast(m), "pos", bx, by, bz);
                     }
                  }
               }
            }
         }
         default -> {
         }
      }
   }

   static void moveStructures(Map<String, Object> structures, String startsKey, int dx, int dz) {
      if (structures == null || dx == 0 && dz == 0) {
         return;
      }

      if (Nbt.map(structures, startsKey) instanceof Map<String, Object> starts) {
         for (Object start : starts.values()) {
            if (start instanceof Map<?, ?> m) {
               Map<String, Object> st = cast(m);
               addInt(st, "ChunkX", dx);
               addInt(st, "ChunkZ", dz);
               movePieces(st, dx * 16, dz * 16, dx, dz);
            }
         }
      }

      if (Nbt.map(structures, "References") instanceof Map<String, Object> refs) {
         for (Map.Entry<String, Object> e : refs.entrySet()) {
            if (e.getValue() instanceof long[] packed) {
               long[] moved = new long[packed.length];
               for (int i = 0; i < packed.length; i++) {
                  int x = (int)packed[i];
                  int z = (int)(packed[i] >> 32);
                  moved[i] = (x + dx) & 0xFFFFFFFFL | ((long)(z + dz) & 0xFFFFFFFFL) << 32;
               }

               e.setValue(moved);
            }
         }
      }
   }

   private static final String[][] PIECE_XZ = {{"PosX", "PosZ"}, {"pos_x", "pos_z"}, {"TPX", "TPZ"}, {"source_x", "source_z"}};

   private static void movePieces(Map<String, Object> m, int bx, int bz, int dx, int dz) {
      for (Map.Entry<String, Object> e : m.entrySet()) {
         String key = e.getKey();
         Object v = e.getValue();
         if (v instanceof int[] box && box.length == 6 && (key.equals("BB") || key.equals("bb"))) {
            e.setValue(new int[]{box[0] + bx, box[1], box[2] + bz, box[3] + bx, box[4], box[5] + bz});
         } else if (v instanceof Map<?, ?> child) {
            movePieces(cast(child), bx, bz, dx, dz);
         } else if (v instanceof List<?> list) {
            boolean chunks = key.equals("Processed");
            for (int i = 0; i < list.size(); i++) {
               Object item = list.get(i);
               if (item instanceof int[] box && box.length == 6 && key.equals("Entrances")) {
                  cast(list).set(i, new int[]{box[0] + bx, box[1], box[2] + bz, box[3] + bx, box[4], box[5] + bz});
               } else if (item instanceof Map<?, ?> child && chunks) {
                  addInt(cast(child), "X", dx);
                  addInt(cast(child), "Z", dz);
               } else if (item instanceof Map<?, ?> child) {
                  movePieces(cast(child), bx, bz, dx, dz);
               }
            }
         }
      }

      for (String[] xz : PIECE_XZ) {
         addInt(m, xz[0], bx);
         addInt(m, xz[1], bz);
      }
   }

   @SuppressWarnings("unchecked")
   private static List<Object> cast(List<?> l) {
      return (List<Object>)l;
   }

   static Map<String, Object> emptyStructures(String starts) {
      Map<String, Object> s = new LinkedHashMap<>();
      s.put("References", new LinkedHashMap<String, Object>());
      s.put(starts, new LinkedHashMap<String, Object>());
      return s;
   }

   private static void blockEntity(Map<String, Object> b, int bx, int by, int bz, String dimension) {
      addInt(b, "x", bx);
      addInt(b, "y", by);
      addInt(b, "z", bz);
      positions(b, bx, by, bz, dimension);
      for (String list : new String[]{"bees", "Bees"}) {
         for (Object bee : listOf(b, list)) {
            for (String data : new String[]{"entity_data", "EntityData"}) {
               Map<String, Object> e = Nbt.map(bee, data);
               if (e != null) {
                  entity(e, bx, by, bz, dimension);
               }
            }
         }
      }
   }

   private static void entity(Map<String, Object> e, int bx, int by, int bz, String dimension) {
      if (e.get("Pos") instanceof List<?> pos && pos.size() == 3 && pos.get(0) instanceof Double x && pos.get(1) instanceof Double y && pos.get(2) instanceof Double z) {
         List<Object> moved = new ArrayList<>(pos);
         moved.set(0, x + bx);
         moved.set(1, y + by);
         moved.set(2, z + bz);
         e.put("Pos", moved);
      }

      positions(e, bx, by, bz, dimension);
      Map<String, Object> memories = Nbt.map(Nbt.map(e, "Brain"), "memories");
      if (memories != null) {
         for (Object memory : memories.values()) {
            Map<String, Object> value = Nbt.map(memory, "value");
            if (value != null && dimension.equals(Nbt.string(value, "dimension"))) {
               triple(value, "pos", bx, by, bz);
            }
         }
      }

      for (Object rider : listOf(e, "Passengers")) {
         if (rider instanceof Map<?, ?> m) {
            entity(cast(m), bx, by, bz, dimension);
         }
      }
   }

   private static void positions(Map<String, Object> m, int bx, int by, int bz, String dimension) {
      for (String key : INT_TRIPLES) {
         triple(m, key, bx, by, bz);
      }

      for (String key : XYZ_COMPOUNDS) {
         Map<String, Object> p = Nbt.map(m, key);
         if (p != null) {
            addInt(p, "X", bx);
            addInt(p, "Y", by);
            addInt(p, "Z", bz);
         }
      }

      for (String[] split : INT_SPLIT) {
         if (m.get(split[0]) instanceof Integer && m.get(split[2]) instanceof Integer) {
            addInt(m, split[0], bx);
            addInt(m, split[1], by);
            addInt(m, split[2], bz);
         }
      }
   }

   private static void triple(Map<String, Object> m, String key, int bx, int by, int bz) {
      if (m.get(key) instanceof int[] p && p.length == 3) {
         m.put(key, new int[]{p[0] + bx, p[1] + by, p[2] + bz});
      }
   }

   private static void addInt(Map<String, Object> m, String key, int by) {
      if (by != 0 && m.get(key) instanceof Integer v) {
         m.put(key, v + by);
      }
   }

   private static List<Object> listOf(Object parent, String key) {
      List<Object> l = Nbt.list(parent, key);
      return l == null ? List.of() : l;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> cast(Map<?, ?> m) {
      return (Map<String, Object>)m;
   }

   private record Ref(Path file, int index, int sectors, boolean mark) {
   }

   private static final class Src {
      final Path file;
      final long size;
      final long modified;
      final boolean away;
      boolean bad;
      RegionFile.Header header;
      private FileChannel channel;

      Src(WorldLayout.RegionInfo info) {
         this.file = info.file();
         this.size = info.size();
         this.modified = info.modified();
         this.away = info.away();
         if (!this.away) {
            try {
               if (info.size() < 8192L) {
                  this.header = new RegionFile.Header(new byte[8192], 0L, info.modified());
               } else {
                  this.header = RegionFile.header(info.file());
                  if (this.header.fileSize != info.size() || this.header.modified != info.modified()) {
                     this.bad = true;
                  }
               }
            } catch (IOException | RuntimeException e) {
               this.bad = true;
            }
         }
      }

      int kind(int i) {
         try {
            if (this.channel == null) {
               this.channel = FileChannel.open(this.file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            }

            ByteBuffer head = ByteBuffer.allocate(5);
            long at = this.header.sector(i) * 4096L;
            while (head.hasRemaining()) {
               if (this.channel.read(head, at + head.position()) < 0) {
                  return -1;
               }
            }

            int length = head.getInt(0);
            return length >= 1 && length <= this.header.sectors(i) * 4096 - 4 ? head.get(4) & 0xFF : -1;
         } catch (IOException | RuntimeException e) {
            return -1;
         }
      }

      void close() {
         try {
            if (this.channel != null) {
               this.channel.close();
            }
         } catch (IOException ignored) {
         }
      }
   }

   private static final class Dst {
      final Path file;
      final String role;
      final int rx;
      final int rz;
      final Map<Long, ChunkEdits.External> externals;
      final TreeMap<Integer, ChunkCopy.Ref> writes = new TreeMap<>();
      final TreeSet<Integer> clears = new TreeSet<>();
      boolean away;
      boolean bad;
      RegionFile.Header header;

      Dst(Path file, String role, int rx, int rz, WorldLayout.RegionInfo info, Map<Long, ChunkEdits.External> externals) {
         this.file = file;
         this.role = role;
         this.rx = rx;
         this.rz = rz;
         this.externals = externals;
         if (info != null) {
            this.away = info.away();
            if (!this.away && info.size() > 0L) {
               try {
                  this.header = RegionFile.header(file);
                  if (info.size() < 8192L || this.header.fileSize != info.size() || this.header.modified != info.modified()) {
                     this.bad = true;
                  }
               } catch (IOException | RuntimeException e) {
                  this.bad = true;
               }
            }
         }
      }
   }
}
