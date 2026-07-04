package com.jordan.mods.opp.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The wardrobe's preloaded skin list. Each preset is a small bundled PNG
 * under assets/opp_v1/textures/skins/, read as raw bytes on first use and
 * cached from then on (they never change at runtime).
 *
 * NOTE ON THE PRESET ART: these are original, simple flat-color placeholder
 * skins - not attempts to reproduce anyone's actual, specific artwork. A
 * few of the requested names (Dream, Techno, Purpled, Boy Dream) are real
 * people's personal/branded skins, and "Mcdonalds" references a
 * trademarked logo/uniform; those five are intentionally generic
 * recolors rather than replicas of the real thing, to avoid reproducing
 * someone else's likeness or trademark. Swap the corresponding PNG under
 * assets/opp_v1/textures/skins/ with properly licensed art any time.
 */
public final class OppPresetSkins {

    public record Preset(String id, String displayName, boolean slim) {
    }

    // id must match the PNG filename (without extension) under
    // assets/opp_v1/textures/skins/. slim=true uses the narrow "Alex" arms.
    private static final List<Preset> PRESETS = List.of(
            new Preset("noob", "Noob", false),
            new Preset("dream", "Dream", false),
            new Preset("herobrine", "Herobrine", false),
            new Preset("techno", "Techno", false),
            new Preset("fish", "Fish", false),
            new Preset("flower_girl", "Flower Girl", true),
            new Preset("pink_girl", "Pink Girl", true),
            new Preset("girl", "Girl", true),
            new Preset("straight", "Straight", false),
            new Preset("detective", "Detective", false),
            new Preset("axolotl_girl", "Axolotl Girl", true),
            new Preset("evil_king", "Evil King", false),
            new Preset("warden", "Warden", false),
            new Preset("mcdonalds", "Mcdonalds", false),
            new Preset("boy_dream", "Boy Dream", false),
            new Preset("banana", "Banana", false),
            new Preset("purpled", "Purpled", false),
            new Preset("gradient", "Gradient", false),
            new Preset("dog", "Dog", false),

            // Vanilla Minecraft's own nine default skins. Model (slim/wide)
            // per Mojang's own assignment: Ari, Kai, Steve, Sunny, and Zuri
            // are wide; Alex, Efe, Makena, and Noor are slim.
            new Preset("steve", "Steve", false),
            new Preset("alex", "Alex", true),
            new Preset("sunny", "Sunny", false),
            new Preset("kai", "Kai", false),
            new Preset("makena", "Makena", true),
            new Preset("noor", "Noor", true),
            new Preset("ari", "Ari", false),
            new Preset("zuri", "Zuri", false),
            new Preset("efe", "Efe", true),

            new Preset("frog_hat_guy", "Frog Hat Guy", false)
    );

    private static final java.util.Map<String, byte[]> CACHE = new java.util.HashMap<>();

    private OppPresetSkins() {
    }

    public static List<Preset> all() {
        return PRESETS;
    }

    /**
     * Reads (and caches) the PNG bytes for a preset from mod resources.
     * Returns null if the resource is missing, which callers should treat
     * as "skip this preset" rather than crash the wardrobe screen.
     */
    public static byte[] getBytes(Preset preset) {
        return CACHE.computeIfAbsent(preset.id(), id -> {
            Identifier resource = Identifier.of("opp_v1", "textures/skins/" + id + ".png");
            try (InputStream stream = openResource(resource)) {
                if (stream == null) {
                    return null;
                }
                return stream.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        });
    }

    private static InputStream openResource(Identifier id) throws IOException {
        // Mod resources ship inside the mod jar and are exposed via the
        // resource manager at runtime; FabricLoader also exposes raw
        // classpath access which works identically in dev and in a built
        // jar, so we use that directly rather than needing a live
        // ResourceManager (the wardrobe screen may be opened before any
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

    /** Convenience list of just the built-in preset caps we ship (currently one). */
    public static byte[] getDefaultCapeBytes() {
        return CACHE.computeIfAbsent("__default_cape__", ignored -> {
            try (InputStream stream = openCapeResource()) {
                if (stream == null) {
                    return null;
                }
                return stream.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        });
    }

    private static InputStream openCapeResource() throws IOException {
        String path = "assets/opp_v1/textures/capes/default_cape.png";
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