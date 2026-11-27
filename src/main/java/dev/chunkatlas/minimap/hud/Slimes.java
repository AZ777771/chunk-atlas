package dev.chunkatlas.minimap.hud;

import dev.chunkatlas.ServerSeed;
import dev.chunkatlas.minimap.map.Tile;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldgenRandom;

public final class Slimes {
   private final Long2BooleanOpenHashMap known = new Long2BooleanOpenHashMap();
   private long seed;
   private boolean has;
   private Long typed;
   private long typedAt;

   public boolean known(Minecraft mc) {
      if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
         return false;
      }

      IntegratedServer server = mc.getSingleplayerServer();
      long wanted;
      if (server != null) {
         wanted = server.overworld().getSeed();
      } else {
         long now = System.currentTimeMillis();
         if (now - this.typedAt > 5000L) {
            this.typedAt = now;
            this.typed = ServerSeed.get();
         }

         if (this.typed == null) {
            return false;
         }

         wanted = this.typed;
      }

      if (!this.has || wanted != this.seed) {
         this.known.clear();
         this.seed = wanted;
         this.has = true;
      }

      return true;
   }

   public boolean slime(int cx, int cz) {
      long key = Tile.key(cx, cz);
      if (this.known.containsKey(key)) {
         return this.known.get(key);
      }

      boolean slime = WorldgenRandom.seedSlimeChunk(cx, cz, this.seed, 987234911L).nextInt(10) == 0;
      if (this.known.size() > 50000) {
         this.known.clear();
      }

      this.known.put(key, slime);
      return slime;
   }
}
