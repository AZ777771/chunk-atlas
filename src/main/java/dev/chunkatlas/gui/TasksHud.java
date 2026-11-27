package dev.chunkatlas.gui;

import dev.chunkatlas.Settings;
import dev.chunkatlas.TaskClient;
import dev.chunkatlas.engine.Area;
import dev.chunkatlas.engine.BorderBook;
import dev.chunkatlas.engine.ChunkTasks;
import dev.chunkatlas.engine.TaskBook;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Hooks;
import dev.chunkatlas.port.Ids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class TasksHud {
   private static final int BAR_W = 120;
   private static final int SHOWN = 4;

   private static final double WALL_WITHIN = 24.0;
   private static final Matrix4f MATRIX = new Matrix4f();
   private static final Vector4f POINT = new Vector4f();

   private TasksHud() {
   }

   public static void register() {
      Hooks.hud("chunkatlas:tasks", Hooks.Under.CHAT, TasksHud::progress);
      Hooks.hud("chunkatlas:border", Hooks.Under.CROSSHAIR, TasksHud::wall);
   }

   private static void progress(Gfx g, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (!Settings.tasksHud() || mc.level == null || Game.screen(mc) != null) {
         return;
      }

      List<ChunkTasks.View> shown = new ArrayList<>();
      for (ChunkTasks.View t : TaskClient.tasks()) {
         if (!t.state().ended() && shown.size() < SHOWN) {
            shown.add(t);
         }
      }

      if (shown.isEmpty()) {
         return;
      }

      List<String> lines = new ArrayList<>();
      int w = BAR_W;
      for (ChunkTasks.View t : shown) {
         String line = Ui.dimension(t.dimension()) + " · " + t.percent() + "%";
         if (t.state() == TaskBook.State.PAUSED) {
            line += " · " + Ui.t("chunkatlas.tasks.paused");
         } else if (t.state() == TaskBook.State.WAITING) {
            line += " · " + Ui.t("chunkatlas.tasks.waiting");
         } else if (t.secondsLeft() >= 0) {
            line += " · " + TasksPanel.left(t.secondsLeft());
         }

         lines.add(line);
         w = Math.max(w, mc.font.width(line));
      }

      int rowH = 16;
      int pw = w + 10;
      int ph = shown.size() * rowH + 4;
      int corner = Settings.tasksHudCorner();
      int x = (corner & 1) == 0 ? 4 : g.width() - 4 - pw;
      int y = (corner & 2) == 0 ? 4 : g.height() - 4 - ph - ((corner & 1) == 0 ? 40 : 0);
      Glass.panel(g, x, y, pw, ph);
      int ty = y + 3;
      for (int i = 0; i < shown.size(); i++) {
         ChunkTasks.View t = shown.get(i);
         boolean running = t.state() == TaskBook.State.RUNNING;
         g.text(mc.font, lines.get(i), x + 5, ty, running ? Ui.WHITE : Glass.LABEL);
         int by = ty + 10;
         g.fill(x + 5, by, x + 5 + w, by + 2, 0x1AFFFFFF);
         int filled = t.total() <= 0 ? w : (int)((long)w * Math.min(t.done(), t.total()) / t.total());
         g.fill(x + 5, by, x + 5 + filled, by + 2, running ? Glass.ACCENT : 0xFF8A8A8A);
         ty += rowH;
      }
   }

   private static void wall(Gfx g, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (!Settings.borderInWorld() || mc.level == null || mc.player == null) {
         return;
      }

      BorderBook.Border b = TaskClient.border(Ids.of(mc.level.dimension()));
      if (b == null) {
         return;
      }

      Area area = b.area();
      double px = mc.player.getX();
      double pz = mc.player.getZ();
      if (area.edgeDistance(px, pz) > WALL_WITHIN) {
         return;
      }

      Vec3 eye = Game.eye(mc, MATRIX);
      if (eye == null) {
         return;
      }

      double[] e = area.nearestEdge(px, pz);

      double nx = px - e[0];
      double nz = pz - e[1];
      if (Math.hypot(nx, nz) < 0.5) {
         nx = e[0] - area.cx();
         nz = e[1] - area.cz();
      }

      double nl = Math.hypot(nx, nz);
      if (nl < 1.0E-6) {
         return;
      }

      double tx = -nz / nl;
      double tz = nx / nl;
      int w = g.width();
      int h = g.height();
      int base = (int)Math.floor(eye.y);
      double lastX = Double.NaN;
      double lastZ = Double.NaN;
      for (double k = -WALL_WITHIN; k <= WALL_WITHIN; k += 1.0) {
         double[] q = area.nearestEdge(e[0] + tx * k, e[1] + tz * k);
         if (Math.abs(q[0] - lastX) < 0.5 && Math.abs(q[1] - lastZ) < 0.5) {
            continue;
         }

         lastX = q[0];
         lastZ = q[1];
         for (int dy = -3; dy <= 5; dy++) {
            double dx = q[0] - eye.x;
            double dyy = base + dy + 0.5 - eye.y;
            double dz = q[1] - eye.z;
            double dist = Math.sqrt(dx * dx + dyy * dyy + dz * dz);
            int alpha = (int)(0xD0 * Math.max(0.0, 1.0 - dist / WALL_WITHIN));
            if (alpha < 12) {
               continue;
            }

            POINT.set((float)dx, (float)dyy, (float)dz, 1.0F);
            MATRIX.transform(POINT);
            if (POINT.w <= 0.05F) {
               continue;
            }

            int sx = Math.round((POINT.x / POINT.w * 0.5F + 0.5F) * w);
            int sy = Math.round((0.5F - POINT.y / POINT.w * 0.5F) * h);
            if (sx < 0 || sy < 0 || sx >= w || sy >= h) {
               continue;
            }

            int size = dist < 6.0 ? 2 : 1;
            g.fill(sx, sy, sx + size, sy + size, alpha << 24 | 0x8FB8FF);
         }
      }
   }
}
