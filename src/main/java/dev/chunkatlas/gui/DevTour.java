package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;

import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Seeds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.chunkatlas.InGame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

public final class DevTour {
   private static final Map<String, Path> ASKED = new HashMap<>();
   private static Path log;
   private static int step;
   private static int titleTicks;
   private static long at;
   private static boolean done;
   private static boolean far;
   private static boolean inGameDone;
   private static boolean featuresShown;
   private static boolean pauseShown;
   private static Screen listScreen;
   private static Screen pauseScreen;
   private static Screen title;
   private static MapScreen map;
   private static SeedTour seedTour;
   private static boolean movedForMinimap;
   private static int hudShots;
   private static boolean seedCompared;

   private DevTour() {
   }

   public static boolean wanted(Path gameFolder) {
      Path file = gameFolder.resolve(".chunkatlas").resolve("tour.txt");

      try {
         if (!Files.isRegularFile(file)) {
            return false;
         }

         for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.substring(eq + 1).isBlank()) {
               ASKED.put(line.substring(0, eq).trim(), Path.of(line.substring(eq + 1).trim()));
            }
         }

         Files.delete(file);
         log = file.resolveSibling("tour.log");
         Files.deleteIfExists(log);
      } catch (IOException | RuntimeException e) {
         return false;
      }

      Path home = ASKED.get("home");
      if (home != null) {
         System.setProperty("chunkatlas.home", home.toString());
      }

      MapScreen.tourDeaf = true;
      if (ASKED.containsKey("seedtour")) {
         seedTour = new SeedTour(ASKED, DevTour::say);
      }

      say("asked for: " + ASKED.keySet());
      return true;
   }

   public static void tick(Minecraft mc) {
      if (!done) {
         mc.options.pauseOnLostFocus = false;

         mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
         try {
            if (ASKED.containsKey("seedcleantour")) {
               cleanTour(mc, "seed");
            } else if (ASKED.containsKey("cavecleantour")) {
               cleanTour(mc, "cave");
            } else if (ASKED.containsKey("slicecleantour")) {
               cleanTour(mc, "slice");
            } else if (ASKED.containsKey("biometour")) {
               cleanTour(mc, "biome");
            } else if (ASKED.containsKey("cleantour")) {
               cleanTour(mc, "clean");
            } else if (ASKED.containsKey("looktour")) {
               lookTour(mc);
            } else if (ASKED.containsKey("pendingtour")) {
               pendingTour(mc);
            } else if (ASKED.containsKey("previewtour")) {
               previewTour(mc);
            } else if (ASKED.containsKey("nbttour")) {
               nbtTour(mc);
            } else if (ASKED.containsKey("fieldstour")) {
               fieldsTour(mc);
            } else if (ASKED.containsKey("toolstour")) {
               toolsTour(mc);
            } else if (ASKED.containsKey("cavetour")) {
               caveTour(mc);
            } else if (ASKED.containsKey("speedtour")) {
               speedTour(mc);
            } else if (ASKED.containsKey("blendtour")) {
               blendTour(mc);
            } else if (ASKED.containsKey("scaletour")) {
               scaleTour(mc);
            } else if (ASKED.containsKey("ktour")) {
               kTour(mc);
            } else if (ASKED.containsKey("frametour")) {
               frameTour(mc);
            } else if (ASKED.containsKey("transfertour")) {
               transferTour(mc);
            } else if (ASKED.containsKey("heighttour")) {
               heightTour(mc);
            } else if (ASKED.containsKey("endtour")) {
               endTour(mc);
            } else if (ASKED.containsKey("xaerotour")) {
               xaeroTour(mc);
            } else if (ASKED.containsKey("minimaptour")) {
               minimapTour(mc);
            } else if (ASKED.containsKey("minimapseedtour")) {
               minimapSeedTour(mc);
            } else if (ASKED.containsKey("pastetour")) {
               pasteTour(mc);
            } else if (ASKED.containsKey("seedlooktour")) {
               seedLookTour(mc);
            } else if (ASKED.containsKey("edittour")) {
               editTour(mc);
            } else if (ASKED.containsKey("servertour")) {
               serverTour(mc);
            } else if (ASKED.containsKey("livetour")) {
               liveTour(mc);
            } else if (ASKED.containsKey("exporttour")) {
               exportTour(mc);
            } else if (ASKED.containsKey("settings2tour")) {
               settings2Tour(mc);
            } else if (ASKED.containsKey("settingstour")) {
               settingsTour(mc);
            } else if (ASKED.containsKey("seedbenchtour")) {
               seedBenchTour(mc);
            } else if (ASKED.containsKey("portaltour")) {
               portalTour(mc);
            } else if (ASKED.containsKey("seedlandtour") || ASKED.containsKey("seedcavetour")) {
               seedLandTour(mc);
            } else if (ASKED.containsKey("loadedtour")) {
               loadedTour(mc);
            } else if (ASKED.containsKey("mobtour")) {
               mobTour(mc);
            } else if (ASKED.containsKey("dimtptour")) {
               dimTeleportTour(mc);
            } else if (ASKED.containsKey("findselecttour")) {
               findSelectTour(mc);
            } else if (ASKED.containsKey("newworldtour")) {
               newWorldTour(mc);
            } else if (ASKED.containsKey("clipblendtour")) {
               clipBlendTour(mc, "clipblendtour");
            } else if (ASKED.containsKey("smartblendtour")) {
               clipBlendTour(mc, "smartblendtour");
            } else if (ASKED.containsKey("seedfartour")) {
               seedFarTour(mc);
            } else if (ASKED.containsKey("biomereadtour")) {
               biomeReadTour(mc);
            } else if (ASKED.containsKey("generatetour")) {
               generateTour(mc);
            } else if (ASKED.containsKey("taskstour")) {
               tasksTour(mc);
            } else if (ASKED.containsKey("bordertour")) {
               borderTour(mc);
            } else if (ASKED.containsKey("livechunkstour")) {
               liveChunksTour(mc);
            } else if (ASKED.containsKey("seedpartialtour")) {
               seedPartialTour(mc);
            } else if (ASKED.containsKey("seedkepttour")) {
               seedKeptTour(mc);
            } else if (ASKED.containsKey("crowdtour")) {
               crowdTour(mc);
            } else if (ASKED.containsKey("pastefittour")) {
               pasteFitTour(mc);
            } else if (ASKED.containsKey("zoomflickertour")) {
               zoomFlickerTour(mc);
            } else if (ASKED.containsKey("handtour")) {
               handTour(mc);
            } else if (ASKED.containsKey("forceblendtour")) {
               forceBlendTour(mc);
            } else if (ASKED.containsKey("structurepastetour")) {
               structurePasteTour(mc);
            } else if (ASKED.containsKey("overlaptour")) {
               overlapTour(mc);
            } else if (seedTour == null) {
               run(mc);
            } else if (seedTour.tick(mc)) {
               finish(mc);
            }
         } catch (RuntimeException e) {
            say("failed at step " + step + ": " + e);
            finish(mc);
         }
      }
   }

   private static void run(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0:
            if (!Game.busy(mc) && screen instanceof TitleScreen && ++titleTicks >= 60) {
               title = screen;
               if (!open(mc, "view")) {
                  step = 5;
               } else {
                  next(now);
               }
            }
            break;
         case 1:
            if (ready(since, 1500L, 40000L)) {
               shot(mc, "1-map");
               map.tourZoom(2.0);
               next(now);
            }
            break;
         case 2:
            if (ready(since, 1200L, 40000L)) {
               shot(mc, "2-closer");
               map.tourEdit(true);
               map.tourSelect(5, 3);
               next(now);
            }
            break;
         case 3:
            if (since > 500L) {
               shot(mc, "3-selected");
               map.tourMenu(true);
               next(now);
            }
            break;
         case 4:
            if (since > 500L) {
               shot(mc, "4-pick-menu");
               map.tourMenu(false);
               map.tourEdit(false);
               map.tourLayers();
               next(now);
            }
            break;
         case 5:
            if (since > 500L) {
               if (map != null && !far) {
                  shot(mc, "4b-layers-menu");
                  map.tourCloseMenu();
               }

               if (open(mc, "big")) {
                  next(now);
               } else {
                  step = 7;
                  at = now;
               }
            }
            break;
         case 6:
            if (ready(since, 2500L, 90000L)) {
               if (!far) {
                  shot(mc, "5-big-world");
                  map.tourZoom(0.015625);
                  far = true;
                  at = now;
               } else {
                  shot(mc, "5b-big-world-far");
                  next(now);
               }
            }
            break;
         case 7:
            if (since > 300L) {
               if (open(mc, "edit")) {
                  next(now);
               } else {
                  step = 13;
                  at = now;
               }
            }
            break;
         case 8:
            if (ready(since, 1500L, 40000L)) {
               say("edit world before: " + map.tourState());
               map.tourZoom(2.0);
               map.tourSelect(5, 5);
               map.tourDelete();
               next(now);
            }
            break;
         case 9:
            if (screen instanceof ConfirmEditScreen confirm) {
               if (since > 1200L) {
                  shot(mc, "6-confirm-delete");
                  confirm.go();
                  next(now);
               }
            } else if (since > 20000L) {
               say("no question about the delete came up: " + map.tourState());
               step = 13;
               at = now;
            }
            break;
         case 10:
            if (screen == map && ready(since, 1500L, 40000L)) {
               say("edit world after delete: " + map.tourState());
               shot(mc, "7-after-delete");
               map.tourUndo();
               next(now);
            } else if (since > 60000L) {
               say("the delete did not come back to the map");
               step = 13;
               at = now;
            }
            break;
         case 11:
            if (screen instanceof ConfirmEditScreen confirm) {
               if (since > 1200L) {
                  shot(mc, "8-confirm-undo");
                  confirm.go();
                  next(now);
               }
            } else if (since > 20000L) {
               say("no question about the undo came up: " + map.tourState());
               step = 13;
               at = now;
            }
            break;
         case 12:
            if (screen == map && ready(since, 1500L, 40000L)) {
               say("edit world after undo: " + map.tourState());
               shot(mc, "9-after-undo");
               next(now);
            } else if (since > 60000L) {
               say("the undo did not come back to the map");
               next(now);
            }
            break;

         case 20:
            mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the in-game world would not open"));
            next(now);
            break;
         case 21:
         case 25:
            if (playing(mc) && since > 4000L) {
               if (step == 25) {
                  shot(mc, "12-back-in-world");
               } else if (!pauseShown) {
                  pauseShown = true;
                  mc.pauseGame(false);
                  step = 93;
                  at = now;
                  break;
               }
               InGame.open(mc, null);
               if (Game.screen(mc) instanceof MapScreen opened) {
                  map = opened;
                  say("opened the map in game");
                  next(now);
               } else {
                  say("the map did not open in game");
                  step = 99;
               }
            } else if (since > 180000L) {
               say("never got into the world (step " + step + ")");
               step = 99;
            }
            break;
         case 22:
            if (!featuresShown && ready(since, 2000L, 60000L)) {
               featuresShown = true;
               step = 40;
               at = now;
            } else if (featuresShown && since > 500L) {
               say("in game before delete: " + map.tourState());
               shot(mc, "10-ingame-map");
               map.tourZoom(2.0);

               map.tourPan(160.0, 0.0);
               map.tourSelect(3, 3);

               dev.chunkatlas.Settings.backups(true);
               map.tourDelete();
               next(now);
            }
            break;
         case 23:
         case 27:
            if (screen instanceof ConfirmEditScreen confirm) {
               if (since > 1200L) {
                  shot(mc, step == 23 ? "11-ingame-confirm" : "14-ingame-confirm-undo");
                  next(now);
                  confirm.go();
               }
            } else if (since > 20000L) {
               say("no question came up in game: " + map.tourState());
               step = 99;
            }
            break;
         case 24:
         case 28:

            if (step == 24 && screen == map && map.live() != null) {
               if (since > 1500L) {
                  shot(mc, "12-ingame-waiting");
                  step = 99;
                  at = now;
               }
               break;
            }

            step = step == 24 ? 25 : 29;
            at = now;
            break;
         case 26:
            if (ready(since, 2000L, 60000L)) {
               say("in game after delete: " + map.tourState());
               shot(mc, "13-ingame-after-delete");
               map.tourUndo();
               next(now);
            }
            break;
         case 29:
            if (playing(mc) && since > 4000L) {
               shot(mc, "15-back-after-undo");
               say("in game undo finished");
               step = 99;
               at = now;
            } else if (since > 180000L) {
               say("never got back after the undo (showing " + (Game.screen(mc) == null ? "the world" : Game.screen(mc).getClass().getSimpleName()) + ")");
               step = 99;
            }
            break;

         case 100:
            mc.options.guiScale().set(3);
            Game.resizeGui(mc);
            next(now);
            break;
         case 101:
            if (since > 1500L) {
               shot(mc, "40-scale-3");
               mc.options.guiScale().set(4);
               Game.resizeGui(mc);
               next(now);
            }
            break;
         case 102:
            if (since > 1500L) {
               shot(mc, "41-scale-4");
               map.tourOpenFilter();
               next(now);
            }
            break;
         case 103:
            if (since > 1000L) {
               shot(mc, "42-filter-scale-4");
               Game.open(mc, map);
               mc.options.guiScale().set(0);
               Game.resizeGui(mc);
               map.tourOpenFilter();
               next(now);
            }
            break;
         case 104:
            if (since > 1000L) {
               map.tourOpenPresets();
               next(now);
            }
            break;
         case 105:
            if (since > 600L) {
               shot(mc, "43-filter-presets");
               Game.open(mc, map);
               map.tourContext();
               step = 106;
               at = now;
            }
            break;
         case 106:
            if (since > 800L) {
               shot(mc, "44-context-menu");
               map.tourCloseMenu();
               step = 22;
               at = now;
            }
            break;
         case 90:
            Game.open(mc, new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(title));
            next(now);
            break;
         case 91:
            if (since > 1500L) {
               for (var child : screen.children()) {
                  if (child instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList list && !list.children().isEmpty()) {
                     list.setSelected(list.children().get(0));
                  }
               }

               next(now);
            }
            break;
         case 93:
            if (since > 1200L) {
               shot(mc, "00b-pause");
               pauseScreen = screen;
               for (var child : screen.children()) {
                  if (child instanceof net.minecraft.client.gui.components.SpriteIconButton icon && icon.getMessage().getString().equals(net.minecraft.client.resources.language.I18n.get("chunkatlas.pause.tooltip"))) {
                     dev.chunkatlas.port.Input.press(icon);
                  }
               }

               step = 96;
               at = now;
            }
            break;

         case 94:
            if (since > 2500L) {
               say("opened from world list: " + (screen == null ? "none" : screen.getClass().getSimpleName()));
               if (screen instanceof MapScreen m) {
                  m.onClose();
               }

               next(now);
            }
            break;
         case 95:
            if (since > 1500L) {
               shot(mc, "00c-world-list-after-map");
               Game.open(mc, title);
               step = 20;
               at = now;
            }
            break;
         case 96:
            if (since > 2500L) {
               say("opened from pause: " + (screen == null ? "none" : screen.getClass().getSimpleName()));
               if (screen instanceof MapScreen m) {
                  m.onClose();
               }

               next(now);
            }
            break;
         case 97:
            if (since > 1500L) {
               shot(mc, "00d-pause-after-map");
               Game.open(mc, null);
               step = 21;
               at = now;
            }
            break;
         case 92:
            if (since > 800L) {
               shot(mc, "00-world-list");
               listScreen = screen;
               for (var child : screen.children()) {
                  if (child instanceof ItemIconButton icon) {
                     dev.chunkatlas.port.Input.press(icon);
                  }
               }

               step = 94;
               at = now;
            }
            break;

         case 40:
         case 41:
         case 42:
         case 43:
            map.tourView(step - 39);
            if (since > 1500L) {
               shot(mc, "20-view-" + (step - 39));
               next(now);
            }
            break;
         case 44:
            map.tourReset();
            map.tourBrush();
            next(now);
            break;
         case 45:
            if (since > 600L) {
               shot(mc, "21-brush");
               say("after brush: " + map.tourState());
               map.tourReset();
               map.tourRegion();
               next(now);
            }
            break;
         case 46:
            if (since > 600L) {
               shot(mc, "22-region");
               say("after region: " + map.tourState());
               map.tourOpenSelections();
               next(now);
            }
            break;
         case 47:
            if (since > 800L) {
               shot(mc, "23-saved-selections");
               say("selections screen: " + (screen == null ? "none" : screen.getClass().getSimpleName()));
               Game.open(mc, map);
               next(now);
            }
            break;
         case 48:
            if (since > 400L) {
               map.tourReset();
               map.tourOpenFilter();
               next(now);
            }
            break;
         case 49:
            if (since > 800L) {
               shot(mc, "24-filter");
               say("filter screen: " + (screen == null ? "none" : screen.getClass().getSimpleName()));
               Game.open(mc, map);
               next(now);
            }
            break;

         case 50:
            if (since > 400L) {
               map.tourReset();
               map.tourSelect(2, 2);
               map.tourProtect(true);
               say("protected: " + map.tourProtected() + " (" + map.tourBanner() + ")");
               next(now);
            }
            break;
         case 51:
            if (since > 600L) {
               shot(mc, "25-protected");
               map.tourSelect(4, 4);
               map.tourDelete();
               next(now);
            }
            break;
         case 52:
            if (screen instanceof ConfirmEditScreen) {
               if (since > 1200L) {
                  shot(mc, "26-confirm-with-protected");
                  Game.open(mc, map);
                  map.tourReset();
                  next(now);
               }
            } else if (since > 20000L) {
               say("no question came up for the protected delete: " + map.tourState() + " / " + map.tourBanner());
               Game.open(mc, map);
               map.tourReset();
               next(now);
            }
            break;
         case 53:
            if (screen == map && ready(since, 1500L, 40000L) && map.tourIdle()) {
               map.tourPicture();
               say("picture asked for, busy: " + !map.tourIdle());
               step = 56;
               at = now;
            }
            break;
         case 56:
            if (since > 500L && map.tourIdle()) {
               say("picture: " + map.tourBanner());
               map.tourOpenShrink();
               step = 54;
               at = now;
            } else if (since > 120000L) {
               say("the picture never finished");
               map.tourOpenShrink();
               step = 54;
               at = now;
            }
            break;
         case 54:
            if (screen instanceof ShrinkScreen shrink && shrink.tourReady() && since > 800L) {
               shot(mc, "27-shrink");
               say("shrink: " + shrink.tourResult());
               shrink.tourFinish(false);
               next(now);
            } else if (since > 180000L) {
               say("the shrink screen never got ready: " + (screen == null ? "none" : screen.getClass().getSimpleName()));
               Game.open(mc, map);
               next(now);
            }
            break;
         case 55:
            if (since > 800L) {
               shot(mc, "28-shrink-selected");
               say("after shrink: " + map.tourState());
               map.tourReset();
               map.tourSelect(2, 2);
               map.tourProtect(false);
               say("protected after removing: " + map.tourProtected());
               map.tourReset();
               map.tourView(3);
               step = 57;
               at = now;
            }
            break;

         case 57:
            if (since > 1500L) {
               shot(mc, "29-view-block-entities");
               map.tourReset();
               map.tourFind("f1;all;BLOCK:AT_LEAST:8:#diamond_ore;STRUCTURE:HAS:0:minecraft:mineshaft");
               next(now);
            }
            break;
         case 58:
            if (since > 1000L && map.tourIdle()) {
               say("find block+structure: " + map.tourState());
               shot(mc, "30-find-result");
               map.tourOpenFilter();
               next(now);
            } else if (since > 120000L) {
               say("the block search never finished");
               map.tourOpenFilter();
               next(now);
            }
            break;
         case 59:
            if (since > 1000L) {
               shot(mc, "31-filter-with-rules");
               Game.open(mc, map);
               map.tourReset();
               step = 60;
               at = now;
            }
            break;

         case 80:
            if (since > 800L) {
               map.tourSelectStroke();
               say("select stroke close: " + map.tourState());
               shot(mc, "32-select-chunks");
               map.tourReset();
               map.tourZoom(0.0625);
               next(now);
            }
            break;
         case 81:
            if (ready(since, 1500L, 40000L)) {
               map.tourSelectStroke();
               say("select stroke far: " + map.tourState());
               shot(mc, "33-select-regions");
               map.tourReset();
               map.tourZoom(16.0);
               step = 100;
               at = now;
            }
            break;

         case 60:
            if (since > 400L) {
               map.tourReset();
               if (!map.seedLayer().shown()) {
                  map.seedLayer().toggle();
               }

               next(now);
            }
            break;
         case 61:
            if (screen instanceof SeedMapWarningScreen warning) {
               if (since > 1000L) {
                  shot(mc, "30-seed-warning");
                  warning.show();
                  next(now);
               }
            } else if (screen == map && map.seedLayer().shown() && since > 1000L) {
               say("seed map already allowed for this world");
               next(now);
            } else if (since > 10000L) {
               say("no seed map warning came up: " + (screen == null ? "none" : screen.getClass().getSimpleName()));
               step = 80;
               at = now;
            }
            break;
         case 62:
            if (screen == map && seedReady(since, 2500L, 60000L)) {
               shot(mc, "31-seed-map");
               map.tourZoom(0.125);
               next(now);
            }
            break;
         case 63:
            if (seedReady(since, 2500L, 60000L)) {
               shot(mc, "32-seed-far");

               map.seedCentre(-1889.0, -739.0);
               map.tourZoom(2.0);
               next(now);
            }
            break;
         case 64:
            if (seedReady(since, 2500L, 60000L)) {
               shot(mc, "33-seed-close");
               map.tourSeedPanel(true);
               next(now);
            }
            break;
         case 65:
            if (since > 600L) {
               shot(mc, "34-seed-menu");
               map.seedPanel().tourFeatures(true);
               next(now);
            }
            break;
         case 66:
            if (since > 800L) {
               shot(mc, "35-seed-types");
               map.seedPanel().tourFeatures(false);
               map.tourSeedPanel(false);
               map.seedLayer().findNearest("minecraft:village");
               next(now);
            }
            break;
         case 67:
            if (since > 1500L && seedReady(since, 1500L, 60000L)) {
               shot(mc, "36-seed-nearest");
               map.seedLayer().tourSlime();
               next(now);
            }
            break;
         case 68:
            if (seedReady(since, 2500L, 60000L)) {
               shot(mc, "37-seed-slime");
               map.seedLayer().tourCheck().whenComplete((text, error) -> say(error != null ? "seed check failed: " + error : text));
               next(now);
            }
            break;
         case 69:
            if (since > 9000L) {
               map.seedLayer().findNearest("minecraft:desert_well");
               next(now);
            }
            break;
         case 70:
            if (seedReady(since, 3000L, 60000L)) {
               shot(mc, "38-seed-desert-well");
               map.seedLayer().openTypes();
               next(now);
            }
            break;
         case 71:
            if (since > 800L) {
               shot(mc, "39-seed-types-grouped");
               map.tourSeedPanel(false);
               next(now);
            }
            break;
         case 72:
            if (since > 500L) {
               if (mc.player != null) {
                  map.seedCentre(mc.player.getX(), mc.player.getZ());
               }

               step = 80;
               at = now;
            }
            break;
         default:
            if (since > 1000L) {
               if (!inGameDone && ASKED.containsKey("ingame")) {
                  inGameDone = true;
                  step = 90;
                  at = now;
               } else {
                  finish(mc);
               }
            }
      }
   }

   private static boolean playing(Minecraft mc) {
      if (mc.player != null && Game.screen(mc) instanceof net.minecraft.client.gui.screens.PauseScreen && Game.screen(mc) != pauseScreen) {
         Game.open(mc, null);
      }

      return mc.player != null && mc.level != null && Game.screen(mc) == null && !Game.busy(mc);
   }

   private static boolean seedReady(long since, long least, long most) {
      if (since < least) {
         return false;
      } else if (map.seedLayer().settled() && map.settled()) {
         say("seed map ready after " + since + " ms: " + map.seedLayer().tourState());
         return true;
      } else if (since > most) {
         say("step " + step + " went on before the seed map was ready: " + map.seedLayer().tourState());
         return true;
      } else {
         return false;
      }
   }

   private static boolean ready(long since, long least, long most) {
      if (since < least) {
         return false;
      } else if (map.settled()) {
         return true;
      } else if (since > most) {
         say("step " + step + " went on without everything drawn: " + map.tourState());
         return true;
      } else {
         return false;
      }
   }

   private static boolean open(Minecraft mc, String key) {
      Path world = ASKED.get(key);
      if (world != null && !world.isAbsolute()) {
         world = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("saves").resolve(world.toString());
      }

      if (world != null && Files.isDirectory(world)) {
         map = new MapScreen(title, world);
         Game.open(mc, map);
         say("opened " + key + " at " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
         return true;
      } else {
         say("no world for " + key);
         return false;
      }
   }

   private static void next(long now) {
      step++;
      at = now;
   }

   private static SettingsScreen settings;

   private static void exportTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen && ++titleTicks >= 60) {
               title = screen;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourEdit(true);
               map.tourSelect(5, 3);
               next(now);
            }
         }
         case 2 -> {
            if (since > 600L) {
               map.tourExport("Tour clip", false);
               next(now);
            }
         }
         case 3, 4 -> {
            if (since > 500L && map.tourIdle()) {
               say("export " + (step == 3 ? "clip" : "world") + ": " + map.tourBanner());
               if (step == 3) {
                  map.tourExport("Tour world", true);
               } else {
                  listTree(ASKED.get("home").resolve("clips"));
                  listTree(ASKED.get("view").getParent().resolve("Tour world"));
                  shot(mc, "export-done");
                  map.tourCopyFrom();
               }

               next(now);
            } else if (since > 60000L) {
               say("export never finished: " + map.tourBanner());
               finish(mc);
            }
         }
         case 5 -> {
            if (since > 1500L) {
               shot(mc, "export-sources");
               boolean opened = screen instanceof CopyChunks.SourceScreen s && s.tourOpenFirstClip();
               say("clip in sources: " + opened);
               if (opened) {
                  map = (MapScreen)Game.screen(mc);
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 6 -> {
            if (ready(since, 1500L, 30000L)) {
               shot(mc, "export-clip-map");
               say("clip map: " + map.tourState());
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static SeedPreviewScreen preview;

   private static void pendingTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               dev.chunkatlas.Settings.editWithoutCheats(true);
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 3000L) {
               dev.chunkatlas.InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (map != null && ready(since, 2000L, 40000L)) {
               map.tourEdit(true);
               map.tourSelect(3, 2);
               map.tourDelete();
               next(now);
            }
         }
         case 3 -> {
            if (screen instanceof ConfirmEditScreen confirm && since > 1200L) {
               confirm.go();
               next(now);
            } else if (since > 30000L) {
               say("no confirm");
               finish(mc);
            }
         }
         case 4 -> {
            if (since > 1500L) {
               say("back on the map: " + (Game.screen(mc) == map) + ", waiting " + map.tourPending() + ", banner " + map.tourBanner());
               shot(mc, "pending-on-map");
               map.onClose();
               next(now);
            }
         }
         case 5 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 3000L) {
               say("back in the world after making the change");
               finish(mc);
            } else if (since > 120000L) {
               say("never came back: " + (Game.screen(mc) == null ? "no screen" : Game.screen(mc).getClass().getSimpleName()));
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void previewTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               Game.createWorld(mc, t);
               next(now);
            }
         }
         case 1 -> {
            if (since > 3000L && screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen c) {
               shot(mc, "preview-create");
               preview = new SeedPreviewScreen(c);
               Game.open(mc, preview);
               next(now);
            } else if (since > 30000L) {
               say("no Create New World screen");
               finish(mc);
            }
         }
         case 2 -> {
            if (since > 3000L && preview.tourIdle() || since > 60000L) {
               shot(mc, "preview-far");
               preview.mouseScrolled(preview.width / 2.0, preview.height / 2.0, 0, 1);
               preview.mouseScrolled(preview.width / 2.0, preview.height / 2.0, 0, 1);
               next(now);
            }
         }
         case 3 -> {
            if (since > 3000L && preview.tourIdle() || since > 60000L) {
               shot(mc, "preview-near");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void nbtTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourEdit(true);
               map.tourNbtHere();
               next(now);
            }
         }
         case 2 -> {
            if (since > 800L && screen instanceof NbtScreen n) {
               n.tourSet("InhabitedTime", "12000");
               next(now);
            } else if (since > 5000L) {
               say("no NBT editor: " + map.tourBanner());
               finish(mc);
            }
         }
         case 3 -> {
            if (since > 800L && screen instanceof NbtScreen n) {
               shot(mc, "nbt-editor");
               n.tourSave();
               next(now);
            }
         }
         case 4 -> {
            if (screen instanceof ConfirmEditScreen confirm) {
               if (since > 1000L) {
                  shot(mc, "nbt-confirm");
                  confirm.go();
                  next(now);
               }
            } else if (since > 30000L) {
               say("no confirm: " + map.tourBanner());
               finish(mc);
            }
         }
         case 5 -> {
            if (screen == map && ready(since, 1500L, 40000L)) {
               say("after: " + map.tourBanner());
               finish(mc);
            } else if (since > 60000L) {
               say("never came back");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void fieldsTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourZoom(2.0);
               map.tourEdit(true);
               map.tourSelect(4, 3);
               map.tourOpenFields();
               next(now);
            }
         }
         case 2 -> {
            if (since > 600L && screen instanceof FieldsScreen f0) {
               f0.tourTick("inhabited", "10");
               f0.tourTick("biome", "minecraft:mushroom_fields");
               f0.tourTick("structures", null);
               step = 22;
               at = now;
            }
         }
         case 22 -> {
            if (since > 800L) {
               shot(mc, "fields-screen");
               if (screen instanceof FieldsScreen f) {
                  f.onClose();
               }

               map.tourFields(new dev.chunkatlas.engine.ChunkFields.Changes(
                  null, 10L * 60L * 20L, null, null, "minecraft:mushroom_fields", null, null, null, true, false, false
               ));
               step = 3;
               at = now;
            }
         }
         case 3 -> {
            if (screen instanceof ConfirmEditScreen confirm) {
               if (since > 1200L) {
                  shot(mc, "fields-confirm");
                  confirm.go();
                  next(now);
               }
            } else if (since > 30000L) {
               say("no confirm: " + map.tourBanner());
               finish(mc);
            }
         }
         case 4 -> {
            if (screen == map && ready(since, 2000L, 40000L)) {
               say("after: " + map.tourState() + " banner " + map.tourBanner());
               map.tourEdit(false);
               map.tourStyle(dev.chunkatlas.engine.TileRenderer.BIOMES);
               next(now);
            } else if (since > 60000L) {
               say("never came back to the map");
               finish(mc);
            }
         }
         case 5 -> {
            if (ready(since, 2500L, 40000L)) {
               shot(mc, "fields-after-biomes");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static int walked;

   private static void toolsTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 3000L) {
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (since > 150L) {
               var server = mc.getSingleplayerServer();
               var sp = server.getPlayerList().getPlayers().get(0);
               double a = walked * 0.12;
               double x = sp.getX() + Math.cos(a) * 6;
               double z = sp.getZ() + Math.sin(a) * 6;
               server.execute(() -> sp.teleportTo(x, sp.getY(), z));
               at = now;
               if (++walked >= 40) {
                  next(now);
               }
            }
         }
         case 3 -> {
            if (since > 2000L) {
               dev.chunkatlas.InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               next(now);
            }
         }
         case 4 -> {
            if (map != null && ready(since, 2000L, 40000L)) {
               map.tourZoom(1.0);
               map.tourMeasure(mc.player.getX() - 80, mc.player.getZ() - 60);
               next(now);
            }
         }
         case 5 -> {
            if (since > 1500L) {
               shot(mc, "tools-footsteps-measure");
               map.tourMeasure(Double.NaN, Double.NaN);
               map.tourUnreadable();
               next(now);
            }
         }
         case 6 -> {
            if (since > 1000L && map.tourIdle()) {
               say("unreadable: " + map.tourState() + " banner " + map.tourBanner());
               Game.open(mc, new SearchScreen(map, "minecraft:overworld", (x, z) -> {}));
               next(now);
            }
         }
         case 7 -> {
            if (since > 800L) {
               if (Game.screen(mc) instanceof SearchScreen s) {
                  s.tourType("-480 -350");
               }

               next(now);
            }
         }
         case 8 -> {
            if (since > 800L) {
               shot(mc, "tools-search");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void caveTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourZoom(2.0);
               next(now);
            }
         }
         case 2 -> {
            if (ready(since, 1500L, 40000L)) {
               shot(mc, "cave-surface");
               map.tourCave(40);
               next(now);
            }
         }
         case 3 -> {
            if (ready(since, 2000L, 60000L)) {
               shot(mc, "cave-y40");
               say("cave: " + map.tourState());
               map.tourStyle(dev.chunkatlas.engine.TileRenderer.SLICE);
               step = 30;
               at = now;
            }
         }
         case 30 -> {
            if (ready(since, 2000L, 60000L)) {
               shot(mc, "cave-slice");
               map.tourCave(Integer.MIN_VALUE);
               map.tourStyle(dev.chunkatlas.engine.TileRenderer.DRY);
               step = 4;
               at = now;
            }
         }
         case 4 -> {
            if (ready(since, 2500L, 60000L)) {
               shot(mc, "ocean-floor");
               map.tourStyle(dev.chunkatlas.engine.TileRenderer.DRY | dev.chunkatlas.engine.TileRenderer.CONTOURS);
               next(now);
            }
         }
         case 5 -> {
            if (ready(since, 2500L, 60000L)) {
               shot(mc, "contours");
               map.tourStyle(dev.chunkatlas.engine.TileRenderer.BIOMES);
               map.tourZoom(0.25);
               next(now);
            }
         }
         case 6 -> {
            if (ready(since, 2500L, 60000L)) {
               shot(mc, "biomes-border");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void cleanTour(Minecraft mc, String kind) {
      long now = System.currentTimeMillis();
      long since = now - at;
      if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
         w.show();
         return;
      }
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               dev.chunkatlas.Settings.grid(false);
               dev.chunkatlas.Settings.players(false);
               dev.chunkatlas.Settings.footsteps(false);
               dev.chunkatlas.Settings.portals(false);
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourHideControls(true);
               if (kind.equals("biome")) {
                  map.tourStyle(dev.chunkatlas.engine.TileRenderer.BIOMES);
               } else if (kind.equals("seed")) {
                  map.tourPan(12000, 0);
                  if (!map.seedLayer().shown()) {
                     map.seedLayer().toggle();
                  }
               } else if (kind.equals("cave") || kind.equals("slice")) {
                  map.tourCave(40);
                  if (kind.equals("slice")) {
                     map.tourStyle(dev.chunkatlas.engine.TileRenderer.SLICE);
                  }
               }

               map.tourZoom(2.0);
               next(now);
            }
         }
         case 2 -> {
            if (ready(since, 3000L, 60000L)) {
               shot(mc, kind + "-2");
               map.tourZoom(2.0);
               next(now);
            }
         }
         case 3 -> {
            if (ready(since, 3000L, 60000L)) {
               shot(mc, kind + "-4");
               map.tourZoom(0.25);
               next(now);
            }
         }
         case 4 -> {
            if (ready(since, 3500L, 60000L)) {
               shot(mc, kind + "-1");
               dev.chunkatlas.Settings.grid(true);
               dev.chunkatlas.Settings.players(true);
               dev.chunkatlas.Settings.footsteps(true);
               dev.chunkatlas.Settings.portals(true);
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void lookTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               dev.chunkatlas.Settings.detailed(true);
               dev.chunkatlas.Settings.night(false);
               dev.chunkatlas.Settings.clearWater(true);
               dev.chunkatlas.GameColours g = dev.chunkatlas.GameColours.get();
               for (String b : new String[]{"minecraft:red_wool", "minecraft:grass_block", "minecraft:oak_leaves", "minecraft:birch_leaves", "minecraft:water",
                  "minecraft:short_grass", "minecraft:ice", "minecraft:sand", "minecraft:lava", "minecraft:glass", "minecraft:torch", "minecraft:snow"}) {
                  long l = g.look(b);
                  say("look " + b + " rgb " + Integer.toHexString(dev.chunkatlas.engine.Look.rgb(l)) + " fill " + dev.chunkatlas.engine.Look.fill(l) + " tint "
                     + dev.chunkatlas.engine.Look.tint(l) + " kinds " + Long.toHexString(l >>> 35 & 31));
               }

               say("tint plains grass " + Integer.toHexString(g.tint("minecraft:plains", 1)) + " swamp water " + Integer.toHexString(g.tint("minecraft:swamp", 3)));
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourZoom(2.0);
               next(now);
            }
         }
         case 2 -> {
            if (ready(since, 2500L, 60000L)) {
               shot(mc, "look-detailed");
               map.tourZoom(4.0);
               next(now);
            }
         }
         case 3 -> {
            if (ready(since, 2500L, 60000L)) {
               shot(mc, "look-close");
               map.tourZoom(0.125);
               next(now);
            }
         }
         case 4 -> {
            if (ready(since, 3500L, 60000L)) {
               shot(mc, "look-far");
               map.tourZoom(2.0);
               dev.chunkatlas.Settings.night(true);
               next(now);
            }
         }
         case 5 -> {
            if (ready(since, 3000L, 60000L)) {
               shot(mc, "look-night");
               dev.chunkatlas.Settings.night(false);
               dev.chunkatlas.Settings.clearWater(false);
               next(now);
            }
         }
         case 6 -> {
            if (ready(since, 3000L, 60000L)) {
               shot(mc, "look-solid");
               dev.chunkatlas.Settings.clearWater(true);
               dev.chunkatlas.Settings.detailed(false);
               next(now);
            }
         }
         case 7 -> {
            if (ready(since, 3000L, 60000L)) {
               shot(mc, "look-vanilla");
               dev.chunkatlas.Settings.detailed(true);
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static long speedStart;
   private static boolean speedAny;
   private static boolean speedIndex;

   private static void speedTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 20) {
               title = t;
               map = new MapScreen(t, Path.of(ASKED.get("view").toString()));
               Game.open(mc, map);
               speedStart = now;
               next(now);
            }
         }
         case 1, 3 -> {
            int[] s = map.tourTiles();
            if (s[0] > 0 && !speedIndex) {
               speedIndex = true;
               say("index ready in " + (now - speedStart) + " ms");
            }

            if (s[0] > 0 && !speedAny && s[1] == s[0]) {
               speedAny = true;
               say("screen filled in " + (now - speedStart) + " ms (" + s[0] + " regions)");
            }

            if (s[0] > 0 && s[2] == s[0]) {
               say("all sharp in " + (now - speedStart) + " ms (" + s[0] + " regions)");
               shot(mc, step == 1 ? "speed-far" : "speed-near");
               if (step == 1) {
                  map.tourZoom(4.0);
                  speedStart = now;
                  speedAny = false;
                  next(now);
               } else {
                  finish(mc);
               }
            } else if (now - at > 120000L) {
               say("not sharp after 2 min: " + java.util.Arrays.toString(s));
               finish(mc);
            }
         }
         case 2 -> next(now);
         default -> {
         }
      }
   }

   private static long kStart;
   private static boolean kShown;

   private static void kTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1, 4 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 8000L) {
               press(mc, "key.chunkatlas.open");
               kStart = now;
               kShown = false;
               next(now);
            } else if (Game.screen(mc) != null && since > 2000L && !(Game.screen(mc) instanceof MapScreen)) {
               Game.open(mc, null);
            }
         }
         case 2, 5 -> {
            if (Game.screen(mc) instanceof MapScreen m) {
               int[] s = m.tourTiles();
               if (!kShown && s[0] > 0 && s[1] == s[0]) {
                  kShown = true;
                  say((step == 2 ? "first" : "second") + " K: map showing in " + (now - kStart) + " ms");
               }

               if (s[0] > 0 && s[2] == s[0]) {
                  say((step == 2 ? "first" : "second") + " K: sharp in " + (now - kStart) + " ms");
                  next(now);
               }
            }

            if (now - kStart > 30000L) {
               say("K: not ready after 30 s");
               next(now);
            }
         }
         case 3 -> {
            Game.open(mc, null);
            next(now);
         }
         default -> finish(mc);
      }
   }

   private static final String[] FRAME_RUNS = {"off", "on", "detailed"};

   private static final int FRAME_LAP = 1200;
   private static int frameTick;
   private static double frameX;
   private static double frameY;
   private static double frameZ;
   private static boolean frameEntered;
   private static boolean frameWasEnabled;
   private static boolean frameWasDetailed;

   private static void frameTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      dev.chunkatlas.minimap.Config cfg = dev.chunkatlas.minimap.Config.get();
      if (step >= 10 && step < 10 + FRAME_RUNS.length * 3) {
         int run = (step - 10) / 3;
         String name = FRAME_RUNS[run];
         switch ((step - 10) % 3) {
            case 0 -> {
               if (!frameEntered) {
                  frameEntered = true;
                  cfg.enabled = run > 0;
                  dev.chunkatlas.Settings.detailed(run == 2);
                  frameTick = 0;
                  frameCommand(mc, String.format(java.util.Locale.ROOT, "tp @p %.1f %.1f %.1f -90 25", frameX, frameY, frameZ));
                  say("frametour run=" + name + " minimap=" + cfg.enabled + " detailed=" + dev.chunkatlas.Settings.detailed());
               } else if (since > 5000L) {
                  frameEntered = false;
                  dev.chunkatlas.Perf.start();
                  next(now);
               }

               frameHold(mc);
            }
            case 1 -> {
               frameHold(mc);
               if (since > 10000L) {
                  dev.chunkatlas.Perf.stop();
                  for (String line : dev.chunkatlas.Perf.report("still-" + name)) {
                     say(line);
                  }

                  dev.chunkatlas.Perf.start();
                  next(now);
               }
            }
            default -> {
               if (frameFly(mc)) {
                  dev.chunkatlas.Perf.stop();
                  for (String line : dev.chunkatlas.Perf.report("fly-" + name)) {
                     say(line);
                  }

                  say("frametour state run=" + name + " scanner: " + dev.chunkatlas.ChunkScanner.stats() + "; minimap: "
                     + dev.chunkatlas.minimap.Minimap.CACHE.size() + " tiles; " + dev.chunkatlas.minimap.Minimap.CACHE.far().stats());
                  step = run + 1 < FRAME_RUNS.length ? step + 1 : 30;
                  at = now;
               }
            }
         }

         return;
      }

      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && Game.screen(mc) == null && since > 8000L) {
               frameCommand(mc, "gamemode creative @a");
               frameCommand(mc, "time set noon");
               frameCommand(mc, "weather clear");
               Game.unthrottled(mc);
               frameWasEnabled = cfg.enabled;
               frameWasDetailed = dev.chunkatlas.Settings.detailed();
               frameX = mc.player.getX();
               frameZ = mc.player.getZ();
               frameY = Math.max(mc.player.getY() + 40.0, 140.0);
               cfg.enabled = true;
               dev.chunkatlas.Settings.detailed(false);
               frameTick = 0;
               say(String.format(java.util.Locale.ROOT, "frametour setup render_distance=%d gui_scale=%d window=%dx%d minimap_size=%s zoom=%.2f shape=%s north_lock=%b start=%.0f,%.0f,%.0f",
                  mc.options.getEffectiveRenderDistance(), Math.round(mc.getWindow().getGuiScale()), mc.getWindow().getWidth(), mc.getWindow().getHeight(), cfg.size,
                  dev.chunkatlas.minimap.Config.ZOOMS[cfg.zoom], cfg.shape, cfg.northLock, frameX, frameY, frameZ));
               next(now);
            } else if (Game.screen(mc) != null && since > 2000L && !(Game.screen(mc) instanceof MapScreen)) {
               Game.open(mc, null);
            }
         }
         case 2 -> {
            if (frameFly(mc)) {
               say("frametour warm-up lap done");
               frameEntered = false;
               step = 10;
               at = now;
            }
         }
         case 30, 33 -> {
            frameHold(mc);
            if (Game.screen(mc) == null && since > 3000L) {
               press(mc, "key.chunkatlas.open");
               kStart = now;
               kShown = false;
               next(now);
            }
         }
         case 31, 34 -> {
            String which = step == 31 ? "first" : "second";
            if (Game.screen(mc) instanceof MapScreen m) {
               int[] s = m.tourTiles();
               if (!kShown && s[0] > 0 && s[1] == s[0]) {
                  kShown = true;
                  say("frametour K run=" + which + " showing_ms=" + (now - kStart));
               }

               if (s[0] > 0 && s[2] == s[0]) {
                  say("frametour K run=" + which + " sharp_ms=" + (now - kStart));
                  next(now);
               }
            }

            if (now - kStart > 30000L) {
               say("frametour K run=" + which + " not ready after 30 s");
               next(now);
            }
         }
         case 32 -> {
            Game.open(mc, null);
            next(now);
         }
         default -> {
            cfg.enabled = frameWasEnabled;
            dev.chunkatlas.Settings.detailed(frameWasDetailed);
            finish(mc);
         }
      }
   }

   private static void frameCommand(Minecraft mc, String command) {
      net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
      if (server != null) {
         server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
      }
   }

   private static void frameHold(Minecraft mc) {
      if (mc.player != null) {
         mc.player.getAbilities().flying = true;
         mc.player.setDeltaMovement(0.0, 0.0, 0.0);
      }
   }

   private static boolean frameFly(Minecraft mc) {
      if (frameTick > FRAME_LAP || mc.player == null) {
         return true;
      }

      int t = frameTick++;
      int leg = FRAME_LAP / 3;
      double dx;
      double dz;
      if (t < leg) {
         dx = t;
         dz = 0.0;
      } else if (t < 2 * leg) {
         dx = leg;
         dz = -(t - leg);
      } else {
         dx = leg - (t - 2 * leg);
         dz = -leg;
      }

      float[] yaws = {-90.0F, -180.0F, -270.0F};
      int i = Math.min(2, t / leg);
      float yaw = yaws[i];
      int intoLeg = t - i * leg;
      if (i > 0 && intoLeg < 10) {
         yaw = net.minecraft.util.Mth.lerp(0.5F + intoLeg / 20.0F, yaws[i - 1], yaws[i]);
      } else if (i < 2 && leg - intoLeg <= 10) {
         yaw = net.minecraft.util.Mth.lerp(0.5F - (leg - intoLeg) / 20.0F, yaws[i], yaws[i + 1]);
      }

      frameHold(mc);
      mc.player.setPos(frameX + dx, frameY, frameZ + dz);
      mc.player.setYRot(yaw);
      mc.player.setYHeadRot(yaw);
      mc.player.setXRot(25.0F);
      return false;
   }

   private static void seedBenchTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && now - at > 6000L) {
               dev.chunkatlas.seed.SeedSampler w = Seeds.open(mc.getSingleplayerServer(), "minecraft:overworld", dev.chunkatlas.GameColours.get());
               say("cold: " + w.bench());
               say("warm: " + w.bench());
               for (int n : new int[]{8, 32, 64}) {
                  long t0 = System.nanoTime();
                  w.terrain(40, 40, n, dev.chunkatlas.seed.SeedSampler.NO_CAVE, true, () -> false);
                  long t1 = System.nanoTime();
                  say("quick region at " + n + ": " + (t1 - t0) / 1000000L + " ms");
               }

               long t0 = System.nanoTime();
               w.terrain(41, 40, 16, () -> false);
               say("height region at 16: " + (System.nanoTime() - t0) / 1000000L + " ms");
               next(now);
            }
         }
         default -> finish(mc);
      }
   }

   private static void portalTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 6000L) {
               dev.chunkatlas.Settings.portals(true);
               mc.player.connection.sendCommand("gamemode creative");
               mc.player.connection.sendCommand("fill ~3 ~ ~ ~6 ~4 ~ minecraft:obsidian");
               mc.player.connection.sendCommand("fill ~4 ~1 ~ ~5 ~3 ~ minecraft:air");
               next(now);
            }
         }
         case 2 -> {
            if (since > 1500L) {
               mc.player.connection.sendCommand("setblock ~4 ~1 ~ minecraft:fire");
               next(now);
            }
         }
         case 3 -> {
            if (since > 2500L) {
               say("portal block there: " + mc.level.getBlockState(mc.player.blockPosition().offset(4, 1, 0)));
               press(mc, "key.chunkatlas.open");
               next(now);
            }
         }
         case 4 -> {
            if (Game.screen(mc) instanceof MapScreen m && since > 5000L) {
               m.tourZoom(4.0);
               next(now);
            }
         }
         case 5 -> {
            if (since > 3000L) {
               shot(mc, "portal-map");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static final double[] LAND_ZOOMS = {1.0E-6, 4.0, 2.0, 2.0, 2.0, 2.0};
   private static final String[] LAND_SHOTS = {"1-64", "1-16", "1-8", "1-4", "1-2", "1-1"};

   private static void seedLandTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      if (step == 0) {
         if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
            title = t;
            mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
            next(now);
         }
      } else if (step == 1) {
         if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 6000L) {
            press(mc, "key.chunkatlas.open");
            next(now);
         }
      } else if (step == 2) {
         if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
            w.show();
         } else if (Game.screen(mc) instanceof MapScreen m && since > 3000L) {
            map = m;
            if (!m.seedLayer().shown()) {
               m.seedLayer().toggle();
            }

            if (m.seedLayer().world() != null) {
               int[] land = m.seedLayer().world().tourLand(mc.player.getBlockX(), mc.player.getBlockZ());
               double[] c = m.tourCentre();
               m.tourPan(land[0] - c[0], land[1] - c[1]);
               say("land at " + land[0] + ", " + land[1] + " (" + land[2] + "% land around it); " + m.seedLayer().world().groundCheck(land[0], land[1]));
               if (ASKED.containsKey("seedcavetour")) {
                  m.tourCave(20);
               }

               m.tourZoom(LAND_ZOOMS[0]);
               next(now);
            }
         }
      } else if (step - 3 < LAND_SHOTS.length) {
         if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
            w.show();
         }

         int i = step - 3;
         if (since > 3000L && since < 3100L) {
            shot(mc, (ASKED.containsKey("seedcavetour") ? "seedcave-" : "seedland-") + LAND_SHOTS[i] + "-3s");
         }

         if (since > 14000L || since > 5000L && map.seedLayer().tourIdle()) {
            shot(mc, (ASKED.containsKey("seedcavetour") ? "seedcave-" : "seedland-") + LAND_SHOTS[i]);
            say(LAND_SHOTS[i] + " after " + since + " ms: " + map.seedLayer().tourState());
            if (i + 1 < LAND_ZOOMS.length) {
               map.tourZoom(LAND_ZOOMS[i + 1]);
            }

            next(now);
         }
      } else {
         finish(mc);
      }
   }

   private static long farStart;

   private static void seedFarTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 6000L) {
               press(mc, "key.chunkatlas.open");
               next(now);
            }
         }
         case 2 -> {
            if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
               w.show();
            } else if (Game.screen(mc) instanceof MapScreen m && since > 3000L) {
               map = m;
               if (!m.seedLayer().shown()) {
                  m.seedLayer().toggle();
               }

               if (m.seedLayer().world() != null) {
                  say("quick heights: " + m.seedLayer().world().groundCheck(mc.player.getBlockX(), mc.player.getBlockZ()));
               }

               m.tourZoom(1.0E-6);
               farStart = now;
               next(now);
            }
         }
         case 3 -> {
            if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
               w.show();
            }

            long t = now - farStart;
            if (t > 500L && t < 600L || t > 1000L && t < 1100L || t > 2000L && t < 2100L || t > 6000L && t < 6100L || t > 15000L && t < 15100L) {
               say("far " + t + " ms: " + map.seedLayer().tourState());
            }

            if (t > 1500L && t < 1600L) {
               shot(mc, "seedfar-1s");
            }

            if (t > 6000L && t < 6100L) {
               shot(mc, "seedfar-6s");
            }

            if (t > 20000L || t > 3000L && map.seedLayer().tourIdle()) {
               say("far settled after " + t + " ms: " + map.seedLayer().tourState());
               shot(mc, "seedfar-settled");
               map.tourColourBy(dev.chunkatlas.engine.TileRenderer.BIOMES);
               next(now);
            }
         }
         case 4 -> {
            if (since > 2000L && since < 2100L) {
               shot(mc, "seedfar-biomes-2s");
            }

            if (since > 12000L) {
               shot(mc, "seedfar-biomes");
               map.tourZoom(16.0);
               next(now);
            }
         }
         case 5 -> {
            if (since > 10000L) {
               shot(mc, "seedfar-biomes-near");
               say("highlighting " + map.seedLayer().tourHighlightAt(mc.player.getBlockX(), mc.player.getBlockZ()));
               next(now);
            }
         }
         case 6 -> {
            if (since > 8000L) {
               shot(mc, "seedfar-highlight");
               say("highlight: " + map.seedLayer().tourState());
               map.tourZoom(1.0E-6);
               next(now);
            }
         }
         case 7 -> {
            if (since > 8000L) {
               shot(mc, "seedfar-highlight-far");
               say("highlight far: " + map.seedLayer().tourState());
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static boolean biomePrimed;

   private static void biomeReadTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 8000L) {
               press(mc, "key.chunkatlas.open");
               next(now);
            } else if (Game.screen(mc) != null && since > 2000L && !(Game.screen(mc) instanceof MapScreen)) {
               Game.open(mc, null);
            }
         }
         case 2, 3, 4 -> {
            if (Game.screen(mc) instanceof MapScreen m && since > 4000L) {
               if (step == 3) {
                  m.tourZoom(0.25);
               }

               if (!biomePrimed) {
                  biomePrimed = true;
                  m.tourBiomeCheck();
               } else if (since > 7000L) {
                  biomePrimed = false;
                  say(m.tourBiomeCheck());
                  shot(mc, "biome-read-" + step);
                  next(now);
               }
            }
         }
         default -> finish(mc);
      }
   }

   private static dev.chunkatlas.engine.ChunkSelection generateBox;
   private static long generateStart;
   private static boolean generateMid;

   private static void generateTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 5000L) {
               InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               next(now);
            } else if (Game.screen(mc) != null && since > 2000L && !(Game.screen(mc) instanceof MapScreen)) {
               Game.open(mc, null);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (map == null) {
               finish(mc);
            } else if (ready(since, 2000L, 30000L)) {
               if (!map.seedLayer().shown()) {
                  map.seedLayer().toggle();
               }

               next(now);
            }
         }
         case 3 -> {
            if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
               w.show();
            } else if (since > 1500L && map.indexNow() != null) {
               dev.chunkatlas.engine.WorldMap.Index i = map.indexNow();
               double x = (i.maxRx() + 3) * 512.0 + 256.0;
               double z = ((i.minRz() + i.maxRz()) / 2) * 512.0 + 256.0;
               map.seedCentre(x, z);
               map.tourEdit(true);
               say("seed map on: " + map.seedLayer().shown() + "; looking at " + (int)x + ", " + (int)z);
               next(now);
            }
         }
         case 4 -> {
            if (since > 2000L) {
               map.tourZoom(1.0 / map.seedZoom());
               next(now);
            }
         }
         case 5 -> {
            if (since > 6000L) {
               GenerateView view = map.tourGenerate();
               view.tourSelect(12, 12);
               dev.chunkatlas.engine.ChunkSelection sel = map.selectionIn(map.shownDimension());
               int[] mid = map.tourMiddleChunk();
               say("picked " + sel.size() + " chunks round chunk " + mid[0] + ", " + mid[1] + "; the map would generate " + view.count(sel, true)[0]);
               next(now);
            }
         }
         case 6 -> {
            if (since > 1000L) {
               shot(mc, "generate-picked");
               map.tourGenerate().open();
               next(now);
            }
         }
         case 7 -> {
            if (Game.screen(mc) instanceof GenerateCard card && since > 1200L) {
               shot(mc, "generate-card");
               generateBox = map.selectionIn(map.shownDimension()).copy();
               generateStart = now;
               card.tourGo(dev.chunkatlas.engine.ChunkGeneration.Speed.BALANCED);
               say("started at Balanced; game paused: " + mc.isPaused());
               next(now);
            } else if (since > 10000L) {
               say("no Generate card came up: " + (Game.screen(mc) == null ? "no screen" : Game.screen(mc).getClass().getSimpleName()));
               finish(mc);
            }
         }
         case 8 -> {
            dev.chunkatlas.engine.ChunkGeneration.Progress p = dev.chunkatlas.Generating.progress();
            if (p == null) {
               say("no run after starting");
               finish(mc);
            } else if (since >= 1000L) {
               at = now;
               say("generate +" + (now - generateStart) + " ms: " + p.state() + " " + p.done() + " / " + p.total() + ", left alone " + p.leftAlone()
                  + ", about " + p.secondsLeft() + " s left; still picked " + map.selectionIn(map.shownDimension()).size() + "; game paused: " + mc.isPaused()
                  + "; live: " + map.tourLive().tourSummary());

               if (!generateMid && !p.state().ended() && (p.done() * 2 >= p.total() || now - generateStart > 30000L)) {
                  generateMid = true;
                  shot(mc, "generate-running");
               }

               if (p.state().ended()) {
                  double s = (now - generateStart) / 1000.0;
                  say("generate ended " + p.state() + " after " + s + " s: " + Math.round(p.done() / Math.max(0.001, s)) + " chunks a second");
                  shot(mc, "generate-done");
                  next(now);
               } else if (now - generateStart > 600000L) {
                  say("generate still going after ten minutes; stopping it");
                  dev.chunkatlas.Generating.stop();
               }
            }
         }
         case 9 -> {
            if (since > 5000L) {
               int[] full = GenerateView.tourFull(map.tourRegionFolder(), generateBox);
               say("region files: " + full[0] + " of " + full[1] + " picked chunks saved with status full");
               shot(mc, "generate-after");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static final int[] taskTourIds = new int[2];
   private static int taskTourDone;
   private static double[] tourSpot;
   private static int tourSteps;

   private static String tasksLine() {
      StringBuilder sb = new StringBuilder();
      for (dev.chunkatlas.engine.ChunkTasks.View t : dev.chunkatlas.TaskClient.tasks()) {
         sb.append(" [").append(t.id()).append(' ').append(t.state()).append(' ').append(t.done()).append('/').append(t.total()).append(' ')
            .append(Math.round(t.rate())).append("/s ").append(t.secondsLeft()).append("s left, ").append(t.flying().length).append(" in flight]");
      }

      return sb.length() == 0 ? " none" : sb.toString();
   }

   private static int tasksDone() {
      int n = 0;
      for (dev.chunkatlas.engine.ChunkTasks.View t : dev.chunkatlas.TaskClient.tasks()) {
         n += t.done();
      }

      return n;
   }

   private static boolean leftWorld(Minecraft mc) {
      if (mc.level != null) {
         map = null;
         Game.leaveLevel(mc.level);
         mc.disconnect(new TitleScreen(), false);
         return false;
      }

      return Game.screen(mc) instanceof TitleScreen;
   }

   private static void tasksTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 5000L) {
               dev.chunkatlas.Settings.tasksRejoin(0);
               dev.chunkatlas.Settings.tasksHud(true);
               dev.chunkatlas.Settings.tasksHudCorner(0);
               MapScreen.tourKeepRunning = true;
               InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (map == null) {
               finish(mc);
            } else if (ready(since, 2000L, 30000L)) {
               map.tasksPanel().open(0);
               dev.chunkatlas.TaskClient.atOnce(2);
               next(now);
            }
         }
         case 3 -> {
            if (since > 1000L) {
               int px = (int)Math.floor(mc.player.getX());
               int pz = (int)Math.floor(mc.player.getZ());
               TasksPanel panel = map.tasksPanel();
               panel.tourSet(dev.chunkatlas.engine.Area.Shape.CIRCLE, px, pz, 400, 0, 0);
               panel.tourStart();
               panel.tourSet(dev.chunkatlas.engine.Area.Shape.SQUARE, px + 700, pz, 200, 0, 0);
               panel.tourStart();
               say("asked for a circle r 400 and a square r 200 round " + px + ", " + pz + "; at once: " + dev.chunkatlas.TaskClient.atOnce());
               next(now);
            }
         }
         case 4 -> {
            if (since > 5000L) {
               List<dev.chunkatlas.engine.ChunkTasks.View> tasks = dev.chunkatlas.TaskClient.tasks();
               say("tasks after 5 s:" + tasksLine());
               if (tasks.size() < 2) {
                  say("expected two tasks");
                  finish(mc);
                  return;
               }

               taskTourIds[0] = tasks.get(0).id();
               taskTourIds[1] = tasks.get(1).id();
               shot(mc, "tasks-two-running");
               map.tasksPanel().close();
               map.tourZoom(0.25);
               next(now);
            }
         }
         case 5 -> {
            if (since > 3000L) {
               shot(mc, "tasks-map-visuals");
               map.tourZoom(4.0);
               next(now);
            }
         }
         case 6 -> {
            if (since > 2000L) {
               shot(mc, "tasks-map-close");
               dev.chunkatlas.TaskClient.pause(taskTourIds[0], true);
               map.tasksPanel().open(0);
               next(now);
            }
         }
         case 7 -> {
            if (since > 2000L) {
               say("after pausing the first:" + tasksLine());
               shot(mc, "tasks-paused");
               dev.chunkatlas.TaskClient.pause(taskTourIds[0], false);
               next(now);
            }
         }
         case 8 -> {
            if (since > 2000L) {
               say("after resuming it:" + tasksLine());
               Game.open(mc, null);
               map = null;
               next(now);
            }
         }
         case 9 -> {
            if (since > 3000L && Game.screen(mc) == null) {
               shot(mc, "tasks-hud");
               taskTourDone = tasksDone();
               say("leaving with " + taskTourDone + " chunks done:" + tasksLine());
               next(now);
            }
         }
         case 10 -> {
            if (since > 1000L && leftWorld(mc)) {
               Path saved = ASKED.get("ingame") == null ? null : mc.gameDirectory.toPath().resolve("saves").resolve(ASKED.get("ingame").toString()).resolve("chunkatlas").resolve("tasks.dat");
               say("left the world; tasks saved: " + (saved != null && Files.isRegularFile(saved)));
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open again"));
               next(now);
            }
         }
         case 11 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 5000L) {
               int back = tasksDone();
               say("back with " + back + " chunks done (left with " + taskTourDone + "):" + tasksLine());
               shot(mc, "tasks-hud-rejoined");
               next(now);
            } else if (since > 90000L) {
               say("never got back into the world");
               finish(mc);
            }
         }
         case 12 -> {
            if (since > 5000L) {
               say("five seconds later, " + tasksDone() + " chunks done:" + tasksLine());
               InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               if (map != null) {
                  map.tasksPanel().open(0);
               }

               next(now);
            }
         }
         case 13 -> {
            if (map == null) {
               finish(mc);
            } else if (ready(since, 2000L, 30000L)) {
               shot(mc, "tasks-panel-rejoined");
               for (dev.chunkatlas.engine.ChunkTasks.View t : dev.chunkatlas.TaskClient.tasks()) {
                  dev.chunkatlas.TaskClient.cancel(t.id());
               }

               next(now);
            }
         }
         case 14 -> {
            if (since > 2000L) {
               say("after cancelling:" + tasksLine());
               shot(mc, "tasks-cancelled");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void borderTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      var server = mc.getSingleplayerServer();
      var sp = server == null || server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 5000L) {
               dev.chunkatlas.Settings.borderInWorld(true);
               MapScreen.tourKeepRunning = true;
               InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (map == null || sp == null) {
               finish(mc);
            } else if (ready(since, 2000L, 30000L)) {
               tourSpot = new double[]{Math.floor(sp.getX()) + 0.5, Math.floor(sp.getZ()) + 0.5};
               map.tasksPanel().tourBorder(dev.chunkatlas.engine.Area.Shape.CIRCLE, (int)Math.floor(tourSpot[0]), (int)Math.floor(tourSpot[1]), 48, false);
               next(now);
            }
         }
         case 3 -> {
            if (since > 2000L) {
               var b = dev.chunkatlas.TaskClient.border(Ids.of(mc.level.dimension()));
               say("border: " + (b == null ? "none" : b.area().write() + (b.wrap() ? " wrap" : "")));
               shot(mc, "border-panel");
               Game.open(mc, null);
               map = null;
               tourSteps = 0;
               next(now);
            }
         }
         case 4 -> {
            if (since > 150L && sp != null) {
               double x = sp.getX() + 2.0;
               double z = sp.getZ();
               server.execute(() -> {
                  sp.setYRot(-90.0F);
                  sp.teleportTo(x, sp.getY(), z);
               });
               at = now;
               if (++tourSteps >= 40) {
                  say("walked east 40 times; " + Math.round(sp.getX() - tourSpot[0]) + " blocks from the centre (radius 48)");
                  next(now);
               }
            }
         }
         case 5 -> {
            if (since > 1000L) {
               shot(mc, "border-walked");
               double x = tourSpot[0] + 200.0;
               server.execute(() -> sp.teleportTo(x, sp.getY() + 2.0, tourSpot[1]));
               next(now);
            }
         }
         case 6 -> {
            if (since > 1500L) {
               say("teleported 200 blocks out; now " + Math.round(Math.hypot(sp.getX() - tourSpot[0], sp.getZ() - tourSpot[1])) + " blocks from the centre");
               shot(mc, "border-teleport-redirected");
               var b = dev.chunkatlas.TaskClient.border(Ids.of(mc.level.dimension()));
               if (b != null) {
                  dev.chunkatlas.TaskClient.border(Ids.of(mc.level.dimension()), new dev.chunkatlas.engine.BorderBook.Border(b.area(), true));
               }

               tourSteps = 0;
               next(now);
            }
         }
         case 7 -> {
            if (since > 150L) {
               double x = sp.getX() + 2.0;
               double z = sp.getZ();
               server.execute(() -> sp.teleportTo(x, sp.getY(), z));
               at = now;
               if (sp.getX() < tourSpot[0] - 20.0 || ++tourSteps >= 40) {
                  say("wrap: after " + tourSteps + " steps the player is " + Math.round(sp.getX() - tourSpot[0]) + " blocks east of the centre");
                  next(now);
               }
            }
         }
         case 8 -> {
            if (since > 1500L) {
               shot(mc, "border-wrapped");
               dev.chunkatlas.TaskClient.border(Ids.of(mc.level.dimension()), null);
               next(now);
            }
         }
         case 9 -> {
            if (since > 1000L) {
               say("border after removing: " + dev.chunkatlas.TaskClient.border(Ids.of(mc.level.dimension())));
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void liveChunksTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 5000L) {
               var server = mc.getSingleplayerServer();

               int x = mc.player.getBlockX() + 8192;
               int z = mc.player.getBlockZ();
               server.execute(() -> {
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamemode creative @a");
                  for (var sp : server.getPlayerList().getPlayers()) {
                     sp.getAbilities().mayfly = true;
                     sp.getAbilities().flying = true;
                     sp.onUpdateAbilities();
                     sp.teleportTo(x, 200.0, z);
                  }
               });
               say("creative and flying before walking, moved to new land at " + x + ", " + z);
               dev.chunkatlas.Settings.footstepsFade(15);
               MapScreen.tourKeepRunning = true;
               InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (map == null) {
               finish(mc);
            } else if (ready(since, 10000L, 30000L)) {
               map.seedCentre(mc.player.getX(), mc.player.getZ());
               map.tourZoom(0.5);
               say("map open, game paused: " + mc.isPaused() + ", player at " + mc.player.getBlockX() + ", " + mc.player.getBlockZ() + "; before walking: "
                  + map.tourLive().tourSummary());
               walked = 0;
               next(now);
            }
         }
         case 3 -> {
            if (since > 150L) {
               var server = mc.getSingleplayerServer();
               var sp = server.getPlayerList().getPlayers().get(0);
               double x = sp.getX() + 6;
               double z = sp.getZ();
               server.execute(() -> {
                  sp.getAbilities().flying = true;
                  sp.onUpdateAbilities();
                  sp.teleportTo(x, Math.max(sp.getY(), 200.0), z);
               });
               map.seedCentre(x, z);
               at = now;
               if (++walked == 60) {
                  shot(mc, "livechunks-walking");
                  say("halfway: " + map.tourLive().tourSummary());
               }

               if (walked >= 120) {
                  next(now);
               }
            }
         }
         case 4 -> {
            int cx = mc.player.getBlockX() >> 4;
            int cz = mc.player.getBlockZ() >> 4;

            if (since > 3000L && (map.tourLive().tourHas(cx, cz) || inFiles(cx, cz) || since > 20000L)) {
               say("after walking to " + mc.player.getBlockX() + ", " + mc.player.getBlockZ() + " (" + since + " ms later): " + map.tourLive().tourSummary() + "; chunk under the player drawn live: " + map.tourLive().tourHas(cx, cz)
                  + ", two chunks ahead: " + map.tourLive().tourHas(cx + 2, cz) + "; under the player drawn from the files: " + inFiles(cx, cz));
               shot(mc, "livechunks-after");
               map.tourZoom(8.0);
               next(now);
            }
         }
         case 5 -> {
            if (since > 2000L) {
               shot(mc, "livechunks-footsteps");
               MapScreen.tourKeepRunning = false;
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static boolean inFiles(int cx, int cz) {
      long[] drawn = map.seedDrawn().get(dev.chunkatlas.engine.WorldLayout.key(cx >> 5, cz >> 5));
      int i = (cz & 31) * 32 + (cx & 31);
      return drawn != null && (drawn[i >> 6] & 1L << (i & 63)) != 0L;
   }

   private static int[] partialHop;

   private static final java.util.Set<Long> partialLeft = new java.util.HashSet<>();
   private static volatile int partialLeftCount = -1;
   private static final int PARTIAL_SIDE = 16;

   private static net.minecraft.world.level.chunk.status.ChunkStatus surfaceStep() {
      for (var status : net.minecraft.world.level.chunk.status.ChunkStatus.getStatusList()) {
         String name = status.toString();
         if (name.endsWith("terrain") || name.endsWith("surface")) {
            return status;
         }
      }

      throw new IllegalStateException("no terrain step");
   }

   private static void seedPartialTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 5000L) {
               int cx = (mc.player.getBlockX() + 3000) >> 4;
               int cz = (mc.player.getBlockZ() >> 4) - PARTIAL_SIDE / 2;
               partialHop = new int[]{cx, cz, PARTIAL_SIDE};
               var server = mc.getSingleplayerServer();
               server.execute(() -> {
                  partialLeft.clear();
                  for (int z = cz; z < cz + PARTIAL_SIDE; z++) {
                     for (int x = cx; x < cx + PARTIAL_SIDE; x++) {
                        partialLeft.add((long)x << 32 | z & 0xFFFFFFFFL);
                     }
                  }

                  partialLeftCount = partialLeft.size();
               });
               say("making chunks " + cx + ", " + cz + " to " + (cx + PARTIAL_SIDE - 1) + ", " + (cz + PARTIAL_SIDE - 1) + " up to their surface");
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            var server = mc.getSingleplayerServer();
            if (partialLeftCount == 0) {
               say("all " + PARTIAL_SIDE * PARTIAL_SIDE + " made up to their surface after " + since + " ms; letting go of them");
               next(now);
            } else if (since > 120000L) {
               say("still " + partialLeftCount + " not made after two minutes; going on");
               next(now);
            } else {
               server.execute(() -> {
                  var level = server.overworld();
                  var it = partialLeft.iterator();
                  while (it.hasNext()) {
                     long k = it.next();
                     var f = level.getChunkSource().getChunkFuture((int)(k >> 32), (int)k,
                        surfaceStep(), true);
                     if (f.isDone() && f.join().isSuccess()) {
                        it.remove();
                     }
                  }

                  partialLeftCount = partialLeft.size();
               });
            }
         }
         case 3 -> {
            if (since > 15000L) {
               var server = mc.getSingleplayerServer();
               server.execute(() -> {
                  var level = server.overworld();
                  int still = 0;
                  for (int z = partialHop[1]; z < partialHop[1] + PARTIAL_SIDE; z++) {
                     for (int x = partialHop[0]; x < partialHop[0] + PARTIAL_SIDE; x++) {
                        still += dev.chunkatlas.port.Tickets.busy(level, x, z) ? 1 : 0;
                     }
                  }

                  say(still + " of them still in memory; saving");
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "save-all flush");
               });
               next(now);
            }
         }
         case 4 -> {
            if (since > 5000L) {
               press(mc, "key.chunkatlas.open");
               step = 40;
               at = now;
            }
         }
         case 40 -> {
            if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
               w.show();
            } else if (Game.screen(mc) instanceof MapScreen m && since > 3000L) {
               map = m;
               if (!m.seedLayer().shown()) {
                  m.seedLayer().toggle();
               }

               map.seedCentre(partialHop[0] * 16.0, (partialHop[1] + PARTIAL_SIDE / 2) * 16.0);
               step = 5;
               at = now;
            }
         }
         case 5 -> {
            if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
               w.show();
            } else if (ready(since, 8000L, 40000L)) {
               int rx = Math.floorDiv((int)map.seedCx(), 512);
               int rz = Math.floorDiv((int)map.seedCz(), 512);
               StringBuilder b = new StringBuilder("regions round the block {saved, read, explored, half made}:");
               for (int dz = -1; dz <= 1; dz++) {
                  for (int dx = -1; dx <= 1; dx++) {
                     b.append(' ').append(rx + dx).append(',').append(rz + dz).append(' ').append(java.util.Arrays.toString(map.seedLayer().tourCover(rx + dx, rz + dz)));
                  }
               }

               say(b.toString());
               shot(mc, "seedpartial-edge");
               map.tourZoom(4.0);
               next(now);
            }
         }
         case 6 -> {
            if (ready(since, 6000L, 30000L)) {
               shot(mc, "seedpartial-close");
               map.seedLayer().toggle();
               next(now);
            }
         }
         case 7 -> {
            if (ready(since, 4000L, 20000L)) {
               shot(mc, "seedpartial-off");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void seedKeptTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 5000L) {
               press(mc, "key.chunkatlas.open");
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
               w.show();
            } else if (Game.screen(mc) instanceof MapScreen m && since > 3000L) {
               map = m;
               if (!m.seedLayer().shown()) {
                  m.seedLayer().toggle();
               }

               next(now);
            }
         }
         case 3 -> {
            if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
               w.show();
            } else if (since > 1500L && map.indexNow() != null) {
               dev.chunkatlas.engine.WorldMap.Index i = map.indexNow();
               map.seedCentre((i.maxRx() + 3) * 512.0 + 256.0, ((i.minRz() + i.maxRz()) / 2) * 512.0 + 256.0);
               map.tourZoom(4.0 / map.seedZoom());
               next(now);
            }
         }
         case 4 -> {
            if (since > 20000L) {
               shot(mc, "seedkept-close");
               say("close up: " + map.seedLayer().tourKept() + " close-ups kept");
               map.tourZoom(1.0 / 32.0);
               next(now);
            }
         }
         case 5 -> {
            if (since > 4000L) {
               shot(mc, "seedkept-far");
               say("zoomed out: " + map.seedLayer().tourKept() + " close-ups kept");
               map.tourZoom(8.0);
               next(now);
            }
         }
         case 6 -> {
            if (since > 4000L) {
               shot(mc, "seedkept-middle");
               map.tourZoom(4.0);
               dev.chunkatlas.Settings.detailed(false);
               next(now);
            }
         }
         case 7 -> {
            if (since > 12000L) {
               shot(mc, "seedkept-vanilla");
               dev.chunkatlas.Settings.detailed(true);
               dev.chunkatlas.Settings.night(true);
               next(now);
            }
         }
         case 8 -> {
            if (since > 12000L) {
               shot(mc, "seedkept-night");
               dev.chunkatlas.Settings.night(false);
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void heightTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 20) {
               title = t;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (since > 3000L) {
               map.tourZoom(0.25);
               map.tourColourBy(dev.chunkatlas.engine.TileRenderer.HEIGHTMAP);
               next(now);
            }
         }
         case 2 -> {
            if (since > 6000L) {
               shot(mc, "height");
               map.tourColourBy(dev.chunkatlas.engine.TileRenderer.DEPTHMAP);
               next(now);
            }
         }
         case 3 -> {
            if (since > 6000L) {
               shot(mc, "depth");
               map.tourColourBy(0);
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void endTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 3000L) {
               press(mc, "key.chunkatlas.open");
               next(now);
            } else if (Game.screen(mc) != null && since > 2000L && !(Game.screen(mc) instanceof MapScreen)) {
               Game.open(mc, null);
            }
         }
         case 2 -> {
            if (since > 3000L) {
               Game.open(mc, null);
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               server.execute(() -> {
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamemode creative @a");
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute in minecraft:the_end run tp @a 0 100 0");
               });
               next(now);
            }
         }
         case 3 -> {
            if (since > 6000L && mc.level != null && Game.screen(mc) == null) {
               say("now in " + Ids.of(mc.level.dimension()));
               press(mc, "key.chunkatlas.open");
               next(now);
            } else if (Game.screen(mc) != null && since > 2000L && !(Game.screen(mc) instanceof MapScreen)) {
               Game.open(mc, null);
            }
         }
         case 4 -> {
            if (since > 8000L) {
               if (Game.screen(mc) instanceof MapScreen m) {
                  say("map shows " + m.shownDimension() + ": " + m.tourState());
               } else {
                  say("no map open");
               }

               shot(mc, "end-map");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void press(Minecraft mc, String name) {
      for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
         if (k.getName().equals(name)) {
            net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(k.saveString()));
         }
      }
   }

   private static void xaeroTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null) {
               dev.chunkatlas.minimap.Config.get().enabled = false;
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               server.execute(() -> {
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule doDaylightCycle false");
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "spreadplayers -584 -146 0 6 false @a");
               });
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (Game.screen(mc) != null) {
               Game.open(mc, null);
            }

            if (since > 25000L && Game.screen(mc) == null) {
               shot(mc, "xaero-hud");
               press(mc, "gui.xaero_open_map");
               next(now);
            }
         }
         case 3 -> {
            if (since > 5000L) {
               say("xaero screen: " + (Game.screen(mc) == null ? "none" : Game.screen(mc).getClass().getName()));
               shot(mc, "xaero-map");
               Game.open(mc, null);
               next(now);
            }
         }
         case 4 -> {
            if (since > 1500L && Game.screen(mc) == null) {
               press(mc, "key.chunkatlas.open");
               next(now);
            }
         }
         case 5 -> {
            if (since > 5000L) {
               shot(mc, "atlas-map");
               finish(mc);
            }
         }
         default -> {
         }
      }
   }

   private static void minimapTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null) {
               dev.chunkatlas.minimap.waypoint.Waypoint w = new dev.chunkatlas.minimap.waypoint.Waypoint(
                  "Home", mc.player.getBlockX() + 24, mc.player.getBlockY(), mc.player.getBlockZ() - 16, Ids.of(mc.level.dimension())
               );
               w.colour = "red";
               dev.chunkatlas.minimap.waypoint.Waypoints.add(w);
               say("in the world; waypoints: " + dev.chunkatlas.minimap.waypoint.Waypoints.all().size());
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (since > 1500L && mc.getSingleplayerServer() != null && !movedForMinimap) {
               movedForMinimap = true;
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               server.execute(() -> {
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "spreadplayers -584 -146 0 6 false @a");
               });
            }

            if (Game.screen(mc) != null && since > 2000L) {
               Game.open(mc, null);
            }

            if (since > 3000L && !seedCompared && mc.getSingleplayerServer() != null) {
               seedCompared = true;
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               try {
                  dev.chunkatlas.seed.SeedSampler real = Seeds.open(server, "minecraft:overworld", dev.chunkatlas.GameColours.get());
                  dev.chunkatlas.seed.SeedSampler typed = null;
                  for (int tries = 0; typed == null && tries < 300; tries++) {
                     typed = Seeds.forSeed(real.seed(), "minecraft:overworld", dev.chunkatlas.GameColours.get());
                     if (typed == null) {
                        Thread.sleep(100L);
                     }
                  }
                  int same = 0;
                  int all = 0;
                  for (int x = -4000; x <= 4000; x += 400) {
                     for (int z = -4000; z <= 4000; z += 400) {
                        all++;
                        if (java.util.Objects.equals(real.biomeId(real.biomeAt(x, z)), typed.biomeId(typed.biomeAt(x, z)))) {
                           same++;
                        }
                     }
                  }

                  say("seed-only biomes match " + same + "/" + all + "; kinds " + real.kinds().equals(typed.kinds()) + " " + typed.kinds().size());

                  typed.keepHeightsIn(dev.chunkatlas.ChunkAtlas.home());
                  StringBuilder times = new StringBuilder("seed map timings:");
                  int prx = mc.player.getBlockX() >> 9;
                  int prz = mc.player.getBlockZ() >> 9;
                  for (int n : new int[]{16, 32, 64, 128, 256, 512}) {
                     long t0 = System.nanoTime();
                     typed.terrain(prx + 3, prz + 1, n, () -> false);
                     times.append(" ").append(n).append("px ").append((System.nanoTime() - t0) / 1000000L).append("ms");
                  }

                  long t1 = System.nanoTime();
                  typed.structures(prx + 3, prz + 2, false, () -> false);
                  times.append(" | quick starts ").append((System.nanoTime() - t1) / 1000000L).append("ms");
                  long t2 = System.nanoTime();
                  typed.structures(prx + 3, prz + 2, true, () -> false);
                  times.append(" | checked starts ").append((System.nanoTime() - t2) / 1000000L).append("ms");
                  long t3 = System.nanoTime();
                  typed.features(prx + 3, prz + 3, () -> false);
                  times.append(" | features ").append((System.nanoTime() - t3) / 1000000L).append("ms");
                  say(times.toString() + " | cores " + Runtime.getRuntime().availableProcessors());
               } catch (RuntimeException | InterruptedException e) {
                  say("seed-only compare failed: " + e);
               }
            }

            if (since > 6000L && hudShots == 0) {
               dev.chunkatlas.minimap.Config.get().enabled = false;
               hudShots = 1;
            } else if (since > 7500L && hudShots == 1 && Game.screen(mc) == null) {
               shot(mc, "hud-clean");
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight"));
               hudShots = 2;
            } else if (since > 10000L && hudShots == 2 && Game.screen(mc) == null) {
               shot(mc, "hud-night");
               dev.chunkatlas.minimap.Config.get().enabled = true;
               dev.chunkatlas.Settings.night(true);
               hudShots = 3;
            } else if (since > 12500L && hudShots == 3 && Game.screen(mc) == null) {
               shot(mc, "minimap-night");
               dev.chunkatlas.Settings.night(false);
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon"));
               hudShots = 4;
            }

            if (since > 15500L && hudShots == 4 && Game.screen(mc) == null) {
               shot(mc, "minimap-hud");
               dev.chunkatlas.InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               say("map open: " + (map != null));
               next(now);
            }
         }
         case 3 -> {
            if (map == null) {
               finish(mc);
            } else if (ready(since, 2000L, 40000L)) {
               map.tourZoom(4.0);
               next(now);
            }
         }
         case 4 -> {
            if (ready(since, 1500L, 30000L)) {
               shot(mc, "minimap-worldmap");

               List<dev.chunkatlas.minimap.waypoint.Waypoint> all = dev.chunkatlas.minimap.waypoint.Waypoints.all();
               Game.open(mc, new dev.chunkatlas.minimap.gui.WaypointsScreen(map, all.isEmpty() ? null : all.get(0)));
               next(now);
            }
         }
         case 5 -> {
            if (since > 1200L) {
               shot(mc, "waypoints-panel");
               settings = new SettingsScreen(map);
               Game.open(mc, settings);
               next(now);
            }
         }
         case 6 -> {
            if (since > 1000L) {
               settings.tourTab(SettingsScreen.MINIMAP);
               next(now);
            }
         }
         case 7 -> {
            if (since > 1000L) {
               shot(mc, "minimap-settings");
               say("imported from Xaero's: " + dev.chunkatlas.minimap.waypoint.Waypoints.importXaero(mc)
                  + ", again: " + dev.chunkatlas.minimap.waypoint.Waypoints.importXaero(mc));
               List<dev.chunkatlas.minimap.waypoint.Waypoint> all = dev.chunkatlas.minimap.waypoint.Waypoints.all();
               Game.open(mc, new dev.chunkatlas.minimap.gui.WaypointsScreen(null, all.isEmpty() ? null : all.get(0)));
               next(now);
            }
         }
         case 8 -> {
            if (since > 1200L) {
               shot(mc, "waypoints-screen");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static boolean seedWasDetailed;
   private static boolean seedWasUnderground;
   private static dev.chunkatlas.minimap.Config.Cave seedWasCave;
   private static dev.chunkatlas.minimap.Config.Shape seedWasShape;
   private static int seedWasZoom;
   private static boolean seedWasEnabled;
   private static int seedWasDistance;

   private static void minimapSeedTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      dev.chunkatlas.minimap.Config c = dev.chunkatlas.minimap.Config.get();
      dev.chunkatlas.minimap.map.SeedTiles seed = dev.chunkatlas.minimap.Minimap.CACHE.seed();
      boolean settled = Game.screen(mc) == null && (seed.idle() || since > 60000L);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && mc.getSingleplayerServer() != null && since > 3000L) {
               seedWasEnabled = c.enabled;
               seedWasShape = c.shape;
               seedWasZoom = c.zoom;
               c.enabled = true;
               c.seedMap = true;
               c.shape = dev.chunkatlas.minimap.Config.Shape.SQUARE;
               c.zoom = 0;
               seedWasDetailed = dev.chunkatlas.Settings.detailed();
               seedWasUnderground = dev.chunkatlas.Settings.undergroundCaves();
               seedWasCave = c.cave;
               seedWasDistance = mc.options.renderDistance().get();
               mc.options.renderDistance().set(6);

               java.nio.file.Path regions = mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("region");
               int rz = mc.player.getBlockZ() >> 9;
               int east = Integer.MIN_VALUE;
               try (java.util.stream.Stream<Path> files = Files.list(regions)) {
                  for (Path f : (Iterable<Path>)files::iterator) {
                     String[] p = f.getFileName().toString().split("\\.");
                     if (p.length == 4 && p[0].equals("r") && p[3].equals("mca") && Integer.parseInt(p[2]) == rz) {
                        east = Math.max(east, Integer.parseInt(p[1]));
                     }
                  }
               } catch (IOException | RuntimeException e) {
                  say("could not list the regions: " + e);
               }

               int x = east == Integer.MIN_VALUE ? mc.player.getBlockX() : (east + 1) * 512 - 48;
               int z = mc.player.getBlockZ();
               say("seed on the minimap: going to " + x + "," + z + " (east edge of region row " + rz + ")");
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               server.execute(() -> {
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");

                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "spreadplayers " + x + " " + z + " 0 24 false @a");
               });
               next(now);
            } else if (Game.screen(mc) != null && since > 2000L) {
               Game.open(mc, null);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (since > 8000L && settled) {
               say("square after " + since + " ms at " + mc.player.getBlockX() + "," + mc.player.getBlockZ() + ", render distance "
                  + mc.options.getEffectiveRenderDistance() + ": " + seed.stats());
               shot(mc, "minimap-seed");
               c.shape = dev.chunkatlas.minimap.Config.Shape.CIRCLE;
               next(now);
            }
         }
         case 3 -> {
            if (since > 3000L && settled) {
               say("round: " + seed.stats());
               shot(mc, "minimap-seed-circle");

               dev.chunkatlas.Settings.detailed(true);
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight"));
               next(now);
            }
         }
         case 4 -> {
            if (since > 6000L && settled) {
               say("night: " + seed.stats());
               shot(mc, "minimap-seed-night");
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon"));
               dev.chunkatlas.Settings.undergroundCaves(true);
               c.cave = dev.chunkatlas.minimap.Config.Cave.ALWAYS;
               next(now);
            }
         }
         case 5 -> {
            if (since > 6000L && settled) {
               say("cave: " + seed.stats());
               shot(mc, "minimap-seed-cave");
               dev.chunkatlas.Settings.detailed(seedWasDetailed);
               dev.chunkatlas.Settings.undergroundCaves(seedWasUnderground);
               c.cave = seedWasCave;
               c.shape = seedWasShape;
               c.zoom = seedWasZoom;
               c.enabled = seedWasEnabled;
               c.seedMap = false;
               mc.options.renderDistance().set(seedWasDistance);
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void pasteTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen && ++titleTicks >= 60) {
               title = screen;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourEdit(true);
               map.tourSelect(3, 2);
               say("before: " + map.tourState());
               map.tourCopy();
               next(now);
            }
         }
         case 2 -> {
            if (since > 500L && map.tourIdle()) {
               say("copy: " + map.tourBanner());
               map.tourPaste();
               say("pasting: " + map.tourPasting());
               next(now);
            }
         }
         case 3 -> {
            if (since > 800L) {
               shot(mc, "paste-preview");
               map.tourPlace(40, 0);
               next(now);
            }
         }
         case 4 -> {
            if (screen instanceof ConfirmEditScreen confirm) {
               if (since > 1200L) {
                  shot(mc, "paste-confirm");
                  confirm.go();
                  next(now);
               }
            } else if (since > 20000L) {
               say("no confirm came up: " + map.tourBanner());
               finish(mc);
            }
         }
         case 5 -> {
            if (screen == map && ready(since, 1500L, 40000L)) {
               map.tourPan(40 * 16, 0);
               next(now);
            } else if (since > 60000L) {
               say("the paste did not come back to the map");
               finish(mc);
            }
         }
         case 6 -> {
            if (ready(since, 1500L, 30000L)) {
               shot(mc, "paste-after");
               say("after: " + map.tourState() + " banner " + map.tourBanner());
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static double blendX;
   private static double blendZ;
   private static Path blendRegions;

   private static String chunkNote(Path regionFolder, int cx, int cz) {
      Path file = regionFolder.resolve("r." + (cx >> 5) + "." + (cz >> 5) + ".mca");
      int index = (cz & 31) << 5 | cx & 31;
      try (dev.chunkatlas.engine.RegionFile region = dev.chunkatlas.engine.RegionFile.open(file)) {
         if (!region.header().present(index)) {
            return "absent";
         }

         java.util.Map<String, Object> root = dev.chunkatlas.engine.Nbt.read(region.chunk(index).data());
         return "present, blending " + root.get("blending_data") + ", status " + root.get("Status");
      } catch (java.io.IOException | RuntimeException e) {
         return "unreadable " + e;
      }
   }

   private static void newWorldTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               String name = String.valueOf(ASKED.get("newworldtour"));
               mc.createWorldOpenFlows().createFreshLevel(
                  name,
                  Game.creative(name),
                  net.minecraft.world.level.levelgen.WorldOptions.defaultWithRandomSeed(),
                  net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions,
                  t
               );
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 25000L) {
               net.minecraft.core.BlockPos spawn = mc.player.blockPosition();
               say("new world seed " + mc.getSingleplayerServer().overworld().getSeed() + ", player at " + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ()
                  + ", chunk " + (spawn.getX() >> 4) + "," + (spawn.getZ() >> 4));
               finish(mc);
            } else if (Game.screen(mc) != null && since > 4000L && mc.level != null) {
               Game.open(mc, null);
            }
         }
         default -> finish(mc);
      }
   }

   private static int[] forcedAt;

   private static void loadedTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (playing(mc) && since > 8000L) {
               forcedAt = new int[]{Math.floorDiv(mc.player.getBlockX(), 16) + 48, Math.floorDiv(mc.player.getBlockZ(), 16)};
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               String dim = Ids.of(mc.player.level().dimension());
               String here = forcedAt[0] * 16 + " " + forcedAt[1] * 16;
               String east = (forcedAt[0] + 1) * 16 + " " + forcedAt[1] * 16;
               server.execute(() -> {
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute in " + dim + " run forceload add " + here);
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute in " + dim + " run forceload add " + east);
               });
               say("force-loading chunks " + forcedAt[0] + "," + forcedAt[1] + " and " + (forcedAt[0] + 1) + "," + forcedAt[1] + " in " + dim
                  + "; player in chunk " + Math.floorDiv(mc.player.getBlockX(), 16) + "," + Math.floorDiv(mc.player.getBlockZ(), 16));
               next(now);
            }
         }
         case 2, 12 -> {
            if (playing(mc) && since > 6000L) {
               press(mc, "key.chunkatlas.open");
               next(now);
            } else if (screen != null && since > 2000L && !(screen instanceof MapScreen)) {
               Game.open(mc, null);
            }
         }
         case 3, 13 -> {
            if (screen instanceof MapScreen m && since > 3000L) {
               map = m;
               dev.chunkatlas.Settings.loadedChunks(true);
               dev.chunkatlas.Settings.loadedTab(0);
               say("layer " + (m.loadedLayer().unavailable() == null ? "available" : m.loadedLayer().unavailable()));
               next(now);
            } else if (since > 30000L) {
               say("no map after the key");
               finish(mc);
            }
         }
         case 4, 14 -> {
            if (since > 3000L) {
               say("snapshot: " + map.loadedLayer().tourSummary());
               say(map.loadedLayer().tourAt(forcedAt[0], forcedAt[1]));
               say(map.loadedLayer().tourAt(forcedAt[0] + 1, forcedAt[1]));
               say(map.loadedLayer().tourAt(Math.floorDiv(mc.player.getBlockX(), 16), Math.floorDiv(mc.player.getBlockZ(), 16)));
               sayForced(mc);
               shot(mc, step == 14 ? "loaded-after" : "loaded-level");
               dev.chunkatlas.Settings.loadedTab(1);
               step = step == 14 ? 15 : 5;
               at = now;
            }
         }
         case 5 -> {
            if (since > 1500L) {
               shot(mc, "loaded-by");
               dev.chunkatlas.Settings.loadedTab(2);
               next(now);
            }
         }
         case 6 -> {
            if (since > 1500L) {
               shot(mc, "loaded-generation");
               dev.chunkatlas.Settings.loadedTab(0);
               say(map.loadedLayer().tourStep(1));
               next(now);
            }
         }
         case 7 -> {
            if (since > 2000L) {
               shot(mc, "loaded-step");
               int bx = forcedAt[0] * 16 + 8;
               int bz = forcedAt[1] * 16 + 8;
               map.centreOn(map.seedDimension(), bx, bz, -1, -1);
               map.tourContext(bx, bz);
               next(now);
            }
         }
         case 8 -> {
            if (since > 1500L) {
               shot(mc, "loaded-context");
               map.tourCloseMenu();
               map.tourEdit(true);
               map.tourSelect(0, 0);
               say("selected for Change fields: " + map.tourState());
               map.tourOpenFields();
               next(now);
            }
         }
         case 9 -> {
            if (screen instanceof FieldsScreen f && since > 800L) {
               f.tourTick("force_load", "off");
               next(now);
            } else if (since > 5000L) {
               say("Change fields didn't open (the force-loaded chunk isn't saved yet?)");
               step = 11;
               at = now;
            }
         }
         case 10 -> {
            if (screen instanceof FieldsScreen f && since > 800L) {
               shot(mc, "loaded-fields");
               f.tourGo();
               next(now);
            }
         }
         case 11 -> {
            if (since > 2000L) {
               say("after Change fields: " + map.tourBanner());
               sayForced(mc);
               map.tourEdit(false);

               Game.open(mc, null);
               next(now);
            }
         }
         case 15 -> {
            if (since > 1500L) {
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void sayForced(Minecraft mc) {
      net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
      String dim = Ids.of(mc.player.level().dimension());
      server.execute(() -> {
         net.minecraft.server.level.ServerLevel level = dev.chunkatlas.net.ServerLoads.level(server, dim);
         List<String> out = new java.util.ArrayList<>();
         for (long p : level == null ? new long[0] : dev.chunkatlas.port.Tickets.forced(level)) {
            out.add(net.minecraft.world.level.ChunkPos.getX(p) + "," + net.minecraft.world.level.ChunkPos.getZ(p));
         }

         say("the game's force-loaded chunks in " + dim + ": " + out);
      });
   }

   private static int mobZoom;

   private static void mobTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      dev.chunkatlas.minimap.Config cfg = dev.chunkatlas.minimap.Config.get();
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && Game.screen(mc) == null && since > 6000L) {
               String[] kinds = {"zombie", "skeleton", "creeper", "spider", "enderman", "witch", "cow", "pig", "sheep", "chicken", "villager", "wolf", "fox", "iron_golem", "slime", "goat"};
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               server.execute(() -> {
                  List<String> commands = new java.util.ArrayList<>(List.of("difficulty normal", "time set noon"));
                  for (int i = 0; i < kinds.length; i++) {
                     double a = Math.PI * 2.0 * i / kinds.length;
                     double r = 6.0 + i % 3 * 3.0;
                     commands.add(String.format(java.util.Locale.ROOT, "execute at @p run summon minecraft:%s ~%.1f ~ ~%.1f {NoAI:1b,PersistenceRequired:1b,Rotation:[%df,0f]}",
                        kinds[i], Math.cos(a) * r, Math.sin(a) * r, i * 45));
                  }

                  commands.add("execute at @p run summon minecraft:bat ~3 ~14 ~3 {NoAI:1b,PersistenceRequired:1b}");
                  commands.add("effect give @e[type=!player] fire_resistance infinite 0 true");
                  for (String c : commands) {
                     server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
                  }
               });
               next(now);
            } else if (Game.screen(mc) != null && since > 2000L && !(Game.screen(mc) instanceof MapScreen)) {
               Game.open(mc, null);
            }
         }
         case 2 -> {
            if (since > 3000L) {
               mobZoom = cfg.zoom;
               cfg.enabled = true;
               cfg.radar = true;
               cfg.radarHostile = true;
               cfg.radarFriendly = true;

               cfg.zoom = 5;
               cfg.radarStyle = dev.chunkatlas.minimap.Config.RadarStyle.FACES;
               StringBuilder seen = new StringBuilder();
               List<net.minecraft.world.entity.Entity> near = new MobMarks.Near().collect(mc, mc.player, cfg);
               for (net.minecraft.world.entity.Entity e : near) {
                  int c = MobMarks.colour(e);
                  seen.append(' ').append(net.minecraft.world.entity.EntityType.getKey(e.getType()).getPath())
                     .append(MobMarks.hasFace(e) ? " face" : " dot")
                     .append(c == MobMarks.HOSTILE ? "/hostile" : c == MobMarks.NEUTRAL ? "/neutral" : "/passive")
                     .append(MobMarks.alpha(e, mc.player) == MobMarks.FADED ? "/faded" : "");
               }

               say("mobs near you: " + near.size() + seen);
               next(now);
            }
         }
         case 3, 4, 5 -> {
            if (since > 1500L && Game.screen(mc) == null) {
               shot(mc, "mobs-minimap-" + cfg.radarStyle.name().toLowerCase(java.util.Locale.ROOT));
               cfg.radarStyle = dev.chunkatlas.minimap.Config.RadarStyle.values()[(cfg.radarStyle.ordinal() + 1) % 3];
               if (step == 5) {
                  cfg.zoom = mobZoom;
                  dev.chunkatlas.Settings.mobs(true);
                  dev.chunkatlas.InGame.open(mc, null);
                  map = Game.screen(mc) instanceof MapScreen m ? m : null;
                  say("map open: " + (map != null));
               }

               next(now);
            }
         }
         case 6 -> {
            if (map == null) {
               finish(mc);
            } else if (since > 4000L) {
               map.tourZoom(2.0);
               next(now);
            }
         }
         case 7, 8, 9 -> {
            if (since > 2500L) {
               shot(mc, "mobs-map-" + cfg.radarStyle.name().toLowerCase(java.util.Locale.ROOT));
               cfg.radarStyle = dev.chunkatlas.minimap.Config.RadarStyle.values()[(cfg.radarStyle.ordinal() + 1) % 3];
               next(now);
            }
         }
         default -> {
            dev.chunkatlas.Settings.mobs(false);
            finish(mc);
         }
      }
   }

   private static void dimTeleportTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 6000L) {
               say("before: " + Ids.of(mc.player.level().dimension()) + " " + mc.player.blockPosition());
               press(mc, "key.chunkatlas.open");
               next(now);
            }
         }
         case 2 -> {
            if (Game.screen(mc) instanceof MapScreen m && since > 3000L) {
               m.tourShow("minecraft:the_nether");
               next(now);
            }
         }
         case 3 -> {
            if (Game.screen(mc) instanceof MapScreen m && since > 3000L) {
               m.tourTeleport(40, 40);
               next(now);
            }
         }
         case 4 -> {
            if (since > 8000L) {
               say("after: " + Ids.of(mc.player.level().dimension()) + " " + mc.player.blockPosition());
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void findSelectTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               dev.chunkatlas.Settings.editWithoutCheats(true);
               map.tourEdit(true);
               map.tourFind("f1;all;DISTANCE_SPAWN:AT_MOST:300");
               next(now);
            }
         }
         case 2 -> {
            if (since > 2000L && map.tourIdle()) {
               say("after Find: " + map.tourBanner() + " | " + map.tourState());
               shot(mc, "find-selected");
               finish(mc);
            } else if (since > 60000L) {
               say("Find never finished");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static int[] clipBox;

   private static boolean pasteHeld;

   private static void clipBlendTour(Minecraft mc, String tour) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      String[] arg = String.valueOf(ASKED.get(tour)).split("@");
      int blend = arg.length > 1 ? Integer.parseInt(arg[1]) : 4;
      boolean smart = tour.equals("smartblendtour");
      String shots = smart ? "smartblend-" : "clipblend-";
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen && ++titleTicks >= 60) {
               title = screen;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               dev.chunkatlas.engine.ChunkClips.Clip clip = null;
               for (dev.chunkatlas.engine.ChunkClips.Clip c : dev.chunkatlas.engine.ChunkClips.list(dev.chunkatlas.ChunkAtlas.home())) {
                  if (c.name().equalsIgnoreCase(arg[0])) {
                     clip = c;
                  }
               }

               if (clip == null) {
                  say("no export named " + arg[0]);
                  finish(mc);
                  return;
               }

               dev.chunkatlas.engine.ChunkSelection chunks = dev.chunkatlas.engine.ChunkClips.chunks(clip.folder());
               int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
               chunks.forEach((x, z) -> {
                  box[0] = Math.min(box[0], x);
                  box[1] = Math.min(box[1], z);
                  box[2] = Math.max(box[2], x);
                  box[3] = Math.max(box[3], z);
               });
               clipBox = box;
               say("export " + clip.name() + ": " + chunks.size() + " chunks, x " + box[0] + ".." + box[2] + ", z " + box[1] + ".." + box[3] + ", blend " + blend);
               dev.chunkatlas.Settings.pasteBlend(blend);
               dev.chunkatlas.Settings.pasteSmart(smart);
               pasteHeld = false;

               dev.chunkatlas.Settings.editWithoutCheats(true);
               map.startPaste(clip, chunks, null);
               next(now);
            }
         }
         case 2 -> {
            int dx = 0;
            int dz = 0;
            if (arg.length > 2) {
               String[] at2 = arg[2].split(",");
               dx = Integer.parseInt(at2[0]) - ((clipBox[0] + clipBox[2]) >> 1);
               dz = Integer.parseInt(at2[1]) - ((clipBox[1] + clipBox[3]) >> 1);
            }

            if (smart && !pasteHeld && since > 1500L) {
               map.tourHoldPaste(dx, dz);
               map.centreOn(map.seedDimension(), ((clipBox[0] + clipBox[2]) / 2.0 + dx) * 16.0 + 8.0, ((clipBox[1] + clipBox[3]) / 2.0 + dz) * 16.0 + 8.0, -1, -1);
               pasteHeld = true;
               at = now;
            } else if (smart && pasteHeld && (map.tourPasteSettled() || since > 30000L)) {
               say("smart blend: " + map.tourPasteState());
               shot(mc, shots + "paste-" + arg[0]);
               clipBox = new int[]{clipBox[0] + dx, clipBox[1] + dz, clipBox[2] + dx, clipBox[3] + dz};
               map.tourPlace(dx, dz);
               next(now);
            } else if (!smart && since > 1500L) {
               shot(mc, shots + "paste-" + arg[0]);
               clipBox = new int[]{clipBox[0] + dx, clipBox[1] + dz, clipBox[2] + dx, clipBox[3] + dz};
               map.tourPlace(dx, dz);
               next(now);
            }
         }
         case 3 -> {
            if (screen instanceof ConfirmEditScreen confirm) {
               if (since > 1200L) {
                  shot(mc, shots + "confirm-" + arg[0]);
                  confirm.go();
                  next(now);
               }
            } else if (since > 20000L) {
               say("no confirm came up: " + map.tourBanner());
               finish(mc);
            }
         }
         case 4 -> {
            if (screen == map && ready(since, 1500L, 60000L)) {
               Path regions = map.tourRegionFolder();
               blendRegions = regions;
               int cx = (clipBox[0] + clipBox[2]) >> 1;
               int cz = (clipBox[1] + clipBox[3]) >> 1;
               say("pasted centre " + chunkNote(regions, cx, cz));
               say("pasted east edge " + chunkNote(regions, clipBox[2], cz));
               for (int d = 1; d <= blend + 2; d++) {
                  say("east +" + d + ": " + chunkNote(regions, clipBox[2] + d, cz));
               }

               next(now);
            } else if (since > 90000L) {
               say("the paste did not come back to the map");
               finish(mc);
            }
         }
         case 5 -> {
            if (since > 1500L) {
               if (smart) {
                  say("smart blend placed with: " + map.tourPasteState());
               }

               shot(mc, shots + "map-before-" + arg[0]);
               Game.open(mc, title);
               next(now);
            }
         }
         case 6 -> {
            if (since > 1500L && Game.screen(mc) instanceof TitleScreen) {
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 7 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null) {
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               int x = clipBox[2] * 16 + 8 + blend * 16 / 2;
               int z = (clipBox[1] + clipBox[3]) * 8;
               server.execute(() -> {
                  var src = server.createCommandSourceStack();
                  server.getCommands().performPrefixedCommand(src, "time set noon");
                  server.getCommands().performPrefixedCommand(src, "gamemode spectator @a");
                  server.getCommands().performPrefixedCommand(src, "tp @a " + x + " 220 " + z + " 0 90");
               });
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 8 -> {
            if (Game.screen(mc) != null && since > 2000L) {
               Game.open(mc, null);
            }

            if (since > 35000L && Game.screen(mc) == null) {
               shot(mc, shots + "above-" + arg[0]);
               int x = clipBox[2] * 16 + 8 + blend * 16 + 40;
               int z = (clipBox[1] + clipBox[3]) * 8;
               mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getCommands().performPrefixedCommand(
                  mc.getSingleplayerServer().createCommandSourceStack(), "tp @a " + x + " 140 " + z + " 90 25"
               ));
               next(now);
            }
         }
         case 9 -> {
            if (since > 8000L) {
               shot(mc, shots + "side-" + arg[0]);
               int cz = (clipBox[1] + clipBox[3]) >> 1;
               for (int d = 1; d <= blend + 2; d++) {
                  say("after playing, east +" + d + ": " + chunkNote(blendRegions, clipBox[2] + d, cz));
               }

               say("after playing, pasted east edge " + chunkNote(blendRegions, clipBox[2], cz));
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void scaleTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      int scale = 1 + (step - 1) / 5;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         default -> {
            if (scale > 4) {
               mc.options.guiScale().set(0);
               Game.resizeGui(mc);
               finish(mc);
               return;
            }

            int sub = (step - 1) % 5;
            if (sub == 0 && since > 1500L && step > 1 && Game.screen(mc) instanceof SettingsScreen) {
               shot(mc, "scale" + (scale - 1) + "-keys");
            }

            if (sub == 0 && since > 1600L) {
               mc.options.guiScale().set(scale);
               Game.resizeGui(mc);
               Game.open(mc, map);
               map.tourEdit(false);
               next(now);
            } else if (sub == 1 && since > 2500L) {
               shot(mc, "scale" + scale + "-map");
               map.tourEdit(true);
               map.tourSelect(3, 2);
               next(now);
            } else if (sub == 2 && since > 1500L) {
               shot(mc, "scale" + scale + "-edit");
               map.tourPanel(true);
               next(now);
            } else if (sub == 3 && since > 1500L) {
               shot(mc, "scale" + scale + "-find");
               map.tourPanel(false);
               next(now);
            } else if (sub == 4 && since > 1500L) {
               shot(mc, "scale" + scale + "-settings");
               map.tourSettingsTab(SettingsScreen.KEYS);
               next(now);
            }
         }
      }
   }

   private static void blendTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen && ++titleTicks >= 60) {
               title = screen;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourEdit(true);
               map.tourSelect(3, 3);
               blendX = map.tourCentre()[0] + 40 * 16;
               blendZ = map.tourCentre()[1];
               map.tourCopy();
               next(now);
            }
         }
         case 2 -> {
            if (since > 500L && map.tourIdle()) {
               dev.chunkatlas.Settings.pasteBlend(4);
               map.tourPaste();
               next(now);
            }
         }
         case 3 -> {
            if (since > 800L) {
               shot(mc, "blend-paste-bar");
               map.tourPlace(40, 0);
               next(now);
            }
         }
         case 4 -> {
            if (screen instanceof ConfirmEditScreen confirm) {
               if (since > 1200L) {
                  shot(mc, "blend-confirm");
                  confirm.go();
                  next(now);
               }
            } else if (since > 20000L) {
               say("no confirm came up: " + map.tourBanner());
               finish(mc);
            }
         }
         case 5 -> {
            if (screen == map && ready(since, 1500L, 40000L)) {
               Path regions = map.tourRegionFolder();
               blendRegions = regions;
               int cx = (int)Math.floor(blendX) >> 4;
               int cz = (int)Math.floor(blendZ) >> 4;
               say("pasted centre " + chunkNote(regions, cx, cz));
               say("pasted edge " + chunkNote(regions, cx + 3, cz));
               say("ring 2 out " + chunkNote(regions, cx + 5, cz));
               say("ring 4 out " + chunkNote(regions, cx + 7, cz));
               say("past the ring " + chunkNote(regions, cx + 9, cz));
               map.tourPan(40 * 16, 0);
               next(now);
            } else if (since > 60000L) {
               say("the paste did not come back to the map");
               finish(mc);
            }
         }
         case 6 -> {
            if (ready(since, 1500L, 30000L)) {
               shot(mc, "blend-map-before");
               Game.open(mc, title);
               next(now);
            }
         }
         case 7 -> {
            if (since > 1500L && Game.screen(mc) instanceof TitleScreen) {
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 8 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null) {
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               String x = String.valueOf((int)blendX);
               String z = String.valueOf((int)blendZ);
               server.execute(() -> {
                  var src = server.createCommandSourceStack();
                  server.getCommands().performPrefixedCommand(src, "time set noon");
                  server.getCommands().performPrefixedCommand(src, "gamemode spectator @a");
                  server.getCommands().performPrefixedCommand(src, "tp @a " + x + " 200 " + z + " 0 90");
               });
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 9 -> {
            if (Game.screen(mc) != null && since > 2000L) {
               Game.open(mc, null);
            }

            if (since > 30000L && Game.screen(mc) == null) {
               shot(mc, "blend-world-above");
               Path regions = blendRegions;
               if (regions != null) {
                  int cx = (int)Math.floor(blendX) >> 4;
                  int cz = (int)Math.floor(blendZ) >> 4;
                  say("after playing, ring 2 out " + chunkNote(regions, cx + 5, cz));
               }

               press(mc, "key.chunkatlas.open");
               next(now);
            }
         }
         case 10 -> {
            if (since > 6000L) {
               shot(mc, "blend-map-after");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void seedLookTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen && ++titleTicks >= 60) {
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 3000L) {
               mc.player.connection.sendCommand("spreadplayers -584 -146 0 6 false @s");
               next(now);
            } else if (since > 90000L) {
               finish(mc);
            }
         }
         case 2 -> {
            if (since > 3000L) {
               dev.chunkatlas.InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               next(now);
            }
         }
         case 3 -> {
            if (map == null) {
               finish(mc);
            } else if (ready(since, 2000L, 30000L)) {
               map.tourZoom(0.125);
               next(now);
            }
         }
         case 4 -> {
            if (since > 4000L && !seedToggled) {
               shot(mc, "seedlook-off-far");
               seedToggled = true;
               if (!map.seedLayer().shown()) {
                  map.seedLayer().toggle();
               }
            }

            if (Game.screen(mc) instanceof SeedMapWarningScreen w) {
               w.show();
            }

            if (since > 5500L) {
               StringBuilder cmp = new StringBuilder("seed vs saved:");
               for (int[] p : new int[][]{{-584, -146}, {-560, -131}, {-101, -407}, {563, 381}, {0, 0}, {200, -200}, {-300, 600}}) {
                  java.nio.file.Path region = map.tourRegionFolder().resolve("r." + (p[0] >> 9) + "." + (p[1] >> 9) + ".mca");
                  short[] saved = dev.chunkatlas.engine.ChunkBiomes.read(region, p[0] >> 4, p[1] >> 4, dev.chunkatlas.GameColours.get());
                  String s = saved == null ? "-" : dev.chunkatlas.engine.BiomeIndex.name(saved[(p[1] & 15) * 16 + (p[0] & 15)]);
                  cmp.append(" ").append(p[0]).append(",").append(p[1]).append(" seed=").append(map.seedLayer().biomeHere(p[0], p[1])).append(" saved=").append(s);
               }

               say(cmp.toString());
               say("seed map on: " + map.seedLayer().shown() + " available " + map.seedLayer().available() + " seed " + map.seedLayer().seedFor() + " screen " + (Game.screen(mc) == null ? "none" : Game.screen(mc).getClass().getSimpleName()));
               next(now);
            }
         }
         case 5 -> {
            if (since > 2000L && (map.seedLayer().tourIdle() || since > 60000L)) {
               say("far done after " + since + " ms; " + map.seedLayer().tourState());
               say("coverage far:" + map.tourCoverage());
               shot(mc, "seedlook-far");

               map.tourZoom(0.5);
               next(now);
            }
         }
         case 6 -> {
            if (since > 2000L && (map.seedLayer().tourIdle() || since > 60000L)) {
               say("mid done after " + since + " ms; " + map.seedLayer().tourState());
               shot(mc, "seedlook-mid");
               map.tourZoom(16.0);
               next(now);
            }
         }
         case 7 -> {
            if (since > 2000L && (map.seedLayer().tourIdle() || since > 60000L)) {
               say("near done after " + since + " ms; " + map.seedLayer().tourState());
               shot(mc, "seedlook-near");
               map.tourZoom(1.0 / 16.0);
               map.tourCave(30);
               next(now);
            }
         }
         case 8 -> {
            if (since > 2000L && (map.seedLayer().tourIdle() || since > 60000L)) {
               say("cave done after " + since + " ms; " + map.seedLayer().tourState());
               int bx = (int)Math.floor(map.seedCx());
               int bz = (int)Math.floor(map.seedCz());
               dev.chunkatlas.seed.SeedSampler w = map.seedLayer().world();
               String top = w == null ? null : w.biomeId(w.biomeAt(bx, bz));
               say("cave read-out at " + bx + "," + bz + " y " + map.seedCaveY() + ": " + map.seedLayer().biomeName(bx, bz)
                  + " (surface " + (top == null ? "?" : SeedMapLayer.biomeName(top)) + ")");
               shot(mc, "seedlook-cave");

               map.tourCave(Integer.MIN_VALUE);
               map.tourZoom(32.0);
               map.tourPan(166, 3487);
               next(now);
            }
         }
         case 9, 10 -> {
            if (since > 3000L && (map.seedLayer().tourIdle() || since > 45000L)) {
               say((step == 9 ? "detail" : "detail cave") + " done after " + since + " ms; " + map.seedLayer().tourState());
               shot(mc, step == 9 ? "seedlook-detail" : "seedlook-detail-cave");
               if (step == 9) {
                  map.tourCave(30);
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         default -> finish(mc);
      }
   }

   private static void editTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen && ++titleTicks >= 60) {
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null && since > 3000L) {
               mc.player.connection.sendCommand("spreadplayers -584 -146 0 6 false @s");
               next(now);
            } else if (since > 90000L) {
               finish(mc);
            }
         }
         case 2 -> {
            if (since > 4000L) {
               dev.chunkatlas.InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               next(now);
            }
         }
         case 3 -> {
            if (map == null) {
               finish(mc);
            } else if (ready(since, 2000L, 40000L)) {
               map.tourZoom(2.0);
               map.tourEdit(true);
               map.tourSelect(3, 2);
               next(now);
            }
         }
         case 4 -> {
            if (since > 2500L) {
               shot(mc, "edit-selected");
               map.tourSelectionMenu(true);
               next(now);
            }
         }
         case 5 -> {
            if (since > 800L) {
               shot(mc, "edit-menu");
               map.tourSelectionMenu(false);
               map.tourClear();
               next(now);
            }
         }
         case 6 -> {
            if (since > 800L) {
               shot(mc, "edit-none");
               map.tourContext(mc.player.getBlockX() + 40, mc.player.getBlockZ() + 30);
               next(now);
            }
         }
         case 7 -> {
            if (since > 800L) {
               shot(mc, "edit-context");
               map.tourEdit(false);
               map.tourContext(mc.player.getBlockX() + 40, mc.player.getBlockZ() + 30);
               next(now);
            }
         }
         case 8 -> {
            if (since > 800L) {
               shot(mc, "map-context");
               map.tourSelectionMenu(false);
               map.tourLayers();
               next(now);
            }
         }
         case 9 -> {
            if (since > 800L) {
               shot(mc, "layers");
               map.tourSelectionMenu(false);
               map.tourView(1);
               next(now);
            }
         }
         case 10 -> {
            if (since > 2500L) {
               shot(mc, "legend");
               say("edit tour: " + map.tourState());
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static boolean serverAnswered;
   private static boolean seedToggled;

   private static String clipNote() {
      dev.chunkatlas.engine.ChunkClips.Clip c = dev.chunkatlas.engine.ChunkClips.clipboardInfo(dev.chunkatlas.ChunkAtlas.home());
      return c == null ? "no clipboard" : c.chunks() + " chunks from " + c.sourceWorld() + " (" + c.dimension() + ", data " + c.dataVersion() + ")";
   }

   private static void transferTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      if (step <= 2) {
         serverTour(mc);
         return;
      }

      switch (step) {
         case 3 -> {
            if (map == null) {
               finish(mc);
               return;
            }

            if (ready(since, 2000L, 60000L)) {
               say("live " + dev.chunkatlas.ServerLink.live() + ", may edit " + dev.chunkatlas.ServerLink.canEdit());
               map.tourEdit(true);
               map.tourSelect(2, 2);
               map.tourCopy();
               next(now);
            }
         }
         case 4 -> {
            if (since > 1000L && map.tourIdle()) {
               say("copied: " + map.tourBanner() + " | clipboard " + clipNote());
               map.tourPaste();
               next(now);
            } else if (since > 60000L) {
               say("copy never finished; progress " + dev.chunkatlas.ServerLink.copyProgress() + ", banner " + map.tourBanner());
               finish(mc);
            }
         }
         case 5 -> {
            if (since > 1000L) {
               shot(mc, "transfer-paste-bar");
               map.tourPlace(30, 0);
               next(now);
            }
         }
         case 6 -> {
            if (since > 1500L && map.tourIdle()) {
               say("paste for a restart: " + map.tourBanner() + " | waiting " + dev.chunkatlas.ServerLink.waiting());
               mc.player.connection.sendCommand("chunkatlas live on");
               next(now);
            } else if (since > 60000L) {
               say("paste never answered");
               finish(mc);
            }
         }
         case 7 -> {
            if (since > 2500L) {
               say("live now " + dev.chunkatlas.ServerLink.live());
               map.tourPaste();
               next(now);
            }
         }
         case 8 -> {
            if (since > 1000L) {
               map.tourPlace(60, 0);
               next(now);
            }
         }
         case 9 -> {
            if (since > 1500L && map.tourIdle()) {
               say("live paste: " + map.tourBanner());
               shot(mc, "transfer-live");

               double[] c = map.tourCentre();
               int cx = (int)Math.floor(c[0]) >> 4;
               int cz = (int)Math.floor(c[1]) >> 4;
               dev.chunkatlas.engine.ChunkSelection sel = new dev.chunkatlas.engine.ChunkSelection();
               for (int x = cx + 59; x <= cx + 61; x++) {
                  for (int z = cz - 1; z <= cz + 1; z++) {
                     sel.add(x, z);
                  }
               }

               boolean sent = dev.chunkatlas.ServerLink.edit("DELETE", "minecraft:overworld", sel, reply -> say("live delete: ok " + reply.ok() + ", " + reply.key() + ", now " + reply.chunks() + ", later " + reply.later()));
               say("asked for a live delete: " + sent + " (" + sel.size() + " chunks)");
               next(now);
            } else if (since > 60000L) {
               say("live paste never answered");
               finish(mc);
            }
         }
         case 10 -> {
            if (since > 3000L) {
               mc.player.connection.sendCommand("chunkatlas live off");
               next(now);
            }
         }
         case 11 -> {
            if (since > 2000L) {
               say("live after switching off " + dev.chunkatlas.ServerLink.live() + "; waiting " + dev.chunkatlas.ServerLink.waiting());
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void serverTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               String address = "localhost:25599";
               net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                  t, mc, net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                  new net.minecraft.client.multiplayer.ServerData("Chunk Atlas test", address, net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null
               );
               say("connecting as " + mc.getUser().getName());
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && since > 4000L) {
               say("on the server; mod there " + dev.chunkatlas.ServerLink.hasMod() + ", may edit " + dev.chunkatlas.ServerLink.canEdit()
                  + ", waiting " + dev.chunkatlas.ServerLink.waiting());

               mc.player.connection.sendCommand("gamemode creative");
               mc.player.connection.sendCommand("spreadplayers -584 -146 0 6 false @s");
               mc.player.connection.sendCommand("time set noon");
               if (Game.screen(mc) != null) {
                  Game.open(mc, null);
               }

               next(now);
            } else if (since > 90000L) {
               say("never got onto the server");
               finish(mc);
            }
         }
         case 2 -> {
            if (since > 15000L) {
               dev.chunkatlas.LiveMap.flushNow();
               dev.chunkatlas.InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               say("map open: " + (map != null));
               next(now);
            }
         }
         case 3 -> {
            if (map == null) {
               finish(mc);
            } else if (ready(since, 3000L, 40000L)) {
               map.tourEdit(true);
               map.tourSelect(1, 1);
               say("edit mode: " + map.tourState());
               next(now);
            }
         }
         case 4 -> {
            if (since > 1500L) {
               shot(mc, "server-edit");
               int cx = mc.player.getBlockX() >> 4;
               int cz = mc.player.getBlockZ() >> 4;
               dev.chunkatlas.engine.ChunkSelection far = new dev.chunkatlas.engine.ChunkSelection();
               for (int dx = 20; dx < 23; dx++) {
                  for (int dz = 20; dz < 23; dz++) {
                     far.add(cx + dx, cz + dz);
                  }
               }

               boolean sent = dev.chunkatlas.ServerLink.edit("DELETE", Ids.of(mc.level.dimension()), far, reply -> {
                  serverAnswered = true;
                  say("server answered: ok " + reply.ok() + ", " + reply.key() + ", chunks " + reply.chunks() + ", waiting " + reply.waiting());
               });
               say("asked the server: " + sent);
               next(now);
            }
         }
         case 5 -> {
            if (serverAnswered || since > 20000L) {
               shot(mc, "server-edit-queued");
               say("waiting now " + dev.chunkatlas.ServerLink.waiting());
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void liveTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen && ++titleTicks >= 60) {
               dev.chunkatlas.LiveMap.testInSingleplayer = true;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && Game.screen(mc) == null) {
               say("in the world, recording");
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            if (since > 15000L) {
               dev.chunkatlas.LiveMap.flushNow();
               Path folder = dev.chunkatlas.LiveMap.folder();
               say("live folder: " + (folder == null ? "none" : folder.getFileName()));
               if (folder == null) {
                  finish(mc);
                  return;
               }

               listTree(folder);
               map = new MapScreen(null, folder, new dev.chunkatlas.InGame.Session(folder.getFileName().toString(), java.util.concurrent.CompletableFuture.completedFuture(null)));
               Game.open(mc, map);
               next(now);
            }
         }
         case 3 -> {
            if (ready(since, 2000L, 30000L)) {
               shot(mc, "live-map");
               say("live map: " + map.tourState());
               map.tourTryEdit();
               next(now);
            }
         }
         case 4 -> {
            if (since > 800L) {
               shot(mc, "live-map-edit-blocked");
               say("after asking for Edit: " + map.tourState() + " banner " + map.tourBanner());
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void listTree(Path dir) {
      try (var walk = Files.walk(dir)) {
         for (Path p : walk.filter(Files::isRegularFile).toList()) {
            say("  " + dir.relativize(p) + " " + Files.size(p));
         }
      } catch (IOException | RuntimeException e) {
         say("  can't list " + dir + ": " + e);
      }
   }

   private static void crowdTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      dev.chunkatlas.minimap.Config cfg = dev.chunkatlas.minimap.Config.get();
      int[] zooms = {0, 2, 5};
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && Game.screen(mc) == null && since > 6000L) {
               net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
               server.execute(() -> {
                  List<String> commands = new java.util.ArrayList<>(List.of("difficulty normal", "time set noon"));
                  String still = "{NoAI:1b,PersistenceRequired:1b}";

                  for (int i = 0; i < 25; i++) {
                     commands.add(String.format(java.util.Locale.ROOT, "execute at @p run summon minecraft:cow ~%.1f ~ ~%.1f %s", 8.0 + i % 5 * 0.8, 6.0 + i / 5 * 0.8, still));
                  }

                  for (int i = 0; i < 12; i++) {
                     commands.add(String.format(java.util.Locale.ROOT, "execute at @p run summon minecraft:zombie ~%.1f ~ ~%.1f %s", -9.0 + i % 4 * 0.6, 4.0 + i / 4 * 0.6, still));
                  }

                  String[] ring = {"skeleton", "creeper", "spider", "pig", "sheep", "chicken", "villager", "wolf", "fox", "witch", "enderman", "slime"};
                  for (int i = 0; i < ring.length; i++) {
                     double a = Math.PI * 2.0 * i / ring.length;
                     commands.add(String.format(java.util.Locale.ROOT, "execute at @p run summon minecraft:%s ~%.1f ~ ~%.1f %s", ring[i], Math.cos(a) * 14.0, Math.sin(a) * 14.0, still));
                  }

                  commands.add("effect give @e[type=!player] fire_resistance infinite 0 true");
                  for (String c : commands) {
                     server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
                  }
               });
               next(now);
            } else if (Game.screen(mc) != null && since > 2000L && !(Game.screen(mc) instanceof MapScreen)) {
               Game.open(mc, null);
            }
         }
         case 2 -> {
            if (since > 3000L) {
               mobZoom = cfg.zoom;
               cfg.enabled = true;
               cfg.radar = true;
               cfg.radarHostile = true;
               cfg.radarFriendly = true;
               cfg.zoom = zooms[0];
               cfg.radarStyle = dev.chunkatlas.minimap.Config.RadarStyle.FACES;
               say("mobs near you: " + new MobMarks.Near().collect(mc, mc.player, cfg).size());
               next(now);
            }
         }
         case 3, 4, 5, 6, 7, 8, 9, 10, 11 -> {
            if (since > 1500L && Game.screen(mc) == null) {
               shot(mc, "crowd-minimap-" + SettingsScreen.zoomName(cfg.zoom).replace("/", "-") + "-" + cfg.radarStyle.name().toLowerCase(java.util.Locale.ROOT));
               int k = step - 2;
               if (k < 9) {
                  cfg.zoom = zooms[k / 3];
                  cfg.radarStyle = dev.chunkatlas.minimap.Config.RadarStyle.values()[k % 3];
               } else {
                  cfg.zoom = 3;
                  cfg.radarStyle = dev.chunkatlas.minimap.Config.RadarStyle.FACES;
                  cfg.lightOverlay = true;
                  cfg.showBiome = true;
                  cfg.showLight = true;
                  net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
                  server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight"));
               }

               next(now);
            }
         }
         case 12 -> {
            if (since > 4000L) {
               shot(mc, "crowd-minimap-spawnable");
               Game.open(mc, new dev.chunkatlas.minimap.gui.PositionScreen(null));
               next(now);
            }
         }
         case 13 -> {
            if (since > 1500L && Game.screen(mc) instanceof dev.chunkatlas.minimap.gui.PositionScreen p) {
               shot(mc, "position-card");
               p.tourResize(170);
               next(now);
            }
         }
         case 14 -> {
            if (since > 1200L) {
               shot(mc, "position-custom");
               Game.open(mc, null);
               next(now);
            }
         }
         case 15 -> {
            if (since > 1500L) {
               say("minimap size " + cfg.size + " " + cfg.customSize);
               shot(mc, "crowd-minimap-custom");
               dev.chunkatlas.Settings.mobs(true);
               dev.chunkatlas.Settings.spawnable(true);
               InGame.open(mc, null);
               map = Game.screen(mc) instanceof MapScreen m ? m : null;
               say("map open: " + (map != null));
               next(now);
            }
         }
         case 16 -> {
            if (map == null) {
               finish(mc);
            } else if (since > 4000L) {
               map.tourZoom(4.0);
               next(now);
            }
         }
         case 17 -> {
            if (since > 3000L) {
               shot(mc, "crowd-map-close");
               map.tourZoom(0.125);
               next(now);
            }
         }
         case 18 -> {
            if (since > 2500L) {
               shot(mc, "crowd-map-merged");
               map.tourOver(new SeedMapWarningScreen(map, () -> {}));
               next(now);
            }
         }
         case 19 -> {
            if (since > 1500L) {
               shot(mc, "seed-warning-card");
               Game.open(mc, map);
               mc.options.guiScale().set(3);
               Game.resizeGui(mc);
               map.tourSettingsTab(SettingsScreen.MAP);
               next(now);
            }
         }
         case 20 -> {
            if (since > 1500L && Game.screen(mc) instanceof SettingsScreen s) {
               shot(mc, "settings-map-scale3");
               s.tourScrollToEnd();
               next(now);
            }
         }
         case 21 -> {
            if (since > 1000L) {
               shot(mc, "settings-map-scale3-end");
               next(now);
            }
         }
         default -> {
            mc.options.guiScale().set(0);
            Game.resizeGui(mc);
            cfg.zoom = mobZoom;
            cfg.lightOverlay = false;
            cfg.showBiome = false;
            cfg.showLight = false;
            cfg.size = dev.chunkatlas.minimap.Config.Size.MEDIUM;
            dev.chunkatlas.Settings.mobs(false);
            dev.chunkatlas.Settings.spawnable(false);
            finish(mc);
         }
      }
   }

   private static void pasteFitTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen && ++titleTicks >= 60) {
               title = screen;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourEdit(true);
               map.tourSelect(3, 2);
               map.tourCopy();
               next(now);
            }
         }
         case 2 -> {
            if (since > 500L && map.tourIdle()) {
               map.tourPaste();
               map.tourHoldPaste(6, 0);
               say("pasting: " + map.tourPasting() + " at " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
               next(now);
            }
         }
         default -> {
            int k = step - 3;
            int scale = 2 + k / 3;
            int sub = k % 3;
            if (scale > 4) {
               map.tourPasteCard(false);
               mc.options.guiScale().set(0);
               Game.resizeGui(mc);
               finish(mc);
            } else if (sub == 0 && since > 800L) {
               map.tourPasteCard(false);
               mc.options.guiScale().set(scale);
               Game.resizeGui(mc);
               next(now);
            } else if (sub == 1 && since > 1500L) {
               shot(mc, "pastefit-scale" + scale);
               map.tourPasteCard(true);
               next(now);
            } else if (sub == 2 && since > 1000L) {
               shot(mc, "pastefit-scale" + scale + "-card");
               next(now);
            }
         }
      }
   }

   private static final java.util.concurrent.atomic.AtomicInteger flickerFrames = new java.util.concurrent.atomic.AtomicInteger();
   private static final java.util.concurrent.atomic.AtomicInteger flickerDark = new java.util.concurrent.atomic.AtomicInteger();
   private static int flickerCycle;
   private static int flickerShots;

   private static void zoomFlickerTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen && ++titleTicks >= 60) {
               title = screen;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               if (!map.tourCentreOnLand()) {
                  say("no saved land to zoom over");
                  finish(mc);
                  return;
               }

               next(now);
            }
         }
         case 2 -> {
            if (ready(since, 1500L, 40000L)) {
               int[][] points = map.tourLandPoints();
               say("settled over land: " + points.length + " points to sample; " + map.tourHeld());
               if (points.length == 0) {
                  finish(mc);
                  return;
               }

               sampleLand(mc, "settled");
               shot(mc, "zoomflicker-settled");
               flickerCycle = 0;
               flickerFrames.set(0);
               flickerDark.set(0);
               next(now);
            }
         }
         case 3 -> {
            sampleLand(mc, "cycle " + flickerCycle);
            if (since >= 600L) {
               map.tourZoom(flickerCycle % 2 == 0 ? 8.0 : 1.0 / 8.0);
               flickerCycle++;
               at = now;
               if (flickerCycle >= 24) {
                  next(now);
               }
            }
         }
         case 4 -> {
            sampleLand(mc, "after");
            if (since > 3000L) {
               say("zoom flicker: " + flickerDark.get() + " of " + flickerFrames.get() + " sampled frames had saved land drawn as the bare background; " + map.tourHeld());
               shot(mc, "zoomflicker-after");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void sampleLand(Minecraft mc, String when) {
      int[][] gui = map.tourLandPoints();
      if (gui.length == 0) {
         return;
      }

      double scale = mc.getWindow().getGuiScale();
      int[][] px = new int[gui.length][];
      for (int i = 0; i < gui.length; i++) {
         px[i] = new int[]{(int)Math.round(gui[i][0] * scale), (int)Math.round(gui[i][1] * scale)};
      }

      String held = map.tourHeld();
      Game.sample(mc, px, colours -> {
         int dark = 0;
         for (int c : colours) {
            if (bareBackground(c)) {
               dark++;
            }
         }

         flickerFrames.incrementAndGet();
         if (dark > 0) {
            flickerDark.incrementAndGet();
            int n = dark;
            mc.execute(() -> {
               say(when + ": " + n + " of " + colours.length + " points over saved land were the bare background (" + held + ")");
               if (flickerShots < 3) {
                  flickerShots++;
                  shot(mc, "zoomflicker-dark-" + flickerShots);
               }
            });
         }
      });
   }

   private static boolean bareBackground(int rgb) {
      int bg = MapScreen.NOT_MADE & 0xFFFFFF;
      for (int shift = 0; shift <= 16; shift += 8) {
         if (Math.abs((rgb >> shift & 0xFF) - (bg >> shift & 0xFF)) > 3) {
            return false;
         }
      }

      return true;
   }

   private static void handTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      int mx = map == null ? 0 : map.width / 2;
      int my = map == null ? 0 : (map.seedTop() + map.seedBottom()) / 2;
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen && ++titleTicks >= 60) {
               title = screen;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourCentreOnLand();
               map.tourEdit(true);
               map.tourSelect(2, 2);
               map.tourTool("hand");
               next(now);
            }
         }
         case 2 -> {
            if (since > 1000L) {
               shot(mc, "hand-tool");
               say("Hand, left drag: " + map.tourDrag(InputConstants.MOUSE_BUTTON_LEFT, false, mx - 60, my, mx + 60, my + 30));
               say("Hand, click: " + map.tourDrag(InputConstants.MOUSE_BUTTON_LEFT, false, mx, my, mx, my));
               next(now);
            }
         }
         case 3 -> {
            if (since > 800L) {
               map.tourTool("chunks");
               say("Select with Space, left drag: " + map.tourDrag(InputConstants.MOUSE_BUTTON_LEFT, true, mx + 60, my + 30, mx - 60, my));
               map.tourTool("brush");
               say("Brush, middle drag: " + map.tourDrag(InputConstants.MOUSE_BUTTON_MIDDLE, false, mx - 40, my, mx + 40, my));
               map.tourTool("chunks");
               say("Select, left drag (picks): " + map.tourDrag(InputConstants.MOUSE_BUTTON_LEFT, false, mx - 40, my + 10, mx + 40, my + 10));
               map.tourTool("hand");
               next(now);
            }
         }
         case 4 -> {
            if (since > 800L) {
               shot(mc, "hand-after");
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static int blendWas;
   private static boolean smartWas;

   private static void forceBlendTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen && ++titleTicks >= 60) {
               title = screen;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               blendWas = dev.chunkatlas.Settings.pasteBlend();
               smartWas = dev.chunkatlas.Settings.pasteSmart();
               dev.chunkatlas.Settings.pasteBlend(4);
               dev.chunkatlas.Settings.pasteSmart(true);
               map.tourCentreOnLand();
               map.tourEdit(true);
               map.tourSelect(3, 3);
               map.tourOpenFields();
               next(now);
            }
         }
         case 2 -> {
            if (since > 600L && screen instanceof FieldsScreen f) {
               f.tourTick("force_blending", null);
               next(now);
            }
         }
         case 3 -> {
            if (screen instanceof FieldsScreen f && (f.tourLift() != null && since > 1500L || since > 20000L)) {
               say("Force blending, Blend 4 with Smart: move " + f.tourLift());
               shot(mc, "forceblend-4-smart");
               dev.chunkatlas.Settings.pasteSmart(false);
               dev.chunkatlas.Settings.pasteBlend(8);
               next(now);
            }
         }
         case 4 -> {
            if (since > 2500L) {
               shot(mc, "forceblend-8");
               dev.chunkatlas.Settings.pasteBlend(0);
               next(now);
            }
         }
         case 5 -> {
            if (since > 1500L && screen instanceof FieldsScreen f) {
               shot(mc, "forceblend-off");
               dev.chunkatlas.Settings.pasteBlend(4);
               dev.chunkatlas.Settings.pasteSmart(true);
               f.tourGo();
               next(now);
            }
         }
         case 6 -> {
            if (screen instanceof ConfirmEditScreen) {
               if (since > 1500L) {
                  shot(mc, "forceblend-confirm");
                  Game.open(mc, map);
                  next(now);
               }
            } else if (since > 60000L) {
               say("no confirm came up: " + map.tourBanner());
               next(now);
            }
         }
         default -> {
            dev.chunkatlas.Settings.pasteBlend(blendWas);
            dev.chunkatlas.Settings.pasteSmart(smartWas);
            finish(mc);
         }
      }
   }

   private static final int FORTRESS_DX = 40;

   private static volatile int[] fortress;

   private static void structurePasteTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen t && ++titleTicks >= 60) {
               title = t;
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 1 -> {
            if (mc.level != null && mc.player != null && screen == null && since > 5000L) {
               fortress = null;
               server.execute(() -> {
                  command(server, "gamemode creative @a", null);
                  command(server, "difficulty normal", null);

                  command(server, "gamerule spawn_mobs true", text -> say("game rules: " + text));
                  command(server, "execute in minecraft:the_nether positioned 0 64 0 run locate structure minecraft:fortress", text -> {
                     int[] at2 = located(text);
                     say("locate from 0, 0: " + text);
                     if (at2 != null) {
                        fortress = at2;
                     }
                  });
               });
               next(now);
            } else if (since > 90000L) {
               say("never got into the world");
               finish(mc);
            }
         }
         case 2 -> {
            int[] f = fortress;
            if (f != null && since > 1000L) {
               server.execute(() -> command(server, "execute in minecraft:the_nether run tp @a " + f[0] + " 90 " + f[1], null));
               say("visiting the fortress at " + f[0] + ", " + f[1]);
               next(now);
            } else if (since > 20000L) {
               say("no fortress found");
               finish(mc);
            }
         }
         case 3 -> {
            if (since > 20000L) {
               int[] f = fortress;
               server.execute(() -> {
                  say("before the paste, at the fortress: " + fortressPiece(server, f[0], f[1]) + "; after 20 s there: " + mobsAround(server, f[0], f[1]));
                  command(server, "difficulty", text -> say("game rules: " + text));
                  command(server, "gamerule spawn_monsters", text -> say("game rules: " + text));
                  command(server, "save-all flush", null);
               });
               next(now);
            }
         }
         case 4 -> {
            if (since > 5000L) {
               map = null;
               Game.leaveLevel(mc.level);
               mc.disconnect(new TitleScreen(), false);
               next(now);
            }
         }
         case 5 -> {
            if (since > 2000L && screen instanceof TitleScreen t && !Game.busy(mc)) {
               title = t;
               if (open(mc, "ingame")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 6 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourShow("minecraft:the_nether");
               next(now);
            }
         }
         case 7 -> {
            if (ready(since, 1500L, 60000L)) {
               int[] f = fortress;
               dev.chunkatlas.Settings.pasteBlend(0);
               dev.chunkatlas.Settings.keepStructures(true);

               dev.chunkatlas.Settings.editWithoutCheats(true);
               map.seedCentre(f[0] + 8.0, f[1] + 8.0);
               map.tourSelect(6, 6);
               say("picked round the fortress: " + map.tourState());
               map.tourCopy();
               next(now);
            }
         }
         case 8 -> {
            if (since > 800L && map.tourIdle()) {
               map.tourPaste();
               next(now);
            }
         }
         case 9 -> {
            if (since > 1500L) {
               map.tourPlace(FORTRESS_DX, 0);
               next(now);
            }
         }
         case 10 -> {
            if (screen instanceof ConfirmEditScreen confirm) {
               if (since > 1200L) {
                  shot(mc, "structurepaste-confirm");
                  confirm.go();
                  next(now);
               }
            } else if (since > 30000L) {
               say("no confirm came up: " + map.tourBanner());
               finish(mc);
            }
         }
         case 11 -> {
            if (screen == map && ready(since, 1500L, 90000L)) {
               shot(mc, "structurepaste-map");
               Game.open(mc, title);
               next(now);
            }
         }
         case 12 -> {
            if (since > 1500L && screen instanceof TitleScreen) {
               mc.createWorldOpenFlows().openWorld(ASKED.get("ingame").toString(), () -> say("the world would not open"));
               next(now);
            }
         }
         case 13 -> {
            if (mc.level != null && mc.player != null && screen == null && since > 5000L) {
               int[] f = fortress;
               int x = f[0] + FORTRESS_DX * 16;
               int z = f[1];
               server.execute(() -> {
                  command(server, "execute in minecraft:the_nether run tp @a " + x + " 90 " + z, null);
                  say("at the new spot " + x + ", " + z + ": " + fortressPiece(server, x, z) + "; at the old one: " + fortressPiece(server, f[0], f[1]));
                  command(server, "execute in minecraft:the_nether positioned " + x + " 64 " + z + " run locate structure minecraft:fortress", text -> {
                     int[] found = located(text);
                     say("locate from the new spot: " + text + (found == null ? "" : " (" + Math.round(Math.hypot(found[0] - x, found[1] - z)) + " blocks from it)"));
                  });
               });
               next(now);
            } else if (since > 90000L) {
               say("never got back into the world");
               finish(mc);
            }
         }
         case 14 -> {
            if (since > 60000L) {
               int[] f = fortress;
               int x = f[0] + FORTRESS_DX * 16;
               int z = f[1];
               server.execute(() -> {
                  say("round the moved fortress after a minute: " + mobsAround(server, x, z));
                  command(server, "difficulty", text -> say("game rules: " + text));
               });
               shot(mc, "structurepaste-ingame");
               next(now);
            }
         }
         case 15 -> {
            if (since > 1000L) {
               finish(mc);
            }
         }
         default -> finish(mc);
      }
   }

   private static void command(net.minecraft.server.MinecraftServer server, String cmd, java.util.function.Consumer<String> out) {
      net.minecraft.commands.CommandSource sink = new net.minecraft.commands.CommandSource() {
         @Override
         public void sendSystemMessage(net.minecraft.network.chat.Component c) {
            if (out != null) {
               out.accept(c.getString());
            }
         }

         @Override
         public boolean acceptsSuccess() {
            return true;
         }

         @Override
         public boolean acceptsFailure() {
            return true;
         }

         @Override
         public boolean shouldInformAdmins() {
            return false;
         }
      };
      server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(sink), cmd);
   }

   private static int[] located(String text) {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[(-?\\d+), [^,\\]]+, (-?\\d+)\\]").matcher(text);
      return m.find() ? new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
   }

   private static String mobsAround(net.minecraft.server.MinecraftServer server, int x, int z) {
      var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
      var box = new net.minecraft.world.phys.AABB(x - 96, 0, z - 96, x + 96, 128, z + 96);
      int blazes = nether.getEntitiesOfClass(net.minecraft.world.entity.monster.Blaze.class, box).size();
      var monsters = nether.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, box);

      long wither = monsters.stream().filter(m -> m.getType().getDescriptionId().endsWith("wither_skeleton")).count();
      return blazes + " blazes, " + wither + " wither skeletons, " + monsters.size() + " monsters in all";
   }

   private static String fortressPiece(net.minecraft.server.MinecraftServer server, int x, int z) {
      var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
      java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>> fortressOnly =
         h -> h.is(net.minecraft.world.level.levelgen.structure.BuiltinStructures.FORTRESS);
      for (int y = 32; y <= 112; y += 4) {
         var start = nether.structureManager().getStructureWithPieceAt(new net.minecraft.core.BlockPos(x, y, z), fortressOnly);
         if (start.isValid()) {
            return "a fortress piece at y " + y + " (start chunk " + dev.chunkatlas.port.Pos.cx(start.getChunkPos()) + ", " + dev.chunkatlas.port.Pos.cz(start.getChunkPos()) + ")";
         }
      }

      return "no fortress piece";
   }

   private static void overlapTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      Screen screen = Game.screen(mc);
      switch (step) {
         case 0 -> {
            if (!Game.busy(mc) && screen instanceof TitleScreen && ++titleTicks >= 60) {
               title = screen;
               if (open(mc, "view")) {
                  next(now);
               } else {
                  finish(mc);
               }
            }
         }
         case 1 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourCave(30);
               next(now);
            }
         }
         case 2, 4 -> {
            if (since > 800L) {
               mc.options.guiScale().set(step == 2 ? 3 : 4);
               Game.resizeGui(mc);

               map.tourZoom(1.0 / 16.0);
               next(now);
            }
         }
         case 3, 5 -> {
            if (since > 1200L) {
               shot(mc, "overlap-cave-scale" + (step == 3 ? 3 : 4));
               map.tourZoom(16.0);
               next(now);
            }
         }
         case 6 -> {
            if (ready(since, 1500L, 40000L)) {
               map.tourCave(Integer.MIN_VALUE);
               map.tourCentreOnLand();
               map.tourEdit(true);
               map.tourSelect(3, 2);
               map.tourCopy();
               next(now);
            }
         }
         case 7 -> {
            if (since > 800L && map.tourIdle()) {
               map.tourPaste();
               next(now);
            }
         }
         case 8, 10 -> {
            if (since > 1000L) {
               mc.options.guiScale().set(step == 8 ? 3 : 4);
               Game.resizeGui(mc);

               double halfBlocks = (map.seedBottom() - map.seedTop()) / 2.0 / map.seedZoom();
               map.tourHoldPaste(0, -(int)(halfBlocks / 16.0) + 3);
               next(now);
            }
         }
         case 9, 11 -> {
            if (since > 1500L) {
               shot(mc, "overlap-paste-scale" + (step == 9 ? 3 : 4));
               next(now);
            }
         }
         default -> {
            mc.options.guiScale().set(0);
            Game.resizeGui(mc);
            finish(mc);
         }
      }
   }

   private static void settingsTour(Minecraft mc) {
      long now = System.currentTimeMillis();
      if (step == 0) {
         if (Game.screen(mc) instanceof TitleScreen t && ++titleTicks > 40) {
            settings = new SettingsScreen(t);
            Game.open(mc, settings);
            step = 1;
            at = now;
         }
      } else if (step <= 4) {
         if (now - at > 1500) {
            settings.tourTab(step - 1);
            step += 10;
            at = now;
         }
      } else if (step >= 11 && step <= 14) {
         if (now - at > 1000) {
            shot(mc, "settings-" + (step - 10));
            step = step - 10 + 1;
            at = now;
         }
      } else if (step == 5) {
         if (now - at > 800) {
            settings.tourTab(SettingsScreen.KEYS);
            settings.tourListen(MapKey.SELECT_ALL);
            step = 6;
            at = now;
         }
      } else if (step == 6) {
         if (now - at > 1000) {
            shot(mc, "settings-listening");
            finish(mc);
         }
      }
   }

   private static final String[][] SAMPLE_BACKUPS = {
      {"-", "29990101-000001-ab01", "DELETE", "56", "0"},
      {"-", "29990101-000002-ab02", "COPY", "12", "1"},
      {"-", "29990101-000003-ab03", "KEEP_ONLY", "3480", "3"},
      {"0123456789abcdef0123", "29990101-000004-ab04", "DELETE_MOBS", "8", "40"},
      {"0123456789abcdef0123", "29990101-000005-ab05", "DELETE", "1204", "120"}
   };

   private static void sampleBackups(Path world) throws IOException {
      Path home = dev.chunkatlas.ChunkAtlas.home();
      Path own = dev.chunkatlas.engine.ChunkEdits.backups(home, world.toRealPath());
      String ownName = world.toRealPath().toString();
      for (String[] s : SAMPLE_BACKUPS) {
         boolean other = !s[0].equals("-");
         Path dir = (other ? home.resolve("backups").resolve(s[0]) : own).resolve(s[1]);
         Files.createDirectories(dir.resolve("files"));
         Files.write(dir.resolve("files").resolve("r.0.0.mca"), new byte[(Integer.parseInt(s[3]) % 900 + 100) * 1024]);
         long created = System.currentTimeMillis() - Long.parseLong(s[4]) * 86400000L - 3600000L;
         String text = "edit 1\nid=" + s[1] + "\nworld=" + java.net.URLEncoder.encode(other ? "/saves/Survival 2" : ownName, StandardCharsets.UTF_8)
            + "\ndimension=minecraft:overworld\nlabel=\nkind=" + s[2] + "\nchunks=" + s[3] + "\ncreated=" + created + "\nstamp=" + created
            + "\nstate=undone\nnote=\nend\n";
         Files.writeString(dir.resolve("edit.txt"), text, StandardCharsets.UTF_8);
      }
   }

   private static void removeSampleBackups(Path world) {
      Path home = dev.chunkatlas.ChunkAtlas.home();
      try {
         String own = dev.chunkatlas.engine.ChunkEdits.backups(home, world.toRealPath()).getFileName().toString();
         for (String[] s : SAMPLE_BACKUPS) {
            dev.chunkatlas.engine.ChunkEdits.deleteBackup(home, s[0].equals("-") ? own : s[0], s[1]);
         }
      } catch (IOException ignored) {
      }
   }

   private static void settings2Tour(Minecraft mc) {
      long now = System.currentTimeMillis();
      long since = now - at;
      if (step == 0) {
         if (!Game.busy(mc) && Game.screen(mc) instanceof TitleScreen t && ++titleTicks >= 60) {
            title = t;
            if (!open(mc, "view")) {
               finish(mc);
               return;
            }

            try {
               sampleBackups(ASKED.get("view"));
            } catch (IOException e) {
               say("no sample backups: " + e);
            }

            next(now);
         }

         return;
      }

      int scale = 2 + (step - 1) / 24;
      if (scale > 3) {
         removeSampleBackups(ASKED.get("view"));
         mc.options.guiScale().set(0);
         Game.resizeGui(mc);
         finish(mc);
         return;
      }

      int sub = (step - 1) % 24;
      String name = "settings2-s" + scale + "-";
      String[] tabs = {"general", "map", "minimap", "seed", "editor", "backups", "keys"};
      if (sub == 0 && since > 1500L) {
         mc.options.guiScale().set(scale);
         Game.resizeGui(mc);
         Game.open(mc, map);
         next(now);
      } else if (sub == 1 && since > 2500L) {
         settings = new SettingsScreen(map);
         Game.open(mc, settings);
         next(now);
      } else if (sub >= 2 && sub <= 15 && since > 1200L) {
         int tab = (sub - 2) / 2;
         if (sub % 2 == 0) {
            settings.tourTab(tab);
         } else {
            shot(mc, name + tabs[tab]);
         }

         next(now);
      } else if (sub == 16 && since > 800L) {
         settings.tourSearch("show");
         next(now);
      } else if (sub == 17 && since > 1200L) {
         shot(mc, name + "search");
         settings.tourSearch("");
         next(now);
      } else if (sub == 18 && since > 1500L) {
         settings.tourConfirm(false);
         next(now);
      } else if (sub == 19 && since > 1200L) {
         shot(mc, name + "delete-confirm");
         settings.tourTab(SettingsScreen.BACKUPS);
         next(now);
      } else if (sub == 20 && since > 800L) {
         settings.tourConfirm(true);
         next(now);
      } else if (sub == 21 && since > 1200L) {
         shot(mc, name + "older-confirm");
         settings.tourTab(SettingsScreen.BACKUPS);
         next(now);
      } else if (sub >= 22 && since > 500L) {
         next(now);
      }
   }

   private static void shot(Minecraft mc, String name) {
      say("picture " + name + (map == null ? "" : ": " + map.tourState()));
      Game.screenshot(mc, "chunkatlas-" + name + ".png", message -> {});
   }

   private static void finish(Minecraft mc) {
      done = true;
      MapScreen.tourDeaf = false;
      say("done");
      mc.stop();
   }

   static void slowFrame(String text) {
      if (log != null) {
         say(text);
      }
   }

   private static void say(String text) {
      if (log != null) {
         try {
            Files.writeString(log, System.currentTimeMillis() + " " + text + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (IOException ignored) {
         }
      }
   }
}
