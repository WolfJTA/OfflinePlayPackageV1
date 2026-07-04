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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerSkinManager {

    /** A skin as held server-side: the raw PNG, arm/model type, and an optional cape PNG. */
    public record SkinData(byte[] png, boolean slim, byte[] capePng) {}

    @FunctionalInterface
    public interface SkinConsumer {
        void accept(UUID uuid, byte[] png, boolean slim, byte[] capePng);
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
                stream.filter(p -> p.toString().endsWith(".png") && !p.toString().endsWith(".cape.png")).forEach(p -> {
                    try {
                        String name = p.getFileName().toString();
                        UUID uuid = UUID.fromString(name.substring(0, name.length() - 4));
                        byte[] png = Files.readAllBytes(p);
                        boolean slim = Files.exists(skinDir.resolve(uuid + ".slim"));
                        Path capePath = skinDir.resolve(uuid + ".cape.png");
                        byte[] cape = Files.exists(capePath) ? Files.readAllBytes(capePath) : null;
                        SKINS.put(uuid, new SkinData(png, slim, cape));
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
     * Validates the skin PNG (proper format, 64x64 or legacy 64x32) and, if
     * present, the cape PNG (64x32) before storing or persisting either. A
     * client sending something malformed - by mistake or otherwise - gets
     * rejected here instead of that garbage getting written to disk and
     * broadcast to every other player. A malformed cape only rejects the
     * cape, not the whole upload - the skin itself still applies.
     *
     * @return true if the skin was accepted and stored, false if it failed validation.
     */
    public static boolean storeSkin(UUID uuid, byte[] pngBytes, boolean slim, Optional<byte[]> capePngOpt) {
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

        byte[] validCape = null;
        if (capePngOpt != null && capePngOpt.isPresent()) {
            byte[] capeBytes = capePngOpt.get();
            BufferedImage capeImage;
            try {
                capeImage = ImageIO.read(new ByteArrayInputStream(capeBytes));
            } catch (IOException e) {
                capeImage = null;
            }
            if (capeImage != null && capeImage.getWidth() == 64
                    && (capeImage.getHeight() == 32 || capeImage.getHeight() == 64)) {
                validCape = capeBytes;
            }
        }

        SKINS.put(uuid, new SkinData(pngBytes, slim, validCape));
        if (skinDir != null) {
            try {
                Files.write(skinDir.resolve(uuid + ".png"), pngBytes);
                Path slimMarker = skinDir.resolve(uuid + ".slim");
                if (slim) {
                    Files.write(slimMarker, new byte[0]);
                } else {
                    Files.deleteIfExists(slimMarker);
                }

                Path capePath = skinDir.resolve(uuid + ".cape.png");
                if (validCape != null) {
                    Files.write(capePath, validCape);
                } else {
                    Files.deleteIfExists(capePath);
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

    public static byte[] getCape(UUID uuid) {
        SkinData data = SKINS.get(uuid);
        return data != null ? data.capePng() : null;
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
        SKINS.forEach((uuid, data) -> consumer.accept(uuid, data.png(), data.slim(), data.capePng()));
    }
}