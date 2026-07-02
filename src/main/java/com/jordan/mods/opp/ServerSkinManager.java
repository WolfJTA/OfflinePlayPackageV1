package com.jordan.mods.opp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerSkinManager {

    private static final Map<UUID, byte[]> SKINS = new ConcurrentHashMap<>();
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
                        SKINS.put(uuid, Files.readAllBytes(p));
                    } catch (Exception ignored) {
                        // not a uuid-named file, skip
                    }
                });
            }
        } catch (IOException e) {
            skinDir = null;
        }
    }

    public static void storeSkin(UUID uuid, byte[] pngBytes) {
        SKINS.put(uuid, pngBytes);
        if (skinDir != null) {
            try {
                Files.write(skinDir.resolve(uuid + ".png"), pngBytes);
            } catch (IOException ignored) {
            }
        }
    }

    public static byte[] getSkin(UUID uuid) {
        return SKINS.get(uuid);
    }

    public static boolean hasSkin(UUID uuid) {
        return SKINS.containsKey(uuid);
    }

    public static void clear() {
        SKINS.clear();
        skinDir = null;
    }
    public static void forEachSkin(java.util.function.BiConsumer<UUID, byte[]> consumer) {
        SKINS.forEach(consumer);
    }
}