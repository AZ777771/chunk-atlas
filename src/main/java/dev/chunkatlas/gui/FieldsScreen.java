package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.ChunkFields;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Input;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class FieldsScreen extends AtlasScreen {
   private static final String K = "chunkatlas.fields.";
   private static final int BOX_H = 16;
   private static final int ROW_H = 20;
   private static final int HEADING_H = 14;
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final int chunks;

   private final boolean serverOnly;

   private final BiConsumer<ChunkFields.Changes, Boolean> apply;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private final List<Ui.Hit> menuHits = new ArrayList<>();
   private final List<FieldsScreen.Group> groups = new ArrayList<>();
   private final List<FieldsScreen.Row> rows = new ArrayList<>();
   private final Suggest suggest;

   private FieldsScreen.Row dropped;
   private int scroll;
   private int contentH;
   private String problem;
   private int ticked;
   private int px;
   private int py;
   private int pw;
   private int ph;
   private int labelX;
   private int labelW;
   private int controlX;
   private int controlW;
   private int forceX;
   private int contentTop;
   private int contentBottom;
   private int footerY;

   private enum Kind {
      STATUS,
      NUMBER,
      TEXT,
      SECTIONS,
      FLAG,

      CHOICE;
   }

   private static final class Row {
      final String key;
      final FieldsScreen.Kind kind;
      final String forceKey;
      final String unit;
      final boolean described;
      boolean on;
      boolean force;
      EditBox box;
      EditBox box2;
      String status = ChunkFields.STATUSES.get(0);
      boolean choice = true;
      String text = "";
      String text2 = "";
      int y;
      int h;

      Row(String key, FieldsScreen.Kind kind, String forceKey, String unit, boolean described) {
         this.key = key;
         this.kind = kind;
         this.forceKey = forceKey;
         this.unit = unit;
         this.described = described;
         this.h = ROW_H;
      }
   }

   private record Group(String key, List<FieldsScreen.Row> rows) {
   }

   FieldsScreen(MapScreen map, int chunks, boolean serverOnly, BiConsumer<ChunkFields.Changes, Boolean> apply) {
      super(Component.translatable(K + "title"));
      this.map = map;
      this.chunks = chunks;
      this.serverOnly = serverOnly;
      this.apply = apply;
      this.suggest = new Suggest(this.font);
      this.groups.add(new FieldsScreen.Group("values", List.of(
         new FieldsScreen.Row("status", FieldsScreen.Kind.STATUS, ChunkFields.FORCE_STATUS, null, false),
         new FieldsScreen.Row("inhabited", FieldsScreen.Kind.NUMBER, ChunkFields.FORCE_INHABITED, Ui.t(K + "unit.min"), false),
         new FieldsScreen.Row("last_update", FieldsScreen.Kind.NUMBER, ChunkFields.FORCE_LAST_UPDATE, Ui.t(K + "unit.ticks"), false),
         new FieldsScreen.Row("data_version", FieldsScreen.Kind.NUMBER, ChunkFields.FORCE_DATA_VERSION, null, false),
         new FieldsScreen.Row("biome", FieldsScreen.Kind.TEXT, ChunkFields.FORCE_BIOME, null, false)
      )));
      this.groups.add(new FieldsScreen.Group("blocks", List.of(
         new FieldsScreen.Row("replace", FieldsScreen.Kind.TEXT, null, null, false),
         new FieldsScreen.Row("delete_sections", FieldsScreen.Kind.SECTIONS, null, Ui.t(K + "unit.section_y"), false)
      )));
      this.groups.add(new FieldsScreen.Group("clean_up", List.of(
         new FieldsScreen.Row("structures", FieldsScreen.Kind.FLAG, null, null, true),
         new FieldsScreen.Row("lighting", FieldsScreen.Kind.FLAG, null, null, true),
         new FieldsScreen.Row("heightmaps", FieldsScreen.Kind.FLAG, null, null, true)
      )));
      this.groups.add(new FieldsScreen.Group("worldgen", List.of(
         new FieldsScreen.Row("prevent_retrogen", FieldsScreen.Kind.FLAG, null, null, true),
         new FieldsScreen.Row("force_blending", FieldsScreen.Kind.FLAG, null, null, true)
      )));
      this.groups.add(new FieldsScreen.Group("loading", List.of(new FieldsScreen.Row("force_load", FieldsScreen.Kind.CHOICE, null, null, false))));
      for (FieldsScreen.Group g : this.groups) {
         this.rows.addAll(g.rows());
      }

      this.row("force_blending").h = ROW_H + BLEND_H;
   }

   private static final int BLEND_H = 16;

   private Integer lift;

   @Override
   protected void init() {
      this.suggest.detach();
      this.dropped = null;
      this.layout();
      int shortW = Math.min(64, this.controlW);
      int y = 0;
      for (FieldsScreen.Group g : this.groups) {
         y += y == 0 ? 2 : 4;
         y += HEADING_H;
         for (FieldsScreen.Row r : g.rows()) {
            r.y = y;
            switch (r.kind) {
               case NUMBER -> r.box = this.box(r, this.controlX, shortW, r.text, Ui.t(K + "hint." + r.key), 12);
               case TEXT -> r.box = this.box(r, this.controlX, this.controlW, r.text, Ui.t(K + "hint." + r.key), 400);
               case SECTIONS -> {
                  int w = Math.min(40, (this.controlW - 14) / 2);
                  r.box = this.box(r, this.controlX, w, r.text, Ui.t(K + "hint.from"), 4);
                  r.box2 = this.box(r, this.controlX + w + 14, w, r.text2, Ui.t(K + "hint.to"), 4);
               }
               default -> {
               }
            }

            this.refresh(r);
            y += r.h;
         }
      }

      this.contentH = y + 4;
      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
      this.position();
   }

   private EditBox box(FieldsScreen.Row r, int x, int w, String text, String hint, int max) {
      EditBox b = new EditBox(this.font, x, 0, w, BOX_H, Component.translatable(K + r.key));
      b.setMaxLength(max);
      b.setHint(Component.literal(hint));
      b.setValue(text);
      b.setResponder(s -> {
         if (b == r.box) {
            r.text = s;
         } else {
            r.text2 = s;
         }

         if (b.isFocused()) {
            this.suggestFor(r, b);
         }
      });
      return this.addRenderableWidget(b);
   }

   private void suggestFor(FieldsScreen.Row r, EditBox b) {
      if (r.key.equals("biome")) {
         this.suggest.edit(b, Suggest.biomes());
      } else if (r.key.equals("replace")) {
         this.suggest.edit(b, Suggest.blocks(), s -> Math.max(s.lastIndexOf(','), s.lastIndexOf('=')) + 1);
      } else {
         this.suggest.close();
      }
   }

   private void layout() {
      this.pw = Math.max(280, Math.min(420, this.width - 8 - 140));
      this.px = Glass.MARGIN;
      this.py = Glass.MARGIN;
      this.ph = this.height - 2 * Glass.MARGIN;
      this.footerY = this.py + this.ph - 6 - BOX_H;
      this.contentTop = this.py + 20 + (this.headerFits() ? 0 : 10) + 12;
      this.contentBottom = this.footerY - 8;
      this.labelX = this.px + 20;
      this.labelW = Math.min(90, (this.pw - 40) / 3);
      int forceW = 34;
      this.controlX = this.labelX + this.labelW + 4;
      this.controlW = this.px + this.pw - 8 - forceW - this.controlX;
      this.forceX = this.px + this.pw - 8 - forceW + 13;
   }

   private String heading() {
      return Ui.plural(K + "heading", this.chunks);
   }

   private boolean headerFits() {
      return true;
   }

   private int maxScroll() {
      return Math.max(0, this.contentH - (this.contentBottom - this.contentTop));
   }

   private void position() {
      for (FieldsScreen.Row r : this.rows) {
         for (EditBox b : new EditBox[]{r.box, r.box2}) {
            if (b != null) {
               int y = this.contentTop + r.y + 2 - this.scroll;
               b.setY(y);
               b.visible = y >= this.contentTop && y + BOX_H <= this.contentBottom;
            }
         }
      }
   }

   private void refresh(FieldsScreen.Row r) {
      for (EditBox b : new EditBox[]{r.box, r.box2}) {
         if (b != null) {
            b.setEditable(r.on);
            b.setTextColor(r.on ? Ui.WHITE : 0xFF707070);
            if (!r.on && b.isFocused()) {
               b.setFocused(false);
               this.setFocused(null);
               if (this.suggest.box() == b) {
                  this.suggest.detach();
               }
            }
         }
      }
   }

   private boolean usable(FieldsScreen.Row r) {
      return !this.serverOnly || r.kind == FieldsScreen.Kind.CHOICE;
   }

   private void toggle(FieldsScreen.Row r) {
      r.on = !r.on;
      this.refresh(r);
      if (r.on && r.box != null) {
         this.setFocused(r.box);
         r.box.setFocused(true);
         this.suggestFor(r, r.box);
      }
   }

   void tourTick(String key, String value) {
      FieldsScreen.Row r = this.row(key);
      r.on = true;
      this.refresh(r);
      if (value != null && r.box != null) {
         r.box.setValue(value);
      } else if (value != null && r.kind == FieldsScreen.Kind.CHOICE) {
         r.choice = !value.equalsIgnoreCase("off");
      }
   }

   Integer tourLift() {
      return this.lift;
   }

   void tourGo() {
      this.go();
   }

   private FieldsScreen.Row row(String key) {
      for (FieldsScreen.Row r : this.rows) {
         if (r.key.equals(key)) {
            return r;
         }
      }

      throw new IllegalArgumentException(key);
   }

   private static final class Bad extends RuntimeException {
      Bad(String why) {
         super(why, null, false, false);
      }
   }

   private static long whole(String s, String key) {
      try {
         return Long.parseLong(s.trim());
      } catch (NumberFormatException e) {
         throw new FieldsScreen.Bad(Ui.t(K + "bad." + key));
      }
   }

   private ChunkFields.Changes read() {
      Set<String> force = new HashSet<>();
      for (FieldsScreen.Row r : this.rows) {
         if (r.on && r.force && r.forceKey != null) {
            force.add(r.forceKey);
         }
      }

      FieldsScreen.Row status = this.row("status");
      FieldsScreen.Row inhabited = this.row("inhabited");
      FieldsScreen.Row last = this.row("last_update");
      FieldsScreen.Row version = this.row("data_version");
      FieldsScreen.Row biome = this.row("biome");
      FieldsScreen.Row replace = this.row("replace");
      FieldsScreen.Row sections = this.row("delete_sections");
      Long ticks = inhabited.on ? whole(inhabited.text, "inhabited") * 60L * 20L : null;
      if (ticks != null && ticks < 0L) {
         throw new FieldsScreen.Bad(Ui.t(K + "bad.inhabited"));
      }

      Long lastUpdate = last.on ? whole(last.text, "last_update") : null;
      Integer dataVersion = null;
      if (version.on) {
         long v = whole(version.text, "data_version");
         if (v <= 0L || v > Integer.MAX_VALUE) {
            throw new FieldsScreen.Bad(Ui.t(K + "bad.data_version"));
         }

         dataVersion = (int)v;
      }

      String biomeId = null;
      if (biome.on) {
         String b = biome.text.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
         if (b.isEmpty() || !b.matches("[a-z0-9_.:/-]+")) {
            throw new FieldsScreen.Bad(Ui.t(K + "bad.biome"));
         }

         biomeId = b.contains(":") ? b : "minecraft:" + b;
      }

      Map<String, String> replaced = null;
      if (replace.on) {
         replaced = ChunkFields.parseReplace(replace.text);
         if (replaced.isEmpty()) {
            throw new FieldsScreen.Bad(Ui.t(K + "bad.replace"));
         }
      }

      FieldsScreen.Row load = this.row("force_load");
      if (load.on && load.choice && this.chunks > ForceLoad.MOST) {
         throw new FieldsScreen.Bad(Ui.t(K + "bad.force_load", ForceLoad.MOST));
      }

      Integer from = null;
      Integer to = null;
      if (sections.on) {
         from = (int)whole(sections.text, "sections");
         to = (int)whole(sections.text2, "sections");
         if (from < -64 || from > 64 || to < -64 || to > 64) {
            throw new FieldsScreen.Bad(Ui.t(K + "bad.sections"));
         }
      }

      return new ChunkFields.Changes(
         status.on ? status.status : null,
         ticks,
         lastUpdate,
         dataVersion,
         biomeId,
         replaced,
         from,
         to,
         this.row("structures").on,
         this.row("lighting").on,
         this.row("heightmaps").on,
         force,
         this.row("prevent_retrogen").on,
         this.row("force_blending").on
      );
   }

   private void check() {
      this.ticked = 0;
      for (FieldsScreen.Row r : this.rows) {
         if (r.on) {
            this.ticked++;
         }
      }

      try {
         this.read();
         this.problem = null;
      } catch (FieldsScreen.Bad bad) {
         this.problem = bad.getMessage();
      }
   }

   private boolean ready() {
      return this.ticked > 0 && this.problem == null;
   }

   private void go() {
      this.check();
      if (!this.ready()) {
         return;
      }

      ChunkFields.Changes c = this.read();
      FieldsScreen.Row force = this.row("force_load");
      boolean fields = this.rows.stream().anyMatch(r -> r.on && r != force);
      Game.open(this.mc, this.map);
      this.apply.accept(fields ? c : null, force.on ? force.choice : null);
   }

   @Override
   public void onClose() {
      this.map.endBlendPreview();
      Game.open(this.mc, this.map);
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      this.map.drawUnder(g, partial);

      FieldsScreen.Row blending = this.row("force_blending");
      this.lift = this.map.drawBlendPreview(g, blending.on && this.usable(blending) ? BlendOptions.blend() : 0, BlendOptions.smart());
      this.hits.clear();
      this.menuHits.clear();
      this.check();
      g.layer();
      Glass.panel(g, this.px, this.py, this.pw, this.ph);
      g.fill(this.px, this.py, this.px + this.pw, this.py + this.ph, 0x66000000);
      g.text(this.font, this.heading(), this.px + 6, this.py + 7, Ui.WHITE);

      int rule = this.contentTop - 12;
      g.fill(this.px + 1, rule, this.px + this.pw - 1, rule + 1, Glass.RULE);

      String forceWord = Ui.t(K + "force");
      g.text(this.font, forceWord, this.forceX + 4 - this.font.width(forceWord) / 2, rule + 3, Glass.NOTE);
      this.drawRows(g, mouseX, mouseY);
      this.drawFooter(g, mouseX, mouseY);
      super.draw(g, mouseX, mouseY, partial);
      this.suggest.draw(g, this.width, this.height, mouseX, mouseY);
      if (this.dropped != null) {
         this.drawDropDown(g, mouseX, mouseY);
      }
   }

   private void drawRows(Gfx g, int mouseX, int mouseY) {
      int top = this.contentTop;
      int bottom = this.contentBottom;
      g.scissor(this.px + 1, top, this.px + this.pw - 1, bottom);
      int y = top - this.scroll;
      for (FieldsScreen.Group group : this.groups) {
         y += y == top - this.scroll ? 2 : 4;
         g.text(this.font, Ui.t(K + "group." + group.key()), this.px + 8, y + 3, Glass.LABEL);
         y += HEADING_H;
         for (FieldsScreen.Row r : group.rows()) {
            if (y + r.h >= top && y <= bottom) {
               this.drawRow(g, r, y, mouseX, mouseY);
            }

            y += r.h;
         }
      }

      g.unscissor();
      int max = this.maxScroll();
      if (max > 0) {
         int track = bottom - top;
         int knob = Math.max(10, track * track / Math.max(1, this.contentH));
         int at = top + (track - knob) * this.scroll / max;
         int sx = this.px + this.pw - 3;
         g.fill(sx, top, sx + 2, bottom, 0x20FFFFFF);
         g.fill(sx, at, sx + 2, at + knob, 0x80FFFFFF);
      }
   }

   private void check(Gfx g, int x, int y, boolean on, boolean usable) {
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
   }

   private void drawRow(Gfx g, FieldsScreen.Row r, int y, int mouseX, int mouseY) {
      int tickX = this.px + 8;
      int textY = y + 2 + (BOX_H - 8) / 2;
      int lineH = Math.min(r.h, ROW_H);
      boolean hover = Glass.over(mouseX, mouseY, this.px + 1, Math.max(y, this.contentTop), this.controlX - this.px - 1, Math.min(y + lineH, this.contentBottom) - Math.max(y, this.contentTop));
      boolean usable = this.usable(r);
      if (hover && usable) {
         g.fill(this.px + 1, y, this.controlX - 2, y + lineH, Glass.TINT);
      }

      this.check(g, tickX, textY, r.on, usable);
      int labelColour = r.on && usable ? Ui.WHITE : Glass.OFF;
      g.text(this.font, this.font.plainSubstrByWidth(Ui.t(K + r.key), r.kind == FieldsScreen.Kind.FLAG ? this.px + this.pw - 8 - this.labelX : this.labelW - 2), this.labelX, textY, labelColour);
      if (usable) {
         this.hits.add(new Ui.Hit(this.px + 1, y, this.controlX - 2, y + lineH, () -> this.toggle(r)));
      }

      if (r.key.equals("force_blending")) {
         Glass.Bar options = BlendOptions.row(r.on && usable, this.lift);
         List<Component> tip = options.draw(g, this.font, this.hits, this.labelX - 4, y + ROW_H - 2, mouseX, mouseY, false);
         if (tip != null) {
            g.tooltip(this.font, tip, mouseX, mouseY);
         }
      }

      if ((r.described || !usable) && hover) {
         String tip = usable ? Ui.t(K + r.key + ".desc") : Ui.t("chunkatlas.server.not_supported");
         g.tooltip(this.font, List.of(Component.literal(tip)), mouseX, mouseY);
      }

      switch (r.kind) {
         case STATUS -> this.dropBox(g, r, y + 2, this.controlW, r.status, mouseX, mouseY);
         case CHOICE -> {
            int bw = Math.min(44, this.controlW);
            this.dropBox(g, r, y + 2, bw, Ui.t(K + r.key + (r.choice ? ".on" : ".off")), mouseX, mouseY);
            int dx = this.controlX + bw + 6;
            g.text(this.font, Ui.fit(this.font, Ui.t(K + r.key + ".desc"), Math.max(0, this.px + this.pw - 8 - dx)), dx, textY, Glass.NOTE);
         }
         case NUMBER -> {
            if (r.unit != null && r.box != null) {
               g.text(this.font, r.unit, r.box.getX() + r.box.getWidth() + 5, textY, r.on ? Glass.LABEL : Glass.OFF);
            }
         }
         case SECTIONS -> {
            if (r.box != null && r.box2 != null) {
               String to = Ui.t(K + "to");
               g.text(this.font, to, r.box.getX() + r.box.getWidth() + 7 - this.font.width(to) / 2, textY, r.on ? Glass.LABEL : Glass.OFF);
               int ux = r.box2.getX() + r.box2.getWidth() + 5;
               if (r.unit != null && ux + this.font.width(r.unit) <= this.controlX + this.controlW) {
                  g.text(this.font, r.unit, ux, textY, r.on ? Glass.LABEL : Glass.OFF);
               }
            }
         }
         default -> {
         }
      }

      if (r.forceKey != null) {
         this.check(g, this.forceX, textY, r.force, r.on);
         if (r.on) {
            this.hits.add(new Ui.Hit(this.forceX - 6, y, this.forceX + 14, y + r.h, () -> r.force = !r.force));
         }
      }
   }

   private void dropBox(Gfx g, FieldsScreen.Row r, int by, int w, String text, int mouseX, int mouseY) {
      int bx = this.controlX;
      boolean over = r.on && Glass.over(mouseX, mouseY, bx, by, w, BOX_H);
      g.fill(bx, by, bx + w, by + BOX_H, r.on ? (over || this.dropped == r ? Glass.HOT : Glass.TINT) : 0x10FFFFFF);
      g.outline(bx, by, w, BOX_H, r.on ? (over ? 0xC0FFFFFF : 0x38FFFFFF) : 0x1AFFFFFF);
      g.text(this.font, this.font.plainSubstrByWidth(text, w - 18), bx + 5, by + 4, r.on ? Ui.WHITE : Glass.OFF);
      g.text(this.font, "▾", bx + w - 10, by + 4, r.on ? Glass.SOFT : Glass.OFF);
      if (r.on) {
         this.hits.add(new Ui.Hit(bx, by, bx + w, by + BOX_H, () -> this.dropped = this.dropped == r ? null : r));
      }
   }

   private void drawDropDown(Gfx g, int mouseX, int mouseY) {
      FieldsScreen.Row r = this.dropped;
      List<Ui.Item> items = new ArrayList<>();
      if (r.kind == FieldsScreen.Kind.CHOICE) {
         for (boolean on : new boolean[]{true, false}) {
            items.add(Ui.Item.radio(Ui.t(K + r.key + (on ? ".on" : ".off")), r.choice == on, () -> {
               r.choice = on;
               this.dropped = null;
            }));
         }
      } else {
         for (String s : ChunkFields.STATUSES) {
            items.add(Ui.Item.radio(s, s.equals(r.status), () -> {
               r.status = s;
               this.dropped = null;
            }));
         }
      }

      int w = Ui.menuWidth(this.font, items);
      int h = Ui.menuHeight(items);
      int x = Math.max(4, Math.min(this.controlX, this.width - w - 4));
      int boxY = this.contentTop + r.y + 2 - this.scroll;
      int y = boxY + BOX_H + 1;
      if (y + h > this.height - 4) {
         y = Math.max(4, Math.min(boxY - 1 - h, this.height - h - 4));
      }

      g.layer();
      Ui.menu(g, this.font, this.menuHits, items, x, y, mouseX, mouseY);
   }

   private void drawFooter(Gfx g, int mouseX, int mouseY) {
      int y = this.footerY;
      g.fill(this.px + 1, y - 5, this.px + this.pw - 1, y - 4, Glass.RULE);
      String go = Ui.plural(K + "go", this.chunks);
      String cancel = Ui.t("chunkatlas.map.cancel");
      int gw = Math.max(80, this.font.width(go) + 20);
      int cw = Math.max(56, this.font.width(cancel) + 20);
      int right = this.px + this.pw - 6;
      Glass.button(g, this.font, this.hits, right - gw, y, gw, BOX_H, go, Glass.ACCENT, mouseX, mouseY, this.ready() ? this::go : null);
      Glass.button(g, this.font, this.hits, right - gw - 4 - cw, y, cw, BOX_H, cancel, Ui.WHITE, mouseX, mouseY, this::onClose);
      String text;
      int colour = Glass.NOTE;
      if (this.suggest.open()) {
         text = Suggest.keysHint();
      } else if (this.ticked == 0) {
         text = Ui.t(K + "nothing");
         colour = Ui.YELLOW;
      } else if (this.problem != null) {
         text = this.problem;
         colour = Ui.YELLOW;
      } else {
         text = Ui.plural(K + "ticked", this.ticked);
      }

      int room = right - gw - 4 - cw - 6 - (this.px + 6);
      g.text(this.font, this.font.plainSubstrByWidth(text, Math.max(0, room)), this.px + 6, y + 4, colour);
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      double x = mouseX;
      double y = mouseY;
      if (this.dropped != null) {
         this.dropped = null;
         for (Ui.Hit h : List.copyOf(this.menuHits)) {
            if (h.has(x, y)) {
               h.action().run();
               return true;
            }
         }

         return true;
      }

      if (this.suggest.mouseClicked(x, y, this.width, this.height)) {
         return true;
      }

      if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
         boolean inList = y >= this.contentTop && y < this.contentBottom;
         for (int n = this.hits.size() - 1; n >= 0; n--) {
            Ui.Hit h = this.hits.get(n);
            boolean rowHit = h.y0() >= this.contentTop - 40 && h.y1() <= this.contentBottom + 40 && h.y0() < this.footerY - 5;
            if (h.has(x, y) && (!rowHit || inList)) {
               h.action().run();
               return true;
            }
         }
      }

      boolean taken = super.onClick(mouseX, mouseY, mouseButton, doubleClick);
      FieldsScreen.Row focused = null;
      for (FieldsScreen.Row r : this.rows) {
         if (r.box != null && r.box.isFocused() || r.box2 != null && r.box2.isFocused()) {
            focused = r;
         }
      }

      if (focused != null && focused.on && focused.box != null && focused.box.isFocused()) {
         this.suggestFor(focused, focused.box);
      } else {
         this.suggest.close();
      }

      return taken;
   }

   @Override
   protected boolean onScroll(double x, double y, double scrollX, double scrollY) {
      if (this.dropped != null) {
         return true;
      }

      if (y >= this.contentTop && y < this.contentBottom && this.maxScroll() > 0) {
         this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)Math.round(scrollY * 16.0)));
         this.position();
         return true;
      }

      return super.onScroll(x, y, scrollX, scrollY);
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      if (this.dropped != null && Keys.escape(keyCode)) {
         this.dropped = null;
         return true;
      }

      if (this.suggest.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      }

      if (Keys.escape(keyCode)) {
         this.onClose();
         return true;
      }

      if (Keys.confirm(keyCode) && this.getFocused() instanceof EditBox) {
         this.go();
         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
   }
}
