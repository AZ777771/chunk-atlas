package dev.chunkatlas.gui;

import dev.chunkatlas.ServerLink;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.ForcedChunks;
import dev.chunkatlas.engine.Message;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.net.ServerLoads;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;

final class ForceLoad {
   static final int MOST = 256;

   private ForceLoad() {
   }

   static boolean onServer() {
      return ServerLink.canEdit() && ServerLink.hasLoads();
   }

   static ConfirmEditScreen.Also change(MapScreen map, WorldLayout.Dimension d, ChunkSelection chunks, boolean on, int dataVersion) {
      Minecraft mc = Minecraft.getInstance();

      String done = on ? "chunkatlas.fields.force_load.done" : "chunkatlas.fields.force_load.undone";
      IntegratedServer server = mc.getSingleplayerServer();
      if (map.live() != null && server != null) {
         server.execute(() -> {
            ServerLevel level = ServerLoads.level(server, d.id());
            int changed = level == null ? -1 : ServerLoads.force(level, chunks, on);
            mc.execute(() -> map.seedTell(changed < 0 ? Ui.t("chunkatlas.fields.failed") : Ui.plural(done, changed), changed < 0 ? Ui.YELLOW : Ui.GREEN));
         });
         return null;
      }

      if (map.live() != null) {
         boolean asked = ServerLink.force(
            d.id(), chunks, on, reply -> map.seedTell(reply.ok() ? Ui.plural(done, reply.chunks()) : Ui.t(reply.key(), Ui.count(reply.chunks())), reply.ok() ? Ui.GREEN : Ui.YELLOW)
         );
         if (!asked) {
            map.seedTell(Ui.t("chunkatlas.map.layers.loaded.server"), Ui.YELLOW);
         }

         return null;
      }

      Path file = ForcedChunks.file(d);
      if (file == null) {
         map.seedTell(Ui.t("chunkatlas.fields.force_load.old_world"), Ui.YELLOW);
         return null;
      }

      int[] changed = {0};
      return new ConfirmEditScreen.Also(
         Ui.plural("chunkatlas.fields.confirm.heading", chunks.size()),
         Ui.plural("chunkatlas.fields.button", chunks.size()),
         () -> Ui.plural(done, changed[0]),
         Ui.t("chunkatlas.fields.force_load"),
         Ui.t(on ? "chunkatlas.fields.force_load.on" : "chunkatlas.fields.force_load.off"),
         d.id(),
         chunks,
         ctx -> {
            if (!ctx.lockHeld().getAsBoolean()) {
               return Message.of("chunkatlas.confirm.world_open");
            }

            try {
               changed[0] = ForcedChunks.write(file, chunks, on, dataVersion);
               return null;
            } catch (IOException | RuntimeException e) {
               return Message.of("chunkatlas.fields.force_load.failed");
            }
         }
      );
   }
}
