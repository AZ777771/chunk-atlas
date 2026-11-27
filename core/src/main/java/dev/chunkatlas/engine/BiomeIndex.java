package dev.chunkatlas.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class BiomeIndex {
   private static final ConcurrentHashMap<String, Short> NUMBERS = new ConcurrentHashMap<>();
   private static final List<String> NAMES = new ArrayList<>();

   private BiomeIndex() {
   }

   public static short of(String name) {
      Short n = NUMBERS.get(name);
      if (n != null) {
         return n;
      }

      synchronized (NAMES) {
         n = NUMBERS.get(name);
         if (n != null) {
            return n;
         }

         if (NAMES.size() >= Short.MAX_VALUE) {
            return -1;
         }

         short next = (short)NAMES.size();
         NAMES.add(name);
         NUMBERS.put(name, next);
         return next;
      }
   }

   public static String name(int number) {
      synchronized (NAMES) {
         return number >= 0 && number < NAMES.size() ? NAMES.get(number) : null;
      }
   }

   public static int size() {
      synchronized (NAMES) {
         return NAMES.size();
      }
   }
}
