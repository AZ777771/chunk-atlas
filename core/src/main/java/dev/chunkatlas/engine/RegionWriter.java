package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class RegionWriter {
   public static final int MAX_PACKED = 255 * 4096 - 5;

   private RegionWriter() {
   }

   public static void write(Path file, Map<Integer, byte[]> chunks, int timestamp) throws IOException {
      int sectors = 2;
      for (byte[] c : chunks.values()) {
         if (c.length <= MAX_PACKED) {
            sectors += (c.length + 5 + 4095) / 4096;
         }
      }

      ByteBuffer out = ByteBuffer.allocate(sectors * 4096);
      int at = 2;
      for (Map.Entry<Integer, byte[]> e : chunks.entrySet()) {
         byte[] c = e.getValue();
         int index = e.getKey();
         if (c.length > MAX_PACKED || index < 0 || index >= 1024) {
            continue;
         }

         int count = (c.length + 5 + 4095) / 4096;
         out.putInt(index * 4, at << 8 | count);
         out.putInt(4096 + index * 4, timestamp);
         out.position(at * 4096);
         out.putInt(c.length + 1);
         out.put((byte)2);
         out.put(c);
         at += count;
      }

      Files.createDirectories(file.getParent());
      Files.write(file, out.array());
   }
}
