package dev.chunkatlas.minimap.map;

import dev.chunkatlas.ChunkScanner;
import dev.chunkatlas.SpawnLight;
import dev.chunkatlas.minimap.Colours;
import dev.chunkatlas.minimap.Config;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Heights;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

public final class MapCache implements ChunkScanner.Reader {
   private static final int KEEP = 12000;
   private static final long NEAR_REFRESH_MS = 1000L;
   private static final long FAR_REFRESH_MS = 8000L;

   private static final long BUDGET_NS = 3_000_000L;

   private static final int CAVE_DEPTH = 96;

   private static final int CAVE_ROOF = 6;

   private final Long2ObjectOpenHashMap<Tile> surface = new Long2ObjectOpenHashMap<>();
   private final Long2ObjectOpenHashMap<Tile> caves = new Long2ObjectOpenHashMap<>();
   private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
   private final BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
   private ResourceKey<Level> dimension;
   private ClientLevel level;
   private int layer = Tile.SURFACE;
   private int wantedRadius = 8;

   private int version;
   private int scannedLastTick;
   private int generation;
   private int radius = 8;
   private int pcx;
   private int pcz;

   private final it.unimi.dsi.fastutil.longs.LongOpenHashSet affected = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
   private it.unimi.dsi.fastutil.longs.LongArrayList changed = new it.unimi.dsi.fastutil.longs.LongArrayList();
   private it.unimi.dsi.fastutil.longs.LongArrayList taken = new it.unimi.dsi.fastutil.longs.LongArrayList();
   private static final int MAX_CHANGED = 1 << 16;

   private long shadeNs = 4_000L;
   private final dev.chunkatlas.LiveColumns columns = new dev.chunkatlas.LiveColumns();

   private int look = -1;
   private dev.chunkatlas.engine.Shader.Options options;
   private final dev.chunkatlas.engine.Surface around = new dev.chunkatlas.engine.Surface(16 + APRON * 2);

   private static final int APRON = 4;
   private final SeedTiles seed = new SeedTiles();
   private final FarTiles far = new FarTiles();

   private final SpawnLight spawns = new SpawnLight();
   private boolean spawnsOn;

   public int version() {
      return this.version;
   }

   public int layer() {
      return this.layer;
   }

   public int look() {
      return this.look;
   }

   public FarTiles far() {
      return this.far;
   }

   public SeedTiles seed() {
      return this.seed;
   }

   public boolean caves() {
      return this.layer != Tile.SURFACE;
   }

   public int scannedLastTick() {
      return this.scannedLastTick;
   }

   public int size() {
      return this.surface.size() + this.caves.size();
   }

   public void want(int chunks) {
      this.wantedRadius = Math.max(2, chunks);
   }

   public Tile tile(int cx, int cz) {
      return this.current().get(Tile.key(cx, cz));
   }

   private Long2ObjectOpenHashMap<Tile> current() {
      return this.layer == Tile.SURFACE ? this.surface : this.caves;
   }

   public void clear() {
      this.surface.clear();
      this.caves.clear();
      this.affected.clear();
      this.changed.clear();
      this.version++;
      this.generation++;
      ChunkScanner.again(this);
   }

   public void rescan() {
      ChunkScanner.again(this);
   }

   @Override
   public boolean prepare(Minecraft mc, ClientLevel level, Entity viewer) {
      if (!Config.get().enabled) {
         this.level = null;
         return false;
      }

      if (level != this.level || level.dimension() != this.dimension) {
         this.level = level;
         this.dimension = level.dimension();
         this.clear();
      }

      int newLayer = this.chooseLayer(level, viewer);
      int bits = dev.chunkatlas.Settings.lookBits();
      if ((bits & dev.chunkatlas.engine.TileRenderer.DETAILED) != 0 && newLayer == Tile.SURFACE && night(level)) {
         bits |= dev.chunkatlas.engine.TileRenderer.NIGHT;
      }

      if (bits != this.look) {
         this.look = bits;
         this.options = (bits & dev.chunkatlas.engine.TileRenderer.DETAILED) == 0
            ? null
            : dev.chunkatlas.engine.TileRenderer.options(bits & ~dev.chunkatlas.engine.TileRenderer.CONTOURS, 1, dev.chunkatlas.GameColours.get());
         this.shadeNs = this.options == null ? 4_000L : 40_000L;
         this.clear();
      }

      if (newLayer != this.layer) {
         this.layer = newLayer;
         this.version++;
         this.generation++;
         this.affected.clear();
         ChunkScanner.again(this);
      }

      if (Config.get().lightOverlay != this.spawnsOn) {
         this.spawnsOn = Config.get().lightOverlay;
         this.rescan();
      }

      this.pcx = viewer.getBlockX() >> 4;
      this.pcz = viewer.getBlockZ() >> 4;
      this.radius = Math.min(this.wantedRadius, mc.options.getEffectiveRenderDistance() + 1);
      this.scannedLastTick = 0;
      return true;
   }

