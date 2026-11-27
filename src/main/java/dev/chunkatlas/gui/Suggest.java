package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.ChunkFields;
import dev.chunkatlas.engine.RuleIds;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import dev.chunkatlas.port.Reg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;

final class Suggest {
   record Option(String value, String label) {
      static Suggest.Option of(String value) {
         return new Suggest.Option(value, "");
      }
   }

   static final int ROW = 12;
   private static final int MAX = 8;
   private final Font font;
   private EditBox box;
   private List<Suggest.Option> options = List.of();
   private ToIntFunction<String> tokenStart = s -> 0;
   private List<Suggest.Option> shown = List.of();
   private int highlighted;
   private int start;
   private String typed = "";

   private String hiddenFor;

   Suggest(Font font) {
      this.font = font;
   }

   void edit(EditBox box, List<Suggest.Option> options) {
      this.edit(box, options, s -> 0);
   }

   void edit(EditBox box, List<Suggest.Option> options, ToIntFunction<String> tokenStart) {
      if (this.box != box) {
         this.detach();
         this.hiddenFor = null;
      }

      this.box = box;
      this.options = options == null ? List.of() : options;
      this.tokenStart = tokenStart;
      this.refresh();
   }

   void options(List<Suggest.Option> options) {
      this.options = options == null ? List.of() : options;
      if (this.box != null) {
         this.refresh();
      }
   }

   EditBox box() {
      return this.box;
   }

   boolean open() {
      return this.box != null && this.box.isFocused() && this.box.visible && !this.shown.isEmpty();
   }

   void close() {
      this.shown = List.of();
      if (this.box != null) {
         this.box.setSuggestion(null);
      }
   }

   void detach() {
      this.close();
      this.box = null;
      this.options = List.of();
   }

   void refresh() {
      EditBox b = this.box;
      this.shown = List.of();
      this.highlighted = 0;
      if (b == null) {
         return;
      }

      String text = b.getValue();
      if (this.hiddenFor != null && !this.hiddenFor.equals(text)) {
         this.hiddenFor = null;
      }

      this.start = Math.max(0, Math.min(text.length(), this.tokenStart.applyAsInt(text)));
      this.typed = text.substring(this.start).strip();
      String q = this.typed.toLowerCase(Locale.ROOT);
      List<Suggest.Option> starts = new ArrayList<>();
      List<Suggest.Option> holds = new ArrayList<>();
      for (Suggest.Option o : this.options) {
         String v = o.value().toLowerCase(Locale.ROOT);
         String tail = v.substring(v.indexOf(':') + 1);
         String label = o.label().toLowerCase(Locale.ROOT);
         if (q.isEmpty() || v.startsWith(q) || tail.startsWith(q) || label.startsWith(q)) {
            starts.add(o);
         } else if (v.contains(q) || label.contains(q)) {
            holds.add(o);
         }
      }

      starts.addAll(holds);
      if (starts.size() == 1 && starts.get(0).value().equalsIgnoreCase(this.typed)) {
         starts.clear();
      }

      if (this.hiddenFor != null) {
         starts.clear();
      }

      this.shown = starts.size() > MAX ? List.copyOf(starts.subList(0, MAX)) : List.copyOf(starts);
      this.inline();
   }

   private void inline() {
      EditBox b = this.box;
      if (b == null) {
         return;
      }

      if (this.shown.isEmpty() || this.typed.isEmpty() || !b.isFocused()) {
         b.setSuggestion(null);
         return;
      }

      String best = this.shown.get(Math.min(this.highlighted, this.shown.size() - 1)).value();
      b.setSuggestion(best.toLowerCase(Locale.ROOT).startsWith(this.typed.toLowerCase(Locale.ROOT)) && b.getCursorPosition() == b.getValue().length()
         ? best.substring(this.typed.length())
         : null);
   }

   void accept(Suggest.Option o) {
      EditBox b = this.box;
      if (b == null) {
         return;
      }

      String text = b.getValue();
      int at = Math.min(this.start, text.length());
      String rest = text.substring(at);
      String lead = rest.substring(0, rest.length() - rest.stripLeading().length());
      b.setSuggestion(null);
      b.setValue(text.substring(0, at) + lead + o.value());
      b.moveCursorToEnd(false);
      this.close();
   }

   int[] bounds(int screenW, int screenH) {
      if (!this.open()) {
         return null;
      }

      int h = this.shown.size() * ROW + 4;
      int y = this.box.getY() + this.box.getHeight() + 1;
      if (y + h > screenH - 2) {
         y = this.box.getY() - 1 - h;
      }

      int w = this.box.getWidth();
      for (Suggest.Option o : this.shown) {
         w = Math.max(w, this.font.width(o.value()) + (o.label().isEmpty() ? 0 : this.font.width(o.label()) + 12) + 10);
      }

      int x = this.box.getX();
      w = Math.min(w, screenW - x - 4);
      return new int[]{x, y, w, h};
   }

