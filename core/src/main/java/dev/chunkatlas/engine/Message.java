package dev.chunkatlas.engine;

import java.util.Arrays;

public record Message(String key, Object... args) {
   public static Message of(String key, Object... args) {
      return new Message(key, args);
   }

   public static Message literal(String text) {
      return new Message(null, text);
   }

   @Override
   public String toString() {
      if (this.key == null) {
         return this.args.length == 0 ? "" : String.valueOf(this.args[0]);
      }

      return this.args.length == 0 ? this.key : this.key + " " + Arrays.toString(this.args);
   }
}
