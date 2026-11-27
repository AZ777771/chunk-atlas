package dev.chunkatlas.gui;

import dev.chunkatlas.engine.IdText;
import dev.chunkatlas.engine.WorldLock;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.ChunkAtlas;
import dev.chunkatlas.GameColours;
import dev.chunkatlas.InGame;
import dev.chunkatlas.Settings;
import dev.chunkatlas.engine.ChunkClips;
import dev.chunkatlas.engine.ChunkCopy;
import dev.chunkatlas.engine.ChunkEdits;
import dev.chunkatlas.engine.ChunkFilter;
import dev.chunkatlas.engine.ChunkRule;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.ChunkTags;
import dev.chunkatlas.engine.GameVersions;
import dev.chunkatlas.engine.LevelMarkers;
import dev.chunkatlas.engine.LiveRegion;
import dev.chunkatlas.engine.Message;
import dev.chunkatlas.engine.Protection;
import dev.chunkatlas.engine.RegionFacts;
import dev.chunkatlas.engine.Safe;
import dev.chunkatlas.engine.SelectionFiles;
import dev.chunkatlas.engine.Tile;
import dev.chunkatlas.engine.TileRenderer;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.engine.WorldMap;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Keys;
import dev.chunkatlas.port.Tex;
import dev.chunkatlas.port.Version;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class MapScreen extends AtlasScreen {
   private static final int SELECTED = 0x8FB8FF;

   private static final int PASTE = 0xC8DCFF;

   static final int NOT_MADE = 0xFF14161B;

   private static final int GONE = NOT_MADE & 0xFFFFFF;

   private static final int DESELECT = 0xFF6B6B;
   private static final int REGION = 512;
   private static final double MAX_ZOOM = 16.0;
   private static final long PIXEL_BUDGET = 25165824L;

   private static final int UPLOAD_BUDGET = 4194304;

   private long tUnder;
   private long tPartial;
   private long tOver;

   private static final int COARSE = 4;
   private final Minecraft mc = Minecraft.getInstance();
   private final Screen parent;
   private final Path worldFolder;
   private final InGame.Session live;
   private final Path home;
   private final GameColours colours = GameColours.get();
   private final List<Ui.Hit> hits = new ArrayList<>();
   private final List<Ui.Hit> top = new ArrayList<>();
   private ExecutorService files;
   private int run;
   private boolean loading;
   private boolean keep;
   private boolean reload;
   private String name;
   private WorldMap map;
   private WorldMap.Index index;
   private String dimension;
   private Optional<ChunkEdits.Edit> lastEdit = Optional.empty();
   private MapScreen.Job job;
   private double cx;
   private double cz;
   private double zoom = 1.0;
   private double zoomTarget = 1.0;
   private double anchorX;
   private double anchorY;
   private boolean placed;
   private long lastFrame;
   private int mapTop;
   private int mapBottom;

   private int mapLeft;
   private int mapRight;
   private final Map<Long, MapScreen.Held> tiles = new HashMap<>();
   private final Map<Long, RegionFacts> facts = new HashMap<>();
   private long heldPixels;
   private List<Long> asked = List.of();
   private int askedLevel = -1;
   private final Map<String, ChunkSelection> selections = new HashMap<>();

   private final Map<String, ChunkSelection> guarded = new HashMap<>();
   private MapScreen.Tool tool = MapScreen.Tool.CHUNKS;
   private static final int[] BRUSH_SIZES = {0, 1, 2, 4, 8};
   private int brush = 1;
   private MapScreen.View view = MapScreen.View.TERRAIN;
   private final Map<Long, MapScreen.Painted> overlays = new HashMap<>();
   private int viewX;
   private MapScreen.Drag drag = MapScreen.Drag.NONE;
   private boolean dragMoved;
   private double dragX0;
   private double dragY0;
   private double dragX1;
   private double dragY1;
   private boolean space;
   private MapScreen.Menu menu = MapScreen.Menu.NONE;
   private int menuX;
   private int menuY;
   private int menuW;
   private int menuH;

   private int[] menuFrom = new int[4];

   private boolean editing;
   private boolean showPlayers = Settings.players();
   private boolean showProtected = Settings.protectedShown();
   private boolean showGrid = Settings.grid();

   private boolean hideControls;

   private boolean follow;

   private ChunkSelection pasteChunks;
   private ChunkClips.Clip pasteClip;

   private WorldLayout.Dimension pasteSourceDim;

   private Object[] pendingPaste;
   private int pasteAnchorX;
   private int pasteAnchorZ;
   private int pasteDx;
   private int pasteDz;
   private ChunkSelection pasteShown;
   private int pasteShownDx = Integer.MIN_VALUE;
   private int pasteShownDz;
   private Boolean clipboardReady;

   private boolean pasteKeep;

   private int pasteDy;

   private final PasteView pasteView = new PasteView(this);

   private final PasteView blendView = new PasteView(this);
   private final PasteBar pasteBar = new PasteBar();

   private int[] pasteHeld;

   private volatile dev.chunkatlas.engine.PasteTerrain.Lift lastSmart;

   private int caveY = Integer.MIN_VALUE;

   private final List<MapScreen.Pending> pending = new ArrayList<>();

   private final EditCaches caches = new EditCaches();

   private record Pending(
      ChunkEdits.Kind kind, java.util.function.BiFunction<ChunkEdits.Context, ChunkEdits.Progress, ChunkEdits.Result> work, boolean offerUndo, ChunkSelection affected,
      Runnable drop
   ) {
   }

   private double[] measureFrom;
   private boolean caveAuto;

   private boolean caveFollow;
   private int dockTop;
   private String banner;
   private int bannerColour;
   private long bannerUntil;
   static boolean tourDeaf;

   CopyChunks.Pick pick;
   private final SeedMapLayer seed = new SeedMapLayer(this);

   private final LoadedLayer loaded = new LoadedLayer(this);
   private final SpawnLayer spawnable = new SpawnLayer(this);

   private final GenerateView generate = new GenerateView(this);

   private final LiveTiles liveTiles = new LiveTiles(this);

   private SeedPanel seedPanel;

   private TasksPanel tasksPanel;

   public MapScreen(Screen parent, Path worldFolder) {
      this(parent, worldFolder, null);
   }

   public MapScreen(Screen parent, Path worldFolder, InGame.Session live) {
      super(Component.translatable("chunkatlas.map.title"));
      this.parent = parent;
      this.worldFolder = worldFolder;
      this.live = live;
      this.home = ChunkAtlas.home();
      this.name = worldFolder.getFileName() == null ? worldFolder.toString() : worldFolder.getFileName().toString();
      GameVersions.learn(Version.name(), Version.dataVersion());
   }

   protected void init() {
      this.keep = false;
      if (this.pick != null) {
         this.editing = true;
      }

      this.layout();
      if (this.reload) {
         this.reload = false;
         this.letGo();
      }

      if (this.map == null && !this.loading) {
         this.start();
      }
   }

   private void start() {
      this.loading = true;
      int mine = ++this.run;
      if (this.files == null) {
         this.files = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Chunk Atlas files");
            t.setDaemon(true);
            return t;
         });
      }

      String wanted = this.dimension;
      Path world = this.worldFolder;

      String playing = this.live != null && this.mc.player != null ? Ids.of(this.mc.player.level().dimension()) : null;

      MapScreen.Kept k = takeKept(world);
      if (k != null && playing != null && k.map().layout().dimension(playing).isEmpty()) {
         k.map().close();
         k = null;
      }

      if (this.live != null && k != null) {
         this.loading = false;
         this.map = k.map();
         this.name = k.name() != null ? k.name() : this.name;
         this.lastEdit = k.lastEdit();
         this.dimension = playing != null ? playing : wanted != null && k.map().layout().dimension(wanted).isPresent() ? wanted : k.dimension();
         this.index = this.dimension == null ? null : k.map().index(this.dimension);
         this.autoCave();
         if (!this.placed && this.index != null) {
            this.place(null);
         }

         this.refreshAfterSave(mine);
         return;
      }

      this.files
         .submit(
            () -> {
               long o0 = System.nanoTime();
               WorldMap first = WorldMap.open(world, this.home, this.colours);
               if (playing != null && first.layout().dimension(playing).isEmpty()) {
                  try {
                     this.live.saved().get(30L, TimeUnit.SECONDS);
                  } catch (Exception e) {
                  }

                  first.close();
                  first = WorldMap.open(world, this.home, this.colours);
               }

               WorldMap opened = first;
               long o1 = System.nanoTime();
               opened.style(dev.chunkatlas.Settings.lookBits());
               String title = null;
               List<Message> notes = new ArrayList<>();
               Optional<ChunkEdits.Edit> last = Optional.empty();
               String dim = null;
               WorldMap.Index idx = null;

               try {
                  WorldLayout.Layout layout = opened.layout();
                  boolean here = layout.note() != WorldLayout.Note.AWAY && !Safe.away(world.resolve("level.dat"));
                  title = opened.fromLiveMap() ? LiveRegion.title(world) : here ? LevelMarkers.name(world) : null;
                  if (here && this.live == null && ChunkEdits.pending(this.home, world)) {
                     Optional<List<Message>> sorted = WorldLock.with(world, held -> ChunkEdits.recover(world, new ChunkEdits.Context(this.home, held)));
                     notes.addAll(sorted.orElse(List.of(Message.of("chunkatlas.map.cut_short_open_elsewhere"))));
                  }

                  last = here ? ChunkEdits.last(this.home, world) : Optional.empty();
                  if (playing != null && layout.dimension(playing).isPresent()) {
                     dim = playing;
                  } else if (wanted != null && layout.dimension(wanted).isPresent()) {
                     dim = wanted;
                  } else {
                     String player = opened.markers().player().map(LevelMarkers.Point::dimension).orElse(null);
                     if (player != null && layout.dimension(player).isPresent()) {
                        dim = player;
                     } else if (!layout.dimensions().isEmpty()) {
                        dim = layout.dimensions().get(0).id();
                     }
                  }

                  idx = dim == null ? null : opened.index(dim);
                  DevTour.slowFrame("opening: world " + (o1 - o0) / 1000000L + " ms, index " + (System.nanoTime() - o1) / 1000000L + " ms");
               } catch (RuntimeException e) {
                  notes.add(Message.of("chunkatlas.map.part_unreadable"));
               }

               String t = title;
               String d = dim;
               WorldMap.Index i = idx;
               Optional<ChunkEdits.Edit> l = last;
               this.mc.execute(() -> {
                  if (mine != this.run) {
                     opened.close();
                  } else {
                     this.loading = false;
                     this.map = opened;
                     if (t != null) {
                        this.name = t;
                     }

                     this.lastEdit = l;
                     this.dimension = d;
                     this.index = i;
                     this.autoCave();
                     if (!this.placed && i != null) {
                        this.place(null);
                     }

                     this.refreshAfterSave(mine);

                     if (!notes.isEmpty()) {
                        List<String> said = new ArrayList<>();
                        for (Message note : notes) {
                           said.add(Ui.t(note));
                        }

                        this.say(String.join(" ", said), Ui.YELLOW);
                     }
                  }
               });
            }
         );
   }

   private record Kept(Path world, WorldMap map, String name, String dimension, Optional<ChunkEdits.Edit> lastEdit) {
   }

   private static MapScreen.Kept kept;

   private static MapScreen.Kept takeKept(Path world) {
      MapScreen.Kept k = kept;
      kept = null;
      if (k != null && !k.world().equals(world)) {
         k.map().close();
         return null;
      }

      return k;
   }

   public static void prepare(Path world) {
      MapScreen.Kept k = kept;
      if (k != null && k.world().equals(world)) {
         return;
      }

      Thread.ofVirtual().start(() -> {
         try {
            Thread.sleep(1000L);
            WorldMap opened = WorldMap.open(world, ChunkAtlas.home(), GameColours.get());
            opened.style(dev.chunkatlas.Settings.lookBits());
            WorldLayout.Layout layout = opened.layout();
            String dim = opened.markers().player().map(LevelMarkers.Point::dimension).filter(d -> layout.dimension(d).isPresent())
               .orElse(layout.dimensions().isEmpty() ? null : layout.dimensions().get(0).id());
            if (dim != null) {
               opened.index(dim);
            }

            String title = LevelMarkers.name(world);
            Optional<ChunkEdits.Edit> last = ChunkEdits.last(ChunkAtlas.home(), world);
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
               if (kept == null && net.minecraft.client.Minecraft.getInstance().getSingleplayerServer() != null) {
                  kept = new MapScreen.Kept(world, opened, title, dim, last);
               } else {
                  opened.close();
               }
            });
         } catch (InterruptedException | RuntimeException ignored) {
         }
      });
   }

   public static WorldMap keptMap(Path world) {
      MapScreen.Kept k = kept;
      return k != null && k.world().equals(world) ? k.map() : null;
   }

   public static void forgetKept() {
      MapScreen.Kept k = kept;
      kept = null;
      if (k != null) {
         k.map().close();
      }
   }

   private void refreshAfterSave(int mine) {
      if (this.live == null || this.map == null || this.dimension == null) {
         return;
      }

      WorldMap m = this.map;
      String dim = this.dimension;

      this.live.saved().copy().completeOnTimeout(null, 4L, TimeUnit.SECONDS).whenComplete((done, failed) -> {
         if (mine == this.run) {
            this.submit(() -> this.readAfterSave(m, dim, mine));
         }
      });
      java.util.concurrent.CompletableFuture.runAsync(() -> {
         if (mine == this.run) {
            this.submit(() -> this.readAfterSave(m, dim, mine));
         }
      }, java.util.concurrent.CompletableFuture.delayedExecutor(12L, TimeUnit.SECONDS));
   }

   private void readAfterSave(WorldMap m, String dim, int mine) {
      WorldMap.Index fresh;
      try {
         fresh = m.index(dim);
      } catch (RuntimeException e) {
         return;
      }

      this.mc.execute(() -> {
         if (mine == this.run && this.map == m && dim.equals(this.dimension)) {
            this.index = fresh;
         }
      });
   }

   private void letGo() {
      this.run++;
      this.loading = false;
      this.job = null;
      this.dropTiles();
      WorldMap old = this.map;
      this.map = null;
      this.index = null;
      if (old != null) {
         old.close();
      }
   }

   void release() {
      this.letGo();
      this.reload = true;
   }

   void edited(String message, boolean changed) {
      if (changed) {
         this.selections.clear();
      }

      this.reload = true;
      this.say(message, changed ? Ui.GREEN : Ui.YELLOW);
   }

   void dispose() {
      this.pasteView.dispose();
      this.blendView.dispose();
      this.seed.stop();
      this.loaded.release();
      this.spawnable.release();
      if (this.live != null && this.map != null && !this.reload && !this.map.fromLiveMap()) {
         forgetKept();
         kept = new MapScreen.Kept(this.worldFolder, this.map, this.name, this.dimension, this.lastEdit);
         this.map = null;
      }

      this.letGo();
      if (this.files != null) {
         this.files.shutdown();
         this.files = null;
      }

      if (this.customThreads != null) {
         this.customThreads.shutdownNow();
         this.customThreads = null;
      }
   }

   public void removed() {
      if (!this.keep) {
         this.dispose();
      }
   }

   public void onClose() {
      if (!this.pending.isEmpty() && this.live != null) {
         this.applyPending();
         return;
      }

      Game.open(this.mc, backTo(this.parent));
   }

   private static Screen backTo(Screen parent) {
      if (parent instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen list) {
         return new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(list.lastScreen);
      }

      return parent;
   }

   boolean queue(
      ChunkEdits.Plan plan, java.util.function.BiFunction<ChunkEdits.Context, ChunkEdits.Progress, ChunkEdits.Result> work, boolean offerUndo, Runnable drop
   ) {
      if (this.live == null || plan == null) {
         return false;
      }

      ChunkSelection affected = new ChunkSelection();
      for (ChunkEdits.FilePlan f : plan.files()) {
         if ("region".equals(f.role())) {
            for (int i : f.indices()) {
               affected.add(f.rx() * 32 + (i & 31), f.rz() * 32 + (i >> 5));
            }
         }
      }

      this.pending.add(new MapScreen.Pending(plan.kind(), work, offerUndo, affected, drop));
      ChunkSelection sel = this.selections.get(this.dimension);
      if (sel != null) {
         sel.clear();
      }

      this.say(Ui.plural("chunkatlas.pending.queued", this.pending.size()), Ui.GREEN);
      return true;
   }

   private void undoPending() {
      if (!this.pending.isEmpty()) {
         this.pending.remove(this.pending.size() - 1).drop().run();
         this.say(this.pending.isEmpty() ? Ui.t("chunkatlas.pending.none") : Ui.plural("chunkatlas.pending.queued", this.pending.size()), Ui.GREY);
      }
   }

   private void applyPending() {
      List<MapScreen.Pending> list = List.copyOf(this.pending);
      this.pending.clear();
      boolean undoable = list.stream().anyMatch(MapScreen.Pending::offerUndo);
      this.dispose();
      LeaveAndEditScreen.start(this.home, this.worldFolder, this.live.levelId(), Ui.plural("chunkatlas.pending.applying", list.size()), undoable, (ctx, progress) -> {
         ChunkEdits.Result last = null;
         try {
            for (MapScreen.Pending p : list) {
               last = p.work().apply(ctx, progress);
               if (last == null || !last.ok()) {
                  return last;
               }
            }
         } finally {
            for (MapScreen.Pending p : list) {
               p.drop().run();
            }
         }

         return last;
      }, r -> Ui.plural("chunkatlas.pending.done", list.size()));
   }

   public boolean isPauseScreen() {
      return !tourKeepRunning && !dev.chunkatlas.Generating.keepsWorldRunning();
   }

   static boolean tourKeepRunning;

   private void show(String dim) {
      if (this.map != null && !dim.equals(this.dimension) && this.job == null) {
         String from = this.dimension;
         this.dropTiles();
         this.index = null;
         this.dimension = dim;
         this.autoCave();
         this.drag = MapScreen.Drag.NONE;
         WorldMap m = this.map;
         int mine = this.run;
         this.submit(() -> {
            WorldMap.Index idx;
            try {
               idx = m.index(dim);
            } catch (RuntimeException e) {
               idx = null;
            }

            WorldMap.Index i = idx;
            this.mc.execute(() -> {
               if (mine == this.run && dim.equals(this.dimension)) {
                  this.index = i;
                  if (i == null) {
                     this.say(Ui.t("chunkatlas.map.dimension_unreadable"), Ui.YELLOW);
                  } else {
                     this.place(from);
                  }
               }
            });
         });
      }
   }

   private void submit(Runnable work) {
      try {
         if (this.files != null) {
            this.files.submit(work);
         }
      } catch (RejectedExecutionException ignored) {
      }
   }

   private void place(String from) {
      WorldMap.Index i = this.index;
      double w = Math.max(1, this.width);
      double h = Math.max(1, this.mapBottom - this.mapTop);
      double midX = (i.minRx() + i.maxRx() + 1) * 256.0;
      double midZ = (i.minRz() + i.maxRz() + 1) * 256.0;
      if (this.placed && from != null && from.equals("minecraft:overworld") && this.dimension.equals("minecraft:the_nether")) {
         this.cx /= 8.0;
         this.cz /= 8.0;
      } else if (this.placed && from != null && from.equals("minecraft:the_nether") && this.dimension.equals("minecraft:overworld")) {
         this.cx *= 8.0;
         this.cz *= 8.0;
      } else if (this.live != null && this.mc.player != null && Ids.of(this.mc.player.level().dimension()).equals(this.dimension)) {
         this.cx = this.mc.player.getX();
         this.cz = this.mc.player.getZ();
         this.zoom = this.clampZoom(2.0);
      } else {
         double fit = Math.min(w / ((i.maxRx() - i.minRx() + 1) * 512.0), h / ((i.maxRz() - i.minRz() + 1) * 512.0)) * 0.92;
         LevelMarkers.Point at = this.marker(this.map.markers().player());
         if (at == null) {
            at = this.marker(this.map.markers().spawn());
         }

         if (!(fit >= 0.5) && at != null && !i.isEmpty()) {
            this.cx = at.x();
            this.cz = at.z();
            this.zoom = 2.0;
         } else {
            this.cx = midX;
            this.cz = midZ;
            this.zoom = this.clampZoom(Math.min(fit, 2.0));
         }
      }

      this.zoomTarget = this.zoom;
      this.placed = true;
      this.clampView();
   }

   private LevelMarkers.Point marker(Optional<LevelMarkers.Point> point) {
      return point.filter(p -> p.dimension().equals(this.dimension)).orElse(null);
   }

   private void layout() {
      this.mapLeft = 0;
      this.mapRight = this.width;
      this.mapTop = 0;
      this.mapBottom = this.height;
   }

   private double mapCx() {
      return this.width / 2.0;
   }

   private double mapCy() {
      return (this.mapTop + this.mapBottom) / 2.0;
   }

   private double blockX(double screenX) {
      return this.cx + (screenX - this.mapCx()) / this.zoom;
   }

   private double blockZ(double screenY) {
      return this.cz + (screenY - this.mapCy()) / this.zoom;
   }

   private double screenX(double block) {
      return this.mapCx() + (block - this.cx) * this.zoom;
   }

   private double screenY(double block) {
      return this.mapCy() + (block - this.cz) * this.zoom;
   }

   private double clampZoom(double z) {
      double min = 24.0 / Math.max(1, this.mc.getWindow().getGuiScale()) / 512.0;
      return Math.max(min, Math.min(MAX_ZOOM, z));
   }

   private void clampView() {
      WorldMap.Index i = this.index;

      if (this.seed.unbounded() || this.pasteChunks != null) {
         this.cx = Math.max(-3.0E7, Math.min(3.0E7, this.cx));
         this.cz = Math.max(-3.0E7, Math.min(3.0E7, this.cz));
      } else if (i != null && !i.isEmpty()) {
         int[] b = this.liveTiles.bounds();
         this.cx = Math.max(Math.min(i.minRx(), b == null ? i.minRx() : b[0]) * 512.0, Math.min((Math.max(i.maxRx(), b == null ? i.maxRx() : b[2]) + 1) * 512.0, this.cx));
         this.cz = Math.max(Math.min(i.minRz(), b == null ? i.minRz() : b[1]) * 512.0, Math.min((Math.max(i.maxRz(), b == null ? i.maxRz() : b[3]) + 1) * 512.0, this.cz));
      }
   }

   private void followCave() {
      if (!this.caveFollow || this.caveY == Integer.MIN_VALUE || this.live == null || this.mc.player == null) {
         return;
      }

      if (!Ids.of(this.mc.player.level().dimension()).equals(this.dimension)) {
         return;
      }

      int y = this.mc.player.getBlockY() + 1;
      if (Math.abs(y - this.caveY) >= 3) {
         this.setCave(y, false);
      }
   }

   private void ease(float dt) {
      this.caveUnderground();
      this.followCave();
      if (this.zoom != this.zoomTarget) {
         double bx = this.blockX(this.anchorX);
         double bz = this.blockZ(this.anchorY);
         double k = Settings.smoothZoom() ? 1.0 - Math.exp(-dt * 16.0F) : 1.0;
         this.zoom = Math.exp(Math.log(this.zoom) + (Math.log(this.zoomTarget) - Math.log(this.zoom)) * k);
         if (Math.abs(this.zoom / this.zoomTarget - 1.0) < 0.003) {
            this.zoom = this.zoomTarget;
         }

         this.cx = bx - (this.anchorX - this.mapCx()) / this.zoom;
         this.cz = bz - (this.anchorY - this.mapCy()) / this.zoom;
         this.clampView();
      }
   }

   private int level() {
      double f = this.zoomTarget * this.mc.getWindow().getGuiScale();

      int lv;
      for (lv = 0; lv < 4 && f < 0.75; lv++) {
         f *= 2.0;
      }

      return lv;
   }

   private List<WorldLayout.RegionInfo> visible(int margin) {
      return this.visible(this.cx, this.cz, this.zoom, margin);
   }

   private List<WorldLayout.RegionInfo> visibleAtTarget(int margin) {
      if (this.zoom == this.zoomTarget) {
         return this.visible(margin);
      }

      double bx = this.blockX(this.anchorX);
      double bz = this.blockZ(this.anchorY);
      double tcx = bx - (this.anchorX - this.mapCx()) / this.zoomTarget;
      double tcz = bz - (this.anchorY - this.mapCy()) / this.zoomTarget;
      return this.visible(tcx, tcz, this.zoomTarget, margin);
   }

   private List<WorldLayout.RegionInfo> visible(double cx, double cz, double zoom, int margin) {
      WorldMap.Index i = this.index;
      List<WorldLayout.RegionInfo> out = new ArrayList<>();
      int rx0 = Math.max(i.minRx(), Math.floorDiv((int)Math.floor(cx + (0.0 - this.mapCx()) / zoom), REGION) - margin);
      int rx1 = Math.min(i.maxRx(), Math.floorDiv((int)Math.floor(cx + (this.width - this.mapCx()) / zoom), REGION) + margin);
      int rz0 = Math.max(i.minRz(), Math.floorDiv((int)Math.floor(cz + (this.mapTop - this.mapCy()) / zoom), REGION) - margin);
      int rz1 = Math.min(i.maxRz(), Math.floorDiv((int)Math.floor(cz + (this.mapBottom - this.mapCy()) / zoom), REGION) + margin);
      if (rx1 >= rx0 && rz1 >= rz0) {
         Map<Long, WorldLayout.RegionInfo> regions = i.regions();
         long area = (long)(rx1 - rx0 + 1) * (rz1 - rz0 + 1);
         if (area <= regions.size() * 2L) {
            for (int rz = rz0; rz <= rz1; rz++) {
               for (int rx = rx0; rx <= rx1; rx++) {
                  WorldLayout.RegionInfo r = regions.get(WorldLayout.key(rx, rz));
                  if (r != null) {
                     out.add(r);
                  }
               }
            }
         } else {
            for (WorldLayout.RegionInfo r : regions.values()) {
               if (r.rx() >= rx0 && r.rx() <= rx1 && r.rz() >= rz0 && r.rz() <= rz1) {
                  out.add(r);
               }
            }
         }

         return out;
      } else {
         return out;
      }
   }

   public void tick() {
      this.seed.tick();
      this.generate.tick();
      String taskProblem = dev.chunkatlas.TaskClient.takeProblem();
      if (taskProblem != null) {
         this.seedTell(taskProblem, Ui.YELLOW);
      }

      this.syncLook();
      if (this.map != null && this.index != null && !this.index.isEmpty()) {
         int lv = this.level();

         List<WorldLayout.RegionInfo> near = this.visible(1);
         List<WorldLayout.RegionInfo> target = this.visibleAtTarget(1);
         List<Long> want = new ArrayList<>();
         List<Long> empty = new ArrayList<>();
         Set<Long> seen = new HashSet<>();

         for (WorldLayout.RegionInfo r : target) {
            long key = WorldLayout.key(r.rx(), r.rz());
            seen.add(key);
            MapScreen.Held held = this.tiles.get(key);
            if (held == null || held.stale() || held.level() > lv) {
               want.add(key);
            }

            if (held == null && !this.backups.containsKey(key)) {
               empty.add(key);
            }
         }

         for (WorldLayout.RegionInfo r : near) {
            long key = WorldLayout.key(r.rx(), r.rz());
            if (seen.add(key) && !this.tiles.containsKey(key) && !this.backups.containsKey(key)) {
               empty.add(key);
            }
         }

         if (!empty.isEmpty() && lv < COARSE) {
            want = empty;
            lv = COARSE;
         }

         double fx = this.cx / 512.0 - 0.5;
         double fz = this.cz / 512.0 - 0.5;
         want.sort(Comparator.comparingDouble(k -> distance(k, fx, fz)));
         if (lv != this.askedLevel || !want.equals(this.asked)) {
            this.map.request(this.index, lv, want);
            this.asked = want;
            this.askedLevel = lv;
         }

         if (this.heldPixels > PIXEL_BUDGET) {
            List<Long> others = new ArrayList<>();

            for (Long key : this.tiles.keySet()) {
               if (!seen.contains(key)) {
                  others.add(key);
               }
            }

            others.sort(Comparator.<Long>comparingDouble(k -> distance(k, fx, fz)).reversed());

            for (Long key : others) {
               if (this.heldPixels <= PIXEL_BUDGET) {
                  break;
               }

               this.drop(this.tiles.remove(key));
               MapScreen.Painted painted = this.overlays.remove(key);
               if (painted != null) {
                  painted.id().close();
               }
            }
         }

         if (this.backups.size() > MAX_BACKUPS) {
            List<Long> old = new ArrayList<>();
            for (Long key : this.backups.keySet()) {
               if (!seen.contains(key) && !this.tiles.containsKey(key)) {
                  old.add(key);
               }
            }

            old.sort(Comparator.<Long>comparingDouble(k -> distance(k, fx, fz)).reversed());
            for (int k = 0; k < old.size() && this.backups.size() > MAX_BACKUPS; k++) {
               this.backups.remove(old.get(k)).close();
            }
         }
      }
   }

   private final Map<Long, Tex> backups = new HashMap<>();
   private static final int MAX_BACKUPS = 16384;

   private void backup(long key, int[] argb, int size) {
      int side = 512 >> COARSE;
      int step = size / side;
      if (step < 1) {
         return;
      }

      int[] abgr = new int[side * side];
      for (int y = 0; y < side; y++) {
         for (int x = 0; x < side; x++) {
            int r = 0;
            int gr = 0;
            int b = 0;
            int n = 0;
            for (int dy = 0; dy < step; dy++) {
               int row = (y * step + dy) * size + x * step;
               for (int dx = 0; dx < step; dx++) {
                  int c = argb[row + dx];
                  if (c >>> 24 != 0) {
                     r += c >> 16 & 0xFF;
                     gr += c >> 8 & 0xFF;
                     b += c & 0xFF;
                     n++;
                  }
               }
            }

            abgr[y * side + x] = n * 2 < step * step ? 0 : 0xFF000000 | (b / n) << 16 | (gr / n) << 8 | r / n;
         }
      }

      Tex tex = this.backups.get(key);
      if (tex == null) {
         tex = Tex.create("Chunk Atlas map backup", side, side);
         this.backups.put(key, tex);
      }

      tex.putAbgr(abgr);
      tex.upload();
   }

   private static double distance(long key, double fx, double fz) {
      double dx = (int)(key >> 32) - fx;
      double dz = (int)key - fz;
      return dx * dx + dz * dz;
   }

   private void takeTiles() {
      int budget = UPLOAD_BUDGET;

      long deadline = System.nanoTime() + 6_000_000L;

      Tile t;
      while (budget > 0 && System.nanoTime() < deadline && (t = this.map.poll()) != null) {
         if (t.dimension.equals(this.dimension) && t.cave == this.map.cave() && t.style == this.map.style()) {
            long key = WorldLayout.key(t.rx, t.rz);
            if (t.facts != null) {
               this.facts.put(key, t.facts);
            }

            MapScreen.Held old = this.tiles.get(key);

            if (t.stale ? old == null : old == null || old.stale() || old.level() >= t.level) {
               Tex image = Tex.create("Chunk Atlas map", t.size, t.size);

               int[] px = t.argb;
               int[] abgr = new int[px.length];
               for (int k = 0; k < px.length; k++) {
                  int c = px[k];
                  abgr[k] = c & 0xFF00FF00 | (c >> 16 & 0xFF) | (c & 0xFF) << 16;
               }

               image.putAbgr(abgr);

               image.upload();
               Tex id = image;
               this.tiles.put(key, new MapScreen.Held(id, t.level, t.size, t.stale));
               if (!t.stale || !this.backups.containsKey(key)) {
                  this.backup(key, px, t.size);
               }

               this.drawnChunks.put(key, drawnMask(px, t.size));
               if (!t.stale) {
                  this.liveTiles.saved(key, t.regionModified, this.drawnChunks.get(key), this.facts.get(key));
               }

               this.heldPixels = this.heldPixels + (long)t.size * t.size;
               this.drop(old);
               budget -= t.size * t.size;
            }
         }
      }
   }

   private void drop(MapScreen.Held held) {
      if (held != null) {
         held.id().close();
         this.heldPixels = this.heldPixels - (long)held.size() * held.size();
      }
   }

   private void dropTiles() {
      this.liveTiles.clear();
      this.releaseMasks();
      this.drawnChunks.clear();

      this.asked = null;
      this.askedLevel = -1;
      for (MapScreen.Held held : this.tiles.values()) {
         held.id().close();
      }

      this.tiles.clear();
      for (Tex tex : this.backups.values()) {
         tex.close();
      }

      this.backups.clear();
      this.dropOverlays();
      this.facts.clear();
      this.customCache.clear();
      this.customHas = new ChunkSelection();
      this.customHasRegions.clear();
      this.heldPixels = 0L;
      this.asked = List.of();
      this.askedLevel = -1;
   }

   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      if (this.map == null || this.index == null || this.index.isEmpty()) {
         super.drawBackground(g, mouseX, mouseY, partial);
      }
   }

   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (float)(now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      this.hits.clear();
      this.top.clear();
      this.layout();
      if (this.pendingPaste != null && this.index != null && this.map != null && this.job == null) {
         Object[] p = this.pendingPaste;
         this.pendingPaste = null;
         this.startPaste((ChunkClips.Clip)p[0], (ChunkSelection)p[1], (WorldLayout.Dimension)p[2]);
      }

      boolean covered = this.menu != MapScreen.Menu.NONE || this.job != null;
      boolean drawn = this.map != null && this.index != null && !this.index.isEmpty();
      if (this.follow && this.live != null && this.mc.player != null && this.drag != MapScreen.Drag.PAN
         && Ids.of(this.mc.player.level().dimension()).equals(this.dimension)) {
         this.cx = this.mc.player.getX();
         this.cz = this.mc.player.getZ();
         this.anchorX = this.mapCx();
         this.anchorY = this.mapCy();
      }

      if (drawn) {
         this.ease(dt);
         long f0 = System.nanoTime();
         this.takeTiles();
         long f1 = System.nanoTime();
         this.drawMap(g, mouseX, mouseY, covered);
         long f2 = System.nanoTime();
         if (f2 - f0 > 40_000_000L) {
            DevTour.slowFrame("slow frame: tiles " + (f1 - f0) / 1000000L + " ms, map " + (f2 - f1) / 1000000L + " ms (seed under " + this.tUnder / 1000000L
               + ", partial " + this.tPartial / 1000000L + ", over " + this.tOver / 1000000L + ")");
         }
      } else if (!this.underPanel) {
         this.drawNote(g);
      }

      if (!this.hideControls && !this.underPanel) {
         this.drawControls(g, mouseX, mouseY, covered, drawn);
      }

      if (this.banner != null && System.currentTimeMillis() < this.bannerUntil && !this.underPanel) {
         String text = this.fit(this.banner, this.width - 30);
         Ui.chip(g, this.font, text, (this.width - Ui.chipWidth(this.font, text)) / 2, this.messageY(), this.bannerColour);
      }

      if (this.job != null) {
         this.drawJob(g, mouseX, mouseY);
      } else if (this.menu != MapScreen.Menu.NONE) {
         this.drawMenu(g, mouseX, mouseY);
      }

      super.draw(g, mouseX, mouseY, partial);
   }

   public void drawUnder(Gfx g, float partial) {
      this.underPanel = true;
      try {
         this.draw(g, -100, -100, partial);
      } finally {
         this.underPanel = false;
      }
   }

   private boolean underPanel;

   private ChunkSelection preview2;
   private int preview2Tone;

   public void previewAlso(ChunkSelection chunks, int tone) {
      this.preview2 = chunks;
      this.preview2Tone = tone;
   }

   public void centreOn(String dimension, double x, double z, int sx, int sy) {
      if (dimension != null && this.dimension != null && !dimension.equals(this.dimension) && this.map != null && this.map.layout().dimension(dimension).isPresent()) {
         this.show(dimension);
      }

      this.follow = false;
      this.zoomTarget = this.clampZoom(Math.max(this.zoomTarget, 1.0));
      this.zoom = this.zoomTarget;
      double dx = sx < 0 || sy < 0 ? 0.0 : (sx - this.mapCx()) / this.zoom;
      double dz = sx < 0 || sy < 0 ? 0.0 : (sy - this.mapCy()) / this.zoom;
      this.cx = x - dx;
      this.cz = z - dz;
      this.anchorX = this.mapCx();
      this.anchorY = this.mapCy();
      this.clampView();
   }

   public double[] screenOf(String dimension, double x, double z) {
      if (dimension == null || !dimension.equals(this.dimension)) {
         return null;
      }

      return new double[]{this.screenX(x), this.screenY(z)};
   }

   private ChunkSelection preview;
   private int previewTone = SELECTED;

   void preview(ChunkSelection chunks, int tone) {
      this.preview = chunks;
      this.previewTone = tone;
   }

   void clearPreview() {
      this.preview = null;
      this.preview2 = null;
   }

   private int[] outlined;

   void outline(int chunkX, int chunkZ) {
      this.outlined = new int[]{chunkX, chunkZ};
   }

   void clearOutline() {
      this.outlined = null;
   }

   private void drawOutlined(Gfx g) {
      int[] o = this.outlined;
      if (o == null) {
         return;
      }

      int x0 = (int)Math.floor(this.screenX(o[0] * 16.0));
      int y0 = (int)Math.floor(this.screenY(o[1] * 16.0));
      int x1 = (int)Math.floor(this.screenX(o[0] * 16.0 + 16.0));
      int y1 = (int)Math.floor(this.screenY(o[1] * 16.0 + 16.0));
      if (x1 - x0 < 6) {
         int mid = (x0 + x1) / 2;
         x0 = mid - 3;
         x1 = mid + 3;
      }

      if (y1 - y0 < 6) {
         int mid = (y0 + y1) / 2;
         y0 = mid - 3;
         y1 = mid + 3;
      }

      g.fill(x0, y0, x1, y1, 0x30000000 | SELECTED);
      g.outline(x0, y0, x1 - x0, y1 - y0, 0xFF000000 | SELECTED);
      g.outline(x0 + 1, y0 + 1, x1 - x0 - 2, y1 - y0 - 2, 0xFF000000 | SELECTED);
   }

   private void drawMap(Gfx g, int mouseX, int mouseY, boolean covered) {
      WorldMap.Index i = this.index;
      g.fill(this.mapLeft, this.mapTop, this.mapRight, this.mapBottom, NOT_MADE);
      g.scissor(this.mapLeft, this.mapTop, this.mapRight, this.mapBottom);

      if (!this.editing || this.seed.shown()) {
         long s0 = System.nanoTime();
         this.seed.drawUnder(g);
         this.tUnder = System.nanoTime() - s0;
      }
      List<WorldLayout.RegionInfo> seen = this.visible(0);
      int baseRx = Math.floorDiv((int)Math.floor(this.cx), REGION);
      int baseRz = Math.floorDiv((int)Math.floor(this.cz), REGION);
      g.push();
      g.translate((float)(this.mapCx() - (this.cx - baseRx * 512.0) * this.zoom), (float)(this.mapCy() - (this.cz - baseRz * 512.0) * this.zoom));
      g.scale((float)this.zoom, (float)this.zoom);

      for (WorldLayout.RegionInfo r : seen) {
         int x = (r.rx() - baseRx) * REGION;
         int z = (r.rz() - baseRz) * REGION;
         long key = WorldLayout.key(r.rx(), r.rz());
         MapScreen.Held held = this.tiles.get(key);
         if (held != null) {
            g.blit(held.id(), x, z, 0.0F, 0.0F, REGION, REGION, held.size(), held.size(), held.size(), held.size());
         } else {
            Tex backup = this.backups.get(key);
            if (backup != null) {
               int side = backup.width();
               g.blit(backup, x, z, 0.0F, 0.0F, REGION, REGION, side, side, side, side);
            }
         }
      }

      if (!this.editing || this.seed.shown()) {
         long s0 = System.nanoTime();
         this.seed.drawPartial(g);
         this.tPartial = System.nanoTime() - s0;
      }

      this.liveTiles.draw(g, baseRx, baseRz, this.level());
      if (this.view != MapScreen.View.TERRAIN) {
         this.drawOverlay(g, seen, baseRx, baseRz);
      }

      if (!this.editing) {
         this.loaded.draw(g, baseRx, baseRz);
         this.spawnable.draw(g, baseRx, baseRz);
      }

      ChunkSelection guard = this.guarded();

      ChunkSelection sel = this.selections.get(this.dimension);
      List<WorldLayout.RegionInfo> picked = sel == null || sel.isEmpty() ? seen : this.selectionRegions(sel);

      if (this.editing && sel != null && !sel.isEmpty()) {
         this.drawSelection(g, sel, picked, baseRx, baseRz, SELECTED);
      }

      g.pop();

      if (this.showProtected && !guard.isEmpty()) {
         this.drawEdges(g, guard, seen, 0x667EE07E);
      }

      if (sel != null && !sel.isEmpty()) {
         this.drawEdges(g, sel, picked, 0xFF000000 | SELECTED);
      }

      if (this.preview != null && this.previewTone == KEPT && !this.preview.isEmpty()) {
         this.drawEdges(g, this.preview, seen, 0xFF000000 | KEPT);
      }

      if (this.view == MapScreen.View.CUSTOM && this.customMode == CUSTOM_HAS && !this.customHas.isEmpty()) {
         this.drawEdges(g, this.customHas, seen, 0xFF000000 | SELECTED);
      }

      this.drawOutlined(g);
      g.push();
      g.translate((float)(this.mapCx() - (this.cx - baseRx * 512.0) * this.zoom), (float)(this.mapCy() - (this.cz - baseRz * 512.0) * this.zoom));
      g.scale((float)this.zoom, (float)this.zoom);

      if (this.pasteChunks != null) {
         this.drawPaste(g, mouseX, mouseY, baseRx, baseRz);
      }

      if (this.preview2 != null && !this.preview2.isEmpty()) {
         this.drawSelection(g, this.preview2, this.regionsOf(this.preview2), baseRx, baseRz, this.preview2Tone);
      }

      if (this.preview != null && !this.preview.isEmpty()) {
         this.drawSelection(g, this.preview, this.regionsOf(this.preview), baseRx, baseRz, this.previewTone);
      }

      ChunkSelection serverGone = this.serverWaiting.get(this.dimension);
      if (serverGone != null && !serverGone.isEmpty()) {
         this.drawSelection(g, serverGone, this.regionsOf(serverGone), baseRx, baseRz, GONE);
      }

      if (!this.pending.isEmpty()) {
         EditCaches q = this.caches;
         q.queued(this.pending, MapScreen.Pending::affected, p -> p.kind() == ChunkEdits.Kind.DELETE || p.kind() == ChunkEdits.Kind.KEEP_ONLY);
         if (!q.gone.isEmpty()) {
            this.drawSelection(g, q.gone, q.goneRegions, baseRx, baseRz, GONE);
         }

         if (!q.changed.isEmpty()) {
            this.drawSelection(g, q.changed, q.changedRegions, baseRx, baseRz, PASTE);
         }
      }

      g.pop();

      if (this.editing || this.showGrid || this.seed.slimeShown()) {
         this.drawGrid(g, 16, 12, this.editing ? 0x1AFFFFFF : 0x14FFFFFF);
         this.drawGrid(g, REGION, 48, this.editing ? 0x52FFFFFF : 0x38FFFFFF);
      }

      TaskLayer.draw(g, new TaskLayer.View(this.mapCx() - this.cx * this.zoom, this.mapCy() - this.cz * this.zoom, this.zoom, this.mapLeft, this.mapTop, this.mapRight,
         this.mapBottom), this.dimension, this.index, this.tasksShown() ? this.tasksPanel.preview() : null);

      boolean inMap = !covered && mouseY >= this.mapTop && mouseY < this.mapBottom && mouseX >= this.mapLeft && mouseX < this.mapRight;
      boolean selecting = this.editing && !this.space;
      if (this.drag == MapScreen.Drag.REGION_SELECT || this.drag == MapScreen.Drag.REGION_UNSELECT) {
         int[] c = this.dragChunks();
         int x0 = (int)Math.floor(this.screenX(Math.floorDiv(c[0], 32) * 512.0));
         int y0 = (int)Math.floor(this.screenY(Math.floorDiv(c[1], 32) * 512.0));
         int x1 = (int)Math.floor(this.screenX((Math.floorDiv(c[2], 32) + 1) * 512.0));
         int y1 = (int)Math.floor(this.screenY((Math.floorDiv(c[3], 32) + 1) * 512.0));
         int tone = this.drag == MapScreen.Drag.REGION_SELECT ? SELECTED : DESELECT;
         g.fill(x0, y0, x1, y1, 0x30000000 | tone);
         g.outline(x0, y0, Math.max(1, x1 - x0 + 1), Math.max(1, y1 - y0 + 1), 0xFF000000 | tone);
         int regions = (Math.floorDiv(c[2], 32) - Math.floorDiv(c[0], 32) + 1) * (Math.floorDiv(c[3], 32) - Math.floorDiv(c[1], 32) + 1);
         this.dragLabel(g, x0, y0, (this.drag == MapScreen.Drag.REGION_UNSELECT ? "− " : "") + Ui.plural("chunkatlas.map.drag.regions", regions), tone);
      } else if (inMap && selecting && (this.tool == MapScreen.Tool.BRUSH || this.tool == MapScreen.Tool.CHUNKS && !this.regionScale()) && this.drag != MapScreen.Drag.PAN && !this.space) {
         int r = this.tool == MapScreen.Tool.CHUNKS ? 0 : BRUSH_SIZES[this.brush];
         int chunkX = Math.floorDiv((int)Math.floor(this.blockX(mouseX)), 16);
         int chunkZ = Math.floorDiv((int)Math.floor(this.blockZ(mouseY)), 16);
         int x0 = (int)Math.floor(this.screenX((chunkX - r) * 16.0));
         int y0 = (int)Math.floor(this.screenY((chunkZ - r) * 16.0));
         int x1 = (int)Math.floor(this.screenX((chunkX + r + 1) * 16.0));
         int y1 = (int)Math.floor(this.screenY((chunkZ + r + 1) * 16.0));
         int tone = this.drag == MapScreen.Drag.ERASE || this.drag == MapScreen.Drag.NONE && Keys.shift() ? DESELECT : SELECTED;
         g.fill(x0, y0, x1, y1, 0x28000000 | tone);
         g.outline(x0, y0, Math.max(1, x1 - x0 + 1), Math.max(1, y1 - y0 + 1), 0xFF000000 | tone);
         if (r > 0) {
            this.dragLabel(g, x0, y0, (tone == DESELECT ? "− " : "") + Ui.t("chunkatlas.map.drag.brush", 2 * r + 1, 2 * r + 1), tone);
         }
      } else if (inMap && selecting && (this.tool == MapScreen.Tool.REGION || this.tool == MapScreen.Tool.CHUNKS && this.regionScale()) && this.drag == MapScreen.Drag.NONE) {
         int rx = Math.floorDiv((int)Math.floor(this.blockX(mouseX)), REGION);
         int rz = Math.floorDiv((int)Math.floor(this.blockZ(mouseY)), REGION);
         int x0 = (int)Math.floor(this.screenX(rx * 512.0));
         int y0 = (int)Math.floor(this.screenY(rz * 512.0));
         int x1 = (int)Math.floor(this.screenX((rx + 1) * 512.0));
         int y1 = (int)Math.floor(this.screenY((rz + 1) * 512.0));
         int tone = Keys.shift() ? DESELECT : SELECTED;
         g.fill(x0, y0, x1, y1, 0x28000000 | tone);
         g.outline(x0, y0, Math.max(1, x1 - x0 + 1), Math.max(1, y1 - y0 + 1), 0xFF000000 | tone);
         int there = this.index == null ? 0 : Long.bitCount(0) + this.countPresent(rx, rz);
         this.dragLabel(g, x0, y0, (tone == DESELECT ? "− " : "") + Ui.t("chunkatlas.map.drag.region", rx, rz, Ui.count(there)), tone);
      } else if (this.drag == MapScreen.Drag.SELECT || this.drag == MapScreen.Drag.UNSELECT) {
         int[] c = this.dragChunks();
         int x0 = (int)Math.floor(this.screenX(c[0] * 16.0));
         int y0 = (int)Math.floor(this.screenY(c[1] * 16.0));
         int x1 = (int)Math.floor(this.screenX((c[2] + 1) * 16.0));
         int y1 = (int)Math.floor(this.screenY((c[3] + 1) * 16.0));
         int tone = this.drag == MapScreen.Drag.SELECT ? SELECTED : DESELECT;
         g.fill(x0, y0, x1, y1, 0x30000000 | tone);
         g.outline(x0, y0, Math.max(1, x1 - x0 + 1), Math.max(1, y1 - y0 + 1), 0xFF000000 | tone);
         int w = c[2] - c[0] + 1;
         int h = c[3] - c[1] + 1;
         String size = Ui.t("chunkatlas.map.drag.box", w, h);
         if (this.drag == MapScreen.Drag.SELECT) {
            int added = this.boxAdds(c);
            this.dragLabel(g, x0, y0, size + (added > 0 ? "  +" + Ui.count(added) : ""), tone);
         } else {
            this.dragLabel(g, x0, y0, "− " + Ui.count(this.boxRemoves(c)), tone);
         }
      } else if (inMap && this.editing && this.drag == MapScreen.Drag.NONE && this.zoom * 16.0 >= 3.0) {
         this.outlineChunk(g, Math.floorDiv((int)Math.floor(this.blockX(mouseX)), 16), Math.floorDiv((int)Math.floor(this.blockZ(mouseY)), 16), 0);
      } else if (this.menu == MapScreen.Menu.CONTEXT && this.zoom * 16.0 >= 3.0) {
         this.outlineChunk(g, Math.floorDiv(this.contextBlockX, 16), Math.floorDiv(this.contextBlockZ, 16), 0x1AFFFFFF);
      }

      if (!this.editing) {
         long s0 = System.nanoTime();
         this.seed.drawOver(g, mouseX, mouseY, inMap);
         this.tOver = System.nanoTime() - s0;
      }

      if (this.pasteChunks != null) {
         this.drawPasteLabel(g);
      }

      this.drawBorder(g);
      String tip5 = Settings.portals() && !this.editing ? this.drawPois(g, mouseX, mouseY, inMap) : null;
      if (Settings.footsteps() && !this.editing && this.worldFolder.getFileName() != null) {
         this.drawFootsteps(g);
      }
      String tip4 = this.live != null && !this.editing && dev.chunkatlas.minimap.Config.get().waypointsOnMap ? this.drawWaypoints(g, mouseX, mouseY, inMap) : null;
      String tip6 = Settings.mobs() && !this.editing ? this.drawMobs(g, mouseX, mouseY, inMap) : null;

      String tip3 = tip6 != null ? tip6 : tip4 != null ? tip4 : tip5;

      LevelMarkers.Point you = this.marker(this.map.markers().player());
      if (this.live != null && this.mc.player != null) {
         you = new LevelMarkers.Point(
            Ids.of(this.mc.player.level().dimension()), this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(), this.mc.player.getYRot()
         );
         you = you.dimension().equals(this.dimension) ? you : null;
      }

      for (MapScreen.Seen p : this.showPlayers && !this.editing ? this.otherPlayers() : List.<MapScreen.Seen>of()) {
         String t = this.drawPlayer(g, p, you, mouseX, mouseY, inMap);
         if (t != null) {
            tip3 = t;
         }
      }

      String tip = this.drawSpawn(g, this.marker(this.map.markers().spawn()), mouseX, mouseY, inMap);

      String tip2 = this.drawYou(
         g,
         you,
         Ui.t(this.live != null ? "chunkatlas.map.marker.player_here" : "chunkatlas.map.marker.player_last"),
         mouseX,
         mouseY,
         inMap
      );
      g.unscissor();
      if (this.view != MapScreen.View.TERRAIN) {
         this.drawLegend(g);
      }

      List<Component> keyTip = this.editing || this.hideControls ? null : this.loaded.drawKey(g, this.hits, mouseX, mouseY, covered);
      if (keyTip != null) {
         g.tooltip(this.font, keyTip, mouseX, mouseY);
      }

      if (this.hideControls) {
         this.markerTip(g, tip2 != null ? tip2 : (tip != null ? tip : tip3), mouseX, mouseY, inMap);
         return;
      }

      if (!this.pending.isEmpty() && System.currentTimeMillis() >= this.bannerUntil) {
         String wait = Ui.plural("chunkatlas.pending.waiting", this.pending.size());
         Ui.chip(g, this.font, wait, (this.width - Ui.chipWidth(this.font, wait)) / 2, this.messageY(), Glass.QUIET);
      }

      if (this.measureFrom != null) {
         double fx = this.screenX(this.measureFrom[0]);
         double fy = this.screenY(this.measureFrom[1]);
         double len = Math.hypot(mouseX - fx, mouseY - fy);
         for (double d = 0; d < len; d += 4.0) {
            int px = (int)(fx + (mouseX - fx) * d / Math.max(1.0, len));
            int py = (int)(fy + (mouseY - fy) * d / Math.max(1.0, len));
            g.fill(px - 1, py - 1, px + 1, py + 1, 0xFFFFD84A);
         }

         g.fill((int)fx - 2, (int)fy - 2, (int)fx + 3, (int)fy + 3, 0xFFFFD84A);
         double blocks = Math.hypot(this.blockX(mouseX) - this.measureFrom[0], this.blockZ(mouseY) - this.measureFrom[1]);
         String text = Ui.t("chunkatlas.measure.distance", Ui.count(Math.round(blocks)));
         Ui.chip(g, this.font, text, mouseX + 10, mouseY - 4, Ui.YELLOW);
         String how = Ui.t("chunkatlas.measure.how");
         Ui.chip(g, this.font, how, (this.width - Ui.chipWidth(this.font, how)) / 2, Glass.MARGIN + 3, Ui.WHITE);
      } else if (this.pasteChunks != null) {
         int[] box = this.pasteHint();
         Ui.chip(g, this.font, this.pasteHowText(), box[0], box[1], Ui.WHITE);
      } else if (this.underPanel || this.editing && this.banner != null && System.currentTimeMillis() < this.bannerUntil) {
      } else if (inMap && Settings.coordinates() && this.editing && this.commands != null) {
         int right = this.width - Glass.MARGIN - (this.side == null ? 0 : this.side.width(this.font)) - 4;
         int room = Math.max(80, right - Glass.MARGIN - 4);
         String info = this.fit(this.hoverText(mouseX, mouseY, room - 10), room);
         Glass.plain(g, this.font, info, right - this.font.width(info), Glass.MARGIN + Glass.tabsHeight() + 6, Ui.WHITE);
      } else if (inMap && Settings.coordinates()) {
         int room = Math.max(80, this.width - 2 * 84);
         String info = this.fit(this.hoverText(mouseX, mouseY, room - 10), room);
         Glass.plain(g, this.font, info, (this.width - this.font.width(info)) / 2, Glass.MARGIN + 4, Ui.WHITE);
      }

      if (!this.editing) {
         String zoomText = this.zoom >= 1.0 ? Math.round(this.zoom) + ":1" : "1:" + Math.round(1.0 / this.zoom);
         Glass.plain(g, this.font, zoomText, (this.width - this.font.width(zoomText)) / 2, this.height - Glass.MARGIN - 9, Glass.QUIET);
      }

      int noteY = Glass.MARGIN + Glass.tabsHeight() + 6;
      int[] cave = this.cavePanelBox();
      if (cave != null) {
         noteY = cave[1] + cave[3] + 6;
      }

      if (this.editing && this.onServer()) {
         int n = dev.chunkatlas.ServerLink.waiting();
         String about = n > 0 ? Ui.plural("chunkatlas.server.waiting", n) : Ui.t(dev.chunkatlas.ServerLink.live() ? "chunkatlas.server.on_server_live" : "chunkatlas.server.on_server");
         Glass.plain(g, this.font, this.fit(about, this.width / 2), Glass.MARGIN + 2, noteY, Glass.ACCENT);
         noteY += 11;
      }

      int busy = this.map.busy();
      if (busy > 0) {
         Glass.plain(g, this.font, Ui.plural("chunkatlas.map.drawing", busy), Glass.MARGIN + 2, noteY, Ui.GREY);
         noteY += 11;
      }

      if (i.away() > 0) {
         Ui.chip(g, this.font, this.fit(Ui.plural("chunkatlas.map.away_regions", i.away()), this.width / 2), Glass.MARGIN, noteY, Ui.YELLOW);
      }

      this.markerTip(g, tip2 != null ? tip2 : (tip != null ? tip : tip3), mouseX, mouseY, inMap);
   }

   private void markerTip(Gfx g, String say, int mouseX, int mouseY, boolean inMap) {
      if (say != null) {
         g.tooltip(this.font, Component.literal(say), mouseX, mouseY);
      } else if (inMap && !this.editing) {
         List<Component> structure = this.seed.hoverTip();
         if (structure != null) {
            g.tooltip(this.font, structure, mouseX, mouseY);
         }
      }
   }

   private void dragLabel(Gfx g, int x, int y, String text, int tone) {
      int w = this.font.width(text) + 8;
      int ly = y - Glass.CHIP_H - 2 < this.mapTop ? y + 2 : y - Glass.CHIP_H - 2;
      int lx = Math.max(this.mapLeft, Math.min(x, this.mapRight - w));
      g.fill(lx, ly, lx + w, ly + Glass.CHIP_H, Glass.CHIP);
      g.text(this.font, text, lx + 4, ly + 2, 0xFF000000 | tone);
   }

   private int countPresent(int rx, int rz) {
      long[] bits = this.index == null ? null : this.index.present().get(WorldLayout.key(rx, rz));
      int n = 0;
      if (bits != null) {
         for (long word : bits) {
            n += Long.bitCount(word);
         }
      }

      return n;
   }

   private int boxAdds(int[] c) {
      if (this.index == null || (long)(c[2] - c[0] + 1) * (c[3] - c[1] + 1) > 200_000L) {
         return 0;
      }

      ChunkSelection sel = this.selection();
      boolean unsaved = this.pickUnsaved();
      int n = 0;
      for (int z = c[1]; z <= c[3]; z++) {
         for (int x = c[0]; x <= c[2]; x++) {
            if ((unsaved || this.index.has(x, z)) && !sel.contains(x, z)) {
               n++;
            }
         }
      }

      return n;
   }

   private int boxRemoves(int[] c) {
      if ((long)(c[2] - c[0] + 1) * (c[3] - c[1] + 1) > 200_000L) {
         return 0;
      }

      ChunkSelection sel = this.selection();
      int n = 0;
      for (int z = c[1]; z <= c[3]; z++) {
         for (int x = c[0]; x <= c[2]; x++) {
            if (sel.contains(x, z)) {
               n++;
            }
         }
      }

      return n;
   }

   private void outlineChunk(Gfx g, int chunkX, int chunkZ, int fill) {
      int x0 = (int)Math.floor(this.screenX(chunkX * 16.0));
      int y0 = (int)Math.floor(this.screenY(chunkZ * 16.0));
      int x1 = (int)Math.floor(this.screenX(chunkX * 16.0 + 16.0));
      int y1 = (int)Math.floor(this.screenY(chunkZ * 16.0 + 16.0));
      if (fill != 0) {
         g.fill(x0, y0, x1, y1, fill);
      }

      g.outline(x0, y0, Math.max(1, x1 - x0 + 1), Math.max(1, y1 - y0 + 1), 0xB0FFFFFF);
   }

   private void drawGrid(Gfx g, int blocks, int minPixels, int colour) {
      if (!(blocks * this.zoom < minPixels)) {
         long first = Math.floorDiv((long)Math.floor(this.blockX(0.0)), blocks) * blocks;

         for (long b = first; this.screenX(b) < this.width; b += blocks) {
            int x = (int)Math.floor(this.screenX(b));
            g.fill(x, this.mapTop, x + 1, this.mapBottom, colour);
         }

         first = Math.floorDiv((long)Math.floor(this.blockZ(this.mapTop)), blocks) * blocks;

         for (long b = first; this.screenY(b) < this.mapBottom; b += blocks) {
            int y = (int)Math.floor(this.screenY(b));
            g.fill(this.mapLeft, y, this.mapRight, y + 1, colour);
         }
      }
   }

   private record Mask(Tex id, long[] bits) {
   }

   private final Map<Long, MapScreen.Mask> selectionMasks = new HashMap<>();
   private final Map<Long, MapScreen.Mask> guardMasks = new HashMap<>();
   private final Map<Long, MapScreen.Mask> pasteMasks = new HashMap<>();
   private final Map<Long, MapScreen.Mask> goneMasks = new HashMap<>();
   private final Map<Long, MapScreen.Mask> skippedMasks = new HashMap<>();
   private final Map<Long, MapScreen.Mask> matchMasks = new HashMap<>();
   private final Map<Long, MapScreen.Mask> keptMasks = new HashMap<>();

   static final int MATCH = 0xFFD84A;

   static final int KEPT = 0x7EE07E;

   static final int DIMMED = 0x0A0C10;
   private final Map<Long, MapScreen.Mask> dimMasks = new HashMap<>();

   private List<WorldLayout.RegionInfo> selectionRegions(ChunkSelection sel) {
      List<WorldLayout.RegionInfo> out = new ArrayList<>();
      int rx0 = Math.floorDiv((int)Math.floor(this.blockX(0.0)), REGION);
      int rx1 = Math.floorDiv((int)Math.floor(this.blockX(this.width)), REGION);
      int rz0 = Math.floorDiv((int)Math.floor(this.blockZ(this.mapTop)), REGION);
      int rz1 = Math.floorDiv((int)Math.floor(this.blockZ(this.mapBottom)), REGION);
      for (int rz = rz0; rz <= rz1; rz++) {
         for (int rx = rx0; rx <= rx1; rx++) {
            if (sel.count(rx, rz) > 0) {
               out.add(new WorldLayout.RegionInfo(rx, rz, null, 0L, 0L, false));
            }
         }
      }

      return out;
   }

   private static List<WorldLayout.RegionInfo> regionsOf(ChunkSelection sel) {
      List<WorldLayout.RegionInfo> out = new ArrayList<>();
      for (long region : sel.regions()) {
         out.add(new WorldLayout.RegionInfo((int)(region >> 32), (int)region, null, 0L, 0L, false));
      }

      return out;
   }

   private static final int GUARD = 0x7EE07E;

   private static final int SKIPPED = 0xFFD84A;

   private void drawEdges(Gfx g, ChunkSelection sel, List<WorldLayout.RegionInfo> seen, int colour) {
      double px = this.zoom * 16.0;
      if (px < 3.0) {
         return;
      }

      for (WorldLayout.RegionInfo r : seen) {
         if (sel.count(r.rx(), r.rz()) == 0) {
            continue;
         }

         long[] bits = sel.bits(r.rx(), r.rz());
         for (int i = 0; i < 1024; i++) {
            if ((bits[i >> 6] >>> (i & 63) & 1L) == 0L) {
               continue;
            }

            int cx = r.rx() * 32 + (i & 31);
            int cz = r.rz() * 32 + (i >> 5);
            int x0 = (int)Math.floor(this.screenX(cx * 16.0));
            int y0 = (int)Math.floor(this.screenY(cz * 16.0));
            int x1 = (int)Math.floor(this.screenX(cx * 16.0 + 16.0));
            int y1 = (int)Math.floor(this.screenY(cz * 16.0 + 16.0));
            if (x1 < this.mapLeft || x0 > this.mapRight || y1 < this.mapTop || y0 > this.mapBottom) {
               continue;
            }

            if (!sel.contains(cx, cz - 1)) {
               g.fill(x0, y0, x1 + 1, y0 + 1, colour);
            }

            if (!sel.contains(cx, cz + 1)) {
               g.fill(x0, y1, x1 + 1, y1 + 1, colour);
            }

            if (!sel.contains(cx - 1, cz)) {
               g.fill(x0, y0, x0 + 1, y1 + 1, colour);
            }

            if (!sel.contains(cx + 1, cz)) {
               g.fill(x1, y0, x1 + 1, y1 + 1, colour);
            }
         }
      }
   }

   private void drawSelection(Gfx g, ChunkSelection sel, List<WorldLayout.RegionInfo> seen, int baseRx, int baseRz, int tone) {
      Map<Long, MapScreen.Mask> cache = tone == SELECTED ? this.selectionMasks : tone == PASTE ? this.pasteMasks : tone == GONE ? this.goneMasks
         : tone == SKIPPED ? this.skippedMasks : tone == MATCH ? this.matchMasks : tone == KEPT ? this.keptMasks : tone == DIMMED ? this.dimMasks : this.guardMasks;
      int colour = (tone == SELECTED ? 0x60 : tone == PASTE ? 0x80 : tone == GONE ? 0xF0 : tone == SKIPPED ? 0x80 : tone == MATCH ? 0x70 : tone == KEPT ? 0x30 : tone == DIMMED ? 0xB4 : 0x30) << 24 | tone;
      Set<Long> used = new HashSet<>();
      for (WorldLayout.RegionInfo r : seen) {
         if (sel.count(r.rx(), r.rz()) == 0) {
            continue;
         }

         long key = WorldLayout.key(r.rx(), r.rz());
         long[] bits = sel.bits(r.rx(), r.rz());
         used.add(key);
         MapScreen.Mask m = cache.get(key);
         if (m == null || !java.util.Arrays.equals(m.bits(), bits)) {
            Tex image = Tex.create("Chunk Atlas selection", 32, 32);
            for (int i = 0; i < 1024; i++) {
               image.set(i & 31, i >> 5, (bits[i >> 6] >>> (i & 63) & 1L) != 0L ? colour : 0);
            }

            image.upload();
            Tex id = image;
            if (m != null) {
               m.id().close();
            }

            m = new MapScreen.Mask(id, bits);
            cache.put(key, m);
         }

         g.blit(m.id(), (r.rx() - baseRx) * REGION, (r.rz() - baseRz) * REGION, 0.0F, 0.0F, REGION, REGION, 32, 32, 32, 32);
      }

      if (cache.size() > used.size() + 64) {
         cache.entrySet().removeIf(e -> {
            if (used.contains(e.getKey())) {
               return false;
            }

            e.getValue().id().close();
            return true;
         });
      }
   }

   private void releaseMasks() {
      for (MapScreen.Mask m : this.selectionMasks.values()) {
         m.id().close();
      }

      for (MapScreen.Mask m : this.guardMasks.values()) {
         m.id().close();
      }

      for (MapScreen.Mask m : this.goneMasks.values()) {
         m.id().close();
      }

      for (MapScreen.Mask m : this.skippedMasks.values()) {
         m.id().close();
      }

      for (MapScreen.Mask m : this.matchMasks.values()) {
         m.id().close();
      }

      for (MapScreen.Mask m : this.keptMasks.values()) {
         m.id().close();
      }

      for (MapScreen.Mask m : this.dimMasks.values()) {
         m.id().close();
      }

      this.dimMasks.clear();

      this.matchMasks.clear();
      this.keptMasks.clear();
      this.skippedMasks.clear();
      this.goneMasks.clear();
      for (MapScreen.Mask m : this.pasteMasks.values()) {
         m.id().close();
      }

      this.selectionMasks.clear();
      this.guardMasks.clear();
      this.pasteMasks.clear();
   }

   private record Seen(String id, String name, LevelMarkers.Point at) {
   }

   private Map<String, String> playerNames;
   private List<MapScreen.Seen> livePlayers = List.of();
   private long livePlayersAt;

   private List<MapScreen.Seen> otherPlayers() {
      List<MapScreen.Seen> saved = Settings.allPlayers() ? this.savedPlayers() : List.of();
      if (this.live != null && this.mc.getSingleplayerServer() != null) {
         long now = System.currentTimeMillis();
         if (now - this.livePlayersAt > 1000L) {
            this.livePlayersAt = now;
            List<MapScreen.Seen> list = new ArrayList<>();
            java.util.UUID me = this.mc.player == null ? null : this.mc.player.getUUID();
            Set<String> online = new HashSet<>();
            for (net.minecraft.server.level.ServerPlayer p : List.copyOf(this.mc.getSingleplayerServer().getPlayerList().getPlayers())) {
               online.add(p.getUUID().toString());
               if (!p.getUUID().equals(me)) {
                  list.add(new MapScreen.Seen(p.getUUID().toString(), p.getScoreboardName(), new LevelMarkers.Point(Ids.of(p.level().dimension()), p.getX(), p.getY(), p.getZ(), p.getYRot())));
               }
            }

            String myName = this.mc.player == null ? null : this.mc.player.getScoreboardName();
            for (MapScreen.Seen p : saved) {
               if (!online.contains(p.id()) && !p.name().equals(myName)) {
                  list.add(p);
               }
            }

            this.livePlayers = list;
         }

         return this.livePlayers;
      }

      return saved;
   }

   private List<MapScreen.Seen> savedPlayers() {
      if (this.map == null || this.map.markers().players().isEmpty()) {
         return List.of();
      }

      if (this.playerNames == null) {
         this.playerNames = knownNames(this.mc.gameDirectory.toPath().resolve("usercache.json"));
      }

      List<MapScreen.Seen> list = new ArrayList<>();
      for (LevelMarkers.Player p : this.map.markers().players()) {
         String name = this.playerNames.getOrDefault(p.id(), p.id().length() > 8 ? p.id().substring(0, 8) : p.id());
         list.add(new MapScreen.Seen(p.id(), name, p.at()));
      }

      return list;
   }

   private static Map<String, String> knownNames(Path file) {
      Map<String, String> names = new HashMap<>();
      try {
         if (java.nio.file.Files.isRegularFile(file) && java.nio.file.Files.size(file) < 8L * 1024L * 1024L) {
            for (com.google.gson.JsonElement e : com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonArray()) {
               com.google.gson.JsonObject o = e.getAsJsonObject();
               if (o.has("uuid") && o.has("name")) {
                  names.put(o.get("uuid").getAsString(), o.get("name").getAsString());
               }
            }
         }
      } catch (java.io.IOException | RuntimeException ignored) {
      }

      return names;
   }

   private String drawYou(Gfx g, LevelMarkers.Point at, String label, int mouseX, int mouseY, boolean inMap) {
      if (at == null) {
         return null;
      }

      int x = (int)Math.floor(this.screenX(at.x()));
      int y = (int)Math.floor(this.screenY(at.z()));
      MapMarkers.you(g, x, y, at.yaw(), dev.chunkatlas.port.Skin.you(this.mc));
      return inMap && Math.abs(mouseX - x) <= 7 && Math.abs(mouseY - y) <= 7 ? label + "  " + (int)Math.floor(at.x()) + ", " + (int)Math.floor(at.z()) : null;
   }

   private String drawPlayer(Gfx g, MapScreen.Seen p, LevelMarkers.Point you, int mouseX, int mouseY, boolean inMap) {
      LevelMarkers.Point at = this.marker(Optional.of(p.at()));
      if (at == null) {
         return null;
      }

      double sx = this.screenX(at.x());
      double sy = this.screenY(at.z());
      int pad = 10;
      double cx = (this.mapLeft + this.mapRight) / 2.0;
      double cy = (this.mapTop + this.mapBottom) / 2.0;

      int top = this.mapTop + pad + 18;
      boolean off = sx < this.mapLeft + pad || sx > this.mapRight - pad || sy < top || sy > this.mapBottom - pad;
      int x;
      int y;
      if (off) {
         double dx = sx - cx;
         double dy = sy - cy;
         double kx = dx == 0 ? Double.MAX_VALUE : ((dx > 0 ? this.mapRight - pad : this.mapLeft + pad) - cx) / dx;
         double ky = dy == 0 ? Double.MAX_VALUE : ((dy > 0 ? this.mapBottom - pad - 10 : top) - cy) / dy;
         double k = Math.min(kx, ky);
         x = (int)Math.floor(cx + dx * k);
         y = (int)Math.floor(cy + dy * k);
         MapMarkers.edgeArrow(g, x, y, Math.atan2(dy, dx));
      } else {
         x = (int)Math.floor(sx);
         y = (int)Math.floor(sy);
      }

      MapMarkers.face(g, x, y, MapMarkers.skin(this.mc, p.id()));

      if (inMap && Math.abs(mouseX - x) <= 6 && Math.abs(mouseY - y) <= 6) {
         if (you != null) {
            long m = Math.round(Math.hypot(at.x() - you.x(), at.z() - you.z()));
            return p.name() + " · " + (m >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", m / 1000.0) : m + " m");
         }

         return p.name() + "  " + (int)Math.floor(at.x()) + ", " + (int)Math.floor(at.z());
      }

      return null;
   }

   private final MobMarks.Near nearMobs = new MobMarks.Near();
   private final MobMarks.Batch mobMarks = new MobMarks.Batch();

   private String drawMobs(Gfx g, int mouseX, int mouseY, boolean inMap) {
      if (this.live == null || this.mc.player == null || this.mc.level == null || !Ids.of(this.mc.level.dimension()).equals(this.dimension)) {
         return null;
      }

      dev.chunkatlas.minimap.Config cfg = dev.chunkatlas.minimap.Config.get();
      List<net.minecraft.world.entity.Entity> mobs = this.nearMobs.collect(this.mc, this.mc.player, cfg);

      this.mobMarks.clear();
      for (net.minecraft.world.entity.Entity e : mobs) {
         int x = (int)Math.floor(this.screenX(e.getX()));
         int y = (int)Math.floor(this.screenY(e.getZ()));
         if (x >= this.mapLeft - 8 && x <= this.mapRight + 8 && y >= this.mapTop - 8 && y <= this.mapBottom + 8) {
            this.mobMarks.add(e, x, y, e.getYHeadRot(), MobMarks.alpha(e, this.mc.player));
         }
      }

      int over = this.mobMarks.draw(g, cfg.radarStyle, MobMarks.size(this.zoom), inMap ? mouseX : -100, inMap ? mouseY : -100);
      if (over < 0) {
         return null;
      }

      if (this.mobMarks.count(over) > 1) {
         return Ui.t("chunkatlas.map.marker.mobs", this.mobMarks.count(over));
      }

      net.minecraft.world.entity.Entity e = this.mobMarks.mob(over);
      long m = Math.round(Math.hypot(e.getX() - this.mc.player.getX(), e.getZ() - this.mc.player.getZ()));
      return Ui.t("chunkatlas.map.marker.mob", e.getName().getString(), m);
   }

   private String drawSpawn(Gfx g, LevelMarkers.Point at, int mouseX, int mouseY, boolean inMap) {
      if (at == null) {
         return null;
      }

      int x = (int)Math.floor(this.screenX(at.x()));
      int y = (int)Math.floor(this.screenY(at.z()));
      MapMarkers.star(g, x, y);
      return inMap && Math.abs(mouseX - x) <= 5 && Math.abs(mouseY - y) <= 5 ? Ui.t("chunkatlas.map.marker.spawn") + "  " + (int)Math.floor(at.x()) + ", " + (int)Math.floor(at.z()) : null;
   }

   private final Map<Long, List<dev.chunkatlas.engine.PointsOfInterest.Point>> pois = new java.util.concurrent.ConcurrentHashMap<>();
   private final Set<Long> poiAsked = new HashSet<>();
   private String poiDimension;

   private static final int PORTAL_HALF = 6;
   private static final String PORTAL_SPRITE = "nether_portal";

   private String drawPois(Gfx g, int mouseX, int mouseY, boolean inMap) {
      WorldMap.Index i = this.index;
      if (i == null || i.dimension().poi().isEmpty() || this.liveMap()) {
         return null;
      }

      if (!this.dimension.equals(this.poiDimension)) {
         this.pois.clear();
         this.poiAsked.clear();
         this.poiDimension = this.dimension;
      }

      java.nio.file.Path folder = i.dimension().poi().get();
      for (WorldLayout.RegionInfo r : this.visible(0)) {
         long key = WorldLayout.key(r.rx(), r.rz());
         if (this.poiAsked.add(key)) {
            java.nio.file.Path file = folder.resolve("r." + r.rx() + "." + r.rz() + ".mca");
            this.submit(() -> this.pois.put(key, java.nio.file.Files.isRegularFile(file) ? dev.chunkatlas.engine.PointsOfInterest.read(file) : List.of()));
         }
      }

      String tip = null;
      for (List<dev.chunkatlas.engine.PointsOfInterest.Point> list : this.pois.values()) {
         for (dev.chunkatlas.engine.PointsOfInterest.Point p : list) {
            if (p.kind() != dev.chunkatlas.engine.PointsOfInterest.Kind.PORTAL) {
               continue;
            }

            int x = (int)Math.floor(this.screenX(p.x() + 0.5));
            int y = (int)Math.floor(this.screenY(p.z() + 0.5));
            if (x < -8 || y < -8 || x > this.width + 8 || y > this.height + 8) {
               continue;
            }

            g.fill(x - PORTAL_HALF + 1, y - PORTAL_HALF + 1, x + PORTAL_HALF + 1, y + PORTAL_HALF + 1, 0x73000000);
            g.blockSprite(PORTAL_SPRITE, x - PORTAL_HALF, y - PORTAL_HALF, PORTAL_HALF * 2, PORTAL_HALF * 2);
            if (inMap && Math.abs(mouseX - x) <= PORTAL_HALF && Math.abs(mouseY - y) <= PORTAL_HALF) {
               tip = Ui.t("chunkatlas.poi." + p.kind().name().toLowerCase(java.util.Locale.ROOT)) + "  " + p.x() + ", " + p.y() + ", " + p.z();
            }
         }
      }

      return tip;
   }

   private int[] footsteps;
   private long footstepsAt;
   private int footstepsSeen = -1;

   private void drawFootsteps(Gfx g) {
      long now = System.currentTimeMillis();
      String trail = dev.chunkatlas.Footsteps.key(this.worldFolder.getFileName().toString(), this.liveMap());
      boolean walking = this.live != null && dev.chunkatlas.Footsteps.walking(trail, this.dimension);
      boolean due = walking ? this.footstepsSeen != dev.chunkatlas.Footsteps.changes() : now - this.footstepsAt > 1000L;
      if (this.footsteps == null || due) {
         this.footstepsAt = now;
         this.footstepsSeen = dev.chunkatlas.Footsteps.changes();
         this.footsteps = dev.chunkatlas.Footsteps.trail(trail, this.dimension);
      }

      int[] p = this.footsteps;
      int n = p.length / 3;
      int fade = Settings.footstepsFade();
      double nowS = now / 1000.0;
      int first = Math.max(0, n - FOOTSTEPS_SHOWN);
      int shown = n - first;
      for (int i = first + 1; i < n; i++) {
         int ax = p[3 * (i - 1)];
         int az = p[3 * (i - 1) + 1];
         int bx = p[3 * i];
         int bz = p[3 * i + 1];
         if (Math.abs(bx - ax) + Math.abs(bz - az) > 64) {
            continue;
         }

         int alpha;
         if (fade == Settings.FADE_NEVER) {
            alpha = (int)(0xD0L * (i - first) / Math.max(1, shown));
         } else {
            double age = p[3 * i + 2] == 0 ? Double.MAX_VALUE : Math.max(0.0, nowS - p[3 * i + 2] - 0.5);
            alpha = age >= fade ? 0 : (int)(0xD0 * (fade - age) / fade);
         }

         if (alpha >= 8) {
            this.footstepLine(g, ax + 0.5, az + 0.5, bx + 0.5, bz + 0.5, alpha);
         }
      }

      if (walking && n > 0 && this.mc.player != null && Ids.of(this.mc.player.level().dimension()).equals(this.dimension)) {
         int lx = p[3 * n - 3];
         int lz = p[3 * n - 2];
         double px = this.mc.player.getX();
         double pz = this.mc.player.getZ();
         if (Math.abs(px - lx) + Math.abs(pz - lz) <= 64) {
            this.footstepLine(g, lx + 0.5, lz + 0.5, px, pz, 0xD0);
         }
      }
   }

   private void footstepLine(Gfx g, double ax, double az, double bx, double bz, int alpha) {
      double x0 = this.screenX(ax);
      double y0 = this.screenY(az);
      double x1 = this.screenX(bx);
      double y1 = this.screenY(bz);
      if (Math.max(x0, x1) < -2 || Math.min(x0, x1) > this.width + 2 || Math.max(y0, y1) < -2 || Math.min(y0, y1) > this.height + 2) {
         return;
      }

      int colour = alpha << 24 | 0xFFFFFF;
      double len = Math.hypot(x1 - x0, y1 - y0);
      int steps = Math.max(1, (int)Math.ceil(len));
      for (int s = 0; s <= steps; s++) {
         int x = (int)Math.floor(x0 + (x1 - x0) * s / steps);
         int y = (int)Math.floor(y0 + (y1 - y0) * s / steps);
         g.fill(x, y, x + 1, y + 1, colour);
      }
   }

   private static final int FOOTSTEPS_SHOWN = 600;

   private final Map<String, Optional<double[]>> borders = new HashMap<>();

   private void drawBorder(Gfx g) {
      double[] b;
      if (this.live != null && this.mc.level != null && Ids.of(this.mc.level.dimension()).equals(this.dimension)) {
         net.minecraft.world.level.border.WorldBorder wb = this.mc.level.getWorldBorder();
         b = wb.getSize() >= 59_000_000.0 ? null : new double[]{wb.getCenterX(), wb.getCenterZ(), wb.getSize()};
      } else if (this.liveMap()) {
         b = null;
      } else {
         b = this.borders.computeIfAbsent(this.dimension, d -> Optional.ofNullable(LevelMarkers.border(this.worldFolder, d))).orElse(null);
      }

      if (b == null) {
         return;
      }

      double half = b[2] / 2.0;
      int x0 = (int)Math.floor(this.screenX(b[0] - half));
      int x1 = (int)Math.floor(this.screenX(b[0] + half));
      int z0 = (int)Math.floor(this.screenY(b[1] - half));
      int z1 = (int)Math.floor(this.screenY(b[1] + half));
      int red = 0xFFFF5555;
      g.fill(x0 - 1, z0 - 1, x1 + 1, z0 + 1, red);
      g.fill(x0 - 1, z1 - 1, x1 + 1, z1 + 1, red);
      g.fill(x0 - 1, z0 - 1, x0 + 1, z1 + 1, red);
      g.fill(x1 - 1, z0 - 1, x1 + 1, z1 + 1, red);
   }

   private String drawWaypoints(Gfx g, int mouseX, int mouseY, boolean inMap) {
      String tip = null;
      boolean deaths = dev.chunkatlas.minimap.Config.get().deathpoints;
      for (dev.chunkatlas.minimap.waypoint.Waypoint w : dev.chunkatlas.minimap.waypoint.Waypoints.all()) {
         if (!w.visible || w.death && !deaths) {
            continue;
         }

         double[] p = w.placeIn(this.dimension);
         if (p == null) {
            continue;
         }

         int x = (int)Math.floor(this.screenX(p[0]));
         int y = (int)Math.floor(this.screenY(p[2]));
         MapMarkers.pin(g, this.font, x, y, w, 0xFF);

         if (inMap && mouseX >= x - 6 && mouseX <= x + 6 && mouseY >= y - 15 && mouseY <= y + 1) {
            tip = w.name + "  " + w.x + ", " + w.y + ", " + w.z;
         }
      }

      return tip;
   }

   private String drawMarker(Gfx g, LevelMarkers.Point at, int colour, String label, int mouseX, int mouseY, boolean inMap) {
      if (at == null) {
         return null;
      }

      int x = (int)Math.floor(this.screenX(at.x()));
      int y = (int)Math.floor(this.screenY(at.z()));
      g.fill(x - 4, y - 4, x + 5, y + 5, 0xFF000000);
      g.fill(x - 3, y - 3, x + 4, y + 4, colour);
      g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF000000);
      return inMap && Math.abs(mouseX - x) <= 5 && Math.abs(mouseY - y) <= 5 ? label + "  " + (int)Math.floor(at.x()) + ", " + (int)Math.floor(at.z()) : null;
   }

   private final Map<Long, short[]> chunkBiomes = new java.util.concurrent.ConcurrentHashMap<>();
   private final Set<Long> biomeAsked = java.util.concurrent.ConcurrentHashMap.newKeySet();
   private String biomesFor;

   private String savedBiome(int bx, int bz, int chunkX, int chunkZ) {
      if (this.live != null && this.mc.level != null && this.mc.player != null
         && Ids.of(this.mc.level.dimension()).equals(this.dimension)
         && this.mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
         return this.mc.level.getBiome(new net.minecraft.core.BlockPos(bx, this.mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, bx, bz), bz))
            .unwrapKey().map(k -> SeedMapLayer.biomeName(Ids.of(k))).orElse(null);
      }

      if (this.index == null || !this.index.has(chunkX, chunkZ) || this.index.dimension() == null) {
         return null;
      }

      if (!this.dimension.equals(this.biomesFor)) {
         this.chunkBiomes.clear();
         this.biomeAsked.clear();
         this.biomesFor = this.dimension;
      }

      long key = WorldLayout.key(chunkX, chunkZ);
      short[] biomes = this.chunkBiomes.get(key);
      if (biomes == null) {
         if (this.biomeAsked.add(key)) {
            if (this.chunkBiomes.size() > 512) {
               this.chunkBiomes.clear();
               this.biomeAsked.clear();
               this.biomeAsked.add(key);
            }

            java.nio.file.Path file = this.index.dimension().region().resolve("r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
            this.submit(() -> {
               short[] read = dev.chunkatlas.engine.ChunkBiomes.read(file, chunkX, chunkZ, dev.chunkatlas.GameColours.get());
               this.chunkBiomes.put(key, read == null ? new short[0] : read);
            });
         }

         return "";
      }

      if (biomes.length < 256) {
         return "";
      }

      String id = dev.chunkatlas.engine.BiomeIndex.name(biomes[(bz & 15) * 16 + (bx & 15)]);
      return IdText.valid(id) ? SeedMapLayer.biomeName(id) : "";
   }

   private String hoverText(int mouseX, int mouseY, int room) {
      int bx = (int)Math.floor(this.blockX(mouseX));
      int bz = (int)Math.floor(this.blockZ(mouseY));
      int chunkX = Math.floorDiv(bx, 16);
      int chunkZ = Math.floorDiv(bz, 16);

      List<String> parts = new ArrayList<>();
      if (!this.editing) {
         parts.add(Ui.t("chunkatlas.context.block", bx, bz));
         if (this.caveY != Integer.MIN_VALUE) {
            parts.add(Ui.t("chunkatlas.map.cave.y", this.caveY));
         }

         this.loaded.hover(chunkX, chunkZ, parts);

         String biome = this.savedBiome(bx, bz, chunkX, chunkZ);
         this.seed.hover(bx, bz, this.index.has(chunkX, chunkZ), parts, biome == null);
         if (biome != null && !biome.isEmpty()) {
            parts.add(biome);
         }

         return this.joinFitting(parts, room);
      }

      parts.add(Ui.t("chunkatlas.map.hover.chunk", chunkX, chunkZ));
      if (this.guarded().contains(chunkX, chunkZ)) {
         parts.add(Ui.t("chunkatlas.protect.hover"));
      }

      String biome = this.savedBiome(bx, bz, chunkX, chunkZ);
      if (biome != null && !biome.isEmpty()) {
         parts.add(biome);
      }

      this.seed.hover(bx, bz, this.index.has(chunkX, chunkZ), parts, biome == null);
      if (!this.index.has(chunkX, chunkZ)) {
         parts.add(Ui.t("chunkatlas.map.hover.nothing_saved"));
      } else {
         RegionFacts known = this.facts.get(WorldLayout.key(chunkX >> 5, chunkZ >> 5));
         if (known != null) {
            RegionFacts.ChunkInfo info = known.chunk(ChunkSelection.index(chunkX, chunkZ));
            switch (info.state()) {
               case 0:
                  parts.add(Ui.t("chunkatlas.map.hover.nothing_saved"));
                  break;
               case 1:
               case 2:
               default:
                  if (info.state() == 2) {
                     parts.add(Ui.t("chunkatlas.map.hover.unfinished"));
                  }

                  parts.add(Ui.played(info.inhabitedTicks()));
                  String ago = Ui.ago(info.savedAtSeconds());
                  if (!ago.isEmpty()) {
                     parts.add(ago);
                  }
                  break;
               case 3:
                  parts.add(Ui.t("chunkatlas.map.hover.unreadable"));
                  break;
               case 4:
                  parts.add(Ui.t("chunkatlas.map.hover.away"));
            }
         }
      }

      return this.joinFitting(parts, room);
   }

   private String joinFitting(List<String> parts, int room) {
      StringBuilder out = new StringBuilder();
      for (String part : parts) {
         String next = out.isEmpty() ? part : out + " · " + part;
         if (!out.isEmpty() && this.font.width(next) > room) {
            break;
         }

         out.setLength(0);
         out.append(next);
      }

      return out.toString();
   }

   private void drawNote(Gfx g) {
      String text;
      if (this.map == null) {
         text = Ui.t("chunkatlas.map.note.reading_world");
      } else if (this.map.layout().note() != WorldLayout.Note.NONE) {
         text = Ui.t(this.map.layout().note().key());
      } else if (this.dimension == null) {
         text = Ui.t("chunkatlas.map.note.no_terrain");
      } else if (this.index == null) {
         text = Ui.t("chunkatlas.map.note.reading_dimension", Ui.dimension(this.dimension));
      } else {
         text = Ui.t("chunkatlas.map.note.dimension_empty", Ui.dimension(this.dimension));
      }

      List<FormattedCharSequence> lines = this.font.split(Component.literal(text), Math.min(340, this.width - 40));
      int y = (this.mapTop + this.mapBottom) / 2 - lines.size() * 6;

      for (FormattedCharSequence line : lines) {
         g.centered(this.font, line, this.width / 2, y, Ui.GREY);
         y += 12;
      }
   }

   private static final MapScreen.Tool[] DOCK_TOOLS = {MapScreen.Tool.HAND, MapScreen.Tool.CHUNKS, MapScreen.Tool.SELECT, MapScreen.Tool.BRUSH, MapScreen.Tool.REGION};
   private static final String[] DOCK_ICONS = {"hand", "select", "box", "brush", "region"};
   private static final MapKey[] DOCK_KEYS = {MapKey.TOOL_HAND, MapKey.TOOL_SELECT, MapKey.TOOL_BOX, MapKey.TOOL_BRUSH, MapKey.TOOL_REGION};
   private Glass.Bar side;
   private Glass.Bar dock;

   private Glass.Bar commands;

   private void drawControls(Gfx g, int mouseX, int mouseY, boolean covered, boolean drawn) {
      List<Component> tip = null;
      boolean can = this.canEdit();
      String[] modes = {Ui.t("chunkatlas.map.mode.map"), Ui.t("chunkatlas.map.mode.edit")};
      int over = Glass.tabs(
         g, this.font, this.hits, Glass.MARGIN + 2, Glass.MARGIN + 2, modes, this.editing ? 1 : 0, new boolean[]{this.pick == null, can && drawn}, i -> this.setEditing(i == 1), mouseX, mouseY, covered
      );
      if (over == 0) {
         tip = Glass.tip(Ui.t("chunkatlas.map.mode.map.tip"), "E");
      } else if (over == 1) {
         tip = can
            ? Glass.tip(Ui.t("chunkatlas.map.mode.edit.tip"), "E")
            : Glass.tip(Ui.t(this.liveMap() ? "chunkatlas.map.mode.edit.live" : "chunkatlas.map.mode.edit.off"), null);
      }

      Glass.Bar column = new Glass.Bar(true).floating();
      Glass.Bar lower = new Glass.Bar(true).floating();
      if (drawn) {
         column.tile("layers", Glass.tip(Ui.t("chunkatlas.map.layers"), null), this.view != MapScreen.View.TERRAIN, Ui.WHITE, () -> this.open(MapScreen.Menu.VIEW, "layers"));
      }

      if (drawn) {
         boolean caveOn = this.caveY != Integer.MIN_VALUE;
         column.tile(
            "cave",
            Glass.tip(Ui.t(this.liveMap() ? "chunkatlas.map.cave.live" : caveOn ? "chunkatlas.map.cave.off" : "chunkatlas.map.cave"), null),
            caveOn,
            Ui.WHITE,
            this.liveMap() ? null : () -> this.setCave(caveOn ? Integer.MIN_VALUE : this.defaultCaveY(), false)
         );
      }

      boolean seedOk = this.seed.available();

      column.tile(
         "seed",
         seedOk ? Glass.tip(Ui.t("chunkatlas.seed.pill"), null) : Glass.tip2(Ui.t("chunkatlas.seed.pill"), Ui.t(this.seed.unavailableKey())),
         this.seed.shown() || !this.editing && this.seedPanel().isOpen(),
         Ui.WHITE,
         this.editing ? (seedOk ? this.seed::toggle : null) : seedOk || this.seed.needsSeed() ? () -> this.seedPanel().toggle() : null
      );
      List<WorldLayout.Dimension> dims = this.map == null ? List.of() : this.map.layout().dimensions();
      if (this.dimension != null) {
         column.tile(
            "dimension",
            Glass.tip(Ui.dimension(this.dimension), null),
            false,
            Ui.WHITE,
            this.job == null && dims.size() > 1 ? () -> this.open(MapScreen.Menu.DIMENSION, "dimension") : null
         );
      }

      if (this.live != null) {
         String why = dev.chunkatlas.TaskClient.blocked();
         column.tile(
            "chunks", why == null ? Glass.tip(Ui.t("chunkatlas.tasks.title"), null) : Glass.tip2(Ui.t("chunkatlas.tasks.title"), why), this.tasksShown(), Ui.WHITE,
            () -> this.tasksPanel().toggle()
         );
      }

      if (!this.editing && this.live != null) {
         lower.tile("waypoint", Glass.tip(Ui.t("chunkatlas.waypoints.title"), null), false, Ui.WHITE, this.job == null ? this::openWaypoints : null);
      }

      (this.editing ? column : lower).tile("settings", Glass.tip(Ui.t("chunkatlas.settings.open"), null), false, Ui.WHITE, this.job == null ? this::openSettings : null);
      this.side = column;
      List<Component> t = column.draw(g, this.font, this.hits, this.width - Glass.MARGIN - column.width(this.font), Glass.MARGIN, mouseX, mouseY, covered);

      List<Component> tLower = lower.draw(
         g, this.font, this.hits, this.width - Glass.MARGIN - lower.width(this.font), this.height - Glass.MARGIN - lower.height(this.font), mouseX, mouseY, covered
      );
      if (tLower != null) {
         t = tLower;
      }
      int[] seedBox = column.box("seed");
      if (seedBox != null && !this.editing && this.seed.needsSeed()) {
         int bx = seedBox[0] + seedBox[2] - 8;
         int by = seedBox[1] - 1;
         MapMarkers.shadow(g, bx + 1, by + 1, new int[][]{{0, 0, 7, 7}}, 0xFF);
         g.fill(bx + 1, by + 1, bx + 8, by + 8, MapMarkers.GOLD);
         g.text(this.font, "?", bx + 2, by + 1, 0xFF000000, false);
      }

      if (this.seedPanelShown()) {
         int right = this.width - Glass.MARGIN - column.width(this.font) - 2;
         List<Component> t2 = this.seedPanel.draw(g, this.hits, right, Glass.MARGIN, this.height - Glass.MARGIN - Glass.CHIP_H - 8, mouseX, mouseY, covered, 0.0F);
         if (t == null || t.isEmpty()) {
            t = t2;
         }
      }

      if (this.tasksShown()) {
         int left = Glass.MARGIN + (this.editing ? Glass.iconSize() + 8 : 0);
         int bottom = this.height - Glass.MARGIN - Glass.CHIP_H - 8 - (this.editing ? 22 : 0);
         List<Component> t2 = this.tasksPanel.draw(g, this.hits, left, Glass.MARGIN + Glass.tabsHeight() + 6, bottom, mouseX, mouseY, covered, 0.0F);
         if (t == null || t.isEmpty()) {
            t = t2;
         }
      }

      this.caveSlider = null;
      if (this.caveY != Integer.MIN_VALUE && !this.hideControls) {
         List<Component> t2 = this.drawCavePanel(g, mouseX, mouseY, covered);
         if (t == null || t.isEmpty()) {
            t = t2;
         }
      }

      if (t != null) {
         tip = t;
      }

      this.dock = null;
      this.commands = null;
      this.dockTop = this.height;
      if (this.editing && drawn) {
         ChunkSelection sel = this.selections.get(this.dimension);
         boolean any = sel != null && !sel.isEmpty();
         boolean idle = this.job == null;
         boolean local = !this.onServer();
         if (this.pick == null) {
            Glass.Bar cmds = new Glass.Bar(false).floating();
            List<Component> findTip = Glass.tip(Ui.t("chunkatlas.map.find"), "F");
            Runnable find = local ? () -> this.open(MapScreen.Menu.PICK, "find") : null;
            Runnable shrink = idle && local ? this::openShrink : null;
            boolean transfers = local || dev.chunkatlas.ServerLink.canEdit();
            Runnable paste = idle && transfers && this.hasClipboard() ? this::startPaste : null;
            List<Component> pasteTip = Glass.tip(Ui.t("chunkatlas.paste.menu"), MapKey.PASTE.keyName());
            List<Component> undoTip = Glass.tip(Ui.t(local ? "chunkatlas.map.undo" : "chunkatlas.server.cancel"), null);
            Runnable undo = local
               ? (idle && (this.lastEdit.isPresent() || !this.pending.isEmpty()) ? this::undo : null)
               : (dev.chunkatlas.ServerLink.waiting() > 0 ? this::cancelServerEdits : null);
            cmds.tile("find", findTip, this.menu == MapScreen.Menu.PICK, Ui.WHITE, find);
            cmds.tile("shrink", Glass.tip(Ui.t("chunkatlas.shrink.open"), null), false, Ui.WHITE, shrink);
            cmds.rule();
            cmds.tile("paste", pasteTip, this.pasteChunks != null, Ui.WHITE, paste);
            cmds.tile("undo", undoTip, false, Ui.WHITE, undo);

            this.commands = cmds;
            int cx = Glass.MARGIN + Glass.tabsWidth(this.font, modes) + 4;
            List<Component> t3 = cmds.draw(g, this.font, this.hits, cx, Glass.MARGIN - 1, mouseX, mouseY, covered);
            if (t3 != null) {
               tip = t3;
            }
         }

         if (this.pasteChunks != null) {
            String src = this.pasteClip == null ? "" : this.pasteClip.sourceWorld();
            PasteBar.State state = new PasteBar.State(this.pasteChunks.size(), src, this.pasteDx, this.pasteDz, this.pasteLift(), this.smartLift(), this.pasteKeep,
               this.onServer(), () -> this.askPasteOffset(true), () -> this.askPasteOffset(false), this::askPasteLift, keep -> this.pasteKeep = keep,
               this::cancelPaste, () -> this.placePaste(this.pasteDx, this.pasteDz));
            t = this.pasteBar.draw(g, this.font, this.hits, this.width, this.height, state, mouseX, mouseY, covered);
            this.dockTop = this.pasteBar.top();
            if (t != null) {
               tip = t;
            }
         } else {
         Glass.Bar tools = new Glass.Bar(true).floating();
         for (int n = 0; n < DOCK_TOOLS.length; n++) {
            MapScreen.Tool tool = DOCK_TOOLS[n];
            tools.tile(DOCK_ICONS[n], Glass.tip(this.toolLabel(tool), DOCK_KEYS[n].keyName()), tool == this.tool, Ui.WHITE, () -> this.pickTool(tool));
         }

         List<Component> tTools = tools.draw(g, this.font, this.hits, Glass.MARGIN, (this.height - tools.height(this.font)) / 2, mouseX, mouseY, covered);
         if (tTools != null) {
            tip = tTools;
         }

         Glass.Bar bar = new Glass.Bar(false).floating();
         if (this.pick == null) {
            if (any) {
               String count = Ui.plural("chunkatlas.map.count", sel.size()) + " ▾";
               bar.word(count, Glass.ACCENT, () -> this.open(MapScreen.Menu.SELECTION, count));
            } else {
               bar.label(Ui.t("chunkatlas.map.none_selected"), Glass.OFF);
            }

            this.generate.button(bar, any ? sel : null);
            bar.tile("copy", Glass.tip(Ui.t("chunkatlas.paste.copy"), MapKey.COPY.keyName()), false, Ui.WHITE, any && idle && (local || dev.chunkatlas.ServerLink.canEdit()) ? this::copySelection : null);
            boolean allGuarded = any && this.caches.unprotected(sel, this.guarded()) == 0;
            bar.tile(
               "protect", Glass.tip(Ui.t(allGuarded ? "chunkatlas.protect.remove" : "chunkatlas.protect.add"), null), allGuarded, Ui.WHITE, any ? () -> this.protect(!allGuarded) : null
            );
            bar.tile("more", Glass.tip(Ui.t("chunkatlas.map.more"), null), this.menu == MapScreen.Menu.MORE, Ui.WHITE, any ? () -> this.open(MapScreen.Menu.MORE, "more") : null);
            bar.tile("delete", Glass.tip(Ui.t("chunkatlas.map.delete"), "Del"), false, Glass.DANGER, any && idle ? () -> this.plan(ChunkEdits.Kind.DELETE) : null);
         } else if (any) {
            bar.label(this.width < 440 ? Ui.count(sel.size()) : Ui.plural("chunkatlas.map.selected", sel.size()), Glass.ACCENT);
            bar.word(Ui.t("chunkatlas.copy.next"), Glass.ACCENT, this::copyNext);
         }

         this.dock = bar;
         int dw = bar.width(this.font);
         this.dockTop = this.height - Glass.MARGIN - bar.height(this.font);
         t = bar.draw(g, this.font, this.hits, (this.width - dw) / 2, this.dockTop, mouseX, mouseY, covered);
         if (t != null) {
            tip = t;
         }
         }
      }

      List<Component> tGenerate = this.generate.drawChip(g, this.font, this.hits, this.editing, mouseX, mouseY, covered);
      if (tGenerate != null) {
         tip = tGenerate;
      }

      if (tip != null) {
         g.tooltip(this.font, tip, mouseX, mouseY);
      }
   }

   private int messageY() {
      return Glass.MARGIN + 3 + Glass.CHIP_H + 4 + (!this.editing && this.generate.chipShown() ? GenerateView.CHIP_H + 4 : 0);
   }

   private int[] caveSlider;
   private static final int CAVE_MIN = -64;
   private static final int CAVE_MAX = 320;

   private int[] cavePanelBox() {
      if (this.caveY == Integer.MIN_VALUE || this.hideControls) {
         return null;
      }

      return new int[]{Glass.MARGIN, Glass.MARGIN + Glass.tabsHeight() + 6, 118, 14 + 22 + 20 + (this.live != null ? 14 : 0) + 6};
   }

   int leftPanelRight() {
      int[] box = this.cavePanelBox();
      return box == null ? 0 : box[0] + box[2];
   }

   private List<Component> drawCavePanel(Gfx g, int mouseX, int mouseY, boolean covered) {
      boolean slice = this.map != null && (this.map.style() & TileRenderer.SLICE) != 0;
      int[] box = this.cavePanelBox();
      int x = box[0];
      int y = box[1];
      int w = box[2];
      int h = box[3];
      Glass.panel(g, x, y, w, h);
      List<Component> tip = null;
      int row = y + 5;
      g.text(this.font, Ui.t("chunkatlas.map.cave"), x + 6, row, Ui.WHITE);
      row += 13;

      int bw = 16;
      int yb = x + 6 + bw + 3;
      int yw = w - 12 - 2 * (bw + 3);
      this.glassButton(g, x + 6, row, bw, 16, "−", mouseX, mouseY, covered, () -> {
         this.caveFollow = false;
         this.setCave(this.caveY - this.caveStep(), false);
      });
      g.fill(yb, row, yb + yw, row + 16, 0xFF000000);
      g.outline(yb, row, yw, 16, 0xFF6A6A6A);
      String ytext = Ui.t("chunkatlas.map.cave.y", this.caveY);
      g.text(this.font, ytext, yb + (yw - this.font.width(ytext)) / 2, row + 4, Glass.ACCENT);
      this.hits.add(new Ui.Hit(yb, row, yb + yw, row + 16, this::askCaveY));
      if (!covered && Glass.over(mouseX, mouseY, yb, row, yw, 16)) {
         tip = Glass.tip(Ui.t("chunkatlas.map.cave.type"), null);
      }

      this.glassButton(g, yb + yw + 3, row, bw, 16, "+", mouseX, mouseY, covered, () -> {
         this.caveFollow = false;
         this.setCave(this.caveY + this.caveStep(), false);
      });
      row += 22;
      String[] modes = {Ui.t("chunkatlas.map.cave.floors"), Ui.t("chunkatlas.map.cave.slice")};
      int segW = Glass.segmentsWidth(this.font, modes);
      int over = Glass.segments(
         g, this.font, this.hits, x + (w - segW) / 2, row - 3, modes, slice ? 1 : 0, new boolean[]{true, true}, i -> {
            if ((i == 1) != slice) {
               this.toggleStyle(TileRenderer.SLICE);
            }
         }, mouseX, mouseY, covered
      );
      if (over >= 0) {
         tip = Glass.tip(Ui.t(over == 1 ? "chunkatlas.map.cave.slice.desc" : "chunkatlas.map.cave.floors.desc"), null);
      }

      row += 20;
      if (this.live != null) {
         row += 4;
         this.checkRow(g, x + 6, row, Ui.t("chunkatlas.map.cave.follow"), this.caveFollow, () -> this.caveFollow = !this.caveFollow);
      }

      int sx = this.width - Glass.MARGIN - 14;
      int sy = Glass.MARGIN + (this.side == null ? 0 : this.side.height(this.font)) + 6;
      int sh = Math.max(60, Math.min(160, this.height - sy - Glass.MARGIN - 40));
      if (sh >= 60) {
         Glass.panel(g, sx, sy, 14, sh);
         int tx = sx + 6;
         int ty = sy + 6;
         int th = sh - 12;
         g.fill(tx, ty, tx + 2, ty + th, 0x33FFFFFF);
         int sea = ty + (int)Math.round((CAVE_MAX - 63) / (double)(CAVE_MAX - CAVE_MIN) * th);
         g.fill(tx - 2, sea, tx + 4, sea + 1, 0x99FFFFFF);
         int ky = ty + (int)Math.round((CAVE_MAX - this.caveY) / (double)(CAVE_MAX - CAVE_MIN) * th);
         g.fill(tx - 3, ky - 2, tx + 5, ky + 2, 0xFF000000);
         g.fill(tx - 2, ky - 1, tx + 4, ky + 1, Glass.ACCENT);
         this.caveSlider = new int[]{sx, ty, 14, th};
         if (!covered && Glass.over(mouseX, mouseY, sx, sy, 14, sh)) {
            tip = Glass.tip(Ui.t("chunkatlas.map.cave.slider"), null);
         }
      }

      return tip;
   }

   private void glassButton(Gfx g, int x, int y, int w, int h, String text, int mouseX, int mouseY, boolean covered, Runnable action) {
      boolean hover = !covered && Glass.over(mouseX, mouseY, x, y, w, h);
      g.fill(x, y, x + w, y + h, hover ? Glass.HOT : Glass.TINT);
      g.outline(x, y, w, h, hover ? 0xC0FFFFFF : 0x38FFFFFF);
      g.text(this.font, text, x + (w - this.font.width(text)) / 2, y + (h - 8) / 2, Ui.WHITE);
      this.hits.add(new Ui.Hit(x, y, x + w, y + h, action));
   }

   private void checkRow(Gfx g, int x, int y, String label, boolean on, Runnable action) {
      g.fill(x, y, x + 8, y + 8, 0xFFA0A0A0);
      g.fill(x + 1, y + 1, x + 7, y + 7, 0xFF1E1E1E);
      if (on) {
         g.fill(x + 2, y + 4, x + 3, y + 5, Ui.WHITE);
         g.fill(x + 3, y + 5, x + 4, y + 6, Ui.WHITE);
         g.fill(x + 4, y + 4, x + 5, y + 5, Ui.WHITE);
         g.fill(x + 5, y + 3, x + 6, y + 4, Ui.WHITE);
         g.fill(x + 6, y + 2, x + 7, y + 3, Ui.WHITE);
      }

      g.text(this.font, label, x + 12, y, Ui.WHITE);
      this.hits.add(new Ui.Hit(x, y - 2, x + 12 + this.font.width(label), y + 10, action));
   }

   private void askCaveY() {
      this.keep = true;
      Game.open(this.mc, new AskScreen(this, Component.translatable("chunkatlas.map.cave.type"), Integer.toString(this.caveY), Ui.t("chunkatlas.map.cave.type.note"), s -> {
         try {
            int v = Integer.parseInt(s.strip());
            return v >= CAVE_MIN && v <= CAVE_MAX;
         } catch (NumberFormatException e) {
            return false;
         }
      }, s -> {
         this.caveFollow = false;
         this.setCave(Integer.parseInt(s.strip()), false);
      }));
   }

   private void caveFromSlider(double y) {
      int[] s = this.caveSlider;
      if (s != null) {
         double k = Math.max(0.0, Math.min(1.0, (y - s[1]) / (double)s[3]));
         this.caveFollow = false;
         this.setCave((int)Math.round(CAVE_MAX - k * (CAVE_MAX - CAVE_MIN)), false);
      }
   }

   private void openGoTo() {
      if (this.index == null) {
         return;
      }

      this.keep = true;
      Game.open(this.mc, new GoToScreen(this, (int)Math.floor(this.cx), (int)Math.floor(this.cz), (x, z) -> {
         this.follow = false;
         this.cx = x + 0.5;
         this.cz = z + 0.5;
         this.anchorX = this.mapCx();
         this.anchorY = this.mapCy();
         this.zoomTarget = this.clampZoom(Math.max(this.zoomTarget, 1.0));
         this.clampView();
      }));
   }

   public void openWaypoints() {
      this.keep = true;
      Game.open(this.mc, new dev.chunkatlas.minimap.gui.WaypointsScreen(this));
   }

   private void openSettings() {
      this.keep = true;
      Game.open(this.mc, new SettingsScreen(this));
   }

   Path worldFolder() {
      return this.worldFolder;
   }

   void cacheCleared() {
      WorldMap open = this.map;
      if (open != null) {
         open.dropCache();
      }
   }

   void settingsChanged() {
      this.showPlayers = Settings.players();
      this.showProtected = Settings.protectedShown();
      this.showGrid = Settings.grid();
      this.syncLook();
   }

   private void open(MapScreen.Menu which, String tile) {
      Glass.Bar bar = which == MapScreen.Menu.PICK ? (this.commands != null ? this.commands : this.dock)
         : which == MapScreen.Menu.MORE || which == MapScreen.Menu.SELECTION ? this.dock : this.side;
      int[] box = bar == null ? null : bar.box(tile);
      this.menuFrom = box == null ? new int[]{this.width / 2, this.height / 2, 0, 0} : box;
      this.menu = which;
   }

   private boolean canEdit() {
      if (this.liveMap()) {
         return dev.chunkatlas.ServerLink.canEdit();
      }

      if (this.pick != null || Settings.editWithoutCheats()) {
         return true;
      }

      if (this.live != null) {
         return this.mc.player != null && this.mc.player.connection != null && this.mc.player.connection.getCommands().getRoot().getChild("gamemode") != null;
      }

      return this.map != null && this.map.markers().commands();
   }

   private void syncLook() {
      if (this.map != null) {
         int now = this.map.style();
         int want = now & ~dev.chunkatlas.Settings.LOOK_BITS | dev.chunkatlas.Settings.lookBits();
         if (want != now) {
            this.map.style(want);
            this.dropTiles();
            this.seed.restyle();
         }
      }
   }

   private static final int COLOUR_BY = TileRenderer.BIOMES | TileRenderer.HEIGHTMAP | TileRenderer.DEPTHMAP;

   private void colourBy(int bit) {
      if (this.map != null && (this.map.style() & COLOUR_BY) != bit) {
         this.map.style(this.map.style() & ~COLOUR_BY | bit);
         this.dropTiles();
         this.seed.restyle();
      }

      this.setView(MapScreen.View.TERRAIN);
   }

   private void toggleStyle(int bit) {
      if (this.map != null) {
         this.map.style(this.map.style() ^ bit);
         this.dropTiles();
         this.seed.restyle();
      }
   }

   int seedCaveY() {
      return this.caveY == Integer.MIN_VALUE ? dev.chunkatlas.seed.SeedSampler.NO_CAVE : this.caveY;
   }

   boolean seedBiomes() {
      return this.map != null && (this.map.style() & TileRenderer.BIOMES) != 0;
   }

   private int caveStep() {
      return Keys.shift() ? 1 : 8;
   }

   private int defaultCaveY() {
      if (this.live != null && this.mc.player != null && Ids.of(this.mc.player.level().dimension()).equals(this.dimension)) {
         return this.mc.player.getBlockY() + 1;
      }

      return "minecraft:the_nether".equals(this.dimension) ? 100 : 40;
   }

   private void setCave(int y, boolean auto) {
      int clamped = y == Integer.MIN_VALUE ? y : Math.max(CAVE_MIN, Math.min(CAVE_MAX, y));
      if (clamped == this.caveY) {
         return;
      }

      this.caveY = clamped;
      this.caveAuto = auto && clamped != Integer.MIN_VALUE;
      if (this.map != null) {
         this.map.cave(clamped);
      }

      this.dropTiles();
   }

   private boolean wasUnderRoof;

   private void caveUnderground() {
      if (this.live == null || this.mc.player == null || this.mc.level == null || this.liveMap() || !Settings.undergroundCaves()) {
         return;
      }

      if (!Ids.of(this.mc.player.level().dimension()).equals(this.dimension) || "minecraft:the_nether".equals(this.dimension)) {
         return;
      }

      boolean under = this.underRoof();
      if (under && !this.wasUnderRoof && this.caveY == Integer.MIN_VALUE) {
         this.setCave(this.mc.player.getBlockY() + 1, true);
         this.caveFollow = true;
      } else if (!under && this.wasUnderRoof && this.caveAuto) {
         this.setCave(Integer.MIN_VALUE, false);
      }

      this.wasUnderRoof = under;
   }

   private boolean underRoof() {
      int size = Math.max(1, dev.chunkatlas.minimap.Config.get().caveRoof);
      int half = size / 2;
      int px = this.mc.player.getBlockX();
      int pz = this.mc.player.getBlockZ();
      int head = this.mc.player.getBlockY() + 1;
      net.minecraft.core.BlockPos.MutableBlockPos at = new net.minecraft.core.BlockPos.MutableBlockPos();
      for (int dz = -half; dz <= size - 1 - half; dz++) {
         for (int dx = -half; dx <= size - 1 - half; dx++) {
            int top = this.mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px + dx, pz + dz);
            if (top <= head + 1 || !this.mc.level.getFluidState(at.set(px + dx, top - 1, pz + dz)).isEmpty()) {
               return false;
            }
         }
      }

      return true;
   }

   private void autoCave() {
      boolean nether = "minecraft:the_nether".equals(this.dimension);
      if (nether && this.caveY == Integer.MIN_VALUE && !this.liveMap()) {
         this.setCave(this.defaultCaveY(), true);
      } else if (!nether && this.caveAuto) {
         this.setCave(Integer.MIN_VALUE, false);
      }
   }

   private boolean liveMap() {
      return this.map != null && this.map.fromLiveMap();
   }

   private void setEditing(boolean on) {
      if (on && !this.canEdit()) {
         this.say(Ui.t(this.liveMap() ? "chunkatlas.map.mode.edit.live" : "chunkatlas.map.mode.edit.off"), Ui.YELLOW);
      } else if (on || this.pick == null) {
         this.editing = on;
         this.menu = MapScreen.Menu.NONE;
         this.drag = MapScreen.Drag.NONE;
      }
   }

   private boolean canTeleport() {
      return this.live != null
         && this.mc.player != null
         && this.mc.player.connection != null
         && this.mc.player.connection.getCommands().getRoot().getChild("tp") != null
         && this.mc.player.connection.getCommands().getRoot().getChild("spreadplayers") != null
         && this.mc.player.connection.getCommands().getRoot().getChild("execute") != null;
   }

   private void drawMenu(Gfx g, int mouseX, int mouseY) {
      if (this.menu == MapScreen.Menu.VIEW) {
         List<List<Ui.Item>> columns = this.layerColumns();
         int[] size = Glass.columnsSize(this.font, columns);
         this.menuW = size[0];
         this.menuH = size[1];
         int[] from = this.menuFrom;
         this.menuX = Math.max(4, from[0] - Glass.INSET - 2 - this.menuW);
         this.menuY = Math.max(4, Math.min(from[1] - Glass.INSET, this.height - this.menuH - 4));
         g.layer();
         Glass.columns(g, this.font, this.top, columns, this.menuX, this.menuY, mouseX, mouseY);
         this.loaded.layerTip(g, Glass.itemAt(this.font, columns, this.menuX, this.menuY, mouseX, mouseY), mouseX, mouseY);
         return;
      }

      List<Ui.Item> items = switch (this.menu) {
         case PICK -> this.pickItems();
         case CONTEXT -> this.contextItems();
         case MORE -> this.moreItems();
         case SELECTION -> this.selectionItems();
         default -> this.dimensionItems();
      };
      this.menuW = Ui.menuWidth(this.font, items);
      this.menuH = Ui.menuHeight(items);
      int[] from = this.menuFrom;
      if (this.menu == MapScreen.Menu.CONTEXT) {
         int bottom = Math.min(this.height, this.dockTop) - 2;
         this.menuX = this.contextX + 2 + this.menuW <= this.width - 4 ? this.contextX + 2 : Math.max(4, this.contextX - 2 - this.menuW);
         this.menuY = this.contextY + this.menuH <= bottom ? this.contextY : Math.max(4, Math.min(this.contextY - this.menuH, bottom - this.menuH));
      } else if (this.menu == MapScreen.Menu.PICK && this.commands != null) {
         this.menuX = Math.max(4, Math.min(from[0], this.width - this.menuW - 4));
         this.menuY = Math.min(from[1] + from[3] + 2, this.height - this.menuH - 4);
      } else if (this.menu == MapScreen.Menu.PICK || this.menu == MapScreen.Menu.MORE || this.menu == MapScreen.Menu.SELECTION) {
         this.menuX = Math.max(4, Math.min(from[0], this.width - this.menuW - 4));
         this.menuY = Math.max(4, this.dockTop - 2 - this.menuH);
      } else {
         this.menuX = Math.max(4, from[0] - Glass.INSET - 2 - this.menuW);
         this.menuY = Math.max(4, Math.min(from[1] - Glass.INSET, this.height - this.menuH - 4));
      }

      g.layer();
      Ui.menu(g, this.font, this.top, items, this.menuX, this.menuY, mouseX, mouseY);
   }

   private void drawJob(Gfx g, int mouseX, int mouseY) {
      MapScreen.Job j = this.job;
      if (j.progress != null) {
         j.done = j.progress.getAsInt();
      }

      String text = j.text + (j.progress != null ? "  " + j.done + "%" : j.total > 0 ? "  " + Ui.t("chunkatlas.map.job.progress", Ui.count(Math.min(j.done, j.total)), Ui.count(j.total)) : "");
      int w = Math.max(160, this.font.width(text) + 24);
      int h = j.canCancel ? 56 : 28;
      int x = (this.width - w) / 2;
      int y = (this.mapTop + this.mapBottom - h) / 2;
      g.layer();
      g.fill(x, y, x + w, y + h, Glass.MENU);
      g.outline(x, y, w, h, Glass.MENU_EDGE);
      g.centered(this.font, text, this.width / 2, y + 8, Ui.WHITE);
      if (j.total > 0) {
         g.fill(x + 8, y + 21, x + w - 8, y + 23, 0x40FFFFFF);
         g.fill(x + 8, y + 21, x + 8 + (int)((long)(w - 16) * Math.min(j.done, j.total) / j.total), y + 23, Glass.ACCENT);
      }

      if (j.canCancel) {
         Glass.Bar cancel = new Glass.Bar(false).word(Ui.t("chunkatlas.map.cancel"), Ui.WHITE, () -> {
            j.cancelled = true;
            this.job = null;
         });
         cancel.draw(g, this.font, this.top, (this.width - cancel.width(this.font)) / 2, y + 28, mouseX, mouseY, false);
      }
   }

   private String fit(String text, int room) {
      return this.font.width(text) <= room ? text : this.font.plainSubstrByWidth(text, Math.max(0, room - this.font.width("…"))) + "…";
   }

   private void say(String text, int colour) {
      this.banner = text;
      this.bannerColour = colour;
      this.bannerUntil = System.currentTimeMillis() + 3000L + Math.min(9000L, text.length() * 70L);
   }

   private String toolLabel(MapScreen.Tool t) {
      String label = Ui.t("chunkatlas.map.tool." + t.name().toLowerCase(java.util.Locale.ROOT));
      if (t == MapScreen.Tool.BRUSH) {
         int side = BRUSH_SIZES[this.brush] * 2 + 1;
         label = label + " " + side + "×" + side;
      }

      return label;
   }

   private void pickTool(MapScreen.Tool t) {
      if (t == MapScreen.Tool.BRUSH && this.tool == MapScreen.Tool.BRUSH) {
         this.brush = (this.brush + 1) % BRUSH_SIZES.length;
      }

      this.tool = t;
   }

   private void paintAt(double screenX, double screenY, boolean add) {
      if (this.index == null) {
         return;
      }

      int cx = Math.floorDiv((int)Math.floor(this.blockX(screenX)), 16);
      int cz = Math.floorDiv((int)Math.floor(this.blockZ(screenY)), 16);
      ChunkSelection sel = this.selection();
      if (this.tool == MapScreen.Tool.CHUNKS && this.regionScale()) {
         int rx = cx >> 5;
         int rz = cz >> 5;
         long[] there = this.pickableIn(rx, rz);
         if (add && there != null) {
            sel.add(rx, rz, there);
         } else if (!add) {
            sel.remove(rx, rz, EVERY);
         }

         return;
      }

      int r = this.tool == MapScreen.Tool.CHUNKS ? 0 : BRUSH_SIZES[this.brush];

      for (int dz = -r; dz <= r; dz++) {
         for (int dx = -r; dx <= r; dx++) {
            if (dx * dx + dz * dz <= r * r + r) {
               if (!add) {
                  sel.remove(cx + dx, cz + dz);
               } else if (this.pickable(cx + dx, cz + dz)) {
                  sel.add(cx + dx, cz + dz);
               }
            }
         }
      }
   }

   private static final long[] EVERY = java.util.stream.LongStream.generate(() -> -1L).limit(ChunkSelection.WORDS).toArray();

   private boolean pickUnsaved() {
      return this.seed.shown();
   }

   private boolean pickable(int chunkX, int chunkZ) {
      return this.pickUnsaved() || this.index.has(chunkX, chunkZ);
   }

   private long[] pickableIn(int rx, int rz) {
      return this.pickUnsaved() ? EVERY.clone() : this.index.present().get(WorldLayout.key(rx, rz));
   }

   private Map<Long, long[]> pickable(int[] c, boolean every) {
      if (!every && !this.pickUnsaved()) {
         return this.index.present();
      }

      Map<Long, long[]> all = new HashMap<>();
      for (int rz = Math.floorDiv(Math.min(c[1], c[3]), 32); rz <= Math.floorDiv(Math.max(c[1], c[3]), 32); rz++) {
         for (int rx = Math.floorDiv(Math.min(c[0], c[2]), 32); rx <= Math.floorDiv(Math.max(c[0], c[2]), 32); rx++) {
            all.put(WorldLayout.key(rx, rz), EVERY);
         }
      }

      return all;
   }

   private boolean regionScale() {
      return this.zoom * 16.0 < REGION_SCALE_PIXELS;
   }

   private static final double REGION_SCALE_PIXELS = 4.0;

   private boolean chunkSelectedAt(double screenX, double screenY) {
      int cx = Math.floorDiv((int)Math.floor(this.blockX(screenX)), 16);
      int cz = Math.floorDiv((int)Math.floor(this.blockZ(screenY)), 16);
      ChunkSelection sel = this.selections.get(this.dimension);
      if (sel == null) {
         return false;
      }

      if (this.regionScale()) {
         long[] there = this.pickableIn(cx >> 5, cz >> 5);
         return there != null && sel.count(cx >> 5, cz >> 5) == java.util.Arrays.stream(there).map(Long::bitCount).sum();
      }

      return sel.contains(cx, cz);
   }

   private void paintStroke(double fromX, double fromY, double toX, double toY, boolean add) {
      double chunkPixels = Math.max(1.0, this.zoom * 16.0);
      double length = Math.hypot(toX - fromX, toY - fromY);
      int steps = Math.max(1, (int)Math.ceil(length / chunkPixels));

      for (int n = 1; n <= steps; n++) {
         double f = (double)n / steps;
         this.paintAt(fromX + (toX - fromX) * f, fromY + (toY - fromY) * f, add);
      }
   }

   private void pickRegions(int[] c, boolean add, boolean click) {
      ChunkSelection sel = this.selection();
      int rx0 = Math.floorDiv(c[0], 32);
      int rz0 = Math.floorDiv(c[1], 32);
      int rx1 = Math.floorDiv(c[2], 32);
      int rz1 = Math.floorDiv(c[3], 32);

      for (int rz = rz0; rz <= rz1; rz++) {
         for (int rx = rx0; rx <= rx1; rx++) {
            long[] here = add || click ? this.pickableIn(rx, rz) : EVERY;
            if (here != null) {
               boolean on = add;
               if (click) {
                  long[] had = sel.bits(rx, rz);
                  boolean all = had != null;
                  for (int w = 0; all && w < here.length; w++) {
                     all = (had[w] & here[w]) == here[w];
                  }

                  on = !all;
               }

               if (on) {
                  sel.add(rx, rz, here);
               } else {
                  sel.remove(rx, rz, here);
               }
            }
         }
      }
   }

   private int contextX;
   private int contextY;
   private int contextBlockX;
   private int contextBlockZ;

   private dev.chunkatlas.minimap.waypoint.Waypoint waypointAt(int sx, int sy) {
      if (!dev.chunkatlas.minimap.Config.get().waypointsOnMap) {
         return null;
      }

      for (dev.chunkatlas.minimap.waypoint.Waypoint w : dev.chunkatlas.minimap.waypoint.Waypoints.all()) {
         double[] p = w.visible ? w.placeIn(this.dimension) : null;
         if (p != null) {
            int x = (int)Math.floor(this.screenX(p[0]));
            int y = (int)Math.floor(this.screenY(p[2]));
            if (sx >= x - 6 && sx <= x + 6 && sy >= y - 15 && sy <= y + 1) {
               return w;
            }
         }
      }

      return null;
   }

   private void openContext(double x, double y) {
      this.contextX = (int)x;
      this.contextY = (int)y;
      this.contextBlockX = (int)Math.floor(this.blockX(x));
      this.contextBlockZ = (int)Math.floor(this.blockZ(y));
      this.menu = MapScreen.Menu.CONTEXT;
   }

   private List<Ui.Item> contextItems() {
      List<Ui.Item> items = new ArrayList<>();
      int bx = this.contextBlockX;
      int bz = this.contextBlockZ;
      int cx = Math.floorDiv(bx, 16);
      int cz = Math.floorDiv(bz, 16);
      boolean has = this.index != null && this.index.has(cx, cz);
      LevelMarkers.Point me = this.marker(this.map == null ? Optional.empty() : this.map.markers().player());
      if (this.live != null && this.mc.player != null) {
         me = new LevelMarkers.Point(Ids.of(this.mc.player.level().dimension()), this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ());
      }

      Runnable copyCoords = () -> {
         this.mc.keyboardHandler.setClipboard(bx + " ~ " + bz);
         this.say(Ui.t("chunkatlas.context.copied", bx + " ~ " + bz), Ui.GREY);
      };
      Runnable measure = () -> this.measureFrom = new double[]{bx + 0.5, bz + 0.5};
      if (!this.editing) {
         dev.chunkatlas.minimap.waypoint.Waypoint w = this.live == null ? null : this.waypointAt(this.contextX, this.contextY);
         if (w != null) {
            items.add(Ui.Item.info(w.name.isBlank() ? w.label() : w.name, w.x + " " + w.y + " " + w.z));
            items.add(new Ui.Item(Ui.t("chunkatlas.context.edit_waypoint"), null, () -> {
               this.keep = true;
               Game.open(this.mc, new dev.chunkatlas.minimap.gui.WaypointsScreen(this, w));
            }));
            items.add(new Ui.Item(Ui.t("chunkatlas.context.delete_waypoint"), null, () -> {
               dev.chunkatlas.minimap.waypoint.Waypoints.remove(w);
               this.say(Ui.t("chunkatlas.context.waypoint_deleted", w.name.isBlank() ? w.label() : w.name), Ui.GREY);
            }, Ui.Kind.DANGER));
            items.add(Ui.Item.separator());
         }

         String here = this.savedBiome(bx, bz, cx, cz);
         items.add(Ui.Item.info(Ui.t("chunkatlas.context.block", bx, bz), here != null && !here.isEmpty() ? here : here == null ? this.seed.biomeName(bx, bz) : null));
         String away = me != null && me.dimension().equals(this.dimension)
            ? Ui.t("chunkatlas.context.distance", Ui.count(Math.round(Math.hypot(bx + 0.5 - me.x(), bz + 0.5 - me.z()))))
            : Ui.t("chunkatlas.context.region", Math.floorDiv(cx, 32), Math.floorDiv(cz, 32));
         String level = this.loaded.levelName(cx, cz);
         items.add(Ui.Item.info(Ui.t("chunkatlas.context.chunk", cx, cz), level != null ? level : away));
         this.loaded.contextRows(items, cx, cz);
         items.add(Ui.Item.separator());
         if (this.live != null) {
            boolean known = this.teleportHeight(bx, bz) >= 0;
            items.add(new Ui.Item(Ui.t(known ? "chunkatlas.context.teleport" : "chunkatlas.context.teleport_guess"), null, this.canTeleport() ? () -> this.teleport(bx, bz) : null));
            String dim = this.dimension;
            int by = this.mc.player != null && Ids.of(this.mc.player.level().dimension()).equals(dim) ? this.mc.player.getBlockY() : 64;
            items.add(new Ui.Item(Ui.t("chunkatlas.context.waypoint"), null, this.mc.player == null ? null : () -> {
               this.keep = true;
               Game.open(this.mc, dev.chunkatlas.minimap.gui.WaypointsScreen.adding(this, bx, by, bz, dim));
            }));
         }

         items.add(new Ui.Item(Ui.t("chunkatlas.context.measure"), null, measure));
         items.add(new Ui.Item(Ui.t("chunkatlas.context.copy_coords"), null, copyCoords));
         items.add(new Ui.Item(Ui.t("chunkatlas.goto.open"), MapKey.GO_TO.keyName(), this::openGoTo));
         items.add(new Ui.Item(Ui.t("chunkatlas.picture.save"), null, this.index != null && this.job == null ? this::savePicture : null));
         items.add(Ui.Item.separator());
         items.add(new Ui.Item(Ui.t("chunkatlas.context.edit_here"), MapKey.EDIT_MODE.keyName(), has && this.canEdit() ? () -> {
            this.setEditing(true);
            this.selection().add(cx, cz);
         } : null));
         return items;
      }

      items.add(Ui.Item.info(Ui.t("chunkatlas.context.chunk", cx, cz), Ui.t("chunkatlas.map.hover.region", cx >> 5, cz >> 5)));
      RegionFacts known = has ? this.facts.get(WorldLayout.key(cx >> 5, cz >> 5)) : null;
      if (known != null) {
         RegionFacts.ChunkInfo info = known.chunk(ChunkSelection.index(cx, cz));
         String ago = Ui.ago(info.savedAtSeconds());
         items.add(Ui.Item.info(Ui.played(info.inhabitedTicks()), ago.isEmpty() ? null : ago));
      } else if (!has) {
         items.add(Ui.Item.info(Ui.t("chunkatlas.map.hover.nothing_saved"), null));
      }

      items.add(Ui.Item.separator());
      ChunkSelection sel = this.selection();
      boolean on = has && sel.contains(cx, cz);
      items.add(new Ui.Item(Ui.t(on ? "chunkatlas.context.deselect_chunk" : "chunkatlas.context.select_chunk"), null, has ? () -> {
         if (on) {
            sel.remove(cx, cz);
         } else {
            sel.add(cx, cz);
         }
      } : null));
      long[] there = this.index == null ? null : this.index.present().get(WorldLayout.key(cx >> 5, cz >> 5));
      items.add(new Ui.Item(Ui.t("chunkatlas.context.select_region"), null, there == null ? null : () -> sel.add(cx >> 5, cz >> 5, there)));
      boolean guarded = has && this.guarded().contains(cx, cz);
      items.add(new Ui.Item(Ui.t(guarded ? "chunkatlas.protect.remove" : "chunkatlas.protect.add"), null, has && this.pick == null ? () -> {
         ChunkSelection one = new ChunkSelection();
         one.add(cx, cz);
         ChunkSelection keep = this.selections.put(this.dimension, one);
         this.protect(!guarded);
         this.selections.put(this.dimension, keep == null ? new ChunkSelection() : keep);
      } : null));
      boolean local = !this.onServer();
      items.add(new Ui.Item(Ui.t("chunkatlas.nbt.menu"), null, has && local && this.pick == null ? () -> this.openNbt(cx, cz) : null));
      items.add(new Ui.Item(Ui.t("chunkatlas.fields.menu"), null, has && (local || ForceLoad.onServer()) && this.pick == null && this.job == null ? () -> {
         ChunkSelection keep = this.selections.get(this.dimension);
         if (keep == null || !keep.contains(cx, cz)) {
            ChunkSelection one = new ChunkSelection();
            one.add(cx, cz);
            this.selections.put(this.dimension, one);
         }

         this.openFields();
      } : null));
      items.add(Ui.Item.separator());
      items.add(new Ui.Item(Ui.t("chunkatlas.context.copy_coords"), null, copyCoords));
      items.add(new Ui.Item(Ui.t("chunkatlas.context.measure"), null, measure));
      items.add(Ui.Item.separator());
      items.add(Ui.Item.danger(Ui.t("chunkatlas.context.delete_chunk"), MapKey.DELETE.keyName(), has && this.pick == null && this.job == null ? () -> {
         ChunkSelection one = new ChunkSelection();
         one.add(cx, cz);
         this.selections.put(this.dimension, one);
         this.plan(ChunkEdits.Kind.DELETE);
      } : null));
      return items;
   }

   private int teleportHeight(int bx, int bz) {
      if ("minecraft:the_nether".equals(this.dimension)) {
         return -1;
      }

      int cx = Math.floorDiv(bx, 16);
      int cz = Math.floorDiv(bz, 16);
      if (this.mc.level != null && this.mc.player != null && Ids.of(this.mc.player.level().dimension()).equals(this.dimension)
         && this.mc.level.getChunkSource().hasChunk(cx, cz)) {
         return this.mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
      }

      if (this.index != null && this.index.has(cx, cz) && this.index.dimension() != null) {
         int[] heights = dev.chunkatlas.engine.ChunkBiomes.heights(
            this.index.dimension().region().resolve("r." + (cx >> 5) + "." + (cz >> 5) + ".mca"), cx, cz, dev.chunkatlas.GameColours.get()
         );
         if (heights != null) {
            return heights[(bz & 15) * 16 + (bx & 15)] + 1;
         }
      }

      return -1;
   }

   private void teleport(int bx, int bz) {
      if (this.mc.player == null || this.mc.player.connection == null) {
         return;
      }

      int y = this.teleportHeight(bx, bz);
      boolean nether = "minecraft:the_nether".equals(this.dimension);
      String command = y >= 0
         ? "tp @s " + (bx + 0.5) + " " + y + " " + (bz + 0.5)
         : "spreadplayers " + (bx + 0.5) + " " + (bz + 0.5) + " 0 1 " + (nether ? "under 120 " : "") + "false @s";

      if (!Ids.of(this.mc.player.level().dimension()).equals(this.dimension)) {
         command = "execute in " + this.dimension + " run " + command;
      }

      this.onClose();
      this.mc.player.connection.sendCommand(command);
   }

   private List<List<Ui.Item>> layerColumns() {
      List<Ui.Item> left = new ArrayList<>();
      List<Ui.Item> right = new ArrayList<>();
      int styleNow = this.map == null ? 0 : this.map.style();
      boolean biomeView = (styleNow & TileRenderer.BIOMES) != 0;
      boolean server = this.liveMap();
      left.add(Ui.Item.heading(Ui.t("chunkatlas.map.layers.colour_by")));
      int colourBy = styleNow & COLOUR_BY;
      boolean terrainLook = this.view == MapScreen.View.TERRAIN;
      left.add(Ui.Item.radio(this.viewLabel(MapScreen.View.TERRAIN), terrainLook && colourBy == 0, () -> this.colourBy(0)));
      left.add(Ui.Item.radio(Ui.t("chunkatlas.map.layers.biomes"), terrainLook && biomeView, server ? null : () -> this.colourBy(TileRenderer.BIOMES)));

      boolean detailed = Settings.detailed() && !server;
      left.add(Ui.Item.radio(Ui.t("chunkatlas.map.layers.height"), terrainLook && colourBy == TileRenderer.HEIGHTMAP, detailed ? () -> this.colourBy(TileRenderer.HEIGHTMAP) : null));
      left.add(Ui.Item.radio(Ui.t("chunkatlas.map.layers.depth"), terrainLook && colourBy == TileRenderer.DEPTHMAP, detailed ? () -> this.colourBy(TileRenderer.DEPTHMAP) : null));

      boolean canPick = biomeView && this.seed.available() && this.seed.shown() == this.seed.shown();
      String picked = this.seed.highlightName();
      left.add(new Ui.Item("   " + Ui.t("chunkatlas.map.layers.pick_biomes"), picked, canPick ? this.seed::openBiomes : null));
      if (picked != null) {
         left.add(new Ui.Item("   " + Ui.t("chunkatlas.seed.menu.clear_highlight"), null, () -> this.seed.highlight(-1)));
      }

      left.add(Ui.Item.heading(Ui.t("chunkatlas.map.layers.chunk_data")));
      for (MapScreen.View v : MapScreen.View.values()) {
         if (v != MapScreen.View.TERRAIN && v != MapScreen.View.CUSTOM) {
            left.add(Ui.Item.radio(this.viewLabel(v), v == this.view, server ? null : () -> this.setView(v)));
         }
      }

      left.add(Ui.Item.radio(Ui.t("chunkatlas.map.layers.custom_nbt"), this.view == MapScreen.View.CUSTOM, server || this.job != null ? null : this::openCustomNbt));

      right.add(Ui.Item.heading(Ui.t("chunkatlas.map.layers.show")));
      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.players"), this.showPlayers, () -> Settings.players(this.showPlayers = !this.showPlayers)));

      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.mobs"), Settings.mobs(), this.live == null ? null : () -> Settings.mobs(!Settings.mobs()))
         .withTip(Ui.t(this.live == null ? "chunkatlas.map.layers.mobs.off" : "chunkatlas.map.layers.mobs.tip")));
      dev.chunkatlas.minimap.Config mini = dev.chunkatlas.minimap.Config.get();
      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.waypoints"), mini.waypointsOnMap, this.live == null ? null : () -> {
         mini.waypointsOnMap = !mini.waypointsOnMap;
         mini.save();
      }));
      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.portals"), Settings.portals(), server ? null : () -> Settings.portals(!Settings.portals())));
      right.add(this.loaded.layerItem());
      right.add(this.spawnable.layerItem());
      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.footsteps"), Settings.footsteps(), () -> Settings.footsteps(!Settings.footsteps())));
      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.protected"), this.showProtected, () -> Settings.protectedShown(this.showProtected = !this.showProtected))
         .withTip(Ui.t("chunkatlas.map.layers.protected.tip")));
      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.slime"), this.seed.slimeShown(), this.seed.available() ? () -> this.seed.slimeShown(!this.seed.slimeShown()) : null));
      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.grid"), this.showGrid || this.editing, this.editing ? null : () -> Settings.grid(this.showGrid = !this.showGrid)));
      right.add(Ui.Item.heading(Ui.t("chunkatlas.map.layers.look")));
      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.night"), Settings.night(), Settings.detailed() ? () -> Settings.night(!Settings.night()) : null));
      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.ocean_floor"), (styleNow & TileRenderer.DRY) != 0, server ? null : () -> this.toggleStyle(TileRenderer.DRY)));
      right.add(Ui.Item.check(Ui.t("chunkatlas.map.layers.contours"), (styleNow & TileRenderer.CONTOURS) != 0, server ? null : () -> this.toggleStyle(TileRenderer.CONTOURS)));
      right.add(Ui.Item.separator());
      if (this.live != null) {
         right.add(Ui.Item.check(Ui.t("chunkatlas.map.follow"), this.follow, () -> this.follow = !this.follow));
      }

      right.add(new Ui.Item(Ui.t("chunkatlas.map.hide_controls"), MapKey.HIDE_CONTROLS.keyName(), () -> this.hideControls = true));
      return List.of(left, right);
   }

   static final int CUSTOM_HAS = 0;
   static final int CUSTOM_VALUE = 1;
   static final int CUSTOM_COUNT = 2;

   private String customTag = "";
   private int customMode = CUSTOM_HAS;

   private final Map<String, Map<Long, ChunkTags.Facts>> customCache = new java.util.LinkedHashMap<>(8, 0.75F, true);
   private final Set<String> customLoading = new HashSet<>();

   private ChunkSelection customHas = new ChunkSelection();
   private final Set<Long> customHasRegions = new HashSet<>();

   private List<Suggest.Option> customPaths;
   private String customPathsFor;
   private boolean customPathsReading;
   private ExecutorService customThreads;

   private void customSubmit(Runnable work) {
      if (this.customThreads == null) {
         this.customThreads = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Chunk Atlas tags");
            t.setDaemon(true);
            return t;
         });
      }

      try {
         this.customThreads.submit(work);
      } catch (RejectedExecutionException ignored) {
      }
   }

   ChunkTags.Facts customFacts(String tag, WorldLayout.RegionInfo r) {
      Map<Long, ChunkTags.Facts> forTag = this.customCache.get(tag);
      long key = WorldLayout.key(r.rx(), r.rz());
      if (forTag != null) {
         ChunkTags.Facts f = forTag.get(key);
         if (f != null) {
            return f;
         }
      }

      String loading = tag + "|" + key;
      if (this.customLoading.size() < 6 && !this.customLoading.contains(loading) && r.file() != null) {
         this.customLoading.add(loading);
         List<String> path = ChunkTags.split(tag);
         Path file = r.file();
         this.customSubmit(() -> {
            ChunkTags.Facts f;
            try {
               f = ChunkTags.scan(file, path);
            } catch (java.io.IOException | RuntimeException e) {
               f = new ChunkTags.Facts();
            }

            ChunkTags.Facts out = f;
            this.mc.execute(() -> {
               this.customLoading.remove(loading);
               Map<Long, ChunkTags.Facts> m = this.customCache.computeIfAbsent(tag, t -> new HashMap<>());
               m.put(key, out);
               while (this.customCache.size() > 4) {
                  this.customCache.remove(this.customCache.keySet().iterator().next());
               }
            });
         });
      }

      return null;
   }

   ChunkTags.Facts customFactsCached(String tag, WorldLayout.RegionInfo r) {
      Map<Long, ChunkTags.Facts> forTag = this.customCache.get(tag);
      return forTag == null ? null : forTag.get(WorldLayout.key(r.rx(), r.rz()));
   }

   boolean customReading(String tag) {
      for (String s : this.customLoading) {
         if (s.startsWith(tag + "|")) {
            return true;
         }
      }

      return false;
   }

   List<WorldLayout.RegionInfo> visibleRegions() {
      return this.index == null ? List.of() : this.visible(0);
   }

   List<Suggest.Option> customPaths() {
      if (this.customPaths != null && this.dimension != null && this.dimension.equals(this.customPathsFor)) {
         return this.customPaths;
      }

      if (!this.customPathsReading && this.index != null) {
         this.customPathsReading = true;
         String dim = this.dimension;
         List<WorldLayout.RegionInfo> seen = this.visible(0);
         if (seen.isEmpty()) {
            seen = new ArrayList<>(this.index.regions().values());
         }

         List<WorldLayout.RegionInfo> sample = new ArrayList<>(seen.subList(0, Math.min(3, seen.size())));
         this.customSubmit(() -> {
            Map<String, String> paths = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (WorldLayout.RegionInfo r : sample) {
               if (r.file() == null) {
                  continue;
               }

               try {
                  paths.putAll(ChunkTags.samplePaths(r.file(), 4));
               } catch (java.io.IOException | RuntimeException ignored) {
               }
            }

            List<Suggest.Option> out = new ArrayList<>();
            for (Map.Entry<String, String> e : paths.entrySet()) {
               out.add(new Suggest.Option(e.getKey(), e.getValue()));
            }

            List<Suggest.Option> done = List.copyOf(out);
            this.mc.execute(() -> {
               this.customPathsReading = false;
               this.customPaths = done;
               this.customPathsFor = dim;
            });
         });
      }

      return null;
   }

   String customTag() {
      return this.customTag;
   }

   int customMode() {
      return this.customMode;
   }

   void showCustom(String tag, int mode, double lo, double hi) {
      this.customTag = tag;
      this.customMode = mode;
      this.ranges.put(MapScreen.View.CUSTOM, new double[]{lo, hi});
      this.customHas = new ChunkSelection();
      this.customHasRegions.clear();
      this.view = MapScreen.View.CUSTOM;
      this.dropOverlays();
   }

   private void openCustomNbt() {
      if (this.index == null || this.job != null) {
         return;
      }

      this.menu = MapScreen.Menu.NONE;
      this.keep = true;
      double[] r = this.range(MapScreen.View.CUSTOM);
      Game.open(this.mc, new CustomNbtScreen(this, this.customTag, this.customMode, r[0], r[1]));
   }

   void openLayers() {
      this.open(MapScreen.Menu.VIEW, "layers");
   }

   private int customHeat(ChunkTags.Facts f, int i) {
      boolean has = f.has[i] != 0;
      switch (this.customMode) {
         case CUSTOM_HAS:
            return has ? 0 : 0x90101418;
         case CUSTOM_VALUE: {
            if (!has || Double.isNaN(f.value[i])) {
               return has ? 0x90101418 : 0;
            }

            return 0xC0000000 | ramp(Math.max(0.0, Math.min(1.0, place(f.value[i], this.range(MapScreen.View.CUSTOM), false))));
         }
         default: {
            if (!has || f.count[i] < 0) {
               return has ? 0x90101418 : 0;
            }

            return 0xC0000000 | ramp(Math.max(0.0, Math.min(1.0, place(f.count[i], this.range(MapScreen.View.CUSTOM), true))));
         }
      }
   }

   private String viewLabel(MapScreen.View v) {
      if (v == MapScreen.View.CUSTOM) {
         List<String> path = ChunkTags.split(this.customTag);
         return path.isEmpty() ? Ui.t("chunkatlas.map.view.custom") : (path.size() > 2 ? "…." + String.join(".", path.subList(path.size() - 2, path.size())) : String.join(".", path));
      }

      return Ui.t("chunkatlas.map.view." + v.name().toLowerCase(java.util.Locale.ROOT));
   }

   private void setView(MapScreen.View v) {
      if (v != this.view) {
         this.view = v;
         this.dropOverlays();
      }
   }

   private void dropOverlays() {
      for (MapScreen.Painted p : this.overlays.values()) {
         p.id().close();
      }

      this.overlays.clear();
   }

   private void drawOverlay(Gfx g, List<WorldLayout.RegionInfo> seen, int baseRx, int baseRz) {
      int built = 0;
      long now = System.currentTimeMillis() / 1000L;

      boolean custom = this.view == MapScreen.View.CUSTOM;
      for (WorldLayout.RegionInfo r : seen) {
         long key = WorldLayout.key(r.rx(), r.rz());
         Object f;
         if (custom) {
            ChunkTags.Facts c = this.customTag.isEmpty() ? null : this.customFacts(this.customTag, r);
            if (c != null && this.customMode == CUSTOM_HAS && this.customHasRegions.add(key)) {
               this.customHas.add(r.rx(), r.rz(), c.mask());
            }

            f = c;
         } else {
            f = this.facts.get(key);
         }

         if (f == null) {
            continue;
         }

         MapScreen.Painted p = this.overlays.get(key);
         if (p == null || p.facts() != f || p.view() != this.view) {
            if (built >= 24) {
               continue;
            }

            built++;
            Tex image = Tex.create("Chunk Atlas overlay", 32, 32);

            for (int i = 0; i < 1024; i++) {
               image.set(i & 31, i >> 5, custom ? this.customHeat((ChunkTags.Facts)f, i) : this.heat(this.view, (RegionFacts)f, i, now));
            }

            image.upload();
            Tex id = image;
            if (p != null) {
               p.id().close();
            }

            p = new MapScreen.Painted(id, f, this.view);
            this.overlays.put(key, p);
         }

         int x = (r.rx() - baseRx) * REGION;
         int z = (r.rz() - baseRz) * REGION;
         g.blit(p.id(), x, z, 0.0F, 0.0F, REGION, REGION, 32, 32, 32, 32);
      }
   }

   private final Map<MapScreen.View, double[]> ranges = new HashMap<>();

   private double[] range(MapScreen.View view) {
      return this.ranges.computeIfAbsent(view, v -> switch (v) {
         case TIME_PLAYED -> new double[]{0.0, 600.0};
         case LAST_CHANGED -> new double[]{365.0, 0.0};
         case CHESTS -> new double[]{1.0, 64.0};
         case MOBS -> new double[]{1.0, 100.0};
         case SIZE -> new double[]{4.0, 1024.0};
         case DATA_VERSION -> this.dataVersionRange();
         default -> new double[]{0.0, 1.0};
      });
   }

   private double[] dataVersionRange() {
      int low = Integer.MAX_VALUE;
      int high = 0;
      for (RegionFacts f : this.facts.values()) {
         for (int i = 0; i < 1024; i++) {
            if ((f.state[i] == RegionFacts.FULL || f.state[i] == RegionFacts.PARTIAL) && f.dataVersion[i] > 0) {
               low = Math.min(low, f.dataVersion[i]);
               high = Math.max(high, f.dataVersion[i]);
            }
         }
      }

      return low > high ? new double[]{0.0, 1.0} : new double[]{low, Math.max(high, low + 1)};
   }

   private static double place(double value, double[] range, boolean log) {
      double lo = range[0];
      double hi = range[1];
      if (hi == lo) {
         return value >= hi ? 1.0 : 0.0;
      }

      double v = log ? Math.log1p(Math.max(0.0, value)) : value;
      double a = log ? Math.log1p(Math.max(0.0, lo)) : lo;
      double b = log ? Math.log1p(Math.max(0.0, hi)) : hi;
      return (v - a) / (b - a);
   }

   private int heat(MapScreen.View view, RegionFacts f, int i, long now) {
      byte state = f.state[i];
      if (state != RegionFacts.FULL && state != RegionFacts.PARTIAL) {
         return 0;
      }

      double t;
      switch (view) {
         case TIME_PLAYED: {
            double minutes = f.inhabited[i] / 1200.0;
            if (minutes <= 0.0) {
               return 0x90101418;
            }

            t = place(minutes, this.range(view), true);
            break;
         }
         case LAST_CHANGED: {
            long saved = f.savedAt[i] & 0xFFFFFFFFL;
            if (saved == 0L) {
               return 0;
            }

            double days = Math.max(0.0, (now - saved) / 86400.0);
            t = place(days, this.range(view), true);
            break;
         }
         case CHESTS: {
            int n = f.blockEntities[i];
            if (n <= 0) {
               return 0;
            }

            t = place(n, this.range(view), true);
            break;
         }
         case MOBS: {
            int n = f.entities[i];
            if (n <= 0) {
               return 0;
            }

            t = place(n, this.range(view), true);
            break;
         }
         case DATA_VERSION: {
            if (f.dataVersion[i] <= 0) {
               return 0;
            }

            t = place(f.dataVersion[i], this.range(view), false);
            break;
         }
         case STATUS: {
            String status = f.chunk(i).status();
            int at = STATUS_ORDER.indexOf(status.substring(status.indexOf(':') + 1));
            if (at < 0) {
               return state == RegionFacts.FULL ? 0xC0000000 | ramp(1.0) : 0;
            }

            t = at / (double)(STATUS_ORDER.size() - 1);
            break;
         }
         case SIZE: {
            int kb = f.kb[i];
            if (kb <= 0) {
               return 0;
            }

            t = place(kb, this.range(view), true);
            break;
         }
         default:
            return 0;
      }

      return 0xC0000000 | ramp(Math.max(0.0, Math.min(1.0, t)));
   }

   private String rangeText(MapScreen.View view, double v) {
      return switch (view) {
         case TIME_PLAYED -> v >= 60.0 ? Ui.plural("chunkatlas.shrink.unit.hours", Math.round(v / 60.0)) : Ui.plural("chunkatlas.shrink.unit.minutes", Math.round(v));
         case LAST_CHANGED -> v <= 0.0 ? Ui.t("chunkatlas.map.legend.last_changed.high") : Ui.t("chunkatlas.ago.days", Math.round(v));
         case SIZE -> v >= 1024.0 ? String.format(java.util.Locale.ROOT, "%.1f MB", v / 1024.0) : Math.round(v) + " KB";
         case DATA_VERSION -> Ui.version((int)Math.round(v));
         default -> Ui.count(Math.round(v));
      };
   }

   private void askRange(boolean high) {
      MapScreen.View view = this.view;
      double[] r = this.range(view);
      String now = String.valueOf(Math.round(r[high ? 1 : 0]));
      this.keep = true;
      Game.open(this.mc, new AskScreen(this, Component.translatable("chunkatlas.map.layers.range.ask"), now, this.viewLabel(view), s -> {
         try {
            Double.parseDouble(s.strip());
            return true;
         } catch (NumberFormatException e) {
            return false;
         }
      }, s -> {
         r[high ? 1 : 0] = Double.parseDouble(s.strip());
         this.dropOverlays();
      }));
   }

   private static int ramp(double t) {
      int[] a = t < 0.5 ? new int[]{0x3B, 0x82, 0xF6} : new int[]{0xFA, 0xCC, 0x15};
      int[] b = t < 0.5 ? new int[]{0xFA, 0xCC, 0x15} : new int[]{0xEF, 0x44, 0x44};
      double k = t < 0.5 ? t * 2.0 : (t - 0.5) * 2.0;
      int r = (int)Math.round(a[0] + (b[0] - a[0]) * k);
      int gr = (int)Math.round(a[1] + (b[1] - a[1]) * k);
      int bl = (int)Math.round(a[2] + (b[2] - a[2]) * k);
      return r << 16 | gr << 8 | bl;
   }

   private void drawLegend(Gfx g) {
      String key = "chunkatlas.map.legend." + this.view.name().toLowerCase(java.util.Locale.ROOT);
      boolean has = this.view == MapScreen.View.CUSTOM && this.customMode == CUSTOM_HAS;
      boolean editable = this.view != MapScreen.View.STATUS && !has;
      double[] r = editable ? this.range(this.view) : null;
      String low = editable ? this.rangeText(this.view, r[0]) : Ui.t(key + ".low");
      String high = editable ? this.rangeText(this.view, r[1]) : Ui.t(key + ".high");
      String name = this.viewLabel(this.view);
      int bar = 60;
      int lw = this.font.width(low) + 8;
      int hw = this.font.width(high) + 8;
      int w = this.font.width(name) + 10 + lw + bar + hw + 16;
      int x = Glass.MARGIN;
      int h = Glass.CHIP_H + 4;
      int y = this.editing ? this.dockTop - 4 - Glass.CHIP_H - 4 - h : this.height - Glass.MARGIN - h;

      if (!this.editing && x + w > (this.width - 60) / 2) {
         y -= Glass.CHIP_H + 6;
      }
      g.fill(x, y, x + w, y + h, Glass.CHIP);
      g.text(this.font, name, x + 5, y + 4, Ui.GREY);
      int lx = x + 10 + this.font.width(name);

      this.rangeBox(g, lx, y + 2, lw, h - 4, low, editable ? () -> this.askRange(false) : null);
      int bx = lx + lw + 4;
      for (int n = 0; n < bar; n++) {
         int colour = has ? (n < bar / 2 ? 0xFF2A2E36 : 0xFF000000 | SELECTED) : 0xFF000000 | ramp(n / (double)(bar - 1));
         g.fill(bx + n, y + 4, bx + n + 1, y + h - 4, colour);
      }

      this.rangeBox(g, bx + bar + 4, y + 2, hw, h - 4, high, editable ? () -> this.askRange(true) : null);
   }

   private void rangeBox(Gfx g, int x, int y, int w, int h, String text, Runnable action) {
      if (action != null) {
         g.fill(x, y, x + w, y + h, 0xFF000000);
         g.outline(x, y, w, h, 0xFF6A6A6A);
         this.hits.add(new Ui.Hit(x, y, x + w, y + h, action));
      }

      g.text(this.font, text, x + 4, y + 2, action != null ? Ui.WHITE : Ui.GREY);
   }

   private List<Ui.Item> moreItems() {
      List<Ui.Item> items = new ArrayList<>();
      ChunkSelection sel = this.selections.get(this.dimension);
      boolean any = sel != null && !sel.isEmpty();
      if (this.pick != null) {
         items.add(new Ui.Item(Ui.t("chunkatlas.map.clear"), null, any ? sel::clear : null));
         return items;
      }

      boolean local = !this.onServer();
      boolean transfers = local || dev.chunkatlas.ServerLink.canEdit();
      items.add(new Ui.Item(Ui.t("chunkatlas.paste.copy"), MapKey.COPY.keyName(), transfers && any && this.job == null ? this::copySelection : null));
      items.add(new Ui.Item(Ui.t("chunkatlas.paste.menu"), MapKey.PASTE.keyName(), transfers && this.job == null && this.hasClipboard() ? this::startPaste : null));
      items.add(new Ui.Item(Ui.t("chunkatlas.copy.menu"), null, local && this.job == null ? this::copyFrom : null));
      items.add(Ui.Item.separator());
      items.add(new Ui.Item(Ui.t("chunkatlas.map.keep_only"), null, any ? () -> this.plan(ChunkEdits.Kind.KEEP_ONLY) : null));
      items.add(new Ui.Item(Ui.t("chunkatlas.map.more.remove_mobs"), null, any ? this::planRemoveMobs : null));
      items.add(new Ui.Item(Ui.t("chunkatlas.fields.menu"), null, (local || ForceLoad.onServer()) && any && this.job == null ? this::openFields : null));
      items.add(Ui.Item.separator());
      items.add(new Ui.Item(Ui.t("chunkatlas.map.more.save"), null, any ? this::openSelections : null));
      items.add(new Ui.Item(Ui.t("chunkatlas.export.menu"), null, local && any && this.job == null ? this::openExport : null));

      int unguarded = any ? this.caches.unprotected(sel, this.guarded()) : 0;
      if (unguarded > 0 && unguarded < sel.size()) {
         items.add(new Ui.Item(Ui.t("chunkatlas.protect.remove"), null, () -> this.protect(false)));
      }

      items.add(new Ui.Item(Ui.t("chunkatlas.picture.save"), null, this.index != null && this.job == null ? this::savePicture : null));
      items.add(Ui.Item.separator());
      items.add(new Ui.Item(Ui.t("chunkatlas.map.pick.invert"), "Ctrl I", any ? this::invert : null));
      items.add(new Ui.Item(Ui.t("chunkatlas.map.clear"), "Esc", any ? sel::clear : null));
      return items;
   }

   private boolean hasClipboard() {
      if (this.clipboardReady == null) {
         this.clipboardReady = ChunkClips.clipboardInfo(this.home) != null;
      }

      return this.clipboardReady;
   }

   private void copySelection() {
      ChunkSelection sel = this.selections.get(this.dimension);
      if (sel != null && !sel.isEmpty() && this.job == null) {
         this.clipboardReady = null;
         if (this.onServer()) {
            this.copyFromServer(sel.copy());
         } else {
            this.exportChunks("Clipboard", Out.CLIPBOARD);
         }
      }
   }

   private void copyFromServer(ChunkSelection sel) {
      MapScreen.Job j = new MapScreen.Job(Ui.t("chunkatlas.server.copying"), 100, false);
      String dim = this.dimension;
      boolean sent = dev.chunkatlas.ServerLink.copy(dim, sel, data -> {
         this.submit(() -> {
            String said;
            boolean ok = false;
            try {
               dev.chunkatlas.engine.ClipArchive.Archive a = dev.chunkatlas.engine.ClipArchive.read(data, Integer.MAX_VALUE);
               java.nio.file.Path folder = ChunkClips.freshClipboard(this.home);
               dev.chunkatlas.engine.ClipArchive.toClip(a, folder, "Clipboard");
               said = Ui.plural("chunkatlas.paste.copied", a.entries().size(), MapKey.PASTE.keyName());
               ok = true;
            } catch (java.io.IOException | RuntimeException e) {
               said = Ui.t("chunkatlas.server.unreadable");
            }

            String s = said;
            boolean good = ok;
            this.mc.execute(() -> {
               if (this.job == j) {
                  this.job = null;
               }

               this.clipboardReady = null;
               this.say(s, good ? Ui.GREEN : Ui.YELLOW);
            });
         });
      }, reply -> {
         if (this.job == j) {
            this.job = null;
         }

         this.say(Ui.t(reply.key(), Ui.count(reply.chunks()), reply.waiting()), Ui.YELLOW);
      });
      if (sent) {
         this.job = j;
         j.progress = () -> (int)Math.round(dev.chunkatlas.ServerLink.copyProgress() * 100.0);
      } else {
         this.say(Ui.t("chunkatlas.server.busy"), Ui.YELLOW);
      }
   }

   private void pasteToServer(ChunkClips.Clip clip, int dx, int dz) {
      String dim = this.dimension;
      boolean keep = this.pasteKeep;
      int blend = Settings.pasteBlend();
      boolean band = this.pasteBand();

      ChunkSelection shown = new ChunkSelection();
      if (this.pasteChunks != null) {
         this.pasteChunks.forEach((x, z) -> shown.add(x + dx, z + dz));
      }

      ChunkSelection guard = this.guarded().copy();
      WorldMap.Index index = this.index;
      this.cancelPaste();
      MapScreen.Job j = new MapScreen.Job(Ui.t("chunkatlas.server.sending"), 100, false);
      this.job = j;
      this.submit(() -> {
         byte[] archive;
         try {
            dev.chunkatlas.engine.PasteLimit limit = dev.chunkatlas.engine.PasteLimit.of(shown, blend, keep, guard, index == null ? (x, z) -> false : index::has, band);
            archive = dev.chunkatlas.engine.PasteLimit.wrap(limit,
               dev.chunkatlas.engine.ClipArchive.fromClip(clip.folder(), clip.sourceWorld(), clip.dimension(), clip.dataVersion(), 1 << 16));
         } catch (java.io.IOException | RuntimeException e) {
            archive = null;
         }

         byte[] a = archive;
         this.mc.execute(() -> {
            boolean sent = a != null && dev.chunkatlas.ServerLink.paste(a, dim, dx, dz, keep, blend, reply -> {
               if (this.job == j) {
                  this.job = null;
               }

               if (reply.ok()) {
                  this.selections.remove(dim);
               }

               this.say(Ui.t(reply.key(), Ui.count(reply.chunks()), reply.ok() && reply.key().endsWith("queued") ? reply.waiting() : reply.later()),
                  reply.ok() ? Ui.GREEN : Ui.YELLOW);
            });
            if (sent) {
               j.progress = () -> (int)Math.round(dev.chunkatlas.ServerLink.pasteProgress() * 100.0);
            } else {
               if (this.job == j) {
                  this.job = null;
               }

               this.say(Ui.t(a == null ? "chunkatlas.paste.failed" : "chunkatlas.server.busy"), Ui.YELLOW);
            }
         });
      });
   }

   private void startPaste() {
      if (this.job != null || this.index == null || this.map == null) {
         return;
      }

      if (!this.editing) {
         this.setEditing(true);
         if (!this.editing) {
            return;
         }
      }

      ChunkClips.Clip clip = ChunkClips.clipboardInfo(this.home);
      ChunkSelection chunks = clip == null ? null : ChunkClips.chunks(clip.folder());
      if (chunks == null || chunks.isEmpty()) {
         this.clipboardReady = false;
         this.say(Ui.t("chunkatlas.paste.empty"), Ui.YELLOW);
         return;
      }

      this.startPaste(clip, chunks, null);
   }

   void startPaste(ChunkClips.Clip clip, ChunkSelection chunks, WorldLayout.Dimension sourceDimension) {
      if (clip == null || chunks == null || chunks.isEmpty()) {
         return;
      }

      if (this.index == null || this.map == null) {
         this.pendingPaste = new Object[]{clip, chunks, sourceDimension};
         return;
      }

      if (this.job != null) {
         return;
      }

      if (!this.editing) {
         this.setEditing(true);
         if (!this.editing) {
            return;
         }
      }

      this.pasteSourceDim = sourceDimension;
      int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
      for (long region : chunks.regions()) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         long[] bits = chunks.bits(rx, rz);
         for (int i = 0; i < 1024; i++) {
            if ((bits[i >> 6] & 1L << (i & 63)) != 0L) {
               int cx = rx * 32 + (i & 31);
               int cz = rz * 32 + (i >> 5);
               minX = Math.min(minX, cx);
               maxX = Math.max(maxX, cx);
               minZ = Math.min(minZ, cz);
               maxZ = Math.max(maxZ, cz);
            }
         }
      }

      this.pasteClip = clip;
      this.pasteChunks = chunks;
      this.pasteDy = 0;
      this.pasteView.start(sourceDimension != null ? sourceDimension : ChunkClips.dimension(clip.folder()), chunks, this.colours, this.map.style());
      this.pasteAnchorX = (minX + maxX) >> 1;
      this.pasteAnchorZ = (minZ + maxZ) >> 1;
      this.pasteShown = null;
      this.pasteShownDx = Integer.MIN_VALUE;
      this.menu = MapScreen.Menu.NONE;

      this.bannerUntil = 0L;
   }

   private static ChunkSelection onlyEmpty(ChunkSelection chunks, int dx, int dz, WorldMap.Index i) {
      ChunkSelection out = new ChunkSelection();
      for (long region : chunks.regions()) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         long[] bits = chunks.bits(rx, rz);
         for (int n = 0; n < 1024; n++) {
            if ((bits[n >> 6] & 1L << (n & 63)) != 0L) {
               int x = rx * 32 + (n & 31);
               int z = rz * 32 + (n >> 5);
               if (!i.has(x + dx, z + dz)) {
                  out.add(x, z);
               }
            }
         }
      }

      return out;
   }

   private void cancelPaste() {
      this.pasteView.stop();
      this.pasteBar.close();
      this.pasteHeld = null;
      this.pasteChunks = null;
      this.pasteClip = null;
      this.pasteSourceDim = null;
      this.pasteShown = null;
   }

   private void drawPaste(Gfx g, int mouseX, int mouseY, int baseRx, int baseRz) {
      int[] held = this.pasteHeld != null ? this.pasteHeld : this.pasteBar.held();
      this.pasteDx = held != null ? held[0] : ((int)Math.floor(this.blockX(mouseX)) >> 4) - this.pasteAnchorX;
      this.pasteDz = held != null ? held[1] : ((int)Math.floor(this.blockZ(mouseY)) >> 4) - this.pasteAnchorZ;
      if (this.pasteShown == null || this.pasteDx != this.pasteShownDx || this.pasteDz != this.pasteShownDz) {
         ChunkSelection moved = new ChunkSelection();
         for (long region : this.pasteChunks.regions()) {
            int rx = (int)(region >> 32);
            int rz = (int)region;
            long[] bits = this.pasteChunks.bits(rx, rz);
            for (int i = 0; i < 1024; i++) {
               if ((bits[i >> 6] & 1L << (i & 63)) != 0L) {
                  moved.add(rx * 32 + (i & 31) + this.pasteDx, rz * 32 + (i >> 5) + this.pasteDz);
               }
            }
         }

         this.pasteShown = moved;
         this.pasteShownDx = this.pasteDx;
         this.pasteShownDz = this.pasteDz;
      }

      ChunkSelection guard = this.guarded();

      this.caches.ghost(this.pasteShown, guard);
      List<WorldLayout.RegionInfo> where = this.caches.ghostRegions;

      this.pasteView.frame(new PasteView.Here(this.pasteDx, this.pasteDz, Settings.pasteBlend(), this.pasteKeep, this.smartLift(),
         this.pasteShown, guard, this.index, this.seedGround(), this.pasteBand()));
      if (this.pasteView.pictureReady()) {
         this.pasteView.drawPicture(g, baseRx, baseRz, this.pasteDx, this.pasteDz);
      } else {
         this.drawSelection(g, this.pasteShown, where, baseRx, baseRz, PASTE);
      }

      if (!guard.isEmpty()) {
         ChunkSelection skipped = this.caches.ghostSkipped;
         if (!skipped.isEmpty()) {
            this.drawSelection(g, skipped, where, baseRx, baseRz, SKIPPED);
         }

         this.pasteSkipped = skipped.size();
      } else {
         this.pasteSkipped = 0;
      }
   }

   private int pasteSkipped;

   private void drawPasteLabel(Gfx g) {
      if (this.pasteShown == null || this.pasteShown.isEmpty()) {
         return;
      }

      this.caches.ghost(this.pasteShown, this.guarded());
      int minX = this.caches.ghostMinX;
      int minZ = this.caches.ghostMinZ;
      int x = (int)Math.floor(this.screenX(minX * 16.0));
      int y = (int)Math.floor(this.screenY(minZ * 16.0));
      int blend = Settings.pasteBlend();
      int[] ring = this.pasteView.drawScreen(g, this.pasteShown, this.pasteDx, this.pasteDz, blend);
      if (ring != null) {
         this.dragLabel(g, ring[0], ring[1], Ui.t("chunkatlas.paste.ring", blend), Glass.ACCENT & 0xFFFFFF);
      }

      String text = Ui.plural("chunkatlas.paste.label", this.pasteChunks.size(), this.pasteDx, this.pasteDz);

      int[] hint = this.pasteHint();
      int w = this.font.width(text) + 8;
      int lx = Math.max(this.mapLeft, Math.min(x, this.mapRight - w));
      if (lx < hint[0] + hint[2] && lx + w > hint[0] && y - Glass.CHIP_H - 2 < hint[1] + hint[3] + 2) {
         y = Math.max(y, hint[1] + hint[3] + Glass.CHIP_H + 4);
      }

      this.dragLabel(g, x, y, text, PASTE);
      if (this.pasteSkipped > 0) {
         String skip = Ui.plural("chunkatlas.paste.skipped", this.pasteSkipped);
         this.dragLabel(g, x, y + Glass.CHIP_H + 2, skip, SKIPPED);
      }
   }

   private static final int GO = 0xFF7EE07E;

   private String pasteHowText() {
      String how = Ui.t("chunkatlas.paste.how");
      return Ui.chipWidth(this.font, how) > this.width / 2 ? Ui.t("chunkatlas.paste.how.short") : how;
   }

   private int[] pasteHint() {
      String how = this.pasteHowText();
      int w = Ui.chipWidth(this.font, how);
      int hy = this.editing ? Glass.MARGIN + Glass.tabsHeight() + 6 : Glass.MARGIN + 3;
      return new int[]{(this.width - w) / 2, hy, w, Glass.CHIP_H};
   }

   private void askPasteOffset(boolean xAxis) {
      this.keep = true;
      Game.open(this.mc, new AskScreen(this, Component.translatable(xAxis ? "chunkatlas.copy.place.x" : "chunkatlas.copy.place.z"),
         Integer.toString(xAxis ? this.pasteDx : this.pasteDz), Ui.t("chunkatlas.copy.place.chunks"), s -> {
            try {
               Integer.parseInt(s.strip());
               return true;
            } catch (NumberFormatException e) {
               return false;
            }
         }, s -> {
            int v = Integer.parseInt(s.strip());
            if (xAxis) {
               this.pasteDx = v;
            } else {
               this.pasteDz = v;
            }

            this.placePaste(this.pasteDx, this.pasteDz);
         }));
   }

   private boolean pasteBand() {
      return !this.onServer() || !dev.chunkatlas.ServerLink.live();
   }

   private boolean smartLift() {
      return Settings.pasteSmart() && Settings.pasteBlend() > 0 && !this.onServer();
   }

   private int pasteLift() {
      if (!this.smartLift()) {
         return this.pasteDy;
      }

      dev.chunkatlas.engine.PasteTerrain.Lift l = this.pasteView.lift(this.pasteDx, this.pasteDz, Settings.pasteBlend());
      l = l != null ? l : this.pasteView.lastLift();
      return l == null ? 0 : l.dy();
   }

   private dev.chunkatlas.engine.PasteTerrain.SeedGround seedGround() {
      dev.chunkatlas.seed.SeedSampler w = this.seed.lookupWorld();
      return w != null && w.dimension().equals(this.dimension) && w.hasTerrain() ? w::groundAt : null;
   }

   private void askPasteLift() {
      this.keep = true;
      Game.open(this.mc, new AskScreen(this, Component.translatable("chunkatlas.copy.place.y"), Integer.toString(this.pasteLift()), Ui.t("chunkatlas.copy.place.blocks"), s -> {
         try {
            return Math.abs(Integer.parseInt(s.strip())) <= dev.chunkatlas.engine.ChunkLift.MAX_DY;
         } catch (NumberFormatException e) {
            return false;
         }
      }, s -> {
         this.pasteDy = Integer.parseInt(s.strip());
         Settings.pasteSmart(false);
      }));
   }

   private void placePaste(int dx, int dz) {
      if (this.onServer() && this.pasteClip != null && this.job == null) {
         this.pasteToServer(this.pasteClip, dx, dz);
         return;
      }

      ChunkClips.Clip clip = this.pasteClip;
      WorldMap.Index i = this.index;
      ChunkSelection chunks = this.pasteKeep && this.pasteChunks != null && i != null ? onlyEmpty(this.pasteChunks, dx, dz, i) : this.pasteChunks;
      if (chunks != null && chunks.isEmpty()) {
         this.say(Ui.t("chunkatlas.paste.all_taken"), Ui.YELLOW);
         return;
      }

      if (clip == null || chunks == null || i == null || this.job != null || this.map == null) {
         return;
      }

      int version = this.live != null ? Version.dataVersion() : this.map.markers().dataVersion();
      Path world = this.worldFolder;
      WorldLayout.Dimension into = i.dimension();
      String dim = into.id();
      String here = this.name == null ? "" : this.name;
      WorldLayout.Dimension from = this.pasteSourceDim != null ? this.pasteSourceDim : ChunkClips.dimension(clip.folder());
      boolean fromWorld = this.pasteSourceDim != null;
      int blend = Settings.pasteBlend();

      boolean smart = this.smartLift();
      dev.chunkatlas.engine.PasteTerrain.Lift known = smart ? this.pasteView.lift(dx, dz, blend) : null;
      boolean looked = smart && this.pasteView.liftDone(dx, dz, blend);
      int typed = this.pasteDy;
      ChunkSelection all = this.pasteChunks;
      dev.chunkatlas.engine.PasteTerrain.SeedGround ground = this.seedGround();
      this.cancelPaste();
      MapScreen.Job j = new MapScreen.Job(Ui.t("chunkatlas.map.job.planning"), 0, false);
      this.job = j;
      this.submit(() -> {
         int dy = typed;
         if (smart) {
            dev.chunkatlas.engine.PasteTerrain.Lift l = known;
            try {
               l = l != null || looked ? l : dev.chunkatlas.engine.PasteTerrain.lift(from, all, into, i::has, dx, dz, blend, ground, this.colours, () -> false);
            } catch (RuntimeException e) {
               l = null;
            }

            dy = l == null ? 0 : l.dy();
            this.lastSmart = l;
         }

         int lift = dy;

         Aside aside = Aside.of(this.home, clip.folder(), from, chunks, fromWorld);
         Supplier<ChunkEdits.Plan> replan = () -> {
            try {
               return ChunkCopy.plan(new ChunkCopy.Request(
                  aside.world(), clip.sourceWorld(), aside.dimension(), chunks, world, here, into, dx, dz,
                  Protection.load(this.home, world, dim), clip.dataVersion(), version
               ).blended(blend).lifted(lift));
            } catch (RuntimeException e) {
               return null;
            }
         };
         ChunkEdits.Plan p = replan.get();
         this.mc.execute(() -> {
            if (this.job == j) {
               this.job = null;
            }

            if (p == null) {
               this.say(Ui.t("chunkatlas.paste.failed"), Ui.YELLOW);
            } else if (p.copy() != null && p.copy().refused() != null) {
               this.say(Ui.t(p.copy().refused()), Ui.YELLOW);
            } else if (p.isEmpty()) {
               this.say(Ui.t("chunkatlas.paste.nothing"), Ui.YELLOW);
            } else {
               this.keep = true;
               Game.open(this.mc, ConfirmEditScreen.copy(this, this.home, world, p, replan, version).reading(aside));
            }
         });
      });
   }

   private void openNbt(int cx, int cz) {
      WorldMap.Index i = this.index;
      WorldLayout.RegionInfo info = i == null ? null : i.regions().get(WorldLayout.key(cx >> 5, cz >> 5));
      if (info == null || this.job != null) {
         return;
      }

      Map<String, Object> root;
      try (dev.chunkatlas.engine.RegionFile region = dev.chunkatlas.engine.RegionFile.open(info.file())) {
         dev.chunkatlas.engine.RegionFile.Chunk c = region.chunk((cz & 31) * 32 + (cx & 31));
         if (!c.ok()) {
            this.say(Ui.t("chunkatlas.nbt.unreadable"), Ui.YELLOW);
            return;
         }

         root = dev.chunkatlas.engine.Nbt.read(c.data());
      } catch (java.io.IOException | RuntimeException e) {
         this.say(Ui.t("chunkatlas.nbt.unreadable"), Ui.YELLOW);
         return;
      }

      String where = Ui.t("chunkatlas.context.chunk", cx, cz) + " · " + Ui.t("chunkatlas.map.hover.region", cx >> 5, cz >> 5);
      this.keep = true;
      this.outline(cx, cz);
      Game.open(this.mc, new NbtScreen(this, where, root, edited -> {
         ChunkSelection one = new ChunkSelection();
         one.add(cx, cz);
         this.rewrite(one, (role, r) -> {
            if (role.equals("region")) {
               r.clear();
               r.putAll(edited);
            }
         });
      }));
   }

   void tourNbtHere() {
      this.openNbt((int)Math.floor(this.cx) >> 4, (int)Math.floor(this.cz) >> 4);
   }

   private void openFields() {
      ChunkSelection sel = this.selections.get(this.dimension);
      if (sel != null && !sel.isEmpty() && this.job == null && this.index != null) {
         this.keep = true;
         Game.open(this.mc, new FieldsScreen(this, sel.size(), this.onServer(), this::changeFields));
      }
   }

   private void changeFields(dev.chunkatlas.engine.ChunkFields.Changes changes, Boolean force) {
      ChunkSelection sel = this.selections.get(this.dimension);
      if (sel != null && !sel.isEmpty() && this.index != null && this.map != null) {
         ConfirmEditScreen.Also also = force == null ? null : ForceLoad.change(this, this.index.dimension(), sel.copy(), force, this.map.markers().dataVersion());
         if (changes == null) {
            if (also != null) {
               this.keep = true;
               Game.open(this.mc, ConfirmEditScreen.also(this, this.home, this.worldFolder, also));
            }

            return;
         }

         int blend = changes.forceBlending() ? BlendOptions.blend() : 0;
         boolean smart = blend > 0 && BlendOptions.smart();
         Integer lift = smart ? this.blendLift(blend) : null;
         this.endBlendPreview();
         this.rewrite(sel.copy(), dev.chunkatlas.engine.ChunkFields.transform(changes), blend, smart, lift, also);
      }
   }

   Integer drawBlendPreview(Gfx g, int blend, boolean smart) {
      ChunkSelection sel = this.selections.get(this.dimension);
      WorldMap.Index i = this.index;
      if (blend <= 0 || sel == null || sel.isEmpty() || i == null) {
         return null;
      }

      ChunkSelection shown = this.blendView.shownChunks();
      if (shown == null || shown.size() != sel.size()) {
         shown = sel.copy();
         this.blendView.startInPlace(i.dimension(), shown, this.colours);
      }

      g.scissor(this.mapLeft, this.mapTop, this.mapRight, this.mapBottom);
      this.blendView.frame(new PasteView.Here(0, 0, blend, false, smart, shown, this.guarded(), i, this.seedGround(), this.pasteBand()));
      int[] ring = this.blendView.drawScreen(g, shown, 0, 0, blend);
      g.unscissor();
      if (ring != null) {
         this.dragLabel(g, ring[0], ring[1], Ui.t("chunkatlas.paste.ring", blend), Glass.ACCENT & 0xFFFFFF);
      }

      return smart ? this.blendLift(blend) : null;
   }

   private Integer blendLift(int blend) {
      dev.chunkatlas.engine.PasteTerrain.Lift l = this.blendView.lift(0, 0, blend);
      return l == null ? null : l.dy();
   }

   void endBlendPreview() {
      this.blendView.stop();
   }

   void rewrite(ChunkSelection chunks, ChunkCopy.Transform t) {
      this.rewrite(chunks, t, 0);
   }

   void rewrite(ChunkSelection chunks, ChunkCopy.Transform t, int blend) {
      this.rewrite(chunks, t, blend, false, null, null);
   }

   void rewrite(ChunkSelection chunks, ChunkCopy.Transform t, int blend, boolean smart, Integer lift, ConfirmEditScreen.Also also) {
      WorldMap.Index i = this.index;
      if (chunks.isEmpty() || i == null || this.map == null || this.job != null) {
         return;
      }

      WorldLayout.Dimension d = i.dimension();
      int version = this.live != null ? Version.dataVersion() : this.map.markers().dataVersion();
      String label = Ui.t("chunkatlas.fields.title");
      String here = this.name == null ? "" : this.name;
      Path worldFolder = this.worldFolder;
      MapScreen.Job j = new MapScreen.Job(Ui.t("chunkatlas.map.job.planning"), chunks.size(), false);
      this.job = j;
      boolean playing = this.live != null;
      dev.chunkatlas.engine.PasteTerrain.SeedGround ground = this.seedGround();
      this.submit(() -> {
         ChunkEdits.Plan plan = null;
         Supplier<ChunkEdits.Plan> replan = null;
         Message problem = null;
         Path own = null;
         int dy = lift == null ? 0 : lift;
         if (smart && lift == null) {
            try {
               dev.chunkatlas.engine.PasteTerrain.Lift l = dev.chunkatlas.engine.PasteTerrain.lift(d, chunks, d, i::has, 0, 0, blend, ground, this.colours, () -> false);
               dy = l == null ? 0 : l.dy();
            } catch (RuntimeException e) {
               dy = 0;
            }
         }

         int lifted = dy;
         try {
            Path world = worldFolder.toRealPath();

            Path aside = dev.chunkatlas.engine.EditSnapshots.fresh(this.home);
            own = aside;
            problem = this.copyAside(world, here, d, chunks, aside, label, version, j);
            if (problem == null) {
               Supplier<ChunkEdits.Plan> back = () -> {
                  try {
                     return ChunkCopy.plan(new ChunkCopy.Request(
                        aside, label, ChunkClips.dimension(aside), chunks, world, here, d, 0, 0, Protection.load(this.home, world, d.id()), version, version, t
                     ).blended(blend).lifted(lifted));
                  } catch (RuntimeException e) {
                     return null;
                  }
               };

               replan = !playing ? back : () -> this.copyAside(world, here, d, chunks, aside, label, version, null) == null ? back.get() : null;
               plan = back.get();
            }
         } catch (java.io.IOException | RuntimeException e) {
            problem = Message.of("chunkatlas.fields.failed");
         }

         ChunkEdits.Plan p = plan;
         Supplier<ChunkEdits.Plan> again = replan;
         Message failed = problem;
         Path folder = own;
         this.mc.execute(() -> {
            if (this.job == j) {
               this.job = null;
            }

            if (failed != null || p == null || p.copy() != null && p.copy().refused() != null) {
               dev.chunkatlas.engine.EditSnapshots.drop(this.home, folder);
            }

            if (failed != null) {
               this.say(Ui.t(failed), Ui.YELLOW);
            } else if (p == null || p.copy() != null && p.copy().refused() != null) {
               this.say(p == null ? Ui.t("chunkatlas.fields.failed") : Ui.t(p.copy().refused()), Ui.YELLOW);
            } else {
               this.keep = true;
               Game.open(this.mc, ConfirmEditScreen.copy(this, this.home, worldFolder, p, again, version).with(also).reading(Aside.own(this.home, folder)));
            }
         });
      });
   }

   private Message copyAside(Path world, String here, WorldLayout.Dimension d, ChunkSelection chunks, Path aside, String label, int version, MapScreen.Job j) {
      try {
         dev.chunkatlas.engine.EditSnapshots.empty(aside);
         ChunkEdits.Plan out = ChunkCopy.plan(new ChunkCopy.Request(world, here, d, chunks, aside, label, ChunkClips.dimension(aside), 0, 0, new ChunkSelection(), version, version));
         if (out.copy() != null && out.copy().refused() != null) {
            return out.copy().refused();
         } else if (out.isEmpty()) {
            return Message.of("chunkatlas.export.nothing");
         }

         ChunkEdits.Result r = ChunkEdits.apply(out, new ChunkEdits.Context(this.home, () -> true, () -> true), j == null ? null : (done, total) -> j.done = done, false);
         return r.ok() ? null : r.message();
      } catch (java.io.IOException | RuntimeException e) {
         return Message.of("chunkatlas.fields.failed");
      }
   }

   void tourOpenFields() {
      this.openFields();
   }

   void tourFields(dev.chunkatlas.engine.ChunkFields.Changes c) {
      this.changeFields(c, null);
   }

   LoadedLayer loadedLayer() {
      return this.loaded;
   }

   private void openExport() {
      ChunkSelection sel = this.selections.get(this.dimension);
      if (sel == null || sel.isEmpty() || this.job != null || this.index == null || this.map == null) {
         return;
      }

      this.keep = true;
      Game.open(this.mc, new ExportScreen(this, sel.copy(), this.name == null ? "Export" : this.name, (n, world) -> this.exportChunks(n, world ? Out.WORLD : Out.CLIP)));
   }

   private enum Out {
      CLIP,
      WORLD,
      CLIPBOARD
   }

   private void exportChunks(String label, Out out) {
      boolean newWorld = out == Out.WORLD;
      ChunkSelection sel = this.selections.get(this.dimension);
      WorldMap.Index i = this.index;
      if (sel == null || sel.isEmpty() || this.job != null || i == null || this.map == null) {
         return;
      }

      ChunkSelection chunks = sel.copy();
      WorldLayout.Dimension d = i.dimension();
      int version = this.live != null ? Version.dataVersion() : this.map.markers().dataVersion();
      String source = this.name == null ? "" : this.name;
      String dimension = this.dimension;
      MapScreen.Job j = new MapScreen.Job(Ui.t(out == Out.CLIPBOARD ? "chunkatlas.paste.copying" : "chunkatlas.export.making"), chunks.size(), false);
      this.job = j;
      int mine = this.run;
      this.submit(() -> {
         Message problem = null;
         int written = 0;
         Path dest = null;
         try {
            Path world = this.worldFolder.toRealPath();
            dest = switch (out) {
               case WORLD -> ChunkClips.newWorld(world.getParent(), world, label);
               case CLIP -> ChunkClips.create(this.home, label);
               case CLIPBOARD -> ChunkClips.freshClipboard(this.home);
            };
            WorldLayout.Dimension into = newWorld ? ChunkClips.sameDimensionIn(dest, world, d) : ChunkClips.dimension(dest);
            ChunkEdits.Plan plan = ChunkCopy.plan(
               new ChunkCopy.Request(world, source, d, chunks, dest, label, into, 0, 0, new ChunkSelection(), version, version)
            );
            if (plan.copy() != null && plan.copy().refused() != null) {
               problem = plan.copy().refused();
            } else if (plan.isEmpty()) {
               problem = Message.of("chunkatlas.export.nothing");
            } else {
               ChunkEdits.Result result = ChunkEdits.apply(plan, new ChunkEdits.Context(this.home, () -> true, () -> true), (done, total) -> {
                  j.done = done;
                  j.total = total;
               }, false);
               if (!result.ok()) {
                  problem = result.message();
               } else {
                  written = plan.chunks();
                  if (!newWorld) {
                     ChunkClips.describe(dest, label, source, dimension, written, version);
                  }
               }
            }
         } catch (java.io.IOException | RuntimeException e) {
            problem = Message.of("chunkatlas.export.failed");
         }

         Message failed = problem;
         int count = written;
         Path folder = dest;
         this.mc.execute(() -> {
            if (this.job == j && mine == this.run) {
               this.job = null;
            }

            if (failed != null) {
               this.say(Ui.t(failed), Ui.YELLOW);
            } else {
               if (out == Out.CLIPBOARD) {
                  this.say(Ui.plural("chunkatlas.paste.copied", count, MapKey.PASTE.keyName()), Ui.GREEN);
                  return;
               }

               this.say(Ui.plural(newWorld ? "chunkatlas.export.done.world" : "chunkatlas.export.done.clip", count, label), Ui.GREEN);
            }
         });
      });
   }

   private void copyFrom() {
      if (this.pick == null && this.job == null && this.map != null && this.index != null) {
         int version = this.live != null ? Version.dataVersion() : this.map.markers().dataVersion();
         this.keep = true;
         CopyChunks.start(new CopyChunks.Target(this, this.home, this.worldFolder, this.name, this.index.dimension(), version));
      }
   }

   private void copyNext() {
      ChunkSelection sel = this.selections.get(this.dimension);
      if (this.pick != null && this.job == null && this.map != null && this.index != null && sel != null && !sel.isEmpty()) {
         this.keep = true;
         this.pick.next(this, this.worldFolder, this.name, this.index.dimension(), sel.copy(), this.map.markers().dataVersion());
      }
   }

   private static final int PICTURE_MAX = 4096;

   private void savePicture() {
      WorldMap.Index i = this.index;
      WorldMap m = this.map;
      if (i == null || m == null || this.job != null || i.present().isEmpty()) {
         return;
      }

      int minRx = Integer.MAX_VALUE, minRz = Integer.MAX_VALUE, maxRx = Integer.MIN_VALUE, maxRz = Integer.MIN_VALUE;
      for (Long key : i.present().keySet()) {
         int rx = (int)(key >> 32);
         int rz = (int)key.longValue();
         minRx = Math.min(minRx, rx);
         maxRx = Math.max(maxRx, rx);
         minRz = Math.min(minRz, rz);
         maxRz = Math.max(maxRz, rz);
      }

      int across = maxRx - minRx + 1;
      int down = maxRz - minRz + 1;

      int per = 512;
      while (per > 1 && (long)Math.max(across, down) * per > PICTURE_MAX) {
         per /= 2;
      }

      int level = Math.min(4, Integer.numberOfTrailingZeros(512 / per));
      int width = across * per;
      int height = down * per;
      int x0 = minRx;
      int z0 = minRz;
      int px = per;
      List<Long> regions = new ArrayList<>(i.present().keySet());
      Path dir = this.mc.gameDirectory.toPath().resolve("screenshots").resolve("chunk-atlas");
      String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
      String dimName = this.dimension.substring(this.dimension.indexOf(':') + 1);
      Path file = dir.resolve(SelectionFiles.cleanName(this.name == null ? "world" : this.name) + "_" + dimName + "_" + stamp + ".png");
      int mine = this.run;
      MapScreen.Job j = new MapScreen.Job(Ui.t("chunkatlas.picture.making"), regions.size(), true);
      this.job = j;
      this.submit(() -> {
         boolean ok = false;
         try (Tex image = Tex.create("Chunk Atlas picture", width, height)) {
            for (int y = 0; y < height; y++) {
               for (int x = 0; x < width; x++) {
                  image.set(x, y, NOT_MADE);
               }
            }

            int done = 0;
            for (Long key : regions) {
               if (j.cancelled) {
                  throw new java.util.concurrent.CancellationException();
               }

               Tile t = m.tileNow(i, key, level);
               if (t != null) {
                  int ox = ((int)(key >> 32) - x0) * px;
                  int oz = ((int)key.longValue() - z0) * px;
                  int step = Math.max(1, t.size / px);
                  for (int y = 0; y < px; y++) {
                     for (int x = 0; x < px; x++) {
                        int c = t.argb[Math.min(t.size - 1, y * step) * t.size + Math.min(t.size - 1, x * step)];
                        if ((c >>> 24) != 0) {
                           image.set(ox + x, oz + y, c | 0xFF000000);
                        }
                     }
                  }
               }

               j.done = ++done;
            }

            java.nio.file.Files.createDirectories(dir);
            image.save(file);
            ok = true;
         } catch (java.io.IOException | RuntimeException | OutOfMemoryError e) {
            ok = false;
         }

         boolean saved = ok;
         this.mc.execute(() -> {
            if (this.job == j && mine == this.run) {
               this.job = null;
               if (saved) {
                  this.say(Ui.t("chunkatlas.picture.saved", file.getFileName().toString()), Ui.GREEN);
               } else if (!j.cancelled) {
                  this.say(Ui.t("chunkatlas.picture.failed"), Ui.YELLOW);
               }
            }
         });
      });
   }

   private void openShrink() {
      if (this.index == null || this.job != null || this.map == null) {
         return;
      }

      LevelMarkers.Point spawn = this.marker(this.map.markers().spawn());
      LevelMarkers.Point player = this.marker(this.map.markers().player());
      String dim = this.dimension;
      this.keep = true;
      Game.open(this.mc, new ShrinkScreen(
         this,
         this.map,
         this.index,
         this.guarded().copy(),
         spawn == null ? null : (int)Math.floor(spawn.x()),
         spawn == null ? null : (int)Math.floor(spawn.z()),
         player == null ? null : (int)Math.floor(player.x()),
         player == null ? null : (int)Math.floor(player.z()),
         (chosen, deleteNow) -> {
            if (!dim.equals(this.dimension)) {
               return;
            }

            this.selections.put(dim, chosen);
            this.say(Ui.plural("chunkatlas.shrink.selected", chosen.size()), Ui.LIGHT_BLUE);
            if (deleteNow) {
               this.plan(ChunkEdits.Kind.DELETE);
            }
         }
      ));
   }

   private ChunkSelection guarded() {
      String dim = this.dimension;
      if (dim == null) {
         return new ChunkSelection();
      }

      return this.guarded.computeIfAbsent(dim, d -> Protection.load(this.home, this.worldFolder, d));
   }

   private void protect(boolean on) {
      ChunkSelection sel = this.selections.get(this.dimension);
      if (sel == null || sel.isEmpty()) {
         return;
      }

      ChunkSelection before = this.guarded();
      ChunkSelection after = on ? Protection.with(before, sel) : Protection.without(before, sel);
      try {
         Protection.save(this.home, this.worldFolder, this.dimension, after);
      } catch (java.io.IOException | RuntimeException e) {
         this.say(Ui.t("chunkatlas.protect.not_saved"), Ui.YELLOW);
         return;
      }

      this.guarded.put(this.dimension, after);
      int changed = Math.abs(after.size() - before.size());
      sel.clear();
      this.say(Ui.plural(on ? "chunkatlas.protect.added" : "chunkatlas.protect.removed", changed), Ui.GOLD);
   }

   private void selectProtected() {
      ChunkSelection guard = this.guarded();
      if (!guard.isEmpty()) {
         this.selections.put(this.dimension, guard.copy());
      }
   }

   private static ChunkEdits.Plan planFor(ChunkEdits.Kind kind, Path world, WorldLayout.Dimension d, ChunkSelection copy, ChunkSelection guard) {
      return switch (kind) {
         case DELETE -> ChunkEdits.planDelete(world, d, Protection.without(copy, guard));
         case KEEP_ONLY -> ChunkEdits.planKeepOnly(world, d, Protection.with(copy, guard));
         case DELETE_MOBS -> ChunkEdits.planDeleteMobs(world, d, Protection.without(copy, guard));
         case COPY -> throw new IllegalArgumentException("copies are planned by CopyChunks");
      };
   }

   private static int protectedSkipped(ChunkEdits.Kind kind, ChunkSelection copy, ChunkSelection guard) {
      return kind == ChunkEdits.Kind.KEEP_ONLY ? 0 : copy.size() - Protection.without(copy, guard).size();
   }

   private static String emptyPlanKey(ChunkEdits.Kind kind, ChunkEdits.Plan plan) {
      return switch (kind) {
         case DELETE -> "chunkatlas.map.plan.nothing_to_delete";
         case KEEP_ONLY -> "chunkatlas.map.plan.everything_selected";
         case DELETE_MOBS -> plan.mobsInsideChunks() ? "chunkatlas.map.plan.mobs_inside_chunks" : "chunkatlas.map.plan.no_mobs";
         case COPY -> "chunkatlas.copy.nothing";
      };
   }

   private void planRemoveMobs() {
      this.plan(ChunkEdits.Kind.DELETE_MOBS);
   }

   private static ChunkFilter lastFilter = ChunkFilter.empty();

   private static int findMode;

   private static boolean findInSelection;

   final class FindHost {
      ChunkFilter.Context context() {
         LevelMarkers.Point spawn = MapScreen.this.marker(MapScreen.this.map.markers().spawn());
         LevelMarkers.Point player = MapScreen.this.marker(MapScreen.this.map.markers().player());
         return new ChunkFilter.Context(
            spawn == null ? null : spawn.x(), spawn == null ? null : spawn.z(), player == null ? null : player.x(), player == null ? null : player.z(),
            MapScreen.this.map.markers().dataVersion(), System.currentTimeMillis() / 1000L
         );
      }

      boolean hasSpawn() {
         return MapScreen.this.marker(MapScreen.this.map.markers().spawn()) != null;
      }

      boolean hasPlayer() {
         return MapScreen.this.marker(MapScreen.this.map.markers().player()) != null;
      }

      int worldVersion() {
         return MapScreen.this.map.markers().dataVersion();
      }

      WorldMap.Index index() {
         return MapScreen.this.index;
      }

      WorldMap map() {
         return MapScreen.this.map;
      }

      ChunkSelection selection() {
         ChunkSelection s = MapScreen.this.selections.get(MapScreen.this.dimension);
         return s == null ? new ChunkSelection() : s;
      }

      Map<Long, RegionFacts> facts() {
         return MapScreen.this.facts;
      }

      int mode() {
         return findMode;
      }

      void mode(int m) {
         findMode = m;
      }

      boolean inSelection() {
         return findInSelection;
      }

      void inSelection(boolean on) {
         findInSelection = on;
      }

      ChunkFilter last() {
         return lastFilter;
      }

      void preview(ChunkSelection chunks) {
         MapScreen.this.preview(chunks, MATCH);
      }

      void clearPreview() {
         MapScreen.this.clearPreview();
      }

      void run(ChunkFilter filter) {
         lastFilter = filter;
         ChunkFilter.Context ctx = this.context();
         MapScreen.this.clearPreview();
         if (filter.deep()) {
            MapScreen.this.findInside(filter.toSearch(ctx), filter.gate(), filter.buffer());
         } else {
            MapScreen.this.pick("filter", filter.toRule(ctx), filter.buffer());
         }
      }
   }

   FindHost findHost() {
      return new FindHost();
   }

   private void openFilter() {
      if (this.index == null || this.job != null || this.map == null) {
         return;
      }

      this.keep = true;
      Game.open(this.mc, new FilterScreen(this, this.findHost()));
   }

   private static ChunkSelection grow(ChunkSelection found, int buffer, WorldMap.Index index) {
      if (buffer <= 0 || found == null || found.isEmpty()) {
         return found;
      }

      ChunkSelection out = found.copy();
      found.forEach((x, z) -> {
         for (int dz = -buffer; dz <= buffer; dz++) {
            for (int dx = -buffer; dx <= buffer; dx++) {
               if (index.has(x + dx, z + dz)) {
                  out.add(x + dx, z + dz);
               }
            }
         }
      });
      return out;
   }

   private void findInside(ChunkFilter.Search search, ChunkRule gate, int buffer) {
      if (this.job != null || this.index == null) {
         return;
      }

      WorldMap m = this.map;
      WorldMap.Index i = this.index;
      String dim = this.dimension;
      int mine = this.run;
      MapScreen.Job j = new MapScreen.Job(Ui.t("chunkatlas.map.pick.filter.looking"), 0, true);
      String reading = Ui.t("chunkatlas.rules.reading");
      this.job = j;
      this.submit(() -> {
         WorldMap.Found found;
         boolean failed = false;
         try {
            found = m.find(i, search, new WorldMap.Progress() {
               @Override
               public void stage(boolean chunks, int total) {
                  j.done = 0;
                  j.total = total;
                  if (chunks) {
                     j.text = reading;
                  }
               }

               @Override
               public void done(int n) {
                  j.done = n;
               }
            }, () -> j.cancelled);
            if (found != null && (gate != null || buffer > 0)) {
               ChunkSelection chunks = found.chunks();
               if (gate != null && !chunks.isEmpty()) {
                  ChunkSelection passing = m.select(i, gate, n -> {}, () -> j.cancelled);
                  ChunkSelection both = new ChunkSelection();
                  chunks.forEach((x, z) -> {
                     if (passing != null && passing.contains(x, z)) {
                        both.add(x, z);
                     }
                  });
                  chunks = both;
               }

               found = new WorldMap.Found(grow(chunks, buffer, i), found.tooOld());
            }
         } catch (RuntimeException e) {
            found = null;
            failed = true;
         }

         WorldMap.Found f = found;
         boolean bad = failed;
         this.mc.execute(() -> {
            if (this.job == j && mine == this.run) {
               this.job = null;
               if (f == null) {
                  if (bad) {
                     this.say(Ui.t("chunkatlas.map.chunks_unreadable"), Ui.YELLOW);
                  }
               } else {
                  this.applyFound("filter", dim, f.chunks());
                  if (f.tooOld() > 0 && this.banner != null) {
                     this.say(this.banner + "  " + Ui.t("chunkatlas.rules.skipped_old", Ui.count(f.tooOld())), f.chunks().isEmpty() ? Ui.GREY : Ui.LIGHT_BLUE);
                  }
               }
            }
         });
      });
   }

   private void openSelections() {
      if (this.index == null || this.job != null) {
         return;
      }

      Path dir = SelectionFiles.dir(this.home, this.worldFolder, this.dimension);
      String dim = this.dimension;
      Map<Long, long[]> present = this.index.present();
      this.keep = true;
      Game.open(this.mc, new SelectionsScreen(this, dir, this.selection().copy(), loaded -> {
         ChunkSelection kept = new ChunkSelection();
         for (Long key : loaded.regions()) {
            long[] mask = present.get(key);
            long[] bits = loaded.bits((int)(key >> 32), (int)key.longValue());
            if (mask != null && bits != null) {
               long[] both = new long[mask.length];
               boolean some = false;
               for (int w = 0; w < mask.length; w++) {
                  both[w] = mask[w] & bits[w];
                  some |= both[w] != 0L;
               }

               if (some) {
                  kept.add((int)(key >> 32), (int)key.longValue(), both);
               }
            }
         }

         this.selections.put(dim, kept);
         this.say(Ui.t("chunkatlas.map.selection.loaded", Ui.chunks(kept.size())), Ui.LIGHT_BLUE);
      }));
   }

   private ChunkSelection selection() {
      return this.selections.computeIfAbsent(this.dimension, k -> new ChunkSelection());
   }

   private List<Ui.Item> dimensionItems() {
      List<Ui.Item> items = new ArrayList<>();

      for (WorldLayout.Dimension d : this.map.layout().dimensions()) {
         items.add(Ui.Item.check(Ui.dimension(d.id()), d.id().equals(this.dimension), () -> this.show(d.id())));
      }

      return items;
   }

   private List<Ui.Item> pickItems() {
      List<Ui.Item> items = new ArrayList<>();
      WorldMap.Index i = this.index;
      if (i == null) {
         return items;
      }

      items.add(new Ui.Item(Ui.t("chunkatlas.map.pick.filter"), "F", this::openFilter));
      items.add(Ui.Item.separator());
      items.add(new Ui.Item(Ui.t("chunkatlas.map.pick.never_visited"), null, () -> this.pick("never_visited", ChunkRule.inhabitedAtMost(0L))));
      items.add(new Ui.Item(Ui.t("chunkatlas.map.pick.hardly_visited"), null, () -> this.pick("hardly_visited", ChunkRule.inhabitedAtMost(6000L))));
      items.add(new Ui.Item(Ui.t("chunkatlas.map.pick.unfinished"), null, () -> this.pick("unfinished", ChunkRule.notFullyGenerated())));
      items.add(new Ui.Item(Ui.t("chunkatlas.map.pick.unreadable"), null, () -> this.pick("unreadable", ChunkRule.unreadable())));
      LevelMarkers.Markers m = this.map.markers();
      if (m.dataVersion() > 0) {
         items.add(
            new Ui.Item(
               Ui.t("chunkatlas.map.pick.older"),
               Ui.t("chunkatlas.map.pick.older.note", Ui.version(m.dataVersion())),
               () -> this.pick("older", ChunkRule.versionBelow(m.dataVersion()))
            )
         );
      }

      LevelMarkers.Point spawn = this.marker(m.spawn());
      if (spawn != null) {
         for (int radius : new int[]{1000, 5000}) {
            items.add(
               new Ui.Item(
                  Ui.t("chunkatlas.map.pick.far", Ui.count(radius)),
                  null,
                  () -> this.pick("far", ChunkRule.outsideRadius(spawn.x(), spawn.z(), radius))
               )
            );
         }
      }

      items.add(Ui.Item.separator());
      items.add(new Ui.Item(Ui.t("chunkatlas.map.pick.saved"), null, this::openSelections));
      items.add(new Ui.Item(Ui.t("chunkatlas.map.pick.all"), Ui.count(i.chunks()), this::selectAll));
      ChunkSelection guard = this.guarded();
      if (!guard.isEmpty()) {
         items.add(new Ui.Item(Ui.t("chunkatlas.protect.select"), Ui.count(guard.size()), this::selectProtected));
      }

      return items;
   }

   private List<Ui.Item> selectionItems() {
      List<Ui.Item> items = new ArrayList<>();
      ChunkSelection sel = this.selections.get(this.dimension);
      boolean any = sel != null && !sel.isEmpty();
      if (any) {
         items.add(Ui.Item.info(Ui.plural("chunkatlas.map.selection.size", sel.size(), Ui.count(sel.regions().size())), null));
         items.add(Ui.Item.separator());
      }

      items.add(new Ui.Item(Ui.t("chunkatlas.map.selection.all"), MapKey.SELECT_ALL.keyName(), this.index != null ? this::selectAll : null));
      items.add(new Ui.Item(Ui.t("chunkatlas.map.pick.invert"), MapKey.INVERT.keyName(), this.index != null ? this::invert : null));
      items.add(new Ui.Item(Ui.t("chunkatlas.map.selection.grow"), "+", any && this.index != null ? this::growSelection : null));
      items.add(new Ui.Item(Ui.t("chunkatlas.map.selection.trim"), "−", any ? this::trimSelection : null));
      items.add(Ui.Item.separator());
      items.add(new Ui.Item(Ui.t("chunkatlas.map.selection.clear"), "Esc", any ? sel::clear : null));
      return items;
   }

   private void growSelection() {
      if (this.index == null) {
         return;
      }

      ChunkSelection sel = this.selection();
      ChunkSelection add = new ChunkSelection();
      Map<Long, long[]> present = this.index.present();
      sel.forEach((x, z) -> {
         for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
               int nx = x + dx;
               int nz = z + dz;
               long[] bits = present.get(WorldLayout.key(nx >> 5, nz >> 5));
               int i = ChunkSelection.index(nx, nz);
               if (bits != null && (bits[i >> 6] >>> (i & 63) & 1L) != 0L && !sel.contains(nx, nz)) {
                  add.add(nx, nz);
               }
            }
         }
      });
      add.forEach(sel::add);
   }

   private void trimSelection() {
      ChunkSelection sel = this.selection();
      ChunkSelection edge = new ChunkSelection();
      sel.forEach((x, z) -> {
         for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
               if (!sel.contains(x + dx, z + dz)) {
                  edge.add(x, z);
                  return;
               }
            }
         }
      });
      edge.forEach(sel::remove);
   }

   private void selectAll() {
      if (this.index != null) {
         ChunkSelection sel = this.selection();

         for (Entry<Long, long[]> e : this.index.present().entrySet()) {
            sel.add((int)(e.getKey() >> 32), (int)e.getKey().longValue(), e.getValue());
         }
      }
   }

   private void invert() {
      ChunkSelection old = this.selection();
      ChunkSelection flipped = new ChunkSelection();

      for (Entry<Long, long[]> e : this.index.present().entrySet()) {
         int rx = (int)(e.getKey() >> 32);
         int rz = (int)e.getKey().longValue();
         long[] had = old.bits(rx, rz);
         long[] mask = e.getValue().clone();
         boolean some = false;

         for (int w = 0; w < mask.length; w++) {
            mask[w] &= had == null ? -1L : ~had[w];
            some |= mask[w] != 0L;
         }

         if (some) {
            flipped.add(rx, rz, mask);
         }
      }

      this.selections.put(this.dimension, flipped);
   }

   private void pick(String what, ChunkRule rule) {
      this.pick(what, rule, 0);
   }

   private void pick(String what, ChunkRule rule, int buffer) {
      if (this.job == null && this.index != null) {
         WorldMap m = this.map;
         WorldMap.Index i = this.index;
         String dim = this.dimension;
         int mine = this.run;
         MapScreen.Job j = new MapScreen.Job(Ui.t("chunkatlas.map.pick." + what + ".looking"), rule.needsFacts() ? i.regions().size() : 0, true);
         this.job = j;
         this.submit(
            () -> {
               boolean failed = false;

               ChunkSelection found;
               try {
                  found = grow(m.select(i, rule, n -> j.done = n, () -> j.cancelled), buffer, i);
               } catch (RuntimeException e) {
                  found = null;
                  failed = true;
               }

               ChunkSelection f = found;
               boolean bad = failed;
               this.mc
                  .execute(
                     () -> {
                        if (this.job == j && mine == this.run) {
                           this.job = null;
                           if (f == null) {
                              if (bad) {
                                 this.say(Ui.t("chunkatlas.map.chunks_unreadable"), Ui.YELLOW);
                              }
                           } else {
                              this.applyFound(what, dim, f);
                           }
                        }
                     }
                  );
            }
         );
      }
   }

   private void applyFound(String what, String dim, ChunkSelection f) {
      ChunkSelection sel = this.selections.computeIfAbsent(dim, k -> new ChunkSelection());
      if ("filter".equals(what)) {
         if (findInSelection) {
            ChunkSelection inside = new ChunkSelection();
            f.forEach((x, z) -> {
               if (sel.contains(x, z)) {
                  inside.add(x, z);
               }
            });
            f = inside;
         }

         if (findMode == 2) {
            int before = sel.size();
            f.forEach(sel::remove);
            this.say(Ui.plural("chunkatlas.map.found_removed", before - sel.size()), Ui.LIGHT_BLUE);
            return;
         }

         if (findMode == 0) {
            sel.clear();
         }
      }

      if (f.isEmpty()) {
         this.say(Ui.t("chunkatlas.map.pick." + what + ".none"), Ui.GREY);
         return;
      }

      int before = sel.size();

      for (Long key : f.regions()) {
         int rx = (int)(key >> 32);
         int rz = (int)key.longValue();
         sel.add(rx, rz, f.bits(rx, rz));
      }

      this.say(
         sel.size() - before == f.size()
            ? Ui.plural("chunkatlas.map.found", f.size())
            : Ui.plural("chunkatlas.map.found_some_new", f.size(), Ui.count(sel.size() - before)),
         Ui.LIGHT_BLUE
      );
   }

   private boolean onServer() {
      return this.liveMap();
   }

   private void planOnServer(ChunkEdits.Kind kind) {
      ChunkSelection sel = this.selections.get(this.dimension);
      if (sel == null || sel.isEmpty() || this.job != null) {
         return;
      }

      ChunkSelection guard = this.guarded();
      ChunkSelection chunks = kind == ChunkEdits.Kind.KEEP_ONLY ? Protection.with(sel.copy(), guard) : Protection.without(sel.copy(), guard);
      if (chunks.isEmpty()) {
         this.say(Ui.t("chunkatlas.protect.all_protected"), Ui.GOLD);
         return;
      }

      String dim = this.dimension;
      String what = Ui.t("chunkatlas.server.confirm." + kind.name().toLowerCase(java.util.Locale.ROOT), Ui.count(chunks.size()));

      boolean now = dev.chunkatlas.ServerLink.madeNow(kind.name());
      this.keep = true;
      Game.open(this.mc, new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
         Game.open(this.mc, this);
         if (!yes) {
            return;
         }

         boolean sent = dev.chunkatlas.ServerLink.edit(kind.name(), dim, chunks, now, reply -> {
            if (reply.ok()) {
               ChunkSelection shown = this.serverWaiting.computeIfAbsent(dim, d -> new ChunkSelection());
               if (kind == ChunkEdits.Kind.DELETE) {
                  chunks.forEach(shown::add);
               }

               ChunkSelection mine = this.selections.get(dim);
               if (mine != null) {
                  mine.clear();
               }
            }

            this.say(Ui.t(reply.key(), Ui.count(reply.chunks()), reply.key().endsWith("live_done") ? reply.later() : reply.waiting()), reply.ok() ? Ui.GREEN : Ui.YELLOW);
         });
         if (!sent) {
            this.say(Ui.t("chunkatlas.server.not_allowed"), Ui.YELLOW);
         }
      }, Component.literal(what), Component.translatable(now ? "chunkatlas.server.confirm.when_live" : "chunkatlas.server.confirm.when")));
   }

   private final Map<String, ChunkSelection> serverWaiting = new HashMap<>();

   private void cancelServerEdits() {
      dev.chunkatlas.ServerLink.cancelAll(reply -> {
         this.serverWaiting.clear();
         this.say(Ui.t(reply.key()), reply.ok() ? Ui.GREY : Ui.YELLOW);
      });
   }

   private void plan(ChunkEdits.Kind kind) {
      if (this.onServer()) {
         this.planOnServer(kind);
         return;
      }

      ChunkSelection sel = this.selections.get(this.dimension);
      if (this.pick == null && this.job == null && this.index != null && sel != null && !sel.isEmpty()) {
         ChunkSelection copy = sel.copy();
         ChunkSelection guard = this.guarded().copy();
         int skipped = protectedSkipped(kind, copy, guard);
         WorldLayout.Dimension d = this.index.dimension();
         Path world = this.worldFolder;
         int worldVersion = this.map.markers().dataVersion();
         int mine = this.run;
         MapScreen.Job j = new MapScreen.Job(Ui.t("chunkatlas.map.job.planning"), 0, false);
         this.job = j;
         this.submit(
            () -> {
               ChunkEdits.Plan p;
               try {
                  p = planFor(kind, world, d, copy, guard);
               } catch (RuntimeException e) {
                  p = null;
               }

               ChunkEdits.Plan plan = p;
               this.mc
                  .execute(
                     () -> {
                        if (this.job == j && mine == this.run) {
                           this.job = null;
                           if (plan == null) {
                              this.say(Ui.t("chunkatlas.map.files_unreadable"), Ui.YELLOW);
                           } else if (plan.isEmpty()) {
                              this.say(
                                 skipped > 0 && skipped == copy.size() ? Ui.t("chunkatlas.protect.all_protected") : Ui.t(emptyPlanKey(kind, plan)),
                                 skipped > 0 && skipped == copy.size() ? Ui.GOLD : Ui.GREY
                              );
                           } else if (Game.screen(this.mc) == this) {
                              this.keep = true;
                              Supplier<ChunkEdits.Plan> replan = () -> {
                                 try {
                                    return planFor(kind, world, d, copy, Protection.load(this.home, world, d.id()));
                                 } catch (RuntimeException e) {
                                    return null;
                                 }
                              };
                              Game.open(this.mc, ConfirmEditScreen.edit(this, this.home, world, plan, replan, worldVersion, this.playerInside(copy, kind), skipped));
                           }
                        }
                     }
                  );
            }
         );
      }
   }

   private void undo() {
      if (!this.pending.isEmpty()) {
         this.undoPending();
         return;
      }

      if (this.pick == null && this.job == null && this.map != null && !this.lastEdit.isEmpty()) {
         this.keep = true;
         Game.open(this.mc, ConfirmEditScreen.undo(this, this.home, this.worldFolder, this.lastEdit.get(), this.map.markers().dataVersion()));
      }
   }

   InGame.Session live() {
      return this.live;
   }

   double seedCx() {
      return this.cx;
   }

   double seedCz() {
      return this.cz;
   }

   double seedZoom() {
      return this.zoom;
   }

   int seedTop() {
      return this.mapTop;
   }

   int seedBottom() {
      return this.mapBottom;
   }

   double seedScreenX(double block) {
      return this.screenX(block);
   }

   double seedScreenY(double block) {
      return this.screenY(block);
   }

   String seedDimension() {
      return this.index == null ? null : this.dimension;
   }

   private final Map<Long, long[]> drawnChunks = new HashMap<>();

   Map<Long, long[]> seedDrawn() {
      return this.drawnChunks;
   }

   private static long[] drawnMask(int[] argb, int side) {
      long[] mask = new long[16];
      int per = Math.max(1, side / 32);
      for (int cz = 0; cz < 32; cz++) {
         for (int cx = 0; cx < 32; cx++) {
            int x = Math.min(side - 1, cx * per + per / 2);
            int y = Math.min(side - 1, cz * per + per / 2);
            if (argb[y * side + x] >>> 24 != 0) {
               int i = cz * 32 + cx;
               mask[i >> 6] |= 1L << (i & 63);
            }
         }
      }

      return mask;
   }

   Map<Long, long[]> seedPresent() {
      return this.index == null ? null : this.index.present();
   }

   Map<Long, RegionFacts> seedFacts() {
      return this.facts;
   }

   boolean seedSavedLoading() {
      return this.map != null && this.map.busy() > 0;
   }

   double seedZoomTarget() {
      return this.zoomTarget;
   }

   Map<Long, dev.chunkatlas.engine.WorldLayout.RegionInfo> seedRegionFiles() {
      return this.index == null || this.map == null || this.map.fromLiveMap() ? null : this.index.regions();
   }

   Path seedWorldFolder() {
      return this.worldFolder;
   }

   Path seedHome() {
      return this.home;
   }

   void seedTell(String text, int colour) {
      this.say(text, colour);
   }

   void seedCentre(double x, double z) {
      this.cx = x;
      this.cz = z;
      this.anchorX = this.mapCx();
      this.anchorY = this.mapCy();
      this.zoomTarget = this.clampZoom(Math.max(this.zoomTarget, 0.5));
      this.clampView();
   }

   void seedOpen(Screen screen) {
      this.keep = true;
      Game.open(this.mc, screen);
   }

   SeedMapLayer seedLayer() {
      return this.seed;
   }

   SeedPanel seedPanel() {
      if (this.seedPanel == null) {
         this.seedPanel = new SeedPanel(this, this.seed, this.font);
      }

      return this.seedPanel;
   }

   TasksPanel tasksPanel() {
      if (this.tasksPanel == null) {
         this.tasksPanel = new TasksPanel(this, this.font);
      }

      return this.tasksPanel;
   }

   private boolean tasksShown() {
      return !this.hideControls && this.live != null && this.tasksPanel != null && this.tasksPanel.isOpen();
   }

   private boolean seedPanelShown() {
      return !this.editing && !this.hideControls && this.seedPanel != null && this.seedPanel.isOpen();
   }

   void openSeedPanel(SeedPanel.Tab tab, boolean editSeed) {
      this.seedPanel().open(tab, editSeed);
   }

   String tourCoverage() {
      StringBuilder sb = new StringBuilder();
      for (WorldLayout.RegionInfo r : this.visible(0)) {
         int[] c = this.seed.tourCover(r.rx(), r.rz());
         MapScreen.Held h = this.tiles.get(WorldLayout.key(r.rx(), r.rz()));
         sb.append(" ").append(r.rx()).append(",").append(r.rz()).append(" ").append(c[0]).append("/").append(c[1]).append("/").append(c[2]).append("/").append(c[3])
            .append(" L").append(h == null ? "-" : h.level() + (h.stale() ? "s" : ""));
      }

      return sb.toString();
   }

   void tourSettingsTab(int tab) {
      this.keep = true;
      Game.open(this.mc, new SettingsScreen(this, tab));
   }

   void tourPasteCard(boolean open) {
      if (open) {
         this.pasteBar.tourOpen();
      } else {
         this.pasteBar.close();
      }
   }

   void tourOver(Screen screen) {
      this.keep = true;
      Game.open(this.mc, screen);
   }

   void tourPanel(boolean find) {
      if (find) {
         this.openFilter();
      } else {
         this.openSettings();
      }
   }

   void tourColourBy(int bit) {
      this.colourBy(bit);
   }

   double[] tourCentre() {
      return new double[]{this.cx, this.cz};
   }

   java.nio.file.Path tourRegionFolder() {
      return this.index.dimension().region();
   }

   String tourBiomeCheck() {
      StringBuilder b = new StringBuilder("biome read-out vs game:");
      net.minecraft.server.MinecraftServer server = this.mc.getSingleplayerServer();
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimId = Ids.dimension(this.dimension);
      net.minecraft.server.level.ServerLevel level = server == null || dimId == null ? null : server.getLevel(dimId);
      int same = 0;
      int asked = 0;
      for (int j = 1; j <= 5; j++) {
         for (int i = 1; i <= 5; i++) {
            int sx = this.width * i / 6;
            int sy = this.height * j / 6;
            int bx = (int)Math.floor(this.blockX(sx));
            int bz = (int)Math.floor(this.blockZ(sy));
            String read = this.hoverText(sx, sy, 100000);
            String truth = "?";
            String game = level == null ? null : dev.chunkatlas.port.Seeds.gameBiome(level, bx, bz);
            if (game != null) {
               truth = SeedMapLayer.biomeName(game);
            }

            String saved = String.valueOf(this.savedBiome(bx, bz, Math.floorDiv(bx, 16), Math.floorDiv(bz, 16)));
            asked++;
            if (read.contains(truth)) {
               same++;
            }

            b.append("\n  ").append(bx).append(",").append(bz).append(" game=").append(truth).append(" saved=").append(saved).append(" | ").append(read);
         }
      }

      return b.append("\n  matching ").append(same).append("/").append(asked).toString();
   }

   int[] tourTiles() {
      if (this.index == null) {
         return new int[]{0, 0, 0};
      }

      int lv = this.level();
      int any = 0;
      int sharp = 0;
      List<WorldLayout.RegionInfo> vis = this.visible(0);
      for (WorldLayout.RegionInfo r : vis) {
         MapScreen.Held h = this.tiles.get(WorldLayout.key(r.rx(), r.rz()));
         if (h != null) {
            any++;
            if (h.level() <= lv) {
               sharp++;
            }
         }
      }

      return new int[]{vis.size(), any, sharp};
   }

   void tourSeedPanel(boolean open) {
      if (open) {
         this.seedPanel().open(SeedPanel.Tab.STRUCTURES, false);
      } else {
         this.seedPanel().close();
      }
   }

   private boolean playerInside(ChunkSelection sel, ChunkEdits.Kind kind) {
      LevelMarkers.Point at = this.map == null ? null : this.marker(this.map.markers().player());
      if (at == null) {
         return false;
      }

      boolean in = sel.contains(Math.floorDiv((int)Math.floor(at.x()), 16), Math.floorDiv((int)Math.floor(at.z()), 16));
      return kind == ChunkEdits.Kind.DELETE ? in : !in;
   }

   boolean settled() {
      return this.map != null
         && this.index != null
         && !this.loading
         && this.job == null
         && this.zoom == this.zoomTarget
         && this.askedLevel >= 0
         && this.asked.isEmpty()
         && this.map.busy() == 0;
   }

   boolean tourCentreOnLand() {
      WorldMap.Index i = this.index;
      if (i == null) {
         return false;
      }

      long best = 0L;
      int most = -1;
      for (Map.Entry<Long, long[]> e : i.present().entrySet()) {
         int n = 0;
         for (long b : e.getValue()) {
            n += Long.bitCount(b);
         }

         if (n > most) {
            most = n;
            best = e.getKey();
         }
      }

      if (most <= 0) {
         return false;
      }

      long[] bits = i.present().get(best);
      int rx = (int)(best >> 32);
      int rz = (int)best;

      int pick = -1;
      int bestD = Integer.MAX_VALUE;
      for (int k = 0; k < 1024; k++) {
         if ((bits[k >> 6] & 1L << (k & 63)) != 0L) {
            int d = Math.abs((k & 31) - 16) + Math.abs((k >> 5) - 16);
            if (d < bestD) {
               bestD = d;
               pick = k;
            }
         }
      }

      this.follow = false;
      this.seedCentre((rx * 32 + (pick & 31)) * 16.0 + 8.0, (rz * 32 + (pick >> 5)) * 16.0 + 8.0);
      return true;
   }

   int[][] tourLandPoints() {
      List<int[]> out = new ArrayList<>();
      for (int oy = -1; oy <= 1; oy++) {
         for (int ox = -1; ox <= 1; ox++) {
            int x = (int)this.mapCx() + ox * 12;
            int y = (int)this.mapCy() + oy * 12;
            int bx = (int)Math.floor(this.blockX(x));
            int bz = (int)Math.floor(this.blockZ(y));
            long[] drawn = this.drawnChunks.get(WorldLayout.key(Math.floorDiv(bx, REGION), Math.floorDiv(bz, REGION)));
            int k = (Math.floorMod(bz, REGION) >> 4) * 32 + (Math.floorMod(bx, REGION) >> 4);
            if (drawn != null && (drawn[k >> 6] & 1L << (k & 63)) != 0L) {
               out.add(new int[]{x, y});
            }
         }
      }

      return out.toArray(new int[0][]);
   }

   String tourHeld() {
      return this.tiles.size() + " pictures, " + this.backups.size() + " backups, " + this.heldPixels + " pixels held, level " + this.level() + ", zoom " + this.zoom;
   }

   void tourTool(String name) {
      this.editing = true;
      this.tool = MapScreen.Tool.valueOf(name.toUpperCase(java.util.Locale.ROOT));
   }

   String tourDrag(int button, boolean space, double x0, double y0, double x1, double y1) {
      boolean deaf = tourDeaf;
      tourDeaf = false;
      double cx0 = this.cx;
      double cz0 = this.cz;
      int before = this.selection().size();
      try {
         this.space = space;
         this.onClick(x0, y0, button, false);
         int steps = 8;
         double lx = x0;
         double ly = y0;
         for (int k = 1; k <= steps; k++) {
            double x = x0 + (x1 - x0) * k / steps;
            double y = y0 + (y1 - y0) * k / steps;
            this.onDrag(x, y, button, x - lx, y - ly);
            lx = x;
            ly = y;
         }

         this.onRelease(x1, y1, button);
      } finally {
         this.space = false;
         tourDeaf = deaf;
      }

      return "view moved " + Math.round(this.cx - cx0) + ", " + Math.round(this.cz - cz0) + " blocks; picked " + before + " -> " + this.selection().size();
   }

   void tourHideControls(boolean hide) {
      this.hideControls = hide;
   }

   void tourZoom(double factor) {
      this.anchorX = this.mapCx();
      this.anchorY = this.mapCy();
      this.zoomTarget = this.clampZoom(this.zoomTarget * factor);
   }

   void tourView(int n) {
      this.setView(MapScreen.View.values()[n % MapScreen.View.values().length]);
   }

   void tourBrush() {
      this.editing = true;
      this.tool = MapScreen.Tool.BRUSH;
      this.brush = 2;
      this.paintStroke(this.mapCx() - 120.0, this.mapCy() - 20.0, this.mapCx() + 120.0, this.mapCy() + 40.0, true);
   }

   void tourRegion() {
      if (this.index != null) {
         this.editing = true;
         this.tool = MapScreen.Tool.REGION;
         int x = Math.floorDiv((int)Math.floor(this.cx), 16) + 64;
         int z = Math.floorDiv((int)Math.floor(this.cz), 16);
         this.pickRegions(new int[]{x, z, x, z}, true, true);
      }
   }

   void tourOpenFilter() {
      this.openFilter();
   }

   void tourOpenSelections() {
      this.openSelections();
   }

   void tourReset() {
      this.selection().clear();
      this.tool = MapScreen.Tool.CHUNKS;
      this.setView(MapScreen.View.TERRAIN);
   }

   void tourPan(double blocksX, double blocksZ) {
      this.cx += blocksX;
      this.cz += blocksZ;
      this.clampView();
   }

   void tourSelect(int halfWidth, int halfHeight) {
      if (this.index != null) {
         int x = Math.floorDiv((int)Math.floor(this.cx), 16);
         int z = Math.floorDiv((int)Math.floor(this.cz), 16);
         this.editing = true;
         this.tool = MapScreen.Tool.SELECT;
         this.selection().addRect(x - halfWidth, z - halfHeight, x + halfWidth, z + halfHeight, this.index.present());
      }
   }

   void tourExport(String label, boolean newWorld) {
      this.exportChunks(label, newWorld ? Out.WORLD : Out.CLIP);
   }

   void tourCopy() {
      this.copySelection();
   }

   void tourPaste() {
      this.startPaste();
   }

   void tourPlace(int dx, int dz) {
      this.placePaste(dx, dz);
   }

   void tourHoldPaste(int dx, int dz) {
      this.pasteHeld = new int[]{dx, dz};
   }

   boolean tourPasteSettled() {
      int blend = Settings.pasteBlend();
      return this.pasteChunks != null && this.pasteView.pictureReady() && this.pasteView.ringReady(this.pasteDx, this.pasteDz, blend) && (!this.smartLift() || this.pasteView.liftDone(this.pasteDx, this.pasteDz, blend));
   }

   String tourPasteState() {
      dev.chunkatlas.engine.PasteTerrain.Lift l = this.pasteChunks != null ? this.pasteView.lift(this.pasteDx, this.pasteDz, Settings.pasteBlend()) : this.lastSmart;
      String picture = this.pasteChunks == null ? "" : (this.pasteView.pictureReady() ? "picture in" : "no picture") + "; ";
      return picture + (l == null ? "no move worked out" : "move " + l.dy() + " blocks (paste ground " + l.pasted() + ", land " + l.land() + ", limits " + l.min() + ".." + l.max() + ")");
   }

   boolean tourPasting() {
      return this.pasteChunks != null;
   }

   void tourCopyFrom() {
      this.copyFrom();
   }

   int tourPending() {
      return this.pending.size();
   }

   void tourMeasure(double bx, double bz) {
      this.measureFrom = Double.isNaN(bx) ? null : new double[]{bx, bz};
   }

   void tourUnreadable() {
      this.pick("unreadable", ChunkRule.unreadable());
   }

   void tourCave(int y) {
      this.setCave(y, false);
   }

   void tourStyle(int bits) {
      if (this.map != null) {
         this.map.style(bits & ~dev.chunkatlas.Settings.LOOK_BITS | dev.chunkatlas.Settings.lookBits());
         this.dropTiles();
      }
   }

   void tourTeleport(int bx, int bz) {
      this.teleport(bx, bz);
   }

   void tourShow(String dimension) {
      this.show(dimension);
   }

   void tourTryEdit() {
      this.setEditing(true);
   }

   void tourContext(int bx, int bz) {
      this.contextBlockX = bx;
      this.contextBlockZ = bz;
      this.contextX = (int)Math.floor(this.screenX(bx));
      this.contextY = (int)Math.floor(this.screenY(bz));
      this.menu = MapScreen.Menu.CONTEXT;
   }

   void tourSelectionMenu(boolean open) {
      if (open && this.dock != null) {
         this.menuFrom = new int[]{this.width / 2 - 20, this.dockTop, 0, 0};
         this.menu = MapScreen.Menu.SELECTION;
      } else {
         this.menu = MapScreen.Menu.NONE;
      }
   }

   void tourClear() {
      ChunkSelection sel = this.selections.get(this.dimension);
      if (sel != null) {
         sel.clear();
      }
   }

   void tourEdit(boolean on) {
      this.editing = on;
      this.menu = MapScreen.Menu.NONE;
   }

   void tourLayers() {
      this.open(MapScreen.Menu.VIEW, "layers");
   }

   void tourMenu(boolean open) {
      if (open && this.index != null) {
         this.open(MapScreen.Menu.PICK, "find");
      } else {
         this.menu = MapScreen.Menu.NONE;
      }
   }

   void tourDelete() {
      this.plan(ChunkEdits.Kind.DELETE);
   }

   void tourProtect(boolean on) {
      this.protect(on);
   }

   int tourProtected() {
      return this.guarded().size();
   }

   void tourPicture() {
      this.savePicture();
   }

   boolean tourIdle() {
      return this.job == null;
   }

   void tourFind(String encoded) {
      ChunkFilter filter = ChunkFilter.decode(encoded);
      lastFilter = filter;
      LevelMarkers.Point spawn = this.marker(this.map.markers().spawn());
      LevelMarkers.Point player = this.marker(this.map.markers().player());
      ChunkFilter.Context ctx = new ChunkFilter.Context(
         spawn == null ? null : spawn.x(),
         spawn == null ? null : spawn.z(),
         player == null ? null : player.x(),
         player == null ? null : player.z(),
         this.map.markers().dataVersion(),
         System.currentTimeMillis() / 1000L
      );
      if (filter.deep()) {
         this.findInside(filter.toSearch(ctx), filter.gate(), filter.buffer());
      } else {
         this.pick("filter", filter.toRule(ctx), filter.buffer());
      }
   }

   void tourSelectStroke() {
      this.editing = true;
      this.tool = MapScreen.Tool.CHUNKS;
      this.paintStroke(this.mapCx() - 60.0, this.mapCy(), this.mapCx() + 60.0, this.mapCy() + 20.0, true);
   }

   void tourOpenPresets() {
      if (Game.screen(this.mc) instanceof FilterScreen f) {
         f.tourOpenPresets();
      }
   }

   void tourContext() {
      this.openContext(this.mapCx() + 40.0, this.mapCy() + 20.0);
   }

   void tourCloseMenu() {
      this.menu = MapScreen.Menu.NONE;
   }

   void tourOpenShrink() {
      this.openShrink();
   }

   String tourBanner() {
      return this.banner;
   }

   void tourUndo() {
      this.undo();
   }

   String tourState() {
      ChunkSelection sel = this.dimension == null ? null : this.selections.get(this.dimension);
      return "dimension "
         + this.dimension
         + ", "
         + (this.index == null ? "no index" : this.index.chunks() + " chunks in " + this.index.regions().size() + " regions")
         + ", "
         + (sel == null ? 0 : sel.size())
         + " selected, "
         + this.tiles.size()
         + " pictures, zoom "
         + this.zoom
         + ", undo "
         + this.lastEdit.isPresent()
         + (this.banner == null ? "" : ", says: " + this.banner);
   }

   private int[] dragChunks() {
      int x0 = Math.floorDiv((int)Math.floor(this.blockX(this.dragX0)), 16);
      int z0 = Math.floorDiv((int)Math.floor(this.blockZ(this.dragY0)), 16);
      int x1 = Math.floorDiv((int)Math.floor(this.blockX(this.dragX1)), 16);
      int z1 = Math.floorDiv((int)Math.floor(this.blockZ(this.dragY1)), 16);
      return new int[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)};
   }

   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (tourDeaf) {
         return true;
      }

      double x = mouseX;
      double y = mouseY;
      boolean left = mouseButton == InputConstants.MOUSE_BUTTON_LEFT;
      if (this.job == null && this.menu == MapScreen.Menu.NONE) {
         if (left && this.caveSlider != null && x >= this.caveSlider[0] && x < this.caveSlider[0] + this.caveSlider[2]
            && y >= this.caveSlider[1] - 6 && y < this.caveSlider[1] + this.caveSlider[3] + 6) {
            this.drag = MapScreen.Drag.CAVE_Y;
            this.caveFromSlider(y);
            return true;
         }

         boolean panel = this.seedPanelShown();
         if (panel && this.seedPanel.focusAt(mouseX, mouseY, mouseButton, this.mods()) && left) {
            return true;
         }

         if (this.tasksShown() && this.tasksPanel.focusAt(mouseX, mouseY, mouseButton, this.mods()) && left) {
            return true;
         }

         if (left) {
            for (int n = this.hits.size() - 1; n >= 0; n--) {
               if (this.hits.get(n).has(x, y)) {
                  this.hits.get(n).action().run();
                  return true;
               }
            }
         }

         if (this.pasteChunks != null && this.pasteBar.cardOpen()) {
            this.pasteBar.close();
            return true;
         }

         if (panel && this.seedPanel.contains(x, y) || this.tasksShown() && this.tasksPanel.contains(x, y)) {
            return true;
         }

         if (this.map != null && this.index != null && !this.index.isEmpty() && !(y < this.mapTop) && !(y >= this.mapBottom) && x >= this.mapLeft && x < this.mapRight) {
            int button = mouseButton;
            if (this.measureFrom != null) {
               this.measureFrom = null;
               return true;
            }

            if (this.pasteChunks != null) {
               if (left) {
                  this.placePaste(this.pasteDx, this.pasteDz);
               } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                  this.cancelPaste();
               }

               return true;
            }

            MapScreen.Drag d = MapScreen.Drag.NONE;
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
               this.openContext(x, y);
               return true;
            } else if (button == InputConstants.MOUSE_BUTTON_MIDDLE || left && (!this.editing || this.space || this.tool == MapScreen.Tool.HAND)) {
               d = MapScreen.Drag.PAN;
            } else if (left) {
               boolean off = Keys.shift(this.mods());
               d = switch (this.tool) {
                  case HAND -> MapScreen.Drag.PAN;
                  case CHUNKS, BRUSH -> off ? MapScreen.Drag.ERASE : MapScreen.Drag.PAINT;
                  case SELECT -> off ? MapScreen.Drag.UNSELECT : MapScreen.Drag.SELECT;
                  case REGION -> off ? MapScreen.Drag.REGION_UNSELECT : MapScreen.Drag.REGION_SELECT;
               };
            }

            if (d == MapScreen.Drag.NONE) {
               return false;
            }

            this.drag = d;
            this.dragMoved = false;
            this.dragX0 = this.dragX1 = x;
            this.dragY0 = this.dragY1 = y;
            this.zoomTarget = this.zoom;
            if (d == MapScreen.Drag.PAINT || d == MapScreen.Drag.ERASE) {
               this.paintAt(x, y, d == MapScreen.Drag.PAINT);
            }

            return true;
         } else {
            return super.onClick(mouseX, mouseY, mouseButton, doubleClick);
         }
      } else {
         boolean inMenu = this.menu != MapScreen.Menu.NONE && x >= this.menuX && x < this.menuX + this.menuW && y >= this.menuY && y < this.menuY + this.menuH;
         Runnable action = null;
         if (left) {
            for (int n = this.top.size() - 1; n >= 0 && action == null; n--) {
               if (this.top.get(n).has(x, y)) {
                  action = this.top.get(n).action();
               }
            }
         }

         if (this.job == null && (action != null || !inMenu)) {
            this.menu = MapScreen.Menu.NONE;
         }

         if (action != null) {
            action.run();
         }

         return true;
      }
   }

   protected boolean onDrag(double mouseX, double mouseY, int mouseButton, double dx, double dy) {
      if (tourDeaf) {
         return true;
      }

      if (this.drag == MapScreen.Drag.NONE) {
         return super.onDrag(mouseX, mouseY, mouseButton, dx, dy);
      }

      if (this.drag == MapScreen.Drag.CAVE_Y) {
         this.caveFromSlider(mouseY);
         return true;
      }

      double lastX = this.dragX1;
      double lastY = this.dragY1;
      this.dragX1 = mouseX;
      this.dragY1 = Math.max(this.mapTop, Math.min(this.mapBottom - 1, mouseY));
      if (this.drag == MapScreen.Drag.PAINT || this.drag == MapScreen.Drag.ERASE) {
         this.paintStroke(lastX, lastY, this.dragX1, this.dragY1, this.drag == MapScreen.Drag.PAINT);
         return true;
      }

      if (Math.abs(this.dragX1 - this.dragX0) + Math.abs(this.dragY1 - this.dragY0) > 3.0) {
         this.dragMoved = true;
      }

      if (this.drag == MapScreen.Drag.PAN) {
         this.follow = false;
         this.cx = this.cx - dx / this.zoom;
         this.cz = this.cz - dy / this.zoom;
         this.clampView();
      }

      return true;
   }

   protected boolean onRelease(double mouseX, double mouseY, int mouseButton) {
      if (tourDeaf) {
         return true;
      }

      MapScreen.Drag d = this.drag;
      this.drag = MapScreen.Drag.NONE;
      if (d == MapScreen.Drag.NONE || this.index == null) {
         return super.onRelease(mouseX, mouseY, mouseButton);
      }

      if (d == MapScreen.Drag.PAN || d == MapScreen.Drag.PAINT || d == MapScreen.Drag.ERASE) {
         return true;
      }

      int[] c = this.dragChunks();
      if (d == MapScreen.Drag.REGION_SELECT || d == MapScreen.Drag.REGION_UNSELECT) {
         this.pickRegions(c, d == MapScreen.Drag.REGION_SELECT, !this.dragMoved && d == MapScreen.Drag.REGION_SELECT);
         return true;
      }

      ChunkSelection sel = this.selection();
      if (!this.dragMoved) {
         if (d == MapScreen.Drag.UNSELECT || sel.contains(c[0], c[1])) {
            sel.remove(c[0], c[1]);
         } else if (this.pickable(c[0], c[1])) {
            sel.add(c[0], c[1]);
         }
      } else if (d == MapScreen.Drag.SELECT) {
         sel.addRect(c[0], c[1], c[2], c[3], this.pickable(c, false));
      } else {
         sel.removeRect(c[0], c[1], c[2], c[3], this.pickable(c, true));
      }

      return true;
   }

   protected boolean onScroll(double x, double y, double scrollX, double scrollY) {
      if (tourDeaf) {
         return true;
      } else if (this.job == null && this.menu == MapScreen.Menu.NONE && this.seedPanelShown() && this.seedPanel.contains(x, y)) {
         this.seedPanel.scroll(scrollY);
         return true;
      } else if (this.job == null && this.menu == MapScreen.Menu.NONE && this.tasksShown() && this.tasksPanel.contains(x, y)) {
         this.tasksPanel.scroll(scrollY);
         return true;
      } else if (this.job == null && this.menu == MapScreen.Menu.NONE && this.index != null && !(y < this.mapTop) && !(y >= this.mapBottom) && scrollY != 0.0) {
         this.zoomTarget = this.clampZoom(this.zoomTarget * Math.pow(2.0, Math.max(-4.0, Math.min(4.0, scrollY)) * 0.5));
         this.anchorX = x;
         this.anchorY = y;
         return true;
      } else {
         return true;
      }
   }

   protected boolean onChar(int codepoint, int modifiers) {
      if (this.seedPanelShown() && this.seedPanel.charTyped(codepoint, modifiers)) {
         return true;
      }

      if (this.tasksShown() && this.tasksPanel.charTyped(codepoint, modifiers)) {
         return true;
      }

      return super.onChar(codepoint, modifiers);
   }

   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (tourDeaf) {
         return true;
      }

      if (this.seedPanelShown() && this.seedPanel.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      }

      if (this.tasksShown() && this.tasksPanel.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      }

      if (Keys.escape(keyCode)) {
         ChunkSelection sel = this.dimension == null ? null : this.selections.get(this.dimension);
         if (this.measureFrom != null) {
            this.measureFrom = null;
         } else if (this.pasteChunks != null && this.pasteBar.cardOpen()) {
            this.pasteBar.close();
         } else if (this.pasteChunks != null) {
            this.cancelPaste();
         } else if (this.menu != MapScreen.Menu.NONE) {
            this.menu = MapScreen.Menu.NONE;
         } else if (this.job != null) {
            if (this.job.canCancel) {
               this.job.cancelled = true;
               this.job = null;
            }
         } else if (this.seedPanelShown()) {
            this.seedPanel.close();
         } else if (this.tasksShown()) {
            this.tasksPanel.close();
         } else if (this.editing && this.pick == null && sel != null && !sel.isEmpty()) {
            sel.clear();
         } else {
            this.onClose();
         }

         return true;
      } else if (this.job == null && this.menu == MapScreen.Menu.NONE) {
         if (this.pasteChunks != null && keyCode == InputConstants.KEY_TAB) {
            this.pasteKeep = !this.pasteKeep;
            return true;
         }

         if (keyCode == InputConstants.KEY_SPACE) {
            this.space = true;
            return true;
         } else if (MapKey.HIDE_CONTROLS.matches(keyCode, modifiers)) {
            this.hideControls = !this.hideControls;
            return true;
         } else if (MapKey.SEARCH.matches(keyCode, modifiers) && this.index != null) {
            this.keep = true;
            Game.open(this.mc, new SearchScreen(this, this.dimension, (x, z) -> {
               this.follow = false;
               this.cx = x + 0.5;
               this.cz = z + 0.5;
               this.anchorX = this.mapCx();
               this.anchorY = this.mapCy();
               this.zoomTarget = this.clampZoom(Math.max(this.zoomTarget, 1.0));
            }));
            return true;
         } else if (MapKey.GO_TO.matches(keyCode, modifiers)) {
            this.openGoTo();
            return true;
         } else if (MapKey.PASTE.matches(keyCode, modifiers) && this.hasClipboard()) {
            this.startPaste();
            return true;
         } else if (MapKey.EDIT_MODE.matches(keyCode, modifiers) && this.index != null && !this.index.isEmpty()) {
            this.setEditing(!this.editing);
            return true;
         } else if (!this.editing) {
            return super.onKey(keyCode, scanCode, modifiers);
         }

         if (MapKey.COPY.matches(keyCode, modifiers)) {
            this.copySelection();
            return true;
         }

         if (MapKey.SELECT_ALL.matches(keyCode, modifiers)) {
            this.selectAll();
         } else if (MapKey.INVERT.matches(keyCode, modifiers)) {
            this.invert();
         } else if (MapKey.UNDO.matches(keyCode, modifiers)) {
            if (this.lastEdit.isPresent() && this.pick == null) {
               this.undo();
            }
         } else if (MapKey.TOOL_HAND.matches(keyCode, modifiers)) {
            this.tool = MapScreen.Tool.HAND;
         } else if (MapKey.TOOL_SELECT.matches(keyCode, modifiers)) {
            this.tool = MapScreen.Tool.CHUNKS;
         } else if (MapKey.TOOL_BOX.matches(keyCode, modifiers)) {
            this.tool = MapScreen.Tool.SELECT;
         } else if (MapKey.TOOL_BRUSH.matches(keyCode, modifiers)) {
            this.tool = MapScreen.Tool.BRUSH;
         } else if (MapKey.TOOL_REGION.matches(keyCode, modifiers)) {
            this.tool = MapScreen.Tool.REGION;
         } else if (keyCode == InputConstants.KEY_EQUALS || keyCode == Keys.PAD_PLUS) {
            this.growSelection();
         } else if (keyCode == InputConstants.KEY_MINUS || keyCode == Keys.PAD_MINUS) {
            this.trimSelection();
         } else if (MapKey.BRUSH_SMALLER.matches(keyCode, modifiers)) {
            this.brush = Math.max(0, this.brush - 1);
         } else if (MapKey.BRUSH_BIGGER.matches(keyCode, modifiers)) {
            this.brush = Math.min(BRUSH_SIZES.length - 1, this.brush + 1);
         } else if (MapKey.FIND.matches(keyCode, modifiers)) {
            this.openFilter();
         } else if (MapKey.DELETE.matches(keyCode, modifiers)) {
            this.plan(ChunkEdits.Kind.DELETE);
         } else {
            return super.onKey(keyCode, scanCode, modifiers);
         }

         return true;
      } else {
         return true;
      }
   }

   protected boolean onKeyRelease(int keyCode, int scanCode, int modifiers) {
      if (keyCode == InputConstants.KEY_SPACE) {
         this.space = false;
      }

      return super.onKeyRelease(keyCode, scanCode, modifiers);
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   private enum Drag {
      NONE,

      CAVE_Y,
      PAN,
      SELECT,
      UNSELECT,
      PAINT,
      ERASE,
      REGION_SELECT,
      REGION_UNSELECT;
   }

   Object[] heldTile(int rx, int rz) {
      MapScreen.Held h = this.tiles.get(WorldLayout.key(rx, rz));
      if (h == null) {
         Tex backup = this.backups.get(WorldLayout.key(rx, rz));
         return backup == null ? null : new Object[]{backup, backup.width()};
      }

      return new Object[]{h.id(), h.size()};
   }

   WorldMap.Index indexNow() {
      return this.index;
   }

   ChunkSelection selectionIn(String dimension) {
      return this.selections.computeIfAbsent(dimension, k -> new ChunkSelection());
   }

   void reread(Set<Long> regions) {
      WorldMap.Index i = this.index;
      if (i == null || this.map == null || this.map.fromLiveMap()) {
         return;
      }

      int mine = this.run;
      this.submit(() -> {
         WorldMap.Index fresh = GenerateView.reread(i, regions);
         this.mc.execute(() -> {
            if (mine == this.run && this.index == i) {
               this.index = fresh;
               this.tiles.replaceAll((k, h) -> regions.contains(k) ? new MapScreen.Held(h.id(), h.level(), h.size(), true) : h);
            }
         });
      });
   }

   int liveStyle() {
      return this.map == null ? -1 : this.map.style();
   }

   boolean liveCave() {
      return this.caveY != Integer.MIN_VALUE;
   }

   LiveTiles tourLive() {
      return this.liveTiles;
   }

   GenerateView tourGenerate() {
      return this.generate;
   }

   int[] tourMiddleChunk() {
      return new int[]{Math.floorDiv((int)Math.floor(this.cx), 16), Math.floorDiv((int)Math.floor(this.cz), 16)};
   }

   public String shownDimension() {
      return this.dimension;
   }

   ChunkSelection guardedNow() {
      return this.guarded();
   }

   private record Held(Tex id, int level, int size, boolean stale) {
   }

   private static final class Job {
      volatile String text;
      volatile int total;
      final boolean canCancel;
      volatile int done;
      volatile boolean cancelled;

      volatile java.util.function.IntSupplier progress;

      Job(String text, int total, boolean canCancel) {
         this.text = text;
         this.total = total;
         this.canCancel = canCancel;
      }
   }

   private enum Menu {
      NONE,
      PICK,
      CONTEXT,
      DIMENSION,
      VIEW,
      MORE,
      SELECTION;
   }

   private enum View {
      TERRAIN,
      TIME_PLAYED,
      LAST_CHANGED,
      CHESTS,
      MOBS,
      DATA_VERSION,
      STATUS,
      SIZE,

      CUSTOM;
   }

   private static final List<String> STATUS_ORDER = List.of(
      "empty", "structure_starts", "structure_references", "biomes", "noise", "surface", "carvers", "features", "initialize_light", "light", "spawn", "full"
   );

   private record Painted(Tex id, Object facts, MapScreen.View view) {
   }

   private enum Tool {
      HAND,

      CHUNKS,
      SELECT,
      BRUSH,
      REGION;
   }
}
