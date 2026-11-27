package dev.chunkatlas.gui;

import dev.chunkatlas.InGame;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Ids;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

final class SeedTour {
   private interface Step {
      boolean run(Minecraft mc, long since);
   }

   private final Consumer<String> say;
   private final List<Step> steps = new ArrayList<>();
   private int at;
   private long started = System.currentTimeMillis();
   private int titleTicks;
   private Screen title;
   private MapScreen map;
   private String folder = "";

   SeedTour(Map<String, Path> asked, Consumer<String> say) {
      this.say = say;
      this.steps.add((mc, since) -> {
         if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++this.titleTicks >= 60) {
            this.title = t;
            return true;
         }

         if (since > 20000L && since % 10000L < 60L) {
            this.say("waiting for the title screen; showing " + Game.screen(mc) + ", overlay " + Game.busy(mc));
         }

         return false;
      });
      this.world(asked.get("seedtour").toString(), null);
      this.seedOn();
      this.partials();
      this.structureList();
      this.biomeHighlight();
      Path makes = asked.get("seedmake");
      if (makes != null) {
         for (String preset : makes.toString().split(",")) {
            if (!preset.isBlank()) {
               this.world("seed-" + preset.strip(), preset.strip());
               this.seedOn();
               this.look();
            }
         }
      }

