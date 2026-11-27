package dev.chunkatlas.gui;

import dev.chunkatlas.engine.IdText;
import dev.chunkatlas.engine.RegionFacts;
import dev.chunkatlas.engine.SeedMapSettings;
import dev.chunkatlas.GameColours;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Seeds;
import dev.chunkatlas.port.Tex;
import dev.chunkatlas.seed.SeedCache;
import dev.chunkatlas.seed.SeedSampler;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

final class SeedMapLayer {
   private static final int[] SIZES = {512, 256, 128, 64, 32, 16, 8, 4};

   private static final int HEIGHTS_FROM = 512;

   private static final int BASE_MAX = 128;

   private static final long TEXTURE_PIXELS = 16L << 20;

   private static final int UPLOAD_PIXELS = 1 << 19;

   private static final long FRAME_WORK = 6_000_000L;

   private static final int MAX_DIMS = 400;

   private static final int MAX_SHEETS = 240;

   private static final int MAX_KEPT = 256;
   private static final int KEPT_SIZE = 128;

   private static final int MAX_SAVED = 2048;

   private static final int DIM = 0xB8000000;

   private static final Set<String> SPRITES = Set.of(
      "village", "pillager_outpost", "stronghold", "mansion", "monument", "desert_pyramid", "jungle_pyramid", "swamp_hut",
      "igloo", "ancient_city", "trial_chambers", "trail_ruins", "abandoned_camp", "ruined_portal", "shipwreck", "ocean_ruin",
      "buried_treasure", "mineshaft", "fortress", "bastion_remnant", "nether_fossil", "end_city", "desert_well",
      "amethyst_geode", "fossil", "lava_pool", "end_gateway"
   );

   private static final List<String> MAJOR = List.of(
      "minecraft:stronghold", "minecraft:mansion", "minecraft:monument", "minecraft:ancient_city", "minecraft:end_city",
      "minecraft:fortress", "minecraft:bastion_remnant", "minecraft:village"
   );

   private static final int FAR_CAP = 60;

   private static final double SMALL_FROM = 6.0;

   private static final double SLIME_FROM = 8.0;

   static final int ICON = 16;

   private static final int SPRITE = 13;

   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen host;
   private SeedMapSettings settings;

   private dev.chunkatlas.engine.LevelMarkers.Generation generation;

   private Long override;
   private final GameColours colours = GameColours.get();
   private SeedCache cache;
   private final Map<SeedCache.Job, Pic> textures = new LinkedHashMap<>(256, 0.75F, true);

   private final Map<SeedCache.Job, Pic> partials = new LinkedHashMap<>(64, 0.75F, true);
   private final Map<Long, Pic> dims = new LinkedHashMap<>(64, 0.75F, true);
   private final Map<Long, Cover> covers = new HashMap<>();
   private Object coverFor;
   private long texturePixels;
   private List<SeedCache.Job> lastWanted = List.of();
   private SeedSampler.Found highlight;
   private long highlightUntil;
   private SeedSampler.Found hovered;
   private volatile boolean finding;
   private volatile boolean stopFinding;
   private long lastFrame;
   private long worstGapNanos;

   private final java.util.Set<Integer> biomes = new java.util.TreeSet<>();

   private int biome = -1;
   private int highlightVersion;
   private Object reasonServer;
   private String reasonDimension;
   private String reason;
   private boolean openFailed;

   SeedMapLayer(MapScreen host) {
      this.host = host;
   }

   static String spriteFor(String kind) {
      String name = "minecraft".equals(IdText.namespace(kind)) && SPRITES.contains(IdText.path(kind)) ? IdText.path(kind) : "other";
      return "chunkatlas:seed/" + name;
   }

   static void icon(Gfx g, String kind, int x, int y) {
      Item item = StructureItems.item(kind);
      if (item != null) {
         StructureItems.draw(g, item, x, y);
      } else {
         int pad = (ICON - SPRITE) / 2;
         g.sprite(spriteFor(kind), x + pad, y + pad, SPRITE, SPRITE);
      }
   }

   private SeedMapSettings settings() {
      if (this.settings == null) {
         this.settings = SeedMapSettings.load(this.host.seedHome(), this.host.seedWorldFolder());
      }

      return this.settings;
   }

   boolean available() {
      if (this.onServer() || this.offline()) {
         return this.seedFor() != null && this.reason() == null;
      }

      return this.host.live() != null && this.mc.getSingleplayerServer() != null && this.reason() == null;
   }

   boolean onServer() {
      return this.host.live() != null && this.mc.getSingleplayerServer() == null;
   }

   boolean offline() {
      return this.host.live() == null;
   }

   Long seedFor() {
      if (this.onServer()) {
         return this.settings().seed();
      }

      if (this.offline()) {
         if (this.override != null) {
            return this.override;
         }

         Long level = this.generation().seed().orElse(null);
         return level != null ? level : this.settings().seed();
      }

      IntegratedServer server = this.mc.getSingleplayerServer();
      return server == null ? null : server.overworld().getSeed();
   }

   boolean seedEditable() {
      return this.onServer() || this.offline();
   }

   private dev.chunkatlas.engine.LevelMarkers.Generation generation() {
      if (this.generation == null) {
         Path world = this.host.seedWorldFolder();
         this.generation = world == null ? dev.chunkatlas.engine.LevelMarkers.Generation.NONE : dev.chunkatlas.engine.LevelMarkers.generation(world);
      }

      return this.generation;
   }

   boolean needsSeed() {
      return Seeds.available() && this.seedEditable() && this.seedFor() == null;
   }

   void askSeed() {
      this.lastFrame = 0L;
      this.host.openSeedPanel(SeedPanel.Tab.STRUCTURES, true);
   }

   void useSeed(long seed) {
      SeedMapSettings s = this.settings();
      if (this.offline() && this.generation().seed().isPresent()) {
         this.override = seed == this.generation().seed().get() ? null : seed;
      } else {
         s.seed(seed);
      }

      this.change(() -> {
         s.accept();
         s.show(true);
      });
      this.stop();
      this.biome = -1;
      this.biomes.clear();
   }

   void seedTyped() {
      this.settings = null;
      this.stop();
      this.biome = -1;
      this.biomes.clear();
   }

   private SeedSampler openWorld(String dim) {
      if (this.onServer() || this.offline()) {
         Long seed = this.seedFor();
         return seed == null ? null : Seeds.forSeed(seed, dim, this.colours);
      }

      return Seeds.open(this.mc.getSingleplayerServer(), dim, this.colours);
   }

   private String reason() {
      if (!Seeds.available()) {
         return "chunkatlas.seed.unavailable.version";
      }

      IntegratedServer server = this.mc.getSingleplayerServer();
      String dim = this.host.seedDimension();
      if (server != this.reasonServer || !java.util.Objects.equals(dim, this.reasonDimension)) {
         this.reasonServer = server;
         this.reasonDimension = dim;
         this.openFailed = false;
         try {
            if (this.offline()) {
               String type = dim == null ? null : this.generation().generator(dim);
               this.reason = type == null || type.equals("minecraft:noise") ? null : "chunkatlas.seed.unavailable.generator";
            } else {
               this.reason = server == null || dim == null ? null : Seeds.unsupported(server, dim);
            }
         } catch (RuntimeException e) {
            this.reason = "chunkatlas.seed.unavailable.generator";
         }
      }

      return this.openFailed ? "chunkatlas.seed.unavailable.generator" : this.reason;
   }

   boolean shown() {
      return this.available() && this.settings().shown();
   }

   boolean terrainShown() {
      return this.shown() && this.settings().biomes();
   }

   boolean unbounded() {
      return this.shown();
   }

   SeedCache cache() {
      return this.cache;
   }

   private long[] covered(int rx, int rz) {
      Map<Long, long[]> present = this.host.seedPresent();
      if (present != this.coverFor) {
         this.covers.clear();
         this.coverFor = present;
      }

      long key = (long)rx << 32 | rz & 0xFFFFFFFFL;
      long[] bits = present == null ? null : present.get(key);
      if (bits == null) {
         return null;
      }

      RegionFacts facts = this.host.seedFacts().get(key);
      long[] drawn = this.host.seedDrawn().get(key);
      Cover known = this.covers.get(key);
      if (known != null && known.bits() == bits && known.facts() == facts && known.drawn() == drawn) {
         return known.mask();
      }

      long[] mask = bits;
      long[] partial = null;
      if (facts == null && drawn != null) {
         mask = new long[16];
         for (int k = 0; k < 16; k++) {
            mask[k] = bits[k] & drawn[k];
         }
      } else if (facts != null) {
         mask = new long[16];
         for (int i = 0; i < 1024; i++) {
            byte state = facts.state[i];
            if ((bits[i >> 6] & 1L << (i & 63)) == 0L) {
               continue;
            }

            boolean shows = drawn != null && (drawn[i >> 6] & 1L << (i & 63)) != 0L && facts.inhabited[i] > 0L;
            if (state == RegionFacts.FULL || state == RegionFacts.UNREADABLE || state == RegionFacts.PARTIAL && shows) {
               mask[i >> 6] |= 1L << (i & 63);
            } else if (state == RegionFacts.PARTIAL) {
               if (partial == null) {
                  partial = new long[16];
               }

               partial[i >> 6] |= 1L << (i & 63);
            }
         }
      }

      this.covers.put(key, new Cover(bits, facts, drawn, mask, partial));
      return mask;
   }

   private long[] partial(int rx, int rz) {
      if (this.covered(rx, rz) == null) {
         return null;
      }

      Cover c = this.covers.get((long)rx << 32 | rz & 0xFFFFFFFFL);
      return c == null ? null : c.partial();
   }

   private static boolean all(long[] mask) {
      if (mask == null) {
         return false;
      }

      for (long b : mask) {
         if (b != -1L) {
            return false;
         }
      }

      return true;
   }

   int tourKept() {
      return this.kept.size();
   }

