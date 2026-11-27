package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveWorkTest {
   @TempDir
   Path dir;

   private static final class Pretend implements LiveWork.World {
      final Set<Long> near = new HashSet<>();
      final List<String> made = new ArrayList<>();
      final Map<String, Integer> times = new HashMap<>();
      int looked;

      @Override
      public boolean busy(String dimension, int cx, int cz) {
         this.looked++;
         return this.near.contains(ServerEdit.spot(cx, cz));
      }

      @Override
      public boolean ready(ServerEdit edit) {
         return true;
      }

      @Override
      public boolean apply(ServerEdit edit, int spot) {
         String what = edit.id() + "@" + ServerEdit.spotX(edit.spots()[spot]) + "," + ServerEdit.spotZ(edit.spots()[spot]);
         this.made.add(what);
         this.times.merge(what, 1, Integer::sum);
         return true;
      }
   }

   private static ServerEdit delete(long id, int... xs) {
      long[] spots = new long[xs.length];
      byte[] actions = new byte[xs.length];
      for (int i = 0; i < xs.length; i++) {
         spots[i] = ServerEdit.spot(xs[i], 0);
         actions[i] = ServerEdit.CLEAR;
      }

      return ServerEdit.liveEdit(id, ChunkEdits.Kind.DELETE, "minecraft:overworld", "op", "", 0, 0, 0, spots, actions);
   }

   private static void save(EditJournal<ServerEdit> journal, LiveWork work, List<Long> finished) throws IOException {
      Map<Long, ServerEdit> now = new HashMap<>();
      for (ServerEdit e : work.edits()) {
         now.put(e.id(), e);
      }

      journal.update(list -> {
         list.removeIf(e -> finished.contains(e.id()));
         list.replaceAll(e -> now.getOrDefault(e.id(), e));
         return list;
      });
   }

   @Test
   void aBoundedBatchEachTickInOrder() {
      LiveWork work = new LiveWork();
      work.add(delete(1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
      Pretend world = new Pretend();
      LiveWork.Step s = work.tick(3, 24, world);
      assertEquals(3, s.made());
      assertEquals(List.of("1@0,0", "1@1,0", "1@2,0"), world.made);
      work.tick(3, 24, world);
      work.tick(3, 24, world);
      s = work.tick(3, 24, world);
      assertEquals(1, s.made());
      assertEquals(List.of(1L), s.finished());
      assertEquals(10, world.made.size());
      for (int i = 0; i < 10; i++) {
         assertEquals("1@" + i + ",0", world.made.get(i));
      }

      assertTrue(work.isEmpty());
   }

   @Test
   void aTickLooksAtABoundedNumberOfSpots() {
      LiveWork work = new LiveWork();
      int[] xs = new int[100];
      for (int i = 0; i < xs.length; i++) {
         xs[i] = i;
      }

      work.add(delete(1, xs));
      Pretend world = new Pretend();
      for (int i = 0; i < 100; i++) {
         world.near.add(ServerEdit.spot(i, 0));
      }

      work.tick(4, 16, world);
      assertEquals(16, world.looked);
      assertFalse(work.idle());

      for (int t = 0; t < 6; t++) {
         work.tick(4, 16, world);
      }

      assertTrue(work.idle(), "all waiting for nobody to be near");
      world.near.clear();
      world.looked = 0;
      LiveWork.Step s = work.tick(4, 16, world);
      assertEquals(4, s.made());
      assertTrue(world.looked <= 16);
   }

   @Test
   void editsToTheSameChunkKeepTheirOrder() {
      LiveWork work = new LiveWork();

      ServerEdit paste = ServerEdit.liveEdit(1, ChunkEdits.Kind.COPY, "minecraft:overworld", "op", "", 0, 0, 0,
         new long[]{ServerEdit.spot(5, 0)}, new byte[]{ServerEdit.PLACE});
      work.add(paste);
      work.add(delete(2, 5, 6));
      Pretend world = new Pretend();
      world.near.add(ServerEdit.spot(5, 0));
      work.tick(10, 10, world);

      assertEquals(List.of("2@6,0"), world.made);
      world.near.clear();
      work.tick(10, 10, world);
      assertEquals(List.of("2@6,0", "1@5,0", "2@5,0"), world.made);
      assertTrue(work.isEmpty());
   }

   @Test
   void aRestartCarriesOnAndMakesEachChangeOnce() throws IOException {
      Path f = this.dir.resolve("edits.journal");
      EditJournal<ServerEdit> journal = new EditJournal<>(EditJournal.file(f), ServerEdit.CODEC);
      journal.load();
      journal.add(ServerEdit.atRestart(1, ChunkEdits.Kind.KEEP_ONLY, "minecraft:overworld", "op", new ChunkSelection()));
      journal.add(delete(2, 0, 1, 2, 3, 4));
      journal.add(delete(3, 2, 9));
      LiveWork work = new LiveWork();
      for (ServerEdit e : journal.items()) {
         if (e.live()) {
            work.add(e);
         }
      }

      Pretend world = new Pretend();

      world.near.add(ServerEdit.spot(3, 0));
      LiveWork.Step s = work.tick(2, 16, world);
      save(journal, work, s.finished());
      s = work.tick(2, 16, world);
      save(journal, work, s.finished());
      s = work.tick(2, 16, world);
      save(journal, work, s.finished());
      assertEquals(List.of(3L), s.finished(), "the second delete is done; the first waits for chunk 3");
      List<String> before = List.copyOf(world.made);
      assertFalse(before.contains("2@3,0"));

      EditJournal<ServerEdit> again = new EditJournal<>(EditJournal.file(f), ServerEdit.CODEC);
      again.load();
      assertEquals(2, again.items().size(), "the restart edit and the unfinished live one");
      LiveWork after = new LiveWork();
      for (ServerEdit e : again.items()) {
         if (e.live()) {
            after.add(e);
         }
      }

      assertEquals(1, after.left());
      world.near.clear();
      s = after.tick(2, 16, world);
      save(again, after, s.finished());
      assertEquals(List.of(2L), s.finished());
      assertTrue(after.isEmpty());
      for (Map.Entry<String, Integer> e : world.times.entrySet()) {
         assertEquals(1, e.getValue(), e.getKey() + " made once");
      }

      assertEquals(7, world.made.size());

      assertTrue(world.made.indexOf("2@2,0") < world.made.indexOf("3@2,0"));
      EditJournal<ServerEdit> last = new EditJournal<>(EditJournal.file(f), ServerEdit.CODEC);
      last.load();
      assertEquals(1, last.items().size());
      assertFalse(last.items().get(0).live());
   }
}