   @Override
   public int radius() {
      return this.radius;
   }

   @Override
   public long nearMs() {
      return NEAR_REFRESH_MS;
   }

   @Override
   public long farMs() {
      return FAR_REFRESH_MS;
   }

   @Override
   public long budgetNs() {
      return BUDGET_NS;
   }

   @Override
   public boolean quickCheck() {
      return this.layer == Tile.SURFACE && !this.spawnsOn && (this.look & dev.chunkatlas.engine.TileRenderer.NIGHT) == 0;
   }

   @Override
   public void read(ClientLevel level, LevelChunk chunk, ChunkScanner.Columns shared, long now) {
      Long2ObjectOpenHashMap<Tile> tiles = this.current();
      int cx = dev.chunkatlas.port.Pos.cx(chunk.getPos());
      int cz = dev.chunkatlas.port.Pos.cz(chunk.getPos());
      Tile t = tiles.computeIfAbsent(Tile.key(cx, cz), k -> new Tile(cx, cz));
      this.scan(level, chunk, t, shared);
      t.scanned = now;
      if (this.options != null) {
         for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
               Tile n = dx == 0 && dz == 0 ? t : tiles.get(Tile.key(cx + dx, cz + dz));
               if (n != null && n.layer == t.layer) {
                  this.affected.add(Tile.key(n.cx, n.cz));
               }
            }
         }
      } else {
         this.affected.add(Tile.key(cx, cz));
         Tile south = tiles.get(Tile.key(cx, cz + 1));
         if (south != null && south.layer == t.layer) {
            this.affected.add(Tile.key(cx, cz + 1));
         }
      }

      this.scannedLastTick++;
   }

   @Override
   public long owed() {
      return this.affected.size() * this.shadeNs;
   }

   @Override
   public int finish() {
      if (this.affected.isEmpty()) {
         return 0;
      }

      Long2ObjectOpenHashMap<Tile> tiles = this.current();
      long t0 = System.nanoTime();
      int n = 0;
      it.unimi.dsi.fastutil.longs.LongIterator it = this.affected.iterator();
      while (it.hasNext()) {
         long key = it.nextLong();
         Tile t = tiles.get(key);
         if (t == null || t.layer != this.layer) {
            continue;
         }

         if (this.options != null) {
            this.shadeDetailed(tiles, t);
         } else {
            this.shade(t, tiles.get(Tile.key(t.cx, t.cz - 1)));
         }

         this.changed(key);
         n++;
      }

      this.affected.clear();
      if (n > 0) {
         this.shadeNs = (this.shadeNs * 7 + (System.nanoTime() - t0) / n) / 8;
         this.version++;
      }

      if (tiles.size() > KEEP) {
         this.trim(tiles, this.pcx, this.pcz);
      }

      return n;
   }

   private void changed(long key) {
      if (this.changed.size() >= MAX_CHANGED) {
         this.changed.clear();
         this.generation++;
      }

      this.changed.add(key);
   }

   public it.unimi.dsi.fastutil.longs.LongArrayList takeChanged() {
      this.taken.clear();
      it.unimi.dsi.fastutil.longs.LongArrayList had = this.changed;
      this.changed = this.taken;
      this.taken = had;
      return had;
   }

   public int generation() {
      return this.generation;
   }

   private int chooseLayer(ClientLevel level, Entity viewer) {
      Config.Cave mode = !dev.chunkatlas.Settings.undergroundCaves() ? Config.Cave.OFF : Config.get().cave == Config.Cave.ALWAYS ? Config.Cave.ALWAYS : Config.Cave.AUTO;
      if (mode == Config.Cave.OFF) {
         return Tile.SURFACE;
      }

      int head = (int)Math.floor(viewer.getEyeY());
      if (mode == Config.Cave.AUTO && !this.underRoof(level, viewer, head)) {
         return Tile.SURFACE;
      }

      return (int)Math.floor(viewer.getY()) + 2;
   }

   private boolean underRoof(ClientLevel level, Entity viewer, int head) {
      int size = Config.get().caveRoof;
      int half = size / 2;
      int px = viewer.getBlockX();
      int pz = viewer.getBlockZ();

      for (int dz = -half; dz <= size - 1 - half; dz++) {
         for (int dx = -half; dx <= size - 1 - half; dx++) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px + dx, pz + dz);

            if (top <= head + 1 || !level.getFluidState(this.above.set(px + dx, top - 1, pz + dz)).isEmpty()) {
               return false;
            }
         }
      }

      return true;
   }

   private void scan(ClientLevel level, LevelChunk chunk, Tile t, ChunkScanner.Columns shared) {
      ChunkScanner.Columns detail = this.options != null && this.layer == Tile.SURFACE ? shared.detail() : null;
      int minY = Heights.min(level);
      int x0 = chunk.getPos().getMinBlockX();
      int z0 = chunk.getPos().getMinBlockZ();
      t.layer = this.layer;

      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int i = lz << 4 | lx;
            int wx = x0 + lx;
            int wz = z0 + lz;
            int y;
            BlockState state;
            if (this.layer == Tile.SURFACE) {
               y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) + 1;
               if (detail != null) {
                  t.top[i] = detail.top[i];
                  t.cover[i] = detail.cover[i];
                  t.topHeight[i] = detail.height[i];
                  t.topDepth[i] = detail.depth[i];
                  t.biome[i] = detail.biome[i];
                  t.topLight[i] = detail.light[i];
               }

               if (y <= minY + 1) {
                  t.base[i] = 0;
                  t.height[i] = (short)minY;
                  t.depth[i] = 0;
                  t.spawn[i] = SpawnLight.NONE;
                  continue;
               }

               do {
                  y--;
                  state = chunk.getBlockState(this.pos.set(wx, y, wz));
               } while (Colours.of(state.getBlock()) == 0 && y > minY);
            } else {
               int bottom = Math.max(minY, this.layer - CAVE_DEPTH);
               y = this.layer;
               state = chunk.getBlockState(this.pos.set(wx, y, wz));

               int roof = Math.max(bottom, this.layer - CAVE_ROOF);
               while (Colours.of(state.getBlock()) != 0 && y > roof) {
                  y--;
                  state = chunk.getBlockState(this.pos.set(wx, y, wz));
               }

               if (Colours.of(state.getBlock()) != 0) {
                  y = bottom;
               }

               while (Colours.of(state.getBlock()) == 0 && y > bottom) {
                  y--;
                  state = chunk.getBlockState(this.pos.set(wx, y, wz));
               }

               if (this.options != null) {
                  if (y <= bottom || y >= this.layer) {
                     t.top[i] = 0;
                     t.cover[i] = 0;
                     t.topDepth[i] = 0;
                  } else {
                     this.columns.scan(level, chunk, lx, lz, y, bottom, i, t.top, t.cover, t.topHeight, t.topDepth, t.biome, t.topLight);
                  }
               }

               if (y <= bottom || y >= this.layer) {
                  t.base[i] = 0;
                  t.height[i] = (short)y;
                  t.depth[i] = 0;
                  t.spawn[i] = SpawnLight.NONE;
                  continue;
               }
            }

            int depth = 0;
            FluidState fluid = state.getFluidState();
            int colour;
            if (!fluid.isEmpty()) {
               int under = y - 1;
               BlockState below;
               do {
                  below = chunk.getBlockState(this.above.set(wx, under--, wz));
                  depth++;
               } while (under > minY && !below.getFluidState().isEmpty() && depth < 127);

               colour = state.isFaceSturdy(chunk, this.pos, Direction.UP) ? Colours.of(state.getBlock()) : Colours.of(fluid.createLegacyBlock().getBlock());
            } else {
               colour = Colours.of(state.getBlock());
            }

            boolean water = Colours.water(colour);
            t.base[i] = colour;
            t.height[i] = (short)y;
            t.depth[i] = (byte)(water ? depth : 0);
            t.spawn[i] = !this.spawnsOn ? SpawnLight.NONE : this.layer == Tile.SURFACE
               ? this.spawns.column(level, chunk, wx, wz, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz), minY, true)
               : this.spawns.column(level, chunk, wx, wz, this.layer, Math.max(minY, this.layer - CAVE_DEPTH), false);
         }
      }
   }

   private void shade(Tile t, Tile north) {
      if (north != null && north.layer != t.layer) {
         north = null;
      }

      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int i = lz << 4 | lx;
            int base = t.base[i];
            if (base == 0) {
               t.pixels[i] = 0;
               continue;
            }

            int odd = lx + lz & 1;
            int brightness;
            if (t.depth[i] > 0) {
               double f = t.depth[i] * 0.1 + odd * 0.2;
               brightness = f < 0.5 ? Colours.HIGH : f > 0.9 ? Colours.LOW : Colours.NORMAL;
            } else {
               int northHeight = lz > 0 ? t.height[i - 16] : north != null ? north.height[240 | lx] : t.height[i];
               double f = (t.height[i] - northHeight) * 4.0 / 5.0 + (odd - 0.5) * 0.4;
               brightness = f > 0.6 ? Colours.HIGH : f < -0.6 ? Colours.LOW : Colours.NORMAL;
            }

            t.pixels[i] = Colours.shade(base, brightness);
         }
      }
   }

   private void shadeDetailed(Long2ObjectOpenHashMap<Tile> tiles, Tile t) {
      dev.chunkatlas.engine.Surface s = this.around;
      s.clear();
      int side = s.side;
      for (int dz = -1; dz <= 1; dz++) {
         for (int dx = -1; dx <= 1; dx++) {
            Tile n = dx == 0 && dz == 0 ? t : tiles.get(Tile.key(t.cx + dx, t.cz + dz));
            if (n == null || n.layer != t.layer) {
               continue;
            }

            for (int z = 0; z < 16; z++) {
               int gz = dz * 16 + z + APRON;
               if (gz < 0 || gz >= side) {
                  continue;
               }

               for (int x = 0; x < 16; x++) {
                  int gx = dx * 16 + x + APRON;
                  if (gx < 0 || gx >= side) {
                     continue;
                  }

                  int i = z << 4 | x;
                  int g = gz * side + gx;
                  s.top[g] = n.top[i];
                  s.cover[g] = n.cover[i];
                  s.height[g] = n.topHeight[i];
                  s.depth[g] = n.topDepth[i];
                  s.biome[g] = n.biome[i];
                  s.light[g] = n.topLight[i];
               }
            }
         }
      }

      dev.chunkatlas.engine.Shader.shade(s, APRON, this.options, t.pixels);
   }

   private void trim(Long2ObjectOpenHashMap<Tile> tiles, int pcx, int pcz) {
      List<Long2ObjectMap.Entry<Tile>> all = new ArrayList<>(tiles.long2ObjectEntrySet());
      all.sort((a, b) -> Long.compare(far(b.getValue(), pcx, pcz), far(a.getValue(), pcx, pcz)));

      for (int n = 0; n < all.size() - KEEP * 3 / 4; n++) {
         long key = all.get(n).getLongKey();
         tiles.remove(key);
         this.changed(key);
      }
   }

   private static long far(Tile t, int pcx, int pcz) {
      long dx = t.cx - pcx;
      long dz = t.cz - pcz;
      return dx * dx + dz * dz;
   }

   private static boolean lastNight;

   private static boolean night(net.minecraft.world.level.Level level) {
      if (level.getRainLevel(1.0F) < 0.2F && level.getThunderLevel(1.0F) < 0.2F) {
         lastNight = Game.night(level);
      }

      return lastNight;
   }
}
