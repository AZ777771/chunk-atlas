package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.SelectionFiles;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class SelectionsScreen extends AtlasScreen {
   private static final int TOP = 26;
   private static final int BOTTOM = 30;
   private static final int ITEM = 30;
   private static final int H = 20;
   private static final int GAP = 4;
   private final Minecraft mc = Minecraft.getInstance();
   private final Screen parent;
   private final Path dir;
   private final ChunkSelection current;
   private final Consumer<ChunkSelection> onLoad;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private List<SelectionFiles.Saved> saved = List.of();
   private EditBox name;
   private Button save;
   private String typed = "";
   private Path confirming;
   private String status;
   private int statusColour = Ui.GREY;
   private int scroll;
   private int listTop;
   private int listBottom;

   public SelectionsScreen(Screen parent, Path dir, ChunkSelection current, Consumer<ChunkSelection> onLoad) {
      super(Component.translatable("chunkatlas.selections.title"));
      this.parent = parent;
      this.dir = dir;
      this.current = current == null ? new ChunkSelection() : current;
      this.onLoad = onLoad;
      this.reload();
   }

   private void reload() {
      this.saved = SelectionFiles.list(this.dir);
      this.confirming = null;
   }

   @Override
   protected void init() {
      int w = this.contentWidth();
      int x0 = (this.width - w) / 2;
      int y = TOP + 2;
      String saveText = Ui.t("chunkatlas.selections.save");
      int saveW = Math.min(w / 2, this.font.width(saveText) + 20);
      this.name = new EditBox(this.font, x0, y, w - saveW - GAP, H, Component.translatable("chunkatlas.selections.name.narration"));
      this.name.setMaxLength(64);
      this.name.setValue(this.typed);
      this.name.setHint(Component.literal(this.defaultName()));
      this.name.setResponder(text -> this.typed = text);
      this.addRenderableWidget(this.name);
      this.save = this.addRenderableWidget(Button.builder(Component.literal(saveText), b -> this.save()).bounds(x0 + w - saveW, y, saveW, H).build());
      this.save.active = !this.current.isEmpty();
      if (this.current.isEmpty()) {
         this.save.setTooltip(Tooltip.create(Component.translatable("chunkatlas.selections.save.empty")));
      }

      this.listTop = y + H + 30;
      this.listBottom = this.height - BOTTOM - 2;
      int bw = Math.min(this.width - 2 * GAP, 200);
      int by = this.height - BOTTOM + 5;
      this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds((this.width - bw) / 2, by, bw, H).build());
      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
   }

   private int contentWidth() {
      return Math.max(120, Math.min(380, this.width - 16));
   }

   private int maxScroll() {
      return Math.max(0, this.saved.size() * ITEM - Math.max(0, this.listBottom - this.listTop));
   }

   private String defaultName() {
      return Ui.t("chunkatlas.selections.default_name", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm")));
   }

   private void save() {
      if (this.current.isEmpty()) {
         return;
      }

      String wanted = this.typed.isBlank() ? this.defaultName() : this.typed;
      String clean = SelectionFiles.cleanName(wanted);
      boolean replacing = Files.exists(this.dir.resolve(clean + SelectionFiles.EXTENSION));
      try {
         SelectionFiles.save(this.dir, wanted, this.current);
         this.say(Ui.plural(replacing ? "chunkatlas.selections.saved_replacing" : "chunkatlas.selections.saved", this.current.size(), clean), Ui.GREEN);
         this.typed = "";
         this.name.setValue("");
      } catch (IOException | RuntimeException e) {
         this.say(Ui.t("chunkatlas.selections.save_failed"), Ui.RED);
      }

      this.reload();
      this.scroll = 0;
   }

   private void load(SelectionFiles.Saved s) {
      ChunkSelection loaded;
      try {
         loaded = SelectionFiles.load(s.file());
      } catch (IOException | RuntimeException e) {
         this.say(Ui.t("chunkatlas.selections.load_failed", s.name()), Ui.RED);
         this.reload();
         return;
      }

      Game.open(this.mc, this.parent);
      this.onLoad.accept(loaded);
   }

   private void delete(SelectionFiles.Saved s) {
      try {
         SelectionFiles.delete(s.file());
         this.say(Ui.t("chunkatlas.selections.deleted", s.name()), Ui.GREY);
      } catch (IOException | RuntimeException e) {
         this.say(Ui.t("chunkatlas.selections.delete_failed", s.name()), Ui.RED);
      }

      this.reload();
      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
   }

   private void say(String text, int colour) {
      this.status = text;
      this.statusColour = colour;
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
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
   protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (mouseY >= this.listTop && mouseY < this.listBottom && this.maxScroll() > 0) {
         this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)Math.round(scrollY * 16.0)));
         return true;
      }

      return super.onScroll(mouseX, mouseY, scrollX, scrollY);
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
      this.hits.clear();
      int w = this.contentWidth();
      int x0 = (this.width - w) / 2;
      g.centered(this.font, this.title, this.width / 2, 9, Ui.WHITE);
      g.fill(0, TOP - 4, this.width, TOP - 3, 0x30FFFFFF);
      g.fill(0, this.height - BOTTOM, this.width, this.height - BOTTOM + 1, 0x30FFFFFF);
      int y = TOP + 2 + H + 6;
      String line = this.status != null
         ? this.status
         : (this.current.isEmpty() ? Ui.t("chunkatlas.selections.current.none") : Ui.plural("chunkatlas.selections.current", this.current.size()));
      g.text(this.font, this.font.plainSubstrByWidth(line, w), x0, y, this.status != null ? this.statusColour : Ui.GREY);
      g.fill(x0, this.listTop - 6, x0 + w, this.listTop - 5, 0x20FFFFFF);
      boolean inList = mouseY >= this.listTop && mouseY < this.listBottom;
      if (this.saved.isEmpty()) {
         int ty = this.listTop + 4;
         for (FormattedCharSequence part : this.font.split(Component.translatable("chunkatlas.selections.none"), w)) {
            if (ty + 10 > this.listBottom) {
               break;
            }

            g.text(this.font, part, x0, ty, Ui.DIM);
            ty += 10;
         }
      } else {
         g.scissor(0, this.listTop, this.width, this.listBottom);
         int ry = this.listTop - this.scroll;

         for (SelectionFiles.Saved s : this.saved) {
            if (ry + ITEM > this.listTop && ry < this.listBottom) {
               this.item(g, s, x0, ry, w, mouseX, mouseY, !inList);
            }

            ry += ITEM;
         }

         g.unscissor();

         this.hits.removeIf(h -> h.y0() < this.listTop || h.y1() > this.listBottom);
         if (this.maxScroll() > 0) {
            int track = this.listBottom - this.listTop;
            int knob = Math.max(12, track * track / Math.max(1, this.saved.size() * ITEM));
            int at = this.listTop + (track - knob) * this.scroll / Math.max(1, this.maxScroll());
            int sx = Math.min(this.width - 3, x0 + w + 4);
            g.fill(sx, this.listTop, sx + 2, this.listBottom, 0x20FFFFFF);
            g.fill(sx, at, sx + 2, at + knob, 0x80FFFFFF);
         }
      }

      super.draw(g, mouseX, mouseY, partial);
   }

   private void item(Gfx g, SelectionFiles.Saved s, int x, int y, int w, int mouseX, int mouseY, boolean covered) {
      boolean asking = s.file().equals(this.confirming);
      g.fill(x, y, x + w, y + ITEM - 3, asking ? 0x30FF6B6B : 0x14FFFFFF);
      String first;
      String second;
      int px = x + w - 4;
      int py = y + (ITEM - 3 - Ui.PILL_H) / 2;
      if (asking) {
         String yes = Ui.t("chunkatlas.selections.delete.yes");
         String no = Ui.t("chunkatlas.selections.delete.no");
         int yesW = Ui.pillWidth(this.font, yes);
         int noW = Ui.pillWidth(this.font, no);
         px -= yesW;
         Ui.pill(g, this.font, this.hits, px, py, yesW, yes, Ui.RED_BG, mouseX, mouseY, covered, () -> this.delete(s));
         px -= noW + GAP;
         Ui.pill(g, this.font, this.hits, px, py, noW, no, Ui.GREY_BG, mouseX, mouseY, covered, () -> this.confirming = null);
         first = s.name();
         second = Ui.t("chunkatlas.selections.delete.ask");
      } else {
         String load = Ui.t("chunkatlas.selections.load");
         String del = Ui.t("chunkatlas.selections.delete");
         int loadW = Ui.pillWidth(this.font, load);
         int delW = Ui.pillWidth(this.font, del);
         px -= delW;
         Ui.pill(g, this.font, this.hits, px, py, delW, del, Ui.GREY_BG, mouseX, mouseY, covered, () -> this.confirming = s.file());
         px -= loadW + GAP;
         Ui.pill(g, this.font, this.hits, px, py, loadW, load, Ui.BLUE_BG, mouseX, mouseY, covered, () -> this.load(s));
         first = s.name();
         String ago = Ui.ago(s.modifiedMillis() / 1000L);
         second = ago.isEmpty() ? Ui.chunks(s.chunks()) : Ui.t("chunkatlas.selections.item", Ui.chunks(s.chunks()), ago);
      }

      int room = Math.max(0, px - x - 12);
      g.text(this.font, this.font.plainSubstrByWidth(first, room), x + 6, y + 4, Ui.WHITE);
      g.text(this.font, this.font.plainSubstrByWidth(second, room), x + 6, y + 15, asking ? Ui.YELLOW : Ui.GREY);
   }
}
