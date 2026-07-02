package com.jordan.mods.opp.client;

import com.jordan.mods.opp.client.screen.OfflineTutorialScreen;
import com.jordan.mods.opp.network.SkinSyncPayload;
import com.jordan.mods.opp.network.SkinUploadPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

public class oppClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OppSkinManager.loadFromDisk();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("opptutorial").executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.execute(() -> client.setScreen(new OfflineTutorialScreen(null)));
                    return 1;
                }))
        );

        // Single JOIN listener: send our skin to the server, and apply it
        // locally right away rather than waiting on the server to echo it
        // back - this is what guarantees the host sees their own skin
        // instantly. (Previously this logic was duplicated across two
        // separate JOIN registrations, which sent the upload packet twice
        // on every join.)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            byte[] skin = OppSkinManager.getSkinBytes();
            if (skin == null) {
                return;
            }

            if (ClientPlayNetworking.canSend(SkinUploadPayload.ID)) {
                sender.sendPacket(new SkinUploadPayload(skin));
            }

            UUID self = client.getSession().getUuidOrNull();
            if (self != null) {
                client.execute(() -> OppRemoteSkinCache.apply(self, skin));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(SkinSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> OppRemoteSkinCache.apply(payload.playerId(), payload.skinPng()))
        );
    }
}