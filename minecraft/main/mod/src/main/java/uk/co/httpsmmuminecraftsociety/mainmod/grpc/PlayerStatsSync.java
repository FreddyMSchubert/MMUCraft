package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStatsSync {
    private static final long BASE_SYNC_INTERVAL_TICKS = 20L * 60L * 20L;
    private static final long STAGGER_WINDOW_TICKS = 5L * 60L * 20L;
    private static final Map<UUID, Long> nextSyncTickByPlayer = new ConcurrentHashMap<>();

    private static long serverTicks;

    private PlayerStatsSync() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncNow(handler.player);
            scheduleNext(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            syncNow(handler.player, true);
            nextSyncTickByPlayer.remove(handler.player.getUUID());
        });
    }

    public static void onServerTick(MinecraftServer server) {
        serverTicks++;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            long nextSyncTick = nextSyncTickByPlayer.computeIfAbsent(
                    playerId,
                    id -> serverTicks + staggerTicks(player)
            );

            if (serverTicks < nextSyncTick) {
                continue;
            }

            syncNow(player);
            scheduleNext(player);
        }
    }

    private static void scheduleNext(ServerPlayer player) {
        nextSyncTickByPlayer.put(
                player.getUUID(),
                serverTicks + BASE_SYNC_INTERVAL_TICKS + staggerTicks(player)
        );
    }

    private static long staggerTicks(ServerPlayer player) {
        return Math.floorMod(player.getUUID().hashCode(), STAGGER_WINDOW_TICKS);
    }

    public static void syncNow(ServerPlayer player) {
        syncNow(player, false);
    }

    private static void syncNow(ServerPlayer player, boolean allowDisconnectedPlayer) {
        if (player == null || (!allowDisconnectedPlayer && player.hasDisconnected())) {
            return;
        }

        GameplayGrpcService.syncPlayerStats(player, collectStats(player))
                .exceptionally(error -> {
                    MainMod.LOGGER.debug("Failed to sync player stats for {}", player.getName().getString(), error);
                    return null;
                });
    }

    private static List<MinecraftStatEntry> collectStats(ServerPlayer player) {
        ServerStatsCounter stats = player.getStats();
        List<MinecraftStatEntry> entries = new ArrayList<>();
        StatType<Identifier> customStats = Stats.CUSTOM;

        // CUSTOM_STAT is a Registry<Identifier>; StatType wants the registered values, not the registry keys.
        BuiltInRegistries.CUSTOM_STAT.stream().forEach(statId -> addStat(
                entries,
                "custom",
                statId,
                customLabel(statId),
                stats.getValue(customStats, statId)
        ));

        long completedAdvancements = player.getAdvancements().visible.stream()
                .filter(holder -> holder.value().display().isPresent())
                .filter(holder -> player.getAdvancements().getOrStartProgress(holder).isDone())
                .count();
        addStat(
                entries,
                "advancement",
                Identifier.fromNamespaceAndPath("minecraft", "earned"),
                "Advancements Earned",
                (int) completedAdvancements
        );

        for (Identifier entityId : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(entityId);
            addStat(
                    entries,
                    "killed",
                    entityId,
                    humanize(entityId) + " Killed",
                    stats.getValue(Stats.ENTITY_KILLED, entityType)
            );
            addStat(
                    entries,
                    "killed_by",
                    entityId,
                    "Killed By " + humanize(entityId),
                    stats.getValue(Stats.ENTITY_KILLED_BY, entityType)
            );
        }

        return entries;
    }

    private static void addStat(
            List<MinecraftStatEntry> entries,
            String category,
            Identifier id,
            String label,
            int value
    ) {
        entries.add(MinecraftStatEntry.newBuilder()
                .setKey("minecraft." + category + "." + id)
                .setCategory(category)
                .setId(id.toString())
                .setLabel(label)
                .setValue(Math.max(0, value))
                .build());
    }

    private static String customLabel(Identifier id) {
        return switch (id.toString()) {
            case "minecraft:play_time" -> "Play Time";
            case "minecraft:total_world_time" -> "Total World Time";
            case "minecraft:time_since_death" -> "Time Since Death";
            case "minecraft:time_since_rest" -> "Time Since Rest";
            case "minecraft:sneak_time", "minecraft:crouch_time" -> "Crouch Time";
            case "minecraft:walk_one_cm" -> "Distance Walked";
            case "minecraft:sprint_one_cm" -> "Distance Sprinted";
            case "minecraft:crouch_one_cm" -> "Distance Crouched";
            case "minecraft:fall_one_cm" -> "Distance Fallen";
            case "minecraft:fly_one_cm" -> "Distance Flown";
            case "minecraft:swim_one_cm" -> "Distance Swum";
            case "minecraft:boat_one_cm" -> "Distance By Boat";
            case "minecraft:minecart_one_cm" -> "Distance By Minecart";
            case "minecraft:horse_one_cm" -> "Distance By Horse";
            case "minecraft:aviate_one_cm" -> "Distance By Elytra";
            case "minecraft:jump" -> "Times Jumped";
            case "minecraft:drop" -> "Items Dropped";
            case "minecraft:damage_dealt" -> "Damage Dealt";
            case "minecraft:damage_taken" -> "Damage Taken";
            case "minecraft:deaths" -> "Deaths";
            case "minecraft:mob_kills" -> "Mob Kills";
            case "minecraft:player_kills" -> "Player Kills";
            case "minecraft:animals_bred" -> "Animals Bred";
            case "minecraft:fish_caught" -> "Fish Caught";
            case "minecraft:talked_to_villager" -> "Villagers Talked To";
            case "minecraft:traded_with_villager" -> "Trades With Villagers";
            case "minecraft:raid_trigger" -> "Raids Triggered";
            case "minecraft:raid_win" -> "Raids Won";
            default -> humanize(id);
        };
    }

    private static String humanize(Identifier id) {
        String path = id.getPath().replace('/', '_');
        String[] parts = path.split("_+");
        List<String> words = new ArrayList<>();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }

        return String.join(" ", words);
    }
}
