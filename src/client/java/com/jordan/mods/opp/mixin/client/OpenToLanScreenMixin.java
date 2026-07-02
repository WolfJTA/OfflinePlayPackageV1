package com.jordan.mods.opp.mixin.client;

import com.jordan.mods.opp.client.OppState;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a visible "Offline Mode" on/off toggle (default ON) to the Open to
 * LAN screen, ensures the Port field starts pre-filled with 25565, and adds
 * small "Why?" info buttons next to both.
 */
@Mixin(OpenToLanScreen.class)
public abstract class OpenToLanScreenMixin extends Screen {

    private static final String DEFAULT_PORT = "25565";

    protected OpenToLanScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void opp$addOfflineModeToggleAndDefaults(CallbackInfo ci) {
        // Offline Mode toggle, top-right corner.
        int toggleWidth = 150;
        int toggleX = this.width - toggleWidth - 6;
        int toggleY = 6;

        CyclingButtonWidget<Boolean> offlineModeToggle = CyclingButtonWidget.onOffBuilder(OppState.offlineModeEnabled)
                .build(toggleX, toggleY, toggleWidth, 20, Text.literal("Offline Mode"),
                        (button, value) -> OppState.offlineModeEnabled = value);
        this.addDrawableChild(offlineModeToggle);

        // "Why?" button just to the left of the toggle.
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("?"), button -> {
                        })
                        .dimensions(toggleX - 24, toggleY, 20, 20)
                        .tooltip(Tooltip.of(Text.literal("Offline Mode lets friends without a linked/authenticated Minecraft account join. It's on by default since this mod is meant for playing without internet access.")))
                        .build()
        );

        // Auto-fill the port field with 25565 if it's currently empty, and
        // add a "Why?" button right after it.
        TextFieldWidget portField = ((OpenToLanScreenAccessor) this).opp$getPortField();
        if (portField != null) {
            if (portField.getText().isBlank()) {
                portField.setText(DEFAULT_PORT);
            }

            ClickableWidget field = portField;
            int infoX = field.getX() + field.getWidth() + 4;
            int infoY = field.getY();
            int infoHeight = field.getHeight();

            this.addDrawableChild(
                    ButtonWidget.builder(Text.literal("?"), button -> {
                            })
                            .dimensions(infoX, infoY, 20, infoHeight)
                            .tooltip(Tooltip.of(Text.literal("25565 is Minecraft's default port, so it usually needs no changes unless something else on your network is already using it.")))
                            .build()
            );
        }
    }
}