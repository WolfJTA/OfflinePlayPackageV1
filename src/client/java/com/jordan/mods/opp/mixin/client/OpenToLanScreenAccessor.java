package com.jordan.mods.opp.mixin.client;

import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private port text field on OpenToLanScreen so it can be
 * pre-filled with a sensible default.
 */
@Mixin(OpenToLanScreen.class)
public interface OpenToLanScreenAccessor {

    @Accessor("portField")
    TextFieldWidget opp$getPortField();
}