package dev.chunkatlas.server;

import dev.chunkatlas.engine.ChunkCopy;
import dev.chunkatlas.engine.ClipArchive;
import dev.chunkatlas.engine.PasteLimit;
import dev.chunkatlas.net.AtlasNet;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Version;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

final class Transfers {
   private static final class Down {
      final int id;
      final byte[] data;
      int at;
      int seq;

      Down(int id, byte[] data) {
         this.id = id;
         this.data = data;
      }
   }

   private static final class Up {
      final AtlasNet.PasteStart start;
      final ByteArrayOutputStream data;
      int seq;

      Up(AtlasNet.PasteStart start) {
         this.start = start;
         this.data = new ByteArrayOutputStream(Math.min(start.bytes(), 1 << 20));
      }
   }

   private static final Map<UUID, Down> downs = new HashMap<>();
   private static final Map<UUID, Up> ups = new HashMap<>();

   private static final org.slf4j.Logger LOG = ServerEdits.LOG;

   private Transfers() {
   }

   static ServerLevel level(MinecraftServer server, String dimension) {
      ResourceKey<Level> id = Ids.dimension(dimension);
      return id == null ? null : server.getLevel(id);
   }

   static int dataVersion() {
      return Version.dataVersion();
   }

   static void copy(MinecraftServer server, ServerPlayer player, AtlasNet.CopyRequest req) {
      if (!ServerEdits.mayEdit(player)) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.not_allowed", 0, 0);
         return;
      }

