package dev.chunkatlas.gui;

import dev.chunkatlas.LoadedChunks;
import dev.chunkatlas.ServerLink;
import dev.chunkatlas.Settings;
import dev.chunkatlas.engine.ChunkLoads;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Tex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class LoadedLayer {
   private static final int ALPHA = 0x66;
   private static final long FADE = 2000L;

   private static final long REPAINT = 100L;
   private static final int[] LEVELS = {0x5AD65A, 0xFFD84A, 0xE8903A, 0xA0A0A0};
   private static final int[] REASONS = {0x8FB8FF, 0xFF7AD1, 0x2EC4A0, 0xC58FFF, 0xA0A0A0};
   private static final int[] RAMP = {0x3A3A8A, 0x2E9A8A, 0x9AD04A, 0xF0D040, 0xE8603A};
   private static final String[] LEVEL_KEYS = {"entity", "block", "border", "loading"};
   private static final String[] REASON_KEYS = {"player", "portal", "pearl", "forced", "other"};
   private static final String[] TAB_KEYS = {"level", "reason", "generation"};
   private static final int ROW = 16;
   private static final List<ChunkStatus> STATUSES = ChunkStatus.getStatusList();
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;

   private final Map<Long, Tex> pictures = new HashMap<>();

   private final Map<Long, long[]> fading = new HashMap<>();
   private ChunkLoads.Snapshot shown;
   private int paintedTab = -1;
   private long paintedAt;
   private boolean dirty;

   private double[] anchor;
   private double[] jumped;

   LoadedLayer(MapScreen map) {
      this.map = map;
   }

   String unavailable() {
      if (this.map.live() == null) {
         return "chunkatlas.map.layers.loaded.playing";
      } else if (this.mc.getSingleplayerServer() != null) {
         return null;
      } else if (!ServerLink.hasLoads()) {
         return "chunkatlas.map.layers.loaded.server";
      } else {
         return ServerLink.canEdit() ? null : "chunkatlas.server.not_allowed";
      }
   }

   boolean on() {
      return Settings.loadedChunks() && this.unavailable() == null;
   }

   Ui.Item layerItem() {
      return Ui.Item.check(Ui.t("chunkatlas.map.layers.loaded"), Settings.loadedChunks(), this.unavailable() == null ? () -> Settings.loadedChunks(!Settings.loadedChunks()) : null);
   }

   void layerTip(Gfx g, Ui.Item over, int mouseX, int mouseY) {
      String why = this.unavailable();
      if (over != null && why != null && over.label().equals(Ui.t("chunkatlas.map.layers.loaded"))) {
         g.tooltip(this.mc.font, Component.literal(Ui.t(why)), mouseX, mouseY);
      }
   }

   void draw(Gfx g, int baseRx, int baseRz) {
      String dim = this.map.seedDimension();
      if (!this.on() || dim == null || this.shown != null && !this.shown.dimension().equals(dim)) {
         this.release();
         if (!this.on() || dim == null) {
            return;
         }
      }

      long now = System.currentTimeMillis();
      ChunkLoads.Snapshot fresh = LoadedChunks.get(dim);
      if (fresh != null && fresh != this.shown) {
         this.fadeOut(fresh, now);
         this.shown = fresh;
         this.dirty = true;
      }

      if (this.dirty || this.paintedTab != Settings.loadedTab() || !this.fading.isEmpty() && now - this.paintedAt >= REPAINT) {
         this.paint(now);
      }

      for (Map.Entry<Long, Tex> e : this.pictures.entrySet()) {
         int x = ((int)(e.getKey() >> 32) - baseRx) * 512;
         int z = ((int)e.getKey().longValue() - baseRz) * 512;
         g.blit(e.getValue(), x, z, 0.0F, 0.0F, 512, 512, 32, 32, 32, 32);
      }
   }

   private void fadeOut(ChunkLoads.Snapshot fresh, long now) {
      ChunkLoads.Snapshot old = this.shown;
      for (int i = 0; old != null && i < old.size(); i++) {
         if (fresh.find(old.x(i), old.z(i)) < 0) {
            this.fading.putIfAbsent(key(old.x(i), old.z(i)), new long[]{this.colour(old, i, this.paintedTab), now});
         }
      }

      this.fading.keySet().removeIf(k -> fresh.find((int)(k >> 32), (int)k.longValue()) >= 0);
   }

   private void paint(long now) {
      int tab = Settings.loadedTab();
      Map<Long, int[]> regions = new HashMap<>();
      ChunkLoads.Snapshot s = this.shown;
      for (int i = 0; s != null && i < s.size(); i++) {
         this.put(regions, s.x(i), s.z(i), ALPHA << 24 | this.colour(s, i, tab));
      }

      this.fading.values().removeIf(f -> now - f[1] >= FADE);
      for (Map.Entry<Long, long[]> e : this.fading.entrySet()) {
         int alpha = (int)(ALPHA * (FADE - (now - e.getValue()[1])) / FADE);
         this.put(regions, (int)(e.getKey() >> 32), (int)e.getKey().longValue(), alpha << 24 | (int)e.getValue()[0]);
      }

      for (Map.Entry<Long, int[]> e : regions.entrySet()) {
         Tex p = this.pictures.get(e.getKey());
         if (p == null) {
            p = Tex.create("Chunk Atlas loaded chunks", 32, 32);
            this.pictures.put(e.getKey(), p);
         }

         p.putAbgr(e.getValue());
         p.upload();
      }

      this.pictures.entrySet().removeIf(e -> {
         boolean gone = !regions.containsKey(e.getKey());
         if (gone) {
            e.getValue().close();
         }

         return gone;
      });
      this.paintedTab = tab;
      this.paintedAt = now;
      this.dirty = false;
   }

   private void put(Map<Long, int[]> regions, int cx, int cz, int argb) {
      int[] px = regions.computeIfAbsent(key(cx >> 5, cz >> 5), k -> new int[1024]);
      px[(cz & 31) * 32 + (cx & 31)] = argb & 0xFF00FF00 | argb >> 16 & 0xFF | (argb & 0xFF) << 16;
   }

   private int colour(ChunkLoads.Snapshot s, int i, int tab) {
      return switch (tab) {
         case 1 -> REASONS[s.reason(i)];
         case 2 -> ramp(s.status(i) / (double)Math.max(1, STATUSES.size() - 1));
         default -> LEVELS[s.level(i)];
      };
   }

   private static int ramp(double t) {
      double at = Math.max(0.0, Math.min(1.0, t)) * (RAMP.length - 1);
      int n = Math.min(RAMP.length - 2, (int)at);
      double f = at - n;
      int a = RAMP[n];
      int b = RAMP[n + 1];
      int r = (int)Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * f);
      int gr = (int)Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * f);
      int bl = (int)Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
      return r << 16 | gr << 8 | bl;
   }

   private static long key(int x, int z) {
      return (long)x << 32 | z & 0xFFFFFFFFL;
   }

   void release() {
      for (Tex p : this.pictures.values()) {
         p.close();
      }

      this.pictures.clear();
      this.fading.clear();
      this.shown = null;
      this.paintedTab = -1;
   }

   private int at(int cx, int cz) {
      ChunkLoads.Snapshot s = this.shown;
      return this.on() && s != null && s.dimension().equals(this.map.seedDimension()) ? s.find(cx, cz) : -1;
   }

   void hover(int cx, int cz, List<String> parts) {
      int i = this.at(cx, cz);
      if (i >= 0) {
         parts.add(Ui.t("chunkatlas.loaded.level." + LEVEL_KEYS[this.shown.level(i)]));
         parts.add(Ui.t("chunkatlas.loaded.by." + REASON_KEYS[this.shown.reason(i)]));
      }
   }

   String levelName(int cx, int cz) {
      int i = this.at(cx, cz);
      return i < 0 ? null : Ui.t("chunkatlas.loaded.level." + LEVEL_KEYS[this.shown.level(i)]);
   }

   void contextRows(List<Ui.Item> items, int cx, int cz) {
      int i = this.at(cx, cz);
      if (i >= 0) {
         items.add(Ui.Item.info(Ui.t("chunkatlas.loaded.tab.reason"), Ui.t("chunkatlas.loaded.reason." + REASON_KEYS[this.shown.reason(i)])));
         items.add(Ui.Item.info(Ui.t("chunkatlas.loaded.generated"), statusName(this.shown.status(i))));
      }
   }

   private static String statusName(int index) {
      if (index < 0 || index >= STATUSES.size()) {
         return "?";
      }

      String id = STATUSES.get(index).getName();
      return Ui.t("chunkatlas.loaded.status." + id.substring(id.indexOf(':') + 1));
   }

   List<Component> drawKey(Gfx g, List<Ui.Hit> hits, int mouseX, int mouseY, boolean covered) {
      if (!this.on() || this.map.seedDimension() == null) {
         return null;
      }

      Font font = this.mc.font;
      int tab = Settings.loadedTab();
      String[] tabs = new String[TAB_KEYS.length];
      int tabsW = -2;
      for (int i = 0; i < tabs.length; i++) {
         tabs[i] = Ui.t("chunkatlas.loaded.tab." + TAB_KEYS[i]);
         tabsW += font.width(tabs[i]) + 16;
      }

      List<String> names = new ArrayList<>();
      List<Integer> colours = new ArrayList<>();
      for (int i = 0; tab != 2 && i < (tab == 0 ? LEVELS.length : REASONS.length); i++) {
         names.add(Ui.t(tab == 0 ? "chunkatlas.loaded.level." + LEVEL_KEYS[i] : "chunkatlas.loaded.reason." + REASON_KEYS[i]));
         colours.add(tab == 0 ? LEVELS[i] : REASONS[i]);
      }

      String empty = Ui.t("chunkatlas.loaded.empty");
      String full = Ui.t("chunkatlas.loaded.full");
      List<int[]> groups = this.order();
      int now = this.nearest(groups);
      String count = groups.isEmpty() ? "0/0" : now + 1 + "/" + groups.size();
      int arrow = font.width("◂") + 6;
      int stepW = 2 * arrow + font.width(count) + 4;

      boolean words = true;
      int keyW = 0;
      for (int pass = 0; pass < 2; pass++) {
         keyW = 0;
         for (String n : names) {
            keyW += 8 + (words ? 3 + font.width(n) : 0) + 8;
         }

         keyW = tab == 2 ? (words ? font.width(empty) + font.width(full) + 8 : 0) + 48 : keyW - 8;
         if (Glass.INSET * 2 + tabsW + 9 + keyW + 9 + stepW <= this.map.width - 2 * Glass.MARGIN - 48) {
            break;
         }

         words = false;
      }

      int w = Glass.INSET * 2 + tabsW + 9 + keyW + 9 + stepW;
      int h = Glass.INSET * 2 + ROW;
      int x = (this.map.width - w) / 2;
      int y = this.map.height - Glass.MARGIN - 9 - 4 - h;
      Glass.panel(g, x, y, w, h);
      hits.add(new Ui.Hit(x, y, x + w, y + h, () -> {
      }));
      int row = y + Glass.INSET;
      int text = row + 4;
      int sx = x + Glass.INSET;
      List<Component> tip = null;
      for (int i = 0; i < tabs.length; i++) {
         int tw = font.width(tabs[i]) + 14;
         boolean hover = !covered && Glass.over(mouseX, mouseY, sx, row, tw, ROW);
         if (i == tab) {
            g.fill(sx, row, sx + tw, row + ROW, Glass.HOT);
            g.fill(sx, row + ROW - 2, sx + tw, row + ROW, Glass.ACCENT);
         } else if (hover) {
            g.fill(sx, row, sx + tw, row + ROW, Glass.TINT);
         }

         g.text(font, tabs[i], sx + 7, text, i == tab || hover ? Ui.WHITE : Glass.SOFT);
         int pick = i;
         hits.add(new Ui.Hit(sx, row, sx + tw, row + ROW, () -> Settings.loadedTab(pick)));
         sx += tw + 2;
      }

      sx += 2;
      g.fill(sx, row + 2, sx + 1, row + ROW - 2, Glass.RULE);
      sx += 5;
      if (tab == 2) {
         if (words) {
            g.text(font, empty, sx, text, Glass.LABEL);
            sx += font.width(empty) + 4;
         }

         for (int n = 0; n < 48; n++) {
            g.fill(sx + n, row + 5, sx + n + 1, row + ROW - 5, 0xFF000000 | ramp(n / 47.0));
         }

         sx += 48;
         if (words) {
            g.text(font, full, sx + 4, text, Glass.LABEL);
            sx += 4 + font.width(full);
         }
      } else {
         for (int i = 0; i < names.size(); i++) {
            g.fill(sx, row + 4, sx + 8, row + 12, 0xFF000000 | colours.get(i));
            if (!covered && Glass.over(mouseX, mouseY, sx - 1, row, 10, ROW) && !words) {
               tip = List.of(Component.literal(names.get(i)));
            }

            sx += 8;
            if (words) {
               g.text(font, names.get(i), sx + 3, text, Glass.LABEL);
               sx += 3 + font.width(names.get(i));
            }

            sx += i < names.size() - 1 ? 8 : 0;
         }
      }

      sx += 4;
      g.fill(sx, row + 2, sx + 1, row + ROW - 2, Glass.RULE);
      sx += 5;
      for (int dir = -1; dir <= 1; dir += 2) {
         boolean usable = !groups.isEmpty();
         boolean hover = usable && !covered && Glass.over(mouseX, mouseY, sx, row, arrow, ROW);
         g.text(font, dir < 0 ? "◂" : "▸", sx + 3, text, !usable ? Glass.OFF : hover ? Ui.WHITE : Glass.SOFT);
         if (usable) {
            int step = dir;
            hits.add(new Ui.Hit(sx, row, sx + arrow, row + ROW, () -> this.jump(groups.get(Math.floorMod(now + step, groups.size())))));
         }

         if (hover) {
            tip = List.of(Component.literal(Ui.t(dir < 0 ? "chunkatlas.loaded.previous" : "chunkatlas.loaded.next")));
         }

         sx += arrow;
         if (dir < 0) {
            g.text(font, count, sx + 2, text, usable ? Ui.WHITE : Glass.OFF);
            sx += font.width(count) + 4;
         }
      }

      return tip;
   }

   private List<int[]> order() {
      ChunkLoads.Snapshot s = this.shown;
      if (s == null) {
         return List.of();
      }

      double vx = this.map.seedCx();
      double vz = this.map.seedCz();
      if (this.jumped == null || Math.abs(vx - this.jumped[0]) > 0.5 || Math.abs(vz - this.jumped[1]) > 0.5) {
         this.anchor = new double[]{vx, vz};
         this.jumped = null;
      }

      double[] from = this.anchor;
      List<int[]> groups = new ArrayList<>(s.clusters());
      groups.sort(Comparator.comparingDouble(c -> distance(c, from[0], from[1])));
      return groups;
   }

   private int nearest(List<int[]> groups) {
      int best = 0;
      for (int i = 1; i < groups.size(); i++) {
         if (distance(groups.get(i), this.map.seedCx(), this.map.seedCz()) < distance(groups.get(best), this.map.seedCx(), this.map.seedCz())) {
            best = i;
         }
      }

      return best;
   }

   private static double distance(int[] group, double x, double z) {
      return Math.hypot((group[0] + group[2] + 1) * 8.0 - x, (group[1] + group[3] + 1) * 8.0 - z);
   }

   private void jump(int[] group) {
      this.map.centreOn(this.map.seedDimension(), (group[0] + group[2] + 1) * 8.0, (group[1] + group[3] + 1) * 8.0, -1, -1);
      this.jumped = new double[]{this.map.seedCx(), this.map.seedCz()};
   }

   String tourSummary() {
      ChunkLoads.Snapshot s = this.shown;
      if (s == null) {
         return "no snapshot (" + (this.unavailable() == null ? "waiting" : this.unavailable()) + ")";
      }

      int[] levels = new int[LEVELS.length];
      int[] reasons = new int[REASONS.length];
      int[] statuses = new int[STATUSES.size()];
      List<String> forced = new ArrayList<>();
      for (int i = 0; i < s.size(); i++) {
         levels[s.level(i)]++;
         reasons[s.reason(i)]++;
         statuses[Math.min(statuses.length - 1, s.status(i))]++;
         if (s.reason(i) == ChunkLoads.FORCED && s.level(i) == ChunkLoads.ENTITY_TICKING) {
            forced.add(s.x(i) + "," + s.z(i));
         }
      }

      return s.size() + " chunks in " + s.dimension() + "; levels " + java.util.Arrays.toString(levels) + " (entity, block, border, loading); by "
         + java.util.Arrays.toString(reasons) + " (player, portal, pearl, forced, other); statuses " + java.util.Arrays.toString(statuses) + "; "
         + s.clusters().size() + " groups; force-loaded entity-ticking chunks " + forced + "; " + this.pictures.size() + " pictures, " + this.fading.size() + " fading";
   }

   String tourAt(int cx, int cz) {
      List<String> parts = new ArrayList<>();
      this.hover(cx, cz, parts);
      List<Ui.Item> rows = new ArrayList<>();
      this.contextRows(rows, cx, cz);
      StringBuilder out = new StringBuilder("chunk " + cx + "," + cz + ": read-out " + parts + ", level " + this.levelName(cx, cz) + ", menu");
      for (Ui.Item r : rows) {
         out.append(" [").append(r.label()).append(" | ").append(r.note()).append("]");
      }

      return out.toString();
   }

   String tourStep(int dir) {
      List<int[]> groups = this.order();
      if (groups.isEmpty()) {
         return "no groups";
      }

      int to = Math.floorMod(this.nearest(groups) + dir, groups.size());
      this.jump(groups.get(to));
      return "jumped to group " + (to + 1) + "/" + groups.size() + " at " + Math.round(this.map.seedCx()) + ", " + Math.round(this.map.seedCz());
   }
}
