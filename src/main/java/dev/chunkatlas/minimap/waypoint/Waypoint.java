package dev.chunkatlas.minimap.waypoint;

import java.util.Locale;
import net.minecraft.world.item.DyeColor;

public final class Waypoint {
   public enum Type { LOCAL, GLOBAL }

   public String name = "";
   public String initials = "";
   public int x;
   public int y;
   public int z;
   public String dimension = "minecraft:overworld";
   public String colour = DyeColor.WHITE.getSerializedName();
   public String set = Waypoints.DEFAULT_SET;
   public Type type = Type.LOCAL;
   public boolean death;

   public boolean visible = true;

   public boolean inWorld = true;
   public long created;

   public Waypoint() {
   }

   public Waypoint(String name, int x, int y, int z, String dimension) {
      this.name = name;
      this.initials = initialsOf(name);
      this.x = x;
      this.y = y;
      this.z = z;
      this.dimension = dimension;
      this.created = System.currentTimeMillis();
   }

   public DyeColor dye() {
      DyeColor dye = DyeColor.byName(this.colour, null);
      return dye == null ? DyeColor.WHITE : dye;
   }

   public int rgb() {
      if (this.colour != null && this.colour.length() == 7 && this.colour.charAt(0) == '#') {
         try {
            return Integer.parseInt(this.colour.substring(1), 16);
         } catch (NumberFormatException ignored) {
         }
      }

      return this.dye().getTextureDiffuseColor() & 0xFFFFFF;
   }

   public String label() {
      if (this.initials != null && !this.initials.isBlank()) {
         return this.initials;
      }

      return initialsOf(this.name);
   }

   public static String initialsOf(String name) {
      String trimmed = name == null ? "" : name.trim();
      return trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
   }

   public double[] placeIn(String here) {
      if (here.equals(this.dimension)) {
         return new double[]{this.x + 0.5, this.y, this.z + 0.5};
      }

      if (this.type != Type.GLOBAL) {
         return null;
      }

      double factor = scale(this.dimension) / scale(here);
      return new double[]{this.x * factor + 0.5, this.y, this.z * factor + 0.5};
   }

   private static double scale(String dimension) {
      return "minecraft:the_nether".equals(dimension) ? 8.0 : 1.0;
   }

   Waypoint checked() {
      if (this.name == null) this.name = "";
      if (this.initials == null) this.initials = "";
      if (this.dimension == null) this.dimension = "minecraft:overworld";
      if (this.colour == null) this.colour = DyeColor.WHITE.getSerializedName();
      if (this.set == null || this.set.isBlank()) this.set = Waypoints.DEFAULT_SET;
      if (this.type == null) this.type = Type.LOCAL;
      return this;
   }
}
