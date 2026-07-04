package com.jordan.mods.opp.client;

import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores the player's chosen offline skin locally (a raw 64x64 or 64x32
 * skin PNG) and persists it to disk so it's remembered between sessions.
 * Also tracks the arm/model type (slim "Alex" vs classic "Steve") since
 * that isn't something we can reliably detect from the PNG itself.
 *
 * Also stores an optional cape PNG (64x32) the same way - entirely
 * separate from the skin, so clearing/changing one never touches the other.
 */
public final class OppSkinManager {

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("opp_v1");
    private static final Path SKIN_FILE = CONFIG_DIR.resolve("offline_skin.png");
    private static final Path MODEL_FILE = CONFIG_DIR.resolve("offline_skin_model.txt");
    private static final Path CAPE_FILE = CONFIG_DIR.resolve("offline_cape.png");

    private static byte[] skinBytes = null;
    private static boolean slimModel = false;
    private static byte[] capeBytes = null;

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

        try {
            if (Files.exists(MODEL_FILE)) {
                String content = Files.readString(MODEL_FILE, StandardCharsets.UTF_8).trim();
                slimModel = "slim".equalsIgnoreCase(content);
            }
        } catch (IOException e) {
            slimModel = false;
        }

        try {
            if (Files.exists(CAPE_FILE)) {
                capeBytes = Files.readAllBytes(CAPE_FILE);
            }
        } catch (IOException e) {
            capeBytes = null;
        }
    }

    public static boolean hasCustomSkin() {
        return skinBytes != null;
    }

    public static byte[] getSkinBytes() {
        return skinBytes;
    }

    public static boolean isSlimModel() {
        return slimModel;
    }

    public static boolean hasCape() {
        return capeBytes != null;
    }

    public static byte[] getCapeBytes() {
        return capeBytes;
    }

    /**
     * Sets the arm/model type for the current skin and persists it. Safe to
     * call regardless of whether a skin is currently loaded - it'll simply
     * apply to whatever skin gets loaded/uploaded next.
     */
    public static void setSlimModel(boolean slim) {
        slimModel = slim;
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(MODEL_FILE, slim ? "slim" : "classic", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
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

        return trySetSkinBytes(bytes);
    }

    /**
     * Same validation/persistence as {@link #trySetSkin(String)}, but takes
     * the PNG bytes directly instead of a file path. Used both by the file
     * path/browse flow (after reading the file) and by the wardrobe's
     * bundled preset skins, which are read from mod resources rather than
     * an arbitrary path on disk.
     *
     * @return null on success, or a human-readable error message on failure.
     */
    public static String trySetSkinBytes(byte[] bytes) {
        if (bytes == null) {
            return "No skin data given.";
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
        slimModel = false;
        try {
            Files.deleteIfExists(SKIN_FILE);
            Files.deleteIfExists(MODEL_FILE);
        } catch (IOException ignored) {
        }
    }

    /**
     * Attempts to load a PNG from the given absolute path as a cape (64x32).
     *
     * @return null on success, or a human-readable error message on failure.
     */
    public static String trySetCape(String rawPath) {
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

        return trySetCapeBytes(bytes);
    }

    /**
     * Same as {@link #trySetCape(String)} but takes PNG bytes directly -
     * used by the wardrobe's bundled preset cape(s).
     *
     * @return null on success, or a human-readable error message on failure.
     */
    public static String trySetCapeBytes(byte[] bytes) {
        if (bytes == null) {
            return "No cape data given.";
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
        // Capes are traditionally 64x32, but Mojang's newer "double height"
        // cape sheets (64x64) exist too, so accept both.
        if (width != 64 || (height != 32 && height != 64)) {
            return "Cape image must be 64x32 - got " + width + "x" + height + ".";
        }

        try {
            Files.createDirectories(CONFIG_DIR);
            Files.write(CAPE_FILE, bytes);
        } catch (IOException e) {
            return "Could not save the cape file.";
        }

        capeBytes = bytes;
        return null;
    }

    public static void clearCape() {
        capeBytes = null;
        try {
            Files.deleteIfExists(CAPE_FILE);
        } catch (IOException ignored) {
        }
    }
}