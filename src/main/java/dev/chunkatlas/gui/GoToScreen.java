package dev.chunkatlas.gui;

import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class GoToScreen extends AtlasScreen {
   private static final int W = 100;
   private final Screen parent;
   private final BiConsumer<Integer, Integer> onGo;
   private final int startX;
   private final int startZ;
   private EditBox x;
   private EditBox z;
   private Button go;

   GoToScreen(Screen parent, int startX, int startZ, BiConsumer<Integer, Integer> onGo) {
      super(Component.translatable("chunkatlas.goto.title"));
      this.parent = parent;
      this.startX = startX;
      this.startZ = startZ;
      this.onGo = onGo;
   }

   @Override
   protected void init() {
      int cx = this.width / 2;
      int y = this.height / 2 - 14;
      this.x = this.number(cx - W - 4, y, this.startX, "chunkatlas.goto.x");
      this.z = this.number(cx + 4, y, this.startZ, "chunkatlas.goto.z");
      y += 32;
      this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose()).bounds(cx - W - 4, y, W, 20).build());
      this.go = this.addRenderableWidget(Button.builder(Component.translatable("chunkatlas.goto.go"), b -> this.go()).bounds(cx + 4, y, W, 20).build());
      this.setInitialFocus(this.x);
      this.refresh();
   }

   private EditBox number(int left, int y, int value, String name) {
      EditBox box = new EditBox(this.font, left, y, W, 20, Component.translatable(name));
      box.setMaxLength(9);
      box.setValue(Integer.toString(value));
      box.setResponder(text -> {
         String clean = text.replaceAll("[^0-9-]", "");
         if (!clean.equals(text)) {
            box.setValue(clean);
         } else {
            this.refresh();
         }
      });
      return this.addRenderableWidget(box);
   }

   private static Integer parse(EditBox box) {
      try {
         return Integer.parseInt(box.getValue().trim());
      } catch (NumberFormatException e) {
         return null;
      }
   }

   private void refresh() {
      if (this.go != null) {
         this.go.active = parse(this.x) != null && parse(this.z) != null;
      }
   }

   private void go() {
      Integer bx = parse(this.x);
      Integer bz = parse(this.z);
      if (bx != null && bz != null) {
         Game.open(Minecraft.getInstance(), this.parent);
         this.onGo.accept(bx, bz);
      }
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (Keys.confirm(keyCode)) {
         this.go();
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
      return true;
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      int cx = this.width / 2;
      int y = this.height / 2 - 14;
      g.centered(this.font, this.title, cx, y - 30, Ui.WHITE);
      g.text(this.font, "X", cx - W - 4, y - 11, Ui.GREY);
      g.text(this.font, "Z", cx + 4, y - 11, Ui.GREY);
      super.draw(g, mouseX, mouseY, partial);
   }
}
