package dev.chunkatlas.gui;

import dev.chunkatlas.minimap.Config;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.MobLook;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

public final class MobMarks {
   public static final int HOSTILE = 0xFFFF6B6B;
   public static final int NEUTRAL = 0xFFFFD84A;
   public static final int PASSIVE = 0xFF7FE07F;

   public static final double FAR_Y = 10.0;
   public static final int FADED = 0x66;

   public static final int MAX = 200;

   private static final int SHADE = 0x73;

   public static final int SMALL = 4;
   public static final int LARGE = 8;

   private static final float MERGE = 0.6F;

   private static final int[][] ARROWS = arrows();

   private static final int[][] NOSES = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

   private static final int[][] DIGITS = {
      {7, 5, 5, 5, 7}, {2, 6, 2, 2, 7}, {7, 1, 7, 4, 7}, {7, 1, 7, 1, 7}, {5, 5, 7, 1, 1},
      {7, 4, 7, 1, 7}, {7, 4, 7, 5, 7}, {7, 1, 1, 1, 1}, {7, 5, 7, 5, 7}, {7, 5, 7, 1, 7}, {0, 2, 7, 2, 0}
   };

   private MobMarks() {
   }

   public static int size(double pxPerBlock) {
      int s = 2 * (int)Math.round(2.0 * Math.sqrt(Math.max(0.0, pxPerBlock)));
      return Math.clamp(s, SMALL, LARGE);
   }

   public static void draw(Gfx g, Entity e, Config.RadarStyle style, int x, int y, float yaw, int alpha) {
      draw(g, e, style, x, y, yaw, alpha, LARGE);
   }

   public static void draw(Gfx g, Entity e, Config.RadarStyle style, int x, int y, float yaw, int alpha, int size) {
      switch (style) {
         case FACES -> {
            if (!face(g, e, x, y, alpha, size)) {
               dot(g, x, y, colour(e), alpha, size);
            }
         }
         case DOTS -> dot(g, x, y, colour(e), alpha, size);
         case ARROWS -> {
            if (size >= 6) {
               arrow(g, x, y, yaw, colour(e), alpha);
            } else {
               smallArrow(g, x, y, yaw, colour(e), alpha);
            }
         }
      }
   }

   public static int alpha(Entity e, Entity viewer) {
      return Math.abs(e.getY() - viewer.getY()) > FAR_Y ? FADED : 0xFF;
   }

   public static int colour(Entity e) {
      if (MobLook.neutral(e)) {
         return NEUTRAL;
      }

      return e instanceof Enemy ? HOSTILE : PASSIVE;
   }

   public static boolean hasFace(Entity e) {
      return Table.BY_TYPE.containsKey(MobLook.id(e));
   }

   private static void dot(Gfx g, int x, int y, int colour, int alpha, int size) {
      int far = size >= 6 ? 2 : 1;
      g.fill(x, y, x + far + 1, y + far + 1, shade(alpha));
      g.fill(x - 1, y - 1, x + far, y + far, alpha << 24 | colour & 0xFFFFFF);
   }

   private static void smallArrow(Gfx g, int x, int y, float yaw, int colour, int alpha) {
      int[] nose = NOSES[Math.floorMod(Math.round((yaw + 180.0F) / 45.0F), 8)];
      int nx = x + nose[0] * 2;
      int ny = y + nose[1] * 2;
      int shadow = shade(alpha);
      int c = alpha << 24 | colour & 0xFFFFFF;
      g.fill(x, y, x + 3, y + 3, shadow);
      g.fill(nx + 1, ny + 1, nx + 2, ny + 2, shadow);
      g.fill(x - 1, y - 1, x + 2, y + 2, c);
      g.fill(nx, ny, nx + 1, ny + 1, c);
   }

