package com.jordan.mods.opp.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The wardrobe's preloaded cape list - same pattern as {@link OppPresetSkins},
 * but for capes. Each preset is a small bundled PNG under
 * assets/opp_v1/textures/capes/, read as raw bytes on first use and cached
 * from then on (they never change at runtime).
 *
 * This is currently just the framework: PRESETS is empty. To add a cape,
 * drop a PNG (64x32, or the "double height" 64x64 layout) at
 * assets/opp_v1/textures/capes/<id>.png and add a matching
 * {@code new Preset("<id>", "<Display Name>")} entry below - the wardrobe's
 * "Preloaded Capes" section picks it up automatically.
 */
public final class OppPresetCapes {

    public record Preset(String id, String displayName) {
    }

    // id must match the PNG filename (without extension) under
    // assets/opp_v1/textures/capes/. Capes have no slim/wide model split.
    private static final List<Preset> PRESETS = List.of(
            new Preset("blue_heart", "Blue Heart"),
            new Preset("hardcore_heart", "Hardcore Heart"),
            new Preset("blue_fire", "Blue Fire"),
            new Preset("luna", "Luna"),
            new Preset("duck", "Duck"),
            new Preset("pink_cat", "Pink Cat"),
            // file renamed from yin&yang.png -> yin_yang.png ('&' isn't a
            // legal Identifier character, so it can never be looked up)
            new Preset("yin_yang", "Yin & Yang"),
            // file renamed from Question_Mark.png -> question_mark.png
            // (Identifier paths must be all-lowercase)
            new Preset("question_mark", "?"),
            new Preset("dark_heart", "Dark Heart"),
            new Preset("galaxy", "Galaxy"),
            new Preset("pink_petals", "Pink Petals"),
            new Preset("white_to_black", "White To Black"),
            new Preset("sword", "Sword"),
            new Preset("blue_axolotl", "Blue Axolotl"),
            new Preset("skulk", "Skulk"),
            new Preset("cyberpunk_creeper", "Cyberpunk Creeper"),
            new Preset("gradient", "Gradient"),
            new Preset("smiley", "Smiley")
    );

    private static final Map<String, byte[]> CACHE = new HashMap<>();

    private OppPresetCapes() {
    }

    public static List<Preset> all() {
        return PRESETS;
    }

    /**
     * Reads (and caches) the PNG bytes for a preset cape from mod
     * resources. Returns null if the resource is missing, which callers
     * should treat as "skip this preset" rather than crash the wardrobe
     * screen.
     */
    public static byte[] getBytes(Preset preset) {
        return CACHE.computeIfAbsent(preset.id(), id -> {
            try {
                Identifier resource = Identifier.of("opp_v1", "textures/capes/" + id + ".png");
                try (InputStream stream = openResource(resource)) {
                    if (stream == null) {
                        return null;
                    }
                    return stream.readAllBytes();
                }
            } catch (IOException | IllegalArgumentException e) {
                // IllegalArgumentException covers Identifier.of() rejecting
                // an id with characters that aren't valid in a resource path
                // (uppercase letters, '&', etc.) - treat that the same as a
                // missing resource rather than letting it blow up the whole
                // preloaded-capes list.
                return null;
            }
        });
    }

    private static InputStream openResource(Identifier id) throws IOException {
        // Same classpath-direct approach as OppPresetSkins#openResource -
        // works identically in dev and in a built jar, and doesn't need a
        // live ResourceManager (the wardrobe may be opened before any
        // world/resource reload has happened, e.g. from the title screen).
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        return FabricLoader.getInstance().getModContainer("opp_v1")
                .map(container -> container.findPath(path).orElse(null))
                .map(p -> {
                    try {
                        return (InputStream) java.nio.file.Files.newInputStream(p);
                    } catch (IOException e) {
                        return null;
                    }
                })
                .orElse(null);
    }
}