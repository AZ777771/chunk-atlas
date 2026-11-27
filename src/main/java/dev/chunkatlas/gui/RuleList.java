package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.ChunkContents;
import dev.chunkatlas.engine.ChunkFilter;
import dev.chunkatlas.engine.RuleIds;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Reg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

final class RuleList {
   static final int H = RulePanel.CTRL;
   static final int ROW = RulePanel.ROW;
   static final int GAP = 3;
   private static final int CARD_PAD = 4;
   private static final int CARD_EDGE = 6;
   private static final int SUGGEST_ROW = 12;
   private static final int SUGGEST_MAX = 6;
   private final Minecraft mc = Minecraft.getInstance();
   private final Font font;
   private final Runnable onChange;
   private final List<ChunkFilter.Field> fields = new ArrayList<>();
   private final boolean hasSpawn;

   private final boolean canLookInside;

   private final Map<ChunkFilter.Field, List<RuleList.Suggestion>> names = new EnumMap<>(ChunkFilter.Field.class);
   private final List<Object> items = new ArrayList<>();
   boolean matchAll = true;

   boolean dirty;
   private int left;
   private int width;
   private int matchY;
   private int addY;
   private int top;
   private int bottom;

   private RuleList.Row menuRow;
   private boolean menuOp;
   private boolean menuMatch;
   private int[] menuAt;

   private RuleList.Row suggesting;
   private List<RuleList.Suggestion> shown = List.of();
   private int highlighted;

   RuleList(Font font, boolean hasSpawn, boolean hasPlayer, int worldDataVersion, Runnable onChange) {
      this.font = font;
      this.onChange = onChange;
      this.hasSpawn = hasSpawn;
      this.canLookInside = worldDataVersion <= 0 || worldDataVersion >= ChunkContents.NAMED_BLOCKS_SINCE;
      for (ChunkFilter.Field f : ChunkFilter.Field.values()) {
         if ((f != ChunkFilter.Field.DISTANCE_SPAWN || hasSpawn) && (f != ChunkFilter.Field.DISTANCE_PLAYER || hasPlayer) && (!f.deep() || this.canLookInside)) {
            this.fields.add(f);
         }
      }

      if (this.canLookInside) {
         this.names.put(ChunkFilter.Field.BLOCK, this.blockNames());
         this.names.put(ChunkFilter.Field.BIOME, this.worldNames(Registries.BIOME, RuleIds.VANILLA_BIOMES, false));
         this.names.put(ChunkFilter.Field.STRUCTURE, this.worldNames(Registries.STRUCTURE, RuleIds.VANILLA_STRUCTURES, true));
      }
   }

   boolean canLookInside() {
      return this.canLookInside;
   }

   void load(ChunkFilter filter) {
      this.items.clear();
      ChunkFilter start = filter == null ? ChunkFilter.empty() : filter;
      this.matchAll = start.matchAll();
      Map<Integer, RuleList.Group> groups = new LinkedHashMap<>();
      for (ChunkFilter.Condition c : start.conditions()) {
         if (!this.fields.contains(c.field())) {
            continue;
         }

         RuleList.Row row = this.row(c);
         if (c.group() == 0) {
            this.items.add(row);
         } else {
            groups.computeIfAbsent(c.group(), k -> {
               RuleList.Group g = new RuleList.Group();
               this.items.add(g);
               return g;
            }).rows.add(row);
         }
      }

      if (this.items.isEmpty()) {
         this.items.add(RuleList.Row.blank(ChunkFilter.Field.TIME_PLAYED));
      }

      this.dirty = true;
   }

   private RuleList.Row row(ChunkFilter.Condition c) {
      RuleList.Row row = new RuleList.Row(c.field(), c.op(), c.field().takesNumber(c.op()) ? Long.toString(c.value()) : "");
      row.id = c.id();
      row.idText = c.field().deep() ? c.id() : "";
      return row;
   }

