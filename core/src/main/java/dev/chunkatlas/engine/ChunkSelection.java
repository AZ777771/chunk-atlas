package dev.chunkatlas.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public final class ChunkSelection {
   public static final int WORDS = 16;
   private final Map<Long, long[]> regions = new HashMap<>();
   private int size;

   private int changes;

   public int size() {
      return this.size;
   }

   public boolean isEmpty() {
      return this.size == 0;
   }

   public void clear() {
      this.regions.clear();
      this.size = 0;
      this.changes++;
   }

   public int changes() {
      return this.changes;
   }

   public boolean contains(int chunkX, int chunkZ) {
      long[] bits = this.regions.get(WorldLayout.key(chunkX >> 5, chunkZ >> 5));
      int i = index(chunkX, chunkZ);
      return bits != null && (bits[i >> 6] & 1L << (i & 63)) != 0L;
   }

   public boolean add(int chunkX, int chunkZ) {
      long[] bits = this.regions.computeIfAbsent(WorldLayout.key(chunkX >> 5, chunkZ >> 5), k -> new long[16]);
      int i = index(chunkX, chunkZ);
      if ((bits[i >> 6] & 1L << (i & 63)) != 0L) {
         return false;
      }

      bits[i >> 6] = bits[i >> 6] | 1L << (i & 63);
      this.size++;
      this.changes++;
      return true;
   }

   public boolean remove(int chunkX, int chunkZ) {
      long key = WorldLayout.key(chunkX >> 5, chunkZ >> 5);
      long[] bits = this.regions.get(key);
      int i = index(chunkX, chunkZ);
      if (bits != null && (bits[i >> 6] & 1L << (i & 63)) != 0L) {
         bits[i >> 6] = bits[i >> 6] & ~(1L << (i & 63));
         this.size--;
         this.changes++;
         if (empty(bits)) {
            this.regions.remove(key);
         }

         return true;
      } else {
         return false;
      }
   }

   public void add(int rx, int rz, long[] mask) {
      this.changes++;
      long[] bits = this.regions.computeIfAbsent(WorldLayout.key(rx, rz), k -> new long[16]);

      for (int w = 0; w < 16; w++) {
         this.size = this.size + Long.bitCount(mask[w] & ~bits[w]);
         bits[w] |= mask[w];
      }

      if (empty(bits)) {
         this.regions.remove(WorldLayout.key(rx, rz));
      }
   }

   public void remove(int rx, int rz, long[] mask) {
      this.changes++;
      long key = WorldLayout.key(rx, rz);
      long[] bits = this.regions.get(key);
      if (bits != null) {
         for (int w = 0; w < 16; w++) {
            this.size = this.size - Long.bitCount(mask[w] & bits[w]);
            bits[w] &= ~mask[w];
         }

         if (empty(bits)) {
            this.regions.remove(key);
         }
      }
   }

   public void addRect(int x0, int z0, int x1, int z1, Map<Long, long[]> present) {
      this.rect(x0, z0, x1, z1, present, true);
   }

   public void removeRect(int x0, int z0, int x1, int z1, Map<Long, long[]> present) {
      this.rect(x0, z0, x1, z1, present, false);
   }

   private void rect(int x0, int z0, int x1, int z1, Map<Long, long[]> present, boolean add) {
      int minX = Math.min(x0, x1);
      int maxX = Math.max(x0, x1);
      int minZ = Math.min(z0, z1);
      int maxZ = Math.max(z0, z1);
      long across = ((long)(maxX >> 5) - (minX >> 5) + 1L) * ((long)(maxZ >> 5) - (minZ >> 5) + 1L);
      if (across > present.size()) {
         for (Entry<Long, long[]> e : present.entrySet()) {
            this.one((int)(e.getKey() >> 32), (int)e.getKey().longValue(), minX, minZ, maxX, maxZ, e.getValue(), add);
         }
      } else {
         for (int rz = minZ >> 5; rz <= maxZ >> 5; rz++) {
            for (int rx = minX >> 5; rx <= maxX >> 5; rx++) {
               long[] there = present.get(WorldLayout.key(rx, rz));
               if (there != null) {
                  this.one(rx, rz, minX, minZ, maxX, maxZ, there, add);
               }
            }
         }
      }
   }

   private void one(int rx, int rz, int minX, int minZ, int maxX, int maxZ, long[] there, boolean add) {
      long[] mask = mask(rx, rz, minX, minZ, maxX, maxZ);
      if (mask != null) {
         for (int w = 0; w < 16; w++) {
            mask[w] &= there[w];
         }

         if (add) {
            this.add(rx, rz, mask);
         } else {
            this.remove(rx, rz, mask);
         }
      }
   }

   static long[] mask(int rx, int rz, int minX, int minZ, int maxX, int maxZ) {
      int lx0 = Math.max(minX, rx << 5) - (rx << 5);
      int lx1 = Math.min(maxX, (rx << 5) + 31) - (rx << 5);
      int lz0 = Math.max(minZ, rz << 5) - (rz << 5);
      int lz1 = Math.min(maxZ, (rz << 5) + 31) - (rz << 5);
      if (lx0 <= lx1 && lz0 <= lz1) {
         long row = (1L << lx1 - lx0 + 1) - 1L << lx0;
         long[] mask = new long[16];

         for (int z = lz0; z <= lz1; z++) {
            mask[z >> 1] = mask[z >> 1] | row << ((z & 1) << 5);
         }

         return mask;
      } else {
         return null;
      }
   }

   public long[] bits(int rx, int rz) {
      long[] bits = this.regions.get(WorldLayout.key(rx, rz));
      return bits == null ? null : (long[])bits.clone();
   }

   public int count(int rx, int rz) {
      long[] bits = this.regions.get(WorldLayout.key(rx, rz));
      int n = 0;
      if (bits != null) {
         for (long w : bits) {
            n += Long.bitCount(w);
         }
      }

      return n;
   }

   public Set<Long> regions() {
      return Set.copyOf(this.regions.keySet());
   }

   public ChunkSelection copy() {
      ChunkSelection c = new ChunkSelection();
      this.regions.forEach((k, v) -> c.regions.put(k, (long[])v.clone()));
      c.size = this.size;
      return c;
   }

   public void forEach(ChunkSelection.Visitor visitor) {
      for (Entry<Long, long[]> e : this.regions.entrySet()) {
         int rx = (int)(e.getKey() >> 32);
         int rz = (int)e.getKey().longValue();
         long[] bits = e.getValue();

         for (int i = 0; i < 1024; i++) {
            if ((bits[i >> 6] & 1L << (i & 63)) != 0L) {
               visitor.chunk((rx << 5) + (i & 31), (rz << 5) + (i >> 5));
            }
         }
      }
   }

   public static int index(int chunkX, int chunkZ) {
      return (chunkX & 31) + (chunkZ & 31) * 32;
   }

   private static boolean empty(long[] bits) {
      for (long w : bits) {
         if (w != 0L) {
            return false;
         }
      }

      return true;
   }

   public interface Visitor {
      void chunk(int chunkX, int chunkZ);
   }
}
