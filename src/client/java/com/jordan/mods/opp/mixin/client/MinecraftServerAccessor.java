package com.jordan.mods.opp.mixin.client;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private online-mode field on MinecraftServer so it can be
 * flipped at runtime when a world is opened to LAN.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    @Accessor("onlineMode")
    void opp$setOnlineMode(boolean onlineMode);
}