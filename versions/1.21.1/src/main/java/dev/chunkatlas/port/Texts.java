package dev.chunkatlas.port;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class Texts {
   private static final Style SEARCH_HINT = Style.EMPTY.applyFormats(ChatFormatting.GRAY, ChatFormatting.ITALIC);

   private Texts() {
   }

   public static Style hover(Style style, Component tip) {
      return style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tip));
   }

   public static Style command(Style style, String command) {
      return style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
   }

   public static void overlay(Minecraft mc, Component line) {
      if (mc.player != null) {
         mc.player.displayClientMessage(line, true);
      }
   }

   public static MutableComponent searchHint(Component hint) {
      return hint.copy().withStyle(SEARCH_HINT);
   }
}
