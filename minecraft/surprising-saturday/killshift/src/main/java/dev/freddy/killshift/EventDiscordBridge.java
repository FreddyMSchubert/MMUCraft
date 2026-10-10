package dev.freddy.killshift;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class EventDiscordBridge {
    private static final String CHANNEL_ID = env("DISCORD_EVENT_CHANNEL_ID");
    private static final String WEBHOOK_URL = env("DISCORD_EVENT_WEBHOOK_URL");
    private static final String BOT_TOKEN = env("DISCORD_BOT_TOKEN");
    private static final String AVATAR_BASE_URL = env("DISCORD_AVATAR_BASE_URL").replaceAll("/+$", "");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "killshift-discord");
        thread.setDaemon(true);
        return thread;
    });
    private static String cursor;
    private static long pollAfter;
    private static volatile boolean enabled;

    private EventDiscordBridge() { }

    static void init() {
        if (CHANNEL_ID.isEmpty() && WEBHOOK_URL.isEmpty()) return;
        if (!CHANNEL_ID.matches("[0-9]{17,20}") || WEBHOOK_URL.isEmpty() || BOT_TOKEN.isEmpty()) {
            System.err.println("Killshift Discord disabled: set DISCORD_EVENT_CHANNEL_ID, "
                    + "DISCORD_EVENT_WEBHOOK_URL, and DISCORD_BOT_TOKEN");
            return;
        }
        URI webhook;
        try {
            webhook = URI.create(WEBHOOK_URL);
        } catch (IllegalArgumentException error) {
            System.err.println("Killshift Discord disabled: invalid webhook URL");
            return;
        }
        if (!"https".equals(webhook.getScheme()) || !"discord.com".equals(webhook.getHost())
                || !webhook.getPath().startsWith("/api/webhooks/")) {
            System.err.println("Killshift Discord disabled: invalid webhook URL");
            return;
        }
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, type) ->
                publish(sender, "chat", message.signedContent()));
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (overlay || !(message.getContents() instanceof TranslatableContents translated)
                    || !translated.getKey().startsWith("death.")) return;
            ServerPlayer player = server.getPlayerList().getPlayers().stream()
                    .filter(candidate -> candidate.getDisplayName().getString()
                            .equals(translated.getArgument(0).getString()))
                    .findFirst().orElse(null);
            if (player == null) return;
            String content = message.getString();
            String name = player.getDisplayName().getString();
            publish(player, "death", content.startsWith(name)
                    ? content.substring(name.length()).stripLeading() : content);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                publish(handler.getPlayer(), "leave", "left Killshift. ("
                        + onlineCount(server, handler.getPlayer()) + " online)"));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WORKER.scheduleWithFixedDelay(() -> {
                if (!enabled) enabled = verifyWebhookChannel();
            }, 0, 30, TimeUnit.SECONDS);
            WORKER.scheduleWithFixedDelay(() -> poll(server), 0, 2, TimeUnit.SECONDS);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            WORKER.shutdown();
            try {
                if (!WORKER.awaitTermination(5, TimeUnit.SECONDS)) WORKER.shutdownNow();
            } catch (InterruptedException error) {
                WORKER.shutdownNow();
                Thread.currentThread().interrupt();
            }
        });
    }

    private static boolean verifyWebhookChannel() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(WEBHOOK_URL))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && CHANNEL_ID.equals(JsonParser.parseString(response.body())
                    .getAsJsonObject().get("channel_id").getAsString())) return true;
            System.err.println("Killshift Discord disabled: event webhook does not match "
                    + "DISCORD_EVENT_CHANNEL_ID (HTTP " + response.statusCode() + ")");
        } catch (Exception error) {
            System.err.println("Killshift Discord disabled: could not check event webhook channel");
        }
        return false;
    }

    static void publishJoin(ServerPlayer player) {
        publish(player, "join", "joined Killshift. (" + onlineCount(player.level().getServer(), null) + " online)");
    }

    private static void publish(ServerPlayer player, String type, String content) {
        if (!enabled || WORKER.isShutdown() || content.isBlank()) return;
        EventApi.DiscordPresentation profile = EventApi.discordPresentation(player);
        JsonObject payload = new JsonObject();
        payload.addProperty("username", type.equals("chat")
                ? EventDiscordFormatting.chatUsername(player.getName().getString(), profile.role())
                : "Minecraft Server");
        payload.addProperty("content", EventDiscordFormatting.format(type, player.getName().getString(),
                profile.role(), profile.colorHex(), content));
        if (type.equals("chat") && !AVATAR_BASE_URL.isEmpty()) {
            payload.addProperty("avatar_url", AVATAR_BASE_URL + "/" + player.getUUID() + ".png?v="
                    + profile.colorHex().replace("#", "").toLowerCase());
        }
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        payload.add("allowed_mentions", mentions);
        WORKER.execute(() -> post(payload.toString(), false));
    }

    static void publishServer(String content) {
        if (!enabled || WORKER.isShutdown() || content.isBlank()) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "Minecraft Server");
        payload.addProperty("content", EventDiscordFormatting.format("server", "", "", "", content));
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        payload.add("allowed_mentions", mentions);
        WORKER.execute(() -> post(payload.toString(), false));
    }

    static void publishScore(ServerPlayer player, String mob, int score) {
        publish(player, "score", "killed " + mob + ". Score: " + score + ".");
    }

    private static void post(String body, boolean retried) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(WEBHOOK_URL
                            + (WEBHOOK_URL.contains("?") ? "&" : "?") + "wait=true"))
                    .timeout(Duration.ofSeconds(5))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 && !retried) {
                WORKER.schedule(() -> post(body, true), retryDelay(response.body()), TimeUnit.MILLISECONDS);
            } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("Killshift Discord webhook returned " + response.statusCode());
            }
        } catch (Exception error) {
            System.err.println("Killshift Discord webhook failed: " + error.getClass().getSimpleName());
        }
    }

    private static void poll(MinecraftServer server) {
        if (!enabled || System.currentTimeMillis() < pollAfter) return;
        try {
            String url = "https://discord.com/api/v10/channels/" + CHANNEL_ID + "/messages?limit="
                    + (cursor == null ? "1" : "100&after=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("authorization", "Bot " + BOT_TOKEN)
                    .header("user-agent", "Killshift-Discord-Bridge/1.0")
                    .GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                pollAfter = System.currentTimeMillis() + retryDelay(response.body());
                return;
            }
            if (response.statusCode() != 200) {
                System.err.println("Killshift Discord channel read returned " + response.statusCode());
                pollAfter = System.currentTimeMillis()
                        + ((response.statusCode() == 401 || response.statusCode() == 403
                                || response.statusCode() == 404) ? 3_600_000 : 30_000);
                return;
            }
            JsonArray messages = JsonParser.parseString(response.body()).getAsJsonArray();
            List<JsonObject> ordered = new ArrayList<>();
            messages.forEach(item -> ordered.add(item.getAsJsonObject()));
            ordered.sort(Comparator.comparing(message -> message.get("id").getAsString(),
                    Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())));
            boolean initial = cursor == null;
            if (initial && ordered.isEmpty()) {
                cursor = Long.toUnsignedString((System.currentTimeMillis() - 1420070400000L) << 22);
            }
            for (JsonObject message : ordered) {
                cursor = message.get("id").getAsString();
                JsonObject author = message.getAsJsonObject("author");
                if (initial || message.has("webhook_id")
                        || (author.has("bot") && author.get("bot").getAsBoolean())) continue;
                String name = author.get("username").getAsString();
                if (message.has("member") && message.get("member").isJsonObject()) {
                    JsonObject member = message.getAsJsonObject("member");
                    if (member.has("nick") && !member.get("nick").isJsonNull())
                        name = member.get("nick").getAsString();
                }
                StringBuilder content = new StringBuilder(message.get("content").getAsString());
                if (message.has("attachments")) message.getAsJsonArray("attachments").forEach(item -> {
                    if (!content.isEmpty()) content.append(' ');
                    content.append(item.getAsJsonObject().get("url").getAsString());
                });
                if (content.isEmpty()) continue;
                String prefix = "[Discord] " + name.strip().replaceAll("[\\r\\n]", " ") + ": ";
                String body = content.toString().strip().replaceAll("[\\r\\n]+", " ");
                server.execute(() -> server.getPlayerList().broadcastSystemMessage(
                        Component.literal(prefix).withStyle(ChatFormatting.AQUA)
                                .append(Component.literal(body).withStyle(ChatFormatting.WHITE)), false));
            }
        } catch (Exception error) {
            System.err.println("Killshift Discord channel read failed: " + error.getClass().getSimpleName());
            pollAfter = System.currentTimeMillis() + 30_000;
        }
    }

    private static long retryDelay(String body) {
        try {
            return Math.max(1_000, Math.min(86_400_000,
                    (long) (JsonParser.parseString(body).getAsJsonObject()
                            .get("retry_after").getAsDouble() * 1_000)));
        } catch (Exception error) {
            return 5_000;
        }
    }

    private static long onlineCount(MinecraftServer server, ServerPlayer excluded) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> player != excluded && !player.hasDisconnected()).count();
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }
}
