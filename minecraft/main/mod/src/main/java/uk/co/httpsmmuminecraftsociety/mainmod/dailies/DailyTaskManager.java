package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityTypes;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.DailyTaskState;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.DailyTasksSnapshot;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class DailyTaskManager {
    private static final List<ActiveTask> ACTIVE = new ArrayList<>();
    private static final Map<UUID, TravelState> TRAVEL = new HashMap<>();
    private static int elapsedTicks;

    private DailyTaskManager() {
    }

    public static void reset() {
        ACTIVE.clear();
        TRAVEL.clear();
        elapsedTicks = 0;
    }

    public static void apply(DailyTasksSnapshot snapshot) {
        ACTIVE.clear();
        for (DailyTaskState task : snapshot.getTasksList()) {
            if (task.getClaimed()) continue;
            add(task.getUserId(), task.getMinecraftUsername(), task.getPeriodKey(), task.getTaskJson());
        }
    }

    public static List<String> generate(int userId, String username, String periodKey, int count) {
        List<String> tasks = DailyTaskRegistry.pick(periodKey + ":" + normalize(username), count).stream()
                .map(JsonObject::toString)
                .toList();
        ACTIVE.removeIf(task -> task.userId() == userId);
        tasks.forEach(task -> add(userId, username, periodKey, task));
        return tasks;
    }

    public static DailyTaskDefinition.ClaimResult claim(
            ServerPlayer player,
            int userId,
            String periodKey,
            String taskJson
    ) {
        JsonObject task = DailyTaskRegistry.parse(taskJson);
        DailyTaskDefinition definition = DailyTaskRegistry.find(task.get("id").getAsString());
        DailyTaskDefinition.ClaimResult result = definition.claim(player, task);
        if (result.claimed()) {
            ACTIVE.removeIf(active -> active.userId() == userId
                    && active.periodKey().equals(periodKey)
                    && active.id().equals(definition.getId()));
        }
        return result;
    }

    public static void onEat(ServerPlayer player, ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        advance(player, "eat:" + itemId);
    }

    public static void advance(ServerPlayer player, String taskId) {
        advance(player, taskId, 1);
    }

    public static void advance(ServerPlayer player, String taskId, int amount) {
        String username = normalize(player.getName().getString());
        for (ActiveTask active : ACTIVE) {
            if (!active.username().equals(username) || !active.id().equals(taskId)) continue;
            advance(active, amount);
        }
    }

    public static void record(ServerPlayer player, DailyTaskEvent event) {
        String username = normalize(player.getName().getString());
        for (ActiveTask active : ACTIVE) {
            if (!active.username().equals(username)) continue;
            DailyTaskDefinition definition = DailyTaskRegistry.find(active.id());
            if (definition == null) continue;
            advance(active, definition.progress(active.task(), event));
        }
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        elapsedTicks++;
        if (elapsedTicks % 20 == 0) tickTravel(server);
        if (elapsedTicks % 1_200 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                record(player, DailyTaskEvent.of(DailyTaskEvent.Type.PLAY_TIME));
            }
        }
    }

    private static void add(int userId, String username, String periodKey, String taskJson) {
        try {
            JsonObject task = DailyTaskRegistry.parse(taskJson);
            ACTIVE.add(new ActiveTask(
                    userId,
                    normalize(username),
                    periodKey,
                    task.get("id").getAsString(),
                    task
            ));
        } catch (RuntimeException exception) {
            MainMod.LOGGER.warn("Ignored invalid saved daily task for user {}", userId, exception);
        }
    }

    private static void persist(ActiveTask active) {
        String json = active.task().toString();
        GameplayGrpcService.updateDailyTask(active.userId(), active.periodKey(), json)
                .whenComplete((response, error) -> {
                    if (error == null || !ACTIVE.contains(active)) return;
                    MainMod.LOGGER.warn("Could not save daily task {}; retrying in 5 seconds", active.id(), error);
                    CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> persist(active));
                });
    }

    private static void advance(ActiveTask active, int amount) {
        JsonObject task = active.task();
        int max = task.get("max").getAsInt();
        int current = task.get("current").getAsInt();
        if (amount < 1 || max < 1 || current >= max) return;
        task.addProperty("current", Math.min(max, current + amount));
        persist(active);
    }

    private static void tickTravel(net.minecraft.server.MinecraftServer server) {
        Map<UUID, TravelState> next = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.minecraft.world.entity.Entity vehicle = player.getVehicle();
            if (vehicle == null) continue;

            String vehicleId = vehicle instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
                    ? DailyTargetId.of(EntityTypes.MINECART)
                    : BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString();
            TravelState previous = TRAVEL.get(player.getUUID());
            double carry = previous != null && previous.vehicleId().equals(vehicleId) ? previous.carry() : 0.0D;
            if (previous != null && previous.vehicleId().equals(vehicleId)) {
                double distance = previous.position().distanceTo(vehicle.position());
                if (distance <= 64.0D) carry += distance;
            }

            int wholeBlocks = (int)Math.floor(carry);
            if (wholeBlocks > 0) {
                record(player, new DailyTaskEvent(DailyTaskEvent.Type.RIDE_DISTANCE, vehicleId, "", wholeBlocks));
                carry -= wholeBlocks;
            }
            next.put(player.getUUID(), new TravelState(vehicleId, vehicle.position(), carry));
        }
        TRAVEL.clear();
        TRAVEL.putAll(next);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record ActiveTask(int userId, String username, String periodKey, String id, JsonObject task) {
    }

    private record TravelState(String vehicleId, net.minecraft.world.phys.Vec3 position, double carry) {
    }
}
