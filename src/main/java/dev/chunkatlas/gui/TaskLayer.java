package dev.chunkatlas.gui;

import dev.chunkatlas.TaskClient;
import dev.chunkatlas.engine.Area;
import dev.chunkatlas.engine.BorderBook;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.ChunkTasks;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.engine.WorldMap;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Pos;
import dev.chunkatlas.port.Tex;
import java.util.Map;

public final class TaskLayer {
   private static final int HATCH = 6;
   private static final int HATCH_TEX = 256;
   private static final int FLYING = 0xB08FB8FF;
   private static final int PREVIEW = 0x1A8FB8FF;
   private static final int BORDER = 0xFFFFFFFF;
   private static final int SHADOW = 0x73000000;
   private static Tex hatch;

   private TaskLayer() {
   }

   record View(double ox, double oy, double zoom, int left, int top, int right, int bottom) {
      double sx(double x) {
         return this.ox + x * this.zoom;
      }

      double sy(double z) {
         return this.oy + z * this.zoom;
      }
   }

   static void draw(Gfx g, View v, String dimension, WorldMap.Index index, Area preview) {
      if (dimension == null) {
         return;
      }

      g.scissor(v.left, v.top, v.right, v.bottom);
      Map<Long, long[]> saved = index == null ? Map.of() : index.present();
      for (ChunkTasks.View t : TaskClient.tasks()) {
         if (!dimension.equals(t.dimension()) || t.state().ended()) {
            continue;
         }

         ChunkSelection dealt = TaskClient.dealt(t.id());
         if (t.area() != null) {
            Area a = t.area();
            cells(g, v, a.chunkBounds(), (x, z) -> a.containsChunk(x, z) && !dealt.contains(x, z) && !isSaved(saved, x, z), true);
            dashed(g, v, a.outline(), Glass.ACCENT);
         }

         for (long p : t.flying()) {
            int cx = Pos.cx(Pos.unpack(p));
            int cz = Pos.cz(Pos.unpack(p));
            int x0 = (int)Math.floor(v.sx(cx * 16.0));
            int y0 = (int)Math.floor(v.sy(cz * 16.0));
            int x1 = Math.max(x0 + 1, (int)Math.floor(v.sx(cx * 16.0 + 16.0)));
            int y1 = Math.max(y0 + 1, (int)Math.floor(v.sy(cz * 16.0 + 16.0)));
            g.fill(x0, y0, x1, y1, FLYING);
         }
      }

      if (preview != null) {
         cells(g, v, preview.chunkBounds(), preview::containsChunk, false);
         dashed(g, v, preview.outline(), Glass.ACCENT);
      }

      BorderBook.Border b = TaskClient.border(dimension);
      if (b != null) {
         double[] p = b.area().outline();
         solid(g, v, p, 1, 1, SHADOW);
         solid(g, v, p, 0, 0, BORDER);
      }

      g.unscissor();
   }

   private static boolean isSaved(Map<Long, long[]> saved, int x, int z) {
      long[] bits = saved.get(WorldLayout.key(x >> 5, z >> 5));
      int i = ChunkSelection.index(x, z);
      return bits != null && (bits[i >> 6] & 1L << (i & 63)) != 0L;
   }

   private interface ChunkTest {
      boolean in(int chunkX, int chunkZ);
   }

   private static void cells(Gfx g, View v, int[] bounds, ChunkTest test, boolean hatched) {
      double chunkPx = 16.0 * v.zoom;
      int k = 1;
      while (chunkPx * k < 4.0 && k < 1 << 16) {
         k <<= 1;
      }

      int minX = Math.max(bounds[0], (int)Math.floor((v.left - v.ox) / v.zoom / 16.0) - 1);
      int maxX = Math.min(bounds[2], (int)Math.ceil((v.right - v.ox) / v.zoom / 16.0) + 1);
      int minZ = Math.max(bounds[1], (int)Math.floor((v.top - v.oy) / v.zoom / 16.0) - 1);
      int maxZ = Math.min(bounds[3], (int)Math.ceil((v.bottom - v.oy) / v.zoom / 16.0) + 1);
      if (minX > maxX || minZ > maxZ) {
         return;
      }

      minX = Math.floorDiv(minX, k) * k;
      minZ = Math.floorDiv(minZ, k) * k;
      Tex tex = hatched ? hatch() : null;
      for (int z = minZ; z <= maxZ; z += k) {
         int y0 = (int)Math.floor(v.sy(z * 16.0));
         int y1 = (int)Math.floor(v.sy((z + k) * 16.0));
         int runFrom = Integer.MIN_VALUE;
         for (int x = minX; x <= maxX + k; x += k) {
            boolean in = x <= maxX && test.in(x + k / 2, z + k / 2);
            if (in && runFrom == Integer.MIN_VALUE) {
               runFrom = x;
            } else if (!in && runFrom != Integer.MIN_VALUE) {
               int x0 = (int)Math.floor(v.sx(runFrom * 16.0));
               int x1 = (int)Math.floor(v.sx(x * 16.0));
               if (tex != null) {
                  hatchRect(g, tex, Math.max(x0, v.left), Math.max(y0, v.top), Math.min(x1, v.right), Math.min(y1, v.bottom));
               } else {
                  g.fill(x0, y0, x1, y1, PREVIEW);
               }

               runFrom = Integer.MIN_VALUE;
            }
         }
      }
   }

   private static void hatchRect(Gfx g, Tex tex, int x0, int y0, int x1, int y1) {
      int room = HATCH_TEX - HATCH;
      for (int y = y0; y < y1; y += room) {
         int h = Math.min(room, y1 - y);
         for (int x = x0; x < x1; x += room) {
            int w = Math.min(room, x1 - x);
            g.blit(tex, x, y, Math.floorMod(x - y, HATCH), 0.0F, w, h, HATCH_TEX, HATCH_TEX);
         }
      }
   }