   List<RuleList.Row> rows() {
      List<RuleList.Row> out = new ArrayList<>();
      for (Object it : this.items) {
         if (it instanceof RuleList.Row r) {
            out.add(r);
         } else if (it instanceof RuleList.Group g) {
            out.addAll(g.rows);
         }
      }

      return out;
   }

   int size() {
      return this.rows().size();
   }

   boolean canAdd() {
      return this.size() < ChunkFilter.MAX_CONDITIONS;
   }

   ChunkFilter filter(int buffer) {
      List<ChunkFilter.Condition> list = new ArrayList<>();
      int group = 0;
      for (Object it : this.items) {
         if (it instanceof RuleList.Row r) {
            ChunkFilter.Condition c = r.condition(0);
            if (c != null) {
               list.add(c);
            }
         } else if (it instanceof RuleList.Group g) {
            group++;
            for (RuleList.Row r : g.rows) {
               ChunkFilter.Condition c = r.condition(group);
               if (c != null) {
                  list.add(c);
               }
            }
         }
      }

      return new ChunkFilter(list, this.matchAll, 0, buffer);
   }

   String note() {
      for (RuleList.Row r : this.rows()) {
         if (r.field.deep() && !r.id.isEmpty() && !this.known(r.field, r.id)) {
            return Ui.t("chunkatlas.rules.unknown", r.idText.trim());
         }
      }

      return null;
   }

   void build(Consumer<EditBox> add) {
      this.dirty = false;
      this.suggesting = null;
      this.shown = List.of();
      for (RuleList.Row row : this.rows()) {
         row.numberBox = null;
         row.nameBox = null;
         if (row.field.takesNumber(row.op)) {
            EditBox box = new EditBox(this.font, 0, 0, 40, H, Component.translatable("chunkatlas.filter.number.narration"));
            box.setMaxLength(9);
            box.setValue(row.text);
            box.setHint(Component.translatable("chunkatlas.filter.value_hint"));
            box.setResponder(text -> {
               String digits = text.replaceAll("[^0-9]", "");
               if (!digits.equals(text)) {
                  box.setValue(digits);
                  return;
               }

               if (!digits.equals(row.text)) {
                  row.text = digits;
                  this.onChange.run();
               }
            });
            row.numberBox = box;
            add.accept(box);
         }

         if (row.field.deep()) {
            EditBox name = new EditBox(this.font, 0, 0, 60, H, Component.translatable(row.field.langKey()));
            name.setMaxLength(100);
            name.setValue(row.idText);
            name.setHint(Component.translatable("chunkatlas.rules.hint." + row.field.key()));
            name.setResponder(text -> {
               row.idText = text;
               String was = row.id;
               row.id = this.resolve(row.field, text);
               this.suggesting = row;
               this.suggest();
               if (!was.equals(row.id)) {
                  this.onChange.run();
               }
            });
            row.nameBox = name;
            add.accept(name);
         }
      }
   }

   int layout(int x, int y, int w, int top, int bottom) {
      this.left = x;
      this.width = w;
      this.top = top;
      this.bottom = bottom;
      int cy = y;
      this.matchY = cy;
      cy += ROW;
      for (Object it : this.items) {
         if (it instanceof RuleList.Row r) {
            this.place(r, x, cy, w);
            cy += ROW;
         } else if (it instanceof RuleList.Group g) {
            g.x = x;
            g.y = cy;
            g.w = w;
            cy += CARD_PAD + 14;
            for (RuleList.Row r : g.rows) {
               this.place(r, x + CARD_EDGE, cy, w - CARD_EDGE - CARD_PAD);
               cy += ROW;
            }

            g.addY = cy;
            cy += H + CARD_PAD;
            g.h = cy - g.y;
            cy += GAP + 1;
         }
      }

      this.addY = cy;
      cy += ROW;
      return cy - y;
   }

