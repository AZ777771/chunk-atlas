package dev.chunkatlas.port;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public abstract class AtlasSlider extends AbstractSliderButton {
   protected AtlasSlider(int x, int y, int w, int h, Component message, double value) {
      super(x, y, w, h, message, value);
   }
}
