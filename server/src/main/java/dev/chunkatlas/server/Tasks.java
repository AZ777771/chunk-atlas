package dev.chunkatlas.server;

import dev.chunkatlas.engine.Borders;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.ChunkTasks;
import dev.chunkatlas.net.AtlasNet;
import dev.chunkatlas.net.TaskWire;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class Tasks {
   private static final int REPORT_TICKS = 10;
   private static int ticks;
   private static int toldTasks = -1;
   private static int toldBorders = -1;

   private static final java.util.Set<java.util.UUID> joined = new java.util.HashSet<>();

   private Tasks() {
   }

   static void register() {
      ServerLifecycleEvents.SERVER_STARTED.register(server -> {
         Borders.options(ServerSettings.borderOptions());
         ChunkTasks.started(server, true);
         Borders.started(server);
      });
      ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
         ChunkTasks.stopping(server);
         Borders.stopping(server);
      });
      ServerTickEvents.END_SERVER_TICK.register(Tasks::tick);
      ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> joined.add(handler.getPlayer().getUUID())));
      ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> joined.remove(handler.getPlayer().getUUID())));
      ServerPlayNetworking.registerGlobalReceiver(AtlasNet.TaskCommand.TYPE, (c, ctx) -> command(ctx.server(), ctx.player(), c));
   }

   private static void tick(MinecraftServer server) {
      ChunkTasks tasks = ChunkTasks.of(server);
      Borders borders = Borders.of(server);
      if (tasks != null) {
         tasks.tick();
      }

      if (borders != null) {
         borders.tick();
      }

      if (++ticks % REPORT_TICKS != 0) {
         return;
      }

      joined.removeIf(id -> {
         ServerPlayer p = server.getPlayerList().getPlayer(id);
         if (p == null) {
            return true;
         }

         if (!ServerPlayNetworking.canSend(p, AtlasNet.TasksUpdate.TYPE)) {
            return ticks % 1200 == 0;
         }

         tell(server, p, true, Map.of());
         return true;
      });

      int tv = tasks == null ? 0 : tasks.version();
      int bv = borders == null ? 0 : borders.version();
      boolean tasksChanged = tv != toldTasks;
      boolean bordersChanged = bv != toldBorders;
      if (!tasksChanged && !bordersChanged) {
         return;
      }

      toldTasks = tv;
      toldBorders = bv;
      Map<Integer, ChunkSelection> fresh = TaskWire.takeFresh(tasks);
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         boolean admin = ServerEdits.mayEdit(p);
         if (admin && tasksChanged || bordersChanged) {
            tell(server, p, false, fresh);
         }
      }
   }

   private static void tell(MinecraftServer server, ServerPlayer p, boolean full, Map<Integer, ChunkSelection> fresh) {
      if (ServerPlayNetworking.canSend(p, AtlasNet.TasksUpdate.TYPE)) {
         ServerPlayNetworking.send(p, TaskWire.update(ChunkTasks.of(server), Borders.of(server), ServerEdits.mayEdit(p), full, fresh));
      }
   }

   private static void command(MinecraftServer server, ServerPlayer player, AtlasNet.TaskCommand c) {
      if (!ServerEdits.mayEdit(player)) {
         ServerEdits.reply(player, c.id(), false, "chunkatlas.server.not_allowed", 0, 0);
         return;
      }

      String why = TaskWire.apply(ChunkTasks.of(server), Borders.of(server), c);
      if (why != null) {
         ServerEdits.reply(player, c.id(), false, why, 0, 0);
      } else if (c.action() == AtlasNet.TaskCommand.ADD) {
         server.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Chunk Atlas] " + player.getScoreboardName() + " added a chunk task in " + c.dimension()));
      }

      toldTasks = -1;
      toldBorders = -1;
   }
}