   int[] tourCover(int rx, int rz) {
      Map<Long, long[]> present = this.host.seedPresent();
      long key = (long)rx << 32 | rz & 0xFFFFFFFFL;
      long[] bits = present == null ? null : present.get(key);
      long[] mask = this.covered(rx, rz);
      long[] part = this.partial(rx, rz);
      return new int[]{count(bits), this.host.seedFacts().get(key) == null ? 0 : 1, count(mask), count(part)};
   }

   private static int count(long[] m) {
      int n = 0;
      if (m != null) {
         for (long b : m) {
            n += Long.bitCount(b);
         }
      }

      return n;
   }

   private boolean generated(int bx, int bz) {
      long[] mask = this.covered(Math.floorDiv(bx, 512), Math.floorDiv(bz, 512));
      int i = (Math.floorMod(bz, 512) >> 4) * 32 + (Math.floorMod(bx, 512) >> 4);
      return mask != null && (mask[i >> 6] & 1L << (i & 63)) != 0L;
   }

   void tick() {
      if (!this.shown() || this.host.seedDimension() == null) {
         this.stop();
         return;
      }

      IntegratedServer server = this.mc.getSingleplayerServer();
      String dim = this.host.seedDimension();
      Long typed = this.seedEditable() ? this.seedFor() : null;
      if (this.cache != null && (this.cache.world().server() != server || !this.cache.world().dimension().equals(dim)
         || typed != null && this.cache.world().seed() != typed)) {
         this.stop();

         this.biome = -1;
         this.biomes.clear();
      }

      if (this.cache == null) {
         SeedSampler world;
         try {
            world = this.openWorld(dim);
         } catch (RuntimeException e) {
            this.openFailed = true;
            world = null;
         }

         if (world == null) {
            return;
         }

         world.keepHeightsIn(this.host.seedHome());
         this.cache = new SeedCache(world);
      }

      this.cache.makeWay(this.host.seedSavedLoading());
      double zoom = this.host.seedZoom();
      double fx = this.host.seedCx() / 512.0 - 0.5;
      double fz = this.host.seedCz() / 512.0 - 0.5;
      int[] view = this.regions(0);
      List<long[]> near = this.ordered(this.regions(1), fx, fz);
      List<long[]> seen = this.ordered(view, fx, fz);
      List<SeedCache.Job> jobs = new ArrayList<>();
      SeedMapSettings s = this.settings();
      boolean structures = s.structures() && 512.0 * zoom >= 24.0;

      boolean lit = this.biome >= 0;
      boolean terrain = s.biomes() || lit;
      int n = this.terrainSize();
      int cave = this.host.seedCaveY();

      int first = this.first(n);
      int base = this.base(n);

      boolean far = far(n);

      double target = this.host.seedZoomTarget();
      if (!(target > 1.0E-4) || !(zoom > 1.0E-4)) {
         target = zoom;
      }

      int targetN = this.terrainSize(target);

      boolean gliding = Math.abs(zoom / target - 1.0) > 0.003;
      this.zoomingFar = terrain && gliding && far(targetN);
      if (terrain && (far || this.zoomingFar)) {
         int per = SeedCache.SHEET;
         List<long[]> sheets = this.ordered(sheets(this.regions(1, this.zoomingFar ? target : zoom)), (fx - per / 2.0 + 0.5) / per, (fz - per / 2.0 + 0.5) / per);
         int[] sizes = sheetSizes(this.zoomingFar ? targetN : n);
         for (int size : gliding ? new int[]{2, sizes[1]} : sizes) {
            for (long[] a : sheets) {
               jobs.add(new SeedCache.Job(SeedCache.Type.SHEET, (int)a[0], (int)a[1], size, cave));
            }
         }
      }

      if (lit) {
         this.readSaved(seen, far);
      }

      int count = 0;
      for (long[] a : near) {
         if (count++ > 6000) {
            break;
         }

         if (terrain && !far && (lit || !all(this.covered((int)a[0], (int)a[1])))) {
            jobs.add(new SeedCache.Job(SeedCache.Type.BASE, (int)a[0], (int)a[1], first, cave));
         }

         if (structures && count <= 1500 && a[0] >= view[0] && a[0] <= view[2] && a[1] >= view[1] && a[1] <= view[3]) {
            jobs.add(new SeedCache.Job(SeedCache.Type.QUICK, (int)a[0], (int)a[1], 0));
         }
      }

      if (terrain && !far && base > first) {
         for (long[] a : seen) {
            if (lit || !all(this.covered((int)a[0], (int)a[1]))) {
               jobs.add(new SeedCache.Job(SeedCache.Type.BASE, (int)a[0], (int)a[1], base, cave));
            }
         }
      }

      boolean detail = this.detailOn(cave);
      int[] chunkView = null;
      if (detail) {
         double halfW = this.host.width / 2.0 / zoom;
         double halfH = (this.host.seedBottom() - this.host.seedTop()) / 2.0 / zoom;
         double cx = this.host.seedCx();
         double cz = this.host.seedCz();
         chunkView = new int[]{
            Math.floorDiv((int)Math.floor(cx - halfW), 16), Math.floorDiv((int)Math.floor(cz - halfH), 16),
            Math.floorDiv((int)Math.floor(cx + halfW), 16), Math.floorDiv((int)Math.floor(cz + halfH), 16),
            Math.floorDiv((int)Math.floor(cx), 16), Math.floorDiv((int)Math.floor(cz), 16)
         };
         this.cache.view(chunkView);
         int workers = dev.chunkatlas.engine.Background.threads(6);
         for (long[] a : seen) {
            long[] covered = this.covered((int)a[0], (int)a[1]);
            this.cache.explored((int)a[0], (int)a[1], covered);
            if (!all(covered)) {
               for (int k = 0; k < workers; k++) {
                  jobs.add(new SeedCache.Job(SeedCache.Type.DETAIL, (int)a[0], (int)a[1], k, cave));
               }
            }
         }
      } else if (terrain && cave == SeedSampler.NO_CAVE && n >= HEIGHTS_FROM) {
         for (long[] a : seen) {
            if (lit || !all(this.covered((int)a[0], (int)a[1]))) {
               jobs.add(new SeedCache.Job(SeedCache.Type.BIOMES, (int)a[0], (int)a[1], n));
            }
         }
      }

      if (structures) {
         List<long[]> check = new ArrayList<>(this.unchecked.size());
         for (long k : this.unchecked) {
            check.add(new long[]{k >> 32, (int)k});
         }

         check.sort(Comparator.comparingDouble(a -> (a[0] - fx) * (a[0] - fx) + (a[1] - fz) * (a[1] - fz)));
         for (long[] a : check) {
            jobs.add(new SeedCache.Job(SeedCache.Type.CHECKED, (int)a[0], (int)a[1], 0));
         }

         SeedSampler.Found over = this.hovered;
         if (over != null && needsCheck(over)) {
            SeedCache.Job now = new SeedCache.Job(SeedCache.Type.CHECKED, Math.floorDiv(over.x(), 512), Math.floorDiv(over.z(), 512), 0);
            jobs.remove(now);
            jobs.add(0, now);
         }
      }

      if (!jobs.equals(this.lastWanted) || detail && !Arrays.equals(chunkView, this.lastView)) {
         this.lastWanted = jobs;
         this.lastView = chunkView;
         this.cache.want(jobs);
      }
   }

   private int[] lastView;

   private boolean zoomingFar;

   private boolean detailOn(int cave) {
      if (this.cache == null || !this.settings().biomes() || this.host.seedZoom() * this.mc.getWindow().getGuiScale() < 2.0) {
         return false;
      }

      SeedSampler w = this.cache.world();
      return (cave != SeedSampler.NO_CAVE || w.hasTerrain()) && w.canMakeChunks();
   }

   private List<long[]> ordered(int[] r, double fx, double fz) {
      List<long[]> order = new ArrayList<>();
      for (int rz = r[1]; rz <= r[3]; rz++) {
         for (int rx = r[0]; rx <= r[2]; rx++) {
            order.add(new long[]{rx, rz});
         }
      }

      order.sort(Comparator.comparingDouble(a -> (a[0] - fx) * (a[0] - fx) + (a[1] - fz) * (a[1] - fz)));
      return order;
   }

   private int[] regions(int margin) {
      return this.regions(margin, this.host.seedZoom());
   }

   private int[] regions(int margin, double zoom) {
      double cx = this.host.seedCx();
      double cz = this.host.seedCz();
      double halfW = this.host.width / 2.0 / zoom;
      double halfH = (this.host.seedBottom() - this.host.seedTop()) / 2.0 / zoom;
      return new int[]{
         Math.floorDiv((int)Math.floor(cx - halfW), 512) - margin,
         Math.floorDiv((int)Math.floor(cz - halfH), 512) - margin,
         Math.floorDiv((int)Math.floor(cx + halfW), 512) + margin,
         Math.floorDiv((int)Math.floor(cz + halfH), 512) + margin,
         0,
         0
      };
   }

   private int first(int n) {
      return Math.max(4, Math.min(16, n / 8));
   }

   private int base(int n) {
      return Math.max(4, Math.min(BASE_MAX, n / 4));
   }

   private static boolean far(int n) {
      return n <= 64;
   }

   private static int[] sheetSizes(int n) {
      return n <= 32 ? new int[]{2, 4, 8, 16} : new int[]{2, 8, 16};
   }

   private static int[] sheets(int[] r) {
      int s = SeedCache.SHEET;
      return new int[]{Math.floorDiv(r[0], s), Math.floorDiv(r[1], s), Math.floorDiv(r[2], s), Math.floorDiv(r[3], s), 0, 0};
   }

   private int terrainSize() {
      return this.terrainSize(this.host.seedZoom());
   }

   private int terrainSize(double zoom) {
      double perBlock = zoom * this.mc.getWindow().getGuiScale();
      int n = 4;
      while (n < 512 && n < 512.0 * perBlock) {
         n *= 2;
      }

      return n;
   }

   void stop() {
      if (this.cache != null) {
         this.cache.close();
         this.cache = null;
      }

      this.stopFinding = true;
      this.lastWanted = List.of();
      this.dropSaved();
      this.dropTextures();

      this.releaseRetired();
   }

