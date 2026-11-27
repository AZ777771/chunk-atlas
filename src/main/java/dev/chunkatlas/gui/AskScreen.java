package dev.chunkatlas.gui;

import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class AskScreen extends AtlasScreen {
   private static final int W = 240;
   private final Screen parent;
   private final String current;
   private final String note;
   private final Predicate<String> valid;
   private final Consumer<String> onOk;
   private EditBox box;
   private Button ok;

   AskScreen(Screen parent, Component title, String current, String note, Predicate<String> valid, Consumer<String> onOk) {
      super(title);
      this.parent = parent;
      this.current = current == null ? "" : current;
      this.note = note;
      this.valid = valid;
      this.onOk = onOk;
   }

   @Override
   protected void init() {
      int left = (this.width - W) / 2;
      int y = this.height / 2 - 18;
      this.box = new EditBox(this.font, left, y, W, 20, this.title);
      this.box.setMaxLength(64);
      this.box.setValue(this.current);
      this.box.setResponder(text -> this.refresh());
      this.addRenderableWidget(this.box);
      int bw = (W - 8) / 2;
      y += this.note == null ? 30 : 44;
      this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose()).bounds(left, y, bw, 20).build());
      this.ok = this.addRenderableWidget(Button.builder(Component.translatable("gui.ok"), b -> this.accept()).bounds(left + bw + 8, y, bw, 20).build());
      this.setInitialFocus(this.box);
      this.refresh();
   }

   private void refresh() {
      if (this.ok != null) {
         this.ok.active = this.valid.test(this.box.getValue());
      }
   }

   private void accept() {
      String text = this.box.getValue();
      if (this.valid.test(text)) {
         Game.open(Minecraft.getInstance(), this.parent);
         this.onOk.accept(text);
      }
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (Keys.confirm(keyCode)) {
         this.accept();
         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
   }

   @Override
   public void onClose() {
      Game.open(Minecraft.getInstance(), this.parent);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      int left = (this.width - W) / 2;
      int y = this.height / 2 - 18;
      int h = this.note == null ? 90 : 104;
      Glass.panel(g, left - 12, y - 34, W + 24, h);
      g.text(this.font, this.title, left, y - 24, Ui.WHITE);
      if (this.note != null) {
         g.text(this.font, this.note, left, y + 26, Ui.GREY);
      }

      super.draw(g, mouseX, mouseY, partial);
   }
}
