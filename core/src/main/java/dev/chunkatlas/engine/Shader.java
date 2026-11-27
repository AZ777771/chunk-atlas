package dev.chunkatlas.engine;

public final class Shader {
   public interface Tints {
      int tint(int biome, int kind);
   }

   public static final int VERSION = 3;

   public static final int SHADE_OFF = 0;
   public static final int SHADE_SOFT = 1;
   public static final int SHADE_STRONG = 2;

   public record Options(int shading, boolean clearWater, boolean biomeTint, boolean heightTint, boolean night, boolean contours, boolean dry, int step,
      int water, Tints tints, int ramp) {
   }

   public static final int RAMP_NONE = 0;
   public static final int RAMP_HEIGHT = 1;
   public static final int RAMP_DEPTH = 2;

   private static final int[] HEIGHT_STOPS = {-64, 0x1B2A6B, 30, 0x2A8C8C, 64, 0x3FA34D, 100, 0xD8C54A, 160, 0x9C6B3C, 240, 0xF2F2F2};

   private static final int[] DEPTH_STOPS = {0, 0xA8E6F0, 8, 0x4FB3D9, 24, 0x2667B3, 48, 0x1A2F7A, 96, 0x0B1033};

   static int ramp(int[] stops, double v) {
      if (v <= stops[0]) {
         return stops[1];
      }

      for (int i = 2; i < stops.length; i += 2) {
         if (v <= stops[i]) {
            double k = (v - stops[i - 2]) / (double)(stops[i] - stops[i - 2]);
            return mix(stops[i - 1], stops[i + 1], k);
         }
      }

      return stops[stops.length - 1];
   }

   private static final int DEEP = 0x1A2430;

   private Shader() {
   }

   public static int[] shade(Surface s, int apron, Options o, int[] out) {
      int side = s.side;
      int n = side - apron * 2;
      int[][] tints = new int[5][];
      int[] cache = new int[0];
      int step = Math.max(1, o.step());

      boolean[] used = new boolean[5];
      for (int i = 0; i < side * side; i++) {
         used[s.top[i] >> 24 & 7] = true;
         if (s.cover[i] != 0) {
            used[s.cover[i] >> 24 & 7] = true;
         }

         if (s.depth[i] != 0) {
            used[Look.TINT_WATER] = true;
         }
      }

      int radius = step >= 2 ? 1 : 2;
      for (int k = 1; k < 5; k++) {
         if (used[k]) {
            tints[k] = blurredTint(s, k, radius, o);
         }
      }

      boolean strong = o.shading() == SHADE_STRONG;
      double up1 = strong ? 1.16 : 1.12;
      double up2 = strong ? 1.28 : 1.2;
      double down1 = strong ? 0.8 : 0.86;
      double down2 = strong ? 0.66 : 0.75;

      double per = Math.max(1.0, step / 4.0);
      for (int z = 0; z < n; z++) {
         for (int x = 0; x < n; x++) {
            int gx = x + apron;
            int gz = z + apron;
            int i = gz * side + gx;
            int at = z * n + x;
            if (!s.drawn(i)) {
               out[at] = 0;
               continue;
            }

            int t = s.top[i];
            int c = s.cover[i];
            int d = s.depth[i] & 255;
            boolean wet = d > 0 && !o.dry();
            double h0 = ground(s, i);

            int ground = t != 0 ? tinted(t, tints, i) : DEEP;
            if (c != 0 && (c & Surface.ABOVE) == 0) {
               ground = mix(ground, tinted(c, tints, i), (c >> 27 & 15) / 15.0);
            }

            if (!wet) {
               ground = saturate(ground, 1.15);
            }

            double light = 1.0;
            if (o.shading() != SHADE_OFF) {
               double north = gz > 0 ? ground(s, gx, gz - 1, h0) : 2.0 * h0 - ground(s, gx, gz + 1, h0);
               double west = gx > 0 ? ground(s, gx - 1, gz, h0) : 2.0 * h0 - ground(s, gx + 1, gz, h0);
               double rise = (h0 - north) + 0.5 * (h0 - west);
               rise /= per;
               light = rise >= 2.0 ? up2 : rise >= 0.5 ? up1 : rise <= -2.0 ? down2 : rise <= -0.5 ? down1 : 1.0;
            }

            if (o.heightTint()) {
               light *= 0.8 + 0.4 * clamp((h0 + 20.0) / 280.0, 0.0, 1.0);
            }

            if ((t & Surface.LEAVES) != 0) {
               boolean edge = false;
               int[][] around = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
               for (int[] a : around) {
                  int j = index(side, gx + a[0], gz + a[1]);
                  if (!s.drawn(j) || (s.top[j] & Surface.LEAVES) == 0 && ground(s, j) < h0 - 1.0) {
                     edge = true;
                     break;
                  }
               }

               if (edge) {
                  light *= 0.78;
               }
            }

            if ((t & Surface.LAVA) != 0 && !wet) {
               light = Math.max(light, 1.05);
            }

            if (o.contours() && !wet) {
               int band = (int)Math.floor(h0) >> 3;
               if ((int)Math.floor(ground(s, gx - 1, gz, h0)) >> 3 != band || (int)Math.floor(ground(s, gx, gz - 1, h0)) >> 3 != band) {
                  light *= 0.72;
               }
            }

            int colour = scale(ground, light);
            if (!wet && d == 0 && t != 0 && besideWater(s, gx, gz)) {
               colour = scale(colour, 0.9);
            }

            if (wet) {
               int water = multiply(o.water(), tints[Look.TINT_WATER] == null ? 0x3F76E4 : tints[Look.TINT_WATER][i]);
               double deep = d >= 255 ? 1.0 : 1.0 - Math.exp(-d / 6.5);

               int shaded = scale(water, 1.3 - 0.55 * deep);
               if (o.clearWater() && t != 0) {
                  int max = Math.max(1, Math.max(water >> 16 & 255, Math.max(water >> 8 & 255, water & 255)));
                  int filter = scale(water, 255.0 / max);
                  int bed = scale(multiply(colour, mix(filter, 0xFFFFFF, 0.2)), 1.08);
                  colour = mix(bed, shaded, 0.22 + 0.74 * deep);
               } else {
                  colour = shaded;
               }

               if (besideLand(s, gx, gz)) {
                  colour = mix(colour, 0xE6F2FF, 0.14);
               }
            }

            if (c != 0 && (c & Surface.ABOVE) != 0) {
               colour = mix(colour, tinted(c, tints, i), (c >> 27 & 15) / 15.0);
            }

            if (o.ramp() == RAMP_HEIGHT) {
               colour = scale(ramp(HEIGHT_STOPS, h0), light);
            } else if (o.ramp() == RAMP_DEPTH) {
               if (wet) {
                  colour = scale(ramp(DEPTH_STOPS, d >= 255 ? 96 : d), Math.min(1.0, light));
               } else {
                  int grey = ((colour >> 16 & 255) * 3 + (colour >> 8 & 255) * 6 + (colour & 255)) / 10;
                  colour = scale(grey * 0x010101, 0.8);
               }
            }

            if (o.night()) {
               int lit = s.light[i] & 15;
               if ((t & Surface.GLOW) != 0 && !wet) {
                  lit = 15;
               }

               colour = night(colour, lit);
            }

            out[at] = 0xFF000000 | colour & 0xFFFFFF;
         }
      }

      return out;
   }

