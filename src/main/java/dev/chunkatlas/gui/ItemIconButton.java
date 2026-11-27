package dev.chunkatlas.gui;

import dev.chunkatlas.port.AtlasButton;
import dev.chunkatlas.port.Gfx;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public final class ItemIconButton extends AtlasButton {
   private final Item item;

   public ItemIconButton(Item item, Component tooltip, Button.OnPress onPress) {
      super(0, 0, 20, 20, tooltip, onPress);
      this.item = item;
      this.setTooltip(Tooltip.create(tooltip));
   }

   public void describe(Component tooltip) {
      if (!tooltip.equals(this.getMessage())) {
         this.setMessage(tooltip);
         this.setTooltip(Tooltip.create(tooltip));
      }
   }

   @Override
   protected void drawContents(Gfx g, int mouseX, int mouseY, float partial) {
      this.drawFrame(g);
      int x = this.getX() + (this.getWidth() - 16) / 2;
      int y = this.getY() + (this.getHeight() - 16) / 2;
      StructureItems.draw(g, this.item, x, y);
      if (!this.active) {
         g.fill(x, y, x + 16, y + 16, 0x90000000);
      }
   }
}
