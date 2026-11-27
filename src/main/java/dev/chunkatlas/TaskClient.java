package dev.chunkatlas;

import dev.chunkatlas.engine.Area;
import dev.chunkatlas.engine.BorderBook;
import dev.chunkatlas.engine.Borders;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.ChunkTasks;
import dev.chunkatlas.engine.TaskBook;
import dev.chunkatlas.gui.Ui;
import dev.chunkatlas.net.AtlasNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

public final class TaskClient {
   public static final String[] SOUNDS = {"", "minecraft:entity.enderman.teleport", "minecraft:block.note_block.bass", "minecraft:block.bell.use"};
   public static final String[] EFFECTS = {"", "minecraft:portal", "minecraft:smoke", "minecraft:witch"};
   private static List<ChunkTasks.View> remote = List.of();
   private static int remoteAtOnce = 1;
   private static boolean remoteAdmin;
   private static boolean heard;
   private static Map<String, BorderBook.Border> remoteBorders = Map.of();

   private static final Map<Integer, ChunkSelection> dealt = new HashMap<>();
   private static int version;
   private static int seenLocal = -1;
   private static String problem;

   private TaskClient() {
   }

   static void register() {
      ServerLifecycleEvents.SERVER_STARTED.register(server -> {
         if (server instanceof IntegratedServer) {
            Borders.options(localOptions());
            ChunkTasks.started(server, Settings.tasksRejoin() == 0);
            Borders.started(server);
         }
      });
      ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
         if (server instanceof IntegratedServer) {
            ChunkTasks.stopping(server);
            Borders.stopping(server);
         }
      });
      ServerTickEvents.END_SERVER_TICK.register(server -> {
         if (server instanceof IntegratedServer) {
            ChunkTasks tasks = ChunkTasks.of(server);
            Borders borders = Borders.of(server);
            if (tasks != null) {
               tasks.tick();
            }

            if (borders != null) {
               borders.tick();
            }
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register(TaskClient::tick);
      ClientPlayNetworking.registerGlobalReceiver(AtlasNet.TasksUpdate.TYPE, (u, ctx) -> heard(u));
      ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
         remote = List.of();
         remoteBorders = Map.of();
         remoteAdmin = false;
         heard = false;
         dealt.clear();
         seenLocal = -1;
         version++;
      });
   }

   static Borders.Options localOptions() {
      String message = Settings.borderMessage();
      return new Borders.Options(
         message == null ? Ui.t("chunkatlas.border.message.default") : message, SOUNDS[Math.min(SOUNDS.length - 1, Settings.borderSound())],
         EFFECTS[Math.min(EFFECTS.length - 1, Settings.borderEffect())], Settings.borderParticles(), "minecraft:end_rod"
      );
   }

   private static void tick(Minecraft mc) {
      ChunkTasks tasks = local();
      if (tasks == null) {
         return;
      }

      if (mc.level != null && mc.level.getGameTime() % 20L == 0L) {
         Borders.options(localOptions());
      }

      for (ChunkTasks.View v : tasks.views()) {
         ChunkSelection had = dealt.get(v.id());
         if (had == null) {
            had = tasks.dealt(v.id());
            tasks.takeFresh(v.id(), ChunkTasks.FOR_MAP);
            dealt.put(v.id(), had);
         } else {
            add(had, tasks.takeFresh(v.id(), ChunkTasks.FOR_MAP));
         }
      }

      if (tasks.version() != seenLocal) {
         seenLocal = tasks.version();
         dealt.keySet().removeIf(id -> tasks.views().stream().noneMatch(v -> v.id() == id));
         version++;
      }
   }

   private static void heard(AtlasNet.TasksUpdate u) {
      heard = true;
      remoteAdmin = u.admin();
      remoteAtOnce = u.atOnce();
      List<ChunkTasks.View> views = new ArrayList<>();
      for (AtlasNet.TaskInfo t : u.tasks()) {
         Area area = t.area().isEmpty() ? null : Area.read(t.area());
         views.add(new ChunkTasks.View(
            t.id(), t.dimension(), area, area == null, TaskBook.State.of(t.state()), t.done(), t.total(), 0, t.rate(), t.secondsLeft(), t.flying()
         ));
         ChunkSelection had = u.full() ? null : dealt.get(t.id());
         if (had == null) {
            had = new ChunkSelection();
            dealt.put(t.id(), had);
         }

         add(had, t.dealt());
      }

      dealt.keySet().removeIf(id -> u.tasks().stream().noneMatch(t -> t.id() == id));
      remote = List.copyOf(views);
      Map<String, BorderBook.Border> borders = new LinkedHashMap<>();
      for (AtlasNet.BorderInfo b : u.borders()) {
         Area area = Area.read(b.area());
         if (area != null) {
            borders.put(b.dimension(), new BorderBook.Border(area, b.wrap()));
         }
      }

      remoteBorders = Map.copyOf(borders);
      version++;
   }

   private static void add(ChunkSelection into, ChunkSelection more) {
      for (long region : more.regions()) {
         into.add((int)(region >> 32), (int)region, more.bits((int)(region >> 32), (int)region));
      }
   }

   private static IntegratedServer server() {
      return Minecraft.getInstance().getSingleplayerServer();
   }

   static ChunkTasks local() {
      IntegratedServer s = server();
      return s == null ? null : ChunkTasks.of(s);
   }

   private static Borders localBorders() {
      IntegratedServer s = server();
      return s == null ? null : Borders.of(s);
   }

   public static int version() {
      ChunkTasks t = local();
      Borders b = localBorders();
      return version + (t == null ? 0 : t.version()) + (b == null ? 0 : b.version() * 7919);
   }

   public static boolean usable() {
      return local() != null || heard && remoteAdmin && ServerLink.canEdit() && ClientPlayNetworking.canSend(AtlasNet.TaskCommand.TYPE);
   }

   public static String blocked() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return Ui.t("chunkatlas.generate.offline");
      }

      if (usable()) {
         return null;
      }

      boolean served = ServerLink.hasMod() && ClientPlayNetworking.canSend(AtlasNet.TaskCommand.TYPE);
      return Ui.t(served ? "chunkatlas.tasks.admins" : "chunkatlas.generate.needs_server");
   }

   public static List<ChunkTasks.View> tasks() {
      ChunkTasks t = local();
      return t != null ? t.views() : remote;
   }

   public static int atOnce() {
      ChunkTasks t = local();
      return t != null ? t.atOnce() : remoteAtOnce;
   }

   public static ChunkSelection dealt(int id) {
      ChunkSelection d = dealt.get(id);
      return d == null ? new ChunkSelection() : d;
   }

   public static Map<String, BorderBook.Border> borders() {
      Borders b = localBorders();
      return b != null ? b.all() : remoteBorders;
   }

   public static BorderBook.Border border(String dimension) {
      return dimension == null ? null : borders().get(dimension);
   }

   public static String takeProblem() {
      String p = problem;
      problem = null;
      return p;
   }

   private static void onServer(java.util.function.Consumer<ChunkTasks> run) {
      IntegratedServer s = server();
      if (s != null) {
         s.execute(() -> {
            ChunkTasks t = ChunkTasks.of(s);
            if (t != null) {
               run.accept(t);
            }
         });
      }
   }

   private static void send(AtlasNet.TaskCommand c) {
      if (ClientPlayNetworking.canSend(AtlasNet.TaskCommand.TYPE)) {
         ClientPlayNetworking.send(c);
      }
   }

   private static int ask() {
      return ServerLink.ask(reply -> {
         if (!reply.ok()) {
            problem = Ui.t(reply.key());
         }
      });
   }

   public static void add(String dimension, Area area, ChunkTasks.WorldBorderUse border, double borderSize) {
      int speed = Settings.generateSpeed();
      boolean finish = Settings.generateFinish();
      if (local() != null) {
         onServer(t -> {
            String why = t.refusal(dimension, border == ChunkTasks.WorldBorderUse.FIT ? 1 : (int)Math.min(Integer.MAX_VALUE, area.chunkCount()));
            if (why != null || t.add(dimension, area, border, borderSize, speed, finish) == null) {
               Minecraft.getInstance().execute(() -> problem = Ui.t(why == null ? "chunkatlas.tasks.failed" : why));
            }
         });
      } else {
         send(new AtlasNet.TaskCommand(AtlasNet.TaskCommand.ADD, ask(), dimension, area.write(), border.ordinal(), borderSize, speed, finish, 0, false));
      }
   }

   public static void pause(int id, boolean on) {
      if (local() != null) {
         onServer(t -> t.pause(id, on));
      } else {
         send(AtlasNet.TaskCommand.of(on ? AtlasNet.TaskCommand.PAUSE : AtlasNet.TaskCommand.RESUME, id));
      }
   }

   public static void cancel(int id) {
      if (local() != null) {
         onServer(t -> t.cancel(id));
      } else {
         send(AtlasNet.TaskCommand.of(AtlasNet.TaskCommand.CANCEL, id));
      }
   }

   public static void atOnce(int n) {
      if (local() != null) {
         onServer(t -> t.atOnce(n));
      } else {
         remoteAtOnce = Math.max(1, Math.min(TaskBook.MAX_AT_ONCE, n));
         send(new AtlasNet.TaskCommand(AtlasNet.TaskCommand.AT_ONCE, 0, "", "", 0, 0.0, 0, false, n, false));
      }
   }

   public static void border(String dimension, BorderBook.Border border) {
      IntegratedServer s = server();
      if (s != null) {
         s.execute(() -> {
            Borders b = Borders.of(s);
            if (b != null) {
               b.set(dimension, border);
            }
         });
      } else if (border == null) {
         send(new AtlasNet.TaskCommand(AtlasNet.TaskCommand.BORDER_CLEAR, 0, dimension, "", 0, 0.0, 0, false, 0, false));
      } else {
         send(new AtlasNet.TaskCommand(AtlasNet.TaskCommand.BORDER_SET, ask(), dimension, border.area().write(), 0, 0.0, 0, false, 0, border.wrap()));
      }
   }
}
