package dev.chunkatlas.net;

import dev.chunkatlas.engine.ChunkLoads;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Tickets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class ServerLoads {
   private static final int MOST = 65536;

   private ServerLoads() {
   }

   public static ServerLevel level(MinecraftServer server, String dimension) {
      ResourceKey<Level> id = Ids.dimension(dimension);
      return id == null ? null : server.getLevel(id);
   }

   public static ChunkLoads.Raw capture(ServerLevel level) {
      try {
         IntArrayList chunks = new IntArrayList();
         Tickets.chunks(level, MOST, (cx, cz, load, sim, status) -> chunks.addElements(chunks.size(), new int[]{cx, cz, load, sim, status}));
         int n = chunks.size() / 5;
         int[] x = new int[n];
         int[] z = new int[n];
         int[] load = new int[n];
         int[] sim = new int[n];
         byte[] status = new byte[n];
         for (int i = 0; i < n; i++) {
            x[i] = chunks.getInt(i * 5);
            z[i] = chunks.getInt(i * 5 + 1);
            load[i] = chunks.getInt(i * 5 + 2);
            sim[i] = chunks.getInt(i * 5 + 3);
            status[i] = (byte)chunks.getInt(i * 5 + 4);
         }

         IntArrayList tickets = new IntArrayList();
         Tickets.tickets(level, (cx, cz, lvl, kind) -> tickets.addElements(tickets.size(), new int[]{cx, cz, lvl, kind}));
         int m = tickets.size() / 4;
         int[] tx = new int[m];
         int[] tz = new int[m];
         int[] tlevel = new int[m];
         byte[] treason = new byte[m];
         for (int t = 0; t < m; t++) {
            tx[t] = tickets.getInt(t * 4);
            tz[t] = tickets.getInt(t * 4 + 1);
            tlevel[t] = tickets.getInt(t * 4 + 2);
            treason[t] = (byte)tickets.getInt(t * 4 + 3);
         }

         return new ChunkLoads.Raw(Tickets.maxLevel(), x, z, load, sim, status, tx, tz, tlevel, treason);
      } catch (RuntimeException e) {
         return null;
      }
   }

   public static int force(ServerLevel level, ChunkSelection chunks, boolean on) {
      int[] changed = {0};
      if (on) {
         chunks.forEach((x, z) -> {
            if (Tickets.force(level, x, z, true)) {
               changed[0]++;
            }
         });
      } else {
         for (long p : Tickets.forced(level)) {
            if (chunks.contains(ChunkPos.getX(p), ChunkPos.getZ(p)) && Tickets.force(level, ChunkPos.getX(p), ChunkPos.getZ(p), false)) {
               changed[0]++;
            }
         }
      }

      return changed[0];
   }
}
