package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Protection {
   private static final String FILE = "protected" + SelectionFiles.EXTENSION;

   private Protection() {
   }

   public static Path file(Path home, Path world, String dimensionId) {
      Path selections = SelectionFiles.dir(home, world, dimensionId);
      return selections.resolveSibling(selections.getFileName() + "-" + FILE);
   }

   public static ChunkSelection load(Path home, Path world, String dimensionId) {
      Path file = file(home, world, dimensionId);
      if (!Files.isRegularFile(file)) {
         return new ChunkSelection();
      }

      try {
         return SelectionFiles.load(file);
      } catch (IOException | RuntimeException e) {
         return new ChunkSelection();
      }
   }

   public static void save(Path home, Path world, String dimensionId, ChunkSelection protectedChunks) throws IOException {
      Path file = file(home, world, dimensionId);
      if (protectedChunks.isEmpty()) {
         Files.deleteIfExists(file);
      } else {
         SelectionFiles.write(file, protectedChunks);
      }
   }

   public static int unprotected(ChunkSelection selection, ChunkSelection protectedChunks) {
      int both = 0;
      if (!protectedChunks.isEmpty() && !selection.isEmpty()) {
         for (long region : protectedChunks.regions()) {
            long[] mine = selection.bits((int)(region >> 32), (int)region);
            if (mine != null) {
               long[] guard = protectedChunks.bits((int)(region >> 32), (int)region);
               for (int w = 0; w < mine.length; w++) {
                  both += Long.bitCount(mine[w] & guard[w]);
               }
            }
         }
      }

      return selection.size() - both;
   }

   public static ChunkSelection without(ChunkSelection selection, ChunkSelection protectedChunks) {
      ChunkSelection out = selection.copy();
      if (!protectedChunks.isEmpty()) {
         for (long region : protectedChunks.regions()) {
            out.remove((int)(region >> 32), (int)region, protectedChunks.bits((int)(region >> 32), (int)region));
         }
      }
      return out;
   }

   public static ChunkSelection with(ChunkSelection selection, ChunkSelection protectedChunks) {
      ChunkSelection out = selection.copy();
      if (!protectedChunks.isEmpty()) {
         for (long region : protectedChunks.regions()) {
            out.add((int)(region >> 32), (int)region, protectedChunks.bits((int)(region >> 32), (int)region));
         }
      }
      return out;
   }
}
