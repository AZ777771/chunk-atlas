package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.ChunkFilter;
import dev.chunkatlas.engine.ChunkRule;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.RegionFacts;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.engine.WorldMap;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class ShrinkScreen extends AtlasScreen {
   private static final int[] PLAYED = {0, 1, 2, 5, 10, 15, 30, 60, 120};
   private static final int[] SPAWN = {0, 128, 256, 512, 1024, 2048, 4096};
   private static final int[] BORDER = {0, 1, 2, 4, 8, 16};

   private static final int AROUND_PLAYER = 8;
   private static final int SLIDER_ROW = 26;

   private final Minecraft mc = Minecraft.getInstance();
   private final Screen parent;
   private final MapScreen mapScreen;
   private final WorldMap map;
   private final WorldMap.Index index;
   private final ChunkSelection guard;
   private final Integer spawnX;
   private final Integer spawnZ;
   private final Integer playerX;
   private final Integer playerZ;
   private final long regionBytes;

   private final BiConsumer<ChunkSelection, Boolean> onDone;
   private final Map<Long, RegionFacts> facts = new ConcurrentHashMap<>();
   private final RuleList rules;
   private final ChunkFilter.Context ctx;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private final List<Ui.Hit> overlayHits = new ArrayList<>();
   private final ShrinkScreen.Slide[] slides;
   private volatile int loaded;
   private volatile boolean ready;
   private volatile boolean failed;
   private volatile boolean cancelled;

   private ChunkSelection kept = new ChunkSelection();
   private ChunkSelection deleted = new ChunkSelection();
   private long freed;
   private int generation;
   private int ticks;
   private int lastLoaded = -1;

   private ChunkSelection ruleKept = new ChunkSelection();
   private ShrinkScreen.Reading reading;
   private int scroll;
   private int content;
   private int viewTop;
   private int viewBottom;
   private int barX;
   private boolean draggingBar;
   private int dragGrab;
   private ShrinkScreen.Slide dragging;

   ShrinkScreen(
      Screen parent,
      WorldMap map,
      WorldMap.Index index,
      ChunkSelection guard,
      Integer spawnX,
      Integer spawnZ,
      Integer playerX,
      Integer playerZ,
      BiConsumer<ChunkSelection, Boolean> onDone
   ) {
      super(Component.translatable("chunkatlas.shrink.title"));
      this.parent = parent;
      this.mapScreen = parent instanceof MapScreen m ? m : null;
      this.map = map;
      this.index = index;
      this.guard = guard;
      this.spawnX = spawnX;
      this.spawnZ = spawnZ;
      this.playerX = playerX;
      this.playerZ = playerZ;
      this.onDone = onDone;
      long bytes = 0L;
      for (WorldLayout.RegionInfo r : index.regions().values()) {
         bytes += Math.max(0L, r.size());
      }

      this.regionBytes = bytes;
      int worldVersion = this.mapScreen == null ? 0 : this.mapScreen.findHost().worldVersion();
      this.ctx = new ChunkFilter.Context(
         spawnX == null ? null : spawnX.doubleValue(),
         spawnZ == null ? null : spawnZ.doubleValue(),
         playerX == null ? null : playerX.doubleValue(),
         playerZ == null ? null : playerZ.doubleValue(),
         worldVersion,
         System.currentTimeMillis() / 1000L
      );
      this.rules = new RuleList(this.font, spawnX != null, playerX != null, worldVersion, this::rulesChanged);
      this.rules.load(ChunkFilter.empty());
      this.slides = new ShrinkScreen.Slide[]{
         new ShrinkScreen.Slide("chunkatlas.shrink.played", PLAYED, 2, "minutes"),
         new ShrinkScreen.Slide("chunkatlas.shrink.within_spawn", SPAWN, spawnX == null ? 0 : 3, "blocks"),
         new ShrinkScreen.Slide("chunkatlas.shrink.border", BORDER, 4, "chunks")
      };
      this.load();
   }

   private void load() {
      Thread t = new Thread(() -> {
         ChunkRule collect = new ChunkRule() {
            @Override
            public boolean matches(int x, int z, RegionFacts f, int i) {
               ShrinkScreen.this.facts.putIfAbsent(WorldLayout.key(x >> 5, z >> 5), f);
               return false;
            }

            @Override
            public boolean needsFacts() {
               return true;
            }
         };

         try {
            ChunkSelection done = this.map.select(this.index, collect, n -> this.loaded = n, () -> this.cancelled);
            this.failed = done == null && !this.cancelled;
         } catch (RuntimeException e) {
            this.failed = true;
         }

         this.mc.execute(() -> {
            if (!this.cancelled && !this.failed) {
               this.ready = true;
               this.work();
            }
         });
      }, "Chunk Atlas shrink");
      t.setDaemon(true);
      t.setPriority(2);
      t.start();
   }

   private void work() {
      int gen = ++this.generation;
      long playedTicks = this.slides[0].value() * 60L * 20L;
      int spawnRadius = this.spawnX == null ? 0 : this.slides[1].value();
      int border = this.slides[2].value();
      ChunkFilter filter = this.rules.filter(0);
      ChunkRule quick = filter.isEmpty() ? null : filter.toRule(this.ctx);
      ChunkSelection fromRules = this.ruleKept.copy();
      ChunkSelection guard = this.guard;
      WorldMap.Index index = this.index;
      RulePanel.WORKER.execute(() -> {
         ChunkSelection keep = new ChunkSelection();
         ChunkSelection present = new ChunkSelection();
         for (Map.Entry<Long, RegionFacts> e : this.facts.entrySet()) {
            int rx = (int)(e.getKey() >> 32);
            int rz = (int)e.getKey().longValue();
            RegionFacts f = e.getValue();
            for (int i = 0; i < 1024; i++) {
               if (f.state[i] != RegionFacts.FULL && f.state[i] != RegionFacts.PARTIAL) {
                  continue;
               }

               int cx = (rx << 5) + (i & 31);
               int cz = (rz << 5) + (i >> 5);
               present.add(cx, cz);
               if (playedTicks > 0L && f.inhabited[i] >= playedTicks
                  || quick != null && quick.matches(cx, cz, f, i)
                  || fromRules.contains(cx, cz)
                  || guard.contains(cx, cz)) {
                  keep.add(cx, cz);
               }
            }
         }

         if (spawnRadius > 0) {
            addCircle(keep, this.spawnX, this.spawnZ, spawnRadius, index);
         }

         if (this.playerX != null) {
            addCircle(keep, this.playerX, this.playerZ, AROUND_PLAYER * 16, index);
         }

         ChunkSelection grown = RulePanel.grow(keep, border, index);
         ChunkSelection gone = new ChunkSelection();
         long[] kb = {0L};
         boolean[] sized = {false};
         present.forEach((x, z) -> {
            if (!grown.contains(x, z)) {
               gone.add(x, z);
               RegionFacts f = this.facts.get(WorldLayout.key(x >> 5, z >> 5));
               if (f != null) {
                  int n = f.kb[ChunkSelection.index(x, z)];
                  kb[0] += n;
                  sized[0] |= n > 0;
               }
            }
         });
         long total = Math.max(1L, index.chunks());
         long bytes = sized[0] ? kb[0] * 1024L : this.regionBytes / total * gone.size();
         this.mc.execute(() -> {
            if (gen == this.generation && Game.screen(this.mc) == this) {
               this.kept = grown;
               this.deleted = gone;
               this.freed = bytes;
               if (this.mapScreen != null) {
                  this.mapScreen.preview(grown, MapScreen.KEPT);
                  this.mapScreen.previewAlso(gone, MapScreen.DIMMED);
               }
            }
         });
      });
   }

   private static void addCircle(ChunkSelection into, int blockX, int blockZ, int radius, WorldMap.Index index) {
      int r = (radius + 15) / 16;
      int cx = Math.floorDiv(blockX, 16);
      int cz = Math.floorDiv(blockZ, 16);
      for (int dz = -r; dz <= r; dz++) {
         for (int dx = -r; dx <= r; dx++) {
            if (dx * dx + dz * dz <= r * r && index.has(cx + dx, cz + dz)) {
               into.add(cx + dx, cz + dz);
            }
         }
      }
   }

   private void rulesChanged() {
      ChunkFilter filter = this.rules.filter(0);
      if (this.reading != null) {
         this.reading.cancelled = true;
         this.reading = null;
      }

      if (!filter.deep()) {
         this.ruleKept = new ChunkSelection();
         this.work();
         return;
      }

      ShrinkScreen.Reading job = new ShrinkScreen.Reading();
      this.reading = job;
      ChunkFilter.Search search = filter.toSearch(this.ctx);
      Thread t = new Thread(() -> {
         WorldMap.Found found;
         try {
            found = this.map.find(this.index, search, new WorldMap.Progress() {
               @Override
               public void stage(boolean chunks, int total) {
                  job.done = 0;
                  job.total = total;
                  job.chunks = chunks;
               }

               @Override
               public void done(int n) {
                  job.done = n;
               }
            }, () -> job.cancelled || this.cancelled);
         } catch (RuntimeException e) {
            found = null;
         }

         WorldMap.Found f = found;
         this.mc.execute(() -> {
            if (this.reading == job) {
               this.reading = null;
               this.ruleKept = f == null ? new ChunkSelection() : f.chunks();
               this.work();
            }
         });
      }, "Chunk Atlas shrink rules");
      t.setDaemon(true);
      t.start();
   }

   @Override
   protected void init() {
      this.rules.build(this::addRenderableWidget);
   }

   @Override
   public void tick() {
      if (this.rules.dirty) {
         this.rules.dirty = false;
         this.rebuildWidgets();
      }

      if (!this.ready && ++this.ticks % 20 == 0 && this.loaded != this.lastLoaded) {
         this.lastLoaded = this.loaded;
         this.work();
      }
   }

   private boolean busy() {
      return !this.ready || this.reading != null;
   }

   boolean tourReady() {
      return this.ready && this.reading == null;
   }

   String tourResult() {
      return this.deleted.size() + " chosen of " + this.index.chunks() + " (loaded " + this.facts.size() + " regions, keeps " + this.kept.size() + ")";
   }

   void tourFinish(boolean deleteNow) {
      this.finish(deleteNow);
   }

   private void finish(boolean deleteNow) {
      if (!this.busy() && !this.deleted.isEmpty()) {
         Game.open(this.mc, this.parent);
         this.onDone.accept(this.deleted, deleteNow);
      }
   }

   @Override
   public void onClose() {
      this.cancelled = true;
      Game.open(this.mc, this.parent);
   }

   @Override
   public void removed() {
      if (this.reading != null) {
         this.reading.cancelled = true;
      }

      if (this.mapScreen != null) {
         this.mapScreen.clearPreview();
      }
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }

   @Override
   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      if (this.mapScreen == null) {
         super.drawBackground(g, mouseX, mouseY, partial);
      }
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      if (this.mapScreen != null) {
         this.mapScreen.drawUnder(g, partial);
      }

      this.hits.clear();
      this.overlayHits.clear();
      boolean covered = this.rules.overlayOpen();
      int mx = covered ? -1000 : mouseX;
      int my = covered ? -1000 : mouseY;
      int[] b = RulePanel.bounds(this.width, this.height);
      int body = RulePanel.frame(g, this.font, b, Ui.t("chunkatlas.shrink.title"), null);

      int lx = RulePanel.leftX(b);
      int lw = RulePanel.leftW(b);
      int hy = body + 5;
      RulePanel.heading(g, this.font, lx, hy, Ui.t("chunkatlas.shrink.keep"));
      this.viewTop = hy + 14;
      this.viewBottom = RulePanel.footerTop(b) - 4;
      int view = this.viewBottom - this.viewTop;
      int listW = lw - RulePanel.BAR_W - 4;
      int slidersH = this.slides.length * SLIDER_ROW + 14;
      int rulesY = this.viewTop - this.scroll + slidersH;
      this.content = slidersH + this.rules.layout(lx, rulesY, listW, this.viewTop, this.viewBottom);
      int max = Math.max(0, this.content - view);
      if (this.scroll > max) {
         this.scroll = max;
         rulesY = this.viewTop - this.scroll + slidersH;
         this.content = slidersH + this.rules.layout(lx, rulesY, listW, this.viewTop, this.viewBottom);
      }

      g.scissor(lx, this.viewTop, lx + lw, this.viewBottom);
      int sy = this.viewTop - this.scroll;
      for (ShrinkScreen.Slide s : this.slides) {
         boolean shown = sy >= this.viewTop && sy + SLIDER_ROW - 4 <= this.viewBottom;
         s.box = shown ? new int[]{lx, sy + 11, listW, 12} : null;
         boolean hot = this.dragging == s || (shown && Glass.over(mx, my, lx, sy + 9, listW, 14));
         String value = Ui.amount(s.value(), s.unit);
         g.text(this.font, Ui.fit(this.font, Ui.t(s.key), listW - this.font.width(value) - 6), lx, sy, Ui.WHITE);
         g.text(this.font, value, lx + listW - this.font.width(value), sy, s.at == 0 ? Glass.LABEL : Glass.ACCENT);
         RulePanel.slider(g, lx, sy + 11, listW, 12, s.fraction(), hot);
         sy += SLIDER_ROW;
      }

      g.text(this.font, Ui.t("chunkatlas.shrink.and_rules"), lx, sy + 2, Glass.LABEL);
      this.rules.draw(g, this.hits, mx, my);
      g.unscissor();
      if (max > 0) {
         RulePanel.fades(g, lx, this.viewTop, listW, view, this.scroll > 0, this.scroll < max);
         this.barX = lx + lw - RulePanel.BAR_W;
         RulePanel.scrollbar(g, this.barX, this.viewTop, view, this.content, this.scroll, this.draggingBar || Glass.over(mx, my, this.barX - 2, this.viewTop, RulePanel.BAR_W + 4, view));
      }

      int rx = RulePanel.rightX(b);
      int ry = body + 5;
      for (FormattedCharSequence line : this.font.split(Component.translatable("chunkatlas.shrink.always_kept"), RulePanel.RIGHT_W)) {
         g.text(this.font, line, rx, ry, Glass.LABEL);
         ry += 10;
      }

      ry += 8;
      RulePanel.heading(g, this.font, rx, ry, Ui.t("chunkatlas.shrink.chunks"));
      ry += 13;
      boolean known = this.ready || this.loaded > 0;
      this.fact(g, rx, ry, Ui.t("chunkatlas.shrink.keeps"), known ? Ui.count(this.kept.size()) : "–", Glass.GO);
      ry += 12;
      this.fact(g, rx, ry, Ui.t("chunkatlas.shrink.deletes"), known ? Ui.count(this.deleted.size()) : "–", Glass.DANGER);
      ry += 12;
      this.fact(g, rx, ry, Ui.t("chunkatlas.shrink.frees"), known && !this.deleted.isEmpty() ? Ui.size(this.freed) : "–", Ui.WHITE);

      int fy = RulePanel.footerY(b);
      String deleteText = Ui.t("chunkatlas.shrink.delete");
      String selectText = Ui.t("chunkatlas.shrink.select_rest");
      String cancelText = Ui.t("chunkatlas.filter.cancel");
      int dw = RulePanel.buttonWidth(this.font, deleteText);
      int sw = RulePanel.buttonWidth(this.font, selectText);
      int cw = RulePanel.buttonWidth(this.font, cancelText);
      int dx = b[0] + b[2] - RulePanel.PAD - dw;
      int sx = dx - 6 - sw;
      int cx = sx - 6 - cw;
      boolean can = !this.busy() && !this.deleted.isEmpty();
      Glass.button(g, this.font, this.hits, dx, fy, dw, RulePanel.CTRL, deleteText, Glass.DANGER, mx, my, can ? () -> this.finish(true) : null);
      Glass.button(g, this.font, this.hits, sx, fy, sw, RulePanel.CTRL, selectText, Ui.WHITE, mx, my, can ? () -> this.finish(false) : null);
      Glass.button(g, this.font, this.hits, cx, fy, cw, RulePanel.CTRL, cancelText, Ui.WHITE, mx, my, this::onClose);
      int statusW = cx - 8 - lx;
      int regions = this.index.regions().size();
      String status;
      int colour = Glass.LABEL;
      double fraction = -1.0;
      ShrinkScreen.Reading job = this.reading;
      if (this.failed) {
         status = Ui.t("chunkatlas.map.chunks_unreadable");
         colour = Glass.WARNING;
      } else if (!this.ready) {
         status = Ui.t("chunkatlas.shrink.reading_short", Ui.count(this.loaded), Ui.count(regions));
         fraction = regions == 0 ? 0.0 : this.loaded / (double)regions;
      } else if (job != null) {
         status = Ui.t(job.chunks ? "chunkatlas.rules.reading" : "chunkatlas.map.pick.filter.looking");
         fraction = job.total == 0 ? 0.0 : job.done / (double)job.total;
      } else {
         String note = this.rules.note();
         status = note != null ? note : "";
         colour = note != null ? Glass.WARNING : Glass.LABEL;
      }

      int ty = fraction < 0.0 ? fy + (RulePanel.CTRL - 8) / 2 : fy;
      g.text(this.font, Ui.fit(this.font, status, statusW), lx, ty, colour);
      if (fraction >= 0.0) {
         RulePanel.progress(g, lx, fy + 12, Math.min(statusW, 160), fraction);
      }

      super.draw(g, mouseX, mouseY, partial);
      this.rules.drawOverlays(g, this.overlayHits, mouseX, mouseY, this.width, this.height);
   }

   private void fact(Gfx g, int x, int y, String label, String value, int colour) {
      g.text(this.font, label, x, y, Glass.LABEL);
      g.text(this.font, value, x + RulePanel.RIGHT_W - this.font.width(value), y, colour);
   }

   private int maxScroll() {
      return Math.max(0, this.content - (this.viewBottom - this.viewTop));
   }

   private void slideTo(ShrinkScreen.Slide s, double x) {
      if (s.box == null) {
         return;
      }

      double along = (x - s.box[0] - 2) / Math.max(1, s.box[2] - 4);
      int at = (int)Math.round(Math.max(0.0, Math.min(1.0, along)) * (s.steps.length - 1));
      if (at != s.at) {
         s.at = at;
         this.work();
      }
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      double x = mouseX;
      double y = mouseY;
      if (this.rules.mouseClicked(x, y, this.overlayHits)) {
         return true;
      }

      if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
         for (ShrinkScreen.Slide s : this.slides) {
            if (s.box != null && x >= s.box[0] - 2 && x < s.box[0] + s.box[2] + 2 && y >= s.box[1] - 2 && y < s.box[1] + s.box[3] + 2) {
               this.clearFocus();
               this.dragging = s;
               this.slideTo(s, x);
               return true;
            }
         }

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
      if (this.dragging != null) {
         this.slideTo(this.dragging, mouseX);
         return true;
      }

      if (this.draggingBar) {
         this.barTo(mouseY);
         return true;
      }

      return super.onDrag(mouseX, mouseY, mouseButton, dx, dy);
   }

   @Override
   protected boolean onRelease(double mouseX, double mouseY, int mouseButton) {
      this.dragging = null;
      this.draggingBar = false;
      return super.onRelease(mouseX, mouseY, mouseButton);
   }

   @Override
   protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
      int[] b = RulePanel.bounds(this.width, this.height);
      if (Glass.over((int)mouseX, (int)mouseY, b[0], b[1], b[2], b[3]) && this.maxScroll() > 0 && !this.rules.overlayOpen()) {
         this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)Math.round(scrollY * 16.0)));
      }

      return true;
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (MapScreen.tourDeaf) {
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
         this.finish(false);
         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
   }

   private static final class Slide {
      final String key;
      final int[] steps;
      final String unit;
      int at;
      int[] box;

      Slide(String key, int[] steps, int at, String unit) {
         this.key = key;
         this.steps = steps;
         this.unit = unit;
         this.at = Math.max(0, Math.min(steps.length - 1, at));
      }

      int value() {
         return this.steps[this.at];
      }

      double fraction() {
         return this.steps.length <= 1 ? 0.0 : this.at / (double)(this.steps.length - 1);
      }
   }

   private static final class Reading {
      volatile int done;
      volatile int total;
      volatile boolean chunks;
      volatile boolean cancelled;
   }
}
