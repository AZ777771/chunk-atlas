package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TaskBookTest {
   @Test
   void keepsProgressSoNothingIsDoneTwice() {
      TaskBook book = new TaskBook();
      book.atOnce = 2;
      TaskBook.Task circle = TaskBook.Task.of(book.nextId++, "minecraft:overworld", Area.of(Area.Shape.CIRCLE, 0, 0, 320), 1, true);
      circle.state = TaskBook.State.RUNNING;
      ChunkSelection made = new ChunkSelection();
      for (int x = -3; x <= 3; x++) {
         made.add(x, 0);
      }

      circle.dealt.add(0, 0, made.bits(0, 0));
      circle.dealt.add(-1, 0, made.bits(-1, 0));
      circle.generated = 5;
      book.tasks.add(circle);
      ChunkSelection sel = new ChunkSelection();
      sel.add(100, 100);
      sel.add(101, 100);
      TaskBook.Task picked = TaskBook.Task.picked(book.nextId++, "minecraft:the_nether", sel, 2, false);
      picked.state = TaskBook.State.PAUSED;
      book.tasks.add(picked);
      TaskBook.Task done = TaskBook.Task.of(book.nextId++, "minecraft:overworld", Area.of(Area.Shape.SQUARE, 0, 0, 16), 1, true);
      done.state = TaskBook.State.DONE;
      book.tasks.add(done);

      TaskBook back = TaskBook.read(book.write());
      assertEquals(2, back.atOnce);
      assertEquals(4, back.nextId);
      assertEquals(2, back.tasks.size());
      TaskBook.Task c = back.find(circle.id);
      assertEquals(circle.area, c.area);
      assertEquals(TaskBook.State.RUNNING, c.state);
      assertEquals(7, c.dealtCount());
      assertEquals(5, c.generated);
      assertEquals(circle.total, c.total);
      assertEquals(circle.total - 7, c.left().size());
      assertFalse(c.left().contains(0, 0));
      assertTrue(c.left().contains(0, 5));
      TaskBook.Task p = back.find(picked.id);
      assertNull(p.area);
      assertEquals(2, p.picked.size());
      assertEquals(TaskBook.State.PAUSED, p.state);
      assertEquals(2, p.speed);
      assertFalse(p.finishPartial);
      assertNull(back.find(done.id));
   }

   @Test
   void unreadableIsEmpty() {
      assertTrue(TaskBook.read(null).tasks.isEmpty());
      assertTrue(TaskBook.read(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}).tasks.isEmpty());
      byte[] good = new TaskBook().write();
      good[good.length - 1] = 9;
      assertTrue(TaskBook.read(good).tasks.isEmpty());
   }
}
