package dev.chunkatlas.minimap;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.chunkatlas.gui.SettingsScreen;
import dev.chunkatlas.minimap.gui.WaypointsScreen;
import dev.chunkatlas.minimap.hud.MinimapHud;
import dev.chunkatlas.minimap.hud.WorldMarkers;
import dev.chunkatlas.minimap.map.MapCache;
import dev.chunkatlas.minimap.waypoint.Waypoint;
import dev.chunkatlas.minimap.waypoint.Waypoints;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Hooks;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Keys;
import dev.chunkatlas.port.Texts;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Minimap {
   public static final String ID = "chunkatlas_minimap";
   public static final Logger LOG = LoggerFactory.getLogger("Chunk Atlas Minimap");
   public static final MapCache CACHE = new MapCache();
   public static final MinimapHud HUD = new MinimapHud(CACHE);
   private static final WorldMarkers MARKERS = new WorldMarkers();

   private static KeyMapping settingsKey;
   private static KeyMapping zoomInKey;
   private static KeyMapping zoomOutKey;
   private static KeyMapping enlargeKey;
   private static KeyMapping newWaypointKey;
   private static KeyMapping waypointsKey;
   private static KeyMapping toggleKey;
   private static KeyMapping worldWaypointsKey;
   private static boolean wasDead;

   private Minimap() {
   }

   public static void register() {
      Config.get();
      Colours.reset();
      settingsKey = key("settings", InputConstants.KEY_Y);
      zoomInKey = key("zoom_in", InputConstants.KEY_EQUALS);
      zoomOutKey = key("zoom_out", InputConstants.KEY_MINUS);
      enlargeKey = key("enlarge", InputConstants.KEY_Z);
      newWaypointKey = key("new_waypoint", InputConstants.KEY_B);
      waypointsKey = key("waypoints", InputConstants.KEY_U);
      toggleKey = key("toggle", InputConstants.UNKNOWN.getValue());
      worldWaypointsKey = key("world_waypoints", InputConstants.UNKNOWN.getValue());
      Hooks.hud(ID + ":waypoints", Hooks.Under.CROSSHAIR, MARKERS::extract);
      Hooks.hud(ID + ":minimap", Hooks.Under.CHAT, HUD::extract);
      ClientTickEvents.END_CLIENT_TICK.register(Minimap::tick);

      dev.chunkatlas.ChunkScanner.add(CACHE);
      Hooks.levelChanged(() -> {
         CACHE.clear();
         wasDead = false;
      });
      Hooks.messages(Minimap::shared);
      Hooks.clientCommand(new Hooks.Command() {
         @Override
         public <S> LiteralArgumentBuilder<S> build(BiConsumer<S, Component> feedback) {
            return waypointCommand(feedback);
         }
      });
   }

   private static <S> LiteralArgumentBuilder<S> waypointCommand(BiConsumer<S, Component> feedback) {
      return LiteralArgumentBuilder.<S>literal("minimap")
            .then(LiteralArgumentBuilder.<S>literal("waypoint")
               .then(LiteralArgumentBuilder.<S>literal("add")
                  .then(RequiredArgumentBuilder.<S, Integer>argument("x", IntegerArgumentType.integer())
                     .then(RequiredArgumentBuilder.<S, Integer>argument("y", IntegerArgumentType.integer())
                        .then(RequiredArgumentBuilder.<S, Integer>argument("z", IntegerArgumentType.integer())
                           .then(RequiredArgumentBuilder.<S, String>argument("dimension", StringArgumentType.string())
                              .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.greedyString()).executes(c -> {
                                 Waypoint w = new Waypoint(
                                    StringArgumentType.getString(c, "name"),
                                    IntegerArgumentType.getInteger(c, "x"),
                                    IntegerArgumentType.getInteger(c, "y"),
                                    IntegerArgumentType.getInteger(c, "z"),
                                    StringArgumentType.getString(c, "dimension")
                                 );
                                 Waypoints.add(w);
                                 feedback.accept(c.getSource(), Component.translatable("chunkatlas_minimap.chat.added", w.name));
                                 return 1;
                              }))))))));
   }

   private static KeyMapping key(String name, int code) {
      return Keys.register("key." + ID + "." + name, code);
   }

   private static void tick(Minecraft mc) {
      Waypoints.sync(mc);

      CACHE.seed().tick(mc);
      if (mc.level == null || mc.player == null) {
         return;
      }

      Config cfg = Config.get();
      boolean free = Game.screen(mc) == null;
      while (settingsKey.consumeClick()) {
         if (free) {
            Game.open(mc, new SettingsScreen(null, SettingsScreen.MINIMAP));
         }
      }

      while (zoomInKey.consumeClick()) {
         zoom(cfg, 1);
      }

      while (zoomOutKey.consumeClick()) {
         zoom(cfg, -1);
      }

      while (toggleKey.consumeClick()) {
         cfg.enabled = !cfg.enabled;
         cfg.save();
      }

      while (worldWaypointsKey.consumeClick()) {
         cfg.waypointsInWorld = !cfg.waypointsInWorld;
         cfg.save();
         Texts.overlay(mc, Component.translatable(cfg.waypointsInWorld ? "chunkatlas.waypoints.in_world.on" : "chunkatlas.waypoints.in_world.off"));
      }

      while (newWaypointKey.consumeClick()) {
         if (free) {
            Game.open(mc, WaypointsScreen.addingHere(null, mc));
         }
      }

      while (waypointsKey.consumeClick()) {
         if (free) {
            dev.chunkatlas.InGame.open(mc, null);
            if (Game.screen(mc) instanceof dev.chunkatlas.gui.MapScreen map) {
               map.openWaypoints();
            } else {
               Game.open(mc, new WaypointsScreen(null));
            }
         }
      }

      if (cfg.enlargeMode == Config.Enlarge.HOLD) {
         HUD.enlarged(enlargeKey.isDown() && free);
         while (enlargeKey.consumeClick()) {
         }
      } else {
         while (enlargeKey.consumeClick()) {
            HUD.enlarged(!HUD.enlarged());
         }
      }

      boolean dead = mc.player.isDeadOrDying();
      if (dead && !wasDead && cfg.deathpoints) {
         Waypoints.died(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), Ids.of(mc.level.dimension()));
      }

      wasDead = dead;
   }

   private static void zoom(Config cfg, int step) {
      int next = Math.clamp(cfg.zoom + step, 0, Config.ZOOMS.length - 1);
      if (next != cfg.zoom) {
         cfg.zoom = next;
         cfg.save();
      }
   }

   private static void shared(Component message, UUID sender) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || sender != null && sender.equals(mc.player.getUUID())) {
         return;
      }

      Waypoint w = Waypoints.parse(message.getString());
      if (w == null) {
         return;
      }

      String command = String.format(java.util.Locale.ROOT, "minimap waypoint add %d %d %d \"%s\" %s", w.x, w.y, w.z, w.dimension, w.name);
      Component add = Component.translatable("chunkatlas_minimap.chat.add")
         .withStyle(s -> Texts.hover(Texts.command(s.withColor(ChatFormatting.GREEN), "/" + command), Component.literal(w.name + " " + w.x + ", " + w.y + ", " + w.z)));
      mc.execute(() -> {
         if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("chunkatlas_minimap.chat.shared", w.name).append(" ").append(add));
         }
      });
   }

   public static List<KeyMapping> keys() {
      if (settingsKey == null) {
         return List.of();
      }

      return List.of(settingsKey, zoomInKey, zoomOutKey, enlargeKey, toggleKey, newWaypointKey, waypointsKey, worldWaypointsKey);
   }

   public static Component keyName(String which) {
      KeyMapping key = switch (which) {
         case "settings" -> settingsKey;
         case "waypoints" -> waypointsKey;
         case "new_waypoint" -> newWaypointKey;
         default -> enlargeKey;
      };
      return key == null ? Component.empty() : key.getTranslatedKeyMessage();
   }
}
