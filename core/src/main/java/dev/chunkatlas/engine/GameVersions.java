package dev.chunkatlas.engine;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;

public final class GameVersions {
   private static final ConcurrentSkipListMap<Integer, String> BY_NUMBER = new ConcurrentSkipListMap<>();
   private static final int KNOWN_UP_TO = 4556;

   private GameVersions() {
   }

   public static void learn(String name, int dataVersion) {
      if (name != null && !name.isBlank() && dataVersion > 0) {
         BY_NUMBER.putIfAbsent(dataVersion, name.trim());
      }
   }

   public static Optional<String> exact(int dataVersion) {
      return Optional.ofNullable(BY_NUMBER.get(dataVersion));
   }

   public static Message describe(int dataVersion) {
      if (dataVersion <= 0) {
         return Message.of("chunkatlas.version.before_1_9");
      }

      String exact = BY_NUMBER.get(dataVersion);
      if (exact != null) {
         return Message.literal(exact);
      }

      Entry<Integer, String> below = BY_NUMBER.floorEntry(dataVersion);
      return below != null && dataVersion <= 4556
         ? Message.of("chunkatlas.version.snapshot_after", below.getValue())
         : Message.of("chunkatlas.version.data", String.valueOf(dataVersion));
   }

   public static OptionalInt parse(String typed) {
      String t = typed == null ? "" : typed.trim();
      if (t.isEmpty()) {
         return OptionalInt.empty();
      }

      for (Entry<Integer, String> e : BY_NUMBER.entrySet()) {
         if (e.getValue().equalsIgnoreCase(t)) {
            return OptionalInt.of(e.getKey());
         }
      }

      return t.matches("\\d{3,6}") ? OptionalInt.of(Integer.parseInt(t)) : OptionalInt.empty();
   }

   public static Map<Integer, String> known() {
      return new TreeMap<>(BY_NUMBER);
   }

   static {
      Object[] known = new Object[]{
         169,
         "1.9",
         175,
         "1.9.1",
         176,
         "1.9.2",
         183,
         "1.9.3",
         184,
         "1.9.4",
         510,
         "1.10",
         511,
         "1.10.1",
         512,
         "1.10.2",
         819,
         "1.11",
         921,
         "1.11.1",
         922,
         "1.11.2",
         1139,
         "1.12",
         1241,
         "1.12.1",
         1343,
         "1.12.2",
         1519,
         "1.13",
         1628,
         "1.13.1",
         1631,
         "1.13.2",
         1952,
         "1.14",
         1957,
         "1.14.1",
         1963,
         "1.14.2",
         1968,
         "1.14.3",
         1976,
         "1.14.4",
         2225,
         "1.15",
         2227,
         "1.15.1",
         2230,
         "1.15.2",
         2566,
         "1.16",
         2567,
         "1.16.1",
         2578,
         "1.16.2",
         2580,
         "1.16.3",
         2584,
         "1.16.4",
         2586,
         "1.16.5",
         2724,
         "1.17",
         2730,
         "1.17.1",
         2860,
         "1.18",
         2865,
         "1.18.1",
         2975,
         "1.18.2",
         3105,
         "1.19",
         3117,
         "1.19.1",
         3120,
         "1.19.2",
         3218,
         "1.19.3",
         3337,
         "1.19.4",
         3463,
         "1.20",
         3465,
         "1.20.1",
         3578,
         "1.20.2",
         3698,
         "1.20.3",
         3700,
         "1.20.4",
         3837,
         "1.20.5",
         3839,
         "1.20.6",
         3953,
         "1.21",
         3955,
         "1.21.1",
         4080,
         "1.21.2",
         4082,
         "1.21.3",
         4189,
         "1.21.4",
         4325,
         "1.21.5",
         4435,
         "1.21.6",
         4438,
         "1.21.7",
         4440,
         "1.21.8",
         4554,
         "1.21.9",
         4556,
         "1.21.10",
         5023,
         "26.3"
      };

      for (int i = 0; i < known.length; i += 2) {
         BY_NUMBER.put((Integer)known[i], (String)known[i + 1]);
      }
   }
}
