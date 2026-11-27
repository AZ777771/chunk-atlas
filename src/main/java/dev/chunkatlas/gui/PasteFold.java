package dev.chunkatlas.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

final class PasteFold {
   static final int FULL = 0;

   static final int OPTIONS = 1;

   static final int EXISTING = 2;

   static final int SHORT_LABEL = 3;

   static final int NO_LABEL = 4;

   static final int MOVE = 5;
   static final int LEVELS = 6;
   static final int[] BLENDS = BlendOptions.WIDTHS;

   enum Kind { LABEL, WORD, TILE, RULE }

   record Part(Kind kind, String id, String text) {
   }

   record Words(String from, String count, String x, String z, String y, String existing, String replace, String keep, String blend, String off,
      String smart, String cancel, String place) {
   }

   private PasteFold() {
   }

   static int fit(int room, Words w, boolean server, ToIntFunction<String> width) {
      for (int level = FULL; level < LEVELS - 1; level++) {
         if (width(bar(level, w, server), width) <= room) {
            return level;
         }
      }

      return MOVE;
   }

   static boolean folds(int level) {
      return level > FULL;
   }

   static boolean optionsFolded(int level) {
      return level >= OPTIONS;
   }

   static boolean existingFolded(int level) {
      return level >= EXISTING;
   }

   static boolean moveFolded(int level) {
      return level >= MOVE;
   }

   static List<Part> bar(int level, Words w, boolean server) {
      List<Part> out = new ArrayList<>();
      if (level < NO_LABEL) {
         out.add(new Part(Kind.LABEL, "label", level < SHORT_LABEL ? w.from() : w.count()));
         out.add(rule());
      }

      if (!moveFolded(level)) {
         out.add(new Part(Kind.WORD, "x", w.x()));
         out.add(new Part(Kind.WORD, "z", w.z()));
      }

      if (!server && !optionsFolded(level)) {
         out.add(new Part(Kind.WORD, "y", w.y()));
      }

      out.add(rule());
      if (!existingFolded(level)) {
         out.add(new Part(Kind.LABEL, "existing", w.existing()));
         out.add(new Part(Kind.WORD, "replace", w.replace()));
         out.add(new Part(Kind.WORD, "keep", w.keep()));
         out.add(rule());
      }

      if (!optionsFolded(level)) {
         out.add(new Part(Kind.LABEL, "blend", w.blend()));
         for (int n : BLENDS) {
            out.add(new Part(Kind.WORD, "blend." + n, n == 0 ? w.off() : Integer.toString(n)));
         }

         if (!server) {
            out.add(rule());
            out.add(new Part(Kind.WORD, "smart", w.smart()));
         }

         out.add(rule());
      }

      if (folds(level)) {
         out.add(new Part(Kind.TILE, "options", "more"));
         out.add(rule());
      }

      out.add(new Part(Kind.WORD, "cancel", w.cancel()));
      out.add(new Part(Kind.WORD, "place", w.place()));
      return trim(out);
   }

   static List<List<Part>> card(int level, Words w, boolean server) {
      List<List<Part>> rows = new ArrayList<>();
      if (moveFolded(level)) {
         rows.add(List.of(new Part(Kind.WORD, "x", w.x()), new Part(Kind.WORD, "z", w.z())));
      }

      if (existingFolded(level)) {
         rows.add(List.of(new Part(Kind.LABEL, "existing", w.existing()), new Part(Kind.WORD, "replace", w.replace()), new Part(Kind.WORD, "keep", w.keep())));
      }

      if (optionsFolded(level)) {
         List<Part> blend = new ArrayList<>();
         blend.add(new Part(Kind.LABEL, "blend", w.blend()));
         for (int n : BLENDS) {
            blend.add(new Part(Kind.WORD, "blend." + n, n == 0 ? w.off() : Integer.toString(n)));
         }

         rows.add(blend);
         if (!server) {
            rows.add(List.of(new Part(Kind.WORD, "smart", w.smart()), new Part(Kind.WORD, "y", w.y())));
         }
      }

      return rows;
   }

   static int width(List<Part> parts, ToIntFunction<String> width) {
      int w = 2 * Glass.INSET;
      for (int i = 0; i < parts.size(); i++) {
         w += along(parts.get(i), width) + (i > 0 ? Glass.GAP : 0);
      }

      return w;
   }

   static int rowWidth(List<Part> parts, ToIntFunction<String> width) {
      int w = 0;
      for (int i = 0; i < parts.size(); i++) {
         Part p = parts.get(i);
         w += (p.kind() == Kind.TILE ? Glass.ICON + 4 : p.kind() == Kind.RULE ? 8 : width.applyAsInt(p.text()) + 8) + (i > 0 ? Glass.GAP : 0);
      }

      return w;
   }

   private static int along(Part p, ToIntFunction<String> width) {
      return switch (p.kind()) {
         case LABEL -> width.applyAsInt(p.text()) + 8;
         case WORD -> width.applyAsInt(p.text()) + 12;
         case TILE -> Glass.TILE;
         case RULE -> 7;
      };
   }

   private static Part rule() {
      return new Part(Kind.RULE, "", "");
   }

   private static List<Part> trim(List<Part> parts) {
      List<Part> out = new ArrayList<>();
      for (Part p : parts) {
         boolean rule = p.kind() == Kind.RULE;
         if (rule && (out.isEmpty() || out.get(out.size() - 1).kind() == Kind.RULE)) {
            continue;
         }

         out.add(p);
      }

      while (!out.isEmpty() && out.get(out.size() - 1).kind() == Kind.RULE) {
         out.remove(out.size() - 1);
      }

      return out;
   }
}
