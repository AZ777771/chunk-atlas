package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.ChunkFilter;
import dev.chunkatlas.engine.ChunkRule;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.RegionFacts;
import dev.chunkatlas.engine.WorldMap;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class FilterScreen extends AtlasScreen {
   private static final int MAX_BORDER = 16;
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final MapScreen.FindHost host;
   private final RuleList rules;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private final List<Ui.Hit> overlayHits = new ArrayList<>();
   private int border;
   private int scroll;
   private int content;

   private int viewTop;
   private int viewBottom;
   private int barX;
   private boolean draggingBar;
   private int dragGrab;
   private boolean presetsOpen;
   private int[] presetsAt;

   private ChunkSelection matched = new ChunkSelection();
   private int regionsRead;
   private int generation;
   private int lastFacts = -1;
   private int ticks;
   private ChunkFilter current = ChunkFilter.empty();

   FilterScreen(MapScreen map, MapScreen.FindHost host) {
      super(Component.translatable("chunkatlas.filter.title"));
      this.map = map;
      this.host = host;
      this.rules = new RuleList(this.font, host.hasSpawn(), host.hasPlayer(), host.worldVersion(), this::changed);
      ChunkFilter last = host.last();
      this.border = Math.min(MAX_BORDER, last.buffer());
      this.rules.load(last);
   }

   @Override
   protected void init() {
      this.rules.build(this::addRenderableWidget);
      this.changed();
   }

   private void changed() {
      this.current = this.rules.filter(this.border);
      this.recount();
   }

   private void recount() {
      int gen = ++this.generation;
      ChunkFilter f = this.current;
      Map<Long, RegionFacts> facts = new HashMap<>(this.host.facts());
      this.lastFacts = facts.size();
      this.regionsRead = facts.size();
      if (f.isEmpty() || f.deep() || this.host.index() == null) {
         this.matched = f.deep() ? null : new ChunkSelection();
         this.host.clearPreview();
         return;
      }

      ChunkRule rule = f.toRule(this.host.context());
      WorldMap.Index index = this.host.index();
      ChunkSelection within = this.host.inSelection() ? this.host.selection().copy() : null;
      int grow = f.buffer();
      RulePanel.WORKER.execute(() -> {
         ChunkSelection out = RulePanel.matches(rule, index, facts, within, grow);
         this.mc.execute(() -> {
            if (gen == this.generation && Game.screen(this.mc) == this) {
               this.matched = out;
               this.host.preview(out);
            }
         });
      });
   }

   @Override
   public void tick() {
      if (this.rules.dirty) {
         this.rules.dirty = false;
         this.rebuildWidgets();
      }

      if (++this.ticks % 20 == 0 && this.host.facts().size() != this.lastFacts) {
         this.recount();
      }
   }

   private boolean ready() {
      return !this.current.isEmpty();
   }

   private void select() {
      if (this.ready()) {
         this.rules.closeOverlays();
         Game.open(this.mc, this.map);
         this.host.run(this.current);
      }
   }

   void tourOpenPresets() {
      this.presetsOpen = true;
   }

   @Override
   public void onClose() {
      this.host.clearPreview();
      Game.open(this.mc, this.map);
   }

   @Override
   public void removed() {
      this.host.clearPreview();
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
      this.hits.clear();
      this.overlayHits.clear();
      boolean covered = this.rules.overlayOpen() || this.presetsOpen;
      int mx = covered ? -1000 : mouseX;
      int my = covered ? -1000 : mouseY;
      int[] b = RulePanel.bounds(this.width, this.height);
      int body = RulePanel.frame(g, this.font, b, Ui.t("chunkatlas.filter.title"), null);
      String presets = Ui.t("chunkatlas.filter.quick") + " ▾";
      int pw = this.font.width(presets) + 12;
      int px = b[0] + b[2] - RulePanel.PAD - pw;
      int py = b[1] + (RulePanel.HEADER - RulePanel.CTRL) / 2;
      this.presetsAt = new int[]{px, py + RulePanel.CTRL + 1};
      Glass.button(g, this.font, this.hits, px, py, pw, RulePanel.CTRL, presets, Ui.WHITE, mx, my, () -> {
         this.rules.closeOverlays();
         this.presetsOpen = !this.presetsOpen;
      });

      int lx = RulePanel.leftX(b);
      int lw = RulePanel.leftW(b);
      int hy = body + 5;
      RulePanel.heading(g, this.font, lx, hy, Ui.t("chunkatlas.filter.rules"));
      this.viewTop = hy + 14;
      this.viewBottom = RulePanel.footerTop(b) - 4;
      int view = this.viewBottom - this.viewTop;
      int listW = lw - RulePanel.BAR_W - 4;
      this.content = this.rules.layout(lx, this.viewTop - this.scroll, listW, this.viewTop, this.viewBottom);
      int max = Math.max(0, this.content - view);
      if (this.scroll > max) {
         this.scroll = max;
         this.content = this.rules.layout(lx, this.viewTop - this.scroll, listW, this.viewTop, this.viewBottom);
      }

      g.scissor(lx, this.viewTop, lx + lw, this.viewBottom);
      this.rules.draw(g, this.hits, mx, my);
      g.unscissor();
      if (max > 0) {
         RulePanel.fades(g, lx, this.viewTop, listW, view, this.scroll > 0, this.scroll < max);
         this.barX = lx + lw - RulePanel.BAR_W;
         RulePanel.scrollbar(g, this.barX, this.viewTop, view, this.content, this.scroll, this.draggingBar || Glass.over(mx, my, this.barX - 2, this.viewTop, RulePanel.BAR_W + 4, view));
      }

      int rx = RulePanel.rightX(b);
      int ry = body + 5;
      RulePanel.heading(g, this.font, rx, ry, Ui.t("chunkatlas.filter.search_in"));
      ry += 13;
      boolean inSel = this.host.inSelection();
      boolean hasSel = !this.host.selection().isEmpty();
      RulePanel.radio(g, this.font, this.hits, rx, ry, Ui.t("chunkatlas.filter.whole_world"), !inSel, () -> this.inSelection(false));
      ry += 12;
      RulePanel.radio(g, this.font, this.hits, rx, ry, Ui.t("chunkatlas.filter.selection_only"), inSel, hasSel || inSel ? () -> this.inSelection(true) : null);
      ry += 18;
      RulePanel.heading(g, this.font, rx, ry, Ui.t("chunkatlas.filter.border"));
      ry += 12;
      RulePanel.stepper(
         g, this.font, this.hits, rx, ry, RulePanel.RIGHT_W, Ui.amount(this.border, "chunks"), mx, my,
         this.border > 0 ? () -> this.setBorder(this.border - 1) : null,
         this.border < MAX_BORDER ? () -> this.setBorder(this.border + 1) : null
      );
      ry += RulePanel.CTRL + 8;
      RulePanel.heading(g, this.font, rx, ry, Ui.t("chunkatlas.filter.with_selection"));
      ry += 13;
      int mode = this.host.mode();
      String[] modes = {Ui.t("chunkatlas.filter.replace"), Ui.t("chunkatlas.filter.add_to"), Ui.t("chunkatlas.filter.remove_from")};
      for (int i = 0; i < modes.length; i++) {
         int m = i;
         RulePanel.radio(g, this.font, this.hits, rx, ry, modes[i], mode == i, () -> this.host.mode(m));
         ry += 12;
      }

      int fy = RulePanel.footerY(b);
      String selectText = this.matched != null && !this.current.isEmpty()
         ? Ui.t("chunkatlas.filter.select_n", Ui.count(this.matched.size()))
         : Ui.t("chunkatlas.filter.select");
      String cancelText = Ui.t("chunkatlas.filter.cancel");
      int sw = RulePanel.buttonWidth(this.font, selectText);
      int cw = RulePanel.buttonWidth(this.font, cancelText);
      int sx = b[0] + b[2] - RulePanel.PAD - sw;
      int cx = sx - 6 - cw;
      Glass.button(g, this.font, this.hits, sx, fy, sw, RulePanel.CTRL, selectText, Glass.ACCENT, mx, my, this.ready() ? this::select : null);
      Glass.button(g, this.font, this.hits, cx, fy, cw, RulePanel.CTRL, cancelText, Ui.WHITE, mx, my, this::onClose);
      String note = this.rules.note();
      String status;
      int colour = Glass.LABEL;
      if (note != null) {
         status = note;
         colour = Glass.WARNING;
      } else {
         status = this.matched == null || this.current.isEmpty() ? "" : Ui.plural("chunkatlas.filter.matches", this.matched.size());
      }

      g.text(this.font, Ui.fit(this.font, status, cx - 8 - lx), lx, fy + (RulePanel.CTRL - 8) / 2, colour);
      super.draw(g, mouseX, mouseY, partial);
      this.rules.drawOverlays(g, this.overlayHits, mouseX, mouseY, this.width, this.height);
      if (this.presetsOpen) {
         List<Ui.Item> items = new ArrayList<>();
         for (RuleList.Quick q : this.rules.quickPicks()) {
            items.add(new Ui.Item(Ui.t(q.key()), null, () -> this.rules.quick(q.condition())));
         }

         int mw = Glass.menuWidth(this.font, items);
         int menuX = Math.max(2, Math.min(this.presetsAt[0] + pw - mw, this.width - mw - 2));
         g.layer();
         Glass.menu(g, this.font, this.overlayHits, items, menuX, this.presetsAt[1], mouseX, mouseY);
      }
   }

   private void inSelection(boolean on) {
      if (this.host.inSelection() != on) {
         this.host.inSelection(on);
         this.recount();
      }
   }

   private void setBorder(int n) {
      this.border = Math.max(0, Math.min(MAX_BORDER, n));
      this.changed();
   }

   private int maxScroll() {
      return Math.max(0, this.content - (this.viewBottom - this.viewTop));
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      double x = mouseX;
      double y = mouseY;
      if (this.presetsOpen) {
         this.presetsOpen = false;
         for (Ui.Hit h : List.copyOf(this.overlayHits)) {
            if (h.has(x, y)) {
               h.action().run();
               break;
            }
         }

         return true;
      }

      if (this.rules.mouseClicked(x, y, this.overlayHits)) {
         return true;
      }

      if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
         int view = this.viewBottom - this.viewTop;
         if (this.maxScroll() > 0 && x >= this.barX - 2 && x < this.barX + RulePanel.BAR_W + 2 && y >= this.viewTop && y < this.viewBottom) {
            int[] knob = RulePanel.knob(this.viewTop, view, this.content, this.scroll);
            this.draggingBar = true;
            this.dragGrab = y >= knob[0] && y < knob[0] + knob[1] ? (int)y - knob[0] : knob[1] / 2;
            this.barTo(y);
            return true;
         }

         for (int n = this.hits.size() - 1; n >= 0; n--) {
            if (this.hits.get(n).has(x, y)) {
               this.clearFocus();
               this.hits.get(n).action().run();
               return true;
            }
         }
      }

      if (super.onClick(mouseX, mouseY, mouseButton, doubleClick)) {
         return true;
      }

      this.clearFocus();
      return true;
   }

   private void barTo(double y) {
      int view = this.viewBottom - this.viewTop;
      int knob = Math.max(12, view * view / Math.max(1, this.content));
      double along = (y - this.dragGrab - this.viewTop) / Math.max(1, view - knob);
      this.scroll = (int)Math.round(Math.max(0.0, Math.min(1.0, along)) * this.maxScroll());
   }

   @Override
   protected boolean onDrag(double mouseX, double mouseY, int mouseButton, double dx, double dy) {
      if (this.draggingBar) {
         this.barTo(mouseY);
         return true;
      }

      return super.onDrag(mouseX, mouseY, mouseButton, dx, dy);
   }

   @Override
   protected boolean onRelease(double mouseX, double mouseY, int mouseButton) {
      this.draggingBar = false;
      return super.onRelease(mouseX, mouseY, mouseButton);
   }

   @Override
   protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
      int[] b = RulePanel.bounds(this.width, this.height);
      if (Glass.over((int)mouseX, (int)mouseY, b[0], b[1], b[2], b[3])) {
         if (this.maxScroll() > 0 && !this.rules.overlayOpen()) {
            this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)Math.round(scrollY * 16.0)));
         }

         return true;
      }

      return true;
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      if (this.presetsOpen && Keys.escape(keyCode)) {
         this.presetsOpen = false;
         return true;
      }

      if (this.rules.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      }

      if (Keys.escape(keyCode)) {
         this.onClose();
         return true;
      }

      if (Keys.confirm(keyCode) && !this.rules.overlayOpen()) {
         this.select();
         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
   }
}
