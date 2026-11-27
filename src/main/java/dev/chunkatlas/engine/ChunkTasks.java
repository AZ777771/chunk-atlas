package dev.chunkatlas.engine;

import dev.chunkatlas.port.Ids;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelResource;

public final class ChunkTasks {
   private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Chunk Atlas");
   private static final long SAVE_EVERY = 15_000L;

   private static final long ENDED_SHOWN = 4_000L;
   private static final long UPDATE_EVERY = 100L;

   private static final double RATE_WEIGHT = 0.35;

   public static final int FOR_MAP = 0;
   public static final int FOR_GENERATE = 1;
   private static volatile ChunkTasks current;

   public record View(
      int id, String dimension, Area area, boolean picked, TaskBook.State state, int done, int total, int generated, double rate, int secondsLeft, long[] flying
   ) {
      public int percent() {
         return this.total <= 0 ? 100 : (int)Math.min(100L, this.done * 100L / this.total);
      }
   }

   private static final class Live {
      final TaskBook.Task task;
      ChunkGeneration run;

      int baseGenerated;
      double rate;
      boolean rated;
      long sampledAt;
      int sampledDone;
      int secondsLeft = -1;
      long endedAt;
      final ChunkSelection[] fresh = {new ChunkSelection(), new ChunkSelection()};

      Live(TaskBook.Task task) {
         this.task = task;
      }
   }

   private final MinecraftServer server;
   private final Path file;
   private final TaskBook book;
   private final Map<Integer, Live> live = new HashMap<>();
   private volatile List<View> views = List.of();
   private boolean dirty;
   private long savedAt = System.currentTimeMillis();
   private long updatedAt;
   private volatile int version;

   private ChunkTasks(MinecraftServer server, Path file, TaskBook book) {
      this.server = server;
      this.file = file;
      this.book = book;
      for (TaskBook.Task t : book.tasks) {
         Live l = new Live(t);
         l.baseGenerated = t.generated;
         this.live.put(t.id, l);
      }
   }

   public static Path file(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve("chunkatlas").resolve("tasks.dat");
   }

   public static ChunkTasks started(MinecraftServer server, boolean goOn) {
      Path file = file(server);
      TaskBook book;
      try {
         book = TaskBook.read(Files.isRegularFile(file) ? Files.readAllBytes(file) : null);
      } catch (IOException e) {
         LOG.warn("Chunk Atlas: couldn't read the chunk tasks: {}", e.toString());
         book = new TaskBook();
      }

      for (TaskBook.Task t : book.tasks) {
         if (t.state.active()) {
            t.state = goOn ? TaskBook.State.WAITING : TaskBook.State.PAUSED;
         }
      }

      ChunkTasks tasks = new ChunkTasks(server, file, book);
      current = tasks;
      tasks.update(true);
      return tasks;
   }

   public static ChunkTasks of(MinecraftServer server) {
      ChunkTasks now = current;
      return now != null && now.server == server ? now : null;
   }

   public static void stopping(MinecraftServer server) {
      ChunkTasks now = of(server);
      if (now == null) {
         return;
      }

      for (Live l : now.live.values()) {
         now.absorb(l);
         if (l.run != null) {
            l.run.stop();
         }
      }

      now.save();
      current = null;
   }

   public int version() {
      return this.version;
   }

   public List<View> views() {
      return this.views;
   }

   public int atOnce() {
      return this.book.atOnce;
   }

   public String refusal(String dimension, int chunks) {
      if (this.level(dimension) == null) {
         return "chunkatlas.server.no_dimension";
      }

      if (chunks <= 0) {
         return "chunkatlas.tasks.nothing";
      }

      if (chunks > ChunkGeneration.MAX) {
         return "chunkatlas.tasks.too_big";
      }

      return this.book.tasks.size() >= TaskBook.MAX_TASKS ? "chunkatlas.tasks.too_many" : null;
   }

   public enum WorldBorderUse {
      IGNORE,
      FIT,
      SET;

      public static WorldBorderUse of(int n) {
         WorldBorderUse[] all = values();
         return all[Math.max(0, Math.min(all.length - 1, n))];
      }
   }

