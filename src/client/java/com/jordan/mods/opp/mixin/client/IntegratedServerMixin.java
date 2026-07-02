package com.jordan.mods.opp.mixin.client;

import com.jordan.mods.opp.client.OppState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

/**
 * When "Open to LAN" is triggered:
 *  - forces online-mode off if the Offline Mode toggle is on (default: on)
 *  - follows up the vanilla "hosted on port X" chat line with a clearer
 *    "Connect on <ip>[:port]" message
 *  - if no local network IP can be found, sends a chat message explaining
 *    that a Wi-Fi connection is required, with a clickable link to the
 *    tutorial screen
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    private static final int DEFAULT_PORT = 25565;

    @Inject(method = "openToLan", at = @At("RETURN"))
    private void opp$forceOfflineModeAndAnnounce(GameMode gameMode, boolean cheatsAllowed, int port, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        if (OppState.offlineModeEnabled) {
            ((MinecraftServerAccessor) (Object) this).opp$setOnlineMode(false);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player == null) {
                return;
            }

            String ip = opp$findLocalIp();

            if (ip == null) {
                MutableText message = Text.literal("You don't seem to be connected to a Wi-Fi network right now. That's required for friends to connect - ")
                        .append(Text.literal("[Open Tutorial]")
                                .styled(style -> style
                                        .withColor(Formatting.AQUA)
                                        .withUnderline(true)
                                        .withClickEvent(new ClickEvent.RunCommand("/opptutorial"))));
                client.player.sendMessage(message, false);
                return;
            }

            String connectString = (port == DEFAULT_PORT) ? ip : (ip + ":" + port);
            client.player.sendMessage(Text.literal("Connect on " + connectString), false);
        });
    }

    /**
     * Finds the machine's LAN-facing IPv4 address (e.g. 192.168.x.x), skipping
     * loopback and virtual interfaces. Returns null if none is found.
     */
    private static String opp$findLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                for (InetAddress address : Collections.list(addresses)) {
                    if (address.isLoopbackAddress() || address.getHostAddress().contains(":")) {
                        continue; // skip loopback and IPv6
                    }
                    if (address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}