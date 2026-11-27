package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.Generating;
import dev.chunkatlas.Settings;
import dev.chunkatlas.engine.ChunkGeneration;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class GenerateCard extends AtlasScreen {
   private static final int CARD_W = 360;
   private static final int BUTTON_W = 96;
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final GenerateView view;
   private final ChunkSelection chunks;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private final boolean single = this.mc.getSingleplayerServer() != null;

   private final int[][] counts = new int[2][];
   private int speed = Settings.generateSpeed();
   private boolean finish = Settings.generateFinish();
   private boolean hold = Settings.generatePause();

   GenerateCard(MapScreen map, GenerateView view, ChunkSelection chunks) {
      super(Component.translatable("chunkatlas.generate.button"));
      this.map = map;
      this.view = view;
      this.chunks = chunks;
   }

   private int[] count() {
      int k = this.finish ? 0 : 1;
      if (this.counts[k] == null) {
         this.counts[k] = this.view.count(this.chunks, this.finish).clone();
      }

      return this.counts[k];
   }

   private void go() {
      int make = this.count()[0];
      if (make <= 0) {
         return;
      }

      Game.open(this.mc, this.map);
      if (!this.view.start(this.chunks, ChunkGeneration.Speed.of(this.speed), this.finish, this.hold)) {
         this.map.seedTell(Ui.t("chunkatlas.generate.failed"), Ui.YELLOW);
      }
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
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      if (Keys.confirm(keyCode)) {
         this.go();
         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
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

      return super.onClick(mouseX, mouseY, mouseButton, doubleClick);
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      this.map.drawUnder(g, partial);
      g.fill(0, 0, this.width, this.height, 0x66000000);
      this.hits.clear();
      int[] n = this.count();
      boolean note = n[1] > 0;
      int cardW = Math.min(this.width - 20, CARD_W);
      int h = 14 + 10 + (note ? 14 : 0) + 14 + Glass.segmentsHeight() + 8 + 16 + (this.single ? 16 : 0) + 10 + 20 + 14;
      int cardX = (this.width - cardW) / 2;
      int y = Math.max(10, (this.height - h) / 2);
      int cx = this.width / 2;
      int left = cardX + 16;
      int right = cardX + cardW - 16;
      Glass.panel(g, cardX, y, cardW, h);
      g.fill(cardX, y, cardX + cardW, y + h, 0x66000000);
      y += 14;
      g.centered(this.font, Component.literal(Ui.plural("chunkatlas.generate.title", n[0])).withStyle(ChatFormatting.BOLD), cx, y, Ui.WHITE);
      y += 10;
      if (note) {
         y += 4;
         g.centered(this.font, Ui.plural("chunkatlas.generate.left_alone", n[1]), cx, y, Glass.LABEL);
         y += 10;
      }

      y += 14;
      List<Component> tip = null;
      g.text(this.font, Ui.t("chunkatlas.generate.speed"), left, y + (Glass.segmentsHeight() - 8) / 2, Ui.WHITE);
      String[] speeds = {Ui.t("chunkatlas.generate.speed.light"), Ui.t("chunkatlas.generate.speed.balanced"), Ui.t("chunkatlas.generate.speed.fast")};
      int over = Glass.segments(g, this.font, this.hits, right - Glass.segmentsWidth(this.font, speeds), y, speeds, this.speed, new boolean[]{true, true, true}, i -> {
         this.speed = i;
         Settings.generateSpeed(i);
      }, mouseX, mouseY, false);
      if (over == 2) {
         tip = Glass.tip(Ui.t("chunkatlas.generate.speed.fast.tip"), null);
      }

      y += Glass.segmentsHeight() + 8;
      Glass.check(g, this.font, this.hits, left, y, Ui.t("chunkatlas.generate.finish"), this.finish, () -> {
         this.finish = !this.finish;
         Settings.generateFinish(this.finish);
      });
      y += 16;
      if (this.single) {
         Glass.check(g, this.font, this.hits, left, y, Ui.t("chunkatlas.generate.hold"), this.hold, () -> {
            this.hold = !this.hold;
            Settings.generatePause(this.hold);
         });
         y += 16;
      }

      y += 10;
      String yes = Ui.t("chunkatlas.generate.button");
      String no = Ui.t("chunkatlas.confirm.cancel");
      int yw = Math.max(BUTTON_W, this.font.width(yes) + 24);
      int nw = Math.max(80, this.font.width(no) + 24);
      if (n[0] > 0) {
         g.text(this.font, GenerateView.about(n[0] / Generating.rate(ChunkGeneration.Speed.of(this.speed))), left, y + 6, Glass.LABEL);
      }

      Glass.button(g, this.font, this.hits, right - yw, y, yw, 20, yes, Glass.ACCENT, mouseX, mouseY, n[0] > 0 ? this::go : null);
      Glass.button(g, this.font, this.hits, right - yw - 8 - nw, y, nw, 20, no, Ui.WHITE, mouseX, mouseY, this::onClose);
      if (tip != null) {
         g.tooltip(this.font, tip, mouseX, mouseY);
      }

      super.draw(g, mouseX, mouseY, partial);
   }

   void tourGo(ChunkGeneration.Speed at) {
      this.speed = at.ordinal();
      this.go();
   }
}