   private int[] pixels(SeedSampler.Terrain t, int n) {
      if (!this.host.seedBiomes() || this.cache == null) {
         return t.pixels(dev.chunkatlas.Settings.lookBits(), this.colours);
      }

      if (t.detail() != null && (dev.chunkatlas.Settings.lookBits() & dev.chunkatlas.engine.TileRenderer.DETAILED) != 0) {
         return this.biomeShaded(t);
      }

      int[] src = t.argb();
      int[] out = new int[src.length];
      int per = Math.max(1, t.blocksPerPixel());
      java.util.Map<Integer, Integer> colours = new java.util.HashMap<>();
      for (int i = 0; i < out.length; i++) {
         if ((src[i] >>> 24) == 0) {
            continue;
         }

         int id = t.biomeAt((i % n) * per, (i / n) * per);
         Integer c = colours.get(id);
         if (c == null) {
            String key = this.cache.world().biomeId(id);
            c = key == null ? src[i] : dev.chunkatlas.engine.BiomePalette.colour(key.toString());
            colours.put(id, c);
         }

         out[i] = c;
      }

      return out;
   }

   private int[] biomeShaded(SeedSampler.Terrain t) {
      dev.chunkatlas.engine.Surface c = t.detail().copy();
      int[] tops = new int[64];
      for (int i = 0; i < c.biome.length; i++) {
         int b = c.biome[i];
         if (b < 0 || !c.drawn(i)) {
            continue;
         }

         if (b >= tops.length) {
            tops = Arrays.copyOf(tops, Math.max(b + 1, tops.length * 2));
         }

         if (tops[b] == 0) {
            String name = dev.chunkatlas.engine.BiomeIndex.name(b);
            int colour = name == null ? 0 : dev.chunkatlas.engine.BiomePalette.colour(name);
            tops[b] = colour == 0 ? -1 : dev.chunkatlas.engine.Surface.top(dev.chunkatlas.engine.Look.of(colour, 255, dev.chunkatlas.engine.Look.TINT_NONE, 0L, 0));
         }

         if (tops[b] != -1) {
            c.top[i] = tops[b];
            c.depth[i] = 0;
            c.cover[i] = 0;
         }
      }

      return new SeedSampler.Terrain(null, t.biomes(), t.side(), t.step(), c, t.blocksPerPixel()).pixels(dev.chunkatlas.Settings.lookBits(), this.colours);
   }

   void restyle() {
      this.lastWanted = List.of();
      this.dropTextures();
   }

   private final List<Tex> retired = new ArrayList<>();

   private Set<Object> used = new java.util.HashSet<>();
   private Set<Object> usedBefore = new java.util.HashSet<>();

   private void retire(Tex id) {
      this.retired.add(id);
      this.retiredEver++;
   }

   private int retiredEver;

   private void releaseRetired() {
      for (Tex id : this.retired) {
         id.close();
      }

      this.retired.clear();
   }

   private boolean inUse(Object texture) {
      return this.used.contains(texture) || this.usedBefore.contains(texture);
   }

   private void dropTextures() {
      this.prepared.clear();
      for (Pic t : this.textures.values()) {
         this.retire(t.id());
      }

      for (Pic t : this.partials.values()) {
         this.retire(t.id());
      }

      for (Pic t : this.details.values()) {
         this.retire(t.id());
      }

      for (Pic t : this.kept.values()) {
         this.retire(t.id());
      }

      for (Sheet t : this.sheets.values()) {
         this.retire(t.id());
      }

      this.sheets.clear();
      this.sheetPrepared.clear();
      this.textures.clear();
      this.partials.clear();
      this.details.clear();
      this.detailFrom.clear();
      this.kept.clear();
      this.texturePixels = 0L;
      this.dropDims();
      this.used.clear();
      this.usedBefore.clear();
   }

   private void dropDims() {
      for (Pic t : this.dims.values()) {
         this.retire(t.id());
      }

      for (Pic t : this.sheetDims.values()) {
         this.retire(t.id());
      }

      this.dims.clear();
      this.sheetDims.clear();
      this.dimFrom.clear();
   }

   void drawUnder(Gfx g) {
      long now = System.nanoTime();
      if (this.lastFrame != 0L) {
         this.worstGapNanos = Math.max(this.worstGapNanos, now - this.lastFrame);
      }

      this.lastFrame = now;
      this.releaseRetired();
      Set<Object> spare = this.usedBefore;
      this.usedBefore = this.used;
      this.used = spare;
      this.used.clear();
      if (!this.terrainShown() || this.cache == null) {
         return;
      }

      int[] r = this.regions(0);
      int want = this.terrainSize();
      int cave = this.host.seedCaveY();
      boolean detail = this.detailOn(cave);
      int budget = UPLOAD_PIXELS;
      long deadline = System.nanoTime() + FRAME_WORK;
      this.push(g, r);
      boolean far = far(want);

      if (far || this.zoomingFar || this.gaps && !this.sheets.isEmpty()) {
         this.drawSheets(g, r, cave, true, deadline);
      }

      if (far) {
         this.gaps = true;
         this.drawKept(g, r, cave);
         g.pop();
         this.trimTextures();
         return;
      }

      boolean gaps = false;
      for (long[] at : this.ordered(r, this.host.seedCx() / 512.0 - 0.5, this.host.seedCz() / 512.0 - 0.5)) {
         {
            int rx = (int)at[0];
            int rz = (int)at[1];
            long[] mask = this.covered(rx, rz);
            if (all(mask)) {
               continue;
            }

            Pic tex = null;
            for (SeedCache.Job key : this.pictures(rx, rz, want, cave)) {
               int n = key.n();
               Pic t = this.textures.get(key);
               if ((t == null || !Arrays.equals(t.mask(), mask)) && budget > 0 && System.nanoTime() < deadline) {
                  SeedSampler.Terrain terrain = this.whole(key);
                  Pic made = terrain == null ? null : this.take(this.textures, key, terrain, n, mask, false);
                  if (made != null) {
                     t = made;
                     budget -= n * n;
                  }
               }

               if (t != null) {
                  tex = t;
                  break;
               }
            }

            if (tex != null) {
               this.used.add(tex);
               g.blit(tex.id(), (rx - r[4]) * 512, (rz - r[5]) * 512, 0.0F, 0.0F, 512, 512, tex.size(), tex.size(), tex.size(), tex.size());
            } else {
               gaps = true;
            }

            SeedCache.Job dk = new SeedCache.Job(SeedCache.Type.DETAIL, rx, rz, 0, cave);
            Pic d = this.details.get(dk);
            if (detail) {
               SeedSampler.Terrain made = this.cache.detail(rx, rz, cave);
               if (made != null && (d == null || this.detailFrom.get(dk) != made || !Arrays.equals(d.mask(), mask)) && budget > 0 && System.nanoTime() < deadline) {
                  Pic fresh = this.takeDetail(dk, made, mask);
                  if (fresh != null) {
                     d = fresh;
                     budget -= 512 * 512;
                  }
               }
            }

            this.drawMade(g, d, dk, rx, rz, r);
         }
      }

      this.gaps = gaps;
      g.pop();
      this.trimTextures();
   }

   private boolean gaps;

   private record Sheet(Tex id, int size, long[][] masks) {
   }

   private record SheetPrepared(int[] abgr, long[][] masks) {
   }

   private final Map<SeedCache.Job, Sheet> sheets = new LinkedHashMap<>(64, 0.75F, true);
   private final Map<SeedCache.Job, SheetPrepared> sheetPrepared = new java.util.concurrent.ConcurrentHashMap<>();

   private final Set<Long> sheetsWithSaved = new java.util.HashSet<>();
   private Object sheetsSavedFor;
   private static final int[] SHEET_SIZES = {16, 8, 4, 2};

   private static long key(int x, int z) {
      return (long)x << 32 | z & 0xFFFFFFFFL;
   }

   private long[][] sheetMasks(int sx, int sz) {
      Map<Long, long[]> present = this.host.seedPresent();
      if (present != this.sheetsSavedFor) {
         this.sheetsSavedFor = present;
         this.sheetsWithSaved.clear();
         if (present != null) {
            for (long k : present.keySet()) {
               this.sheetsWithSaved.add(key(Math.floorDiv((int)(k >> 32), SeedCache.SHEET), Math.floorDiv((int)k, SeedCache.SHEET)));
            }
         }
      }

      if (!this.sheetsWithSaved.contains(key(sx, sz))) {
         return null;
      }

      int s = SeedCache.SHEET;
      long[][] out = new long[s * s][];
      for (int z = 0; z < s; z++) {
         for (int x = 0; x < s; x++) {
            out[z * s + x] = this.covered(sx * s + x, sz * s + z);
         }
      }

      return out;
   }

   private static boolean same(long[][] a, long[][] b) {
      if (a == null || b == null) {
         return a == b;
      }

      for (int i = 0; i < a.length; i++) {
         if (a[i] != b[i] && !Arrays.equals(a[i], b[i])) {
            return false;
         }
      }

      return true;
   }

   private static boolean explored(long[][] masks, int bx, int bz) {
      long[] m = masks[(bz >> 9) * SeedCache.SHEET + (bx >> 9)];
      int chunk = ((bz & 511) >> 4) * 32 + ((bx & 511) >> 4);
      return m != null && (m[chunk >> 6] & 1L << (chunk & 63)) != 0L;
   }

   private void drawSheets(Gfx g, int[] r, int cave, boolean make, long deadline) {
      int s = SeedCache.SHEET;
      int budget = UPLOAD_PIXELS;
      double fx = (this.host.seedCx() / 512.0 - s / 2.0) / s;
      double fz = (this.host.seedCz() / 512.0 - s / 2.0) / s;
      for (long[] at : this.ordered(sheets(r), fx, fz)) {
         int sx = (int)at[0];
         int sz = (int)at[1];
         long[][] masks = make ? this.sheetMasks(sx, sz) : null;
         Sheet tex = null;
         for (int n : SHEET_SIZES) {
            SeedCache.Job key = new SeedCache.Job(SeedCache.Type.SHEET, sx, sz, n, cave);
            Sheet t = this.sheets.get(key);
            if (make && (t == null || !same(t.masks(), masks)) && budget > 0 && System.nanoTime() < deadline) {
               SeedSampler.Terrain terrain = this.whole(key);
               Sheet made = terrain == null ? null : this.takeSheet(key, terrain, masks);
               if (made != null) {
                  t = made;
                  budget -= made.size() * made.size();
               }
            }

            if (t != null) {
               tex = t;
               break;
            }
         }

         if (tex != null) {
            this.used.add(tex);
            int side = s * 512;
            g.blit(tex.id(), (sx * s - r[4]) * 512, (sz * s - r[5]) * 512, 0.0F, 0.0F, side, side, tex.size(), tex.size(), tex.size(), tex.size());
         }
      }
   }

