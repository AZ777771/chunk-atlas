package dev.chunkatlas.minimap.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.gui.Glass;
import dev.chunkatlas.gui.MapMarkers;
import dev.chunkatlas.gui.MapScreen;
import dev.chunkatlas.gui.Ui;
import dev.chunkatlas.minimap.Minimap;
import dev.chunkatlas.minimap.waypoint.Waypoint;
import dev.chunkatlas.minimap.waypoint.Waypoints;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;

public final class WaypointsScreen extends AtlasScreen {
   private static final String K = "chunkatlas.waypoints.";
   private static final int PAD = 10;
   private static final int HEADER = 28;
   private static final int FOOTER = 30;
   private static final int ROW = 22;
   private static final int CONTROL = 20;
   private static final int SWATCH = 12;
   private static final int SWATCH_GAP = 2;
   private static final int MAX_W = 480;
   private static final int MAX_H = 300;
   private static final int RING = 11;

   private enum Sort {
      NEAREST,
      NAME,
      NEWEST;

      String label() {
         return Ui.t(K + "sort." + this.name().toLowerCase(Locale.ROOT));
      }
   }

   private static Sort sort = Sort.NEAREST;

   private final Minecraft mc = Minecraft.getInstance();
   private final Screen parent;

   private final MapScreen map;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private final List<Ui.Hit> menuHits = new ArrayList<>();
   private Waypoint selected;
   private boolean focusName;
   private boolean loading;
   private String search = "";
   private boolean sortOpen;
   private int scroll;
   private List<Waypoint> shown = List.of();

   private String note;
   private Waypoint deleted;

   private int x0;
   private int y0;
   private int w;
   private int h;
   private int listX;
   private int listY;
   private int listW;
   private int listH;
   private int rightX;
   private int rightY;
   private int rightW;
   private int sortX;
   private int sortW;
   private int swatchY;

   private EditBox searchBox;
   private EditBox name;
   private EditBox letter;
   private EditBox hex;
   private EditBox x;
   private EditBox y;
   private EditBox z;

   public WaypointsScreen(Screen parent) {
      this(parent, null);
   }

   public WaypointsScreen(Screen parent, Waypoint select) {
      super(Component.translatable(K + "title"));
      this.parent = parent;
      this.map = parent instanceof MapScreen m ? m : null;
      this.selected = select;
   }

   public static WaypointsScreen adding(Screen parent, int x, int y, int z, String dimension) {
      WaypointsScreen screen = new WaypointsScreen(parent, create(x, y, z, dimension));
      screen.focusName = true;
      return screen;
   }

