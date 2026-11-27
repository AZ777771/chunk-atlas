package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupListTest {
   private static final String KEY = "0123456789abcdef0123";

   @TempDir
   Path home;

   private Path make(String id, String state, long created, int chunks, int bytes) throws IOException {
      Path dir = this.home.resolve("backups").resolve(KEY).resolve(id);
      Files.createDirectories(dir.resolve("files"));
      Files.write(dir.resolve("files").resolve("r.0.0.mca"), new byte[bytes]);
      ChunkEdits.Manifest m = new ChunkEdits.Manifest();
      m.id = id;
      m.world = "/games/saves/Survival 2";
      m.dimension = "minecraft:overworld";
      m.kind = "DELETE";
      m.chunks = chunks;
      m.created = created;
      m.stamp = created;
      m.state = state;
      Files.write(dir.resolve("edit.txt"), ChunkEdits.format(m).getBytes(StandardCharsets.UTF_8));
      return dir;
   }

   @Test
   void listsNewestFirstWithSizesAndSkipsEditsInProgress() throws IOException {
      this.make("20260101-100000-aaaa", "applied", 1_000L, 5, 100);
      this.make("20260102-100000-bbbb", "applied", 2_000L, 7, 300);
      this.make("20260103-100000-cccc", "journaled", 3_000L, 9, 50);
      List<ChunkEdits.Backup> list = ChunkEdits.listBackups(this.home);
      assertEquals(2, list.size());
      assertEquals("20260102-100000-bbbb", list.get(0).id());
      assertEquals("Survival 2", list.get(0).world());
      assertEquals(ChunkEdits.Kind.DELETE, list.get(0).kind());
      assertEquals(7, list.get(0).chunks());
      assertTrue(list.get(0).bytes() >= 300L);
   }

   @Test
   void deletesOnlyOneBackupFolder() throws IOException {
      Path a = this.make("20260101-100000-aaaa", "applied", 1_000L, 5, 100);
      Path b = this.make("20260102-100000-bbbb", "applied", 2_000L, 7, 300);
      Path outside = this.home.resolve("keep.txt");
      Files.writeString(outside, "x");
      assertTrue(ChunkEdits.deleteBackup(this.home, KEY, "20260101-100000-aaaa"));
      assertFalse(Files.exists(a));
      assertTrue(Files.exists(b));

      assertFalse(ChunkEdits.deleteBackup(this.home, KEY, "../../keep.txt"));
      assertFalse(ChunkEdits.deleteBackup(this.home, "..", "20260102-100000-bbbb"));
      assertTrue(Files.exists(outside));
      assertTrue(ChunkEdits.deleteBackup(this.home, KEY, "20260102-100000-bbbb"));
      assertFalse(Files.exists(this.home.resolve("backups").resolve(KEY)));
   }

   @Test
   void leavesAnEditInProgressAlone() throws IOException {
      Path c = this.make("20260103-100000-cccc", "journaled", 3_000L, 9, 50);
      assertFalse(ChunkEdits.deleteBackup(this.home, KEY, "20260103-100000-cccc"));
      assertTrue(Files.exists(c));
   }
}
