package dev.chunkatlas.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class ChunkContents {
   public static final int NAMED_BLOCKS_SINCE = 1451;
   private static final Set<String> ALWAYS_SKIP = Set.of(
      "BlockLight",
      "SkyLight",
      "Heightmaps",
      "HeightMap",
      "block_entities",
      "TileEntities",
      "Entities",
      "entities",
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
      "below_zero_retrogen"
   );
   private final boolean tooOld;
   private final List<Section> sections;
   private final Set<String> blocks;
   private final Set<String> biomes;
   private final Set<String> structures;

   private ChunkContents(boolean tooOld, List<Section> sections, Set<String> blocks, Set<String> biomes, Set<String> structures) {
      this.tooOld = tooOld;
      this.sections = sections;
      this.blocks = blocks;
      this.biomes = biomes;
      this.structures = structures;
   }

   public record Need(boolean blocks, boolean biomes, boolean structures) {
      Set<String> skip() {
         Set<String> s = new HashSet<>(ALWAYS_SKIP);
         if (!this.blocks) {
            s.addAll(Set.of("block_states", "BlockStates", "Palette"));
         }

         if (!this.biomes) {
            s.addAll(Set.of("biomes", "Biomes"));
         }

         if (!this.structures) {
            s.addAll(Set.of("structures", "Structures"));
         }

         return Set.copyOf(s);
      }
   }

   public static ChunkContents read(byte[] data, Set<String> skip) throws IOException {
      return of(Nbt.read(data, skip, Set.of()));
   }

   static ChunkContents of(Map<String, Object> root) {
      Map<String, Object> level = Nbt.map(root, "Level");
      if (level == null) {
         level = root;
      }

      int version = (int)Nbt.number(root, "DataVersion", Nbt.number(level, "DataVersion", 0L));
      boolean tooOld = version > 0 && version < NAMED_BLOCKS_SINCE;
      List<Section> sections = new ArrayList<>();
      Set<String> blocks = new HashSet<>();
      Set<String> biomes = new HashSet<>();
      List<Object> raw = Nbt.list(level, "sections");
      if (raw == null) {
         raw = Nbt.list(level, "Sections");
      }

      if (raw != null) {
         for (Object o : raw) {
            if (!(o instanceof Map<?, ?> m)) {
               continue;
            }

            if (m.get("Blocks") instanceof byte[]) {
               tooOld = true;
            }

            Map<String, Object> states = Nbt.map(o, "block_states");
            List<Object> palette = states != null ? Nbt.list(states, "palette") : Nbt.list(o, "Palette");
            Object packed = states != null ? states.get("data") : m.get("BlockStates");
            if (palette != null && !palette.isEmpty()) {
               String[] names = new String[palette.size()];
               for (int i = 0; i < names.length; i++) {
                  String name = ChunkDecoder.blockName(palette.get(i));
                  names[i] = name == null ? "" : name;
                  blocks.add(names[i]);
               }

               sections.add(new Section(names, packed instanceof long[] words ? words : null, version));
            }

            Map<String, Object> sectionBiomes = Nbt.map(o, "biomes");
            List<Object> biomePalette = sectionBiomes == null ? null : Nbt.list(sectionBiomes, "palette");
            if (biomePalette != null) {
               for (Object b : biomePalette) {
                  if (b instanceof String s) {
                     biomes.add(s);
                  }
               }
            }
         }
      }

      if (level.get("Biomes") instanceof int[] numbers) {
         int last = Integer.MIN_VALUE;
         for (int n : numbers) {
            if (n != last) {
               last = n;
               String name = RuleIds.legacyBiome(n);
               if (name != null) {
                  biomes.add(name);
               }
            }
         }
      }

      Set<String> structures = new HashSet<>();
      Map<String, Object> st = Nbt.map(root, "structures");
      if (st == null) {
         st = Nbt.map(level, "Structures");
      }

      if (st != null) {
         Map<String, Object> starts = Nbt.map(st, "starts");
         if (starts == null) {
            starts = Nbt.map(st, "Starts");
         }

         if (starts != null) {
            for (Map.Entry<String, Object> e : starts.entrySet()) {
               String id = Nbt.string(e.getValue(), "id");
               if (id == null || !id.equals("INVALID")) {
                  structures.add(RuleIds.structure(e.getKey()));
               }
            }
         }

         Map<String, Object> refs = Nbt.map(st, "References");
         if (refs != null) {
            for (Map.Entry<String, Object> e : refs.entrySet()) {
               if (e.getValue() instanceof long[] l && l.length > 0) {
                  structures.add(RuleIds.structure(e.getKey()));
               }
            }
         }
      }

      return new ChunkContents(tooOld, sections, blocks, biomes, structures);
   }

   public boolean tooOld() {
      return this.tooOld;
   }

   public boolean hasBlock(Predicate<String> name) {
      for (String b : this.blocks) {
         if (name.test(b)) {
            return true;
         }
      }

      return false;
   }

   public long countBlocks(Predicate<String> name, long enough) {
      long n = 0L;
      for (Section s : this.sections) {
         n += s.count(name);
         if (n >= enough) {
            return n;
         }
      }

      return n;
   }

   public boolean hasBiome(Predicate<String> name) {
      for (String b : this.biomes) {
         if (name.test(b)) {
            return true;
         }
      }

      return false;
   }

   public boolean hasStructure(Predicate<String> name) {
      for (String s : this.structures) {
         if (name.test(s)) {
            return true;
         }
      }

      return false;
   }

   public Set<String> blocks() {
      return Set.copyOf(this.blocks);
   }

   public Set<String> biomes() {
      return Set.copyOf(this.biomes);
   }

   public Set<String> structures() {
      return Set.copyOf(this.structures);
   }

   private record Section(String[] palette, long[] data, int dataVersion) {
      long count(Predicate<String> name) {
         boolean[] wanted = new boolean[this.palette.length];
         boolean any = false;
         for (int i = 0; i < wanted.length; i++) {
            wanted[i] = name.test(this.palette[i]);
            any |= wanted[i];
         }

         if (!any) {
            return 0L;
         }

         if (this.data == null || this.data.length == 0) {
            return this.palette.length == 1 ? 4096L : 0L;
         }

         int[] packing = ChunkDecoder.packing(this.palette.length, this.data.length, this.dataVersion);
         if (packing == null) {
            return 0L;
         }

         long n = 0L;
         boolean aligned = packing[1] != 0;
         for (int i = 0; i < 4096; i++) {
            int p = ChunkDecoder.unpack(this.data, packing[0], aligned, i);
            if (p < wanted.length && wanted[p]) {
               n++;
            }
         }

         return n;
      }
   }
}
