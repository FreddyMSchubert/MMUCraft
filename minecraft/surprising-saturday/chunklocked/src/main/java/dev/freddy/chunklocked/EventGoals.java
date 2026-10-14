package dev.freddy.chunklocked;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;

final class EventGoals {
    private static final String BASE_URL = System.getenv("SURPRISING_SATURDAY_API_URL");
    private static final String SECRET = System.getenv("SURPRISING_SATURDAY_API_SECRET");
    private static final Path OUTBOX = Path.of("/data/chunklock-goals-outbox.jsonl");
    private static final Set<String> SEEN = new HashSet<>();
    private static final Object LOCK = new Object();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "chunklock-goals-outbox");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile MinecraftServer server;

    private EventGoals() { }

    static void register() {
        if (BASE_URL == null || SECRET == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            WORKER.scheduleWithFixedDelay(EventGoals::flush, 0, 5, TimeUnit.SECONDS);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            server = null;
            WORKER.shutdown();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isSpectator()) continue;
                if (player.level().dimension() == net.minecraft.world.level.Level.NETHER) record(player, "nether");
                if (player.level().dimension() == net.minecraft.world.level.Level.END) record(player, "end");
                for (var stack : player.getInventory().getNonEquipmentItems()) {
                    if (stack.isEmpty()) continue;
                    if (stack.is(Items.OAK_LOG)) record(player, "wood");
                    if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_pickaxe")) record(player, "pickaxe");
                    if (stack.is(Items.IRON_INGOT)) record(player, "iron");
                    if (stack.is(Items.DIAMOND)) record(player, "diamonds");
                }
            }
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player instanceof ServerPlayer serverPlayer && player.getItemInHand(hand).is(Items.ENDER_EYE)) {
                record(serverPlayer, "eye");
            }
            return InteractionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
            if (source.getEntity() instanceof ServerPlayer player) {
                if (dead.getType() == EntityTypes.BLAZE) record(player, "blaze");
                if (dead.getType() == EntityTypes.ENDER_DRAGON) record(player, "dragon");
            }
        });
    }

    private static void record(ServerPlayer player, String goal) {
        String key = player.getUUID() + ":" + goal;
        if (SEEN.contains(key)) return;
        String line = "{\"playerUuid\":\"" + player.getUUID() + "\",\"itemId\":\"chunklock:"
                + goal + "\",\"occurredAtUnixMs\":" + System.currentTimeMillis() + "}";
        synchronized (LOCK) {
            try {
                Files.writeString(OUTBOX, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                SEEN.add(key);
            } catch (Exception error) {
                System.err.println("Could not save Chunklock goal: " + error);
            }
        }
    }

    private static void flush() {
        try {
            while (true) {
                String line;
                synchronized (LOCK) {
                    if (!Files.exists(OUTBOX)) return;
                    List<String> lines = Files.readAllLines(OUTBOX);
                    if (lines.isEmpty()) return;
                    line = lines.get(0);
                }
                HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/api/internal/surprising-saturday/list-completion"))
                        .timeout(Duration.ofSeconds(5))
                        .header("authorization", "Bearer " + SECRET)
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(line)).build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (response.statusCode() != 400 && response.statusCode() != 404) return;
                    System.err.println("Chunklock goal rejected: " + response.statusCode() + " " + response.body());
                }
                JsonObject receipt = response.statusCode() >= 200 && response.statusCode() < 300
                        ? JsonParser.parseString(response.body()).getAsJsonObject() : null;
                synchronized (LOCK) {
                    List<String> lines = Files.readAllLines(OUTBOX);
                    if (lines.isEmpty() || !lines.get(0).equals(line)) continue;
                    Path next = OUTBOX.resolveSibling("chunklock-goals-outbox.tmp");
                    Files.write(next, lines.subList(1, lines.size()));
                    Files.move(next, OUTBOX, StandardCopyOption.REPLACE_EXISTING);
                }
                if (receipt != null && receipt.get("accepted").getAsBoolean()) {
                    JsonObject completion = JsonParser.parseString(line).getAsJsonObject();
                    String itemId = completion.get("itemId").getAsString();
                    UUID playerId = UUID.fromString(completion.get("playerUuid").getAsString());
                    for (var item : receipt.getAsJsonObject("score").getAsJsonArray("completed")) {
                        JsonObject goal = item.getAsJsonObject();
                        if (!goal.get("id").getAsString().equals(itemId)) continue;
                        String name = goal.get("name").getAsString();
                        MinecraftServer running = server;
                        if (running != null) running.execute(() -> {
                            ServerPlayer player = running.getPlayerList().getPlayer(playerId);
                            if (player == null) return;
                            for (ServerPlayer recipient : running.getPlayerList().getPlayers()) {
                                recipient.sendSystemMessage(recipient == player
                                        ? Component.literal("You completed \"" + name + "\"!")
                                        : EventPresentation.coloredName(player).copy()
                                                .append(Component.literal(" completed \"" + name + "\"!")));
                            }
                            EventDiscordBridge.publishGoal(player, name);
                        });
                        break;
                    }
                }
            }
        } catch (Exception error) {
            System.err.println("Chunklock goal replay paused: " + error);
        }
    }
}
