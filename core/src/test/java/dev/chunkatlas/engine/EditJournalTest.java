package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditJournalTest {
   @TempDir
   Path dir;

   private static final class Flaky implements EditJournal.Store {
      final EditJournal.Store real;
      boolean failing;

      Flaky(Path f) {
         this.real = EditJournal.file(f);
      }

      @Override
      public byte[] load() throws IOException {
         return this.real.load();
      }

      @Override
      public void save(byte[] data) throws IOException {
         if (this.failing) {
            throw new IOException("disk full");
         }

         this.real.save(data);
      }

      @Override
      public void delete() throws IOException {
         if (this.failing) {
            throw new IOException("disk gone");
         }

         this.real.delete();
      }
   }

   private static ChunkSelection chunks(int... xz) {
      ChunkSelection s = new ChunkSelection();
      for (int i = 0; i < xz.length; i += 2) {
         s.add(xz[i], xz[i + 1]);
      }

      return s;
   }

   private EditJournal<ServerEdit> open(EditJournal.Store store) throws IOException {
      EditJournal<ServerEdit> j = new EditJournal<>(store, ServerEdit.CODEC);
      j.load();
      return j;
   }

   private List<Long> ids(EditJournal<ServerEdit> j) {
      List<Long> out = new ArrayList<>();
      for (ServerEdit e : j.items()) {
         out.add(e.id());
      }

      return out;
   }

   @Test
   void aMadeEditIsNeverMadeAgainWhenALaterOneFails() throws IOException {
      Path f = this.dir.resolve("edits.journal");
      EditJournal<ServerEdit> j = this.open(EditJournal.file(f));
      j.add(ServerEdit.atRestart(1, ChunkEdits.Kind.DELETE, "minecraft:overworld", "op", chunks(0, 0)));
      j.add(ServerEdit.pasteAtRestart(2, "minecraft:overworld", "op", chunks(1, 1), "clip", 3, 4, false, 2, null));
      j.add(ServerEdit.atRestart(3, ChunkEdits.Kind.DELETE_MOBS, "minecraft:overworld", "op", chunks(2, 2)));
      Map<Long, Integer> made = new HashMap<>();

      int done = j.drain(e -> !e.live(), e -> {
         made.merge(e.id(), 1, Integer::sum);
         return e.id() != 2;
      });
      assertEquals(1, done);
      assertEquals(List.of(2L, 3L), this.ids(j));

      EditJournal<ServerEdit> again = this.open(EditJournal.file(f));
      assertEquals(List.of(2L, 3L), this.ids(again));
      again.drain(e -> !e.live(), e -> {
         made.merge(e.id(), 1, Integer::sum);
         return true;
      });
      assertEquals(1, made.get(1L));
      assertEquals(2, made.get(2L));
      assertEquals(1, made.get(3L));
      assertTrue(this.open(EditJournal.file(f)).items().isEmpty());
      assertTrue(!Files.exists(f));
   }

   @Test
   void cancelWithAFailingDiskChangesNothing() throws IOException {
      Path f = this.dir.resolve("edits.journal");
      Flaky store = new Flaky(f);
      EditJournal<ServerEdit> j = this.open(store);
      j.add(ServerEdit.atRestart(1, ChunkEdits.Kind.DELETE, "minecraft:overworld", "op", chunks(0, 0)));
      j.add(ServerEdit.atRestart(2, ChunkEdits.Kind.DELETE, "minecraft:overworld", "op", chunks(5, 5)));
      store.failing = true;
      assertThrows(IOException.class, () -> j.remove(e -> !e.live()));

      assertEquals(List.of(1L, 2L), this.ids(j));
      assertEquals(List.of(1L, 2L), this.ids(this.open(EditJournal.file(f))));

      assertThrows(IOException.class, () -> j.add(ServerEdit.atRestart(3, ChunkEdits.Kind.DELETE, "minecraft:overworld", "op", chunks(9, 9))));
      assertEquals(List.of(1L, 2L), this.ids(j));
      store.failing = false;
      assertEquals(2, j.remove(e -> !e.live()).size());
      assertTrue(this.open(EditJournal.file(f)).items().isEmpty());
   }

   @Test
   void editsRoundTrip() throws IOException {
      Path f = this.dir.resolve("edits.journal");
      EditJournal<ServerEdit> j = this.open(EditJournal.file(f));
      ChunkSelection guard = chunks(1, 1);
      PasteLimit limit = PasteLimit.of(chunks(0, 0, 1, 1, 0, 1), 2, true, guard, (x, z) -> true, true);
      j.add(ServerEdit.pasteAtRestart(7, "minecraft:the_nether", "op", chunks(0, 0, 1, 1), "clip", -3, 4, true, 2, limit));
      ServerEdit live = ServerEdit.liveEdit(8, ChunkEdits.Kind.COPY, "minecraft:overworld", "op", "file", 1, 2, 3,
         new long[]{ServerEdit.spot(-5, 7), ServerEdit.spot(1, -1)}, new byte[]{ServerEdit.PLACE, ServerEdit.CLEAR});
      java.util.BitSet done = new java.util.BitSet();
      done.set(1);
      j.add(live.withDone(done));
      List<ServerEdit> back = this.open(EditJournal.file(f)).items();
      ServerEdit p = back.get(0);
      assertEquals(ChunkEdits.Kind.COPY, p.kind());
      assertEquals("minecraft:the_nether", p.dimension());
      assertEquals(2, p.chunks().size());
      assertEquals(-3, p.dx());
      assertTrue(p.keep());
      assertTrue(p.limit().protects(1, 1));
      assertEquals(limit.ring().size(), p.limit().ring().size());
      ServerEdit l = back.get(1);
      assertTrue(l.live());
      assertEquals(-5, ServerEdit.spotX(l.spots()[0]));
      assertEquals(7, ServerEdit.spotZ(l.spots()[0]));
      assertEquals(ServerEdit.CLEAR, l.actions()[1]);
      assertTrue(l.done().get(1));
      assertEquals(1, l.left());
   }

   @Test
   void anUnreadableListSaysSo() throws IOException {
      Path f = this.dir.resolve("edits.journal");
      Files.write(f, new byte[]{1, 2, 3});
      assertThrows(IOException.class, () -> this.open(EditJournal.file(f)));
   }
}
