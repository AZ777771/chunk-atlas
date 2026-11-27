package dev.chunkatlas.port;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public abstract class AtlasButton extends Button {
   private static final WidgetSprites SPRITES = new WidgetSprites(
      ResourceLocation.withDefaultNamespace("widget/button"),
      ResourceLocation.withDefaultNamespace("widget/button_disabled"),
      ResourceLocation.withDefaultNamespace("widget/button_highlighted"));

   protected AtlasButton(int x, int y, int w, int h, Component message, Button.OnPress onPress) {
      super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
   }

   @Override
   protected final void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
      this.drawContents(Gfx.of(g), mouseX, mouseY, partial);
   }

   protected abstract void drawContents(Gfx g, int mouseX, int mouseY, float partial);

   protected final void drawFrame(Gfx g) {
      g.g.setColor(1.0F, 1.0F, 1.0F, this.alpha);
      RenderSystem.enableBlend();
      RenderSystem.enableDepthTest();
      g.g.blitSprite(SPRITES.get(this.active, this.isHoveredOrFocused()), this.getX(), this.getY(), this.getWidth(), this.getHeight());
      g.g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
   }
}
