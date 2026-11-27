package dev.chunkatlas.engine;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public final class SeedMapSettings {
   private static final Set<String> HIDDEN_AT_FIRST = Set.of(
      "minecraft:mineshaft", "minecraft:nether_fossil", "minecraft:fossil", "minecraft:amethyst_geode", "minecraft:lava_pool"
   );

   private static final String LAST = "last.properties";
   private final Path file;
   private final Path last;
   private boolean accepted;
   private boolean shown;
   private boolean biomes = true;
   private boolean structures = false;
   private boolean slime;

   private Long seed;
   private final Set<String> hidden = new TreeSet<>(HIDDEN_AT_FIRST);

   private boolean featuresOpen;

   private SeedMapSettings(Path file, Path last) {
      this.file = file;
      this.last = last;
   }

   public static Path dir(Path home, Path world) {
      Path real;
      try {
         real = world.toRealPath();
      } catch (IOException e) {
         real = world.toAbsolutePath().normalize();
      }

      return home.resolve("seedmap").resolve(TileCache.worldKey(real));
   }

   public static SeedMapSettings load(Path home, Path world) {
      Path own = home == null ? null : dir(home, world).resolve("settings.properties");
      SeedMapSettings s = new SeedMapSettings(own, home == null ? null : home.resolve("seedmap").resolve(LAST));
      if (own == null) {
         return s;
      }

      boolean mine = Files.isRegularFile(own);
      Properties p = read(mine ? own : s.last);
      if (p == null) {
         return s;
      }

      s.shown = Boolean.parseBoolean(p.getProperty("shown", "false"));
      s.accepted = mine ? Boolean.parseBoolean(p.getProperty("accepted", "false")) : s.shown;
      s.shown &= s.accepted;
      s.biomes = Boolean.parseBoolean(p.getProperty("biomes", "true"));
      s.structures = Boolean.parseBoolean(p.getProperty("structures", "false"));
      s.slime = Boolean.parseBoolean(p.getProperty("slime", "false"));
      s.featuresOpen = Boolean.parseBoolean(p.getProperty("features_open", "false"));
      if (mine) {
         try {
            String seed = p.getProperty("seed");
            s.seed = seed == null || seed.isBlank() ? null : Long.parseLong(seed.strip());
         } catch (NumberFormatException e) {
            s.seed = null;
         }
      }

      String hidden = p.getProperty("hidden");
      if (hidden != null) {
         s.hidden.clear();
         Arrays.stream(hidden.split(",")).map(String::strip).filter(k -> !k.isEmpty()).forEach(s.hidden::add);
      }

      return s;
   }

   private static Properties read(Path file) {
      if (file == null || !Files.isRegularFile(file)) {
         return null;
      }

      Properties p = new Properties();
      try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
         p.load(in);
         return p;
      } catch (IOException | RuntimeException e) {
         return null;
      }
   }

   public void save() {
      if (this.file == null) {
         return;
      }

      Properties p = new Properties();
      p.setProperty("shown", Boolean.toString(this.shown));
      p.setProperty("biomes", Boolean.toString(this.biomes));
      p.setProperty("structures", Boolean.toString(this.structures));
      p.setProperty("slime", Boolean.toString(this.slime));
      p.setProperty("hidden", String.join(",", this.hidden));
      p.setProperty("features_open", Boolean.toString(this.featuresOpen));
      store(this.last, p);
      p.setProperty("accepted", Boolean.toString(this.accepted));
      if (this.seed != null) {
         p.setProperty("seed", Long.toString(this.seed));
      }

      store(this.file, p);
   }

   private static void store(Path file, Properties p) {
      if (file == null) {
         return;
      }

      try {
         Files.createDirectories(file.getParent());
         Path temp = file.resolveSibling(".saving-" + System.nanoTime() + ".tmp");
         try {
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
               p.store(out, "Chunk Atlas seed map");
            }

            try {
               Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
               Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
         } finally {
            Files.deleteIfExists(temp);
         }
      } catch (IOException | RuntimeException ignored) {
      }
   }

   public boolean accepted() {
      return this.accepted;
   }

   public void accept() {
      this.accepted = true;
   }

   public boolean shown() {
      return this.accepted && this.shown;
   }

   public void show(boolean on) {
      this.shown = on && this.accepted;
   }

   public Long seed() {
      return this.seed;
   }

   public void seed(Long seed) {
      this.seed = seed;
   }

   public static Long parseSeed(String text) {
      String s = text == null ? "" : text.strip();
      if (s.isEmpty()) {
         return null;
      }

      try {
         return Long.parseLong(s);
      } catch (NumberFormatException e) {
         return (long)s.hashCode();
      }
   }

   public boolean biomes() {
      return this.biomes;
   }

   public void biomes(boolean on) {
      this.biomes = on;
   }

   public boolean structures() {
      return this.structures;
   }

   public void structures(boolean on) {
      this.structures = on;
   }

   public boolean slime() {
      return this.slime;
   }

   public void slime(boolean on) {
      this.slime = on;
   }

   public boolean featuresOpen() {
      return this.featuresOpen;
   }

   public void featuresOpen(boolean open) {
      this.featuresOpen = open;
   }

   public boolean kindShown(String kind) {
      return !this.hidden.contains(kind);
   }

   public void kindShown(String kind, boolean on) {
      if (on) {
         this.hidden.remove(kind);
      } else {
         this.hidden.add(kind);
      }
   }
}
