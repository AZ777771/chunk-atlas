package dev.chunkatlas.gui;

import dev.chunkatlas.engine.PasteLimit;
import dev.chunkatlas.engine.BlockColours;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.PasteTerrain;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.engine.WorldMap;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Tex;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.client.Minecraft;

final class PasteView {
   private static final int ACCENT = 0x8FB8FF;

   private static final int PICTURE = 0xEBFFFFFF;

   private static final long SETTLE = 250L;
   private static final int DOTS = 256;
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private ExecutorService worker;

   private volatile int generation;
   private WorldLayout.Dimension source;
   private ChunkSelection chunks;
   private BlockColours colours;

   private final ArrayDeque<Map.Entry<Long, int[]>> uploads = new ArrayDeque<>();
   private final Map<Long, Tex> textures = new HashMap<>();
   private int side;
   private boolean pictureAsked;
   private boolean pictureIn;
   private Tex dots;

   private Rings rings;
   private String ringsAsked;
   private boolean ringsBusy;

   private PasteTerrain.Lift lift;
   private String liftFor;
   private String liftAsked;
   private boolean liftBusy;
   private String movedKey;
   private long movedAt;

   private record Rings(ChunkSelection ring, ChunkSelection band, int dx, int dz, int blend, int[] corner) {
   }

   record Here(
      int dx, int dz, int blend, boolean keep, boolean smart, ChunkSelection shown, ChunkSelection guard, WorldMap.Index index, PasteTerrain.SeedGround seed,
      boolean band
   ) {
      Here(int dx, int dz, int blend, boolean keep, boolean smart, ChunkSelection shown, ChunkSelection guard, WorldMap.Index index, PasteTerrain.SeedGround seed) {
         this(dx, dz, blend, keep, smart, shown, guard, index, seed, true);
      }
   }

   PasteView(MapScreen map) {
      this.map = map;
   }

   void start(WorldLayout.Dimension source, ChunkSelection chunks, BlockColours colours, int style) {
      this.stop();
      this.source = source;
      this.chunks = chunks;
      this.colours = colours;
      int mine = this.generation;
      this.pictureAsked = true;
      this.run(() -> {
         PasteTerrain.Picture p = PasteTerrain.picture(source, chunks, colours, style, () -> mine != this.generation);
         this.mc.execute(() -> {
            if (mine == this.generation && p != null) {
               this.side = p.side();
               this.uploads.addAll(p.regions().entrySet());
               this.pictureIn = this.uploads.isEmpty() && !p.regions().isEmpty();
            }
         });
      });
   }

   void startInPlace(WorldLayout.Dimension dimension, ChunkSelection chunks, BlockColours colours) {
      this.stop();
      this.source = dimension;
      this.chunks = chunks;
      this.colours = colours;
   }

   ChunkSelection shownChunks() {
      return this.chunks;
   }

   void stop() {
      this.generation++;
      for (Tex t : this.textures.values()) {
         t.close();
      }

      this.textures.clear();
      this.uploads.clear();
      this.pictureAsked = false;
      this.pictureIn = false;
      this.rings = null;
      this.ringsAsked = null;
      this.ringsBusy = false;
      this.lift = null;
      this.liftFor = null;
      this.liftAsked = null;
      this.liftBusy = false;
      this.movedKey = null;
      this.chunks = null;
   }

   void dispose() {
      this.stop();
      if (this.dots != null) {
         this.dots.close();
         this.dots = null;
      }

      if (this.worker != null) {
         this.worker.shutdownNow();
         this.worker = null;
      }
   }

