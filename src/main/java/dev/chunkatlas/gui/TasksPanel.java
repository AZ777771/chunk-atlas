package dev.chunkatlas.gui;

import dev.chunkatlas.TaskClient;
import dev.chunkatlas.engine.Area;
import dev.chunkatlas.engine.BorderBook;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.ChunkTasks;
import dev.chunkatlas.engine.TaskBook;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Input;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class TasksPanel {
   static final int W = 232;
   private static final int PAD = 8;
   private static final int ROW = 18;

   private static final int SEG_ROW = 24;
   private static final int BOX_H = 14;
   private static final int TASK_H = 36;
   private static final int ICON_BOX = 14;
   private static final int SHAPE_BOX = 17;
   private static final int GREY_BAR = 0xFF8A8A8A;
   private static final Area.Shape[] SHAPES = Area.Shape.values();
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final Font font;
   private final TasksPanel.Form task;
   private final TasksPanel.Form border;
   private final EditBox borderSize;
   private boolean open;

   private int tab;

   private int worldBorder;
   private boolean wrap;
   private int scroll;
   private int contentH;
   private int listH;
   private int x;
   private int y;
   private int h;

   private final class Form {
      Area.Shape shape = Area.Shape.CIRCLE;

      int centre;
      final EditBox cx = TasksPanel.this.box(true, "0");
      final EditBox cz = TasksPanel.this.box(true, "0");
      final EditBox r1;
      final EditBox r2;
      double[] selection;

      Form(String radius) {
         this.r1 = TasksPanel.this.box(false, radius);
         this.r2 = TasksPanel.this.box(false, radius);
      }

      List<EditBox> boxes() {
         List<EditBox> out = new ArrayList<>();
         if (this.centre == 3) {
            out.add(this.cx);
            out.add(this.cz);
         }

         out.add(this.r1);
         if (this.shape.twoRadii()) {
            out.add(this.r2);
         }

         return out;
      }
   }

   TasksPanel(MapScreen map, Font font) {
      this.map = map;
      this.font = font;
      this.task = new Form("2000");
      this.border = new Form("1000");
      this.borderSize = this.box(false, "5000");
   }

   private EditBox box(boolean signed, String value) {
      EditBox b = new EditBox(this.font, 0, 0, 40, BOX_H, Component.literal(""));
      b.setMaxLength(9);

      b.setResponder(text -> {
         String clean = text.replaceAll(signed ? "(?!^-)[^0-9]" : "[^0-9]", "");
         if (!clean.equals(text)) {
            b.setValue(clean);
         }
      });
      b.setValue(value);
      b.setBordered(true);
      return b;
   }

   boolean isOpen() {
      return this.open;
   }

   void toggle() {
      this.open = !this.open;
      if (!this.open) {
         this.unfocus();
      }
   }

   void close() {
      this.open = false;
      this.unfocus();
   }

   void open(int tab) {
      this.open = true;
      this.tab = Math.max(0, Math.min(1, tab));
   }

   private TasksPanel.Form form() {
      return this.tab == 0 ? this.task : this.border;
   }

   private static int number(EditBox box, int otherwise) {
      try {
         return Integer.parseInt(box.getValue().strip());
      } catch (NumberFormatException e) {
         return otherwise;
      }
   }

   private double[] centre(TasksPanel.Form f) {
      String dim = this.map.shownDimension();
      boolean here = this.mc.level != null && dim != null && dim.equals(Ids.of(this.mc.level.dimension()));
      return switch (f.centre) {
         case 0 -> here && this.mc.player != null ? new double[]{this.mc.player.getX(), this.mc.player.getZ()} : null;
         case 1 -> {
            if (!here) {
               yield null;
            }

            BlockPos s = Game.spawn(this.mc.level);
            yield new double[]{s.getX() + 0.5, s.getZ() + 0.5};
         }
         case 2 -> f.selection;
         default -> new double[]{number(f.cx, 0), number(f.cz, 0)};
      };
   }

   private double[] selectionMiddle() {
      String dim = this.map.shownDimension();
      ChunkSelection sel = dim == null ? null : this.map.selectionIn(dim);
      if (sel == null || sel.isEmpty()) {
         return null;
      }

      int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
      sel.forEach((cx, cz) -> {
         b[0] = Math.min(b[0], cx);
         b[1] = Math.min(b[1], cz);
         b[2] = Math.max(b[2], cx);
         b[3] = Math.max(b[3], cz);
      });
      return new double[]{(b[0] + b[2] + 1) * 8.0, (b[1] + b[3] + 1) * 8.0};
   }

   private Area area(TasksPanel.Form f) {
      double[] c = this.centre(f);
      int r1 = number(f.r1, 0);
      int r2 = f.shape.twoRadii() ? number(f.r2, 0) : r1;
      if (c == null || r1 < 1 || r2 < 1) {
         return null;
      }

      if (f == this.task && this.worldBorder == ChunkTasks.WorldBorderUse.FIT.ordinal()) {
         if (this.mc.level == null || !Ids.of(this.mc.level.dimension()).equals(this.map.shownDimension())) {
            return null;
         }

         var wb = this.mc.level.getWorldBorder();
         return Area.of(f.shape, wb.getCenterX(), wb.getCenterZ(), wb.getSize() / 2.0);
      }

      return new Area(f.shape, c[0], c[1], r1, r2);
   }

   Area preview() {
      return this.open ? this.area(this.form()) : null;
   }

   private void start() {
      String dim = this.map.shownDimension();
      Area a = this.area(this.task);
      if (dim == null || a == null || !TaskClient.usable()) {
         return;
      }

      TaskClient.add(dim, a, ChunkTasks.WorldBorderUse.of(this.worldBorder), number(this.borderSize, 0));
      this.unfocus();
      this.map.seedTell(Ui.t("chunkatlas.tasks.started"), Ui.GREY);
   }

   private void setBorder() {
      String dim = this.map.shownDimension();
      Area a = this.area(this.border);
      if (dim == null || a == null || !TaskClient.usable()) {
         return;
      }

      TaskClient.border(dim, new BorderBook.Border(a, this.wrap));
      this.unfocus();
   }

   private void useAsBorder(ChunkTasks.View t) {
      if (t.area() != null) {
         TaskClient.border(t.dimension(), new BorderBook.Border(t.area(), this.wrap));
         this.map.seedTell(Ui.t("chunkatlas.border.set", Ui.dimension(t.dimension())), Ui.GREY);
      }
   }

   boolean contains(double mx, double my) {
      return this.open && mx >= this.x && mx < this.x + W && my >= this.y && my < this.y + this.h;
   }

   private EditBox focused() {
      for (EditBox b : this.shownBoxes()) {
         if (b.isFocused()) {
            return b;
         }
      }

      return null;
   }

   boolean typing() {
      return this.open && this.focused() != null;
   }

   private List<EditBox> shownBoxes() {
      List<EditBox> out = this.form().boxes();
      if (this.tab == 0 && this.worldBorder == ChunkTasks.WorldBorderUse.SET.ordinal()) {
         out.add(this.borderSize);
      }

      return out;
   }

   private void unfocus() {
      for (TasksPanel.Form f : new TasksPanel.Form[]{this.task, this.border}) {
         f.cx.setFocused(false);
         f.cz.setFocused(false);
         f.r1.setFocused(false);
         f.r2.setFocused(false);
      }

      this.borderSize.setFocused(false);
   }

   boolean focusAt(double mouseX, double mouseY, int button, int mods) {
      if (!this.open) {
         return false;
      }

      boolean took = false;
      for (EditBox b : this.shownBoxes()) {
         if (b.visible && Glass.over((int)mouseX, (int)mouseY, b.getX(), b.getY(), b.getWidth(), b.getHeight())) {
            this.unfocus();
            b.setFocused(true);
            Input.click(b, mouseX, mouseY, button, mods, false);
            took = true;
         }
      }

      if (!took) {
         this.unfocus();
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
      } else if (Keys.confirm(key)) {
         if (this.tab == 0) {
            this.start();
         } else {
            this.setBorder();
         }
      } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) {
         List<EditBox> boxes = this.shownBoxes();
         int i = boxes.indexOf(box);
         box.setFocused(false);
         boxes.get(Math.floorMod(i + (Keys.shift(mods) ? -1 : 1), boxes.size())).setFocused(true);
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
      int room = Math.max(0, this.contentH - this.listH);
      this.scroll = Math.max(0, Math.min(room, this.scroll - (int)Math.round(dy * TASK_H / 2.0)));
   }

   List<Component> draw(Gfx g, List<Ui.Hit> hits, int left, int top, int bottom, int mouseX, int mouseY, boolean covered, float partial) {
      int mx = covered ? -1000 : mouseX;
      int my = covered ? -1000 : mouseY;
      boolean usable = TaskClient.usable();
      List<ChunkTasks.View> tasks = this.tab == 0 ? TaskClient.tasks() : List.of();
      TasksPanel.Form f = this.form();
      int rowsH = this.formHeight(f);
      int headH = 20;
      int formH = 14 + rowsH + 4 + 26;
      int borderLineH = this.tab == 1 ? 16 : 0;
      this.contentH = tasks.size() * TASK_H;
      int room = Math.max(0, bottom - top - headH - formH - borderLineH - (tasks.isEmpty() ? 0 : 5));
      this.listH = Math.min(this.contentH, Math.max(TASK_H, room - room % TASK_H));
      this.scroll = Math.max(0, Math.min(Math.max(0, this.contentH - this.listH), this.scroll));
      this.x = left;
      this.y = top;
      this.h = Math.min(bottom - top, headH + borderLineH + (tasks.isEmpty() ? 0 : this.listH + 5) + formH);
      Glass.panel(g, this.x, this.y, W, this.h);

      hits.add(new Ui.Hit(this.x, this.y, this.x + W, this.y + this.h, () -> {
      }));
      List<Component> tip = null;
      String[] tabs = {Ui.t("chunkatlas.tasks.title"), Ui.t("chunkatlas.border.title")};
      Glass.tabs(g, this.font, hits, this.x + PAD - 2, this.y + 5, tabs, this.tab, new boolean[]{true, true}, i -> {
         this.tab = i;
         this.unfocus();
         this.scroll = 0;
      }, mx, my, covered);
      int at = this.y + headH;
      if (this.tab == 1) {
         tip = pick(tip, this.drawCurrentBorder(g, hits, at, mx, my, usable));
         at += borderLineH;
      }

      if (!tasks.isEmpty()) {
         g.scissor(this.x, at, this.x + W, at + this.listH);
         int ty = at - this.scroll;
         for (ChunkTasks.View t : tasks) {
            if (ty + TASK_H > at && ty < at + this.listH) {
               tip = pick(tip, this.drawTask(g, hits, t, ty, at, at + this.listH, mx, my, usable));
            }

            ty += TASK_H;
         }

         g.unscissor();
         at += this.listH;
         g.fill(this.x + 1, at + 2, this.x + W - 1, at + 3, Glass.RULE);
         at += 5;
      }

      g.text(this.font, Ui.t(this.tab == 0 ? "chunkatlas.tasks.new" : "chunkatlas.border.new"), this.x + PAD, at + 4, Glass.NOTE, false);
      at += 14;
      tip = pick(tip, this.drawForm(g, hits, f, at, mx, my, partial, usable));
      at += rowsH + 4;
      tip = pick(tip, this.drawFooter(g, hits, f, at, mx, my, usable));
      return tip;
   }

   private static List<Component> pick(List<Component> had, List<Component> more) {
      return more != null ? more : had;
   }

   private List<Component> drawCurrentBorder(Gfx g, List<Ui.Hit> hits, int at, int mx, int my, boolean usable) {
      String dim = this.map.shownDimension();
      BorderBook.Border b = TaskClient.border(dim);
      if (b == null) {
         g.text(this.font, Ui.t("chunkatlas.border.none"), this.x + PAD, at + 3, Glass.LABEL, false);
         return null;
      }

      String text = describe(b.area()) + (b.wrap() ? " · " + Ui.t("chunkatlas.border.wraps") : "");
      int ix = this.x + W - PAD - ICON_BOX + 3;
      g.text(this.font, Ui.fit(this.font, text, ix - this.x - PAD - 4), this.x + PAD, at + 3, Ui.WHITE);
      boolean over = Glass.over(mx, my, ix, at, ICON_BOX, ICON_BOX);
      glyph(g, ix + 3, at + 3, 2, over ? 0xFFFF9A9A : Glass.DANGER);
      if (usable) {
         hits.add(new Ui.Hit(ix, at, ix + ICON_BOX, at + ICON_BOX, () -> TaskClient.border(dim, null)));
      }

      return over ? Glass.tip(Ui.t("chunkatlas.border.remove"), null) : null;
   }

   private List<Component> drawTask(Gfx g, List<Ui.Hit> hits, ChunkTasks.View t, int ty, int clipTop, int clipBottom, int mx, int my, boolean usable) {
      List<Component> tip = null;
      boolean paused = t.state() == TaskBook.State.PAUSED;
      boolean ended = t.state().ended();

      int ix = this.x + W - PAD - ICON_BOX + 3;
      int iy = ty + 3;
      boolean inside = my >= clipTop && my < clipBottom;
      int[][] icons = ended ? new int[0][] : t.area() != null ? new int[][]{{2}, {3}, {paused ? 1 : 0}} : new int[][]{{2}, {paused ? 1 : 0}};
      int nameRight = ix;
      for (int[] icon : icons) {
         int kind = icon[0];
         boolean over = inside && Glass.over(mx, my, ix, iy, ICON_BOX, ICON_BOX);
         int colour = kind == 2 ? (over ? 0xFFFF9A9A : Glass.DANGER) : over ? Ui.WHITE : 0xFFE2E2E2;
         glyph(g, ix + 3, iy + 3, kind, colour);
         if (usable && inside) {
            Runnable action = switch (kind) {
               case 0 -> () -> TaskClient.pause(t.id(), true);
               case 1 -> () -> TaskClient.pause(t.id(), false);
               case 2 -> () -> TaskClient.cancel(t.id());
               default -> () -> this.useAsBorder(t);
            };
            hits.add(new Ui.Hit(ix, Math.max(iy, clipTop), ix + ICON_BOX, Math.min(iy + ICON_BOX, clipBottom), action));
         }

         if (over) {
            tip = Glass.tip(Ui.t(switch (kind) {
               case 0 -> "chunkatlas.tasks.pause";
               case 1 -> "chunkatlas.tasks.resume";
               case 2 -> "chunkatlas.tasks.cancel";
               default -> "chunkatlas.tasks.use_as_border";
            }), null);
         }

         nameRight = ix;
         ix -= ICON_BOX;
      }

      String name = Ui.dimension(t.dimension()) + " · " + (t.area() == null ? Ui.t("chunkatlas.tasks.picked") : describe(t.area()));
      g.text(this.font, Ui.fit(this.font, name, nameRight - this.x - PAD - 4), this.x + PAD, ty + 6, Ui.WHITE);
      int by = ty + 19;
      int bw = W - 2 * PAD;
      g.fill(this.x + PAD, by, this.x + PAD + bw, by + 4, 0x1AFFFFFF);
      int filled = t.total() <= 0 ? bw : (int)((long)bw * Math.min(t.done(), t.total()) / t.total());
      g.fill(this.x + PAD, by, this.x + PAD + filled, by + 4, t.state() == TaskBook.State.RUNNING || t.state() == TaskBook.State.DONE ? Glass.ACCENT : GREY_BAR);
      g.text(this.font, Ui.fit(this.font, stats(t), bw), this.x + PAD, by + 7, Glass.LABEL, false);
      return tip;
   }

   static String stats(ChunkTasks.View t) {
      String counts = Ui.t("chunkatlas.generate.of", Ui.count(t.done()), Ui.count(t.total())) + " · " + t.percent() + "%";
      return switch (t.state()) {
         case PAUSED -> counts + " · " + Ui.t("chunkatlas.tasks.paused");
         case WAITING -> counts + " · " + Ui.t("chunkatlas.tasks.waiting");
         case DONE -> Ui.t("chunkatlas.tasks.done") + " · " + Ui.count(t.total());
         case CANCELLED -> Ui.t("chunkatlas.tasks.cancelled");
         default -> {
            String s = counts;
            if (t.rate() > 0.0) {
               s += " · " + Ui.t("chunkatlas.tasks.rate", Ui.count(Math.round(t.rate())));
            }

            if (t.secondsLeft() >= 0) {
               s += " · " + left(t.secondsLeft());
            }

            yield s;
         }
      };
   }

   static String left(long seconds) {
      long s = Math.max(1L, seconds);
      if (s < 90L) {
         return Ui.t("chunkatlas.tasks.left.seconds", s);
      }

      return s < 5400L ? Ui.t("chunkatlas.tasks.left.minutes", Math.round(s / 60.0)) : Ui.t("chunkatlas.tasks.left.hours", Math.round(s / 3600.0));
   }

   static String describe(Area a) {
      String r = a.shape().twoRadii() ? Ui.t("chunkatlas.tasks.radius2", Math.round(a.rx()), Math.round(a.rz())) : Ui.t("chunkatlas.tasks.radius1", Math.round(a.rx()));
      return Ui.t("chunkatlas.tasks.shape." + a.shape().id()) + " · " + Math.round(Math.floor(a.cx())) + ", " + Math.round(Math.floor(a.cz())) + " · " + r;
   }

   private int formHeight(TasksPanel.Form f) {
      int h = ROW + SEG_ROW + (f.centre == 3 ? ROW : 0) + ROW;
      if (this.tab == 0) {
         h += SEG_ROW + (this.worldBorder == ChunkTasks.WorldBorderUse.SET.ordinal() ? ROW : 0) + ROW;
      } else {
         h += ROW;
      }

      return h;
   }

   private List<Component> drawForm(Gfx g, List<Ui.Hit> hits, TasksPanel.Form f, int at, int mx, int my, float partial, boolean usable) {
      List<Component> tip = null;
      int left = this.x + PAD;
      int right = this.x + W - PAD;

      label(g, "chunkatlas.tasks.shape", left, at);
      int sx = right - SHAPES.length * SHAPE_BOX;
      for (int i = 0; i < SHAPES.length; i++) {
         Area.Shape s = SHAPES[i];
         int bx = sx + i * SHAPE_BOX;
         int by = at;
         boolean on = f.shape == s;
         boolean over = Glass.over(mx, my, bx, by, SHAPE_BOX, SHAPE_BOX);
         if (on) {
            g.fill(bx, by, bx + SHAPE_BOX, by + SHAPE_BOX, Glass.HOT);
            g.fill(bx + 2, by + SHAPE_BOX, bx + SHAPE_BOX - 2, by + SHAPE_BOX + 1, Glass.ACCENT);
         } else if (over) {
            g.fill(bx, by, bx + SHAPE_BOX, by + SHAPE_BOX, Glass.TINT);
         }

         int ix = bx + (SHAPE_BOX - TaskLayer.ICON) / 2;
         int iy = by + (SHAPE_BOX - TaskLayer.ICON) / 2;
         TaskLayer.icon(g, s, ix + 1, iy + 1, 0x73000000);
         TaskLayer.icon(g, s, ix, iy, Ui.WHITE);
         hits.add(new Ui.Hit(bx, by, bx + SHAPE_BOX, by + SHAPE_BOX, () -> f.shape = s));
         if (over) {
            tip = Glass.tip(Ui.t("chunkatlas.tasks.shape." + s.id()), null);
         }
      }

      at += ROW;

      label(g, "chunkatlas.tasks.centre", left, at + 2);
      String[] centres = {
         Ui.t("chunkatlas.tasks.centre.you"), Ui.t("chunkatlas.tasks.centre.spawn"), Ui.t("chunkatlas.tasks.centre.selection"), Ui.t("chunkatlas.tasks.centre.xz")
      };
      String dim = this.map.shownDimension();
      boolean here = this.mc.level != null && dim != null && dim.equals(Ids.of(this.mc.level.dimension()));
      double[] sel = this.selectionMiddle();
      boolean[] can = {here, here, sel != null, true};
      int over = Glass.segments(g, this.font, hits, right - Glass.segmentsWidth(this.font, centres), at, centres, f.centre, can, i -> {
         f.centre = i;
         if (i == 2) {
            f.selection = this.selectionMiddle();
         }
      }, mx, my, false);
      if (over >= 0 && !can[over]) {
         tip = Glass.tip(Ui.t(over == 2 ? "chunkatlas.tasks.centre.no_selection" : "chunkatlas.tasks.centre.elsewhere"), null);
      } else if (over == 2) {
         tip = Glass.tip(Ui.t("chunkatlas.tasks.centre.selection.tip"), null);
      }

      if (f.centre == 2 && f.selection == null) {
         f.selection = sel;
      }

      at += SEG_ROW;
      if (f.centre == 3) {
         label(g, "chunkatlas.tasks.xz", left, at);
         this.boxes(g, right, at, partial, mx, my, f.cx, f.cz, null);
         at += ROW;
      }

      label(g, "chunkatlas.tasks.radius", left, at);
      this.boxes(g, right, at, partial, mx, my, f.r1, f.shape.twoRadii() ? f.r2 : null, Ui.t("chunkatlas.tasks.blocks"));
      at += ROW;
      if (this.tab == 0) {
         label(g, "chunkatlas.tasks.world_border", left, at + 2);
         String[] uses = {Ui.t("chunkatlas.tasks.world_border.ignore"), Ui.t("chunkatlas.tasks.world_border.fit"), Ui.t("chunkatlas.tasks.world_border.set")};
         int o = Glass.segments(g, this.font, hits, right - Glass.segmentsWidth(this.font, uses), at, uses, this.worldBorder, new boolean[]{true, true, true}, i -> {
            this.worldBorder = i;
            this.unfocus();
         }, mx, my, false);
         if (o >= 0) {
            tip = Glass.tip(Ui.t("chunkatlas.tasks.world_border." + new String[]{"ignore", "fit", "set"}[o] + ".tip"), null);
         }

         at += SEG_ROW;
         if (this.worldBorder == ChunkTasks.WorldBorderUse.SET.ordinal()) {
            label(g, "chunkatlas.tasks.new_border", left, at);

            this.boxes(g, right, at, partial, mx, my, this.borderSize, null, Ui.t("chunkatlas.tasks.square_blocks", Ui.count(number(this.borderSize, 0))));
            at += ROW;
         }

         label(g, "chunkatlas.tasks.at_once", left, at);
         int n = TaskClient.atOnce();
         int bx = right - 15;
         int sy = at + 1;
         Glass.button(g, this.font, hits, bx, sy, 15, 15, "+", Ui.WHITE, mx, my, usable && n < TaskBook.MAX_AT_ONCE ? () -> TaskClient.atOnce(n + 1) : null);
         int vx = bx - 4 - 22;
         g.fill(vx, sy, vx + 22, sy + 15, 0xFFA0A0A0);
         g.fill(vx + 1, sy + 1, vx + 21, sy + 14, 0xFF000000);
         g.centered(this.font, Integer.toString(n), vx + 11, sy + 4, Ui.WHITE);
         Glass.button(g, this.font, hits, vx - 4 - 15, sy, 15, 15, "−", Ui.WHITE, mx, my, usable && n > 1 ? () -> TaskClient.atOnce(n - 1) : null);
         if (Glass.over(mx, my, this.x, at, W, ROW) && Glass.over(mx, my, this.x, at, vx - 4 - 15 - this.x, ROW)) {
            tip = Glass.tip(Ui.t("chunkatlas.tasks.at_once.tip"), null);
         }
      } else {
         Glass.check(g, this.font, hits, left, at + 4, Ui.t("chunkatlas.border.wrap"), this.wrap, () -> this.wrap = !this.wrap);
         if (Glass.over(mx, my, left, at, W - 2 * PAD, ROW)) {
            tip = Glass.tip(Ui.t("chunkatlas.border.wrap.tip"), null);
         }
      }

      return tip;
   }

   private void label(Gfx g, String key, int left, int at) {
      g.text(this.font, Ui.t(key), left, at + 5, Ui.WHITE);
   }

   private void boxes(Gfx g, int right, int at, float partial, int mx, int my, EditBox a, EditBox b, String after) {
      int bw = 40;
      int x = right;
      if (after != null) {
         x -= this.font.width(after);
         g.text(this.font, after, x, at + 5, Glass.LABEL, false);
         x -= 5;
      }

      EditBox[] order = b == null ? new EditBox[]{a} : new EditBox[]{b, a};
      for (int i = 0; i < order.length; i++) {
         x -= bw;
         EditBox box = order[i];
         box.setX(x);
         box.setY(at + 2);
         box.setWidth(bw);
         box.visible = true;
         g.widget(box, mx, my, partial);
         if (i == 0 && b != null) {
            x -= this.font.width("×") + 6;
            g.text(this.font, "×", x + 3, at + 5, Glass.LABEL, false);
         }
      }
   }

   private List<Component> drawFooter(Gfx g, List<Ui.Hit> hits, TasksPanel.Form f, int at, int mx, int my, boolean usable) {
      g.fill(this.x + 1, at, this.x + W - 1, at + 1, Glass.RULE);
      int by = at + 5;
      Area a = this.area(f);
      long n = a == null ? 0L : a.chunkCount();
      if (a != null) {
         g.text(this.font, Ui.plural("chunkatlas.tasks.count", n), this.x + PAD, by + 5, Glass.ACCENT);
      }

      String yes = Ui.t(this.tab == 0 ? "chunkatlas.tasks.start" : "chunkatlas.border.set_button");
      String no = Ui.t("chunkatlas.confirm.cancel");
      int yw = this.font.width(yes) + 16;
      int nw = this.font.width(no) + 16;
      int right = this.x + W - PAD;
      boolean ok = usable && a != null && n > 0;
      Glass.button(g, this.font, hits, right - yw, by, yw, 18, yes, Glass.ACCENT, mx, my, ok ? (this.tab == 0 ? this::start : this::setBorder) : null);
      Glass.button(g, this.font, hits, right - yw - 5 - nw, by, nw, 18, no, Ui.WHITE, mx, my, this::close);
      if (!usable && Glass.over(mx, my, right - yw, by, yw, 18)) {
         String why = TaskClient.blocked();
         return why == null ? null : Glass.tip(why, null);
      }

      return null;
   }

   static void glyph(Gfx g, int x, int y, int kind, int colour) {
      int shadow = 0x73000000;
      for (int pass = 0; pass < 2; pass++) {
         int c = pass == 0 ? shadow : colour;
         int o = pass == 0 ? 1 : 0;
         int gx = x + o;
         int gy = y + o;
         switch (kind) {
            case 0 -> {
               g.fill(gx + 1, gy + 1, gx + 3, gy + 7, c);
               g.fill(gx + 5, gy + 1, gx + 7, gy + 7, c);
            }
            case 1 -> {
               g.fill(gx + 2, gy + 1, gx + 3, gy + 7, c);
               g.fill(gx + 3, gy + 2, gx + 5, gy + 6, c);
               g.fill(gx + 5, gy + 3, gx + 6, gy + 5, c);
            }
            case 2 -> g.fill(gx + 1, gy + 1, gx + 7, gy + 7, c);
            default -> {
               g.fill(gx, gy, gx + 8, gy + 1, c);
               g.fill(gx, gy + 7, gx + 8, gy + 8, c);
               g.fill(gx, gy + 1, gx + 1, gy + 7, c);
               g.fill(gx + 7, gy + 1, gx + 8, gy + 7, c);
               g.fill(gx + 3, gy + 3, gx + 5, gy + 5, c);
            }
         }
      }
   }

   void tourSet(Area.Shape shape, int cx, int cz, int radius, int border, int borderSize) {
      this.open(0);
      this.task.shape = shape;
      this.task.centre = 3;
      this.task.cx.setValue(Integer.toString(cx));
      this.task.cz.setValue(Integer.toString(cz));
      this.task.r1.setValue(Integer.toString(radius));
      this.task.r2.setValue(Integer.toString(radius));
      this.worldBorder = border;
      this.borderSize.setValue(Integer.toString(borderSize));
   }

   void tourStart() {
      this.start();
   }

   void tourBorder(Area.Shape shape, int cx, int cz, int radius, boolean wrap) {
      this.open(1);
      this.border.shape = shape;
      this.border.centre = 3;
      this.border.cx.setValue(Integer.toString(cx));
      this.border.cz.setValue(Integer.toString(cz));
      this.border.r1.setValue(Integer.toString(radius));
      this.wrap = wrap;
      this.setBorder();
   }
}
