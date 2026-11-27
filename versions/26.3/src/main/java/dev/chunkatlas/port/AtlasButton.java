package dev.chunkatlas.port;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public abstract class AtlasButton extends Button {
   protected AtlasButton(int x, int y, int w, int h, Component message, Button.OnPress onPress) {
      super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
   }

   @Override
   protected final void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
      this.drawContents(Gfx.of(g), mouseX, mouseY, partial);
   }

   protected abstract void drawContents(Gfx g, int mouseX, int mouseY, float partial);

   protected final void drawFrame(Gfx g) {
      this.extractDefaultSprite(g.g);
   }
}
