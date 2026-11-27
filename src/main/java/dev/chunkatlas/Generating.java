package dev.chunkatlas;

import dev.chunkatlas.engine.ChunkGeneration;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.ChunkTasks;
import dev.chunkatlas.engine.TaskBook;
import dev.chunkatlas.gui.Ui;
import dev.chunkatlas.net.AtlasNet;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

public final class Generating {
   private static final long SHOWN_AFTER = 3000L;

   private static int local;
   private static ChunkGeneration.Progress localEnded;
   private static int remoteId;
   private static ChunkGeneration.Progress remote;
   private static final ChunkSelection remoteDealt = new ChunkSelection();
   private static Path world;
   private static String dimension;
   private static ChunkGeneration.Speed speed = ChunkGeneration.Speed.BALANCED;
   private static boolean holdGame;
   private static boolean pausing;
   private static long startedAt;
   private static long endedAt;
   private static String problem;

   private static final double[] RATE = new double[ChunkGeneration.Speed.values().length];

   private Generating() {
   }

   static void register() {
      ClientPlayNetworking.registerGlobalReceiver(AtlasNet.GenerateProgress.TYPE, (p, ctx) -> {
         if (p.id() != remoteId || remote == null) {
            return;
         }

         ChunkGeneration.State[] states = ChunkGeneration.State.values();
         remote = new ChunkGeneration.Progress(states[Math.max(0, Math.min(states.length - 1, p.state()))], p.done(), p.total(), p.leftAlone(), p.secondsLeft());
         for (long region : p.dealt().regions()) {
            remoteDealt.add((int)(region >> 32), (int)region, p.dealt().bits((int)(region >> 32), (int)region));
         }

         if (remote.state().ended()) {
            ended();
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         if (local != 0 && endedAt == 0L) {
            ChunkGeneration.Progress p = progress();
            if (p == null || p.state().ended()) {
               ended();
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
         local = 0;
         localEnded = null;
         remote = null;
         remoteId = 0;
         endedAt = 0L;
      });
   }

   public static boolean start(Path folder, String dim, ChunkSelection chunks, ChunkGeneration.Speed at, boolean finish, boolean hold) {
      Minecraft mc = Minecraft.getInstance();
      if (active() || mc.player == null || chunks.isEmpty()) {
         return false;
      }

      IntegratedServer server = mc.getSingleplayerServer();
      if (server != null) {
         int made;
         try {
            made = server.submit(() -> {
               ChunkTasks tasks = ChunkTasks.of(server);
               TaskBook.Task t = tasks == null ? null : tasks.addPicked(dim, chunks, at.ordinal(), finish);
               return t == null ? 0 : t.id;
            }).get(5L, java.util.concurrent.TimeUnit.SECONDS);
         } catch (Exception e) {
            made = 0;
         }

         if (made == 0) {
            return false;
         }

         local = made;
         localEnded = null;
         remote = null;
      } else {
         if (!remoteReady()) {
            return false;
         }

         int id = ServerLink.ask(reply -> {
            if (!reply.ok() && reply.id() == remoteId) {
               problem = Ui.t(reply.key(), Ui.count(reply.chunks()));
               remote = null;
               remoteId = 0;
            }
         });
         List<ChunkSelection> pieces = pieces(chunks);
         for (int i = 0; i < pieces.size(); i++) {
            ClientPlayNetworking.send(new AtlasNet.GenerateRequest(id, dim, at.ordinal(), finish, i == pieces.size() - 1, pieces.get(i)));
         }

         local = 0;
         remoteId = id;
         remote = new ChunkGeneration.Progress(ChunkGeneration.State.RUNNING, 0, chunks.size(), 0, -1);
         remoteDealt.clear();
      }

      world = folder;
      dimension = dim;
      speed = at;
      holdGame = hold && server != null;
      pausing = false;
      startedAt = System.currentTimeMillis();
      endedAt = 0L;
      return true;
   }

   public static boolean remoteReady() {
      return ServerLink.canEdit() && ClientPlayNetworking.canSend(AtlasNet.GenerateRequest.TYPE);
   }

   private static List<ChunkSelection> pieces(ChunkSelection chunks) {
      List<ChunkSelection> out = new ArrayList<>();
      ChunkSelection piece = new ChunkSelection();
      int regions = 0;
      for (long region : chunks.regions()) {
         if (regions == AtlasNet.GenerateRequest.PIECE_REGIONS) {
            out.add(piece);
            piece = new ChunkSelection();
            regions = 0;
         }

         piece.add((int)(region >> 32), (int)region, chunks.bits((int)(region >> 32), (int)region));
         regions++;
      }

      out.add(piece);
      return out;
   }

   private static void ended() {
      endedAt = System.currentTimeMillis();
      ChunkGeneration.Progress p = progress();
      double seconds = (endedAt - startedAt) / 1000.0;
      if (p != null && p.state() == ChunkGeneration.State.DONE && p.done() >= 16 && seconds > 2.0) {
         RATE[speed.ordinal()] = p.done() / seconds;
      }
   }

   public static ChunkGeneration.Progress progress() {
      if (local == 0) {
         return remote;
      }

      ChunkTasks tasks = TaskClient.local();
      ChunkTasks.View v = null;
      if (tasks != null) {
         for (ChunkTasks.View x : tasks.views()) {
            if (x.id() == local) {
               v = x;
            }
         }
      }

      if (v == null) {
         return localEnded != null ? localEnded : new ChunkGeneration.Progress(ChunkGeneration.State.STOPPED, 0, 0, 0, -1);
      }

      ChunkGeneration.State state = switch (v.state()) {
         case PAUSED -> ChunkGeneration.State.PAUSED;
         case DONE -> ChunkGeneration.State.DONE;
         case CANCELLED -> ChunkGeneration.State.STOPPED;
         default -> ChunkGeneration.State.RUNNING;
      };
      int leftAlone = Math.max(0, v.done() - v.generated());
      ChunkGeneration.Progress p = new ChunkGeneration.Progress(state, v.generated(), v.total() - leftAlone, leftAlone, v.secondsLeft());
      if (state.ended()) {
         localEnded = p;
      }

      return p;
   }

   public static boolean active() {
      ChunkGeneration.Progress p = progress();
      return p != null && !p.state().ended();
   }

   public static boolean showing() {
      return active() || progress() != null && System.currentTimeMillis() - endedAt < SHOWN_AFTER;
   }

   public static Path world() {
      return world;
   }

   public static String dimension() {
      return dimension;
   }

   public static ChunkSelection takeDealt() {
      if (local != 0) {
         ChunkTasks tasks = TaskClient.local();
         return tasks == null ? new ChunkSelection() : tasks.takeFresh(local, ChunkTasks.FOR_GENERATE);
      }

      ChunkSelection out = remoteDealt.copy();
      remoteDealt.clear();
      return out;
   }

   public static boolean paused() {
      return pausing;
   }

   public static void pause(boolean on) {
      pausing = on;
      if (local != 0) {
         TaskClient.pause(local, on);
      } else if (remote != null && ClientPlayNetworking.canSend(AtlasNet.GenerateControl.TYPE)) {
         ClientPlayNetworking.send(new AtlasNet.GenerateControl(remoteId, on ? AtlasNet.GenerateControl.PAUSE : AtlasNet.GenerateControl.RESUME));
      }
   }

   public static void stop() {
      if (local != 0) {
         TaskClient.cancel(local);
      } else if (remote != null && ClientPlayNetworking.canSend(AtlasNet.GenerateControl.TYPE)) {
         ClientPlayNetworking.send(new AtlasNet.GenerateControl(remoteId, AtlasNet.GenerateControl.STOP));
      }
   }

   public static boolean keepsWorldRunning() {
      return local != 0 && active() && !holdGame;
   }

   public static String takeProblem() {
      String p = problem;
      problem = null;
      return p;
   }

   public static double rate(ChunkGeneration.Speed at) {
      double measured = RATE[at.ordinal()];
      if (measured > 0.0) {
         return measured;
      }

      int cores = dev.chunkatlas.engine.Background.cores();
      return switch (at) {
         case LIGHT -> Math.min(8, cores) * 1.5;
         case BALANCED -> cores * 3.0;
         case FAST -> cores * 4.0;
      };
   }
}
