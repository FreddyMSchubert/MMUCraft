package dev.freddy.chunklocked;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

final class EventCountdown {
    private static final String BASE_URL = System.getenv("SURPRISING_SATURDAY_API_URL");
    private static final String SECRET = System.getenv("SURPRISING_SATURDAY_API_SECRET");
    private static final int[] MINUTE_WARNINGS = {120, 60, 30, 20, 10, 5, 3};
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "chunklock-countdown");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile long endsAt = -1;
    private static long previousEnd = -1;
    private static long previousSeconds = Long.MAX_VALUE;
    private static boolean pollFailed;

    private EventCountdown() { }

    static void register() {
        if (BASE_URL == null || SECRET == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                WORKER.scheduleWithFixedDelay(EventCountdown::poll, 0, 1, TimeUnit.SECONDS));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORKER.shutdownNow());
        ServerTickEvents.END_SERVER_TICK.register(EventCountdown::tick);
    }

    private static void poll() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL
                            + "/api/internal/velocity/event-control"))
                    .timeout(Duration.ofSeconds(5))
                    .header("authorization", "Bearer " + SECRET)
                    .GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            var end = JsonParser.parseString(response.body()).getAsJsonObject().get("endsAtUnixMs");
            endsAt = end == null || end.isJsonNull() ? -1 : end.getAsLong();
            pollFailed = false;
        } catch (Exception error) {
            endsAt = -1;
            if (!pollFailed) System.err.println("Chunklock countdown could not read event time: " + error);
            pollFailed = true;
        }
    }

    private static void tick(MinecraftServer server) {
        long end = endsAt;
        if (end < 0) {
            previousEnd = -1;
            return;
        }
        long seconds = Math.max(0, (end - System.currentTimeMillis() + 999) / 1000);
        if (end != previousEnd) {
            previousEnd = end;
            previousSeconds = seconds + 1;
        }
        if (seconds <= 0 || seconds == previousSeconds) return;
        if (seconds <= 60 && previousSeconds > seconds) {
            announce(server, seconds == 60 ? "1 minute"
                    : seconds + " second" + (seconds == 1 ? "" : "s"));
        } else {
            for (int minutes : MINUTE_WARNINGS) {
                if (previousSeconds > minutes * 60L && seconds <= minutes * 60L) {
                    announce(server, minutes == 120 ? "2 hours"
                            : minutes == 60 ? "1 hour" : minutes + " minutes");
                    break;
                }
            }
        }
        previousSeconds = seconds;
    }

    private static void announce(MinecraftServer server, String remaining) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Chunk Locked ends in " + remaining + "."), false);
    }
}
