package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClipArchiveTest {
   @TempDir
   Path dir;

   private static byte[] doc(String key, String value) throws java.io.IOException {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("DataVersion", 4000);
      root.put(key, value);
      return ClipArchive.deflate(Nbt.write(root));
   }

   @Test
   void pointsOfInterestRoundTrip() throws Exception {
      ClipArchive.Entry a = new ClipArchive.Entry(3, -2, doc("t", "chunk a"), doc("t", "ents a"), doc("t", "poi a"));
      ClipArchive.Entry b = new ClipArchive.Entry(40, 7, doc("t", "chunk b"), null, null);
      byte[] packed = ClipArchive.write(new ClipArchive.Header("world", "minecraft:overworld", 4000, 2), List.of(a, b));
      ClipArchive.Archive back = ClipArchive.read(packed, 10);
      assertArrayEquals(a.poi(), back.entries().get(0).poi());
      assertArrayEquals(a.entities(), back.entries().get(0).entities());
      assertNull(back.entries().get(1).poi());

      Path clip = this.dir.resolve("clip");
      Files.createDirectories(clip.resolve("region"));
      ClipArchive.toClip(back, clip, "test");
      assertTrue(Files.isRegularFile(clip.resolve("poi").resolve(RegionFile.name(0, -1))));
      ClipArchive.Archive again = ClipArchive.read(ClipArchive.fromClip(clip, "world", "minecraft:overworld", 4000, 10), 10);
      assertEquals(2, again.entries().size());
      for (ClipArchive.Entry e : again.entries()) {
         if (e.cx() == 3) {
            assertEquals("poi a", Nbt.read(ClipArchive.inflate(e.poi())).get("t"));
            assertEquals("ents a", Nbt.read(ClipArchive.inflate(e.entities())).get("t"));
         } else {
            assertNull(e.poi());
            assertNull(e.entities());
         }
      }
   }

   @Test
   void olderArchivesStillRead() throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      byte[] chunk = doc("t", "old");
      out.writeInt(0x43415458);
      out.writeInt(1);
      out.writeUTF("world");
      out.writeUTF("minecraft:overworld");
      out.writeInt(3000);
      out.writeInt(1);
      out.writeInt(5);
      out.writeInt(6);
      out.writeInt(chunk.length);
      out.write(chunk);
      out.writeInt(0);
      ClipArchive.Archive a = ClipArchive.read(bytes.toByteArray(), 10);
      assertEquals(1, a.entries().size());
      assertNull(a.entries().get(0).poi());
      assertNull(a.entries().get(0).entities());
      assertEquals(3000, a.header().dataVersion());
   }
}
