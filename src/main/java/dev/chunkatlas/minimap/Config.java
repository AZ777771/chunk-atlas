package dev.chunkatlas.minimap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class Config {
   public enum Shape { SQUARE, CIRCLE }

   public enum Size {
      SMALL(80), MEDIUM(110), LARGE(140), HUGE(180), AUTO(0),

      CUSTOM(0);

      public final int px;

      Size(int px) {
         this.px = px;
      }
   }

   public enum Cave { OFF, AUTO, ALWAYS }

   public enum RadarStyle {
      @SerializedName(value = "FACES", alternate = "ICONS")
      FACES,
      DOTS,
      ARROWS
   }

   public enum Enlarge { HOLD, TOGGLE }

   public enum EnlargeView { CENTRED, IN_PLACE }

   public static final double[] ZOOMS = {0.25, 0.5, 1.0, 2.0, 3.0, 4.0, 6.0};

   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static Config current;

   public boolean enabled = true;
   public Shape shape = Shape.SQUARE;
   public Size size = Size.MEDIUM;

   public int customSize = 0;

   public int zoom = 2;
   public boolean northLock = true;

   public int compass = 1;
   public boolean frame = true;
   public float opacity = 1.0F;

   public boolean anchorRight = true;
   public boolean anchorBottom = false;
   public int offsetX = 6;
   public int offsetY = 6;

   public Enlarge enlargeMode = Enlarge.HOLD;
   public EnlargeView enlargeView = EnlargeView.CENTRED;
   public boolean enlargeZoomOut = false;

   public boolean showCoords = true;
   public boolean showBiome = false;
   public boolean showLight = false;
   public boolean showTime = false;
   public boolean showAngle = false;

   public Cave cave = Cave.AUTO;

   public int caveRoof = 3;

   public boolean radar = true;
   public RadarStyle radarStyle = RadarStyle.FACES;
   public boolean radarPlayers = true;
   public boolean radarHostile = true;
   public boolean radarFriendly = true;

   public boolean waypointsOnMap = true;
   public boolean waypointsInWorld = true;
   public boolean deathpoints = true;

   public boolean chunkGrid = false;
   public boolean slimeChunks = false;
   public boolean lightOverlay = false;

   public long slimeSeed = 0L;

   public boolean chunkAtlasColours = true;

   public boolean seedMap = false;

   public boolean smartZoom = false;

   public int version = 2;

   public static Config get() {
      if (current == null) {
         current = load();
      }

      return current;
   }

   public static Config reset() {
      current = new Config();
      current.save();
      return current;
   }

   public static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("chunkatlas-minimap.json");
   }

   private static Config load() {
      Path file = file();
      if (Files.isRegularFile(file)) {
         try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Config read = GSON.fromJson(text, Config.class);
            if (read != null) {
               if (!text.contains("\"version\"")) {
                  read.smartZoom = false;
               }

               return read.checked();
            }
         } catch (IOException | RuntimeException e) {
            Minimap.LOG.warn("Could not read {}, using defaults: {}", file.getFileName(), e.toString());
         }
      }

      return new Config();
   }

   private Config checked() {
      Config d = new Config();
      if (this.shape == null) this.shape = d.shape;
      if (this.size == null) this.size = d.size;
      if (this.cave == null) this.cave = d.cave;
      if (this.radarStyle == null) this.radarStyle = d.radarStyle;
      if (this.enlargeMode == null) this.enlargeMode = d.enlargeMode;
      if (this.enlargeView == null) this.enlargeView = d.enlargeView;
      this.customSize = Math.clamp(this.customSize, 0, 1024);
      if (this.size == Size.CUSTOM && this.customSize == 0) {
         this.size = d.size;
      }

      this.zoom = Math.clamp(this.zoom, 0, ZOOMS.length - 1);
      this.caveRoof = Math.clamp(this.caveRoof, 1, 15);
      this.opacity = Math.clamp(this.opacity, 0.2F, 1.0F);
      return this;
   }

   public void save() {
      try {
         Files.createDirectories(file().getParent());
         Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
      } catch (IOException e) {
         Minimap.LOG.warn("Could not save {}: {}", file().getFileName(), e.toString());
      }
   }

   public double scale() {
      return ZOOMS[this.zoom];
   }
}
