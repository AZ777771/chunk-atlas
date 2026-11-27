package dev.chunkatlas.gui;

import dev.chunkatlas.engine.IdText;
import dev.chunkatlas.engine.SeedMapSettings;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Input;
import dev.chunkatlas.port.Keys;
import dev.chunkatlas.port.Texts;
import dev.chunkatlas.seed.SeedSampler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.WorldOptions;

final class SeedPanel {
   private static final int MIN_W = 220;
   private static final int PAD = 6;

   private static final int ROW = 18;
   private static final int BIOME_ROW = 12;
   private static final int BOX_H = 16;
   private final MapScreen host;
   private final SeedMapLayer layer;
   private final Minecraft mc = Minecraft.getInstance();
   private final Font font;
   private boolean open;
   private SeedPanel.Tab tab = SeedPanel.Tab.STRUCTURES;
   private boolean editingSeed;
   private int scroll;
   private EditBox seedBox;
   private EditBox search;
   private boolean seedBoxShown;
   private boolean searchShown;
   private String query = "";
   private int x;
   private int y;
   private int w;
   private int h;
   private int[] list = {0, 0, 0, 0};
   private int contentH;
   private Object biomesFor;
   private List<SeedPanel.Biome> biomes = List.of();

   enum Tab {
      STRUCTURES,
      BIOMES;
   }

   record Biome(int number, String id, String name, int colour) {
   }

   SeedPanel(MapScreen host, SeedMapLayer layer, Font font) {
      this.host = host;
      this.layer = layer;
      this.font = font;
   }

   boolean isOpen() {
      return this.open;
   }

   SeedPanel.Tab tab() {
      return this.tab;
   }

   void open(SeedPanel.Tab tab, boolean editSeed) {
      this.open = true;
      this.tab = tab;
      this.scroll = 0;
      if (editSeed || this.layer.needsSeed()) {
         this.editSeed();
      } else if (this.layer.available() && !this.layer.shown()) {
         this.layer.toggle();
      }
   }

   void close() {
      this.open = false;
      this.unfocus();
   }

   void toggle() {
      if (this.open) {
         this.close();
      } else {
         this.open(this.tab, false);
      }
   }

   boolean typing() {
      return this.focused() != null;
   }

   private EditBox focused() {
      if (this.seedBox != null && this.seedBoxShown && this.seedBox.isFocused()) {
         return this.seedBox;
      }

      return this.search != null && this.searchShown && this.search.isFocused() ? this.search : null;
   }

   private void unfocus() {
      if (this.seedBox != null) {
         this.seedBox.setFocused(false);
      }

      if (this.search != null) {
         this.search.setFocused(false);
      }
   }

   private EditBox seedBox() {
      if (this.seedBox == null) {
         this.seedBox = new EditBox(this.font, 0, 0, 100, BOX_H, Component.translatable("chunkatlas.seed.server.hint"));
         this.seedBox.setMaxLength(64);
      }

      return this.seedBox;
   }

   private EditBox search() {
      if (this.search == null) {
         this.search = new EditBox(this.font, 0, 0, 100, BOX_H, Component.translatable("chunkatlas.seed.biomes.search"));
         this.search.setHint(Texts.searchHint(Component.translatable("chunkatlas.seed.biomes.search")));
         this.search.setMaxLength(40);
         this.search.setResponder(text -> {
            this.query = text;
            this.scroll = 0;
         });
      }

      return this.search;
   }

   private void editSeed() {
      EditBox box = this.seedBox();
      Long seed = this.layer.shownSeed();
      box.setValue(seed == null ? "" : Long.toString(seed));
      box.setHint(Component.translatable(this.layer.onServer() ? "chunkatlas.seed.server.hint" : "chunkatlas.seed.panel.world_seed_hint"));
      this.editingSeed = true;
      this.unfocus();
      box.setFocused(true);
   }

   private void useSeed() {
      Long seed = SeedMapSettings.parseSeed(this.seedBox().getValue());
      if (seed != null) {
         this.layer.useSeed(seed);
         this.editingSeed = false;
         this.unfocus();
      }
   }

   private void randomSeed() {
      long seed = WorldOptions.randomSeed();
      this.seedBox().setValue(Long.toString(seed));
      this.layer.useSeed(seed);
      this.editingSeed = false;
      this.unfocus();
   }

