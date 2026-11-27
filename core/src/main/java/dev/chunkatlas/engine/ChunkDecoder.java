package dev.chunkatlas.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChunkDecoder {
   static final Set<String> SKIP = Set.of(
      "BlockLight",
      "SkyLight",
      "structures",
      "Structures",
      "biomes",
      "Biomes",
      "block_ticks",
      "fluid_ticks",
      "TileTicks",
      "LiquidTicks",
      "PostProcessing",
      "Lights",
      "ToBeTicked",
      "LiquidsToBeTicked",
      "CarvingMasks",
      "UpgradeData",
      "blending_data",
      "HeightMap",
      "below_zero_retrogen"
   );
   static final Set<String> COUNT = Set.of("block_entities", "TileEntities", "Entities", "entities");
   static final Set<String> SKIP_BLOCKS;
   static final Set<String> SKIP_BUT_BIOMES;
   static final Set<String> SKIP_BUT_BIOMES_AND_LIGHT;
   static final int ALIGNED_SINCE = 2529;
   private final BlockColours colours;
   private final Map<String, int[]> names = new HashMap<>();
   private int[] legacy;
   public final int[] colour = new int[256];
   public final int[] height = new int[256];
   public final int[] water = new int[256];

   public int caveY = Integer.MIN_VALUE;
   public int caveRoof = 8;

   public boolean dry;

   public boolean biomes;
   private static final int NOT_LOOKED_UP = 33554432;

   public boolean detailed;

   public boolean night;
   public final int[] top = new int[256];
   public final int[] cover = new int[256];
   public final short[] biome = new short[256];
   public final byte[] light = new byte[256];
   private final Map<String, Long> looks = new HashMap<>();
   private long[] legacyLooks;

   public ChunkDecoder(BlockColours colours) {
      this.colours = colours;
   }

   public static ChunkDecoder.Facts facts(Map<String, Object> root) {
      Map<String, Object> level = Nbt.map(root, "Level");
      boolean wrapped = level != null;
      if (!wrapped) {
         level = root;
      }

      int version = (int)Nbt.number(root, "DataVersion", Nbt.number(level, "DataVersion", 0L));
      String status = Nbt.string(level, "Status");
      boolean full;
      if (status == null) {
         full = !Nbt.has(level, "TerrainPopulated") || Nbt.number(level, "TerrainPopulated", 1L) != 0L;
         status = full ? "full" : "not populated";
      } else {
         if (status.startsWith("minecraft:")) {
            status = status.substring(10);
         }

         full = status.equals("full") || status.equals("fullchunk") || status.equals("postprocessed");
      }

      int sections = Math.max(Nbt.count(level, "sections"), Nbt.count(level, "Sections"));
      int blockEntities = Math.max(Nbt.count(level, "block_entities"), Nbt.count(level, "TileEntities"));
      int entities = Math.max(Nbt.count(level, "Entities"), Nbt.count(level, "entities"));
      return new ChunkDecoder.Facts(
         (int)Nbt.number(level, "xPos", 0L),
         (int)Nbt.number(level, "zPos", 0L),
         version,
         status,
         full,
         Nbt.number(level, "InhabitedTime", 0L),
         Math.max(sections, 0),
         Math.max(blockEntities, 0),
         entities
      );
   }

   public boolean decode(Map<String, Object> root, ChunkDecoder.Facts facts) {
      Arrays.fill(this.colour, 0);
      Arrays.fill(this.height, 0);
      Arrays.fill(this.water, 0);
      if (this.detailed) {
         Arrays.fill(this.top, 0);
         Arrays.fill(this.cover, 0);
         Arrays.fill(this.biome, (short)-1);
         Arrays.fill(this.light, (byte)0);
      }

      Map<String, Object> level = Nbt.map(root, "Level");
      if (level == null) {
         level = root;
      }

      List<Object> raw = Nbt.list(level, "sections");
      if (raw == null) {
         raw = Nbt.list(level, "Sections");
      }

      if (raw != null && !raw.isEmpty()) {
         List<ChunkDecoder.Section> sections = new ArrayList<>(raw.size());

         for (Object o : raw) {
            ChunkDecoder.Section s = this.section(o, facts.dataVersion());
            if (s != null) {
               sections.add(s);
            }
         }

         if (sections.isEmpty()) {
            return false;
         }

         sections.sort((a, b) -> Integer.compare(b.y, a.y));
         this.readOldBiomes(level);
         if (this.caveY != Integer.MIN_VALUE) {
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  int start = this.caveStart(sections, x, z);
                  if (start != Integer.MIN_VALUE) {
                     this.column(sections, x, z, start);
                  }
               }
            }

            return true;
         }

         int[] top = facts.fullyGenerated() ? surface(level, sections) : null;

         for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
               this.column(sections, x, z, top == null ? Integer.MAX_VALUE : top[z * 16 + x]);
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private void column(List<ChunkDecoder.Section> sections, int x, int z, int startY) {
      if (this.detailed) {
         this.detailedColumn(sections, x, z, startY);
         return;
      }

      this.columnTop(sections, x, z, startY);
      int at = z * 16 + x;
      if (this.biomes && this.colour[at] != 0) {
         int b = this.biomeAt(sections, x, this.height[at], z);
         if (b == 0 && this.oldBiomes != null) {
            String name = BiomeIndex.name(this.oldBiome(x, this.height[at], z));
            b = name == null ? 0 : BiomePalette.colour(name);
         }

         if (b != 0) {
            this.colour[at] = b;
         }
      }
   }

   private int biomeAt(List<ChunkDecoder.Section> sections, int x, int y, int z) {
      int sy = Math.floorDiv(y, 16);
      for (ChunkDecoder.Section s : sections) {
         if (s.y == sy && s.biomeColours != null) {
            int cell = ((y & 15) >> 2) << 4 | (z >> 2) << 2 | x >> 2;
            int p = 0;
            if (s.biomeData != null && s.biomeBits > 0) {
               int per = 64 / s.biomeBits;
               long word = s.biomeData[Math.min(s.biomeData.length - 1, cell / per)];
               p = (int)(word >>> cell % per * s.biomeBits & (1L << s.biomeBits) - 1L);
            }

            return p < s.biomeColours.length ? s.biomeColours[p] : 0;
         }
      }

      return 0;
   }

   private void detailedColumn(List<ChunkDecoder.Section> sections, int x, int z, int startY) {
      int at = z * 16 + x;
      int waterTop = Integer.MIN_VALUE;
      long coverLook = 0L;
      boolean coverDry = false;
      int coverY = Integer.MIN_VALUE;

      for (ChunkDecoder.Section s : sections) {
         int base = s.y * 16;
         if (base > startY) {
            continue;
         }

         for (int ly = Math.min(15, startY - base); ly >= 0; ly--) {
            long look = this.lookAt(s, ly << 8 | z << 4 | x);
            if (look == 0L) {
               continue;
            }

            int y = base + ly;
            if (Look.is(look, Look.COVER) || Look.is(look, Look.CLEAR)) {
               if (coverLook == 0L) {
                  coverLook = look;
                  coverDry = waterTop == Integer.MIN_VALUE;
                  coverY = y;
               }

               if (Look.is(look, Look.WATER) && waterTop == Integer.MIN_VALUE) {
                  waterTop = y;
               }

               continue;
            }

            if (Look.is(look, Look.WATER)) {
               if (waterTop == Integer.MIN_VALUE) {
                  waterTop = y;
               }

               continue;
            }

            int topY = waterTop != Integer.MIN_VALUE ? waterTop : y;
            this.height[at] = topY;
            int biomeTop = this.biomes ? this.biomeTop(sections, x, topY, z, 0L) : 0;
            if (biomeTop != 0) {
               this.top[at] = Look.is(look, Look.LEAVES) ? this.biomeTop(sections, x, topY, z, look) : biomeTop;
               this.finishDetailed(sections, at, x, z, topY, 0L, false);
               return;
            }

            this.top[at] = Surface.top(look);
            this.water[at] = waterTop != Integer.MIN_VALUE ? Math.min(254, waterTop - y) : 0;
            this.finishDetailed(sections, at, x, z, topY, coverLook, coverDry && waterTop != Integer.MIN_VALUE);
            return;
         }
      }

      int deep = this.biomes && waterTop != Integer.MIN_VALUE ? this.biomeTop(sections, x, waterTop, z, 0L) : 0;
      if (deep != 0) {
         this.height[at] = waterTop;
         this.top[at] = deep;
         this.finishDetailed(sections, at, x, z, waterTop, 0L, false);
      } else if (waterTop != Integer.MIN_VALUE) {
         this.height[at] = waterTop;
         this.water[at] = 255;
         this.finishDetailed(sections, at, x, z, waterTop, coverLook, coverDry);
      } else if (coverLook != 0L) {
         this.height[at] = coverY;
         this.finishDetailed(sections, at, x, z, coverY, coverLook, false);
      }
   }

   private void finishDetailed(List<ChunkDecoder.Section> sections, int at, int x, int z, int topY, long coverLook, boolean above) {
      if (coverLook != 0L) {
         this.cover[at] = Surface.cover(coverLook, above);
      }

      this.biome[at] = this.biomeIdAt(sections, x, topY, z);
      if (this.night) {
         this.light[at] = (byte)this.blockLightAt(sections, x, topY + 1, z);
      }
   }

   private int biomeTop(List<ChunkDecoder.Section> sections, int x, int y, int z, long look) {
      String name = BiomeIndex.name(this.biomeIdAt(sections, x, y, z));
      int c = name == null ? 0 : BiomePalette.colour(name);
      return c == 0 ? (look == 0L ? 0 : Surface.top(look)) : Surface.top(Look.of(c, 255, Look.TINT_NONE, Look.is(look, Look.LEAVES) ? Look.LEAVES : 0L, 0));
   }

   private int[] oldBiomes;

   private void readOldBiomes(Map<String, Object> level) {
      this.oldBiomes = null;
      if (!this.biomes && !this.detailed) {
         return;
      }

      Object b = level.get("Biomes");
      if (b instanceof int[] ints) {
         this.oldBiomes = ints;
      } else if (b instanceof byte[] bytes) {
         int[] numbers = new int[bytes.length];
         for (int i = 0; i < bytes.length; i++) {
            numbers[i] = bytes[i] & 255;
         }

         this.oldBiomes = numbers;
      }
   }

   private short oldBiome(int x, int y, int z) {
      int[] numbers = this.oldBiomes;
      if (numbers == null) {
         return -1;
      }

      int n;
      if (numbers.length == 256) {
         n = numbers[z * 16 + x];
      } else if (numbers.length == 1024) {
         n = numbers[(Math.max(0, Math.min(255, y)) >> 2) * 16 + (z >> 2) * 4 + (x >> 2)];
      } else {
         return -1;
      }

      String name = RuleIds.legacyBiome(n);
      return name == null ? -1 : BiomeIndex.of(name);
   }

   private short biomeIdAt(List<ChunkDecoder.Section> sections, int x, int y, int z) {
      int sy = Math.floorDiv(y, 16);
      ChunkDecoder.Section best = null;
      for (ChunkDecoder.Section s : sections) {
         if (s.biomeIds != null) {
            if (s.y == sy) {
               best = s;
               break;
            }

            if (best == null || Math.abs(s.y - sy) < Math.abs(best.y - sy)) {
               best = s;
            }
         }
      }

      if (best == null) {
         return this.oldBiome(x, y, z);
      }

      int ly = best.y == sy ? y & 15 : best.y < sy ? 15 : 0;
      int cell = (ly >> 2) << 4 | (z >> 2) << 2 | x >> 2;
      int p = 0;
      if (best.biomeData != null && best.biomeBits > 0) {
         int per = 64 / best.biomeBits;
         long word = best.biomeData[Math.min(best.biomeData.length - 1, cell / per)];
         p = (int)(word >>> cell % per * best.biomeBits & (1L << best.biomeBits) - 1L);
      }

      return p < best.biomeIds.length ? best.biomeIds[p] : -1;
   }

   private int blockLightAt(List<ChunkDecoder.Section> sections, int x, int y, int z) {
      int sy = Math.floorDiv(y, 16);
      for (ChunkDecoder.Section s : sections) {
         if (s.y == sy) {
            return s.blockLight == null ? 0 : nibble(s.blockLight, (y & 15) << 8 | z << 4 | x);
         }
      }

      return 0;
   }

   private long lookAt(ChunkDecoder.Section s, int index) {
      if (s.blocks != null) {
         int id = s.blocks[index] & 255;
         if (s.add != null) {
            id |= nibble(s.add, index) << 8;
         }

         if (id == 0) {
            return 0L;
         }

         return this.legacyLook(id, s.meta == null ? 0 : nibble(s.meta, index));
      }

      int p = get(s, index);
      return s.looks == null || p >= s.looks.length ? 0L : s.looks[p];
   }

   private long legacyLook(int id, int data) {
      if (this.legacyLooks == null) {
         this.legacyLooks = new long[65536];
         Arrays.fill(this.legacyLooks, -1L);
      }

      int key = (id & 4095) << 4 | data & 15;
      long v = this.legacyLooks[key];
      if (v == -1L) {
         String name = BlockColours.legacyName(id, data);
         v = name == null ? Look.fromColour("legacy:" + id, BlockColours.neutral("legacy:" + id)) : this.lookOf(name);
         this.legacyLooks[key] = v;
      }

      return v;
   }

   private long lookOf(String name) {
      Long v = this.looks.get(name);
      if (v == null) {
         v = this.colours.look(name);
         this.looks.put(name, v);
      }

      return v;
   }

   private void columnTop(List<ChunkDecoder.Section> sections, int x, int z, int startY) {
      int at = z * 16 + x;
      int waterTop = Integer.MIN_VALUE;
      int waterColour = 0;
      int si = 0;

      for (int n = sections.size(); si < n; si++) {
         ChunkDecoder.Section s = sections.get(si);
         int base = s.y * 16;
         if (base <= startY) {
            int from = Math.min(15, startY - base);

            for (int ly = from; ly >= 0; ly--) {
               int index = ly << 8 | z << 4 | x;
               int c;
               boolean wet;
               if (s.blocks != null) {
                  int id = s.blocks[index] & 255;
                  if (s.add != null) {
                     id |= nibble(s.add, index) << 8;
                  }

                  if (id == 0) {
                     continue;
                  }

                  int packed = this.legacy(id, s.meta == null ? 0 : nibble(s.meta, index));
                  c = packed | 0xFF000000;
                  wet = (packed & 0xFF000000) == 16777216;
                  if ((packed & 0xFF000000) == 0) {
                     continue;
                  }
               } else {
                  int p = get(s, index);
                  if (p >= s.colours.length) {
                     continue;
                  }

                  c = s.colours[p];
                  if (c == 0) {
                     continue;
                  }

                  wet = s.wet[p];
               }

               if (this.dry && wet) {
                  continue;
               }

               int y = base + ly;
               if (!wet) {
                  if (waterTop != Integer.MIN_VALUE) {
                     this.colour[at] = waterColour;
                     this.height[at] = waterTop;
                     this.water[at] = Math.min(255, waterTop - y);
                  } else {
                     this.colour[at] = c;
                     this.height[at] = y;
                  }

                  return;
               }

               if (waterTop == Integer.MIN_VALUE) {
                  waterTop = y;
                  waterColour = c;
               }
            }
         }
      }

      if (waterTop != Integer.MIN_VALUE) {
         this.colour[at] = waterColour;
         this.height[at] = waterTop;
         this.water[at] = 255;
      }
   }

   private int caveStart(List<ChunkDecoder.Section> sections, int x, int z) {
      if (this.caveRoof < 0) {
         return this.caveY;
      }

      int y = this.caveY;
      int roof = this.caveY - this.caveRoof;
      while (y > roof && this.solid(sections, x, y, z)) {
         y--;
      }

      return this.solid(sections, x, y, z) ? Integer.MIN_VALUE : y;
   }

   private boolean solid(List<ChunkDecoder.Section> sections, int x, int y, int z) {
      int sy = Math.floorDiv(y, 16);
      for (ChunkDecoder.Section s : sections) {
         if (s.y != sy) {
            continue;
         }

         int index = (y & 15) << 8 | z << 4 | x;
         if (s.blocks != null) {
            int id = s.blocks[index] & 255;
            if (s.add != null) {
               id |= nibble(s.add, index) << 8;
            }

            if (id == 0) {
               return false;
            }

            int packed = this.legacy(id, s.meta == null ? 0 : nibble(s.meta, index));
            return (packed & 0xFF000000) != 0 && (packed & 0xFF000000) != 16777216;
         }

         int p = get(s, index);
         return p < s.colours.length && s.colours[p] != 0 && !s.wet[p];
      }

      return false;
   }

   private static int nibble(byte[] a, int index) {
      int b = a[index >> 1] & 255;
      return (index & 1) == 0 ? b & 15 : b >> 4;
   }

   private static int get(ChunkDecoder.Section s, int index) {
      if (s.data == null) {
         return 0;
      }

      long mask = (1L << s.bits) - 1L;
      if (s.aligned) {
         return (int)(s.data[index / s.perWord] >>> index % s.perWord * s.bits & mask);
      }

      int bit = index * s.bits;
      int word = bit >>> 6;
      int off = bit & 63;
      long v = s.data[word] >>> off;
      if (off + s.bits > 64) {
         v |= s.data[word + 1] << 64 - off;
      }

      return (int)(v & mask);
   }

   private int legacy(int id, int data) {
      if (this.legacy == null) {
         this.legacy = new int[65536];
         Arrays.fill(this.legacy, 33554432);
      }

      int key = (id & 4095) << 4 | data & 15;
      int v = this.legacy[key];
      if (v == 33554432) {
         String name = BlockColours.legacyName(id, data);
         int[] c = name == null ? new int[]{BlockColours.neutral("legacy:" + id), 0} : this.name(name);
         v = c[0] == 0 ? 0 : c[0] & 16777215 | (c[1] != 0 ? 16777216 : -16777216);
         this.legacy[key] = v;
      }

      return v;
   }

   private int[] name(String name) {
      int[] c = this.names.get(name);
      if (c == null) {
         int colour = this.colours.colour(name);
         boolean wet = name.equals("minecraft:water")
            || name.equals("minecraft:flowing_water")
            || name.equals("minecraft:bubble_column")
            || name.endsWith("seagrass")
            || name.equals("minecraft:kelp")
            || name.equals("minecraft:kelp_plant");
         if (wet && colour == 0) {
            colour = -12566273;
         }

         c = new int[]{colour, wet ? 1 : 0};
         this.names.put(name, c);
      }

      return c;
   }

   static String blockName(Object entry) {
      if (entry instanceof String s) {
         return s;
      } else if (entry instanceof Map<?, ?> m) {
         Object v = m.get("Name");
         if (!(v instanceof String)) {
            v = m.get("id");
         }

         if (!(v instanceof String)) {
            v = m.get("");
         }

         return v instanceof String s ? s : null;
      } else {
         return null;
      }
   }

   private ChunkDecoder.Section section(Object o, int dataVersion) {
      if (!(o instanceof Map)) {
         return null;
      }

      ChunkDecoder.Section s = new ChunkDecoder.Section();
      s.y = (int)Nbt.number(o, "Y", 0L);
      if (this.night && ((Map)o).get("BlockLight") instanceof byte[] light && light.length >= 2048) {
         s.blockLight = light;
      }

      if (this.biomes || this.detailed) {
         Map<String, Object> b = Nbt.map(o, "biomes");
         List<Object> names = b == null ? null : Nbt.list(b, "palette");
         if (names != null && !names.isEmpty()) {
            s.biomeColours = new int[names.size()];
            s.biomeIds = new short[names.size()];
            for (int i = 0; i < names.size(); i++) {
               s.biomeColours[i] = names.get(i) instanceof String id ? BiomePalette.colour(id) : 0;
               s.biomeIds[i] = names.get(i) instanceof String id ? BiomeIndex.of(id) : -1;
            }

            if (b.get("data") instanceof long[] words && words.length > 0) {
               s.biomeData = words;
               s.biomeBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(names.size() - 1));
            }
         }
      }
      if (((Map)o).get("Blocks") instanceof byte[] b) {
         if (b.length < 4096) {
            return null;
         }

         s.blocks = b;
         s.add = ((Map)o).get("Add") instanceof byte[] a && a.length >= 2048 ? a : null;
         s.meta = ((Map)o).get("Data") instanceof byte[] d && d.length >= 2048 ? d : null;
         return s;
      } else {
         Map<String, Object> states = Nbt.map(o, "block_states");
         List<Object> palette;
         Object data;
         if (states != null) {
            palette = Nbt.list(states, "palette");
            data = states.get("data");
         } else {
            palette = Nbt.list(o, "Palette");
            data = ((Map)o).get("BlockStates");
         }

         if (palette != null && !palette.isEmpty()) {
            int n = palette.size();
            s.colours = new int[n];
            s.wet = new boolean[n];
            if (this.detailed) {
               s.looks = new long[n];
            }

            boolean any = false;

            for (int i = 0; i < n; i++) {
               String name = blockName(palette.get(i));
               int[] c = name == null ? new int[]{0, 0} : this.name(name);
               s.colours[i] = c[0];
               s.wet[i] = c[1] != 0;
               any |= c[0] != 0;
               if (s.looks != null && name != null) {
                  s.looks[i] = this.lookOf(name);
                  any |= s.looks[i] != 0L;
               }
            }

            if (!any) {
               return null;
            }

            if (data instanceof long[] words && words.length != 0) {
               int[] packing = packing(n, words.length, dataVersion);
               if (packing == null) {
                  return null;
               }

               s.bits = packing[0];
               s.aligned = packing[1] != 0;
               s.perWord = 64 / s.bits;
               s.data = words;
               return s;
            } else {
               return n == 1 ? s : null;
            }
         } else {
            return null;
         }
      }
   }

   static int[] packing(int paletteSize, int words, int dataVersion) {
      int need = Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
      int alignedLength = (4096 + 64 / need - 1) / (64 / need);
      int runOnLength = 64 * need;
      if (words == alignedLength && !(alignedLength == runOnLength && dataVersion < 2529 && 64 % need != 0)) {
         return new int[]{need, 1};
      }

      if (words == runOnLength) {
         return new int[]{need, 0};
      }

      if (dataVersion > 0 && dataVersion < 2529 && words % 64 == 0 && words / 64 >= need && words / 64 <= 32) {
         return new int[]{words / 64, 0};
      }

      for (int b = need; b <= 32; b++) {
         if ((4096 + 64 / b - 1) / (64 / b) == words) {
            return new int[]{b, 1};
         }
      }

      return null;
   }

   static int unpack(long[] data, int bits, boolean aligned, int index) {
      long mask = (1L << bits) - 1L;
      if (aligned) {
         int perWord = 64 / bits;
         return (int)(data[index / perWord] >>> index % perWord * bits & mask);
      }

      int bit = index * bits;
      int word = bit >>> 6;
      int off = bit & 63;
      long v = data[word] >>> off;
      if (off + bits > 64) {
         v |= data[word + 1] << 64 - off;
      }

      return (int)(v & mask);
   }

   private static int[] surface(Map<String, Object> level, List<ChunkDecoder.Section> sections) {
      Map<String, Object> maps = Nbt.map(level, "Heightmaps");
      if (!((maps == null ? null : maps.get("WORLD_SURFACE")) instanceof long[] words)) {
         return null;
      } else {
         int lowest = sections.get(sections.size() - 1).y * 16;
         int minY = Nbt.has(level, "yPos") ? (int)Nbt.number(level, "yPos", 0L) * 16 : Math.min(0, lowest);
         int ceiling = sections.get(0).y * 16 + 15;
         boolean aligned;
         if (words.length == 37) {
            aligned = true;
         } else {
            if (words.length != 36) {
               return null;
            }

            aligned = false;
         }

         int[] out = new int[256];

         for (int i = 0; i < 256; i++) {
            int value;
            if (aligned) {
               value = (int)(words[i / 7] >>> i % 7 * 9 & 511L);
            } else {
               int bit = i * 9;
               int off = bit & 63;
               long w = words[bit >>> 6] >>> off;
               if (off + 9 > 64) {
                  w |= words[(bit >>> 6) + 1] << 64 - off;
               }

               value = (int)(w & 511L);
            }

            int y = value - 1 + minY;
            out[i] = value > 0 && y <= ceiling ? y : Integer.MAX_VALUE;
         }

         return out;
      }
   }

   static {
      HashSet<String> s = new HashSet<>(SKIP);
      s.addAll(Set.of("block_states", "BlockStates", "Palette", "Blocks", "Add", "Data", "Heightmaps"));
      SKIP_BLOCKS = Set.copyOf(s);
      HashSet<String> b = new HashSet<>(SKIP);
      b.remove("biomes");
      b.remove("Biomes");
      SKIP_BUT_BIOMES = Set.copyOf(b);
      b.remove("BlockLight");
      SKIP_BUT_BIOMES_AND_LIGHT = Set.copyOf(b);
   }

   public record Facts(
      int chunkX, int chunkZ, int dataVersion, String status, boolean fullyGenerated, long inhabitedTicks, int sections, int blockEntities, int entities
   ) {
   }

   private static final class Section {
      int y;
      int[] colours;
      boolean[] wet;
      long[] data;
      int bits;
      int perWord;
      boolean aligned;
      byte[] blocks;
      byte[] add;
      byte[] meta;
      int[] biomeColours;
      short[] biomeIds;
      long[] looks;
      byte[] blockLight;
      long[] biomeData;
      int biomeBits;
   }
}
