package dev.chunkatlas.port;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class Game {
   private Game() {
   }

   public static Screen screen(Minecraft mc) {
      return mc.screen;
   }

   public static void open(Minecraft mc, Screen screen) {
      mc.setScreen(screen);
   }

   public static boolean busy(Minecraft mc) {
      return mc.getOverlay() != null;
   }

   public static boolean mac() {
      return Util.getPlatform() == Util.OS.OSX;
   }

   public static Executor background() {
      return Util.backgroundExecutor();
   }

   public static BlockPos spawn(ClientLevel level) {
      return level.dimension() == Level.OVERWORLD ? level.getSharedSpawnPos() : null;
   }

   public static long dayTime(ClientLevel level) {
      return level.getDayTime();
   }

   public static boolean night(Level level) {
      return level.isNight();
   }

   public static Vec3 eye(Minecraft mc, Matrix4f viewProjection) {
      Camera camera = mc.gameRenderer.getMainCamera();
      if (!camera.isInitialized()) {
         return null;
      }

      Matrix4f rotation = new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()));
      viewProjection.set(mc.gameRenderer.getProjectionMatrix(mc.options.fov().get())).mul(rotation);
      return camera.getPosition();
   }

   public static Optional<LevelSummary> selectedWorld(WorldSelectionList list) {
      return list.getSelectedOpt().map(e -> e.summary);
   }

   public static void leaveLevel(ClientLevel level) {
      level.disconnect();
   }

   public static void resizeGui(Minecraft mc) {
      mc.resizeDisplay();
   }

   public static void sample(Minecraft mc, int[][] points, Consumer<int[]> done) {
      try (com.mojang.blaze3d.platform.NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
         int[] out = new int[points.length];
         for (int i = 0; i < points.length; i++) {
            int x = Math.max(0, Math.min(image.getWidth() - 1, points[i][0]));
            int y = Math.max(0, Math.min(image.getHeight() - 1, points[i][1]));

            int c = image.getPixelRGBA(x, y);
            out[i] = (c & 0xFF) << 16 | c & 0xFF00 | c >> 16 & 0xFF;
         }

         done.accept(out);
      }
   }

   public static void unthrottled(Minecraft mc) {
      mc.options.enableVsync().set(false);
      mc.options.framerateLimit().set(net.minecraft.client.Options.UNLIMITED_FRAMERATE_CUTOFF);
   }

   public static void screenshot(Minecraft mc, String file, Consumer<Component> done) {
      Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), done);
   }

   public static void createWorld(Minecraft mc, Screen back) {
      CreateWorldScreen.openFresh(mc, back);
   }

   public static LevelSettings creative(String name) {
      return new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
   }
}
