package com.jordan.mods.opp.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Small hub screen for OfflinePlayPackage - links out to the tutorial and
 * the offline skin screen. Used as the Mod Menu config screen, and reused
 * as the target of the title-screen/options-screen shortcut buttons.
 */
public class OppMenuScreen extends Screen {

    private final Screen parent;

    public OppMenuScreen(Screen parent) {
        super(Text.literal("OfflinePlayPackage"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 200;
        int x = (this.width - buttonWidth) / 2;
        int y = this.height / 2 - 30;

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Tutorial"), button -> this.client.setScreen(new OfflineTutorialScreen(this)))
                        .dimensions(x, y, buttonWidth, 20).build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Offline Skin"), button -> this.client.setScreen(new OppSkinScreen(this)))
                        .dimensions(x, y + 26, buttonWidth, 20).build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions(x, this.height - 30, buttonWidth, 20).build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}