package com.jordan.mods.opp.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.Optional;
import java.util.UUID;

/**
 * Server -> client. capePng is empty when that player has no cape set.
 */
public record SkinSyncPayload(UUID playerId, byte[] skinPng, boolean slim, Optional<byte[]> capePng) implements CustomPayload {
    public static final CustomPayload.Id<SkinSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("opp", "skin_sync"));

    public static final PacketCodec<RegistryByteBuf, SkinSyncPayload> CODEC =
            PacketCodec.tuple(
                    Uuids.PACKET_CODEC, SkinSyncPayload::playerId,
                    PacketCodecs.byteArray(1 << 20), SkinSyncPayload::skinPng,
                    PacketCodecs.BOOLEAN, SkinSyncPayload::slim,
                    PacketCodecs.optional(PacketCodecs.byteArray(1 << 20)), SkinSyncPayload::capePng,
                    SkinSyncPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}