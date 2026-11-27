package dev.chunkatlas;

import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.net.AtlasNet;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ServerLink {
   private static boolean hasMod;
   private static boolean canEdit;
   private static boolean live;
   private static int waiting;
   private static int nextId = 1;
   private static final Map<Integer, Consumer<AtlasNet.Reply>> asked = new HashMap<>();

   private static final Map<Integer, Consumer<dev.chunkatlas.engine.ChunkLoads.Snapshot>> loadsAsked = new HashMap<>();

   private static int downId;
   private static ByteArrayOutputStream down;
   private static int downTotal;
   private static Consumer<byte[]> downDone;

   private static long downHeard;

   private static final long DOWN_SILENT_MS = 60_000L;

   private static final ArrayDeque<AtlasNet.UpPiece> up = new ArrayDeque<>();
   private static int upTotal;
   private static int upSent;

   private ServerLink() {
   }

   static void register() {
      ClientPlayNetworking.registerGlobalReceiver(AtlasNet.Hello.TYPE, (hello, ctx) -> {
         hasMod = hello.version() == AtlasNet.VERSION;
         canEdit = hasMod && hello.canEdit();
         live = hasMod && hello.live();
         waiting = hello.waiting();
      });
      ClientPlayNetworking.registerGlobalReceiver(AtlasNet.Reply.TYPE, (reply, ctx) -> {
         waiting = reply.waiting();
         if (reply.id() == downId && !reply.ok()) {
            down = null;
            downDone = null;
         }

         if (!reply.ok() && !up.isEmpty() && up.peek().id() == reply.id()) {
            up.clear();
         }

         Consumer<AtlasNet.Reply> who = asked.remove(reply.id());
         if (who != null) {
            who.accept(reply);
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(AtlasNet.LoadsReply.TYPE, (reply, ctx) -> {
         asked.remove(reply.id());
         Consumer<dev.chunkatlas.engine.ChunkLoads.Snapshot> who = loadsAsked.remove(reply.id());
         if (who != null) {
            who.accept(reply.loads());
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(AtlasNet.DownPiece.TYPE, (piece, ctx) -> {
         if (piece.id() != downId || down == null) {
            return;
         }

         downTotal = piece.total();
         downHeard = System.currentTimeMillis();
         down.writeBytes(piece.data());
         if (down.size() >= downTotal) {
            Consumer<byte[]> done = downDone;
            byte[] data = down.toByteArray();
            down = null;
            downDone = null;
            asked.remove(piece.id());
            if (done != null) {
               done.accept(data);
            }
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         for (int k = 0; k < 4 && !up.isEmpty() && ClientPlayNetworking.canSend(AtlasNet.UpPiece.TYPE); k++) {
            AtlasNet.UpPiece p = up.poll();
            ClientPlayNetworking.send(p);
            upSent += p.data().length;
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
         hasMod = false;
         canEdit = false;
         live = false;
         waiting = 0;
         asked.clear();
         loadsAsked.clear();
         down = null;
         downDone = null;
         up.clear();
      });
   }

   public static boolean hasMod() {
      return hasMod;
   }

   public static boolean canEdit() {
      return canEdit;
   }

   public static boolean live() {
      return live;
   }

   public static int waiting() {
      return waiting;
   }

   public static boolean madeNow(String kind) {
      return live && !"KEEP_ONLY".equals(kind);
   }

   public static boolean edit(String kind, String dimension, ChunkSelection chunks, Consumer<AtlasNet.Reply> answer) {
      return edit(kind, dimension, chunks, madeNow(kind), answer);
   }

   public static boolean edit(String kind, String dimension, ChunkSelection chunks, boolean now, Consumer<AtlasNet.Reply> answer) {
      if (!canEdit || !ClientPlayNetworking.canSend(AtlasNet.EditRequest.TYPE)) {
         return false;
      }

      int id = nextId++;
      asked.put(id, answer);
      ClientPlayNetworking.send(new AtlasNet.EditRequest(id, kind, dimension, chunks.copy(), now));
      return true;
   }

   public static boolean hasLoads() {
      return hasMod && ClientPlayNetworking.canSend(AtlasNet.LoadsRequest.TYPE);
   }

   public static boolean loads(String dimension, Consumer<dev.chunkatlas.engine.ChunkLoads.Snapshot> answer) {
      if (!canEdit || !hasLoads()) {
         return false;
      }

      int id = nextId++;
      loadsAsked.put(id, answer);
      asked.put(id, reply -> {
         if (loadsAsked.remove(id) != null) {
            answer.accept(null);
         }
      });
      ClientPlayNetworking.send(new AtlasNet.LoadsRequest(id, dimension));
      return true;
   }

   public static boolean force(String dimension, ChunkSelection chunks, boolean on, Consumer<AtlasNet.Reply> answer) {
      if (!canEdit || !ClientPlayNetworking.canSend(AtlasNet.ForceRequest.TYPE)) {
         return false;
      }

      int id = nextId++;
      asked.put(id, answer);
      ClientPlayNetworking.send(new AtlasNet.ForceRequest(id, dimension, chunks.copy(), on));
      return true;
   }

   public static int ask(Consumer<AtlasNet.Reply> answer) {
      int id = nextId++;
      asked.put(id, answer);
      return id;
   }

   public static boolean cancelAll(Consumer<AtlasNet.Reply> answer) {
      if (!canEdit || !ClientPlayNetworking.canSend(AtlasNet.CancelRequest.TYPE)) {
         return false;
      }

      int id = nextId++;
      asked.put(id, answer);
      ClientPlayNetworking.send(new AtlasNet.CancelRequest(id));
      return true;
   }

   public static boolean copy(String dimension, ChunkSelection chunks, Consumer<byte[]> done, Consumer<AtlasNet.Reply> failed) {
      if (down != null && System.currentTimeMillis() - downHeard > DOWN_SILENT_MS) {
         asked.remove(downId);
         down = null;
         downDone = null;
      }

      if (!canEdit || down != null || !ClientPlayNetworking.canSend(AtlasNet.CopyRequest.TYPE)) {
         return false;
      }

      int id = nextId++;
      downId = id;
      downHeard = System.currentTimeMillis();
      down = new ByteArrayOutputStream(1 << 20);
      downTotal = 0;
      downDone = done;
      asked.put(id, failed);
      ClientPlayNetworking.send(new AtlasNet.CopyRequest(id, dimension, chunks.copy()));
      return true;
   }

   public static double copyProgress() {
      ByteArrayOutputStream d = down;
      return d == null || downTotal <= 0 ? 0.0 : Math.min(1.0, d.size() / (double)downTotal);
   }

   public static boolean paste(byte[] archive, String dimension, int dx, int dz, boolean keep, int blend, Consumer<AtlasNet.Reply> answer) {
      if (!canEdit || !up.isEmpty() || !ClientPlayNetworking.canSend(AtlasNet.PasteStart.TYPE)) {
         return false;
      }

      int id = nextId++;
      asked.put(id, answer);
      ClientPlayNetworking.send(new AtlasNet.PasteStart(id, dimension, dx, dz, keep, blend, archive.length));
      int seq = 0;
      for (int at = 0; at < archive.length; at += AtlasNet.UP_PIECE) {
         up.add(new AtlasNet.UpPiece(id, seq++, java.util.Arrays.copyOfRange(archive, at, Math.min(archive.length, at + AtlasNet.UP_PIECE))));
      }

      upTotal = archive.length;
      upSent = 0;
      return true;
   }

   public static double pasteProgress() {
      return upTotal <= 0 ? 0.0 : Math.min(1.0, upSent / (double)upTotal);
   }
}
