package com.jordan.mods.opp.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Remembers every custom skin the player has loaded through the file
 * browser/path field (OppSkinScreen's "Load Skin"), so the wardrobe can
 * show a "Your Skins" section on top of the preloaded/Mojang-default
 * sections without the player having to re-browse for a file they already
 * used once. Purely local bookkeeping - has no effect on what actually
 * gets uploaded/applied, which is still just whatever's in OppSkinManager.
 *
 * Persisted as one small PNG per entry (named by a content hash, so
 * loading the exact same file twice just bumps it to the top instead of
 * creating a duplicate) plus a manifest text file recording the
 * hash/display-name/model-type for each, most-recent-first.
 */
public final class OppSkinHistory {

    public record Entry(String hash, String displayName, boolean slim) {
    }

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("opp_v1");
    private static final Path HISTORY_DIR = CONFIG_DIR.resolve("history");
    private static final Path MANIFEST_FILE = HISTORY_DIR.resolve("history.txt");
    private static final int MAX_ENTRIES = 25;

    private static List<Entry> entries = new ArrayList<>();
    private static boolean loaded = false;

    private OppSkinHistory() {
    }

    /** Lazily loads the manifest on first use; safe to call repeatedly. */
    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        entries = new ArrayList<>();
        if (!Files.exists(MANIFEST_FILE)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(MANIFEST_FILE, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", 3);
                if (parts.length != 3) {
                    continue;
                }
                String hash = parts[0];
                boolean slim = "1".equals(parts[1]);
                String displayName = parts[2];
                if (Files.exists(HISTORY_DIR.resolve(hash + ".png"))) {
                    entries.add(new Entry(hash, displayName, slim));
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static List<Entry> getAll() {
        ensureLoaded();
        return List.copyOf(entries);
    }

    public static byte[] getBytes(String hash) {
        try {
            return Files.readAllBytes(HISTORY_DIR.resolve(hash + ".png"));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Records (or "touches" - moves to the top if already present) a
     * successfully loaded custom skin. Safe to call for every successful
     * skin load; dedupes by content hash so re-loading the same file
     * doesn't pile up duplicate entries.
     */
    public static void record(String displayName, byte[] pngBytes, boolean slim) {
        ensureLoaded();

        String hash = sha1Hex(pngBytes);
        String safeName = sanitize(displayName);

        try {
            Files.createDirectories(HISTORY_DIR);
            Files.write(HISTORY_DIR.resolve(hash + ".png"), pngBytes);
        } catch (IOException e) {
            return; // couldn't persist it - don't add a manifest entry we can't back
        }

        entries.removeIf(e -> e.hash().equals(hash));
        entries.add(0, new Entry(hash, safeName, slim));
        while (entries.size() > MAX_ENTRIES) {
            Entry removed = entries.remove(entries.size() - 1);
            try {
                Files.deleteIfExists(HISTORY_DIR.resolve(removed.hash() + ".png"));
            } catch (IOException ignored) {
            }
        }

        persist();
    }

    private static void persist() {
        try {
            Files.createDirectories(HISTORY_DIR);
            StringBuilder sb = new StringBuilder();
            for (Entry e : entries) {
                sb.append(e.hash()).append('|').append(e.slim() ? '1' : '0').append('|').append(e.displayName()).append('\n');
            }
            Files.writeString(MANIFEST_FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String sanitize(String name) {
        String cleaned = name.replace("|", "_").replace("\n", " ").replace("\r", " ").trim();
        return cleaned.isEmpty() ? "Custom Skin" : cleaned;
    }

    private static String sha1Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is always available on the standard JDK providers; this is unreachable in practice.
            return Integer.toHexString(java.util.Arrays.hashCode(bytes));
        }
    }
}
