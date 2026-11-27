package dev.chunkatlas.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class TaskBook {
   private static final int MAGIC = 0x43415453;
   private static final int VERSION = 1;

   public static final int MAX_TASKS = 64;
   public static final int MAX_AT_ONCE = 8;

   public enum State {
      RUNNING,
      WAITING,
      PAUSED,
      DONE,
      CANCELLED;

      public boolean active() {
         return this == RUNNING || this == WAITING;
      }

      public boolean ended() {
         return this == DONE || this == CANCELLED;
      }

      public static State of(int n) {
         State[] all = values();
         return all[Math.max(0, Math.min(all.length - 1, n))];
      }
   }

   public static final class Task {
      public final int id;
      public final String dimension;
      public final Area area;
      public final ChunkSelection picked;

      public final int speed;
      public final boolean finishPartial;

      public final int total;
      public State state;

      public final ChunkSelection dealt;

      public int generated;

      public Task(int id, String dimension, Area area, ChunkSelection picked, int speed, boolean finishPartial, int total, State state, ChunkSelection dealt, int generated) {
         if (area == null == (picked == null)) {
            throw new IllegalArgumentException("a task has an area or picked chunks");
         }

         this.id = id;
         this.dimension = dimension;
         this.area = area;
         this.picked = picked;
         this.speed = speed;
         this.finishPartial = finishPartial;
         this.total = total;
         this.state = state;
         this.dealt = dealt == null ? new ChunkSelection() : dealt;
         this.generated = generated;
      }

      public static Task of(int id, String dimension, Area area, int speed, boolean finishPartial) {
         long n = area.chunkCount();
         return new Task(id, dimension, area, null, speed, finishPartial, (int)Math.min(Integer.MAX_VALUE, n), State.WAITING, null, 0);
      }

      public static Task picked(int id, String dimension, ChunkSelection chunks, int speed, boolean finishPartial) {
         return new Task(id, dimension, null, chunks.copy(), speed, finishPartial, chunks.size(), State.WAITING, null, 0);
      }

      public boolean covers(int chunkX, int chunkZ) {
         return this.area != null ? this.area.containsChunk(chunkX, chunkZ) : this.picked.contains(chunkX, chunkZ);
      }

      public ChunkSelection left() {
         ChunkSelection out = this.area != null ? this.area.chunks() : this.picked.copy();
         for (long region : this.dealt.regions()) {
            out.remove((int)(region >> 32), (int)region, this.dealt.bits((int)(region >> 32), (int)region));
         }

         return out;
      }

      public int dealtCount() {
         return Math.min(this.total, this.dealt.size());
      }
   }

   public final List<Task> tasks = new ArrayList<>();

   public int atOnce = 1;
   public int nextId = 1;

   public Task find(int id) {
      for (Task t : this.tasks) {
         if (t.id == id) {
            return t;
         }
      }

      return null;
   }

   public byte[] write() {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(bytes)) {
         out.writeInt(MAGIC);
         out.writeInt(VERSION);
         out.writeInt(this.atOnce);
         out.writeInt(this.nextId);
         List<Task> kept = new ArrayList<>();
         for (Task t : this.tasks) {
            if (!t.state.ended()) {
               kept.add(t);
            }
         }

         out.writeInt(kept.size());
         for (Task t : kept) {
            out.writeInt(t.id);
            out.writeUTF(t.dimension);
            out.writeBoolean(t.area != null);
            if (t.area != null) {
               out.writeUTF(t.area.write());
            } else {
               writeSelection(out, t.picked);
            }

            out.writeByte(t.speed);
            out.writeBoolean(t.finishPartial);
            out.writeInt(t.total);

            out.writeByte(t.state.ordinal());
            out.writeInt(t.generated);
            writeSelection(out, t.dealt);
         }
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }

      return bytes.toByteArray();
   }

   public static TaskBook read(byte[] data) {
      TaskBook book = new TaskBook();
      if (data == null || data.length < 8) {
         return book;
      }

      try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
         if (in.readInt() != MAGIC || in.readInt() > VERSION) {
            return book;
         }

         book.atOnce = Math.max(1, Math.min(MAX_AT_ONCE, in.readInt()));
         book.nextId = Math.max(1, in.readInt());
         int n = in.readInt();
         if (n < 0 || n > MAX_TASKS) {
            return new TaskBook();
         }

         for (int i = 0; i < n; i++) {
            int id = in.readInt();
            String dim = in.readUTF();
            Area area = null;
            ChunkSelection picked = null;
            if (in.readBoolean()) {
               area = Area.read(in.readUTF());
               if (area == null) {
                  return new TaskBook();
               }
            } else {
               picked = readSelection(in);
            }

            int speed = in.readByte();
            boolean finish = in.readBoolean();
            int total = in.readInt();
            State state = State.of(in.readByte());
            int generated = in.readInt();
            ChunkSelection dealt = readSelection(in);
            book.tasks.add(new Task(id, dim, area, picked, speed, finish, total, state, dealt, generated));
            book.nextId = Math.max(book.nextId, id + 1);
         }
      } catch (IOException | RuntimeException e) {
         return new TaskBook();
      }

      return book;
   }

   static void writeSelection(DataOutputStream out, ChunkSelection sel) throws IOException {
      List<Long> regions = new ArrayList<>(sel.regions());
      out.writeInt(regions.size());
      for (long key : regions) {
         int rx = (int)(key >> 32);
         int rz = (int)key;
         out.writeInt(rx);
         out.writeInt(rz);
         for (long w : sel.bits(rx, rz)) {
            out.writeLong(w);
         }
      }
   }

   static ChunkSelection readSelection(DataInputStream in) throws IOException {
      int n = in.readInt();
      if (n < 0 || n > 4_000_000) {
         throw new IOException("bad selection");
      }

      ChunkSelection sel = new ChunkSelection();
      for (int i = 0; i < n; i++) {
         int rx = in.readInt();
         int rz = in.readInt();
         long[] bits = new long[ChunkSelection.WORDS];
         for (int w = 0; w < bits.length; w++) {
            bits[w] = in.readLong();
         }

         sel.add(rx, rz, bits);
      }

      return sel;
   }
}