   private void copySeed() {
      Long seed = this.layer.shownSeed();
      if (seed != null) {
         this.mc.keyboardHandler.setClipboard(Long.toString(seed));
         this.host.seedTell(Ui.t("chunkatlas.context.copied", Long.toString(seed)), Ui.GREY);
      }
   }

   boolean contains(double mx, double my) {
      return this.open && mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
   }

   boolean focusAt(double mouseX, double mouseY, int button, int mods) {
      boolean took = false;
      EditBox[] boxes = {this.seedBox, this.search};
      boolean[] shown = {this.seedBoxShown, this.searchShown};
      for (int i = 0; i < boxes.length; i++) {
         EditBox b = boxes[i];
         if (b == null) {
            continue;
         }

         if (shown[i] && Glass.over((int)mouseX, (int)mouseY, b.getX(), b.getY(), b.getWidth(), b.getHeight())) {
            b.setFocused(true);
            Input.click(b, mouseX, mouseY, button, mods, false);
            took = true;
         } else {
            b.setFocused(false);
         }
      }

      return took;
   }

   boolean keyPressed(int key, int scan, int mods) {
      EditBox box = this.focused();
      if (box == null) {
         return false;
      }

      if (Keys.escape(key)) {
         box.setFocused(false);
         if (box == this.seedBox && !this.layer.needsSeed()) {
            this.editingSeed = false;
         }
      } else if (Keys.confirm(key)) {
         if (box == this.seedBox) {
            this.useSeed();
         } else {
            this.findNearestBiome();
         }
      } else {
         Input.key(box, key, scan, mods);
      }

      return true;
   }

   boolean charTyped(int codepoint, int mods) {
      EditBox box = this.focused();
      return box != null && Input.chars(box, codepoint, mods);
   }

   void scroll(double dy) {
      int room = Math.max(0, this.contentH - this.list[3]);
      this.scroll = Math.max(0, Math.min(room, this.scroll - (int)Math.round(dy * 3 * BIOME_ROW)));
   }

   private int width(List<String> kinds) {
      int name = 0;
      for (String k : kinds) {
         name = Math.max(name, this.font.width(SeedMapLayer.kindName(k)));
      }

      int col = 32 + Math.min(name, 110) + 6;
      return Math.max(MIN_W, 2 * col + 2 * PAD);
   }

   private List<SeedPanel.Biome> biomes() {
      SeedSampler world = this.layer.world();
      if (world != this.biomesFor) {
         this.biomesFor = world;
         this.biomes = this.layer.biomeList();
      }

      String q = this.query.strip().toLowerCase(Locale.ROOT);
      if (q.isEmpty()) {
         return this.biomes;
      }

      List<SeedPanel.Biome> out = new ArrayList<>();
      for (SeedPanel.Biome b : this.biomes) {
         if (b.name().toLowerCase(Locale.ROOT).contains(q) || IdText.path(b.id()).contains(q.replace(' ', '_'))) {
            out.add(b);
         }
      }

      return out;
   }