   static void count(Gfx g, int x, int y, int n, int alpha, int size) {
      int[] glyphs = n > 9 ? new int[]{9, 10} : new int[]{n};
      int w = glyphs.length * 4 - 1;
      int left = x + size / 2 - 1;
      int top = y + size / 2 - 3;
      g.fill(left - 1, top - 1, left + w + 1, top + 6, (0xB0 * alpha / 0xFF) << 24);
      int c = alpha << 24 | 0xFFFFFF;
      for (int k = 0; k < glyphs.length; k++) {
         int[] rows = DIGITS[glyphs[k]];
         int gx = left + k * 4;
         for (int r = 0; r < 5; r++) {
            for (int b = 0; b < 3; b++) {
               if ((rows[r] >> 2 - b & 1) != 0) {
                  g.fill(gx + b, top + r, gx + b + 1, top + r + 1, c);
               }
            }
         }
      }
   }

   private static void arrow(Gfx g, int x, int y, float yaw, int colour, int alpha) {
      int[] runs = ARROWS[Math.floorMod(Math.round((yaw + 180.0F) / 22.5F), 16)];
      int shadow = shade(alpha);
      for (int i = 0; i < runs.length; i += 3) {
         g.fill(x + runs[i] + 1, y + runs[i + 1] + 1, x + runs[i + 2] + 1, y + runs[i + 1] + 2, shadow);
      }

      int c = alpha << 24 | colour & 0xFFFFFF;
      for (int i = 0; i < runs.length; i += 3) {
         g.fill(x + runs[i], y + runs[i + 1], x + runs[i + 2], y + runs[i + 1] + 1, c);
      }
   }

   private static boolean face(Gfx g, Entity e, int x, int y, int alpha, int size) {
      Face f = Table.BY_TYPE.get(MobLook.id(e));
      if (f == null) {
         return false;
      }

      if (MobLook.coldCow(e)) {
         f = Table.COLD_COW;
      }

      String texture = texture(e, f);
      int w = Math.max(1, Math.round((float)f.w * size / Math.max(f.w, f.h)));
      int h = Math.max(1, Math.round((float)f.h * size / Math.max(f.w, f.h)));
      int l = x - w / 2;
      int t = y - h / 2;
      int tint = alpha << 24 | 0xFFFFFF;
      g.fill(l + 1, t + 1, l + w + 1, t + h + 1, shade(alpha));
      g.push();
      g.translate(l, t);
      g.scale((float)w / f.w, (float)h / f.h);
      g.blit(texture, 0, 0, f.u, f.v, f.w, f.h, f.w, f.h, f.texW, f.texH, tint);
      for (int i = 0; i < f.parts.length; i += 6) {
         float[] p = f.parts;
         g.push();
         g.translate(p[i + 4], p[i + 5]);
         g.blit(texture, 0, 0, p[i], p[i + 1], (int)p[i + 2], (int)p[i + 3], (int)p[i + 2], (int)p[i + 3], f.texW, f.texH, tint);
         g.pop();
      }

      if (f.over != null) {
         g.blit(f.over, 0, 0, f.u, f.v, f.w, f.h, f.w, f.h, f.texW, f.texH, tint);
      }

      g.pop();
      return true;
   }

   private static int shade(int alpha) {
      return (SHADE * alpha / 0xFF) << 24;
   }

   private static String texture(Entity e, Face f) {
      if (e instanceof LivingEntity l && l.isBaby()) {
         return f.texture;
      }

      String variant = MobLook.variant(e);
      return variant == null ? f.texture : variant;
   }

   private static int[][] arrows() {
      String[][] drawn = {
         {"..XX..", ".XXXX.", "XXXXXX", "..XX..", "..XX..", "..XX.."},
         {"...XX.", ".XXXX.", "XXXXXX", "..XX..", ".XX...", ".XX..."},
         {"..XXXX", "...XXX", "..XXXX", ".XXX.X", "XXX...", ".X...."}
      };
      int[][] out = new int[16][];
      for (int d = 0; d < 16; d++) {
         boolean[] on = new boolean[64];
         for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
               if (drawn[d % 4 == 3 ? 1 : d % 4][y].charAt(x) != 'X') {
                  continue;
               }

               int cx = x - 3;
               int cy = y - 3;
               if (d % 4 == 3) {
                  int m = cx;
                  cx = -cy - 1;
                  cy = -m - 1;
               }

               for (int q = 0; q < d / 4; q++) {
                  int m = cx;
                  cx = -cy - 1;
                  cy = m;
               }

               on[(cy + 4) * 8 + cx + 4] = true;
            }
         }

