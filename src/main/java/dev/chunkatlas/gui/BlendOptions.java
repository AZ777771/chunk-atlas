package dev.chunkatlas.gui;

import dev.chunkatlas.Settings;

final class BlendOptions {
   static final int[] WIDTHS = {0, 2, 4, 8};

   private BlendOptions() {
   }

   static int blend() {
      return Settings.pasteBlend();
   }

   static boolean smart() {
      return Settings.pasteSmart() && blend() > 0;
   }

   static void width(Glass.Bar bar, int n, String text, boolean usable) {
      bar.word(text, blend() == n ? Glass.ACCENT : Glass.SOFT, usable ? () -> Settings.pasteBlend(n) : null);
   }

   static void smart(Glass.Bar bar, String text, boolean word, boolean usable) {
      Runnable flip = usable && blend() > 0 ? () -> Settings.pasteSmart(!Settings.pasteSmart()) : null;
      if (word) {
         bar.word(text, Settings.pasteSmart() ? Glass.ACCENT : Glass.SOFT, flip);
      } else {
         bar.toggle(text, Glass.tip(Ui.t("chunkatlas.paste.smart.tip"), null), Settings.pasteSmart(), flip);
      }
   }

   static Glass.Bar row(boolean usable, Integer lift) {
      Glass.Bar bar = new Glass.Bar(false).floating();
      bar.label(Ui.t("chunkatlas.paste.blend"), usable ? Glass.LABEL : Glass.OFF);
      for (int n : WIDTHS) {
         width(bar, n, n == 0 ? Ui.t("chunkatlas.paste.blend.off") : Integer.toString(n), usable);
      }

      bar.rule();
      smart(bar, Ui.t("chunkatlas.paste.smart"), false, usable);
      if (usable && smart() && lift != null) {
         bar.label("Y " + (lift > 0 ? "+" + lift : Integer.toString(lift)), Glass.ACCENT);
      }

      return bar;
   }
}
