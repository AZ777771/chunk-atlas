package dev.chunkatlas.minimap;

import dev.chunkatlas.port.Reg;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

public final class Colours {
   public static final int LOW = 180;
   public static final int NORMAL = 220;
   public static final int HIGH = 255;

   private static final Map<Block, Integer> CACHE = new IdentityHashMap<>();
   private static boolean usingAtlas;

   private Colours() {
   }

   public static synchronized void reset() {
      CACHE.clear();
      usingAtlas = Config.get().chunkAtlasColours && ChunkAtlasLink.present();
   }

   public static int of(Block block) {
      Integer known = CACHE.get(block);
      if (known != null) {
         return known;
      }

      int colour = -1;
      if (usingAtlas) {
         colour = ChunkAtlasLink.colour(Reg.blockId(block));
      }

      if (colour < 0) {
         MapColor map = block.defaultMapColor();
         colour = map == MapColor.NONE ? 0 : map.col;
      }

      colour &= 0xFFFFFF;
      CACHE.put(block, colour);
      return colour;
   }

   public static boolean water(int rgb) {
      return rgb == MapColor.WATER.col;
   }

   public static int shade(int rgb, int brightness) {
      int r = (rgb >> 16 & 0xFF) * brightness / 255;
      int g = (rgb >> 8 & 0xFF) * brightness / 255;
      int b = (rgb & 0xFF) * brightness / 255;
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   public static int blend(int under, int over) {
      int a = over >>> 24;
      if (a == 0) {
         return under;
      }

      int na = 255 - a;
      int r = ((over >> 16 & 0xFF) * a + (under >> 16 & 0xFF) * na) / 255;
      int g = ((over >> 8 & 0xFF) * a + (under >> 8 & 0xFF) * na) / 255;
      int b = ((over & 0xFF) * a + (under & 0xFF) * na) / 255;
      return 0xFF000000 | r << 16 | g << 8 | b;
   }
}
