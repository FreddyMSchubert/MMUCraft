package dev.freddy.chunklocked;

import com.google.gson.Gson;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.biome.Biomes;

public final class EventGoals {
    private static final String BASE_URL = System.getenv("SURPRISING_SATURDAY_API_URL");
    private static final String SECRET = System.getenv("SURPRISING_SATURDAY_API_SECRET");
    private static final Path OUTBOX = Path.of("/data/chunklock-goals-outbox.jsonl");
    private static final Path PROGRESS_FILE = Path.of("/data/chunklock-goal-progress.json");
    private static final Gson GSON = new Gson();
    private static final Set<String> SEEN = new HashSet<>();
    private static final Map<UUID, ObservedRaid> RAIDS = new HashMap<>();
    private static final Object LOCK = new Object();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "chunklock-goals-outbox");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile MinecraftServer server;
    private static Progress progress;

    private record ObservedRaid(Raid raid, String dimension) { }

    private static final class Progress {
        Map<String, Integer> unlockedChunks = new HashMap<>();
        Map<String, Set<String>> contributors = new HashMap<>();
        Map<String, Set<String>> bossHitters = new HashMap<>();
    }

    private EventGoals() { }

    static void register() {
        if (BASE_URL == null || SECRET == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            try {
                progress = Files.exists(PROGRESS_FILE)
                        ? GSON.fromJson(Files.readString(PROGRESS_FILE), Progress.class) : new Progress();
                if (progress == null || progress.unlockedChunks == null
                        || progress.contributors == null || progress.bossHitters == null) {
                    throw new IllegalStateException("Invalid Chunklock goal progress");
                }
            } catch (Exception error) {
                throw new IllegalStateException("Cannot read Chunklock goal progress", error);
            }
            server = started;
            WORKER.scheduleWithFixedDelay(EventGoals::flush, 0, 5, TimeUnit.SECONDS);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            server = null;
            WORKER.shutdown();
            RAIDS.clear();
            progress = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(EventGoals::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, stopped) ->
                RAIDS.remove(handler.getPlayer().getUUID()));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player instanceof ServerPlayer serverPlayer && entity instanceof Villager) {
                record(serverPlayer, "villager");
            }
            return InteractionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0 || !(source.getEntity() instanceof ServerPlayer player)) return;
            if (victim instanceof AbstractIllager) record(player, "illager");
            if (victim instanceof Ravager) record(player, "ravager");
            if (victim instanceof WitherBoss || victim instanceof EnderDragon) {
                if (progress != null && progress.bossHitters
                        .computeIfAbsent(victim.getUUID().toString(), ignored -> new HashSet<>())
                        .add(player.getUUID().toString())) saveProgress();
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
            if (progress != null && (dead instanceof WitherBoss || dead instanceof EnderDragon)) {
                Set<String> hitters = progress.bossHitters.remove(dead.getUUID().toString());
                if (hitters != null) {
                    String goal = dead instanceof WitherBoss ? "wither" : "dragon";
                    for (String hitter : hitters) record(UUID.fromString(hitter), goal);
                    saveProgress();
                }
            }
        });
    }

    private static void tick(MinecraftServer running) {
        for (ServerPlayer player : running.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            ServerLevel level = (ServerLevel) player.level();
            var pos = player.blockPosition();
            ObservedRaid observed = RAIDS.get(player.getUUID());
            if (observed != null && observed.raid().isOver()) {
                if ((observed.raid().isVictory() || observed.raid().isLoss())
                        && observed.dimension().equals(ChunkWorld.dimension(level))
                        && level.isVillage(pos)
                        && pos.distSqr(observed.raid().getCenter()) <= 96 * 96) {
                    record(player, "raid_survive");
                }
                RAIDS.remove(player.getUUID());
            }
            Raid raid = level.getRaidAt(pos);
            if (raid != null && raid.isActive() && raid.isStarted() && !raid.isOver()
                    && level.isCloseToVillage(pos, 3)) {
                record(player, "raid_experience");
                RAIDS.put(player.getUUID(), new ObservedRaid(raid, ChunkWorld.dimension(level)));
            }
            if (running.getTickCount() % 20 != 0) continue;
            if (player.isSwimming() && player.isInWater()) record(player, "swim");
            if (player.onGround() && level.getBiome(pos).is(Biomes.DESERT)) record(player, "desert");
        }
    }

    public static void trialSpawnerActivated(ServerLevel level, TrialSpawner spawner) {
        if (server != level.getServer()) return;
        for (UUID playerId : spawner.getStateData().pack().detectedPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && player.level() == level) record(player, "trial_spawner");
        }
    }

    public static void trialKeyUsed(Player player, VaultConfig config) {
        if (!(player instanceof ServerPlayer serverPlayer) || server == null) return;
        if (config.keyItem().is(Items.TRIAL_KEY)) record(serverPlayer, "trial_key");
        if (config.keyItem().is(Items.OMINOUS_TRIAL_KEY)) record(serverPlayer, "ominous_trial_key");
    }

    static void contributed(ChunkWorld.Cell cell, ServerPlayer player) {
        if (progress != null && progress.contributors
                .computeIfAbsent(ChunkWorld.key(cell.dimension, cell.x, cell.z), ignored -> new HashSet<>())
                .add(player.getUUID().toString())) saveProgress();
    }

    static boolean hasContributors(ChunkWorld.Cell cell) {
        return progress != null && !progress.contributors
                .getOrDefault(ChunkWorld.key(cell.dimension, cell.x, cell.z), Set.of()).isEmpty();
    }

    static Set<UUID> unlocked(ChunkWorld.Cell cell, int totalPaid) {
        if (progress == null) return Set.of();
        if (totalPaid == 100 || totalPaid == 200 || totalPaid == 300 || totalPaid == 400
                || totalPaid == 500 || totalPaid == 750 || totalPaid == 1000) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                record(player, "online_" + totalPaid);
            }
        }
        Set<String> contributors = progress.contributors.remove(ChunkWorld.key(cell.dimension, cell.x, cell.z));
        if (contributors == null) return Set.of();
        Set<UUID> credited = new HashSet<>();
        for (String contributor : contributors) {
            UUID playerId = UUID.fromString(contributor);
            credited.add(playerId);
            int count = progress.unlockedChunks.merge(contributor, 1, Integer::sum);
            if (count == 1) record(playerId, "unlock_1");
            if (count == 3) record(playerId, "unlock_3");
            if (count == 5) record(playerId, "unlock_5");
        }
        saveProgress();
        return credited;
    }

    static int unlockedCount(UUID playerId) {
        return progress == null ? 0 : progress.unlockedChunks.getOrDefault(playerId.toString(), 0);
    }

    private static void saveProgress() {
        try {
            Path next = PROGRESS_FILE.resolveSibling("chunklock-goal-progress.tmp");
            Files.writeString(next, GSON.toJson(progress));
            Files.move(next, PROGRESS_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot save Chunklock goal progress", error);
        }
    }

    private static void record(ServerPlayer player, String goal) {
        record(player.getUUID(), goal);
    }

    private static void record(UUID playerId, String goal) {
        String key = playerId + ":" + goal;
        if (SEEN.contains(key)) return;
        String line = "{\"playerUuid\":\"" + playerId + "\",\"itemId\":\"chunklock:"
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