   List<Component> draw(Gfx g, List<Ui.Hit> hits, int right, int top, int bottom, int mouseX, int mouseY, boolean covered, float partial) {
      int mx = covered ? -1000 : mouseX;
      int my = covered ? -1000 : mouseY;
      List<String> kinds = this.layer.kinds();
      List<String> structures = new ArrayList<>();
      List<String> features = new ArrayList<>();
      for (String k : kinds) {
         (SeedSampler.isFeature(k) ? features : structures).add(k);
      }

      boolean needsSeed = this.layer.needsSeed();
      boolean ready = this.layer.available();
      boolean seedRow = needsSeed || this.editingSeed;
      this.seedBoxShown = seedRow;
      this.searchShown = !needsSeed && this.tab == SeedPanel.Tab.BIOMES;
      this.w = this.width(kinds);
      this.x = right - this.w;
      this.y = top;
      int headH = 5 + 13 + 13 + BOX_H + 4;
      int tabsH = Glass.segmentsHeight() + 4;
      int subH = this.tab == SeedPanel.Tab.STRUCTURES ? 18 : BOX_H + 4;
      int footH = this.tab == SeedPanel.Tab.BIOMES ? BOX_H + 6 : 0;
      List<SeedPanel.Biome> shownBiomes = this.searchShown ? this.biomes() : List.of();
      if (needsSeed) {
         this.contentH = 0;
         this.h = headH + 12 + PAD;
      } else if (this.tab == SeedPanel.Tab.STRUCTURES) {
         int rows = (structures.size() + 1) / 2;
         this.contentH = kinds.isEmpty() ? 14 : rows * ROW + (features.isEmpty() ? 0 : 14 + (this.layer.featuresOpen() ? (features.size() + 1) / 2 * ROW : 0));
         this.h = headH + tabsH + subH + this.contentH + PAD;
      } else {
         this.contentH = Math.max(14, shownBiomes.size() * BIOME_ROW);
         this.h = headH + tabsH + subH + this.contentH + footH + PAD;
      }

      this.h = Math.min(this.h, Math.max(headH + tabsH + subH + footH + 3 * ROW, bottom - top));
      Glass.panel(g, this.x, this.y, this.w, this.h);
      List<Component> tip = null;
      int row = this.y + 5;
      g.text(this.font, Ui.t("chunkatlas.seed.pill"), this.x + PAD, row, Ui.WHITE);

      String shownLabel = Ui.t("chunkatlas.seed.panel.shown");
      int cx = this.x + this.w - PAD - 12 - this.font.width(shownLabel);
      Glass.check(g, this.font, hits, cx, row, shownLabel, this.layer.shown(), ready || needsSeed ? this.layer::toggle : null);

      row += 13;
      String terrainLabel = Ui.t("chunkatlas.seed.menu.terrain");
      String structuresLabel = Ui.t("chunkatlas.seed.menu.structures");
      boolean usable = ready && this.layer.shown();
      Glass.check(g, this.font, hits, this.x + PAD, row, terrainLabel, this.layer.terrainOn(), usable ? () -> this.layer.terrainOn(!this.layer.terrainOn()) : null);
      Glass.check(g, this.font, hits, this.x + PAD + 22 + this.font.width(terrainLabel), row, structuresLabel, this.layer.structuresOn(),
         usable ? () -> this.layer.structuresOn(!this.layer.structuresOn()) : null);
      row += 13;
      tip = this.drawSeedRow(g, hits, row, seedRow, mx, my, partial);
      row += BOX_H + 4;
      if (needsSeed) {
         String kept = Ui.t(this.layer.onServer() ? "chunkatlas.seed.server.kept" : "chunkatlas.seed.panel.kept_world");
         g.text(this.font, this.font.plainSubstrByWidth(kept, this.w - 2 * PAD), this.x + PAD, row, Glass.NOTE);
         this.list = new int[]{this.x, row, this.w, 0};
         return tip;
      }

      String[] tabs = {Ui.t("chunkatlas.seed.types.title"), Ui.t("chunkatlas.seed.panel.biomes")};
      Glass.segments(g, this.font, hits, this.x + PAD, row, tabs, this.tab.ordinal(), new boolean[]{true, true}, i -> {
         this.tab = SeedPanel.Tab.values()[i];
         this.scroll = 0;
         this.unfocus();
      }, mx, my, covered);
      row += tabsH;
      int listTop;
      if (this.tab == SeedPanel.Tab.STRUCTURES) {
         List<String> all = new ArrayList<>(structures);
         if (this.layer.featuresOpen()) {
            all.addAll(features);
         }

         boolean any = !all.isEmpty() && ready;
         int bw = this.font.width(Ui.t("chunkatlas.seed.types.all")) + 12;
         Glass.button(g, this.font, hits, this.x + PAD, row, bw, 14, Ui.t("chunkatlas.seed.types.all"), Ui.WHITE, mx, my, any ? () -> this.setAll(all, true) : null);
         int bw2 = this.font.width(Ui.t("chunkatlas.seed.types.none")) + 12;
         Glass.button(g, this.font, hits, this.x + PAD + bw + 3, row, bw2, 14, Ui.t("chunkatlas.seed.types.none"), Ui.WHITE, mx, my, any ? () -> this.setAll(all, false) : null);
         listTop = row + subH;
      } else {
         EditBox box = this.search();
         box.setX(this.x + PAD);
         box.setY(row);
         box.setWidth(this.w - 2 * PAD);
         g.widget(box, mouseX, mouseY, partial);
         listTop = row + subH;
      }

      int listBottom = this.y + this.h - PAD - footH;
      this.list = new int[]{this.x + PAD, listTop, this.w - 2 * PAD, listBottom - listTop};
      this.scroll = Math.max(0, Math.min(Math.max(0, this.contentH - this.list[3]), this.scroll));
      g.scissor(this.x, listTop, this.x + this.w, listBottom);
      List<Component> t2 = this.tab == SeedPanel.Tab.STRUCTURES
         ? this.drawKinds(g, hits, structures, features, listTop - this.scroll, listTop, listBottom, mx, my)
         : this.drawBiomes(g, hits, shownBiomes, listTop - this.scroll, listTop, listBottom, mx, my);
      g.unscissor();
      if (t2 != null) {
         tip = t2;
      }

      if (this.contentH > this.list[3]) {
         int track = this.list[3];
         int knob = Math.max(6, track * track / this.contentH);
         int at = listTop + (track - knob) * this.scroll / Math.max(1, this.contentH - track);
         g.fill(this.x + this.w - 3, listTop, this.x + this.w - 2, listBottom, Glass.RULE);
         g.fill(this.x + this.w - 3, at, this.x + this.w - 2, at + knob, Glass.QUIET);
      }

      if (this.tab == SeedPanel.Tab.BIOMES) {
         this.drawBiomeFooter(g, hits, listBottom + 4, mx, my);
      }

      return tip;
   }

