package dev.chunkatlas.engine;

import java.util.List;

public interface ChunkRule {
   long TICKS_PER_SECOND = 20L;

   boolean matches(int chunkX, int chunkZ, RegionFacts facts, int index);

   default boolean needsFacts() {
      return true;
   }

   default boolean includesUnreadable() {
      return false;
   }

   static ChunkRule unreadable() {
      return new ChunkRule() {
         @Override
         public boolean matches(int x, int z, RegionFacts facts, int i) {
            return facts != null && facts.state[i] == 3;
         }

         @Override
         public boolean includesUnreadable() {
            return true;
         }
      };
   }

   static ChunkRule inhabitedAtMost(long ticks) {
      return (x, z, facts, i) -> facts.state[i] != 3 && facts.inhabited[i] <= ticks;
   }

   static ChunkRule notFullyGenerated() {
      return (x, z, facts, i) -> facts.state[i] == 2;
   }

   static ChunkRule versionBelow(int version) {
      return (x, z, facts, i) -> facts.state[i] != 3 && facts.dataVersion[i] < version;
   }

   static ChunkRule versionAbove(int version) {
      return (x, z, facts, i) -> facts.state[i] != 3 && facts.dataVersion[i] > version;
   }

   static ChunkRule outsideRadius(final double blockX, final double blockZ, final double radius) {
      return new ChunkRule() {
         @Override
         public boolean matches(int x, int z, RegionFacts facts, int i) {
            double dx = Math.max(Math.max(x * 16.0 - blockX, blockX - (x * 16.0 + 16.0)), 0.0);
            double dz = Math.max(Math.max(z * 16.0 - blockZ, blockZ - (z * 16.0 + 16.0)), 0.0);
            return dx * dx + dz * dz > radius * radius;
         }

         @Override
         public boolean needsFacts() {
            return false;
         }
      };
   }

   static boolean readable(RegionFacts facts, int index) {
      return facts != null && (facts.state[index] == RegionFacts.FULL || facts.state[index] == RegionFacts.PARTIAL);
   }

   static ChunkRule none() {
      return new ChunkRule() {
         @Override
         public boolean matches(int x, int z, RegionFacts facts, int i) {
            return false;
         }

         @Override
         public boolean needsFacts() {
            return false;
         }
      };
   }

   static ChunkRule all(List<ChunkRule> rules) {
      final List<ChunkRule> list = List.copyOf(rules);
      final boolean facts = list.stream().anyMatch(ChunkRule::needsFacts);
      return new ChunkRule() {
         @Override
         public boolean matches(int x, int z, RegionFacts f, int i) {
            for (ChunkRule r : list) {
               if (!r.matches(x, z, f, i)) {
                  return false;
               }
            }

            return true;
         }

         @Override
         public boolean needsFacts() {
            return facts;
         }
      };
   }

   static ChunkRule any(List<ChunkRule> rules) {
      final List<ChunkRule> list = List.copyOf(rules);
      final boolean facts = list.stream().anyMatch(ChunkRule::needsFacts);
      return new ChunkRule() {
         @Override
         public boolean matches(int x, int z, RegionFacts f, int i) {
            for (ChunkRule r : list) {
               if (r.matches(x, z, f, i)) {
                  return true;
               }
            }

            return false;
         }

         @Override
         public boolean needsFacts() {
            return facts;
         }
      };
   }

   static ChunkRule not(ChunkRule rule) {
      final boolean facts = rule.needsFacts();
      return new ChunkRule() {
         @Override
         public boolean matches(int x, int z, RegionFacts f, int i) {
            return (!facts || ChunkRule.readable(f, i)) && !rule.matches(x, z, f, i);
         }

         @Override
         public boolean needsFacts() {
            return facts;
         }
      };
   }
}
