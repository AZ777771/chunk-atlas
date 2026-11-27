package dev.chunkatlas.minimap.hud;

import dev.chunkatlas.Footsteps;
import dev.chunkatlas.Settings;
import dev.chunkatlas.gui.MapMarkers;
import dev.chunkatlas.gui.MobMarks;
import dev.chunkatlas.minimap.Config;
import dev.chunkatlas.minimap.map.MapCache;
import dev.chunkatlas.minimap.map.SeedTiles;
import dev.chunkatlas.minimap.waypoint.Waypoint;
import dev.chunkatlas.minimap.waypoint.Waypoints;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Skin;
import dev.chunkatlas.port.Tex;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;

public final class MinimapHud {
   public static final int PAPER = 0xFF14161B;
   public static final int PAPER_EDGE = 0x66000000;

   private static final int GLASS = 0x99000000;
   private static final int GLASS_EDGE = 0x66000000;
   private static final int CHIP = 0x8C000000;

   private static final int FADED = 0x90;

   private static final int PINNED = 14;

   private static final int FOOTSTEPS_SHOWN = 600;
   private static final int MAX_TEXELS = 512;

   private static final int LINE = 10;
   private static final int INFO_PAD = 2;
   private static final int INFO_GAP = 2;

   public static final int CUSTOM_MIN = 48;
   public static final int CUSTOM_MAX = 320;

   private final MapCache cache;
   private final Slimes slimes = new Slimes();

   private final MobMarks.Near near = new MobMarks.Near();
   private final MobMarks.Batch marked = new MobMarks.Batch();
   private final float[] spot = new float[2];

   private final Terrain terrain = new Terrain();
   private final Terrain.Look look = new Terrain.Look();
   private float[] strips = new float[0];
   private Tex ring;
   private int ringKey;
   private boolean enlarged;
   private boolean hiddenForScreen;

   private int[] trail;
   private long trailAt;

   public MinimapHud(MapCache cache) {
      this.cache = cache;
   }

   public static String decoration(String name) {
      return "minecraft:textures/map/decorations/" + name + ".png";
   }

   public void enlarged(boolean on) {
      this.enlarged = on;
   }

   public boolean enlarged() {
      return this.enlarged;
   }

   public void hideForScreen(boolean hide) {
      this.hiddenForScreen = hide;
   }

   public Slimes slimes() {
      return this.slimes;
   }

   public Layout layout(int screenW, int screenH, Config cfg) {
      int infoH = infoHeight(this.lines(cfg));
      int box = boxSize(cfg, screenW, screenH, infoH);
      if (this.enlarged) {
         if (cfg.enlargeView == Config.EnlargeView.CENTRED) {
            int big = Math.max(box, Math.min(screenW - 40, screenH - 40 - infoH));
            return new Layout((screenW - big) / 2, (screenH - big - infoH) / 2, big, infoH);
         }

         box = Math.min(box * 2, Math.min(screenW - 8, screenH - 8 - infoH));
      }

      int x = cfg.anchorRight ? screenW - cfg.offsetX - box : cfg.offsetX;
      int y = cfg.anchorBottom ? screenH - cfg.offsetY - box - infoH : cfg.offsetY;
      x = Mth.clamp(x, 0, Math.max(0, screenW - box));
      y = Mth.clamp(y, 0, Math.max(0, screenH - box - infoH));
      return new Layout(x, y, box, infoH);
   }

   public static int infoHeight(int lines) {
      return lines == 0 ? 0 : INFO_GAP + lines * LINE + 2 * INFO_PAD - 1;
   }

   public static int boxSize(Config cfg, int screenW, int screenH) {
      return boxSize(cfg, screenW, screenH, 0);
   }

   public static int boxSize(Config cfg, int screenW, int screenH, int infoH) {
      if (cfg.size == Config.Size.AUTO) {
         return Mth.clamp(Math.min(screenW, screenH) * 2 / 7, 64, 200);
      }

      if (cfg.size == Config.Size.CUSTOM) {
         return customSize(cfg.customSize, screenW, screenH, infoH);
      }

      return cfg.size.px;
   }

