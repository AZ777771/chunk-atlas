package dev.chunkatlas.server;

import dev.chunkatlas.engine.ChunkCopy;
import dev.chunkatlas.engine.ChunkEdits;
import dev.chunkatlas.engine.ChunkFields;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.ClipArchive;
import dev.chunkatlas.engine.EditJournal;
import dev.chunkatlas.engine.LiveWork;
import dev.chunkatlas.engine.Nbt;
import dev.chunkatlas.engine.PasteLimit;
import dev.chunkatlas.engine.ServerEdit;
import dev.chunkatlas.net.AtlasNet;
import dev.chunkatlas.port.Ids;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class LiveQueue {
   private static final int LOOK_PER_CHANGE = 8;

   private static final int PAUSE = 40;

   private static final LiveWork work = new LiveWork();

   private static final Map<Long, Map<Long, CompoundTag[]>> ready = new ConcurrentHashMap<>();
   private static final Set<Long> unpacking = ConcurrentHashMap.newKeySet();

   private static final Set<Long> finishedUnsaved = new HashSet<>();
   private static boolean unsaved;
   private static boolean toldSaveFailed;
   private static int pause;
   private static MinecraftServer server;

   private LiveQueue() {
   }

   static void started(MinecraftServer s, EditJournal<ServerEdit> journal) {
      server = s;
      work.clear();
      ready.clear();
      unpacking.clear();
      finishedUnsaved.clear();
      unsaved = false;
      pause = 0;
      int n = 0;
      for (ServerEdit e : journal.items()) {
         if (e.live()) {
            work.add(e);
            n++;
         }
      }

      if (n > 0) {
         ServerEdits.LOG.info("Chunk Atlas carries on with {} live edits ({} chunks to change)", n, work.left());
      }
   }

   static void stopping() {
      if (unsaved) {
         save();
      }
   }

   static int left() {
      return work.left();
   }

   static void tick(MinecraftServer s) {
      if (server != s || work.isEmpty() && !unsaved) {
         return;
      }

      if (pause > 0) {
         pause--;
         return;
      }

      if (unsaved && !save()) {
         pause = PAUSE;
         return;
      }

      if (!LiveEdits.available()) {
         pause = PAUSE * 5;
         return;
      }

      int most = ServerSettings.liveChunksPerTick();
      LiveWork.Step step = work.tick(most, most * LOOK_PER_CHANGE, WORLD);
      if (step.made() > 0 || !step.finished().isEmpty()) {
         finishedUnsaved.addAll(step.finished());
         unsaved = true;
         if (!save()) {
            pause = PAUSE;
            return;
         }
      }

      if (work.idle()) {
         pause = PAUSE;
      }
   }

   private static boolean save() {
      Map<Long, ServerEdit> now = new HashMap<>();
      for (ServerEdit e : work.edits()) {
         now.put(e.id(), e);
      }

      List<ServerEdit> finished = new ArrayList<>();
      try {
         ServerEdits.journal().update(list -> {
            list.removeIf(e -> {
               if (e.live() && finishedUnsaved.contains(e.id())) {
                  finished.add(e);
                  return true;
               }

               return false;
            });
            list.replaceAll(e -> e.live() && now.containsKey(e.id()) ? now.get(e.id()) : e);
            return list;
         });
      } catch (IOException e) {
         if (!toldSaveFailed && server != null) {
            toldSaveFailed = true;
            ServerEdits.tellOps(server, "Couldn't save how far live edits got; they're paused until it can be saved");
         }

         return false;
      }

      for (ServerEdit e : finished) {
         ready.remove(e.id());
         ServerEdits.deleteClip(e.clip());
         ServerEdits.LOG.info("Chunk Atlas finished a live {} asked for by {} in {}", e.kind().name().toLowerCase(java.util.Locale.ROOT), e.who(), e.dimension());
      }

      finishedUnsaved.clear();
      unsaved = false;
      toldSaveFailed = false;
      return true;
   }

   private static final LiveWork.World WORLD = new LiveWork.World() {
      @Override
      public boolean busy(String dimension, int cx, int cz) {
         ServerLevel level = Transfers.level(server, dimension);
         return level == null || LiveEdits.busy(level, cx, cz);
      }

      @Override
      public boolean ready(ServerEdit edit) {
         if (!edit.isPaste() || ready.containsKey(edit.id())) {
            return true;
         }

         if (unpacking.add(edit.id())) {
            ServerEdits.WORK.submit(() -> unpackSaved(edit));
         }

         return false;
      }

      @Override
      public boolean apply(ServerEdit edit, int i) {
         ServerLevel level = Transfers.level(server, edit.dimension());
         if (level == null) {
            return false;
         }

         int cx = ServerEdit.spotX(edit.spots()[i]);
         int cz = ServerEdit.spotZ(edit.spots()[i]);
         try {
            switch (edit.actions()[i]) {
               case ServerEdit.PLACE -> {
                  Map<Long, CompoundTag[]> chunks = ready.get(edit.id());
                  CompoundTag[] tags = chunks == null ? null : chunks.get(edit.spots()[i]);
                  if (tags == null) {
                     return false;
                  }

                  LiveEdits.writeChunk(level, cx, cz, tags[0]);
                  LiveEdits.writeEntities(level, cx, cz, tags[1]);
               }
               case ServerEdit.CLEAR -> {
                  LiveEdits.writeChunk(level, cx, cz, null);
                  LiveEdits.writeEntities(level, cx, cz, null);
               }
               default -> LiveEdits.writeEntities(level, cx, cz, null);
            }

            return true;
         } catch (RuntimeException e) {
            ServerEdits.LOG.warn("Chunk Atlas couldn't change chunk {}, {}; it's tried again later", cx, cz, e);
            return false;
         }
      }
   };

   private static void unpackSaved(ServerEdit edit) {
      Map<Long, CompoundTag[]> tags;
      try {
         ClipArchive.Archive a = ClipArchive.read(Files.readAllBytes(Path.of(edit.clip())), Integer.MAX_VALUE);
         Set<Long> wanted = new HashSet<>();
         for (int i = 0; i < edit.spots().length; i++) {
            if (edit.actions()[i] == ServerEdit.PLACE) {
               wanted.add(edit.spots()[i]);
            }
         }

         tags = unpack(a, edit.dx(), edit.dz(), edit.blend(), edit.dimension(), wanted);
      } catch (IOException | RuntimeException e) {
         tags = null;
      }

      Map<Long, CompoundTag[]> t = tags;
      server.execute(() -> {
         unpacking.remove(edit.id());
         if (t != null) {
            ready.put(edit.id(), t);
            return;
         }

         work.remove(edit.id());
         finishedUnsaved.add(edit.id());
         unsaved = true;
         ServerEdits.tellOps(server, "A live paste by " + edit.who() + " couldn't be read back after the restart and was dropped; nothing more of it is made");
      });
   }

   private static Map<Long, CompoundTag[]> unpack(ClipArchive.Archive a, int dx, int dz, int blend, String dimension, Set<Long> wanted) throws IOException {
      Map<Long, CompoundTag[]> out = new HashMap<>();
      for (ClipArchive.Entry e : a.entries()) {
         long spot = ServerEdit.spot(e.cx() + dx, e.cz() + dz);
         if (!wanted.contains(spot)) {
            continue;
         }

         Map<String, Object> root = Nbt.read(ClipArchive.inflate(e.region()));
         ChunkCopy.move(root, "region", dx, dz, dimension);
         if (blend > 0) {
            ChunkFields.markForBlending(root);
         }

         CompoundTag ents = null;
         if (e.entities() != null) {
            Map<String, Object> er = Nbt.read(ClipArchive.inflate(e.entities()));
            ChunkCopy.move(er, "entities", dx, dz, dimension);
            ents = Transfers.tag(Nbt.write(er));
         }

         out.put(spot, new CompoundTag[]{Transfers.tag(Nbt.write(root)), ents});
      }

      return out;
   }

   static void delete(MinecraftServer server, ServerPlayer player, int id, ServerLevel level, ChunkSelection chunks, boolean mobsOnly) {
      level.save(null, false, false);
      UUID who = player.getUUID();
      String name = player.getScoreboardName();
      String dim = Ids.of(level.dimension());
      List<Long> asked = spots(chunks);
      ServerEdits.WORK.submit(() -> {
         List<Long> spots = new ArrayList<>();
         List<ClipArchive.Entry> old = new ArrayList<>();
         try {
            for (long s : asked) {
               int cx = ServerEdit.spotX(s);
               int cz = ServerEdit.spotZ(s);
               Optional<CompoundTag> chunk = LiveEdits.readChunk(level, cx, cz).join();
               Optional<CompoundTag> ents = LiveEdits.readEntities(level, cx, cz).exceptionally(e -> Optional.empty()).join();

               if (chunk.isEmpty() || mobsOnly && ents.isEmpty()) {
                  continue;
               }

               spots.add(s);
               old.add(new ClipArchive.Entry(cx, cz, ClipArchive.deflate(Transfers.bytes(chunk.get())), ents.isPresent() ? ClipArchive.deflate(Transfers.bytes(ents.get())) : null));
            }

            backup(level, old, name);
         } catch (IOException | RuntimeException e) {
            ServerEdits.LOG.warn("Chunk Atlas couldn't back up a live delete by {}; nothing changed", name, e);
            answer(server, who, id, false, "chunkatlas.server.backup_failed", 0, 0);
            return;
         }

         if (spots.isEmpty()) {
            answer(server, who, id, false, "chunkatlas.server.nothing", 0, 0);
            return;
         }

         byte[] actions = new byte[spots.size()];
         java.util.Arrays.fill(actions, mobsOnly ? ServerEdit.CLEAR_ENTITIES : ServerEdit.CLEAR);
         ChunkEdits.Kind kind = mobsOnly ? ChunkEdits.Kind.DELETE_MOBS : ChunkEdits.Kind.DELETE;
         ServerEdit edit = ServerEdit.liveEdit(ServerEdits.nextId(), kind, dim, name, "", 0, 0, 0, longs(spots), actions);
         server.execute(() -> {
            if (!take(server, who, id, edit, null)) {
               return;
            }

            int busy = busyNow(level, edit);
            server.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Chunk Atlas] " + name + " " + kind.name().toLowerCase(java.util.Locale.ROOT)
               + " live: " + spots.size() + " chunks, " + busy + " wait for nobody to be near, in " + dim));
            answer(server, who, id, true, "chunkatlas.server.live_done", spots.size(), busy);
         });
      });
   }

   static void paste(MinecraftServer server, UUID who, String name, AtlasNet.PasteStart req, ClipArchive.Archive archive, byte[] raw, PasteLimit limit) {
      ServerLevel level = Transfers.level(server, req.dimension());
      if (level == null) {
         answer(server, who, req.id(), false, "chunkatlas.server.no_dimension", 0, 0);
         return;
      }

      long id = ServerEdits.nextId();
      Path file = ServerEdits.home().resolve("server-clips").resolve(ServerEdits.worldName() + "-" + id + ".atlasclip");
      Map<Long, CompoundTag[]> tags;
      List<Long> spots = new ArrayList<>();
      List<Byte> actions = new ArrayList<>();
      try {
         server.submit(() -> level.save(null, false, false)).join();

         ChunkSelection placed = new ChunkSelection();
         Set<Long> wanted = new HashSet<>();
         for (ClipArchive.Entry e : archive.entries()) {
            int tx = e.cx() + req.dx();
            int tz = e.cz() + req.dz();
            if (limit.protects(tx, tz) || placed.contains(tx, tz) || req.keep() && LiveEdits.readChunk(level, tx, tz).join().isPresent()) {
               continue;
            }

            placed.add(tx, tz);
            wanted.add(ServerEdit.spot(tx, tz));
            spots.add(ServerEdit.spot(tx, tz));
            actions.add(ServerEdit.PLACE);
         }

         tags = unpack(archive, req.dx(), req.dz(), req.blend(), req.dimension(), wanted);

         if (req.blend() > 0) {
            for (long s : spots(limit.ring(placed, req.blend()))) {
               if (LiveEdits.readChunk(level, ServerEdit.spotX(s), ServerEdit.spotZ(s)).join().isPresent()) {
                  spots.add(s);
                  actions.add(ServerEdit.CLEAR);
               }
            }
         }

         if (spots.isEmpty()) {
            answer(server, who, req.id(), false, "chunkatlas.server.nothing", 0, 0);
            return;
         }

         List<ClipArchive.Entry> old = new ArrayList<>();
         for (long s : spots) {
            int cx = ServerEdit.spotX(s);
            int cz = ServerEdit.spotZ(s);
            Optional<CompoundTag> chunk = LiveEdits.readChunk(level, cx, cz).join();
            if (chunk.isPresent()) {
               Optional<CompoundTag> ents = LiveEdits.readEntities(level, cx, cz).exceptionally(e -> Optional.empty()).join();
               old.add(new ClipArchive.Entry(cx, cz, ClipArchive.deflate(Transfers.bytes(chunk.get())), ents.isPresent() ? ClipArchive.deflate(Transfers.bytes(ents.get())) : null));
            }
         }

         backup(level, old, name);
         Files.createDirectories(file.getParent());
         Files.write(file, raw);
      } catch (IOException | RuntimeException e) {
         ServerEdits.LOG.warn("Chunk Atlas couldn't prepare a live paste by {}; nothing changed", name, e);
         ServerEdits.deleteClip(file.toString());
         answer(server, who, req.id(), false, "chunkatlas.server.backup_failed", 0, 0);
         return;
      }

      byte[] a = new byte[actions.size()];
      for (int i = 0; i < a.length; i++) {
         a[i] = actions.get(i);
      }

      ServerEdit edit = ServerEdit.liveEdit(id, ChunkEdits.Kind.COPY, req.dimension(), name, file.toString(), req.dx(), req.dz(), req.blend(), longs(spots), a);
      Map<Long, CompoundTag[]> t = tags;
      server.execute(() -> {
         if (!take(server, who, req.id(), edit, t)) {
            ServerEdits.deleteClip(file.toString());
            return;
         }

         int busy = busyNow(level, edit);
         server.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "[Chunk Atlas] " + name + " pasted " + edit.spots().length + " chunks into " + req.dimension() + " live; " + busy + " wait for nobody to be near"
         ));
         answer(server, who, req.id(), true, "chunkatlas.server.paste_live", edit.spots().length, busy);
      });
   }

   private static boolean take(MinecraftServer server, UUID who, int id, ServerEdit edit, Map<Long, CompoundTag[]> tags) {
      try {
         ServerEdits.journal().add(edit);
      } catch (IOException e) {
         ServerEdits.LOG.warn("Chunk Atlas couldn't save a live edit by {}; nothing changed", edit.who());
         answer(server, who, id, false, "chunkatlas.server.save_failed", 0, 0);
         return false;
      }

      if (tags != null) {
         ready.put(edit.id(), tags);
      }

      work.add(edit);
      pause = 0;
      return true;
   }

   private static int busyNow(ServerLevel level, ServerEdit edit) {
      int n = 0;
      for (long s : edit.spots()) {
         if (LiveEdits.busy(level, ServerEdit.spotX(s), ServerEdit.spotZ(s))) {
            n++;
         }
      }

      return n;
   }

   private static void backup(ServerLevel level, List<ClipArchive.Entry> old, String who) throws IOException {
      if (old.isEmpty()) {
         return;
      }

      Path f = ServerEdits.home().resolve("server-backups").resolve(ServerEdits.worldName()).resolve(System.currentTimeMillis() + "-" + who + ".atlasclip");
      Files.createDirectories(f.getParent());
      Files.write(f, ClipArchive.write(new ClipArchive.Header(ServerEdits.worldName(), Ids.of(level.dimension()), Transfers.dataVersion(), old.size()), old));
   }

   private static List<Long> spots(ChunkSelection chunks) {
      List<Long> regions = new ArrayList<>(chunks.regions());
      regions.sort(null);
      List<Long> out = new ArrayList<>(chunks.size());
      for (long region : regions) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         long[] bits = chunks.bits(rx, rz);
         for (int i = 0; i < 1024; i++) {
            if ((bits[i >> 6] & 1L << (i & 63)) != 0L) {
               out.add(ServerEdit.spot(rx * 32 + (i & 31), rz * 32 + (i >> 5)));
            }
         }
      }

      return out;
   }

   private static long[] longs(List<Long> list) {
      long[] out = new long[list.size()];
      for (int i = 0; i < out.length; i++) {
         out[i] = list.get(i);
      }

      return out;
   }

   private static void answer(MinecraftServer server, UUID player, int id, boolean ok, String key, int chunks, int later) {
      server.execute(() -> {
         ServerPlayer p = server.getPlayerList().getPlayer(player);
         if (p != null) {
            ServerEdits.reply(p, id, ok, key, chunks, later);
         }
      });
   }
}
