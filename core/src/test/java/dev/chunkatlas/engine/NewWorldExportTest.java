package dev.chunkatlas.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NewWorldExportTest {
   @TempDir
   Path dir;

   private static void gz(Path file, Map<String, Object> root) throws Exception {
      Files.createDirectories(file.getParent());
      try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
         out.write(Nbt.write(root));
      }
   }

   private static Map<String, Object> read(Path file) throws Exception {
      try (InputStream in = Files.newInputStream(file)) {
         return Nbt.read(Packed.gunzip(in, 1 << 24));
      }
   }

   private static Map<String, Object> generation(long seed) {
      Map<String, Object> flat = new LinkedHashMap<>();
      flat.put("type", "minecraft:flat");
      flat.put("settings", new LinkedHashMap<>(Map.of("biome", "minecraft:desert")));
      Map<String, Object> noise = new LinkedHashMap<>();
      noise.put("type", "minecraft:noise");
      noise.put("settings", "minecraft:nether");
      Map<String, Object> dims = new LinkedHashMap<>();
      dims.put("minecraft:overworld", new LinkedHashMap<>(Map.of("type", "minecraft:overworld", "generator", flat)));
      dims.put("minecraft:the_nether", new LinkedHashMap<>(Map.of("type", "minecraft:the_nether", "generator", noise)));
      Map<String, Object> gen = new LinkedHashMap<>();
      gen.put("seed", seed);
      gen.put("generate_structures", (byte)1);
      gen.put("bonus_chest", (byte)0);
      gen.put("dimensions", dims);
      return gen;
   }

   private static Map<String, Object> level(String name, Map<String, Object> worldGen) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("LevelName", name);
      data.put("DataVersion", 5023);
      if (worldGen != null) {
         data.put("WorldGenSettings", worldGen);
      }

      Map<String, Object> root = new LinkedHashMap<>();
      root.put("Data", data);
      return root;
   }

   @Test
   void newLayoutKeepsSeedGeneratorRulesAndPacks() throws Exception {
      Path saves = this.dir.resolve("saves");
      Path source = saves.resolve("source");
      gz(source.resolve("level.dat"), level("Source", null));
      Map<String, Object> file = new LinkedHashMap<>();
      file.put("data", generation(-116588860198084458L));
      file.put("DataVersion", 5023);
      gz(source.resolve("data/minecraft/world_gen_settings.dat"), file);
      gz(source.resolve("data/minecraft/game_rules.dat"), new LinkedHashMap<>(Map.of("data", new LinkedHashMap<>(Map.of("minecraft:keep_inventory", (byte)1)))));
      Files.createDirectories(source.resolve("datapacks/pack/data"));
      Files.writeString(source.resolve("datapacks/pack/pack.mcmeta"), "{}");
      Files.write(source.resolve("datapacks/terrain.zip"), new byte[]{1, 2, 3});

      Path made = ChunkClips.newWorld(saves, source, "Exported");

      LevelMarkers.Generation before = LevelMarkers.generation(source);
      LevelMarkers.Generation after = LevelMarkers.generation(made);
      assertEquals(Optional.of(-116588860198084458L), after.seed());
      assertEquals(before.generators(), after.generators());
      assertEquals("minecraft:flat", after.generator("minecraft:overworld"));
      for (String f : ChunkClips.WORLD_FILES) {
         assertArrayEquals(Files.readAllBytes(source.resolve(f)), Files.readAllBytes(made.resolve(f)), f);
      }

      assertTrue(Files.isRegularFile(made.resolve("datapacks/pack/pack.mcmeta")));
      assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(made.resolve("datapacks/terrain.zip")));
      assertEquals("Exported", LevelMarkers.name(made));
   }

   @Test
   void oldLayoutKeepsTheSeedInLevelDat() throws Exception {
      Path saves = this.dir.resolve("saves");
      Path source = saves.resolve("Old");
      gz(source.resolve("level.dat"), level("Old", generation(42L)));

      Path made = ChunkClips.newWorld(saves, source, "Old");

      LevelMarkers.Generation after = LevelMarkers.generation(made);
      assertEquals(Optional.of(42L), after.seed());
      assertEquals("minecraft:flat", after.generator("minecraft:overworld"));
      assertEquals("minecraft:noise", after.generator("minecraft:the_nether"));

      assertEquals("Old (2)", made.getFileName().toString());
      assertEquals("Old", Nbt.string(Nbt.map(read(made.resolve("level.dat")), "Data"), "LevelName"));
      assertTrue(!Files.exists(made.resolve("data/minecraft/world_gen_settings.dat")));
   }
}