   public static int customSize(int wanted, int screenW, int screenH, int infoH) {
      int fit = Math.min(screenW, screenH - infoH) - 8;
      return Math.max(Math.min(CUSTOM_MIN, Math.max(16, fit)), Math.min(Math.clamp(wanted, CUSTOM_MIN, CUSTOM_MAX), fit));
   }

   private int lines(Config cfg) {
      int n = 0;
      if (cfg.showCoords) n++;
      if (cfg.showBiome) n++;
      if (cfg.showLight) n++;
      if (cfg.showTime) n++;
      if (cfg.showAngle) n++;
      return n;
   }

   public double scale(Config cfg) {
      return this.enlarged && cfg.enlargeZoomOut ? Config.ZOOMS[0] : cfg.scale() * (cfg.smartZoom ? this.smart : 1.0);
   }

   private double smart = 1.0;

   private void smartZoom(Entity viewer) {
      double v = Math.hypot(viewer.getX() - viewer.xo, viewer.getZ() - viewer.zo);
      double target = v > 1.2 ? 0.25 : v > 0.45 ? 0.5 : v > 0.24 ? 0.75 : 1.0;
      if (this.cache.caves()) {
         target = Math.max(target, 1.5);
      }

      this.smart += (target - this.smart) * 0.04;
      if (Math.abs(this.smart - target) < 0.01) {
         this.smart = target;
      }
   }

   public void extract(Gfx g, float partial) {
      dev.chunkatlas.Perf.frame();
      Minecraft mc = Minecraft.getInstance();
      Config cfg = Config.get();
      if (!cfg.enabled || this.hiddenForScreen || mc.player == null || mc.level == null) {
         return;
      }

      Layout at = this.layout(g.width(), g.height(), cfg);
      this.draw(g, mc, cfg, at, partial);
   }

   public void draw(Gfx g, Minecraft mc, Config cfg, Layout at, float partial) {
      Entity viewer = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
      if (cfg.smartZoom) {
         this.smartZoom(viewer);
      }

      double px = Mth.lerp(partial, viewer.xo, viewer.getX());
      double pz = Mth.lerp(partial, viewer.zo, viewer.getZ());
      float yaw = Mth.rotLerp(partial, viewer.yRotO, viewer.getYRot());
      double scale = this.scale(cfg);
      boolean circle = cfg.shape == Config.Shape.CIRCLE;
      boolean rotate = !cfg.northLock;
      int box = at.box();
      int border = cfg.frame ? 2 : 0;
      int inner = box - border * 2;

      this.cache.want((int)Math.ceil(inner * 0.75 / scale / 16.0) + 1);
      int guiScale = Math.max(1, (int)Math.round(mc.getWindow().getGuiScale()));
      int n = Math.min(MAX_TEXELS, box * guiScale);
      int alpha = Math.round(cfg.opacity * 255.0F) << 24;
      if (cfg.frame && !circle) {
         int glass = Math.round(cfg.opacity * (GLASS >>> 24)) << 24;
         int edge = Math.round(cfg.opacity * (GLASS_EDGE >>> 24)) << 24;
         g.fill(at.x(), at.y(), at.x() + box, at.y() + box, glass);
         g.outline(at.x(), at.y(), box, box, edge);
      }

      long t0 = System.nanoTime();
      this.terrain(g, mc, cfg, n, at, border, px, pz, rotate ? yaw : 180.0F, scale, circle, alpha | 0xFFFFFF);
      dev.chunkatlas.Perf.hud(System.nanoTime() - t0);
      Marks marks = new Marks(at.x() + box / 2.0F, at.y() + box / 2.0F, inner / 2.0F, circle, px, pz, rotate ? yaw : 180.0F, scale);
      g.scissor(at.x() + border, at.y() + border, at.x() + box - border, at.y() + box - border);
      if (Settings.footsteps()) {
         this.footsteps(g, marks);
      }

      dev.chunkatlas.engine.BorderBook.Border edge = mc.level == null ? null : dev.chunkatlas.TaskClient.border(Ids.of(mc.level.dimension()));
      if (edge != null) {
         this.border(g, marks, edge.area());
      }

      if (cfg.radar) {
         this.radar(g, mc, cfg, marks, partial, viewer);
      }

      g.unscissor();

      if (cfg.waypointsOnMap) {
         this.waypoints(g, mc, marks);
      }

      this.spawn(g, mc, marks);
      if (cfg.radarPlayers) {
         this.players(g, mc, marks, partial, viewer);
      }

      this.you(g, mc, marks, viewer, rotate ? 180.0F : yaw);
      this.compass(g, mc.font, marks, cfg, rotate);

      this.info(g, mc, cfg, at, viewer);
   }

