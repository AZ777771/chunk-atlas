package dev.chunkatlas.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChunkLoads {
   public static final int ENTITY_TICKING = 0;
   public static final int BLOCK_TICKING = 1;
   public static final int BORDER = 2;
   public static final int LOADING = 3;

   public static final int PLAYER = 0;
   public static final int PORTAL = 1;
   public static final int PEARL = 2;
   public static final int FORCED = 3;
   public static final int OTHER = 4;

   private static final int[] RANK = {3, 1, 2, 0, 4};
   private static final int[][] AROUND = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

   private ChunkLoads() {
   }

   public record Raw(int maxLevel, int[] x, int[] z, int[] load, int[] sim, byte[] status, int[] tx, int[] tz, int[] tlevel, byte[] treason) {
   }

   public static int level(int load, int sim) {
      if (load > 33) {
         return LOADING;
      }

      int at = Math.max(load, Math.min(sim, 34));
      return at <= 31 ? ENTITY_TICKING : at == 32 ? BLOCK_TICKING : BORDER;
   }

   static long key(int x, int z) {
      return (long)x << 32 | z & 0xFFFFFFFFL;
   }

   private static boolean better(int level, int reason, int than, int thanReason) {
      return level < than || level == than && RANK[reason] < RANK[thanReason];
   }

   public static Snapshot build(String dimension, Raw raw, int cap) {
      int n = raw.x().length;
      Map<Long, Integer> at = new HashMap<>(n * 2);
      for (int i = 0; i < n; i++) {
         at.put(key(raw.x()[i], raw.z()[i]), i);
      }

      int max = raw.maxLevel();
      int[] best = new int[n];
      Arrays.fill(best, Integer.MAX_VALUE);
      byte[] why = new byte[n];
      Arrays.fill(why, (byte)OTHER);
      int[][] queue = new int[max + 1][];
      int[] queued = new int[max + 1];
      for (int t = 0; t < raw.tx().length; t++) {
         Integer i = at.get(key(raw.tx()[t], raw.tz()[t]));
         int lvl = Math.max(0, raw.tlevel()[t]);
         if (i != null && lvl <= max && better(lvl, raw.treason()[t], best[i], why[i])) {
            best[i] = lvl;
            why[i] = raw.treason()[t];
            push(queue, queued, lvl, i);
         }
      }

      boolean[] done = new boolean[n];
      for (int lvl = 0; lvl <= max; lvl++) {
         for (int q = 0; q < queued[lvl]; q++) {
            int i = queue[lvl][q];
            if (done[i] || best[i] != lvl) {
               continue;
            }

            done[i] = true;
            if (lvl == max) {
               continue;
            }

            for (int[] d : AROUND) {
               Integer j = at.get(key(raw.x()[i] + d[0], raw.z()[i] + d[1]));
               if (j != null && !done[j] && better(lvl + 1, why[i], best[j], why[j])) {
                  best[j] = lvl + 1;
                  why[j] = why[i];
                  push(queue, queued, lvl + 1, j);
               }
            }
         }
      }

      Integer[] order = new Integer[n];
      for (int i = 0; i < n; i++) {
         order[i] = i;
      }

      Arrays.sort(order, Comparator.comparingInt((Integer i) -> raw.load()[i]));
      Integer[] kept = Arrays.copyOf(order, Math.min(n, cap));
      Arrays.sort(kept, Comparator.comparingInt((Integer i) -> raw.z()[i]).thenComparingInt(i -> raw.x()[i]));
      int k = kept.length;
      int[] x = new int[k];
      int[] z = new int[k];
      byte[] level = new byte[k];
      byte[] reason = new byte[k];
      byte[] status = new byte[k];
      for (int m = 0; m < k; m++) {
         int i = kept[m];
         x[m] = raw.x()[i];
         z[m] = raw.z()[i];
         level[m] = (byte)level(raw.load()[i], raw.sim()[i]);
         reason[m] = why[i];
         status[m] = raw.status()[i];
      }

      return new Snapshot(dimension, x, z, level, reason, status);
   }

   private static void push(int[][] queue, int[] queued, int level, int i) {
      int[] q = queue[level];
      if (q == null || queued[level] == q.length) {
         queue[level] = q = q == null ? new int[64] : Arrays.copyOf(q, q.length * 2);
      }

      q[queued[level]++] = i;
   }

   public static final class Snapshot {
      private final String dimension;
      private final int[] x;
      private final int[] z;
      private final byte[] level;
      private final byte[] reason;
      private final byte[] status;
      private Map<Long, Integer> index;
      private List<int[]> clusters;

      public Snapshot(String dimension, int[] x, int[] z, byte[] level, byte[] reason, byte[] status) {
         this.dimension = dimension;
         this.x = x;
         this.z = z;
         this.level = level;
         this.reason = reason;
         this.status = status;
      }

      public String dimension() {
         return this.dimension;
      }

      public int size() {
         return this.x.length;
      }

      public int x(int i) {
         return this.x[i];
      }

      public int z(int i) {
         return this.z[i];
      }

      public int level(int i) {
         return this.level[i];
      }

      public int reason(int i) {
         return this.reason[i];
      }

      public int status(int i) {
         return this.status[i];
      }

      public int find(int cx, int cz) {
         Integer i = this.prepare().get(key(cx, cz));
         return i == null ? -1 : i;
      }

      public synchronized List<int[]> clusters() {
         this.prepare();
         return this.clusters;
      }

      public synchronized Map<Long, Integer> prepare() {
         if (this.index == null) {
            Map<Long, Integer> at = new HashMap<>(this.x.length * 2);
            for (int i = 0; i < this.x.length; i++) {
               at.put(key(this.x[i], this.z[i]), i);
            }

            List<int[]> groups = new ArrayList<>();
            boolean[] seen = new boolean[this.x.length];
            int[] stack = new int[Math.max(1, this.x.length)];
            for (int start = 0; start < this.x.length; start++) {
               if (seen[start]) {
                  continue;
               }

               int[] g = {this.x[start], this.z[start], this.x[start], this.z[start], 0};
               int top = 0;
               stack[top++] = start;
               seen[start] = true;
               while (top > 0) {
                  int i = stack[--top];
                  g[0] = Math.min(g[0], this.x[i]);
                  g[1] = Math.min(g[1], this.z[i]);
                  g[2] = Math.max(g[2], this.x[i]);
                  g[3] = Math.max(g[3], this.z[i]);
                  g[4]++;
                  for (int[] d : AROUND) {
                     Integer j = at.get(key(this.x[i] + d[0], this.z[i] + d[1]));
                     if (j != null && !seen[j]) {
                        seen[j] = true;
                        stack[top++] = j;
                     }
                  }
               }

               groups.add(g);
            }

            this.clusters = List.copyOf(groups);
            this.index = at;
         }

         return this.index;
      }
   }
}
