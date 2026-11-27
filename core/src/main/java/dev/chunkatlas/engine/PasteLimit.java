package dev.chunkatlas.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public record PasteLimit(ChunkSelection protect, ChunkSelection ring, ChunkSelection band) {
   private static final int MAGIC = 0x4341504C;
   private static final int VERSION = 1;

   public static ChunkSelection placed(ChunkSelection shown, boolean keep, ChunkSelection protect, BlendRing.Saved saved) {
      ChunkSelection placed = new ChunkSelection();
      shown.forEach((x, z) -> {
         if ((protect == null || !protect.contains(x, z)) && (!keep || saved == null || !saved.has(x, z))) {
            placed.add(x, z);
         }
      });
      return placed;
   }

   public static PasteLimit of(ChunkSelection shown, int blend, boolean keep, ChunkSelection protect, BlendRing.Saved saved, boolean band) {
      ChunkSelection guard = protect == null ? new ChunkSelection() : protect;
      ChunkSelection placed = placed(shown, keep, guard, saved);
      ChunkSelection ring = BlendRing.ring(placed, blend, guard, saved);
      ChunkSelection outer = band ? BlendRing.band(placed, blend, guard, saved) : new ChunkSelection();
      ChunkSelection near = new ChunkSelection();
      int reach = Math.max(0, Math.min(blend, ChunkCopy.MAX_BLEND)) + (band ? BlendRing.BAND : 0);
      ChunkSelection area = reach > 0 ? BlendRing.around(shown, 1, reach, null, null) : new ChunkSelection();
      guard.forEach((x, z) -> {
         if (shown.contains(x, z) || area.contains(x, z)) {
            near.add(x, z);
         }
      });
      return new PasteLimit(near, ring, outer);
   }

   public boolean protects(int x, int z) {
      return this.protect.contains(x, z);
   }

   public ChunkSelection ring(ChunkSelection placed, int blend) {
      return within(BlendRing.ring(placed, blend, this.protect, null), this.ring);
   }

   public ChunkSelection band(ChunkSelection placed, int blend) {
      return within(BlendRing.band(placed, blend, this.protect, null), this.band);
   }

   private static ChunkSelection within(ChunkSelection sel, ChunkSelection allowed) {
      ChunkSelection out = new ChunkSelection();
      sel.forEach((x, z) -> {
         if (allowed.contains(x, z)) {
            out.add(x, z);
         }
      });
      return out;
   }

   public void write(DataOutputStream out) throws IOException {
      SelectionBytes.write(out, this.protect);
      SelectionBytes.write(out, this.ring);
      SelectionBytes.write(out, this.band);
   }

   public static PasteLimit read(DataInputStream in) throws IOException {
      return new PasteLimit(SelectionBytes.read(in), SelectionBytes.read(in), SelectionBytes.read(in));
   }

   public record Upload(PasteLimit limit, byte[] archive) {
   }

   public static byte[] wrap(PasteLimit limit, byte[] archive) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(archive.length + 4096);
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(MAGIC);
      out.writeInt(VERSION);
      limit.write(out);
      out.writeInt(archive.length);
      out.write(archive);
      return bytes.toByteArray();
   }

   public static Upload unwrap(byte[] data) throws IOException {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
      if (in.readInt() != MAGIC || in.readInt() != VERSION) {
         throw new IOException("not a Chunk Atlas paste");
      }

      PasteLimit limit = read(in);
      int n = in.readInt();
      if (n < 0 || n > in.available()) {
         throw new IOException("cut short");
      }

      byte[] archive = new byte[n];
      in.readFully(archive);
      return new Upload(limit, archive);
   }
}
