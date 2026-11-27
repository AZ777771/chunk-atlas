package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AreaTest {
   @Test
   void squareCountsWholeChunks() {
      Area a = Area.of(Area.Shape.SQUARE, 0, 0, 2000);
      assertEquals(250L * 250L, a.chunkCount());
      assertEquals(a.chunkCount(), a.chunks().size());
      assertTrue(a.containsChunk(0, 0));
      assertTrue(a.containsChunk(-125, 124));
      assertFalse(a.containsChunk(125, 0));
   }

   @Test
   void circleIsRoughlyPiRSquared() {
      Area a = Area.of(Area.Shape.CIRCLE, 0, 0, 3200);
      double expected = Math.PI * 200.0 * 200.0;
      assertEquals(expected, a.chunkCount(), expected * 0.002);
   }

   @Test
   void everyShapeCountsWhatItLists() {
      for (Area.Shape s : Area.Shape.values()) {
         Area a = new Area(s, 37.5, -260, 300, 180);
         ChunkSelection listed = a.chunks();
         assertEquals(a.chunkCount(), listed.size(), s.id());

         int[] b = a.chunkBounds();
         int in = 0;
         for (int z = b[1] - 1; z <= b[3] + 1; z++) {
            for (int x = b[0] - 1; x <= b[2] + 1; x++) {
               boolean has = a.containsChunk(x, z);
               assertEquals(has, listed.contains(x, z), s.id() + " " + x + "," + z);
               in += has ? 1 : 0;
            }
         }

         assertEquals(listed.size(), in, s.id());
      }
   }

   @Test
   void shapesNestInsideTheirCircle() {
      double r = 1000;
      long circle = Area.of(Area.Shape.CIRCLE, 0, 0, r).chunkCount();
      long square = Area.of(Area.Shape.SQUARE, 0, 0, r).chunkCount();
      assertTrue(square > circle);
      for (Area.Shape s : new Area.Shape[]{Area.Shape.DIAMOND, Area.Shape.TRIANGLE, Area.Shape.PENTAGON, Area.Shape.HEXAGON, Area.Shape.STAR}) {
         assertTrue(Area.of(s, 0, 0, r).chunkCount() < circle, s.id());
      }

      assertTrue(Area.of(Area.Shape.STAR, 0, 0, r).chunkCount() < Area.of(Area.Shape.PENTAGON, 0, 0, r).chunkCount());
   }

   @Test
   void polygonsPointNorth() {
      Area tri = Area.of(Area.Shape.TRIANGLE, 0, 0, 100);
      assertTrue(tri.contains(0, -99));
      assertFalse(tri.contains(0, 99));
      Area star = Area.of(Area.Shape.STAR, 0, 0, 100);
      assertTrue(star.contains(0, -95));
      assertTrue(star.contains(0, 0));

      assertFalse(star.contains(Math.cos(Math.toRadians(-54)) * 90, Math.sin(Math.toRadians(-54)) * 90));
   }

   @Test
   void secondRadiusOnlyCountsForRectangleAndEllipse() {
      assertEquals(Area.of(Area.Shape.CIRCLE, 0, 0, 100), new Area(Area.Shape.CIRCLE, 0, 0, 100, 900));
      Area rect = new Area(Area.Shape.RECTANGLE, 0, 0, 160, 80);
      assertEquals(20L * 10L, rect.chunkCount());
      assertTrue(new Area(Area.Shape.ELLIPSE, 0, 0, 300, 100).contains(290, 0));
      assertFalse(new Area(Area.Shape.ELLIPSE, 0, 0, 300, 100).contains(0, 110));
   }

   @Test
   void nearestInsideIsInsideAndClose() {
      for (Area.Shape s : Area.Shape.values()) {
         Area a = new Area(s, 10, 20, 200, 120);
         double[][] tries = {{500, 20}, {10, -900}, {-300, 300}, {260, -140}, {10, 20}};
         for (double[] p : tries) {
            double[] q = a.nearestInside(p[0], p[1], 1.0);
            assertTrue(a.contains(q[0], q[1]), s.id() + " " + q[0] + "," + q[1]);
            if (!a.contains(p[0], p[1])) {
               double straight = Math.hypot(q[0] - p[0], q[1] - p[1]);
               assertTrue(straight <= a.edgeDistance(p[0], p[1]) + 6.0, s.id() + " moved " + straight);
            }
         }
      }
   }

   @Test
   void wrapComesInOnTheFarSide() {
      Area sq = Area.of(Area.Shape.SQUARE, 0, 0, 100);
      double[] w = sq.wrap(101, 30, 2.0);
      assertTrue(sq.contains(w[0], w[1]));
      assertTrue(w[0] < -90);
      assertEquals(30, w[1], 1.0E-9);
      Area circle = Area.of(Area.Shape.CIRCLE, 0, 0, 100);
      double[] c = circle.wrap(0, -101, 2.0);
      assertTrue(circle.contains(c[0], c[1]));
      assertTrue(c[1] > 90);
      for (Area.Shape s : Area.Shape.values()) {
         Area a = new Area(s, 0, 0, 150, 90);
         double[] q = a.wrap(400, 33, 2.0);
         assertTrue(a.contains(q[0], q[1]), s.id());
         assertTrue(q[0] < 0, s.id());
      }
   }

   @Test
   void readsWhatItWrites() {
      Area a = new Area(Area.Shape.ELLIPSE, -0.5, 1234, 300, 75.25);
      assertEquals(a, Area.read(a.write()));
      assertNull(Area.read("blob 1 2 3"));
      assertNull(Area.read("circle x 2 3"));
      assertNotNull(Area.read("circle 0 0 50"));
   }

   @Test
   void bordersRoundTrip() {
      BorderBook book = new BorderBook();
      book.borders.put("minecraft:overworld", new BorderBook.Border(Area.of(Area.Shape.CIRCLE, 0, 0, 2000), true));
      book.borders.put("minecraft:the_nether", new BorderBook.Border(new Area(Area.Shape.RECTANGLE, 5, -5, 100, 60), false));
      BorderBook back = BorderBook.read(book.write());
      assertEquals(book.borders, back.borders);
      assertTrue(BorderBook.read("nonsense\nminecraft:overworld star 0 0").borders.isEmpty());
   }
}
