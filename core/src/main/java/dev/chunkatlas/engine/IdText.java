package dev.chunkatlas.engine;

public final class IdText {
   private IdText() {
   }

   public static String namespace(String id) {
      if (!valid(id)) {
         return null;
      }

      int colon = id.indexOf(':');
      return colon < 1 ? "minecraft" : id.substring(0, colon);
   }

   public static String path(String id) {
      return valid(id) ? id.substring(id.indexOf(':') + 1) : null;
   }

   public static boolean valid(String id) {
      if (id == null) {
         return false;
      }

      int colon = id.indexOf(':');
      for (int i = 0; i < id.length(); i++) {
         char c = id.charAt(i);
         boolean ok = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-' || c == '/' && i > colon;
         if (!ok && i != colon) {
            return false;
         }
      }

      return true;
   }
}
