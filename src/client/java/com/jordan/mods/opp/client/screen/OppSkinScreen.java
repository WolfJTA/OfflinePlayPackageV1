package com.jordan.mods.opp.client.screen;

import com.jordan.mods.opp.client.OppSkinManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Lets the player set a custom local skin PNG (64x64) to be used while
 * playing offline, with a live pixel-block preview. Stage 1: local storage
 * only - networking/rendering to other players comes in a later stage.
 *
 * The preview is decoded with plain java.awt/ImageIO rather than
 * Minecraft's own texture classes, since those have proven to be a moving
 * target across recent 1.21.x patches (1.21.11 is in fact the final
 * Yarn-mapped Minecraft version). ImageIO is a stable JDK API and
 * BufferedImage.getRGB() already returns plain ARGB, matching fill()
 * exactly with no conversion needed.
 */
public class OppSkinScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget pathField;
    private String statusMessage = "";
    private boolean statusIsError = false;

    private int[] previewPixelsArgb;
    private int previewWidth;
    private int previewHeight;

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
        int fieldY = this.height / 2 - 10;

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
            this.opp$updatePreview(OppSkinManager.getSkinBytes());
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
                    this.previewPixelsArgb = null;
                }).dimensions(fieldX + buttonWidth + 10, buttonY, buttonWidth, 20).build()
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

    private void opp$updatePreview(byte[] pngBytes) {
        if (pngBytes == null) {
            return;
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (image == null) {
                this.previewPixelsArgb = null;
                return;
            }

            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = new int[w * h];

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels[y * w + x] = image.getRGB(x, y);
                }
            }

            this.previewPixelsArgb = pixels;
            this.previewWidth = w;
            this.previewHeight = h;
        } catch (IOException e) {
            this.previewPixelsArgb = null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 70, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Pick a 64x64 skin PNG, or paste a file path directly."), this.width / 2, this.height / 2 - 56, 0xFF999999);

        if (this.previewPixelsArgb != null) {
            int scale = 2;
            int drawWidth = this.previewWidth * scale;
            int drawHeight = this.previewHeight * scale;
            int drawX = (this.width - drawWidth) / 2;
            int drawY = this.height / 2 - 40 - drawHeight;

            for (int y = 0; y < this.previewHeight; y++) {
                for (int x = 0; x < this.previewWidth; x++) {
                    int argb = this.previewPixelsArgb[y * this.previewWidth + x];
                    if ((argb >>> 24) == 0) {
                        continue; // fully transparent, skip
                    }
                    int px = drawX + x * scale;
                    int py = drawY + y * scale;
                    context.fill(px, py, px + scale, py + scale, argb);
                }
            }
        }

        if (!this.statusMessage.isEmpty()) {
            int color = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), this.width / 2, this.height / 2 + 20, color);
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