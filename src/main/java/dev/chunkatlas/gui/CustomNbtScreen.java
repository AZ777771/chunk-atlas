package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.ChunkTags;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class CustomNbtScreen extends AtlasScreen {
   private static final String K = "chunkatlas.custom.";
   private static final int W = 180;
   private static final int BOX_H = 16;
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private final Suggest suggest;
   private final String startTag;
   private int mode;
   private double lo;
   private double hi;
   private EditBox tag;
   private EditBox loBox;
   private EditBox hiBox;
   private String tagText;

   private long typedAt;
   private int px;
   private int py;
   private int pw;
   private int ph;

   CustomNbtScreen(MapScreen map, String tag, int mode, double lo, double hi) {
      super(Component.translatable(K + "title"));
      this.map = map;
      this.startTag = tag == null ? "" : tag;
      this.tagText = this.startTag;
      this.mode = mode;
      this.lo = lo;
      this.hi = hi;
      this.suggest = new Suggest(this.font);
   }

   @Override
   protected void init() {
      this.suggest.detach();
      this.pw = Math.min(W, this.width - 2 * Glass.MARGIN);
      this.px = Math.max(Glass.MARGIN, this.width - 31 - this.pw);
      this.py = Glass.MARGIN;
      int x = this.px + 6;
      int y = this.py + 20 + 12;
      this.tag = new EditBox(this.font, x, y, this.pw - 12, BOX_H, Component.translatable(K + "tag"));
      this.tag.setMaxLength(200);
      this.tag.setHint(Component.translatable(K + "tag.hint"));
      this.tag.setValue(this.tagText);
      this.tag.setResponder(s -> {
         this.tagText = s;
         this.typedAt = System.currentTimeMillis();
         if (this.tag.isFocused()) {
            this.suggest.edit(this.tag, this.paths());
         }
      });
      this.addRenderableWidget(this.tag);

      int ry = y + BOX_H + 12 + 20 + 14;
      int rw = Math.min(60, (this.pw - 12 - 8) / 2);
      this.loBox = this.rangeBox(x, ry, rw, this.lo, true);
      this.hiBox = this.rangeBox(this.px + this.pw - 6 - rw, ry, rw, this.hi, false);
      this.setInitialFocus(this.tag);
      this.suggest.edit(this.tag, this.paths());
   }

   private EditBox rangeBox(int x, int y, int w, double value, boolean low) {
      EditBox b = new EditBox(this.font, x, y, w, BOX_H, Component.translatable(K + (low ? "low" : "high")));
      b.setMaxLength(20);
      b.setValue(number(value));
      b.setResponder(s -> {
         try {
            double v = Double.parseDouble(s.trim());
            if (low) {
               this.lo = v;
            } else {
               this.hi = v;
            }

            b.setTextColor(Ui.WHITE);
         } catch (NumberFormatException e) {
            b.setTextColor(Ui.YELLOW);
         }
      });
      return this.addRenderableWidget(b);
   }

   private static String number(double v) {
      return v == Math.rint(v) && Math.abs(v) < 1.0E15 ? Long.toString((long)v) : Double.toString(v);
   }

   private List<Suggest.Option> paths() {
      List<Suggest.Option> p = this.map.customPaths();
      return p == null ? List.of() : p;
   }

   private boolean rangeOk() {
      return this.mode != MapScreen.CUSTOM_VALUE || this.lo != this.hi;
   }

   private void show() {
      String t = ChunkTags.join(ChunkTags.split(this.tagText));
      if (t.isEmpty() || !this.rangeOk()) {
         return;
      }

      Game.open(this.mc, this.map);
      this.map.showCustom(t, this.mode, this.lo, this.hi);
   }

   private void back() {
      Game.open(this.mc, this.map);
      this.map.openLayers();
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
   public void tick() {
      if (this.tag.isFocused() && this.suggest.box() == this.tag && !this.paths().isEmpty() && !this.suggest.open()) {
         this.suggest.options(this.paths());
      }
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      this.map.drawUnder(g, partial);
      this.hits.clear();
      boolean value = this.mode == MapScreen.CUSTOM_VALUE;
      this.loBox.visible = value;
      this.hiBox.visible = value;
      int x = this.px + 6;
      int y = this.py;
      this.ph = 20 + 12 + BOX_H + 12 + 20 + (value ? 14 + BOX_H + 8 : 4) + 14 + 8 + BOX_H + 6;
      g.layer();
      Glass.panel(g, this.px, this.py, this.pw, this.ph);
      g.fill(this.px, this.py, this.px + this.pw, this.py + this.ph, 0x66000000);

      boolean overBack = Glass.over(mouseX, mouseY, x - 2, y + 3, 12, 14);
      g.text(this.font, "‹", x + 1, y + 6, overBack ? Ui.WHITE : Glass.SOFT);
      this.hits.add(new Ui.Hit(x - 2, y + 3, x + 10, y + 17, this::back));
      g.text(this.font, Ui.t(K + "title"), x + 12, y + 7, Ui.WHITE);
      g.fill(this.px + 1, y + 19, this.px + this.pw - 1, y + 20, Glass.RULE);
      y += 20;
      g.text(this.font, Ui.t(K + "tag"), x, y + 2, Glass.LABEL);
      y += 12 + BOX_H + 6;
      g.text(this.font, Ui.t(K + "colour_by"), x, y, Glass.LABEL);
      y += 11;
      String[] modes = {Ui.t(K + "has"), Ui.t(K + "value"), Ui.t(K + "count")};
      int segW = Glass.segmentsWidth(this.font, modes);
      Glass.segments(g, this.font, this.hits, x + (this.pw - 12 - segW) / 2, y - 3, modes, this.mode, new boolean[]{true, true, true}, m -> this.mode = m, mouseX, mouseY, false);
      y += Glass.segmentsHeight() - 3 + 2;
      if (value) {
         int bx = x;
         int bw = this.pw - 12;
         for (int n = 0; n < bw; n++) {
            g.fill(bx + n, y + 3, bx + n + 1, y + 9, 0xFF000000 | rampOf(n / (double)(bw - 1)));
         }

         y += 14 + BOX_H + 8;
      } else {
         y += 4;
      }

      g.text(this.font, this.font.plainSubstrByWidth(this.countLine(), this.pw - 12), x, y, Glass.NOTE);
      y += 14 + 8;
      String show = Ui.t(K + "show");
      String cancel = Ui.t("chunkatlas.map.cancel");
      int sw = Math.max(56, this.font.width(show) + 20);
      int cw = Math.max(56, this.font.width(cancel) + 20);
      int right = this.px + this.pw - 6;
      boolean can = !ChunkTags.split(this.tagText).isEmpty() && this.rangeOk();
      Glass.button(g, this.font, this.hits, right - sw, y, sw, BOX_H, show, Glass.ACCENT, mouseX, mouseY, can ? this::show : null);
      Glass.button(g, this.font, this.hits, right - sw - 4 - cw, y, cw, BOX_H, cancel, Ui.WHITE, mouseX, mouseY, this::onClose);
      if (this.suggest.open()) {
         g.text(this.font, this.font.plainSubstrByWidth(Suggest.keysHint(), right - sw - 4 - cw - 6 - x), x, y + 4, Glass.NOTE);
      }

      super.draw(g, mouseX, mouseY, partial);
      this.suggest.draw(g, this.width, this.height, mouseX, mouseY);
   }

   private static int rampOf(double t) {
      int[] a = t < 0.5 ? new int[]{0x3B, 0x82, 0xF6} : new int[]{0xFA, 0xCC, 0x15};
      int[] b = t < 0.5 ? new int[]{0xFA, 0xCC, 0x15} : new int[]{0xEF, 0x44, 0x44};
      double k = t < 0.5 ? t * 2.0 : (t - 0.5) * 2.0;
      int r = (int)Math.round(a[0] + (b[0] - a[0]) * k);
      int gr = (int)Math.round(a[1] + (b[1] - a[1]) * k);
      int bl = (int)Math.round(a[2] + (b[2] - a[2]) * k);
      return r << 16 | gr << 8 | bl;
   }

   private String countLine() {
      String t = ChunkTags.join(ChunkTags.split(this.tagText));
      if (t.isEmpty()) {
         return Ui.t(K + "type_a_tag");
      }

      long have = 0L;
      long all = 0L;
      int waiting = 0;
      boolean settled = System.currentTimeMillis() - this.typedAt > 400L;
      for (WorldLayout.RegionInfo r : this.map.visibleRegions()) {
         ChunkTags.Facts f = settled ? this.map.customFacts(t, r) : this.map.customFactsCached(t, r);
         if (f == null) {
            waiting++;
         } else {
            have += f.found;
            all += f.present;
         }
      }

      String line = Ui.plural(K + "have", have, Ui.count(all));
      return waiting > 0 ? line + " · " + Ui.plural(K + "reading", waiting) : line;
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      double x = mouseX;
      double y = mouseY;
      if (this.suggest.mouseClicked(x, y, this.width, this.height)) {
         return true;
      }

      if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
         for (int n = this.hits.size() - 1; n >= 0; n--) {
            if (this.hits.get(n).has(x, y)) {
               this.hits.get(n).action().run();
               return true;
            }
         }
      }

      boolean taken = super.onClick(mouseX, mouseY, mouseButton, doubleClick);
      if (this.tag.isFocused()) {
         this.suggest.edit(this.tag, this.paths());
      } else {
         this.suggest.close();
      }

      return taken;
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      if (this.suggest.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      }

      if (Keys.escape(keyCode)) {
         this.onClose();
         return true;
      }

      if (Keys.confirm(keyCode)) {
         this.show();
         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
   }

   void tourShow(String text, int mode) {
      this.tag.setValue(text);
      this.mode = mode;
      this.show();
   }
}
