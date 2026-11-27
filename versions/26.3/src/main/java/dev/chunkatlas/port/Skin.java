package dev.chunkatlas.port;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;

public final class Skin {
   private static final Map<PlayerSkin, Skin> KNOWN = new IdentityHashMap<>();
   final PlayerSkin skin;

   private Skin(PlayerSkin skin) {
      this.skin = skin;
   }

   private static Skin wrap(PlayerSkin skin) {
      Skin have = KNOWN.get(skin);
      if (have == null) {
         if (KNOWN.size() > 256) {
            KNOWN.clear();
         }

         have = new Skin(skin);
         KNOWN.put(skin, have);
      }

      return have;
   }

   public static Skin of(Minecraft mc, String id) {
      try {
         UUID uuid = UUID.fromString(id);
         ClientPacketListener net = mc.getConnection();
         PlayerInfo info = net == null ? null : net.getPlayerInfo(uuid);
         return wrap(info != null ? info.getSkin() : DefaultPlayerSkin.get(uuid));
      } catch (IllegalArgumentException e) {
         return wrap(DefaultPlayerSkin.getDefaultSkin());
      }
   }

   public static Skin of(Entity e) {
      return e instanceof AbstractClientPlayer p ? wrap(p.getSkin()) : null;
   }

   public static Skin you(Minecraft mc) {
      return mc.player != null ? wrap(mc.player.getSkin()) : wrap(DefaultPlayerSkin.get(mc.getUser().getProfileId()));
   }
}
