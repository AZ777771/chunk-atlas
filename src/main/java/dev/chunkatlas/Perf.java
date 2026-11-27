package dev.chunkatlas;

import java.util.Arrays;
import java.util.Locale;

public final class Perf {
   private static final int MAX = 1 << 16;
   private static boolean recording;
   private static long lastFrame;
   private static final float[] frameMs = new float[MAX];
   private static int frames;
   private static final float[] hudMs = new float[MAX];
   private static int huds;
   private static final float[] scanMs = new float[MAX];
   private static final int[] checkedPerTick = new int[MAX];
   private static final int[] readPerTick = new int[MAX];
   private static int ticks;
   private static long skipped;
   private static long shaded;
   private static long texels;
   private static long uploadTexels;
   private static long cellsDrawn;

   public static long totalChecked;
   public static long totalRead;
   public static long totalSkipped;

   private Perf() {
   }

   public static void start() {
      recording = true;
      frames = 0;
      huds = 0;
      ticks = 0;
      skipped = 0L;
      shaded = 0L;
      texels = 0L;
      uploadTexels = 0L;
      cellsDrawn = 0L;
      lastFrame = 0L;
   }

   public static void stop() {
      recording = false;
   }

   public static void frame() {
      long now = System.nanoTime();
      if (recording && lastFrame != 0L && frames < MAX) {
         frameMs[frames++] = (now - lastFrame) / 1.0E6F;
      }

      lastFrame = now;
   }

   public static void hud(long ns) {
      if (recording && huds < MAX) {
         hudMs[huds++] = ns / 1.0E6F;
      }
   }

   public static void terrain(int drawn, int uploaded, int cells) {
      if (recording) {
         texels += drawn;
         uploadTexels += uploaded;
         cellsDrawn += cells;
      }
   }

   public static void scan(int checked, int read, int unchanged, int tilesShaded, long ns) {
      totalChecked += checked;
      totalRead += read;
      totalSkipped += unchanged;
      if (recording && ticks < MAX) {
         checkedPerTick[ticks] = checked;
         readPerTick[ticks] = read;
         scanMs[ticks] = ns / 1.0E6F;
         ticks++;
         skipped += unchanged;
         shaded += tilesShaded;
      }
   }

   public static String[] report(String run) {
      float[] f = Arrays.copyOf(frameMs, frames);
      float[] h = Arrays.copyOf(hudMs, huds);
      float[] s = Arrays.copyOf(scanMs, ticks);
      Arrays.sort(f);
      Arrays.sort(h);
      Arrays.sort(s);
      long checked = 0L;
      long read = 0L;
      int checkedMax = 0;
      for (int i = 0; i < ticks; i++) {
         checked += checkedPerTick[i];
         read += readPerTick[i];
         checkedMax = Math.max(checkedMax, checkedPerTick[i]);
      }

      int t = Math.max(1, ticks);
      int n = Math.max(1, huds);
      return new String[]{
         String.format(Locale.ROOT, "frametour frames run=%s frames=%d median_ms=%.2f p99_ms=%.2f max_ms=%.2f", run, frames, at(f, 0.5), at(f, 0.99), at(f, 1.0)),
         String.format(Locale.ROOT, "frametour minimap run=%s frames=%d median_ms=%.3f p99_ms=%.3f texels_per_frame=%d uploaded_per_frame=%d cells_per_frame=%.1f", run, huds,
            at(h, 0.5), at(h, 0.99), texels / n, uploadTexels / n, (double)cellsDrawn / n),
         String.format(Locale.ROOT, "frametour scan run=%s ticks=%d checked_per_tick=%.1f checked_max=%d read_per_tick=%.2f unchanged_per_tick=%.2f shaded_per_tick=%.2f scan_ms_median=%.3f scan_ms_p99=%.3f scan_ms_mean=%.3f",
            run, ticks, (double)checked / t, checkedMax, (double)read / t, (double)skipped / t, (double)shaded / t, at(s, 0.5), at(s, 0.99), mean(s))
      };
   }

   private static float at(float[] sorted, double q) {
      if (sorted.length == 0) {
         return 0.0F;
      }

      return sorted[Math.min(sorted.length - 1, (int)Math.floor(q * (sorted.length - 1)))];
   }

   private static double mean(float[] v) {
      double sum = 0.0;
      for (float x : v) {
         sum += x;
      }

      return v.length == 0 ? 0.0 : sum / v.length;
   }
}