   private void footsteps(Gfx g, Marks marks) {
      long now = System.currentTimeMillis();
      if (this.trail == null || now - this.trailAt > 500L) {
         this.trailAt = now;
         this.trail = Footsteps.live();
      }

      int[] p = this.trail;
      int n = p.length / 3;
      int fade = Settings.footstepsFade();
      long nowS = now / 1000L;
      int first = Math.max(0, n - FOOTSTEPS_SHOWN);
      int shown = n - first;
      double reach = marks.radius * 1.5;
      for (int i = first + 1; i < n; i++) {
         int ax = p[3 * (i - 1)];
         int az = p[3 * (i - 1) + 1];
         int bx = p[3 * i];
         int bz = p[3 * i + 1];
         if (Math.abs(bx - ax) + Math.abs(bz - az) > 64) {
            continue;
         }

         int alpha;
         if (fade == Settings.FADE_NEVER) {
            alpha = (int)(0xD0L * (i - first) / Math.max(1, shown));
         } else {
            long age = p[3 * i + 2] == 0 ? Long.MAX_VALUE : nowS - p[3 * i + 2];
            alpha = age >= fade ? 0 : (int)(0xD0L * (fade - age) / fade);
         }

         if (alpha < 8) {
            continue;
         }

         float[] a = marks.at(ax + 0.5, az + 0.5);
         float[] b = marks.at(bx + 0.5, bz + 0.5);
         if (Math.min(Math.hypot(a[0] - marks.cx, a[1] - marks.cy), Math.hypot(b[0] - marks.cx, b[1] - marks.cy)) > reach) {
            continue;
         }

         int colour = alpha << 24 | 0xFFFFFF;
         double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
         int steps = Math.max(1, (int)Math.ceil(len));
         for (int s = 0; s <= steps; s++) {
            int x = (int)Math.floor(a[0] + (b[0] - a[0]) * s / steps);
            int y = (int)Math.floor(a[1] + (b[1] - a[1]) * s / steps);

            double dx = x + 0.5 - marks.cx;
            double dy = y + 0.5 - marks.cy;
            boolean in = marks.circle ? dx * dx + dy * dy <= (marks.radius - 1.0) * (marks.radius - 1.0)
               : Math.abs(dx) <= marks.radius && Math.abs(dy) <= marks.radius;
            if (in) {
               g.fill(x, y, x + 1, y + 1, colour);
            }
         }
      }
   }

   private void border(Gfx g, Marks marks, dev.chunkatlas.engine.Area area) {
      double[] p = area.outline();
      int n = p.length / 2;
      double reach = marks.radius * 1.5 / marks.scale;
      for (int i = 0; i < n; i++) {
         double ax = p[2 * i];
         double az = p[2 * i + 1];
         double bx = p[2 * ((i + 1) % n)];
         double bz = p[2 * ((i + 1) % n) + 1];

         double dx = bx - ax;
         double dz = bz - az;
         double fx = ax - marks.px;
         double fz = az - marks.pz;
         double a = dx * dx + dz * dz;
         double b = 2.0 * (fx * dx + fz * dz);
         double c = fx * fx + fz * fz - reach * reach;
         double disc = b * b - 4.0 * a * c;
         if (a < 1.0E-9 || disc < 0.0) {
            continue;
         }

         double root = Math.sqrt(disc);
         double t0 = Math.max(0.0, (-b - root) / (2.0 * a));
         double t1 = Math.min(1.0, (-b + root) / (2.0 * a));
         if (t0 >= t1) {
            continue;
         }

         double len = Math.sqrt(a) * (t1 - t0) * marks.scale;
         int steps = Math.max(1, (int)Math.ceil(len));
         for (int s = 0; s <= steps; s++) {
            double t = t0 + (t1 - t0) * s / steps;
            float[] at = marks.at(ax + dx * t, az + dz * t);
            int x = (int)Math.floor(at[0]);
            int y = (int)Math.floor(at[1]);
            double ox = x + 0.5 - marks.cx;
            double oy = y + 0.5 - marks.cy;
            boolean in = marks.circle ? ox * ox + oy * oy <= (marks.radius - 1.0) * (marks.radius - 1.0)
               : Math.abs(ox) <= marks.radius && Math.abs(oy) <= marks.radius;
            if (in) {
               g.fill(x + 1, y + 1, x + 2, y + 2, 0x73000000);
               g.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
            }
         }
      }
   }

