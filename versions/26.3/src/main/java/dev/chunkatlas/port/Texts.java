package dev.chunkatlas.port;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class Texts {
   private Texts() {
   }

   public static Style hover(Style style, Component tip) {
      return style.withHoverEvent(new HoverEvent.ShowText(tip));
   }

   public static Style command(Style style, String command) {
      return style.withClickEvent(new ClickEvent.RunCommand(command));
   }

   public static void overlay(Minecraft mc, Component line) {
      if (mc.player != null) {
         mc.player.sendOverlayMessage(line);
      }
   }

   public static MutableComponent searchHint(Component hint) {
      return hint.copy().withStyle(EditBox.SEARCH_HINT_STYLE);
   }
}
