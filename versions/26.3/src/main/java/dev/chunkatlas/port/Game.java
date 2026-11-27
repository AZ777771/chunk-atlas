package dev.chunkatlas.port;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class Game {
   private Game() {
   }

   public static Screen screen(Minecraft mc) {
      return mc.gui.screen();
   }

   public static void open(Minecraft mc, Screen screen) {
      mc.gui.setScreen(screen);
   }

   public static boolean busy(Minecraft mc) {
      return mc.gui.overlay() != null;
   }

   public static boolean mac() {
      return Util.getPlatform() == Util.OS.OSX;
   }

   public static Executor background() {
      return Util.backgroundExecutor();
   }

   public static BlockPos spawn(ClientLevel level) {
      LevelData.RespawnData spawn = level.getRespawnData();
      return spawn == null || spawn.dimension() != level.dimension() ? null : spawn.pos();
   }

   public static long dayTime(ClientLevel level) {
      return level.getOverworldClockTime();
   }

   public static boolean night(Level level) {
      return level.isDarkOutside();
   }

   public static Vec3 eye(Minecraft mc, Matrix4f viewProjection) {
      Camera camera = mc.gameRenderer.mainCamera();
      if (!camera.isInitialized()) {
         return null;
      }

      camera.getViewRotationProjectionMatrix(viewProjection);
      return camera.position();
   }

   public static Optional<LevelSummary> selectedWorld(WorldSelectionList list) {
      return list.getSelectedOpt().map(WorldSelectionList.WorldListEntry::getLevelSummary);
   }

   public static void leaveLevel(ClientLevel level) {
      level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
   }

   public static void resizeGui(Minecraft mc) {
      mc.resizeGui();
   }

   public static void unthrottled(Minecraft mc) {
      mc.options.enableVsync().set(false);
      mc.options.framerateLimit().set(net.minecraft.client.Options.UNLIMITED_FRAMERATE_CUTOFF);
      mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
   }

   public static void screenshot(Minecraft mc, String file, Consumer<Component> done) {
      Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1, done);
   }

   public static void sample(Minecraft mc, int[][] points, Consumer<int[]> done) {
      Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
         try (image) {
            int[] out = new int[points.length];
            for (int i = 0; i < points.length; i++) {
               int x = Math.max(0, Math.min(image.getWidth() - 1, points[i][0]));
               int y = Math.max(0, Math.min(image.getHeight() - 1, points[i][1]));

               out[i] = image.getPixel(x, y) & 0xFFFFFF;
            }

            done.accept(out);
         }
      });
   }

   public static void createWorld(Minecraft mc, Screen back) {
      CreateWorldScreen.openFresh(mc, () -> open(mc, back));
   }

   public static LevelSettings creative(String name) {
      return new LevelSettings(name, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
   }
}
