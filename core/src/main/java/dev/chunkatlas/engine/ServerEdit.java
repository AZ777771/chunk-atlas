package dev.chunkatlas.engine;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.BitSet;

public record ServerEdit(
   long id,
   boolean live,
   ChunkEdits.Kind kind,
   String dimension,
   String who,
   ChunkSelection chunks,
   String clip,
   int dx,
   int dz,
   boolean keep,
   int blend,
   PasteLimit limit,
   long[] spots,
   byte[] actions,
   BitSet done
) {
   public static final byte PLACE = 0;

   public static final byte CLEAR = 1;

   public static final byte CLEAR_ENTITIES = 2;

   public static ServerEdit atRestart(long id, ChunkEdits.Kind kind, String dimension, String who, ChunkSelection chunks) {
      return new ServerEdit(id, false, kind, dimension, who, chunks, "", 0, 0, false, 0, null, new long[0], new byte[0], new BitSet());
   }

   public static ServerEdit pasteAtRestart(long id, String dimension, String who, ChunkSelection chunks, String clip, int dx, int dz, boolean keep, int blend, PasteLimit limit) {
      return new ServerEdit(id, false, ChunkEdits.Kind.COPY, dimension, who, chunks, clip, dx, dz, keep, blend, limit, new long[0], new byte[0], new BitSet());
   }

   public static ServerEdit liveEdit(long id, ChunkEdits.Kind kind, String dimension, String who, String clip, int dx, int dz, int blend, long[] spots, byte[] actions) {
      if (spots.length != actions.length) {
         throw new IllegalArgumentException("spots and actions differ");
      }

      return new ServerEdit(id, true, kind, dimension, who, new ChunkSelection(), clip, dx, dz, false, blend, null, spots, actions, new BitSet());
   }

   public static long spot(int cx, int cz) {
      return (long)cx << 32 | cz & 0xFFFFFFFFL;
   }

   public static int spotX(long spot) {
      return (int)(spot >> 32);
   }

   public static int spotZ(long spot) {
      return (int)spot;
   }

   public ServerEdit withDone(BitSet done) {
      return new ServerEdit(this.id, this.live, this.kind, this.dimension, this.who, this.chunks, this.clip, this.dx, this.dz, this.keep, this.blend, this.limit,
         this.spots, this.actions, (BitSet)done.clone());
   }

   public int left() {
      return this.spots.length - this.done.cardinality();
   }

   public boolean isPaste() {
      return this.kind == ChunkEdits.Kind.COPY;
   }

   public static final EditJournal.Codec<ServerEdit> CODEC = new EditJournal.Codec<>() {
      @Override
      public void write(DataOutputStream out, ServerEdit e) throws IOException {
         out.writeLong(e.id);
         out.writeBoolean(e.live);
         out.writeUTF(e.kind.name());
         out.writeUTF(e.dimension);
         out.writeUTF(e.who);
         SelectionBytes.write(out, e.chunks);
         out.writeUTF(e.clip);
         out.writeInt(e.dx);
         out.writeInt(e.dz);
         out.writeBoolean(e.keep);
         out.writeInt(e.blend);
         out.writeBoolean(e.limit != null);
         if (e.limit != null) {
            e.limit.write(out);
         }

         out.writeInt(e.spots.length);
         for (int i = 0; i < e.spots.length; i++) {
            out.writeLong(e.spots[i]);
            out.writeByte(e.actions[i]);
         }

         byte[] done = e.done.toByteArray();
         out.writeInt(done.length);
         out.write(done);
      }

      @Override
      public ServerEdit read(DataInputStream in) throws IOException {
         long id = in.readLong();
         boolean live = in.readBoolean();
         ChunkEdits.Kind kind;
         try {
            kind = ChunkEdits.Kind.valueOf(in.readUTF());
         } catch (IllegalArgumentException e) {
            throw new IOException("unknown edit", e);
         }

         String dimension = in.readUTF();
         String who = in.readUTF();
         ChunkSelection chunks = SelectionBytes.read(in);
         String clip = in.readUTF();
         int dx = in.readInt();
         int dz = in.readInt();
         boolean keep = in.readBoolean();
         int blend = in.readInt();
         PasteLimit limit = in.readBoolean() ? PasteLimit.read(in) : null;
         int n = in.readInt();
         if (n < 0 || n > in.available() / 9) {
            throw new IOException("bad length");
         }

         long[] spots = new long[n];
         byte[] actions = new byte[n];
         for (int i = 0; i < n; i++) {
            spots[i] = in.readLong();
            actions[i] = in.readByte();
         }

         int bytes = in.readInt();
         if (bytes < 0 || bytes > in.available()) {
            throw new IOException("bad length");
         }

         byte[] done = new byte[bytes];
         in.readFully(done);
         return new ServerEdit(id, live, kind, dimension, who, chunks, clip, dx, dz, keep, blend, limit, spots, actions, BitSet.valueOf(done));
      }
   };
}
