package dev.chunkatlas.engine;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChunkEdits {
   public static final int REAL_LOCK_SINCE = 2566;
   public static final int KEEP_DAYS = 30;
   static final String JOURNALED = "journaled";
   static final String APPLIED = "applied";
   static final String UNDOING = "undoing";
   static final String UNDONE = "undone";
   static final String ROLLED_BACK = "rolled_back";
   static final String INTERRUPTED = "interrupted";
   private static final Set<String> STATES = Set.of("journaled", "applied", "undoing", "undone", "rolled_back", "interrupted");
   private static final List<String> ROLES = List.of("region", "entities", "poi");
   private static final Object LOCK = new Object();
   private static final Pattern EXTERNAL = Pattern.compile("c\\.(-?\\d{1,9})\\.(-?\\d{1,9})\\.mcc");
   private static final Pattern ID = Pattern.compile("\\d{8}-\\d{6}-[0-9a-f]{4}");
   private static final String MANIFEST = "edit.txt";
   private static final String FIRST_LINE = "edit 1";
   private static final long MARGIN = 67108864L;
   private static final Message NOT_LOCKED = Message.of("chunkatlas.edit.not_locked");

   private ChunkEdits() {
   }

   public static Path backups(Path home, Path worldReal) {
      return home.resolve("backups").resolve(sha(worldReal.toString()).substring(0, 20));
   }

   public record Backup(String worldKey, String world, String id, ChunkEdits.Kind kind, String dimension, int chunks, long createdMillis, long bytes) {
   }

   private static final Pattern WORLD_KEY = Pattern.compile("[0-9a-f]{20}");

   public static List<ChunkEdits.Backup> listBackups(Path home) {
      List<ChunkEdits.Backup> out = new ArrayList<>();
      Path root = home == null ? null : home.resolve("backups");
      if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
         return out;
      }

      try (DirectoryStream<Path> worlds = Files.newDirectoryStream(root)) {
         for (Path worldDir : worlds) {
            String key = worldDir.getFileName().toString();
            if (!WORLD_KEY.matcher(key).matches() || !Files.isDirectory(worldDir, LinkOption.NOFOLLOW_LINKS)) {
               continue;
            }

            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(worldDir)) {
               for (Path dir : dirs) {
                  ChunkEdits.Backup b = readBackup(key, dir);
                  if (b != null) {
                     out.add(b);
                  }
               }
            }
         }
      } catch (IOException | RuntimeException ignored) {
      }

      out.sort(Comparator.comparingLong(ChunkEdits.Backup::createdMillis).reversed());
      return out;
   }

   private static ChunkEdits.Backup readBackup(String key, Path dir) {
      String id = dir.getFileName().toString();
      Path file = dir.resolve(MANIFEST);
      try {
         if (!ID.matcher(id).matches() || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return null;
         }

         String world = "";
         String dimension = "";
         String kindName = null;
         int chunks = 0;
         long created = 0L;
         String state = "";
         if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 67108864L) {
            ChunkEdits.Manifest m = parse(Files.readString(file, StandardCharsets.UTF_8));
            if (m != null) {
               world = m.world == null ? "" : m.world;
               dimension = m.dimension;
               kindName = m.kind;
               chunks = m.chunks;
               created = m.created;
               state = m.state == null ? "" : m.state;
            }
         }

         if (state.equals(JOURNALED) || state.equals(UNDOING)) {
            return null;
         }

         ChunkEdits.Kind kind = null;
         try {
            kind = kindName == null ? null : ChunkEdits.Kind.valueOf(kindName);
         } catch (IllegalArgumentException ignored) {
         }

         if (created <= 0L) {
            created = Files.getLastModifiedTime(dir).toMillis();
         }

         Path named = world.isEmpty() ? null : Path.of(world).getFileName();
         return new ChunkEdits.Backup(key, named == null ? "" : named.toString(), id, kind, dimension, chunks, created, treeSize(dir));
      } catch (IOException | RuntimeException e) {
         return null;
      }
   }

   private static long treeSize(Path dir) {
      long[] total = new long[1];
      try {
         Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
               total[0] += a.isRegularFile() ? a.size() : 0L;
               return FileVisitResult.CONTINUE;
            }

            public FileVisitResult visitFileFailed(Path file, IOException e) {
               return FileVisitResult.CONTINUE;
            }
         });
      } catch (IOException ignored) {
      }

      return total[0];
   }

   public static boolean deleteBackup(Path home, String worldKey, String id) {
      if (home == null || worldKey == null || id == null || !WORLD_KEY.matcher(worldKey).matches() || !ID.matcher(id).matches()) {
         return false;
      }

      synchronized (LOCK) {
         Path worldDir = home.resolve("backups").resolve(worldKey);
         Path dir = worldDir.resolve(id);
         if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return true;
         }

         if (readBackup(worldKey, dir) == null && Files.exists(dir.resolve(MANIFEST), LinkOption.NOFOLLOW_LINKS)) {
            return false;
         }

         deleteTree(dir);
         try {
            Files.deleteIfExists(worldDir);
         } catch (IOException ignored) {
         }

         return !Files.exists(dir, LinkOption.NOFOLLOW_LINKS);
      }
   }

   public static ChunkEdits.Plan planDelete(Path world, WorldLayout.Dimension dimension, ChunkSelection selection) {
      return plan(world, dimension, selection, ChunkEdits.Kind.DELETE);
   }

   public static ChunkEdits.Plan planKeepOnly(Path world, WorldLayout.Dimension dimension, ChunkSelection selection) {
      return plan(world, dimension, selection, ChunkEdits.Kind.KEEP_ONLY);
   }

   public static ChunkEdits.Plan planDeleteMobs(Path world, WorldLayout.Dimension dimension, ChunkSelection selection) {
      return plan(world, dimension, selection, ChunkEdits.Kind.DELETE_MOBS);
   }

   public static boolean mobsInsideChunks(WorldLayout.Dimension dimension) {
      return dimension.entities().isEmpty();
   }

   private static ChunkEdits.Plan plan(Path world, WorldLayout.Dimension dimension, ChunkSelection selection, ChunkEdits.Kind kind) {
      boolean mobsInside = kind == ChunkEdits.Kind.DELETE_MOBS && mobsInsideChunks(dimension);
      if (selection.isEmpty() || mobsInside) {
         return new ChunkEdits.Plan(world, dimension.id(), dimension.label(), kind, 0, 0, 0, 0, 0L, 0L, 0L, 0, 0, List.of(), mobsInside);
      }

      boolean mobs = kind == ChunkEdits.Kind.DELETE_MOBS;
      boolean clears = kind != ChunkEdits.Kind.KEEP_ONLY;
      int firstRole = mobs ? 1 : 0;
      int lastRole = mobs ? 1 : ROLES.size() - 1;
      Path[] folders = new Path[]{dimension.region(), dimension.entities().orElse(null), dimension.poi().orElse(null)};
      for (int role = 0; role < folders.length; role++) {
         if (role < firstRole || role > lastRole) {
            folders[role] = null;
         }
      }
      List<Map<Long, WorldLayout.RegionInfo>> listed = new ArrayList<>();
      List<Map<Long, ChunkEdits.External>> externals = new ArrayList<>();
      TreeSet<Long> keys = new TreeSet<>();

      for (Path folder : folders) {
         Map<Long, WorldLayout.RegionInfo> files = new HashMap<>();
         if (folder != null) {
            for (WorldLayout.RegionInfo r : WorldLayout.list(folder)) {
               files.put(WorldLayout.key(r.rx(), r.rz()), r);
            }
         }

         keys.addAll(files.keySet());
         listed.add(files);
         externals.add(folder == null ? Map.of() : externals(folder));
      }

      List<ChunkEdits.FilePlan> files = new ArrayList<>();
      int chunks = 0;
      int kept = 0;
      int regionFiles = 0;
      int otherFiles = 0;
      int unreadable = 0;
      int away = 0;
      long before = 0L;
      long after = 0L;
      long externalBytes = 0L;

      for (long key : keys) {
         int rx = (int)(key >> 32);
         int rz = (int)key;
         long[] picked = selection.bits(rx, rz);
         if (picked != null || !clears) {
            List<ChunkEdits.FilePlan> mine = new ArrayList<>();
            boolean isAway = false;
            boolean isBad = false;
            int keptHere = 0;
            int chunksHere = 0;
            long externalHere = 0L;

            for (int role = firstRole; role <= lastRole && !isAway && !isBad; role++) {
               WorldLayout.RegionInfo info = listed.get(role).get(key);
               if (info != null) {
                  if (info.away()) {
                     isAway = true;
                     break;
                  }

                  if (info.size() >= 8192L) {
                     RegionFile.Header header;
                     try {
                        header = RegionFile.header(info.file());
                     } catch (IOException | RuntimeException e) {
                        isBad = true;
                        break;
                     }

                     int[] indices = new int[1024];
                     int n = 0;
                     int survivors = 0;
                     long sectors = 0L;
                     List<Path> set = new ArrayList<>();

                     for (int i = 0; i < 1024; i++) {
                        if (header.present(i)) {
                           boolean selected = picked != null && (picked[i >> 6] & 1L << (i & 63)) != 0L;
                           if (selected != clears) {
                              survivors++;
                              sectors += header.sectors(i);
                           } else {
                              indices[n++] = i;
                              ChunkEdits.External x = externals.get(role).get(WorldLayout.key((rx << 5) + (i & 31), (rz << 5) + (i >> 5)));
                              if (x != null) {
                                 isAway |= x.away();
                                 set.add(x.file());
                                 externalHere += x.size();
                              }
                           }
                        }
                     }

                     if (role == firstRole) {
                        keptHere = survivors;
                        chunksHere = n;
                     }

                     if (n > 0) {
                        mine.add(
                           new ChunkEdits.FilePlan(
                              info.file(),
                              ROLES.get(role),
                              rx,
                              rz,
                              Arrays.copyOf(indices, n),
                              survivors,
                              header.fileSize,
                              header.modified,
                              8192L + sectors * 4096L,
                              List.copyOf(set)
                           )
                        );
                     }
                  }
               }
            }

            if (isAway) {
               away++;
            } else if (isBad) {
               unreadable++;
            } else {
               kept += keptHere;
               chunks += chunksHere;
               externalBytes += externalHere;

               for (ChunkEdits.FilePlan f : mine) {
                  files.add(f);
                  before += f.size();
                  after += f.newSize();
                  if (f.role().equals("region")) {
                     regionFiles++;
                  } else {
                     otherFiles++;
                  }
               }
            }
         }
      }

      return kind == ChunkEdits.Kind.KEEP_ONLY && kept == 0
         ? new ChunkEdits.Plan(world, dimension.id(), dimension.label(), kind, 0, 0, 0, 0, 0L, 0L, 0L, unreadable, away, List.of(), false)
         : new ChunkEdits.Plan(
            world,
            dimension.id(),
            dimension.label(),
            kind,
            chunks,
            kept,
            regionFiles,
            otherFiles,
            before,
            after,
            externalBytes,
            unreadable,
            away,
            List.copyOf(files),
            false
         );
   }

   static Map<Long, ChunkEdits.External> externals(Path dir) {
      Map<Long, ChunkEdits.External> out = new HashMap<>();
      List<Long> keys = new ArrayList<>();
      List<Path> paths = new ArrayList<>();
      List<Long> sizes = new ArrayList<>();

      try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "c.*.mcc")) {
         for (Path f : files) {
            Matcher m = EXTERNAL.matcher(f.getFileName().toString());
            if (m.matches()) {
               try {
                  BasicFileAttributes a = Files.readAttributes(f, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                  if (a.isRegularFile() && !Safe.isLink(a)) {
                     keys.add(WorldLayout.key(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
                     paths.add(f);
                     sizes.add(a.size());
                  }
               } catch (IOException ignored) {
               }
            }
         }
      } catch (IOException | RuntimeException ignored) {
      }

      boolean[] away = Safe.away(paths);

      for (int i = 0; i < paths.size(); i++) {
         out.put(keys.get(i), new ChunkEdits.External(paths.get(i), sizes.get(i), away[i]));
      }

      return out;
   }

   public static ChunkEdits.Result apply(ChunkEdits.Plan plan, ChunkEdits.Context ctx) {
      return apply(plan, ctx, null);
   }

   public static ChunkEdits.Result apply(ChunkEdits.Plan plan, ChunkEdits.Context ctx, ChunkEdits.Progress progress) {
      return apply(plan, ctx, progress, point -> {}, true);
   }

   public static ChunkEdits.Result apply(ChunkEdits.Plan plan, ChunkEdits.Context ctx, ChunkEdits.Progress progress, boolean backup) {
      return apply(plan, ctx, progress, point -> {}, backup);
   }

   static ChunkEdits.Result apply(ChunkEdits.Plan plan, ChunkEdits.Context ctx, ChunkEdits.Progress progress, ChunkEdits.Hook hook) {
      return apply(plan, ctx, progress, hook, true);
   }

   static ChunkEdits.Result apply(ChunkEdits.Plan plan, ChunkEdits.Context ctx, ChunkEdits.Progress progress, ChunkEdits.Hook hook, boolean backup) {
      synchronized (LOCK) {
         if (plan.isEmpty()) {
            return refuse(ChunkEdits.Refusal.NOTHING, Message.of("chunkatlas.edit.nothing_to_change"));
         }

         if (!held(ctx)) {
            return refuse(ChunkEdits.Refusal.NOT_LOCKED, NOT_LOCKED);
         }

         ChunkCopy.Details copy = plan.kind() == ChunkEdits.Kind.COPY ? plan.copy() : null;
         if (plan.kind() == ChunkEdits.Kind.COPY) {
            if (copy == null || copy.refused() != null) {
               return refuse(ChunkEdits.Refusal.FAILED, copy == null ? Message.of("chunkatlas.edit.nothing_to_change") : copy.refused());
            }

            boolean sourceHeld;
            try {
               sourceHeld = ctx.sourceHeld() != null && ctx.sourceHeld().getAsBoolean();
            } catch (RuntimeException e) {
               sourceHeld = false;
            }

            if (!sourceHeld) {
               return refuse(ChunkEdits.Refusal.NOT_LOCKED, Message.of("chunkatlas.copy.source_open"));
            }
         }

         ChunkEdits.Manifest m = new ChunkEdits.Manifest();

         Path worldReal;
         Path root;
         try {
            worldReal = plan.world().toRealPath();
            root = backups(ctx.home(), worldReal);
            List<Message> sorted = recoverLocked(root, worldReal);
            if (!sorted.isEmpty()) {
               return refuse(ChunkEdits.Refusal.CHANGED, Message.of("chunkatlas.edit.recovered_first.apply", sorted.get(0)));
            }

            m.world = worldReal.toString();
            m.dimension = plan.dimension();
            m.label = plan.label();
            m.kind = plan.kind().name();
            m.chunks = plan.chunks();
            m.created = System.currentTimeMillis();
            m.state = JOURNALED;
            if (copy != null) {
               Message sourceChanged = ChunkCopy.sourceChanged(copy);
               if (sourceChanged != null) {
                  return refuse(ChunkEdits.Refusal.CHANGED, sourceChanged);
               }
            }

            List<Path> all = new ArrayList<>();

            for (ChunkEdits.FilePlan f : plan.files()) {
               ChunkCopy.Incoming in = copy == null ? null : copy.incoming().get(f.file());
               if (in == null || !in.created()) {
                  all.add(f.file());
               }

               all.addAll(f.external());
            }

            if (copy != null) {
               all.addAll(copy.sources().keySet());
            }

            for (boolean away : Safe.away(all)) {
               if (away) {
                  return refuse(ChunkEdits.Refusal.CHANGED, Message.of("chunkatlas.edit.away"));
               }
            }

            for (ChunkEdits.FilePlan f : plan.files()) {
               Message changed = Message.of("chunkatlas.edit.changed_since_plan", String.valueOf(f.file().getFileName()));
               ChunkCopy.Incoming in = copy == null ? null : copy.incoming().get(f.file());
               if (in == null && copy != null) {
                  return refuse(ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.edit.nothing_to_change"));
               }

               if (in != null && in.created()) {
                  Files.createDirectories(f.file().getParent());
                  if (!missingOrEmpty(f.file())) {
                     return refuse(ChunkEdits.Refusal.CHANGED, changed);
                  }

                  ChunkEdits.Entry e = new ChunkEdits.Entry();
                  e.path = relative(worldReal, f.file().getParent()) + "/" + f.file().getFileName();
                  e.role = f.role();
                  e.cleared = new int[0];
                  e.written = in.written().clone();
                  e.created = true;
                  e.survivors = e.written.length;
                  e.sizeAfter = f.newSize();
                  e.incoming = in;
                  e.details = copy;
                  m.files.add(e);
                  continue;
               }

               BasicFileAttributes a = Files.readAttributes(f.file(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
               if (!a.isRegularFile() || Safe.isLink(a) || a.size() != f.size() || a.lastModifiedTime().toMillis() != f.modified() || a.size() < 8192L) {
                  return refuse(ChunkEdits.Refusal.CHANGED, changed);
               }

               RegionFile.Header header = new RegionFile.Header(RegionFile.rawHeader(f.file()), a.size(), f.modified());
               boolean[] gone = new boolean[1024];

               for (int i : f.indices()) {
                  if (!header.present(i)) {
                     return refuse(ChunkEdits.Refusal.CHANGED, changed);
                  }

                  gone[i] = true;
               }

               ChunkEdits.Entry e = new ChunkEdits.Entry();
               e.path = relative(worldReal, f.file());
               e.role = f.role();
               e.cleared = f.indices().clone();
               Arrays.sort(e.cleared);
               e.sizeBefore = a.size();
               e.modifiedBefore = a.lastModifiedTime().toMillis();
               long sectors = 0L;

               for (int i = 0; i < 1024; i++) {
                  if (header.present(i) && !gone[i]) {
                     e.survivors++;
                     sectors += header.sectors(i);
                  }
               }

               e.sizeAfter = 8192L + sectors * 4096L;
               if (in != null) {
                  e.written = in.written().clone();
                  e.cleared = Arrays.stream(e.cleared).filter(i -> Arrays.binarySearch(e.written, i) < 0).toArray();
                  e.survivors += e.written.length;
                  e.sizeAfter = Math.max(e.sizeAfter, f.newSize());
                  e.incoming = in;
                  e.details = copy;
               }

               for (Path x : f.external()) {
                  BasicFileAttributes xa;
                  try {
                     xa = Files.readAttributes(x, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                  } catch (NoSuchFileException none) {
                     continue;
                  }

                  if (xa.isRegularFile() && !Safe.isLink(xa)) {
                     e.external.add(relative(worldReal, x));
                     e.externalSizes.add(xa.size());
                  }
               }

               m.files.add(e);
            }

            if (!sane(m)) {
               return refuse(ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.edit.not_terrain"));
            }

            for (ChunkEdits.Entry e : m.files) {
               inside(worldReal, e.path);

               for (String x : e.external) {
                  inside(worldReal, x);
               }
            }
         } catch (IOException e) {
            return refuse(ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.edit.nothing_changed_because", why(e)));
         }

         if (!backup) {
            ChunkEdits.Result direct = applyDirect(worldReal, m, progress, hook);
            try {
               Files.createDirectories(root);
               Files.writeString(root.resolve(LAST_DIRECT), Long.toString(System.currentTimeMillis()));
            } catch (IOException | RuntimeException ignored) {
            }

            return direct;
         }

         Path dir;
         try {
            Files.createDirectories(root);
            prune(root, worldReal, System.currentTimeMillis());
            Message shortBy = space(root, worldReal, m, false);
            if (shortBy != null) {
               return refuse(ChunkEdits.Refusal.NO_SPACE, Message.of("chunkatlas.edit.no_space.copy", shortBy));
            }

            m.stamp = stamp(root, worldReal, m);
            m.id = newId(root);
            dir = root.resolve(m.id);
            Files.createDirectories(dir.resolve("files"));
            save(dir, m);
         } catch (IOException e) {
            return refuse(ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.edit.nothing_changed_because", why(e)));
         }

         int total = m.files.size() * 2;
         int done = 0;

         ChunkEdits.Result result;
         try {
            hook.at(JOURNALED);

            for (int n = 0; n < m.files.size(); n++) {
               keep(worldReal, dir, n, m.files.get(n));
               tell(progress, n + 1, total);
            }

            hook.at("copied");

            while (done < m.files.size()) {
               rewrite(worldReal, dir, m, done, hook);
               hook.at("file " + done);
               tell(progress, m.files.size() + done + 1, total);
               done++;
            }

            m.state = APPLIED;
            save(dir, m);
            result = new ChunkEdits.Result(true, ChunkEdits.Refusal.NONE, null, m.id);
         } catch (ChunkEdits.Crash crash) {
            throw crash;
         } catch (IOException | RuntimeException e) {
            Message reason = e instanceof IOException io ? why(io) : Message.of("chunkatlas.edit.why.unexpected", String.valueOf(e));

            try {
               for (int n = Math.min(done, m.files.size() - 1); n >= 0; n--) {
                  restore(worldReal, dir, m, n);
               }

               m.state = ROLLED_BACK;
               m.note = reason.toString();
               save(dir, m);
               forget(dir);
               result = refuse(ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.edit.nothing_changed_because", reason));
            } catch (IOException | RuntimeException again) {
               return refuse(ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.edit.stopped_partway", reason, dir.toString()));
            }

            return result;
         }

         return result;
      }
   }

   private static final String LAST_DIRECT = "last-direct-edit";

   private static long lastDirect(Path root) {
      try {
         return Long.parseLong(Files.readString(root.resolve(LAST_DIRECT)).strip());
      } catch (IOException | RuntimeException e) {
         return 0L;
      }
   }

   private static ChunkEdits.Result applyDirect(Path worldReal, ChunkEdits.Manifest m, ChunkEdits.Progress progress, ChunkEdits.Hook hook) {
      m.id = "direct-" + Long.toHexString(System.nanoTime());
      m.stamp = System.currentTimeMillis();
      int done = 0;
      try {
         while (done < m.files.size()) {
            rewrite(worldReal, null, m, done, hook);
            done++;
            tell(progress, done, m.files.size());
         }

         return new ChunkEdits.Result(true, ChunkEdits.Refusal.NONE, null, null);
      } catch (ChunkEdits.Crash crash) {
         throw crash;
      } catch (IOException | RuntimeException e) {
         Message reason = e instanceof IOException io ? why(io) : Message.of("chunkatlas.edit.why.unexpected", String.valueOf(e));
         return refuse(
            ChunkEdits.Refusal.FAILED,
            done == 0 ? Message.of("chunkatlas.edit.nothing_changed_because", reason) : Message.of("chunkatlas.edit.stopped_direct", done, m.files.size(), reason)
         );
      }
   }

   private static boolean held(ChunkEdits.Context ctx) {
      try {
         return ctx.lockHeld().getAsBoolean();
      } catch (RuntimeException e) {
         return false;
      }
   }

   private static void tell(ChunkEdits.Progress progress, int done, int total) {
      if (progress != null) {
         progress.at(done, total);
      }
   }

   private static Message space(Path root, Path worldReal, ChunkEdits.Manifest m, boolean undo) throws IOException {
      long copies = 0L;
      long moved = 0L;
      long largest = 0L;

      for (ChunkEdits.Entry e : m.files) {
         copies += e.sizeBefore;
         largest = Math.max(largest, undo ? e.sizeBefore : e.sizeAfter);

         for (long size : e.externalSizes) {
            moved += size;
         }
      }

      boolean sameDisk = Files.getFileStore(root).equals(Files.getFileStore(worldReal));
      long forWorld = largest + 67108864L + (undo && !sameDisk ? moved : 0L);
      long forCopies = undo ? 0L : copies + 67108864L + (sameDisk ? 0L : moved);
      if (sameDisk) {
         long free = Safe.free(root);
         long need = undo ? forWorld : forCopies + largest;
         return free >= 0L && free < need ? Message.of("chunkatlas.edit.space.short", Safe.size(need), Safe.size(free)) : null;
      }

      long free = Safe.free(root);
      if (!undo && free >= 0L && free < forCopies) {
         return Message.of("chunkatlas.edit.space.short", Safe.size(forCopies), Safe.size(free));
      }

      free = Safe.free(worldReal);
      return free >= 0L && free < forWorld ? Message.of("chunkatlas.edit.space.short_world", Safe.size(forWorld), Safe.size(free)) : null;
   }

   private static long stamp(Path root, Path worldReal, ChunkEdits.Manifest m) {
      long stamp = m.created / 2000L * 2000L;

      for (ChunkEdits.Loaded l : all(root, worldReal, STATES)) {
         stamp = Math.max(stamp, l.manifest.stamp + 2000L);
      }

      boolean again = true;

      while (again) {
         again = false;

         for (ChunkEdits.Entry e : m.files) {
            if (e.modifiedBefore == stamp) {
               stamp += 2000L;
               again = true;
            }
         }
      }

      return stamp;
   }

   private static Path copyOf(Path dir, int n) {
      return dir.resolve("files").resolve(n + ".bin");
   }

   private static Path aside(Path dir, int n, Path file) {
      return dir.resolve("mcc").resolve(Integer.toString(n)).resolve(file.getFileName().toString());
   }

   private static Path temp(Path file, ChunkEdits.Manifest m) {
      return file.resolveSibling(file.getFileName() + "." + m.id + ".tmp");
   }

   private static void keep(Path worldReal, Path dir, int n, ChunkEdits.Entry e) throws IOException {
      if (e.created) {
         if (!missingOrEmpty(inside(worldReal, e.path))) {
            throw new ChunkEdits.Problem("chunkatlas.edit.why.changed_editing", e.path.substring(e.path.lastIndexOf('/') + 1));
         }

         return;
      }

      Path file = inside(worldReal, e.path);
      Path copy = copyOf(dir, n);
      requireAs(file, e.sizeBefore, e.modifiedBefore);
      Files.copy(file, copy, LinkOption.NOFOLLOW_LINKS);

      try (FileChannel ch = FileChannel.open(copy, StandardOpenOption.WRITE)) {
         ch.force(true);
      }

      if (Files.size(copy) != e.sizeBefore) {
         throw new ChunkEdits.Problem("chunkatlas.edit.why.changed_copying", file.getFileName());
      }

      requireAs(file, e.sizeBefore, e.modifiedBefore);
   }

   static boolean missingOrEmpty(Path file) {
      try {
         BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
         return a.isRegularFile() && !Safe.isLink(a) && a.size() == 0L;
      } catch (NoSuchFileException none) {
         return true;
      } catch (IOException e) {
         return false;
      }
   }

   private static void requireBefore(Path file, ChunkEdits.Entry e) throws IOException {
      if (e.created) {
         if (!missingOrEmpty(file)) {
            throw new ChunkEdits.Problem("chunkatlas.edit.why.changed_editing", file.getFileName());
         }
      } else {
         requireAs(file, e.sizeBefore, e.modifiedBefore);
      }
   }

   private static void requireAs(Path file, long size, long modified) throws IOException {
      BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!a.isRegularFile() || Safe.isLink(a) || a.size() != size || a.lastModifiedTime().toMillis() != modified) {
         throw new ChunkEdits.Problem("chunkatlas.edit.why.changed_editing", file.getFileName());
      }
   }

   private static void rewrite(Path worldReal, Path dir, ChunkEdits.Manifest m, int n, ChunkEdits.Hook hook) throws IOException {
      ChunkEdits.Entry e = m.files.get(n);
      Path file = inside(worldReal, e.path);
      Path temp = temp(file, m);
      requireBefore(file, e);
      boolean[] gone = new boolean[1024];

      for (int i : e.cleared) {
         gone[i] = true;
      }

      ChunkCopy.Payload[] incoming = new ChunkCopy.Payload[1024];
      if (e.written.length > 0) {
         if (e.incoming == null || e.details == null) {
            throw new ChunkEdits.Problem("chunkatlas.edit.why.changed_editing", file.getFileName());
         }

         for (int k = 0; k < e.incoming.written().length; k++) {
            int i = e.incoming.written()[k];
            incoming[i] = ChunkCopy.payload(e.details, e.role, e.incoming.from()[k], e.incoming.fromIndex()[k], e.incoming.marked()[k]);
            gone[i] = true;
         }
      }

      Files.deleteIfExists(temp);

      try {
         try (
            FileChannel in = e.created ? null : FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            FileChannel out = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE);
         ) {
            RegionFile.Header old = in == null
               ? new RegionFile.Header(new byte[8192], 0L, 0L)
               : new RegionFile.Header(RegionFile.rawHeader(in), e.sizeBefore, e.modifiedBefore);
            ByteBuffer table = ByteBuffer.allocate(8192);
            int next = 2;
            int survivors = 0;

            for (int i = 0; i < 1024; i++) {
               if (incoming[i] != null) {
                  int count = incoming[i].sectors().length / 4096;
                  writeAll(out, ByteBuffer.wrap(incoming[i].sectors()), next * 4096L);
                  table.putInt(i * 4, next << 8 | count);
                  table.putInt(4096 + i * 4, incoming[i].timestamp());
                  next += count;
                  survivors++;
               } else if (old.present(i) && !gone[i]) {
                  int count = old.sectors(i);
                  writeAll(out, ByteBuffer.wrap(sectors(in, old.sector(i), count)), next * 4096L);
                  table.putInt(i * 4, next << 8 | count);
                  table.putInt(4096 + i * 4, old.timestamps[i]);
                  next += count;
                  survivors++;
               }
            }

            writeAll(out, table, 0L);
            out.force(true);
            if (survivors != e.survivors || (e.written.length == 0 && out.size() != e.sizeAfter)) {
               throw new ChunkEdits.Problem("chunkatlas.edit.why.changed_editing", file.getFileName());
            }

            RegionFile.Header made = new RegionFile.Header(RegionFile.rawHeader(out), out.size(), 0L);

            for (int i = 0; i < 1024; i++) {
               if (incoming[i] != null) {
                  if (!made.present(i)
                     || made.sectors(i) * 4096 != incoming[i].sectors().length
                     || made.timestamps[i] != incoming[i].timestamp()
                     || !Arrays.equals(sectors(out, made.sector(i), made.sectors(i)), incoming[i].sectors())) {
                     throw new ChunkEdits.Problem("chunkatlas.edit.why.bad_result", file.getFileName());
                  }
               } else if (old.present(i) && !gone[i]) {
                  if (!made.present(i)
                     || made.sectors(i) != old.sectors(i)
                     || made.timestamps[i] != old.timestamps[i]
                     || !Arrays.equals(sectors(out, made.sector(i), made.sectors(i)), sectors(in, old.sector(i), old.sectors(i)))) {
                     throw new ChunkEdits.Problem("chunkatlas.edit.why.bad_result", file.getFileName());
                  }
               } else if (made.locations[i] != 0 || made.timestamps[i] != 0) {
                  throw new ChunkEdits.Problem("chunkatlas.edit.why.bad_result", file.getFileName());
               }
            }

            if (e.written.length > 0 && out.size() != e.sizeAfter) {
               e.sizeAfter = out.size();
               if (dir != null) {
                  save(dir, m);
               }
            }
         }

         Files.setLastModifiedTime(temp, FileTime.fromMillis(m.stamp));
         requireAs(temp, e.sizeAfter, m.stamp);
         requireBefore(file, e);
         hook.at("before rename " + n);
         Safe.replace(temp, file);
      } finally {
         Files.deleteIfExists(temp);
      }

      for (String x : e.external) {
         Path from = inside(worldReal, x);
         if (dir == null) {
            if (Safe.plainFile(from)) {
               Files.deleteIfExists(from);
            }

            continue;
         }

         Path to = aside(dir, n, from);
         if (Safe.plainFile(from) && !Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(to.getParent());
            Files.move(from, to);
         }
      }
   }

   private static byte[] sectors(FileChannel ch, int sector, int count) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(count * 4096);
      long at = sector * 4096L;

      while (buf.hasRemaining()) {
         int got = ch.read(buf, at + buf.position());
         if (got < 0) {
            break;
         }
      }

      return buf.array();
   }

   private static void writeAll(FileChannel ch, ByteBuffer buf, long at) throws IOException {
      buf.rewind();

      while (buf.hasRemaining()) {
         ch.write(buf, at + buf.position());
      }
   }

   private static ChunkEdits.Now now(Path file, ChunkEdits.Manifest m, ChunkEdits.Entry e) {
      if (e.created && missingOrEmpty(file)) {
         return ChunkEdits.Now.ORIGINAL;
      }

      try {
         BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
         if (a.isRegularFile() && !Safe.isLink(a)) {
            long modified = a.lastModifiedTime().toMillis();
            if (a.size() == e.sizeBefore && modified == e.modifiedBefore) {
               return ChunkEdits.Now.ORIGINAL;
            } else {
               return a.size() == e.sizeAfter && modified == m.stamp ? ChunkEdits.Now.OURS : ChunkEdits.Now.OTHER;
            }
         } else {
            return ChunkEdits.Now.OTHER;
         }
      } catch (IOException x) {
         return ChunkEdits.Now.OTHER;
      }
   }

   private static void restore(Path worldReal, Path dir, ChunkEdits.Manifest m, int n) throws IOException {
      ChunkEdits.Entry e = m.files.get(n);
      Path file = inside(worldReal, e.path);
      Path temp = temp(file, m);
      if (Files.isRegularFile(temp, LinkOption.NOFOLLOW_LINKS)) {
         Files.delete(temp);
      }

      ChunkEdits.Now now = now(file, m, e);
      if (now == ChunkEdits.Now.OTHER) {
         throw new ChunkEdits.Problem("chunkatlas.edit.why.changed_by_other", file.getFileName());
      }

      if (now == ChunkEdits.Now.OURS && e.created) {
         Files.delete(file);
      } else if (now == ChunkEdits.Now.OURS) {
         Path copy = copyOf(dir, n);
         BasicFileAttributes a = Files.readAttributes(copy, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
         if (!a.isRegularFile() || a.size() != e.sizeBefore) {
            throw new ChunkEdits.Problem("chunkatlas.edit.why.copy_damaged", file.getFileName());
         }

         try {
            Files.copy(copy, temp, LinkOption.NOFOLLOW_LINKS);

            try (FileChannel ch = FileChannel.open(temp, StandardOpenOption.WRITE)) {
               ch.force(true);
            }

            Files.setLastModifiedTime(temp, FileTime.fromMillis(e.modifiedBefore));
            requireAs(temp, e.sizeBefore, e.modifiedBefore);
            if (now(file, m, e) != ChunkEdits.Now.OURS) {
               throw new ChunkEdits.Problem("chunkatlas.edit.why.changed_by_other", file.getFileName());
            }

            Safe.replace(temp, file);
         } finally {
            Files.deleteIfExists(temp);
         }
      }

      for (String x : e.external) {
         Path to = inside(worldReal, x);
         Path from = aside(dir, n, to);
         if (Files.isRegularFile(from, LinkOption.NOFOLLOW_LINKS) && !Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
            Files.move(from, to);
         }
      }
   }

   public static Optional<ChunkEdits.Edit> last(Path home, Path world) {
      synchronized (LOCK) {
         Optional<ChunkEdits.Edit> found;
         try {
            Path worldReal = world.toRealPath();
            Path root = backups(home, worldReal);
            long direct = lastDirect(root);

            found = newest(root, worldReal, Set.of(APPLIED)).map(l -> edit(l.manifest)).filter(e -> e.createdMillis() > direct);
         } catch (IOException | RuntimeException e) {
            return Optional.empty();
         }

         return found;
      }
   }

   private static ChunkEdits.Edit edit(ChunkEdits.Manifest m) {
      long bytes = 0L;

      for (ChunkEdits.Entry e : m.files) {
         bytes += e.sizeBefore;

         for (long size : e.externalSizes) {
            bytes += size;
         }
      }

      return new ChunkEdits.Edit(m.id, ChunkEdits.Kind.valueOf(m.kind), m.dimension, m.label, m.chunks, m.created, bytes);
   }

   public static ChunkEdits.Result undo(Path world, ChunkEdits.Context ctx) {
      return undo(world, ctx, null, point -> {});
   }

   public static ChunkEdits.Result undo(Path world, ChunkEdits.Context ctx, ChunkEdits.Progress progress) {
      return undo(world, ctx, progress, point -> {});
   }

   static ChunkEdits.Result undo(Path world, ChunkEdits.Context ctx, ChunkEdits.Progress progress, ChunkEdits.Hook hook) {
      synchronized (LOCK) {
         if (!held(ctx)) {
            return refuse(ChunkEdits.Refusal.NOT_LOCKED, NOT_LOCKED);
         }

         ChunkEdits.Loaded last;
         Path worldReal;
         try {
            worldReal = world.toRealPath();
            Path root = backups(ctx.home(), worldReal);
            List<Message> sorted = recoverLocked(root, worldReal);
            if (!sorted.isEmpty()) {
               return refuse(ChunkEdits.Refusal.CHANGED, Message.of("chunkatlas.edit.recovered_first.undo", sorted.get(0)));
            }

            last = newest(root, worldReal, Set.of(APPLIED)).orElse(null);
            if (last == null) {
               return refuse(ChunkEdits.Refusal.NOTHING, Message.of("chunkatlas.edit.nothing_to_undo"));
            }

            for (int n = 0; n < last.manifest.files.size(); n++) {
               Message changed = changedSince(worldReal, last.dir, last.manifest, n);
               if (changed != null) {
                  return refuse(ChunkEdits.Refusal.CHANGED, changed);
               }
            }

            Message shortBy = space(root, worldReal, last.manifest, true);
            if (shortBy != null) {
               return refuse(ChunkEdits.Refusal.NO_SPACE, Message.of("chunkatlas.edit.no_space.undo", shortBy));
            }

            last.manifest.state = UNDOING;
            save(last.dir, last.manifest);
         } catch (IOException e) {
            return refuse(ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.edit.nothing_changed_because", why(e)));
         }

         ChunkEdits.Result result;
         try {
            hook.at(UNDOING);
            int total = last.manifest.files.size();

            for (int n = total - 1; n >= 0; n--) {
               restore(worldReal, last.dir, last.manifest, n);
               hook.at("file " + n);
               tell(progress, total - n, total);
            }

            last.manifest.state = UNDONE;
            save(last.dir, last.manifest);
            forget(last.dir);
            result = new ChunkEdits.Result(true, ChunkEdits.Refusal.NONE, null, last.manifest.id);
         } catch (ChunkEdits.Crash crash) {
            throw crash;
         } catch (IOException | RuntimeException e) {
            Message reason = e instanceof IOException io ? why(io) : Message.of("chunkatlas.edit.why.unexpected", String.valueOf(e));
            return refuse(ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.edit.undo_stopped_partway", reason));
         }

         return result;
      }
   }

   private static Message changedSince(Path worldReal, Path dir, ChunkEdits.Manifest m, int n) {
      ChunkEdits.Entry e = m.files.get(n);
      String name = e.path.substring(e.path.lastIndexOf('/') + 1);
      Message gone = Message.of("chunkatlas.edit.undo.world_changed", name);
      Message damaged = Message.of("chunkatlas.edit.undo.copy_damaged");

      try {
         Path file = inside(worldReal, e.path);
         if (now(file, m, e) == ChunkEdits.Now.OURS && !Safe.away(file)) {
            RegionFile.Header header = RegionFile.header(file);
            if (header.count() != e.survivors) {
               return gone;
            }

            for (int i : e.cleared) {
               if (header.locations[i] != 0) {
                  return gone;
               }
            }

            for (int i : e.written) {
               if (!header.present(i)) {
                  return gone;
               }
            }

            if (e.created) {
               return null;
            }

            BasicFileAttributes a = Files.readAttributes(copyOf(dir, n), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (a.isRegularFile() && a.size() == e.sizeBefore) {
               for (String x : e.external) {
                  Path to = inside(worldReal, x);
                  if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
                     return gone;
                  }

                  if (!Files.isRegularFile(aside(dir, n, to), LinkOption.NOFOLLOW_LINKS)) {
                     return damaged;
                  }
               }

               return null;
            } else {
               return damaged;
            }
         } else {
            return gone;
         }
      } catch (NoSuchFileException x) {
         return x.getFile() != null && x.getFile().startsWith(dir.toString()) ? damaged : gone;
      } catch (IOException | RuntimeException x) {
         return gone;
      }
   }

   public static boolean pending(Path home, Path world) {
      synchronized (LOCK) {
         boolean any;
         try {
            Path worldReal = world.toRealPath();
            any = !all(backups(home, worldReal), worldReal, Set.of(JOURNALED, UNDOING)).isEmpty();
         } catch (IOException | RuntimeException e) {
            return false;
         }

         return any;
      }
   }

   public static List<Message> recover(Path world, ChunkEdits.Context ctx) {
      synchronized (LOCK) {
         List<Message> said;
         try {
            if (!held(ctx)) {
               return List.of();
            }

            Path worldReal = world.toRealPath();
            said = recoverLocked(backups(ctx.home(), worldReal), worldReal);
         } catch (IOException | RuntimeException e) {
            return List.of();
         }

         return said;
      }
   }

   private static List<Message> recoverLocked(Path root, Path worldReal) {
      List<Message> said = new ArrayList<>();
      List<ChunkEdits.Loaded> pending = all(root, worldReal, Set.of(JOURNALED, UNDOING));
      pending.sort(Comparator.<ChunkEdits.Loaded>comparingLong(lx -> lx.manifest.stamp).reversed());

      for (ChunkEdits.Loaded l : pending) {
         said.add(finish(worldReal, l));
      }

      return said;
   }

   private static Message finish(Path worldReal, ChunkEdits.Loaded l) {
      ChunkEdits.Manifest m = l.manifest;
      boolean undoing = m.state.equals(UNDOING);

      try {
         for (ChunkEdits.Entry e : m.files) {
            if (now(inside(worldReal, e.path), m, e) == ChunkEdits.Now.OTHER) {
               throw new ChunkEdits.Problem("chunkatlas.edit.why.changed_by_other", e.path.substring(e.path.lastIndexOf('/') + 1));
            }
         }

         for (int n = m.files.size() - 1; n >= 0; n--) {
            restore(worldReal, l.dir, m, n);
         }

         m.state = undoing ? UNDONE : ROLLED_BACK;
         m.note = "cut short";
         save(l.dir, m);
         forget(l.dir);
         return Message.of(undoing ? "chunkatlas.edit.recover.undo_finished" : "chunkatlas.edit.recover.rolled_back");
      } catch (IOException | RuntimeException e) {
         m.state = INTERRUPTED;
         Message reason = e instanceof IOException io ? why(io) : Message.literal(String.valueOf(e));
         m.note = reason.toString();

         try {
            save(l.dir, m);
         } catch (IOException ignored) {
         }

         return Message.of("chunkatlas.edit.recover.stuck", reason, l.dir.toString());
      }
   }

   private static Optional<ChunkEdits.Loaded> newest(Path root, Path worldReal, Set<String> states) {
      return all(root, worldReal, states).stream().max(Comparator.comparingLong(l -> l.manifest.stamp));
   }

   private static List<ChunkEdits.Loaded> all(Path root, Path worldReal, Set<String> states) {
      List<ChunkEdits.Loaded> out = new ArrayList<>();
      if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
         return out;
      }

      try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
         for (Path dir : dirs) {
            Path file = dir.resolve("edit.txt");
            if (ID.matcher(dir.getFileName().toString()).matches()
               && Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)
               && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
               && Files.size(file) <= 67108864L) {
               try {
                  ChunkEdits.Manifest m = parse(Files.readString(file, StandardCharsets.UTF_8));
                  if (m != null && worldReal.toString().equals(m.world) && states.contains(m.state) && dir.getFileName().toString().equals(m.id) && sane(m)) {
                     out.add(new ChunkEdits.Loaded(dir, m));
                  }
               } catch (IOException | RuntimeException ignored) {
               }
            }
         }
      } catch (IOException ignored) {
      }

      return out;
   }

   private static boolean sane(ChunkEdits.Manifest m) {
      if (m.kind != null && m.state != null && m.world != null && STATES.contains(m.state)) {
         ChunkEdits.Kind.valueOf(m.kind);

         for (ChunkEdits.Entry e : m.files) {
            if (e.path != null
               && e.cleared != null
               && ROLES.contains(e.role)
               && (e.sizeBefore >= 8192L || e.created && e.sizeBefore == 0L && e.external.isEmpty())
               && e.sizeAfter >= 8192L
               && sorted(e.written)
               && e.survivors >= 0
               && e.external.size() == e.externalSizes.size()) {
               String[] parts = e.path.split("/");
               if (parts.length >= 2 && parts[parts.length - 2].equals(e.role) && !RegionFile.coords(parts[parts.length - 1]).isEmpty()) {
                  String folder = e.path.substring(0, e.path.lastIndexOf('/') + 1);
                  int before = -1;

                  for (int i : e.cleared) {
                     if (i <= before || i >= 1024) {
                        return false;
                     }

                     before = i;
                  }

                  for (String x : e.external) {
                     if (!x.startsWith(folder) || !EXTERNAL.matcher(x.substring(folder.length())).matches()) {
                        return false;
                     }
                  }

                  continue;
               }

               return false;
            }

            return false;
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean sorted(int[] indices) {
      int before = -1;
      for (int i : indices) {
         if (i <= before || i >= 1024) {
            return false;
         }

         before = i;
      }

      return true;
   }

   private static String enc(String s) {
      return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
   }

   private static String dec(String s) {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
   }

   static String format(ChunkEdits.Manifest m) {
      StringBuilder sb = new StringBuilder();
      sb.append("edit 1").append('\n');
      sb.append("id=").append(m.id).append('\n');
      sb.append("world=").append(enc(m.world)).append('\n');
      sb.append("dimension=").append(enc(m.dimension)).append('\n');
      sb.append("label=").append(enc(m.label)).append('\n');
      sb.append("kind=").append(m.kind).append('\n');
      sb.append("chunks=").append(m.chunks).append('\n');
      sb.append("created=").append(m.created).append('\n');
      sb.append("stamp=").append(m.stamp).append('\n');
      sb.append("state=").append(m.state).append('\n');
      sb.append("note=").append(enc(m.note)).append('\n');

      for (int n = 0; n < m.files.size(); n++) {
         ChunkEdits.Entry e = m.files.get(n);
         sb.append("file\t")
            .append(e.role)
            .append('\t')
            .append(enc(e.path))
            .append('\t')
            .append(e.sizeBefore)
            .append('\t')
            .append(e.modifiedBefore)
            .append('\t')
            .append(e.sizeAfter)
            .append('\t')
            .append(e.survivors)
            .append('\t');

         for (int i = 0; i < e.cleared.length; i++) {
            sb.append(i == 0 ? "" : ",").append(e.cleared[i]);
         }

         sb.append('\n');

         for (int x = 0; x < e.external.size(); x++) {
            sb.append("mcc\t").append(n).append('\t').append(enc(e.external.get(x))).append('\t').append(e.externalSizes.get(x)).append('\n');
         }

         if (e.created || e.written.length > 0) {
            sb.append("wrote\t").append(n).append('\t').append(e.created ? 1 : 0).append('\t');
            for (int i = 0; i < e.written.length; i++) {
               sb.append(i == 0 ? "" : ",").append(e.written[i]);
            }

            sb.append('\n');
         }
      }

      sb.append("end\n");
      return sb.toString();
   }

   static ChunkEdits.Manifest parse(String text) {
      String[] lines = text.split("\n", -1);
      if (lines.length >= 3 && lines[0].equals("edit 1") && lines[lines.length - 2].equals("end") && lines[lines.length - 1].isEmpty()) {
         ChunkEdits.Manifest m = new ChunkEdits.Manifest();

         for (int at = 1; at < lines.length - 2; at++) {
            String line = lines[at];
            if (line.startsWith("file\t")) {
               String[] f = line.split("\t", -1);
               if (f.length != 8) {
                  return null;
               }

               ChunkEdits.Entry e = new ChunkEdits.Entry();
               e.role = f[1];
               e.path = dec(f[2]);
               e.sizeBefore = Long.parseLong(f[3]);
               e.modifiedBefore = Long.parseLong(f[4]);
               e.sizeAfter = Long.parseLong(f[5]);
               e.survivors = Integer.parseInt(f[6]);
               String[] cleared = f[7].isEmpty() ? new String[0] : f[7].split(",");
               e.cleared = new int[cleared.length];

               for (int i = 0; i < cleared.length; i++) {
                  e.cleared[i] = Integer.parseInt(cleared[i]);
                  if (e.cleared[i] < 0) {
                     return null;
                  }
               }

               m.files.add(e);
            } else if (line.startsWith("wrote\t")) {
               String[] f = line.split("\t", -1);
               int n = f.length == 4 ? Integer.parseInt(f[1]) : -1;
               if (n < 0 || n >= m.files.size() || !f[2].matches("[01]")) {
                  return null;
               }

               ChunkEdits.Entry e = m.files.get(n);
               e.created = f[2].equals("1");
               String[] written = f[3].isEmpty() ? new String[0] : f[3].split(",");
               e.written = new int[written.length];
               for (int i = 0; i < written.length; i++) {
                  e.written[i] = Integer.parseInt(written[i]);
               }
            } else if (line.startsWith("mcc\t")) {
               String[] f = line.split("\t", -1);
               int n = f.length == 4 ? Integer.parseInt(f[1]) : -1;
               if (n < 0 || n >= m.files.size()) {
                  return null;
               }

               m.files.get(n).external.add(dec(f[2]));
               m.files.get(n).externalSizes.add(Long.parseLong(f[3]));
            } else {
               int eq = line.indexOf('=');
               if (eq < 0) {
                  return null;
               }

               String value = line.substring(eq + 1);
               switch (line.substring(0, eq)) {
                  case "id":
                     m.id = value;
                     break;
                  case "world":
                     m.world = dec(value);
                     break;
                  case "dimension":
                     m.dimension = dec(value);
                     break;
                  case "label":
                     m.label = dec(value);
                     break;
                  case "kind":
                     m.kind = value;
                     break;
                  case "chunks":
                     m.chunks = Integer.parseInt(value);
                     break;
                  case "created":
                     m.created = Long.parseLong(value);
                     break;
                  case "stamp":
                     m.stamp = Long.parseLong(value);
                     break;
                  case "state":
                     m.state = value;
                     break;
                  case "note":
                     m.note = dec(value);
               }
            }
         }

         return m.id != null && ID.matcher(m.id).matches() ? m : null;
      } else {
         return null;
      }
   }

   private static void save(Path dir, ChunkEdits.Manifest m) throws IOException {
      Safe.write(dir.resolve("edit.txt"), format(m).getBytes(StandardCharsets.UTF_8));
   }

   private static void forget(Path dir) {
      deleteTree(dir.resolve("files"));
      deleteTree(dir.resolve("mcc"));
   }

   private static void prune(Path root, Path worldReal, long now) {
      for (ChunkEdits.Loaded l : all(root, worldReal, Set.of(UNDONE, ROLLED_BACK, APPLIED))) {
         boolean old = now - l.manifest.created > 2592000000L;
         if (!l.manifest.state.equals(APPLIED) || old) {
            deleteTree(l.dir);
         }
      }
   }

   private static void deleteTree(Path dir) {
      if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
         try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
               public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                  Files.delete(file);
                  return FileVisitResult.CONTINUE;
               }

               public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                  Files.delete(d);
                  return FileVisitResult.CONTINUE;
               }
            });
         } catch (IOException ignored) {
         }
      }
   }

   private static String newId(Path root) {
      String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

      String id;
      do {
         id = stamp + "-" + String.format("%04x", ThreadLocalRandom.current().nextInt(65536));
      } while (Files.exists(root.resolve(id), LinkOption.NOFOLLOW_LINKS));

      return id;
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

   private static String relative(Path worldReal, Path file) throws IOException {
      Path real = file.toRealPath();
      if (real.startsWith(worldReal) && !real.equals(worldReal)) {
         StringBuilder sb = new StringBuilder();

         for (Path part : worldReal.relativize(real)) {
            sb.append(sb.length() == 0 ? "" : "/").append(part);
         }

         return sb.toString();
      } else {
         throw new ChunkEdits.Problem("chunkatlas.edit.why.not_inside", file.getFileName());
      }
   }

   private static Path inside(Path worldReal, String relative) throws IOException {
      Path p = worldReal;

      for (String part : relative.split("/")) {
         if (part.isEmpty() || part.equals(".") || part.equals("..") || part.indexOf('\\') >= 0 || part.indexOf(':') >= 0) {
            throw new ChunkEdits.Problem("chunkatlas.edit.why.record_outside");
         }

         p = p.resolve(part);
      }

      Path parent = p.getParent();
      if (parent != null && !parent.equals(worldReal) && parent.toRealPath().equals(parent)) {
         return p;
      } else {
         throw new ChunkEdits.Problem("chunkatlas.edit.why.record_outside");
      }
   }

   private static ChunkEdits.Result refuse(ChunkEdits.Refusal refusal, Message message) {
      return new ChunkEdits.Result(false, refusal, message, null);
   }

   private static Message why(IOException e) {
      if (e instanceof ChunkEdits.Problem p) {
         return p.text;
      } else if (e instanceof NoSuchFileException n && n.getFile() != null) {
         return Message.of("chunkatlas.edit.why.missing", String.valueOf(Path.of(n.getFile()).getFileName()));
      } else if (e instanceof AccessDeniedException a && a.getFile() != null) {
         return Message.of("chunkatlas.edit.why.denied", String.valueOf(Path.of(a.getFile()).getFileName()));
      } else {
         String m = e.getMessage();
         return m != null && !m.isBlank()
            ? Message.literal(Character.toUpperCase(m.charAt(0)) + m.substring(1) + (m.endsWith(".") ? "" : "."))
            : Message.of("chunkatlas.edit.why.io");
      }
   }

   static final class Problem extends IOException {
      private static final long serialVersionUID = 1L;
      final Message text;

      Problem(String key, Object... args) {
         super(key);
         Object[] words = new Object[args.length];
         for (int i = 0; i < args.length; i++) {
            words[i] = String.valueOf(args[i]);
         }

         this.text = Message.of(key, words);
      }
   }

   public record Context(Path home, BooleanSupplier lockHeld, BooleanSupplier sourceHeld) {
      public Context(Path home, BooleanSupplier lockHeld) {
         this(home, lockHeld, null);
      }

      public ChunkEdits.Context withSource(BooleanSupplier held) {
         return new ChunkEdits.Context(this.home, this.lockHeld, held);
      }
   }

   static final class Crash extends RuntimeException {
      private static final long serialVersionUID = 1L;

      Crash() {
         super("simulated crash");
      }
   }

   public record Edit(String id, ChunkEdits.Kind kind, String dimension, String label, int chunks, long createdMillis, long copyBytes) {
   }

   static final class Entry {
      String path;
      String role;
      int[] cleared;
      int survivors;
      long sizeBefore;
      long modifiedBefore;
      long sizeAfter;
      final List<String> external = new ArrayList<>();
      final List<Long> externalSizes = new ArrayList<>();

      int[] written = new int[0];
      boolean created;

      ChunkCopy.Incoming incoming;
      ChunkCopy.Details details;
   }

   record External(Path file, long size, boolean away) {
   }

   public record FilePlan(Path file, String role, int rx, int rz, int[] indices, int survivors, long size, long modified, long newSize, List<Path> external) {
   }

   interface Hook {
      void at(String point) throws IOException;
   }

   public enum Kind {
      DELETE,
      KEEP_ONLY,

      DELETE_MOBS,

      COPY;
   }

   private record Loaded(Path dir, ChunkEdits.Manifest manifest) {
   }

   static final class Manifest {
      String id;
      String world;
      String dimension = "";
      String label = "";
      String kind;
      int chunks;
      long created;
      long stamp;
      String state;
      String note = "";
      final List<ChunkEdits.Entry> files = new ArrayList<>();
   }

   private enum Now {
      ORIGINAL,
      OURS,
      OTHER;
   }

   public record Plan(
      Path world,
      String dimension,
      String label,
      ChunkEdits.Kind kind,
      int chunks,
      int kept,
      int regionFiles,
      int otherFiles,
      long before,
      long after,
      long externalBytes,
      int unreadable,
      int away,
      List<ChunkEdits.FilePlan> files,

      boolean mobsInsideChunks,

      ChunkCopy.Details copy
   ) {
      public Plan(
         Path world,
         String dimension,
         String label,
         ChunkEdits.Kind kind,
         int chunks,
         int kept,
         int regionFiles,
         int otherFiles,
         long before,
         long after,
         long externalBytes,
         int unreadable,
         int away,
         List<ChunkEdits.FilePlan> files,
         boolean mobsInsideChunks
      ) {
         this(world, dimension, label, kind, chunks, kept, regionFiles, otherFiles, before, after, externalBytes, unreadable, away, files, mobsInsideChunks, null);
      }

      public boolean isEmpty() {
         return this.files.isEmpty();
      }

      public long freed() {
         return this.before - this.after + this.externalBytes;
      }

      public long copyBytes() {
         return this.before + this.externalBytes;
      }
   }

   public interface Progress {
      void at(int done, int total);
   }

   public enum Refusal {
      NONE,
      NOTHING,
      NOT_LOCKED,
      CHANGED,
      NO_SPACE,
      FAILED;
   }

   public record Result(boolean ok, ChunkEdits.Refusal refusal, Message message, String editId) {
   }
}
