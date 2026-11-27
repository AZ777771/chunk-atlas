package dev.chunkatlas.engine;

import dev.chunkatlas.port.Ids;
import dev.chunkatlas.port.Players;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

public final class Borders {
   private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Chunk Atlas");

   private static final double JUMP = 12.0;

   private static final double MARGIN = 1.5;

   private static final double SHOW_WITHIN = 16.0;
   private static final int MESSAGE_EVERY = 40;
   private static volatile Borders current;

   public record Options(String message, String sound, String effect, boolean edge, String edgeParticle) {
      public static final Options DEFAULT = new Options("You can't go past the border", "minecraft:entity.enderman.teleport", "minecraft:portal", true, "minecraft:end_rod");
   }

   private record Spot(String dimension, double x, double y, double z) {
   }

   private static volatile Options options = Options.DEFAULT;
   private final MinecraftServer server;
   private final Path file;
   private BorderBook book;
   private volatile Map<String, BorderBook.Border> shown = Map.of();
   private final Map<UUID, Spot> lastInside = new HashMap<>();
   private final Map<UUID, Integer> toldAt = new HashMap<>();
   private int ticks;
   private volatile int version;

   private Borders(MinecraftServer server, Path file, BorderBook book) {
      this.server = server;
      this.file = file;
      this.book = book;
      this.shown = Map.copyOf(book.borders);
   }

   public static void options(Options set) {
      options = set == null ? Options.DEFAULT : set;
   }

   public static Options options() {
      return options;
   }

   public static Borders started(MinecraftServer server) {
      Path file = server.getWorldPath(LevelResource.ROOT).resolve("chunkatlas").resolve("borders.txt");
      BorderBook book;
      try {
         book = BorderBook.read(Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null);
      } catch (IOException e) {
         LOG.warn("Chunk Atlas: couldn't read the borders: {}", e.toString());
         book = new BorderBook();
      }

      Borders b = new Borders(server, file, book);
      current = b;
      return b;
   }

   public static Borders of(MinecraftServer server) {
      Borders now = current;
      return now != null && now.server == server ? now : null;
   }

   public static void stopping(MinecraftServer server) {
      if (of(server) != null) {
         current = null;
      }
   }

   public Map<String, BorderBook.Border> all() {
      return this.shown;
   }

   public int version() {
      return this.version;
   }

   public void set(String dimension, BorderBook.Border border) {
      if (border == null) {
         this.book.borders.remove(dimension);
      } else {
         this.book.borders.put(dimension, border);
      }

      this.shown = Map.copyOf(this.book.borders);
      this.version++;
      LOG.info("Chunk Atlas: border in {}: {}", dimension, border == null ? "none" : border.area().write() + (border.wrap() ? " wrap" : ""));
      try {
         Files.createDirectories(this.file.getParent());
         Path tmp = this.file.resolveSibling("borders.txt.tmp");
         Files.writeString(tmp, this.book.write(), StandardCharsets.UTF_8);
         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
         LOG.warn("Chunk Atlas: couldn't save the borders: {}", e.toString());
      }
   }

   public void tick() {
      this.ticks++;
      if (this.book.borders.isEmpty()) {
         this.lastInside.clear();
         return;
      }

      Options o = options;
      for (ServerPlayer p : this.server.getPlayerList().getPlayers()) {
         try {
            this.keep(p, o);
         } catch (RuntimeException e) {
            LOG.warn("Chunk Atlas: border check failed: {}", e.toString());
         }
      }

      if (this.ticks % 200 == 0) {
         this.lastInside.keySet().removeIf(id -> this.server.getPlayerList().getPlayer(id) == null);
         this.toldAt.keySet().removeIf(id -> this.server.getPlayerList().getPlayer(id) == null);
      }
   }

