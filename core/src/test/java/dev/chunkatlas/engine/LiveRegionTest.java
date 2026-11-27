package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveRegionTest {
   @TempDir
   Path dir;

   private static void put(LiveRegion r, int cx, int cz, int c) {
      int[] colour = new int[256];
      Arrays.fill(colour, 0xFF000000 | c);
      short[] height = new short[256];
      Arrays.fill(height, (short)64);
      int[] top = new int[256];
      Arrays.fill(top, c);
      short[] biome = new short[256];
      Arrays.fill(biome, (short)-1);
      r.put(cx, cz, colour, height, new byte[256], top, new int[256], biome, new byte[256]);
   }

   private static boolean knows(LiveRegion r, int cx, int cz) {
      int i = (cz & 31) * 32 + (cx & 31);
      return (r.known[i >> 6] & 1L << (i & 63)) != 0L;
   }

   private static int colourAt(LiveRegion r, int cx, int cz) {
      return r.colour[((cz & 31) * 16) * 512 + (cx & 31) * 16];
   }

   @Test
   void seeingTheSameChunkAgainChangesNothing() throws Exception {
      Path file = this.dir.resolve("r.0.0.live");
      LiveRegion r = new LiveRegion(0, 0);
      assertFalse(r.dirty());
      put(r, 3, 4, 0x336699);
      assertTrue(r.dirty());
      r.write(file);
      assertFalse(r.dirty());
      long written = Files.getLastModifiedTime(file).toMillis();

      put(r, 3, 4, 0x336699);
      assertFalse(r.dirty(), "an identical rescan needs no write");
      Files.delete(file);
      r.write(file);
      assertFalse(Files.exists(file), "nothing new, nothing written");

      put(r, 3, 4, 0x112233);
      assertTrue(r.dirty());
      assertTrue(written > 0L);
   }

   @Test
   void aFailedWriteKeepsTheRegionToWriteAgain() throws Exception {
      Path live = this.dir.resolve("live");
      Files.createDirectories(live);
      Path blocker = live.resolve("server");
      Files.writeString(blocker, "not a folder");
      Path file = blocker.resolve("minecraft@overworld").resolve(LiveRegion.name(0, 0));

      LiveRegion r = new LiveRegion(0, 0);
      put(r, 1, 2, 0x445566);
      assertThrows(IOException.class, () -> r.write(file));
      assertTrue(r.dirty(), "still to be written");

      Files.delete(blocker);
      r.write(file);
      assertFalse(r.dirty());
      LiveRegion back = LiveRegion.read(file, 0, 0);
      assertTrue(knows(back, 1, 2));
      assertEquals(0xFF445566, colourAt(back, 1, 2));
      try (var left = Files.list(file.getParent())) {
         assertEquals(1L, left.count(), "no temporary file left behind");
      }
   }

   @Test
   void aChangeMadeWhileWritingStillNeedsWriting() throws Exception {
      Path file = this.dir.resolve("r.0.0.live");
      LiveRegion r = new LiveRegion(0, 0);
      put(r, 0, 0, 0x101010);
      LiveRegion.Copy copy = r.copy();
      put(r, 5, 5, 0x202020);
      r.write(file, copy);
      assertTrue(r.dirty(), "the chunk recorded during the write isn't in the file yet");
      r.write(file);
      assertFalse(r.dirty());
      LiveRegion back = LiveRegion.read(file, 0, 0);
      assertTrue(knows(back, 0, 0));
      assertTrue(knows(back, 5, 5));
   }

   @Test
   void aRegionReadLaterKeepsWhatWasRecordedMeanwhile() throws Exception {
      Path file = this.dir.resolve("r.0.0.live");
      LiveRegion saved = new LiveRegion(0, 0);
      put(saved, 0, 0, 0x0000AA);
      put(saved, 1, 0, 0x0000BB);
      saved.write(file);

      LiveRegion now = new LiveRegion(0, 0);
      put(now, 1, 0, 0x00CC00);
      put(now, 2, 0, 0x00DD00);
      now.fillFrom(LiveRegion.read(file, 0, 0));
      assertEquals(0xFF0000AA, colourAt(now, 0, 0));
      assertEquals(0xFF00CC00, colourAt(now, 1, 0));
      assertEquals(0xFF00DD00, colourAt(now, 2, 0));
      assertTrue(now.dirty());
      now.write(file);
      LiveRegion back = LiveRegion.read(file, 0, 0);
      assertTrue(knows(back, 0, 0) && knows(back, 1, 0) && knows(back, 2, 0));

      LiveRegion idle = new LiveRegion(0, 0);
      idle.fillFrom(LiveRegion.read(file, 0, 0));
      assertFalse(idle.dirty());
      assertTrue(knows(idle, 2, 0));
   }
}
