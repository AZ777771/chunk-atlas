package dev.chunkatlas.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public record ChunkFilter(List<ChunkFilter.Condition> conditions, boolean matchAll, int inhabitedBelow, int buffer) {
   public static final int MAX_CONDITIONS = 12;
   private static final String FORMAT = "f1";
   private static final long MAX_VALUE = 1_000_000_000L;
   private static final long TICKS_PER_MINUTE = 1200L;
   private static final long SECONDS_PER_DAY = 86400L;

   public ChunkFilter {
      List<ChunkFilter.Condition> list = conditions == null ? List.of() : conditions;
      conditions = List.copyOf(list.size() > MAX_CONDITIONS ? list.subList(0, MAX_CONDITIONS) : list);
      inhabitedBelow = Math.max(0, inhabitedBelow);
      buffer = Math.max(0, Math.min(64, buffer));
   }

   public ChunkFilter(List<ChunkFilter.Condition> conditions, boolean matchAll) {
      this(conditions, matchAll, 0, 0);
   }

   public static ChunkFilter empty() {
      return new ChunkFilter(List.of(), true);
   }

   public boolean isEmpty() {
      return this.conditions.isEmpty() && this.inhabitedBelow == 0;
   }

   public ChunkFilter withConditions(List<ChunkFilter.Condition> list) {
      return new ChunkFilter(list, this.matchAll, this.inhabitedBelow, this.buffer);
   }

   public ChunkFilter withMatchAll(boolean all) {
      return new ChunkFilter(this.conditions, all, this.inhabitedBelow, this.buffer);
   }

   public ChunkFilter withBuffer(int chunks) {
      return new ChunkFilter(this.conditions, this.matchAll, this.inhabitedBelow, chunks);
   }

   public ChunkRule gate() {
      return this.inhabitedBelow == 0 ? null : ChunkRule.inhabitedAtMost(this.inhabitedBelow * TICKS_PER_MINUTE - 1L);
   }

   public List<Integer> groups() {
      List<Integer> out = new ArrayList<>();
      for (ChunkFilter.Condition c : this.conditions) {
         if (c.group() > 0 && !out.contains(c.group())) {
            out.add(c.group());
         }
      }

      return out;
   }

   public boolean deep() {
      return this.conditions.stream().anyMatch(c -> c.field().deep());
   }

   public ChunkRule toRule(ChunkFilter.Context ctx) {
      ChunkRule gate = this.gate();
      if (this.conditions.isEmpty()) {
         return gate == null ? ChunkRule.none() : gate;
      }

      ChunkRule rule = this.tree(ctx).rule(ChunkFilter.Mode.SURE);
      return gate == null ? rule : ChunkRule.all(List.of(rule, gate));
   }

   public ChunkFilter.Search toSearch(ChunkFilter.Context ctx) {
      boolean blocks = this.conditions.stream().anyMatch(c -> c.field() == ChunkFilter.Field.BLOCK);
      boolean biomes = this.conditions.stream().anyMatch(c -> c.field() == ChunkFilter.Field.BIOME);
      boolean structures = this.conditions.stream().anyMatch(c -> c.field() == ChunkFilter.Field.STRUCTURE);
      return new ChunkFilter.Search(this.tree(ctx), new ChunkContents.Need(blocks, biomes, structures));
   }

   private ChunkFilter.Node tree(ChunkFilter.Context ctx) {
      Map<Integer, ChunkFilter.Node> groups = new LinkedHashMap<>();
      List<ChunkFilter.Node> top = new ArrayList<>();
      for (ChunkFilter.Condition c : this.conditions) {
         ChunkFilter.Node leaf = ChunkFilter.Node.leaf(c, c.field().deep() ? null : c.toRule(ctx));
         if (c.group() == 0) {
            top.add(leaf);
         } else {
            groups.computeIfAbsent(c.group(), k -> {
               ChunkFilter.Node group = ChunkFilter.Node.branch(true);
               top.add(group);
               return group;
            }).kids.add(leaf);
         }
      }

      ChunkFilter.Node root = ChunkFilter.Node.branch(!this.matchAll);
      root.kids.addAll(top);
      return root;
   }

   private enum Mode {
      MAYBE,
      SURE,
      FULL;
   }

   private static final class Node {
      final ChunkFilter.Condition cond;
      final ChunkRule quick;
      final boolean any;
      final List<ChunkFilter.Node> kids = new ArrayList<>();

      private Node(ChunkFilter.Condition cond, ChunkRule quick, boolean any) {
         this.cond = cond;
         this.quick = quick;
         this.any = any;
      }

      static ChunkFilter.Node leaf(ChunkFilter.Condition c, ChunkRule quick) {
         return new ChunkFilter.Node(c, quick, false);
      }

      static ChunkFilter.Node branch(boolean any) {
         return new ChunkFilter.Node(null, null, any);
      }

      boolean eval(ChunkFilter.Mode mode, ChunkContents c, int x, int z, RegionFacts f, int i) {
         if (this.cond != null) {
            if (this.quick != null) {
               return this.quick.matches(x, z, f, i);
            }

            return switch (mode) {
               case MAYBE -> true;
               case SURE -> false;
               case FULL -> c != null && !c.tooOld() && this.cond.test(c);
            };
         }

         for (ChunkFilter.Node k : this.kids) {
            if (k.eval(mode, c, x, z, f, i) == this.any) {
               return this.any;
            }
         }

         return !this.any;
      }

      boolean needsFacts() {
         if (this.cond != null) {
            return this.quick != null && this.quick.needsFacts();
         }

         return this.kids.stream().anyMatch(ChunkFilter.Node::needsFacts);
      }

      boolean deep() {
         return this.cond != null ? this.quick == null : this.kids.stream().anyMatch(ChunkFilter.Node::deep);
      }

      ChunkRule rule(ChunkFilter.Mode mode) {
         boolean facts = this.needsFacts();
         return new ChunkRule() {
            @Override
            public boolean matches(int x, int z, RegionFacts f, int i) {
               return ChunkFilter.Node.this.eval(mode, null, x, z, f, i);
            }

            @Override
            public boolean needsFacts() {
               return facts;
            }
         };
      }
   }

   public static final class Search {
      private final ChunkFilter.Node root;
      private final ChunkContents.Need need;

      private Search(ChunkFilter.Node root, ChunkContents.Need need) {
         this.root = root;
         this.need = need;
      }

      public ChunkRule maybe() {
         return this.root.rule(ChunkFilter.Mode.MAYBE);
      }

      public ChunkRule sure() {
         return this.root.rule(ChunkFilter.Mode.SURE);
      }

      public boolean needsFacts() {
         return this.root.needsFacts();
      }

      public boolean deep() {
         return this.root.deep();
      }

      public ChunkContents.Need need() {
         return this.need;
      }

      public boolean matches(ChunkContents c, int x, int z, RegionFacts facts, int i) {
         return this.root.eval(ChunkFilter.Mode.FULL, c, x, z, facts, i);
      }
   }

   public static String opKey(ChunkFilter.Field field, ChunkFilter.Op op) {
      return field.deep() ? "chunkatlas.rules.op." + field.key() + "." + op.key() : "chunkatlas.filter.op." + op.key();
   }

   public static String opLongKey(ChunkFilter.Field field, ChunkFilter.Op op) {
      return field.deep() ? "chunkatlas.rules.op." + field.key() + "." + op.key() : "chunkatlas.filter.op." + op.key() + ".long";
   }

   public Message describe() {
      if (this.conditions.isEmpty()) {
         return Message.of("chunkatlas.filter.say.nothing");
      }

      Message first = this.conditions.get(0).describe();
      if (this.conditions.size() == 1) {
         return first;
      }

      String mode = this.matchAll ? "all" : "any";
      if (this.conditions.size() == 2) {
         return Message.of("chunkatlas.filter.say.join." + mode, first, this.conditions.get(1).describe());
      }

      int more = this.conditions.size() - 1;
      return Message.of("chunkatlas.filter.say.more." + mode + (more == 1 ? ".one" : ".many"), first, more);
   }

   public String encode() {
      StringBuilder sb = new StringBuilder(FORMAT).append(';').append(this.matchAll ? "all" : "any");
      if (this.inhabitedBelow > 0) {
         sb.append(";below=").append(this.inhabitedBelow);
      }

      if (this.buffer > 0) {
         sb.append(";buffer=").append(this.buffer);
      }

      int group = 0;
      for (ChunkFilter.Condition c : this.conditions) {
         if (c.group() != group) {
            group = c.group();
            sb.append(";group=").append(group);
         }

         sb.append(';').append(c.field().name()).append(':').append(c.op().name()).append(':').append(c.value());
         if (c.field().deep()) {
            sb.append(':').append(c.id());
         }
      }

      return sb.toString();
   }

   public static ChunkFilter decode(String text) {
      if (text == null || text.isBlank()) {
         return empty();
      }

      String[] parts = text.trim().split(";");
      if (parts.length < 2 || !parts[0].equals(FORMAT)) {
         return empty();
      }

      boolean all = !parts[1].equals("any");
      List<ChunkFilter.Condition> list = new ArrayList<>();
      int below = 0;
      int buffer = 0;
      int group = 0;

      for (int n = 2; n < parts.length && list.size() < MAX_CONDITIONS; n++) {
         try {
            if (parts[n].startsWith("below=")) {
               below = Integer.parseInt(parts[n].substring(6));
               continue;
            } else if (parts[n].startsWith("buffer=")) {
               buffer = Integer.parseInt(parts[n].substring(7));
               continue;
            } else if (parts[n].startsWith("group=")) {
               group = Math.max(0, Integer.parseInt(parts[n].substring(6)));
               continue;
            }
         } catch (NumberFormatException e) {
            continue;
         }

         String[] f = parts[n].split(":", 4);
         if (f.length == 3 || f.length == 4) {
            try {
               ChunkFilter.Field field = ChunkFilter.Field.valueOf(f[0]);
               if (field.deep() == (f.length == 4)) {
                  list.add(new ChunkFilter.Condition(field, ChunkFilter.Op.valueOf(f[1]), Long.parseLong(f[2]), f.length == 4 ? f[3] : "", group));
               }
            } catch (IllegalArgumentException ignored) {
            }
         }
      }

      return new ChunkFilter(list, all, below, buffer);
   }

   private static String number(long n) {
      return String.format(Locale.ROOT, "%,d", n);
   }

   private static double distance(int chunkX, int chunkZ, double blockX, double blockZ) {
      double dx = Math.max(Math.max(chunkX * 16.0 - blockX, blockX - (chunkX * 16.0 + 16.0)), 0.0);
      double dz = Math.max(Math.max(chunkZ * 16.0 - blockZ, blockZ - (chunkZ * 16.0 + 16.0)), 0.0);
      return Math.sqrt(dx * dx + dz * dz);
   }

   private static boolean compare(ChunkFilter.Op op, long a, long v) {
      return switch (op) {
         case EQUALS -> a == v;
         case NOT_EQUALS -> a != v;
         case LESS -> a < v;
         case AT_MOST -> a <= v;
         case MORE -> a > v;
         case AT_LEAST -> a >= v;
         default -> false;
      };
   }

   private static boolean compareScaled(ChunkFilter.Op op, long fine, long v, long perUnit) {
      return op == ChunkFilter.Op.EQUALS || op == ChunkFilter.Op.NOT_EQUALS ? compare(op, fine / perUnit, v) : compare(op, fine, v * perUnit);
   }

   private static boolean compare(ChunkFilter.Op op, double a, long v) {
      return switch (op) {
         case EQUALS -> (long)Math.floor(a) == v;
         case NOT_EQUALS -> (long)Math.floor(a) != v;
         case LESS -> a < v;
         case AT_MOST -> a <= v;
         case MORE -> a > v;
         case AT_LEAST -> a >= v;
         default -> false;
      };
   }

   public enum Field {
      TIME_PLAYED("time_played", "minutes"),
      LAST_SAVED("last_saved", "days"),
      CHESTS("chests", ""),
      MOBS("mobs", ""),
      DISTANCE_SPAWN("distance_spawn", "blocks"),
      DISTANCE_PLAYER("distance_player", "blocks"),
      NOT_FINISHED("not_finished", null),
      OLDER_VERSION("older_version", null),
      BLOCK("block", ""),
      BIOME("biome", ""),
      STRUCTURE("structure", "");

      private static final List<ChunkFilter.Op> NUMBER_OPS = List.of(
         ChunkFilter.Op.EQUALS, ChunkFilter.Op.NOT_EQUALS, ChunkFilter.Op.LESS, ChunkFilter.Op.AT_MOST, ChunkFilter.Op.MORE, ChunkFilter.Op.AT_LEAST
      );
      private final String key;
      private final String unit;

      Field(String key, String unit) {
         this.key = key;
         this.unit = unit;
      }

      public String key() {
         return this.key;
      }

      public String langKey() {
         return (this.deep() ? "chunkatlas.rules.field." : "chunkatlas.filter.field.") + this.key;
      }

      public boolean deep() {
         return this == BLOCK || this == BIOME || this == STRUCTURE;
      }

      public boolean takesNumber(ChunkFilter.Op op) {
         return this.deep() ? op == ChunkFilter.Op.AT_LEAST : !this.flag();
      }

      public boolean flag() {
         return this.unit == null;
      }

      public String unit() {
         return this.unit;
      }

      public List<ChunkFilter.Op> ops() {
         if (this == BLOCK) {
            return List.of(ChunkFilter.Op.HAS, ChunkFilter.Op.LACKS, ChunkFilter.Op.AT_LEAST);
         }

         if (this.deep()) {
            return List.of(ChunkFilter.Op.HAS, ChunkFilter.Op.LACKS);
         }

         return this.flag() ? List.of(ChunkFilter.Op.IS, ChunkFilter.Op.IS_NOT) : NUMBER_OPS;
      }

      public ChunkFilter.Op defaultOp() {
         return this == TIME_PLAYED ? ChunkFilter.Op.AT_MOST : this.flag() || this.deep() ? this.ops().get(0) : ChunkFilter.Op.AT_LEAST;
      }
   }

   public enum Op {
      AT_LEAST,
      AT_MOST,
      IS,
      IS_NOT,
      HAS,
      LACKS,
      EQUALS,
      NOT_EQUALS,
      LESS,
      MORE;

      public String key() {
         return this.name().toLowerCase(Locale.ROOT);
      }
   }

   public record Condition(ChunkFilter.Field field, ChunkFilter.Op op, long value, String id, int group) {
      public Condition {
         if (field == null) {
            field = ChunkFilter.Field.TIME_PLAYED;
         }

         if (op == null || !field.ops().contains(op)) {
            op = field.ops().get(0);
         }

         value = !field.takesNumber(op) ? 0L : Math.max(field.deep() ? 1L : 0L, Math.min(MAX_VALUE, value));
         id = field.deep() ? RuleIds.normalize(id) : "";
         group = Math.max(0, group);
      }

      public Condition(ChunkFilter.Field field, ChunkFilter.Op op, long value, String id) {
         this(field, op, value, id, 0);
      }

      public Condition(ChunkFilter.Field field, ChunkFilter.Op op, long value) {
         this(field, op, value, "", 0);
      }

      public ChunkFilter.Condition withGroup(int g) {
         return new ChunkFilter.Condition(this.field, this.op, this.value, this.id, g);
      }

      public Message describe() {
         if (this.field.deep()) {
            String key = "chunkatlas.rules.say." + this.field.key() + "." + this.op.key();
            Message name = RuleIds.describe(this.id);
            return this.op == ChunkFilter.Op.AT_LEAST ? Message.of(key, number(this.value), name) : Message.of(key, name);
         }

         String key = "chunkatlas.filter.say." + this.field.key() + "." + this.op.key();
         return this.field.flag() ? Message.of(key) : Message.of(key, number(this.value));
      }

      public boolean test(ChunkContents c) {
         if (!this.field.deep() || this.id.isEmpty()) {
            return false;
         }

         String wanted = this.id;
         return switch (this.field) {
            case BLOCK -> {
               Predicate<String> name = b -> RuleIds.matches(wanted, b, true);
               yield switch (this.op) {
                  case LACKS -> !c.hasBlock(name);
                  case AT_LEAST -> c.hasBlock(name) && c.countBlocks(name, this.value) >= this.value;
                  default -> c.hasBlock(name);
               };
            }
            case BIOME -> c.hasBiome(b -> RuleIds.matches(wanted, b, false)) != (this.op == ChunkFilter.Op.LACKS);
            case STRUCTURE -> c.hasStructure(s -> RuleIds.matches(wanted, s, false)) != (this.op == ChunkFilter.Op.LACKS);
            default -> false;
         };
      }

      ChunkRule toRule(ChunkFilter.Context ctx) {
         boolean yes = this.op == ChunkFilter.Op.IS;
         ChunkFilter.Op op = this.op;
         long v = this.value;
         return switch (this.field) {
            case TIME_PLAYED -> (x, z, f, i) -> ChunkRule.readable(f, i) && compareScaled(op, f.inhabited[i], v, TICKS_PER_MINUTE);
            case LAST_SAVED -> {
               long now = ctx.nowEpochSeconds();
               yield (x, z, f, i) -> {
                  long saved = f == null ? 0L : f.savedAt[i] & 4294967295L;
                  if (!ChunkRule.readable(f, i) || saved <= 0L || now <= 0L) {
                     return false;
                  }

                  return compareScaled(op, Math.max(0L, now - saved), v, SECONDS_PER_DAY);
               };
            }
            case CHESTS -> (x, z, f, i) -> ChunkRule.readable(f, i) && compare(op, f.blockEntities[i], v);
            case MOBS -> (x, z, f, i) -> ChunkRule.readable(f, i) && f.entities[i] >= 0 && compare(op, f.entities[i], v);
            case DISTANCE_SPAWN -> distanceRule(ctx.spawnX(), ctx.spawnZ(), op, v);
            case DISTANCE_PLAYER -> distanceRule(ctx.playerX(), ctx.playerZ(), op, v);
            case NOT_FINISHED -> (x, z, f, i) -> ChunkRule.readable(f, i) && (f.state[i] == RegionFacts.PARTIAL) == yes;
            case BLOCK, BIOME, STRUCTURE -> ChunkRule.none();
            case OLDER_VERSION -> {
               int world = ctx.worldDataVersion();
               if (world <= 0) {
                  yield ChunkRule.none();
               }

               yield (x, z, f, i) -> ChunkRule.readable(f, i) && (f.dataVersion[i] < world) == yes;
            }
         };
      }

      private static ChunkRule distanceRule(Double px, Double pz, ChunkFilter.Op op, long blocks) {
         if (px == null || pz == null) {
            return ChunkRule.none();
         }

         final double bx = px;
         final double bz = pz;
         return new ChunkRule() {
            @Override
            public boolean matches(int x, int z, RegionFacts facts, int i) {
               return compare(op, ChunkFilter.distance(x, z, bx, bz), blocks);
            }

            @Override
            public boolean needsFacts() {
               return false;
            }
         };
      }
   }

   public record Context(Double spawnX, Double spawnZ, Double playerX, Double playerZ, int worldDataVersion, long nowEpochSeconds) {
   }
}
