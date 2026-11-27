package dev.chunkatlas.server;

import dev.chunkatlas.engine.ChunkClips;
import dev.chunkatlas.engine.ChunkCopy;
import dev.chunkatlas.engine.ChunkEdits;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.EditJournal;
import dev.chunkatlas.engine.PasteLimit;
import dev.chunkatlas.engine.RegionFile;
import dev.chunkatlas.engine.ServerEdit;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.engine.WorldLock;
import dev.chunkatlas.net.AtlasNet;
import dev.chunkatlas.port.Perms;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class ServerEdits implements ModInitializer {
   private static final List<ChunkEdits.Kind> ALLOWED = List.of(ChunkEdits.Kind.DELETE, ChunkEdits.Kind.KEEP_ONLY, ChunkEdits.Kind.DELETE_MOBS);
   static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Chunk Atlas server edits");
      t.setDaemon(true);
      return t;
   });
   static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Chunk Atlas");
   private static Path world;

   private static volatile EditJournal<ServerEdit> journal = new EditJournal<>(new EditJournal.Store() {
      @Override
      public byte[] load() {
         return null;
      }

      @Override
      public void save(byte[] data) throws IOException {
         throw new IOException("no world yet");
      }

      @Override
      public void delete() throws IOException {
         throw new IOException("no world yet");
      }
   }, ServerEdit.CODEC);
   private static long lastId;

   @Override
   public void onInitialize() {
      AtlasNet.register();
      ServerLifecycleEvents.SERVER_STARTED.register(ServerEdits::started);
      ServerLifecycleEvents.SERVER_STOPPING.register(server -> LiveQueue.stopping());
      ServerLifecycleEvents.SERVER_STOPPED.register(ServerEdits::stopped);
      ServerTickEvents.END_SERVER_TICK.register(Transfers::tick);
      ServerTickEvents.END_SERVER_TICK.register(LiveQueue::tick);
      ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(hello(handler.getPlayer())));
      ServerPlayNetworking.registerGlobalReceiver(AtlasNet.EditRequest.TYPE, (req, ctx) -> request(ctx.server(), ctx.player(), req));
      ServerPlayNetworking.registerGlobalReceiver(AtlasNet.CancelRequest.TYPE, (req, ctx) -> cancel(ctx.player(), req));
      ServerPlayNetworking.registerGlobalReceiver(AtlasNet.CopyRequest.TYPE, (req, ctx) -> Transfers.copy(ctx.server(), ctx.player(), req));
      ServerPlayNetworking.registerGlobalReceiver(AtlasNet.PasteStart.TYPE, (req, ctx) -> Transfers.pasteStart(ctx.server(), ctx.player(), req));
      ServerPlayNetworking.registerGlobalReceiver(AtlasNet.UpPiece.TYPE, (req, ctx) -> Transfers.piece(ctx.server(), ctx.player(), req));
      ServerPlayNetworking.registerGlobalReceiver(AtlasNet.LoadsRequest.TYPE, (req, ctx) -> Loads.request(ctx.server(), ctx.player(), req));
      ServerPlayNetworking.registerGlobalReceiver(AtlasNet.ForceRequest.TYPE, (req, ctx) -> Loads.force(ctx.server(), ctx.player(), req));
      Generations.register();
      Tasks.register();
      CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
         Commands.literal("chunkatlas")
            .requires(Perms::admin)
            .then(Commands.literal("live")
               .then(Commands.literal("on").executes(c -> live(c.getSource().getServer(), true, c.getSource())))
               .then(Commands.literal("off").executes(c -> live(c.getSource().getServer(), false, c.getSource()))))
            .then(Commands.literal("status").executes(c -> {
               int queued = waitingCount();
               boolean on = live();
               c.getSource().sendSuccess(() -> Component.literal("Chunk Atlas: edits are made " + (on ? "right away (live)" : "when the server stops")
                  + "; " + queued + " waiting for a restart, " + LiveQueue.left() + " live changes still to make"), false);
               return 1;
            }))
      ));
   }

   static boolean live() {
      return ServerSettings.live() && LiveEdits.available();
   }

   private static int live(MinecraftServer server, boolean on, net.minecraft.commands.CommandSourceStack src) {
      if (on && !LiveEdits.available()) {
         src.sendFailure(Component.literal("Chunk Atlas: live edits don't work on this game version; edits stay for a restart"));
         return 0;
      }

      ServerSettings.live(on);
      src.sendSuccess(() -> Component.literal("Chunk Atlas: edits are now made " + (on ? "right away, as nobody is near each chunk" : "when the server stops")), true);
      helloAll(server);
      return 1;
   }

   private static void helloAll(MinecraftServer server) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         if (ServerPlayNetworking.canSend(p, AtlasNet.Hello.TYPE)) {
            ServerPlayNetworking.send(p, hello(p));
         }
      }
   }

   private static AtlasNet.Hello hello(ServerPlayer player) {
      return new AtlasNet.Hello(AtlasNet.VERSION, mayEdit(player), waitingCount(), live());
   }

   static boolean mayEdit(ServerPlayer player) {
      return Perms.admin(player);
   }

   static Path home() {
      return FabricLoader.getInstance().getGameDir().resolve("chunkatlas");
   }

   static String worldName() {
      return world == null || world.getFileName() == null ? "world" : world.getFileName().toString();
   }

   static EditJournal<ServerEdit> journal() {
      return journal;
   }

   static int waitingCount() {
      return journal.count(e -> !e.live());
   }

   static synchronized long nextId() {
      long id = Math.max(System.currentTimeMillis(), lastId + 1);
      for (ServerEdit e : journal.items()) {
         id = Math.max(id, e.id() + 1);
      }

      lastId = id;
      return id;
   }

   private static Path journalFile() {
      return home().resolve("server-edits").resolve(worldName() + ".journal");
   }

   private static Path oldQueueFile() {
      return home().resolve("server-edits").resolve(worldName() + ".bin");
   }

   private static void started(MinecraftServer server) {
      world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
      Path f = journalFile();
      EditJournal<ServerEdit> j = new EditJournal<>(EditJournal.file(f), ServerEdit.CODEC);
      try {
         j.load();
      } catch (IOException | RuntimeException e) {
         Path aside = f.resolveSibling(f.getFileName() + ".unreadable-" + System.currentTimeMillis());
         LOG.warn("Chunk Atlas couldn't read its list of waiting edits; it's kept as {} and the list starts empty", aside.getFileName());
         try {
            Files.move(f, aside, StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException ignored) {
         }
      }

      Path old = oldQueueFile();
      if (Files.isRegularFile(old)) {
         List<ServerEdit> moved = readOld(old);
         try {
            if (moved == null) {
               throw new IOException("unreadable");
            }

            j.update(list -> {
               list.addAll(moved);
               return list;
            });
            Files.delete(old);
         } catch (IOException e) {
            LOG.warn("Chunk Atlas couldn't move the waiting edits to its new list; they stay in {}", old.getFileName());
         }
      }

      journal = j;
      LiveQueue.started(server, j);
   }

   private static void request(MinecraftServer server, ServerPlayer player, AtlasNet.EditRequest req) {
      if (world == null) {
         return;
      }

      if (!mayEdit(player)) {
         reply(player, req.id(), false, "chunkatlas.server.not_allowed", 0, 0);
         return;
      }

      ChunkEdits.Kind kind;
      try {
         kind = ChunkEdits.Kind.valueOf(req.kind());
      } catch (IllegalArgumentException e) {
         kind = null;
      }

      if (kind == null || !ALLOWED.contains(kind) || req.chunks().isEmpty()) {
         reply(player, req.id(), false, "chunkatlas.server.not_supported", 0, 0);
         return;
      }

      boolean now = kind != ChunkEdits.Kind.KEEP_ONLY && live();
      if (req.live() != now) {
         reply(player, req.id(), false, "chunkatlas.server.mode_changed", 0, 0);
         helloAll(server);
         return;
      }

      ServerLevel level = Transfers.level(server, req.dimension());
      if (now) {
         if (level == null) {
            reply(player, req.id(), false, "chunkatlas.server.no_dimension", 0, 0);
         } else {
            LiveQueue.delete(server, player, req.id(), level, req.chunks(), kind == ChunkEdits.Kind.DELETE_MOBS);
         }

         return;
      }

      ChunkEdits.Kind k = kind;
      String who = player.getScoreboardName();
      WORK.submit(() -> {
         int chunks;
         String key;
         try {
            ChunkEdits.Plan plan = plan(k, req.dimension(), req.chunks());
            chunks = plan == null ? -1 : plan.chunks();
            key = plan == null ? "chunkatlas.server.no_dimension" : plan.isEmpty() ? "chunkatlas.server.nothing" : "chunkatlas.server.queued";
         } catch (RuntimeException e) {
            chunks = -1;
            key = "chunkatlas.server.unreadable";
         }

         int n = chunks;
         String planned = key;
         server.execute(() -> {
            boolean ok = n > 0;
            String said = planned;
            if (ok) {
               try {
                  journal.add(ServerEdit.atRestart(nextId(), k, req.dimension(), who, req.chunks()));
               } catch (IOException e) {
                  LOG.warn("Chunk Atlas couldn't save an edit asked for by {}; it's not queued", who);
                  ok = false;
                  said = "chunkatlas.server.save_failed";
               }
            }

            if (ok) {
               server.sendSystemMessage(Component.literal(
                  "[Chunk Atlas] " + who + " queued " + k.name().toLowerCase(java.util.Locale.ROOT) + " of " + n + " chunks in " + req.dimension() + "; made when the server stops"
               ));
            }

            reply(player, req.id(), ok, said, Math.max(0, n), 0);
         });
      });
   }

   static int queuePaste(MinecraftServer server, String who, AtlasNet.PasteStart req, Path clip, ChunkSelection chunks, PasteLimit limit) throws IOException {
      journal.add(ServerEdit.pasteAtRestart(nextId(), req.dimension(), who, chunks, clip.toString(), req.dx(), req.dz(), req.keep(), req.blend(), limit));
      server.execute(() -> server.sendSystemMessage(Component.literal(
         "[Chunk Atlas] " + who + " queued a paste of " + chunks.size() + " chunks into " + req.dimension() + "; made when the server stops"
      )));
      return waitingCount();
   }

   private static void cancel(ServerPlayer player, AtlasNet.CancelRequest req) {
      if (!mayEdit(player) || world == null) {
         reply(player, req.id(), false, "chunkatlas.server.not_allowed", 0, 0);
         return;
      }

      List<ServerEdit> dropped;
      try {
         dropped = journal.remove(e -> !e.live());
      } catch (IOException e) {
         LOG.warn("Chunk Atlas couldn't save the cancelled list; the waiting edits stay");
         reply(player, req.id(), false, "chunkatlas.server.save_failed", 0, 0);
         return;
      }

      for (ServerEdit e : dropped) {
         deleteClip(e.clip());
      }

      reply(player, req.id(), true, "chunkatlas.server.cancelled", 0, 0);
   }

   static void reply(ServerPlayer player, int id, boolean ok, String key, int chunks, int later) {
      if (ServerPlayNetworking.canSend(player, AtlasNet.Reply.TYPE)) {
         ServerPlayNetworking.send(player, new AtlasNet.Reply(id, ok, key, chunks, waitingCount(), later));
      }
   }

   static void tellOps(MinecraftServer server, String text) {
      LOG.warn(text);
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         if (mayEdit(p)) {
            p.sendSystemMessage(Component.literal("[Chunk Atlas] " + text));
         }
      }
   }

   private static WorldLayout.Dimension dimension(String id) {
      return WorldLayout.read(world).dimensions().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
   }

   private static ChunkEdits.Plan plan(ChunkEdits.Kind kind, String dimension, ChunkSelection chunks) {
      WorldLayout.Dimension d = dimension(dimension);
      if (d == null) {
         return null;
      }

      return switch (kind) {
         case DELETE -> ChunkEdits.planDelete(world, d, chunks);
         case KEEP_ONLY -> ChunkEdits.planKeepOnly(world, d, chunks);
         case DELETE_MOBS -> ChunkEdits.planDeleteMobs(world, d, chunks);
         default -> null;
      };
   }

   private static ChunkEdits.Plan planPaste(ServerEdit w) {
      WorldLayout.Dimension d = dimension(w.dimension());
      Path clip = Path.of(w.clip());
      ChunkClips.Clip info = ChunkClips.info(clip);
      if (d == null || info == null) {
         return null;
      }

      ChunkSelection chunks = w.keep() ? onlyEmpty(w.chunks(), w.dx(), w.dz(), d) : w.chunks();
      ChunkSelection protect = w.limit() == null ? new ChunkSelection() : w.limit().protect();
      return ChunkCopy.plan(new ChunkCopy.Request(
         clip, info.sourceWorld(), ChunkClips.dimension(clip), chunks, world, worldName(), d, w.dx(), w.dz(), protect, info.dataVersion(),
         Transfers.dataVersion()
      ).blended(w.blend()).limited(w.limit()));
   }

   private static ChunkSelection onlyEmpty(ChunkSelection chunks, int dx, int dz, WorldLayout.Dimension d) {
      ChunkSelection out = new ChunkSelection();
      java.util.Map<Long, RegionFile.Header> headers = new java.util.HashMap<>();
      for (long region : chunks.regions()) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         long[] bits = chunks.bits(rx, rz);
         for (int i = 0; i < 1024; i++) {
            if ((bits[i >> 6] & 1L << (i & 63)) == 0L) {
               continue;
            }

            int tx = rx * 32 + (i & 31) + dx;
            int tz = rz * 32 + (i >> 5) + dz;
            RegionFile.Header h = headers.computeIfAbsent(WorldLayout.key(tx >> 5, tz >> 5), k -> {
               try {
                  Path f = d.region().resolve(RegionFile.name(tx >> 5, tz >> 5));
                  return Files.isRegularFile(f) ? RegionFile.header(f) : null;
               } catch (IOException e) {
                  return null;
               }
            });
            if (h == null || !h.present(ChunkSelection.index(tx, tz))) {
               out.add(tx - dx, tz - dz);
            }
         }
      }

      return out;
   }

   private static void stopped(MinecraftServer server) {
      if (world == null) {
         return;
      }

      int waiting = waitingCount();
      if (waiting == 0) {
         return;
      }

      int made = journal.drain(e -> !e.live(), ServerEdits::make);
      if (made < waiting) {
         LOG.warn("Chunk Atlas made {} of {} waiting edits; the rest wait for next time", made, waiting);
      } else {
         LOG.info("Chunk Atlas made {} waiting edits", made);
      }
   }

   private static boolean make(ServerEdit w) {
      ChunkEdits.Plan plan;
      try {
         plan = w.isPaste() ? planPaste(w) : plan(w.kind(), w.dimension(), w.chunks());
      } catch (RuntimeException e) {
         plan = null;
      }

      if (plan == null || plan.isEmpty() || plan.copy() != null && plan.copy().refused() != null) {
         LOG.info("Skipped an edit by {}: nothing to change in {}", w.who(), w.dimension());
         deleteClip(w.clip());
         return true;
      }

      ChunkEdits.Plan p = plan;
      ChunkEdits.Result r;
      try {
         r = WorldLock.with(world, held -> {
            if (!w.isPaste()) {
               return ChunkEdits.apply(p, new ChunkEdits.Context(home(), held));
            }

            return WorldLock.with(Path.of(w.clip()), source -> ChunkEdits.apply(p, new ChunkEdits.Context(home(), held).withSource(source))).orElse(null);
         }).orElse(null);
      } catch (RuntimeException e) {
         r = null;
      }

      if (r == null || !r.ok()) {
         LOG.warn("Couldn't make an edit asked for by {} in {}; it and those after it stay waiting for next time", w.who(), w.dimension());
         return false;
      }

      deleteClip(w.clip());
      LOG.info("Made {} of {} chunks in {}, asked for by {}", w.kind().name().toLowerCase(java.util.Locale.ROOT), p.chunks(), w.dimension(), w.who());
      return true;
   }

   static void deleteClip(String path) {
      if (path == null || path.isEmpty()) {
         return;
      }

      Path clip = Path.of(path);
      if (!clip.toAbsolutePath().normalize().startsWith(home().resolve("server-clips").toAbsolutePath().normalize())) {
         return;
      }

      if (!Files.exists(clip)) {
         return;
      }

      try (Stream<Path> walk = Files.walk(clip)) {
         for (Path f : walk.sorted(Comparator.reverseOrder()).toList()) {
            Files.deleteIfExists(f);
         }
      } catch (IOException ignored) {
      }
   }

   private static final int LIST_V2 = -2;

   private static List<ServerEdit> readOld(Path f) {
      List<ServerEdit> out = new ArrayList<>();
      try (DataInputStream in = new DataInputStream(Files.newInputStream(f))) {
         int first = in.readInt();
         boolean v2 = first == LIST_V2;
         int n = v2 ? in.readInt() : first;
         for (int i = 0; i < n; i++) {
            ChunkEdits.Kind kind = ChunkEdits.Kind.valueOf(in.readUTF());
            String dim = in.readUTF();
            String who = in.readUTF();
            String clip = v2 ? in.readUTF() : "";
            int dx = v2 ? in.readInt() : 0;
            int dz = v2 ? in.readInt() : 0;
            boolean keep = v2 && in.readBoolean();
            int blend = v2 ? in.readInt() : 0;
            int regions = in.readInt();
            ChunkSelection sel = new ChunkSelection();
            for (int r = 0; r < regions; r++) {
               int rx = in.readInt();
               int rz = in.readInt();
               long[] mask = new long[ChunkSelection.WORDS];
               for (int w = 0; w < mask.length; w++) {
                  mask[w] = in.readLong();
               }

               sel.add(rx, rz, mask);
            }

            long id = nextId();
            out.add(kind == ChunkEdits.Kind.COPY
               ? ServerEdit.pasteAtRestart(id, dim, who, sel, clip, dx, dz, keep, blend, null)
               : ServerEdit.atRestart(id, kind, dim, who, sel));
         }
      } catch (IOException | RuntimeException e) {
         return null;
      }

      return out;
   }
}