      ServerLevel level = level(server, req.dimension());
      int n = req.chunks().size();
      if (level == null || n == 0) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.no_dimension", 0, 0);
         return;
      }

      if (n > ServerSettings.maxChunks()) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.too_many", ServerSettings.maxChunks(), 0);
         return;
      }

      UUID who = player.getUUID();
      List<int[]> spots = new ArrayList<>();
      List<CompletableFuture<Optional<CompoundTag>>> chunks = new ArrayList<>();
      List<CompletableFuture<Optional<CompoundTag>>> entities = new ArrayList<>();
      List<CompletableFuture<Optional<CompoundTag>>> poi = new ArrayList<>();
      try {
         level.save(null, false, false);
         for (long region : req.chunks().regions()) {
            int rx = (int)(region >> 32);
            int rz = (int)region;
            long[] bits = req.chunks().bits(rx, rz);
            for (int i = 0; i < 1024; i++) {
               if ((bits[i >> 6] & 1L << (i & 63)) != 0L) {
                  int cx = rx * 32 + (i & 31);
                  int cz = rz * 32 + (i >> 5);
                  spots.add(new int[]{cx, cz});
                  chunks.add(LiveEdits.readChunk(level, cx, cz));
                  entities.add(LiveEdits.readEntities(level, cx, cz).exceptionally(e -> Optional.empty()));
                  poi.add(LiveEdits.readPoi(level, cx, cz).exceptionally(e -> Optional.empty()));
               }
            }
         }
      } catch (RuntimeException e) {
         LOG.warn("Chunk Atlas: a copy couldn't start", e);
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.unreadable", 0, 0);
         return;
      }

      String source = ServerEdits.worldName();
      LOG.info("Chunk Atlas: {} asked for a copy of {} chunks in {}", player.getScoreboardName(), spots.size(), req.dimension());
      List<CompletableFuture<?>> all = new ArrayList<>(chunks);
      all.addAll(entities);
      all.addAll(poi);

      CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).handleAsync((v, failed) -> {
         byte[] packed = null;
         String problem = null;
         if (failed != null) {
            LOG.warn("Chunk Atlas: a copy couldn't read the chunks", failed);
            problem = "chunkatlas.server.unreadable";
         } else {
            try {
               List<ClipArchive.Entry> out = new ArrayList<>();
               long bytes = 0;
               for (int i = 0; i < spots.size(); i++) {
                  Optional<CompoundTag> chunk = chunks.get(i).join();
                  if (chunk.isEmpty()) {
                     continue;
                  }

                  Optional<CompoundTag> ents = entities.get(i).join();
                  Optional<CompoundTag> points = poi.get(i).join();
                  byte[] c = ClipArchive.deflate(bytes(chunk.get()));
                  byte[] e = ents.isPresent() ? ClipArchive.deflate(bytes(ents.get())) : null;
                  byte[] p = points.isPresent() ? ClipArchive.deflate(bytes(points.get())) : null;
                  bytes += c.length + (e == null ? 0 : e.length) + (p == null ? 0 : p.length);
                  if (bytes > ServerSettings.maxBytes()) {
                     problem = "chunkatlas.server.too_big";
                     break;
                  }

                  out.add(new ClipArchive.Entry(spots.get(i)[0], spots.get(i)[1], c, e, p));
               }

               if (problem == null) {
                  packed = out.isEmpty() ? null : ClipArchive.write(new ClipArchive.Header(source, req.dimension(), dataVersion(), out.size()), out);
                  problem = out.isEmpty() ? "chunkatlas.server.nothing" : null;
               }
            } catch (IOException | RuntimeException e) {
               LOG.warn("Chunk Atlas: a copy couldn't be packed", e);
               problem = "chunkatlas.server.unreadable";
            }
         }

         byte[] data = packed;
         String why = problem;
         server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(who);
            if (p == null) {
               return;
            }

            if (why != null) {
               LOG.info("Chunk Atlas: copy for {} stopped: {}", p.getScoreboardName(), why);
               ServerEdits.reply(p, req.id(), false, why, 0, 0);
            } else {
               LOG.info("Chunk Atlas: sending {} bytes to {}", data.length, p.getScoreboardName());
               downs.put(who, new Down(req.id(), data));
            }
         });
         return null;
      }, ServerEdits.WORK);
   }

   static byte[] bytes(CompoundTag tag) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
      NbtIo.write(tag, new DataOutputStream(out));
      return out.toByteArray();
   }

   static CompoundTag tag(byte[] raw) throws IOException {
      return NbtIo.read(new DataInputStream(new ByteArrayInputStream(raw)), NbtAccounter.create(ClipArchive.MAX_CHUNK));
   }

   static void pasteStart(MinecraftServer server, ServerPlayer player, AtlasNet.PasteStart req) {
      if (!ServerEdits.mayEdit(player)) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.not_allowed", 0, 0);
         return;
      }

      if (req.bytes() <= 0 || req.bytes() > ServerSettings.maxBytes() || req.blend() < 0 || req.blend() > ChunkCopy.MAX_BLEND
         || Math.abs(req.dx()) > ChunkCopy.MAX_OFFSET || Math.abs(req.dz()) > ChunkCopy.MAX_OFFSET) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.too_big", 0, 0);
         return;
      }

      if (level(server, req.dimension()) == null) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.no_dimension", 0, 0);
         return;
      }

      ups.put(player.getUUID(), new Up(req));
   }

   static void piece(MinecraftServer server, ServerPlayer player, AtlasNet.UpPiece piece) {
      Up up = ups.get(player.getUUID());
      if (up == null || piece.id() != up.start.id()) {
         return;
      }

      if (piece.seq() != up.seq++ || up.data.size() + piece.data().length > up.start.bytes()) {
         ups.remove(player.getUUID());
         ServerEdits.reply(player, piece.id(), false, "chunkatlas.server.cut_short", 0, 0);
         return;
      }

      up.data.writeBytes(piece.data());
      if (up.data.size() == up.start.bytes()) {
         ups.remove(player.getUUID());
         byte[] data = up.data.toByteArray();
         String who = player.getScoreboardName();
         UUID id = player.getUUID();
         ServerEdits.WORK.submit(() -> landed(server, id, who, up.start, data));
      }
   }

   private static void landed(MinecraftServer server, UUID player, String who, AtlasNet.PasteStart req, byte[] data) {
      PasteLimit.Upload upload;
      ClipArchive.Archive archive;
      try {
         upload = PasteLimit.unwrap(data);
         archive = ClipArchive.read(upload.archive(), ServerSettings.maxChunks());
      } catch (IOException | RuntimeException e) {
         answer(server, player, req.id(), false, "chunkatlas.server.unreadable", 0, 0);
         return;
      }

      if (archive.header().dataVersion() > dataVersion()) {
         answer(server, player, req.id(), false, "chunkatlas.server.newer", 0, 0);
         return;
      }

      if (ServerEdits.live()) {
         LiveQueue.paste(server, player, who, req, archive, upload.archive(), upload.limit());
         return;
      }

      Path folder = ServerEdits.home().resolve("server-clips").resolve(ServerEdits.worldName() + "-" + ServerEdits.nextId());
      try {
         Files.createDirectories(folder.resolve("region"));
         ClipArchive.toClip(archive, folder, "upload by " + who);
         int queued = ServerEdits.queuePaste(server, who, req, folder, dev.chunkatlas.engine.ChunkClips.chunks(folder), upload.limit());
         answer(server, player, req.id(), true, "chunkatlas.server.paste_queued", archive.entries().size(), queued);
      } catch (IOException | RuntimeException e) {
         LOG.warn("Chunk Atlas couldn't keep a paste by {} for the restart; nothing is queued", who);
         ServerEdits.deleteClip(folder.toString());
         answer(server, player, req.id(), false, "chunkatlas.server.save_failed", 0, 0);
      }
   }

   private static void answer(MinecraftServer server, UUID player, int id, boolean ok, String key, int chunks, int later) {
      server.execute(() -> {
         ServerPlayer p = server.getPlayerList().getPlayer(player);
         if (p != null) {
            ServerEdits.reply(p, id, ok, key, chunks, later);
         }
      });
   }

   static void tick(MinecraftServer server) {
      downs.entrySet().removeIf(e -> {
         ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
         Down d = e.getValue();
         if (p == null) {
            return true;
         }

         for (int k = 0; k < 2 && d.at < d.data.length; k++) {
            int n = Math.min(AtlasNet.DOWN_PIECE, d.data.length - d.at);
            byte[] part = java.util.Arrays.copyOfRange(d.data, d.at, d.at + n);
            ServerPlayNetworking.send(p, new AtlasNet.DownPiece(d.id, d.seq++, d.data.length, part));
            d.at += n;
         }

         return d.at >= d.data.length;
      });
      ups.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
   }
}
