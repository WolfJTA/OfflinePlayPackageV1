package com.jordan.mods.opp.client.screen;

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
 * The cape slot in SkinTextures.create(...) works the exact same way -
 * see opp$rebuildPreviewTexture below.
 *
 * Every skin successfully loaded here through the file browser/path field
 * also gets handed to OppSkinHistory, which is what lets the Wardrobe
 * (see the "Wardrobe" button below, and WardrobeScreen) show a "Your
 * Skins" section of everything you've ever loaded, on top of the bundled
 * preset/Mojang-default skins.
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
        super(Text.literal("Offline Skin"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int previewWidth = 70;
        int previewHeight = 100;
        this.skinWidget = new PlayerSkinWidget(previewWidth, previewHeight, MinecraftClient.getInstance().getLoadedEntityModels(), this::opp$currentPreviewSkin);
        this.skinWidget.setPosition(this.width / 2 - previewWidth / 2, this.height / 2 - 130);
        this.addDrawableChild(this.skinWidget);

        int fieldWidth = 200;
        int browseWidth = 80;
        int gap = 6;
        int totalWidth = fieldWidth + gap + browseWidth;
        int fieldX = (this.width - totalWidth) / 2;
        int fieldY = this.height / 2 + 8;

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
                                    this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes(), OppSkinManager.getCapeBytes());
                                })
        );

        // Cape controls - entirely optional and independent of the skin
        // itself. A cape isn't a valid Minecraft skin size (64x32 rather
        // than 64x64) so it needs its own file dialog / validation path,
        // but shares the same "read bytes -> validate -> persist ->
        // rebuild preview" shape as the skin controls above.
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

        // Wardrobe shortcut - opens the full "Your Skins" (history) /
        // "Preloaded Skins" / "Mojang Default Skins" browser. Tucked into
        // the bottom-left corner (same spot OfflineTutorialScreen uses for
        // its own shortcut) so it doesn't compete with the vertical stack
        // of skin/cape controls above. Handing it our own parent (rather
        // than "this") means pressing Done inside the wardrobe goes back to
        // whatever opened the skin editor, instead of bouncing back
        // through this screen.
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Wardrobe"), button -> this.client.setScreen(new WardrobeScreen(this.parent)))
                        .dimensions(10, bottomY, 100, 20)
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

    /** Derives a friendly "Your Skins" card name from a loaded file's path, e.g. "my_skin.png" -> "my_skin". */
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

    /**
     * Opens the native OS file picker on a background thread (tinyfd blocks
     * the calling thread until the dialog closes, so this avoids freezing
     * the game while it's open) and hops back onto the client thread to
     * apply the result.
     */
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

    /** Same pattern as opp$openSkinFileDialog, but for the 64x32 cape PNG. */
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

    /**
     * Decodes the given skin PNG (and cape PNG, if present), registers them
     * as textures with the client's TextureManager, and rebuilds
     * previewSkinTextures from them - same pattern OppRemoteSkinCache.apply()
     * uses for other players, just under fixed ids since there's only ever
     * one local preview at a time.
     */
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

        // Re-registering under the same id replaces whatever texture was
        // there before, so we don't leak a GL texture per skin change.
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
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Drag the model to rotate it."), this.width / 2, this.height / 2 - 19, 0xFF777777);

        if (!this.statusMessage.isEmpty()) {
            int color = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), this.width / 2, this.height / 2 + 112, color);
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