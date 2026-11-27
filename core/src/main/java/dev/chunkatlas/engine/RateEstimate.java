package dev.chunkatlas.engine;

public final class RateEstimate {
   static final long SAMPLE_NANOS = 500_000_000L;

   static final double SETTLE_SECONDS = 3.0;

   static final double FIRST_SECONDS = 2.0;
   private long startedAt = Long.MIN_VALUE;
   private long lastAt;
   private long lastDone;
   private double rate = -1.0;

   public void sample(long now, long done) {
      if (this.startedAt == Long.MIN_VALUE) {
         this.startedAt = now;
         this.lastAt = now;
         this.lastDone = done;
         return;
      }

      long dt = now - this.lastAt;
      if (dt < SAMPLE_NANOS) {
         return;
      }

      double seconds = dt / 1.0E9;
      double r = (done - this.lastDone) / seconds;
      this.lastAt = now;
      this.lastDone = done;
      if (this.rate < 0.0) {
         if (r > 0.0) {
            this.rate = r;
         }
      } else {
         double k = 1.0 - Math.exp(-seconds / SETTLE_SECONDS);
         this.rate += (r - this.rate) * k;
      }
   }

   public void resume(long now, long done) {
      if (this.startedAt != Long.MIN_VALUE) {
         this.lastAt = now;
         this.lastDone = done;
      }
   }

   public double rate() {
      return this.rate;
   }

   public int secondsLeft(long now, long left) {
      if (this.rate <= 0.0 || this.startedAt == Long.MIN_VALUE || (now - this.startedAt) / 1.0E9 < FIRST_SECONDS) {
         return -1;
      }

      return (int)Math.min(Integer.MAX_VALUE, Math.ceil(Math.max(0L, left) / this.rate));
   }
}
