package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.InGame;
import dev.chunkatlas.ServerLink;
import dev.chunkatlas.ServerSeed;
import dev.chunkatlas.Settings;
import dev.chunkatlas.engine.ChunkEdits;
import dev.chunkatlas.engine.SeedMapSettings;
import dev.chunkatlas.minimap.Colours;
import dev.chunkatlas.minimap.Config;
import dev.chunkatlas.minimap.Minimap;
import dev.chunkatlas.minimap.gui.PositionScreen;
import dev.chunkatlas.minimap.gui.WaypointsScreen;
import dev.chunkatlas.minimap.hud.MinimapHud;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Input;
import dev.chunkatlas.port.Keys;
import dev.chunkatlas.port.Texts;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends AtlasScreen {
   public static final int GENERAL = 0;
   public static final int MAP = 1;
   public static final int MINIMAP = 2;
   public static final int SEED = 3;
   public static final int EDITOR = 4;
   public static final int BACKUPS = 5;
   public static final int KEYS = 6;
   private static final String[] TABS = {"general", "map", "minimap", "seed", "editor", "backups", "keys"};
   private static final int[] CACHE_MB = {128, 256, 384, 512, 768, 1024, 2048, Settings.NO_LIMIT};
   private static final int[] WORKERS = {0, 1, 2, 3, 4, 6, 8, 12, 16};
   private static final int[] ROOFS = {1, 3, 5, 7, 9};
   private static final int[] OPACITIES = {20, 40, 60, 80, 100};
   private static final int[] OLDER_DAYS = {7, 30, 90};
   private static final String[] FADES = {"off", "1s", "5s", "15s", "30s", "1m", "5m", "never"};
   private static final int PANEL_W = 400;
   private static final int PANEL_H = 262;
   private static final int CARD_W = 136;
   private static final int CARD_GAP = 8;
   private static final int TABS_W = 78;
   private static final int HEADER_H = 26;
   private static final int FOOTER_H = 30;
   private static final int ROW = 18;
   private static final int HEAD = 18;
   private static final int SLIDER_W = 90;
   private static final int SEARCH_W = 108;
   private static final int UNLIT = 0xFF4E4E4E;
   private static final long DAY_MS = 86400000L;

   private final Minecraft mc = Minecraft.getInstance();
   private final Screen parent;

   private final MapScreen map;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private final List<Ui.Hit> menuHits = new ArrayList<>();
   private final List<Ui.Hit> confirmHits = new ArrayList<>();
   private final List<SettingsScreen.Slider> sliders = new ArrayList<>();

   private final int[] scroll = new int[TABS.length + 1];
   private final int[] contentH = new int[TABS.length + 1];
   private int tab;

   private Object listening;
   private SettingsScreen.Slider dragging;
   private boolean scrollDrag;
   private List<Ui.Item> menu;
   private int menuX;
   private int menuY;
   private EditBox seedBox;

   private EditBox borderBox;
   private EditBox searchBox;

   private SettingsScreen.Confirm confirm;

   private int older = 1;

   private List<SettingsScreen.Found> index;
   private List<SettingsScreen.Found> collect;
   private String query = "";
   private String[] terms = new String[0];
   private final int[] counts = new int[TABS.length];
   private int matches;

   private final int[] resultAt = new int[TABS.length];

   private int running;

   private Gfx g;
   private int mouseX;
   private int mouseY;
   private float partial;
   private boolean covered;
   private String tip;
   private int y;
   private boolean first;

   private int px;
   private int py;
   private int pw;
   private int ph;
   private int cx0;
   private int cx1;
   private int cy0;
   private int cy1;

   public SettingsScreen(Screen parent) {
      this(parent, parent instanceof MapScreen ? MAP : GENERAL);
   }

   public SettingsScreen(Screen parent, int tab) {
      super(Component.translatable("chunkatlas.settings.title"));
      this.parent = parent;
      this.map = parent instanceof MapScreen m ? m : null;
      this.tab = Math.clamp(tab, 0, TABS.length - 1);
   }

   @Override
   protected void init() {
      Minimap.HUD.hideForScreen(true);
      Long seed = this.onServer() ? ServerSeed.get() : null;
      String typed = this.seedBox == null ? (seed == null ? "" : Long.toString(seed)) : this.seedBox.getValue();
      this.seedBox = new EditBox(this.font, 0, 0, 90, 16, Component.translatable("chunkatlas.settings.server_seed"));
      this.seedBox.setMaxLength(24);
      this.seedBox.setValue(typed);
      this.seedBox.setResponder(s -> this.seedTyped = true);
      this.seedBox.visible = false;
      this.addWidget(this.seedBox);
      String message = this.borderBox == null ? Settings.borderMessage() : this.borderBox.getValue();
      this.borderBox = new EditBox(this.font, 0, 0, 90, 16, Component.translatable("chunkatlas.settings.border_message"));
      this.borderBox.setMaxLength(80);
      this.borderBox.setValue(message == null ? "" : message);
      this.borderBox.setHint(Component.literal(Ui.t("chunkatlas.border.message.default")));
      this.borderBox.setResponder(Settings::borderMessage);
      this.borderBox.visible = false;
      this.addWidget(this.borderBox);
      String search = this.searchBox == null ? "" : this.searchBox.getValue();
      this.searchBox = new EditBox(this.font, 0, 0, SEARCH_W, 16, Component.translatable("chunkatlas.settings.search"));
      this.searchBox.setMaxLength(40);
      this.searchBox.setHint(Texts.searchHint(Component.translatable("chunkatlas.settings.search")));
      this.searchBox.setValue(search);
      this.searchBox.setResponder(s -> this.scroll[TABS.length] = 0);
      this.addWidget(this.searchBox);
   }

   private boolean onServer() {
      return this.mc.level != null && this.mc.getSingleplayerServer() == null;
   }

   private boolean seedTyped;

   void tourTab(int i) {
      this.show(i);
   }

   void tourScrollToEnd() {
      this.scroll[this.slot()] = Integer.MAX_VALUE / 2;
   }

   void tourSearch(String text) {
      this.searchBox.setValue(text);
   }

   void tourConfirm(boolean olderOnes) {
      this.show(BACKUPS);
      List<ChunkEdits.Backup> all = DiskFacts.backups();
      if (all != null && !all.isEmpty()) {
         if (olderOnes) {
            this.askOlder(all, OLDER_DAYS[this.older]);
         } else {
            this.askOne(all.get(0));
         }
      }
   }

   void tourListen(MapKey key) {
      this.show(KEYS);
      this.listening = key;
   }

   private void show(int i) {
      this.tab = Math.clamp(i, 0, TABS.length - 1);
      this.listening = null;
      this.menu = null;
      this.confirm = null;
      this.setFocused(null);
   }

   @Override
   public void onClose() {
      Game.open(this.mc, this.parent);
   }

   @Override
   public void removed() {
      Minimap.HUD.hideForScreen(false);
      Config.get().save();
      if (this.seedTyped && this.onServer()) {
         this.seedTyped = false;
         ServerSeed.set(SeedMapSettings.parseSeed(this.seedBox.getValue()));
         if (this.map != null) {
            this.map.seedLayer().seedTyped();
         }
      }
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }

   @Override
   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      if (this.map == null) {
         super.drawBackground(g, mouseX, mouseY, partial);
      }
   }

   private void changed() {
      if (this.map != null) {
         this.map.settingsChanged();
      }
   }

   private void saved(Runnable change) {
      change.run();
      Config.get().save();
      this.changed();
   }

   private void buildIndex() {
      this.index = new ArrayList<>();
      this.collect = this.index;
      int was = this.running;
      try {
         for (int t = 0; t < TABS.length; t++) {
            this.running = t;
            this.runTab(t);
         }
      } finally {
         this.running = was;
         this.collect = null;
      }
   }

   private void readQuery() {
      String q = this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
      if (q.equals(this.query) && this.index != null) {
         return;
      }

      this.query = q;
      this.terms = q.isEmpty() ? new String[0] : q.split("\\s+");
      if (this.index == null) {
         this.buildIndex();
      }

      Arrays.fill(this.counts, 0);
      this.matches = 0;
      if (!q.isEmpty()) {
         for (SettingsScreen.Found f : this.index) {
            if (this.matches(f.label())) {
               this.counts[f.tab()]++;
               this.matches++;
            }
         }
      }
   }

   private boolean searching() {
      return !this.query.isEmpty();
   }

   private boolean indexing() {
      return this.collect != null || this.searching();
   }

   private boolean matches(String label) {
      String lower = label.toLowerCase(Locale.ROOT);
      for (String t : this.terms) {
         if (!lower.contains(t)) {
            return false;
         }
      }

      return true;
   }

   private boolean[] hot(String label) {
      boolean[] hot = new boolean[label.length()];
      String lower = label.toLowerCase(Locale.ROOT);
      if (lower.length() != label.length()) {
         return hot;
      }

      for (String t : this.terms) {
         for (int at = lower.indexOf(t); at >= 0 && !t.isEmpty(); at = lower.indexOf(t, at + 1)) {
            for (int i = at; i < at + t.length(); i++) {
               hot[i] = true;
            }
         }
      }

      return hot;
   }

   private boolean gate(String label) {
      if (this.collect != null) {
         this.collect.add(new SettingsScreen.Found(this.running, label));
         return false;
      }

      if (this.searching()) {
         if (!this.matches(label)) {
            return false;
         }

         if (this.resultAt[this.running] < 0) {
            this.resultAt[this.running] = this.y + this.scroll[this.slot()] - this.cy0;
         }
      }

      return true;
   }

   private int slot() {
      return this.searching() ? TABS.length : this.tab;
   }

   private void runTab(int t) {
      switch (t) {
         case GENERAL -> this.generalTab();
         case MAP -> this.mapTab();
         case MINIMAP -> this.minimapTab();
         case SEED -> this.seedTab();
         case EDITOR -> this.editorTab();
         case BACKUPS -> this.backupsTab();
         default -> this.keysTab();
      }
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      if (this.map != null) {
         this.map.drawUnder(g, partial);
      }

      this.g = g;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.partial = partial;
      this.covered = this.menu != null || this.confirm != null;
      this.tip = null;
      this.hits.clear();
      this.sliders.clear();
      this.seedBox.visible = false;
      this.borderBox.visible = false;
      this.readQuery();
      boolean card = this.tab == MINIMAP && !this.searching() && this.width >= 300;
      int group = Math.min(this.width - 2 * Glass.MARGIN - 8, PANEL_W + (card ? CARD_GAP + CARD_W : 0));
      this.pw = card ? group - CARD_GAP - CARD_W : group;
      this.ph = Math.min(this.height - 2 * Glass.MARGIN - 8, PANEL_H);
      this.px = (this.width - group) / 2;
      this.py = (this.height - this.ph) / 2;
      this.cx0 = this.px + TABS_W + 10;
      this.cx1 = this.px + this.pw - 14;
      this.cy0 = this.py + HEADER_H;
      this.cy1 = this.py + this.ph - FOOTER_H;
      Glass.panel(g, this.px, this.py, this.pw, this.ph);
      g.fill(this.px, this.py, this.px + this.pw, this.py + this.ph, 0x66000000);
      g.text(this.font, Ui.t("chunkatlas.settings.open"), this.px + 8, this.py + 9, Ui.WHITE);
      g.fill(this.px + 1, this.cy0 - 1, this.px + this.pw - 1, this.cy0, Glass.RULE);
      g.fill(this.px + 1, this.cy1, this.px + this.pw - 1, this.cy1 + 1, Glass.RULE);
      this.drawTabs();
      this.drawContent();
      this.drawSearch();
      this.drawFooter();
      if (card) {
         this.drawPreview(this.px + this.pw + CARD_GAP, this.py, CARD_W, this.ph);
      }

      if (this.menu != null) {
         this.menuHits.clear();
         int mw = Glass.menuWidth(this.font, this.menu);
         int mh = Glass.menuHeight(this.menu);
         int mx = Math.max(0, Math.min(this.width - mw, this.menuX));
         int my = this.menuY + mh > this.height ? Math.max(0, this.menuY - mh - 18) : this.menuY;
         Glass.menu(g, this.font, this.menuHits, this.menu, mx, my, mouseX, mouseY);
      } else if (this.confirm != null) {
         this.drawConfirm();
      } else if (this.tip != null) {
         g.tooltip(this.font, Component.literal(this.tip), mouseX, mouseY);
      }

      super.draw(g, mouseX, mouseY, partial);
      this.g = null;
   }

   private void drawSearch() {
      int bx = this.px + this.pw - 8 - 16 - 2 - SEARCH_W;
      this.searchBox.setX(bx);
      this.searchBox.setY(this.py + 5);
      this.searchBox.setWidth(SEARCH_W);
      this.searchBox.visible = true;
      this.g.widget(this.searchBox, this.covered ? -100 : this.mouseX, this.mouseY, this.partial);
      if (this.searchBox.getValue().isEmpty()) {
         return;
      }

      int cx = bx + SEARCH_W + 2;
      boolean over = !this.covered && Glass.over(this.mouseX, this.mouseY, cx, this.py + 5, 16, 16);
      this.g.sprite(Glass.icon("close"), cx, this.py + 5, 16, 16, over ? Ui.WHITE : 0xB0FFFFFF);
      this.hits.add(new Ui.Hit(cx, this.py + 5, cx + 16, this.py + 21, this::clearSearch));
   }

   private void clearSearch() {
      this.searchBox.setValue("");
      this.setFocused(null);
   }

   private void drawTabs() {
      int x = this.px + 4;
      int w = TABS_W - 6;
      int ty = this.cy0 + 4;

      int th = Math.clamp((this.cy1 - this.cy0 - 6) / TABS.length, 10, 18);
      boolean search = this.searching();
      for (int i = 0; i < TABS.length; i++) {
         boolean on = !search && i == this.tab;
         boolean none = search && this.counts[i] == 0;
         boolean hover = !this.covered && !none && Glass.over(this.mouseX, this.mouseY, x, ty, w, th);
         if (on) {
            this.g.fill(x, ty, x + w, ty + th, Glass.HOT);
            this.g.fill(x, ty, x + 2, ty + th, Glass.ACCENT);
         } else if (hover) {
            this.g.fill(x, ty, x + w, ty + th, Glass.TINT);
         }

         this.g.text(this.font, Ui.t("chunkatlas.settings.tab." + TABS[i]), x + 8, ty + (th - 8) / 2, on ? Ui.WHITE : none ? Glass.OFF : Glass.SOFT);
         if (search && !none) {
            String n = Integer.toString(this.counts[i]);
            this.g.text(this.font, n, x + w - 6 - this.font.width(n), ty + (th - 8) / 2, Glass.ACCENT);
         }

         if (!on && !none) {
            int n = i;
            this.hits.add(new Ui.Hit(x, ty, x + w, ty + th, () -> this.pickTab(n)));
         }

         ty += th;
      }

      this.g.fill(this.px + TABS_W, this.cy0 + 3, this.px + TABS_W + 1, this.cy1 - 3, Glass.RULE);
   }

   private void pickTab(int i) {
      if (this.searching()) {
         if (this.resultAt[i] >= 0) {
            this.scroll[TABS.length] = Math.max(0, this.resultAt[i] - 4);
         }
      } else {
         this.show(i);
      }
   }

   private void drawContent() {
      int viewH = this.cy1 - this.cy0;
      int slot = this.slot();
      this.scroll[slot] = Math.max(0, Math.min(this.scroll[slot], this.maxScroll()));
      this.y = this.cy0 + 4 - this.scroll[slot];
      this.first = true;
      this.g.scissor(this.px + TABS_W + 1, this.cy0, this.px + this.pw - 1, this.cy1);
      if (this.searching()) {
         Arrays.fill(this.resultAt, -1);
         for (int t = 0; t < TABS.length; t++) {
            this.running = t;
            this.runTab(t);
         }

         if (this.matches == 0) {
            String none = Ui.t("chunkatlas.settings.found.none");
            this.g.text(this.font, none, this.cx0, this.cy0 + 10, Glass.NOTE);
            this.y += 24;
         }
      } else {
         this.running = this.tab;
         this.runTab(this.tab);
      }

      this.g.unscissor();
      this.contentH[slot] = this.y + this.scroll[slot] - this.cy0 + 4;
      int max = this.maxScroll();
      if (max > 0) {
         int sx = this.px + this.pw - 7;
         int track = viewH - 4;
         int knob = Math.max(12, track * viewH / Math.max(1, this.contentH[slot]));
         int at = this.cy0 + 2 + (track - knob) * this.scroll[slot] / max;
         boolean hot = this.scrollDrag || !this.covered && this.overScrollBar(this.mouseX, this.mouseY);
         this.g.fill(sx, this.cy0 + 2, sx + 2, this.cy1 - 2, 0x20FFFFFF);
         this.g.fill(sx, at, sx + 2, at + knob, hot ? 0xC0FFFFFF : 0x80FFFFFF);
      }
   }

   private boolean overScrollBar(double x, double y) {
      return this.maxScroll() > 0 && x >= this.px + this.pw - 10 && x < this.px + this.pw && y >= this.cy0 && y < this.cy1;
   }

   private int maxScroll() {
      return Math.max(0, this.contentH[this.slot()] - (this.cy1 - this.cy0));
   }

   private void drawFooter() {
      int by = this.cy1 + 5;
      String done = Ui.t("gui.done");
      int dw = Math.max(70, this.font.width(done) + 24);
      int mx = this.covered ? -100 : this.mouseX;
      Glass.button(this.g, this.font, this.hits, this.px + this.pw - 8 - dw, by, dw, 20, done, Glass.ACCENT, mx, this.mouseY, this::onClose);
      if (this.searching()) {
         this.g.text(this.font, Ui.t("chunkatlas.settings.found", Ui.count(this.matches)), this.px + 10, by + 6, Glass.NOTE);
         return;
      }

      if (this.tab == BACKUPS) {
         return;
      }

      String reset = Ui.t("chunkatlas.settings.reset_tab");
      int rw = this.font.width(reset) + 16;
      Glass.button(this.g, this.font, this.hits, this.px + 8, by, rw, 20, reset, Ui.WHITE, mx, this.mouseY, this::resetTab);
      if (this.listening != null) {
         String hint = Ui.t("chunkatlas.settings.keys.hint");
         int room = this.pw - 16 - rw - dw - 16;
         this.g.text(this.font, this.font.plainSubstrByWidth(hint, Math.max(0, room)), this.px + 8 + rw + 8 + (room - Math.min(room, this.font.width(hint))) / 2, by + 6, Glass.NOTE);
      }
   }

   private void drawPreview(int x, int y, int w, int maxH) {
      Config c = Config.get();
      MinimapHud.Layout size = Minimap.HUD.layout(this.width, this.height, c);
      int infoH = size.infoHeight();
      int box = Math.max(32, Math.min(size.box(), Math.min(w - 16, maxH - 20 - infoH - 10)));
      int h = 20 + box + infoH + 10;
      Glass.panel(this.g, x, y, w, h);
      this.g.fill(x, y, x + w, y + h, 0x66000000);
      this.g.text(this.font, Ui.t("chunkatlas.settings.preview"), x + 8, y + 7, Glass.NOTE);
      MinimapHud.Layout at = new MinimapHud.Layout(x + (w - box) / 2, y + 20, box, infoH);
      if (this.mc.player != null && this.mc.level != null) {
         Minimap.HUD.draw(this.g, this.mc, c, at, this.partial);
      } else {
         this.g.fill(at.x(), at.y(), at.x() + box, at.y() + box, MinimapHud.PAPER);
      }

      if (!c.enabled) {
         this.g.fill(at.x(), at.y(), at.x() + box, at.y() + box + infoH, 0xAA000000);
      }
   }

   private record Confirm(String title, String detail, String yes, Runnable action) {
   }

   private void askOne(ChunkEdits.Backup b) {
      this.confirm = new SettingsScreen.Confirm(
         Ui.t("chunkatlas.settings.backups.confirm"),
         Ui.t("chunkatlas.settings.backups.confirm.detail", this.when(b.createdMillis()), this.what(b), Ui.size(b.bytes())),
         Ui.t("chunkatlas.settings.backups.delete"),
         () -> DiskFacts.deleteBackups(List.of(b), null)
      );
   }

   private void askOlder(List<ChunkEdits.Backup> all, int days) {
      List<ChunkEdits.Backup> old = this.olderThan(all, days);
      if (old.isEmpty()) {
         return;
      }

      long bytes = 0L;
      for (ChunkEdits.Backup b : old) {
         bytes += b.bytes();
      }

      this.confirm = new SettingsScreen.Confirm(
         Ui.plural("chunkatlas.settings.backups.confirm_older", old.size(), Ui.plural("chunkatlas.settings.backups.days", days)),
         Ui.t("chunkatlas.settings.backups.confirm_older.detail", Ui.size(bytes)),
         Ui.t("chunkatlas.settings.backups.delete"),
         () -> DiskFacts.deleteBackups(old, null)
      );
   }

   private void askClear(boolean thisWorld, long bytes) {
      Path world = this.map == null ? null : this.map.worldFolder();
      Path open = world;
      Runnable empty = this.map == null ? null : this.map::cacheCleared;
      this.confirm = new SettingsScreen.Confirm(
         Ui.t(thisWorld ? "chunkatlas.settings.cache_clear.world" : "chunkatlas.settings.cache_clear.all"),
         Ui.t("chunkatlas.settings.cache_clear.detail", Ui.size(Math.max(0L, bytes))),
         Ui.t("chunkatlas.settings.cache_clear"),
         () -> DiskFacts.clearCache(thisWorld ? world : null, open, empty, null)
      );
   }

   private void drawConfirm() {
      Gfx g = this.g;
      g.fill(0, 0, this.width, this.height, 0x55000000);
      SettingsScreen.Confirm c = this.confirm;
      String cancel = Ui.t("gui.cancel");
      int bw = Math.max(this.font.width(cancel), this.font.width(c.yes())) + 20;
      int w = Math.min(this.width - 16, Math.max(180, Math.max(this.font.width(c.title()), Math.max(this.font.width(c.detail()), bw * 2 + 8)) + 28));
      int h = 14 + 12 + 6 + 10 + 12 + 20 + 12;
      int x = (this.width - w) / 2;
      int y = (this.height - h) / 2;
      Glass.panel(g, x, y, w, h);
      g.fill(x, y, x + w, y + h, 0xA0000000);
      g.text(this.font, this.font.plainSubstrByWidth(c.title(), w - 16), x + 8, y + 10, Ui.WHITE);
      g.text(this.font, this.font.plainSubstrByWidth(c.detail(), w - 16), x + 8, y + 10 + 12 + 6, Glass.LABEL);
      this.confirmHits.clear();
      int by = y + h - 12 - 20;
      Glass.button(g, this.font, this.confirmHits, x + w - 8 - bw, by, bw, 20, c.yes(), Glass.DANGER, this.mouseX, this.mouseY, () -> {
         this.confirm = null;
         c.action().run();
      });
      Glass.button(g, this.font, this.confirmHits, x + w - 8 - bw * 2 - 6, by, bw, 20, cancel, Ui.WHITE, this.mouseX, this.mouseY, () -> this.confirm = null);
   }

   private boolean visible(int h) {
      return this.y + h > this.cy0 && this.y < this.cy1;
   }

   private boolean hover(int h) {
      return !this.covered
         && this.mouseX >= this.cx0 - 4
         && this.mouseX < this.cx1 + 6
         && this.mouseY >= Math.max(this.y, this.cy0)
         && this.mouseY < Math.min(this.y + h, this.cy1);
   }

   private void add(Ui.Hit hit) {
      int y0 = Math.max(hit.y0(), this.cy0);
      int y1 = Math.min(hit.y1(), this.cy1);
      if (y1 > y0) {
         this.hits.add(new Ui.Hit(hit.x0(), y0, hit.x1(), y1, hit.action()));
      }
   }

   private void add(List<Ui.Hit> made) {
      for (Ui.Hit h : made) {
         this.add(h);
      }
   }

   private void tip(String key, boolean hover) {
      if (hover) {
         String k = key + ".tip";
         String t = I18n.get(k);
         if (!t.equals(k)) {
            this.tip = t;
         }
      }
   }

   private void name(String key, int h, boolean usable, int room) {
      this.label(Ui.t(key), h, usable, room);
   }

   private void label(String text, int h, boolean usable, int room) {
      int ty = this.y + (h - 8) / 2;
      int colour = usable ? Ui.WHITE : Glass.OFF;
      String shown = this.font.plainSubstrByWidth(text, Math.max(12, room));
      if (!this.searching()) {
         this.g.text(this.font, shown, this.cx0, ty, colour);
         return;
      }

      boolean[] hot = this.hot(text);
      int x = this.cx0;
      int i = 0;
      while (i < shown.length()) {
         boolean lit = i < hot.length && hot[i];
         int j = i;
         while (j < shown.length() && j < hot.length && hot[j] == lit) {
            j++;
         }

         String part = shown.substring(i, Math.max(j, i + 1));
         this.g.text(this.font, part, x, ty, lit ? Glass.ACCENT : colour);
         x += this.font.width(part);
         i += part.length();
      }

      String where = Ui.t("chunkatlas.settings.tab." + TABS[this.running]);
      if (shown.length() == text.length() && x + 8 + this.font.width(where) <= this.cx0 + room) {
         this.g.text(this.font, where, x + 8, ty, Glass.NOTE, false);
      }
   }

   private void heading(String key) {
      if (this.indexing()) {
         return;
      }

      if (!this.first) {
         this.y += 6;
      }

      this.first = false;
      if (this.visible(HEAD)) {
         this.g.text(this.font, Ui.t(key), this.cx0, this.y + 5, Glass.NOTE);
      }

      this.y += HEAD;
   }

   private void check(String key, boolean on, Runnable action) {
      if (!this.gate(Ui.t(key))) {
         return;
      }

      int h = ROW;
      boolean usable = action != null;
      if (this.visible(h)) {
         boolean hover = this.hover(h);
         if (hover && usable) {
            this.g.fill(this.cx0 - 4, Math.max(this.y, this.cy0), this.cx1 + 6, Math.min(this.y + h, this.cy1), 0x12FFFFFF);
         }

         this.name(key, h, usable, this.cx1 - 14 - this.cx0);
         Glass.check(this.g, this.font, new ArrayList<>(), this.cx1 - 8, this.y + 5, "", on, action);
         if (usable) {
            this.add(new Ui.Hit(this.cx0 - 4, this.y, this.cx1 + 6, this.y + h, action));
         }

         this.tip(key, hover);
      }

      this.y += h;
   }

   private void choice(String key, String[] labels, int chosen, boolean usable, IntConsumer pick) {
      if (!this.gate(Ui.t(key))) {
         return;
      }

      int h = Glass.segmentsHeight() + 2;
      if (this.visible(h)) {
         int sw = Glass.segmentsWidth(this.font, labels);
         int sx = this.cx1 - sw;
         int sy = this.y + 1;
         boolean hover = this.hover(h);
         this.name(key, h, usable, sx - 6 - this.cx0);
         if (usable) {
            boolean[] use = new boolean[labels.length];
            Arrays.fill(use, true);
            List<Ui.Hit> made = new ArrayList<>();
            Glass.segments(this.g, this.font, made, sx, sy, labels, chosen, use, pick, this.mouseX, this.mouseY, this.covered || !this.hover(h));
            this.add(made);
         } else {
            Glass.panel(this.g, sx, sy, sw, Glass.segmentsHeight());
            int at = sx + Glass.INSET;
            for (int i = 0; i < labels.length; i++) {
               int w = this.font.width(labels[i]) + 14;
               if (i == chosen) {
                  this.g.fill(at, sy + Glass.INSET, at + w, sy + Glass.INSET + 16, 0x14FFFFFF);
                  this.g.fill(at, sy + Glass.INSET + 14, at + w, sy + Glass.INSET + 16, UNLIT);
               }

               this.g.text(this.font, labels[i], at + 7, sy + Glass.INSET + 4, Glass.OFF, false);
               at += w + 2;
            }
         }

         this.tip(key, hover);
      }

      this.y += h;
   }

   private String[] words(String key, String... names) {
      String[] out = new String[names.length];
      for (int i = 0; i < names.length; i++) {
         out[i] = Ui.t(key + "." + names[i]);
      }

      return out;
   }

   private static <T extends Enum<T>> String[] enumWords(String key, T[] values) {
      String[] out = new String[values.length];
      for (int i = 0; i < values.length; i++) {
         out[i] = Ui.t(key + "." + values[i].name().toLowerCase(Locale.ROOT));
      }

      return out;
   }

   private void slider(String key, String value, int steps, int index, boolean usable, IntConsumer pick) {
      if (!this.gate(Ui.t(key))) {
         return;
      }

      int h = ROW + 2;
      if (this.visible(h)) {
         int tx = this.cx1 - SLIDER_W;
         int ty = this.y + h / 2;
         boolean hover = this.hover(h);
         int vw = this.font.width(value);
         this.g.text(this.font, value, tx - 8 - vw, this.y + (h - 8) / 2, usable ? Glass.SOFT : Glass.OFF);
         this.name(key, h, usable, tx - 14 - vw - this.cx0);
         this.g.fill(tx, ty - 1, tx + SLIDER_W, ty + 1, usable ? 0x33FFFFFF : 0x1AFFFFFF);
         for (int i = 0; i < steps; i++) {
            int mx = tx + (int)Math.round(i / (double)Math.max(1, steps - 1) * (SLIDER_W - 1));
            this.g.fill(mx, ty - 2, mx + 1, ty + 2, usable ? 0x33FFFFFF : 0x1AFFFFFF);
         }

         int kx = tx + (int)Math.round(Math.clamp(index, 0, steps - 1) / (double)Math.max(1, steps - 1) * (SLIDER_W - 1));
         this.g.fill(kx - 2, ty - 5, kx + 3, ty + 5, 0xFF000000);
         this.g.fill(kx - 1, ty - 4, kx + 2, ty + 4, usable ? Glass.ACCENT : Glass.OFF);
         if (usable) {
            this.sliders.add(new SettingsScreen.Slider(tx, Math.max(this.y, this.cy0), Math.min(this.y + h, this.cy1), SLIDER_W, steps, pick));
         }

         this.tip(key, hover);
      }

      this.y += h;
   }

   private int dropBox(int right, String[] labels, int chosen, boolean usable, IntConsumer pick) {
      int widest = 0;
      for (String l : labels) {
         widest = Math.max(widest, this.font.width(l));
      }

      int w = widest + 24;
      int bx = right - w;
      int by = this.y + 2;
      int bh = 16;
      boolean over = this.hover(ROW + 2) && Glass.over(this.mouseX, this.mouseY, bx, by, w, bh);
      this.g.fill(bx, by, bx + w, by + bh, usable ? (over ? Glass.HOT : Glass.TINT) : 0x10FFFFFF);
      this.g.outline(bx, by, w, bh, usable ? (over ? 0xC0FFFFFF : 0x38FFFFFF) : 0x1AFFFFFF);
      this.g.text(this.font, labels[Math.clamp(chosen, 0, labels.length - 1)], bx + 6, by + 4, usable ? Ui.WHITE : Glass.OFF, usable);
      int ax = bx + w - 11;
      int ay = by + 7;
      int c = usable ? Glass.SOFT : Glass.OFF;
      this.g.fill(ax, ay, ax + 5, ay + 1, c);
      this.g.fill(ax + 1, ay + 1, ax + 4, ay + 2, c);
      this.g.fill(ax + 2, ay + 2, ax + 3, ay + 3, c);
      if (usable) {
         this.add(new Ui.Hit(bx, by, bx + w, by + bh, () -> {
            List<Ui.Item> items = new ArrayList<>();
            for (int i = 0; i < labels.length; i++) {
               int n = i;
               items.add(Ui.Item.radio(labels[i], i == chosen, () -> pick.accept(n)));
            }

            this.menu = items;
            this.menuX = bx + w - Glass.menuWidth(this.font, items);
            this.menuY = by + bh + 1;
         }));
      }

      return bx;
   }

   private void dropdown(String key, String[] labels, int chosen, boolean usable, IntConsumer pick) {
      if (!this.gate(Ui.t(key))) {
         return;
      }

      int h = ROW + 2;
      if (this.visible(h)) {
         boolean hover = this.hover(h);
         int left = this.dropBox(this.cx1, labels, chosen, usable, pick);
         this.name(key, h, usable, left - 6 - this.cx0);
         this.tip(key, hover);
      }

      this.y += h;
   }

   private void button(String key, String textKey, boolean usable, Runnable action) {
      if (!this.gate(Ui.t(key))) {
         return;
      }

      int h = ROW + 4;
      if (this.visible(h)) {
         String text = Ui.t(textKey);
         int w = this.font.width(text) + 16;
         boolean hover = this.hover(h);
         this.name(key, h, usable, this.cx1 - w - 6 - this.cx0);
         List<Ui.Hit> made = new ArrayList<>();
         Glass.button(this.g, this.font, made, this.cx1 - w, this.y + 2, w, 18, text, Ui.WHITE, this.covered ? -100 : this.mouseX, this.mouseY, usable ? action : null);
         this.add(made);
         this.tip(key, hover);
      }

      this.y += h;
   }

   private void textBox(String key, EditBox box, boolean usable) {
      if (!this.gate(Ui.t(key))) {
         return;
      }

      int h = ROW + 4;
      int w = 90;
      int bx = this.cx1 - w;
      int by = this.y + 3;
      boolean shown = this.visible(h);
      box.setX(bx);
      box.setY(by);
      box.setWidth(w);
      box.visible = shown && by >= this.cy0 && by + 16 <= this.cy1;
      box.active = usable;
      box.setEditable(usable);
      if (shown) {
         boolean hover = this.hover(h);
         this.name(key, h, usable, bx - 6 - this.cx0);
         this.g.widget(box, this.covered ? -100 : this.mouseX, this.mouseY, this.partial);
         this.tip(key, hover);
      }

      this.y += h;
   }

   private void sized(String key, long bytes, String buttonKey, Runnable action) {
      if (!this.gate(Ui.t(key))) {
         return;
      }

      int h = ROW + 4;
      if (this.visible(h)) {
         String text = Ui.t(buttonKey);
         int w = this.font.width(text) + 16;
         String size = bytes < 0L ? "…" : Ui.size(bytes);
         int sw = this.font.width(size);
         boolean hover = this.hover(h);
         boolean usable = bytes > 0L && !DiskFacts.busy();
         this.name(key, h, true, this.cx1 - w - 10 - sw - 6 - this.cx0);
         this.g.text(this.font, size, this.cx1 - w - 6 - sw, this.y + (h - 8) / 2, Glass.LABEL);
         List<Ui.Hit> made = new ArrayList<>();
         Glass.button(this.g, this.font, made, this.cx1 - w, this.y + 2, w, 18, text, Ui.WHITE, this.covered ? -100 : this.mouseX, this.mouseY, usable ? action : null);
         this.add(made);
         this.tip(key, hover);
      }

      this.y += h;
   }

   private record Found(int tab, String label) {
   }

   private void generalTab() {
      this.heading("chunkatlas.settings.group.playstyle");

      this.choice("chunkatlas.settings.preset", this.words("chunkatlas.settings.preset", Presets.NAMES), Presets.current(), true, i -> {
         Presets.apply(i);
         this.changed();
      });
      this.heading("chunkatlas.settings.group.buttons");
      this.check("chunkatlas.settings.pause_button", Settings.pauseButton(), () -> Settings.pauseButton(!Settings.pauseButton()));
      this.check("chunkatlas.settings.world_list_button", Settings.worldListButton(), () -> Settings.worldListButton(!Settings.worldListButton()));
   }

   private void mapTab() {
      boolean detailed = Settings.detailed();
      this.heading("chunkatlas.settings.group.look");
      this.choice("chunkatlas.settings.look", this.words("chunkatlas.settings.look", "vanilla", "detailed"), detailed ? 1 : 0, true, i -> {
         Settings.detailed(i == 1);
         this.changed();
      });
      this.choice("chunkatlas.settings.water", this.words("chunkatlas.settings.water", "solid", "clear"), Settings.clearWater() ? 1 : 0, detailed, i -> {
         Settings.clearWater(i == 1);
         this.changed();
      });
      this.choice("chunkatlas.settings.shading", this.words("chunkatlas.settings.shading", "off", "soft", "strong"), Settings.shading(), detailed, i -> {
         Settings.shading(i);
         this.changed();
      });
      this.check("chunkatlas.settings.biome_colours", Settings.biomeColours(), detailed ? () -> {
         Settings.biomeColours(!Settings.biomeColours());
         this.changed();
      } : null);
      this.check("chunkatlas.settings.height_tint", Settings.heightTint(), detailed ? () -> {
         Settings.heightTint(!Settings.heightTint());
         this.changed();
      } : null);
      this.check("chunkatlas.settings.night", Settings.night(), detailed ? () -> {
         Settings.night(!Settings.night());
         this.changed();
      } : null);

      Config c = Config.get();
      this.heading("chunkatlas.settings.group.underground");
      this.choice("chunkatlas.settings.underground", this.words("chunkatlas.settings.underground", "surface", "caves"), Settings.undergroundCaves() ? 1 : 0, true, i -> {
         Settings.undergroundCaves(i == 1);
         this.saved(() -> c.cave = i == 1 ? Config.Cave.AUTO : Config.Cave.OFF);
      });
      int roof = StepSlider.nearest(ROOFS, c.caveRoof);
      this.slider("chunkatlas_minimap.settings.cave_roof", ROOFS[roof] + "×" + ROOFS[roof], ROOFS.length, roof, Settings.undergroundCaves(), i -> {
         if (c.caveRoof != ROOFS[i]) {
            this.saved(() -> c.caveRoof = ROOFS[i]);
         }
      });

      this.heading("chunkatlas.settings.group.on_map");
      this.check("chunkatlas.settings.coordinates", Settings.coordinates(), () -> {
         Settings.coordinates(!Settings.coordinates());
         this.changed();
      });
      this.check("chunkatlas.settings.players", Settings.players(), () -> {
         Settings.players(!Settings.players());
         this.changed();
      });
      this.check("chunkatlas.settings.mobs", Settings.mobs(), () -> {
         Settings.mobs(!Settings.mobs());
         this.changed();
      });
      this.check("chunkatlas.settings.all_players", Settings.allPlayers(), () -> {
         Settings.allPlayers(!Settings.allPlayers());
         this.changed();
      });
      this.check("chunkatlas.settings.protected", Settings.protectedShown(), () -> {
         Settings.protectedShown(!Settings.protectedShown());
         this.changed();
      });
      this.check("chunkatlas.settings.grid", Settings.grid(), () -> {
         Settings.grid(!Settings.grid());
         this.changed();
      });
      this.check("chunkatlas.settings.smooth_zoom", Settings.smoothZoom(), () -> Settings.smoothZoom(!Settings.smoothZoom()));
      int fade = 0;
      if (Settings.footsteps()) {
         fade = 1 + StepSlider.nearest(Settings.FOOTSTEP_FADES, Settings.footstepsFade());
      }

      this.slider("chunkatlas.settings.footsteps_fade", Ui.t("chunkatlas.settings.footsteps_fade." + FADES[fade]), FADES.length, fade, true, i -> {
         if (i == 0) {
            Settings.footsteps(false);
         } else {
            Settings.footsteps(true);
            Settings.footstepsFade(Settings.FOOTSTEP_FADES[i - 1]);
         }

         this.changed();
      });

      this.heading("chunkatlas.settings.group.performance");

      int cache = Settings.cacheMb() == Settings.NO_LIMIT ? CACHE_MB.length - 1 : StepSlider.nearest(Arrays.copyOf(CACHE_MB, CACHE_MB.length - 1), Settings.cacheMb());
      String limit = CACHE_MB[cache] == Settings.NO_LIMIT ? Ui.t("chunkatlas.settings.cache.none") : Ui.t("chunkatlas.settings.cache.value", CACHE_MB[cache]);
      long used = this.indexing() ? -1L : CacheSize.bytes();
      this.slider("chunkatlas.settings.cache", used < 0L ? limit : Ui.t("chunkatlas.settings.cache.used", Ui.size(used), limit), CACHE_MB.length, cache, true, i -> {
         if (CACHE_MB[i] != Settings.cacheMb()) {
            Settings.cacheMb(CACHE_MB[i]);
         }
      });
      int workers = StepSlider.nearest(WORKERS, Settings.workers());
      this.slider("chunkatlas.settings.workers", WORKERS[workers] == 0 ? Ui.t("chunkatlas.settings.workers.auto") : Integer.toString(WORKERS[workers]), WORKERS.length, workers, true, i -> {
         if (WORKERS[i] != Settings.workers()) {
            Settings.workers(WORKERS[i]);
         }
      });
   }

   private void minimapTab() {
      Config c = Config.get();
      String k = "chunkatlas_minimap.settings.";
      this.heading("chunkatlas.settings.group.minimap");
      this.check(k + "enabled", c.enabled, () -> this.saved(() -> c.enabled = !c.enabled));
      boolean on = c.enabled;
      this.choice(k + "shape", enumWords(k + "shape", Config.Shape.values()), c.shape.ordinal(), on, i -> this.saved(() -> c.shape = Config.Shape.values()[i]));

      Config.Size[] sizes = c.customSize > 0 ? Config.Size.values() : Arrays.copyOf(Config.Size.values(), Config.Size.CUSTOM.ordinal());
      this.dropdown(k + "size", enumWords(k + "size", sizes), Math.min(c.size.ordinal(), sizes.length - 1), on, i -> this.saved(() -> c.size = sizes[i]));
      this.slider(k + "zoom", zoomName(c.zoom), Config.ZOOMS.length, c.zoom, on, i -> {
         if (c.zoom != i) {
            this.saved(() -> c.zoom = i);
         }
      });
      this.check("chunkatlas.settings.minimap.smart_zoom", c.smartZoom, on ? () -> this.saved(() -> c.smartZoom = !c.smartZoom) : null);
      this.check(k + "north_lock", c.northLock, on ? () -> this.saved(() -> c.northLock = !c.northLock) : null);
      this.choice(k + "compass", new String[]{Ui.t(k + "compass.off"), Ui.t(k + "compass.north"), Ui.t(k + "compass.all")}, Math.max(0, Math.min(2, c.compass)), on, i -> this.saved(() -> c.compass = i));
      this.check(k + "frame", c.frame, on ? () -> this.saved(() -> c.frame = !c.frame) : null);
      int opacity = StepSlider.nearest(OPACITIES, Math.round(c.opacity * 100.0F));
      this.slider(k + "opacity", OPACITIES[opacity] + "%", OPACITIES.length, opacity, on, i -> {
         if (Math.round(c.opacity * 100.0F) != OPACITIES[i]) {
            this.saved(() -> c.opacity = OPACITIES[i] / 100.0F);
         }
      });
      this.check(k + "atlas_colours", c.chunkAtlasColours, on ? () -> this.saved(() -> {
         c.chunkAtlasColours = !c.chunkAtlasColours;
         Colours.reset();
         Minimap.CACHE.clear();
      }) : null);

      this.heading("chunkatlas.settings.group.under_minimap");
      this.check(k + "coords", c.showCoords, on ? () -> this.saved(() -> c.showCoords = !c.showCoords) : null);
      this.check(k + "biome", c.showBiome, on ? () -> this.saved(() -> c.showBiome = !c.showBiome) : null);
      this.check(k + "light", c.showLight, on ? () -> this.saved(() -> c.showLight = !c.showLight) : null);
      this.check(k + "time", c.showTime, on ? () -> this.saved(() -> c.showTime = !c.showTime) : null);
      this.check(k + "angle", c.showAngle, on ? () -> this.saved(() -> c.showAngle = !c.showAngle) : null);

      this.heading("chunkatlas.settings.group.radar");
      this.check(k + "radar", c.radar, on ? () -> this.saved(() -> c.radar = !c.radar) : null);
      boolean radar = on && c.radar;
      this.choice(k + "radar_style", enumWords(k + "radar_style", Config.RadarStyle.values()), c.radarStyle.ordinal(), radar, i -> this.saved(() -> c.radarStyle = Config.RadarStyle.values()[i]));
      this.check(k + "radar_players", c.radarPlayers, radar ? () -> this.saved(() -> c.radarPlayers = !c.radarPlayers) : null);
      this.check(k + "radar_hostile", c.radarHostile, radar ? () -> this.saved(() -> c.radarHostile = !c.radarHostile) : null);
      this.check(k + "radar_friendly", c.radarFriendly, radar ? () -> this.saved(() -> c.radarFriendly = !c.radarFriendly) : null);

      this.heading("chunkatlas.settings.group.waypoints");
      this.check(k + "waypoints_map", c.waypointsOnMap, () -> this.saved(() -> c.waypointsOnMap = !c.waypointsOnMap));
      this.check(k + "waypoints_world", c.waypointsInWorld, () -> this.saved(() -> c.waypointsInWorld = !c.waypointsInWorld));
      this.check(k + "deathpoints", c.deathpoints, () -> this.saved(() -> c.deathpoints = !c.deathpoints));
      this.button(k + "waypoint_list", k + "waypoint_list.button", true, () -> Game.open(this.mc, new WaypointsScreen(this)));

      this.heading("chunkatlas.settings.group.overlays");
      this.check(k + "chunk_grid", c.chunkGrid, on ? () -> this.saved(() -> c.chunkGrid = !c.chunkGrid) : null);
      this.check(k + "slime_chunks", c.slimeChunks, on ? () -> this.saved(() -> c.slimeChunks = !c.slimeChunks) : null);
      this.check(k + "light_overlay", c.lightOverlay, on ? () -> this.saved(() -> c.lightOverlay = !c.lightOverlay) : null);

      this.heading("chunkatlas.settings.group.position");
      this.button(k + "position", k + "position.button", true, () -> Game.open(this.mc, new PositionScreen(this)));
      this.choice(k + "enlarge_mode", enumWords(k + "enlarge_mode", Config.Enlarge.values()), c.enlargeMode.ordinal(), on, i -> this.saved(() -> c.enlargeMode = Config.Enlarge.values()[i]));
      this.choice(k + "enlarge_view", enumWords(k + "enlarge_view", Config.EnlargeView.values()), c.enlargeView.ordinal(), on, i -> this.saved(() -> c.enlargeView = Config.EnlargeView.values()[i]));
      this.check(k + "enlarge_zoom_out", c.enlargeZoomOut, on ? () -> this.saved(() -> c.enlargeZoomOut = !c.enlargeZoomOut) : null);
   }

   static String zoomName(int i) {
      double z = Config.ZOOMS[Math.clamp(i, 0, Config.ZOOMS.length - 1)];
      return z >= 1.0 ? "×" + (int)z : "×1/" + Math.round(1.0 / z);
   }

   private void seedTab() {
      Config c = Config.get();

      this.heading("chunkatlas.settings.group.seed");
      this.textBox("chunkatlas.settings.server_seed", this.seedBox, this.onServer());
      if (dev.chunkatlas.port.Seeds.available()) {
         this.heading("chunkatlas.settings.group.seed_map");
         this.check("chunkatlas_minimap.settings.seed_map", c.seedMap, () -> this.saved(() -> c.seedMap = !c.seedMap));
      }
   }

   private boolean editorUsable() {
      return this.mc.level == null || this.mc.getSingleplayerServer() != null || ServerLink.hasMod();
   }

   private void editorTab() {
      boolean usable = this.editorUsable();
      this.heading("chunkatlas.settings.group.editing");
      this.check("chunkatlas.settings.edit_without_cheats", Settings.editWithoutCheats(), usable ? () -> Settings.editWithoutCheats(!Settings.editWithoutCheats()) : null);
      this.check("chunkatlas.settings.backups", Settings.backups(), usable ? () -> Settings.backups(!Settings.backups()) : null);
      this.check("chunkatlas.settings.keep_structures", Settings.keepStructures(), usable ? () -> Settings.keepStructures(!Settings.keepStructures()) : null);
      this.heading("chunkatlas.settings.group.tasks");
      String[] rejoin = {Ui.t("chunkatlas.settings.tasks_rejoin.continue"), Ui.t("chunkatlas.settings.tasks_rejoin.pause")};
      this.choice("chunkatlas.settings.tasks_rejoin", rejoin, Settings.tasksRejoin(), true, Settings::tasksRejoin);
      this.check("chunkatlas.settings.tasks_hud", Settings.tasksHud(), () -> Settings.tasksHud(!Settings.tasksHud()));
      String[] corners = {
         Ui.t("chunkatlas.settings.corner.top_left"), Ui.t("chunkatlas.settings.corner.top_right"), Ui.t("chunkatlas.settings.corner.bottom_left"),
         Ui.t("chunkatlas.settings.corner.bottom_right")
      };
      this.dropdown("chunkatlas.settings.tasks_hud_corner", corners, Settings.tasksHudCorner(), Settings.tasksHud(), Settings::tasksHudCorner);
      this.heading("chunkatlas.settings.group.borders");
      this.check("chunkatlas.settings.border_in_world", Settings.borderInWorld(), () -> Settings.borderInWorld(!Settings.borderInWorld()));

      boolean single = this.mc.level == null || this.mc.getSingleplayerServer() != null;
      this.check("chunkatlas.settings.border_particles", Settings.borderParticles(), single ? () -> Settings.borderParticles(!Settings.borderParticles()) : null);
      this.textBox("chunkatlas.settings.border_message", this.borderBox, single);
      String[] sounds = {
         Ui.t("chunkatlas.settings.border_sound.none"), Ui.t("chunkatlas.settings.border_sound.enderman"), Ui.t("chunkatlas.settings.border_sound.note_block"),
         Ui.t("chunkatlas.settings.border_sound.bell")
      };
      this.dropdown("chunkatlas.settings.border_sound", sounds, Math.min(sounds.length - 1, Settings.borderSound()), single, Settings::borderSound);
      String[] effects = {
         Ui.t("chunkatlas.settings.border_effect.none"), Ui.t("chunkatlas.settings.border_effect.portal"), Ui.t("chunkatlas.settings.border_effect.smoke"),
         Ui.t("chunkatlas.settings.border_effect.witch")
      };
      this.dropdown("chunkatlas.settings.border_effect", effects, Math.min(effects.length - 1, Settings.borderEffect()), single, Settings::borderEffect);
   }

   private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault());
   private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());

   private Path world() {
      return this.map == null ? null : this.map.worldFolder();
   }

   private String when(long millis) {
      LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
      return (t.getYear() == LocalDateTime.now().getYear() ? DAY_TIME : DAY_YEAR).format(t);
   }

   private String what(ChunkEdits.Backup b) {
      String kind = b.kind() == null ? null : switch (b.kind()) {
         case DELETE -> "delete";
         case KEEP_ONLY -> "keep_only";
         case DELETE_MOBS -> "delete_mobs";
         case COPY -> "copy";
      };
      String text = kind == null || b.chunks() <= 0
         ? Ui.t("chunkatlas.settings.backups.kind.edit")
         : Ui.plural("chunkatlas.settings.backups.kind." + kind, b.chunks());
      boolean other = b.dimension() != null && !b.dimension().isEmpty() && !b.dimension().equals("minecraft:overworld");
      return other ? text + " · " + Ui.dimension(b.dimension()) : text;
   }

   private List<ChunkEdits.Backup> olderThan(List<ChunkEdits.Backup> all, int days) {
      long before = System.currentTimeMillis() - days * DAY_MS;
      List<ChunkEdits.Backup> out = new ArrayList<>();
      for (ChunkEdits.Backup b : all) {
         if (b.createdMillis() < before) {
            out.add(b);
         }
      }

      return out;
   }

   private void backupsTab() {
      List<ChunkEdits.Backup> all = this.indexing() ? null : DiskFacts.backups();
      this.olderRow(all);
      if (!this.indexing()) {
         this.backupList(all);
         this.heading("chunkatlas.settings.group.cache");
      }

      Path world = this.world();
      if (world != null) {
         long bytes = this.indexing() ? -1L : DiskFacts.cacheWorld(world);
         this.sized("chunkatlas.settings.cache_world", bytes, "chunkatlas.settings.cache_clear", () -> this.askClear(true, bytes));
      }

      long every = this.indexing() ? -1L : DiskFacts.cacheAll();
      this.sized("chunkatlas.settings.cache_all", every, "chunkatlas.settings.cache_clear", () -> this.askClear(false, every));
   }

   private void olderRow(List<ChunkEdits.Backup> all) {
      String key = "chunkatlas.settings.backups.older";
      if (!this.gate(Ui.t(key))) {
         return;
      }

      int h = ROW + 4;
      if (this.visible(h)) {
         int days = OLDER_DAYS[this.older];
         List<ChunkEdits.Backup> old = all == null ? List.of() : this.olderThan(all, days);
         String text = Ui.t("chunkatlas.settings.backups.delete_n", Ui.count(old.size()));
         int w = this.font.width(text) + 16;
         boolean hover = this.hover(h);
         List<Ui.Hit> made = new ArrayList<>();
         boolean usable = !old.isEmpty() && !DiskFacts.busy();
         Glass.button(this.g, this.font, made, this.cx1 - w, this.y + 2, w, 18, text, Glass.DANGER, this.covered ? -100 : this.mouseX, this.mouseY, usable ? () -> this.askOlder(all, days) : null);
         this.add(made);
         String[] labels = new String[OLDER_DAYS.length];
         for (int i = 0; i < labels.length; i++) {
            labels[i] = Ui.plural("chunkatlas.settings.backups.days", OLDER_DAYS[i]);
         }

         int left = this.dropBox(this.cx1 - w - 6, labels, this.older, true, i -> this.older = i);
         this.name(key, h, true, left - 6 - this.cx0);
         this.tip(key, hover);
      }

      this.y += h;
   }

   private void backupList(List<ChunkEdits.Backup> all) {
      if (all == null || all.isEmpty()) {
         String none = Ui.t(all == null ? "chunkatlas.settings.backups.counting" : "chunkatlas.settings.backups.none");
         this.first = false;
         if (this.visible(ROW)) {
            this.g.text(this.font, none, this.cx0, this.y + 12, Glass.NOTE);
         }

         this.y += ROW + 6;
         return;
      }

      Map<String, List<ChunkEdits.Backup>> worlds = new LinkedHashMap<>();
      for (ChunkEdits.Backup b : all) {
         worlds.computeIfAbsent(b.worldKey(), x -> new ArrayList<>()).add(b);
      }

      String delete = Ui.t("chunkatlas.settings.backups.delete");
      int dw = this.font.width(delete);
      int dateW = this.font.width("00 Mmm 00:00") + 8;
      for (List<ChunkEdits.Backup> group : worlds.values()) {
         this.y += 6;
         long total = 0L;
         for (ChunkEdits.Backup b : group) {
            total += b.bytes();
         }

         if (this.visible(ROW)) {
            String name = group.get(0).world().isEmpty() ? Ui.t("chunkatlas.settings.backups.unknown_world") : group.get(0).world();
            String size = Ui.size(total);
            int sw = this.font.width(size);
            this.g.text(this.font, this.font.plainSubstrByWidth(name, this.cx1 - this.cx0 - sw - 12), this.cx0, this.y + 6, Ui.WHITE);
            this.g.text(this.font, size, this.cx1 - sw, this.y + 6, Glass.LABEL);
            this.g.fill(this.cx0 - 4, this.y + ROW - 1, this.cx1 + 6, this.y + ROW, 0x14FFFFFF);
         }

         this.y += ROW;
         for (ChunkEdits.Backup b : group) {
            this.backupRow(b, dateW, dw, delete);
         }
      }

      this.y += 4;
   }

   private void backupRow(ChunkEdits.Backup b, int dateW, int dw, String delete) {
      int h = 16;
      if (this.visible(h)) {
         boolean hover = this.hover(h);
         if (hover) {
            this.g.fill(this.cx0 - 4, Math.max(this.y, this.cy0), this.cx1 + 6, Math.min(this.y + h, this.cy1), 0x12FFFFFF);
         }

         int ty = this.y + 4;
         String size = Ui.size(b.bytes());
         int sw = this.font.width(size);
         int dx = this.cx1 - dw;
         boolean overDelete = hover && this.mouseX >= dx - 4;
         this.g.text(this.font, this.when(b.createdMillis()), this.cx0, ty, Glass.LABEL);
         int ex = this.cx0 + dateW;
         int room = dx - 10 - sw - 8 - ex;
         this.g.text(this.font, this.font.plainSubstrByWidth(this.what(b), Math.max(12, room)), ex, ty, Ui.WHITE);
         this.g.text(this.font, size, dx - 10 - sw, ty, Glass.LABEL);
         this.g.text(this.font, delete, dx, ty, overDelete ? 0xFFFFB0B0 : Glass.DANGER);
         this.add(new Ui.Hit(dx - 4, this.y, this.cx1 + 6, this.y + h, () -> this.askOne(b)));
      }

      this.y += h;
   }

   private static List<KeyMapping> inGameKeys() {
      List<KeyMapping> keys = new ArrayList<>();
      if (InGame.openKey() != null) {
         keys.add(InGame.openKey());
      }

      keys.addAll(Minimap.keys());
      return keys;
   }

   private void keysTab() {
      this.heading("chunkatlas.settings.keys.in_game");
      for (KeyMapping key : inGameKeys()) {
         this.keyRow(key);
      }

      this.heading("chunkatlas.settings.keys.map");
      for (MapKey key : MapKey.values()) {
         if (key.map) {
            this.keyRow(key);
         }
      }

      this.heading("chunkatlas.settings.keys.editor");
      for (MapKey key : MapKey.values()) {
         if (!key.map) {
            this.keyRow(key);
         }
      }
   }

   private void keyRow(Object key) {
      int h = ROW + 2;
      String name = key instanceof MapKey mk ? mk.label().getString() : Component.translatable(((KeyMapping)key).getName()).getString();
      if (!this.gate(name)) {
         return;
      }

      if (!this.visible(h)) {
         this.y += h;
         return;
      }

      boolean set;
      boolean isDefault;
      String text;
      String clash;
      String defaultName;
      Runnable reset;
      if (key instanceof MapKey k) {
         set = k.isSet();
         isDefault = k.isDefault();
         text = k.keyName();
         clash = this.clash(k);
         defaultName = MapKey.keyName(k.defaultCombo());
         reset = () -> k.set(null);
      } else {
         KeyMapping m = (KeyMapping)key;
         set = !m.isUnbound();
         isDefault = m.isDefault();
         text = m.getTranslatedKeyMessage().getString();
         clash = this.clash(m);
         defaultName = m.getDefaultKey().getDisplayName().getString();
         reset = () -> {
            m.setKey(m.getDefaultKey());
            this.keysChanged();
         };
      }

      boolean listen = this.listening == key;
      boolean hover = this.hover(h);

      int ax = this.cx1 - 10;
      boolean lit = !isDefault;
      boolean overArrow = hover && this.mouseX >= ax - 3;
      if (lit && overArrow) {
         this.g.fill(ax - 2, this.y + 2, ax + 12, this.y + h - 2, Glass.TINT);
      }

      if (lit) {
         this.g.sprite(Glass.icon("undo"), ax - 4, this.y + (h - 16) / 2, 16, 16, Ui.WHITE);
         this.add(new Ui.Hit(ax - 3, this.y, this.cx1 + 6, this.y + h, () -> {
            reset.run();
            if (this.listening == key) {
               this.listening = null;
            }
         }));
         if (overArrow) {
            this.tip = Ui.t("chunkatlas.settings.keys.reset_to", defaultName.isEmpty() ? Ui.t("chunkatlas.settings.keys.not_set") : defaultName);
         }
      }

      int right = ax - 8;
      int left;
      int capTop = this.y + 3;
      if (listen) {
         left = this.cap(right, capTop, Ui.t("chunkatlas.settings.keys.press"), Glass.ACCENT, Glass.ACCENT, false);
      } else if (!set) {
         left = this.cap(right, capTop, Ui.t("chunkatlas.settings.keys.not_set"), Glass.OFF, 0x66FFFFFF, true);
      } else {
         int colour = clash != null ? Glass.DANGER : Ui.WHITE;
         int edge = clash != null ? Glass.DANGER : 0x50FFFFFF;
         String[] parts = text.split(" \\+ ");
         left = right;
         for (int i = parts.length - 1; i >= 0; i--) {
            left = this.cap(left, capTop, parts[i], colour, edge, false);
            if (i > 0) {
               left -= 10;
               this.g.text(this.font, "+", left + 2, this.y + 6, Glass.SOFT);
            }
         }
      }

      if (!listen) {
         this.add(new Ui.Hit(left - 2, this.y, right + 2, this.y + h, () -> {
            this.listening = key;
            this.setFocused(null);
         }));
         if (hover && !overArrow && this.mouseX >= left - 2 && this.mouseX < right + 2) {
            this.tip = Ui.t("chunkatlas.settings.keys.change");
         }
      }

      int nameW = this.font.width(name);
      int room = left - 8 - this.cx0;
      if (clash != null && !listen) {
         String note = Ui.t("chunkatlas.settings.keys.same_as", clash);
         int noteRoom = room - nameW - 8;
         if (noteRoom > 20) {
            String shown = this.font.plainSubstrByWidth(note, noteRoom);
            this.g.text(this.font, shown, left - 8 - this.font.width(shown), this.y + 6, Glass.DANGER);
            room = Math.min(room, left - 8 - this.font.width(shown) - 8 - this.cx0);
         }
      }

      this.label(name, h, true, room);
      this.y += h;
   }

   private int cap(int right, int top, String text, int colour, int edge, boolean dashed) {
      int w = this.font.width(text) + 10;
      int x = right - w;
      int h = 14;
      this.g.fill(x, top, x + w, top + h, Glass.TINT);
      if (dashed) {
         this.dashed(x, top, w, h, edge);
      } else {
         this.g.outline(x, top, w, h, edge);
         this.g.fill(x + 1, top + h - 2, x + w - 1, top + h - 1, 0x40000000);
      }

      this.g.text(this.font, text, x + 5, top + 3, colour, !dashed);
      return x;
   }

   private void dashed(int x, int y, int w, int h, int c) {
      for (int i = 0; i < w; i += 4) {
         int e = Math.min(i + 2, w);
         this.g.fill(x + i, y, x + e, y + 1, c);
         this.g.fill(x + i, y + h - 1, x + e, y + h, c);
      }

      for (int i = 0; i < h; i += 4) {
         int e = Math.min(i + 2, h);
         this.g.fill(x, y + i, x + 1, y + e, c);
         this.g.fill(x + w - 1, y + i, x + w, y + e, c);
      }
   }

   private String clash(MapKey key) {
      if (!key.isSet()) {
         return null;
      }

      for (MapKey other : MapKey.values()) {
         if (other != key && other.combo().equals(key.combo())) {
            return other.label().getString();
         }
      }

      return null;
   }

   private String clash(KeyMapping key) {
      if (key.isUnbound()) {
         return null;
      }

      for (KeyMapping other : this.mc.options.keyMappings) {
         if (other != key && !other.isUnbound() && key.same(other) && !other.getName().startsWith("key.debug.")) {
            return Component.translatable(other.getName()).getString();
         }
      }

      return null;
   }

   private void keysChanged() {
      KeyMapping.resetMapping();
      this.mc.options.save();
   }

   private void resetTab() {
      this.listening = null;
      this.menu = null;
      switch (this.tab) {
         case GENERAL -> Settings.reset("pause_button", "world_list_button");
         case MAP -> {
            Settings.reset(
               "look.detailed", "look.clear_water", "look.shading", "look.biome_colours", "look.height_tint", "look.night", "underground_caves",
               "coordinates", "players", "map.mobs", "players.all", "protected", "grid", "smooth_zoom", "footsteps", "footsteps.fade", "cache_mb", "workers"
            );
            Settings.applyLimits();
            Config d = new Config();
            this.saved(() -> {
               Config.get().cave = d.cave;
               Config.get().caveRoof = d.caveRoof;
            });
         }
         case MINIMAP -> {
            long seed = Config.get().slimeSeed;
            boolean seedMap = Config.get().seedMap;
            Config.reset();
            Config.get().slimeSeed = seed;
            Config.get().seedMap = seedMap;
            Config.get().save();
            Colours.reset();
            Minimap.CACHE.clear();
         }
         case SEED -> this.saved(() -> Config.get().seedMap = new Config().seedMap);
         case EDITOR -> {
            Settings.reset(
               "edit_without_cheats", "backups", "keep_structures", "tasks.rejoin", "tasks.hud", "tasks.hud.corner", "border.in_world", "border.particles",
               "border.message", "border.sound", "border.effect"
            );
            Settings.applyLimits();
            this.borderBox.setValue("");
         }
         default -> {
            for (MapKey key : MapKey.values()) {
               key.set(null);
            }

            for (KeyMapping key : inGameKeys()) {
               key.setKey(key.getDefaultKey());
            }

            this.keysChanged();
         }
      }

      this.changed();
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      double x = mouseX;
      double y = mouseY;
      boolean left = mouseButton == InputConstants.MOUSE_BUTTON_LEFT;
      if (this.confirm != null) {
         if (left) {
            for (int n = this.confirmHits.size() - 1; n >= 0; n--) {
               if (this.confirmHits.get(n).has(x, y)) {
                  this.confirmHits.get(n).action().run();
                  break;
               }
            }
         }

         return true;
      }

      if (this.listening != null) {
         this.listening = null;
         return true;
      }

      if (this.menu != null) {
         if (left) {
            for (int n = this.menuHits.size() - 1; n >= 0; n--) {
               if (this.menuHits.get(n).has(x, y)) {
                  this.menuHits.get(n).action().run();
                  break;
               }
            }
         }

         this.menu = null;
         return true;
      }

      if (!left) {
         return true;
      }

      if (Glass.over((int)x, (int)y, this.searchBox.getX(), this.searchBox.getY(), this.searchBox.getWidth(), this.searchBox.getHeight())
         || this.seedBox.visible && Glass.over((int)x, (int)y, this.seedBox.getX(), this.seedBox.getY(), this.seedBox.getWidth(), this.seedBox.getHeight())
         || this.borderBox.visible && Glass.over((int)x, (int)y, this.borderBox.getX(), this.borderBox.getY(), this.borderBox.getWidth(), this.borderBox.getHeight())) {
         return super.onClick(mouseX, mouseY, mouseButton, doubleClick);
      }

      this.setFocused(null);

      if (this.overScrollBar(x, y)) {
         this.scrollDrag = true;
         this.scrollTo(y);
         return true;
      }

      for (int n = this.hits.size() - 1; n >= 0; n--) {
         if (this.hits.get(n).has(x, y)) {
            this.hits.get(n).action().run();
            return true;
         }
      }

      for (SettingsScreen.Slider s : this.sliders) {
         if (s.has(x, y)) {
            this.dragging = s;
            s.apply(x);
            return true;
         }
      }

      return true;
   }

   private void scrollTo(double y) {
      int viewH = this.cy1 - this.cy0;
      int track = viewH - 4;
      int knob = Math.max(12, track * viewH / Math.max(1, this.contentH[this.slot()]));
      double k = (y - this.cy0 - 2 - knob / 2.0) / Math.max(1, track - knob);
      this.scroll[this.slot()] = (int)Math.round(Math.clamp(k, 0.0, 1.0) * this.maxScroll());
   }

   @Override
   protected boolean onDrag(double mouseX, double mouseY, int mouseButton, double dx, double dy) {
      if (this.dragging != null) {
         this.dragging.apply(mouseX);
         return true;
      }

      if (this.scrollDrag) {
         this.scrollTo(mouseY);
         return true;
      }

      return super.onDrag(mouseX, mouseY, mouseButton, dx, dy);
   }

   @Override
   protected boolean onRelease(double mouseX, double mouseY, int mouseButton) {
      if (this.dragging != null || this.scrollDrag) {
         this.dragging = null;
         this.scrollDrag = false;
         return true;
      }

      return super.onRelease(mouseX, mouseY, mouseButton);
   }

   @Override
   protected boolean onScroll(double x, double y, double scrollX, double scrollY) {
      if (this.menu == null && this.confirm == null && x >= this.px && x < this.px + this.pw && y >= this.cy0 && y < this.cy1) {
         int s = this.slot();
         this.scroll[s] = Math.max(0, Math.min(this.maxScroll(), this.scroll[s] - (int)Math.round(scrollY * 16.0)));
      }

      return true;
   }

   @Override
   protected boolean onChar(int codepoint, int mods) {
      if (this.getFocused() == null && this.listening == null && this.menu == null && this.confirm == null && codepoint >= 32) {
         this.setFocused(this.searchBox);
      }

      return super.onChar(codepoint, mods);
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      int key = keyCode;
      if (this.confirm != null) {
         if (Keys.escape(keyCode)) {
            this.confirm = null;
         } else if (Keys.confirm(keyCode)) {
            SettingsScreen.Confirm c = this.confirm;
            this.confirm = null;
            c.action().run();
         }

         return true;
      }

      if (this.listening != null) {
         if (key == InputConstants.KEY_ESCAPE) {
            this.listening = null;
         } else if (key == InputConstants.KEY_BACKSPACE) {
            this.bind(null, modifiers);
         } else if (!isModifier(key)) {
            this.bind(Keys.key(key), modifiers);
         }

         return true;
      }

      if (this.menu != null) {
         if (Keys.escape(keyCode)) {
            this.menu = null;
         }

         return true;
      }

      if (Keys.escape(keyCode) && !this.searchBox.getValue().isEmpty()) {
         this.clearSearch();
         return true;
      }

      if (Keys.confirm(keyCode) && this.getFocused() == null) {
         this.onClose();
         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
   }

   private void bind(InputConstants.Key key, int mods) {
      if (this.listening instanceof MapKey k) {
         k.set(key == null ? MapKey.NONE : new MapKey.Combo(key.getValue(), Keys.shortcut(mods)));
      } else if (this.listening instanceof KeyMapping m) {
         m.setKey(key == null ? InputConstants.UNKNOWN : key);
         this.keysChanged();
      }

      this.listening = null;
   }

   private static boolean isModifier(int key) {
      return key == InputConstants.KEY_LCONTROL
         || key == InputConstants.KEY_RCONTROL
         || key == InputConstants.KEY_LSHIFT
         || key == InputConstants.KEY_RSHIFT
         || key == InputConstants.KEY_LALT
         || key == InputConstants.KEY_RALT
         || key == Keys.LEFT_SUPER
         || key == Keys.RIGHT_SUPER;
   }

   private record Slider(int x, int y0, int y1, int w, int steps, IntConsumer pick) {
      boolean has(double mx, double my) {
         return mx >= this.x - 4 && mx < this.x + this.w + 4 && my >= this.y0 && my < this.y1;
      }

      void apply(double mx) {
         double k = Math.clamp((mx - this.x) / (double)Math.max(1, this.w - 1), 0.0, 1.0);
         this.pick.accept((int)Math.round(k * (this.steps - 1)));
      }
   }
}
