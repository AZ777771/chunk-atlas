package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.ChunkClips;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

final class CopyChunks {
   private CopyChunks() {
   }

   record Target(MapScreen map, Path home, Path world, String name, WorldLayout.Dimension dimension, int version) {
   }

   record Pick(CopyChunks.Target target, Screen sources) {
      void next(MapScreen source, Path world, String name, WorldLayout.Dimension dimension, ChunkSelection selection, int version) {
         source.dispose();
         ChunkClips.Clip from = new ChunkClips.Clip(name, world, name, dimension.id(), selection.size(), version, 0L);
         Game.open(Minecraft.getInstance(), this.target.map());
         this.target.map().startPaste(from, selection, dimension);
      }
   }

   static void start(CopyChunks.Target target) {
      Game.open(Minecraft.getInstance(), new CopyChunks.SourceScreen(target));
   }

   static final class SourceScreen extends AtlasScreen {
      private static final int CARD_W = 440;
      private static final int THUMB_W = 150;
      private static final int THUMB_H = 100;
      private static final int ROW = 24;
      private static final int HEADING = 14;
      private static final int LIST_H = 140;
      private static final int BUTTON_W = 110;
      private final Minecraft mc = Minecraft.getInstance();
      private final CopyChunks.Target target;
      private final List<Ui.Hit> hits = new ArrayList<>();
      private final List<ChunkClips.Clip> clips;
      private final Map<Path, ChunkSelection> shapes = new ConcurrentHashMap<>();
      private volatile List<LevelSummary> worlds;
      private volatile boolean failed;
      private EditBox search;
      private String typed = "";
      private int scroll;
      private ChunkClips.Clip clip;
      private LevelSummary world;

      SourceScreen(CopyChunks.Target target) {
         super(Component.translatable("chunkatlas.copy.source.title"));
         this.target = target;
         this.clips = ChunkClips.list(target.home());
         this.load();
      }

      private void load() {
         try {
            LevelStorageSource source = this.mc.getLevelSource();
            source.loadLevelSummaries(source.findLevelCandidates()).whenComplete((list, error) -> this.mc.execute(() -> {
               if (list == null) {
                  this.failed = true;
                  return;
               }

               List<LevelSummary> keep = new ArrayList<>();
               String own = this.target.world().getFileName() == null ? "" : this.target.world().getFileName().toString();
               for (LevelSummary s : list) {
                  if (!s.getLevelId().equals(own) && !s.isDisabled()) {
                     keep.add(s);
                  }
               }

               this.worlds = keep;
            }));
         } catch (Exception e) {
            this.failed = true;
         }
      }

      private int cardW() {
         return Math.min(this.width - 20, CARD_W);
      }

      private int listW() {
         return this.cardW() - THUMB_W - 48;
      }

      private int listH() {
         return Math.max(60, Math.min(LIST_H, this.height - 20 - (14 + 22 + 16 + 6 + 10 + 20 + 14)));
      }

      private int bodyH() {
         return Math.max(THUMB_H + 12 + 22, 16 + 6 + this.listH());
      }

      private int cardH() {
         return 14 + 22 + this.bodyH() + 10 + 20 + 14;
      }

      private int cardY() {
         return Math.max(10, (this.height - this.cardH()) / 2);
      }

      private int listX() {
         return (this.width - this.cardW()) / 2 + 16 + THUMB_W + 16;
      }

      @Override
      protected void init() {
         this.search = new EditBox(this.font, this.listX(), this.cardY() + 14 + 22, this.listW(), 16, Component.translatable("chunkatlas.copy.source.search"));
         this.search.setMaxLength(64);
         this.search.setHint(Component.translatable("chunkatlas.copy.source.search").withStyle(ChatFormatting.DARK_GRAY));
         this.search.setValue(this.typed);
         this.search.setResponder(text -> {
            this.typed = text;
            this.scroll = 0;
         });
         this.addRenderableWidget(this.search);
      }

      private boolean matches(String name) {
         String q = this.typed.trim().toLowerCase(Locale.ROOT);
         return q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q);
      }

      private List<ChunkClips.Clip> shownClips() {
         List<ChunkClips.Clip> out = new ArrayList<>();
         for (ChunkClips.Clip c : this.clips) {
            if (this.matches(c.name())) {
               out.add(c);
            }
         }

         return out;
      }

