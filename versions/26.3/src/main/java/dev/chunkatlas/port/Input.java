package dev.chunkatlas.port;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

public final class Input {
   private Input() {
   }

   public static boolean click(GuiEventListener widget, double x, double y, int button, int mods, boolean doubleClick) {
      return widget.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(button, mods)), doubleClick);
   }

   public static boolean release(GuiEventListener widget, double x, double y, int button) {
      return widget.mouseReleased(new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)));
   }

   public static boolean drag(GuiEventListener widget, double x, double y, int button, double dx, double dy) {
      return widget.mouseDragged(new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)), dx, dy);
   }

   public static boolean key(GuiEventListener widget, int key, int scan, int mods) {
      return widget.keyPressed(new KeyEvent(key, scan, mods));
   }

   public static boolean chars(GuiEventListener widget, int codepoint, int mods) {
      return widget.charTyped(new CharacterEvent(codepoint));
   }

   public static void press(AbstractButton button) {
      button.onPress(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
   }
}
