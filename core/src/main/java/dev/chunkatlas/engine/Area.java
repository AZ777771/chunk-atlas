package dev.chunkatlas.engine;

import java.util.Locale;

public final class Area {
   public static final double MAX_RADIUS = 30_000_000.0;

   private static final int ROUND_POINTS = 128;

   private static final double STAR_INNER = 0.3819660112501051;

   public enum Shape {
      SQUARE(0),
      RECTANGLE(0),
      CIRCLE(0),
      ELLIPSE(0),
      DIAMOND(4),
      TRIANGLE(3),
      PENTAGON(5),
      HEXAGON(6),
      STAR(5);

      private final int corners;

      Shape(int corners) {
         this.corners = corners;
      }

      public boolean twoRadii() {
         return this == RECTANGLE || this == ELLIPSE;
      }

      public String id() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public static Shape of(String id) {
         for (Shape s : values()) {
            if (s.id().equalsIgnoreCase(id == null ? "" : id.strip())) {
               return s;
            }
         }

         return null;
      }

      public static Shape of(int ordinal) {
         Shape[] all = values();
         return all[Math.max(0, Math.min(all.length - 1, ordinal))];
      }
   }

   private final Area.Shape shape;
   private final double cx;
   private final double cz;
   private final double rx;
   private final double rz;

   private final double[] corners;

   public Area(Area.Shape shape, double cx, double cz, double rx, double rz) {
      if (shape == null) {
         throw new IllegalArgumentException("no shape");
      }

      this.shape = shape;
      this.cx = cx;
      this.cz = cz;
      this.rx = clampRadius(rx);
      this.rz = shape.twoRadii() ? clampRadius(rz) : this.rx;
      this.corners = shape.corners > 0 ? this.makeCorners() : null;
   }

   public Area.Shape shape() {
      return this.shape;
   }

   public double cx() {
      return this.cx;
   }

   public double cz() {
      return this.cz;
   }

   public double rx() {
      return this.rx;
   }

   public double rz() {
      return this.rz;
   }

   @Override
   public boolean equals(Object o) {
      return o instanceof Area a && a.shape == this.shape && a.cx == this.cx && a.cz == this.cz && a.rx == this.rx && a.rz == this.rz;
   }

   @Override
   public int hashCode() {
      return java.util.Objects.hash(this.shape, this.cx, this.cz, this.rx, this.rz);
   }

   @Override
   public String toString() {
      return this.write();
   }

   private static double clampRadius(double r) {
      return Double.isNaN(r) ? 1.0 : Math.max(1.0, Math.min(MAX_RADIUS, r));
   }

   public static Area of(Area.Shape shape, double cx, double cz, double r) {
      return new Area(shape, cx, cz, r, r);
   }

   public Area at(double x, double z) {
      return new Area(this.shape, x, z, this.rx, this.rz);
   }

   public boolean contains(double x, double z) {
      double dx = x - this.cx;
      double dz = z - this.cz;
      return switch (this.shape) {
         case SQUARE, RECTANGLE -> Math.abs(dx) <= this.rx && Math.abs(dz) <= this.rz;
         case CIRCLE -> dx * dx + dz * dz <= this.rx * this.rx;
         case ELLIPSE -> {
            double a = dx / this.rx;
            double b = dz / this.rz;
            yield a * a + b * b <= 1.0;
         }
         case DIAMOND -> Math.abs(dx) + Math.abs(dz) <= this.rx;
         default -> {
            if (dx * dx + dz * dz > this.rx * this.rx) {
               yield false;
            }

            yield inPolygon(this.corners(), x, z);
         }
      };
   }

   public boolean containsChunk(int chunkX, int chunkZ) {
      return this.contains(chunkX * 16.0 + 8.0, chunkZ * 16.0 + 8.0);
   }

