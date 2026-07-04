package com.jordan.mods.opp.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Client -> server. capePng is empty when the player has no cape set (or
 * is clearing one); present with the raw 64x32 PNG bytes otherwise.
 */
public record SkinUploadPayload(byte[] skinPng, boolean slim, Optional<byte[]> capePng) implements CustomPayload {
    public static final CustomPayload.Id<SkinUploadPayload> ID =
            new CustomPayload.Id<>(Identifier.of("opp", "skin_upload"));

    public static final PacketCodec<RegistryByteBuf, SkinUploadPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.byteArray(1 << 20), SkinUploadPayload::skinPng,
                    PacketCodecs.BOOLEAN, SkinUploadPayload::slim,
                    PacketCodecs.optional(PacketCodecs.byteArray(1 << 20)), SkinUploadPayload::capePng,
                    SkinUploadPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}