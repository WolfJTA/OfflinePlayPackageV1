package com.jordan.mods.opp.mixin.client;

import com.jordan.mods.opp.client.OppRemoteSkinCache;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a skin cached via OppRemoteSkinCache override the vanilla skin
 * lookup for that player entity, so opp-aware clients see the right skin
 * immediately rather than waiting on a PlayerListS2CPacket round trip or
 * the server's HTTP skin host. Vanilla clients are unaffected - they still
 * resolve skins the normal way via the GameProfile texture URL that
 * OppTextureInjector sets server-side.
 *
 * NOTE ON 1.21.11: the skin accessor on AbstractClientPlayerEntity was
 * renamed from getSkinTextures() to getSkin() as part of the 1.21.11
 * mapping changes, and SkinTextures itself moved to
 * net.minecraft.entity.player.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void opp$overrideCachedSkin(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        SkinTextures cached = OppRemoteSkinCache.get(self.getUuid());
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }
}