   private void place(RuleList.Row r, int x, int y, int w) {
      r.x = x;
      r.y = y;
      r.w = w;
      r.shown = y >= this.top && y + H <= this.bottom;
      int[] geo = this.geometry(r);
      if (r.numberBox != null) {
         r.numberBox.setX(geo[4]);
         r.numberBox.setY(y);
         r.numberBox.setWidth(geo[5]);
         r.numberBox.visible = r.shown;
      }

      if (r.nameBox != null) {
         r.nameBox.setX(geo[6]);
         r.nameBox.setY(y);
         r.nameBox.setWidth(geo[7]);
         r.nameBox.visible = r.shown;
      }
   }

   private int[] geometry(RuleList.Row r) {
      int fieldW = Math.max(56, Math.min(84, r.w * 36 / 100));
      int opW = 22;
      for (ChunkFilter.Op op : r.field.ops()) {
         opW = Math.max(opW, this.font.width(Ui.t(ChunkFilter.opKey(r.field, op))) + 12);
      }

      opW = Math.min(opW, 64);
      int fx = r.x;
      int ox = fx + fieldW + GAP;
      int vx = ox + opW + GAP;
      int removeX = r.x + r.w - H;
      int vw = Math.max(24, removeX - GAP - vx);
      int numX = 0;
      int numW = 0;
      int nameX = 0;
      int nameW = 0;
      if (r.field.deep()) {
         if (r.field.takesNumber(r.op)) {
            numX = vx;
            numW = Math.min(30, vw / 3);
            nameX = vx + numW + GAP;
            nameW = Math.max(20, vw - numW - GAP);
         } else {
            nameX = vx;
            nameW = vw;
         }
      } else if (!r.field.flag()) {
         numX = vx;
         numW = vw;
      }

      return new int[]{fx, fieldW, ox, opW, numX, numW, nameX, nameW, removeX};
   }

   void draw(Gfx g, List<Ui.Hit> hits, int mouseX, int mouseY) {
      int mx = this.overlayOpen() ? -1000 : mouseX;
      int my = this.overlayOpen() ? -1000 : mouseY;
      List<Ui.Hit> none = new ArrayList<>();

      boolean matchShown = this.matchY >= this.top && this.matchY + H <= this.bottom;
      List<Ui.Hit> mh = matchShown ? hits : none;
      String lead = Ui.t("chunkatlas.filter.match.lead");
      g.text(this.font, lead, this.left, this.matchY + (H - 8) / 2, Ui.WHITE);
      String all = Ui.t("chunkatlas.filter.match.all");
      String any = Ui.t("chunkatlas.filter.match.any");
      int dw = Math.max(this.font.width(all), this.font.width(any)) + 20;
      int dx = this.left + this.font.width(lead) + 5;
      RulePanel.dropdown(g, this.font, mh, dx, this.matchY, dw, this.matchAll ? all : any, mx, my, () -> {
         this.closeOverlays();
         this.menuMatch = true;
         this.menuAt = new int[]{dx, this.matchY};
      });
      g.text(this.font, Ui.t("chunkatlas.filter.match.of_them"), dx + dw + 5, this.matchY + (H - 8) / 2, Ui.WHITE);

      for (Object it : this.items) {
         if (it instanceof RuleList.Row r) {
            this.drawRow(g, r.shown ? hits : none, r, mx, my, this.size() > 1);
         } else if (it instanceof RuleList.Group grp) {
            g.fill(grp.x, grp.y, grp.x + grp.w, grp.y + grp.h, 0x14FFFFFF);
            g.fill(grp.x, grp.y, grp.x + 2, grp.y + grp.h, Glass.ACCENT);
            int hy = grp.y + CARD_PAD;
            g.text(this.font, Ui.t("chunkatlas.filter.group"), grp.x + CARD_EDGE, hy + 2, Glass.LABEL);
            boolean headShown = hy >= this.top && hy + 12 <= this.bottom;
            Glass.button(g, this.font, headShown ? hits : none, grp.x + grp.w - CARD_PAD - 12, hy, 12, 12, "✕", Ui.WHITE, mx, my, () -> this.removeGroup(grp));
            for (RuleList.Row r : grp.rows) {
               this.drawRow(g, r.shown ? hits : none, r, mx, my, true);
            }

            boolean addShown = grp.addY >= this.top && grp.addY + H <= this.bottom;
            String add = Ui.t("chunkatlas.filter.add");
            RulePanel.dashed(g, this.font, addShown ? hits : none, grp.x + CARD_EDGE, grp.addY, this.font.width(add) + 16, H, add, mx, my, this.canAdd() ? () -> this.addRule(grp) : null);
         }
      }

      boolean addShown = this.addY >= this.top && this.addY + H <= this.bottom;
      List<Ui.Hit> ah = addShown ? hits : none;
      String add = Ui.t("chunkatlas.filter.add");
      String group = Ui.t("chunkatlas.filter.add_group");
      int aw = this.font.width(add) + 16;
      int gw = this.font.width(group) + 16;
      RulePanel.dashed(g, this.font, ah, this.left, this.addY, aw, H, add, mx, my, this.canAdd() ? () -> this.addRule(null) : null);
      if (this.left + aw + 6 + gw <= this.left + this.width) {
         RulePanel.dashed(g, this.font, ah, this.left + aw + 6, this.addY, gw, H, group, mx, my, this.canAdd() ? this::addGroup : null);
      }
   }

