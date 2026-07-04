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
 * Caches decoded skin (and optional cape) textures per player UUID so we
 * can show the right skin immediately, without waiting on the server's
 * HTTP host to be reachable/round-tripped. Used for two cases: applying
 * our own skin the instant we join (oppClient's JOIN handler), and
 * applying a skin pushed to us via SkinSyncPayload for other players.
 *
 * Note: this only affects rendering for opp-aware clients that check this
 * cache (see AbstractClientPlayerEntityMixin). Vanilla clients still get
 * skins the normal way, via the GameProfile texture URL that
 * OppTextureInjector writes server-side (which now also carries a CAPE
 * entry when a cape is set - see OppTextureInjector).
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
 * checkerboard. See apply() below. The cape asset is registered/expanded
 * the exact same way, just under a "..._cape" asset id and passed as
 * SkinTextures.create's second (cape) argument instead of the first (body).
 */
public final class OppRemoteSkinCache {

    private static final Map<UUID, SkinTextures> SKINS = new ConcurrentHashMap<>();

    private OppRemoteSkinCache() {
    }

    /** Back-compat overload for callers that don't have a cape to apply. */
    public static void apply(UUID playerId, byte[] skinPng, boolean slim) {
        apply(playerId, skinPng, slim, null);
    }

    /**
     * Decodes the given skin PNG (and cape PNG, if non-null), registers them
     * as textures with the client's TextureManager, and caches the
     * resulting SkinTextures for this player. Must be called on the client
     * (render) thread - callers are expected to hop over with
     * client.execute(...) first, same as oppClient already does.
     */
    public static void apply(UUID playerId, byte[] skinPng, boolean slim, byte[] capePng) {
        NativeImage bodyImage;
        try {
            bodyImage = NativeImage.read(skinPng);
        } catch (IOException e) {
            return; // malformed PNG - nothing we can do, keep whatever was cached before
        }

        MinecraftClient client = MinecraftClient.getInstance();

        Identifier bodyAssetId = Identifier.of("opp", "remote_skin/" + playerId);
        Identifier bodyTextureId = bodyAssetId.withPath(path -> "textures/" + path + ".png");
        // NativeImageBackedTexture takes ownership of the NativeImage (it
        // closes it itself), so we don't close it ourselves here.
        client.getTextureManager().registerTexture(bodyTextureId, new NativeImageBackedTexture(() -> "opp remote skin " + playerId, bodyImage));
        AssetInfo.TextureAsset body = new AssetInfo.TextureAssetInfo(bodyAssetId);

        AssetInfo.TextureAsset cape = null;
        if (capePng != null) {
            NativeImage capeImage;
            try {
                capeImage = NativeImage.read(capePng);
            } catch (IOException e) {
                capeImage = null;
            }
            if (capeImage != null) {
                Identifier capeAssetId = Identifier.of("opp", "remote_cape/" + playerId);
                Identifier capeTextureId = capeAssetId.withPath(path -> "textures/" + path + ".png");
                client.getTextureManager().registerTexture(capeTextureId, new NativeImageBackedTexture(() -> "opp remote cape " + playerId, capeImage));
                cape = new AssetInfo.TextureAssetInfo(capeAssetId);
            }
        }

        SkinTextures textures = SkinTextures.create(body, cape, null, slim ? PlayerSkinType.SLIM : PlayerSkinType.WIDE);
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