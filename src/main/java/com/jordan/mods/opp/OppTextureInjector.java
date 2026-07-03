package com.jordan.mods.opp;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Writes an unsigned "textures" property into a player's live GameProfile
 * pointing at our HTTP server. Vanilla clients read this the same way
 * they'd read a real Mojang-hosted skin URL - no mod required on their end.
 *
 * KNOWN LIMITATION: this only takes effect for clients that connect (or
 * reconnect) after the property is set. Minecraft has no built-in "skin
 * changed" packet, so players already connected when someone sets/changes
 * their skin won't see it update live - they'd need to rejoin. Revisit this
 * with verified PlayerListS2CPacket internals if instant updates matter.
 */
public final class OppTextureInjector {

    private OppTextureInjector() {}

    public static void apply(ServerPlayerEntity player, boolean slim) {
        String url = OppSkinHttpServer.buildSkinUrl(player.getUuid());
        if (url == null) {
            return; // HTTP server isn't up (e.g. no LAN IP found)
        }

        String json = "{"
                + "\"timestamp\":" + System.currentTimeMillis() + ","
                + "\"profileId\":\"" + player.getUuid().toString().replace("-", "") + "\","
                + "\"profileName\":\"" + player.getGameProfile().name() + "\","
                + "\"textures\":{\"SKIN\":{\"url\":\"" + url + "\""
                + (slim ? ",\"metadata\":{\"model\":\"slim\"}" : "")
                + "}}}";

        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        // NOTE: still the one unverified line flagged earlier - if
        // GameProfile.properties() doesn't compile, try getProperties().
        PropertyMap properties = player.getGameProfile().properties();

        // Profiles populated from a real session-service lookup come back
        // with an ImmutableListMultimap backing the PropertyMap, so any
        // mutating call (removeAll/put) throws UnsupportedOperationException
        // and aborts the join with "Invalid player data". Swap the backing
        // multimap for a mutable one first - same PropertyMap instance, so
        // nothing else holding a reference to the profile needs to change.
        makeMutable(properties);

        properties.removeAll("textures");
        properties.put("textures", new Property("textures", encoded));

        // Vanilla clients only read texture data out of a PlayerListS2CPacket,
        // and the automatic one from PlayerManager#onPlayerConnect already went
        // out (with the un-injected profile) before this method ever runs - so
        // every vanilla observer's tab-list entry is stuck without the texture
        // until they manually rejoin. Re-broadcasting a fresh ADD_PLAYER packet
        // here refreshes everyone's cached entry (and therefore the in-world
        // render) immediately, no client-side mod required.
        broadcastToAll(player);
    }

    private static void broadcastToAll(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) {
            return;
        }
        server.getPlayerManager().sendToAll(
                new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, player));
    }

    /**
     * Reflectively replaces PropertyMap's backing multimap with a mutable
     * LinkedHashMultimap if it's currently immutable. No-ops if it's already
     * mutable. Field-agnostic (scans for the Multimap-typed field) so it
     * survives authlib/mapping version differences.
     */
    @SuppressWarnings("unchecked")
    private static void makeMutable(PropertyMap map) {
        for (Field field : PropertyMap.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue; // skip PropertyMap.EMPTY - it's a PropertyMap, and
                // PropertyMap itself extends ForwardingMultimap, so
                // it matches the Multimap type check below too. It's
                // also static final, which reflection can't write to.
            }
            if (!Multimap.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object current = field.get(map);
                if (current instanceof Multimap) {
                    Multimap<String, Property> mutable =
                            LinkedHashMultimap.create((Multimap<String, Property>) current);
                    field.set(map, mutable);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to make PropertyMap mutable", e);
            }
            return;
        }
        throw new IllegalStateException("Could not locate backing multimap field in PropertyMap");
    }
}