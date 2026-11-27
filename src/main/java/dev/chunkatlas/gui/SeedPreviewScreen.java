package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.GameColours;
import dev.chunkatlas.Settings;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Input;
import dev.chunkatlas.port.Keys;
import dev.chunkatlas.port.Seeds;
import dev.chunkatlas.port.Tex;
import dev.chunkatlas.seed.SeedCache;
import dev.chunkatlas.seed.SeedSampler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.WorldOptions;

public final class SeedPreviewScreen extends AtlasScreen {
   private static final String K = "chunkatlas.preview.";
   private static final List<String> DIMENSIONS = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
   private static final int M = Glass.MARGIN;

   private static final int PANEL_W = 230;
   private static final int PAD = 6;
   private static final int ROW_STRUCT = 18;
   private static final int ROW_BIOME = 14;
   private static final int BAR_H = 26;

   private static final double MIN_ZOOM = 1.0 / 64.0;
   private static final double MAX_ZOOM = 4.0;

   private static final int DIM = 0xB8000000;

   private final CreateWorldScreen parent;
   private final String startSeed;
   private final Minecraft mc = Minecraft.getInstance();
   private final GameColours colours = GameColours.get();
   private final Map<SeedCache.Job, Tex> textures = new HashMap<>();
   private final Map<Tex, Integer> sides = new HashMap<>();
   private final List<Ui.Hit> hits = new ArrayList<>();

   private final Map<String, Boolean> kindChoice = new HashMap<>();

   private final Set<Integer> biomes = new TreeSet<>();
   private volatile SeedCache cache;
   private volatile boolean building;
   private volatile String failed;
   private long seed;
   private String dimension = DIMENSIONS.get(0);
   private boolean biomesTab;
   private boolean dimensionMenu;
   private double cx;
   private double cz;
   private double zoom = 0.25;
   private double zoomTarget = 0.25;
   private double anchorX = Double.NaN;
   private double anchorY;
   private long lastFrame;
   private List<SeedCache.Job> asked = List.of();
   private EditBox seedBox;
   private boolean dragging;
   private double lastX;
   private double lastY;
   private int listScroll;

   private List<SeedPreviewScreen.Biome> biomeList;
   private SeedSampler biomeListFor;

   private record Biome(int id, String name) {
   }

   public SeedPreviewScreen(CreateWorldScreen parent) {
      super(Component.translatable(K + "title"));
      this.parent = parent;
      this.startSeed = parent.getUiState().getSeed();
      OptionalLong typed = WorldOptions.parseSeed(this.startSeed);
      this.seed = typed.isPresent() ? typed.getAsLong() : WorldOptions.randomSeed();
   }

   private int panelW() {
      SeedCache c = this.cache;
      int widest = 0;
      if (c != null) {
         for (String kind : c.world().kinds()) {
            widest = Math.max(widest, this.font.width(SeedMapLayer.kindName(kind)));
         }
      }

      int need = 2 * (32 + widest + 6) + 2 * PAD;
      return Math.min(Math.max(PANEL_W, Math.min(need, this.width * 3 / 5)), this.width - 2 * M);
   }

   private int panelX() {
      return this.width - M - this.panelW();
   }

   private int barTop() {
      return this.height - M - BAR_H;
   }

   private int randomW() {
      return this.font.width(Ui.t(K + "random")) + 12;
   }

   private int[] seedBoxBox() {
      int x = this.panelX() + PAD;
      int w = this.panelW() - 2 * PAD - Glass.TILE - 2 - this.randomW() - 2;
      return new int[]{x, M + Glass.INSET + 14 + 2, w, 16};
   }

   private int listTop() {
      return M + Glass.INSET + 14 + 20 + 20;
   }

   private int listH() {
      int room = this.barTop() - M - this.listTop() - Glass.INSET;
      return Math.max(40, Math.min(room, this.listNeeded()));
   }

   private int panelH() {
      return this.listOpen ? this.listTop() - M + this.listH() + Glass.INSET : this.listTop() - M - 20 + Glass.INSET;
   }

   private boolean listOpen;

   @Override
   protected void init() {
      int[] box = this.seedBoxBox();
      this.seedBox = new EditBox(this.font, box[0], box[1], box[2], box[3], Component.translatable(K + "seed"));
      this.seedBox.setMaxLength(64);
      this.seedBox.setValue(Long.toString(this.seed));
      this.addRenderableWidget(this.seedBox);
      if (this.cache == null && !this.building) {
         this.rebuild();
      }
   }

