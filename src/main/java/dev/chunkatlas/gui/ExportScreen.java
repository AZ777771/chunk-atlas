package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.RegionFile;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.engine.WorldMap;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class ExportScreen extends AtlasScreen {
   private static final int THUMB_W = 150;
   private static final int THUMB_H = 100;
   private static final int CARD_W = 400;
   private static final int BUTTON_W = 110;

   interface Done {
      void export(String name, boolean newWorld);
   }

   private final Minecraft mc = Minecraft.getInstance();
   private final Screen parent;
   private final MapScreen map;
   private final ChunkSelection chunks;
   private final int count;
   private final String startName;
   private final Done done;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private EditBox name;
   private boolean newWorld;

   private volatile long bytes = -1L;

   ExportScreen(Screen parent, int chunks, String startName, Done done) {
      this(parent, parent instanceof MapScreen m ? m : null, null, chunks, startName, done);
   }

   ExportScreen(MapScreen map, ChunkSelection chunks, String startName, Done done) {
      this(map, map, chunks, chunks.size(), startName, done);
   }

   private ExportScreen(Screen parent, MapScreen map, ChunkSelection chunks, int count, String startName, Done done) {
      super(Component.translatable("chunkatlas.export.title"));
      this.parent = parent;
      this.map = map;
      this.chunks = chunks;
      this.count = count;
      this.startName = startName;
      this.done = done;
      this.measure();
   }

   private void measure() {
      WorldMap.Index index = this.map == null ? null : this.map.indexNow();
      if (this.chunks == null || index == null) {
         return;
      }

      ChunkSelection sel = this.chunks;
      Thread t = new Thread(() -> {
         long total = 0L;
         for (long region : sel.regions()) {
            WorldLayout.RegionInfo info = index.regions().get(region);
            if (info == null) {
               continue;
            }

            try {
               RegionFile.Header h = RegionFile.header(info.file());
               long[] bits = sel.bits((int)(region >> 32), (int)region);
               for (int i = 0; i < 1024; i++) {
                  if ((bits[i >> 6] >>> (i & 63) & 1L) != 0L && h.present(i)) {
                     total += (long)h.sectors(i) * RegionFile.SECTOR;
                  }
               }
            } catch (java.io.IOException | RuntimeException ignored) {
            }
         }

         this.bytes = total;
      }, "Chunk Atlas export size");
      t.setDaemon(true);
      t.start();
   }

   private int cardW() {
      return Math.min(this.width - 20, CARD_W);
   }

   private int textW() {
      return this.cardW() - THUMB_W - 48;
   }

   private List<FormattedCharSequence> note() {
      return this.font.split(Component.translatable(this.newWorld ? "chunkatlas.export.world.note" : "chunkatlas.export.clip.note"), this.textW());
   }

   private int bodyH() {
      int lines = 1;
      for (String key : new String[]{"chunkatlas.export.clip.note", "chunkatlas.export.world.note"}) {
         lines = Math.max(lines, this.font.split(Component.translatable(key), this.textW()).size());
      }

      return 16 + 10 + 20 + 8 + 10 + Glass.segmentsHeight() + 6 + lines * 11 + 11;
   }

   private int cardH() {
      return 14 + 12 + 10 + Math.max(this.bodyH(), THUMB_H + 12) + 10 + 20 + 14;
   }

   private int cardY() {
      return Math.max(10, (this.height - this.cardH()) / 2);
   }

   private int textX() {
      return (this.width - this.cardW()) / 2 + 16 + THUMB_W + 16;
   }

   @Override
   protected void init() {
      int y = this.cardY() + 14 + 22 + 16 + 10;
      this.name = new EditBox(this.font, this.textX(), y, this.textW(), 20, Component.translatable("chunkatlas.export.name"));
      this.name.setMaxLength(64);
      this.name.setValue(this.startName);
      this.addRenderableWidget(this.name);
      this.setInitialFocus(this.name);
   }

   private boolean canGo() {
      return this.name != null && !this.name.getValue().isBlank();
   }

   private void go() {
      String n = this.name.getValue().trim();
      if (!n.isEmpty()) {
         Game.open(this.mc, this.parent);
         this.done.export(n, this.newWorld);
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
   public void onClose() {
      Game.open(this.mc, this.parent);
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }

   @Override
   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      super.drawBackground(g, mouseX, mouseY, partial);
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      if (this.map != null) {
         this.map.drawUnder(g, partial);
         g.fill(0, 0, this.width, this.height, 0x66000000);
      }

      this.hits.clear();
      int cardW = this.cardW();
      int h = this.cardH();
      int cardX = (this.width - cardW) / 2;
      int y = this.cardY();
      int cx = this.width / 2;
      Glass.panel(g, cardX, y, cardW, h);
      g.fill(cardX, y, cardX + cardW, y + h, 0x66000000);
      y += 14;
      g.centered(this.font, Component.literal(Ui.t("chunkatlas.export.title")).withStyle(ChatFormatting.BOLD), cx, y, Ui.WHITE);
      y += 22;
      int left = cardX + 16;
      int top = y;

      Thumbnail.draw(g, this.map, left, y, THUMB_W, THUMB_H, this.chunks, Glass.ACCENT, false);
      String facts = Ui.chunks(this.count);
      if (this.chunks != null) {
         facts += " · " + Ui.plural("chunkatlas.map.drag.regions", this.chunks.regions().size());
      }

      long size = this.bytes;
      if (size >= 0L) {
         facts += " · " + Ui.size(size);
      }

      g.text(this.font, this.font.plainSubstrByWidth(facts, THUMB_W + 8), left, y + THUMB_H + 4, Ui.GREY);

      int tx = this.textX();
      int textW = this.textW();
      g.text(this.font, Ui.t("chunkatlas.export.name"), tx, y + 16, Glass.NOTE);
      y += 16 + 10 + 20 + 8;
      g.text(this.font, Ui.t("chunkatlas.export.save_as"), tx, y, Glass.NOTE);
      y += 10;
      String[] labels = {Ui.t("chunkatlas.export.to.clip"), Ui.t("chunkatlas.export.to.world")};
      Glass.segments(g, this.font, this.hits, tx, y, labels, this.newWorld ? 1 : 0, new boolean[]{true, true}, i -> this.newWorld = i == 1, mouseX, mouseY, false);
      y += Glass.segmentsHeight() + 6;
      for (FormattedCharSequence line : this.note()) {
         g.text(this.font, line, tx, y, Ui.GREY);
         y += 11;
      }

      String typed = this.name == null ? this.startName : this.name.getValue().trim();
      String where = Ui.t(this.newWorld ? "chunkatlas.export.world.where" : "chunkatlas.export.clip.where", typed.isEmpty() ? "…" : typed);
      g.text(this.font, this.font.plainSubstrByWidth(where, textW), tx, y, Glass.SOFT);

      int by = top + Math.max(this.bodyH(), THUMB_H + 12) + 10;
      int right = cardX + cardW - 16;
      String yes = Ui.plural("chunkatlas.export.go", this.count);
      String no = Ui.t("chunkatlas.confirm.cancel");
      int yw = Math.max(BUTTON_W, this.font.width(yes) + 24);
      int nw = Math.max(80, this.font.width(no) + 24);
      Glass.button(g, this.font, this.hits, right - yw, by, yw, 20, yes, Glass.ACCENT, mouseX, mouseY, this.canGo() ? this::go : null);
      Glass.button(g, this.font, this.hits, right - yw - 8 - nw, by, nw, 20, no, Ui.WHITE, mouseX, mouseY, this::onClose);
      super.draw(g, mouseX, mouseY, partial);
   }
}
