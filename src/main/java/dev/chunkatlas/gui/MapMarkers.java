package dev.chunkatlas.gui;

import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Skin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

public final class MapMarkers {
   public static final int TAG = 0x99000000;
   public static final int GOLD = 0xFFFFD84A;

   public static final int DEATH = 0xFF474F52;
   public static final String DEATH_GLYPH = "\u2620";

   private static final int SHADE = 0x73;

   private MapMarkers() {
   }

   static void shadow(Gfx g, int x0, int y0, int[][] rects, int alpha) {
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      for (int[] r : rects) {
         minX = Math.min(minX, r[0]);
         minY = Math.min(minY, r[1]);
         maxX = Math.max(maxX, r[0] + r[2]);
         maxY = Math.max(maxY, r[1] + r[3]);
      }

      int w = maxX - minX;
      boolean[] on = new boolean[w * (maxY - minY)];
      for (int[] r : rects) {
         for (int y = r[1]; y < r[1] + r[3]; y++) {
            for (int x = r[0]; x < r[0] + r[2]; x++) {
               on[(y - minY) * w + x - minX] = true;
            }
         }
      }

      int colour = (SHADE * alpha / 0xFF) << 24;
      for (int y = minY; y < maxY; y++) {
         int x = minX;
         while (x < maxX) {
            if (!on[(y - minY) * w + x - minX]) {
               x++;
               continue;
            }

            int start = x;
            while (x < maxX && on[(y - minY) * w + x - minX]) {
               x++;
            }

            g.fill(x0 + start + 1, y0 + y + 1, x0 + x + 1, y0 + y + 2, colour);
         }
      }
   }

   public static Skin skin(Minecraft mc, String id) {
      return Skin.of(mc, id);
   }

   public static void you(Gfx g, int x, int y, float yaw, Skin skin) {
      float facing = Float.isNaN(yaw) ? 180.0F : yaw;
      float angle = (float)Math.toRadians(facing + 90.0F);
      arrowHead(g, x + 1, y + 1, angle, 0x73000000);
      shadow(g, x, y, new int[][]{{-5, -5, 10, 10}}, 0xFF);
      arrowHead(g, x, y, angle, 0xFFFFFFFF);
      g.fill(x - 5, y - 5, x + 5, y + 5, 0xFFFFFFFF);
      g.face(skin, x - 4, y - 4, 8);
   }

   private static void arrowHead(Gfx g, int x, int y, float angle, int colour) {
      g.push();
      g.translate(x, y);
      g.rotate(angle);
      g.fill(9, -2, 10, 3, colour);
      g.fill(10, -1, 11, 2, colour);
      g.fill(11, 0, 12, 1, colour);
      g.pop();
   }

   public static void face(Gfx g, int x, int y, Skin skin) {
      face(g, x, y, skin, 0xFF);
   }

   public static void face(Gfx g, int x, int y, Skin skin, int alpha) {
      shadow(g, x, y, new int[][]{{-4, -4, 8, 8}}, alpha);
      g.face(skin, x - 4, y - 4, 8, alpha << 24 | 0xFFFFFF);
   }

   public static void edgeArrow(Gfx g, int x, int y, double angle) {
      g.push();
      g.translate(x, y);
      g.rotate((float)angle);
      shadow(g, 0, 0, new int[][]{{7, -2, 1, 5}, {8, -1, 1, 3}, {9, 0, 1, 1}}, 0xFF);
      g.fill(7, -2, 8, 3, 0xFFFFFFFF);
      g.fill(8, -1, 9, 2, 0xFFFFFFFF);
      g.fill(9, 0, 10, 1, 0xFFFFFFFF);
      g.pop();
   }

   public static void star(Gfx g, int x, int y) {
      star(g, x, y, 0xFF);
   }

   public static void star(Gfx g, int x, int y, int alpha) {
      int l = x - 5;
      int t = y - 5;
      int gold = alpha << 24 | GOLD & 0xFFFFFF;
      shadow(g, l, t, new int[][]{{4, 1, 2, 8}, {1, 4, 8, 2}, {3, 3, 4, 4}}, alpha);
      g.fill(l + 4, t + 1, l + 6, t + 9, gold);
      g.fill(l + 1, t + 4, l + 9, t + 6, gold);
      g.fill(l + 3, t + 3, l + 7, t + 7, gold);
      g.fill(l + 4, t + 4, l + 6, t + 6, alpha << 24 | 0xFFF6C0);
   }

   public static final int PIN_H = 14;
   public static final int PIN_W = 6;

   public static void pin(Gfx g, Font font, int x, int y, int colour, String letter) {
      pin(g, font, x, y, colour, letter, 0xFF);
   }

   public static void pin(Gfx g, Font font, int x, int y, int colour, String letter, int alpha) {
      int l = x - 5;
      int t = y - PIN_H;
      int inside = alpha << 24 | colour & 0xFFFFFF;

      int[][] fill = {{2, 1, 7}, {1, 2, 9}, {1, 3, 9}, {1, 4, 9}, {1, 5, 9}, {1, 6, 9}, {1, 7, 9}, {1, 8, 9}, {2, 9, 7}, {3, 10, 5}, {4, 11, 3}, {5, 12, 1}};
      int[][] rows = new int[fill.length][];
      for (int i = 0; i < fill.length; i++) {
         rows[i] = new int[]{fill[i][0], fill[i][1], fill[i][2], 1};
      }

      shadow(g, l, t, rows, alpha);
      for (int[] r : fill) {
         g.fill(l + r[0], t + r[1], l + r[0] + r[2], t + r[1] + 1, inside);
      }

      int lum = ((colour >> 16 & 0xFF) * 299 + (colour >> 8 & 0xFF) * 587 + (colour & 0xFF) * 114) / 1000;
      g.centered(font, letter, x + 1, t + 2, alpha << 24 | (lum > 160 ? 0x000000 : 0xFFFFFF));
   }

   public static void pin(Gfx g, Font font, int x, int y, dev.chunkatlas.minimap.waypoint.Waypoint w, int alpha) {
      if (w.death) {
         pin(g, font, x, y, DEATH, DEATH_GLYPH, alpha);
      } else {
         pin(g, font, x, y, 0xFF000000 | w.rgb(), w.label(), alpha);
      }
   }

   public static void ring(Gfx g, int cx, int cy, int r, int colour) {
      int x = r;
      int y = 0;
      int d = 1 - r;
      while (x >= y) {
         g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, colour);
         g.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, colour);
         g.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, colour);
         g.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, colour);
         g.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, colour);
         g.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, colour);
         g.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, colour);
         g.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, colour);
         y++;
         if (d < 0) {
            d += 2 * y + 1;
         } else {
            x--;
            d += 2 * (y - x) + 1;
         }
      }
   }

   public static void tag(Gfx g, Font font, String text, int x, int y, int colour) {
      int w = font.width(text);
      g.fill(x, y - 5, x + w + 4, y + 5, TAG);
      g.text(font, text, x + 2, y - 4, colour);
   }

   public static void tagCentred(Gfx g, Font font, String text, int x, int y, int colour) {
      tag(g, font, text, x - (font.width(text) + 4) / 2, y, colour);
   }
}
