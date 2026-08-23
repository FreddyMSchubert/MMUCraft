package uk.co.httpsmmuminecraftsociety.mainmod.metrics;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import net.minecraft.server.MinecraftServer;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class MetricsServer {
    private static final int PORT = 9100;
    private static final Gauge ONLINE_PLAYERS = Gauge.builder()
            .name("mainmod_online_players")
            .help("Players currently connected to the Minecraft server.")
            .register();
    private static HTTPServer server;

    private MetricsServer() {}

    public static void start(MinecraftServer minecraftServer) {
        update(minecraftServer);

        try {
            server = HTTPServer.builder().port(PORT).buildAndStart();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not start Prometheus metrics server", exception);
        }
        MainMod.LOGGER.info("Prometheus metrics listening on port {}", PORT);
    }

    public static void update(MinecraftServer minecraftServer) {
        ONLINE_PLAYERS.set(minecraftServer.getPlayerList().getPlayerCount());
    }

    public static void stop() {
        if (server != null) {
            server.close();
            server = null;
        }
    }
}
