package dev.chunkatlas.port;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;

public final class Input {
   private Input() {
   }

   public static boolean click(GuiEventListener widget, double x, double y, int button, int mods, boolean doubleClick) {
      return widget.mouseClicked(x, y, button);
   }

   public static boolean release(GuiEventListener widget, double x, double y, int button) {
      return widget.mouseReleased(x, y, button);
   }

   public static boolean drag(GuiEventListener widget, double x, double y, int button, double dx, double dy) {
      return widget.mouseDragged(x, y, button, dx, dy);
   }

   public static boolean key(GuiEventListener widget, int key, int scan, int mods) {
      return widget.keyPressed(key, scan, mods);
   }

   public static boolean chars(GuiEventListener widget, int codepoint, int mods) {
      return widget.charTyped((char)codepoint, mods);
   }

   public static void press(AbstractButton button) {
      button.onPress();
   }
}
