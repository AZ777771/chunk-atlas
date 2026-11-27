package dev.chunkatlas.gui;

import dev.chunkatlas.Settings;
import dev.chunkatlas.minimap.Colours;
import dev.chunkatlas.minimap.Config;
import dev.chunkatlas.minimap.Minimap;

final class Presets {
   static final int VANILLA = 0;
   static final int FULL = 1;
   static final String[] NAMES = {"vanilla", "full"};

   private Presets() {
   }

   private static final class Values {
      final boolean detailed;
      final boolean mobs;
      final boolean grid;
      final boolean slimes;
      final boolean seedMap;
      final boolean atlasColours;
      final boolean radarMobs;
      final boolean paperFrame;

      Values(boolean detailed, boolean mobs, boolean grid, boolean slimes, boolean seedMap, boolean atlasColours, boolean radarMobs, boolean paperFrame) {
         this.detailed = detailed;
         this.mobs = mobs;
         this.grid = grid;
         this.slimes = slimes;
         this.seedMap = seedMap;
         this.atlasColours = atlasColours;
         this.radarMobs = radarMobs;
         this.paperFrame = paperFrame;
      }
   }

   private static final Values[] SETS = {
      new Values(false, false, false, false, false, false, false, true),
      new Values(true, false, false, false, false, true, true, true)
   };

   static void apply(int preset) {
      Values v = SETS[preset];
      Config c = Config.get();
      Settings.detailed(v.detailed);
      Settings.mobs(v.mobs);
      Settings.grid(v.grid);
      c.chunkGrid = v.grid;
      c.slimeChunks = v.slimes;
      c.seedMap = v.seedMap;
      c.chunkAtlasColours = v.atlasColours;
      c.radarHostile = v.radarMobs;
      c.radarFriendly = v.radarMobs;
      if (v.paperFrame) {
         c.shape = Config.Shape.SQUARE;
         c.frame = true;
         c.northLock = true;
         c.smartZoom = false;
      }

      c.save();
      Colours.reset();
      Minimap.CACHE.clear();
   }

   static int current() {
      Config c = Config.get();
      for (int i = 0; i < SETS.length; i++) {
         Values v = SETS[i];
         boolean frame = !v.paperFrame || c.shape == Config.Shape.SQUARE && c.frame && c.northLock && !c.smartZoom;
         if (Settings.detailed() == v.detailed
            && Settings.mobs() == v.mobs
            && Settings.grid() == v.grid
            && c.chunkGrid == v.grid
            && c.slimeChunks == v.slimes
            && c.seedMap == v.seedMap
            && c.chunkAtlasColours == v.atlasColours
            && c.radarHostile == v.radarMobs
            && c.radarFriendly == v.radarMobs
            && frame) {
            return i;
         }
      }

      return -1;
   }
}
