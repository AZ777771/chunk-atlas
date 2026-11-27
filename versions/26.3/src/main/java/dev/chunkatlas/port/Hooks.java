package dev.chunkatlas.port;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class Hooks {
   private Hooks() {
   }

   public interface Hud {
      void draw(Gfx g, float partial);
   }

   public enum Under {
      CROSSHAIR,

      CHAT
   }

   public interface Command {
      <S> LiteralArgumentBuilder<S> build(BiConsumer<S, Component> feedback);
   }

   public static void hud(String id, Under under, Hud hud) {
      Identifier before = under == Under.CHAT ? VanillaHudElements.CHAT : VanillaHudElements.CROSSHAIR;
      HudElementRegistry.attachElementBefore(before, Identifier.parse(id), (g, delta) -> hud.draw(Gfx.of(g), delta.getGameTimeDeltaPartialTick(true)));
   }

   public static void levelChanged(Runnable run) {
      ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> run.run());
   }

   public static void clientCommand(Command command) {
      ClientCommandRegistrationCallback.EVENT.register(
         (dispatcher, context) -> dispatcher.register(command.<FabricClientCommandSource>build(FabricClientCommandSource::sendFeedback))
      );
   }

   public static void messages(BiConsumer<Component, UUID> got) {
      ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) -> got.accept(message, sender == null ? null : sender.id()));
      ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
         if (!overlay) {
            got.accept(message, null);
         }
      });
   }

   public static void addWidget(Screen screen, AbstractWidget widget) {
      Screens.getWidgets(screen).add(widget);
   }

   public static SpriteIconButton iconButton(Component tooltip, Button.OnPress onPress, String sprite) {
      SpriteIconButton b = SpriteIconButton.builder(tooltip, onPress, true).sprite(Identifier.parse(sprite), 16, 16).size(20, 20).build();
      b.setTooltip(Tooltip.create(tooltip));
      return b;
   }

   public static void serverChunkLoaded(BiConsumer<ServerLevel, LevelChunk> loaded) {
      ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> loaded.accept(level, chunk));
   }
}
