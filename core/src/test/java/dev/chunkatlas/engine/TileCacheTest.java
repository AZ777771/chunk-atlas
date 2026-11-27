package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TileCacheTest {
   private static final String DIM = "minecraft:overworld";

   @TempDir
   Path home;

   private static int[][] levels(long seed) {
      Random r = new Random(seed);
      int[] base = new int[512 * 512];
      for (int i = 0; i < base.length; i++) {
         base[i] = (i / 37 + i / 512 / 23) % 5 == 0 ? 0 : 0xFF000000 | (r.nextInt(4) * 0x203040 + 0x305020);
      }

      int[][] levels = new int[5][];
      levels[0] = base;
      for (int i = 1; i < 5; i++) {
         levels[i] = Tile.half(levels[i - 1], 512 >> i - 1);
      }

      return levels;
   }

   private static WorldLayout.RegionInfo region(int rx, int rz, long size, long modified) {
      return new WorldLayout.RegionInfo(rx, rz, Path.of("r." + rx + "." + rz + ".mca"), size, modified, false);
   }

   private Path file(Path world) {
      return TileCache.root(this.home).resolve(TileCache.worldKey(world) + ".atlas");
   }

   @Test
   void everyLevelComesBackExactlyAfterReopening() {
      Path world = this.home.resolve("world");
      int[][] levels = levels(1);
      TileCache c = new TileCache(this.home, world);
      c.store(DIM, region(3, -2, 100, 200), 7L, "look", levels, new RegionFacts());
      c.close();

      TileCache again = new TileCache(this.home, world);
      for (int lv = 0; lv < 5; lv++) {
         Tile t = again.load(DIM, region(3, -2, 100, 200), 7L, "look", lv, true, lv == 0);
         assertNotNull(t, "level " + lv);
         assertArrayEquals(levels[lv], t.argb, "level " + lv);
      }

      again.close();
   }

   @Test
   void outOfDateOrOtherLookIsRefused() {
      Path world = this.home.resolve("world");
      TileCache c = new TileCache(this.home, world);
      c.store(DIM, region(0, 0, 100, 200), 7L, "look", levels(2), new RegionFacts());
      assertNull(c.load(DIM, region(0, 0, 100, 999), 7L, "look", 0, true, false), "region changed");
      assertNull(c.load(DIM, region(0, 0, 101, 200), 7L, "look", 0, true, false), "region size changed");
      assertNull(c.load(DIM, region(0, 0, 100, 200), 8L, "look", 0, true, false), "entities changed");
      assertNull(c.load(DIM, region(0, 0, 100, 200), 7L, "other", 0, true, false), "other look");
      assertNull(c.load("minecraft:the_nether", region(0, 0, 100, 200), 7L, "look", 0, true, false), "other dimension");

      assertNotNull(c.load(DIM, region(0, 0, 100, 999), 7L, "look", 0, true, false, true));
      c.close();
   }

   @Test
   void aRecordCutShortIsDropped() throws Exception {
      Path world = this.home.resolve("world");
      int[][] levels = levels(3);
      TileCache c = new TileCache(this.home, world);
      c.store(DIM, region(1, 1, 10, 20), 1L, "look", levels, new RegionFacts());
      c.close();
      Path f = this.file(world);
      long good = Files.size(f);

      try (FileChannel ch = FileChannel.open(f, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
         ch.write(ByteBuffer.allocate(12).putInt(5000).putInt(1).putInt(2).flip());
      }

      TileCache again = new TileCache(this.home, world);
      Tile t = again.load(DIM, region(1, 1, 10, 20), 1L, "look", 2, true, false);
      assertNotNull(t);
      assertArrayEquals(levels[2], t.argb);
      again.close();
      assertEquals(good, Files.size(f), "the broken tail is cut off");
   }

   @Test
   void compactingKeepsOnlyTheNewest() throws Exception {
      Path world = this.home.resolve("world");
      int[][] first = levels(4);
      int[][] second = levels(5);
      TileCache c = new TileCache(this.home, world);
      c.store(DIM, region(0, 0, 10, 20), 1L, "look", first, new RegionFacts());
      c.store(DIM, region(0, 1, 10, 20), 1L, "look", first, new RegionFacts());
      c.store(DIM, region(0, 0, 11, 21), 1L, "look", second, new RegionFacts());
      long before = Files.size(this.file(world));
      assertNotNull(c.load(DIM, region(0, 0, 11, 21), 1L, "look", 0, true, false));
      c.compact();
      assertTrue(Files.size(this.file(world)) < before, "smaller after compacting");
      assertArrayEquals(second[0], c.load(DIM, region(0, 0, 11, 21), 1L, "look", 0, true, false).argb);
      assertArrayEquals(first[1], c.load(DIM, region(0, 1, 10, 20), 1L, "look", 1, true, false).argb);
      assertNull(c.load(DIM, region(0, 0, 10, 20), 1L, "look", 0, true, false), "the older copy is gone");
      c.close();
      TileCache again = new TileCache(this.home, world);
      assertArrayEquals(second[0], again.load(DIM, region(0, 0, 11, 21), 1L, "look", 0, true, false).argb);
      again.close();
   }
}
