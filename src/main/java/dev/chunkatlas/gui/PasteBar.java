package dev.chunkatlas.gui;

import dev.chunkatlas.port.Gfx;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class PasteBar {
   private static final int GO = 0xFF7EE07E;
   private static final int ROW_H = Glass.ICON + 4;
   private boolean open;
   private int[] held;
   private int top;
   private int[] card;

   record State(int chunks, String source, int dx, int dz, int dy, boolean smartLift, boolean keep, boolean server, Runnable askX, Runnable askZ,
      Runnable askY, Consumer<Boolean> setKeep, Runnable cancel, Runnable place) {
   }

   int[] held() {
      return this.open ? this.held : null;
   }

   boolean cardOpen() {
      return this.open;
   }

   void close() {
      this.open = false;
      this.held = null;
   }

   int top() {
      return this.top;
   }

   boolean onCard(double x, double y) {
      int[] c = this.card;
      return this.open && c != null && x >= c[0] && x < c[0] + c[2] && y >= c[1] && y < c[1] + c[3];
   }

   static PasteFold.Words words(State s) {
      return new PasteFold.Words(
         Ui.plural("chunkatlas.paste.from", s.chunks(), s.source()), Ui.plural("chunkatlas.map.count", s.chunks()), "X " + s.dx(), "Z " + s.dz(), "Y " + s.dy(),
         Ui.t("chunkatlas.paste.existing"), Ui.t("chunkatlas.paste.replace.word"), Ui.t("chunkatlas.paste.keep.word"), Ui.t("chunkatlas.paste.blend"),
         Ui.t("chunkatlas.paste.blend.off"), Ui.t("chunkatlas.paste.smart"), Ui.t("chunkatlas.map.cancel"), Ui.t("chunkatlas.paste.place")
      );
   }

   List<Component> draw(Gfx g, Font font, List<Ui.Hit> hits, int width, int height, State s, int mouseX, int mouseY, boolean covered) {
      PasteFold.Words w = words(s);
      int level = PasteFold.fit(width - 2 * Glass.MARGIN, w, s.server(), font::width);
      if (!PasteFold.folds(level)) {
         this.close();
      }

      Glass.Bar bar = new Glass.Bar(false);
      for (PasteFold.Part p : PasteFold.bar(level, w, s.server())) {
         this.add(bar, p, s, false);
      }

      int bw = bar.width(font);
      this.top = height - Glass.MARGIN - bar.height(font);
      List<Component> tip = bar.draw(g, font, hits, (width - bw) / 2, this.top, mouseX, mouseY, covered);
      this.card = null;
      int[] tile = bar.box("more");
      if (this.open && tile != null) {
         List<Component> t = this.drawCard(g, font, hits, PasteFold.card(level, w, s.server()), s, tile, width, mouseX, mouseY, covered);
         if (t != null) {
            tip = t;
         }
      }

      return tip;
   }

   private List<Component> drawCard(Gfx g, Font font, List<Ui.Hit> hits, List<List<PasteFold.Part>> rows, State s, int[] tile, int width,
      int mouseX, int mouseY, boolean covered) {
      int inner = 0;
      for (List<PasteFold.Part> row : rows) {
         inner = Math.max(inner, PasteFold.rowWidth(row, font::width));
      }

      int cw = inner + 2 * Glass.INSET + 4;
      int ch = rows.size() * ROW_H + 2 * Glass.INSET;
      int cx = Math.clamp(tile[0] + tile[2] / 2 - cw / 2, Glass.MARGIN, Math.max(Glass.MARGIN, width - Glass.MARGIN - cw));
      int cy = this.top - 3 - ch;
      this.card = new int[]{cx, cy, cw, ch};
      g.fill(cx, cy, cx + cw, cy + ch, Glass.MENU);
      g.outline(cx, cy, cw, ch, Glass.MENU_EDGE);
      List<Component> tip = null;
      int y = cy + Glass.INSET;
      for (List<PasteFold.Part> row : rows) {
         Glass.Bar line = new Glass.Bar(false).floating();
         for (PasteFold.Part p : row) {
            this.add(line, p, s, true);
         }

         List<Component> t = line.draw(g, font, hits, cx + Glass.INSET + 2, y, mouseX, mouseY, covered);
         if (t != null) {
            tip = t;
         }

         y += ROW_H;
      }

      return tip;
   }

   private void add(Glass.Bar bar, PasteFold.Part p, State s, boolean inCard) {
      switch (p.kind()) {
         case RULE -> bar.rule();
         case LABEL -> bar.label(p.text(), p.id().equals("label") ? Ui.WHITE : Glass.NOTE);
         case TILE -> bar.tile("more", Glass.tip(Ui.t("chunkatlas.paste.options"), null), this.open, Ui.WHITE, () -> this.toggle(s));
         case WORD -> {
            String id = p.id();
            if (id.startsWith("blend.")) {
               BlendOptions.width(bar, Integer.parseInt(id.substring(6)), p.text(), true);
            } else {
               switch (id) {
                  case "x" -> bar.word(p.text(), Ui.WHITE, s.askX());
                  case "z" -> bar.word(p.text(), Ui.WHITE, s.askZ());
                  case "y" -> bar.word(p.text(), s.smartLift() ? Glass.ACCENT : Ui.WHITE, s.askY());
                  case "replace" -> bar.word(p.text(), s.keep() ? Glass.SOFT : Glass.ACCENT, () -> s.setKeep().accept(false));
                  case "keep" -> bar.word(p.text(), s.keep() ? Glass.ACCENT : Glass.SOFT, () -> s.setKeep().accept(true));
                  case "smart" -> BlendOptions.smart(bar, p.text(), inCard, !s.server());
                  case "cancel" -> bar.word(p.text(), Ui.WHITE, s.cancel());
                  default -> bar.word(p.text(), GO, s.place());
               }
            }
         }
      }
   }

   void tourOpen() {
      this.open = true;
   }

   private void toggle(State s) {
      this.open = !this.open;
      this.held = this.open ? new int[]{s.dx(), s.dz()} : null;
   }
}
