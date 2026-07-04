package com.jordan.mods.opp.client.screen;

import net.minecraft.client.util.DefaultSkinHelper;
import com.jordan.mods.opp.client.OppPresetSkins;
import com.jordan.mods.opp.client.OppSkinHistory;
import com.jordan.mods.opp.client.OppSkinManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The wardrobe: a two-pane skin browser. The left side shows a big
 * click-and-drag 3D preview of whatever's currently equipped (same
 * PlayerSkinWidget/SkinTextures pattern as OppSkinScreen). The right side
 * is a scrollable card list of every skin available to wear, grouped
 * top-to-bottom as:
 *
 *   1. "Your Skins"          - every skin you've ever loaded through the
 *                               Custom Skin/Cape Editor's file browser,
 *                               newest first (see OppSkinHistory).
 *   2. "Preloaded Skins"     - the bundled non-vanilla presets.
 *   3. "Mojang Default Skins"- the nine vanilla Steve/Alex-family skins,
 *                               bundled the same way as the other presets.
 *
 * Clicking any card applies that skin immediately, the same one-click
 * behavior the old preset grid had. Each card carries its own small
 * PlayerSkinWidget as a thumbnail so you can actually see the skin before
 * picking it, rather than just reading a name off a button.
 *
 * The list itself isn't a vanilla EntryListWidget/ElementListWidget - it's
 * a hand-rolled scroll area (manual scroll offset + enableScissor, mouse
 * wheel and click hit-testing done ourselves) following the exact same
 * pattern OfflineTutorialScreen already uses for its scrolling step list.
 * That keeps this screen from depending on guessing an unfamiliar vanilla
 * list-widget API surface, while still giving smooth scrolling over
 * however many cards end up in the wardrobe.
 */
public class WardrobeScreen extends Screen {

    private static final Identifier PREVIEW_ASSET_ID = Identifier.of("opp", "wardrobe_skin_preview");
    private static final Identifier PREVIEW_CAPE_ASSET_ID = Identifier.of("opp", "wardrobe_cape_preview");

    /** Preset ids that are Mojang's own vanilla default skins, split into their own section rather than "Preloaded". */
    private static final Set<String> MOJANG_DEFAULT_IDS = Set.of(
            "steve", "alex", "sunny", "kai", "makena", "noor", "ari", "zuri", "efe"
    );

    private static final int THUMB_W = 26;
    private static final int THUMB_H = 34;
    private static final int CARD_HEIGHT = 38;
    private static final int HEADER_HEIGHT = 20;
    private static final int NOTE_HEIGHT = 26;
    private static final int ROW_GAP = 4;

    private final Screen parent;

    private String statusMessage = "";
    private boolean statusIsError = false;
    private SkinTextures previewSkinTextures;

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, PlayerSkinWidget> cardWidgets = new HashMap<>();

    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int listContentRight;
    private double scrollOffset = 0;
    private double maxScroll = 0;

    /** A single entry in the wardrobe - one card the player can click to wear. */
    private record CardEntry(String key, String displayName, boolean slim, Supplier<byte[]> bytesSupplier) {
    }

    /** One row in the scrollable list: either a section header, an informational note, or a clickable card. */
    private record Row(Kind kind, String text, CardEntry card) {
        enum Kind {HEADER, NOTE, CARD}

        static Row header(String text) {
            return new Row(Kind.HEADER, text, null);
        }

        static Row note(String text) {
            return new Row(Kind.NOTE, text, null);
        }

        static Row card(CardEntry card) {
            return new Row(Kind.CARD, null, card);
        }

        int height() {
            return switch (this.kind) {
                case HEADER -> HEADER_HEIGHT;
                case NOTE -> NOTE_HEIGHT;
                case CARD -> CARD_HEIGHT;
            };
        }
    }

