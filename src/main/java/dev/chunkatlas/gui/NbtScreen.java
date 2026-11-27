package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.ChunkFields;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Input;
import dev.chunkatlas.port.Keys;
import dev.chunkatlas.port.Texts;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class NbtScreen extends AtlasScreen {
   private static final String K = "chunkatlas.nbt.";
   private static final int ROW = 11;
   private static final int BOX_H = 16;
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final String where;
   private final Map<String, Object> root;
   private final Consumer<Map<String, Object>> save;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private final Suggest suggest;

   private final Map<NbtScreen.Node, Object> originals = new IdentityHashMap<>();
   private final NbtScreen.Node top;
   private int deleted;
   private EditBox search;
   private EditBox value;
   private String searchText = "";
   private NbtScreen.Node selected;
   private boolean loading;
   private String problem;
   private String flash;
   private long flashUntil;
   private List<NbtScreen.Row> rows = List.of();
   private int scroll;
   private int px;
   private int py;
   private int pw;
   private int ph;
   private int treeX;
   private int treeY;
   private int treeW;
   private int treeH;
   private int rightX;
   private int rightW;
   private int valueY;
   private int footerY;

   private static final class Node {
      final NbtScreen.Node parent;
      final Object key;
      Object value;
      final int depth;
      boolean open;
      List<NbtScreen.Node> kids;

      Node(NbtScreen.Node parent, Object key, Object value, int depth) {
         this.parent = parent;
         this.key = key;
         this.value = value;
         this.depth = depth;
      }

      boolean container() {
         return this.value instanceof Map || this.value instanceof List;
      }

      @SuppressWarnings("unchecked")
      List<NbtScreen.Node> kids() {
         if (this.kids == null) {
            this.kids = new ArrayList<>();
            if (this.value instanceof Map) {
               List<String> keys = new ArrayList<>(((Map<String, Object>)this.value).keySet());
               keys.sort(String.CASE_INSENSITIVE_ORDER);
               for (String k : keys) {
                  this.kids.add(new NbtScreen.Node(this, k, ((Map<String, Object>)this.value).get(k), this.depth + 1));
               }
            } else if (this.value instanceof List) {
               List<Object> list = (List<Object>)this.value;
               for (int i = 0; i < list.size(); i++) {
                  this.kids.add(new NbtScreen.Node(this, i, list.get(i), this.depth + 1));
               }
            }
         }

         return this.kids;
      }

      String name() {
         return String.valueOf(this.key);
      }

      String path() {
         if (this.parent == null) {
            return "";
         }

         String up = this.parent.path();
         return up.isEmpty() ? this.name() : up + "." + this.name();
      }

      boolean holds(NbtScreen.Node other) {
         for (NbtScreen.Node n = other; n != null; n = n.parent) {
            if (n == this) {
               return true;
            }
         }

         return false;
      }

      @SuppressWarnings("unchecked")
      void store() {
         if (this.parent == null) {
            return;
         }

         if (this.parent.value instanceof Map) {
            ((Map<String, Object>)this.parent.value).put((String)this.key, this.value);
         } else if (this.parent.value instanceof List) {
            ((List<Object>)this.parent.value).set((Integer)this.key, this.value);
         }
      }

      @SuppressWarnings("unchecked")
      void remove() {
         if (this.parent == null) {
            return;
         }

         if (this.parent.value instanceof Map) {
            ((Map<String, Object>)this.parent.value).remove(this.key);
         } else if (this.parent.value instanceof List) {
            ((List<Object>)this.parent.value).remove((int)(Integer)this.key);
         }

         this.parent.kids = null;
      }
   }

   private record Row(NbtScreen.Node node, String path) {
   }

   NbtScreen(MapScreen map, String where, Map<String, Object> root, Consumer<Map<String, Object>> save) {
      super(Component.translatable(K + "title"));
      this.map = map;
      this.where = where;
      this.root = root;
      this.save = save;
      this.suggest = new Suggest(this.font);
      this.top = new NbtScreen.Node(null, Ui.t(K + "chunk"), root, 0);
      this.top.open = true;
   }

   @Override
   protected void init() {
      this.layout();
      this.search = new EditBox(this.font, this.treeX, this.py + 22, this.treeW, BOX_H, Component.translatable(K + "search"));
      this.search.setHint(Texts.searchHint(Component.translatable(K + "search")));
      this.search.setMaxLength(100);
      this.search.setValue(this.searchText);
      this.search.setResponder(s -> {
         this.searchText = s;
         this.scroll = 0;
         this.refill();
      });
      this.addRenderableWidget(this.search);
      this.value = new EditBox(this.font, this.rightX, this.valueY, this.rightW, BOX_H, Component.translatable(K + "value"));
      this.value.setMaxLength(32767);
      this.value.setResponder(s -> this.edited());
      this.addRenderableWidget(this.value);
      this.refill();
      this.load();
   }

   private void layout() {
      this.pw = Math.max(250, Math.min(430, this.width - 8 - 140));
      this.px = Glass.MARGIN;
      this.py = Glass.MARGIN;
      this.ph = this.height - 2 * Glass.MARGIN;
      this.footerY = this.py + this.ph - 6 - BOX_H;
      this.treeX = this.px + 6;
      this.treeY = this.py + 22 + BOX_H + 4;
      this.treeW = (this.pw - 20) * 11 / 20;
      this.treeH = this.footerY - 8 - this.treeY;
      this.rightX = this.treeX + this.treeW + 8;
      this.rightW = this.px + this.pw - 6 - this.rightX;
      this.valueY = this.py + 22 + 12 + 14 + 10;
   }

   private void refill() {
      List<NbtScreen.Row> out = new ArrayList<>();
      String find = this.searchText.trim().toLowerCase(Locale.ROOT);
      if (find.isEmpty()) {
         this.walk(this.top, out);
      } else {
         this.find(this.top, find, out, 0);
      }

      this.rows = out;
      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
   }

   private void walk(NbtScreen.Node n, List<NbtScreen.Row> out) {
      out.add(new NbtScreen.Row(n, null));
      if (n.container() && n.open) {
         for (NbtScreen.Node k : n.kids()) {
            this.walk(k, out);
         }
      }
   }

   private void find(NbtScreen.Node n, String find, List<NbtScreen.Row> out, int depth) {
      if (out.size() > 400 || depth > 24) {
         return;
      }

      if (n != this.top && n.name().toLowerCase(Locale.ROOT).contains(find)) {
         String up = n.parent == null || n.parent == this.top ? "" : n.parent.path().replace(".", " › ") + " › ";
         out.add(new NbtScreen.Row(n, up));
      }

      if (n.container()) {
         for (NbtScreen.Node k : n.kids()) {
            this.find(k, find, out, depth + 1);
         }
      }
   }

   private int maxScroll() {
      return Math.max(0, this.rows.size() * ROW - this.treeH);
   }

   private void select(NbtScreen.Node n) {
      this.selected = n;
      this.suggest.detach();
      this.load();
   }

   private void reveal() {
      for (int i = 0; i < this.rows.size(); i++) {
         if (this.rows.get(i).node() == this.selected) {
            int y = i * ROW;
            if (y < this.scroll) {
               this.scroll = y;
            } else if (y + ROW > this.scroll + this.treeH) {
               this.scroll = y + ROW - this.treeH;
            }
         }
      }
   }

   private static boolean editable(NbtScreen.Node n) {
      return n != null && !n.container() && !(n.value instanceof byte[] || n.value instanceof int[] || n.value instanceof long[]);
   }

   private void load() {
      this.loading = true;
      NbtScreen.Node n = this.selected;
      boolean editable = editable(n);
      this.value.setValue(editable ? String.valueOf(n.value) : "");
      this.value.setEditable(editable);
      this.value.setTextColor(Ui.WHITE);
      this.value.setSuggestion(null);
      this.value.moveCursorToStart(false);
      this.problem = null;
      this.loading = false;
   }

   private static List<Suggest.Option> optionsFor(NbtScreen.Node n) {
      if (n == null || n.parent == null) {
         return List.of();
      }

      if (n.parent.parent == null && "Status".equals(n.key) && n.value instanceof String) {
         return Suggest.statuses();
      }

      if (n.value instanceof String) {
         NbtScreen.Node list = "Name".equals(n.key) && n.parent.key instanceof Integer ? n.parent.parent : (n.key instanceof Integer ? n.parent : null);
         if (list != null && "palette".equals(list.key) && list.parent != null) {
            if ("block_states".equals(list.parent.key) && "Name".equals(n.key)) {
               return Suggest.blocks();
            }

            if ("biomes".equals(list.parent.key) && n.key instanceof Integer) {
               return Suggest.biomes();
            }
         }

         return List.of();
      }

      return n.value instanceof Byte ? Suggest.booleans() : List.of();
   }

   private void edited() {
      NbtScreen.Node n = this.selected;
      if (this.loading || n == null || !editable(n)) {
         return;
      }

      String s = this.value.getValue().trim();
      String why = null;
      Object v = null;
      try {
         switch (n.value) {
            case Byte b -> v = (byte)whole(s, Byte.MIN_VALUE, Byte.MAX_VALUE, "Byte");
            case Short sh -> v = (short)whole(s, Short.MIN_VALUE, Short.MAX_VALUE, "Short");
            case Integer i -> v = (int)whole(s, Integer.MIN_VALUE, Integer.MAX_VALUE, "Int");
            case Long l -> v = whole(s, Long.MIN_VALUE, Long.MAX_VALUE, "Long");
            case Float f -> v = (float)decimal(s);
            case Double d -> v = decimal(s);
            case String str -> {
               v = this.value.getValue();
               if (n.parent.parent == null && "Status".equals(n.key) && !knownStatus((String)v)) {
                  why = Ui.t(K + "bad.status");
               }
            }
            default -> {
            }
         }
      } catch (NbtScreen.Bad bad) {
         why = bad.getMessage();
      }

      this.problem = why;
      this.value.setTextColor(why == null ? Ui.WHITE : Ui.YELLOW);
      if (why == null && v != null) {
         this.originals.putIfAbsent(n, n.value);
         n.value = v;
         n.store();
      }

      this.suggest.edit(this.value, optionsFor(n));
   }

   private static final class Bad extends RuntimeException {
      Bad(String why) {
         super(why, null, false, false);
      }
   }

   private static long whole(String s, long min, long max, String type) {
      long v;
      try {
         v = Long.parseLong(s);
      } catch (NumberFormatException e) {
         throw new NbtScreen.Bad(Ui.t(K + "bad.whole"));
      }

      if (v < min || v > max) {
         throw new NbtScreen.Bad(Ui.t(K + "bad.range", type, Ui.count(min), Ui.count(max)));
      }

      return v;
   }

   private static double decimal(String s) {
      try {
         double d = Double.parseDouble(s);
         if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new NbtScreen.Bad(Ui.t(K + "bad.number"));
         }

         return d;
      } catch (NumberFormatException e) {
         throw new NbtScreen.Bad(Ui.t(K + "bad.number"));
      }
   }

   private static boolean knownStatus(String s) {
      String t = s.trim();
      String plain = t.substring(t.indexOf(':') + 1);
      for (String known : ChunkFields.STATUSES) {
         if (known.equals(t) || known.substring(known.indexOf(':') + 1).equals(plain)) {
            return true;
         }
      }

      return false;
   }

   private int changes() {
      int n = this.deleted;
      for (Map.Entry<NbtScreen.Node, Object> e : this.originals.entrySet()) {
         if (!Objects.equals(e.getKey().value, e.getValue())) {
            n++;
         }
      }

      return n;
   }

   private boolean canSave() {
      return this.problem == null && this.changes() > 0;
   }

   private void deleteSelected() {
      NbtScreen.Node n = this.selected;
      if (n != null && n.parent != null) {
         n.remove();
         this.originals.keySet().removeIf(n::holds);
         this.deleted++;
         this.select(n.parent);
         this.refill();
      }
   }

   private void copyPath() {
      NbtScreen.Node n = this.selected;
      if (n != null && n.parent != null) {
         this.mc.keyboardHandler.setClipboard(n.path());
         this.flash = Ui.t(K + "copied");
         this.flashUntil = System.currentTimeMillis() + 2000L;
      }
   }

   void tourSet(String key, String text) {
      for (NbtScreen.Node k : this.top.kids()) {
         if (k.name().equals(key)) {
            this.select(k);
            this.value.setValue(text);
            this.refill();
            this.reveal();
         }
      }
   }

   void tourSave() {
      this.saveNow();
   }

   private void saveNow() {
      if (!this.canSave()) {
         return;
      }

      Game.open(this.mc, this.map);
      this.save.accept(this.root);
   }

   @Override
   public void onClose() {
      Game.open(this.mc, this.map);
   }

   @Override
   public void removed() {
      this.map.clearOutline();
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }

   static String type(Object v) {
      return dev.chunkatlas.engine.ChunkTags.kind(v);
   }

   private static String badge(Object v) {
      return switch (v) {
         case Byte b -> "B";
         case Short s -> "Sh";
         case Integer i -> "I";
         case Long l -> "L";
         case Float f -> "F";
         case Double d -> "D";
         case String s -> "S";
         case byte[] a -> "[B]";
         case int[] a -> "[I]";
         case long[] a -> "[L]";
         case List<?> l -> "[ ]";
         case Map<?, ?> m -> "{ }";
         default -> "?";
      };
   }

   private static int badgeColour(Object v) {
      return switch (v) {
         case Byte b -> 0xFFE0A070;
         case Short s -> 0xFFE0A070;
         case Integer i -> 0xFF8FB8FF;
         case Long l -> 0xFF6FD0C8;
         case Float f -> 0xFFF0A0D0;
         case Double d -> 0xFFF0A0D0;
         case String s -> 0xFFFFD84A;
         case List<?> l -> 0xFFB48CFF;
         case Map<?, ?> m -> 0xFFC8C8C8;
         default -> 0xFF9AC48A;
      };
   }

   private static String preview(Object v) {
      return switch (v) {
         case Map<?, ?> m -> Ui.plural(K + "tags", m.size());
         case List<?> l -> Ui.plural(K + "entries", l.size());
         case byte[] a -> Ui.plural(K + "bytes", a.length);
         case int[] a -> Ui.plural(K + "ints", a.length);
         case long[] a -> Ui.plural(K + "longs", a.length);
         case String s -> s.length() > 60 ? s.substring(0, 60) + "…" : s;
         default -> String.valueOf(v);
      };
   }

   private static String hint(NbtScreen.Node n) {
      if (n == null || n.parent == null) {
         return "";
      }

      boolean atRoot = n.parent.parent == null;
      if (atRoot && "InhabitedTime".equals(n.key) && n.value instanceof Long t) {
         return Ui.t(K + "hint.inhabited", time(t));
      }

      if (atRoot && "LastUpdate".equals(n.key) && n.value instanceof Long t) {
         return Ui.t(K + "hint.last_update", time(t));
      }

      if (atRoot && "DataVersion".equals(n.key) && n.value instanceof Integer v) {
         return Ui.version(v);
      }

      if ("Y".equals(n.key) && n.value instanceof Byte y && n.parent.key instanceof Integer) {
         return Ui.t(K + "hint.section_y", y * 16, y * 16 + 15);
      }

      if (atRoot && ("xPos".equals(n.key) || "zPos".equals(n.key)) && n.value instanceof Integer v) {
         return Ui.t(K + "hint.pos", v * 16, v * 16 + 15);
      }

      return "";
   }

   private static String time(long ticks) {
      long sec = ticks / 20L;
      return sec >= 3600L ? sec / 3600L + " h " + sec % 3600L / 60L + " min" : sec / 60L + " min " + sec % 60L + " s";
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      this.map.drawUnder(g, partial);
      this.hits.clear();
      g.layer();
      Glass.panel(g, this.px, this.py, this.pw, this.ph);
      g.fill(this.px, this.py, this.px + this.pw, this.py + this.ph, 0x66000000);

      String head = this.font.plainSubstrByWidth(Ui.t(K + "heading", this.where), this.pw - 12);
      g.text(this.font, head, this.px + 6, this.py + 7, Ui.WHITE);
      g.fill(this.px + 1, this.py + 19, this.px + this.pw - 1, this.py + 20, Glass.RULE);
      this.drawTree(g, mouseX, mouseY);
      this.drawPicked(g, mouseX, mouseY);
      this.drawFooter(g, mouseX, mouseY);
      super.draw(g, mouseX, mouseY, partial);
      if (this.problem != null && editable(this.selected)) {
         g.outline(this.value.getX(), this.value.getY(), this.value.getWidth(), this.value.getHeight(), Ui.YELLOW);
      }

      this.suggest.draw(g, this.width, this.height, mouseX, mouseY);
   }

   private void drawTree(Gfx g, int mouseX, int mouseY) {
      int x0 = this.treeX;
      int y0 = this.treeY;
      boolean inTree = Glass.over(mouseX, mouseY, x0, y0, this.treeW, this.treeH);
      g.scissor(x0, y0, x0 + this.treeW, y0 + this.treeH);
      boolean searching = !this.searchText.trim().isEmpty();
      for (int i = 0; i < this.rows.size(); i++) {
         int y = y0 + i * ROW - this.scroll;
         if (y + ROW < y0 || y > y0 + this.treeH) {
            continue;
         }

         NbtScreen.Row row = this.rows.get(i);
         NbtScreen.Node n = row.node();
         if (n == this.selected) {
            g.fill(x0, y, x0 + this.treeW, y + ROW, Glass.HOT);
            g.fill(x0, y, x0 + 1, y + ROW, Glass.ACCENT);
         } else if (inTree && mouseY >= y && mouseY < y + ROW) {
            g.fill(x0, y, x0 + this.treeW, y + ROW, Glass.TINT);
         }

         int x = x0 + 3 + (searching ? 0 : n.depth * 8);
         if (!searching && n.container()) {
            arrow(g, x, y + 3, n.open);
         }

         x += 7;
         String b = badge(n.value);
         int bw = this.font.width(b) + 4;
         g.fill(x, y + 1, x + bw, y + 10, badgeColour(n.value));
         g.text(this.font, b, x + 2, y + 2, 0xFF000000, false);
         x += bw + 4;
         int right = x0 + this.treeW - 4;
         if (row.path() != null && !row.path().isEmpty()) {
            String p = this.font.plainSubstrByWidth(row.path(), Math.max(0, right - x - this.font.width(n.name())));
            g.text(this.font, p, x, y + 2, Glass.NOTE);
            x += this.font.width(p);
         }

         if (searching) {
            this.highlighted(g, n.name(), this.searchText.trim(), x, y + 2);
         } else {
            g.text(this.font, n.name(), x, y + 2, Ui.WHITE);
         }

         x += this.font.width(n.name()) + 6;
         if (right - x > 12) {
            g.text(this.font, this.font.plainSubstrByWidth(preview(n.value), right - x), x, y + 2, Glass.NOTE);
         }
      }

      if (this.rows.isEmpty()) {
         g.text(this.font, Ui.t(K + "none"), x0 + 4, y0 + 4, Glass.NOTE);
      }

      g.unscissor();
      int max = this.maxScroll();
      if (max > 0) {
         int track = this.treeH;
         int knob = Math.max(10, track * track / Math.max(1, this.rows.size() * ROW));
         int at = y0 + (track - knob) * this.scroll / max;
         int sx = x0 + this.treeW - 2;
         g.fill(sx, y0, sx + 2, y0 + track, 0x20FFFFFF);
         g.fill(sx, at, sx + 2, at + knob, 0x80FFFFFF);
      }
   }

   private void highlighted(Gfx g, String name, String find, int x, int y) {
      int at = name.toLowerCase(Locale.ROOT).indexOf(find.toLowerCase(Locale.ROOT));
      if (at < 0) {
         g.text(this.font, name, x, y, Ui.WHITE);
         return;
      }

      String before = name.substring(0, at);
      String match = name.substring(at, at + find.length());
      String after = name.substring(at + find.length());
      g.text(this.font, before, x, y, Ui.WHITE);
      x += this.font.width(before);
      g.text(this.font, match, x, y, Glass.ACCENT);
      x += this.font.width(match);
      g.text(this.font, after, x, y, Ui.WHITE);
   }

   private static void arrow(Gfx g, int x, int y, boolean open) {
      int c = Glass.SOFT;
      if (open) {
         g.fill(x, y + 1, x + 5, y + 2, c);
         g.fill(x + 1, y + 2, x + 4, y + 3, c);
         g.fill(x + 2, y + 3, x + 3, y + 4, c);
      } else {
         g.fill(x + 1, y, x + 2, y + 5, c);
         g.fill(x + 2, y + 1, x + 3, y + 4, c);
         g.fill(x + 3, y + 2, x + 4, y + 3, c);
      }
   }

   private void drawPicked(Gfx g, int mouseX, int mouseY) {
      NbtScreen.Node n = this.selected;
      int x = this.rightX;
      int y = this.py + 22;
      int w = this.rightW;
      if (n == null) {
         for (FormattedCharSequence line : this.font.split(Component.literal(Ui.t(K + "pick")), w)) {
            g.text(this.font, line, x, y, Glass.NOTE);
            y += 10;
         }

         return;
      }

      String name = n.name();
      String up = n.parent == null ? "" : (n.parent.parent == null ? this.top.name() : this.top.name() + " › " + n.parent.path().replace(".", " › ")) + " › ";
      int nameW = this.font.width(name);
      int room = w - nameW;
      if (this.font.width(up) > room) {
         while (!up.isEmpty() && this.font.width("…" + up) > room) {
            up = up.substring(1);
         }

         up = "…" + up;
      }

      g.text(this.font, up, x, y, Glass.NOTE);
      g.text(this.font, name, x + this.font.width(up), y, Ui.WHITE);
      y += 12;

      String b = badge(n.value);
      int bw = this.font.width(b) + 4;
      g.fill(x, y, x + bw, y + 9, badgeColour(n.value));
      g.text(this.font, b, x + 2, y + 1, 0xFF000000, false);
      g.text(this.font, type(n.value), x + bw + 4, y + 1, Glass.LABEL);
      y += 14;
      g.text(this.font, Ui.t(K + "value"), x, y, Glass.LABEL);
      y += 10 + BOX_H + 3;

      String note = this.problem != null ? this.problem : editable(n) ? hint(n) : preview(n.value);
      for (FormattedCharSequence line : this.font.split(Component.literal(note), w)) {
         g.text(this.font, line, x, y, this.problem != null ? Ui.YELLOW : Glass.NOTE);
         y += 10;
      }

      y = Math.max(y + 4, this.valueY + BOX_H + 26);
      boolean real = n.parent != null;
      int bw2 = (w - 4) / 2;
      Glass.button(g, this.font, this.hits, x, y, bw2, BOX_H, Ui.t(K + "copy_path"), Ui.WHITE, mouseX, mouseY, real ? this::copyPath : null);
      Glass.button(g, this.font, this.hits, x + bw2 + 4, y, w - bw2 - 4, BOX_H, Ui.t(K + "delete"), Glass.DANGER, mouseX, mouseY, real ? this::deleteSelected : null);
   }

   private void drawFooter(Gfx g, int mouseX, int mouseY) {
      int y = this.footerY;
      g.fill(this.px + 1, y - 5, this.px + this.pw - 1, y - 4, Glass.RULE);
      String save = Ui.t(K + "save");
      String cancel = Ui.t("chunkatlas.map.cancel");
      int sw = Math.max(64, this.font.width(save) + 20);
      int cw = Math.max(56, this.font.width(cancel) + 20);
      int right = this.px + this.pw - 6;
      Glass.button(g, this.font, this.hits, right - sw, y, sw, BOX_H, save, Glass.ACCENT, mouseX, mouseY, this.canSave() ? this::saveNow : null);
      Glass.button(g, this.font, this.hits, right - sw - 4 - cw, y, cw, BOX_H, cancel, Ui.WHITE, mouseX, mouseY, this::onClose);

      String text;
      int colour = Glass.NOTE;
      if (this.suggest.open()) {
         text = Suggest.keysHint();
      } else if (this.flash != null && System.currentTimeMillis() < this.flashUntil) {
         text = this.flash;
      } else if (this.problem != null) {
         text = Ui.t(K + "fix_first");
         colour = Ui.YELLOW;
      } else {
         int n = this.changes();
         text = n == 0 ? "" : Ui.plural(K + "unsaved", n);
      }

      int room = right - sw - 4 - cw - 6 - (this.px + 6);
      g.text(this.font, this.font.plainSubstrByWidth(text, Math.max(0, room)), this.px + 6, y + 4, colour);
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      double x = mouseX;
      double y = mouseY;
      if (this.suggest.mouseClicked(x, y, this.width, this.height)) {
         return true;
      }

      if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
         for (int n = this.hits.size() - 1; n >= 0; n--) {
            if (this.hits.get(n).has(x, y)) {
               this.hits.get(n).action().run();
               return true;
            }
         }

         if (Glass.over((int)x, (int)y, this.treeX, this.treeY, this.treeW, this.treeH)) {
            int i = (int)((y - this.treeY + this.scroll) / ROW);
            if (i >= 0 && i < this.rows.size()) {
               NbtScreen.Node n = this.rows.get(i).node();
               boolean searching = !this.searchText.trim().isEmpty();
               if (n != this.selected) {
                  this.select(n);
               }

               if (n.container() && (doubleClick || !searching && x < this.treeX + 3 + n.depth * 8 + 8)) {
                  n.open = !n.open;
                  this.refill();
               }
            }

            this.setFocused(null);
            return true;
         }
      }

      boolean taken = super.onClick(mouseX, mouseY, mouseButton, doubleClick);
      if (this.value.isFocused() && editable(this.selected)) {
         this.suggest.edit(this.value, optionsFor(this.selected));
      } else {
         this.suggest.close();
      }

      return taken;
   }

   @Override
   protected boolean onScroll(double x, double y, double scrollX, double scrollY) {
      if (Glass.over((int)x, (int)y, this.treeX, this.treeY, this.treeW, this.treeH) && this.maxScroll() > 0) {
         this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)Math.round(scrollY * ROW * 2)));
         return true;
      }

      return super.onScroll(x, y, scrollX, scrollY);
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      if (this.suggest.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      }

      if (Keys.escape(keyCode)) {
         this.onClose();
         return true;
      }

      if (Keys.confirm(keyCode) && this.value.isFocused()) {
         if (this.canSave()) {
            this.saveNow();
         }

         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
   }
}
