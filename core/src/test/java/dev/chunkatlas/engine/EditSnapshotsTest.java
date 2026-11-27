package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditSnapshotsTest {
   @TempDir
   Path dir;

   private static byte[] chunk(int cx, int cz, String tag) throws Exception {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("DataVersion", 4000);
      root.put("xPos", cx);
      root.put("zPos", cz);
      root.put("yPos", 0);
      root.put("Status", "minecraft:full");
      root.put("test", tag);
      Map<String, Object> s = new LinkedHashMap<>();
      s.put("Y", (byte)0);
      Map<String, Object> states = new LinkedHashMap<>();
      Map<String, Object> air = new LinkedHashMap<>();
      air.put("Name", "minecraft:air");
      states.put("palette", new ArrayList<Object>(List.of(air)));
      s.put("block_states", states);
      Map<String, Object> biomes = new LinkedHashMap<>();
      biomes.put("palette", new ArrayList<Object>(List.of("minecraft:plains")));
      s.put("biomes", biomes);
      root.put("sections", new ArrayList<Object>(List.of(s)));
      return ClipArchive.deflate(Nbt.write(root));
   }

   private static String tag(Path regionFile, int cx, int cz) throws Exception {
      try (RegionFile f = RegionFile.open(regionFile)) {
         int i = ChunkSelection.index(cx, cz);
         if (!f.header().present(i)) {
            return null;
         }

         RegionFile.Chunk c = f.chunk(i);
         assertTrue(c.ok());
         return Nbt.string(Nbt.read(c.data()), "test");
      }
   }

   private static void copyIntoClipboard(Path home, String tag) throws Exception {
      Path clipboard = ChunkClips.freshClipboard(home);
      Map<Integer, byte[]> chunks = new LinkedHashMap<>();
      chunks.put(ChunkSelection.index(0, 0), chunk(0, 0, tag));
      chunks.put(ChunkSelection.index(1, 0), chunk(1, 0, tag));
      RegionWriter.write(clipboard.resolve("region").resolve("r.0.0.mca"), chunks, 1);
   }

   private static ChunkEdits.Plan paste(Path from, Path world, ChunkSelection chunks) {
      return ChunkCopy.plan(new ChunkCopy.Request(
         from, "clip", ChunkClips.dimension(from), chunks, world, "world", ChunkClips.dimension(world), 5, 5, new ChunkSelection(), 4000, 4000
      ));
   }

   @Test
   void twoWaitingPastesOfTheSameChunksEachWriteTheirOwn() throws Exception {
      Path home = this.dir.resolve("home");
      Path world = this.dir.resolve("world");
      Files.createDirectories(world.resolve("region"));
      ChunkSelection picked = new ChunkSelection();
      picked.add(0, 0);
      picked.add(1, 0);

      copyIntoClipboard(home, "first");
      Path one = EditSnapshots.take(home, ChunkClips.dimension(ChunkClips.clipboard(home)), picked);
      copyIntoClipboard(home, "second");
      Path two = EditSnapshots.take(home, ChunkClips.dimension(ChunkClips.clipboard(home)), picked);
      assertFalse(one.equals(two));
      assertEquals(2L, EditSnapshots.count(home));

      Path region = world.resolve("region").resolve("r.0.0.mca");
      ChunkEdits.Context ctx = new ChunkEdits.Context(home, () -> true, () -> true);
      ChunkEdits.Plan first = paste(one, world, picked);
      assertEquals(2, first.chunks());
      assertTrue(ChunkEdits.apply(first, ctx, null, false).ok());
      EditSnapshots.drop(home, one);
      assertEquals("first", tag(region, 5, 5));
      assertEquals("first", tag(region, 6, 5));

      ChunkEdits.Plan second = paste(two, world, picked);
      assertEquals(2, second.chunks());
      assertTrue(ChunkEdits.apply(second, ctx, null, false).ok());
      EditSnapshots.drop(home, two);
      assertEquals("second", tag(region, 5, 5));
      assertEquals("second", tag(region, 6, 5));

      assertEquals(0L, EditSnapshots.count(home));
      assertEquals("second", tag(ChunkClips.clipboard(home).resolve("region").resolve("r.0.0.mca"), 0, 0));
   }

   @Test
   void onlyTheChunksFilesAreTaken() throws Exception {
      Path home = this.dir.resolve("home");
      Path src = this.dir.resolve("src");
      Files.createDirectories(src.resolve("region"));
      Files.createDirectories(src.resolve("entities"));
      Map<Integer, byte[]> near = Map.of(ChunkSelection.index(0, 0), chunk(0, 0, "near"));
      Map<Integer, byte[]> far = Map.of(ChunkSelection.index(64, 0), chunk(64, 0, "far"));
      RegionWriter.write(src.resolve("region").resolve("r.0.0.mca"), near, 1);
      RegionWriter.write(src.resolve("region").resolve("r.2.0.mca"), far, 1);
      RegionWriter.write(src.resolve("entities").resolve("r.0.0.mca"), near, 1);
      ChunkSelection picked = new ChunkSelection();
      picked.add(0, 0);

      Path taken = EditSnapshots.take(home, ChunkClips.dimension(src), picked);
      assertTrue(Files.isRegularFile(taken.resolve("region").resolve("r.0.0.mca")));
      assertTrue(Files.isRegularFile(taken.resolve("entities").resolve("r.0.0.mca")));
      assertFalse(Files.exists(taken.resolve("region").resolve("r.2.0.mca")));

      EditSnapshots.drop(home, src);
      assertTrue(Files.isDirectory(src.resolve("region")));
      EditSnapshots.drop(home, taken);
      assertFalse(Files.exists(taken));
   }

   @Test
   void foldersFromAnEarlierGameAreCleared() throws Exception {
      Path home = this.dir.resolve("home");
      Path left = EditSnapshots.dir(home).resolve("0-1");
      Files.createDirectories(left.resolve("region"));
      Files.writeString(left.resolve("region").resolve("r.0.0.mca"), "x");
      Path oldFields = ChunkClips.freshScratch(home, "fields");
      Path mine = EditSnapshots.fresh(home);

      EditSnapshots.clearOld(home);
      assertFalse(Files.exists(left));
      assertFalse(Files.exists(oldFields));
      assertTrue(Files.isDirectory(mine));
   }
}