   private void rebuild() {
      OptionalLong typed = WorldOptions.parseSeed(this.seedBox == null ? Long.toString(this.seed) : this.seedBox.getValue());
      if (typed.isPresent()) {
         this.seed = typed.getAsLong();
      }

      this.parent.getUiState().setSeed(Long.toString(this.seed));
      SeedCache old = this.cache;
      this.cache = null;
      if (old != null) {
         old.close();
      }

      this.releaseAll();
      this.asked = List.of();
      this.failed = null;
      this.building = true;
      long s = this.seed;
      String dim = this.dimension;
      var ctx = this.parent.getUiState().getSettings();
      Thread t = new Thread(() -> {
         SeedCache made = null;
         String why = null;
         try {
            SeedSampler w = Seeds.preview(ctx, s, dim, this.colours);
            if (w == null) {
               why = Ui.t(K + "unavailable");
            } else {
               w.keepHeightsIn(dev.chunkatlas.ChunkAtlas.home());
               made = new SeedCache(w);
            }
         } catch (RuntimeException e) {
            why = Ui.t(K + "unavailable");
         }

         SeedCache c = made;
         String w2 = why;
         this.mc.execute(() -> {
            this.building = false;
            if (s == this.seed && dim.equals(this.dimension)) {
               this.cache = c;
               this.failed = w2;
            } else if (c != null) {
               c.close();
            }
         });
      }, "Chunk Atlas seed preview");
      t.setDaemon(true);
      t.start();
   }

   private void setDimension(String id) {
      this.dimensionMenu = false;
      if (!id.equals(this.dimension)) {
         this.dimension = id;
         this.biomes.clear();
         this.listScroll = 0;
         this.rebuild();
      }
   }

   private double mapCy() {
      return this.height / 2.0;
   }

   private double sx(double bx) {
      return this.width / 2.0 + (bx - this.cx) * this.zoom;
   }

   private double sy(double bz) {
      return this.mapCy() + (bz - this.cz) * this.zoom;
   }

   private double bx(double screenX) {
      return this.cx + (screenX - this.width / 2.0) / this.zoom;
   }

   private double bz(double screenY) {
      return this.cz + (screenY - this.mapCy()) / this.zoom;
   }

   private void ease() {
      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      if (this.zoom == this.zoomTarget) {
         return;
      }

      double ax = Double.isNaN(this.anchorX) ? this.width / 2.0 : this.anchorX;
      double ay = Double.isNaN(this.anchorX) ? this.mapCy() : this.anchorY;
      double bx = this.bx(ax);
      double bz = this.bz(ay);
      double k = Settings.smoothZoom() ? 1.0 - Math.exp(-dt * 16.0F) : 1.0;
      this.zoom = Math.exp(Math.log(this.zoom) + (Math.log(this.zoomTarget) - Math.log(this.zoom)) * k);
      if (Math.abs(this.zoom / this.zoomTarget - 1.0) < 0.003) {
         this.zoom = this.zoomTarget;
      }

      this.cx = bx - (ax - this.width / 2.0) / this.zoom;
      this.cz = bz - (ay - this.mapCy()) / this.zoom;
   }