   private SeedSampler.Terrain whole(SeedCache.Job key) {
      SeedSampler.Terrain t = this.cache.picture(key);
      if (t != null && t.slim()) {
         this.cache.again(key);
         this.lastWanted = List.of();
         return null;
      }

      return t;
   }

   private Sheet takeSheet(SeedCache.Job key, SeedSampler.Terrain terrain, long[][] masks) {
      SheetPrepared p = this.sheetPrepared.get(key);
      if (p != null && same(p.masks(), masks)) {
         this.sheetPrepared.remove(key);
         Sheet old = this.sheets.remove(key);
         if (old != null) {
            this.retire(old.id());
            this.texturePixels -= (long)old.size() * old.size();
         }

         int n = key.n() * SeedCache.SHEET;
         Tex image = Tex.create("Chunk Atlas seed map", n, n);
         image.putAbgr(p.abgr());
         image.upload();
         Tex id = image;
         Sheet t = new Sheet(id, n, masks);
         this.sheets.put(key, t);
         this.texturePixels += (long)n * n;
         return t;
      }

      String pk = "s" + key;
      if (this.preparing.add(pk)) {
         SHADING.execute(() -> {
            try {
               int n = key.n() * SeedCache.SHEET;
               int[] argb = this.pixels(terrain, n);
               int per = SeedCache.SHEET * 512 / n;
               int[] out = new int[argb.length];
               for (int i = 0; i < argb.length; i++) {
                  int c = argb[i];
                  if (masks != null && c != 0) {
                     int x = i % n * per;
                     int z = i / n * per;
                     if (explored(masks, x + per / 2, z + per / 2) && explored(masks, x, z) && explored(masks, x + per - 1, z)
                        && explored(masks, x, z + per - 1) && explored(masks, x + per - 1, z + per - 1)) {
                        c = 0;
                     }
                  }

                  out[i] = c & 0xFF00FF00 | (c >> 16 & 0xFF) | (c & 0xFF) << 16;
               }

               this.sheetPrepared.put(key, new SheetPrepared(out, masks));
            } catch (RuntimeException ignored) {
            } finally {
               this.preparing.remove(pk);
            }
         });
      }

      return null;
   }

   private final Map<SeedCache.Job, Pic> details = new java.util.LinkedHashMap<>(16, 0.75F, true);
   private final Map<SeedCache.Job, SeedSampler.Terrain> detailFrom = new java.util.HashMap<>();

   private Pic takeDetail(SeedCache.Job key, SeedSampler.Terrain terrain, long[] mask) {
      String pk = "d" + key;
      Prepared p = this.prepared.get(pk);
      if (p != null && p.from() == terrain && Arrays.equals(p.mask(), mask)) {
         this.prepared.remove(pk);
         this.release(this.details, key);
         Pic t = this.uploadReady(this.details, key, p.abgr(), 512, mask);
         this.detailFrom.put(key, terrain);

         Pic old = this.kept.remove(key);
         if (old != null) {
            this.retire(old.id());
         }

         this.keep(key, p.small(), mask);
         return t;
      }

      if (this.preparing.add(pk)) {
         SHADING.execute(() -> {
            try {
               int look = dev.chunkatlas.Settings.lookBits();
               int[] argb = !this.host.seedBiomes() ? terrain.pixels(look, this.colours)
                  : (look & dev.chunkatlas.engine.TileRenderer.DETAILED) != 0 ? this.biomeShaded(terrain) : vanillaBiomes(terrain);
               int[] abgr = cut(argb, 512, mask, false);
               this.prepared.put(pk, new Prepared(abgr, mask, terrain, shrink(abgr, 512)));
            } catch (RuntimeException ignored) {
            } finally {
               this.preparing.remove(pk);
            }
         });
      }

      return null;
   }

   void drawPartial(Gfx g) {
      if (!this.terrainShown() || this.cache == null) {
         return;
      }

      if (far(this.terrainSize())) {
         return;
      }

      g.push();
      g.identity();
      int[] r = this.regions(0);
      int want = this.terrainSize();
      int cave = this.host.seedCaveY();
      int budget = UPLOAD_PIXELS / 2;
      long deadline = System.nanoTime() + FRAME_WORK / 2;
      this.push(g, r);
      for (int rz = r[1]; rz <= r[3]; rz++) {
         for (int rx = r[0]; rx <= r[2]; rx++) {
            long[] partial = this.partial(rx, rz);
            if (partial == null) {
               continue;
            }

            Pic tex = null;
            for (SeedCache.Job key : this.pictures(rx, rz, want, cave)) {
               int n = key.n();
               Pic t = this.partials.get(key);
               if ((t == null || !Arrays.equals(t.mask(), partial)) && budget > 0 && System.nanoTime() < deadline) {
                  SeedSampler.Terrain terrain = this.whole(key);
                  Pic made = terrain == null ? null : this.take(this.partials, key, terrain, n, partial, true);
                  if (made != null) {
                     t = made;
                     budget -= n * n;
                  }
               }

               if (t != null) {
                  tex = t;
                  break;
               }
            }

            if (tex != null) {
               this.used.add(tex);
               g.blit(tex.id(), (rx - r[4]) * 512, (rz - r[5]) * 512, 0.0F, 0.0F, 512, 512, tex.size(), tex.size(), tex.size(), tex.size());
            }

            SeedCache.Job dk = new SeedCache.Job(SeedCache.Type.DETAIL, rx, rz, 0, cave);
            Pic made = this.details.get(dk);
            long[] mask = this.covered(rx, rz);
            if (made == null || !Arrays.equals(made.mask(), mask)) {
               made = this.kept.get(dk);
            }

            if (made != null && Arrays.equals(made.mask(), mask)) {
               this.drawMade(g, made, dk, rx, rz, r);
            }
         }
      }

      g.pop();
      g.pop();
   }

   private void drawMade(Gfx g, Pic d, SeedCache.Job dk, int rx, int rz, int[] r) {
      if (d == null) {
         d = this.kept.get(dk);
      }

      if (d != null) {
         this.used.add(d);
         g.blit(d.id(), (rx - r[4]) * 512, (rz - r[5]) * 512, 0.0F, 0.0F, 512, 512, d.size(), d.size(), d.size(), d.size());
      }
   }

   private void drawKept(Gfx g, int[] r, int cave) {
      for (Map.Entry<SeedCache.Job, Pic> e : new ArrayList<>(this.kept.entrySet())) {
         SeedCache.Job k = e.getKey();
         if (k.y() == cave && k.rx() >= r[0] && k.rx() <= r[2] && k.rz() >= r[1] && k.rz() <= r[3]) {
            this.drawMade(g, null, k, k.rx(), k.rz(), r);
         }
      }
   }

   private static int[] shrink(int[] abgr, int n) {
      int k = KEPT_SIZE;
      int per = n / k;
      int[] out = new int[k * k];
      for (int z = 0; z < k; z++) {
         for (int x = 0; x < k; x++) {
            int a = 0;
            int b = 0;
            int c = 0;
            int count = 0;
            for (int dz = 0; dz < per; dz++) {
               int row = (z * per + dz) * n + x * per;
               for (int dx = 0; dx < per; dx++) {
                  int p = abgr[row + dx];
                  if (p >>> 24 != 0) {
                     a += p >> 16 & 0xFF;
                     b += p >> 8 & 0xFF;
                     c += p & 0xFF;
                     count++;
                  }
               }
            }

            out[z * k + x] = count * 2 < per * per ? 0 : 0xFF000000 | a / count << 16 | b / count << 8 | c / count;
         }
      }

      return out;
   }

   private List<SeedCache.Job> pictures(int rx, int rz, int want, int cave) {
      List<SeedCache.Job> out = new ArrayList<>(SIZES.length * 2);
      if (cave == SeedSampler.NO_CAVE) {
         for (int n : SIZES) {
            if (n <= want && n >= HEIGHTS_FROM / 4) {
               out.add(new SeedCache.Job(SeedCache.Type.BIOMES, rx, rz, n));
            }
         }
      }

      for (int n : SIZES) {
         if (n <= Math.min(want, BASE_MAX)) {
            out.add(new SeedCache.Job(SeedCache.Type.BASE, rx, rz, n, cave));
         }
      }

      return out;
   }

   private void push(Gfx g, int[] r) {
      double zoom = this.host.seedZoom();
      double cx = this.host.seedCx();
      double cz = this.host.seedCz();
      int baseRx = Math.floorDiv((int)Math.floor(cx), 512);
      int baseRz = Math.floorDiv((int)Math.floor(cz), 512);
      double midX = this.host.width / 2.0;
      double midY = (this.host.seedTop() + this.host.seedBottom()) / 2.0;
      g.push();
      g.translate((float)(midX - (cx - baseRx * 512.0) * zoom), (float)(midY - (cz - baseRz * 512.0) * zoom));
      g.scale((float)zoom, (float)zoom);
      r[4] = baseRx;
      r[5] = baseRz;
   }

   private record Prepared(int[] abgr, long[] mask, SeedSampler.Terrain from, int[] small) {
      Prepared(int[] abgr, long[] mask, SeedSampler.Terrain from) {
         this(abgr, mask, from, null);
      }
   }

   private final Map<SeedCache.Job, Pic> kept = new LinkedHashMap<>(64, 0.75F, true);

