package dev.chunkatlas.gui;

import dev.chunkatlas.engine.WorldLock;

import dev.chunkatlas.InGame;
import dev.chunkatlas.engine.ChunkEdits;
import dev.chunkatlas.engine.Message;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

final class LeaveAndEditScreen extends AtlasScreen {
   private static final long WAIT_FOR_CLOSE_MILLIS = 60_000L;
   private final Minecraft mc = Minecraft.getInstance();
   private final Path home;
   private final Path world;
   private final String levelId;
   private final BiFunction<ChunkEdits.Context, ChunkEdits.Progress, ChunkEdits.Result> work;
   private final Function<ChunkEdits.Result, String> describe;
   private boolean offerUndo;
   private volatile String status;
   private volatile int done;
   private volatile int total;

   private LeaveAndEditScreen(
      Path home,
      Path world,
      String levelId,
      String doing,
      BiFunction<ChunkEdits.Context, ChunkEdits.Progress, ChunkEdits.Result> work,
      Function<ChunkEdits.Result, String> describe
   ) {
      super(Component.translatable("chunkatlas.leave.title"));
      this.home = home;
      this.world = world;
      this.levelId = levelId;
      this.work = work;
      this.describe = describe;
      this.status = doing;
   }

   static void start(
      Path home,
      Path world,
      String levelId,
      String doing,
      boolean offerUndo,
      BiFunction<ChunkEdits.Context, ChunkEdits.Progress, ChunkEdits.Result> work,
      Function<ChunkEdits.Result, String> describe
   ) {
      Minecraft mc = Minecraft.getInstance();
      LeaveAndEditScreen screen = new LeaveAndEditScreen(home, world, levelId, doing, work, describe);
      screen.offerUndo = offerUndo;
      if (mc.level != null) {
         Game.leaveLevel(mc.level);
      }

      mc.disconnect(screen, false);
      screen.begin();
   }

   private void begin() {
      Thread worker = new Thread(() -> {
         long until = System.currentTimeMillis() + WAIT_FOR_CLOSE_MILLIS;
         ChunkEdits.Progress progress = (n, of) -> {
            this.done = n;
            this.total = of;
         };
         Optional<ChunkEdits.Result> answer = Optional.empty();

         while (true) {
            try {
               answer = WorldLock.with(this.world, held -> this.work.apply(new ChunkEdits.Context(this.home, held), progress));
            } catch (RuntimeException e) {
               answer = Optional.of(new ChunkEdits.Result(false, ChunkEdits.Refusal.FAILED, Message.of("chunkatlas.edit.failed_partway"), null));
            }

            if (answer.isPresent() || System.currentTimeMillis() > until) {
               break;
            }

            this.status = Ui.t("chunkatlas.leave.waiting");

            try {
               Thread.sleep(250L);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               break;
            }
         }

         Optional<ChunkEdits.Result> a = answer;
         this.mc.execute(() -> this.finish(a));
      }, "Chunk Atlas edit");
      worker.setDaemon(false);
      worker.start();
   }

   private void finish(Optional<ChunkEdits.Result> answer) {
      Component message;
      if (answer.isEmpty()) {
         message = Component.translatable("chunkatlas.leave.never_closed").withStyle(ChatFormatting.GOLD);
      } else if (!answer.get().ok()) {
         Message why = answer.get().message();
         message = Component.literal(why != null ? Ui.t(why) : Ui.t("chunkatlas.edit.nothing_changed")).withStyle(ChatFormatting.GOLD);
      } else {
         message = Component.literal(this.describe.apply(answer.get())).withStyle(ChatFormatting.GREEN);
         if (this.offerUndo) {
            message = message.copy().append(" ").append(Component.translatable("chunkatlas.leave.undo_hint", InGame.keyName()).withStyle(ChatFormatting.GRAY));
         }
      }

      InGame.tellLater(Component.literal("[Chunk Atlas] ").withStyle(ChatFormatting.AQUA).append(message));
      this.status = Ui.t("chunkatlas.leave.returning");
      this.mc.createWorldOpenFlows().openWorld(this.levelId, () -> Game.open(this.mc, new TitleScreen()));
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      super.drawBackground(g, mouseX, mouseY, partial);
   }

   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      int y = this.height / 2 - 14;
      g.centered(this.font, this.status, this.width / 2, y, Ui.WHITE);
      int of = this.total;
      if (of > 0) {
         int at = Math.min(this.done, of);
         int w = Math.min(240, this.width - 40);
         int x = (this.width - w) / 2;
         g.fill(x, y + 16, x + w, y + 18, 0xFF262A30);
         g.fill(x, y + 16, x + (int)((long)w * at / of), y + 18, Ui.LIGHT_BLUE);
         g.centered(this.font, Ui.t("chunkatlas.leave.files", Ui.count(at), Ui.count(of)), this.width / 2, y + 24, Ui.GREY);
      }

      super.draw(g, mouseX, mouseY, partial);
   }
}
