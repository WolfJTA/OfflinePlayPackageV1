package com.jordan.mods.opp.client.screen;

import net.minecraft.client.util.DefaultSkinHelper;
import com.jordan.mods.opp.client.OppSkinHistory;
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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Lets the player set a custom local skin PNG (64x64) and an optional cape
 * PNG (64x32) to be used while playing offline, with a live 3D model
 * preview, and pick the arm/model type (slim "Alex" vs classic "Steve")
 * that goes with it. Both files are uploaded to the server, which stores
 * them and makes them visible to other players (modded or vanilla) via
 * ServerSkinManager / OppSkinHttpServer.
 */
public class OppSkinScreen extends Screen {

    private static final Identifier PREVIEW_ASSET_ID = Identifier.of("opp", "local_skin_preview");
    private static final Identifier PREVIEW_CAPE_ASSET_ID = Identifier.of("opp", "local_cape_preview");

    private final Screen parent;
    private TextFieldWidget pathField;
    private PlayerSkinWidget skinWidget;
    private String statusMessage = "";
    private boolean statusIsError = false;

    /** Null until a custom skin has been loaded this session; falls back to the real player's current skin until then. */
    private SkinTextures previewSkinTextures;

    public OppSkinScreen(Screen parent) {
        super(Text.literal("Offline Skins/Capes"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int previewWidth = 80;
        int previewHeight = 110;

        // Original X value restored; Y position calculated to center the element vertically
        int previewX = 20;
        int previewY = (this.height - previewHeight) / 2;

        this.skinWidget = new PlayerSkinWidget(previewWidth, previewHeight, MinecraftClient.getInstance().getLoadedEntityModels(), this::opp$currentPreviewSkin);
        this.skinWidget.setPosition(previewX, previewY);
        this.addDrawableChild(this.skinWidget);

        int fieldWidth = 200;
        int browseWidth = 80;
        int gap = 6;
        int totalWidth = fieldWidth + gap + browseWidth;
        int fieldX = previewX + previewWidth + 40;
        int fieldY = previewY;

        this.pathField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldWidth, 20, Text.literal("Skin file path"));
        this.pathField.setMaxLength(1024);
        this.addDrawableChild(this.pathField);

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Browse..."), button -> this.opp$openSkinFileDialog())
                        .dimensions(fieldX + fieldWidth + gap, fieldY, browseWidth, 20)
                        .build()
        );

        if (OppSkinManager.hasCustomSkin()) {
            this.statusMessage = "A custom offline skin is currently loaded.";
            this.statusIsError = false;
        }
        this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes(), OppSkinManager.getCapeBytes());

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
                    this.opp$rebuildPreviewTexture(null, OppSkinManager.getCapeBytes());
                }).dimensions(fieldX + buttonWidth + 10, buttonY, buttonWidth, 20).build()
        );

        int modelY = buttonY + 26;
        this.addDrawableChild(
                CyclingButtonWidget.<Boolean>builder(slim -> Text.literal(slim ? "Slim (Alex) arms" : "Classic (Steve) arms"), OppSkinManager.isSlimModel())
                        .values(Boolean.FALSE, Boolean.TRUE)
                        .build(fieldX, modelY, totalWidth, 20, Text.literal("Arm Model"),
                                (button, slim) -> {
                                    OppSkinManager.setSlimModel(slim);
                                    this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes(), OppSkinManager.getCapeBytes());
                                })
        );

        int capeY = modelY + 26;
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal(OppSkinManager.hasCape() ? "Replace Cape..." : "Load Cape..."), button -> this.opp$openCapeFileDialog())
                        .dimensions(fieldX, capeY, buttonWidth, 20).build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Clear Cape"), button -> {
                    OppSkinManager.clearCape();
                    this.statusMessage = "Cape cleared.";
                    this.statusIsError = false;
                    this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes(), null);
                }).dimensions(fieldX + buttonWidth + 10, capeY, buttonWidth, 20).build()
        );

        int doneWidth = 200;
        int bottomY = this.height - 30;
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions((this.width - doneWidth) / 2, bottomY, doneWidth, 20)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Wardrobe"), button -> this.client.setScreen(new WardrobeScreen(this.parent)))
                        .dimensions(this.width - 110, bottomY, 100, 20)
                        .build()
        );
    }

    private void opp$applySkin(String path) {
        String error = OppSkinManager.trySetSkin(path);
        if (error == null) {
            this.statusMessage = "Skin loaded successfully!";
            this.statusIsError = false;
            this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes(), OppSkinManager.getCapeBytes());
            OppSkinHistory.record(opp$displayNameFromPath(path), OppSkinManager.getSkinBytes(), OppSkinManager.isSlimModel());
        } else {
            this.statusMessage = error;
            this.statusIsError = true;
        }
    }

    private static String opp$displayNameFromPath(String rawPath) {
        try {
            String fileName = Path.of(rawPath.trim().replace("\"", "")).getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String base = dot > 0 ? fileName.substring(0, dot) : fileName;
            return base.isBlank() ? "Custom Skin" : base;
        } catch (Exception e) {
            return "Custom Skin";
        }
    }

    private void opp$applyCape(String path) {
        String error = OppSkinManager.trySetCape(path);
        if (error == null) {
            this.statusMessage = "Cape loaded successfully!";
            this.statusIsError = false;
            this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes(), OppSkinManager.getCapeBytes());
        } else {
            this.statusMessage = error;
            this.statusIsError = true;
        }
    }

    private void opp$openSkinFileDialog() {
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

    private void opp$openCapeFileDialog() {
        Thread dialogThread = new Thread(() -> {
            String selected = TinyFileDialogs.tinyfd_openFileDialog("Select Cape (64x32 PNG)", "", null, null, false);

            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (selected == null) {
                    return;
                }
                this.opp$applyCape(selected);
            });
        }, "opp-cape-file-dialog");
        dialogThread.setDaemon(true);
        dialogThread.start();
    }

    private void opp$rebuildPreviewTexture(byte[] skinPngBytes, byte[] capePngBytes) {
        if (skinPngBytes == null) {
            this.previewSkinTextures = null;
            return;
        }

        NativeImage skinImage;
        try {
            skinImage = NativeImage.read(skinPngBytes);
        } catch (IOException e) {
            this.previewSkinTextures = null;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Identifier skinTextureId = PREVIEW_ASSET_ID.withPath(path -> "textures/" + path + ".png");

        client.getTextureManager().registerTexture(skinTextureId, new NativeImageBackedTexture(() -> "opp local skin preview", skinImage));
        AssetInfo.TextureAsset body = new AssetInfo.TextureAssetInfo(PREVIEW_ASSET_ID);

        AssetInfo.TextureAsset cape = null;
        if (capePngBytes != null) {
            NativeImage capeImage;
            try {
                capeImage = NativeImage.read(capePngBytes);
            } catch (IOException e) {
                capeImage = null;
            }
            if (capeImage != null) {
                Identifier capeTextureId = PREVIEW_CAPE_ASSET_ID.withPath(path -> "textures/" + path + ".png");
                client.getTextureManager().registerTexture(capeTextureId, new NativeImageBackedTexture(() -> "opp local cape preview", capeImage));
                cape = new AssetInfo.TextureAssetInfo(PREVIEW_CAPE_ASSET_ID);
            }
        }

        this.previewSkinTextures = SkinTextures.create(body, cape, null,
                OppSkinManager.isSlimModel() ? PlayerSkinType.SLIM : PlayerSkinType.WIDE);
    }

    private SkinTextures opp$currentPreviewSkin() {
        if (this.previewSkinTextures != null) {
            return this.previewSkinTextures;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        AbstractClientPlayerEntity player = client.player;

        if (player != null) {
            return player.getSkin();
        }

        return DefaultSkinHelper.getSkinTextures(client.getSession().getUuidOrNull());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Derive Y coordinates relatively from the model height center setup in init()
        int previewHeight = 110;
        int previewY = (this.height - previewHeight) / 2;

        // Header Text shifts upwards dynamically relative to the elements
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, previewY - 35, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Pick a 64x64 skin PNG, or paste a file path directly."), this.width / 2, previewY - 23, 0xFF999999);

        // Description text sits cleanly underneath the 3D element bounds
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Drag the model to rotate it."), this.width / 2, previewY + previewHeight + 10, 0xFF777777);

        if (!this.statusMessage.isEmpty()) {
            int color = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), this.width / 2, previewY + previewHeight + 25, color);
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