   void draw(Gfx g, int screenW, int screenH, int mouseX, int mouseY) {
      int[] b = this.bounds(screenW, screenH);
      if (b == null) {
         return;
      }

      g.layer();
      g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0xF0101216);
      g.outline(b[0], b[1], b[2], b[3], 0x60FFFFFF);
      String q = this.typed.toLowerCase(Locale.ROOT);
      for (int n = 0; n < this.shown.size(); n++) {
         int y = b[1] + 2 + n * ROW;
         boolean over = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= y && mouseY < y + ROW;
         if (over || n == this.highlighted) {
            g.fill(b[0] + 1, y, b[0] + b[2] - 1, y + ROW, over && n != this.highlighted ? 0x24FFFFFF : 0x40FFFFFF);
         }

         Suggest.Option o = this.shown.get(n);
         int room = b[2] - 8;
         String v = this.font.plainSubstrByWidth(o.value(), room);
         int x = b[0] + 4;
         int at = q.isEmpty() ? -1 : v.toLowerCase(Locale.ROOT).indexOf(q);
         if (at < 0) {
            g.text(this.font, v, x, y + 2, Ui.WHITE);
         } else {
            String before = v.substring(0, at);
            String match = v.substring(at, Math.min(v.length(), at + q.length()));
            String after = v.substring(Math.min(v.length(), at + q.length()));
            g.text(this.font, before, x, y + 2, Ui.WHITE);
            g.text(this.font, match, x + this.font.width(before), y + 2, Glass.ACCENT);
            g.text(this.font, after, x + this.font.width(before) + this.font.width(match), y + 2, Ui.WHITE);
         }

         int lx = x + this.font.width(v) + 8;
         if (!o.label().isEmpty() && lx < b[0] + b[2] - 12) {
            g.text(this.font, this.font.plainSubstrByWidth(o.label(), b[0] + b[2] - 4 - lx), lx, y + 2, Ui.GREY);
         }
      }
   }

   boolean mouseClicked(double x, double y, int screenW, int screenH) {
      int[] b = this.bounds(screenW, screenH);
      if (b == null || x < b[0] || x >= b[0] + b[2] || y < b[1] || y >= b[1] + b[3]) {
         return false;
      }

      int n = (int)((y - b[1] - 2) / ROW);
      if (n >= 0 && n < this.shown.size()) {
         this.accept(this.shown.get(n));
      }

      return true;
   }

   boolean keyPressed(int key, int scan, int mods) {
      if (!this.open()) {
         return false;
      }

      if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
         int n = this.shown.size();
         this.highlighted = (this.highlighted + (key == InputConstants.KEY_DOWN ? 1 : n - 1)) % n;
         this.inline();
         return true;
      }

      if (key == InputConstants.KEY_TAB || Keys.confirm(key)) {
         this.accept(this.shown.get(Math.min(this.highlighted, this.shown.size() - 1)));
         return true;
      }

      if (Keys.escape(key)) {
         this.hiddenFor = this.box.getValue();
         this.close();
         return true;
      }

      return false;
   }

   static String keysHint() {
      return Ui.t("chunkatlas.suggest.keys");
   }

   private static List<Suggest.Option> blocks;

   static List<Suggest.Option> statuses() {
      List<Suggest.Option> out = new ArrayList<>();
      for (String s : ChunkFields.STATUSES) {
         out.add(Suggest.Option.of(s));
      }

      return List.copyOf(out);
   }

   static List<Suggest.Option> blocks() {
      if (blocks == null) {
         List<Suggest.Option> out = new ArrayList<>();
         for (String id : Reg.blockIds()) {
            out.add(new Suggest.Option(id, Reg.blockName(id)));
         }

         out.sort(Comparator.comparing(Suggest.Option::value));
         blocks = List.copyOf(out);
      }

      return blocks;
   }

   static List<Suggest.Option> biomes() {
      Set<String> ids = new LinkedHashSet<>(RuleIds.VANILLA_BIOMES);
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            ids.addAll(Reg.ids(mc.level.registryAccess(), Registries.BIOME));
         }
      } catch (RuntimeException ignored) {
      }

      List<Suggest.Option> out = new ArrayList<>();
      for (String id : ids) {
         String lang = "biome." + id.replace(':', '.');
         out.add(new Suggest.Option(id, Language.getInstance().has(lang) ? Ui.t(lang) : ""));
      }

      out.sort(Comparator.comparing(Suggest.Option::value));
      return List.copyOf(out);
   }

   static List<Suggest.Option> booleans() {
      return List.of(new Suggest.Option("0", Ui.t("chunkatlas.suggest.false")), new Suggest.Option("1", Ui.t("chunkatlas.suggest.true")));
   }
}
