package dev.chunkatlas;

import dev.chunkatlas.gui.DevTour;
import dev.chunkatlas.gui.ItemIconButton;
import dev.chunkatlas.gui.MapScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Hooks;
import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

public final class ChunkAtlas implements ClientModInitializer {
   public static final String ID = "chunkatlas";

   public void onInitializeClient() {
      Settings.applyLimits();

      Thread.ofVirtual().start(() -> dev.chunkatlas.engine.EditSnapshots.clearOld(home()));
      InGame.register();
      LiveMap.register();
      LiveChunks.register();
      Footsteps.register();
      dev.chunkatlas.net.AtlasNet.register();
      ServerLink.register();
      Generating.register();
      TaskClient.register();
      dev.chunkatlas.gui.TasksHud.register();
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> dev.chunkatlas.gui.MapScreen.forgetKept());
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
         net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
         if (server != null) {
            dev.chunkatlas.gui.MapScreen.prepare(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize());
         }
      });
      ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
         if (screen instanceof SelectWorldScreen list && Settings.worldListButton()) {
            addMapButton(mc, list);
         }

         if (screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen create && Settings.worldListButton() && dev.chunkatlas.port.Seeds.available()) {
            addPreviewButton(mc, create);
         }
      });

      ClientTickEvents.END_CLIENT_TICK.register(mc -> dev.chunkatlas.engine.Background.playing = mc.level != null && !mc.isPaused());
      if (DevTour.wanted(FabricLoader.getInstance().getGameDir())) {
         ClientTickEvents.END_CLIENT_TICK.register(DevTour::tick);
      }
   }

   public static void open(Screen parent, Path worldFolder) {
      Game.open(Minecraft.getInstance(), new MapScreen(parent, worldFolder));
   }

   public static Path home() {
      String set = System.getProperty("chunkatlas.home", "");
      return !set.isBlank() ? Path.of(set) : FabricLoader.getInstance().getGameDir().resolve("chunkatlas");
   }

   public static final String MAP_ICON = ID + ":icon/map";

   private static final java.util.Map<Screen, Button> ADDED = new java.util.WeakHashMap<>();

   @SuppressWarnings("unchecked")
   static <T extends Button> T ownButton(Screen screen, java.util.function.Supplier<T> make) {
      Button button = ADDED.get(screen);
      if (button == null || !screen.children().contains(button)) {
         if (button == null) {
            button = make.get();
            ADDED.put(screen, button);
         }

         Hooks.addWidget(screen, button);
      }

      return (T)button;
   }

   private static ItemIconButton mapItemButton(Component tooltip, Button.OnPress onPress) {
      return new ItemIconButton(net.minecraft.world.item.Items.FILLED_MAP, tooltip, onPress);
   }

   private static void addMapButton(Minecraft mc, SelectWorldScreen screen) {
      ItemIconButton button = ownButton(screen, () -> mapItemButton(
         Component.translatable("chunkatlas.select.tooltip"),
         b -> selected(screen).ifPresent(world -> open(screen, mc.getLevelSource().getLevelPath(world.getLevelId())))
      ));
      Runnable place = () -> {
         int rowY = -1;
         int left = Integer.MAX_VALUE;
         int right = Integer.MIN_VALUE;
         for (GuiEventListener child : screen.children()) {
            if (child instanceof Button other && !(other instanceof SpriteIconButton) && !(other instanceof ItemIconButton) && other.getHeight() == 20) {
               if (other.getY() > rowY) {
                  rowY = other.getY();
                  left = Integer.MAX_VALUE;
                  right = Integer.MIN_VALUE;
               }

               if (other.getY() == rowY) {
                  left = Math.min(left, other.getX());
                  right = Math.max(right, other.getX() + other.getWidth());
               }
            }
         }

         if (rowY < 0) {
            button.setPosition(screen.width - 24, 4);
         } else if (left - 24 >= 4) {
            button.setPosition(left - 24, rowY);
         } else {
            button.setPosition(Math.min(screen.width - 24, right + 4), rowY);
         }

         Optional<LevelSummary> world = selected(screen);
         button.active = world.isPresent();

         button.describe(
            world.isPresent()
               ? Component.translatable("chunkatlas.select.tooltip.world", world.get().getLevelName())
               : Component.translatable("chunkatlas.select.tooltip")
         );
      };
      place.run();
      ScreenEvents.afterTick(screen).register(s -> place.run());
   }

   private static void addPreviewButton(Minecraft mc, net.minecraft.client.gui.screens.worldselection.CreateWorldScreen screen) {
      ItemIconButton button = ownButton(screen, () -> mapItemButton(
         Component.translatable("chunkatlas.preview.tooltip"), b -> Game.open(mc, new dev.chunkatlas.gui.SeedPreviewScreen(screen))
      ));
      int rowY = -1;
      int left = Integer.MAX_VALUE;
      for (GuiEventListener child : screen.children()) {
         if (child instanceof Button other && !(other instanceof SpriteIconButton) && !(other instanceof ItemIconButton) && other.getHeight() == 20 && other.getY() >= rowY) {
            if (other.getY() > rowY) {
               rowY = other.getY();
               left = Integer.MAX_VALUE;
            }

            left = Math.min(left, other.getX());
         }
      }

      button.setPosition(rowY < 0 ? screen.width - 24 : Math.max(4, left - 24), rowY < 0 ? 4 : rowY);
   }

   private static Optional<LevelSummary> selected(SelectWorldScreen screen) {
      for (GuiEventListener child : screen.children()) {
         if (child instanceof WorldSelectionList list) {
            return Game.selectedWorld(list);
         }
      }

      return Optional.empty();
   }
}
