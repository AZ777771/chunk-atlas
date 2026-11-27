package dev.chunkatlas.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class ForcedChunks {
   static final String TYPE = "minecraft:forced";

   static final int LEVEL = 31;
   private static final int MOST_BYTES = 64 << 20;

   private ForcedChunks() {
   }

   public static Path file(WorldLayout.Dimension d) {
      Path folder = d.region().getParent();
      Path group = folder == null ? null : folder.getParent();
      Path dims = group == null ? null : group.getParent();
      return dims != null && dims.getFileName() != null && dims.getFileName().toString().equals("dimensions")
            && Files.isDirectory(dims.resolve("minecraft").resolve("overworld"))
         ? folder.resolve("data").resolve("minecraft").resolve("chunk_tickets.dat")
         : null;
   }

   public static ChunkSelection read(Path file) throws IOException {
      ChunkSelection out = new ChunkSelection();
      for (Object t : tickets(load(file, 0))) {
         int[] at = forcedAt(t);
         if (at != null) {
            out.add(at[0], at[1]);
         }
      }

      return out;
   }

   public static int write(Path file, ChunkSelection chunks, boolean on, int dataVersion) throws IOException {
      Map<String, Object> root = load(file, dataVersion);
      Object data = root.get("data");
      List<Object> tickets = tickets(root);
      ChunkSelection forced = new ChunkSelection();
      List<Object> kept = new ArrayList<>();
      int changed = 0;
      for (Object t : tickets) {
         int[] at = forcedAt(t);
         if (at != null && !on && chunks.contains(at[0], at[1])) {
            changed++;
            continue;
         }

         if (at != null) {
            forced.add(at[0], at[1]);
         }

         kept.add(t);
      }

      if (on) {
         int[] added = {0};
         chunks.forEach((x, z) -> {
            if (!forced.contains(x, z)) {
               Map<String, Object> t = new LinkedHashMap<>();
               t.put("chunk_pos", new int[]{x, z});
               t.put("type", TYPE);
               t.put("level", LEVEL);
               kept.add(t);
               added[0]++;
            }
         });
         changed = added[0];
      }

      if (changed == 0) {
         return 0;
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> d = (Map<String, Object>)data;
      if (kept.isEmpty()) {
         d.remove("tickets");
      } else {
         d.put("tickets", kept);
      }

      Files.createDirectories(file.getParent());
      Path tmp = file.resolveSibling(file.getFileName() + ".chunkatlas");
      try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp))) {
         out.write(Nbt.write(root));
      }

      try {
         Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (java.nio.file.AtomicMoveNotSupportedException e) {
         Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }

      return changed;
   }

   private static Map<String, Object> load(Path file, int dataVersion) throws IOException {
      Map<String, Object> root;
      if (Files.isRegularFile(file)) {
         try (InputStream in = Files.newInputStream(file)) {
            root = Nbt.read(Packed.gunzip(in, MOST_BYTES));
         }
      } else {
         root = new LinkedHashMap<>();
         root.put("DataVersion", dataVersion);
      }

      if (!(root.get("data") instanceof Map)) {
         root.put("data", new LinkedHashMap<String, Object>());
      }

      return root;
   }

   private static List<Object> tickets(Map<String, Object> root) {
      List<Object> list = Nbt.list(root.get("data"), "tickets");
      return list == null ? List.of() : list;
   }

   private static int[] forcedAt(Object ticket) {
      String type = Nbt.string(ticket, "type");
      boolean forced = TYPE.equals(type) || "forced".equals(type);
      return forced && ticket instanceof Map<?, ?> m && m.get("chunk_pos") instanceof int[] at && at.length == 2 ? at : null;
   }
}
