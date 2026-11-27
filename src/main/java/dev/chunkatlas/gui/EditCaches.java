package dev.chunkatlas.gui;

import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.Protection;
import dev.chunkatlas.engine.WorldLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

final class EditCaches {
   private final List<Object> queuedFor = new ArrayList<>();
   ChunkSelection gone = new ChunkSelection();
   ChunkSelection changed = new ChunkSelection();
   List<WorldLayout.RegionInfo> goneRegions = List.of();
   List<WorldLayout.RegionInfo> changedRegions = List.of();

   <P> void queued(List<P> edits, Function<P, ChunkSelection> affected, Predicate<P> clears) {
      boolean same = edits.size() == this.queuedFor.size();
      for (int i = 0; same && i < edits.size(); i++) {
         same = edits.get(i) == this.queuedFor.get(i);
      }

      if (same) {
         return;
      }

      this.queuedFor.clear();
      this.queuedFor.addAll(edits);
      ChunkSelection g = new ChunkSelection();
      ChunkSelection c = new ChunkSelection();
      for (P p : edits) {
         ChunkSelection into = clears.test(p) ? g : c;
         ChunkSelection a = affected.apply(p);
         for (long region : a.regions()) {
            into.add((int)(region >> 32), (int)region, a.bits((int)(region >> 32), (int)region));
         }
      }

      this.gone = g;
      this.changed = c;
      this.goneRegions = regionsOf(g);
      this.changedRegions = regionsOf(c);
   }

   static List<WorldLayout.RegionInfo> regionsOf(ChunkSelection sel) {
      List<WorldLayout.RegionInfo> out = new ArrayList<>();
      for (long region : sel.regions()) {
         out.add(new WorldLayout.RegionInfo((int)(region >> 32), (int)region, null, 0L, 0L, false));
      }

      return out;
   }

   private ChunkSelection guardSel;
   private int guardSelChanges;
   private ChunkSelection guardOf;
   private int guardOfChanges;
   private int unprotected;

   int unprotected(ChunkSelection sel, ChunkSelection guard) {
      if (sel != this.guardSel || sel.changes() != this.guardSelChanges || guard != this.guardOf || guard.changes() != this.guardOfChanges) {
         this.guardSel = sel;
         this.guardSelChanges = sel.changes();
         this.guardOf = guard;
         this.guardOfChanges = guard.changes();
         this.unprotected = Protection.unprotected(sel, guard);
      }

      return this.unprotected;
   }

   private ChunkSelection ghostFor;
   private ChunkSelection ghostGuard;
   private int ghostGuardChanges;
   List<WorldLayout.RegionInfo> ghostRegions = List.of();
   ChunkSelection ghostSkipped = new ChunkSelection();

   int ghostMinX;
   int ghostMinZ;

   void ghost(ChunkSelection shown, ChunkSelection guard) {
      boolean moved = shown != this.ghostFor;
      if (moved) {
         this.ghostFor = shown;
         this.ghostRegions = regionsOf(shown);
         int minX = Integer.MAX_VALUE;
         int minZ = Integer.MAX_VALUE;
         for (long region : shown.regions()) {
            int rx = (int)(region >> 32);
            int rz = (int)region;
            long[] bits = shown.bits(rx, rz);
            for (int i = 0; i < 1024; i++) {
               if ((bits[i >> 6] >>> (i & 63) & 1L) != 0L) {
                  minX = Math.min(minX, rx * 32 + (i & 31));
                  minZ = Math.min(minZ, rz * 32 + (i >> 5));
               }
            }
         }

         this.ghostMinX = minX;
         this.ghostMinZ = minZ;
      }

      if (moved || guard != this.ghostGuard || guard.changes() != this.ghostGuardChanges) {
         this.ghostGuard = guard;
         this.ghostGuardChanges = guard.changes();
         ChunkSelection skipped = new ChunkSelection();
         if (!guard.isEmpty()) {
            for (long region : guard.regions()) {
               long[] mine = shown.bits((int)(region >> 32), (int)region);
               if (mine != null) {
                  long[] g = guard.bits((int)(region >> 32), (int)region);
                  for (int w = 0; w < mine.length; w++) {
                     mine[w] &= g[w];
                  }

                  skipped.add((int)(region >> 32), (int)region, mine);
               }
            }
         }

         this.ghostSkipped = skipped;
      }
   }
}
