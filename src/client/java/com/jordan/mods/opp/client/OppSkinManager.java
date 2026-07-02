package com.jordan.mods.opp.client;

import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores the player's chosen offline skin locally (a raw 64x64 or 64x32
 * skin PNG) and persists it to disk so it's remembered between sessions.
 * Networking/rendering hooks (Stage 2/3) will read from getSkinBytes().
 */
public final class OppSkinManager {

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("opp_v1");
    private static final Path SKIN_FILE = CONFIG_DIR.resolve("offline_skin.png");

    private static byte[] skinBytes = null;

    private OppSkinManager() {
    }

    public static void loadFromDisk() {
        try {
            if (Files.exists(SKIN_FILE)) {
                skinBytes = Files.readAllBytes(SKIN_FILE);
            }
        } catch (IOException e) {
            skinBytes = null;
        }
    }

    public static boolean hasCustomSkin() {
        return skinBytes != null;
    }

    public static byte[] getSkinBytes() {
        return skinBytes;
    }

    /**
     * Attempts to load a PNG from the given absolute path, validates it's a
     * proper Minecraft skin size (64x64, or the legacy 64x32), and if valid,
     * saves it as this player's offline skin.
     *
     * @return null on success, or a human-readable error message on failure.
     */
    public static String trySetSkin(String rawPath) {
        Path source;
        try {
            source = Path.of(rawPath.trim().replace("\"", ""));
        } catch (Exception e) {
            return "That doesn't look like a valid file path.";
        }

        if (!Files.exists(source)) {
            return "File not found.";
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(source);
        } catch (IOException e) {
            return "Could not read that file.";
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            image = null;
        }

        if (image == null) {
            return "That doesn't look like a valid PNG.";
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width != 64 || (height != 64 && height != 32)) {
            return "Image must be 64x64 (or legacy 64x32) - got " + width + "x" + height + ".";
        }

        try {
            Files.createDirectories(CONFIG_DIR);
            Files.write(SKIN_FILE, bytes);
        } catch (IOException e) {
            return "Could not save the skin file.";
        }

        skinBytes = bytes;
        return null;
    }

    public static void clearSkin() {
        skinBytes = null;
        try {
            Files.deleteIfExists(SKIN_FILE);
        } catch (IOException ignored) {
        }
    }
}