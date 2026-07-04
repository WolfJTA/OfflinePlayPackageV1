package com.jordan.mods.opp;

import com.jordan.mods.opp.network.SkinSyncPayload;
import com.jordan.mods.opp.network.SkinUploadPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;

public class opp implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(SkinUploadPayload.ID, SkinUploadPayload.CODEC);
        // SkinSyncPayload flows server -> client, so it belongs on the S2C
        // registry rather than C2S. This is registered here (in the common
        // ModInitializer, which runs on both the client and the server)
        // rather than in oppClient, since both sides need the codec
        // registered - the server to send it, the client to receive it.
        PayloadTypeRegistry.playS2C().register(SkinSyncPayload.ID, SkinSyncPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerSkinManager.init(server);
            OppSkinHttpServer.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ServerSkinManager.clear();
            OppSkinHttpServer.stop();
        });

        // Client uploads their skin (+ optional cape) -> validate it, store
        // it, inject it into their GameProfile as texture URLs. Works for
        // vanilla clients too, since it's the game's own skin pipeline doing
        // the rendering, not a custom packet. Note: the GameProfile
        // injection only takes effect for clients that connect (or
        // reconnect) after this runs - see OppTextureInjector's class
        // comment. To cover already-connected opp-aware clients too, we
        // also broadcast a SkinSyncPayload to every online player so
        // AbstractClientPlayerEntityMixin can pick it up immediately
        // without needing a rejoin.
        ServerPlayNetworking.registerGlobalReceiver(SkinUploadPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayerEntity player = context.player();
                    boolean accepted = ServerSkinManager.storeSkin(player.getUuid(), payload.skinPng(), payload.slim(), payload.capePng());
                    if (!accepted) {
                        return; // malformed/invalid PNG - drop it silently
                    }
                    byte[] storedCape = ServerSkinManager.getCape(player.getUuid());
                    OppTextureInjector.apply(player, payload.slim(), storedCape != null);

                    SkinSyncPayload sync = new SkinSyncPayload(player.getUuid(), payload.skinPng(), payload.slim(),
                            storedCape != null ? Optional.of(storedCape) : Optional.empty());
                    for (ServerPlayerEntity other : context.server().getPlayerManager().getPlayerList()) {
                        if (ServerPlayNetworking.canSend(other, SkinSyncPayload.ID)) {
                            ServerPlayNetworking.send(other, sync);
                        }
                    }
                })
        );

        // New join -> if they already have a stored skin from a previous
        // session, inject it into their GameProfile right away so everyone
        // sees it correctly. We also need to actively push every already-known
        // skin (and cape) to the joining player via SkinSyncPayload: those
        // other players uploaded their skin when *they* joined, so this new
        // client never saw that upload packet and would otherwise have an
        // empty OppRemoteSkinCache for all of them until they individually
        // rejoin.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joining = handler.player;
            if (ServerSkinManager.hasSkin(joining.getUuid())) {
                byte[] cape = ServerSkinManager.getCape(joining.getUuid());
                OppTextureInjector.apply(joining, ServerSkinManager.isSlim(joining.getUuid()), cape != null);
            }

            ServerSkinManager.forEachSkin((uuid, png, slim, capePng) -> {
                if (ServerPlayNetworking.canSend(joining, SkinSyncPayload.ID)) {
                    sender.sendPacket(new SkinSyncPayload(uuid, png, slim,
                            capePng != null ? Optional.of(capePng) : Optional.empty()));
                }
            });
        });
    }
}