   private void terrain(Gfx g, Minecraft mc, Config cfg, int n, Layout at, int border, double px, double pz, float yaw, double scale, boolean circle, int argb) {
      int box = at.box();

      double t = (double)box / n;
      double half = box / 2.0;
      double mapRadius = half - border;
      double edge = half - 0.5 * t;

      double rad = Math.toRadians(yaw);
      double fx = -Math.sin(rad);
      double fz = Math.cos(rad);
      double rx = -Math.cos(rad);
      double rz = -Math.sin(rad);

      double reach = mapRadius * Math.sqrt(2.0) / scale + 1.0;
      int c0x = Mth.floor(px - reach) >> 4;
      int c0z = Mth.floor(pz - reach) >> 4;
      int c1x = Mth.floor(px + reach) >> 4;
      int c1z = Mth.floor(pz + reach) >> 4;
      SeedTiles seed = this.cache.seed();
      seed.frame();
      dev.chunkatlas.minimap.map.FarTiles far = this.cache.far();
      far.frame(mc);
      Terrain.Look look = this.look;
      look.cache = this.cache;
      look.seed = seed;
      look.far = far;

      look.seeded = seed.begin(c0x, c0z, c1x, c1z, this.cache.layer(), this.cache.look(), t / scale);
      look.useFar = !this.cache.caves();
      look.slimes = this.slimes;
      look.slimesShown = cfg.slimeChunks && this.slimes.known(mc);
      look.light = cfg.lightOverlay;
      look.grid = cfg.chunkGrid;
      look.blank = this.cache.caves() ? 0xFF101010 : PAPER;
      look.blocksPerTexel = t / scale;
      double density = scale / t;
      if (look.useFar && reach / 16.0 > mc.options.getEffectiveRenderDistance() - 1) {
         far.want(c0x, c0z, c1x, c1z, Math.clamp(4 - Terrain.shiftFor(density), 0, 4), px, pz);
      }

      int options = (look.grid ? 1 : 0) | (look.slimesShown ? 2 : 0) | (look.light ? 4 : 0) | (look.useFar ? 8 : 0) | (look.seeded ? 16 : 0) | (this.cache.caves() ? 32 : 0);
      this.terrain.update(look, px, pz, reach, density, options);
      if (look.seeded) {
         seed.end(px, pz);
      }

      if (this.strips.length < n * 4) {
         this.strips = new float[n * 4];
      }

      float[] st = this.strips;
      int rows = 0;
      if (!circle) {
         int a = border > 0 ? (int)Math.ceil(border / t - 0.5) : 0;
         int b = border > 0 ? (int)Math.floor((box - border) / t - 0.5) : n - 1;
         if (a <= b) {
            st[0] = (float)(at.x() + a * t);
            st[1] = (float)(at.x() + (b + 1) * t);
            st[2] = (float)(at.y() + a * t);
            st[3] = (float)(at.y() + (b + 1) * t);
            rows = 1;
         }
      } else {
         double radius = border > 0 ? mapRadius : edge;
         int lastA = Integer.MIN_VALUE;
         int lastB = Integer.MIN_VALUE;
         int lastRow = Integer.MIN_VALUE;
         for (int ty = 0; ty < n; ty++) {
            double oy = (ty + 0.5) * t - half;
            double left = radius * radius - oy * oy;
            if (left < 0.0) {
               continue;
            }

            double w = Math.sqrt(left);
            int a = (int)Math.ceil((half - w) / t - 0.5);
            int b = (int)Math.floor((half + w) / t - 0.5);
            if (a > b) {
               continue;
            }

            if (a == lastA && b == lastB && ty == lastRow + 1) {
               st[(rows - 1) * 4 + 3] = (float)(at.y() + (ty + 1) * t);
            } else {
               st[rows * 4] = (float)(at.x() + a * t);
               st[rows * 4 + 1] = (float)(at.x() + (b + 1) * t);
               st[rows * 4 + 2] = (float)(at.y() + ty * t);
               st[rows * 4 + 3] = (float)(at.y() + (ty + 1) * t);
               rows++;
            }

            lastA = a;
            lastB = b;
            lastRow = ty;
         }
      }

      this.terrain.draw(g, st, rows, (float)(at.x() + half), (float)(at.y() + half), px, pz, rx, rz, fx, fz, scale, argb);
      if (circle && border > 0) {
         g.blit(this.ring(n, box, border), at.x(), at.y(), 0.0F, 0.0F, box, box, n, n, n, n, argb);
      }
   }