   private void keep(SeedCache.Job key, int[] small, long[] mask) {
      if (small == null) {
         return;
      }

      Tex image = Tex.create("Chunk Atlas seed map", KEPT_SIZE, KEPT_SIZE);
      image.putAbgr(small);
      image.upload();
      Tex id = image;
      this.kept.put(key, new Pic(id, KEPT_SIZE, KEPT_SIZE, mask, -1));
   }

   private static int[] vanillaBiomes(SeedSampler.Terrain t) {
      dev.chunkatlas.engine.Surface d = t.detail();
      int side = d.side;
      int[] base = new int[side * side];
      Map<Integer, Integer> colours = new HashMap<>();
      for (int i = 0; i < base.length; i++) {
         if (!d.drawn(i)) {
            continue;
         }

         int b = d.biome[i];
         Integer c = colours.get(b);
         if (c == null) {
            String name = dev.chunkatlas.engine.BiomeIndex.name(b);
            c = name == null ? 0 : dev.chunkatlas.engine.BiomePalette.colour(name);
            colours.put(b, c);
         }

         base[i] = c == 0 ? 0 : 0xFF000000 | c;
      }

      int[] all = dev.chunkatlas.engine.TileRenderer.shadeVanilla(base, d.height, d.depth, side, false);
      int apron = (side - 512) / 2;
      int[] out = new int[512 * 512];
      for (int z = 0; z < 512; z++) {
         System.arraycopy(all, (z + apron) * side + apron, out, z * 512, 512);
      }

      return out;
   }