   private void drawRow(Gfx g, List<Ui.Hit> hits, RuleList.Row r, int mx, int my, boolean removable) {
      int[] geo = this.geometry(r);
      RulePanel.dropdown(g, this.font, hits, geo[0], r.y, geo[1], Ui.t(r.field.langKey()), mx, my, () -> {
         this.closeOverlays();
         this.menuRow = r;
         this.menuOp = false;
         this.menuAt = new int[]{geo[0], r.y};
      });
      RulePanel.dropdown(g, this.font, hits, geo[2], r.y, geo[3], Ui.t(ChunkFilter.opKey(r.field, r.op)), mx, my, () -> {
         this.closeOverlays();
         this.menuRow = r;
         this.menuOp = true;
         this.menuAt = new int[]{geo[2], r.y};
      });

      if (r.numberBox != null && !r.field.deep() && !r.field.unit().isEmpty() && r.shown) {
         String unit = Ui.t("chunkatlas.filter.unit." + r.field.unit() + (r.text.equals("1") ? ".one" : ".many"));
         int uw = this.font.width(unit);
         if (geo[5] - uw - 8 >= 36) {
            g.text(this.font, unit, geo[4] + geo[5] - uw - 4, r.y + (H - 8) / 2, Glass.NOTE, false);
         }
      }

      Glass.button(g, this.font, hits, geo[8], r.y, H, H, "✕", Ui.WHITE, mx, my, removable ? () -> this.remove(r) : null);
      if (r.shown && Glass.over(mx, my, geo[0], r.y, geo[1], H) && !this.overlayOpen()) {
         g.tooltip(this.font, List.of(Component.translatable(r.field.langKey() + ".tooltip")), mx, my);
      }
   }

   boolean overlayOpen() {
      return this.menuRow != null || this.menuMatch || this.suggestionBox() != null;
   }

   void closeOverlays() {
      this.menuRow = null;
      this.menuMatch = false;
      this.shown = List.of();
   }

