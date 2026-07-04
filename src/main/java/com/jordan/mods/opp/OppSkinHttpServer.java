package com.jordan.mods.opp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * A tiny HTTP server that serves stored offline skins (and capes) as plain
 * PNGs, so they can be referenced by URL from a player's GameProfile
 * "textures" property. This is what makes skins/capes visible to vanilla
 * (non-modded) clients too - they read that property the same way they'd
 * read a real Mojang-hosted skin/cape URL, no mod required on their end.
 *
 * Binds to an OS-assigned free port on all interfaces (0.0.0.0) so it
 * never collides with the game's own port, then figures out the LAN IP
 * the same way "Connect on ..." chat message does.
 */
public final class OppSkinHttpServer {

    private static HttpServer server;
    private static String host;
    private static int port = -1;

    private OppSkinHttpServer() {}

    public static void start(MinecraftServer mcServer) {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "opp-skin-http");
                t.setDaemon(true);
                return t;
            }));
            server.createContext("/skins/", exchange -> handle(exchange, false));
            server.createContext("/capes/", exchange -> handle(exchange, true));
            server.start();
            port = server.getAddress().getPort();
            host = findLocalIp();
        } catch (IOException e) {
            server = null;
            port = -1;
            host = null;
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        port = -1;
        host = null;
    }

    /** Full URL for a player's skin, or null if the server isn't up / no LAN IP was found. */
    public static String buildSkinUrl(UUID uuid) {
        if (server == null || host == null) {
            return null;
        }
        return "http://" + host + ":" + port + "/skins/" + uuid + ".png";
    }

    /** Full URL for a player's cape, or null if the server isn't up / no LAN IP was found. */
    public static String buildCapeUrl(UUID uuid) {
        if (server == null || host == null) {
            return null;
        }
        return "http://" + host + ":" + port + "/capes/" + uuid + ".png";
    }

    private static void handle(HttpExchange exchange, boolean isCape) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (!fileName.endsWith(".png")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(fileName.substring(0, fileName.length() - 4));
            } catch (IllegalArgumentException e) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            byte[] png = isCape ? ServerSkinManager.getCape(uuid) : ServerSkinManager.getSkin(uuid);
            if (png == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, png.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(png);
            }
        } finally {
            exchange.close();
        }
    }

    /** Finds the machine's LAN-facing IPv4 address, skipping loopback/virtual interfaces. */
    private static String findLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(iface.getInetAddresses())) {
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