   private Tex ring(int n, int box, int border) {
      int key = (n * 4099 + box) * 31 + border;
      if (this.ring != null && this.ringKey == key) {
         return this.ring;
      }

      if (this.ring != null) {
         this.ring.close();
      }

      Tex image = Tex.create("Minimap ring", n, n);
      double t = (double)box / n;
      double half = box / 2.0;
      double mapRadius = half - border;
      double edge = half - 0.5 * t;
      for (int ty = 0; ty < n; ty++) {
         double oy = (ty + 0.5) * t - half;
         for (int tx = 0; tx < n; tx++) {
            double ox = (tx + 0.5) * t - half;
            double r = Math.sqrt(ox * ox + oy * oy);
            image.set(tx, ty, r > edge || r <= mapRadius ? 0 : r > edge - Math.max(t, 1.0) ? PAPER_EDGE : PAPER);
         }
      }

      image.upload();
      this.ring = image;
      this.ringKey = key;
      return image;
   }

   private void radar(Gfx g, Minecraft mc, Config cfg, Marks marks, float partial, Entity viewer) {
      List<Entity> mobs = this.near.collect(mc, viewer, cfg);
      this.marked.clear();
      for (Entity e : mobs) {
         if (marks.put(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.zo, e.getZ()), this.spot)) {
            float yaw = e.getYHeadRot() - marks.yaw + 180.0F;
            this.marked.add(e, this.spot[0], this.spot[1], yaw, MobMarks.alpha(e, viewer));
         }
      }