         List<Integer> runs = new ArrayList<>();
         for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
               if (on[y * 8 + x] && (x == 0 || !on[y * 8 + x - 1])) {
                  int end = x;
                  while (end < 8 && on[y * 8 + end]) {
                     end++;
                  }

                  runs.add(x - 4);
                  runs.add(y - 4);
                  runs.add(end - 4);
               }
            }
         }

         out[d] = runs.stream().mapToInt(Integer::intValue).toArray();
      }

      return out;
   }

   private record Face(String texture, int texW, int texH, int u, int v, int w, int h, String over, float[] parts) {
   }

   private static final class Table {
      static final float[] VILLAGER_NOSE = {26, 2, 2, 3, 3, 7};
      static final Face COLD_COW = face("minecraft:cow#cold", "cow/cow_cold", 64, 64, 6, 6, 8, 8, null, 10, 34, 6, 3, 1, 5);
      static final Map<String, Face> BY_TYPE = build();

      private static Map<String, Face> build() {
         Map<String, Face> m = new HashMap<>();
         put(m, "minecraft:zombie", "zombie/zombie", 64, 64, 8, 8, 8, 8, null);
         put(m, "minecraft:husk", "zombie/husk", 64, 64, 8, 8, 8, 8, null);
         put(m, "minecraft:drowned", "zombie/drowned", 64, 64, 8, 8, 8, 8, "zombie/drowned_outer_layer");
         put(m, "minecraft:skeleton", "skeleton/skeleton", 64, 32, 8, 8, 8, 8, null);
         put(m, "minecraft:stray", "skeleton/stray", 64, 32, 8, 8, 8, 8, "skeleton/stray_overlay");
         put(m, "minecraft:bogged", "skeleton/bogged", 64, 32, 8, 8, 8, 8, "skeleton/bogged_overlay");
         put(m, "minecraft:parched", "skeleton/parched", 64, 64, 8, 8, 8, 8, null);
         put(m, "minecraft:wither_skeleton", "skeleton/wither_skeleton", 64, 32, 8, 8, 8, 8, null);
         put(m, "minecraft:creeper", "creeper/creeper", 64, 32, 8, 8, 8, 8, null);
         put(m, "minecraft:enderman", "enderman/enderman", 64, 32, 8, 8, 8, 8, "enderman/enderman_eyes");
         put(m, "minecraft:spider", "spider/spider", 64, 32, 40, 12, 8, 8, "spider/spider_eyes");
         put(m, "minecraft:cave_spider", "spider/cave_spider", 64, 32, 40, 12, 8, 8, "spider/spider_eyes");
         put(m, "minecraft:blaze", "blaze/blaze", 64, 32, 8, 8, 8, 8, null);
         put(m, "minecraft:ghast", "ghast/ghast", 64, 32, 16, 16, 16, 16, null);
         put(m, "minecraft:phantom", "phantom/phantom", 64, 64, 5, 5, 7, 3, "phantom/phantom_eyes");
         put(m, "minecraft:slime", "slime/slime", 64, 32, 8, 8, 8, 8, null, 6, 22, 6, 6, 1, 1, 34, 2, 2, 2, 1, 2, 34, 6, 2, 2, 5, 2, 33, 9, 1, 1, 4, 5);
         put(m, "minecraft:cow", "cow/cow_temperate", 64, 64, 6, 6, 8, 8, null, 2, 34, 6, 3, 1, 5);
         put(m, "minecraft:mooshroom", "cow/mooshroom_red", 64, 64, 6, 6, 8, 8, null, 2, 34, 6, 3, 1, 5);
         put(m, "minecraft:pig", "pig/pig_temperate", 64, 64, 8, 8, 8, 8, null, 17, 17, 4, 3, 2, 4);
         put(m, "minecraft:sheep", "sheep/sheep", 64, 32, 8, 8, 6, 6, null);
         put(m, "minecraft:chicken", "chicken/chicken_temperate", 64, 32, 3, 3, 4, 6, null, 16, 2, 4, 2, 0, 2, 16, 6, 2, 2, 1, 4);
         put(m, "minecraft:villager", "villager/villager", 64, 64, 8, 8, 8, 10, null, VILLAGER_NOSE);
         put(m, "minecraft:wandering_trader", "wandering_trader/wandering_trader", 64, 64, 8, 8, 8, 10, null, VILLAGER_NOSE);
         put(m, "minecraft:zombie_villager", "zombie_villager/zombie_villager", 64, 64, 8, 8, 8, 10, null, VILLAGER_NOSE);
         put(m, "minecraft:witch", "witch/witch", 64, 128, 8, 8, 8, 10, null, VILLAGER_NOSE);
         put(m, "minecraft:pillager", "illager/pillager", 64, 64, 8, 8, 8, 10, null, VILLAGER_NOSE);
         put(m, "minecraft:vindicator", "illager/vindicator", 64, 64, 8, 8, 8, 10, null, VILLAGER_NOSE);
         put(m, "minecraft:evoker", "illager/evoker", 64, 64, 8, 8, 8, 10, null, VILLAGER_NOSE);
         put(m, "minecraft:illusioner", "illager/illusioner", 64, 64, 8, 8, 8, 10, null, VILLAGER_NOSE);
         put(m, "minecraft:iron_golem", "iron_golem/iron_golem", 128, 128, 8, 8, 8, 10, null, VILLAGER_NOSE);
         put(m, "minecraft:piglin", "piglin/piglin", 64, 64, 8, 8, 10, 8, null, 32, 2, 4, 4, 3, 4);
         put(m, "minecraft:piglin_brute", "piglin/piglin_brute", 64, 64, 8, 8, 10, 8, null, 32, 2, 4, 4, 3, 4);
         put(m, "minecraft:zombified_piglin", "piglin/zombified_piglin", 64, 64, 8, 8, 10, 8, null, 32, 2, 4, 4, 3, 4);
         put(m, "minecraft:wolf", "wolf/wolf", 64, 32, 4, 4, 6, 6, null, 4, 14, 3, 3, 1.5F, 3);
         put(m, "minecraft:cat", "cat/cat_tabby", 64, 32, 5, 5, 5, 4, null, 2, 26, 3, 2, 1, 2);
         put(m, "minecraft:ocelot", "cat/ocelot", 64, 32, 5, 5, 5, 4, null, 2, 26, 3, 2, 1, 2);
         put(m, "minecraft:fox", "fox/fox", 48, 32, 7, 11, 8, 6, null, 9, 21, 4, 2, 2, 4);
         put(m, "minecraft:rabbit", "rabbit/rabbit_brown", 64, 64, 5, 21, 5, 5, null);
         put(m, "minecraft:bee", "bee/bee", 64, 64, 10, 10, 7, 7, null);
         put(m, "minecraft:axolotl", "axolotl/axolotl_lucy", 64, 64, 5, 6, 8, 5, null);
         put(m, "minecraft:squid", "squid/squid", 64, 32, 12, 12, 12, 16, null);
         put(m, "minecraft:glow_squid", "squid/glow_squid", 64, 32, 12, 12, 12, 16, null);
         put(m, "minecraft:llama", "llama/llama_creamy", 128, 64, 6, 20, 8, 8, null, 9, 9, 4, 4, 2, 2);
         put(m, "minecraft:trader_llama", "llama/llama_creamy", 128, 64, 6, 20, 8, 8, null, 9, 9, 4, 4, 2, 2);
         put(m, "minecraft:panda", "panda/panda", 64, 64, 9, 15, 13, 10, null, 47, 18, 7, 5, 3, 5);
         put(m, "minecraft:polar_bear", "bear/polarbear", 128, 64, 7, 7, 7, 7, null, 3, 47, 5, 3, 1, 4);
         put(m, "minecraft:strider", "strider/strider", 64, 128, 16, 16, 16, 14, null);
         return m;
      }

      private static void put(Map<String, Face> m, String id, String path, int texW, int texH, int u, int v, int w, int h, String over, float... parts) {
         m.put(id, face(id, path, texW, texH, u, v, w, h, over, parts));
      }

      private static Face face(String id, String path, int texW, int texH, int u, int v, int w, int h, String over, float... parts) {
         MobLook.Face f = MobLook.fix(id, new MobLook.Face(path, texW, texH, u, v, w, h, over, parts));
         return new Face(
            MobLook.entityTexture(f.texture()), f.texW(), f.texH(), f.u(), f.v(), f.w(), f.h(),
            f.over() == null ? null : MobLook.entityTexture(f.over()), f.parts()
         );
      }
   }

   public static final class Near implements Comparator<Entity> {
      private final List<Entity> list = new ArrayList<>();
      private double x;
      private double z;

      public List<Entity> collect(Minecraft mc, Entity viewer, Config cfg) {
         this.list.clear();
         if (mc.level == null || mc.player == null || !cfg.radarHostile && !cfg.radarFriendly) {
            return this.list;
         }

         this.x = viewer.getX();
         this.z = viewer.getZ();
         for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Mob && e != viewer && e.isAlive() && !e.isInvisibleTo(mc.player) && (e instanceof Enemy ? cfg.radarHostile : cfg.radarFriendly)) {
               this.list.add(e);
            }
         }

         this.list.sort(this);
         if (this.list.size() > MAX) {
            this.list.subList(MAX, this.list.size()).clear();
         }

         return this.list;
      }

      @Override
      public int compare(Entity a, Entity b) {
         return Double.compare(this.distance(a), this.distance(b));
      }

      private double distance(Entity e) {
         double dx = e.getX() - this.x;
         double dz = e.getZ() - this.z;
         return dx * dx + dz * dz;
      }
   }

   public static final class Batch {
      private final Entity[] mobs = new Entity[MAX];
      private final float[] xs = new float[MAX];
      private final float[] ys = new float[MAX];
      private final float[] yaws = new float[MAX];
      private final int[] alphas = new int[MAX];
      private final MarkClusters groups = new MarkClusters();
      private int n;
      private int shown;

      public void clear() {
         java.util.Arrays.fill(this.mobs, 0, this.n, null);
         this.n = 0;
         this.shown = 0;
      }

      public void add(Entity e, float x, float y, float yaw, int alpha) {
         if (this.n < MAX) {
            this.mobs[this.n] = e;
            this.xs[this.n] = x;
            this.ys[this.n] = y;
            this.yaws[this.n] = yaw;
            this.alphas[this.n] = alpha;
            this.n++;
         }
      }

      public int draw(Gfx g, Config.RadarStyle style, int size, int mouseX, int mouseY) {
         this.shown = this.groups.group(this.xs, this.ys, this.n, size * MERGE);
         int hover = -1;
         int reach = size / 2 + 1;

         for (int c = this.shown - 1; c >= 0; c--) {
            int i = this.groups.lead(c);
            int x = Math.round(this.xs[i]);
            int y = Math.round(this.ys[i]);
            MobMarks.draw(g, this.mobs[i], style, x, y, this.yaws[i], this.alphas[i], size);
            int count = this.groups.count(c);
            if (count > 1) {
               MobMarks.count(g, x, y, count, this.alphas[i], size);
            }

            if (Math.abs(mouseX - x) <= reach && Math.abs(mouseY - y) <= reach) {
               hover = c;
            }
         }

         return hover;
      }

      public Entity mob(int group) {
         return this.mobs[this.groups.lead(group)];
      }

      public int count(int group) {
         return this.groups.count(group);
      }

      public int shown() {
         return this.shown;
      }

      public int size() {
         return this.n;
      }
   }
}
