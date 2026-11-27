package dev.chunkatlas;

import dev.chunkatlas.port.Ids;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

public final class Footsteps {
   private static final int STEP = 4;
   private static final int KEEP = 20_000;
   private static final long SAVE_MS = 30_000L;

   private static String key;
   private static String dimension;
   private static int[] points = new int[384];
   private static int count;
   private static long savedAt;
   private static boolean dirty;

   private static int changes;

   private Footsteps() {
   }

   static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(Footsteps::tick);
      net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> save());
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> save());
   }

   public static String key(String worldFolderName, boolean server) {
      return (server ? "mp-" : "sp-") + worldFolderName.replaceAll("[^A-Za-z0-9._ -]", "_");
   }

   private static void tick(Minecraft mc) {
      String k = null;
      if (mc.level != null && mc.player != null) {
         IntegratedServer server = mc.getSingleplayerServer();
         if (server != null) {
            k = key(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString(), false);
         } else if (LiveMap.folder() != null) {
            k = key(LiveMap.folder().getFileName().toString(), true);
         }
      }

      String dim = mc.level == null ? null : Ids.of(mc.level.dimension());
      if (!java.util.Objects.equals(k, key) || !java.util.Objects.equals(dim, dimension)) {
         save();
         key = k;
         dimension = dim;
         count = 0;
         changes++;
         if (k != null) {
            load(file(k, dim));
         }
      }

      if (k == null) {
         return;
      }

      int x = mc.player.getBlockX();
      int z = mc.player.getBlockZ();
      if (count == 0 || Math.abs(x - points[3 * count - 3]) >= STEP || Math.abs(z - points[3 * count - 2]) >= STEP) {
         add(x, z);
      }

      if (dirty && System.currentTimeMillis() - savedAt > SAVE_MS) {
         save();
      }
   }

   private static void add(int x, int z) {
      if (count == KEEP) {
         System.arraycopy(points, KEEP / 4 * 3, points, 0, (KEEP - KEEP / 4) * 3);
         count -= KEEP / 4;
      }

      if (3 * count + 3 > points.length) {
         points = Arrays.copyOf(points, Math.min(KEEP * 3, points.length * 2));
      }

      points[3 * count] = x;
      points[3 * count + 1] = z;
      points[3 * count + 2] = (int)(System.currentTimeMillis() / 1000L);
      count++;
      dirty = true;
      changes++;
   }

   public static int changes() {
      return changes;
   }

   public static boolean walking(String trailKey, String dim) {
      return trailKey.equals(key) && dim != null && dim.equals(dimension);
   }

   public static int[] live() {
      return key == null ? new int[0] : Arrays.copyOf(points, 3 * count);
   }

   public static int[] trail(String trailKey, String dim) {
      if (trailKey.equals(key) && dim.equals(dimension)) {
         return Arrays.copyOf(points, 3 * count);
      }

      Path f = file(trailKey, dim);
      return Files.isRegularFile(f) ? readFile(f) : new int[0];
   }

   private static void load(Path f) {
      int[] read = Files.isRegularFile(f) ? readFile(f) : new int[0];
      points = Arrays.copyOf(read, Math.max(384, read.length));
      count = read.length / 3;
      dirty = false;
      savedAt = System.currentTimeMillis();
   }

   private static int[] readFile(Path f) {
      try (DataInputStream in = new DataInputStream(Files.newInputStream(f))) {
         int first = in.readInt();
         boolean timed = first == VERSION;
         int n = Math.min(KEEP, Math.max(0, timed ? in.readInt() : first));
         int[] out = new int[3 * n];
         for (int i = 0; i < n; i++) {
            out[3 * i] = in.readInt();
            out[3 * i + 1] = in.readInt();
            out[3 * i + 2] = timed ? in.readInt() : 0;
         }

         return out;
      } catch (IOException | RuntimeException e) {
         return new int[0];
      }
   }

   private static final int VERSION = -2;

   private static void save() {
      savedAt = System.currentTimeMillis();
      if (!dirty || key == null || dimension == null) {
         return;
      }

      dirty = false;
      Path f = file(key, dimension);
      try {
         Files.createDirectories(f.getParent());
         Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
         try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmp))) {
            out.writeInt(VERSION);
            out.writeInt(count);
            for (int i = 0; i < 3 * count; i++) {
               out.writeInt(points[i]);
            }
         }

         Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException ignored) {
      }
   }

   private static Path file(String trailKey, String dim) {
      return ChunkAtlas.home().resolve("footsteps").resolve(trailKey).resolve(dim.replace(':', '@').replace('/', '+') + ".bin");
   }
}
