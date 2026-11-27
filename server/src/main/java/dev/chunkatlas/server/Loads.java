package dev.chunkatlas.server;

import dev.chunkatlas.engine.Background;
import dev.chunkatlas.engine.ChunkLoads;
import dev.chunkatlas.net.AtlasNet;
import dev.chunkatlas.net.ServerLoads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class Loads {
   private static final long EVERY = 450L;

   private static final int MOST = 16384;

   private static final int MOST_FORCED = 256;
   private static final ExecutorService WORK = Executors.newSingleThreadExecutor(Background.named("Chunk Atlas server loaded chunks"));
   private static final Map<String, Dim> dims = new HashMap<>();

   private static final class Dim {
      ChunkLoads.Snapshot last;
      long at;
      boolean working;
      final List<Waiter> waiting = new ArrayList<>();
   }

   private record Waiter(UUID player, int id) {
   }

   private Loads() {
   }

   static void request(MinecraftServer server, ServerPlayer player, AtlasNet.LoadsRequest req) {
      if (!ServerEdits.mayEdit(player)) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.not_allowed", 0, 0);
         return;
      }

      ServerLevel level = ServerLoads.level(server, req.dimension());
      if (level == null) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.no_dimension", 0, 0);
         return;
      }

      Dim d = dims.computeIfAbsent(req.dimension(), k -> new Dim());
      long now = System.currentTimeMillis();
      if (d.last != null && now - d.at < EVERY) {
         send(player, req.id(), d.last);
         return;
      }

      d.waiting.add(new Waiter(player.getUUID(), req.id()));
      if (d.working) {
         return;
      }

      ChunkLoads.Raw raw = ServerLoads.capture(level);
      if (raw == null) {
         answerAll(server, d, null);
         return;
      }

      d.working = true;
      String dim = req.dimension();
      WORK.execute(() -> {
         ChunkLoads.Snapshot s;
         try {
            s = ChunkLoads.build(dim, raw, MOST);
         } catch (RuntimeException e) {
            s = null;
         }

         ChunkLoads.Snapshot built = s;
         server.execute(() -> {
            d.working = false;
            if (built != null) {
               d.last = built;
               d.at = now;
            }

            answerAll(server, d, built);
         });
      });
   }

   private static void answerAll(MinecraftServer server, Dim d, ChunkLoads.Snapshot s) {
      for (Waiter w : d.waiting) {
         ServerPlayer p = server.getPlayerList().getPlayer(w.player());
         if (p != null && s == null) {
            ServerEdits.reply(p, w.id(), false, "chunkatlas.server.unreadable", 0, 0);
         } else if (p != null) {
            send(p, w.id(), s);
         }
      }

      d.waiting.clear();
   }

   private static void send(ServerPlayer player, int id, ChunkLoads.Snapshot s) {
      if (ServerPlayNetworking.canSend(player, AtlasNet.LoadsReply.TYPE)) {
         ServerPlayNetworking.send(player, new AtlasNet.LoadsReply(id, s));
      }
   }

   static void force(MinecraftServer server, ServerPlayer player, AtlasNet.ForceRequest req) {
      if (!ServerEdits.mayEdit(player)) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.not_allowed", 0, 0);
         return;
      }

      ServerLevel level = ServerLoads.level(server, req.dimension());
      if (level == null) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.no_dimension", 0, 0);
         return;
      }

      if (req.on() && req.chunks().size() > MOST_FORCED) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.too_many", MOST_FORCED, 0);
         return;
      }

      int changed = ServerLoads.force(level, req.chunks(), req.on());
      server.sendSystemMessage(Component.literal("[Chunk Atlas] " + player.getScoreboardName() + (req.on() ? " force-loaded " : " stopped force-loading ")
         + changed + " chunks in " + req.dimension()));
      ServerEdits.reply(player, req.id(), true, "chunkatlas.server.forced", changed, 0);
   }
}
