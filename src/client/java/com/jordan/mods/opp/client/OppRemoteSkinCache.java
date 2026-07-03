package com.jordan.mods.opp.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches decoded skin textures per player UUID so we can show the right
 * skin immediately, without waiting on the server's HTTP host to be
 * reachable/round-tripped. Used for two cases: applying our own skin the
 * instant we join (oppClient's JOIN handler), and applying a skin pushed to
 * us via SkinSyncPayload for other players.
 *
 * Note: this only affects rendering for opp-aware clients that check this
 * cache (see AbstractClientPlayerEntityMixin). Vanilla clients still get
 * skins the normal way, via the GameProfile texture URL that
 * OppTextureInjector writes server-side.
 *
 * NOTE ON 1.21.11: as of 1.21.11, SkinTextures lives in
 * net.minecraft.entity.player (not net.minecraft.client.util like earlier
 * 1.21.x versions) and its "model" field is the standalone PlayerSkinType
 * enum (SLIM/WIDE) rather than a nested SkinTextures.Model. Its texture
 * fields are also AssetInfo.TextureAsset now instead of raw Identifiers.
 * Confirmed against the actual 1.21.11 classes: AssetInfo.TextureAssetInfo
 * wraps a short "asset id" and expands it to the real GL texture identifier
 * via id.withPath(path -> "textures/" + path + ".png") - so the id handed to
 * TextureManager.registerTexture() must be that *expanded* form, not the
 * short asset id, or lookups miss and render as the missing-texture
 * checkerboard. See apply() below.
 */
public final class OppRemoteSkinCache {

    private static final Map<UUID, SkinTextures> SKINS = new ConcurrentHashMap<>();

    private OppRemoteSkinCache() {
    }

    /**
     * Decodes the given skin PNG, registers it as a texture with the
     * client's TextureManager, and caches the resulting SkinTextures for
     * this player. Must be called on the client (render) thread - callers
     * are expected to hop over with client.execute(...) first, same as
     * oppClient already does.
     */
    public static void apply(UUID playerId, byte[] skinPng, boolean slim) {
        NativeImage image;
        try {
            image = NativeImage.read(skinPng);
        } catch (IOException e) {
            return; // malformed PNG - nothing we can do, keep whatever was cached before
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Identifier assetId = Identifier.of("opp", "remote_skin/" + playerId);

        // AssetInfo.TextureAssetInfo doesn't bind whatever Identifier you hand
        // it directly - its accessor expands the id via
        // id.withPath(path -> "textures/" + path + ".png") before the renderer
        // uses it to look up the GL texture (the same convention vanilla uses
        // to turn e.g. "block/stone" into "textures/block/stone.png"). If we
        // register the texture under the raw "opp:remote_skin/<uuid>" id but
        // wrap that same raw id in TextureAssetInfo, the renderer ends up
        // asking TextureManager for "opp:textures/remote_skin/<uuid>.png" -
        // which was never registered - and falls back to the missing-texture
        // checkerboard. So we register under the *expanded* id instead, and
        // keep passing the short, unexpanded id to TextureAssetInfo.
        Identifier textureId = assetId.withPath(path -> "textures/" + path + ".png");

        // NativeImageBackedTexture takes ownership of the NativeImage (it
        // closes it itself), so we don't close it ourselves here.
        client.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(() -> "opp remote skin " + playerId, image));

        AssetInfo.TextureAsset body = new AssetInfo.TextureAssetInfo(assetId);
        SkinTextures textures = SkinTextures.create(body, null, null, slim ? PlayerSkinType.SLIM : PlayerSkinType.WIDE);

        SKINS.put(playerId, textures);
    }

    /**
     * Returns the cached skin textures for this player, or null if we
     * haven't cached one (in which case callers should fall back to
     * whatever vanilla would normally show).
     */
    public static SkinTextures get(UUID playerId) {
        return SKINS.get(playerId);
    }

    public static void remove(UUID playerId) {
        SKINS.remove(playerId);
    }

    public static void clear() {
        SKINS.clear();
    }
}