package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class SeedMapWarningScreen extends AtlasScreen {
   private static final int CARD_W = 280;
   private static final int BUTTON_W = 80;
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final Runnable onShow;
   private final List<Ui.Hit> hits = new ArrayList<>();

   SeedMapWarningScreen(MapScreen map, Runnable onShow) {
      super(Component.translatable("chunkatlas.seed.warning.title"));
      this.map = map;
      this.onShow = onShow;
   }

   void show() {
      this.onShow.run();
      Game.open(this.mc, this.map);
   }

   @Override
   public void onClose() {
      Game.open(this.mc, this.map);
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }

   @Override
   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      this.map.drawUnder(g, partial);
      g.fill(0, 0, this.width, this.height, 0x66000000);
      this.hits.clear();
      int cardW = Math.min(this.width - 20, CARD_W);
      List<FormattedCharSequence> text = this.font.split(Component.translatable("chunkatlas.seed.warning.text"), cardW - 32);
      int h = 14 + 10 + 12 + text.size() * 11 + 14 + 20 + 14;
      int x = (this.width - cardW) / 2;
      int y = Math.max(10, (this.height - h) / 2);
      int cx = this.width / 2;
      Glass.panel(g, x, y, cardW, h);
      g.fill(x, y, x + cardW, y + h, 0x66000000);
      y += 14;
      g.centered(this.font, Component.literal(this.title.getString()).withStyle(ChatFormatting.BOLD), cx, y, Ui.WHITE);
      y += 22;
      for (FormattedCharSequence line : text) {
         g.centered(this.font, line, cx, y, Glass.LABEL);
         y += 11;
      }

      y += 14;
      String show = Ui.t("chunkatlas.seed.warning.show");
      String cancel = Ui.t("chunkatlas.seed.warning.cancel");
      int sw = Math.max(BUTTON_W, this.font.width(show) + 24);
      int nw = Math.max(BUTTON_W, this.font.width(cancel) + 24);
      int right = x + cardW - 16;
      Glass.button(g, this.font, this.hits, right - sw, y, sw, 20, show, Glass.ACCENT, mouseX, mouseY, this::show);
      Glass.button(g, this.font, this.hits, right - sw - 8 - nw, y, nw, 20, cancel, Ui.WHITE, mouseX, mouseY, this::onClose);
      super.draw(g, mouseX, mouseY, partial);
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
         for (int n = this.hits.size() - 1; n >= 0; n--) {
            if (this.hits.get(n).has(mouseX, mouseY)) {
               this.hits.get(n).action().run();
               return true;
            }
         }
      }

      return true;
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      if (Keys.confirm(keyCode)) {
         this.show();
         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
   }
}
