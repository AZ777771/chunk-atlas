package dev.chunkatlas.gui;

import dev.chunkatlas.port.Gfx;
import java.util.List;
import java.util.Locale;
import dev.chunkatlas.engine.GameVersions;
import dev.chunkatlas.engine.Message;
import dev.chunkatlas.engine.Safe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;

public final class Ui {
   public static final int WHITE = -1;
   public static final int GREY = 0xFFA8A8A8;
   public static final int DIM = -9408400;
   public static final int YELLOW = 0xFFFFD84A;
   public static final int RED = -38037;
   public static final int LIGHT_BLUE = -7358209;
   public static final int GREEN = -11746422;

   public static final int GOLD = 0xFFF2C14E;
   public static final int BLUE_BG = -12951650;
   public static final int GREY_BG = -11908534;
   public static final int RED_BG = -6407622;
   public static final int ORANGE_BG = -6395350;
   public static final int GREEN_BG = -13730481;
   public static final int VOID = -15855597;
   public static final int HEADER = 26;
   public static final int FOOTER = 28;

   public static final int PILL_H = 20;

   public static final int INFO = 14;
   public static final String TAB_HEADER = "minecraft:textures/gui/tab_header_background.png";
   private static final String BUTTON = "minecraft:widget/button";
   private static final String BUTTON_HOVER = "minecraft:widget/button_highlighted";
   private static final String BUTTON_OFF = "minecraft:widget/button_disabled";

   private Ui() {
   }

   public static String t(String key, Object... args) {
      return I18n.get(key, args);
   }

   public static String t(Message message) {
      if (message == null) {
         return "";
      }

      Object[] args = new Object[message.args().length];
      for (int i = 0; i < args.length; i++) {
         args[i] = message.args()[i] instanceof Message inner ? t(inner) : message.args()[i];
      }

      if (message.key() == null) {
         return args.length == 0 ? "" : String.valueOf(args[0]);
      }

      return I18n.get(message.key(), args);
   }

   public static String plural(String key, long n, Object... more) {
      Object[] args = new Object[more.length + 1];
      args[0] = count(n);
      System.arraycopy(more, 0, args, 1, more.length);
      return I18n.get(key + (n == 1L ? ".one" : ".many"), args);
   }

   public static String dimension(String id) {
      return switch (id) {
         case "minecraft:overworld" -> t("chunkatlas.dimension.overworld");
         case "minecraft:the_nether" -> t("chunkatlas.dimension.nether");
         case "minecraft:the_end" -> t("chunkatlas.dimension.end");
         default -> id.startsWith("DIM") ? t("chunkatlas.dimension.numbered", id.substring(3)) : id;
      };
   }

   public static String size(long bytes) {
      return t(Safe.size(bytes));
   }

   public static String version(int dataVersion) {
      return t(GameVersions.describe(dataVersion));
   }

   public static int pillWidth(Font font, String label) {
      return font.width(label) + 16;
   }

   public static int pill(
      Gfx g,
      Font font,
      List<Ui.Hit> hits,
      int x,
      int y,
      int w,
      String label,
      int colour,
      int mouseX,
      int mouseY,
      boolean covered,
      Runnable action
   ) {
      boolean hover = action != null && !covered && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + PILL_H;

      boolean on = colour == BLUE_BG;
      g.sprite(action == null ? BUTTON_OFF : (hover || on ? BUTTON_HOVER : BUTTON), x, y, w, PILL_H);
      g.centered(font, label, x + w / 2, y + (PILL_H - 8) / 2, action == null ? GREY : (colour == RED_BG ? 0xFFFF7A7A : WHITE));
      if (action != null) {
         hits.add(new Ui.Hit(x, y, x + w, y + PILL_H, action));
      }

      return w;
   }

   public static int chip(Gfx g, Font font, String text, int x, int y, int colour) {
      return Glass.chip(g, font, text, x, y, colour);
   }

   public static int chipWidth(Font font, String text) {
      return Glass.chipWidth(font, text);
   }

   public static int menuWidth(Font font, List<Ui.Item> items) {
      return Glass.menuWidth(font, items);
   }

