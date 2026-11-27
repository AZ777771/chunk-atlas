package dev.chunkatlas.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BorderBook {
   public record Border(Area area, boolean wrap) {
   }

   public final Map<String, Border> borders = new LinkedHashMap<>();

   public Border get(String dimension) {
      return this.borders.get(dimension);
   }

   public String write() {
      StringBuilder sb = new StringBuilder("# Chunk Atlas borders: dimension shape x z radius radius2 [wrap]\n");
      this.borders.forEach((dim, b) -> sb.append(dim).append(' ').append(b.area().write()).append(b.wrap() ? " wrap" : "").append('\n'));
      return sb.toString();
   }

   public static BorderBook read(String text) {
      BorderBook book = new BorderBook();
      if (text == null) {
         return book;
      }

      for (String line : text.split("\n")) {
         String s = line.strip();
         if (s.isEmpty() || s.startsWith("#")) {
            continue;
         }

         int sp = s.indexOf(' ');
         if (sp <= 0) {
            continue;
         }

         String dim = s.substring(0, sp);
         String rest = s.substring(sp + 1).strip();
         boolean wrap = rest.endsWith(" wrap");
         if (wrap) {
            rest = rest.substring(0, rest.length() - 5);
         }

         Area area = Area.read(rest);
         if (area != null && IdText.valid(dim)) {
            book.borders.put(dim, new Border(area, wrap));
         }
      }

      return book;
   }
}
