package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.engine.WorldLock;

import dev.chunkatlas.engine.ChunkCopy;
import dev.chunkatlas.engine.ChunkEdits;
import dev.chunkatlas.engine.Message;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class ConfirmEditScreen extends AtlasScreen {
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final Path home;
   private final Path world;
   private final ChunkEdits.Plan plan;
   private final ChunkEdits.Edit undone;
   private String heading;

   private ConfirmEditScreen.Also also;
   private final List<ConfirmEditScreen.Line> lines = new ArrayList<>();
   private final List<Ui.Hit> hits = new ArrayList<>();
   private boolean running;
   private boolean back;
   private String problem;
   private volatile int done;
   private volatile int total;

   private Supplier<ChunkEdits.Plan> replan;

   private volatile ChunkEdits.Plan applied;

   private boolean backup = dev.chunkatlas.Settings.backups();

   private Aside aside;
   private boolean kept;

   private ConfirmEditScreen(MapScreen map, Path home, Path world, ChunkEdits.Plan plan, ChunkEdits.Edit undone, int worldVersion, boolean playerInside) {
      super(
         Component.translatable(
            plan == null
               ? (undone == null ? "chunkatlas.fields.title" : "chunkatlas.confirm.undo.title")
               : (plan.kind() == ChunkEdits.Kind.COPY ? "chunkatlas.copy.title" : (plan.kind() == ChunkEdits.Kind.DELETE_MOBS ? "chunkatlas.confirm.mobs.title" : "chunkatlas.confirm.delete.title"))
         )
      );
      this.map = map;
      this.home = home;
      this.world = world;
      this.plan = plan;
      this.undone = undone;
      if (plan != null && plan.kind() == ChunkEdits.Kind.COPY) {
         this.heading = copyFacts(plan, this.lines);
      } else if (plan != null && plan.kind() == ChunkEdits.Kind.DELETE_MOBS) {
         this.heading = Ui.plural("chunkatlas.confirm.mobs.heading", plan.chunks());
         this.fact("chunkatlas.confirm.fact.dimension", Ui.dimension(plan.dimension()));
         this.fact("chunkatlas.confirm.fact.entity_files", Ui.count(plan.otherFiles()));
         this.note(Ui.t("chunkatlas.confirm.mobs.gone"));
         this.skippedRegions(plan);
      } else if (plan != null) {
         this.heading = plan.kind() == ChunkEdits.Kind.DELETE
            ? Ui.plural("chunkatlas.confirm.delete.heading", plan.chunks())
            : Ui.plural("chunkatlas.confirm.keep_only.heading", plan.kept(), Ui.count(plan.chunks()));
         this.fact("chunkatlas.confirm.fact.dimension", Ui.dimension(plan.dimension()));
         this.fact("chunkatlas.confirm.fact.region_files", Ui.count(plan.regionFiles()));
         if (plan.otherFiles() > 0) {
            this.fact("chunkatlas.confirm.fact.entity_files", Ui.count(plan.otherFiles()));
         }

         if (plan.freed() > 0L) {
            this.fact("chunkatlas.confirm.fact.freed", Ui.size(plan.freed()));
         }

         this.skippedRegions(plan);
      } else if (undone == null) {
         this.heading = "";
      } else {
         this.heading = Ui.t("chunkatlas.confirm.undo.heading");
         this.note(Ui.plural(undoKey(undone, "what"), undone.chunks(), where(undone)));
         String when = Ui.ago(undone.createdMillis() / 1000L);
         if (!when.isEmpty()) {
            this.fact("chunkatlas.confirm.fact.when", when);
         }
      }

      if (map.live() != null) {
         if (playerInside && !this.mobs() && plan != null && plan.kind() != ChunkEdits.Kind.COPY) {
            this.warn(Ui.t("chunkatlas.confirm.player_inside"));
         }
      } else if (worldVersion < 2566) {
         this.warn(Ui.t("chunkatlas.confirm.old_world.known"));
      }
   }

   private void fact(String labelKey, String value) {
      this.lines.add(new ConfirmEditScreen.Line(Ui.t(labelKey), value, ConfirmEditScreen.Kind.FACT));
   }

   private void note(String text) {
      this.lines.add(new ConfirmEditScreen.Line("", text, ConfirmEditScreen.Kind.NOTE));
   }

   private void warn(String text) {
      this.lines.add(new ConfirmEditScreen.Line("", text, ConfirmEditScreen.Kind.WARN));
   }

   private void skippedRegions(ChunkEdits.Plan plan) {
      if (plan.unreadable() > 0) {
         this.warn(Ui.plural("chunkatlas.confirm.delete.unreadable", plan.unreadable()));
      }

      if (plan.away() > 0) {
         this.warn(Ui.plural("chunkatlas.confirm.delete.away", plan.away()));
      }
   }

   static ConfirmEditScreen edit(
      MapScreen map, Path home, Path world, ChunkEdits.Plan plan, Supplier<ChunkEdits.Plan> replan, int worldVersion, boolean playerInside
   ) {
      return edit(map, home, world, plan, replan, worldVersion, playerInside, 0);
   }

   static ConfirmEditScreen edit(
      MapScreen map, Path home, Path world, ChunkEdits.Plan plan, Supplier<ChunkEdits.Plan> replan, int worldVersion, boolean playerInside, int protectedSkipped
   ) {
      ConfirmEditScreen screen = new ConfirmEditScreen(map, home, world, plan, null, worldVersion, playerInside);
      screen.replan = replan;
      if (protectedSkipped > 0) {
         int at = (int)screen.lines.stream().filter(l -> l.kind() == ConfirmEditScreen.Kind.FACT).count();
         screen.lines.add(at, new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.protected"), Ui.count(protectedSkipped), ConfirmEditScreen.Kind.FACT));
      }
      return screen;
   }

   static ConfirmEditScreen copy(MapScreen map, Path home, Path world, ChunkEdits.Plan plan, Supplier<ChunkEdits.Plan> replan, int worldVersion) {
      return edit(map, home, world, plan, replan, worldVersion, false);
   }

   private static String copyFacts(ChunkEdits.Plan plan, List<ConfirmEditScreen.Line> lines) {
      ChunkCopy.Details d = plan.copy();
      if (d.fields()) {
         lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.dimension"), Ui.dimension(plan.dimension()), ConfirmEditScreen.Kind.FACT));
         if (d.protectedSkipped() > 0) {
            lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.protected"), Ui.count(d.protectedSkipped()), ConfirmEditScreen.Kind.FACT));
         }

         blendFact(d, lines);
         return Ui.plural("chunkatlas.fields.confirm.heading", plan.chunks());
      }

      lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.from"), d.sourceName() + " · " + Ui.dimension(d.sourceDimension()), ConfirmEditScreen.Kind.FACT));
      lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.into"), d.destName() + " · " + Ui.dimension(plan.dimension()), ConfirmEditScreen.Kind.FACT));
      if (d.replaced() > 0) {
         lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.replaced"), Ui.count(d.replaced()), ConfirmEditScreen.Kind.FACT));
      }

      if (d.protectedSkipped() > 0) {
         lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.protected"), Ui.count(d.protectedSkipped()), ConfirmEditScreen.Kind.FACT));
      }

      if (d.moved()) {
         lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.offset"), Ui.count(d.dx()) + ", " + Ui.count(d.dz()), ConfirmEditScreen.Kind.FACT));
      }

      if (d.lifted()) {
         String way = d.dy() > 0 ? "chunkatlas.copy.confirm.lifted.up" : "chunkatlas.copy.confirm.lifted.down";
         lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.height"), Ui.plural(way, Math.abs(d.dy())), ConfirmEditScreen.Kind.FACT));
      }

      if (d.moved() && !dev.chunkatlas.engine.ChunkCopy.keepStructures() || d.lifted()) {
         lines.add(new ConfirmEditScreen.Line("", Ui.t("chunkatlas.copy.confirm.structures_dropped"), ConfirmEditScreen.Kind.WARN));
      }

      blendFact(d, lines);

      if (d.olderSource()) {
         lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.source_version"), Ui.version(d.sourceVersion()), ConfirmEditScreen.Kind.FACT));
      }

      if (d.tooBig() > 0) {
         lines.add(new ConfirmEditScreen.Line("", Ui.plural("chunkatlas.copy.confirm.too_big", d.tooBig()), ConfirmEditScreen.Kind.WARN));
      }

      if (plan.unreadable() + plan.away() > 0) {
         lines.add(new ConfirmEditScreen.Line("", Ui.plural("chunkatlas.copy.confirm.unreadable", plan.unreadable() + plan.away()), ConfirmEditScreen.Kind.WARN));
      }

      return Ui.plural("chunkatlas.copy.confirm.heading", plan.chunks());
   }

   private static void blendFact(ChunkCopy.Details d, List<ConfirmEditScreen.Line> lines) {
      if (d.blendRing() > 0) {
         String ring = Ui.plural("chunkatlas.copy.confirm.blend", d.blendRing(), d.blend());
         String band = d.blendBand() > 0 ? " · " + Ui.plural("chunkatlas.copy.confirm.blend_band", d.blendBand()) : "";
         lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.blend"), ring + band, ConfirmEditScreen.Kind.FACT));
      } else if (d.blend() > 0) {
         lines.add(new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.blend"), Ui.t("chunkatlas.copy.confirm.blend_edges"), ConfirmEditScreen.Kind.FACT));
      }
   }

   private boolean copying() {
      return this.plan != null && this.plan.kind() == ChunkEdits.Kind.COPY;
   }

   record Also(String heading, String button, Supplier<String> done, String label, String value, String dimension, dev.chunkatlas.engine.ChunkSelection chunks,
      java.util.function.Function<ChunkEdits.Context, Message> write) {
   }

   static ConfirmEditScreen also(MapScreen map, Path home, Path world, ConfirmEditScreen.Also also) {
      ConfirmEditScreen screen = new ConfirmEditScreen(map, home, world, null, null, Integer.MAX_VALUE, false);
      screen.heading = also.heading();
      screen.fact("chunkatlas.confirm.fact.dimension", Ui.dimension(also.dimension()));
      return screen.with(also);
   }

   ConfirmEditScreen reading(Aside aside) {
      this.aside = aside;
      return this;
   }

   private void dropAside() {
      if (this.aside != null) {
         this.aside.drop();
      }
   }

   ConfirmEditScreen with(ConfirmEditScreen.Also also) {
      if (also != null) {
         this.also = also;
         this.lines.add(new ConfirmEditScreen.Line(also.label(), also.value(), ConfirmEditScreen.Kind.FACT));
      }

      return this;
   }

   private boolean alone() {
      return this.plan == null && this.undone == null;
   }

   static ConfirmEditScreen undo(MapScreen map, Path home, Path world, ChunkEdits.Edit edit, int worldVersion) {
      return new ConfirmEditScreen(map, home, world, null, edit, worldVersion, false);
   }

   private boolean mobs() {
      return this.plan != null && this.plan.kind() == ChunkEdits.Kind.DELETE_MOBS;
   }

   private static String undoKey(ChunkEdits.Edit edit, String part) {
      if (edit.kind() == ChunkEdits.Kind.COPY) {
         return "chunkatlas.copy.undo." + part;
      }

      return edit.kind() == ChunkEdits.Kind.DELETE_MOBS ? "chunkatlas.confirm.undo.mobs_" + part : "chunkatlas.confirm.undo." + part;
   }

   private static String where(ChunkEdits.Edit edit) {
      return edit.dimension() == null || edit.dimension().isEmpty() ? edit.label() : Ui.dimension(edit.dimension());
   }

   private ChunkEdits.Result work(ChunkEdits.Context ctx, ChunkEdits.Progress progress) {
      try {
         return this.make(ctx, progress);
      } finally {
         this.dropAside();
      }
   }

   private ChunkEdits.Result make(ChunkEdits.Context ctx, ChunkEdits.Progress progress) {
      if (this.alone()) {
         return this.alsoAfter(ctx, new ChunkEdits.Result(true, ChunkEdits.Refusal.NONE, null, null));
      }

      if (this.plan == null) {
         return ChunkEdits.undo(this.world, ctx, progress);
      }

      ChunkEdits.Plan now = this.plan;
      if (this.replan != null) {
         now = this.replan.get();
         if (now == null) {
            return new ChunkEdits.Result(false, ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.map.files_unreadable"), null);
         }

         if (now.isEmpty()) {
            return new ChunkEdits.Result(
               false,
               ChunkEdits.Refusal.FAILED,
               Message.of(this.copying() ? "chunkatlas.copy.nothing" : (this.mobs() ? "chunkatlas.map.plan.no_mobs" : "chunkatlas.map.plan.nothing_to_delete")),
               null
            );
         }
      }

      this.applied = now;
      if (now.kind() == ChunkEdits.Kind.COPY && now.copy() != null) {
         ChunkEdits.Plan copy = now;
         return this.alsoAfter(ctx, WorldLock.with(copy.copy().sourceWorld(), held -> ChunkEdits.apply(copy, ctx.withSource(held), progress))
            .orElse(new ChunkEdits.Result(false, ChunkEdits.Refusal.NOT_LOCKED, Message.of("chunkatlas.copy.source_open"), null)));
      }

      return this.alsoAfter(ctx, ChunkEdits.apply(now, ctx, progress, this.backup));
   }

   private ChunkEdits.Result alsoAfter(ChunkEdits.Context ctx, ChunkEdits.Result done) {
      if (this.also == null || !done.ok()) {
         return done;
      }

      Message why = this.also.write().apply(ctx);
      return why == null ? done : new ChunkEdits.Result(false, ChunkEdits.Refusal.FAILED, why, done.editId());
   }

   private String success() {
      if (this.alone()) {
         return this.also.done().get();
      }

      if (this.plan == null) {
         return Ui.plural(
            this.undone.kind() == ChunkEdits.Kind.COPY
               ? "chunkatlas.copy.result.undone"
               : (this.undone.kind() == ChunkEdits.Kind.DELETE_MOBS ? "chunkatlas.result.mobs_put_back" : "chunkatlas.result.put_back"),
            this.undone.chunks(),
            where(this.undone)
         );
      }

      ChunkEdits.Plan done = this.applied != null ? this.applied : this.plan;
      String where = Ui.dimension(done.dimension());
      if (done.kind() == ChunkEdits.Kind.DELETE_MOBS) {
         return Ui.plural("chunkatlas.result.mobs_removed", done.chunks(), where);
      }

      if (done.kind() == ChunkEdits.Kind.COPY && done.copy() != null && done.copy().fields()) {
         return Ui.plural("chunkatlas.fields.done", done.chunks());
      }

      if (done.kind() == ChunkEdits.Kind.COPY) {
         return Ui.plural("chunkatlas.copy.result.done", done.chunks(), done.copy() == null ? where : done.copy().destName());
      }

      return done.freed() > 0L
         ? Ui.plural("chunkatlas.result.deleted_smaller", done.chunks(), where, Ui.size(done.freed()))
         : Ui.plural("chunkatlas.result.deleted", done.chunks(), where);
   }

   void go() {
      if (!this.running && this.map.live() != null && this.plan != null) {
         if (this.aside != null && !this.kept) {
            this.running = true;
            this.problem = null;
            Thread.ofVirtual().start(() -> {
               Message why = this.aside.keep();
               this.mc.execute(() -> {
                  this.running = false;
                  if (why != null) {
                     this.problem = Ui.t(why);
                  } else {
                     this.kept = true;
                     this.go();
                  }
               });
            });
            return;
         }

         if (this.map.queue(this.plan, this::work, this.backup || this.copying(), this::dropAside)) {
            this.back = true;
            Game.open(this.mc, this.map);
            return;
         }
      }

      if (!this.running && this.map.live() != null) {
         this.running = true;
         this.back = true;
         this.map.dispose();
         LeaveAndEditScreen.start(
            this.home,
            this.world,
            this.map.live().levelId(),
            Ui.t(
               this.plan == null
                  ? "chunkatlas.leave.undoing"
                  : (this.copying() ? "chunkatlas.copy.leave" : (this.mobs() ? "chunkatlas.leave.removing_mobs" : "chunkatlas.leave.deleting"))
            ),
            this.plan != null && (this.backup || this.copying()),
            this::work,
            result -> this.success()
         );
      } else if (!this.running) {
         this.running = true;
         this.problem = null;
         this.done = 0;
         this.total = 0;
         this.map.release();
         ChunkEdits.Progress progress = (n, of) -> {
            this.done = n;
            this.total = of;
         };
         Thread worker = new Thread(
            () -> {
               Optional<ChunkEdits.Result> answer;
               try {
                  answer = WorldLock.with(this.world, held -> {
                     return this.work(new ChunkEdits.Context(this.home, held), progress);
                  });
               } catch (RuntimeException e) {
                  answer = Optional.of(new ChunkEdits.Result(false, ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.edit.failed_partway"), null));
               }

               Optional<ChunkEdits.Result> a = answer;
               this.mc.execute(() -> this.finished(a));
            },
            "Chunk Atlas edit"
         );
         worker.setDaemon(false);
         worker.start();
      }
   }

   private void finished(Optional<ChunkEdits.Result> answer) {
      this.running = false;
      if (answer.isEmpty()) {
         this.problem = Ui.t("chunkatlas.confirm.world_open");
      } else {
         ChunkEdits.Result result = answer.get();
         String message;
         if (!result.ok()) {
            message = result.message() != null ? Ui.t(result.message()) : Ui.t("chunkatlas.edit.nothing_changed");
         } else {
            message = this.success();
         }

         this.map.edited(message, result.ok());
         if (Game.screen(this.mc) == this) {
            this.back = true;
            Game.open(this.mc, this.map);
         } else {
            this.map.dispose();
         }
      }
   }

   private void cancel() {
      if (!this.running) {
         this.dropAside();
         this.back = true;
         Game.open(this.mc, this.map);
      }
   }

   public void removed() {
      if (!this.back && !this.running) {
         this.dropAside();
         this.map.dispose();
      }
   }

   public void onClose() {
      this.cancel();
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public boolean isPauseScreen() {
      return true;
   }

   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      super.drawBackground(g, mouseX, mouseY, partial);
   }

   private static final int BUTTON_W = 110;
   private static final int THUMB_W = 150;
   private static final int THUMB_H = 100;

   private dev.chunkatlas.engine.ChunkSelection affected() {
      if (this.alone()) {
         return this.also.chunks();
      }

      dev.chunkatlas.engine.ChunkSelection sel = new dev.chunkatlas.engine.ChunkSelection();
      if (this.plan != null) {
         for (ChunkEdits.FilePlan f : this.plan.files()) {
            if ("region".equals(f.role())) {
               for (int i : f.indices()) {
                  sel.add(f.rx() * 32 + (i & 31), f.rz() * 32 + (i >> 5));
               }
            }
         }
      }

      return sel;
   }

   private void thumbnail(Gfx g, int x, int y, int w, int h, dev.chunkatlas.engine.ChunkSelection sel) {
      Thumbnail.draw(g, this.map, x, y, w, h, sel, this.copying() || this.alone() ? 0x8FB8FF : 0xFF6B6B, true);
   }

   private String regionNames() {
      if (this.plan == null) {
         return "";
      }

      java.util.TreeSet<String> names = new java.util.TreeSet<>();
      for (ChunkEdits.FilePlan f : this.plan.files()) {
         if ("region".equals(f.role())) {
            names.add("r." + f.rx() + "." + f.rz());
         }
      }

      List<String> list = new ArrayList<>(names);
      if (list.size() > 4) {
         return String.join(", ", list.subList(0, 3)) + " " + Ui.plural("chunkatlas.confirm.more_files", list.size() - 3);
      }

      return String.join(", ", list);
   }

   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      this.hits.clear();
      int cx = this.width / 2;
      boolean showBackup = this.plan != null && !this.copying();
      dev.chunkatlas.engine.ChunkSelection sel = this.affected();
      boolean thumb = !sel.isEmpty();
      int cardW = Math.min(this.width - 20, thumb ? 370 : 300);
      int textW = thumb ? cardW - THUMB_W - 48 : cardW - 32;
      List<ConfirmEditScreen.Line> facts = new ArrayList<>(this.lines.stream().filter(l -> l.kind() == ConfirmEditScreen.Kind.FACT).toList());
      if (this.plan != null && this.plan.regionFiles() > 0 && !this.copying()) {
         facts.removeIf(l -> l.label().equals(Ui.t("chunkatlas.confirm.fact.region_files")) || l.label().equals(Ui.t("chunkatlas.confirm.fact.entity_files")));
         facts.add(1, new ConfirmEditScreen.Line(Ui.t("chunkatlas.confirm.fact.region_files"), this.regionNames(), ConfirmEditScreen.Kind.FACT));
      }

      facts.removeIf(l -> l.label().equals(Ui.t("chunkatlas.confirm.fact.freed")));
      List<FormattedCharSequence> notes = new ArrayList<>();
      List<FormattedCharSequence> warns = new ArrayList<>();
      for (ConfirmEditScreen.Line l : this.lines) {
         if (l.kind() == ConfirmEditScreen.Kind.NOTE) {
            notes.addAll(this.font.split(Component.literal(l.text()), textW));
         } else if (l.kind() == ConfirmEditScreen.Kind.WARN) {
            warns.addAll(this.font.split(Component.literal(l.text()), textW));
         }
      }

      List<FormattedCharSequence> trouble = this.problem == null ? List.of() : this.font.split(Component.literal(this.problem), cardW - 32);
      int bodyH = notes.size() * 11 + (notes.isEmpty() ? 0 : 6) + facts.size() * 20 + warns.size() * 11 + (warns.isEmpty() ? 0 : 6);
      int body = Math.max(bodyH, thumb ? THUMB_H + 12 : 0);
      int h = 14 + 12 + 10 + body + (showBackup ? 16 : 0) + trouble.size() * 11 + 10 + 20 + 14;
      int cardX = cx - cardW / 2;
      int y = Math.max(10, (this.height - h) / 2);
      Glass.panel(g, cardX, y, cardW, h);
      g.fill(cardX, y, cardX + cardW, y + h, 0x66000000);
      y += 14;
      g.centered(this.font, Component.literal(this.heading).withStyle(net.minecraft.ChatFormatting.BOLD), cx, y, Ui.WHITE);
      y += 22;
      int left = cardX + 16;
      int textX = left;
      if (thumb) {
         this.thumbnail(g, left, y, THUMB_W, THUMB_H, sel);
         String what = Ui.chunks(sel.size());
         g.text(this.font, what, left, y + THUMB_H + 4, Ui.GREY);
         textX = left + THUMB_W + 16;
      }

      int ty = y;
      for (FormattedCharSequence line : notes) {
         g.text(this.font, line, textX, ty, Ui.GREY);
         ty += 11;
      }

      if (!notes.isEmpty()) {
         ty += 6;
      }

      for (ConfirmEditScreen.Line f : facts) {
         g.text(this.font, f.label(), textX, ty, Glass.NOTE);
         boolean kept = f.label().equals(Ui.t("chunkatlas.confirm.fact.protected"));
         g.text(this.font, this.font.plainSubstrByWidth(f.text(), textW), textX, ty + 10, kept ? Ui.YELLOW : Ui.WHITE);
         ty += 20;
      }

      for (FormattedCharSequence line : warns) {
         g.text(this.font, line, textX, ty, Ui.YELLOW);
         ty += 11;
      }

      y += body + 10;
      if (showBackup) {
         String label = this.plan.copyBytes() > 0L ? Ui.t("chunkatlas.confirm.backup.label", Ui.size(this.plan.copyBytes())) : Ui.t("chunkatlas.confirm.backup.label_plain");
         Glass.check(g, this.font, this.hits, left, y, label, this.backup, this.running ? null : () -> {
            this.backup = !this.backup;
            dev.chunkatlas.Settings.backups(this.backup);
         });
         y += 16;
      }

      for (FormattedCharSequence line : trouble) {
         g.text(this.font, line, left, y, Ui.RED);
         y += 11;
      }

      y += 10;
      int right = cardX + cardW - 16;
      if (this.running) {
         int of = this.total;
         int at = Math.min(this.done, of);
         String text = of > 0
            ? Ui.t(this.plan != null ? "chunkatlas.confirm.progress.deleting" : "chunkatlas.confirm.progress.undoing", Ui.count(at), Ui.count(of))
            : Ui.t("chunkatlas.confirm.progress.ready");
         g.text(this.font, text, left, y + 2, Ui.LIGHT_BLUE);
         g.fill(left, y + 14, right, y + 16, 0xFF262A30);
         if (of > 0) {
            g.fill(left, y + 14, left + (int)((long)(right - left) * at / of), y + 16, Ui.LIGHT_BLUE);
         }
      } else {
         String yes = this.alone()
            ? this.also.button()
            : this.plan == null
            ? Ui.t("chunkatlas.confirm.undo.button")
            : (this.copying() ? Ui.plural(this.plan.copy() != null && this.plan.copy().fields() ? "chunkatlas.fields.button" : "chunkatlas.copy.button", this.plan.chunks())
               : (this.mobs() ? Ui.t("chunkatlas.confirm.mobs.button") : Ui.plural("chunkatlas.confirm.delete.button", this.plan.chunks())));
         String no = Ui.t("chunkatlas.confirm.cancel");
         int yw = Math.max(BUTTON_W, this.font.width(yes) + 24);
         int nw = Math.max(80, this.font.width(no) + 24);
         boolean destructive = this.plan != null && !this.copying();
         Glass.button(g, this.font, this.hits, right - yw, y, yw, 20, yes, destructive ? Glass.DANGER : (this.copying() ? Glass.GO : Glass.ACCENT), mouseX, mouseY, this::go);
         Glass.button(g, this.font, this.hits, right - yw - 8 - nw, y, nw, 20, no, Ui.WHITE, mouseX, mouseY, this::cancel);
      }

      super.draw(g, mouseX, mouseY, partial);
   }

   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (MapScreen.tourDeaf) {
         return true;
      }

      if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
         for (int n = this.hits.size() - 1; n >= 0; n--) {
            if (this.hits.get(n).has(mouseX, mouseY)) {
               this.hits.get(n).action().run();
               return true;
            }
         }
      }

      return super.onClick(mouseX, mouseY, mouseButton, doubleClick);
   }

   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (MapScreen.tourDeaf) {
         return true;
      } else if (Keys.escape(keyCode)) {
         this.cancel();
         return true;
      } else {
         return super.onKey(keyCode, scanCode, modifiers);
      }
   }

   private enum Kind {
      FACT,
      NOTE,
      WARN;
   }

   private record Line(String label, String text, ConfirmEditScreen.Kind kind) {
   }
}
