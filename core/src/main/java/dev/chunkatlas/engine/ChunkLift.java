package dev.chunkatlas.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ChunkLift {
   public static final int SINCE = 2844;

   public static final int MAX_DY = 4064;
   private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
   private static final String BEDROCK = "minecraft:bedrock";

   private ChunkLift() {
   }

   public static ChunkCopy.Transform transform(int dy, String dimension) {
      return (role, root) -> lift(root, role, dy, dimension);
   }

   public static boolean lift(Map<String, Object> root, String role, int dy, String dimension) {
      if (dy == 0) {
         return true;
      }

      switch (role) {
         case "region" -> {
            return region(root, dy, dimension);
         }
         case "entities" -> ChunkCopy.shift(root, "entities", 0, dy, 0, dimension);
         case "poi" -> poi(root, dy);
         default -> {
         }
      }

      return true;
   }

   @SuppressWarnings("unchecked")
   private static boolean region(Map<String, Object> root, int dy, String dimension) {
      if (root.containsKey("Level") || !(root.get("sections") instanceof List<?> raw)) {
         return false;
      }

      TreeMap<Integer, Map<String, Object>> byY = new TreeMap<>();
      for (Object o : raw) {
         if (o instanceof Map<?, ?> m && m.get("Y") instanceof Number y && m.get("block_states") instanceof Map) {
            byY.put(y.intValue(), (Map<String, Object>)m);
         }
      }

      if (byY.isEmpty()) {
         return false;
      }

      int lo = byY.firstKey();
      int hi = byY.lastKey();
      int n = hi - lo + 1;
      Terrain t = new Terrain(n);
      for (Map.Entry<Integer, Map<String, Object>> e : byY.entrySet()) {
         if (!t.read(e.getKey() - lo, e.getValue())) {
            return false;
         }
      }

      List<Object> sections = new ArrayList<>(n);
      Terrain moved = t.moved(dy);
      for (int s = 0; s < n; s++) {
         Map<String, Object> old = byY.get(lo + s);
         Map<String, Object> section = old == null ? new LinkedHashMap<>() : new LinkedHashMap<>(old);
         if (old == null) {
            section.put("Y", (byte)(lo + s));
         }

         section.remove("BlockLight");
         section.remove("SkyLight");
         section.put("block_states", moved.blockStates(s));
         Map<String, Object> biomes = moved.biomes(s);
         if (biomes != null) {
            section.put("biomes", biomes);
         } else {
            section.remove("biomes");
         }

         sections.add(section);
      }

      int minY = lo * 16;
      int maxY = hi * 16 + 15;

      root.put("sections", sections);
      ChunkFields.apply(new ChunkFields.Changes(null, null, null, null, null, null, null, null, true, true, true), root);
      ChunkCopy.shift(root, "region", 0, dy, 0, dimension);
      for (String list : new String[]{"block_entities", "block_ticks", "fluid_ticks"}) {
         if (root.get(list) instanceof List<?> l) {
            l.removeIf(o -> !(o instanceof Map<?, ?> m) || !(m.get("y") instanceof Integer y) || y < minY || y > maxY);
         }
      }

      postProcessing(root, dy, lo, n);

      root.remove("carving_mask");
      root.remove("CarvingMasks");
      root.remove("Lights");
      return true;
   }

   @SuppressWarnings("unchecked")
   private static void postProcessing(Map<String, Object> root, int dy, int lo, int n) {
      if (!(root.get("PostProcessing") instanceof List<?> lists)) {
         return;
      }

      int base = root.get("yPos") instanceof Number y ? y.intValue() : lo;
      List<List<Object>> out = new ArrayList<>();
      for (int i = 0; i < lists.size(); i++) {
         out.add(new ArrayList<>());
      }

      for (int i = 0; i < lists.size(); i++) {
         if (!(lists.get(i) instanceof List<?> l)) {
            continue;
         }

         for (Object o : l) {
            if (o instanceof Short packed) {
               int y = (base + i) * 16 + (packed >> 4 & 15) + dy;
               int at = Math.floorDiv(y, 16) - base;
               if (y >= lo * 16 && y < (lo + n) * 16 && at >= 0 && at < out.size()) {
                  out.get(at).add((short)(packed & 0xF0F | (y & 15) << 4));
               }
            }
         }
      }

      root.put("PostProcessing", new ArrayList<Object>(out));
   }

   private static final class Terrain {
      final int n;
      final List<Map<String, Object>> states;
      final Map<String, Integer> stateIds;
      final List<String> biomeNames;
      final Map<String, Integer> biomeIds;

      final int[] blocks;

      final int[] cells;

      Terrain(int n) {
         this(n, new ArrayList<>(), new HashMap<>(), new ArrayList<>(), new HashMap<>());
         java.util.Arrays.fill(this.blocks, this.state(air()));
         java.util.Arrays.fill(this.cells, -1);
      }

      private Terrain(int n, List<Map<String, Object>> states, Map<String, Integer> stateIds, List<String> biomeNames, Map<String, Integer> biomeIds) {
         this.n = n;
         this.states = states;
         this.stateIds = stateIds;
         this.biomeNames = biomeNames;
         this.biomeIds = biomeIds;
         this.blocks = new int[n * 4096];
         this.cells = new int[n * 64];
      }

      private static Map<String, Object> air() {
         Map<String, Object> a = new LinkedHashMap<>();
         a.put("Name", "minecraft:air");
         return a;
      }

      int state(Map<String, Object> entry) {
         Object props = entry.get("Properties");
         String key = entry.get("Name") + (props instanceof Map<?, ?> p ? new TreeMap<>(p).toString() : "");
         return this.stateIds.computeIfAbsent(key, k -> {
            this.states.add(entry);
            return this.states.size() - 1;
         });
      }

      String name(int state) {
         return String.valueOf(this.states.get(state).get("Name"));
      }

      @SuppressWarnings("unchecked")
      boolean read(int s, Map<String, Object> section) {
         Map<String, Object> bs = Nbt.map(section, "block_states");
         List<Object> palette = Nbt.list(bs, "palette");
         if (palette == null || palette.isEmpty()) {
            return false;
         }

         int[] ids = new int[palette.size()];
         for (int i = 0; i < ids.length; i++) {
            if (!(palette.get(i) instanceof Map<?, ?> m) || !(m.get("Name") instanceof String)) {
               return false;
            }

            ids[i] = this.state((Map<String, Object>)m);
         }

         int[] local = unpacked(bs.get("data"), ids.length, 4096, true);
         if (local == null) {
            return false;
         }

         for (int i = 0; i < 4096; i++) {
            this.blocks[s * 4096 + i] = ids[local[i]];
         }

         Map<String, Object> bm = Nbt.map(section, "biomes");
         List<Object> names = Nbt.list(bm, "palette");
         if (names == null || names.isEmpty()) {
            return true;
         }

         int[] bids = new int[names.size()];
         for (int i = 0; i < bids.length; i++) {
            if (!(names.get(i) instanceof String b)) {
               return false;
            }

            bids[i] = this.biomeIds.computeIfAbsent(b, k -> {
               this.biomeNames.add(k);
               return this.biomeNames.size() - 1;
            });
         }

         int[] cell = unpacked(bm.get("data"), bids.length, 64, false);
         if (cell == null) {
            return false;
         }

         for (int i = 0; i < 64; i++) {
            this.cells[s * 64 + i] = bids[cell[i]];
         }

         return true;
      }

      Terrain moved(int dy) {
         Terrain out = new Terrain(this.n, this.states, this.stateIds, this.biomeNames, this.biomeIds);
         int h = this.n * 16;
         Map<String, Object> stone = new LinkedHashMap<>();
         stone.put("Name", "minecraft:stone");
         int stoneId = this.state(stone);
         int airId = this.state(air());
         for (int col = 0; col < 256; col++) {
            int bottom = this.blocks[col];
            boolean floor = this.name(bottom).equals(BEDROCK);

            int fill = bottom;
            if (AIR.contains(this.name(bottom))) {
               fill = airId;
            } else if (floor) {
               fill = stoneId;
               for (int y = 1; y < Math.min(16, h); y++) {
                  int b = this.blocks[y << 8 | col];
                  String name = this.name(b);
                  if (!name.equals(BEDROCK) && !AIR.contains(name)) {
                     fill = b;
                     break;
                  }
               }
            }

            for (int y = 0; y < h; y++) {
               int from = y - dy;
               out.blocks[y << 8 | col] = from < 0 ? fill : from >= h ? airId : this.blocks[from << 8 | col];
            }

            if (floor) {
               out.blocks[col] = bottom;
            }
         }

         int cellsHigh = this.n * 4;
         for (int cy = 0; cy < cellsHigh; cy++) {
            int from = Math.max(0, Math.min(cellsHigh - 1, Math.floorDiv(cy * 4 + 2 - dy, 4)));
            System.arraycopy(this.cells, from * 16, out.cells, cy * 16, 16);
         }

         return out;
      }

      Map<String, Object> blockStates(int s) {
         int[] values = new int[4096];
         System.arraycopy(this.blocks, s * 4096, values, 0, 4096);
         List<Object> palette = new ArrayList<>();
         long[] data = this.packed(values, palette, true, id -> copy(this.states.get(id)));
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("palette", palette);
         if (data != null) {
            out.put("data", data);
         }

         return out;
      }

      Map<String, Object> biomes(int s) {
         int[] values = new int[64];
         int known = -1;
         for (int i = 0; i < 64; i++) {
            values[i] = this.cells[s * 64 + i];
            if (values[i] >= 0) {
               known = values[i];
            }
         }

         if (known < 0) {
            return null;
         }

         for (int i = 0; i < 64; i++) {
            if (values[i] < 0) {
               values[i] = known;
            }
         }

         List<Object> palette = new ArrayList<>();
         long[] data = this.packed(values, palette, false, this.biomeNames::get);
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("palette", palette);
         if (data != null) {
            out.put("data", data);
         }

         return out;
      }

      private long[] packed(int[] values, List<Object> palette, boolean blocks, java.util.function.IntFunction<Object> entry) {
         Map<Integer, Integer> local = new HashMap<>();
         int[] index = new int[values.length];
         for (int i = 0; i < values.length; i++) {
            int v = values[i];
            Integer at = local.get(v);
            if (at == null) {
               at = palette.size();
               local.put(v, at);
               palette.add(entry.apply(v));
            }

            index[i] = at;
         }

         if (palette.size() == 1) {
            return null;
         }

         int bits = bits(palette.size(), blocks);
         int per = 64 / bits;
         long[] data = new long[(values.length + per - 1) / per];
         for (int i = 0; i < values.length; i++) {
            data[i / per] |= (long)index[i] << (i % per * bits);
         }

         return data;
      }

      @SuppressWarnings("unchecked")
      private static Map<String, Object> copy(Map<String, Object> state) {
         Map<String, Object> c = new LinkedHashMap<>(state);
         if (state.get("Properties") instanceof Map<?, ?> p) {
            c.put("Properties", new LinkedHashMap<>((Map<String, Object>)p));
         }

         return c;
      }
   }

   static int bits(int size, boolean blocks) {
      int need = 32 - Integer.numberOfLeadingZeros(size - 1);
      return blocks ? Math.max(4, need) : Math.max(1, need);
   }

   private static int[] unpacked(Object data, int size, int count, boolean blocks) {
      int[] out = new int[count];
      if (size == 1) {
         return out;
      }

      if (!(data instanceof long[] words)) {
         return null;
      }

      int bits = bits(size, blocks);
      int per = 64 / bits;
      if (words.length != (count + per - 1) / per) {
         return null;
      }

      for (int i = 0; i < count; i++) {
         int v = ChunkDecoder.unpack(words, bits, true, i);
         if (v >= size) {
            return null;
         }

         out[i] = v;
      }

      return out;
   }

   @SuppressWarnings("unchecked")
   private static void poi(Map<String, Object> root, int dy) {
      Map<String, Object> sections = Nbt.map(root, "Sections");
      if (sections == null) {
         return;
      }

      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<String, Object> e : sections.entrySet()) {
         if (!(e.getValue() instanceof Map<?, ?> section)) {
            continue;
         }

         Object valid = section.get("Valid");
         for (Object rec : Nbt.list(section, "Records") == null ? List.of() : Nbt.list(section, "Records")) {
            if (!(rec instanceof Map<?, ?> m) || !(m.get("pos") instanceof int[] p) || p.length != 3) {
               continue;
            }

            Map<String, Object> moved = new LinkedHashMap<>((Map<String, Object>)m);
            moved.put("pos", new int[]{p[0], p[1] + dy, p[2]});
            Map<String, Object> into = (Map<String, Object>)out.computeIfAbsent(Integer.toString(Math.floorDiv(p[1] + dy, 16)), k -> {
               Map<String, Object> s = new LinkedHashMap<>();
               s.put("Valid", valid instanceof Byte ? valid : (byte)1);
               s.put("Records", new ArrayList<Object>());
               return s;
            });
            ((List<Object>)into.get("Records")).add(moved);
         }
      }

      root.put("Sections", out);
   }
}