   public double[] bounds() {
      if (this.shape.corners > 0) {
         double[] p = this.corners();
         double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
         for (int i = 0; i < p.length; i += 2) {
            b[0] = Math.min(b[0], p[i]);
            b[1] = Math.min(b[1], p[i + 1]);
            b[2] = Math.max(b[2], p[i]);
            b[3] = Math.max(b[3], p[i + 1]);
         }

         return b;
      }

      return new double[]{this.cx - this.rx, this.cz - this.rz, this.cx + this.rx, this.cz + this.rz};
   }

   public int[] chunkBounds() {
      double[] b = this.bounds();
      return new int[]{(int)Math.floor((b[0] - 8.0) / 16.0), (int)Math.floor((b[1] - 8.0) / 16.0), (int)Math.ceil((b[2] - 8.0) / 16.0), (int)Math.ceil((b[3] - 8.0) / 16.0)};
   }

   public long chunkCount() {
      int[] b = this.chunkBounds();
      long n = 0L;
      for (int z = b[1]; z <= b[3]; z++) {
         int[] run = this.row(z, b[0], b[2]);
         for (int i = 0; i < run.length; i += 2) {
            n += run[i + 1] - run[i] + 1;
         }
      }

      return n;
   }

   public ChunkSelection chunks() {
      ChunkSelection out = new ChunkSelection();
      int[] b = this.chunkBounds();
      for (int z = b[1]; z <= b[3]; z++) {
         int[] run = this.row(z, b[0], b[2]);
         for (int i = 0; i < run.length; i += 2) {
            for (int x = run[i]; x <= run[i + 1]; x++) {
               out.add(x, z);
            }
         }
      }

      return out;
   }

   int[] row(int chunkZ, int minX, int maxX) {
      int mid = (int)Math.floor((this.cx - 8.0) / 16.0);
      mid = Math.max(minX, Math.min(maxX, mid));
      if (this.shape == Shape.STAR) {
         int[] out = new int[8];
         int n = 0;
         int start = Integer.MIN_VALUE;
         for (int x = minX; x <= maxX + 1; x++) {
            boolean in = x <= maxX && this.containsChunk(x, chunkZ);
            if (in && start == Integer.MIN_VALUE) {
               start = x;
            } else if (!in && start != Integer.MIN_VALUE) {
               if (n + 2 > out.length) {
                  out = java.util.Arrays.copyOf(out, out.length * 2);
               }

               out[n++] = start;
               out[n++] = x - 1;
               start = Integer.MIN_VALUE;
            }
         }

         return java.util.Arrays.copyOf(out, n);
      }

      int seed = Integer.MIN_VALUE;
      if (this.containsChunk(mid, chunkZ)) {
         seed = mid;
      } else if (mid + 1 <= maxX && this.containsChunk(mid + 1, chunkZ)) {
         seed = mid + 1;
      } else {
         for (int x = minX; x <= maxX; x++) {
            if (this.containsChunk(x, chunkZ)) {
               seed = x;
               break;
            }
         }
      }

      if (seed == Integer.MIN_VALUE) {
         return new int[0];
      }

      int lo = minX;
      int hi = seed;
      while (lo < hi) {
         int m = lo + (hi - lo) / 2;
         if (this.containsChunk(m, chunkZ)) {
            hi = m;
         } else {
            lo = m + 1;
         }
      }

      int first = lo;
      lo = seed;
      hi = maxX;
      while (lo < hi) {
         int m = lo + (hi - lo + 1) / 2;
         if (this.containsChunk(m, chunkZ)) {
            lo = m;
         } else {
            hi = m - 1;
         }
      }

      return new int[]{first, lo};
   }

   private double[] corners() {
      return this.corners;
   }