   private List<Component> drawSeedRow(Gfx g, List<Ui.Hit> hits, int row, boolean editing, int mx, int my, float partial) {
      List<Component> tip = null;
      int left = this.x + PAD;
      int right = this.x + this.w - PAD;
      if (editing) {
         String use = Ui.t("chunkatlas.seed.server.use");
         int useW = this.font.width(use) + 14;
         boolean canUse = SeedMapSettings.parseSeed(this.seedBox().getValue()) != null;
         Glass.button(g, this.font, hits, right - useW, row, useW, BOX_H, use, Glass.ACCENT, mx, my, canUse ? this::useSeed : null);
         right -= useW + 3;
         if (this.layer.offline()) {
            String random = Ui.t("chunkatlas.preview.random");
            int rw = this.font.width(random) + 12;
            Glass.button(g, this.font, hits, right - rw, row, rw, BOX_H, random, Ui.WHITE, mx, my, this::randomSeed);
            right -= rw + 3;
         }

         EditBox box = this.seedBox();
         box.setX(left);
         box.setY(row);
         box.setWidth(Math.max(40, right - left));
         g.widget(box, mx, my, partial);
         return tip;
      }

      Long seed = this.layer.shownSeed();
      String text = seed == null ? "…" : Long.toString(seed);
      String copy = Ui.t("chunkatlas.seed.panel.copy");
      int copyW = this.font.width(copy) + 12;
      int changeW = 0;
      if (this.layer.seedEditable()) {
         String change = Ui.t("chunkatlas.seed.panel.change");
         changeW = this.font.width(change) + 12;
         Glass.button(g, this.font, hits, right - changeW, row, changeW, BOX_H, change, Ui.WHITE, mx, my, this::editSeed);
         changeW += 3;
      }

      Glass.button(g, this.font, hits, right - changeW - copyW, row, copyW, BOX_H, copy, Ui.WHITE, mx, my, seed == null ? null : this::copySeed);
      if (!Glass.over(mx, my, right - changeW - copyW, row, copyW, BOX_H)) {
         tip = null;
      } else {
         tip = Glass.tip(Ui.t("chunkatlas.preview.copy"), null);
      }

      int room = right - changeW - copyW - 4 - left;
      g.text(this.font, this.font.plainSubstrByWidth(text, room), left, row + 4, Glass.ACCENT);
      return tip;
   }

   private void setAll(List<String> kinds, boolean on) {
      for (String k : kinds) {
         this.layer.kindShown(k, on);
      }
   }

