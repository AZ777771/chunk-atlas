package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkLoadsTest {
   private static ChunkLoads.Raw raw(int[][] tickets, int simulated) {
      List<int[]> chunks = new ArrayList<>();
      for (int x = -20; x <= 30; x++) {
         for (int z = -20; z <= 20; z++) {
            int best = Integer.MAX_VALUE;
            for (int[] t : tickets) {
               best = Math.min(best, t[2] + Math.max(Math.abs(x - t[0]), Math.abs(z - t[1])));
            }

            if (best <= 44) {
               chunks.add(new int[]{x, z, best});
            }
         }
      }

      int n = chunks.size();
      int[] x = new int[n];
      int[] z = new int[n];
      int[] load = new int[n];
      int[] sim = new int[n];
      byte[] status = new byte[n];
      for (int i = 0; i < n; i++) {
         x[i] = chunks.get(i)[0];
         z[i] = chunks.get(i)[1];
         load[i] = chunks.get(i)[2];
         sim[i] = load[i] <= 33 ? simulated : 100;
         status[i] = (byte)(load[i] <= 33 ? 9 : 5);
      }

      int[] tx = new int[tickets.length];
      int[] tz = new int[tickets.length];
      int[] tl = new int[tickets.length];
      byte[] tr = new byte[tickets.length];
      for (int i = 0; i < tickets.length; i++) {
         tx[i] = tickets[i][0];
         tz[i] = tickets[i][1];
         tl[i] = tickets[i][2];
         tr[i] = (byte)tickets[i][3];
      }

      return new ChunkLoads.Raw(44, x, z, load, sim, status, tx, tz, tl, tr);
   }

   @Test
   void levelsFollowTheTicketLevels() {
      assertEquals(ChunkLoads.ENTITY_TICKING, ChunkLoads.level(31, 31));
      assertEquals(ChunkLoads.BLOCK_TICKING, ChunkLoads.level(31, 32));
      assertEquals(ChunkLoads.BLOCK_TICKING, ChunkLoads.level(32, 30));

      assertEquals(ChunkLoads.BORDER, ChunkLoads.level(31, 60));
      assertEquals(ChunkLoads.BORDER, ChunkLoads.level(33, 31));
      assertEquals(ChunkLoads.LOADING, ChunkLoads.level(34, 31));
   }

   @Test
   void theNearestTicketKeepsEachChunkLoaded() {
      ChunkLoads.Snapshot s = ChunkLoads.build("minecraft:overworld", raw(new int[][]{{0, 0, 31, ChunkLoads.FORCED}, {4, 0, 31, ChunkLoads.PLAYER}}, 31), Integer.MAX_VALUE);
      assertEquals(ChunkLoads.FORCED, s.reason(s.find(0, 0)));
      assertEquals(ChunkLoads.FORCED, s.reason(s.find(-3, 2)));

      assertEquals(ChunkLoads.FORCED, s.reason(s.find(2, 0)));
      assertEquals(ChunkLoads.PLAYER, s.reason(s.find(3, 0)));
      assertEquals(ChunkLoads.PLAYER, s.reason(s.find(14, 0)));
      assertEquals(ChunkLoads.ENTITY_TICKING, s.level(s.find(0, 0)));
      assertEquals(ChunkLoads.BLOCK_TICKING, s.level(s.find(1, 1)));
      assertEquals(ChunkLoads.BORDER, s.level(s.find(-2, 0)));
      assertEquals(ChunkLoads.LOADING, s.level(s.find(-3, 0)));
      assertEquals(-1, s.find(-14, 0));
      assertEquals(1, s.clusters().size());
   }

   @Test
   void groupsAreTheTouchingChunks() {
      ChunkLoads.Snapshot s = ChunkLoads.build("minecraft:overworld", raw(new int[][]{{-16, 0, 42, ChunkLoads.OTHER}, {26, 0, 42, ChunkLoads.PORTAL}}, 100), Integer.MAX_VALUE);
      List<int[]> groups = new ArrayList<>(s.clusters());
      groups.sort((a, b) -> Integer.compare(a[0], b[0]));
      assertEquals(2, groups.size());
      assertArrayEquals(new int[]{-18, -2, -14, 2, 25}, groups.get(0));
      assertArrayEquals(new int[]{24, -2, 28, 2, 25}, groups.get(1));
      assertEquals(ChunkLoads.PORTAL, s.reason(s.find(28, 2)));
   }

   @Test
   void aCapKeepsTheMostLoadedInRows() {
      ChunkLoads.Snapshot s = ChunkLoads.build("minecraft:overworld", raw(new int[][]{{0, 0, 31, ChunkLoads.FORCED}}, 31), 9);
      assertEquals(9, s.size());
      for (int i = 0; i < s.size(); i++) {
         assertEquals(true, Math.abs(s.x(i)) <= 1 && Math.abs(s.z(i)) <= 1);
         if (i > 0) {
            assertEquals(true, s.z(i - 1) < s.z(i) || s.z(i - 1) == s.z(i) && s.x(i - 1) < s.x(i));
         }
      }
   }
}
