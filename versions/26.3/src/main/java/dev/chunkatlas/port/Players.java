package dev.chunkatlas.port;

import java.util.Set;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class Players {
   private Players() {
   }

   public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
      player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot(), false);
   }

   public static void sound(ServerLevel level, ServerPlayer player, String id) {
      Identifier key = id == null || id.isBlank() ? null : Identifier.tryParse(id.strip());
      SoundEvent sound = key == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(key).orElse(null);
      if (sound != null) {
         level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8F, 1.0F);
      }
   }

   public static boolean particles(ServerLevel level, ServerPlayer to, String id, double x, double y, double z, int count, double spread) {
      Identifier key = id == null || id.isBlank() ? null : Identifier.tryParse(id.strip());
      Object type = key == null ? null : BuiltInRegistries.PARTICLE_TYPE.getOptional(key).orElse(null);
      if (!(type instanceof ParticleOptions options)) {
         return false;
      }

      level.sendParticles(to, options, true, false, x, y, z, count, spread, spread, spread, 0.0);
      return true;
   }
}
