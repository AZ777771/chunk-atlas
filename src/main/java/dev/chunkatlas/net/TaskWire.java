package dev.chunkatlas.net;

import dev.chunkatlas.engine.Area;
import dev.chunkatlas.engine.BorderBook;
import dev.chunkatlas.engine.Borders;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.ChunkTasks;
import dev.chunkatlas.engine.TaskBook;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TaskWire {
   private static final int FULL_REGIONS = 1500;

   private TaskWire() {
   }

   public static AtlasNet.TasksUpdate update(ChunkTasks tasks, Borders borders, boolean admin, boolean full, Map<Integer, ChunkSelection> fresh) {
      List<AtlasNet.TaskInfo> infos = new ArrayList<>();
      if (admin && tasks != null) {
         for (ChunkTasks.View v : tasks.views()) {
            ChunkSelection dealt = full ? tasks.dealt(v.id()) : fresh.getOrDefault(v.id(), new ChunkSelection());
            if (dealt.regions().size() > FULL_REGIONS) {
               dealt = new ChunkSelection();
            }

            infos.add(new AtlasNet.TaskInfo(
               v.id(), v.dimension(), v.area() == null ? "" : v.area().write(), v.state().ordinal(), v.done(), v.total(), (float)v.rate(), v.secondsLeft(), v.flying(), dealt
            ));
         }
      }

      List<AtlasNet.BorderInfo> bs = new ArrayList<>();
      if (borders != null) {
         borders.all().forEach((dim, b) -> bs.add(new AtlasNet.BorderInfo(dim, b.area().write(), b.wrap())));
      }

      return new AtlasNet.TasksUpdate(admin, full, tasks == null ? 1 : tasks.atOnce(), infos, bs);
   }

   public static Map<Integer, ChunkSelection> takeFresh(ChunkTasks tasks) {
      Map<Integer, ChunkSelection> out = new java.util.HashMap<>();
      if (tasks != null) {
         for (ChunkTasks.View v : tasks.views()) {
            out.put(v.id(), tasks.takeFresh(v.id(), ChunkTasks.FOR_MAP));
         }
      }

      return out;
   }

   public static String apply(ChunkTasks tasks, Borders borders, AtlasNet.TaskCommand c) {
      switch (c.action()) {
         case AtlasNet.TaskCommand.ADD -> {
            Area area = Area.read(c.area());
            if (tasks == null || area == null) {
               return "chunkatlas.tasks.failed";
            }

            ChunkTasks.WorldBorderUse use = ChunkTasks.WorldBorderUse.of(c.border());
            String why = tasks.refusal(c.dimension(), use == ChunkTasks.WorldBorderUse.FIT ? 1 : (int)Math.min(Integer.MAX_VALUE, area.chunkCount()));
            if (why != null) {
               return why;
            }

            return tasks.add(c.dimension(), area, use, c.size(), c.speed(), c.finish()) == null ? "chunkatlas.tasks.failed" : null;
         }
         case AtlasNet.TaskCommand.PAUSE, AtlasNet.TaskCommand.RESUME -> {
            if (tasks != null) {
               tasks.pause(c.id(), c.action() == AtlasNet.TaskCommand.PAUSE);
            }
         }
         case AtlasNet.TaskCommand.CANCEL -> {
            if (tasks != null) {
               tasks.cancel(c.id());
            }
         }
         case AtlasNet.TaskCommand.AT_ONCE -> {
            if (tasks != null) {
               tasks.atOnce(c.value());
            }
         }
         case AtlasNet.TaskCommand.BORDER_SET -> {
            Area area = Area.read(c.area());
            if (borders == null || area == null || !dev.chunkatlas.engine.IdText.valid(c.dimension())) {
               return "chunkatlas.border.failed";
            }

            borders.set(c.dimension(), new BorderBook.Border(area, c.flag()));
         }
         case AtlasNet.TaskCommand.BORDER_CLEAR -> {
            if (borders != null) {
               borders.set(c.dimension(), null);
            }
         }
         default -> {
            return "chunkatlas.tasks.failed";
         }
      }

      return null;
   }

   public static TaskBook.State state(int n) {
      return TaskBook.State.of(n);
   }
}
