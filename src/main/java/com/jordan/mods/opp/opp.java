package com.jordan.mods.opp;

import com.jordan.mods.opp.network.SkinSyncPayload;
import com.jordan.mods.opp.network.SkinUploadPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public class opp implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(SkinUploadPayload.ID, SkinUploadPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SkinSyncPayload.ID, SkinSyncPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(ServerSkinManager::init);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerSkinManager.clear());

        // Client uploads their skin -> store it, then tell everyone else about it
        ServerPlayNetworking.registerGlobalReceiver(SkinUploadPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayerEntity player = context.player();
                    ServerSkinManager.storeSkin(player.getUuid(), payload.skinPng());

                    SkinSyncPayload sync = new SkinSyncPayload(player.getUuid(), payload.skinPng());
                    for (ServerPlayerEntity other : context.server().getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(other, sync);
                    }
                })
        );

        // New join -> backfill every already-known skin (covers players who joined earlier)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joining = handler.player;
            ServerSkinManager.forEachSkin((uuid, bytes) ->
                    ServerPlayNetworking.send(joining, new SkinSyncPayload(uuid, bytes))
            );
        });
    }
}