      private List<LevelSummary> shownWorlds() {
         List<LevelSummary> out = new ArrayList<>();
         for (LevelSummary s : this.worlds == null ? List.<LevelSummary>of() : this.worlds) {
            if (this.matches(s.getLevelName())) {
               out.add(s);
            }
         }

         return out;
      }

      private void choose(ChunkClips.Clip c) {
         this.clip = c;
         this.world = null;
         if (!this.shapes.containsKey(c.folder())) {
            Thread t = new Thread(() -> this.shapes.put(c.folder(), ChunkClips.chunks(c.folder())), "Chunk Atlas clip shape");
            t.setDaemon(true);
            t.start();
         }
      }

      private void choose(LevelSummary s) {
         this.world = s;
         this.clip = null;
      }

      private void place() {
         if (this.clip != null) {
            ChunkSelection chunks = this.shapes.get(this.clip.folder());
            if (chunks == null) {
               chunks = ChunkClips.chunks(this.clip.folder());
            }

            ChunkClips.Clip c = this.clip;
            Game.open(this.mc, this.target.map());
            this.target.map().startPaste(c, chunks, null);
         } else if (this.world != null) {
            this.openFolder(this.mc.getLevelSource().getLevelPath(this.world.getLevelId()));
         }
      }

      boolean tourOpenFirstClip() {
         if (this.clips.isEmpty()) {
            return false;
         }

         this.choose(this.clips.get(0));
         this.place();
         return true;
      }

      private void openFolder(Path folder) {
         MapScreen map = new MapScreen(this, folder);
         map.pick = new CopyChunks.Pick(this.target, this);
         Game.open(this.mc, map);
      }