   private double[] makeCorners() {
      if (this.shape == Shape.DIAMOND) {
         return new double[]{this.cx, this.cz - this.rx, this.cx + this.rx, this.cz, this.cx, this.cz + this.rx, this.cx - this.rx, this.cz};
      }

      int n = this.shape.corners;
      boolean star = this.shape == Shape.STAR;
      int points = star ? n * 2 : n;
      double[] out = new double[points * 2];
      for (int i = 0; i < points; i++) {
         double angle = -Math.PI / 2.0 + 2.0 * Math.PI * i / points;
         double r = star && (i & 1) == 1 ? this.rx * STAR_INNER : this.rx;
         out[2 * i] = this.cx + Math.cos(angle) * r;
         out[2 * i + 1] = this.cz + Math.sin(angle) * r;
      }

      return out;
   }

   public double[] outline() {
      return switch (this.shape) {
         case SQUARE, RECTANGLE -> new double[]{
            this.cx - this.rx, this.cz - this.rz, this.cx + this.rx, this.cz - this.rz, this.cx + this.rx, this.cz + this.rz, this.cx - this.rx, this.cz + this.rz
         };
         case CIRCLE, ELLIPSE -> {
            double[] out = new double[ROUND_POINTS * 2];
            for (int i = 0; i < ROUND_POINTS; i++) {
               double angle = -Math.PI / 2.0 + 2.0 * Math.PI * i / ROUND_POINTS;
               out[2 * i] = this.cx + Math.cos(angle) * this.rx;
               out[2 * i + 1] = this.cz + Math.sin(angle) * this.rz;
            }

            yield out;
         }
         default -> this.corners.clone();
      };
   }

   public double edgeDistance(double x, double z) {
      double[] p = this.nearestEdge(x, z);
      return Math.hypot(p[0] - x, p[1] - z);
   }

