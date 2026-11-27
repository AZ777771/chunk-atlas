package dev.chunkatlas.port;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class Net {
   private Net() {
   }

   public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
      return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("chunkatlas", path));
   }

   public static <T extends CustomPacketPayload> void c2s(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
      PayloadTypeRegistry.serverboundPlay().register(type, codec);
   }

   public static <T extends CustomPacketPayload> void s2c(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
      PayloadTypeRegistry.clientboundPlay().register(type, codec);
   }
}
