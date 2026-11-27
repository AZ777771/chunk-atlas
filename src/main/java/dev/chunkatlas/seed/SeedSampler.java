package dev.chunkatlas.seed;

import dev.chunkatlas.engine.BlockColours;
import dev.chunkatlas.engine.Surface;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

public interface SeedSampler {
   int NO_CAVE = Integer.MIN_VALUE;

   long seed();

   MinecraftServer server();

   String dimension();

   boolean hasSlime();

   boolean slimeChunk(int chunkX, int chunkZ);

   int biomeAt(int blockX, int blockZ);

   int biomeAt(int blockX, int blockY, int blockZ);

   String biomeId(int id);

   int biomeColour(int id);

   List<Integer> possibleBiomes();

   int[] nearestBiome(int biome, int blockX, int blockZ, int maxBlocks);

   boolean flat();

   boolean hasTerrain();

   double groundAt(int blockX, int blockZ);

   void keepHeightsIn(Path home);

   default Terrain terrain(int rx, int rz, int n, BooleanSupplier cancelled) {
      return this.terrain(rx, rz, n, NO_CAVE, false, cancelled);
   }

   Terrain terrain(int rx, int rz, int n, int caveY, boolean quick, BooleanSupplier cancelled);

   Terrain sheet(int sx, int sz, int regions, int n, int caveY, BooleanSupplier cancelled);

   boolean canMakeChunks();

   boolean makeChunk(int cx, int cz, int caveY, Surface into, MapColumns map, int ox, int oz, List<Tree> trees);

   default boolean makeChunk(int cx, int cz, int caveY, Surface into, int ox, int oz) {
      return this.makeChunk(cx, cz, caveY, into, null, ox, oz, null);
   }

   default boolean makeChunk(int cx, int cz, int caveY, Surface into, int ox, int oz, List<Tree> trees) {
      return this.makeChunk(cx, cz, caveY, into, null, ox, oz, trees);
   }

   List<String> kinds();

   List<Found> structures(int rx, int rz, boolean full, BooleanSupplier cancelled);

   List<Found> features(int rx, int rz, BooleanSupplier cancelled);

   List<Found> rings();

   Found nearest(String kind, int blockX, int blockZ, int maxBlocks, BooleanSupplier cancelled);

   String bench();

   String groundCheck(int cx, int cz);

   int[] tourLand(int cx, int cz);

   String featureState();

   String tourCheck(int px, int pz, int water);

   static boolean isFeature(String kind) {
      return SeedKinds.isFeature(kind);
   }

   static int apron(int blocksPerPixel) {
      return blocksPerPixel >= 16 ? 2 : blocksPerPixel >= 4 ? 4 : 6;
   }

   record Tree(int x, int z, int top, int leaves, int radius, int map) {
   }

   record Found(String kind, String structure, int x, int z, boolean checked) {
   }

   record Terrain(int[] argb, short[] biomes, int side, int step, Surface detail, int blocksPerPixel) {
      public boolean slim() {
         return this.argb == null && this.detail == null;
      }

      public Terrain slimmed() {
         return new Terrain(null, this.biomes, this.side, this.step, null, this.blocksPerPixel);
      }

      public long weight() {
         return this.argb != null ? this.argb.length : this.detail != null ? (long)this.detail.side * this.detail.side : 0L;
      }

      public int biomeAt(int ox, int oz) {
         return this.biomes[(oz / this.step + 1) * this.side + ox / this.step + 1];
      }

      public int[] pixels(int look, BlockColours colours) {
         if (this.detail == null || (look & dev.chunkatlas.engine.TileRenderer.DETAILED) == 0 && this.argb != null) {
            return this.argb;
         }

         int apron = apron(this.blocksPerPixel);
         int inner = this.detail.side - 2 * apron;

         return dev.chunkatlas.engine.Shader.shade(
            this.detail, apron, dev.chunkatlas.engine.TileRenderer.options(look, this.blocksPerPixel, colours), new int[inner * inner]
         );
      }
   }
}