   private void zoomBy(double factor, double atX, double atY) {
      this.anchorX = atX;
      this.anchorY = atY;
      this.zoomTarget = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.zoomTarget * factor));
   }

   private boolean shown(String kind) {
      return this.kindChoice.getOrDefault(kind, false);
   }

   private boolean anyShown() {
      return this.kindChoice.containsValue(true);
   }

   private boolean overControls(double x, double y) {
      for (Ui.Hit h : this.hits) {
         if (h.has(x, y)) {
            return true;
         }
      }

      return Glass.over((int)x, (int)y, this.panelX(), M, this.panelW(), this.panelH()) || y >= this.barTop();
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      this.ease();
      this.hits.clear();
      g.fill(0, 0, this.width, this.height, MapScreen.NOT_MADE);
      SeedCache c = this.cache;
      SeedSampler.Found hover = null;
      boolean flat = c != null && c.world().flat();
      int px = this.panelX();
      int ph = this.panelH();
      boolean inMap = !Glass.over(mouseX, mouseY, px, M, this.panelW(), ph) && mouseY < this.barTop() && !this.dimensionMenu;
      if (c != null && !flat) {
         g.scissor(0, 0, this.width, this.height);
         hover = this.drawTerrain(g, c, inMap ? mouseX : -1000, mouseY);
         g.unscissor();

         int ox = (int)this.sx(0.5);
         int oy = (int)this.sy(0.5);
         if (!Glass.over(ox, oy, px - 8, M - 8, this.panelW() + 16, ph + 16) && oy < this.barTop() - 8) {
            MapMarkers.star(g, ox, oy);
            MapMarkers.tag(g, this.font, Ui.t(K + "spawn"), ox + 7, oy, Ui.WHITE);
         }
      }

      String note = this.failed != null ? this.failed : flat ? Ui.t(K + "flat") : null;
      if (note != null) {
         g.centered(this.font, note, (M + px) / 2, this.height / 2 - 4, Ui.GREY);
      }

      List<Component> tip = this.drawBack(g, mouseX, mouseY);
      List<Component> t2 = this.drawPanel(g, c, mouseX, mouseY);
      if (t2 != null) {
         tip = t2;
      }

      super.draw(g, mouseX, mouseY, partial);
      String biome = inMap ? this.biomeAt(c, (int)Math.floor(this.bx(mouseX)), (int)Math.floor(this.bz(mouseY))) : null;
      String where = inMap ? "X " + (int)Math.floor(this.bx(mouseX)) + "  Z " + (int)Math.floor(this.bz(mouseY)) : "";
      t2 = this.drawBar(g, where, biome, mouseX, mouseY);
      if (t2 != null) {
         tip = t2;
      }

      if (this.building || c != null && !c.idle()) {
         int total = Math.max(1, this.asked.size());
         int done = c == null ? 0 : Math.max(0, total - c.waiting());
         String text = this.building ? Ui.t(K + "working") : Ui.t(K + "progress", Ui.count(done), Ui.count(total));
         int backW = this.font.width(Ui.t(K + "back")) + 14;
         int mid = (M + backW + px) / 2;
         Ui.chip(g, this.font, text, mid - Ui.chipWidth(this.font, text) / 2, M + 3, Glass.QUIET);
      }

      if (this.dimensionMenu) {
         this.drawDimensionMenu(g, mouseX, mouseY);
      }

      if (hover != null && inMap) {
         this.drawCard(g, hover, mouseX, mouseY);
      } else if (tip != null && !tip.isEmpty()) {
         g.tooltip(this.font, tip, mouseX, mouseY);
      }
   }

   private List<Component> drawBack(Gfx g, int mouseX, int mouseY) {
      String back = Ui.t(K + "back");
      int w = this.font.width(back) + 14;
      Glass.button(g, this.font, this.hits, M, M, w, 16, back, Ui.WHITE, mouseX, mouseY, this::cancel);
      return null;
   }

   private List<Component> drawPanel(Gfx g, SeedCache c, int mouseX, int mouseY) {
      int px = this.panelX();
      int pw = this.panelW();
      int ph = this.panelH();
      Glass.panel(g, px, M, pw, ph);
      int y = M + Glass.INSET;
      g.text(this.font, Component.literal(Ui.t(K + "title")).withStyle(ChatFormatting.BOLD), px + PAD, y + 3, Ui.WHITE);

      String dim = Ui.dimension(this.dimension) + " ▾";
      int dw = this.font.width(dim) + 8;
      int dx = px + pw - PAD - dw;
      boolean hot = Glass.over(mouseX, mouseY, dx, y, dw, 14);
      if (hot || this.dimensionMenu) {
         g.fill(dx, y, dx + dw, y + 14, Glass.TINT);
      }

      g.text(this.font, dim, dx + 4, y + 3, Glass.ACCENT);
      this.hits.add(new Ui.Hit(dx, y, dx + dw, y + 14, () -> this.dimensionMenu = !this.dimensionMenu));

      String fold = this.listOpen ? "▴" : "▾";
      int fx = dx - 14;
      if (Glass.over(mouseX, mouseY, fx, y, 12, 14)) {
         g.fill(fx, y, fx + 12, y + 14, Glass.TINT);
      }

      g.text(this.font, fold, fx + 3, y + 3, Glass.SOFT);
      this.hits.add(new Ui.Hit(fx, y, fx + 12, y + 14, () -> this.listOpen = !this.listOpen));
      y += 14;

      List<Component> tip = null;
      int[] box = this.seedBoxBox();
      int tx = box[0] + box[2] + 2;
      int ty = box[1] - 2;
      boolean copyHot = Glass.over(mouseX, mouseY, tx, ty, Glass.TILE, Glass.TILE);
      if (copyHot) {
         g.fill(tx, ty, tx + Glass.TILE, ty + Glass.TILE, Glass.TINT);
         g.outline(tx, ty, Glass.TILE, Glass.TILE, 0xC0FFFFFF);
         tip = Glass.tip(Ui.t(K + "copy"), null);
      }

      g.sprite(Glass.icon("copy"), tx + 2, ty + 2, Glass.ICON, Glass.ICON, Ui.WHITE);
      this.hits.add(new Ui.Hit(tx, ty, tx + Glass.TILE, ty + Glass.TILE, () -> this.mc.keyboardHandler.setClipboard(this.seedBox.getValue())));
      int rx = tx + Glass.TILE + 2;
      int rw = this.randomW();
      Glass.button(g, this.font, this.hits, rx, ty, rw, Glass.TILE, Ui.t(K + "random"), Ui.WHITE, mouseX, mouseY, () -> {
         this.seed = WorldOptions.randomSeed();
         this.seedBox.setValue(Long.toString(this.seed));
         this.rebuild();
      });
      y += 20;
      if (!this.listOpen) {
         return tip;
      }

      String[] tabs = {Ui.t(K + "tab.structures"), Ui.t(K + "tab.biomes")};
      int tabX = px + PAD;
      for (int i = 0; i < tabs.length; i++) {
         int w = this.font.width(tabs[i]) + 10;
         boolean on = this.biomesTab == (i == 1);
         if (on) {
            g.fill(tabX, y, tabX + w, y + 16, Glass.HOT);
            g.fill(tabX, y + 14, tabX + w, y + 16, Glass.ACCENT);
         } else if (Glass.over(mouseX, mouseY, tabX, y, w, 16)) {
            g.fill(tabX, y, tabX + w, y + 16, Glass.TINT);
         }

         g.text(this.font, tabs[i], tabX + 5, y + 4, on ? Ui.WHITE : Glass.SOFT);
         boolean biomesTab = i == 1;
         this.hits.add(new Ui.Hit(tabX, y, tabX + w, y + 16, () -> {
            this.biomesTab = biomesTab;
            this.listScroll = 0;
         }));
         tabX += w + 2;
      }

      String none = Ui.t("chunkatlas.seed.types.none");
      int nw = this.font.width(none) + 8;
      int nx = px + pw - PAD - nw;
      this.word(g, nx, y, nw, none, mouseX, mouseY, c == null ? null : () -> {
         if (this.biomesTab) {
            this.biomes.clear();
            this.releaseAll();
         } else {
            for (String kind : c.world().kinds()) {
               this.kindChoice.put(kind, false);
            }
         }
      });
      if (!this.biomesTab) {
         String all = Ui.t("chunkatlas.seed.types.all");
         int aw = this.font.width(all) + 8;
         this.word(g, nx - 2 - aw, y, aw, all, mouseX, mouseY, c == null ? null : () -> {
            for (String kind : c.world().kinds()) {
               this.kindChoice.put(kind, true);
            }
         });
      }

      y += 20;

      int lh = this.listH();
      int needed = this.listNeeded();
      this.listScroll = Math.max(0, Math.min(this.listScroll, needed - lh));
      g.scissor(px + 1, y, px + pw - 1, y + lh);
      boolean over = Glass.over(mouseX, mouseY, px, y, pw, lh);
      int before = this.hits.size();
      List<Component> t2 = this.biomesTab ? this.drawBiomes(g, c, px + PAD, y - this.listScroll, pw - 2 * PAD, over ? mouseX : -1000, mouseY) : this.drawKinds(g, c, px + PAD, y - this.listScroll, pw - 2 * PAD, over ? mouseX : -1000, mouseY);
      g.unscissor();

      for (int i = this.hits.size() - 1; i >= before; i--) {
         Ui.Hit h = this.hits.get(i);
         if (h.y0() < y || h.y1() > y + lh) {
            this.hits.remove(i);
         }
      }

      if (needed > lh) {
         int barH = Math.max(8, lh * lh / needed);
         int barY = y + (lh - barH) * this.listScroll / Math.max(1, needed - lh);
         g.fill(px + pw - 3, barY, px + pw - 1, barY + barH, 0x80FFFFFF);
      }

      return t2 != null ? t2 : tip;
   }

   private void word(Gfx g, int x, int y, int w, String text, int mouseX, int mouseY, Runnable action) {
      if (action != null && Glass.over(mouseX, mouseY, x, y, w, 16)) {
         g.fill(x, y, x + w, y + 16, Glass.TINT);
      }

      g.text(this.font, text, x + 4, y + 4, action == null ? Glass.OFF : Glass.SOFT);
      if (action != null) {
         this.hits.add(new Ui.Hit(x, y, x + w, y + 16, action));
      }
   }

   private int listNeeded() {
      SeedCache c = this.cache;
      if (c == null) {
         return 40;
      }

      if (this.biomesTab) {
         return Math.max(1, this.biomeList(c).size()) * ROW_BIOME + 4;
      }

      int structures = 0;
      int features = 0;
      for (String kind : c.world().kinds()) {
         if (SeedSampler.isFeature(kind)) {
            features++;
         } else {
            structures++;
         }
      }

      return (structures + 1) / 2 * ROW_STRUCT + (features > 0 ? 14 + (features + 1) / 2 * ROW_STRUCT : 0) + 4;
   }

   private List<Component> drawKinds(Gfx g, SeedCache c, int x, int y, int w, int mouseX, int mouseY) {
      if (c == null) {
         return null;
      }

      List<String> structures = new ArrayList<>();
      List<String> features = new ArrayList<>();
      for (String kind : c.world().kinds()) {
         (SeedSampler.isFeature(kind) ? features : structures).add(kind);
      }

      List<Component> tip = this.kindRows(g, structures, x, y, w, mouseX, mouseY);
      y += (structures.size() + 1) / 2 * ROW_STRUCT;
      if (!features.isEmpty()) {
         g.fill(x, y + 2, x + w, y + 3, Glass.RULE);
         g.text(this.font, Ui.t("chunkatlas.seed.types.features"), x, y + 5, Glass.NOTE);
         y += 14;
         List<Component> t2 = this.kindRows(g, features, x, y, w, mouseX, mouseY);
         if (t2 != null) {
            tip = t2;
         }
      }

      return tip;
   }

   private List<Component> kindRows(Gfx g, List<String> kinds, int x, int y, int w, int mouseX, int mouseY) {
      int colW = w / 2;
      int nameW = colW - 12 - 16 - 4;
      List<Component> tip = null;
      for (int i = 0; i < kinds.size(); i++) {
         String kind = kinds.get(i);
         int cx = x + (i % 2) * colW;
         int cy = y + (i / 2) * ROW_STRUCT;
         boolean on = this.shown(kind);
         String name = SeedMapLayer.kindName(kind);
         String fit = this.font.width(name) <= nameW ? name : this.font.plainSubstrByWidth(name, nameW - this.font.width("…")) + "…";
         boolean hot = Glass.over(mouseX, mouseY, cx, cy, colW, ROW_STRUCT);
         if (hot) {
            g.fill(cx - 2, cy, cx + colW - 2, cy + ROW_STRUCT, Glass.TINT);
            if (!fit.equals(name)) {
               tip = List.of(Component.literal(name));
            }
         }

         int by = cy + (ROW_STRUCT - 8) / 2;
         g.fill(cx, by, cx + 8, by + 8, 0xFFA0A0A0);
         g.fill(cx + 1, by + 1, cx + 7, by + 7, 0xFF1E1E1E);
         if (on) {
            g.fill(cx + 2, by + 4, cx + 3, by + 5, Ui.WHITE);
            g.fill(cx + 3, by + 5, cx + 4, by + 6, Ui.WHITE);
            g.fill(cx + 4, by + 4, cx + 5, by + 5, Ui.WHITE);
            g.fill(cx + 5, by + 3, cx + 6, by + 4, Ui.WHITE);
            g.fill(cx + 6, by + 2, cx + 7, by + 3, Ui.WHITE);
         }

         SeedMapLayer.icon(g, kind, cx + 12, cy + (ROW_STRUCT - 16) / 2);
         if (!on) {
            g.fill(cx + 12, cy + (ROW_STRUCT - 16) / 2, cx + 28, cy + (ROW_STRUCT - 16) / 2 + 16, 0x90000000);
         }

         g.text(this.font, fit, cx + 32, cy + (ROW_STRUCT - 8) / 2, on ? Ui.WHITE : Glass.OFF);
         this.hits.add(new Ui.Hit(cx - 2, cy, cx + colW - 2, cy + ROW_STRUCT, () -> this.kindChoice.put(kind, !on)));
      }

      return tip;
   }

   private List<SeedPreviewScreen.Biome> biomeList(SeedCache c) {
      if (this.biomeList == null || this.biomeListFor != c.world()) {
         List<SeedPreviewScreen.Biome> list = new ArrayList<>();
         for (int id : c.world().possibleBiomes()) {
            String bid = c.world().biomeId(id);
            if (bid != null) {
               list.add(new SeedPreviewScreen.Biome(id, SeedMapLayer.biomeName(bid)));
            }
         }

         list.sort(Comparator.comparing(SeedPreviewScreen.Biome::name, String.CASE_INSENSITIVE_ORDER));
         this.biomeList = list;
         this.biomeListFor = c.world();
      }

      return this.biomeList;
   }

   private List<Component> drawBiomes(Gfx g, SeedCache c, int x, int y, int w, int mouseX, int mouseY) {
      if (c == null) {
         return null;
      }

      for (SeedPreviewScreen.Biome b : this.biomeList(c)) {
         boolean on = this.biomes.contains(b.id());
         if (Glass.over(mouseX, mouseY, x, y, w, ROW_BIOME)) {
            g.fill(x - 2, y, x + w - 2, y + ROW_BIOME, Glass.TINT);
         }

         int by = y + 3;
         g.fill(x, by, x + 8, by + 8, 0xFFA0A0A0);
         g.fill(x + 1, by + 1, x + 7, by + 7, 0xFF1E1E1E);
         if (on) {
            g.fill(x + 2, by + 4, x + 3, by + 5, Ui.WHITE);
            g.fill(x + 3, by + 5, x + 4, by + 6, Ui.WHITE);
            g.fill(x + 4, by + 4, x + 5, by + 5, Ui.WHITE);
            g.fill(x + 5, by + 3, x + 6, by + 4, Ui.WHITE);
            g.fill(x + 6, by + 2, x + 7, by + 3, Ui.WHITE);
         }

         g.text(this.font, this.font.plainSubstrByWidth(b.name(), w - 16), x + 12, y + 3, on ? Glass.ACCENT : Ui.WHITE);
         this.hits.add(new Ui.Hit(x - 2, y, x + w - 2, y + ROW_BIOME, () -> {
            if (!this.biomes.remove(b.id())) {
               this.biomes.add(b.id());
            }

            this.releaseAll();
         }));
         y += ROW_BIOME;
      }

      return null;
   }

   private void drawDimensionMenu(Gfx g, int mouseX, int mouseY) {
      List<Ui.Item> items = new ArrayList<>();
      for (String id : DIMENSIONS) {
         items.add(Ui.Item.radio(Ui.dimension(id), id.equals(this.dimension), () -> this.setDimension(id)));
      }

      int w = Glass.menuWidth(this.font, items);
      int x = this.panelX() + this.panelW() - PAD - w;
      Glass.menu(g, this.font, this.hits, items, x, M + Glass.INSET + 15, mouseX, mouseY);
   }

   private List<Component> drawBar(Gfx g, String where, String biome, int mouseX, int mouseY) {
      int y = this.barTop();
      int x0 = M;
      int w = this.width - 2 * M;
      Glass.panel(g, x0, y, w, BAR_H);
      int by = y + Glass.INSET;
      int right = x0 + w - Glass.INSET;

      String spawn = Ui.t(K + "spawn");
      int sw = this.font.width(spawn) + 12;
      right -= sw;
      this.barWord(g, right, by, sw, spawn, mouseX, mouseY, () -> {
         this.cx = 0.5;
         this.cz = 0.5;
      });
      right -= 7;
      g.fill(right + 3, by + 3, right + 4, by + Glass.TILE - 3, Glass.RULE);
      int plusW = this.font.width("+") + 12;
      right -= plusW;
      this.barWord(g, right, by, plusW, "+", mouseX, mouseY, () -> this.zoomBy(Math.pow(2.0, 0.5), (M + this.panelX()) / 2.0, this.mapCy()));
      String zoomText = this.zoom >= 1.0 ? String.format(Locale.ROOT, "%.1fx", this.zoom) : "1:" + Math.round(1.0 / this.zoom);
      int zw = Math.max(this.font.width("1:64"), this.font.width(zoomText)) + 8;
      right -= zw;
      g.text(this.font, zoomText, right + (zw - this.font.width(zoomText)) / 2, by + (Glass.TILE - 8) / 2, Glass.QUIET);
      int minusW = this.font.width("-") + 12;
      right -= minusW;
      this.barWord(g, right, by, minusW, "-", mouseX, mouseY, () -> this.zoomBy(Math.pow(2.0, -0.5), (M + this.panelX()) / 2.0, this.mapCy()));
      right -= 7;
      g.fill(right + 3, by + 3, right + 4, by + Glass.TILE - 3, Glass.RULE);

      String use = Ui.t(K + "done");
      int uw = this.font.width(use) + 24;
      right -= uw;
      Glass.button(g, this.font, this.hits, right, by, uw, Glass.TILE, use, Glass.ACCENT, mouseX, mouseY, this::use);
      String cancel = Ui.t("chunkatlas.confirm.cancel");
      int cw = this.font.width(cancel) + 24;
      right -= cw + 4;
      Glass.button(g, this.font, this.hits, right, by, cw, Glass.TILE, cancel, Ui.WHITE, mouseX, mouseY, this::cancel);

      int room = right - 8 - (x0 + PAD);
      if (!where.isEmpty() && room > 40) {
         String text = biome == null ? where : where + " · " + biome;
         g.text(this.font, this.font.plainSubstrByWidth(text, room), x0 + PAD, by + (Glass.TILE - 8) / 2, Ui.WHITE);
      }

      return null;
   }

   private void barWord(Gfx g, int x, int y, int w, String text, int mouseX, int mouseY, Runnable action) {
      if (Glass.over(mouseX, mouseY, x, y, w, Glass.TILE)) {
         g.fill(x, y, x + w, y + Glass.TILE, Glass.TINT);
         g.outline(x, y, w, Glass.TILE, 0xC0FFFFFF);
      }

      g.text(this.font, text, x + 6, y + (Glass.TILE - 8) / 2, Ui.WHITE);
      this.hits.add(new Ui.Hit(x, y, x + w, y + Glass.TILE, action));
   }

   private void drawCard(Gfx g, SeedSampler.Found f, int mouseX, int mouseY) {
      String name = SeedMapLayer.kindName(f.kind());
      int dist = (int)Math.round(Math.hypot(f.x(), f.z()));
      String line2 = Ui.t(K + "card", f.x(), f.z(), Ui.count(dist));
      int w = Math.max(this.font.width(name), this.font.width(line2)) + 12;
      int x = Math.min(this.width - w - 4, mouseX + 12);
      int y = Math.max(4, mouseY - 26);
      g.fill(x, y, x + w, y + 26, 0xD0000000);
      g.text(this.font, name, x + 6, y + 4, Ui.WHITE);
      g.text(this.font, line2, x + 6, y + 15, Ui.GREY);
   }

   private long biomeCell = Long.MIN_VALUE;
   private SeedCache biomeFrom;
   private String biomeName;

   private String biomeAt(SeedCache c, int bx, int bz) {
      if (c == null) {
         return null;
      }

      long cell = (long)(bx >> 2) << 32 | (bz >> 2) & 0xFFFFFFFFL;
      if (cell != this.biomeCell || c != this.biomeFrom) {
         this.biomeCell = cell;
         this.biomeFrom = c;
         String id = c.world().biomeId(c.world().biomeAt(bx, bz));
         this.biomeName = id == null ? null : SeedMapLayer.biomeName(id);
      }

      return this.biomeName;
   }

   private final List<Tex> retired = new ArrayList<>();

   private SeedSampler.Found drawTerrain(Gfx g, SeedCache c, int mouseX, int mouseY) {
      for (Tex id : this.retired) {
         this.sides.remove(id);
         id.close();
      }

      this.retired.clear();
      double regionPx = 512.0 * this.zoom;

      int n = regionPx < 40 ? 32 : regionPx < 80 ? 64 : 128;
      int rx0 = (int)Math.floor(this.bx(0) / 512.0) - 1;
      int rx1 = (int)Math.floor(this.bx(this.width) / 512.0) + 1;
      int rz0 = (int)Math.floor(this.bz(0) / 512.0) - 1;
      int rz1 = (int)Math.floor(this.bz(this.height) / 512.0) + 1;
      List<SeedCache.Job> want = new ArrayList<>();
      boolean icons = this.zoom >= 1.0 / 8.0 && this.anyShown();
      for (int rz = rz0; rz <= rz1; rz++) {
         for (int rx = rx0; rx <= rx1; rx++) {
            want.add(new SeedCache.Job(SeedCache.Type.BASE, rx, rz, Math.max(4, n / 8)));
            want.add(new SeedCache.Job(SeedCache.Type.BASE, rx, rz, n));
            if (icons) {
               want.add(new SeedCache.Job(SeedCache.Type.QUICK, rx, rz, 0));
            }
         }
      }

      double fx = this.cx / 512.0;
      double fz = this.cz / 512.0;
      int coarse = Math.max(4, n / 8);

      want.sort(Comparator.<SeedCache.Job>comparingInt(j -> j.type() == SeedCache.Type.BASE && j.n() > coarse ? 1 : 0)
         .thenComparingDouble(j -> (j.rx() + 0.5 - fx) * (j.rx() + 0.5 - fx) + (j.rz() + 0.5 - fz) * (j.rz() + 0.5 - fz)));
      if (!want.equals(this.asked)) {
         c.want(want);
         this.asked = want;
      }

      for (int rz = rz0; rz <= rz1; rz++) {
         for (int rx = rx0; rx <= rx1; rx++) {
            Tex tex = this.texture(c, rx, rz, n);
            if (tex == null) {
               tex = this.texture(c, rx, rz, coarse);
            }

            if (tex != null) {
               int x0 = (int)Math.floor(this.sx(rx * 512.0));
               int z0 = (int)Math.floor(this.sy(rz * 512.0));
               int x1 = (int)Math.floor(this.sx(rx * 512.0 + 512.0));
               int z1 = (int)Math.floor(this.sy(rz * 512.0 + 512.0));
               int side = this.sides.getOrDefault(tex, 64);
               g.blit(tex, x0, z0, 0.0F, 0.0F, x1 - x0, z1 - z0, side, side, side, side);
            }
         }
      }

      SeedSampler.Found hover = null;
      if (icons) {
         int px = this.panelX();
         int ph = this.panelH();
         for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
               List<SeedSampler.Found> found = c.structures(rx, rz);
               if (found == null) {
                  continue;
               }

               for (SeedSampler.Found f : found) {
                  if (!this.shown(f.kind())) {
                     continue;
                  }

                  int x = (int)this.sx(f.x() + 0.5);
                  int y = (int)this.sy(f.z() + 0.5);

                  if (x < -8 || x > this.width + 8 || y < -8 || y > this.barTop() + 8 || Glass.over(x, y, px - 8, M - 8, this.panelW() + 16, ph + 16)) {
                     continue;
                  }

                  SeedMapLayer.icon(g, f.kind(), x - 8, y - 8);
                  if (Math.abs(mouseX - x) <= 8 && Math.abs(mouseY - y) <= 8) {
                     hover = f;
                  }
               }
            }
         }
      }

      if (hover != null) {
         int x = (int)this.sx(hover.x() + 0.5);
         int y = (int)this.sy(hover.z() + 0.5);
         g.fill(x - 10, y - 10, x + 10, y - 9, 0xFFFFFFFF);
         g.fill(x - 10, y + 9, x + 10, y + 10, 0xFFFFFFFF);
         g.fill(x - 10, y - 10, x - 9, y + 10, 0xFFFFFFFF);
         g.fill(x + 9, y - 10, x + 10, y + 10, 0xFFFFFFFF);
      }

      if (this.textures.size() > 600) {
         this.retired.addAll(this.textures.values());
         this.textures.clear();
      }

      return hover;
   }

   private Tex texture(SeedCache c, int rx, int rz, int n) {
      SeedCache.Job key = new SeedCache.Job(SeedCache.Type.BIOMES, rx, rz, n);
      Tex have = this.textures.get(key);
      if (have != null) {
         return have;
      }

      SeedSampler.Terrain t = c.terrain(rx, rz, n);
      if (t != null && t.slim()) {
         c.again(key);
         c.again(new SeedCache.Job(SeedCache.Type.BASE, rx, rz, n));
         this.asked = List.of();
         return null;
      }

      if (t == null) {
         return null;
      }

      int[] argb = t.pixels(dev.chunkatlas.Settings.lookBits(), this.colours);
      Tex image = Tex.create("Chunk Atlas seed preview", n, n);
      boolean dim = !this.biomes.isEmpty();
      for (int i = 0; i < Math.min(argb.length, n * n); i++) {
         int colour = argb[i];
         if (dim && !this.biomes.contains(t.biomeAt((i % n) * 512 / n, (i / n) * 512 / n))) {
            colour = dim(colour);
         }

         image.set(i % n, i / n, colour);
      }

      image.upload();
      Tex id = image;
      this.textures.put(key, id);
      this.sides.put(id, n);
      return id;
   }

   private static int dim(int argb) {
      int a = DIM >>> 24;
      int r = (argb >> 16 & 0xFF) * (255 - a) / 255;
      int gr = (argb >> 8 & 0xFF) * (255 - a) / 255;
      int b = (argb & 0xFF) * (255 - a) / 255;
      return argb & 0xFF000000 | r << 16 | gr << 8 | b;
   }

   private void releaseAll() {
      for (Tex id : this.textures.values()) {
         id.close();
      }

      this.textures.clear();
      this.sides.clear();
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      if (super.onClick(mouseX, mouseY, mouseButton, doubleClick)) {
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

      if (this.dimensionMenu) {
         this.dimensionMenu = false;
         return true;
      }

      if (!this.overControls(mouseX, mouseY)) {
         this.dragging = true;
         this.lastX = mouseX;
         this.lastY = mouseY;
         return true;
      }

      return false;
   }

   @Override
   protected boolean onDrag(double mouseX, double mouseY, int mouseButton, double dx, double dy) {
      if (this.dragging) {
         this.cx -= (mouseX - this.lastX) / this.zoom;
         this.cz -= (mouseY - this.lastY) / this.zoom;
         this.lastX = mouseX;
         this.lastY = mouseY;
         this.anchorX = Double.NaN;
         return true;
      }

      return super.onDrag(mouseX, mouseY, mouseButton, dx, dy);
   }

   @Override
   protected boolean onRelease(double mouseX, double mouseY, int mouseButton) {
      this.dragging = false;
      return super.onRelease(mouseX, mouseY, mouseButton);
   }

   @Override
   protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (Glass.over((int)mouseX, (int)mouseY, this.panelX(), M, this.panelW(), this.panelH())) {
         int max = Math.max(0, this.listNeeded() - this.listH());
         this.listScroll = Math.max(0, Math.min(max, this.listScroll - (int)Math.round(scrollY * 16.0)));
         return true;
      }

      if (mouseY >= this.barTop()) {
         return true;
      }

      this.zoomBy(Math.pow(2.0, Math.max(-4.0, Math.min(4.0, scrollY)) * 0.5), mouseX, mouseY);
      return true;
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      if (Keys.confirm(keyCode) && this.getFocused() == this.seedBox) {
         this.rebuild();
         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
   }

   public boolean tourIdle() {
      return this.cache != null && this.cache.idle() && !this.building && this.zoom == this.zoomTarget;
   }

   private void close(boolean keepSeed) {
      if (keepSeed) {
         OptionalLong typed = WorldOptions.parseSeed(this.seedBox.getValue());
         this.parent.getUiState().setSeed(typed.isPresent() ? Long.toString(typed.getAsLong()) : Long.toString(this.seed));
      } else {
         this.parent.getUiState().setSeed(this.startSeed);
      }

      SeedCache old = this.cache;
      this.cache = null;
      if (old != null) {
         old.close();
      }

      this.releaseAll();
      Game.open(this.mc, this.parent);
   }

   private void use() {
      this.close(true);
   }

   private void cancel() {
      this.close(false);
   }

   @Override
   public void onClose() {
      this.cancel();
   }
}
