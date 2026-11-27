package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeedMapSettingsTest {
   @TempDir
   Path dir;

   @Test
   void firstWorldStartsFromTheDefaults() throws Exception {
      Path home = this.dir.resolve("home");
      SeedMapSettings s = SeedMapSettings.load(home, Files.createDirectories(this.dir.resolve("a")));
      assertFalse(s.accepted());
      assertFalse(s.shown());
      assertTrue(s.biomes());
      assertFalse(s.structures());
      assertFalse(s.kindShown("minecraft:mineshaft"));
      assertTrue(s.kindShown("minecraft:village"));
      assertFalse(s.featuresOpen());
   }

   @Test
   void aNewWorldStartsFromTheLastChoicesAndThenKeepsItsOwn() throws Exception {
      Path home = this.dir.resolve("home");
      Path a = Files.createDirectories(this.dir.resolve("a"));
      Path b = Files.createDirectories(this.dir.resolve("b"));
      Path c = Files.createDirectories(this.dir.resolve("c"));
      SeedMapSettings first = SeedMapSettings.load(home, a);
      first.accept();
      first.show(true);
      first.slime(true);
      first.structures(false);
      first.kindShown("minecraft:mineshaft", true);
      first.kindShown("minecraft:village", false);
      first.featuresOpen(true);
      first.seed(1234L);
      first.save();

      SeedMapSettings next = SeedMapSettings.load(home, b);
      assertTrue(next.accepted());
      assertTrue(next.shown());
      assertTrue(next.slime());
      assertFalse(next.structures());
      assertTrue(next.kindShown("minecraft:mineshaft"));
      assertFalse(next.kindShown("minecraft:village"));
      assertTrue(next.featuresOpen());

      assertNull(next.seed());

      next.show(false);
      next.save();
      SeedMapSettings again = SeedMapSettings.load(home, a);
      assertTrue(again.shown());
      assertEquals(1234L, again.seed());
      SeedMapSettings third = SeedMapSettings.load(home, c);
      assertFalse(third.shown());
      assertFalse(third.accepted());
      assertTrue(third.slime());
   }
}