   public static int night(int colour, int lit) {
      double f = lit / 15.0;
      double b = 0.2 + 0.8 * Math.pow(f, 1.6);
      int r = colour >> 16 & 255;
      int g = colour >> 8 & 255;
      int bl = colour & 255;

      double warm = f * 0.45;
      r = (int)Math.min(255.0, r * b * (1.0 + warm * 0.1));
      g = (int)Math.min(255.0, g * b * (1.0 - warm * 0.12));
      bl = (int)Math.min(255.0, bl * b * (1.0 - warm * 0.35 + (1.0 - f) * 0.25));
      return r << 16 | g << 8 | bl;
   }

   private static double ground(Surface s, int i) {
      int d = s.depth[i] & 255;
      return d > 0 && d < 255 ? s.height[i] - d : s.height[i];
   }

   private static double ground(Surface s, int x, int z, double otherwise) {
      int j = index(s.side, x, z);
      return s.drawn(j) ? ground(s, j) : otherwise;
   }

   private static int index(int side, int x, int z) {
      return Math.max(0, Math.min(side - 1, z)) * side + Math.max(0, Math.min(side - 1, x));
   }

   private static boolean besideWater(Surface s, int x, int z) {
      return wetAt(s, x - 1, z) || wetAt(s, x + 1, z) || wetAt(s, x, z - 1) || wetAt(s, x, z + 1);
   }

   private static boolean besideLand(Surface s, int x, int z) {
      return landAt(s, x - 1, z) || landAt(s, x + 1, z) || landAt(s, x, z - 1) || landAt(s, x, z + 1);
   }

   private static boolean wetAt(Surface s, int x, int z) {
      if (x < 0 || z < 0 || x >= s.side || z >= s.side) {
         return false;
      }

      return s.depth[z * s.side + x] != 0;
   }

