package com.jordan.mods.opp.client.screen;

import com.jordan.mods.opp.client.OppSkinManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Lets the player set a custom local skin PNG (64x64) to be used while
 * playing offline, with a live rotatable 3D preview, and pick the arm/model
 * type (slim "Alex" vs classic "Steve") that goes with it. The file itself
 * is uploaded to the server, which stores it and makes it visible to other
 * players (modded or vanilla) via ServerSkinManager / OppSkinHttpServer.
 *
 * The 3D preview reuses vanilla's own PlayerSkinWidget (the same widget the
 * "Select Character" screen uses) instead of hand-rolling a model renderer.
 * It reads from a dynamic texture registered under a fixed identifier,
 * which we re-upload whenever the loaded skin bytes change. If no custom
 * skin is loaded yet, the widget falls back to this account's default
 * (Steve/Alex) placeholder so it's never blank.
 */
public class OppSkinScreen extends Screen {

    private static final Identifier PREVIEW_TEXTURE_ID = Identifier.of("opp_v1", "skin_preview");

    private final Screen parent;
    private TextFieldWidget pathField;
    private String statusMessage = "";
    private boolean statusIsError = false;

    private NativeImageBackedTexture previewTexture;

    public OppSkinScreen(Screen parent) {
        super(Text.literal("Offline Skin"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = 200;
        int browseWidth = 80;
        int gap = 6;
        int totalWidth = fieldWidth + gap + browseWidth;
        int fieldX = (this.width - totalWidth) / 2;

        // 3D preview sits below the title/subtitle text (which render at
        // height/2-70 and height/2-56), with the rest of the layout
        // flowing beneath it.
        int previewWidth = 40;
        int previewHeight = 64;
        int previewX = (this.width - previewWidth) / 2;
        int previewY = this.height / 2 - 50;

        int fieldY = previewY + previewHeight + 8;

        if (OppSkinManager.hasCustomSkin() && this.previewTexture == null) {
            this.statusMessage = "A custom offline skin is currently loaded.";
            this.statusIsError = false;
            this.opp$updatePreview(OppSkinManager.getSkinBytes());
        }

        PlayerSkinWidget skinWidget = new PlayerSkinWidget(previewWidth, previewHeight,
                MinecraftClient.getInstance().getEntityModelLoader(), this::opp$currentSkinTextures);
        skinWidget.setPosition(previewX, previewY);
        this.addDrawableChild(skinWidget);

        this.pathField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldWidth, 20, Text.literal("Skin file path"));
        this.pathField.setMaxLength(1024);
        this.addDrawableChild(this.pathField);

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Browse..."), button -> this.opp$openFileDialog())
                        .dimensions(fieldX + fieldWidth + gap, fieldY, browseWidth, 20)
                        .build()
        );

        int buttonY = fieldY + 26;
        int buttonWidth = (totalWidth - 10) / 2;

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Load Skin"), button -> {
                    String path = this.pathField.getText();
                    if (path.isBlank()) {
                        this.statusMessage = "Choose a file first.";
                        this.statusIsError = true;
                        return;
                    }
                    this.opp$applySkin(path);
                }).dimensions(fieldX, buttonY, buttonWidth, 20).build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Clear Skin"), button -> {
                    OppSkinManager.clearSkin();
                    this.pathField.setText("");
                    this.statusMessage = "Custom skin cleared.";
                    this.statusIsError = false;
                    this.opp$closePreviewTexture();
                }).dimensions(fieldX + buttonWidth + 10, buttonY, buttonWidth, 20).build()
        );

        // Slim ("Alex") vs classic ("Steve") arm model - not something we
        // can reliably infer from the PNG, so it's a manual toggle.
        int modelY = buttonY + 26;
        this.addDrawableChild(
                CyclingButtonWidget.<Boolean>builder(slim -> Text.literal(slim ? "Slim (Alex) arms" : "Classic (Steve) arms"), OppSkinManager.isSlimModel())
                        .values(Boolean.FALSE, Boolean.TRUE)
                        .build(fieldX, modelY, totalWidth, 20, Text.literal("Arm Model"),
                                (button, slim) -> OppSkinManager.setSlimModel(slim))
        );

        int doneWidth = 200;
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions((this.width - doneWidth) / 2, this.height - 30, doneWidth, 20)
                        .build()
        );
    }

    private void opp$applySkin(String path) {
        String error = OppSkinManager.trySetSkin(path);
        if (error == null) {
            this.statusMessage = "Skin loaded successfully!";
            this.statusIsError = false;
            this.opp$updatePreview(OppSkinManager.getSkinBytes());
        } else {
            this.statusMessage = error;
            this.statusIsError = true;
        }
    }

    /**
     * Opens the native OS file picker on a background thread (tinyfd blocks
     * the calling thread until the dialog closes, so this avoids freezing
     * the game while it's open) and hops back onto the client thread to
     * apply the result.
     */
    private void opp$openFileDialog() {
        Thread dialogThread = new Thread(() -> {
            String selected = TinyFileDialogs.tinyfd_openFileDialog("Select Offline Skin (64x64 PNG)", "", null, null, false);

            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (selected == null) {
                    return;
                }
                this.pathField.setText(selected);
                this.opp$applySkin(selected);
            });
        }, "opp-file-dialog");
        dialogThread.setDaemon(true);
        dialogThread.start();
    }

    /**
     * Decodes the given PNG bytes and (re)registers them as the dynamic
     * texture the preview widget reads from. Any previously registered
     * preview texture is torn down first so we never leak a GL texture.
     */
    private void opp$updatePreview(byte[] pngBytes) {
        this.opp$closePreviewTexture();

        if (pngBytes == null) {
            return;
        }

        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(pngBytes));
            this.previewTexture = new NativeImageBackedTexture(() -> "opp_skin_preview", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(PREVIEW_TEXTURE_ID, this.previewTexture);
        } catch (IOException e) {
            this.previewTexture = null;
        }
    }

    private void opp$closePreviewTexture() {
        if (this.previewTexture != null) {
            // destroyTexture() closes the underlying GL texture for us.
            MinecraftClient.getInstance().getTextureManager().destroyTexture(PREVIEW_TEXTURE_ID);
            this.previewTexture = null;
        }
    }

    /**
     * Supplier handed to PlayerSkinWidget. Points at our registered dynamic
     * texture while a custom skin is loaded, otherwise falls back to this
     * account's default Steve/Alex placeholder so the widget is never left
     * rendering nothing.
     */
    private SkinTextures opp$currentSkinTextures() {
        SkinTextures.Model model = OppSkinManager.isSlimModel() ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE;

        if (this.previewTexture != null) {
            return new SkinTextures(PREVIEW_TEXTURE_ID, null, null, null, model, true);
        }

        return DefaultSkinHelper.getSkinTextures(MinecraftClient.getInstance().getGameProfile());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 70, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Pick a 64x64 skin PNG, or paste a file path directly."), this.width / 2, this.height / 2 - 56, 0xFF999999);

        if (!this.statusMessage.isEmpty()) {
            int color = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), this.width / 2, this.height / 2 + 100, color);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void removed() {
        this.opp$closePreviewTexture();
        super.removed();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