   private static Tex hatch() {
      if (hatch == null || hatch.closed()) {
         hatch = Tex.create("chunkatlas_hatch", HATCH_TEX, HATCH_TEX);
         for (int y = 0; y < HATCH_TEX; y++) {
            for (int x = 0; x < HATCH_TEX; x++) {
               hatch.set(x, y, Math.floorMod(x - y, HATCH) == 0 ? 0x668FB8FF : 0x0F8FB8FF);
            }
         }

         hatch.upload();
      }

      return hatch;
   }

   static void dashed(Gfx g, View v, double[] p, int colour) {
      int n = p.length / 2;
      double along = 0.0;
      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         along = line(g, v, v.sx(p[2 * i]), v.sy(p[2 * i + 1]), v.sx(p[2 * j]), v.sy(p[2 * j + 1]), colour, 0, 0, along, true);
      }
   }

   static void solid(Gfx g, View v, double[] p, int dx, int dy, int colour) {
      int n = p.length / 2;
      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         line(g, v, v.sx(p[2 * i]), v.sy(p[2 * i + 1]), v.sx(p[2 * j]), v.sy(p[2 * j + 1]), colour, dx, dy, 0.0, false);
      }
   }

   private static double line(Gfx g, View v, double x0, double y0, double x1, double y1, int colour, int dx, int dy, double along, boolean dashed) {
      double len = Math.hypot(x1 - x0, y1 - y0);
      if (len < 1.0E-6) {
         return along;
      }

      double[] t = clip(x0, y0, x1, y1, v.left - 1, v.top - 1, v.right + 1, v.bottom + 1);
      if (t == null) {
         return along + len;
      }

      double from = t[0] * len;
      double to = t[1] * len;
      for (double s = Math.floor(from); s <= to; s += 1.0) {
         if (dashed && (along + s) % 7.0 >= 4.0) {
            continue;
         }

         int x = (int)Math.floor(x0 + (x1 - x0) * s / len) + dx;
         int y = (int)Math.floor(y0 + (y1 - y0) * s / len) + dy;
         g.fill(x, y, x + 1, y + 1, colour);
      }

      return along + len;
   }

   private static double[] clip(double x0, double y0, double x1, double y1, double minX, double minY, double maxX, double maxY) {
      double t0 = 0.0;
      double t1 = 1.0;
      double dx = x1 - x0;
      double dy = y1 - y0;
      double[] p = {-dx, dx, -dy, dy};
      double[] q = {x0 - minX, maxX - x0, y0 - minY, maxY - y0};
      for (int i = 0; i < 4; i++) {
         if (p[i] == 0.0) {
            if (q[i] < 0.0) {
               return null;
            }
         } else {
            double r = q[i] / p[i];
            if (p[i] < 0.0) {
               t0 = Math.max(t0, r);
            } else {
               t1 = Math.min(t1, r);
            }
         }
      }

      return t0 > t1 ? null : new double[]{t0, t1};
   }

   static final int ICON = 14;

   private static final int[][] ICONS = {
      {0x0000, 0x1FFE, 0x1002, 0x1002, 0x1002, 0x1002, 0x1002, 0x1002, 0x1002, 0x1002, 0x1002, 0x1002, 0x1FFE, 0x0000},
      {0x0000, 0x0000, 0x0000, 0x3FFF, 0x2001, 0x2001, 0x2001, 0x2001, 0x2001, 0x2001, 0x3FFF, 0x0000, 0x0000, 0x0000},
      {0x0000, 0x03F0, 0x0408, 0x0804, 0x1002, 0x1002, 0x1002, 0x1002, 0x1002, 0x1002, 0x0804, 0x0408, 0x03F0, 0x0000},
      {0x0000, 0x0000, 0x0000, 0x07F8, 0x1806, 0x1002, 0x2001, 0x2001, 0x1002, 0x1806, 0x07F8, 0x0000, 0x0000, 0x0000},
      {0x0000, 0x00C0, 0x0120, 0x0210, 0x0408, 0x0804, 0x1002, 0x1002, 0x0804, 0x0408, 0x0210, 0x0120, 0x00C0, 0x0000},
      {0x0000, 0x0000, 0x00C0, 0x00C0, 0x0120, 0x0210, 0x0210, 0x0408, 0x0408, 0x0804, 0x0804, 0x1002, 0x1FFE, 0x0000},
      {0x0000, 0x00C0, 0x0330, 0x0408, 0x0804, 0x1002, 0x1002, 0x1002, 0x0804, 0x0804, 0x0804, 0x0408, 0x07F8, 0x0000},
      {0x0000, 0x01E0, 0x0618, 0x0804, 0x1002, 0x1002, 0x1002, 0x1002, 0x1002, 0x1002, 0x0804, 0x0618, 0x01E0, 0x0000},
      {0x0000, 0x0000, 0x0000, 0x00C0, 0x00C0, 0x1F3E, 0x0C0C, 0x0210, 0x0210, 0x02D0, 0x0330, 0x0210, 0x0000, 0x0000}
   };

   static void icon(Gfx g, Area.Shape shape, int x, int y, int colour) {
      int[] rows = ICONS[shape.ordinal()];
      for (int r = 0; r < ICON; r++) {
         int bits = rows[r];
         int c = 0;
         while (c < ICON) {
            if ((bits & 1 << c) == 0) {
               c++;
               continue;
            }

            int from = c;
            while (c < ICON && (bits & 1 << c) != 0) {
               c++;
            }

            g.fill(x + from, y + r, x + c, y + r + 1, colour);
         }
      }
   }
}