   private static boolean landAt(Surface s, int x, int z) {
      if (x < 0 || z < 0 || x >= s.side || z >= s.side) {
         return false;
      }

      int j = z * s.side + x;
      return s.depth[j] == 0 && s.top[j] != 0 && (s.top[j] & Surface.LAVA) == 0;
   }

   private static int tinted(int packed, int[][] tints, int i) {
      int kind = packed >> 24 & 7;
      int rgb = packed & 0xFFFFFF;
      return kind == 0 || tints[kind] == null ? rgb : multiply(rgb, tints[kind][i]);
   }

   private static int[] blurredTint(Surface s, int kind, int radius, Options o) {
      int side = s.side;
      int n = side * side;
      int[] raw = new int[n];
      boolean[] has = new boolean[n];
      java.util.HashMap<Integer, Integer> seen = new java.util.HashMap<>();
      for (int i = 0; i < n; i++) {
         int b = o.biomeTint() ? s.biome[i] : -1;
         Integer c = seen.get(b);
         if (c == null) {
            c = o.tints().tint(b, kind) & 0xFFFFFF;
            seen.put(b, c);
         }

         raw[i] = c;
         has[i] = s.drawn(i) && s.biome[i] >= 0;
      }

      if (!o.biomeTint() || seen.size() <= 1) {
         return raw;
      }

      int[] r = new int[n];
      int[] g = new int[n];
      int[] b = new int[n];
      int[] w = new int[n];
      for (int z = 0; z < side; z++) {
         int sr = 0, sg = 0, sb = 0, sw = 0;
         int row = z * side;
         for (int x = -radius; x < side; x++) {
            int in = x + radius;
            if (in < side && has[row + in]) {
               int c = raw[row + in];
               sr += c >> 16 & 255;
               sg += c >> 8 & 255;
               sb += c & 255;
               sw++;
            }

            int outX = x - radius - 1;
            if (outX >= 0 && has[row + outX]) {
               int c = raw[row + outX];
               sr -= c >> 16 & 255;
               sg -= c >> 8 & 255;
               sb -= c & 255;
               sw--;
            }

            if (x >= 0) {
               r[row + x] = sr;
               g[row + x] = sg;
               b[row + x] = sb;
               w[row + x] = sw;
            }
         }
      }

      int[] out = new int[n];
      for (int x = 0; x < side; x++) {
         int sr = 0, sg = 0, sb = 0, sw = 0;
         for (int z = -radius; z < side; z++) {
            int in = z + radius;
            if (in < side) {
               int j = in * side + x;
               sr += r[j];
               sg += g[j];
               sb += b[j];
               sw += w[j];
            }

            int outZ = z - radius - 1;
            if (outZ >= 0) {
               int j = outZ * side + x;
               sr -= r[j];
               sg -= g[j];
               sb -= b[j];
               sw -= w[j];
            }

            if (z >= 0) {
               int j = z * side + x;
               out[j] = sw <= 0 ? raw[j] : (sr / sw) << 16 | (sg / sw) << 8 | sb / sw;
            }
         }
      }

      return out;
   }

   static int saturate(int rgb, double f) {
      int r = rgb >> 16 & 255;
      int g = rgb >> 8 & 255;
      int b = rgb & 255;
      double grey = r * 0.299 + g * 0.587 + b * 0.114;
      r = (int)clamp(grey + (r - grey) * f, 0.0, 255.0);
      g = (int)clamp(grey + (g - grey) * f, 0.0, 255.0);
      b = (int)clamp(grey + (b - grey) * f, 0.0, 255.0);
      return r << 16 | g << 8 | b;
   }

   static double clamp(double v, double lo, double hi) {
      return v < lo ? lo : v > hi ? hi : v;
   }

   static int scale(int rgb, double f) {
      int r = (int)Math.min(255.0, (rgb >> 16 & 255) * f);
      int g = (int)Math.min(255.0, (rgb >> 8 & 255) * f);
      int b = (int)Math.min(255.0, (rgb & 255) * f);
      return Math.max(0, r) << 16 | Math.max(0, g) << 8 | Math.max(0, b);
   }

   static int mix(int a, int b, double t) {
      t = clamp(t, 0.0, 1.0);
      int r = (int)Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
      int g = (int)Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
      int bl = (int)Math.round((a & 255) * (1 - t) + (b & 255) * t);
      return r << 16 | g << 8 | bl;
   }

   static int multiply(int a, int b) {
      int r = (a >> 16 & 255) * (b >> 16 & 255) / 255;
      int g = (a >> 8 & 255) * (b >> 8 & 255) / 255;
      int bl = (a & 255) * (b & 255) / 255;
      return r << 16 | g << 8 | bl;
   }
}
