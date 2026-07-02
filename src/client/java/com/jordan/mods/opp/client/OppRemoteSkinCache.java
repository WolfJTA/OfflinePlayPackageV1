package com.jordan.mods.opp.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OppRemoteSkinCache {

    private static final Map<UUID, Identifier> TEXTURES = new ConcurrentHashMap<>();

    private OppRemoteSkinCache() {}

    /** Must be called on the render thread. */
    public static void apply(UUID uuid, byte[] pngBytes) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(pngBytes)) {
            NativeImage image = NativeImage.read(stream);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "opp_skin_" + uuid, image);
            Identifier id = Identifier.of("opp", "skin/" + uuid.toString().replace("-", ""));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            TEXTURES.put(uuid, id);
        } catch (IOException ignored) {
        }
    }

    public static Identifier getTexture(UUID uuid) {
        return TEXTURES.get(uuid);
    }

    public static boolean hasOverride(UUID uuid) {
        return TEXTURES.containsKey(uuid);
    }
}