   public static WaypointsScreen addingHere(Screen parent, Minecraft mc) {
      return adding(parent, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), Ids.of(mc.level.dimension()));
   }

   private static Waypoint create(int x, int y, int z, String dimension) {
      int n = Waypoints.all().size();
      Waypoint w = new Waypoint(Ui.t(K + "default_name", n + 1), x, y, z, dimension);
      w.initials = "";
      DyeColor[] dyes = DyeColor.values();
      w.colour = dyes[(n * 5 + 14) % dyes.length].getSerializedName();
      Waypoints.add(w);
      return w;
   }

   @Override
   protected void init() {
      this.w = Math.min(this.width - 16, MAX_W);
      this.h = Math.min(this.height - 16, MAX_H);
      this.x0 = (this.width - this.w) / 2;
      this.y0 = (this.height - this.h) / 2;
      int body = this.y0 + HEADER;
      int inner = this.w - 2 * PAD;
      this.rightW = Math.max(150, Math.min(188, inner * 2 / 5));
      this.listW = inner - this.rightW - 14;
      this.listX = this.x0 + PAD;
      this.rightX = this.listX + this.listW + 14;
      this.rightY = body;

      this.sortW = 0;
      for (Sort s : Sort.values()) {
         this.sortW = Math.max(this.sortW, this.font.width(s.label() + " ▾") + 14);
      }

      this.sortX = this.listX + this.listW - this.sortW;
      this.searchBox = new EditBox(this.font, this.listX, body, this.listW - this.sortW - 6, CONTROL, Component.translatable(K + "search"));
      this.searchBox.setHint(Component.translatable(K + "search"));
      this.searchBox.setValue(this.search);
      this.searchBox.setResponder(s -> {
         this.search = s;
         this.scroll = 0;
         this.refill();
      });
      this.addRenderableWidget(this.searchBox);
      this.listY = body + CONTROL + 4;
      this.listH = this.y0 + this.h - FOOTER - 6 - this.listY;

      int px = this.rightX;
      int pw = this.rightW;
      int y = body;
      this.letter = this.box(px, y, 26, "letter", 2);
      this.name = this.box(px + 30, y, pw - 30, "name", 32);
      y += CONTROL + 4;
      this.swatchY = y + 1;
      int swatchesW = 8 * (SWATCH + SWATCH_GAP) - SWATCH_GAP;
      this.hex = this.box(px + swatchesW + 6, y + 3, pw - swatchesW - 6, "hex", 7);
      y += 2 * (SWATCH + SWATCH_GAP) + 4;
      y += 9;
      int third = (pw - 8) / 3;
      this.x = this.box(px, y, third, "x", 9);
      this.y = this.box(px + third + 4, y, third, "y", 5);
      this.z = this.box(px + 2 * (third + 4), y, pw - 2 * (third + 4), "z", 9);

      this.refill();
      this.load();
      if (this.focusName) {
         this.focusName = false;
         this.setInitialFocus(this.name);
         this.name.moveCursorToEnd(false);
         this.name.setHighlightPos(0);
      } else {
         this.setInitialFocus(this.searchBox);
      }
   }

   private EditBox box(int x, int y, int w, String key, int max) {
      EditBox b = new EditBox(this.font, x, y, w, CONTROL, Component.translatable(K + key));
      b.setHint(Component.translatable(K + key));
      b.setMaxLength(max);
      b.setResponder(s -> this.changed());
      return this.addRenderableWidget(b);
   }

   private String here() {
      return this.mc.level == null ? "minecraft:overworld" : Ids.of(this.mc.level.dimension());
   }

   private Double distance(Waypoint w) {
      if (this.mc.player == null) {
         return null;
      }

      double[] p = w.placeIn(this.here());
      if (p == null) {
         return null;
      }

      return Math.hypot(p[0] - this.mc.player.getX(), p[2] - this.mc.player.getZ());
   }

   private static String metres(double m) {
      long n = Math.round(m);
      return n >= 1000L ? Ui.t(K + "km", String.format(Locale.ROOT, "%.1f", n / 1000.0)) : Ui.t(K + "metres", n);
   }

   private void refill() {
      String find = this.search.trim().toLowerCase(Locale.ROOT);
      List<Waypoint> list = new ArrayList<>();
      for (Waypoint w : Waypoints.all()) {
         if (find.isEmpty() || w.name.toLowerCase(Locale.ROOT).contains(find)) {
            list.add(w);
         }
      }

      switch (sort) {
         case NEAREST -> list.sort(Comparator.comparingDouble(w -> {
            Double d = this.distance(w);
            return d == null ? Double.MAX_VALUE : d;
         }));
         case NAME -> list.sort(Comparator.comparing(w -> w.name.toLowerCase(Locale.ROOT)));
         case NEWEST -> list.sort(Comparator.comparingLong((Waypoint w) -> w.created).reversed());
      }

      this.shown = list;
      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
   }

   private int maxScroll() {
      return Math.max(0, this.shown.size() * ROW - this.listH);
   }

   private void load() {
      this.loading = true;
      Waypoint w = this.selected;
      this.name.setValue(w == null ? "" : w.name);
      this.letter.setValue(w == null ? "" : w.initials);
      this.letter.setHint(Component.literal(w == null ? "" : Waypoint.initialsOf(w.name)));
      this.hex.setValue(w == null ? "" : String.format(Locale.ROOT, "#%06X", w.rgb()));
      this.x.setValue(w == null ? "" : Integer.toString(w.x));
      this.y.setValue(w == null ? "" : Integer.toString(w.y));
      this.z.setValue(w == null ? "" : Integer.toString(w.z));
      this.loading = false;
      for (EditBox b : new EditBox[]{this.name, this.letter, this.hex, this.x, this.y, this.z}) {
         b.setEditable(w != null);
      }
   }

   private void changed() {
      Waypoint w = this.selected;
      if (this.loading || w == null) {
         return;
      }

      w.name = this.name.getValue();
      w.initials = this.letter.getValue().trim().toUpperCase(Locale.ROOT);
      this.letter.setHint(Component.literal(Waypoint.initialsOf(w.name)));
      String hex = this.hex.getValue().trim();
      if (hex.matches("#?[0-9a-fA-F]{6}")) {
         w.colour = "#" + hex.replace("#", "").toUpperCase(Locale.ROOT);
      }

      w.x = number(this.x, w.x);
      w.y = number(this.y, w.y);
      w.z = number(this.z, w.z);
      Waypoints.save();
   }

   private static int number(EditBox box, int otherwise) {
      try {
         return Integer.parseInt(box.getValue().trim());
      } catch (NumberFormatException e) {
         return otherwise;
      }
   }

   private void select(Waypoint w) {
      this.selected = w;
      this.load();
      this.showOnMap(w);
   }

   private void showOnMap(Waypoint w) {
      if (this.map == null || w == null) {
         return;
      }

      int[] spot = this.freeSpot();
      this.map.centreOn(w.dimension, w.x + 0.5, w.z + 0.5, spot[0], spot[1]);
      String shown = this.map.shownDimension();
      if (shown != null && !shown.equals(w.dimension)) {
         double[] p = w.placeIn(shown);
         if (p != null) {
            this.map.centreOn(shown, p[0], p[2], spot[0], spot[1]);
         }
      }
   }

   private int[] freeSpot() {
      int left = this.x0;
      int right = this.width - (this.x0 + this.w);
      int top = this.y0;
      int bottom = this.height - (this.y0 + this.h);
      int best = Math.max(Math.max(left, right), Math.max(top, bottom));
      if (best < 40) {
         return new int[]{-1, -1};
      } else if (best == right) {
         return new int[]{this.x0 + this.w + right / 2, this.height / 2};
      } else if (best == left) {
         return new int[]{left / 2, this.height / 2};
      } else if (best == bottom) {
         return new int[]{this.width / 2, this.y0 + this.h + bottom / 2};
      } else {
         return new int[]{this.width / 2, top / 2};
      }
   }

   private boolean canTeleport(Waypoint w) {
      return w != null
         && w.dimension.equals(this.here())
         && this.mc.player != null
         && this.mc.player.connection != null
         && this.mc.player.connection.getCommands().getRoot().getChild("tp") != null;
   }

   private void teleport() {
      Waypoint w = this.selected;
      if (this.canTeleport(w)) {
         this.mc.player.connection.sendCommand("tp @s " + w.x + " " + w.y + " " + w.z);
         this.onClose();
      }
   }

   private void share() {
      if (this.selected != null && this.mc.player != null) {
         this.mc.player.connection.sendChat(Waypoints.shareText(this.selected));
         this.note = Ui.t(K + "shared", this.selected.name);
      }
   }

   private void delete() {
      Waypoint w = this.selected;
      if (w != null) {
         Waypoints.remove(w);
         this.deleted = w;
         this.note = Ui.t(K + "deleted", w.name);
         this.selected = null;
         this.load();
         this.refill();
      }
   }

   private void undoDelete() {
      Waypoint w = this.deleted;
      if (w != null) {
         Waypoints.add(w);
         this.deleted = null;
         this.note = null;
         this.refill();
         this.select(w);
      }
   }

   private void addHere() {
      if (this.mc.player != null && this.mc.level != null) {
         Waypoint w = create(this.mc.player.getBlockX(), this.mc.player.getBlockY(), this.mc.player.getBlockZ(), this.here());
         this.search = "";
         this.searchBox.setValue("");
         this.refill();
         this.select(w);
         this.setFocused(this.name);
         this.name.moveCursorToEnd(false);
         this.name.setHighlightPos(0);
         this.scrollTo(w);
      }
   }

   private void scrollTo(Waypoint w) {
      int i = this.shown.indexOf(w);
      if (i >= 0) {
         int top = i * ROW;
         if (top < this.scroll) {
            this.scroll = top;
         } else if (top + ROW > this.scroll + this.listH) {
            this.scroll = Math.min(this.maxScroll(), top + ROW - this.listH);
         }
      }
   }

   private void whereIStand() {
      Waypoint w = this.selected;
      if (w != null && this.mc.player != null) {
         w.x = this.mc.player.getBlockX();
         w.y = this.mc.player.getBlockY();
         w.z = this.mc.player.getBlockZ();
         w.dimension = this.here();
         Waypoints.save();
         this.load();
         this.showOnMap(w);
      }
   }

   private void importXaero() {
      int n = Waypoints.importXaero(this.mc);
      this.note = n < 0 ? Ui.t(K + "import_none") : Ui.t(K + "import_done", n);
      this.deleted = null;
      this.refill();
   }

   @Override
   public void tick() {
      if (sort == Sort.NEAREST && this.mc.player != null) {
         this.refill();
      }
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
         if (this.sortOpen) {
            this.sortOpen = false;
            for (int n = this.menuHits.size() - 1; n >= 0; n--) {
               if (this.menuHits.get(n).has(mouseX, mouseY)) {
                  this.menuHits.get(n).action().run();
                  break;
               }
            }

            return true;
         }

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
      if (Glass.over((int)mouseX, (int)mouseY, this.listX, this.listY, this.listW + 8, this.listH)) {
         this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)Math.round(scrollY * ROW)));
         return true;
      }

      return super.onScroll(mouseX, mouseY, scrollX, scrollY);
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (Keys.escape(keyCode)) {
         if (this.sortOpen) {
            this.sortOpen = false;
         } else {
            this.onClose();
         }

         return true;
      }

      if (super.onKey(keyCode, scanCode, modifiers)) {
         return true;
      }

      if (Keys.confirm(keyCode)) {
         this.onClose();
         return true;
      }

      return false;
   }

   @Override
   public void onClose() {
      Waypoints.save();
      Game.open(this.mc, this.parent);
   }

   @Override
   public boolean isPauseScreen() {
      return this.map != null;
   }

   @Override
   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      if (this.map == null) {
         super.drawBackground(g, mouseX, mouseY, partial);
         return;
      }

      this.map.drawUnder(g, partial);
      Waypoint w = this.selected;
      String shown = this.map.shownDimension();
      double[] p = w == null || shown == null ? null : w.placeIn(shown);
      double[] at = p == null ? null : this.map.screenOf(shown, p[0], p[2]);
      if (at != null) {
         int px = (int)Math.floor(at[0]);
         int py = (int)Math.floor(at[1]);
         MapMarkers.ring(g, px, py - MapMarkers.PIN_H / 2, RING, 0xFF000000);
         MapMarkers.ring(g, px, py - MapMarkers.PIN_H / 2, RING - 1, Glass.ACCENT);
         Double d = this.distance(w);
         if (d != null) {
            MapMarkers.tagCentred(g, this.font, metres(d), px, py + 9, Ui.WHITE);
         }
      }
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      this.hits.clear();
      boolean covered = this.sortOpen;
      int x1 = this.x0 + this.w;
      int y1 = this.y0 + this.h;
      Glass.panel(g, this.x0, this.y0, this.w, this.h);
      g.fill(this.x0, this.y0, x1, y1, 0x66000000);

      g.text(this.font, this.title, this.x0 + PAD, this.y0 + 9, Ui.WHITE);
      int n = Waypoints.all().size();
      if (n > 0) {
         String count = Ui.plural(K + "count", n);
         g.text(this.font, count, this.listX + this.listW - this.font.width(count), this.y0 + 9, Glass.NOTE);
      }

      g.fill(this.x0 + 1, this.y0 + HEADER - 6, x1 - 1, this.y0 + HEADER - 5, Glass.RULE);

      Glass.button(g, this.font, this.hits, this.sortX, this.rightY, this.sortW, CONTROL, sort.label() + " ▾", Ui.WHITE, mouseX, mouseY, () -> this.sortOpen = !this.sortOpen);
      this.drawList(g, mouseX, mouseY, covered);
      this.drawDetails(g, mouseX, mouseY, covered);

      g.fill(this.x0 + 1, y1 - FOOTER, x1 - 1, y1 - FOOTER + 1, Glass.RULE);
      int by = y1 - FOOTER + 5;
      String importText = Ui.t(K + "import_xaero");
      int importW = this.font.width(importText) + 16;
      Glass.button(g, this.font, this.hits, this.x0 + PAD, by, importW, CONTROL, importText, Ui.WHITE, mouseX, mouseY, covered ? null : this::importXaero);
      String done = Ui.t("gui.done");
      int doneW = Math.max(60, this.font.width(done) + 20);
      Glass.button(g, this.font, this.hits, x1 - PAD - doneW, by, doneW, CONTROL, done, Glass.ACCENT, mouseX, mouseY, covered ? null : this::onClose);
      String add = Ui.t(K + "new");
      int addW = this.font.width(add) + 16;
      int addX = x1 - PAD - doneW - 6 - addW;
      Glass.button(g, this.font, this.hits, addX, by, addW, CONTROL, add, Glass.GO, mouseX, mouseY, covered || this.mc.player == null ? null : this::addHere);
      if (this.note != null) {
         int nx = this.x0 + PAD + importW + 8;
         int room = addX - 8 - nx;
         if (this.deleted != null) {
            String undo = Ui.t(K + "undo");
            int undoW = this.font.width(undo) + 14;
            room -= undoW + 6;
            if (room > 30) {
               Glass.button(g, this.font, this.hits, nx + Math.min(room, this.font.width(this.note)) + 6, by, undoW, CONTROL, undo, Ui.WHITE, mouseX, mouseY, covered ? null : this::undoDelete);
            }
         }

         if (room > 30) {
            g.text(this.font, this.font.plainSubstrByWidth(this.note, room), nx, by + 6, Glass.LABEL);
         }
      }

      super.draw(g, mouseX, mouseY, partial);

      this.menuHits.clear();
      if (this.sortOpen) {
         List<Ui.Item> items = new ArrayList<>();
         for (Sort s : Sort.values()) {
            items.add(Ui.Item.radio(s.label(), s == sort, () -> {
               sort = s;
               this.scroll = 0;
               this.refill();
            }));
         }

         g.layer();
         Glass.menu(g, this.font, this.menuHits, items, this.sortX + this.sortW - Glass.menuWidth(this.font, items), this.rightY + CONTROL + 1, mouseX, mouseY);
      }
   }

   private void drawList(Gfx g, int mouseX, int mouseY, boolean covered) {
      int lx = this.listX;
      int ly = this.listY;
      int lw = this.listW;
      int lh = this.listH;
      if (this.shown.isEmpty()) {
         String first = Ui.t(Waypoints.all().isEmpty() ? K + "empty" : "chunkatlas.search.none");
         List<FormattedCharSequence> lines = new ArrayList<>();
         if (Waypoints.all().isEmpty()) {
            lines.addAll(this.font.split(Component.translatable(K + "empty.how", Minimap.keyName("new_waypoint").getString()), lw - 16));
         }

         int th = 9 + (lines.isEmpty() ? 0 : 4 + lines.size() * 10);
         int ty = ly + Math.max(10, (lh - th) / 3);
         g.centered(this.font, first, lx + lw / 2, ty, Glass.LABEL);
         ty += 13;
         for (FormattedCharSequence line : lines) {
            g.centered(this.font, line, lx + lw / 2, ty, Glass.NOTE);
            ty += 10;
         }

         return;
      }

      boolean inList = !covered && Glass.over(mouseX, mouseY, lx, ly, lw, lh);
      int first = Math.max(0, this.scroll / ROW);
      int last = Math.min(this.shown.size(), (this.scroll + lh) / ROW + 1);
      g.scissor(lx, ly, lx + lw, ly + lh);
      for (int i = first; i < last; i++) {
         Waypoint w = this.shown.get(i);
         int ry = ly + i * ROW - this.scroll;
         boolean picked = w == this.selected;
         boolean hover = inList && mouseY >= ry && mouseY < ry + ROW;
         if (picked) {
            g.fill(lx, ry, lx + lw, ry + ROW, Glass.HOT);
            g.fill(lx, ry, lx + 2, ry + ROW, Glass.ACCENT);
         } else if (hover) {
            g.fill(lx, ry, lx + lw, ry + ROW, Glass.TINT);
         }

         int alpha = w.visible ? 0xFF : 0x70;
         int text = w.visible ? Ui.WHITE : Glass.OFF;
         int soft = w.visible ? Glass.NOTE : Glass.OFF;
         MapMarkers.pin(g, this.font, lx + 10, ry + 18, w, alpha);

         int eyeX = lx + lw - 16;
         this.eye(g, eyeX, ry + 8, w.visible, hover && mouseX >= eyeX - 4);
         Double d = this.distance(w);
         String far = d == null ? "" : metres(d);
         int farW = this.font.width(far);
         g.text(this.font, far, eyeX - 6 - farW, ry + 7, soft);

         int tx = lx + 22;
         int room = eyeX - 6 - farW - 6 - tx;
         String shownName = this.font.width(w.name) <= room ? w.name : this.font.plainSubstrByWidth(w.name, Math.max(10, room - this.font.width("…"))) + "…";
         g.text(this.font, shownName, tx, ry + 3, text);
         String where = w.x + " " + w.y + " " + w.z;
         if (w.death) {
            String ago = Ui.ago(w.created / 1000L);
            if (!ago.isEmpty()) {
               where += " · " + ago;
            }
         } else if (!w.dimension.equals(this.here())) {
            where += " · " + Ui.dimension(w.dimension);
         }

         where = this.font.plainSubstrByWidth(where, Math.max(10, room - (w.type == Waypoint.Type.GLOBAL ? 11 : 0)));
         g.text(this.font, where, tx, ry + 12, soft);
         if (w.type == Waypoint.Type.GLOBAL) {
            this.globe(g, tx + this.font.width(where) + 4, ry + 12, soft);
         }

         if (!covered) {
            int top = Math.max(ry, ly);
            int bottom = Math.min(ry + ROW, ly + lh);
            if (bottom > top) {
               this.hits.add(new Ui.Hit(lx, top, eyeX - 4, bottom, () -> this.select(w)));
               this.hits.add(new Ui.Hit(eyeX - 4, top, lx + lw, bottom, () -> {
                  w.visible = !w.visible;
                  Waypoints.save();
               }));
            }
         }
      }

      g.unscissor();
      int max = this.maxScroll();
      if (max > 0) {
         int sx = lx + lw + 4;
         int knob = Math.max(12, lh * lh / Math.max(1, this.shown.size() * ROW));
         int at = ly + (lh - knob) * this.scroll / max;
         g.fill(sx, ly, sx + 2, ly + lh, 0x20FFFFFF);
         g.fill(sx, at, sx + 2, at + knob, 0x80FFFFFF);
      }
   }

   private void eye(Gfx g, int x, int y, boolean open, boolean hover) {
      int c = hover ? Ui.WHITE : (open ? Glass.SOFT : Glass.OFF);
      if (open) {
         g.fill(x + 2, y, x + 7, y + 1, c);
         g.fill(x + 1, y + 1, x + 2, y + 2, c);
         g.fill(x + 7, y + 1, x + 8, y + 2, c);
         g.fill(x, y + 2, x + 1, y + 3, c);
         g.fill(x + 8, y + 2, x + 9, y + 3, c);
         g.fill(x + 3, y + 1, x + 6, y + 4, c);
         g.fill(x + 1, y + 3, x + 2, y + 4, c);
         g.fill(x + 7, y + 3, x + 8, y + 4, c);
         g.fill(x + 2, y + 4, x + 7, y + 5, c);
      } else {
         g.fill(x, y + 2, x + 1, y + 3, c);
         g.fill(x + 8, y + 2, x + 9, y + 3, c);
         g.fill(x + 1, y + 3, x + 2, y + 4, c);
         g.fill(x + 7, y + 3, x + 8, y + 4, c);
         g.fill(x + 2, y + 4, x + 7, y + 5, c);
      }
   }

   private void globe(Gfx g, int x, int y, int c) {
      g.fill(x + 2, y, x + 5, y + 1, c);
      g.fill(x + 1, y + 1, x + 2, y + 2, c);
      g.fill(x + 3, y + 1, x + 4, y + 2, c);
      g.fill(x + 5, y + 1, x + 6, y + 2, c);
      g.fill(x, y + 2, x + 1, y + 5, c);
      g.fill(x + 3, y + 2, x + 4, y + 3, c);
      g.fill(x + 6, y + 2, x + 7, y + 5, c);
      g.fill(x, y + 3, x + 7, y + 4, c);
      g.fill(x + 3, y + 4, x + 4, y + 5, c);
      g.fill(x + 1, y + 5, x + 2, y + 6, c);
      g.fill(x + 3, y + 5, x + 4, y + 6, c);
      g.fill(x + 5, y + 5, x + 6, y + 6, c);
      g.fill(x + 2, y + 6, x + 5, y + 7, c);
   }

   private void drawDetails(Gfx g, int mouseX, int mouseY, boolean covered) {
      Waypoint w = this.selected;
      boolean any = w != null;
      int px = this.rightX;
      int pw = this.rightW;

      DyeColor[] dyes = DyeColor.values();
      int mine = any ? w.rgb() : -1;
      for (int i = 0; i < dyes.length; i++) {
         int sx = px + i % 8 * (SWATCH + SWATCH_GAP);
         int sy = this.swatchY + i / 8 * (SWATCH + SWATCH_GAP);
         int rgb = dyes[i].getTextureDiffuseColor() & 0xFFFFFF;
         boolean hover = any && !covered && Glass.over(mouseX, mouseY, sx, sy, SWATCH, SWATCH);
         g.fill(sx, sy, sx + SWATCH, sy + SWATCH, (any ? 0xFF000000 : 0x60000000) | rgb);
         g.outline(sx, sy, SWATCH, SWATCH, rgb == mine && any ? Ui.WHITE : (hover ? 0xC0FFFFFF : 0xFF000000));
         if (any && !covered) {
            DyeColor dye = dyes[i];
            this.hits.add(new Ui.Hit(sx, sy, sx + SWATCH, sy + SWATCH, () -> {
               w.colour = dye.getSerializedName();
               Waypoints.save();
               this.load();
            }));
         }
      }

      int y = this.swatchY - 1 + 2 * (SWATCH + SWATCH_GAP) + 4;
      int third = (pw - 8) / 3;
      g.text(this.font, Ui.t(K + "x"), px, y, Glass.NOTE);
      g.text(this.font, Ui.t(K + "y"), px + third + 4, y, Glass.NOTE);
      g.text(this.font, Ui.t(K + "z"), px + 2 * (third + 4), y, Glass.NOTE);
      y += 9 + CONTROL + 4;

      int room = this.y0 + this.h - FOOTER - 4 - y;
      int ctl = CONTROL;
      int gap = 8;
      int step = 14;
      int last = 16;
      if (room < 2 * ctl + gap + 2 * step + last) {
         gap = 4;
         step = 12;
         last = 13;
      }

      if (room < 2 * ctl + gap + 2 * step + last) {
         ctl = 16;
      }

      if (room < 2 * ctl + gap + 2 * step + last) {
         step = 10;
         last = 11;
      }

      Glass.button(g, this.font, this.hits, px, y, pw, ctl, Ui.t(K + "where_i_stand"), Ui.WHITE, mouseX, mouseY, any && !covered && this.mc.player != null ? this::whereIStand : null);
      y += ctl + gap;
      Glass.check(g, this.font, this.hits, px, y, Ui.t(K + "show_map"), any && w.visible, any && !covered ? () -> {
         w.visible = !w.visible;
         Waypoints.save();
      } : null);
      y += step;
      Glass.check(g, this.font, this.hits, px, y, Ui.t(K + "show_world"), any && w.inWorld, any && !covered ? () -> {
         w.inWorld = !w.inWorld;
         Waypoints.save();
      } : null);
      y += step;
      Glass.check(g, this.font, this.hits, px, y, Ui.t(K + "show_every"), any && w.type == Waypoint.Type.GLOBAL, any && !covered ? () -> {
         w.type = w.type == Waypoint.Type.GLOBAL ? Waypoint.Type.LOCAL : Waypoint.Type.GLOBAL;
         Waypoints.save();
      } : null);
      y += last;
      int bw = (pw - 8) / 3;
      Glass.button(g, this.font, this.hits, px, y, bw, ctl, Ui.t(K + "teleport"), Ui.WHITE, mouseX, mouseY, !covered && this.canTeleport(w) ? this::teleport : null);
      Glass.button(g, this.font, this.hits, px + bw + 4, y, bw, ctl, Ui.t(K + "share"), Ui.WHITE, mouseX, mouseY, any && !covered && this.mc.player != null ? this::share : null);
      Glass.button(
         g, this.font, this.hits, px + 2 * (bw + 4), y, pw - 2 * (bw + 4), ctl, Ui.t(K + "delete"), Glass.DANGER, mouseX, mouseY, any && !covered ? this::delete : null
      );
   }
}
