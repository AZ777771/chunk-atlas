package dev.chunkatlas.gui;

import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Tex;

final class Thumbnail {
   private Thumbnail() {
   }

   static int[] bounds(ChunkSelection sel) {
      int minX = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;
      for (long region : sel.regions()) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         long[] bits = sel.bits(rx, rz);
         for (int i = 0; i < 1024; i++) {
            if ((bits[i >> 6] >>> (i & 63) & 1L) != 0L) {
               int cx = rx * 32 + (i & 31);
               int cz = rz * 32 + (i >> 5);
               minX = Math.min(minX, cx);
               maxX = Math.max(maxX, cx);
               minZ = Math.min(minZ, cz);
               maxZ = Math.max(maxZ, cz);
            }
         }
      }

      return minX == Integer.MAX_VALUE ? null : new int[]{minX, minZ, maxX, maxZ};
   }

   static void frame(Gfx g, int x, int y, int w, int h) {
      g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
      g.fill(x, y, x + w, y + h, MapScreen.NOT_MADE);
   }

   static void draw(Gfx g, MapScreen map, int x, int y, int w, int h, ChunkSelection sel, int tone, boolean guards) {
      frame(g, x, y, w, h);
      int[] b = sel == null ? null : bounds(sel);
      if (b == null) {
         return;
      }

      int minX = b[0];
      int minZ = b[1];
      int maxX = b[2];
      int maxZ = b[3];

      int cw = maxX - minX + 5;
      int ch = maxZ - minZ + 5;
      double px = Math.max(0.25, Math.min(8.0, Math.min(w / (double)cw, h / (double)ch)));
      double ox = x + (w - cw * px) / 2.0 - (minX - 2) * px;
      double oz = y + (h - ch * px) / 2.0 - (minZ - 2) * px;
      g.scissor(x, y, x + w, y + h);
      if (map != null) {
         for (int rx = Math.floorDiv(minX - 2, 32); rx <= Math.floorDiv(maxX + 2, 32); rx++) {
            for (int rz = Math.floorDiv(minZ - 2, 32); rz <= Math.floorDiv(maxZ + 2, 32); rz++) {
               Object[] held = map.heldTile(rx, rz);
               if (held == null) {
                  continue;
               }

               int tx = (int)Math.floor(ox + rx * 32 * px);
               int tz = (int)Math.floor(oz + rz * 32 * px);
               int size = (int)Math.ceil(32 * px);
               int tex = (Integer)held[1];
               g.blit((Tex)held[0], tx, tz, 0.0F, 0.0F, size, size, tex, tex, tex, tex);
            }
         }
      }

      int rgb = tone & 0xFFFFFF;
      for (long region : sel.regions()) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         long[] bits = sel.bits(rx, rz);
         for (int i = 0; i < 1024; i++) {
            if ((bits[i >> 6] >>> (i & 63) & 1L) == 0L) {
               continue;
            }

            int cx = rx * 32 + (i & 31);
            int cz = rz * 32 + (i >> 5);
            int x0 = (int)Math.floor(ox + cx * px);
            int z0 = (int)Math.floor(oz + cz * px);
            int x1 = Math.max(x0 + 1, (int)Math.floor(ox + (cx + 1) * px));
            int z1 = Math.max(z0 + 1, (int)Math.floor(oz + (cz + 1) * px));
            g.fill(x0, z0, x1, z1, (map == null ? 0x90000000 : 0x50000000) | rgb);
            if (px >= 2.0) {
               if (!sel.contains(cx, cz - 1)) {
                  g.fill(x0, z0, x1, z0 + 1, 0xFF000000 | rgb);
               }

               if (!sel.contains(cx, cz + 1)) {
                  g.fill(x0, z1 - 1, x1, z1, 0xFF000000 | rgb);
               }

               if (!sel.contains(cx - 1, cz)) {
                  g.fill(x0, z0, x0 + 1, z1, 0xFF000000 | rgb);
               }

               if (!sel.contains(cx + 1, cz)) {
                  g.fill(x1 - 1, z0, x1, z1, 0xFF000000 | rgb);
               }
            }
         }
      }

      ChunkSelection guard = guards && map != null ? map.guardedNow() : null;
      if (guard != null && !guard.isEmpty() && px >= 2.0) {
         for (int cz = minZ - 2; cz <= maxZ + 2; cz++) {
            for (int cx = minX - 2; cx <= maxX + 2; cx++) {
               if (!guard.contains(cx, cz)) {
                  continue;
               }

               int x0 = (int)Math.floor(ox + cx * px);
               int z0 = (int)Math.floor(oz + cz * px);
               int x1 = (int)Math.floor(ox + (cx + 1) * px);
               int z1 = (int)Math.floor(oz + (cz + 1) * px);
               g.fill(x0, z0, x1, z1, 0x40FFD84A);
               for (int d = x0 - z1; d < x1 - z0; d += 3) {
                  for (int zz = z0; zz < z1; zz++) {
                     int xx = zz + d;
                     if (xx >= x0 && xx < x1) {
                        g.fill(xx, zz, xx + 1, zz + 1, 0xA0FFD84A);
                     }
                  }
               }
            }
         }
      }

      g.unscissor();
   }
}
