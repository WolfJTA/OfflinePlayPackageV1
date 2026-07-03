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
import net.minecraft.client.network.ClientPlayerEntity;

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

        // Single JOIN listener: send our skin (and arm model) to the server,
        // and apply it locally right away rather than waiting on the server
        // to echo it back - this is what guarantees the host sees their own
        // skin instantly. (Previously this logic was duplicated across two
        // separate JOIN registrations, which sent the upload packet twice
        // on every join.)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            byte[] skin = OppSkinManager.getSkinBytes();
            if (skin == null) {
                return;
            }

            boolean slim = OppSkinManager.isSlimModel();

            if (ClientPlayNetworking.canSend(SkinUploadPayload.ID)) {
                sender.sendPacket(new SkinUploadPayload(skin, slim));
            }

            // Use the local player entity's UUID (the one the server actually
            // assigned us), NOT client.getSession().getUuidOrNull(). On offline/
            // cracked servers the server derives its own UUID from the username
            // and ignores the launcher account UUID entirely, so the two can
            // differ. Caching under the wrong UUID here means the mixin lookup
            // in AbstractClientPlayerEntityMixin (keyed by the entity's real
            // UUID) misses, and we fall back to a broken-looking skin. We read
            // client.player inside execute() so we're guaranteed it's set.
            client.execute(() -> {
                ClientPlayerEntity self = client.player;
                if (self != null) {
                    OppRemoteSkinCache.apply(self.getUuid(), skin, slim);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SkinSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> OppRemoteSkinCache.apply(payload.playerId(), payload.skinPng(), payload.slim()))
        );
    }
}