   void drawOverlays(Gfx g, List<Ui.Hit> overlayHits, int mouseX, int mouseY, int screenW, int screenH) {
      if (this.menuRow != null || this.menuMatch) {
         List<Ui.Item> items = new ArrayList<>();
         if (this.menuMatch) {
            items.add(Ui.Item.radio(Ui.t("chunkatlas.filter.match.all"), this.matchAll, () -> this.setMatchAll(true)));
            items.add(Ui.Item.radio(Ui.t("chunkatlas.filter.match.any"), !this.matchAll, () -> this.setMatchAll(false)));
         } else if (this.menuOp) {
            RuleList.Row r = this.menuRow;
            for (ChunkFilter.Op op : r.field.ops()) {
               String sign = Ui.t(ChunkFilter.opKey(r.field, op));
               String words = Ui.t(ChunkFilter.opLongKey(r.field, op));
               items.add(Ui.Item.radio(sign.equals(words) ? sign : sign + "  " + words, op == r.op, () -> this.changeOp(r, op)));
            }
         } else {
            RuleList.Row r = this.menuRow;
            for (ChunkFilter.Field f : this.fields) {
               items.add(Ui.Item.radio(Ui.t(f.langKey()), f == r.field, () -> this.changeField(r, f)));
            }
         }

         int mw = Glass.menuWidth(this.font, items);
         int mh = Glass.menuHeight(items);
         int mx = Math.max(2, Math.min(this.menuAt[0], screenW - mw - 2));
         int my = this.menuAt[1] + H + 1;
         if (my + mh > screenH - 2) {
            my = Math.max(2, this.menuAt[1] - mh - 1);
         }

         g.layer();
         Glass.menu(g, this.font, overlayHits, items, mx, my, mouseX, mouseY);
         return;
      }

      this.drawSuggestions(g, mouseX, mouseY, screenW, screenH);
   }

   boolean mouseClicked(double x, double y, List<Ui.Hit> overlayHits) {
      if (this.menuRow != null || this.menuMatch) {
         for (Ui.Hit h : List.copyOf(overlayHits)) {
            if (h.has(x, y)) {
               h.action().run();
               this.menuRow = null;
               this.menuMatch = false;
               return true;
            }
         }

         this.menuRow = null;
         this.menuMatch = false;
         return true;
      }

      int[] b = this.suggestionBox();
      if (b != null && x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) {
         int n = (int)((y - b[1] - 2) / SUGGEST_ROW);
         if (n >= 0 && n < this.shown.size()) {
            this.accept(this.shown.get(n));
         }

         return true;
      }

      return false;
   }

   boolean keyPressed(int key, int scan, int mods) {
      if ((this.menuRow != null || this.menuMatch) && key == InputConstants.KEY_ESCAPE) {
         this.menuRow = null;
         this.menuMatch = false;
         return true;
      }

      if (this.suggestionBox() != null) {
         if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
            int n = this.shown.size();
            this.highlighted = (this.highlighted + (key == InputConstants.KEY_DOWN ? 1 : n - 1)) % n;
            return true;
         }

         if (key == InputConstants.KEY_TAB || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            this.accept(this.shown.get(Math.min(this.highlighted, this.shown.size() - 1)));
            return true;
         }

         if (key == InputConstants.KEY_ESCAPE) {
            this.shown = List.of();
            return true;
         }
      }

