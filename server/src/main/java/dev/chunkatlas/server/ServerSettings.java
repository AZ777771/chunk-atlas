package dev.chunkatlas.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

final class ServerSettings {
   private static final Properties PROPS = new Properties();
   private static boolean loaded;

   private ServerSettings() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("chunkatlas-server.properties");
   }

   private static synchronized Properties props() {
      if (!loaded) {
         loaded = true;
         Path f = file();
         if (Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
               PROPS.load(in);
            } catch (IOException ignored) {
            }
         }

         PROPS.putIfAbsent("live", "false");
         PROPS.putIfAbsent("max_chunks", "4096");
         PROPS.putIfAbsent("max_megabytes", "64");
         PROPS.putIfAbsent("generate_max_chunks", "100000");
         PROPS.putIfAbsent("live_chunks_per_tick", "32");
         PROPS.putIfAbsent("border_message", dev.chunkatlas.engine.Borders.Options.DEFAULT.message());
         PROPS.putIfAbsent("border_sound", dev.chunkatlas.engine.Borders.Options.DEFAULT.sound());
         PROPS.putIfAbsent("border_effect", dev.chunkatlas.engine.Borders.Options.DEFAULT.effect());
         PROPS.putIfAbsent("border_edge_particles", "true");
         PROPS.putIfAbsent("border_edge_particle", dev.chunkatlas.engine.Borders.Options.DEFAULT.edgeParticle());
         save();
      }

      return PROPS;
   }

   private static synchronized void save() {
      try {
         Files.createDirectories(file().getParent());
         try (OutputStream out = Files.newOutputStream(file())) {
            PROPS.store(out, "Chunk Atlas Server. live: make edits right away (true) or when the server stops (false).");
         }
      } catch (IOException ignored) {
      }
   }

   static boolean live() {
      return Boolean.parseBoolean(props().getProperty("live", "false"));
   }

   static synchronized void live(boolean on) {
      props().setProperty("live", Boolean.toString(on));
      save();
   }

   static int maxChunks() {
      return number("max_chunks", 4096, 1, 65536);
   }

   static int maxBytes() {
      return number("max_megabytes", 64, 1, 1024) << 20;
   }

   static int liveChunksPerTick() {
      return number("live_chunks_per_tick", 32, 1, 1024);
   }

   static int generateMaxChunks() {
      return number("generate_max_chunks", 100000, 1, dev.chunkatlas.engine.ChunkGeneration.MAX);
   }

   static dev.chunkatlas.engine.Borders.Options borderOptions() {
      Properties p = props();
      return new dev.chunkatlas.engine.Borders.Options(
         p.getProperty("border_message", ""), p.getProperty("border_sound", ""), p.getProperty("border_effect", ""),
         Boolean.parseBoolean(p.getProperty("border_edge_particles", "true")), p.getProperty("border_edge_particle", "")
      );
   }

   private static int number(String key, int otherwise, int lo, int hi) {
      try {
         return Math.max(lo, Math.min(hi, Integer.parseInt(props().getProperty(key, Integer.toString(otherwise)).trim())));
      } catch (NumberFormatException e) {
         return otherwise;
      }
   }
}