      @Override
      public void onClose() {
         Game.open(this.mc, this.target.map());
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

         if (Keys.confirm(keyCode) && (this.clip != null || this.world != null)) {
            this.place();
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
                  if (doubleClick && (this.clip != null || this.world != null)) {
                     this.place();
                  }

                  return true;
               }
            }
         }

         return super.onClick(mouseX, mouseY, mouseButton, doubleClick);
      }

      private int rowsH(List<ChunkClips.Clip> clips, List<LevelSummary> worlds) {
         int h = 0;
         if (!clips.isEmpty()) {
            h += HEADING + clips.size() * ROW;
         }

         if (!worlds.isEmpty() || this.worlds == null || this.failed) {
            h += HEADING + Math.max(1, worlds.size()) * ROW;
         }

         return h;
      }

      @Override
      protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
         int max = Math.max(0, this.rowsH(this.shownClips(), this.shownWorlds()) - this.listH());
         this.scroll = Math.max(0, Math.min(max, this.scroll - (int)Math.round(scrollY * 16.0)));
         return true;
      }

      @Override
      protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
         super.drawBackground(g, mouseX, mouseY, partial);
      }

      @Override
      protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
         this.target.map().drawUnder(g, partial);
         g.fill(0, 0, this.width, this.height, 0x66000000);
         this.hits.clear();
         int cardW = this.cardW();
         int h = this.cardH();
         int cardX = (this.width - cardW) / 2;
         int y = this.cardY();
         Glass.panel(g, cardX, y, cardW, h);
         g.fill(cardX, y, cardX + cardW, y + h, 0x66000000);
         y += 14;
         g.centered(this.font, Component.literal(Ui.t("chunkatlas.copy.source.title")).withStyle(ChatFormatting.BOLD), this.width / 2, y, Ui.WHITE);
         y += 22;
         int left = cardX + 16;
         int top = y;

         ChunkSelection shape = this.clip == null ? null : this.shapes.get(this.clip.folder());
         Thumbnail.draw(g, null, left, y, THUMB_W, THUMB_H, shape, Glass.ACCENT, false);
         String under = this.clip != null
            ? Ui.t("chunkatlas.copy.source.then")
            : (this.world != null ? Ui.t("chunkatlas.copy.source.world_then") : Ui.t("chunkatlas.copy.source.pick"));
         int ty = y + THUMB_H + 4;
         for (FormattedCharSequence line : this.font.split(Component.literal(under), THUMB_W + 8)) {
            g.text(this.font, line, left, ty, Ui.GREY);
            ty += 11;
         }

         int lx = this.listX();
         int lw = this.listW();
         int ly = y + 16 + 6;
         int lh = this.listH();
         List<ChunkClips.Clip> clips = this.shownClips();
         List<LevelSummary> worlds = this.shownWorlds();
         int max = Math.max(0, this.rowsH(clips, worlds) - lh);
         this.scroll = Math.min(this.scroll, max);
         g.fill(lx, ly, lx + lw, ly + lh, 0x40000000);
         g.scissor(lx, ly, lx + lw, ly + lh);
         int ry = ly - this.scroll;
         boolean overList = Glass.over(mouseX, mouseY, lx, ly, lw, lh);
         if (clips.isEmpty() && worlds.isEmpty() && this.worlds != null && !this.failed) {
            String none = Ui.t(this.typed.isBlank() ? "chunkatlas.copy.source.none" : "chunkatlas.copy.source.no_match");
            g.centered(this.font, none, lx + lw / 2, ly + 10, Ui.GREY);
         }

         if (!clips.isEmpty()) {
            g.text(this.font, Ui.t("chunkatlas.copy.source.clips"), lx + 4, ry + 3, Glass.NOTE);
            ry += HEADING;
            for (ChunkClips.Clip c : clips) {
               boolean on = c == this.clip;
               String second = Ui.plural("chunkatlas.copy.source.clip", c.chunks(), c.sourceWorld(), Ui.dimension(c.dimension()), Ui.ago(c.createdMillis() / 1000L));
               this.row(g, lx, ry, lw, c.name(), second, on, false, overList && mouseY >= ry && mouseY < ry + ROW && Glass.over(mouseX, mouseY, lx, ly, lw, lh), () -> this.choose(c));
               ry += ROW;
            }
         }

         if (!worlds.isEmpty() || this.worlds == null || this.failed) {
            g.text(this.font, Ui.t("chunkatlas.copy.source.worlds"), lx + 4, ry + 3, Glass.NOTE);
            ry += HEADING;
            if (worlds.isEmpty()) {
               g.text(this.font, Ui.t(this.failed ? "chunkatlas.copy.source.failed" : "chunkatlas.copy.source.loading"), lx + 6, ry + 8, Ui.GREY);
               ry += ROW;
            }

            for (LevelSummary s : worlds) {
               boolean locked = s.isLocked();
               boolean on = s == this.world;
               String second = locked ? Ui.t("chunkatlas.copy.source.in_use") : s.getWorldVersionName().getString();
               this.row(
                  g, lx, ry, lw, s.getLevelName(), second, on, locked, overList && mouseY >= ry && mouseY < ry + ROW, locked ? null : () -> this.choose(s)
               );
               ry += ROW;
            }
         }

         g.unscissor();

         this.hits.removeIf(hit -> hit.y0() < ly || hit.y1() > ly + lh);
         if (max > 0) {
            int barH = Math.max(8, lh * lh / (lh + max));
            int barY = ly + (lh - barH) * this.scroll / max;
            g.fill(lx + lw - 2, barY, lx + lw, barY + barH, 0x80FFFFFF);
         }

         int by = top + this.bodyH() + 10;
         int right = cardX + cardW - 16;
         String yes = Ui.t("chunkatlas.copy.source.place");
         String no = Ui.t("chunkatlas.confirm.cancel");
         int yw = Math.max(BUTTON_W, this.font.width(yes) + 24);
         int nw = Math.max(80, this.font.width(no) + 24);
         boolean can = this.clip != null || this.world != null;
         Glass.button(g, this.font, this.hits, right - yw, by, yw, 20, yes, Glass.ACCENT, mouseX, mouseY, can ? this::place : null);
         Glass.button(g, this.font, this.hits, right - yw - 8 - nw, by, nw, 20, no, Ui.WHITE, mouseX, mouseY, this::onClose);
         super.draw(g, mouseX, mouseY, partial);
      }

      private void row(Gfx g, int x, int y, int w, String name, String second, boolean on, boolean off, boolean hover, Runnable action) {
         if (on) {
            g.fill(x, y, x + w, y + ROW - 1, Glass.HOT);
            g.fill(x, y, x + 2, y + ROW - 1, Glass.ACCENT);
         } else if (hover && action != null) {
            g.fill(x, y, x + w, y + ROW - 1, Glass.TINT);
         }

         g.text(this.font, this.font.plainSubstrByWidth(name, w - 12), x + 6, y + 3, off ? Glass.OFF : Ui.WHITE);
         g.text(this.font, this.font.plainSubstrByWidth(second, w - 12), x + 6, y + 13, off ? Ui.YELLOW : Glass.NOTE);
         if (action != null) {
            this.hits.add(new Ui.Hit(x, y, x + w, y + ROW, action));
         }
      }
   }
}
