package dev.chunkatlas.engine;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SelectionBytes {
   public static final int MAX_REGIONS = 4096;

   private SelectionBytes() {
   }

   public static void write(DataOutput out, ChunkSelection chunks) throws IOException {
      List<Long> regions = new ArrayList<>(chunks.regions());
      regions.sort(null);
      out.writeInt(regions.size());
      for (long key : regions) {
         int rx = (int)(key >> 32);
         int rz = (int)key;
         out.writeInt(rx);
         out.writeInt(rz);
         for (long word : chunks.bits(rx, rz)) {
            out.writeLong(word);
         }
      }
   }

   public static ChunkSelection read(DataInput in) throws IOException {
      int n = in.readInt();
      if (n < 0 || n > MAX_REGIONS) {
         throw new IOException("too many regions: " + n);
      }

      ChunkSelection sel = new ChunkSelection();
      for (int r = 0; r < n; r++) {
         int rx = in.readInt();
         int rz = in.readInt();
         long[] mask = new long[ChunkSelection.WORDS];
         for (int w = 0; w < mask.length; w++) {
            mask[w] = in.readLong();
         }

         sel.add(rx, rz, mask);
      }

      return sel;
   }
}
