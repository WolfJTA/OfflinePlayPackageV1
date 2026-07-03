package com.jordan.mods.opp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerSkinManager {

    /** A skin as held server-side: the raw PNG plus its arm/model type. */
    public record SkinData(byte[] png, boolean slim) {}

    @FunctionalInterface
    public interface SkinConsumer {
        void accept(UUID uuid, byte[] png, boolean slim);
    }

    private static final Map<UUID, SkinData> SKINS = new ConcurrentHashMap<>();
    private static Path skinDir;

    private ServerSkinManager() {}

    public static void init(MinecraftServer server) {
        skinDir = server.getSavePath(WorldSavePath.ROOT)
                .resolve("serverconfig").resolve("opp_v1").resolve("skins");
        try {
            Files.createDirectories(skinDir);
            try (var stream = Files.list(skinDir)) {
                stream.filter(p -> p.toString().endsWith(".png")).forEach(p -> {
                    try {
                        String name = p.getFileName().toString();
                        UUID uuid = UUID.fromString(name.substring(0, name.length() - 4));
                        byte[] png = Files.readAllBytes(p);
                        boolean slim = Files.exists(skinDir.resolve(uuid + ".slim"));
                        SKINS.put(uuid, new SkinData(png, slim));
                    } catch (Exception ignored) {
                        // not a uuid-named file, skip
                    }
                });
            }
        } catch (IOException e) {
            skinDir = null;
        }
    }

    /**
     * Validates the PNG (proper format, 64x64 or legacy 64x32) before
     * storing or persisting it. A client sending something malformed - by
     * mistake or otherwise - gets rejected here instead of that garbage
     * getting written to disk and broadcast to every other player.
     *
     * @return true if the skin was accepted and stored, false if it failed validation.
     */
    public static boolean storeSkin(UUID uuid, byte[] pngBytes, boolean slim) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        } catch (IOException e) {
            image = null;
        }

        if (image == null) {
            return false;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width != 64 || (height != 64 && height != 32)) {
            return false;
        }

        SKINS.put(uuid, new SkinData(pngBytes, slim));
        if (skinDir != null) {
            try {
                Files.write(skinDir.resolve(uuid + ".png"), pngBytes);
                Path slimMarker = skinDir.resolve(uuid + ".slim");
                if (slim) {
                    Files.write(slimMarker, new byte[0]);
                } else {
                    Files.deleteIfExists(slimMarker);
                }
            } catch (IOException ignored) {
            }
        }
        return true;
    }

    public static byte[] getSkin(UUID uuid) {
        SkinData data = SKINS.get(uuid);
        return data != null ? data.png() : null;
    }

    public static boolean isSlim(UUID uuid) {
        SkinData data = SKINS.get(uuid);
        return data != null && data.slim();
    }

    public static boolean hasSkin(UUID uuid) {
        return SKINS.containsKey(uuid);
    }

    public static void clear() {
        SKINS.clear();
        skinDir = null;
    }

    public static void forEachSkin(SkinConsumer consumer) {
        SKINS.forEach((uuid, data) -> consumer.accept(uuid, data.png(), data.slim()));
    }
}