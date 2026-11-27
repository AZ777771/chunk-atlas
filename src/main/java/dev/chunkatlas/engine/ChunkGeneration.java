package dev.chunkatlas.engine;

import dev.chunkatlas.port.ChunkIo;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Pos;
import dev.chunkatlas.port.Tickets;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class ChunkGeneration {
   public static final int MAX = 1 << 22;
   private static final long STEP_NANOS = 50_000_000L;

   private static final long DRAIN_NANOS = 3_000_000_000L;

   private static final long IDLE_NANOS = 150_000_000L;
   private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Chunk Atlas");
   private static volatile ChunkGeneration current;

   private static final java.util.List<ChunkGeneration> RUNS = new java.util.concurrent.CopyOnWriteArrayList<>();

   private static final int FLYING_SHOWN = 256;
   private static boolean unloadFailed;

   public enum Speed {
      LIGHT,
      BALANCED,
      FAST;

      public int inFlight() {
         return switch (this) {
            case LIGHT -> 8;
            case BALANCED -> 32;
            case FAST -> Math.max(64, Background.cores() * 16);
         };
      }

      public static Speed of(int n) {
         return values()[Math.max(0, Math.min(values().length - 1, n))];
      }
   }

   public enum State {
      RUNNING,
      PAUSED,
      DONE,
      STOPPED;

      public boolean ended() {
         return this == DONE || this == STOPPED;
      }
   }

   public record Progress(State state, int done, int total, int leftAlone, int secondsLeft) {
   }

   public interface Listener {
      void stepped(ChunkGeneration run);
   }

   private final MinecraftServer server;
   private final ServerLevel level;
   private final UUID player;
   private final ChunkSelection chosen;
   private final int size;
   private final Speed speed;
   private final boolean finishPartial;
   private final Listener listener;

   private final Tickets.Own ticket = Tickets.own();
   private final ConcurrentLinkedQueue<long[]> checked = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<long[]> finished = new ConcurrentLinkedQueue<>();
   private final ChunkSelection dealt = new ChunkSelection();
   private final long startedAt = System.nanoTime();

   private long[] positions;
   private long[] order;
   private int next;
   private int checking;
   private final LongArrayFIFOQueue ready = new LongArrayFIFOQueue();
   private final LongOpenHashSet flying = new LongOpenHashSet();
   private ResourceKey<Level> playerWas;
   private int lastTick = -1;
   private long lastTickAt;

   private final RateEstimate pace = new RateEstimate();
   private boolean pausedBefore;

   private volatile State state = State.RUNNING;
   private volatile boolean pauseAsked;
   private volatile boolean stopAsked;
   private volatile int done;
   private volatile int failed;
   private volatile int leftAlone;
   private volatile int secondsLeft = -1;
   private volatile long endedAt;

   private volatile long[] flyingNow = new long[0];

   private ChunkGeneration(MinecraftServer server, ServerLevel level, UUID player, ChunkSelection chunks, Speed speed, boolean finishPartial, Listener listener) {
      this.server = server;
      this.level = level;
      this.player = player;
      this.chosen = chunks.copy();
      this.size = chunks.size();
      this.speed = speed;
      this.finishPartial = finishPartial;
      this.listener = listener;
   }

   public static synchronized ChunkGeneration start(
      MinecraftServer server, ServerLevel level, UUID player, ChunkSelection chunks, Speed speed, boolean finishPartial, Listener listener
   ) {
      ChunkGeneration now = current;
      if (now != null && now.server == server && !now.state.ended() || chunks.isEmpty() || chunks.size() > MAX) {
         return null;
      }

      ChunkGeneration run = new ChunkGeneration(server, level, player, chunks, speed, finishPartial, listener);
      current = run;
      run.go();
      return run;
   }

   public static ChunkGeneration startTask(MinecraftServer server, ServerLevel level, ChunkSelection chunks, Speed speed, boolean finishPartial, Listener listener) {
      if (chunks.isEmpty() || chunks.size() > MAX) {
         return null;
      }

      ChunkGeneration run = new ChunkGeneration(server, level, null, chunks, speed, finishPartial, listener);
      run.go();
      return run;
   }

   private void go() {
      RUNS.add(this);
      Background.quiet(new Thread(this::pump, "Chunk Atlas generate")).start();
      LOG.info("Chunk Atlas: generating {} chunks in {} ({})", this.size, Ids.of(this.level.dimension()), this.speed.name().toLowerCase(java.util.Locale.ROOT));
   }

   public long[] flying() {
      return this.flyingNow;
   }

   public static ChunkGeneration current(MinecraftServer server) {
      ChunkGeneration now = current;
      return now != null && now.server == server ? now : null;
   }

   public String dimension() {
      return Ids.of(this.level.dimension());
   }

   public Speed speed() {
      return this.speed;
   }

   public UUID player() {
      return this.player;
   }

   public State state() {
      return this.state;
   }

   public void pause(boolean on) {
      this.pauseAsked = on;
   }

   public void stop() {
      this.stopAsked = true;
   }

   public Progress progress() {
      return new Progress(this.state, this.done, this.size - this.leftAlone, this.leftAlone, this.secondsLeft);
   }

   public double seconds() {
      long end = this.endedAt == 0L ? System.nanoTime() : this.endedAt;
      return (end - this.startedAt) / 1.0E9;
   }

   public ChunkSelection takeDealt() {
      synchronized (this.dealt) {
         ChunkSelection out = this.dealt.copy();
         this.dealt.clear();
         return out;
      }
   }

   private void pump() {
      this.nearestFirst();
      long drainUntil = 0L;
      while (true) {
         long t0 = System.nanoTime();
         try {
            this.server.submit(this::step).get(5L, TimeUnit.SECONDS);
         } catch (TimeoutException e) {
         } catch (Exception e) {
            LOG.warn("Chunk Atlas: generating stopped: {}", e.toString());
            this.ended(State.STOPPED);
            break;
         }

         if (!this.server.isRunning()) {
            this.ended(State.STOPPED);
            break;
         }

         if (this.state.ended()) {
            drainUntil = drainUntil == 0L ? System.nanoTime() + DRAIN_NANOS : drainUntil;
            if (System.nanoTime() > drainUntil) {
               break;
            }
         }

         long wait = STEP_NANOS - (System.nanoTime() - t0);
         if (wait > 0L) {
            try {
               Thread.sleep(wait / 1_000_000L, (int)(wait % 1_000_000L));
            } catch (InterruptedException e) {
               break;
            }
         }
      }

      synchronized (ChunkGeneration.class) {
         if (current == this) {
            current = null;
         }
      }

      RUNS.remove(this);
   }

   private void ended(State how) {
      if (!this.state.ended()) {
         this.state = how;
         this.endedAt = System.nanoTime();
         this.flyingNow = new long[0];
         LOG.info("Chunk Atlas: generating {}: {} chunks made, {} left alone, {} failed, in {} s", how == State.DONE ? "done" : "stopped", this.done, this.leftAlone,
            this.failed, Math.round(this.seconds()));
      }
   }

   private void step() {
      try {
         if (!this.server.isRunning()) {
            this.ended(State.STOPPED);
            return;
         }

         this.unloadIfIdle();
         if (this.state.ended()) {
            return;
         }

         ServerPlayer p = this.player == null ? null : this.server.getPlayerList().getPlayer(this.player);
         if (this.playerWas == null && p != null) {
            this.playerWas = p.level().dimension();
         }

         if (this.stopAsked || this.player != null && (p == null || !p.level().dimension().equals(this.playerWas))) {
            this.letGoAll();
            this.flyingNow = new long[0];
            this.ended(State.STOPPED);
            return;
         }

         this.collect();
         this.check();
         this.state = this.pauseAsked ? State.PAUSED : State.RUNNING;

         if (!this.pauseAsked && (this.finishPartial || this.next >= this.order.length && this.checking == 0)) {
            this.request();
         }

         this.measure();
         this.showFlying();
         if (this.next >= this.order.length && this.checking == 0 && this.ready.isEmpty() && this.flying.isEmpty()) {
            this.ended(State.DONE);
         }
      } catch (RuntimeException e) {
         LOG.warn("Chunk Atlas: generating failed", e);
         this.letGoAll();
         this.ended(State.STOPPED);
      } finally {
         if (this.listener != null) {
            try {
               this.listener.stepped(this);
            } catch (RuntimeException e) {
               LOG.warn("Chunk Atlas: couldn't report generating: {}", e.toString());
            }
         }
      }
   }

   private void collect() {
      for (long[] f = this.finished.poll(); f != null; f = this.finished.poll()) {
         if (this.flying.remove(f[0])) {
            Tickets.release(this.level, this.ticket, f[0]);
            this.done++;
            if (f[1] == 0L) {
               this.failed++;
            }

            this.deal(f[0]);
         }
      }
   }

   private void check() {
      for (long[] c = this.checked.poll(); c != null; c = this.checked.poll()) {
         this.checking--;
         if (c[1] != 0L) {
            this.ready.enqueue(c[0]);
         } else {
            this.leftAlone++;
            this.deal(c[0]);
         }
      }

      int ahead = Math.max(256, this.speed.inFlight() * 4);
      int queued = this.finishPartial ? this.ready.size() : 0;
      while (this.next < this.order.length && this.checking + queued < ahead) {
         long pos = this.positions[(int)(this.order[this.next++] & 0x3FFFFFL)];
         if (Tickets.full(this.level, pos)) {
            this.leftAlone++;
            this.deal(pos);
            continue;
         }

         CollectFields status = new CollectFields(new FieldSelector(StringTag.TYPE, "Status"));
         this.checking++;
         this.level.getChunkSource().chunkScanner().scanChunk(Pos.unpack(pos), status).whenComplete((v, e) -> {
            boolean generate = false;
            try {
               if (e == null) {
                  Tag found = status.getResult();
                  generate = !(found instanceof CompoundTag tag) || this.finishPartial && !ChunkIo.savedFull(tag);
               }
            } finally {
               this.checked.add(new long[]{pos, generate ? 1L : 0L});
            }
         });
      }
   }

   private void request() {
      while (this.flying.size() < this.speed.inFlight() && !this.ready.isEmpty()) {
         long pos = this.ready.dequeueLong();
         this.flying.add(pos);
         CompletableFuture<Boolean> made;
         try {
            made = Tickets.load(this.level, this.ticket, pos);
         } catch (RuntimeException e) {
            this.finished.add(new long[]{pos, 0L});
            continue;
         }

         made.whenComplete((r, e) -> this.finished.add(new long[]{pos, e == null && Boolean.TRUE.equals(r) ? 1L : 0L}));
      }
   }

   private void showFlying() {
      long[] out = new long[Math.min(FLYING_SHOWN, this.flying.size())];
      int n = 0;
      for (long pos : this.flying) {
         if (n == out.length) {
            break;
         }

         out[n++] = pos;
      }

      this.flyingNow = out;
   }

   private void letGoAll() {
      for (long pos : this.flying) {
         Tickets.release(this.level, this.ticket, pos);
      }

      this.flying.clear();
      this.ready.clear();
   }

   private void deal(long pos) {
      synchronized (this.dealt) {
         this.dealt.add(ChunkPos.getX(pos), ChunkPos.getZ(pos));
      }
   }

   private void measure() {
      long now = System.nanoTime();
      if (this.pauseAsked) {
         this.pausedBefore = true;
         return;
      }

      if (this.pausedBefore) {
         this.pausedBefore = false;
         this.pace.resume(now, this.done);
      }

      this.pace.sample(now, this.done);
      this.secondsLeft = this.pace.secondsLeft(now, this.size - this.leftAlone - this.done);
   }

   private void unloadIfIdle() {
      long now = System.nanoTime();
      int tick = this.server.getTickCount();
      if (tick != this.lastTick) {
         this.lastTick = tick;
         this.lastTickAt = now;
         return;
      }

      if (now - this.lastTickAt < IDLE_NANOS || unloadFailed || this.firstOnServer() != this) {
         return;
      }

      long until = now + (this.speed == Speed.FAST ? 12_000_000L : 6_000_000L);
      try {
         Tickets.unloadNow(this.level, () -> System.nanoTime() < until);
      } catch (RuntimeException e) {
         LOG.warn("Chunk Atlas: couldn't unload generated chunks while the game is paused: {}", e.toString());
         unloadFailed = true;
      }
   }

   private ChunkGeneration firstOnServer() {
      for (ChunkGeneration r : RUNS) {
         if (r.server == this.server && !r.state.ended()) {
            return r;
         }
      }

      return this;
   }

   private void nearestFirst() {
      long[] pos = new long[this.size];
      int[] n = {0};
      int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
      this.chosen.forEach((x, z) -> {
         pos[n[0]++] = Pos.pack(x, z);
         bounds[0] = Math.min(bounds[0], x);
         bounds[1] = Math.min(bounds[1], z);
         bounds[2] = Math.max(bounds[2], x);
         bounds[3] = Math.max(bounds[3], z);
      });
      this.positions = pos;
      this.order = order(pos, (bounds[0] + (long)bounds[2]) / 2.0, (bounds[1] + (long)bounds[3]) / 2.0);
   }

   private static long[] order(long[] pos, double midX, double midZ) {
      long[] keys = new long[pos.length];
      for (int i = 0; i < pos.length; i++) {
         double dx = ChunkPos.getX(pos[i]) - midX;
         double dz = ChunkPos.getZ(pos[i]) - midZ;
         long d = Math.min((1L << 40) - 1L, (long)(dx * dx + dz * dz));
         keys[i] = d << 22 | i;
      }

      Arrays.sort(keys);
      return keys;
   }
}
