package uk.co.httpsmmuminecraftsociety.mainmod.metrics;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyCharm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MetricsServer {
    private static final int PORT = 9100;
    private static final Gauge ONLINE_PLAYERS = Gauge.builder()
            .name("mainmod_online_players")
            .help("Players currently connected to the Minecraft server.")
            .register();
    private static final Gauge PEAK_ONLINE_PLAYERS = gauge(
            "mainmod_peak_online_players", "Highest concurrent player count ever recorded.");
    private static final Gauge TICKS_PER_SECOND = gauge(
            "mainmod_ticks_per_second", "Estimated Minecraft server ticks per second.");
    private static final Gauge MILLISECONDS_PER_TICK = gauge(
            "mainmod_milliseconds_per_tick", "Average Minecraft server tick duration in milliseconds.");
    private static final Gauge LOADED_CHUNKS = gauge(
            "mainmod_loaded_chunks", "Chunks currently loaded across all dimensions.");
    private static final Gauge ENTITIES = gauge(
            "mainmod_entities", "Entities currently loaded across all dimensions.");
    private static final Gauge UPTIME_SECONDS = gauge(
            "mainmod_uptime_seconds", "Seconds since the Minecraft server started.");
    private static final Counter PLAYER_JOINS = Counter.builder()
            .name("mainmod_player_joins_total")
            .help("Player joins since the Minecraft server process started.")
            .labelNames("kind")
            .register();
    private static final Counter POTIONS_USED = Counter.builder()
            .name("mainmod_potions_used_total")
            .help("Successfully used MMUCraft potions by type.")
            .labelNames("potion")
            .register();
    private static HTTPServer server;
    private static Path peakFile;
    private static int peakOnlinePlayers;
    private static long startedAtNanos;
    private static long nextUpdateNanos;

    private MetricsServer() {}

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraftServer) ->
                PLAYER_JOINS.labelValues(
                        handler.player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) == 0
                                ? "new" : "returning"
                ).inc());
    }

    public static void start(MinecraftServer minecraftServer) {
        JvmMetrics.builder().register();
        startedAtNanos = System.nanoTime();
        nextUpdateNanos = 0;
        peakFile = minecraftServer.getWorldPath(LevelResource.ROOT).resolve("mainmod-peak-players.txt");
        peakOnlinePlayers = readPeak();
        update(minecraftServer);

        try {
            server = HTTPServer.builder().port(PORT).buildAndStart();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not start Prometheus metrics server", exception);
        }
        MainMod.LOGGER.info("Prometheus metrics listening on port {}", PORT);
    }

    public static void update(MinecraftServer minecraftServer) {
        long now = System.nanoTime();
        if (now < nextUpdateNanos) return;
        nextUpdateNanos = now + 1_000_000_000L;

        int onlinePlayers = minecraftServer.getPlayerList().getPlayerCount();
        ONLINE_PLAYERS.set(onlinePlayers);
        if (onlinePlayers > peakOnlinePlayers) {
            peakOnlinePlayers = onlinePlayers;
            writePeak();
        }
        PEAK_ONLINE_PLAYERS.set(peakOnlinePlayers);

        double millisecondsPerTick = minecraftServer.getAverageTickTimeNanos() / 1_000_000.0;
        MILLISECONDS_PER_TICK.set(millisecondsPerTick);
        TICKS_PER_SECOND.set(Math.min(20.0, 1_000.0 / Math.max(1.0, millisecondsPerTick)));
        int loadedChunks = 0;
        int entities = 0;
        for (ServerLevel level : minecraftServer.getAllLevels()) {
            loadedChunks += level.getChunkSource().getLoadedChunksCount();
            // ponytail: these mappings expose no constant-time entity count; maintain an event counter only if this one-second scan becomes measurable.
            for (Entity ignored : level.getAllEntities()) entities++;
        }
        LOADED_CHUNKS.set(loadedChunks);
        ENTITIES.set(entities);
        UPTIME_SECONDS.set((now - startedAtNanos) / 1_000_000_000.0);
    }

    public static void stop() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    public static void recordPotionUse(DailyCharm charm) {
        POTIONS_USED.labelValues(charm.name().toLowerCase()).inc();
    }

    private static Gauge gauge(String name, String help) {
        return Gauge.builder().name(name).help(help).register();
    }

    private static int readPeak() {
        if (peakFile == null || !Files.exists(peakFile)) return 0;
        try {
            return Math.max(0, Integer.parseInt(Files.readString(peakFile).trim()));
        } catch (IOException | NumberFormatException exception) {
            MainMod.LOGGER.warn("Could not read persisted peak player count", exception);
            return 0;
        }
    }

    private static void writePeak() {
        try {
            Files.writeString(peakFile, Integer.toString(peakOnlinePlayers));
        } catch (IOException exception) {
            MainMod.LOGGER.warn("Could not persist peak player count", exception);
        }
    }
}
