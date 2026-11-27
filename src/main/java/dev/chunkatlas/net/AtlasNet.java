package dev.chunkatlas.net;

import dev.chunkatlas.engine.ChunkLoads;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.port.Net;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class AtlasNet {
   public static final int VERSION = 3;

   public static final int UP_PIECE = 30000;

   public static final int DOWN_PIECE = 256 * 1024;

   private AtlasNet() {
   }

   private static boolean registered;

   public static synchronized void register() {
      if (registered) {
         return;
      }

      registered = true;
      Net.s2c(Hello.TYPE, Hello.CODEC);
      Net.s2c(Reply.TYPE, Reply.CODEC);
      Net.c2s(EditRequest.TYPE, EditRequest.CODEC);
      Net.c2s(CancelRequest.TYPE, CancelRequest.CODEC);
      Net.c2s(CopyRequest.TYPE, CopyRequest.CODEC);
      Net.c2s(PasteStart.TYPE, PasteStart.CODEC);
      Net.c2s(UpPiece.TYPE, UpPiece.CODEC);
      Net.s2c(DownPiece.TYPE, DownPiece.CODEC);
      Net.c2s(LoadsRequest.TYPE, LoadsRequest.CODEC);
      Net.s2c(LoadsReply.TYPE, LoadsReply.CODEC);
      Net.c2s(ForceRequest.TYPE, ForceRequest.CODEC);
      Net.c2s(GenerateRequest.TYPE, GenerateRequest.CODEC);
      Net.c2s(GenerateControl.TYPE, GenerateControl.CODEC);
      Net.s2c(GenerateProgress.TYPE, GenerateProgress.CODEC);
      Net.c2s(TaskCommand.TYPE, TaskCommand.CODEC);
      Net.s2c(TasksUpdate.TYPE, TasksUpdate.CODEC);
   }

   public record Hello(int version, boolean canEdit, int waiting, boolean live) implements CustomPacketPayload {
      public static final Type<Hello> TYPE = Net.type("hello");
      public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.of(
         (buf, h) -> {
            buf.writeVarInt(h.version);
            buf.writeBoolean(h.canEdit);
            buf.writeVarInt(h.waiting);
            buf.writeBoolean(h.live);
         },
         buf -> {
            int version = buf.readVarInt();
            boolean canEdit = buf.readBoolean();
            int waiting = buf.readVarInt();

            boolean live = buf.isReadable() && buf.readBoolean();
            return new Hello(version, canEdit, waiting, live);
         }
      );

      @Override
      public Type<Hello> type() {
         return TYPE;
      }
   }

   public record EditRequest(int id, String kind, String dimension, ChunkSelection chunks, boolean live) implements CustomPacketPayload {
      public static final Type<EditRequest> TYPE = Net.type("edit");

      public static final int MAX_REGIONS = 4096;
      public static final StreamCodec<RegistryFriendlyByteBuf, EditRequest> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeUtf(r.kind, 32);
            buf.writeUtf(r.dimension, 256);
            List<Long> regions = new ArrayList<>(r.chunks.regions());
            buf.writeVarInt(regions.size());
            for (long key : regions) {
               int rx = (int)(key >> 32);
               int rz = (int)key;
               buf.writeInt(rx);
               buf.writeInt(rz);
               for (long word : r.chunks.bits(rx, rz)) {
                  buf.writeLong(word);
               }
            }

            buf.writeBoolean(r.live);
         },
         buf -> {
            int id = buf.readVarInt();
            String kind = buf.readUtf(32);
            String dim = buf.readUtf(256);
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_REGIONS) {
               throw new IllegalArgumentException("too many regions: " + n);
            }

            ChunkSelection sel = new ChunkSelection();
            for (int i = 0; i < n; i++) {
               int rx = buf.readInt();
               int rz = buf.readInt();
               long[] mask = new long[ChunkSelection.WORDS];
               for (int w = 0; w < mask.length; w++) {
                  mask[w] = buf.readLong();
               }

               sel.add(rx, rz, mask);
            }

            return new EditRequest(id, kind, dim, sel, buf.readBoolean());
         }
      );

      @Override
      public Type<EditRequest> type() {
         return TYPE;
      }
   }

   static void writeSelection(RegistryFriendlyByteBuf buf, ChunkSelection chunks) {
      List<Long> regions = new ArrayList<>(chunks.regions());
      buf.writeVarInt(regions.size());
      for (long key : regions) {
         int rx = (int)(key >> 32);
         int rz = (int)key;
         buf.writeInt(rx);
         buf.writeInt(rz);
         for (long word : chunks.bits(rx, rz)) {
            buf.writeLong(word);
         }
      }
   }

   static ChunkSelection readSelection(RegistryFriendlyByteBuf buf) {
      int n = buf.readVarInt();
      if (n < 0 || n > EditRequest.MAX_REGIONS) {
         throw new IllegalArgumentException("too many regions: " + n);
      }

      ChunkSelection sel = new ChunkSelection();
      for (int i = 0; i < n; i++) {
         int rx = buf.readInt();
         int rz = buf.readInt();
         long[] mask = new long[ChunkSelection.WORDS];
         for (int w = 0; w < mask.length; w++) {
            mask[w] = buf.readLong();
         }

         sel.add(rx, rz, mask);
      }

      return sel;
   }

   public record CopyRequest(int id, String dimension, ChunkSelection chunks) implements CustomPacketPayload {
      public static final Type<CopyRequest> TYPE = Net.type("copy");
      public static final StreamCodec<RegistryFriendlyByteBuf, CopyRequest> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeUtf(r.dimension, 256);
            writeSelection(buf, r.chunks);
         },
         buf -> new CopyRequest(buf.readVarInt(), buf.readUtf(256), readSelection(buf))
      );

      @Override
      public Type<CopyRequest> type() {
         return TYPE;
      }
   }

   public record PasteStart(int id, String dimension, int dx, int dz, boolean keep, int blend, int bytes) implements CustomPacketPayload {
      public static final Type<PasteStart> TYPE = Net.type("paste");
      public static final StreamCodec<RegistryFriendlyByteBuf, PasteStart> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeUtf(r.dimension, 256);
            buf.writeInt(r.dx);
            buf.writeInt(r.dz);
            buf.writeBoolean(r.keep);
            buf.writeVarInt(r.blend);
            buf.writeVarInt(r.bytes);
         },
         buf -> new PasteStart(buf.readVarInt(), buf.readUtf(256), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt())
      );

      @Override
      public Type<PasteStart> type() {
         return TYPE;
      }
   }

   public record UpPiece(int id, int seq, byte[] data) implements CustomPacketPayload {
      public static final Type<UpPiece> TYPE = Net.type("up");
      public static final StreamCodec<RegistryFriendlyByteBuf, UpPiece> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeVarInt(r.seq);
            buf.writeByteArray(r.data);
         },
         buf -> new UpPiece(buf.readVarInt(), buf.readVarInt(), buf.readByteArray(UP_PIECE))
      );

      @Override
      public Type<UpPiece> type() {
         return TYPE;
      }
   }

   public record DownPiece(int id, int seq, int total, byte[] data) implements CustomPacketPayload {
      public static final Type<DownPiece> TYPE = Net.type("down");
      public static final StreamCodec<RegistryFriendlyByteBuf, DownPiece> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeVarInt(r.seq);
            buf.writeVarInt(r.total);
            buf.writeByteArray(r.data);
         },
         buf -> new DownPiece(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(DOWN_PIECE))
      );

      @Override
      public Type<DownPiece> type() {
         return TYPE;
      }
   }

   public record GenerateRequest(int id, String dimension, int speed, boolean finishPartial, boolean last, ChunkSelection chunks) implements CustomPacketPayload {
      public static final Type<GenerateRequest> TYPE = Net.type("generate");
      public static final int PIECE_REGIONS = 200;
      public static final StreamCodec<RegistryFriendlyByteBuf, GenerateRequest> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeUtf(r.dimension, 256);
            buf.writeVarInt(r.speed);
            buf.writeBoolean(r.finishPartial);
            buf.writeBoolean(r.last);
            writeSelection(buf, r.chunks);
         },
         buf -> new GenerateRequest(buf.readVarInt(), buf.readUtf(256), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), readSelection(buf))
      );

      @Override
      public Type<GenerateRequest> type() {
         return TYPE;
      }
   }

   public record GenerateControl(int id, int action) implements CustomPacketPayload {
      public static final int PAUSE = 0;
      public static final int RESUME = 1;
      public static final int STOP = 2;
      public static final Type<GenerateControl> TYPE = Net.type("generate_control");
      public static final StreamCodec<RegistryFriendlyByteBuf, GenerateControl> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeVarInt(r.action);
         },
         buf -> new GenerateControl(buf.readVarInt(), buf.readVarInt())
      );

      @Override
      public Type<GenerateControl> type() {
         return TYPE;
      }
   }

   public record GenerateProgress(int id, int state, int done, int total, int leftAlone, int secondsLeft, ChunkSelection dealt) implements CustomPacketPayload {
      public static final Type<GenerateProgress> TYPE = Net.type("generate_progress");
      public static final StreamCodec<RegistryFriendlyByteBuf, GenerateProgress> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeVarInt(r.state);
            buf.writeVarInt(r.done);
            buf.writeVarInt(r.total);
            buf.writeVarInt(r.leftAlone);
            buf.writeInt(r.secondsLeft);
            writeSelection(buf, r.dealt);
         },
         buf -> new GenerateProgress(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(), readSelection(buf))
      );

      @Override
      public Type<GenerateProgress> type() {
         return TYPE;
      }
   }

   public record TaskCommand(int action, int id, String dimension, String area, int border, double size, int speed, boolean finish, int value, boolean flag)
      implements CustomPacketPayload {
      public static final int ADD = 0;
      public static final int PAUSE = 1;
      public static final int RESUME = 2;
      public static final int CANCEL = 3;
      public static final int AT_ONCE = 4;
      public static final int BORDER_SET = 5;
      public static final int BORDER_CLEAR = 6;
      public static final Type<TaskCommand> TYPE = Net.type("task");
      public static final StreamCodec<RegistryFriendlyByteBuf, TaskCommand> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.action);
            buf.writeVarInt(r.id);
            buf.writeUtf(r.dimension, 256);
            buf.writeUtf(r.area, 256);
            buf.writeVarInt(r.border);
            buf.writeDouble(r.size);
            buf.writeVarInt(r.speed);
            buf.writeBoolean(r.finish);
            buf.writeVarInt(r.value);
            buf.writeBoolean(r.flag);
         },
         buf -> new TaskCommand(
            buf.readVarInt(), buf.readVarInt(), buf.readUtf(256), buf.readUtf(256), buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
            buf.readBoolean()
         )
      );

      public static TaskCommand of(int action, int id) {
         return new TaskCommand(action, id, "", "", 0, 0.0, 0, false, 0, false);
      }

      @Override
      public Type<TaskCommand> type() {
         return TYPE;
      }
   }

   public record TaskInfo(int id, String dimension, String area, int state, int done, int total, float rate, int secondsLeft, long[] flying, ChunkSelection dealt) {
   }

   public record BorderInfo(String dimension, String area, boolean wrap) {
   }

   public record TasksUpdate(boolean admin, boolean full, int atOnce, List<TaskInfo> tasks, List<BorderInfo> borders) implements CustomPacketPayload {
      public static final Type<TasksUpdate> TYPE = Net.type("tasks");
      public static final int MAX_TASKS = 64;
      public static final StreamCodec<RegistryFriendlyByteBuf, TasksUpdate> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeBoolean(r.admin);
            buf.writeBoolean(r.full);
            buf.writeVarInt(r.atOnce);
            buf.writeVarInt(r.tasks.size());
            for (TaskInfo t : r.tasks) {
               buf.writeVarInt(t.id());
               buf.writeUtf(t.dimension(), 256);
               buf.writeUtf(t.area(), 256);
               buf.writeVarInt(t.state());
               buf.writeVarInt(t.done());
               buf.writeVarInt(t.total());
               buf.writeFloat(t.rate());
               buf.writeInt(t.secondsLeft());
               buf.writeVarInt(t.flying().length);
               for (long f : t.flying()) {
                  buf.writeLong(f);
               }

               writeSelection(buf, t.dealt());
            }

            buf.writeVarInt(r.borders.size());
            for (BorderInfo b : r.borders) {
               buf.writeUtf(b.dimension(), 256);
               buf.writeUtf(b.area(), 256);
               buf.writeBoolean(b.wrap());
            }
         },
         buf -> {
            boolean admin = buf.readBoolean();
            boolean full = buf.readBoolean();
            int atOnce = buf.readVarInt();
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_TASKS) {
               throw new IllegalArgumentException("too many tasks: " + n);
            }

            List<TaskInfo> tasks = new ArrayList<>();
            for (int i = 0; i < n; i++) {
               int id = buf.readVarInt();
               String dim = buf.readUtf(256);
               String area = buf.readUtf(256);
               int state = buf.readVarInt();
               int done = buf.readVarInt();
               int total = buf.readVarInt();
               float rate = buf.readFloat();
               int left = buf.readInt();
               int f = buf.readVarInt();
               if (f < 0 || f > 4096) {
                  throw new IllegalArgumentException("too many chunks in flight: " + f);
               }

               long[] flying = new long[f];
               for (int k = 0; k < f; k++) {
                  flying[k] = buf.readLong();
               }

               tasks.add(new TaskInfo(id, dim, area, state, done, total, rate, left, flying, readSelection(buf)));
            }

            int b = buf.readVarInt();
            if (b < 0 || b > 256) {
               throw new IllegalArgumentException("too many borders: " + b);
            }

            List<BorderInfo> borders = new ArrayList<>();
            for (int i = 0; i < b; i++) {
               borders.add(new BorderInfo(buf.readUtf(256), buf.readUtf(256), buf.readBoolean()));
            }

            return new TasksUpdate(admin, full, atOnce, tasks, borders);
         }
      );

      @Override
      public Type<TasksUpdate> type() {
         return TYPE;
      }
   }

   public record CancelRequest(int id) implements CustomPacketPayload {
      public static final Type<CancelRequest> TYPE = Net.type("cancel");
      public static final StreamCodec<RegistryFriendlyByteBuf, CancelRequest> CODEC = StreamCodec.of(
         (buf, r) -> buf.writeVarInt(r.id), buf -> new CancelRequest(buf.readVarInt())
      );

      @Override
      public Type<CancelRequest> type() {
         return TYPE;
      }
   }

   public record LoadsRequest(int id, String dimension) implements CustomPacketPayload {
      public static final Type<LoadsRequest> TYPE = Net.type("loads");
      public static final StreamCodec<RegistryFriendlyByteBuf, LoadsRequest> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeUtf(r.dimension, 256);
         },
         buf -> new LoadsRequest(buf.readVarInt(), buf.readUtf(256))
      );

      @Override
      public Type<LoadsRequest> type() {
         return TYPE;
      }
   }

   public record LoadsReply(int id, ChunkLoads.Snapshot loads) implements CustomPacketPayload {
      public static final Type<LoadsReply> TYPE = Net.type("loaded");

      public static final int MOST = 65536;
      public static final StreamCodec<RegistryFriendlyByteBuf, LoadsReply> CODEC = StreamCodec.of(
         (buf, r) -> {
            ChunkLoads.Snapshot s = r.loads;
            buf.writeVarInt(r.id);
            buf.writeUtf(s.dimension(), 256);
            buf.writeVarInt(s.size());
            int px = 0;
            int pz = 0;
            for (int i = 0; i < s.size(); i++) {
               buf.writeVarInt(zigzag(s.x(i) - px));
               buf.writeVarInt(zigzag(s.z(i) - pz));
               buf.writeVarInt(s.level(i) | s.reason(i) << 2 | s.status(i) << 5);
               px = s.x(i);
               pz = s.z(i);
            }
         },
         buf -> {
            int id = buf.readVarInt();
            String dim = buf.readUtf(256);
            int n = buf.readVarInt();
            if (n < 0 || n > MOST) {
               throw new IllegalArgumentException("too many chunks: " + n);
            }

            int[] x = new int[n];
            int[] z = new int[n];
            byte[] level = new byte[n];
            byte[] reason = new byte[n];
            byte[] status = new byte[n];
            int px = 0;
            int pz = 0;
            for (int i = 0; i < n; i++) {
               x[i] = px += unzigzag(buf.readVarInt());
               z[i] = pz += unzigzag(buf.readVarInt());
               int packed = buf.readVarInt();
               level[i] = (byte)(packed & 3);
               reason[i] = (byte)Math.min(ChunkLoads.OTHER, packed >> 2 & 7);
               status[i] = (byte)(packed >> 5 & 31);
            }

            return new LoadsReply(id, new ChunkLoads.Snapshot(dim, x, z, level, reason, status));
         }
      );

      private static int zigzag(int n) {
         return n << 1 ^ n >> 31;
      }

      private static int unzigzag(int n) {
         return n >>> 1 ^ -(n & 1);
      }

      @Override
      public Type<LoadsReply> type() {
         return TYPE;
      }
   }

   public record ForceRequest(int id, String dimension, ChunkSelection chunks, boolean on) implements CustomPacketPayload {
      public static final Type<ForceRequest> TYPE = Net.type("force");
      public static final StreamCodec<RegistryFriendlyByteBuf, ForceRequest> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeUtf(r.dimension, 256);
            writeSelection(buf, r.chunks);
            buf.writeBoolean(r.on);
         },
         buf -> new ForceRequest(buf.readVarInt(), buf.readUtf(256), readSelection(buf), buf.readBoolean())
      );

      @Override
      public Type<ForceRequest> type() {
         return TYPE;
      }
   }

   public record Reply(int id, boolean ok, String key, int chunks, int waiting, int later) implements CustomPacketPayload {
      public static final Type<Reply> TYPE = Net.type("reply");
      public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeVarInt(r.id);
            buf.writeBoolean(r.ok);
            buf.writeUtf(r.key, 128);
            buf.writeVarInt(r.chunks);
            buf.writeVarInt(r.waiting);
            buf.writeVarInt(r.later);
         },
         buf -> new Reply(buf.readVarInt(), buf.readBoolean(), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.isReadable() ? buf.readVarInt() : 0)
      );

      @Override
      public Type<Reply> type() {
         return TYPE;
      }
   }
}