   private void keep(ServerPlayer p, Options o) {
      ServerLevel level = (ServerLevel)p.level();
      String dim = Ids.of(level.dimension());
      BorderBook.Border b = this.book.get(dim);
      UUID id = p.getUUID();
      if (b == null || p.isSpectator()) {
         this.lastInside.remove(id);
         return;
      }

      double x = p.getX();
      double z = p.getZ();
      Area area = b.area();
      if (area.contains(x, z)) {
         this.lastInside.put(id, new Spot(dim, x, p.getY(), z));
         if (o.edge() && this.ticks % 10 == 0 && area.edgeDistance(x, z) < SHOW_WITHIN) {
            this.edge(level, p, area, o);
         }

         return;
      }

      Spot was = this.lastInside.get(id);
      boolean jumped = was == null || !was.dimension().equals(dim) || Math.hypot(was.x() - x, was.z() - z) > JUMP;
      if (p.isPassenger()) {
         p.stopRiding();
      }

      if (b.wrap() && !jumped) {
         double[] to = area.wrap(x, z, MARGIN + 1.0);
         Players.teleport(p, level, to[0], ground(level, to[0], to[1], p.getY()), to[1]);
         p.resetFallDistance();
         Players.sound(level, p, o.sound());
      } else if (jumped) {
         double[] to = area.nearestInside(x, z, MARGIN);
         Players.teleport(p, level, to[0], ground(level, to[0], to[1], p.getY()), to[1]);
         p.resetFallDistance();
         this.tell(p, o);
      } else {
         p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
         Players.teleport(p, level, was.x(), was.y(), was.z());
         p.resetFallDistance();
         if (this.tell(p, o)) {
            Players.sound(level, p, o.sound());
            Players.particles(level, p, o.effect(), was.x(), was.y() + 1.0, was.z(), 24, 0.4);
         }
      }

      Spot now = new Spot(dim, p.getX(), p.getY(), p.getZ());
      if (area.contains(now.x(), now.z())) {
         this.lastInside.put(id, now);
      }
   }

   private boolean tell(ServerPlayer p, Options o) {
      Integer at = this.toldAt.get(p.getUUID());
      if (at != null && this.ticks - at < MESSAGE_EVERY) {
         return false;
      }

      this.toldAt.put(p.getUUID(), this.ticks);
      if (o.message() != null && !o.message().isBlank()) {
         p.sendSystemMessage(Component.literal(o.message()), true);
      }

      return true;
   }

   private void edge(ServerLevel level, ServerPlayer p, Area area, Options o) {
      double[] e = area.nearestEdge(p.getX(), p.getZ());
      double tx = -(e[1] - area.cz());
      double tz = e[0] - area.cx();
      double len = Math.hypot(tx, tz);
      if (len < 1.0E-6) {
         return;
      }

      tx /= len;
      tz /= len;
      for (int k = -12; k <= 12; k += 2) {
         double[] q = area.nearestEdge(e[0] + tx * k, e[1] + tz * k);
         for (int h = 0; h < 3; h++) {
            if (!Players.particles(level, p, o.edgeParticle(), q[0], p.getY() + 0.3 + h, q[1], 1, 0.0)) {
               return;
            }
         }
      }
   }

   static double ground(ServerLevel level, double x, double z, double nearY) {
      int bx = (int)Math.floor(x);
      int bz = (int)Math.floor(z);
      if (!level.dimensionType().hasCeiling()) {
         return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
      }

      int from = (int)Math.floor(nearY);
      int lo = dev.chunkatlas.port.Heights.min(level);
      int hi = dev.chunkatlas.port.Heights.maxInclusive(level);
      for (int d = 0; d < 64; d++) {
         for (int y : new int[]{from - d, from + d}) {
            if (y > lo && y + 1 < hi && free(level, bx, y, bz) && free(level, bx, y + 1, bz) && !free(level, bx, y - 1, bz)) {
               return y;
            }
         }
      }

      return nearY;
   }

   private static boolean free(ServerLevel level, int x, int y, int z) {
      return level.getBlockState(new BlockPos(x, y, z)).getCollisionShape(level, new BlockPos(x, y, z)).isEmpty();
   }
}
