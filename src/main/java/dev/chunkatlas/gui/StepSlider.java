package dev.chunkatlas.gui;

import dev.chunkatlas.port.AtlasSlider;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;

final class StepSlider extends AtlasSlider {
   private final int[] values;
   private final IntFunction<String> label;
   private final IntConsumer onChange;
   private int index;

   StepSlider(int x, int y, int w, int h, int[] values, int value, IntFunction<String> label, IntConsumer onChange) {
      super(x, y, w, h, Component.empty(), 0.0);
      this.values = values;
      this.label = label;
      this.onChange = onChange;
      this.index = nearest(values, value);
      this.value = values.length <= 1 ? 0.0 : this.index / (double)(values.length - 1);
      this.updateMessage();
   }

   static int nearest(int[] values, int value) {
      int best = 0;
      for (int i = 1; i < values.length; i++) {
         if (Math.abs(values[i] - value) < Math.abs(values[best] - value)) {
            best = i;
         }
      }

      return best;
   }

   int chosen() {
      return this.values[this.index];
   }

   @Override
   protected void updateMessage() {
      this.setMessage(Component.literal(this.label.apply(this.values[this.index])));
   }

   @Override
   protected void applyValue() {
      int i = (int)Math.round(this.value * (this.values.length - 1));
      if (i != this.index) {
         this.index = i;
         this.onChange.accept(this.values[i]);
      }
   }
}