   private final Map<String, Prepared> prepared = new java.util.concurrent.ConcurrentHashMap<>();
   private final java.util.Set<String> preparing = java.util.concurrent.ConcurrentHashMap.newKeySet();
   private static final java.util.concurrent.ExecutorService SHADING = java.util.concurrent.Executors.newFixedThreadPool(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)), r -> {
      Thread th = new Thread(r, "Chunk Atlas seed pictures");
      th.setDaemon(true);
      th.setPriority(Thread.MIN_PRIORITY + 1);
      return th;
   });

   private Pic take(Map<SeedCache.Job, Pic> into, SeedCache.Job key, SeedSampler.Terrain terrain, int n, long[] mask, boolean keep) {
      String pk = (keep ? "p" : "t") + key;
      Prepared p = this.prepared.get(pk);
      if (p != null && Arrays.equals(p.mask(), mask)) {
         this.prepared.remove(pk);
         this.release(into, key);
         return this.uploadReady(into, key, p.abgr(), n, mask);
      }

      if (this.preparing.add(pk)) {
         SHADING.execute(() -> {
            try {
               int[] argb = this.pixels(terrain, n);
               this.prepared.put(pk, new Prepared(cut(argb, n, mask, keep), mask, terrain));
            } catch (RuntimeException ignored) {
            } finally {
               this.preparing.remove(pk);
            }
         });
      }

      return null;
   }

   private static int[] cut(int[] argb, int n, long[] mask, boolean keep) {
      int[] out = new int[argb.length];
      int perChunk = n / 32;
      for (int i = 0; i < argb.length; i++) {
         int c = argb[i];
         if (mask != null) {
            int x = i % n;
            int z = i / n;
            int chunk = perChunk == 0 ? (z * 32 / n) * 32 + x * 32 / n : (z / perChunk) * 32 + x / perChunk;
            if (((mask[chunk >> 6] & 1L << (chunk & 63)) != 0L) != keep) {
               c = 0;
            }
         }

         out[i] = c & 0xFF00FF00 | (c >> 16 & 0xFF) | (c & 0xFF) << 16;
      }

      return out;
   }

   private Pic uploadReady(Map<SeedCache.Job, Pic> into, SeedCache.Job key, int[] abgr, int n, long[] mask) {
      Tex image = Tex.create("Chunk Atlas seed map", n, n);
      image.putAbgr(abgr);
      image.upload();
      Tex id = image;
      Pic t = new Pic(id, n, n, mask, -1);
      into.put(key, t);
      this.texturePixels += (long)n * n;
      return t;
   }

   private void release(SeedCache.Job key) {
      this.release(this.textures, key);
   }

   private void release(Map<SeedCache.Job, Pic> from, SeedCache.Job key) {
      Pic old = from.remove(key);
      if (old != null) {
         this.retire(old.id());
         this.texturePixels -= (long)old.size() * old.size();
      }
   }

   private void trimTextures() {
      Iterator<Map.Entry<SeedCache.Job, Pic>> it = this.details.entrySet().iterator();
      int over = this.details.size() - 12;
      while (over > 0 && it.hasNext()) {
         Map.Entry<SeedCache.Job, Pic> e = it.next();
         if (this.inUse(e.getValue())) {
            continue;
         }

         this.retire(e.getValue().id());
         this.texturePixels -= 512L * 512L;
         this.detailFrom.remove(e.getKey());
         it.remove();
         over--;
      }

      it = this.kept.entrySet().iterator();
      over = this.kept.size() - MAX_KEPT;
      while (over > 0 && it.hasNext()) {
         Pic t = it.next().getValue();
         if (this.inUse(t)) {
            continue;
         }

         this.retire(t.id());
         it.remove();
         over--;
      }

      Iterator<Map.Entry<SeedCache.Job, Sheet>> sheets = this.sheets.entrySet().iterator();
      over = this.sheets.size() - MAX_SHEETS;
      while ((over > 0 || this.texturePixels > TEXTURE_PIXELS) && sheets.hasNext()) {
         Sheet t = sheets.next().getValue();
         if (this.inUse(t)) {
            continue;
         }

         this.retire(t.id());
         this.texturePixels -= (long)t.size() * t.size();
         sheets.remove();
         over--;
      }

      it = this.partials.entrySet().iterator();
      while (this.texturePixels > TEXTURE_PIXELS && this.partials.size() > 64 && it.hasNext()) {
         Pic t = it.next().getValue();
         if (this.inUse(t)) {
            continue;
         }

         this.retire(t.id());
         this.texturePixels -= (long)t.size() * t.size();
         it.remove();
      }

      it = this.textures.entrySet().iterator();
      while (this.texturePixels > TEXTURE_PIXELS && it.hasNext()) {
         Pic t = it.next().getValue();
         if (this.inUse(t)) {
            continue;
         }

         this.retire(t.id());
         this.texturePixels -= (long)t.size() * t.size();
         it.remove();
      }
   }

   private record Saved(short[] ids, dev.chunkatlas.engine.WorldLayout.RegionInfo from) {
   }

   private final Map<Long, Saved> saved = new LinkedHashMap<>(64, 0.75F, true);
   private final Set<Long> savedReading = new java.util.HashSet<>();
   private final java.util.concurrent.ConcurrentLinkedQueue<Object[]> savedRead = new java.util.concurrent.ConcurrentLinkedQueue<>();

   private final Map<Long, Integer> savedInSheet = new HashMap<>();
   private volatile int savedEpoch;

   private volatile String savedProblem;
   private static final java.util.concurrent.ExecutorService READING = java.util.concurrent.Executors.newFixedThreadPool(
      Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 3)), dev.chunkatlas.engine.Background.named("Chunk Atlas saved biomes")
   );

   private java.util.BitSet litSaved = new java.util.BitSet();
   private int litSavedFor = -2;

   private void readSaved(List<long[]> seen, boolean far) {
      Object[] done;
      while ((done = this.savedRead.poll()) != null) {
         long key = (Long)done[0];
         this.savedReading.remove(key);
         if ((Integer)done[3] == this.savedEpoch && done[1] != null) {
            this.saved.put(key, new Saved((short[])done[1], (dev.chunkatlas.engine.WorldLayout.RegionInfo)done[2]));
            this.savedInSheet.merge(key(Math.floorDiv((int)(key >> 32), SeedCache.SHEET), Math.floorDiv((int)key, SeedCache.SHEET)), 1, Integer::sum);
         }
      }

      Iterator<Map.Entry<Long, Saved>> it = this.saved.entrySet().iterator();
      while (this.saved.size() > MAX_SAVED && it.hasNext()) {
         it.next();
         it.remove();
      }

      Map<Long, dev.chunkatlas.engine.WorldLayout.RegionInfo> files = this.host.seedRegionFiles();
      if (files == null || files.isEmpty()) {
         return;
      }

      int epoch = this.savedEpoch;
      for (long[] a : seen) {
         if (this.savedReading.size() >= 12) {
            break;
         }

         long key = key((int)a[0], (int)a[1]);
         dev.chunkatlas.engine.WorldLayout.RegionInfo file = files.get(key);
         if (file == null || this.savedReading.contains(key)) {
            continue;
         }

         Saved known = this.saved.get(key);
         if (known != null && known.from().equals(file)) {
            continue;
         }

         this.savedReading.add(key);
         READING.execute(() -> {
            short[] ids = null;
            try {
               ids = dev.chunkatlas.engine.SavedBiomes.read(file, this.colours, () -> epoch != this.savedEpoch);
            } catch (Throwable t) {
               this.savedProblem = String.valueOf(t);
            } finally {
               this.savedRead.add(new Object[]{key, ids, file, epoch});
            }
         });
      }
   }

   private void dropSaved() {
      this.savedEpoch++;
      this.saved.clear();
      this.savedReading.clear();
      this.savedInSheet.clear();
   }

   private int savedBiome(int bx, int bz) {
      Saved s = this.saved.get(key(bx >> 9, bz >> 9));
      int n = dev.chunkatlas.engine.SavedBiomes.SIDE;
      return s == null ? -1 : s.ids()[((bz & 511) * n >> 9) * n + ((bx & 511) * n >> 9)];
   }

   private boolean litSaved(int index) {
      if (this.litSavedFor != this.biome) {
         this.litSavedFor = this.biome;
         this.litSaved = new java.util.BitSet();
         for (int b : this.biomes) {
            String id = this.cache == null ? null : this.cache.world().biomeId(b);
            int i = id == null ? -1 : dev.chunkatlas.engine.BiomeIndex.of(id.toString());
            if (i >= 0) {
               this.litSaved.set(i);
            }
         }
      }

      return this.litSaved.get(index);
   }

   private final Map<Long, Pic> sheetDims = new LinkedHashMap<>(32, 0.75F, true);

   private final Map<Long, Object> dimFrom = new HashMap<>();

   private void drawHighlight(Gfx g) {
      int[] r = this.regions(0);
      int want = this.terrainSize();
      int cave = this.host.seedCaveY();
      int budget = UPLOAD_PIXELS / 4;
      long deadline = System.nanoTime() + FRAME_WORK / 2;
      this.push(g, r);
      if (far(want)) {
         int s = SeedCache.SHEET;
         int[] sr = sheets(r);
         for (int sz = sr[1]; sz <= sr[3]; sz++) {
            for (int sx = sr[0]; sx <= sr[2]; sx++) {
               long key = key(sx, sz);
               SeedSampler.Terrain best = null;
               int bestN = 0;
               for (int n : SHEET_SIZES) {
                  SeedSampler.Terrain t = this.cache.picture(new SeedCache.Job(SeedCache.Type.SHEET, sx, sz, n, cave));
                  if (t != null) {
                     best = t;
                     bestN = n;
                     break;
                  }
               }

               Pic d = this.sheetDims.get(key);
               Object from = this.savedInSheet.getOrDefault(key, 0);
               if (best != null && (d == null || d.n() != bestN || d.biome() != this.biome || !from.equals(this.dimFrom.get(~key)))
                  && budget > 0 && System.nanoTime() < deadline) {
                  if (d != null) {
                     this.retire(d.id());
                  }

                  d = this.dim(this.sheetDims, key, best, bestN, sx * s * 512, sz * s * 512, s * 512, best.side() - 2);
                  this.dimFrom.put(~key, from);
                  budget -= d.size() * d.size();
               }

               int x = (sx * s - r[4]) * 512;
               int z = (sz * s - r[5]) * 512;
               if (d != null && d.biome() == this.biome) {
                  this.used.add(d);
                  g.blit(d.id(), x, z, 0.0F, 0.0F, s * 512, s * 512, d.size(), d.size(), d.size(), d.size());
               } else {
                  g.fill(x, z, x + s * 512, z + s * 512, DIM);
               }
            }
         }
      } else {
         for (int rz = r[1]; rz <= r[3]; rz++) {
            for (int rx = r[0]; rx <= r[2]; rx++) {
               long key = key(rx, rz);
               SeedSampler.Terrain best = null;
               int bestN = 0;
               for (int n : SIZES) {
                  SeedSampler.Terrain t = n > want ? null : this.cache.terrain(rx, rz, n);
                  if (t == null && n <= want && cave != SeedSampler.NO_CAVE) {
                     t = this.cache.picture(new SeedCache.Job(SeedCache.Type.BASE, rx, rz, n, cave));
                  }

                  if (t != null) {
                     best = t;
                     bestN = n;
                     break;
                  }
               }

               Pic d = this.dims.get(key);
               Saved saved = this.saved.get(key);
               Object from = saved == null ? "" : saved.ids();
               if (best != null && (d == null || d.n() != bestN || d.biome() != this.biome || from != this.dimFrom.get(key))
                  && budget > 0 && System.nanoTime() < deadline) {
                  if (d != null) {
                     this.retire(d.id());
                  }

                  int m = best.side() - 2;
                  d = this.dim(this.dims, key, best, bestN, rx * 512, rz * 512, 512, saved == null ? m : Math.max(m, Math.min(64, want)));
                  this.dimFrom.put(key, from);
                  budget -= d.size() * d.size();
               }

               int x = (rx - r[4]) * 512;
               int z = (rz - r[5]) * 512;
               if (d != null && d.biome() == this.biome) {
                  this.used.add(d);
                  g.blit(d.id(), x, z, 0.0F, 0.0F, 512, 512, d.size(), d.size(), d.size(), d.size());
               } else {
                  g.fill(x, z, x + 512, z + 512, DIM);
               }
            }
         }
      }

      g.pop();
      Iterator<Map.Entry<Long, Pic>> it = this.dims.entrySet().iterator();
      int over = this.dims.size() - MAX_DIMS;
      while (over > 0 && it.hasNext()) {
         Map.Entry<Long, Pic> e = it.next();
         if (this.inUse(e.getValue())) {
            continue;
         }

         this.retire(e.getValue().id());
         this.dimFrom.remove(e.getKey());
         it.remove();
         over--;
      }

      it = this.sheetDims.entrySet().iterator();
      over = this.sheetDims.size() - 64;
      while (over > 0 && it.hasNext()) {
         Map.Entry<Long, Pic> e = it.next();
         if (this.inUse(e.getValue())) {
            continue;
         }

         this.retire(e.getValue().id());
         this.dimFrom.remove(~e.getKey());
         it.remove();
         over--;
      }
   }

   private Pic dim(Map<Long, Pic> into, long key, SeedSampler.Terrain t, int n, int x0, int z0, int blocks, int m) {
      Tex image = Tex.create("Chunk Atlas biome highlight", m, m);
      int per = blocks / m;
      for (int z = 0; z < m; z++) {
         for (int x = 0; x < m; x++) {
            int ox = x * per + per / 2;
            int oz = z * per + per / 2;
            int saved = this.savedBiome(x0 + ox, z0 + oz);
            boolean lit = saved >= 0 ? this.litSaved(saved) : this.biomes.contains(t.biomeAt(ox, oz));
            image.set(x, z, lit ? 0 : DIM);
         }
      }

      image.upload();
      Tex id = image;
      Pic d = new Pic(id, n, m, null, this.biome);
      into.put(key, d);
      return d;
   }

   private final java.util.Set<Long> unchecked = new java.util.HashSet<>();
   private int markersDrawn;

   private static boolean needsCheck(SeedSampler.Found f) {
      return !f.checked() && !SeedSampler.isFeature(f.kind());
   }

   void drawOver(Gfx g, int mouseX, int mouseY, boolean inMap) {
      this.hovered = null;
      this.unchecked.clear();
      this.markersDrawn = 0;
      if (!this.shown() && this.settings().slime()) {
         this.drawSlimeFromSeed(g);
      }

      if (!this.shown() || this.cache == null) {
         return;
      }

      if (this.biome >= 0) {
         this.drawHighlight(g);
      }

      SeedMapSettings s = this.settings();
      SeedSampler world = this.cache.world();
      double zoom = this.host.seedZoom();
      int[] r = this.regions(0);
      if (s.slime() && world.hasSlime() && zoom * 16.0 >= SLIME_FROM) {
         int cx0 = Math.floorDiv((int)Math.floor(this.host.seedCx() - this.host.width / 2.0 / zoom), 16);
         int cx1 = Math.floorDiv((int)Math.floor(this.host.seedCx() + this.host.width / 2.0 / zoom), 16);
         double halfH = (this.host.seedBottom() - this.host.seedTop()) / 2.0 / zoom;
         int cz0 = Math.floorDiv((int)Math.floor(this.host.seedCz() - halfH), 16);
         int cz1 = Math.floorDiv((int)Math.floor(this.host.seedCz() + halfH), 16);
         for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
               if (world.slimeChunk(cx, cz)) {
                  int x0 = (int)Math.floor(this.host.seedScreenX(cx * 16.0));
                  int y0 = (int)Math.floor(this.host.seedScreenY(cz * 16.0));
                  int x1 = (int)Math.floor(this.host.seedScreenX(cx * 16.0 + 16.0));
                  int y1 = (int)Math.floor(this.host.seedScreenY(cz * 16.0 + 16.0));
                  g.outline(x0, y0, Math.max(1, x1 - x0 + 1), Math.max(1, y1 - y0 + 1), 0xD058E05A);
               }
            }
         }
      }

      SeedSampler.Found over = null;
      double overD = 81.0;
      if (s.structures()) {
         boolean small = zoom * 16.0 >= SMALL_FROM;
         List<SeedSampler.Found> shown = new ArrayList<>();
         for (SeedSampler.Found f : world.rings()) {
            if (s.kindShown(f.kind())) {
               shown.add(f);
            }
         }

         if (512.0 * zoom >= 24.0) {
            for (int rz = r[1]; rz <= r[3]; rz++) {
               for (int rx = r[0]; rx <= r[2]; rx++) {
                  List<SeedSampler.Found> here = this.cache.structures(rx, rz);
                  if (here != null) {
                     for (SeedSampler.Found f : here) {
                        if (s.kindShown(f.kind()) && (small || MAJOR.contains(f.kind()) || f.equals(this.highlight))) {
                           shown.add(f);
                        }
                     }
                  }
               }
            }
         }

         double cx = this.host.seedCx();
         double cz = this.host.seedCz();
         if (!small) {
            shown.sort(Comparator.comparingDouble(f -> (f.x() - cx) * (f.x() - cx) + (f.z() - cz) * (f.z() - cz)));
            if (shown.size() > FAR_CAP) {
               shown = new ArrayList<>(shown.subList(0, FAR_CAP));
            }
         }

         shown.sort(Comparator.comparingInt(f -> f.equals(this.highlight) ? -1 : MAJOR.contains(f.kind()) ? MAJOR.indexOf(f.kind()) : MAJOR.size()));
         Map<Long, int[]> placed = new HashMap<>();
         int half = ICON / 2;
         for (SeedSampler.Found f : shown) {
            int x = (int)Math.floor(this.host.seedScreenX(f.x()));
            int y = (int)Math.floor(this.host.seedScreenY(f.z()));
            if (x < -ICON || x > this.host.width + ICON || y < this.host.seedTop() - ICON || y > this.host.seedBottom() + ICON || overlaps(placed, x, y)) {
               continue;
            }

            placed.put((long)Math.floorDiv(x, ICON) << 32 | Math.floorDiv(y, ICON) & 0xFFFFFFFFL, new int[]{x, y});
            icon(g, f.kind(), x - half, y - half);
            this.markersDrawn++;
            if (needsCheck(f)) {
               this.unchecked.add((long)Math.floorDiv(f.x(), 512) << 32 | Math.floorDiv(f.z(), 512) & 0xFFFFFFFFL);
            }

            double d = (mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y);
            if (inMap && d < overD) {
               overD = d;
               over = f;
            }
         }
      }

      this.hovered = over;
      if (this.highlight != null && System.currentTimeMillis() < this.highlightUntil) {
         int x = (int)Math.floor(this.host.seedScreenX(this.highlight.x()));
         int y = (int)Math.floor(this.host.seedScreenY(this.highlight.z()));
         int pulse = 9 + (int)(System.currentTimeMillis() / 120L % 5L);
         g.outline(x - pulse, y - pulse, pulse * 2 + 1, pulse * 2 + 1, 0xFF8FB8FF);
      }
   }

   private static boolean overlaps(Map<Long, int[]> placed, int x, int y) {
      int gx = Math.floorDiv(x, ICON);
      int gy = Math.floorDiv(y, ICON);
      for (int dy = -1; dy <= 1; dy++) {
         for (int dx = -1; dx <= 1; dx++) {
            int[] p = placed.get((long)(gx + dx) << 32 | gy + dy & 0xFFFFFFFFL);
            if (p != null && Math.abs(p[0] - x) < ICON + 1 && Math.abs(p[1] - y) < ICON + 1) {
               return true;
            }
         }
      }

      return false;
   }

   void hover(int bx, int bz, boolean saved, List<String> parts, boolean biome) {
      if (!this.shown() || this.cache == null) {
         String name = biome ? this.biomeHere(bx, bz) : null;
         if (name != null) {
            parts.add(name);
         }

         return;
      }

      SeedSampler.Found over = this.hovered;
      if (over != null) {
         parts.add(0, kindName(over.kind()) + "  " + over.x() + ", " + over.z());
      }

      SeedMapSettings s = this.settings();
      if (biome && (s.biomes() || this.biome >= 0 || !saved)) {
         String name = this.biomeName(bx, bz);
         if (name != null) {
            parts.add(Ui.t("chunkatlas.seed.hover.biome", name));
         }
      }

      if (s.slime() && this.cache.world().slimeChunk(Math.floorDiv(bx, 16), Math.floorDiv(bz, 16))) {
         parts.add(Ui.t("chunkatlas.seed.hover.slime"));
      }
   }

   private record Pic(Tex id, int n, int size, long[] mask, int biome) {
   }

   private record Cover(long[] bits, RegionFacts facts, long[] drawn, long[] mask, long[] partial) {
   }

   static String biomeName(String id) {
      String path = IdText.path(id);
      if (path == null) {
         return id;
      }

      String key = "biome." + IdText.namespace(id) + "." + path;
      return Language.getInstance().has(key) ? Ui.t(key) : pretty(path);
   }

   static String kindName(String kind) {
      String path = IdText.path(kind);
      if (path == null) {
         return kind;
      }

      String key = "chunkatlas.seed.kind." + path;
      return IdText.namespace(kind).equals("minecraft") && Language.getInstance().has(key) ? Ui.t(key) : pretty(path);
   }

   private static String pretty(String path) {
      String s = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ').strip();
      return s.isEmpty() ? path : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
   }

   String unavailableKey() {
      if (this.needsSeed()) {
         return this.onServer() ? "chunkatlas.seed.unavailable.server" : "chunkatlas.seed.unavailable.world_seed";
      }

      String why = this.reason();
      return why == null ? "chunkatlas.seed.unavailable" : why;
   }

   String biomeName(int bx, int bz) {
      if (!this.shown() || this.cache == null) {
         return null;
      }

      int cave = this.host.seedCaveY();
      int rx = Math.floorDiv(bx, 512);
      int rz = Math.floorDiv(bz, 512);
      for (int n : SIZES) {
         SeedSampler.Terrain t = cave == SeedSampler.NO_CAVE ? this.cache.terrain(rx, rz, n) : this.cache.picture(new SeedCache.Job(SeedCache.Type.BASE, rx, rz, n, cave));
         if (t != null) {
            String id = this.cache.world().biomeId(t.biomeAt(Math.floorMod(bx, 512), Math.floorMod(bz, 512)));
            return id == null ? null : biomeName(id);
         }
      }

      return this.biomeHere(bx, bz);
   }

   private SeedSampler lookup;
   private String lookupKey;
   private long lookupAt = Long.MIN_VALUE;
   private int lookupY;
   private String lookupName;

   SeedSampler lookupWorld() {
      if (!this.available()) {
         return null;
      }

      String dim = this.host.seedDimension();
      Long seed = this.seedFor();
      if (dim == null || seed == null) {
         return null;
      }

      String key = dim + "/" + seed;
      if (!key.equals(this.lookupKey)) {
         this.lookupKey = key;
         this.lookupAt = Long.MIN_VALUE;
         try {
            this.lookup = this.cache != null && this.cache.world().dimension().equals(dim) && this.cache.world().seed() == seed ? this.cache.world() : this.openWorld(dim);
         } catch (RuntimeException e) {
            this.lookup = null;
         }
      }

      if (this.lookup == null && this.seedEditable()) {
         this.lookupKey = null;
      }

      return this.lookup;
   }

   String biomeHere(int bx, int bz) {
      if (this.lookupWorld() == null) {
         return null;
      }

      int cave = this.host.seedCaveY();
      long at = (long)(bx >> 2) << 32 | (bz >> 2) & 0xFFFFFFFFL;
      if (at != this.lookupAt || cave != this.lookupY) {
         this.lookupAt = at;
         this.lookupY = cave;
         String id = this.lookup.biomeId(cave == SeedSampler.NO_CAVE ? this.lookup.biomeAt(bx, bz) : this.lookup.biomeAt(bx, cave, bz));
         this.lookupName = id == null ? null : biomeName(id);
      }

      return this.lookupName;
   }

   SeedSampler world() {
      return this.cache == null ? null : this.cache.world();
   }

   List<String> kinds() {
      return this.cache == null ? List.of() : this.cache.world().kinds();
   }

   Long shownSeed() {
      return this.cache != null ? this.cache.world().seed() : this.seedFor();
   }

   int[] from() {
      int x = (int)Math.floor(this.host.seedCx());
      int z = (int)Math.floor(this.host.seedCz());
      if (this.mc.player != null && this.cache != null && Ids.of(this.mc.player.level().dimension()).equals(this.cache.world().dimension())) {
         x = this.mc.player.getBlockX();
         z = this.mc.player.getBlockZ();
      }

      return new int[]{x, z};
   }

   long distance(SeedSampler.Found f) {
      int[] from = this.from();
      return Math.round(Math.hypot(f.x() - from[0], f.z() - from[1]));
   }

   SeedSampler.Found nearestKnown(String kind) {
      if (this.cache == null) {
         return null;
      }

      int[] from = this.from();
      SeedSampler.Found best = null;
      double bestD = Double.MAX_VALUE;
      for (SeedSampler.Found f : this.cache.world().rings()) {
         double d = Math.hypot(f.x() - from[0], f.z() - from[1]);
         if (f.kind().equals(kind) && d < bestD) {
            best = f;
            bestD = d;
         }
      }

      int rx0 = Math.floorDiv(from[0], 512);
      int rz0 = Math.floorDiv(from[1], 512);
      for (int dz = -6; dz <= 6; dz++) {
         for (int dx = -6; dx <= 6; dx++) {
            List<SeedSampler.Found> here = this.cache.structures(rx0 + dx, rz0 + dz);
            if (here == null) {
               continue;
            }

            for (SeedSampler.Found f : here) {
               if (f.kind().equals(kind)) {
                  double d = Math.hypot(f.x() - from[0], f.z() - from[1]);
                  if (d < bestD) {
                     best = f;
                     bestD = d;
                  }
               }
            }
         }
      }

      return best;
   }

   void jumpTo(SeedSampler.Found f) {
      this.kindShown(f.kind(), true);
      this.highlight = f;
      this.highlightUntil = System.currentTimeMillis() + 8000L;
      this.host.seedCentre(f.x(), f.z());
   }

   List<Component> hoverTip() {
      SeedSampler.Found over = this.hovered;
      if (over == null || !this.shown()) {
         return null;
      }

      return Glass.tip2(kindName(over.kind()), Ui.t("chunkatlas.seed.distance", Ui.count(this.distance(over))));
   }

   private void change(Runnable r) {
      r.run();
      this.settings().save();
   }

   void toggle() {
      this.lastFrame = 0L;
      SeedMapSettings s = this.settings();
      if (this.shown()) {
         this.change(() -> s.show(false));
      } else if (this.needsSeed()) {
         this.askSeed();
      } else if (!this.available()) {
         return;
      } else if (s.accepted()) {
         this.change(() -> s.show(true));
      } else {
         this.host.seedOpen(new SeedMapWarningScreen(this.host, () -> this.change(() -> {
            s.accept();
            s.show(true);
         })));
      }
   }

   boolean terrainOn() {
      return this.settings().biomes();
   }

   void terrainOn(boolean on) {
      this.change(() -> this.settings().biomes(on));
   }

   boolean featuresOpen() {
      return this.settings().featuresOpen();
   }

   void featuresOpen(boolean open) {
      this.change(() -> this.settings().featuresOpen(open));
   }

   boolean structuresOn() {
      return this.settings().structures();
   }

   void structuresOn(boolean on) {
      this.change(() -> this.settings().structures(on));
   }

   void openTypes() {
      this.lastFrame = 0L;
      this.host.openSeedPanel(SeedPanel.Tab.STRUCTURES, false);
   }

   void openBiomes() {
      this.lastFrame = 0L;
      this.host.openSeedPanel(SeedPanel.Tab.BIOMES, false);
   }

   List<SeedPanel.Biome> biomeList() {
      SeedSampler here = this.world();
      if (here == null) {
         return List.of();
      }

      List<SeedPanel.Biome> list = new ArrayList<>();
      for (int b : here.possibleBiomes()) {
         String id = here.biomeId(b);
         if (id != null) {
            list.add(new SeedPanel.Biome(b, id, biomeName(id), here.biomeColour(b)));
         }
      }

      list.sort(Comparator.comparing(SeedPanel.Biome::name));
      return list;
   }

   private void drawSlimeFromSeed(Gfx g) {
      Long seed = this.available() ? this.seedFor() : null;
      double zoom = this.host.seedZoom();
      if (seed == null || !"minecraft:overworld".equals(this.host.seedDimension()) || zoom * 16.0 < SLIME_FROM) {
         return;
      }

      int cx0 = Math.floorDiv((int)Math.floor(this.host.seedCx() - this.host.width / 2.0 / zoom), 16);
      int cx1 = Math.floorDiv((int)Math.floor(this.host.seedCx() + this.host.width / 2.0 / zoom), 16);
      double halfH = (this.host.seedBottom() - this.host.seedTop()) / 2.0 / zoom;
      int cz0 = Math.floorDiv((int)Math.floor(this.host.seedCz() - halfH), 16);
      int cz1 = Math.floorDiv((int)Math.floor(this.host.seedCz() + halfH), 16);
      for (int cz = cz0; cz <= cz1; cz++) {
         for (int cx = cx0; cx <= cx1; cx++) {
            if (net.minecraft.world.level.levelgen.WorldgenRandom.seedSlimeChunk(cx, cz, seed, 987234911L).nextInt(10) == 0) {
               int x0 = (int)Math.floor(this.host.seedScreenX(cx * 16.0));
               int y0 = (int)Math.floor(this.host.seedScreenY(cz * 16.0));
               int x1 = (int)Math.floor(this.host.seedScreenX(cx * 16.0 + 16.0));
               int y1 = (int)Math.floor(this.host.seedScreenY(cz * 16.0 + 16.0));
               g.outline(x0, y0, Math.max(1, x1 - x0 + 1), Math.max(1, y1 - y0 + 1), 0xD058E05A);
            }
         }
      }
   }

   boolean slimeShown() {
      return this.settings().slime();
   }

   void slimeShown(boolean on) {
      this.change(() -> this.settings().slime(on));
   }

   boolean kindShown(String kind) {
      return this.settings().kindShown(kind);
   }

   void kindShown(String kind, boolean on) {
      this.change(() -> this.settings().kindShown(kind, on));
   }

   int highlighted() {
      return this.biomes.size() == 1 ? this.biomes.iterator().next() : -1;
   }

   java.util.Set<Integer> highlightedAll() {
      return java.util.Collections.unmodifiableSet(this.biomes);
   }

   boolean isHighlighted(int biome) {
      return this.biomes.contains(biome);
   }

   void highlight(int biome) {
      this.biomes.clear();
      if (biome >= 0) {
         this.biomes.add(biome);
      }

      this.changedHighlight();
   }

   void highlight(int biome, boolean on) {
      if (on ? this.biomes.add(biome) : this.biomes.remove(biome)) {
         this.changedHighlight();
      }
   }

   private void changedHighlight() {
      this.biome = this.biomes.isEmpty() ? -1 : ++this.highlightVersion;
      this.dropDims();
      String name = this.highlightName();
      if (name != null) {
         this.host.seedTell(Ui.t("chunkatlas.seed.highlighting", name), Ui.LIGHT_BLUE);
      }
   }

   String highlightName() {
      if (this.biomes.isEmpty() || this.cache == null) {
         return null;
      }

      if (this.biomes.size() > 1) {
         return Ui.plural("chunkatlas.seed.highlighting.several", this.biomes.size());
      }

      String id = this.cache.world().biomeId(this.biomes.iterator().next());
      return id == null ? null : biomeName(id);
   }

   boolean finding() {
      return this.finding;
   }

   void findNearest(String kind) {
      if (this.cache != null && !this.finding) {
         this.kindShown(kind, true);
         this.find(kindName(kind), (w, x, z) -> w.nearest(kind, x, z, 30000, () -> this.stopFinding));
      }
   }

   void findBiome(int biome) {
      if (this.cache != null && !this.finding) {
         String id = this.cache.world().biomeId(biome);
         this.find(id == null ? "?" : biomeName(id), (w, x, z) -> {
            int[] at = w.nearestBiome(biome, x, z, 12800);
            return at == null ? null : new SeedSampler.Found("biome", String.valueOf(id), at[0], at[1], true);
         });
      }
   }

   private interface Search {
      SeedSampler.Found run(SeedSampler world, int x, int z);
   }

   private void find(String name, Search search) {
      SeedCache c = this.cache;
      int x = (int)Math.floor(this.host.seedCx());
      int z = (int)Math.floor(this.host.seedCz());
      if (this.mc.player != null && Ids.of(this.mc.player.level().dimension()).equals(c.world().dimension())) {
         x = this.mc.player.getBlockX();
         z = this.mc.player.getBlockZ();
      }

      this.host.seedTell(Ui.t("chunkatlas.seed.finding", name), Ui.GREY);
      this.finding = true;
      this.stopFinding = false;
      int fromX = x;
      int fromZ = z;
      Thread t = new Thread(() -> {
         SeedSampler.Found f = null;
         try {
            f = search.run(c.world(), fromX, fromZ);
         } catch (RuntimeException ignored) {
         }

         SeedSampler.Found found = f;
         this.mc.execute(() -> {
            this.finding = false;
            if (this.cache != c || this.stopFinding) {
               return;
            }

            if (found == null) {
               this.host.seedTell(Ui.t("chunkatlas.seed.not_found", name), Ui.YELLOW);
            } else {
               long blocks = Math.round(Math.hypot(found.x() - fromX, found.z() - fromZ));
               this.highlight = found;
               this.highlightUntil = System.currentTimeMillis() + 8000L;
               this.host.seedCentre(found.x(), found.z());
               this.host.seedTell(Ui.t("chunkatlas.seed.found", name, Ui.count(blocks)), Ui.LIGHT_BLUE);
            }
         });
      }, "Chunk Atlas find nearest");
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
   }

   java.util.concurrent.CompletableFuture<String> tourCheck() {
      SeedCache c = this.cache;
      IntegratedServer server = this.mc.getSingleplayerServer();
      if (c == null || server == null || this.mc.player == null) {
         return java.util.concurrent.CompletableFuture.completedFuture("no seed map to check");
      }

      SeedSampler w = c.world();
      int water = w.flat() ? 0 : this.colours.colour("minecraft:water") & 0xFFFFFF;

      int px = this.mc.player.getBlockX() + 6000;
      int pz = this.mc.player.getBlockZ() + 6000;
      return server.submit(() -> w.tourCheck(px, pz, water));
   }

   void tourSlime() {
      this.change(() -> this.settings().slime(true));
   }

   boolean tourHighlight(String id) {
      if (this.cache != null) {
         for (int b : this.cache.world().possibleBiomes()) {
            if (String.valueOf(this.cache.world().biomeId(b)).equals(id)) {
               this.highlight(b);
               return true;
            }
         }
      }

      return false;
   }

   int[] tourPartialSpot(int bx, int bz) {
      int[] best = null;
      double bestD = Double.MAX_VALUE;
      for (Map.Entry<Long, RegionFacts> e : this.host.seedFacts().entrySet()) {
         int rx = (int)(e.getKey() >> 32);
         int rz = (int)(long)e.getKey();
         RegionFacts f = e.getValue();
         for (int i = 0; i < 1024; i++) {
            if (f.state[i] == RegionFacts.PARTIAL) {
               int x = rx * 512 + (i & 31) * 16 + 8;
               int z = rz * 512 + (i >> 5) * 16 + 8;
               double d = (double)(x - bx) * (x - bx) + (double)(z - bz) * (z - bz);
               if (d < bestD) {
                  bestD = d;
                  best = new int[]{x, z};
               }
            }
         }
      }

      return best;
   }

   String tourCoverage(int bx, int bz) {
      int rx = Math.floorDiv(bx, 512);
      int rz = Math.floorDiv(bz, 512);
      Map<Long, long[]> present = this.host.seedPresent();
      long[] bits = present == null ? null : present.get((long)rx << 32 | rz & 0xFFFFFFFFL);
      long[] mask = this.covered(rx, rz);
      RegionFacts facts = this.host.seedFacts().get((long)rx << 32 | rz & 0xFFFFFFFFL);
      int saved = 0;
      int full = 0;
      int partial = 0;
      for (int i = 0; i < 1024; i++) {
         if (bits != null && (bits[i >> 6] & 1L << (i & 63)) != 0L) {
            saved++;
            if (mask != null && (mask[i >> 6] & 1L << (i & 63)) != 0L) {
               full++;
            }

            if (facts != null && facts.state[i] == RegionFacts.PARTIAL) {
               partial++;
            }
         }
      }

      return "region " + rx + "," + rz + ": " + saved + " saved, " + full + " fully generated (no prediction), " + partial
         + " partly generated" + (facts == null ? " (not read yet)" : "");
   }

   boolean settled() {
      return !this.shown() || this.cache != null && this.cache.idle() && !this.finding;
   }

   boolean tourIdle() {
      return this.cache != null && this.cache.idle();
   }

   String tourHighlightAt(int bx, int bz) {
      int b = this.cache.world().biomeAt(bx, bz);
      this.highlight(b);
      return this.cache.world().biomeId(b) + " (" + b + ")";
   }

   String tourState() {
      long gap = this.worstGapNanos / 1000000L;
      this.worstGapNanos = 0L;
      return "seed " + (this.shown() ? "on" : "off") + (this.reason() == null ? "" : " (" + this.reason() + ")")
         + (this.cache == null ? "" : ", " + this.cache.waiting() + " waiting, " + this.cache.stats() + ", " + this.cache.world().kinds().size() + " kinds")
         + ", " + this.textures.size() + " biome pictures (" + this.texturePixels / 1000000L + " M pixels, " + this.retiredEver + " let go), " + this.sheets.size() + " sheets, " + this.saved.size() + " regions' saved biomes (" + this.savedReading.size() + " being read" + (this.savedProblem == null ? "" : ", " + this.savedProblem) + "), " + this.dims.size() + " highlight masks"
         + ", " + this.markersDrawn + " markers drawn, " + this.unchecked.size() + " regions of them still quick"
         + (this.biome >= 0 ? ", highlight " + this.highlightName() : "") + ", worst frame gap " + gap + " ms";
   }
}