   public TaskBook.Task add(String dimension, Area area, WorldBorderUse border, double borderSize, int speed, boolean finishPartial) {
      ServerLevel level = this.level(dimension);
      if (level == null) {
         return null;
      }

      WorldBorder wb = level.getWorldBorder();
      if (border == WorldBorderUse.FIT) {
         double half = wb.getSize() / 2.0;
         area = new Area(area.shape(), wb.getCenterX(), wb.getCenterZ(), half, half);
      } else if (border == WorldBorderUse.SET && borderSize >= 1.0) {
         wb.setCenter(area.cx(), area.cz());
         wb.setSize(Math.min(Area.MAX_RADIUS * 2.0, borderSize));
      }

      long n = area.chunkCount();
      if (this.refusal(dimension, (int)Math.min(Integer.MAX_VALUE, n)) != null) {
         return null;
      }

      return this.put(TaskBook.Task.of(this.book.nextId++, dimension, area, speed, finishPartial));
   }

   public TaskBook.Task addPicked(String dimension, ChunkSelection chunks, int speed, boolean finishPartial) {
      if (this.refusal(dimension, chunks.size()) != null) {
         return null;
      }

      return this.put(TaskBook.Task.picked(this.book.nextId++, dimension, chunks, speed, finishPartial));
   }

   private TaskBook.Task put(TaskBook.Task t) {
      this.book.tasks.add(t);
      this.live.put(t.id, new Live(t));
      this.dirty = true;
      LOG.info("Chunk Atlas: chunk task {} added: {} chunks in {}", t.id, t.total, t.dimension);
      this.update(true);
      return t;
   }

   public void pause(int id, boolean on) {
      Live l = this.live.get(id);
      if (l == null || l.task.state.ended()) {
         return;
      }

      if (on && l.task.state.active()) {
         l.task.state = TaskBook.State.PAUSED;
         if (l.run != null) {
            l.run.stop();
         }
      } else if (!on && l.task.state == TaskBook.State.PAUSED) {
         l.task.state = TaskBook.State.WAITING;
      }

      this.dirty = true;
      this.update(true);
   }

   public void cancel(int id) {
      Live l = this.live.get(id);
      if (l == null || l.task.state.ended()) {
         return;
      }

      l.task.state = TaskBook.State.CANCELLED;
      l.endedAt = System.currentTimeMillis();
      if (l.run != null) {
         l.run.stop();
      }

      this.dirty = true;
      this.update(true);
   }

   public void atOnce(int n) {
      this.book.atOnce = Math.max(1, Math.min(TaskBook.MAX_AT_ONCE, n));
      this.dirty = true;
      this.update(true);
   }

   public ChunkSelection takeFresh(int id, int reader) {
      Live l = this.live.get(id);
      if (l == null) {
         return new ChunkSelection();
      }

      synchronized (l) {
         ChunkSelection out = l.fresh[reader];
         l.fresh[reader] = new ChunkSelection();
         return out;
      }
   }

   public ChunkSelection dealt(int id) {
      Live l = this.live.get(id);
      if (l == null) {
         return new ChunkSelection();
      }

      synchronized (l) {
         return l.task.dealt.copy();
      }
   }

   public void tick() {
      this.update(false);
   }

   private void update(boolean now) {
      long t = System.currentTimeMillis();
      if (!now && t - this.updatedAt < UPDATE_EVERY) {
         return;
      }

      this.updatedAt = t;
      int running = 0;
      for (Live l : this.live.values()) {
         this.absorb(l);
         if (l.run != null && l.run.state().ended()) {
            ChunkGeneration.State how = l.run.state();
            l.run = null;
            l.baseGenerated = l.task.generated;
            if (how == ChunkGeneration.State.DONE && l.task.state.active()) {
               l.task.state = TaskBook.State.DONE;
               l.endedAt = t;
               this.dirty = true;
               LOG.info("Chunk Atlas: chunk task {} done: {} chunks generated", l.task.id, l.task.generated);
            } else if (l.task.state == TaskBook.State.RUNNING) {
               l.task.state = TaskBook.State.WAITING;
            }
         }

         if (l.run != null) {
            running++;
         }
      }

      for (int i = this.book.tasks.size() - 1; i >= 0 && running > this.book.atOnce; i--) {
         Live l = this.live.get(this.book.tasks.get(i).id);
         if (l != null && l.run != null && l.task.state == TaskBook.State.RUNNING) {
            l.task.state = TaskBook.State.WAITING;
            l.run.stop();
            running--;
         }
      }

      for (TaskBook.Task task : this.book.tasks) {
         if (running >= this.book.atOnce) {
            break;
         }

         Live l = this.live.get(task.id);
         if (task.state == TaskBook.State.WAITING && l != null && l.run == null) {
            if (this.begin(l)) {
               running++;
            }
         }
      }

      this.book.tasks.removeIf(task -> {
         Live l = this.live.get(task.id);
         boolean gone = task.state.ended() && (l == null || l.run == null && t - l.endedAt > ENDED_SHOWN);
         if (gone) {
            this.live.remove(task.id);
         }

         return gone;
      });
      if (this.dirty && t - this.savedAt > SAVE_EVERY) {
         this.save();
      }

      List<View> fresh = this.makeViews();
      if (!same(this.views, fresh)) {
         this.version++;
      }

      this.views = fresh;
   }

