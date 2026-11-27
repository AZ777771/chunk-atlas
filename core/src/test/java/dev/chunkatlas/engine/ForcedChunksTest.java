package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForcedChunksTest {
   @TempDir
   Path dir;

   private static WorldLayout.Dimension dimension(Path region) {
      return new WorldLayout.Dimension("minecraft:overworld", "Overworld", region, Optional.empty(), Optional.empty(), 0);
   }

   private static ChunkSelection chunks(int... xz) {
      ChunkSelection s = new ChunkSelection();
      for (int i = 0; i < xz.length; i += 2) {
         s.add(xz[i], xz[i + 1]);
      }

      return s;
   }

   private static Map<String, Object> document(Path file) throws Exception {
      try (InputStream in = Files.newInputStream(file)) {
         return Nbt.read(Packed.gunzip(in, 1 << 20));
      }
   }

   @Test
   void theFileIsWhereThisMinecraftKeepsIt() throws Exception {
      Path old = this.dir.resolve("old");
      Path custom = Files.createDirectories(old.resolve("dimensions").resolve("pack").resolve("sky").resolve("region"));
      assertNull(ForcedChunks.file(dimension(custom)));
      assertNull(ForcedChunks.file(dimension(old.resolve("region"))));
      Path region = Files.createDirectories(this.dir.resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region"));
      assertEquals(region.getParent().resolve("data").resolve("minecraft").resolve("chunk_tickets.dat"), ForcedChunks.file(dimension(region)));
   }

   @Test
   void forceLoadingKeepsOtherTicketsAndTheDataVersion() throws Exception {
      Path file = this.dir.resolve("chunk_tickets.dat");
      Map<String, Object> portal = new LinkedHashMap<>();
      portal.put("chunk_pos", new int[]{5, 5});
      portal.put("type", "minecraft:portal");
      portal.put("level", 31);
      portal.put("ticks_left", 120L);
      List<Object> tickets = new ArrayList<>(List.of(portal));
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("tickets", tickets);
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("data", data);
      root.put("DataVersion", 4999);
      try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
         out.write(Nbt.write(root));
      }

      assertEquals(2, ForcedChunks.write(file, chunks(1, 2, -3, 4), true, 5023));
      assertEquals(0, ForcedChunks.write(file, chunks(1, 2), true, 5023));
      ChunkSelection forced = ForcedChunks.read(file);
      assertEquals(2, forced.size());
      assertTrue(forced.contains(-3, 4));
      Map<String, Object> after = document(file);
      assertEquals(4999, ((Number)after.get("DataVersion")).intValue());
      assertEquals(3, Nbt.list(after.get("data"), "tickets").size());

      assertEquals(1, ForcedChunks.write(file, chunks(1, 2, 9, 9), false, 5023));
      forced = ForcedChunks.read(file);
      assertFalse(forced.contains(1, 2));
      assertTrue(forced.contains(-3, 4));
      assertEquals("minecraft:portal", Nbt.string(Nbt.list(document(file).get("data"), "tickets").get(0), "type"));
   }

   @Test
   void aNewFileSaysTheWorldsVersionAndEmptiesCleanly() throws Exception {
      Path file = this.dir.resolve("data").resolve("minecraft").resolve("chunk_tickets.dat");
      assertEquals(0, ForcedChunks.read(file).size());
      assertEquals(1, ForcedChunks.write(file, chunks(0, 0), true, 5023));
      assertEquals(5023, ((Number)document(file).get("DataVersion")).intValue());
      Map<String, Object> ticket = Nbt.map(Map.of("t", Nbt.list(document(file).get("data"), "tickets").get(0)), "t");
      assertEquals("minecraft:forced", ticket.get("type"));
      assertEquals(31, ((Number)ticket.get("level")).intValue());
      assertEquals(1, ForcedChunks.write(file, chunks(0, 0), false, 5023));
      assertFalse(Nbt.has(document(file).get("data"), "tickets"));
   }
}