   public static int menuHeight(List<Ui.Item> items) {
      return Glass.menuHeight(items);
   }

   public static void menu(Gfx g, Font font, List<Ui.Hit> hits, List<Ui.Item> items, int x, int y, int mouseX, int mouseY) {
      Glass.menu(g, font, hits, items, x, y, mouseX, mouseY);
   }

   public static String amount(int value, String unit) {
      if (value == 0) {
         return t("chunkatlas.shrink.off");
      }

      if (unit.equals("minutes") && value >= 60) {
         return plural("chunkatlas.shrink.unit.hours", value / 60);
      }

      return plural("chunkatlas.shrink.unit." + unit, value);
   }

   public static String count(long n) {
      return String.format(Locale.ROOT, "%,d", n);
   }

   public static String chunks(long n) {
      return plural("chunkatlas.count.chunks", n);
   }

   public static String played(long ticks) {
      long seconds = ticks / 20L;
      if (seconds < 1L) {
         return t("chunkatlas.played.never");
      }

      if (seconds < 60L) {
         return t("chunkatlas.played.seconds", seconds);
      }

      if (seconds < 3600L) {
         return t("chunkatlas.played.minutes", seconds / 60L);
      }

      long hours = seconds / 3600L;
      return hours < 100L ? t("chunkatlas.played.hours_minutes", hours, seconds % 3600L / 60L) : t("chunkatlas.played.hours", count(hours));
   }

   public static String ago(long thenSeconds) {
      long s = System.currentTimeMillis() / 1000L - thenSeconds;
      if (thenSeconds <= 0L || s < 0L) {
         return "";
      } else if (s < 90L) {
         return t("chunkatlas.ago.just_now");
      } else if (s < 5400L) {
         return t("chunkatlas.ago.minutes", s / 60L);
      } else if (s < 129600L) {
         return t("chunkatlas.ago.hours", Math.round(s / 3600.0));
      } else {
         long days = Math.round(s / 86400.0);
         if (days < 60L) {
            return t("chunkatlas.ago.days", days);
         } else {
            return days < 700L ? t("chunkatlas.ago.months", Math.round(days / 30.4)) : t("chunkatlas.ago.years", Math.round(days / 365.25));
         }
      }
   }

   public static String fit(net.minecraft.client.gui.Font font, String text, int w) {
      return font.width(text) <= w ? text : font.plainSubstrByWidth(text, Math.max(0, w - font.width("…"))) + "…";
   }

   public record Hit(int x0, int y0, int x1, int y1, Runnable action) {
      public boolean has(double x, double y) {
         return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
      }
   }

   public record Item(String label, String note, Runnable action, Ui.Kind kind, String tip) {
      public Item(String label, String note, Runnable action, Ui.Kind kind) {
         this(label, note, action, kind, null);
      }

      public Item(String label, String note, Runnable action) {
         this(label, note, action, Ui.Kind.ACTION);
      }

      public Ui.Item withTip(String tip) {
         return new Ui.Item(this.label, this.note, this.action, this.kind, tip);
      }

      public static Ui.Item info(String label, String note) {
         return new Ui.Item(label, note, null, Ui.Kind.INFO);
      }

      public static Ui.Item heading(String label) {
         return new Ui.Item(label, null, null, Ui.Kind.HEADING);
      }

      public static Ui.Item separator() {
         return new Ui.Item("", null, null, Ui.Kind.SEPARATOR);
      }

      public static Ui.Item danger(String label, String note, Runnable action) {
         return new Ui.Item(label, note, action, Ui.Kind.DANGER);
      }

      public static Ui.Item check(String label, boolean on, Runnable action) {
         return new Ui.Item(label, on ? "✔" : null, action, Ui.Kind.CHECK);
      }

      public static Ui.Item radio(String label, boolean on, Runnable action) {
         return new Ui.Item(label, on ? "•" : null, action, Ui.Kind.RADIO);
      }
   }

   public enum Kind {
      ACTION,
      INFO,
      HEADING,
      SEPARATOR,
      DANGER,
      CHECK,
      RADIO;
   }
}
