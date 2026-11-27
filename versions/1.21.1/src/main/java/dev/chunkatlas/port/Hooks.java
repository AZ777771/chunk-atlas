package dev.chunkatlas.port;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class Hooks {
   private static final float HUD_LAYER = 200.0F;

   private static final int CROSSHAIR_LAYER = 1;

   private static final int CHAT_LAYER = 11;

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
      float z = ((under == Under.CHAT ? CHAT_LAYER : CROSSHAIR_LAYER) - 0.5F) * HUD_LAYER;
      HudRenderCallback.EVENT.register((g, delta) -> {
         g.pose().pushPose();
         g.pose().translate(0.0F, 0.0F, z);
         hud.draw(Gfx.of(g), delta.getGameTimeDeltaPartialTick(true));
         g.pose().popPose();
      });
   }

   public static void levelChanged(Runnable run) {
      ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((mc, level) -> run.run());
   }

   public static void clientCommand(Command command) {
      ClientCommandRegistrationCallback.EVENT.register(
         (dispatcher, context) -> dispatcher.register(command.<FabricClientCommandSource>build(FabricClientCommandSource::sendFeedback))
      );
   }

   public static void messages(BiConsumer<Component, UUID> got) {
      ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, time) -> got.accept(message, sender == null ? null : sender.getId()));
      ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
         if (!overlay) {
            got.accept(message, null);
         }
      });
   }

   public static void addWidget(Screen screen, AbstractWidget widget) {
      Screens.getButtons(screen).add(widget);
   }

   public static SpriteIconButton iconButton(Component tooltip, Button.OnPress onPress, String sprite) {
      SpriteIconButton b = SpriteIconButton.builder(tooltip, onPress, true).sprite(ResourceLocation.parse(sprite), 16, 16).size(20, 20).build();
      b.setTooltip(Tooltip.create(tooltip));
      return b;
   }

   public static void serverChunkLoaded(BiConsumer<ServerLevel, LevelChunk> loaded) {
      ServerChunkEvents.CHUNK_LOAD.register(loaded::accept);
   }
}
