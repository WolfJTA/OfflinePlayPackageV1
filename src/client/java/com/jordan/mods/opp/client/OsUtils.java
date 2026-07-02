package com.jordan.mods.opp.client;

/**
 * Minimal OS detection + best-effort "open network settings" helper.
 * Windows and macOS have a reliable single command/URI for this.
 * Linux does not (too many desktop environments), so we just surface
 * manual instructions instead of guessing.
 */
public final class OsUtils {

    public enum Os {
        WINDOWS, MACOS, LINUX, OTHER
    }

    public static final Os CURRENT = detect();

    private static Os detect() {
        String name = System.getProperty("os.name", "").toLowerCase();
        if (name.contains("win")) return Os.WINDOWS;
        if (name.contains("mac") || name.contains("darwin")) return Os.MACOS;
        if (name.contains("nux") || name.contains("nix")) return Os.LINUX;
        return Os.OTHER;
    }

    /**
     * Attempts to open the OS's network settings page directly.
     * Returns true if a launch command was issued, false if this OS
     * isn't supported (caller should fall back to manual instructions).
     */
    public static boolean tryOpenNetworkSettings() {
        try {
            switch (CURRENT) {
                case WINDOWS -> {
                    new ProcessBuilder("cmd", "/c", "start", "ms-settings:network-wifisettings").start();
                    return true;
                }
                case MACOS -> {
                    new ProcessBuilder("open", "x-apple.systempreferences:com.apple.preference.network").start();
                    return true;
                }
                default -> {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Human-readable manual instructions, tailored per-OS where we can't
     * (or the launch attempt failed to) open settings directly.
     */
    public static String getManualInstructions() {
        return switch (CURRENT) {
            case WINDOWS -> "Settings > Network & Internet > Wi-Fi > (your hotspot) > set to Private.";
            case MACOS -> "System Settings > Network > Wi-Fi > Details > set the network as Trusted, and allow it through the Firewall in System Settings > Network > Firewall.";
            case LINUX -> "Open your network manager (e.g. GNOME Settings > Wi-Fi > gear icon, or nm-connection-editor) and set the connection's Firewall Zone / Sharing profile to Home or Trusted, not Public.";
            case OTHER -> "Find your Wi-Fi network's settings and set its profile to Private/Home/Trusted rather than Public.";
        };
    }
}