    public WardrobeScreen(Screen parent) {
        super(Text.literal("Wardrobe"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        this.rows.clear();
        this.cardWidgets.clear();
        this.scrollOffset = 0;

        // --- Left pane: big live preview of whatever's currently equipped ---
        int previewWidth = 100;
        int previewHeight = 140;
        int leftPaneWidth = Math.max(150, this.width / 4);
        int previewX = 10 + (leftPaneWidth - previewWidth) / 2;
        int previewY = 40;

        PlayerSkinWidget skinWidget = new PlayerSkinWidget(previewWidth, previewHeight, MinecraftClient.getInstance().getLoadedEntityModels(), this::opp$currentPreviewSkin);
        skinWidget.setPosition(previewX, previewY);
        this.addDrawableChild(skinWidget);

        this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes(), OppSkinManager.getCapeBytes());

        // --- Bottom bar (shared across both panes) ---
        int doneWidth = 200;
        int doneY = this.height - 30;
        int editorY = doneY - 26;
        int capeRowY = editorY - 26;

        int bottomButtonW = 150;
        int bottomGap = 8;
        int bottomTotal = bottomButtonW * 2 + bottomGap;
        int bottomX = (this.width - bottomTotal) / 2;

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Use Default Cape"), button -> this.opp$applyDefaultCape())
                        .dimensions(bottomX, capeRowY, bottomButtonW, 20).build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Clear Cape"), button -> {
                    OppSkinManager.clearCape();
                    this.statusMessage = "Cape cleared.";
                    this.statusIsError = false;
                    this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes(), null);
                }).dimensions(bottomX + bottomButtonW + bottomGap, capeRowY, bottomButtonW, 20).build()
        );

        int editorWidth = 220;
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Offline Skins/Capes..."), button -> this.client.setScreen(new OppSkinScreen(this.parent)))
                        .dimensions((this.width - editorWidth) / 2, editorY, editorWidth, 20).build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions((this.width - doneWidth) / 2, doneY, doneWidth, 20)
                        .build()
        );

        // --- Right pane: scrollable card list ---
        this.listLeft = 10 + leftPaneWidth + 16;
        this.listRight = this.width - 10;
        this.listTop = 40;
        this.listBottom = capeRowY - 10;
        this.listContentRight = this.listRight - 8;

        this.opp$buildRows();

        int contentHeight = 0;
        for (Row row : this.rows) {
            contentHeight += row.height() + ROW_GAP;
        }
        this.maxScroll = Math.max(0, contentHeight - (this.listBottom - this.listTop));
    }

    /** Builds the flattened row list (headers/notes/cards) and the per-card thumbnail widgets. */
    private void opp$buildRows() {
        List<CardEntry> yourSkins = new ArrayList<>();
        for (OppSkinHistory.Entry entry : OppSkinHistory.getAll()) {
            CardEntry card = new CardEntry(entry.hash(), entry.displayName(), entry.slim(), () -> OppSkinHistory.getBytes(entry.hash()));
            if (card.bytesSupplier().get() != null) {
                yourSkins.add(card);
            }
        }

        List<CardEntry> preloaded = new ArrayList<>();
        List<CardEntry> mojangDefaults = new ArrayList<>();
        for (OppPresetSkins.Preset preset : OppPresetSkins.all()) {
            CardEntry card = new CardEntry(preset.id(), preset.displayName(), preset.slim(), () -> OppPresetSkins.getBytes(preset));
            if (card.bytesSupplier().get() == null) {
                continue;
            }
            if (MOJANG_DEFAULT_IDS.contains(preset.id())) {
                mojangDefaults.add(card);
            } else {
                preloaded.add(card);
            }
        }

        this.rows.add(Row.header("Your Skins"));
        if (yourSkins.isEmpty()) {
            this.rows.add(Row.note("No custom skins loaded yet - use the Custom Skin / Cape Editor to add one."));
        } else {
            for (CardEntry card : yourSkins) {
                this.rows.add(Row.card(card));
            }
        }

        this.rows.add(Row.header("Preloaded Skins"));
        for (CardEntry card : preloaded) {
            this.rows.add(Row.card(card));
        }

        this.rows.add(Row.header("Mojang Default Skins"));
        for (CardEntry card : mojangDefaults) {
            this.rows.add(Row.card(card));
        }

        for (Row row : this.rows) {
            if (row.kind() != Row.Kind.CARD) {
                continue;
            }
            CardEntry entry = row.card();
            SkinTextures textures = this.opp$buildCardTexture(entry.key(), entry.bytesSupplier().get(), entry.slim());
            if (textures != null) {
                PlayerSkinWidget widget = new PlayerSkinWidget(THUMB_W, THUMB_H, MinecraftClient.getInstance().getLoadedEntityModels(), () -> textures);
                this.cardWidgets.put(entry.key(), widget);
            }
        }
    }

    private void opp$applyCard(CardEntry entry) {
        byte[] bytes = entry.bytesSupplier().get();
        if (bytes == null) {
            this.statusMessage = "Couldn't load \"" + entry.displayName() + "\" - resource missing.";
            this.statusIsError = true;
            return;
        }

        OppSkinManager.setSlimModel(entry.slim());
        String error = OppSkinManager.trySetSkinBytes(bytes);
        if (error == null) {
            this.statusMessage = "Wearing \"" + entry.displayName() + "\". Rejoin/reconnect to show it to others.";
            this.statusIsError = false;
            this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes(), OppSkinManager.getCapeBytes());
        } else {
            this.statusMessage = error;
            this.statusIsError = true;
        }
    }

    private void opp$applyDefaultCape() {
        byte[] bytes = OppPresetSkins.getDefaultCapeBytes();
        if (bytes == null) {
            this.statusMessage = "Default cape resource is missing.";
            this.statusIsError = true;
            return;
        }

        String error = OppSkinManager.trySetCapeBytes(bytes);
        if (error == null) {
            this.statusMessage = "Default cape applied.";
            this.statusIsError = false;
            this.opp$rebuildPreviewTexture(OppSkinManager.getSkinBytes(), OppSkinManager.getCapeBytes());
        } else {
            this.statusMessage = error;
            this.statusIsError = true;
        }
    }

    /**
     * Same texture-registration pattern as OppSkinScreen/OppRemoteSkinCache
     * - decode, register with TextureManager under a fixed id (there's only
     * ever one wardrobe preview at a time), then build SkinTextures from it.
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
        client.getTextureManager().registerTexture(skinTextureId, new NativeImageBackedTexture(() -> "opp wardrobe skin preview", skinImage));
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
                client.getTextureManager().registerTexture(capeTextureId, new NativeImageBackedTexture(() -> "opp wardrobe cape preview", capeImage));
                cape = new AssetInfo.TextureAssetInfo(PREVIEW_CAPE_ASSET_ID);
            }
        }

        this.previewSkinTextures = SkinTextures.create(body, cape, null,
                OppSkinManager.isSlimModel() ? PlayerSkinType.SLIM : PlayerSkinType.WIDE);
    }

    /**
     * Decodes and registers a card's skin PNG as its own small texture
     * (no cape - thumbnails are skin-only) and builds the SkinTextures its
     * PlayerSkinWidget thumbnail renders from. Returns null if the PNG is
     * missing/unreadable, in which case the caller skips that card.
     */
    private SkinTextures opp$buildCardTexture(String key, byte[] pngBytes, boolean slim) {
        if (pngBytes == null) {
            return null;
        }

        NativeImage image;
        try {
            image = NativeImage.read(pngBytes);
        } catch (IOException e) {
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Identifier assetId = Identifier.of("opp", "wardrobe_card/" + key);
        Identifier textureId = assetId.withPath(path -> "textures/" + path + ".png");
        client.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(() -> "opp wardrobe card " + key, image));
        AssetInfo.TextureAsset body = new AssetInfo.TextureAssetInfo(assetId);

        return SkinTextures.create(body, null, null, slim ? PlayerSkinType.SLIM : PlayerSkinType.WIDE);
    }

    private SkinTextures opp$currentPreviewSkin() {
        if (this.previewSkinTextures != null) {
            return this.previewSkinTextures;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        AbstractClientPlayerEntity player = client.player;

        // If we're in-game, use the actual player's skin
        if (player != null) {
            return player.getSkin();
        }

        // Fallback for the Main Menu: Generate a default skin using the session UUID
        return DefaultSkinHelper.getSkinTextures(client.getSession().getUuidOrNull());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScroll > 0 && mouseX >= this.listLeft && mouseX <= this.listRight
                && mouseY >= this.listTop && mouseY <= this.listBottom) {
            this.scrollOffset -= verticalAmount * 40;
            this.scrollOffset = MathHelper.clamp(this.scrollOffset, 0, this.maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() != 0 || mouseX < this.listLeft || mouseX > this.listRight
                || mouseY < this.listTop || mouseY > this.listBottom) {
            return false;
        }

        int y = this.listTop - (int) this.scrollOffset;
        for (Row row : this.rows) {
            int h = row.height();
            if (row.kind() == Row.Kind.CARD && mouseY >= y && mouseY < y + h) {
                this.opp$applyCard(row.card());
                return true;
            }
            y += h + ROW_GAP;
        }
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        context.enableScissor(this.listLeft, this.listTop, this.listRight, this.listBottom);

        int y = this.listTop - (int) this.scrollOffset;
        for (Row row : this.rows) {
            int h = row.height();

            if (y + h >= this.listTop && y <= this.listBottom) {
                switch (row.kind()) {
                    case HEADER -> {
                        context.drawTextWithShadow(this.textRenderer, Text.literal(row.text()).styled(style -> style.withBold(true)),
                                this.listLeft + 2, y + 5, 0xFFFFFFFF);
                        context.fill(this.listLeft, y + h - 2, this.listContentRight, y + h - 1, 0x55FFFFFF);
                    }
                    case NOTE -> context.drawTextWithShadow(this.textRenderer, Text.literal(row.text()),
                            this.listLeft + 2, y + 8, 0xFF999999);
                    case CARD -> {
                        CardEntry entry = row.card();
                        boolean hovered = mouseX >= this.listLeft && mouseX <= this.listContentRight
                                && mouseY >= y && mouseY < y + h
                                && mouseY >= this.listTop && mouseY <= this.listBottom;
                        if (hovered) {
                            context.fill(this.listLeft, y, this.listContentRight, y + h, 0x33FFFFFF);
                        }

                        PlayerSkinWidget thumb = this.cardWidgets.get(entry.key());
                        if (thumb != null) {
                            thumb.setPosition(this.listLeft + 6, y + (h - THUMB_H) / 2);
                            thumb.render(context, mouseX, mouseY, delta);
                        }

                        int textX = this.listLeft + 6 + THUMB_W + 10;
                        int textY = y + (h - this.textRenderer.fontHeight) / 2;
                        context.drawTextWithShadow(this.textRenderer, Text.literal(entry.displayName()), textX, textY, 0xFFFFFFFF);
                    }
                }
            }

            y += h + ROW_GAP;
        }

        context.disableScissor();

        if (this.maxScroll > 0) {
            int trackHeight = this.listBottom - this.listTop;
            int thumbHeight = Math.max(10, (int) (trackHeight * (trackHeight / (double) (trackHeight + this.maxScroll))));
            int thumbY = this.listTop + (int) ((trackHeight - thumbHeight) * (this.scrollOffset / this.maxScroll));
            int barX = this.listRight - 3;

            context.fill(barX, this.listTop, barX + 2, this.listBottom, 0x33FFFFFF);
            context.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0xAAFFFFFF);
        }

        if (!this.statusMessage.isEmpty()) {
            int color = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), this.width / 2, 24, color);
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