   private List<Component> drawKinds(Gfx g, List<Ui.Hit> hits, List<String> structures, List<String> features, int at, int top, int bottom, int mx, int my) {
      int left = this.x + PAD;
      if (structures.isEmpty() && features.isEmpty()) {
         String note = Ui.t(this.layer.shown() ? "chunkatlas.preview.working" : "chunkatlas.seed.panel.off");
         g.text(this.font, this.font.plainSubstrByWidth(note, this.w - 2 * PAD), left, at + 3, Glass.NOTE);
         return null;
      }

      List<Component> tip = null;
      int colW = (this.w - 2 * PAD) / 2;
      int half = (structures.size() + 1) / 2;
      for (int i = 0; i < structures.size(); i++) {
         int col = i / half;
         int ry = at + i % half * ROW;
         List<Component> t = this.kindRow(g, hits, left + col * colW, ry, colW, structures.get(i), top, bottom, mx, my);
         if (t != null) {
            tip = t;
         }
      }

      at += half * ROW;
      if (!features.isEmpty()) {
         String label = (this.layer.featuresOpen() ? "▾ " : "▸ ") + Ui.t("chunkatlas.seed.types.features");
         boolean hover = Glass.over(mx, my, left, at, this.w - 2 * PAD, 14) && my >= top && my < bottom;
         g.text(this.font, label, left + 2, at + 3, hover ? Ui.WHITE : Glass.LABEL);
         this.clippedHit(hits, left, at, this.w - 2 * PAD, 14, top, bottom, () -> this.layer.featuresOpen(!this.layer.featuresOpen()));
         at += 14;
         if (this.layer.featuresOpen()) {
            int fhalf = (features.size() + 1) / 2;
            for (int i = 0; i < features.size(); i++) {
               int col = i / fhalf;
               int ry = at + i % fhalf * ROW;
               List<Component> t = this.kindRow(g, hits, left + col * colW, ry, colW, features.get(i), top, bottom, mx, my);
               if (t != null) {
                  tip = t;
               }
            }
         }
      }

      return tip;
   }

   private List<Component> kindRow(Gfx g, List<Ui.Hit> hits, int x, int y, int colW, String kind, int top, int bottom, int mx, int my) {
      if (y + ROW <= top || y >= bottom) {
         return null;
      }

      boolean on = this.layer.kindShown(kind);
      boolean hover = Glass.over(mx, my, x, y, colW, ROW) && my >= top && my < bottom;
      if (hover) {
         g.fill(x, y, x + colW, y + ROW, Glass.TINT);
      }

      pixelCheck(g, x + 2, y + 5, on, true);
      SeedMapLayer.icon(g, kind, x + 14, y + 1);
      String name = SeedMapLayer.kindName(kind);
      int textX = x + 34;
      int room = colW - 34 - 2;
      List<Component> tip = null;
      int distW = 0;
      if (hover) {
         SeedSampler.Found near = this.layer.nearestKnown(kind);
         String dist = near != null ? Ui.t("chunkatlas.seed.nearest", Ui.count(this.layer.distance(near))) : Ui.t("chunkatlas.seed.nearest.find");
         distW = this.font.width(dist) + 4;
         int dx = x + colW - distW;
         boolean overDist = Glass.over(mx, my, dx, y, distW, ROW);
         g.text(this.font, dist, dx + 2, y + 5, overDist ? Ui.WHITE : Glass.ACCENT);
         Runnable go = near != null ? () -> this.layer.jumpTo(near) : this.layer.finding() ? null : () -> this.layer.findNearest(kind);
         this.clippedHit(hits, dx, y, distW, ROW, top, bottom, go);
         room -= distW;
         if (this.font.width(name) > room) {
            tip = Glass.tip(name, null);
         }
      }

      g.text(this.font, this.font.plainSubstrByWidth(name, Math.max(10, room)), textX, y + 5, on ? Ui.WHITE : Glass.SOFT);
      this.clippedHit(hits, x, y, colW - distW, ROW, top, bottom, () -> this.layer.kindShown(kind, !on));
      return tip;
   }

