package dev.chunkatlas.minimap.hud;

import dev.chunkatlas.gui.MapMarkers;
import dev.chunkatlas.gui.Ui;
import dev.chunkatlas.minimap.Config;
import dev.chunkatlas.minimap.waypoint.Waypoint;
import dev.chunkatlas.minimap.waypoint.Waypoints;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class WorldMarkers {
   private static final int TAG = 0x90000000;
   private final Matrix4f matrix = new Matrix4f();
   private final Vector4f point = new Vector4f();

   public void extract(Gfx g, float partial) {
      Minecraft mc = Minecraft.getInstance();
      Config cfg = Config.get();
      if (!cfg.enabled || !cfg.waypointsInWorld || mc.player == null || mc.level == null || Waypoints.all().isEmpty()) {
         return;
      }

      Vec3 eye = Game.eye(mc, this.matrix);
      if (eye == null) {
         return;
      }

      String here = Ids.of(mc.level.dimension());
      int w = g.width();
      int h = g.height();

      for (Waypoint wp : Waypoints.all()) {
         if (!wp.inWorld || wp.death && !cfg.deathpoints) {
            continue;
         }

         double[] p = wp.placeIn(here);
         if (p == null) {
            continue;
         }

         double dx = p[0] - eye.x;
         double dy = p[1] + 1.0 - eye.y;
         double dz = p[2] - eye.z;
         double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (distance < 2.0) {
            continue;
         }

         double pull = distance > 64.0 ? 64.0 / distance : 1.0;
         this.point.set((float)(dx * pull), (float)(dy * pull), (float)(dz * pull), 1.0F);
         this.matrix.transform(this.point);
         if (this.point.w <= 0.05F) {
            continue;
         }

         float sx = (this.point.x / this.point.w * 0.5F + 0.5F) * w;
         float sy = (0.5F - this.point.y / this.point.w * 0.5F) * h;
         if (sx < -20 || sy < -20 || sx > w + 20 || sy > h + 20) {
            continue;
         }

         int x = Math.round(sx);
         int y = Math.round(sy);
         int alpha = distance > 256.0 ? 0xB0 : 0xFF;
         MapMarkers.pin(g, mc.font, x, y, wp, alpha);
         String line = (wp.name.isBlank() ? "" : wp.name + "  ") + metres(distance);
         int lw = mc.font.width(line);
         g.fill(x - lw / 2 - 2, y + 3, x + lw / 2 + 3, y + 14, TAG);
         g.text(mc.font, line, x - lw / 2 + 1, y + 5, alpha << 24 | 0xFFFFFF, false);
      }
   }

   static String metres(double m) {
      long n = Math.round(m);
      return n >= 1000L ? Ui.t("chunkatlas.waypoints.km", String.format(java.util.Locale.ROOT, "%.1f", n / 1000.0)) : Ui.t("chunkatlas.waypoints.metres", n);
   }
}
