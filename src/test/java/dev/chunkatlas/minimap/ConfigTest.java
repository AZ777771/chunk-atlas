package dev.chunkatlas.minimap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class ConfigTest {
   private static Config.RadarStyle read(String style) {
      return new Gson().fromJson("{\"radarStyle\":\"" + style + "\"}", Config.class).radarStyle;
   }

   @Test
   void oldIconsStyleReadsAsFaces() {
      assertEquals(Config.RadarStyle.FACES, read("ICONS"));
      assertEquals(Config.RadarStyle.FACES, read("FACES"));
      assertEquals(Config.RadarStyle.DOTS, read("DOTS"));
      assertEquals(Config.RadarStyle.ARROWS, read("ARROWS"));
      assertEquals("\"FACES\"", new Gson().toJson(Config.RadarStyle.FACES));
   }

   @Test
   void oldFilesWithItemsOnTheRadarStillRead() {
      Config c = new Gson().fromJson("{\"radarItems\":true,\"radarHostile\":false,\"size\":\"LARGE\"}", Config.class);
      assertEquals(false, c.radarHostile);
      assertEquals(Config.Size.LARGE, c.size);
      assertEquals(0, c.customSize);
   }

   @Test
   void aDraggedSizeIsKept() {
      Config c = new Gson().fromJson("{\"size\":\"CUSTOM\",\"customSize\":150}", Config.class);
      assertEquals(Config.Size.CUSTOM, c.size);
      assertEquals(150, dev.chunkatlas.minimap.hud.MinimapHud.boxSize(c, 427, 240));

      assertEquals(dev.chunkatlas.minimap.hud.MinimapHud.CUSTOM_MIN, dev.chunkatlas.minimap.hud.MinimapHud.customSize(10, 427, 240, 0));
      assertEquals(219, dev.chunkatlas.minimap.hud.MinimapHud.customSize(500, 427, 240, 13));
   }

   @Test
   void facesByDefault() {
      assertEquals(Config.RadarStyle.FACES, new Config().radarStyle);
      assertEquals(Config.RadarStyle.FACES, new Gson().fromJson("{}", Config.class).radarStyle);
   }
}