   private List<Component> drawBiomes(Gfx g, List<Ui.Hit> hits, List<SeedPanel.Biome> shown, int at, int top, int bottom, int mx, int my) {
      int left = this.x + PAD;
      if (shown.isEmpty()) {
         String note = this.biomes.isEmpty() ? Ui.t(this.layer.shown() ? "chunkatlas.preview.working" : "chunkatlas.seed.panel.off") : Ui.t("chunkatlas.seed.panel.no_match");
         g.text(this.font, this.font.plainSubstrByWidth(note, this.w - 2 * PAD), left, at + 3, Glass.NOTE);
         return null;
      }

      Set<Integer> lit = this.layer.highlightedAll();
      int width = this.w - 2 * PAD;
      for (SeedPanel.Biome b : shown) {
         int ry = at;
         at += BIOME_ROW;
         if (ry + BIOME_ROW <= top || ry >= bottom) {
            continue;
         }

         boolean on = lit.contains(b.number());
         boolean hover = Glass.over(mx, my, left, ry, width, BIOME_ROW) && my >= top && my < bottom;
         if (hover) {
            g.fill(left, ry, left + width, ry + BIOME_ROW, Glass.TINT);
         }

         pixelCheck(g, left + 2, ry + 2, on, true);

         int sx = left + 14;
         g.fill(sx, ry + 1, sx + 10, ry + 11, 0xFF000000);
         g.fill(sx + 1, ry + 2, sx + 9, ry + 10, 0xFF000000 | b.colour());
         int colour = on ? Glass.ACCENT : lit.isEmpty() ? Ui.WHITE : Glass.SOFT;
         g.text(this.font, this.font.plainSubstrByWidth(b.name(), width - 30), sx + 14, ry + 2, colour);
         this.clippedHit(hits, left, ry, width, BIOME_ROW, top, bottom, () -> this.layer.highlight(b.number(), !on));
      }

      return null;
   }

   private void drawBiomeFooter(Gfx g, List<Ui.Hit> hits, int row, int mx, int my) {
      int n = this.layer.highlightedAll().size();
      int right = this.x + this.w - PAD;
      String find = Ui.t("chunkatlas.seed.types.find");
      int fw = this.font.width(find) + 12;
      Glass.button(g, this.font, hits, right - fw, row, fw, BOX_H, find, Glass.ACCENT, mx, my, n > 0 && !this.layer.finding() ? this::findNearestBiome : null);
      String clear = Ui.t("chunkatlas.seed.panel.clear");
      int cw = this.font.width(clear) + 12;
      Glass.button(g, this.font, hits, right - fw - 3 - cw, row, cw, BOX_H, clear, Ui.WHITE, mx, my, n > 0 ? () -> this.layer.highlight(-1) : null);
      String note = n == 0 ? Ui.t("chunkatlas.seed.biomes.ticked.none") : Ui.plural("chunkatlas.seed.biomes.ticked", n);
      int room = right - fw - 3 - cw - 4 - (this.x + PAD);
      g.text(this.font, this.font.plainSubstrByWidth(note, Math.max(10, room)), this.x + PAD, row + 4, Glass.NOTE);
   }

   private void findNearestBiome() {
      Set<Integer> picked = this.layer.highlightedAll();
      if (!picked.isEmpty() && !this.layer.finding()) {
         this.layer.findBiome(picked.iterator().next());
      }
   }

   private void clippedHit(List<Ui.Hit> hits, int x, int y, int w, int h, int top, int bottom, Runnable action) {
      int y0 = Math.max(y, top);
      int y1 = Math.min(y + h, bottom);
      if (action != null && y1 > y0) {
         hits.add(new Ui.Hit(x, y0, x + w, y1, action));
      }
   }

   private static void pixelCheck(Gfx g, int x, int y, boolean on, boolean usable) {
      g.fill(x, y, x + 8, y + 8, usable ? 0xFFA0A0A0 : 0xFF555555);
      g.fill(x + 1, y + 1, x + 7, y + 7, 0xFF1E1E1E);
      if (on) {
         int c = usable ? Ui.WHITE : Ui.DIM;
         g.fill(x + 2, y + 4, x + 3, y + 5, c);
         g.fill(x + 3, y + 5, x + 4, y + 6, c);
         g.fill(x + 4, y + 4, x + 5, y + 5, c);
         g.fill(x + 5, y + 3, x + 6, y + 4, c);
         g.fill(x + 6, y + 2, x + 7, y + 3, c);
      }
   }

   void tourTab(SeedPanel.Tab tab) {
      this.tab = tab;
      this.scroll = 0;
   }

   void tourFeatures(boolean open) {
      this.layer.featuresOpen(open);
   }

   void tourSearch(String text) {
      this.search().setValue(text);
   }

   int tourRows() {
      return this.biomes().size();
   }

   boolean tourClick(int row, boolean find) {
      if (find) {
         this.findNearestBiome();
         return true;
      }

      List<SeedPanel.Biome> shown = this.biomes();
      if (row >= shown.size()) {
         return false;
      }

      SeedPanel.Biome b = shown.get(row);
      this.layer.highlight(b.number(), !this.layer.isHighlighted(b.number()));
      return true;
   }
}
