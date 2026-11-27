package dev.chunkatlas.minimap;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ChunkAtlasLink {
   private static Boolean present;
   private static Object colours;
   private static Method colour;
   private static Method open;

   private ChunkAtlasLink() {
   }

   public static boolean present() {
      if (present == null) {
         present = FabricLoader.getInstance().isModLoaded("chunkatlas");
         if (present) {
            try {
               Class<?> game = Class.forName("dev.chunkatlas.GameColours");
               colours = game.getConstructor().newInstance();
               colour = game.getMethod("colour", String.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
               Minimap.LOG.info("Chunk Atlas colours not available: {}", e.toString());
               colours = null;
            }

            try {
               open = Class.forName("dev.chunkatlas.InGame").getMethod("open", Minecraft.class, Screen.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
               Minimap.LOG.info("Chunk Atlas map not available: {}", e.toString());
            }
         }
      }

      return present;
   }

   public static boolean canOpenMap() {
      return present() && open != null;
   }

   public static void openMap(Minecraft mc, Screen parent) {
      if (!canOpenMap()) {
         return;
      }

      try {
         open.invoke(null, mc, parent);
      } catch (ReflectiveOperationException | RuntimeException e) {
         Minimap.LOG.warn("Could not open the Chunk Atlas map: {}", e.toString());
      }
   }

   static int colour(String block) {
      if (colours == null) {
         return -1;
      }

      try {
         int argb = (Integer)colour.invoke(colours, block);
         return argb == 0 ? 0 : argb & 0xFFFFFF;
      } catch (ReflectiveOperationException | RuntimeException e) {
         colours = null;
         return -1;
      }
   }
}
