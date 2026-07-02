package com.jordan.mods.opp.client;

/**
 * Small shared client-side state. The Open-To-Lan screen writes to this,
 * and IntegratedServerMixin reads from it when the world is actually opened.
 */
public final class OppState {

    // Default ON: this mod's whole point is offline play.
    public static volatile boolean offlineModeEnabled = true;

    private OppState() {
    }
}