   public double[] nearestEdge(double x, double z) {
      if (this.shape == Shape.CIRCLE) {
         double dx = x - this.cx;
         double dz = z - this.cz;
         double d = Math.hypot(dx, dz);
         if (d < 1.0E-9) {
            return new double[]{this.cx, this.cz - this.rx};
         }

         return new double[]{this.cx + dx / d * this.rx, this.cz + dz / d * this.rx};
      }

      double[] p = this.outline();
      double best = Double.MAX_VALUE;
      double[] out = {this.cx, this.cz};
      int n = p.length / 2;
      for (int i = 0; i < n; i++) {
         double ax = p[2 * i];
         double az = p[2 * i + 1];
         double bx = p[2 * ((i + 1) % n)];
         double bz = p[2 * ((i + 1) % n) + 1];
         double ex = bx - ax;
         double ez = bz - az;
         double len = ex * ex + ez * ez;
         double t = len == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, ((x - ax) * ex + (z - az) * ez) / len));
         double qx = ax + ex * t;
         double qz = az + ez * t;
         double d = (qx - x) * (qx - x) + (qz - z) * (qz - z);
         if (d < best) {
            best = d;
            out[0] = qx;
            out[1] = qz;
         }
      }

      return out;
   }

   public double[] nearestInside(double x, double z, double margin) {
      if (this.contains(x, z) && this.edgeDistance(x, z) >= margin) {
         return new double[]{x, z};
      }

      double[] e = this.nearestEdge(x, z);
      return this.inward(e[0], e[1], margin);
   }

   private double[] inward(double ex, double ez, double margin) {
      double dx = this.cx - ex;
      double dz = this.cz - ez;
      double d = Math.hypot(dx, dz);
      if (d < 1.0E-9) {
         return new double[]{this.cx, this.cz};
      }

      double ux = dx / d;
      double uz = dz / d;
      double step = Math.min(margin, d);
      double[] p = {ex + ux * step, ez + uz * step};

      for (int i = 0; i < 64 && (!this.contains(p[0], p[1]) || this.edgeDistance(p[0], p[1]) < margin * 0.5) && step < d; i++) {
         step = Math.min(d, step + Math.max(0.5, margin * 0.5));
         p[0] = ex + ux * step;
         p[1] = ez + uz * step;
      }

      return p;
   }

   public double[] wrap(double x, double z, double margin) {
      if (this.shape == Shape.SQUARE || this.shape == Shape.RECTANGLE) {
         double dx = x - this.cx;
         double dz = z - this.cz;
         double m = Math.min(margin, Math.min(this.rx, this.rz) * 0.5);
         double nx = x;
         double nz = z;
         if (Math.abs(dx) > this.rx - m * 0.5) {
            nx = this.cx - Math.signum(dx) * (this.rx - m);
         }

         if (Math.abs(dz) > this.rz - m * 0.5) {
            nz = this.cz - Math.signum(dz) * (this.rz - m);
         }

         nx = Math.max(this.cx - this.rx + m, Math.min(this.cx + this.rx - m, nx));
         nz = Math.max(this.cz - this.rz + m, Math.min(this.cz + this.rz - m, nz));
         return new double[]{nx, nz};
      }

      double dx = x - this.cx;
      double dz = z - this.cz;
      double d = Math.hypot(dx, dz);
      if (d < 1.0E-9) {
         return new double[]{this.cx, this.cz};
      }

      double ux = -dx / d;
      double uz = -dz / d;
      double lo = 0.0;
      double hi = Math.max(this.rx, this.rz) * 1.01 + 1.0;
      for (int i = 0; i < 48; i++) {
         double m = (lo + hi) / 2.0;
         if (this.contains(this.cx + ux * m, this.cz + uz * m)) {
            lo = m;
         } else {
            hi = m;
         }
      }

      return this.inward(this.cx + ux * lo, this.cz + uz * lo, margin);
   }

   public String write() {
      return this.shape.id() + " " + num(this.cx) + " " + num(this.cz) + " " + num(this.rx) + " " + num(this.rz);
   }

   public static Area read(String line) {
      String[] p = line == null ? new String[0] : line.strip().split("\\s+");
      if (p.length < 4) {
         return null;
      }

      Area.Shape shape = Shape.of(p[0]);
      try {
         double cx = Double.parseDouble(p[1]);
         double cz = Double.parseDouble(p[2]);
         double rx = Double.parseDouble(p[3]);
         double rz = p.length > 4 ? Double.parseDouble(p[4]) : rx;
         if (shape == null || !Double.isFinite(cx) || !Double.isFinite(cz) || Math.abs(cx) > MAX_RADIUS || Math.abs(cz) > MAX_RADIUS) {
            return null;
         }

         return new Area(shape, cx, cz, rx, rz);
      } catch (NumberFormatException e) {
         return null;
      }
   }

   private static String num(double v) {
      return v == Math.rint(v) && Math.abs(v) < 1.0E15 ? Long.toString((long)v) : Double.toString(v);
   }

   private static boolean inPolygon(double[] p, double x, double z) {
      boolean in = false;
      int n = p.length / 2;
      for (int i = 0, j = n - 1; i < n; j = i++) {
         double xi = p[2 * i];
         double zi = p[2 * i + 1];
         double xj = p[2 * j];
         double zj = p[2 * j + 1];
         if (zi > z != zj > z && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
            in = !in;
         }
      }

      return in || onEdge(p, x, z);
   }

   private static boolean onEdge(double[] p, double x, double z) {
      int n = p.length / 2;
      for (int i = 0; i < n; i++) {
         double ax = p[2 * i];
         double az = p[2 * i + 1];
         double bx = p[2 * ((i + 1) % n)];
         double bz = p[2 * ((i + 1) % n) + 1];
         double cross = (bx - ax) * (z - az) - (bz - az) * (x - ax);
         if (Math.abs(cross) < 1.0E-9 && x >= Math.min(ax, bx) - 1.0E-9 && x <= Math.max(ax, bx) + 1.0E-9 && z >= Math.min(az, bz) - 1.0E-9 && z <= Math.max(az, bz) + 1.0E-9) {
            return true;
         }
      }

      return false;
   }
}