      return false;
   }

   private void setMatchAll(boolean all) {
      if (this.matchAll != all) {
         this.matchAll = all;
         this.onChange.run();
      }
   }

   private void changeOp(RuleList.Row row, ChunkFilter.Op op) {
      if (row.op == op) {
         return;
      }

      boolean before = row.field.takesNumber(row.op);
      row.op = op;
      if (before != row.field.takesNumber(op)) {
         this.dirty = true;
      }

      this.onChange.run();
   }

   private void changeField(RuleList.Row old, ChunkFilter.Field field) {
      if (old.field == field) {
         return;
      }

      ChunkFilter.Op op = field.ops().contains(old.op) ? old.op : field.defaultOp();
      RuleList.Row now = new RuleList.Row(field, op, field.takesNumber(op) && !old.field.deep() && !field.deep() ? old.text : "");
      this.replace(old, now);
      this.dirty = true;
      this.onChange.run();
   }

   private void replace(RuleList.Row old, RuleList.Row now) {
      int at = this.items.indexOf(old);
      if (at >= 0) {
         this.items.set(at, now);
         return;
      }

      for (Object it : this.items) {
         if (it instanceof RuleList.Group g) {
            int n = g.rows.indexOf(old);
            if (n >= 0) {
               g.rows.set(n, now);
               return;
            }
         }
      }
   }

   private ChunkFilter.Field nextField() {
      List<RuleList.Row> rows = this.rows();
      for (ChunkFilter.Field f : this.fields) {
         if (rows.stream().noneMatch(r -> r.field == f)) {
            return f;
         }
      }

      return this.fields.get(0);
   }

   void addRule(RuleList.Group group) {
      if (!this.canAdd()) {
         return;
      }

      RuleList.Row row = RuleList.Row.blank(this.nextField());
      if (group == null) {
         this.items.add(row);
      } else {
         group.rows.add(row);
      }

      this.dirty = true;
   }

   void addGroup() {
      if (this.canAdd()) {
         RuleList.Group g = new RuleList.Group();
         g.rows.add(RuleList.Row.blank(this.nextField()));
         this.items.add(g);
         this.dirty = true;
      }
   }

   private void remove(RuleList.Row row) {
      if (this.items.remove(row)) {
         this.afterRemove();
         return;
      }

      for (Object it : this.items) {
         if (it instanceof RuleList.Group g && g.rows.remove(row)) {
            if (g.rows.isEmpty()) {
               this.items.remove(g);
            }

            this.afterRemove();
            return;
         }
      }
   }

   private void removeGroup(RuleList.Group g) {
      if (this.items.remove(g)) {
         this.afterRemove();
      }
   }

   private void afterRemove() {
      if (this.items.isEmpty()) {
         this.items.add(RuleList.Row.blank(ChunkFilter.Field.TIME_PLAYED));
      }

      this.dirty = true;
      this.onChange.run();
   }

   List<RuleList.Quick> quickPicks() {
      List<RuleList.Quick> out = new ArrayList<>();
      out.add(new RuleList.Quick("chunkatlas.filter.quick.barely_visited", new ChunkFilter.Condition(ChunkFilter.Field.TIME_PLAYED, ChunkFilter.Op.LESS, 2L)));
      out.add(new RuleList.Quick("chunkatlas.filter.quick.untouched", new ChunkFilter.Condition(ChunkFilter.Field.LAST_SAVED, ChunkFilter.Op.AT_LEAST, 30L)));
      if (this.hasSpawn) {
         out.add(new RuleList.Quick("chunkatlas.filter.quick.far", new ChunkFilter.Condition(ChunkFilter.Field.DISTANCE_SPAWN, ChunkFilter.Op.AT_LEAST, 2000L)));
      }

      out.add(new RuleList.Quick("chunkatlas.filter.quick.mobs", new ChunkFilter.Condition(ChunkFilter.Field.MOBS, ChunkFilter.Op.AT_LEAST, 50L)));
      if (this.canLookInside) {
         out.add(new RuleList.Quick("chunkatlas.rules.quick.village", new ChunkFilter.Condition(ChunkFilter.Field.STRUCTURE, ChunkFilter.Op.HAS, 0L, "#village")));
         out.add(new RuleList.Quick("chunkatlas.rules.quick.ancient_city", new ChunkFilter.Condition(ChunkFilter.Field.STRUCTURE, ChunkFilter.Op.HAS, 0L, "minecraft:ancient_city")));
         out.add(new RuleList.Quick("chunkatlas.rules.quick.diamond_ore", new ChunkFilter.Condition(ChunkFilter.Field.BLOCK, ChunkFilter.Op.HAS, 0L, "#diamond_ore")));
      }

      return out;
   }

   void quick(ChunkFilter.Condition c) {
      RuleList.Row made = this.row(c);
      RuleList.Row at = null;
      for (Object it : this.items) {
         if (it instanceof RuleList.Row r && r.field == c.field() && (!c.field().deep() || r.id.equals(c.id()))) {
            at = r;
            break;
         }
      }

      if (at == null) {
         for (Object it : this.items) {
            if (it instanceof RuleList.Row r && r.blank()) {
               at = r;
               break;
            }
         }
      }

      if (at != null) {
         this.replace(at, made);
      } else if (this.canAdd()) {
         this.items.add(made);
      } else {
         return;
      }

      this.dirty = true;
      this.onChange.run();
   }

   private List<RuleList.Suggestion> blockNames() {
      List<String> ids = new ArrayList<>();
      Map<String, String> labels = new HashMap<>();
      for (String s : Reg.blockIds()) {
         if (!s.equals("minecraft:air")) {
            ids.add(s);
            labels.put(s, Reg.blockName(s));
         }
      }

      return this.suggestions(ids, true, labels);
   }

   private <T> List<RuleList.Suggestion> worldNames(ResourceKey<? extends Registry<? extends T>> key, List<String> vanilla, boolean structure) {
      Set<String> ids = new LinkedHashSet<>(vanilla);
      try {
         if (this.mc.level != null) {
            ids.addAll(Reg.ids(this.mc.level.registryAccess(), key));
         }
      } catch (RuntimeException ignored) {
      }

      Map<String, String> labels = new HashMap<>();
      for (String id : ids) {
         String lang = "biome." + id.replace(':', '.');
         labels.put(id, !structure && Language.getInstance().has(lang) ? Ui.t(lang) : capital(RuleIds.plain(id)));
      }

      return this.suggestions(new ArrayList<>(ids), false, labels);
   }

   private List<RuleList.Suggestion> suggestions(List<String> ids, boolean block, Map<String, String> labels) {
      List<RuleList.Suggestion> out = new ArrayList<>();
      for (String g : RuleIds.groups(ids, block)) {
         out.add(new RuleList.Suggestion(g, Ui.t("chunkatlas.rules.group", RuleIds.plain(g))));
      }

      List<RuleList.Suggestion> rest = new ArrayList<>();
      for (String id : ids) {
         rest.add(new RuleList.Suggestion(id, labels.getOrDefault(id, capital(RuleIds.plain(id)))));
      }

      rest.sort(Comparator.comparing(RuleList.Suggestion::label, String.CASE_INSENSITIVE_ORDER));
      out.addAll(rest);
      return List.copyOf(out);
   }

   private static String capital(String s) {
      return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
   }

   private String resolve(ChunkFilter.Field field, String text) {
      String t = text.trim();
      for (RuleList.Suggestion s : this.names.getOrDefault(field, List.of())) {
         if (s.label().equalsIgnoreCase(t)) {
            return s.id();
         }
      }

      return RuleIds.normalize(t);
   }

   private boolean known(ChunkFilter.Field field, String id) {
      List<RuleList.Suggestion> list = this.names.get(field);
      return list == null || id.isEmpty() || list.stream().anyMatch(s -> s.id().equals(id));
   }

   private void suggest() {
      RuleList.Row row = this.suggesting;
      this.shown = List.of();
      this.highlighted = 0;
      if (row == null || row.nameBox == null) {
         return;
      }

      String q = row.idText.trim().toLowerCase(Locale.ROOT);
      if (q.isEmpty()) {
         return;
      }

      String qid = RuleIds.normalize(q);
      String qpath = qid.startsWith("#") ? qid.substring(1) : qid.substring(qid.indexOf(':') + 1);
      List<RuleList.Suggestion> starts = new ArrayList<>();
      List<RuleList.Suggestion> holds = new ArrayList<>();
      for (RuleList.Suggestion s : this.names.getOrDefault(row.field, List.of())) {
         String label = s.label().toLowerCase(Locale.ROOT);
         String path = s.id().substring(s.id().indexOf(':') + 1).replace("#", "");
         if (label.startsWith(q) || path.startsWith(qpath) || s.id().equals(qid)) {
            starts.add(s);
         } else if (label.contains(q) || path.contains(qpath)) {
            holds.add(s);
         }
      }

      starts.addAll(holds);
      if (starts.size() == 1 && starts.get(0).id().equals(row.id)) {
         return;
      }

      this.shown = starts.subList(0, Math.min(SUGGEST_MAX, starts.size()));
   }

   private int[] suggestionBox() {
      RuleList.Row row = this.suggesting;
      if (row == null || row.nameBox == null || this.shown.isEmpty() || !row.nameBox.isFocused() || !row.nameBox.visible) {
         return null;
      }

      EditBox box = row.nameBox;
      int h = this.shown.size() * SUGGEST_ROW + 4;
      int y = box.getY() + box.getHeight();
      int screenH = this.mc.getWindow().getGuiScaledHeight();
      if (y + h > screenH - 2) {
         y = box.getY() - h;
      }

      int w = box.getWidth();
      for (RuleList.Suggestion s : this.shown) {
         w = Math.max(w, this.font.width(s.id()) + this.font.width(s.label()) + 16);
      }

      w = Math.min(w, this.mc.getWindow().getGuiScaledWidth() - box.getX() - 2);
      return new int[]{box.getX(), y, w, h};
   }

   private void drawSuggestions(Gfx g, int mouseX, int mouseY, int screenW, int screenH) {
      int[] b = this.suggestionBox();
      if (b == null) {
         return;
      }

      g.layer();
      g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], Glass.MENU);
      g.outline(b[0], b[1], b[2], b[3], Glass.MENU_EDGE);
      for (int n = 0; n < this.shown.size(); n++) {
         int y = b[1] + 2 + n * SUGGEST_ROW;
         boolean over = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= y && mouseY < y + SUGGEST_ROW;
         if (over || n == this.highlighted) {
            g.fill(b[0] + 1, y, b[0] + b[2] - 1, y + SUGGEST_ROW, Glass.HOT);
         }

         RuleList.Suggestion s = this.shown.get(n);
         String id = this.font.plainSubstrByWidth(s.id(), b[2] - 8);
         g.text(this.font, id, b[0] + 4, y + 2, RuleIds.isGroup(s.id()) ? Glass.ACCENT : Ui.WHITE);
         int lx = b[0] + 4 + this.font.width(id) + 8;
         if (lx < b[0] + b[2] - 12) {
            g.text(this.font, this.font.plainSubstrByWidth(s.label(), b[0] + b[2] - 4 - lx), lx, y + 2, Glass.LABEL);
         }
      }
   }

   private void accept(RuleList.Suggestion s) {
      RuleList.Row row = this.suggesting;
      if (row == null || row.nameBox == null) {
         return;
      }

      row.nameBox.setValue(s.id());
      row.idText = s.id();
      row.id = s.id();
      this.shown = List.of();
      this.onChange.run();
   }

   static final class Row {
      final ChunkFilter.Field field;
      ChunkFilter.Op op;
      String text;

      String id = "";
      String idText = "";
      EditBox numberBox;
      EditBox nameBox;
      int x;
      int y;
      int w;

      boolean shown;

      Row(ChunkFilter.Field field, ChunkFilter.Op op, String text) {
         this.field = field;
         this.op = op;
         this.text = text;
      }

      boolean blank() {
         return this.field.deep() ? this.id.isEmpty() : !this.field.flag() && this.text.isEmpty();
      }

      ChunkFilter.Condition condition(int group) {
         long value = 0L;
         if (this.field.takesNumber(this.op)) {
            try {
               value = Long.parseLong(this.text);
            } catch (NumberFormatException e) {
               return null;
            }
         }

         if (this.field.deep() && this.id.isEmpty()) {
            return null;
         }

         return new ChunkFilter.Condition(this.field, this.op, value, this.id, group);
      }

      static RuleList.Row blank(ChunkFilter.Field field) {
         return new RuleList.Row(field, field.defaultOp(), "");
      }
   }

   static final class Group {
      final List<RuleList.Row> rows = new ArrayList<>();
      int x;
      int y;
      int w;
      int h;
      int addY;
   }

   record Quick(String key, ChunkFilter.Condition condition) {
   }

   private record Suggestion(String id, String label) {
   }
}
