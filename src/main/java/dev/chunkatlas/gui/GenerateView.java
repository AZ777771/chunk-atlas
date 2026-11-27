package dev.chunkatlas.gui;

import dev.chunkatlas.Generating;
import dev.chunkatlas.engine.ChunkGeneration;
import dev.chunkatlas.engine.ChunkSelection;
import dev.chunkatlas.engine.RegionFacts;
import dev.chunkatlas.engine.RegionFile;
import dev.chunkatlas.engine.WorldLayout;
import dev.chunkatlas.engine.WorldMap;
import dev.chunkatlas.port.Gfx;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class GenerateView {
   private static final long REREAD_FOR = 6000L;
   private static final long REREAD_EVERY = 1000L;
   static final int CHIP_H = 16;
   private static final int BAR_W = 160;
   private final Minecraft mc = Minecraft.getInstance();
   private final MapScreen map;
   private final Map<Long, Long> touched = new HashMap<>();
   private long lastReread;

   private Object[] countedFrom;
   private int[] counted = {0, 0};
   private long countedAt;

   GenerateView(MapScreen map) {
      this.map = map;
   }

   private boolean mine() {
      return Generating.world() != null && Generating.world().equals(this.map.seedWorldFolder());
   }

   private String blocked() {
      if (this.map.live() == null) {
         return Ui.t("chunkatlas.generate.offline");
      }

      if (this.mc.getSingleplayerServer() == null && !Generating.remoteReady()) {
         return Ui.t("chunkatlas.generate.needs_server");
      }

      return Generating.active() ? Ui.t("chunkatlas.generate.busy") : null;
   }

   int[] count(ChunkSelection sel, boolean finish) {
      int[] c = this.tally(sel);
      int make = c[0] + (finish ? c[1] : 0);
      return new int[]{make, sel.size() - make};
   }

   private int[] tally(ChunkSelection sel) {
      WorldMap.Index index = this.map.indexNow();
      Map<Long, RegionFacts> facts = this.map.seedFacts();
      Object[] from = {sel, sel.size(), index, facts.size()};
      long now = System.currentTimeMillis();
      Object[] was = this.countedFrom;
      if (was != null && was[0] == sel && was[1].equals(from[1]) && was[2] == index && was[3].equals(from[3]) && now - this.countedAt < 250L) {
         return this.counted;
      }

      int missing = 0;
      int partial = 0;
      for (long region : sel.regions()) {
         int rx = (int)(region >> 32);
         int rz = (int)region;
         long[] bits = sel.bits(rx, rz);
         long[] saved = index == null ? null : index.present().get(region);
         RegionFacts known = facts.get(region);
         for (int w = 0; w < bits.length; w++) {
            long gone = saved == null ? bits[w] : bits[w] & ~saved[w];
            missing += Long.bitCount(gone);
            if (known != null) {
               for (long b = bits[w] & ~gone; b != 0L; b &= b - 1L) {
                  if (known.state[w * 64 + Long.numberOfTrailingZeros(b)] == RegionFacts.PARTIAL) {
                     partial++;
                  }
               }
            }
         }
      }

      this.countedFrom = from;
      this.countedAt = now;
      this.counted = new int[]{missing, partial};
      return this.counted;
   }

   void button(Glass.Bar bar, ChunkSelection sel) {
      String why = this.blocked();
      int[] c = sel == null ? new int[]{0, 0} : this.tally(sel);
      int make = c[0] + (dev.chunkatlas.Settings.generateFinish() ? c[1] : 0);
      String tip = why != null ? why
         : make > 0 ? Ui.plural("chunkatlas.generate.title", make) : Ui.t(sel == null || c[1] > 0 ? "chunkatlas.generate.button" : "chunkatlas.generate.nothing");
      bar.tile("generate", Glass.tip(tip, null), false, Ui.WHITE, why == null && c[0] + c[1] > 0 ? this::open : null);
   }

   void open() {
      ChunkSelection sel = this.map.selectionIn(this.map.shownDimension());
      if (!sel.isEmpty() && this.blocked() == null) {
         this.map.seedOpen(new GenerateCard(this.map, this, sel.copy()));
      }
   }

   boolean start(ChunkSelection chunks, ChunkGeneration.Speed speed, boolean finish, boolean hold) {
      return Generating.start(this.map.seedWorldFolder(), this.map.shownDimension(), chunks, speed, finish, hold);
   }

   void tick() {
      String problem = Generating.takeProblem();
      if (problem != null) {
         this.map.seedTell(problem, Ui.YELLOW);
      }

      if (!this.mine()) {
         return;
      }

      ChunkSelection dealt = Generating.takeDealt();
      long now = System.currentTimeMillis();
      if (!dealt.isEmpty()) {
         ChunkSelection sel = this.map.selectionIn(Generating.dimension());
         for (long region : dealt.regions()) {
            sel.remove((int)(region >> 32), (int)region, dealt.bits((int)(region >> 32), (int)region));
            this.touched.put(region, now);
         }
      }

      if (now - this.lastReread >= REREAD_EVERY && !this.touched.isEmpty()) {
         this.lastReread = now;
         this.touched.values().removeIf(t -> now - t > REREAD_FOR);
         if (!this.touched.isEmpty() && Generating.dimension().equals(this.map.shownDimension())) {
            this.map.reread(Set.copyOf(this.touched.keySet()));
         }
      }
   }

   static int chipY(boolean editing) {
      return editing ? Glass.MARGIN + Glass.tabsHeight() + 6 + 13 : Glass.MARGIN + 3 + Glass.CHIP_H + 4;
   }

   boolean chipShown() {
      return this.mine() && Generating.showing();
   }

   List<Component> drawChip(Gfx g, Font font, List<Ui.Hit> hits, boolean editing, int mouseX, int mouseY, boolean covered) {
      ChunkGeneration.Progress p = Generating.progress();
      if (!this.chipShown() || p == null) {
         return null;
      }

      boolean running = !p.state().ended();
      String label = Ui.t(p.state() == ChunkGeneration.State.DONE ? "chunkatlas.generate.done"
         : p.state() == ChunkGeneration.State.STOPPED ? "chunkatlas.generate.stopped" : Generating.paused() ? "chunkatlas.generate.paused" : "chunkatlas.generate.running");
      int total = Math.max(p.total(), p.done());
      String counts = Ui.t("chunkatlas.generate.of", Ui.count(p.done()), Ui.count(total));
      if (running && p.secondsLeft() >= 0) {
         counts += " · " + about(p.secondsLeft());
      }

      int w = 5 + font.width(label) + 6 + BAR_W + 6 + font.width(counts) + (running ? 6 + 12 + 2 + 12 : 0) + 5;
      int x = (this.map.width - w) / 2;

      x = Math.max(x, this.map.leftPanelRight() + 8);
      int y = chipY(editing);
      g.fill(x, y, x + w, y + CHIP_H, Glass.CHIP);

      hits.add(new Ui.Hit(x, y, x + w, y + CHIP_H, () -> {
      }));
      int ty = y + (CHIP_H - 8) / 2;
      g.text(font, label, x + 5, ty, p.state() == ChunkGeneration.State.DONE ? Glass.ACCENT : Ui.WHITE);
      int bx = x + 5 + font.width(label) + 6;
      int by = y + CHIP_H / 2 - 2;
      g.fill(bx, by, bx + BAR_W, by + 4, 0x40FFFFFF);
      if (total > 0) {
         g.fill(bx, by, bx + (int)((long)BAR_W * Math.min(p.done(), total) / total), by + 4, Glass.ACCENT);
      } else if (!running) {
         g.fill(bx, by, bx + BAR_W, by + 4, Glass.ACCENT);
      }

      int cx = bx + BAR_W + 6;
      g.text(font, counts, cx, ty, Glass.LABEL);
      if (!running) {
         return null;
      }

      List<Component> tip = null;
      int ix = cx + font.width(counts) + 6;
      int iy = y + 2;
      boolean paused = Generating.paused();
      boolean overPause = !covered && Glass.over(mouseX, mouseY, ix, iy, 12, 12);
      int colour = overPause ? Ui.WHITE : 0xFFE2E2E2;
      if (paused) {
         for (int k = 0; k < 4; k++) {
            g.fill(ix + 3 + k, iy + 2 + k, ix + 4 + k, iy + 10 - k, colour);
         }
      } else {
         g.fill(ix + 3, iy + 2, ix + 5, iy + 10, colour);
         g.fill(ix + 7, iy + 2, ix + 9, iy + 10, colour);
      }

      hits.add(new Ui.Hit(ix, iy, ix + 12, iy + 12, () -> Generating.pause(!paused)));
      if (overPause) {
         tip = Glass.tip(Ui.t(paused ? "chunkatlas.generate.resume" : "chunkatlas.generate.pause"), null);
      }

      int sx = ix + 14;
      boolean overStop = !covered && Glass.over(mouseX, mouseY, sx, iy, 12, 12);
      g.fill(sx + 2, iy + 2, sx + 10, iy + 10, overStop ? 0xFFFF9A9A : Glass.DANGER);
      hits.add(new Ui.Hit(sx, iy, sx + 12, iy + 12, Generating::stop));
      if (overStop) {
         tip = Glass.tip(Ui.t("chunkatlas.generate.stop"), null);
      }

      return tip;
   }

   static String about(double seconds) {
      long s = Math.max(1L, Math.round(seconds));
      if (s < 90L) {
         return Ui.t("chunkatlas.generate.about.seconds", s);
      }

      return s < 5400L ? Ui.t("chunkatlas.generate.about.minutes", Math.round(s / 60.0)) : Ui.t("chunkatlas.generate.about.hours", Math.round(s / 3600.0));
   }

   static WorldMap.Index reread(WorldMap.Index old, Set<Long> regions) {
      Map<Long, WorldLayout.RegionInfo> infos = new HashMap<>(old.regions());
      Map<Long, long[]> present = new HashMap<>(old.present());
      int chunks = old.chunks();
      int minRx = old.minRx();
      int minRz = old.minRz();
      int maxRx = old.maxRx();
      int maxRz = old.maxRz();
      boolean first = infos.isEmpty();
      for (long key : regions) {
         int rx = (int)(key >> 32);
         int rz = (int)key;
         Path file = old.dimension().region().resolve(RegionFile.name(rx, rz));
         long[] bits = new long[ChunkSelection.WORDS];
         WorldLayout.RegionInfo info;
         try {
            BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!a.isRegularFile() || a.size() < RegionFile.HEADER) {
               continue;
            }

            RegionFile.Header h = RegionFile.header(file);
            for (int i = 0; i < RegionFile.CHUNKS; i++) {
               if (h.present(i)) {
                  bits[i >> 6] |= 1L << (i & 63);
               }
            }

            info = new WorldLayout.RegionInfo(rx, rz, file, a.size(), a.lastModifiedTime().toMillis(), false);
         } catch (IOException | RuntimeException e) {
            continue;
         }

         long[] had = present.get(key);
         for (int w = 0; w < bits.length; w++) {
            chunks += Long.bitCount(bits[w]) - (had == null ? 0 : Long.bitCount(had[w]));
         }

         present.put(key, bits);
         infos.put(key, info);
         minRx = first ? rx : Math.min(minRx, rx);
         minRz = first ? rz : Math.min(minRz, rz);
         maxRx = first ? rx : Math.max(maxRx, rx);
         maxRz = first ? rz : Math.max(maxRz, rz);
         first = false;
      }

      return new WorldMap.Index(old.dimension(), Map.copyOf(infos), present, chunks, minRx, minRz, maxRx, maxRz, old.entities(), old.away());
   }

   void tourSelect(int w, int h) {
      ChunkSelection sel = this.map.selectionIn(this.map.shownDimension());
      int[] mid = this.map.tourMiddleChunk();
      sel.clear();
      for (int z = 0; z < h; z++) {
         for (int x = 0; x < w; x++) {
            sel.add(mid[0] - w / 2 + x, mid[1] - h / 2 + z);
         }
      }
   }

   static int[] tourFull(Path regionFolder, ChunkSelection sel) {
      int[] out = {0, sel.size()};
      for (long key : sel.regions()) {
         int rx = (int)(key >> 32);
         int rz = (int)key;
         Path file = regionFolder.resolve(RegionFile.name(rx, rz));
         if (!Files.isRegularFile(file)) {
            continue;
         }

         long[] bits = sel.bits(rx, rz);
         try (RegionFile region = RegionFile.open(file)) {
            for (int i = 0; i < RegionFile.CHUNKS; i++) {
               if ((bits[i >> 6] & 1L << (i & 63)) == 0L || !region.header().present(i)) {
                  continue;
               }

               RegionFile.Chunk c = region.chunk(i);
               if (c.ok() && "minecraft:full".equals(dev.chunkatlas.engine.Nbt.read(c.data()).get("Status"))) {
                  out[0]++;
               }
            }
         } catch (IOException | RuntimeException e) {
         }
      }

      return out;
   }
}
