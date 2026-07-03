package com.jordan.mods.opp.client.screen;

import com.jordan.mods.opp.client.OppSkinManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;

import java.io.IOException;

/**
 * Lets the player set a custom local skin PNG (64x64) to be used while
 * playing offline, with a live 3D model preview, and pick the arm/model
 * type (slim "Alex" vs classic "Steve") that goes with it. The file itself
 * is uploaded to the server, which stores it and makes it visible to other
 * players (modded or vanilla) via ServerSkinManager / OppSkinHttpServer.
 *
 * The 3D preview uses vanilla's own net.minecraft.client.gui.widget.PlayerSkinWidget
 * rather than hand-rolled entity rendering - it already handles model
 * building, texture display, AND click-and-drag rotation internally, so
 * there's nothing to reimplement there. What it needs from us is just a
 * Supplier<SkinTextures> - the same SkinTextures type (and construction
 * pattern: NativeImage -> NativeImageBackedTexture -> AssetInfo.TextureAsset
 * -> SkinTextures.create(...)) that OppRemoteSkinCache already uses for
 * other players' synced skins, confirmed working against 1.21.11's actual
 * mappings (SkinTextures now lives in net.minecraft.entity.player, and
 * takes a PlayerSkinType SLIM/WIDE rather than the old nested Model enum).
 */
public class OppSkinScreen extends Screen {

    private static final Identifier PREVIEW_ASSET_ID = Identifier.of("opp", "local_skin_preview");

    private final Screen parent;
    private TextFieldWidget pathField;
    private PlayerSkinWidget skinWidget;
    private String statusMessage = "";
    private boolean statusIsError = false;

    /** Null until a custom skin has been loaded this session; falls back to the real player's current skin until then. */
    private SkinTextures previewSkinTextures;

    public OppSkinScreen(Screen parent) {
        super(Text.literal("Offline Skin"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int previewWidth = 70;
        int previewHeight = 100;
        this.skinWidget = new PlayerSkinWidget(previewWidth, previewHeight, MinecraftClient.getInstance().getLoadedEntityModels(), this::opp$currentPreviewSkin);
        this.skinWidget.setPosition(this.width / 2 - previewWidth / 2, this.height / 2 - 115); // was -130
        this.addDrawableChild(this.skinWidget);

        int fieldWidth = 200;
        int browseWidth = 80;
        int gap = 6;
        int totalWidth = fieldWidth + gap + browseWidth;
        int fieldX = (this.width - totalWidth) / 2;
        int fieldY = this.height / 2 + 20;

        this.pathField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldWidth, 20, Text.literal("Skin file path"));
        this.pathField.setMaxLength(1024);
        this.addDrawableChild(this.pathField);

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Browse..."), button -> this.opp$openFileDialog())
                        .dimensions(fieldX + fieldWidth + gap, fieldY, browseWidth, 20)
                        .build()
        );

        if (OppSkinManager.hasCustomSkin()) {
            this.statusMessage = "A custom offline skin is currently loaded.";
            this.statusIsError = false;
            this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes());
        }

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
                    this.previewSkinTextures = null;
                }).dimensions(fieldX + buttonWidth + 10, buttonY, buttonWidth, 20).build()
        );

        // Slim ("Alex") vs classic ("Steve") arm model - not something we
        // can reliably infer from the PNG, so it's a manual toggle. Changing
        // it also has to rebuild the preview texture, since the model type
        // is baked into the SkinTextures we hand the widget.
        int modelY = buttonY + 26;
        this.addDrawableChild(
                CyclingButtonWidget.<Boolean>builder(slim -> Text.literal(slim ? "Slim (Alex) arms" : "Classic (Steve) arms"), OppSkinManager.isSlimModel())
                        .values(Boolean.FALSE, Boolean.TRUE)
                        .build(fieldX, modelY, totalWidth, 20, Text.literal("Arm Model"),
                                (button, slim) -> {
                                    OppSkinManager.setSlimModel(slim);
                                    if (OppSkinManager.hasCustomSkin()) {
                                        this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes());
                                    }
                                })
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
            this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes());
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
     * Decodes the given skin PNG, registers it as a texture with the
     * client's TextureManager, and rebuilds previewSkinTextures from it -
     * same pattern OppRemoteSkinCache.apply() uses for other players, just
     * under a fixed id since there's only ever one local preview at a time.
     */
    private void opp$rebuildPreviewTexture(byte[] pngBytes) {
        if (pngBytes == null) {
            this.previewSkinTextures = null;
            return;
        }

        NativeImage image;
        try {
            image = NativeImage.read(pngBytes);
        } catch (IOException e) {
            this.previewSkinTextures = null;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Identifier textureId = PREVIEW_ASSET_ID.withPath(path -> "textures/" + path + ".png");

        // Re-registering under the same id replaces whatever texture was
        // there before, so we don't leak a GL texture per skin change.
        client.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(() -> "opp local skin preview", image));

        AssetInfo.TextureAsset body = new AssetInfo.TextureAssetInfo(PREVIEW_ASSET_ID);
        this.previewSkinTextures = SkinTextures.create(body, null, null,
                OppSkinManager.isSlimModel() ? PlayerSkinType.SLIM : PlayerSkinType.WIDE);
    }

    /**
     * Supplier handed to PlayerSkinWidget. Falls back to the real local
     * player's current skin until a custom one has been loaded this
     * session, so the preview never shows a blank/missing texture.
     */
    private SkinTextures opp$currentPreviewSkin() {
        if (this.previewSkinTextures != null) {
            return this.previewSkinTextures;
        }
        AbstractClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null ? player.getSkin() : null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 150, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Pick a 64x64 skin PNG, or paste a file path directly."), this.width / 2, this.height / 2 - 138, 0xFF999999);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Drag the model to rotate it."), this.width / 2, this.height / 2 - 4, 0xFF777777); // was -24

        if (!this.statusMessage.isEmpty()) {
            int color = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), this.width / 2, this.height / 2 + 98, color); // was +82
        }
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