   private static boolean same(List<View> a, List<View> b) {
      if (a.size() != b.size()) {
         return false;
      }

      for (int i = 0; i < a.size(); i++) {
         View x = a.get(i);
         View y = b.get(i);
         if (x.id() != y.id() || x.state() != y.state() || x.done() != y.done() || x.generated() != y.generated() || x.secondsLeft() != y.secondsLeft()
            || x.flying().length != y.flying().length || x.flying().length > 0) {
            return false;
         }
      }

      return true;
   }

   private boolean begin(Live l) {
      TaskBook.Task task = l.task;
      ServerLevel level = this.level(task.dimension);
      ChunkSelection left = level == null ? null : task.left();
      if (left == null || left.isEmpty()) {
         task.state = level == null ? TaskBook.State.CANCELLED : TaskBook.State.DONE;
         l.endedAt = System.currentTimeMillis();
         this.dirty = true;
         return false;
      }

      ChunkGeneration run = ChunkGeneration.startTask(this.server, level, left, ChunkGeneration.Speed.of(task.speed), task.finishPartial, r -> this.update(false));
      if (run == null) {
         task.state = TaskBook.State.CANCELLED;
         l.endedAt = System.currentTimeMillis();
         this.dirty = true;
         return false;
      }

      l.run = run;
      l.baseGenerated = task.generated;
      l.rated = false;
      l.sampledAt = System.currentTimeMillis();
      l.sampledDone = task.dealtCount();
      l.secondsLeft = -1;
      task.state = TaskBook.State.RUNNING;
      return true;
   }

   private void absorb(Live l) {
      if (l.run == null) {
         return;
      }

      ChunkSelection dealt = l.run.takeDealt();
      if (!dealt.isEmpty()) {
         synchronized (l) {
            for (long region : dealt.regions()) {
               int rx = (int)(region >> 32);
               int rz = (int)region;
               long[] bits = dealt.bits(rx, rz);
               l.task.dealt.add(rx, rz, bits);
               for (ChunkSelection f : l.fresh) {
                  f.add(rx, rz, bits);
               }
            }
         }

         this.dirty = true;
      }

      l.task.generated = l.baseGenerated + l.run.progress().done();
      long now = System.currentTimeMillis();
      long span = now - l.sampledAt;
      if (span >= 1000L) {
         int done = l.task.dealtCount();
         double rate = (done - l.sampledDone) * 1000.0 / span;

         l.rate = l.rated ? l.rate + (rate - l.rate) * RATE_WEIGHT : rate;
         l.rated = true;
         l.sampledAt = now;
         l.sampledDone = done;
         int left = l.task.total - done;
         l.secondsLeft = l.rate > 0.05 ? (int)Math.min(Integer.MAX_VALUE, Math.ceil(left / l.rate)) : -1;
      }
   }

   private List<View> makeViews() {
      List<View> out = new ArrayList<>();
      for (TaskBook.Task t : this.book.tasks) {
         Live l = this.live.get(t.id);
         boolean going = l != null && l.run != null && t.state == TaskBook.State.RUNNING;
         out.add(new View(
            t.id, t.dimension, t.area, t.picked != null, t.state, t.dealtCount(), t.total, t.generated, going ? l.rate : 0.0, going ? l.secondsLeft : -1,
            going ? l.run.flying() : new long[0]
         ));
      }

      return List.copyOf(out);
   }

   private ServerLevel level(String dimension) {
      ResourceKey<Level> key = Ids.dimension(dimension);
      return key == null ? null : this.server.getLevel(key);
   }

   private void save() {
      byte[] data;
      synchronized (this) {
         data = this.book.write();
      }

      this.dirty = false;
      this.savedAt = System.currentTimeMillis();
      try {
         Files.createDirectories(this.file.getParent());
         Path tmp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
         Files.write(tmp, data);
         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
         LOG.warn("Chunk Atlas: couldn't save the chunk tasks: {}", e.toString());
      }
   }
}