      Path also = asked.get("seedalso");
      if (also != null) {
         this.world(also.toString(), null);
         this.seedOn();
         this.look();
      }
   }

   private void seedOn() {
      this.act(0L, mc -> {
         if (this.map != null && !this.map.seedLayer().shown()) {
            this.map.seedLayer().toggle();
         }
      });
      this.steps.add((mc, since) -> {
         if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
            if (since > 800L) {
               this.shot(mc, "warning");
               w.show();
               return true;
            }

            return false;
         }

         if (since > 1500L) {
            this.say("seed map shown: " + (this.map != null && this.map.seedLayer().shown()) + "; " + (this.map == null ? "" : this.map.seedLayer().tourState()));
            return true;
         }

         return false;
      });
   }

   private void partials() {
      this.act(0L, mc -> {
         this.centre(mc);
         this.map.tourZoom(0.125);
      });
      this.ready("partial-far", mc -> {
         int[] spot = this.map.seedLayer().tourPartialSpot(mc.player.getBlockX(), mc.player.getBlockZ());
         this.say("nearest partly generated chunk: " + (spot == null ? "none" : spot[0] + ", " + spot[1]));
         if (spot != null) {
            this.map.seedCentre(spot[0], spot[1]);
            this.say("coverage there: " + this.map.seedLayer().tourCoverage(spot[0], spot[1]));
            this.map.tourZoom(4.0);
         }
      });
      this.ready("partial-on", mc -> this.map.seedLayer().toggle());
      this.ready("partial-off", mc -> this.map.seedLayer().toggle());
      this.ready("partial-on-again", mc -> this.map.tourZoom(0.0625 / this.map.seedZoom()));
   }

   private void structureList() {
      this.ready("far-before", mc -> this.map.seedLayer().openTypes());
      this.act(1200L, mc -> {
         this.shot(mc, "types");
         boolean before = this.map.seedLayer().kindShown("minecraft:village");
         this.map.seedLayer().kindShown("minecraft:village", false);
         this.say("village unticked: " + before + " -> " + this.map.seedLayer().kindShown("minecraft:village"));
      });
      this.act(600L, mc -> {
         this.shot(mc, "types-village-off");
         this.map.seedPanel().tourFeatures(true);
      });
      this.act(600L, mc -> {
         this.setFeatures(false);
         this.say("features None: desert well " + this.map.seedLayer().kindShown("minecraft:desert_well") + ", geode " + this.map.seedLayer().kindShown("minecraft:amethyst_geode"));
      });
      this.act(600L, mc -> {
         this.shot(mc, "types-features-none");
         this.setFeatures(true);
         this.say("features All: desert well " + this.map.seedLayer().kindShown("minecraft:desert_well") + ", geode " + this.map.seedLayer().kindShown("minecraft:amethyst_geode"));
      });
      this.act(600L, mc -> {
         this.shot(mc, "types-features-all");
         this.map.seedPanel().tourFeatures(false);
         this.map.tourSeedPanel(false);
      });
      this.ready("far-village-off", mc -> this.map.seedLayer().openTypes());
      this.act(1000L, mc -> {
         for (String kind : this.map.seedLayer().kinds()) {
            if (!dev.chunkatlas.seed.SeedSampler.isFeature(kind)) {
               this.map.seedLayer().kindShown(kind, true);
            }
         }

         this.say("structures All: village " + this.map.seedLayer().kindShown("minecraft:village"));
         this.map.tourSeedPanel(false);
      });
      this.ready("far-village-back", mc -> {});
   }

   private void setFeatures(boolean on) {
      for (String kind : this.map.seedLayer().kinds()) {
         if (dev.chunkatlas.seed.SeedSampler.isFeature(kind)) {
            this.map.seedLayer().kindShown(kind, on);
         }
      }
   }

   private void biomeHighlight() {
      this.act(0L, mc -> this.map.seedLayer().openBiomes());
      this.act(1200L, mc -> {
         this.shot(mc, "biomes");
         this.map.seedPanel().tourSearch("plains");
      });
      this.act(800L, mc -> {
         this.shot(mc, "biomes-search");
         SeedPanel s = this.map.seedPanel();
         this.say("biomes matching 'plains': " + s.tourRows());
         boolean taken = s.tourClick(0, false);
         this.say("highlight clicked (taken " + taken + ")");
      });
      this.ready("highlight-far", mc -> {
         this.centre(mc);
         this.map.tourZoom(16.0);
      });
      this.ready("highlight-near", mc -> this.map.tourSeedPanel(true));
      this.act(800L, mc -> {
         this.shot(mc, "highlight-menu");
         this.map.seedLayer().openBiomes();
      });
      this.act(1000L, mc -> this.map.seedPanel().tourSearch("jungle"));
      this.act(800L, mc -> {
         SeedPanel s = this.map.seedPanel();
         this.map.seedLayer().highlight(-1);
         boolean taken = s.tourClick(0, false) && s.tourClick(0, true);
         this.say("find nearest jungle clicked (taken " + taken + ")");
      });
      this.ready("highlight-found", mc -> {
         this.say("after find: " + this.map.tourState());
         this.map.seedLayer().highlight(-1);
         this.map.tourSeedPanel(false);
      });
      this.ready("highlight-cleared", mc -> {});
   }

   private void look() {
      this.act(0L, mc -> {
         if (this.map != null) {
            this.centre(mc);
            this.map.tourZoom(2.0);
         }
      });
      this.ready("near", mc -> {
         if (this.map != null) {
            this.say("coverage: " + this.map.seedLayer().tourCoverage(mc.player.getBlockX(), mc.player.getBlockZ()));
            this.map.tourZoom(0.0625);
         }
      });
      this.ready("far", mc -> {
         int[] spot = this.map == null ? null : this.map.seedLayer().tourPartialSpot(mc.player.getBlockX(), mc.player.getBlockZ());
         this.say("nearest partly generated chunk: " + (spot == null ? "none" : spot[0] + ", " + spot[1]));
         if (spot != null) {
            this.map.seedCentre(spot[0], spot[1]);

            this.map.tourZoom(4.0);
         }
      });
      this.ready("partial", mc -> {
         if (this.map != null) {
            this.centre(mc);
            this.map.tourZoom(0.0625 / this.map.seedZoom());
         }
      });
      this.ready("far-again", mc -> {
         if (this.map != null && this.map.seedLayer().shown()) {
            String f = this.folder;
            this.map.seedLayer().tourCheck().whenComplete((text, error) -> this.say(f + ": " + (error != null ? "seed check failed: " + error : text)));
         }
      });
      this.act(12000L, mc -> {});
   }

   private void world(String folder, String preset) {
      this.steps.add((mc, since) -> {
         if (mc.level != null) {
            this.map = null;
            Game.leaveLevel(mc.level);
            mc.disconnect(new TitleScreen(), false);
            return false;
         }

         if (!(Game.screen(mc) instanceof TitleScreen) || since < 1000L) {
            return false;
         }

         this.folder = folder;
         Path saves = mc.gameDirectory.toPath().resolve("saves");
         if (Files.isDirectory(saves.resolve(folder))) {
            this.say("opening " + folder);
            mc.createWorldOpenFlows().openWorld(folder, () -> this.say("could not open " + folder));
         } else if (preset != null) {
            this.say("creating " + folder + " as " + preset);
            Game.createWorld(mc, this.title);
         } else {
            this.say("no world " + folder);
         }

         return true;
      });
      if (preset != null) {
         this.steps.add((mc, since) -> {
            if (this.inWorld(mc)) {
               return true;
            }

            if (Game.screen(mc) instanceof CreateWorldScreen create && since > 1500L) {
               WorldCreationUiState ui = create.getUiState();
               ui.setName(folder);
               ui.setSeed("chunkatlas");
               ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
               ui.setAllowCommands(true);
               ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> key = Ids.key(Registries.WORLD_PRESET, preset);
               boolean set = false;
               List<WorldCreationUiState.WorldTypeEntry> all = new ArrayList<>(ui.getNormalPresetList());
               all.addAll(ui.getAltPresetList());
               for (WorldCreationUiState.WorldTypeEntry e : all) {
                  if (e.preset() != null && e.preset().is(key)) {
                     ui.setWorldType(e);
                     set = true;
                  }
               }

               this.say("world type " + preset + (set ? " set" : " not found") + ", folder " + ui.getTargetFolder());

               try {
                  create.onCreate();
               } catch (RuntimeException e) {
                  this.say("could not create: " + e);
               }

               return true;
            }

            return since > 60000L;
         });
      }

      this.steps.add((mc, since) -> {
         if (this.inWorld(mc) && since > 5000L) {
            InGame.open(mc, null);
            if (Game.screen(mc) instanceof MapScreen m) {
               this.map = m;
               this.say("map open in " + folder + " at " + mc.player.blockPosition());
               return true;
            }
         }

         if (since > 240000L) {
            this.say("never got into " + folder);
            return true;
         }

         return false;
      });
      this.steps.add((mc, since) -> this.map == null || this.drawn(since, 1500L, 40000L));
   }

   private void act(long wait, Consumer<Minecraft> action) {
      this.steps.add((mc, since) -> {
         if (since < wait) {
            return false;
         }

         action.accept(mc);
         return true;
      });
   }

   private void ready(String name, Consumer<Minecraft> after) {
      this.steps.add((mc, since) -> {
         if (this.map != null && !this.seedReady(since, 2500L, 60000L)) {
            return false;
         }

         this.shot(mc, name);
         after.accept(mc);
         return true;
      });
   }

   private void centre(Minecraft mc) {
      if (mc.player != null && this.map != null) {
         this.map.seedCentre(mc.player.getX(), mc.player.getZ());
      }
   }

   private boolean inWorld(Minecraft mc) {
      return mc.player != null && mc.level != null && Game.screen(mc) == null && !Game.busy(mc);
   }

   private boolean drawn(long since, long least, long most) {
      if (since < least) {
         return false;
      } else if (this.map.settled()) {
         return true;
      } else if (since > most) {
         this.say("went on without everything drawn: " + this.map.tourState());
         return true;
      }

      return false;
   }

   private boolean seedReady(long since, long least, long most) {
      if (since < least) {
         return false;
      } else if (this.map.seedLayer().settled() && this.map.settled()) {
         return true;
      } else if (since > most) {
         this.say("went on before the seed map was ready: " + this.map.seedLayer().tourState());
         return true;
      }

      return false;
   }

   private void shot(Minecraft mc, String name) {
      String file = "s-" + this.folder + "-" + name;
      this.say("picture " + file + (this.map == null ? "" : ": " + this.map.tourState() + "; " + this.map.seedLayer().tourState()));
      Game.screenshot(mc, "chunkatlas-" + file + ".png", message -> {});
   }

   private void say(String text) {
      this.say.accept(text);
   }

   boolean tick(Minecraft mc) {
      if (this.at >= this.steps.size()) {
         return true;
      }

      long now = System.currentTimeMillis();
      if (this.steps.get(this.at).run(mc, now - this.started)) {
         this.at++;
         this.started = now;
      }

      return false;
   }
}
