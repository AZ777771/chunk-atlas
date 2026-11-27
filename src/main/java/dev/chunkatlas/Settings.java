package dev.chunkatlas;

import dev.chunkatlas.engine.TileRenderer;
import dev.chunkatlas.engine.WorldMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Settings {
   private static Properties values;

   private Settings() {
   }

   public static boolean backups() {
      return flag("backups", false);
   }

   public static void backups(boolean on) {
      putFlag("backups", on);
   }

   public static boolean keepStructures() {
      return flag("keep_structures", true);
   }

   public static void keepStructures(boolean on) {
      putFlag("keep_structures", on);
      applyLimits();
   }

   public static boolean editWithoutCheats() {
      return flag("edit_without_cheats", false);
   }

   public static void editWithoutCheats(boolean on) {
      putFlag("edit_without_cheats", on);
   }

   public static boolean pauseButton() {
      return flag("pause_button", true);
   }

   public static void pauseButton(boolean on) {
      putFlag("pause_button", on);
   }

   public static boolean worldListButton() {
      return flag("world_list_button", true);
   }

   public static void worldListButton(boolean on) {
      putFlag("world_list_button", on);
   }

   public static boolean smoothZoom() {
      return flag("smooth_zoom", true);
   }

   public static void smoothZoom(boolean on) {
      putFlag("smooth_zoom", on);
   }

   public static boolean grid() {
      return flag("grid", false);
   }

   public static void grid(boolean on) {
      putFlag("grid", on);
   }

   public static boolean players() {
      return flag("players", true);
   }

   public static boolean allPlayers() {
      return flag("players.all", false);
   }

   public static void allPlayers(boolean on) {
      putFlag("players.all", on);
   }

   public static void players(boolean on) {
      putFlag("players", on);
   }

   public static boolean protectedShown() {
      return flag("protected", true);
   }

   public static void protectedShown(boolean on) {
      putFlag("protected", on);
   }

   public static boolean portals() {
      return flag("map.portals", false);
   }

   public static void portals(boolean on) {
      putFlag("map.portals", on);
   }

   public static boolean loadedChunks() {
      return flag("map.loaded", false);
   }

   public static void loadedChunks(boolean on) {
      putFlag("map.loaded", on);
   }

   public static int loadedTab() {
      return Math.max(0, Math.min(2, number("map.loaded.tab", 0)));
   }

   public static void loadedTab(int tab) {
      putNumber("map.loaded.tab", tab);
   }

   public static boolean mobs() {
      return flag("map.mobs", false);
   }

   public static void mobs(boolean on) {
      putFlag("map.mobs", on);
   }

   public static boolean spawnable() {
      return flag("map.spawnable", false);
   }

   public static void spawnable(boolean on) {
      putFlag("map.spawnable", on);
   }

   public static boolean undergroundCaves() {
      return flag("underground_caves", true);
   }

   public static void undergroundCaves(boolean on) {
      putFlag("underground_caves", on);
   }

   public static boolean footsteps() {
      return flag("footsteps", true);
   }

   public static void footsteps(boolean on) {
      putFlag("footsteps", on);
   }

   public static final int[] FOOTSTEP_FADES = {1, 5, 15, 30, 60, 300, -1};
   public static final int FADE_NEVER = -1;
   private static final int FADE_DEFAULT = 15;

   public static int footstepsFade() {
      int s = number("footsteps.fade", FADE_DEFAULT);
      if (s == FADE_NEVER) {
         return s;
      }

      int best = FADE_DEFAULT;
      for (int v : FOOTSTEP_FADES) {
         if (v != FADE_NEVER && Math.abs(v - s) < Math.abs(best - s)) {
            best = v;
         }
      }

      return best;
   }

   public static void footstepsFade(int seconds) {
      putNumber("footsteps.fade", seconds);
   }

   public static boolean coordinates() {
      return flag("coordinates", true);
   }

   public static void coordinates(boolean on) {
      putFlag("coordinates", on);
   }

   public static boolean detailed() {
      return flag("look.detailed", true);
   }

   public static void detailed(boolean on) {
      putFlag("look.detailed", on);
   }

   public static boolean clearWater() {
      return flag("look.clear_water", true);
   }

   public static void clearWater(boolean on) {
      putFlag("look.clear_water", on);
   }

   public static int pasteBlend() {
      int n = number("paste.blend", 0);
      return n == 2 || n == 4 || n == 8 ? n : 0;
   }

   public static void pasteBlend(int chunks) {
      putNumber("paste.blend", chunks);
   }

   public static boolean pasteSmart() {
      return flag("paste.smart", true);
   }

   public static void pasteSmart(boolean on) {
      putFlag("paste.smart", on);
   }

   public static int generateSpeed() {
      return Math.max(0, Math.min(2, number("generate.speed", 1)));
   }

   public static void generateSpeed(int speed) {
      putNumber("generate.speed", speed);
   }

   public static boolean generateFinish() {
      return flag("generate.finish", true);
   }

   public static void generateFinish(boolean on) {
      putFlag("generate.finish", on);
   }

   public static boolean generatePause() {
      return flag("generate.pause", false);
   }

   public static void generatePause(boolean on) {
      putFlag("generate.pause", on);
   }

   public static int tasksRejoin() {
      return Math.max(0, Math.min(1, number("tasks.rejoin", 0)));
   }

   public static void tasksRejoin(int n) {
      putNumber("tasks.rejoin", n);
   }

   public static boolean tasksHud() {
      return flag("tasks.hud", true);
   }

   public static void tasksHud(boolean on) {
      putFlag("tasks.hud", on);
   }

   public static int tasksHudCorner() {
      return Math.max(0, Math.min(3, number("tasks.hud.corner", 0)));
   }

   public static void tasksHudCorner(int n) {
      putNumber("tasks.hud.corner", n);
   }

   public static boolean borderInWorld() {
      return flag("border.in_world", true);
   }

   public static void borderInWorld(boolean on) {
      putFlag("border.in_world", on);
   }

   public static boolean borderParticles() {
      return flag("border.particles", false);
   }

   public static void borderParticles(boolean on) {
      putFlag("border.particles", on);
   }

   public static String borderMessage() {
      String m = load().getProperty("border.message");
      return m == null || m.isBlank() ? null : m;
   }

   public static void borderMessage(String text) {
      if (text == null || text.isBlank()) {
         load().remove("border.message");
      } else {
         load().setProperty("border.message", text.strip());
      }

      save();
   }

   public static int borderSound() {
      return Math.max(0, number("border.sound", 1));
   }

   public static void borderSound(int n) {
      putNumber("border.sound", n);
   }

   public static int borderEffect() {
      return Math.max(0, number("border.effect", 1));
   }

   public static void borderEffect(int n) {
      putNumber("border.effect", n);
   }

   public static int shading() {
      return Math.max(0, Math.min(2, number("look.shading", 1)));
   }

   public static void shading(int level) {
      putNumber("look.shading", level);
   }

   public static boolean biomeColours() {
      return flag("look.biome_colours", true);
   }

   public static void biomeColours(boolean on) {
      putFlag("look.biome_colours", on);
   }

   public static boolean heightTint() {
      return flag("look.height_tint", false);
   }

   public static void heightTint(boolean on) {
      putFlag("look.height_tint", on);
   }

   public static boolean night() {
      return flag("look.night", false);
   }

   public static void night(boolean on) {
      putFlag("look.night", on);
   }

   public static int lookBits() {
      if (!detailed()) {
         return 0;
      }

      int b = TileRenderer.DETAILED;
      if (!clearWater()) {
         b |= TileRenderer.SOLID_WATER;
      }

      int shade = shading();
      if (shade == 0) {
         b |= TileRenderer.SHADE_OFF;
      } else if (shade == 2) {
         b |= TileRenderer.SHADE_STRONG;
      }

      if (!biomeColours()) {
         b |= TileRenderer.NO_BIOME_TINT;
      }

      if (heightTint()) {
         b |= TileRenderer.HEIGHT_TINT;
      }

      if (night()) {
         b |= TileRenderer.NIGHT;
      }

      return b;
   }

   public static final int LOOK_BITS = TileRenderer.DETAILED | TileRenderer.SOLID_WATER | TileRenderer.SHADE_STRONG | TileRenderer.SHADE_OFF
      | TileRenderer.NO_BIOME_TINT | TileRenderer.HEIGHT_TINT | TileRenderer.NIGHT;

   public static final int NO_LIMIT = 0;

   public static int cacheMb() {
      int mb = number("cache_mb", NO_LIMIT);
      return mb <= NO_LIMIT ? NO_LIMIT : Math.max(64, mb);
   }

   public static void cacheMb(int mb) {
      putNumber("cache_mb", mb);
      applyLimits();
   }

   public static int workers() {
      return Math.max(0, Math.min(16, number("workers", 0)));
   }

   public static void workers(int n) {
      putNumber("workers", n);
      applyLimits();
   }

   public static void applyLimits() {
      WorldMap.limits(workers(), cacheMb() == NO_LIMIT ? Long.MAX_VALUE : (long)cacheMb() << 20);
      dev.chunkatlas.engine.ChunkCopy.keepStructures(keepStructures());
   }

   public static String key(String id) {
      return load().getProperty("key." + id);
   }

   public static void key(String id, String value) {
      if (value == null) {
         load().remove("key." + id);
      } else {
         load().setProperty("key." + id, value);
      }

      save();
   }

   public static void reset(String... keys) {
      for (String key : keys) {
         load().remove(key);
      }

      save();
   }

   private static boolean flag(String key, boolean otherwise) {
      return Boolean.parseBoolean(load().getProperty(key, Boolean.toString(otherwise)));
   }

   private static void putFlag(String key, boolean on) {
      load().setProperty(key, Boolean.toString(on));
      save();
   }

   private static int number(String key, int otherwise) {
      try {
         return Integer.parseInt(load().getProperty(key, Integer.toString(otherwise)).trim());
      } catch (NumberFormatException e) {
         return otherwise;
      }
   }

   private static void putNumber(String key, int value) {
      load().setProperty(key, Integer.toString(value));
      save();
   }

   private static synchronized Properties load() {
      if (values == null) {
         values = new Properties();
         Path file = file();
         if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
               values.load(in);
            } catch (IOException | RuntimeException ignored) {
            }
         }
      }

      return values;
   }

   private static synchronized void save() {
      Path file = file();
      try {
         Files.createDirectories(file.getParent());
         try (OutputStream out = Files.newOutputStream(file)) {
            values.store(out, "Chunk Atlas");
         }
      } catch (IOException | RuntimeException ignored) {
      }
   }

   private static Path file() {
      return ChunkAtlas.home().resolve("settings.properties");
   }
}
