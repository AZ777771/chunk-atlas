package dev.chunkatlas.engine;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class Background {
   public static volatile boolean playing;

   private Background() {
   }

   public static int cores() {
      return Runtime.getRuntime().availableProcessors();
   }

   public static int threads(int most) {
      int n = playing ? cores() / 3 : cores() - 1;
      return Math.max(1, Math.min(most, n));
   }

   public static ThreadFactory named(String name) {
      AtomicInteger count = new AtomicInteger();
      return r -> {
         Thread t = new Thread(r, name + " " + count.incrementAndGet());
         t.setDaemon(true);
         t.setPriority(Thread.MIN_PRIORITY + 1);
         return t;
      };
   }

   public static Thread quiet(Thread t) {
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY + 1);
      return t;
   }
}