      this.marked.draw(g, cfg.radarStyle, MobMarks.size(marks.scale), -100, -100);
   }

   private void waypoints(Gfx g, Minecraft mc, Marks marks) {
      String here = Ids.of(mc.level.dimension());
      boolean deaths = Config.get().deathpoints;

      for (Waypoint w : Waypoints.all()) {
         if (!w.visible || w.death && !deaths) {
            continue;
         }

         double[] p = w.placeIn(here);
         if (p == null) {
            continue;
         }

         float[] at = marks.place(p[0], p[2], true, PINNED);
         int x = Math.round(at[0]);
         int y = Math.round(at[1]);
         if (at[2] != 0.0F) {
            MapMarkers.pin(g, mc.font, x, y + MapMarkers.PIN_H / 2, w, FADED);
            MapMarkers.edgeArrow(g, x, y, marks.outward(p[0], p[2]));
         } else {
            MapMarkers.pin(g, mc.font, x, y, w, 0xFF);
            if (this.enlarged && !w.name.isBlank()) {
               MapMarkers.tagCentred(g, mc.font, w.name, x, y + 6, 0xFFFFFFFF);
            }
         }
      }
   }

   private void spawn(Gfx g, Minecraft mc, Marks marks) {
      BlockPos pos = Game.spawn(mc.level);
      if (pos == null) {
         return;
      }

      float[] at = marks.place(pos.getX() + 0.5, pos.getZ() + 0.5, true, PINNED);
      int x = Math.round(at[0]);
      int y = Math.round(at[1]);
      if (at[2] != 0.0F) {
         MapMarkers.star(g, x, y, FADED);
         MapMarkers.edgeArrow(g, x, y, marks.outward(pos.getX() + 0.5, pos.getZ() + 0.5));
      } else {
         MapMarkers.star(g, x, y);
      }
   }

   private void players(Gfx g, Minecraft mc, Marks marks, float partial, Entity viewer) {
      for (AbstractClientPlayer p : mc.level.players()) {
         if (p == viewer || p == mc.player || !p.isAlive() || p.isInvisibleTo(mc.player)) {
            continue;
         }

         double ex = Mth.lerp(partial, p.xo, p.getX());
         double ez = Mth.lerp(partial, p.zo, p.getZ());
         float[] at = marks.place(ex, ez, true, PINNED);
         int x = Math.round(at[0]);
         int y = Math.round(at[1]);
         if (at[2] != 0.0F) {
            MapMarkers.face(g, x, y, Skin.of(p), FADED);
            MapMarkers.edgeArrow(g, x, y, marks.outward(ex, ez));
         } else {
            MapMarkers.face(g, x, y, Skin.of(p));
            MapMarkers.tagCentred(g, mc.font, p.getScoreboardName(), x, y + 11, 0xFFFFFFFF);
         }
      }
   }

   private void you(Gfx g, Minecraft mc, Marks marks, Entity viewer, float yaw) {
      Skin skin = viewer instanceof AbstractClientPlayer ? Skin.of(viewer) : Skin.you(mc);
      MapMarkers.you(g, Math.round(marks.cx), Math.round(marks.cy), yaw, skin);
   }

   private void compass(Gfx g, Font font, Marks marks, Config cfg, boolean rotate) {
      if (cfg.compass <= 0) {
         return;
      }

      String[] letters = {"N", "E", "S", "W"};
      double[][] dirs = {{0.0, -1.0}, {1.0, 0.0}, {0.0, 1.0}, {-1.0, 0.0}};
      int count = cfg.compass >= 2 ? 4 : 1;
      float distance = cfg.frame && !rotate ? marks.radius : marks.radius - 4.0F;
      for (int i = 0; i < count; i++) {
         float[] at = marks.direction(dirs[i][0], dirs[i][1], distance);
         int x = Math.round(at[0]);
         int y = Math.round(at[1]);
         int w = font.width(letters[i]) + 4;
         g.fill(x - w / 2, y - 5, x - w / 2 + w, y + 5, CHIP);
         g.centered(font, letters[i], x, y - 4, i == 0 ? 0xFFFFFFFF : 0xFFC8C8C8);
      }
   }

   private void info(Gfx g, Minecraft mc, Config cfg, Layout at, Entity viewer) {
      List<String> lines = new ArrayList<>();
      BlockPos pos = viewer.blockPosition();
      if (cfg.showCoords) {
         lines.add(I18n.get("chunkatlas_minimap.info.coords", pos.getX(), pos.getY(), pos.getZ()));
      }

      if (cfg.showBiome) {
         Holder<Biome> biome = mc.level.getBiome(pos);
         lines.add(biome.unwrapKey().map(k -> {
            String key = "biome." + Ids.namespace(k) + "." + Ids.path(k);
            return net.minecraft.locale.Language.getInstance().has(key) ? I18n.get(key) : Ids.of(k);
         }).orElse("?"));
      }

      if (cfg.showLight) {
         lines.add(I18n.get("chunkatlas_minimap.info.light", mc.level.getBrightness(LightLayer.BLOCK, pos), mc.level.getBrightness(LightLayer.SKY, pos)));
      }

      if (cfg.showTime) {
         long ticks = Game.dayTime(mc.level);
         long dayTime = Math.floorMod(ticks + 6000L, 24000L);
         lines.add(I18n.get("chunkatlas_minimap.info.time", ticks / 24000L + 1, String.format(Locale.ROOT, "%02d:%02d", dayTime / 1000L, dayTime % 1000L * 60L / 1000L)));
      }

      if (cfg.showAngle) {
         float yaw = Mth.wrapDegrees(viewer.getYRot());
         lines.add(String.format(Locale.ROOT, "%s %.1f / %.1f", facing(yaw), yaw, viewer.getXRot()));
      }

      if (lines.isEmpty()) {
         return;
      }

      int x = at.x();
      int w = at.box();
      int y = at.y() + at.box() + INFO_GAP;
      int h = lines.size() * LINE + 2 * INFO_PAD - 1;
      int glass = Math.round(cfg.opacity * (GLASS >>> 24)) << 24;
      g.fill(x, y, x + w, y + h, glass);
      g.outline(x, y, w, h, Math.round(cfg.opacity * (GLASS_EDGE >>> 24)) << 24);
      int room = w - 4;
      int cx = x + w / 2;
      int ty = y + INFO_PAD;
      for (String line : lines) {
         String shown = fit(mc.font, line, room);
         g.centered(mc.font, shown, cx, ty, 0xFFFFFFFF);
         ty += LINE;
      }
   }

   static String fit(Font font, String line, int room) {
      if (font.width(line) <= room) {
         return line;
      }

      String tight = line.replace(" · ", "·").replace(", ", ",");
      if (font.width(tight) <= room) {
         return tight;
      }

      return font.plainSubstrByWidth(tight, Math.max(0, room - font.width("…"))) + "…";
   }

   private static String facing(float yaw) {
      String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
      return names[Math.floorMod(Math.round(yaw / 45.0F), 8)];
   }

   public record Layout(int x, int y, int box, int infoHeight) {
      public int height() {
         return this.box + this.infoHeight;
      }
   }

   static final class Marks {
      final float cx;
      final float cy;
      final float radius;
      final boolean circle;
      final double px;
      final double pz;
      final double scale;

      final float yaw;
      final double fx;
      final double fz;
      final double rx;
      final double rz;

      Marks(float cx, float cy, float radius, boolean circle, double px, double pz, float yaw, double scale) {
         this.cx = cx;
         this.cy = cy;
         this.radius = radius;
         this.circle = circle;
         this.px = px;
         this.pz = pz;
         this.scale = scale;
         this.yaw = yaw;
         double rad = Math.toRadians(yaw);
         this.fx = -Math.sin(rad);
         this.fz = Math.cos(rad);
         this.rx = -Math.cos(rad);
         this.rz = -Math.sin(rad);
      }

      float[] at(double x, double z) {
         double dx = x - this.px;
         double dz = z - this.pz;
         return new float[]{(float)(this.cx + (dx * this.rx + dz * this.rz) * this.scale), (float)(this.cy - (dx * this.fx + dz * this.fz) * this.scale)};
      }

      double outward(double x, double z) {
         float[] at = this.at(x, z);
         return Math.atan2(at[1] - this.cy, at[0] - this.cx);
      }

      boolean put(double x, double z, float[] into) {
         double dx = x - this.px;
         double dz = z - this.pz;
         double sx = (dx * this.rx + dz * this.rz) * this.scale;
         double sy = -(dx * this.fx + dz * this.fz) * this.scale;
         double limit = Math.max(2.0, this.radius - 4);
         if (this.circle ? sx * sx + sy * sy > limit * limit : Math.max(Math.abs(sx), Math.abs(sy)) > limit) {
            return false;
         }

         into[0] = (float)(this.cx + sx);
         into[1] = (float)(this.cy + sy);
         return true;
      }

      float[] place(double x, double z, boolean clamp, int margin) {
         double dx = x - this.px;
         double dz = z - this.pz;
         double sx = (dx * this.rx + dz * this.rz) * this.scale;
         double sy = -(dx * this.fx + dz * this.fz) * this.scale;
         double limit = Math.max(2.0, this.radius - margin);
         boolean out;
         if (this.circle) {
            double r = Math.sqrt(sx * sx + sy * sy);
            out = r > limit;
            if (out && clamp) {
               sx = sx / r * limit;
               sy = sy / r * limit;
            }
         } else {
            double m = Math.max(Math.abs(sx), Math.abs(sy));
            out = m > limit;
            if (out && clamp) {
               sx = sx / m * limit;
               sy = sy / m * limit;
            }
         }

         if (out && !clamp) {
            return null;
         }

         return new float[]{(float)(this.cx + sx), (float)(this.cy + sy), out ? 1.0F : 0.0F};
      }

      float[] direction(double dx, double dz, float distance) {
         double sx = dx * this.rx + dz * this.rz;
         double sy = -(dx * this.fx + dz * this.fz);
         double len = Math.sqrt(sx * sx + sy * sy);
         if (!this.circle) {
            double m = Math.max(Math.abs(sx), Math.abs(sy));
            return new float[]{(float)(this.cx + sx / m * distance), (float)(this.cy + sy / m * distance)};
         }

         return new float[]{(float)(this.cx + sx / len * distance), (float)(this.cy + sy / len * distance)};
      }
   }
}
