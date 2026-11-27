package dev.chunkatlas.gui;

import dev.chunkatlas.port.Gfx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public final class Glass {
   public static final int PANEL = 0x99000000;
   public static final int EDGE = 0x66000000;
   public static final int CHIP = 0x8C000000;
   public static final int MENU = 0xD1000000;
   public static final int MENU_EDGE = 0x80000000;
   public static final int TINT = 0x24FFFFFF;
   public static final int HOT = 0x29FFFFFF;
   public static final int RULE = 0x2EFFFFFF;
   public static final int ACCENT = 0xFF8FB8FF;
   public static final int DESELECT = 0xFFFF6B6B;
   public static final int WARNING = 0xFFFFD84A;
   public static final int DANGER = 0xFFFF7A7A;
   public static final int LABEL = 0xFFA8A8A8;
   public static final int NOTE = 0xFF8A8A8A;
   public static final int SOFT = 0xFFB8B8B8;

   public static final int OFF = 0xFF858585;
   public static final int QUIET = 0xFFB0B0B0;
   public static final int TILE = 20;
   public static final int ICON = 16;
   public static final int CHIP_H = 12;

   public static final int INSET = 3;
   public static final int GAP = 1;

   public static final int MARGIN = 4;
   private static final int SEGMENT_H = 16;
   private static final int ROW_H = 14;
   private static final int HEADING_H = 12;
   private static final int RULE_H = 5;

   private Glass() {
   }

   public static String icon(String name) {
      return "chunkatlas:icon/ui/" + name;
   }

   public static List<Component> tip(String label, String key) {
      Component line = Component.literal(label);
      if (key != null && !key.isEmpty()) {
         line = line.copy().append(Component.literal("  " + key).withStyle(ChatFormatting.GRAY));
      }

      return List.of(line);
   }

   public static List<Component> tip2(String first, String second) {
      return List.of(Component.literal(first), Component.literal(second).withStyle(ChatFormatting.GRAY));
   }

   public static void panel(Gfx g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, PANEL);
      g.outline(x, y, w, h, EDGE);
   }

   public static boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
      return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
   }

   public static int chipWidth(Font font, String text) {
      return font.width(text) + 10;
   }

   public static int chip(Gfx g, Font font, String text, int x, int y, int colour) {
      int w = chipWidth(font, text);
      g.fill(x, y, x + w, y + CHIP_H, CHIP);
      g.text(font, text, x + 5, y + 2, colour);
      return w;
   }

   public static int segmentsWidth(Font font, String[] labels) {
      int w = 2 * INSET + (labels.length - 1) * 2;
      for (String label : labels) {
         w += font.width(label) + 14;
      }

      return w;
   }

   public static int segmentsHeight() {
      return SEGMENT_H + 2 * INSET;
   }

   public static int segments(
      Gfx g, Font font, List<Ui.Hit> hits, int x, int y, String[] labels, int chosen, boolean[] usable, IntConsumer pick, int mouseX, int mouseY, boolean covered
   ) {
      panel(g, x, y, segmentsWidth(font, labels), segmentsHeight());
      int sx = x + INSET;
      int sy = y + INSET;
      int hovered = -1;
      for (int i = 0; i < labels.length; i++) {
         int w = font.width(labels[i]) + 14;
         boolean on = i == chosen;
         if (!covered && over(mouseX, mouseY, sx, sy, w, SEGMENT_H)) {
            hovered = i;
         }

         if (on) {
            g.fill(sx, sy, sx + w, sy + SEGMENT_H, HOT);
            g.fill(sx, sy + SEGMENT_H - 2, sx + w, sy + SEGMENT_H, ACCENT);
         } else if (hovered == i && usable[i]) {
            g.fill(sx, sy, sx + w, sy + SEGMENT_H, TINT);
         }

         if (on) {
            g.text(font, labels[i], sx + 7, sy + 4, Ui.WHITE);
         } else if (usable[i]) {
            g.text(font, labels[i], sx + 7, sy + 4, SOFT);
            int n = i;
            hits.add(new Ui.Hit(sx, sy, sx + w, sy + SEGMENT_H, () -> pick.accept(n)));
         } else {
            int lx = sx + 2;
            int ly = sy + 4;
            g.fill(lx + 1, ly, lx + 4, ly + 1, OFF);
            g.fill(lx, ly + 1, lx + 1, ly + 3, OFF);
            g.fill(lx + 4, ly + 1, lx + 5, ly + 3, OFF);
            g.fill(lx, ly + 3, lx + 5, ly + 7, OFF);
            g.text(font, labels[i], sx + 8, sy + 4, OFF, false);
         }

         sx += w + 2;
      }

      return hovered;
   }

   public static void button(Gfx g, Font font, List<Ui.Hit> hits, int x, int y, int w, int h, String text, int edge, int mouseX, int mouseY, Runnable action) {
      boolean usable = action != null;
      boolean hover = usable && over(mouseX, mouseY, x, y, w, h);
      int fill = edge == ACCENT ? 0x488FB8FF : edge == DANGER ? 0x4DFF6B6B : edge == GO ? 0x487EE07E : TINT;
      g.fill(x, y, x + w, y + h, usable ? (hover ? fill | 0x20000000 : fill) : 0x10FFFFFF);
      g.outline(x, y, w, h, usable ? (hover ? 0xFFFFFFFF : (edge == Ui.WHITE ? 0x38FFFFFF : edge)) : 0x1AFFFFFF);
      g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, usable ? Ui.WHITE : OFF, usable);
      if (usable) {
         hits.add(new Ui.Hit(x, y, x + w, y + h, action));
      }
   }

   public static final int GO = 0xFF7EE07E;

   public static void check(Gfx g, Font font, List<Ui.Hit> hits, int x, int y, String label, boolean on, Runnable action) {
      boolean usable = action != null;
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

      g.text(font, label, x + 12, y, usable ? Ui.WHITE : OFF);
      if (usable) {
         hits.add(new Ui.Hit(x, y - 2, x + 12 + font.width(label), y + 10, action));
      }
   }

   public static int iconSize() {
      return ICON;
   }

   public static void floatingIcon(Gfx g, String sprite, int x, int y, int colour) {
      int alpha = colour >>> 24;
      g.sprite(sprite, x + 1, y + 1, ICON, ICON, (0x73 * alpha / 0xFF) << 24);
      g.sprite(sprite, x, y, ICON, ICON, colour);
   }

   public static void plain(Gfx g, Font font, String text, int x, int y, int colour) {
      g.text(font, text, x, y, colour, true);
   }

   public static int tabsWidth(Font font, String[] labels) {
      int w = 0;
      for (String label : labels) {
         w += font.width(label) + 10;
      }

      return w + 8;
   }

   public static int tabsHeight() {
      return 14;
   }

   public static int tabs(
      Gfx g, Font font, List<Ui.Hit> hits, int x, int y, String[] labels, int chosen, boolean[] usable, IntConsumer pick, int mouseX, int mouseY, boolean covered
   ) {
      int sx = x + 2;
      int hovered = -1;
      for (int i = 0; i < labels.length; i++) {
         boolean lock = !usable[i] && i != chosen;
         int w = font.width(labels[i]) + (lock ? 7 : 0);
         if (!covered && over(mouseX, mouseY, sx - 3, y, w + 6, 14)) {
            hovered = i;
         }

         int colour = i == chosen ? Ui.WHITE : lock ? OFF : hovered == i ? Ui.WHITE : SOFT;
         int tx = sx;
         if (lock) {
            int ly = y + 2;
            g.fill(sx + 1, ly, sx + 4, ly + 1, OFF);
            g.fill(sx, ly + 1, sx + 1, ly + 3, OFF);
            g.fill(sx + 4, ly + 1, sx + 5, ly + 3, OFF);
            g.fill(sx, ly + 3, sx + 5, ly + 7, OFF);
            tx += 7;
         }

         g.text(font, labels[i], tx, y + 2, colour, !lock);
         if (i == chosen) {
            g.fill(sx, y + 12, sx + w, y + 13, ACCENT);
         } else if (!lock) {
            int n = i;
            hits.add(new Ui.Hit(sx - 3, y, sx + w + 3, y + 14, () -> pick.accept(n)));
         }

         sx += w + 10;
      }

      return hovered;
   }

   private static final int[][] CUBE = {{7, 0, 2, 1}, {5, 1, 6, 1}, {3, 2, 10, 1}, {1, 3, 14, 10}, {3, 13, 10, 1}, {5, 14, 6, 1}, {7, 15, 2, 1}};

   private static final int[][] SQUARE = {{0, 0, 16, 16}};

   static void floatingItem(Gfx g, Item item, int x, int y, int veil) {
      int[][] shape = StructureItems.drawable(item) ? CUBE : SQUARE;
      MapMarkers.shadow(g, x, y, shape, 0xFF);
      StructureItems.draw(g, item, x, y);
      if (veil != 0) {
         for (int[] r : shape) {
            g.fill(x + r[0], y + r[1], x + r[0] + r[2], y + r[1] + r[3], veil);
         }
      }
   }

   public static final class Bar {
      private final boolean column;
      private boolean floating;
      private final List<Bar.Entry> entries = new ArrayList<>();
      private final Map<String, int[]> boxes = new HashMap<>();
      private final Map<String, Item> items = new HashMap<>();

      Bar(boolean column) {
         this.column = column;
      }

      Bar floating() {
         this.floating = true;
         return this;
      }

      private int cell() {
         return ICON + 4;
      }

      Bar tile(String id, List<Component> tip, boolean active, int tint, Runnable action) {
         this.entries.add(new Bar.Entry(Bar.Kind.TILE, id, tip, active, tint, action));
         return this;
      }

      Bar item(String id, Item item, List<Component> tip, Runnable action) {
         this.entries.add(new Bar.Entry(Bar.Kind.ITEM, id, tip, false, Ui.WHITE, action));
         this.items.put(id, item);
         return this;
      }

      Bar button(String icon, String text, List<Component> tip, boolean active, Runnable action) {
         this.entries.add(new Bar.Entry(Bar.Kind.BUTTON, icon, tip, active, Ui.WHITE, action, text));
         return this;
      }

      Bar label(String text, int colour) {
         this.entries.add(new Bar.Entry(Bar.Kind.LABEL, text, null, false, colour, null));
         return this;
      }

      Bar word(String text, int colour, Runnable action) {
         this.entries.add(new Bar.Entry(Bar.Kind.WORD, text, null, false, colour, action));
         return this;
      }

      Bar toggle(String text, List<Component> tip, boolean on, Runnable action) {
         this.entries.add(new Bar.Entry(Bar.Kind.WORD, text, tip, on, on ? Ui.WHITE : SOFT, action));
         return this;
      }

      Bar rule() {
         if (!this.entries.isEmpty() && this.entries.get(this.entries.size() - 1).kind() != Bar.Kind.RULE) {
            this.entries.add(new Bar.Entry(Bar.Kind.RULE, "", null, false, 0, null));
         }

         return this;
      }

      boolean isEmpty() {
         return this.entries.isEmpty();
      }

      private int along(Font font, Bar.Entry e) {
         if (this.floating) {
            return switch (e.kind()) {
               case BUTTON, TILE, ITEM -> this.cell();
               case LABEL, WORD -> font.width(e.id()) + 8;
               case RULE -> 8;
            };
         }

         return switch (e.kind()) {
            case BUTTON -> TILE + font.width(e.text()) + 6;
            case TILE, ITEM -> TILE;
            case LABEL -> font.width(e.id()) + 8;
            case WORD -> font.width(e.id()) + 12;
            case RULE -> 7;
         };
      }

      private List<Bar.Entry> shown() {
         List<Bar.Entry> out = new ArrayList<>(this.entries);
         while (!out.isEmpty() && out.get(out.size() - 1).kind() == Bar.Kind.RULE) {
            out.remove(out.size() - 1);
         }

         return out;
      }

      private int length(Font font) {
         List<Bar.Entry> shown = this.shown();
         int n = 0;
         for (Bar.Entry e : shown) {
            n += this.along(font, e);
         }

         return n + Math.max(0, shown.size() - 1) * GAP;
      }

      int width(Font font) {
         if (this.floating) {
            return this.column ? this.cell() : this.length(font);
         }

         return 2 * INSET + (this.column ? TILE : this.length(font));
      }

      int height(Font font) {
         if (this.floating) {
            return this.column ? this.length(font) : this.cell();
         }

         return 2 * INSET + (this.column ? this.length(font) : TILE);
      }

      int[] box(String id) {
         return this.boxes.get(id);
      }

      List<Component> draw(Gfx g, Font font, List<Ui.Hit> hits, int x, int y, int mouseX, int mouseY, boolean covered) {
         this.boxes.clear();
         if (this.floating) {
            return this.drawFloating(g, font, hits, x, y, mouseX, mouseY, covered);
         }

         panel(g, x, y, this.width(font), this.height(font));
         int at = this.column ? y + INSET : x + INSET;
         List<Component> tip = null;
         for (Bar.Entry e : this.shown()) {
            int size = this.along(font, e);
            int ex = this.column ? x + INSET : at;
            int ey = this.column ? at : y + INSET;
            int ew = this.column ? TILE : size;
            int eh = this.column ? size : TILE;
            boolean hover = !covered && over(mouseX, mouseY, ex, ey, ew, eh);
            switch (e.kind()) {
               case TILE -> {
                  this.boxes.put(e.id(), new int[]{ex, ey, ew, eh});
                  boolean usable = e.action() != null;
                  if (e.active()) {
                     g.fill(ex, ey, ex + ew, ey + eh, TINT);
                     g.fill(ex + 2, ey + eh - 1, ex + ew - 2, ey + eh + 1, ACCENT);
                  }

                  if (hover && usable) {
                     g.fill(ex, ey, ex + ew, ey + eh, TINT);
                     g.outline(ex, ey, ew, eh, 0xC0FFFFFF);
                  }

                  int colour = !usable ? OFF : (e.active() && e.colour() == Ui.WHITE ? ACCENT : e.colour());
                  g.sprite(icon(e.id()), ex + (ew - ICON) / 2, ey + (eh - ICON) / 2, ICON, ICON, colour);
                  if (usable) {
                     hits.add(new Ui.Hit(ex, ey, ex + ew, ey + eh, e.action()));
                  }

                  if (hover) {
                     tip = e.tip();
                  }
               }
               case BUTTON -> {
                  this.boxes.put(e.id(), new int[]{ex, ey, ew, eh});
                  boolean usable = e.action() != null;
                  if (e.active()) {
                     g.fill(ex, ey, ex + ew, ey + eh, TINT);
                     g.fill(ex + 2, ey + eh - 1, ex + ew - 2, ey + eh + 1, ACCENT);
                  }

                  if (hover && usable) {
                     g.fill(ex, ey, ex + ew, ey + eh, TINT);
                     g.outline(ex, ey, ew, eh, 0xC0FFFFFF);
                  }

                  g.sprite(icon(e.id()), ex + 2, ey + (eh - ICON) / 2, ICON, ICON, usable ? Ui.WHITE : OFF);
                  g.text(font, e.text(), ex + ICON + 5, ey + (eh - 8) / 2, usable ? Ui.WHITE : OFF, usable);
                  if (usable) {
                     hits.add(new Ui.Hit(ex, ey, ex + ew, ey + eh, e.action()));
                  }

                  if (hover) {
                     tip = e.tip();
                  }
               }
               case ITEM -> {
                  this.boxes.put(e.id(), new int[]{ex, ey, ew, eh});
                  boolean usable = e.action() != null;
                  if (hover && usable) {
                     g.fill(ex, ey, ex + ew, ey + eh, TINT);
                     g.outline(ex, ey, ew, eh, 0xC0FFFFFF);
                  }

                  floatingItem(g, this.items.get(e.id()), ex + (ew - ICON) / 2, ey + (eh - ICON) / 2, usable ? 0 : 0x99000000);
                  if (usable) {
                     hits.add(new Ui.Hit(ex, ey, ex + ew, ey + eh, e.action()));
                  }

                  if (hover) {
                     tip = e.tip();
                  }
               }
               case LABEL -> g.text(font, e.id(), ex + 4, ey + (eh - 8) / 2, e.colour());
               case WORD -> {
                  this.boxes.put(e.id(), new int[]{ex, ey, ew, eh});
                  if (e.active() && e.action() != null) {
                     g.fill(ex, ey, ex + ew, ey + eh, TINT);
                     g.fill(ex + 2, ey + eh - 1, ex + ew - 2, ey + eh + 1, ACCENT);
                  }

                  if (hover && e.action() != null) {
                     g.fill(ex, ey, ex + ew, ey + eh, TINT);
                     g.outline(ex, ey, ew, eh, 0xC0FFFFFF);
                  }

                  g.text(font, e.id(), ex + 6, ey + (eh - 8) / 2, e.action() == null ? OFF : e.colour());
                  if (e.action() != null) {
                     hits.add(new Ui.Hit(ex, ey, ex + ew, ey + eh, e.action()));
                  }

                  if (hover && e.tip() != null) {
                     tip = e.tip();
                  }
               }
               case RULE -> {
                  if (this.column) {
                     g.fill(ex + 3, ey + 3, ex + ew - 3, ey + 4, RULE);
                  } else {
                     g.fill(ex + 3, ey + 3, ex + 4, ey + eh - 3, RULE);
                  }
               }
            }

            at += size + GAP;
         }

         return tip;
      }

      private List<Component> drawFloating(Gfx g, Font font, List<Ui.Hit> hits, int x, int y, int mouseX, int mouseY, boolean covered) {
         int at = this.column ? y : x;
         int cell = this.cell();
         int icon = iconSize();
         List<Component> tip = null;
         for (Bar.Entry e : this.shown()) {
            int size = this.along(font, e);
            int ex = this.column ? x : at;
            int ey = this.column ? at : y;
            int ew = this.column ? cell : size;
            int eh = this.column ? size : cell;
            boolean hover = !covered && over(mouseX, mouseY, ex, ey, ew, eh);
            switch (e.kind()) {
               case TILE, BUTTON -> {
                  this.boxes.put(e.id(), new int[]{ex, ey, ew, eh});
                  boolean usable = e.action() != null;
                  int ix = ex + (ew - icon) / 2;
                  int iy = ey + (eh - icon) / 2;
                  int colour = !usable ? 0x66FFFFFF : e.active() && e.colour() == Ui.WHITE ? ACCENT : hover ? e.colour() : (e.colour() == Ui.WHITE ? 0xFFE2E2E2 : e.colour());
                  floatingIcon(g, icon(e.id()), ix, iy, colour);
                  if (e.active()) {
                     g.fill(ix, iy + icon + 1, ix + icon, iy + icon + 2, ACCENT);
                  }

                  if (usable) {
                     hits.add(new Ui.Hit(ex, ey, ex + ew, ey + eh, e.action()));
                  }

                  if (hover) {
                     tip = e.kind() == Bar.Kind.BUTTON && (e.tip() == null || e.tip().isEmpty()) ? List.of(Component.literal(e.text())) : e.tip();
                  }
               }
               case ITEM -> {
                  this.boxes.put(e.id(), new int[]{ex, ey, ew, eh});
                  boolean usable = e.action() != null;

                  floatingItem(g, this.items.get(e.id()), ex + (ew - icon) / 2, ey + (eh - icon) / 2, !usable ? 0x99000000 : hover ? 0 : 0x1E000000);
                  if (usable) {
                     hits.add(new Ui.Hit(ex, ey, ex + ew, ey + eh, e.action()));
                  }

                  if (hover) {
                     tip = e.tip();
                  }
               }
               case LABEL -> g.text(font, e.id(), ex + 4, ey + (eh - 8) / 2, e.colour(), true);
               case WORD -> {
                  this.boxes.put(e.id(), new int[]{ex, ey, ew, eh});
                  boolean usable = e.action() != null;
                  g.text(font, e.id(), ex + 4, ey + (eh - 8) / 2, usable ? e.colour() : OFF, true);
                  if (hover && usable) {
                     g.fill(ex + 4, ey + (eh - 8) / 2 + 9, ex + ew - 4, ey + (eh - 8) / 2 + 10, e.colour());
                  }

                  if (usable) {
                     hits.add(new Ui.Hit(ex, ey, ex + ew, ey + eh, e.action()));
                  }
               }
               case RULE -> {
               }
            }

            at += size + GAP;
         }

         return tip;
      }

      private enum Kind {
         BUTTON,
         TILE,
         ITEM,
         LABEL,
         WORD,
         RULE;
      }

      private record Entry(Bar.Kind kind, String id, List<Component> tip, boolean active, int colour, Runnable action, String text) {
         Entry(Bar.Kind kind, String id, List<Component> tip, boolean active, int colour, Runnable action) {
            this(kind, id, tip, active, colour, action, null);
         }
      }
   }

   public static int[] columnsSize(Font font, List<List<Ui.Item>> columns) {
      int w = 0;
      int h = 0;
      for (List<Ui.Item> col : columns) {
         w += menuWidth(font, col);
         h = Math.max(h, menuHeight(col));
      }

      return new int[]{w + Math.max(0, columns.size() - 1) * 2, h};
   }

   public static void columns(Gfx g, Font font, List<Ui.Hit> hits, List<List<Ui.Item>> columns, int x, int y, int mouseX, int mouseY) {
      int[] size = columnsSize(font, columns);
      g.fill(x, y, x + size[0], y + size[1], MENU);
      g.outline(x, y, size[0], size[1], MENU_EDGE);
      int at = x;
      for (int i = 0; i < columns.size(); i++) {
         List<Ui.Item> col = columns.get(i);
         int w = menuWidth(font, col);
         if (i > 0) {
            g.fill(at - 1, y + 4, at, y + size[1] - 4, RULE);
         }

         menuRows(g, font, hits, col, at, y, w, mouseX, mouseY);
         at += w + 2;
      }
   }

   public static Ui.Item itemAt(Font font, List<List<Ui.Item>> columns, int x, int y, int mouseX, int mouseY) {
      int at = x;
      for (List<Ui.Item> col : columns) {
         int w = menuWidth(font, col);
         int row = y + INSET;
         for (Ui.Item item : col) {
            if (over(mouseX, mouseY, at, row, w, rowHeight(item))) {
               return item;
            }

            row += rowHeight(item);
         }

         at += w + 2;
      }

      return null;
   }

   private static int rowHeight(Ui.Item item) {
      return switch (item.kind()) {
         case SEPARATOR -> RULE_H;
         case HEADING -> HEADING_H;
         default -> ROW_H;
      };
   }

   public static int menuWidth(Font font, List<Ui.Item> items) {
      int w = 100;
      for (Ui.Item item : items) {
         if (item.kind() != Ui.Kind.SEPARATOR) {
            boolean boxed = item.kind() == Ui.Kind.CHECK || item.kind() == Ui.Kind.RADIO;
            w = Math.max(w, font.width(item.label()) + (item.note() == null || boxed ? 0 : font.width(item.note()) + 12) + (boxed ? 25 : 14));
         }
      }

      return w;
   }

   public static int menuHeight(List<Ui.Item> items) {
      int h = 2 * INSET;
      for (Ui.Item item : items) {
         h += rowHeight(item);
      }

      return h;
   }

   public static void menu(Gfx g, Font font, List<Ui.Hit> hits, List<Ui.Item> items, int x, int y, int mouseX, int mouseY) {
      int w = menuWidth(font, items);
      int h = menuHeight(items);
      g.fill(x, y, x + w, y + h, MENU);
      g.outline(x, y, w, h, MENU_EDGE);
      menuRows(g, font, hits, items, x, y, w, mouseX, mouseY);
   }

   private static void menuRows(Gfx g, Font font, List<Ui.Hit> hits, List<Ui.Item> items, int x, int y, int w, int mouseX, int mouseY) {
      int row = y + INSET;
      for (Ui.Item item : items) {
         int rh = rowHeight(item);
         switch (item.kind()) {
            case SEPARATOR -> g.fill(x + 1, row + 2, x + w - 1, row + 3, RULE);
            case HEADING -> g.text(font, item.label(), x + 7, row + 3, NOTE);
            case INFO -> {
               g.text(font, item.label(), x + 7, row + 3, LABEL);
               if (item.note() != null) {
                  g.text(font, item.note(), x + w - 7 - font.width(item.note()), row + 3, NOTE);
               }
            }
            default -> {
               boolean on = item.action() != null;
               boolean hover = over(mouseX, mouseY, x + 1, row, w - 2, rh);
               if (on && hover) {
                  g.fill(x + 1, row, x + w - 1, row + rh, HOT);
               }

               if (hover && item.tip() != null) {
                  g.tooltip(font, Component.literal(item.tip()), mouseX, mouseY);
               }

               int colour = !on ? Ui.DIM : (item.kind() == Ui.Kind.DANGER ? DANGER : Ui.WHITE);
               boolean boxed = item.kind() == Ui.Kind.CHECK || item.kind() == Ui.Kind.RADIO;
               if (boxed) {
                  int bx = x + 6;
                  int by = row + 3;
                  boolean ticked = item.note() != null;
                  g.fill(bx, by, bx + 8, by + 8, on ? 0xFFA0A0A0 : 0xFF555555);
                  g.fill(bx + 1, by + 1, bx + 7, by + 7, 0xFF1E1E1E);
                  if (ticked && item.kind() == Ui.Kind.RADIO) {
                     g.fill(bx + 2, by + 2, bx + 6, by + 6, on ? ACCENT : Ui.DIM);
                  } else if (ticked) {
                     int c = on ? Ui.WHITE : Ui.DIM;
                     g.fill(bx + 2, by + 4, bx + 3, by + 5, c);
                     g.fill(bx + 3, by + 5, bx + 4, by + 6, c);
                     g.fill(bx + 4, by + 4, bx + 5, by + 5, c);
                     g.fill(bx + 5, by + 3, bx + 6, by + 4, c);
                     g.fill(bx + 6, by + 2, bx + 7, by + 3, c);
                  }

                  g.text(font, item.label(), x + 18, row + 3, colour);
               } else {
                  g.text(font, item.label(), x + 7, row + 3, colour);
               }

               if (item.note() != null && !boxed) {
                  int noteColour = !on ? Ui.DIM : NOTE;
                  g.text(font, item.note(), x + w - 7 - font.width(item.note()), row + 3, noteColour);
               }

               if (on) {
                  hits.add(new Ui.Hit(x, row, x + w, row + rh, item.action()));
               }
            }
         }

         row += rh;
      }
   }
}
