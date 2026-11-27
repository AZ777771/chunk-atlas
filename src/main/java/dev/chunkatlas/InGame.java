package dev.chunkatlas;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.gui.MapScreen;
import dev.chunkatlas.port.ChunkIo;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Hooks;
import dev.chunkatlas.port.Keys;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

public final class InGame {
   private static KeyMapping openKey;
   private static volatile Component pending;

   private InGame() {
   }

   public record Session(String levelId, CompletableFuture<Void> saved) {
   }

   static void register() {
      openKey = Keys.register("key.chunkatlas.open", InputConstants.KEY_K);
      dev.chunkatlas.minimap.Minimap.register();
      ClientTickEvents.END_CLIENT_TICK.register(mc -> {
         while (openKey.consumeClick()) {
            if (Game.screen(mc) == null) {
               open(mc, null);
            }
         }

         Component message = pending;
         if (message != null && mc.player != null && Game.screen(mc) == null) {
            pending = null;
            mc.player.sendSystemMessage(message);
         }
      });
      ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
         if (screen instanceof PauseScreen pause && pause.showsPauseMenu() && (mc.hasSingleplayerServer() || LiveMap.folder() != null) && Settings.pauseButton()) {
            SpriteIconButton button = ChunkAtlas.ownButton(screen, () -> Hooks.iconButton(Component.translatable("chunkatlas.pause.tooltip"), b -> open(mc, screen), ChunkAtlas.MAP_ICON));
            List<Button> icons = new java.util.ArrayList<>();
            int top = Integer.MAX_VALUE;
            int left = Integer.MAX_VALUE;
            for (var child : screen.children()) {
               if (child instanceof Button other && other != button && other.getHeight() == 20) {
                  if (other.getWidth() == 20) {
                     icons.add(other);
                  }

                  if (other.getY() < top) {
                     top = other.getY();
                     left = other.getX();
                  } else if (other.getY() == top) {
                     left = Math.min(left, other.getX());
                  }
               }
            }

            if (!icons.isEmpty()) {
               int rowY = icons.get(0).getY();
               icons.removeIf(i -> i.getY() != rowY);
               icons.sort(java.util.Comparator.comparingInt(Button::getX));
               int step = icons.size() > 1 ? icons.get(1).getX() - icons.get(0).getX() : 24;
               int count = icons.size() + 1;
               int x = (screen.width - ((count - 1) * step + 20)) / 2;
               for (Button i : icons) {
                  i.setX(x);
                  x += step;
               }

               button.setPosition(x, rowY);
            } else {
               button.setPosition(top == Integer.MAX_VALUE ? 4 : Math.max(4, left - 24), top == Integer.MAX_VALUE ? 4 : top);
            }
         }
      });
   }

   public static void open(Minecraft mc, Screen parent) {
      IntegratedServer server = mc.getSingleplayerServer();
      if (server == null) {
         Path folder = LiveMap.folder();
         if (folder == null || mc.player == null) {
            return;
         }

         dev.chunkatlas.minimap.Minimap.CACHE.far().release();

         Game.open(mc, new MapScreen(parent, folder, new Session(folder.getFileName().toString(), LiveMap.save())));
         return;
      }

      Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
      String levelId = world.getFileName().toString();

      CompletableFuture<Void> saved = server.submit(() -> {
         server.saveEverything(true, false, true);
         List<CompletableFuture<Void>> written = new java.util.ArrayList<>();
         for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            written.add(ChunkIo.flush(level));
         }

         return CompletableFuture.allOf(written.toArray(CompletableFuture[]::new));
      }).thenCompose(f -> f);
      Game.open(mc, new MapScreen(parent, world, new Session(levelId, saved)));
   }

   public static void tellLater(Component message) {
      pending = message;
   }

   public static Component keyName() {
      return openKey == null ? Component.literal("K") : openKey.getTranslatedKeyMessage();
   }

   public static KeyMapping openKey() {
      return openKey;
   }
}
