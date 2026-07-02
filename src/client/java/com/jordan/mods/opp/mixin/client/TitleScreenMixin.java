package com.jordan.mods.opp.mixin.client;

import com.jordan.mods.opp.client.screen.OfflineTutorialScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a small "Play Offline Together" button to the main menu that opens
 * OfflineTutorialScreen. Placed in the top-left corner so it doesn't
 * collide with the mod-count text or realms notice in the bottom corners.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void opp$addTutorialButton(CallbackInfo ci) {
        int buttonWidth = 150;
        int buttonHeight = 20;
        int x = 4;
        int y = 4;

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Play Offline Together"), button ->
                        this.client.setScreen(new OfflineTutorialScreen(this))
                ).dimensions(x, y, buttonWidth, buttonHeight).build()
        );
    }
}