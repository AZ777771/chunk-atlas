package dev.chunkatlas.gui;

import dev.chunkatlas.engine.ChunkRule;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.RegionFacts;
import dev.chunkatlas.engine.WorldMap;
import dev.chunkatlas.port.Gfx;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.Font;

final class RulePanel {
   static final int CTRL = 16;

   static final int ROW = 20;
   static final int PAD = 8;
   static final int HEADER = 24;
   static final int FOOTER = 30;
   static final int COL_GAP = 12;

   static final int RIGHT_W = 104;

   static final int BAR_W = 2;

   static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Chunk Atlas rule count");
      t.setDaemon(true);
      t.setPriority(Thread.NORM_PRIORITY - 1);
      return t;
   });

   private RulePanel() {
   }

   static int[] bounds(int width, int height) {
      int x = Glass.MARGIN;
      int y = Glass.MARGIN + Glass.tabsHeight() + 4;
      int w = Math.min(width - 2 * Glass.MARGIN, Math.max(300, Math.min(460, width * 2 / 3)));
      int h = Math.max(120, Math.min(310, height - y - Glass.MARGIN));
      return new int[]{x, y, w, h};
   }

   static int frame(Gfx g, Font font, int[] b, String title, String lead) {
      Glass.panel(g, b[0], b[1], b[2], b[3]);
      g.fill(b[0] + 1, b[1] + 1, b[0] + b[2] - 1, b[1] + b[3] - 1, 0x40000000);
      int tx = b[0] + PAD;
      int ty = b[1] + (HEADER - 8) / 2;
      g.text(font, title, tx, ty, Ui.WHITE);
      if (lead != null && !lead.isEmpty()) {
         int lx = tx + font.width(title);
         String more = " · " + lead;
         g.text(font, font.plainSubstrByWidth(more, b[0] + b[2] - PAD - lx), lx, ty, Glass.LABEL);
      }

      g.fill(b[0] + 1, b[1] + HEADER, b[0] + b[2] - 1, b[1] + HEADER + 1, Glass.RULE);
      g.fill(b[0] + 1, b[1] + b[3] - FOOTER, b[0] + b[2] - 1, b[1] + b[3] - FOOTER + 1, Glass.RULE);
      return b[1] + HEADER + 1;
   }

   static int footerTop(int[] b) {
      return b[1] + b[3] - FOOTER;
   }

   static int footerY(int[] b) {
      return footerTop(b) + 1 + (FOOTER - 1 - CTRL) / 2;
   }

   static int leftX(int[] b) {
      return b[0] + PAD;
   }

   static int leftW(int[] b) {
      return b[2] - 2 * PAD - COL_GAP - RIGHT_W;
   }

   static int rightX(int[] b) {
      return b[0] + b[2] - PAD - RIGHT_W;
   }

   static int buttonWidth(Font font, String text) {
      return Math.max(60, font.width(text) + 18);
   }

   static void heading(Gfx g, Font font, int x, int y, String text) {
      g.text(font, text, x, y, Glass.LABEL);
   }

   static void radio(Gfx g, Font font, List<Ui.Hit> hits, int x, int y, String label, boolean on, Runnable action) {
      boolean usable = action != null;
      g.fill(x, y, x + 8, y + 8, usable ? 0xFFA0A0A0 : 0xFF555555);
      g.fill(x + 1, y + 1, x + 7, y + 7, 0xFF1E1E1E);
      if (on) {
         g.fill(x + 2, y + 2, x + 6, y + 6, usable ? Glass.ACCENT : Ui.DIM);
      }

      g.text(font, label, x + 12, y, usable ? Ui.WHITE : Glass.OFF);
      if (usable) {
         hits.add(new Ui.Hit(x, y - 2, x + 12 + font.width(label), y + 10, action));
      }
   }

   static void dashed(Gfx g, Font font, List<Ui.Hit> hits, int x, int y, int w, int h, String text, int mouseX, int mouseY, Runnable action) {
      boolean usable = action != null;
      boolean hover = usable && Glass.over(mouseX, mouseY, x, y, w, h);
      if (hover) {
         g.fill(x, y, x + w, y + h, Glass.TINT);
      }

      int colour = !usable ? 0x30FFFFFF : hover ? 0xC0FFFFFF : 0x66FFFFFF;
      for (int dx = 0; dx < w; dx += 4) {
         int to = Math.min(x + dx + 2, x + w);
         g.fill(x + dx, y, to, y + 1, colour);
         g.fill(x + dx, y + h - 1, to, y + h, colour);
      }

      for (int dy = 0; dy < h; dy += 4) {
         int to = Math.min(y + dy + 2, y + h);
         g.fill(x, y + dy, x + 1, to, colour);
         g.fill(x + w - 1, y + dy, x + w, to, colour);
      }

      g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, usable ? Glass.SOFT : Glass.OFF, usable);
      if (usable) {
         hits.add(new Ui.Hit(x, y, x + w, y + h, action));
      }
   }

   static void dropdown(Gfx g, Font font, List<Ui.Hit> hits, int x, int y, int w, String text, int mouseX, int mouseY, Runnable action) {
      boolean usable = action != null;
      boolean hover = usable && Glass.over(mouseX, mouseY, x, y, w, CTRL);
      g.fill(x, y, x + w, y + CTRL, hover ? Glass.HOT : Glass.TINT);
      g.outline(x, y, w, CTRL, hover ? 0xC0FFFFFF : 0x38FFFFFF);
      int arrow = font.width("▾");
      g.text(font, font.plainSubstrByWidth(text, w - arrow - 9), x + 4, y + (CTRL - 8) / 2, usable ? Ui.WHITE : Glass.OFF);
      g.text(font, "▾", x + w - arrow - 3, y + (CTRL - 8) / 2, usable ? Glass.SOFT : Glass.OFF);
      if (usable) {
         hits.add(new Ui.Hit(x, y, x + w, y + CTRL, action));
      }
   }

   static void stepper(Gfx g, Font font, List<Ui.Hit> hits, int x, int y, int w, String text, int mouseX, int mouseY, Runnable less, Runnable more) {
      int bw = CTRL;
      Glass.button(g, font, hits, x, y, bw, CTRL, "−", Ui.WHITE, mouseX, mouseY, less);
      int vx = x + bw + 3;
      int vw = w - 2 * (bw + 3);
      g.fill(vx, y, vx + vw, y + CTRL, 0xFF000000);
      g.outline(vx, y, vw, CTRL, 0xFF6A6A6A);
      g.text(font, font.plainSubstrByWidth(text, vw - 6), vx + (vw - font.width(font.plainSubstrByWidth(text, vw - 6))) / 2, y + (CTRL - 8) / 2, Glass.ACCENT);
      Glass.button(g, font, hits, x + w - bw, y, bw, CTRL, "+", Ui.WHITE, mouseX, mouseY, more);
   }

   static void slider(Gfx g, int x, int y, int w, int h, double fraction, boolean hot) {
      int ty = y + h / 2;
      g.fill(x, ty - 1, x + w, ty + 1, 0x33FFFFFF);
      int kx = x + 2 + (int)Math.round((w - 4) * Math.max(0.0, Math.min(1.0, fraction)));
      g.fill(x, ty - 1, kx, ty + 1, hot ? Glass.ACCENT : 0x998FB8FF);
      g.fill(kx - 3, ty - 5, kx + 3, ty + 5, 0xFF000000);
      g.fill(kx - 2, ty - 4, kx + 2, ty + 4, hot ? Ui.WHITE : Glass.ACCENT);
   }

   static void fades(Gfx g, int x, int y, int w, int h, boolean top, boolean bottom) {
      int d = Math.min(10, h / 3);
      if (top) {
         g.gradient(x, y, x + w, y + d, 0xC0000000, 0x00000000);
      }

      if (bottom) {
         g.gradient(x, y + h - d, x + w, y + h, 0x00000000, 0xC0000000);
      }
   }

   static void scrollbar(Gfx g, int x, int y, int h, int content, int scroll, boolean hot) {
      if (content <= h) {
         return;
      }

      int knob = Math.max(12, h * h / Math.max(1, content));
      int at = y + (h - knob) * scroll / Math.max(1, content - h);
      g.fill(x, y, x + BAR_W, y + h, 0x20FFFFFF);
      g.fill(x, at, x + BAR_W, at + knob, hot ? 0xC0FFFFFF : 0x80FFFFFF);
   }

   static int[] knob(int y, int h, int content, int scroll) {
      int knob = Math.max(12, h * h / Math.max(1, content));
      return new int[]{y + (h - knob) * scroll / Math.max(1, content - h), knob};
   }

   static void progress(Gfx g, int x, int y, int w, double fraction) {
      g.fill(x, y, x + w, y + 2, 0xFF262A30);
      g.fill(x, y, x + (int)Math.round(w * Math.max(0.0, Math.min(1.0, fraction))), y + 2, Glass.ACCENT);
   }

   static ChunkSelection matches(ChunkRule rule, WorldMap.Index index, Map<Long, RegionFacts> facts, ChunkSelection within, int border) {
      ChunkSelection out = new ChunkSelection();
      boolean needFacts = rule.needsFacts();
      for (Map.Entry<Long, long[]> e : index.present().entrySet()) {
         RegionFacts f = facts.get(e.getKey());
         if (needFacts && f == null) {
            continue;
         }

         int rx = (int)(e.getKey() >> 32);
         int rz = (int)e.getKey().longValue();
         long[] present = e.getValue();
         long[] mask = new long[16];
         boolean some = false;
         for (int i = 0; i < 1024; i++) {
            if ((present[i >> 6] & 1L << (i & 63)) == 0L) {
               continue;
            }

            if (f != null && (f.state[i] == RegionFacts.ABSENT || f.state[i] == RegionFacts.UNREADABLE && !rule.includesUnreadable())) {
               continue;
            }

            int cx = (rx << 5) + (i & 31);
            int cz = (rz << 5) + (i >> 5);
            if ((within == null || within.contains(cx, cz)) && rule.matches(cx, cz, f, i)) {
               mask[i >> 6] |= 1L << (i & 63);
               some = true;
            }
         }

         if (some) {
            out.add(rx, rz, mask);
         }
      }

      return grow(out, border, index);
   }

   static ChunkSelection grow(ChunkSelection found, int border, WorldMap.Index index) {
      if (border <= 0 || found == null || found.isEmpty()) {
         return found;
      }

      ChunkSelection out = found.copy();
      found.forEach((x, z) -> {
         for (int dz = -border; dz <= border; dz++) {
            for (int dx = -border; dx <= border; dx++) {
               if (index.has(x + dx, z + dz)) {
                  out.add(x + dx, z + dz);
               }
            }
         }
      });
      return out;
   }
}