   private void run(Runnable work) {
      if (this.worker == null) {
         this.worker = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Chunk Atlas paste");
            t.setDaemon(true);
            return t;
         });
      }

      try {
         this.worker.submit(() -> {
            try {
               work.run();
            } catch (RuntimeException e) {
               this.mc.execute(() -> {
                  this.ringsBusy = false;
                  this.liftBusy = false;
               });
            }
         });
      } catch (RejectedExecutionException ignored) {
      }
   }

   void frame(PasteView.Here h) {
      if (this.chunks == null) {
         return;
      }

      this.takePictures();
      String ringKey = h.dx() + "," + h.dz() + "," + h.blend() + "," + h.keep() + "," + h.band();
      if (h.blend() > 0 && !ringKey.equals(this.ringsAsked) && !this.ringsBusy && h.index() != null) {
         this.askRings(h, ringKey);
      }

      String key = h.dx() + "," + h.dz() + "," + h.blend();
      long now = System.currentTimeMillis();
      if (!key.equals(this.movedKey)) {
         this.movedKey = key;
         this.movedAt = now;
      }

      if (h.smart() && h.blend() > 0 && h.index() != null && !key.equals(this.liftAsked) && !this.liftBusy && now - this.movedAt >= SETTLE) {
         this.askLift(h, key);
      }
   }

   private void takePictures() {
      long deadline = System.nanoTime() + 4_000_000L;
      Map.Entry<Long, int[]> e;
      while (System.nanoTime() < deadline && (e = this.uploads.poll()) != null) {
         int[] px = e.getValue();
         Tex image = Tex.create("Chunk Atlas paste", this.side, this.side);
         int[] abgr = new int[px.length];
         for (int k = 0; k < px.length; k++) {
            int c = px[k];
            abgr[k] = c & 0xFF00FF00 | (c >> 16 & 0xFF) | (c & 0xFF) << 16;
         }

         image.putAbgr(abgr);
         image.upload();
         this.textures.put(e.getKey(), image);
         if (this.uploads.isEmpty()) {
            this.pictureIn = true;
         }
      }
   }

   private void askRings(PasteView.Here h, String key) {
      this.ringsAsked = key;
      this.ringsBusy = true;
      int mine = this.generation;
      ChunkSelection shown = h.shown().copy();
      ChunkSelection guard = h.guard().copy();
      WorldMap.Index index = h.index();
      this.run(() -> {
         PasteLimit limit = PasteLimit.of(shown, h.blend(), h.keep(), guard, index::has, h.band());
         ChunkSelection ring = limit.ring();
         ChunkSelection band = limit.band();
         int[] bounds = Thumbnail.bounds(ring);
         Rings r = new Rings(ring, band, h.dx(), h.dz(), h.blend(), bounds);
         this.mc.execute(() -> {
            if (mine == this.generation) {
               this.rings = r;
               this.ringsBusy = false;
            }
         });
      });
   }

   private void askLift(PasteView.Here h, String key) {
      this.liftAsked = key;
      this.liftBusy = true;
      int mine = this.generation;
      WorldLayout.Dimension from = this.source;
      ChunkSelection chunks = this.chunks;
      BlockColours colours = this.colours;
      WorldMap.Index index = h.index();
      this.run(() -> {
         PasteTerrain.Lift l = PasteTerrain.lift(from, chunks, index.dimension(), index::has, h.dx(), h.dz(), h.blend(), h.seed(), colours, () -> mine != this.generation);
         this.mc.execute(() -> {
            if (mine == this.generation) {
               this.lift = l;
               this.liftFor = key;
               this.liftBusy = false;
            }
         });
      });
   }

   PasteTerrain.Lift lift(int dx, int dz, int blend) {
      return (dx + "," + dz + "," + blend).equals(this.liftFor) ? this.lift : null;
   }

   boolean liftDone(int dx, int dz, int blend) {
      return (dx + "," + dz + "," + blend).equals(this.liftFor);
   }

   PasteTerrain.Lift lastLift() {
      return this.lift;
   }

   boolean ringReady(int dx, int dz, int blend) {
      Rings r = this.rings;
      return blend <= 0 || r != null && r.dx() == dx && r.dz() == dz && r.blend() == blend;
   }

   boolean pictureReady() {
      return this.pictureIn;
   }

   boolean pictureAsked() {
      return this.pictureAsked;
   }

   void drawPicture(Gfx g, int baseRx, int baseRz, int dx, int dz) {
      for (Map.Entry<Long, Tex> e : this.textures.entrySet()) {
         int rx = (int)(e.getKey() >> 32);
         int rz = (int)(long)e.getKey();
         int x = (rx * 32 + dx) * 16 - baseRx * 512;
         int z = (rz * 32 + dz) * 16 - baseRz * 512;
         g.blit(e.getValue(), x, z, 0.0F, 0.0F, 512, 512, this.side, this.side, this.side, this.side, PICTURE);
      }
   }

   int[] drawScreen(Gfx g, ChunkSelection shown, int dx, int dz, int blend) {
      int[] label = null;
      Rings r = this.rings;
      if (blend > 0 && r != null && r.blend() == blend) {
         int ox = dx - r.dx();
         int oz = dz - r.dz();
         this.edges(g, r.band(), ox, oz, 0x40000000 | ACCENT, false);
         this.hatch(g, r.ring(), ox, oz);
         this.edges(g, r.ring(), ox, oz, 0xFF000000 | ACCENT, true);
         if (r.corner() != null) {
            label = new int[]{(int)Math.floor(this.map.seedScreenX((r.corner()[0] + ox) * 16.0)), (int)Math.floor(this.map.seedScreenY((r.corner()[1] + oz) * 16.0))};
         }
      }

      this.edges(g, shown, 0, 0, 0xFFFFFFFF, true);
      return label;
   }

   private void hatch(Gfx g, ChunkSelection sel, int ox, int oz) {
      Tex tex = this.dotTexture();
      int top = this.map.seedTop();
      int bottom = this.map.seedBottom();
      int width = this.map.width;
      for (long region : sel.regions()) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         int bx = (rx * 32 + ox) * 16;
         int bz = (rz * 32 + oz) * 16;
         if (this.map.seedScreenX(bx + 512.0) < 0 || this.map.seedScreenX(bx) > width || this.map.seedScreenY(bz + 512.0) < top || this.map.seedScreenY(bz) > bottom) {
            continue;
         }

         long[] bits = sel.bits(rx, rz);
         for (int row = 0; row < 32; row++) {
            int y0 = (int)Math.floor(this.map.seedScreenY(bz + row * 16.0));
            int y1 = (int)Math.floor(this.map.seedScreenY(bz + row * 16.0 + 16.0));
            if (y1 < top || y0 > bottom || y1 <= y0) {
               continue;
            }

            int col = 0;
            while (col < 32) {
               int i = row * 32 + col;
               if ((bits[i >> 6] & 1L << (i & 63)) == 0L) {
                  col++;
                  continue;
               }

               int start = col;
               while (col < 32 && (bits[(row * 32 + col) >> 6] & 1L << ((row * 32 + col) & 63)) != 0L) {
                  col++;
               }

               int x0 = (int)Math.floor(this.map.seedScreenX(bx + start * 16.0));
               int x1 = (int)Math.floor(this.map.seedScreenX(bx + col * 16.0));
               this.dotted(g, tex, Math.max(x0, -4), Math.max(y0, top - 4), Math.min(x1, width + 4), Math.min(y1, bottom + 4));
            }
         }
      }
   }

   private void dotted(Gfx g, Tex tex, int x0, int y0, int x1, int y1) {
      int tile = DOTS - 8;
      for (int y = y0; y < y1; y += tile) {
         for (int x = x0; x < x1; x += tile) {
            int w = Math.min(tile, x1 - x);
            int h = Math.min(tile, y1 - y);
            g.blit(tex, x, y, Math.floorMod(x, 4), Math.floorMod(y, 4), w, h, w, h, DOTS, DOTS);
         }
      }
   }

   private Tex dotTexture() {
      if (this.dots == null) {
         Tex image = Tex.create("Chunk Atlas paste ring", DOTS, DOTS);
         int[] abgr = new int[DOTS * DOTS];
         int fill = 0x1A000000 | (ACCENT & 0xFF) << 16 | (ACCENT >> 8 & 0xFF) << 8 | ACCENT >> 16 & 0xFF;
         int dot = 0xB4000000 | (ACCENT & 0xFF) << 16 | (ACCENT >> 8 & 0xFF) << 8 | ACCENT >> 16 & 0xFF;
         for (int y = 0; y < DOTS; y++) {
            for (int x = 0; x < DOTS; x++) {
               boolean on = (y & 3) == 0 && (x & 3) == 0 || (y & 3) == 2 && (x & 3) == 2;
               abgr[y * DOTS + x] = on ? dot : fill;
            }
         }

         image.putAbgr(abgr);
         image.upload();
         this.dots = image;
      }

      return this.dots;
   }

   private void edges(Gfx g, ChunkSelection sel, int ox, int oz, int colour, boolean dashed) {
      double px = this.map.seedZoom() * 16.0;
      if (px < 2.0) {
         return;
      }

      int top = this.map.seedTop();
      int bottom = this.map.seedBottom();
      int width = this.map.width;
      for (long region : sel.regions()) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         double bx = (rx * 32 + ox) * 16.0;
         double bz = (rz * 32 + oz) * 16.0;
         if (this.map.seedScreenX(bx + 512.0) < 0 || this.map.seedScreenX(bx) > width || this.map.seedScreenY(bz + 512.0) < top || this.map.seedScreenY(bz) > bottom) {
            continue;
         }

         long[] bits = sel.bits(rx, rz);
         for (int i = 0; i < 1024; i++) {
            if ((bits[i >> 6] >>> (i & 63) & 1L) == 0L) {
               continue;
            }

            int cx = rx * 32 + (i & 31);
            int cz = rz * 32 + (i >> 5);
            int x0 = (int)Math.floor(this.map.seedScreenX((cx + ox) * 16.0));
            int y0 = (int)Math.floor(this.map.seedScreenY((cz + oz) * 16.0));
            int x1 = (int)Math.floor(this.map.seedScreenX((cx + ox) * 16.0 + 16.0));
            int y1 = (int)Math.floor(this.map.seedScreenY((cz + oz) * 16.0 + 16.0));
            if (x1 < 0 || x0 > width || y1 < top || y0 > bottom) {
               continue;
            }

            if (!sel.contains(cx, cz - 1)) {
               line(g, x0, y0, x1 + 1, true, colour, dashed);
            }

            if (!sel.contains(cx, cz + 1)) {
               line(g, x0, y1, x1 + 1, true, colour, dashed);
            }

            if (!sel.contains(cx - 1, cz)) {
               line(g, x0, y0, y1 + 1, false, colour, dashed);
            }

            if (!sel.contains(cx + 1, cz)) {
               line(g, x1, y0, y1 + 1, false, colour, dashed);
            }
         }
      }
   }

   private static void line(Gfx g, int x, int y, int end, boolean across, int colour, boolean dashed) {
      int from = across ? x : y;
      if (!dashed) {
         if (across) {
            g.fill(x, y, end, y + 1, colour);
         } else {
            g.fill(x, y, x + 1, end, colour);
         }

         return;
      }

      for (int a = from; a < end; ) {
         int phase = Math.floorMod(a, 6);
         int b = Math.min(end, a + (phase < 3 ? 3 - phase : 6 - phase));
         if (phase < 3) {
            if (across) {
               g.fill(a, y, b, y + 1, colour);
            } else {
               g.fill(x, a, x + 1, b, colour);
            }
         }

         a = b;
      }
   }
}
