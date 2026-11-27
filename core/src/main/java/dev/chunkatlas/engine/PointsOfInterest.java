package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PointsOfInterest {
   public enum Kind {
      PORTAL("minecraft:nether_portal"),
      BELL("minecraft:meeting"),
      LODESTONE("minecraft:lodestone");

      final String id;

      Kind(String id) {
         this.id = id;
      }
   }

   public record Point(Kind kind, int x, int y, int z) {
   }

   private PointsOfInterest() {
   }

   public static List<Point> read(Path poiFile) {
      List<Point> out = new ArrayList<>();
      Set<Long> seen = new HashSet<>();
      try (RegionFile file = RegionFile.open(poiFile)) {
         RegionFile.Header h = file.header();
         for (int i = 0; i < 1024; i++) {
            if (!h.present(i)) {
               continue;
            }

            RegionFile.Chunk c = file.chunk(i);
            if (!c.ok()) {
               continue;
            }

            Map<String, Object> sections = Nbt.map(Nbt.read(c.data()), "Sections");
            if (sections == null) {
               continue;
            }

            for (Object section : sections.values()) {
               List<Object> records = Nbt.list(section, "Records");
               if (records == null) {
                  continue;
               }

               for (Object r : records) {
                  String type = Nbt.string(r, "type");
                  Kind kind = null;
                  for (Kind k : Kind.values()) {
                     if (k.id.equals(type)) {
                        kind = k;
                     }
                  }

                  if (kind != null && ((Map<?, ?>)r).get("pos") instanceof int[] p && p.length == 3) {
                     long cell = (long)kind.ordinal() << 60 ^ (long)(p[0] >> 3) << 30 ^ (p[2] >> 3) & 0x3FFFFFFFL;
                     if (seen.add(cell)) {
                        out.add(new Point(kind, p[0], p[1], p[2]));
                     }
                  }
               }
            }
         }
      } catch (IOException | RuntimeException ignored) {
      }

      return out;
   }
}
