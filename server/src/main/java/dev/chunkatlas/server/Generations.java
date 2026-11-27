package dev.chunkatlas.server;

import dev.chunkatlas.engine.ChunkGeneration;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.ChunkTasks;
import dev.chunkatlas.engine.TaskBook;
import dev.chunkatlas.net.AtlasNet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class Generations {
   private static final long SEND_NANOS = 500_000_000L;

   private static final Map<UUID, Incoming> incoming = new HashMap<>();

   private static final Map<Integer, Run> running = new HashMap<>();
   private static long sentAt;

   private Generations() {
   }

   private static final class Incoming {
      final int id;
      final ChunkSelection chunks = new ChunkSelection();

      Incoming(int id) {
         this.id = id;
      }
   }

   static void register() {
      ServerPlayNetworking.registerGlobalReceiver(AtlasNet.GenerateRequest.TYPE, (req, ctx) -> request(ctx.server(), ctx.player(), req));
      ServerPlayNetworking.registerGlobalReceiver(AtlasNet.GenerateControl.TYPE, (req, ctx) -> control(ctx.server(), ctx.player(), req));
      ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> incoming.remove(handler.getPlayer().getUUID()));
      net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Generations::tick);
   }

   private static void request(MinecraftServer server, ServerPlayer player, AtlasNet.GenerateRequest req) {
      UUID who = player.getUUID();
      if (!ServerEdits.mayEdit(player)) {
         incoming.remove(who);
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.not_allowed", 0, 0);
         return;
      }

      Incoming in = incoming.get(who);
      if (in == null || in.id != req.id()) {
         in = new Incoming(req.id());
         incoming.put(who, in);
      }

      for (long region : req.chunks().regions()) {
         in.chunks.add((int)(region >> 32), (int)region, req.chunks().bits((int)(region >> 32), (int)region));
      }

      int max = ServerSettings.generateMaxChunks();
      if (in.chunks.size() > max) {
         incoming.remove(who);
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.too_many", max, 0);
         return;
      }

      if (!req.last()) {
         return;
      }

      incoming.remove(who);
      ServerLevel level = Transfers.level(server, req.dimension());
      if (level == null || in.chunks.isEmpty()) {
         ServerEdits.reply(player, req.id(), false, "chunkatlas.server.no_dimension", 0, 0);
         return;
      }

      int id = req.id();
      ChunkTasks tasks = ChunkTasks.of(server);
      String why = tasks == null ? "chunkatlas.server.generate_busy" : tasks.refusal(req.dimension(), in.chunks.size());
      TaskBook.Task task = why == null ? tasks.addPicked(req.dimension(), in.chunks, req.speed(), req.finishPartial()) : null;
      if (task == null) {
         ServerEdits.reply(player, id, false, why == null ? "chunkatlas.server.generate_busy" : why, 0, 0);
         return;
      }

      running.put(id, new Run(who, task.id));
      ServerEdits.reply(player, id, true, "chunkatlas.server.generate_started", in.chunks.size(), 0);
      server.sendSystemMessage(Component.literal("[Chunk Atlas] " + player.getScoreboardName() + " is generating " + in.chunks.size() + " chunks in " + req.dimension()));
   }

   private static void control(MinecraftServer server, ServerPlayer player, AtlasNet.GenerateControl req) {
      Run run = running.get(req.id());
      ChunkTasks tasks = ChunkTasks.of(server);
      if (run == null || tasks == null || !run.player.equals(player.getUUID()) && !ServerEdits.mayEdit(player)) {
         return;
      }

      switch (req.action()) {
         case AtlasNet.GenerateControl.PAUSE -> tasks.pause(run.task, true);
         case AtlasNet.GenerateControl.RESUME -> tasks.pause(run.task, false);
         default -> tasks.cancel(run.task);
      }
   }

   private static void tick(MinecraftServer server) {
      long now = System.nanoTime();
      if (running.isEmpty() || now - sentAt < SEND_NANOS) {
         return;
      }

      sentAt = now;
      ChunkTasks tasks = ChunkTasks.of(server);
      running.entrySet().removeIf(e -> {
         Run run = e.getValue();
         ChunkTasks.View v = null;
         if (tasks != null) {
            for (ChunkTasks.View x : tasks.views()) {
               if (x.id() == run.task) {
                  v = x;
               }
            }
         }

         ChunkGeneration.State state = v == null ? ChunkGeneration.State.STOPPED : switch (v.state()) {
            case PAUSED -> ChunkGeneration.State.PAUSED;
            case DONE -> ChunkGeneration.State.DONE;
            case CANCELLED -> ChunkGeneration.State.STOPPED;
            default -> ChunkGeneration.State.RUNNING;
         };
         int done = v == null ? 0 : v.generated();
         int leftAlone = v == null ? 0 : Math.max(0, v.done() - v.generated());
         int total = v == null ? 0 : v.total() - leftAlone;
         ChunkSelection dealt = tasks == null ? new ChunkSelection() : tasks.takeFresh(run.task, ChunkTasks.FOR_GENERATE);
         ServerPlayer p = server.getPlayerList().getPlayer(run.player);
         if (p != null && ServerPlayNetworking.canSend(p, AtlasNet.GenerateProgress.TYPE)) {
            ServerPlayNetworking.send(p, new AtlasNet.GenerateProgress(e.getKey(), state.ordinal(), done, total, leftAlone, v == null ? -1 : v.secondsLeft(), dealt));
         }

         return state.ended();
      });
   }

   private record Run(UUID player, int task) {
   }
}
