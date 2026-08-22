package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import net.minecraft.server.level.ServerPlayer;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class GameplayEventsClient {
    private GameplayEventsGrpc.GameplayEventsFutureStub gameplayEvents;

    void start(ManagedChannel apiChannel) {
        gameplayEvents = GameplayEventsGrpc.newFutureStub(apiChannel);
        requestClaimsSnapshot();
        requestDailyTasksSnapshot();
    }

    void stop() {
        gameplayEvents = null;
    }

    CompletableFuture<UnlockNextKnowledgeResponse> unlockNext(
            String minecraftUsername,
            String minecraftUuid,
            String sourceItemId,
            String unlockType
    ) {
        UnlockNextKnowledgeRequest request = UnlockNextKnowledgeRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setMinecraftUuid(minecraftUuid)
                .setSourceItemId(sourceItemId)
                .setUnlockType(unlockType)
                .setUnixMs(System.currentTimeMillis())
                .build();
        return call(client -> client.unlockNextKnowledge(request));
    }

    CompletableFuture<GetUnlockAvailabilityResponse> getUnlockAvailability(
            String minecraftUsername,
            String minecraftUuid
    ) {
        GetUnlockAvailabilityRequest request = GetUnlockAvailabilityRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setMinecraftUuid(minecraftUuid)
                .setUnixMs(System.currentTimeMillis())
                .build();
        return call(client -> client.getUnlockAvailability(request));
    }

    CompletableFuture<SyncPlayerStatsResponse> syncPlayerStats(
            ServerPlayer player,
            List<MinecraftStatEntry> stats
    ) {
        SyncPlayerStatsRequest request = SyncPlayerStatsRequest.newBuilder()
                .setMinecraftUsername(player.getName().getString())
                .setMinecraftUuid(player.getUUID().toString())
                .setUnixMs(System.currentTimeMillis())
                .setBalanceDabloons(Math.max(0, MoneyHelper.GetBalance(player)))
                .addAllStats(stats)
                .build();
        return call(client -> client.withDeadlineAfter(5, TimeUnit.SECONDS).syncPlayerStats(request));
    }

    CompletableFuture<RecordMoneyEventResponse> recordMoneyEvent(
            String minecraftUsername,
            String minecraftUuid,
            int amountDabloons,
            String source,
            String referenceId,
            int balanceDabloons
    ) {
        RecordMoneyEventRequest request = RecordMoneyEventRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setMinecraftUuid(minecraftUuid)
                .setAmountDabloons(Math.max(0, amountDabloons))
                .setSource(source)
                .setReferenceId(referenceId)
                .setUnixMs(System.currentTimeMillis())
                .setBalanceDabloons(Math.max(0, balanceDabloons))
                .build();
        return call(client -> client.recordMoneyEvent(request));
    }

    CompletableFuture<RecordFishCatchResponse> recordFishCatch(
            ServerPlayer player,
            String fishId,
            double lengthCm,
            String rarity
    ) {
        RecordFishCatchRequest request = RecordFishCatchRequest.newBuilder()
                .setMinecraftUsername(player.getName().getString())
                .setMinecraftUuid(player.getUUID().toString())
                .setFishId(fishId)
                .setLengthCm(lengthCm)
                .setRarity(rarity)
                .setUnixMs(System.currentTimeMillis())
                .build();
        return call(client -> client.recordFishCatch(request));
    }

    CompletableFuture<UpdateDailyTaskResponse> updateDailyTask(
            int userId,
            String periodKey,
            String taskJson
    ) {
        UpdateDailyTaskRequest request = UpdateDailyTaskRequest.newBuilder()
                .setUserId(userId)
                .setPeriodKey(periodKey)
                .setTaskJson(taskJson)
                .setUnixMs(System.currentTimeMillis())
                .build();
        return call(client -> client.withDeadlineAfter(5, TimeUnit.SECONDS).updateDailyTask(request));
    }

    CompletableFuture<PublishDiscordEventResponse> publishDiscordEvent(PublishDiscordEventRequest request) {
        return call(client -> client.publishDiscordEvent(request));
    }

    private void requestClaimsSnapshot() {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;
        if (client == null) return;

        ListenableFuture<ClaimsSnapshot> rpc = client.withDeadlineAfter(5, TimeUnit.SECONDS)
                .getClaimsSnapshot(GetClaimsSnapshotRequest.getDefaultInstance());
        rpc.addListener(() -> {
            try {
                ClaimsSnapshot snapshot = rpc.get();
                GrpcBridge.runOnMainThread(() -> ClaimsManager.apply(snapshot));
                MainMod.LOGGER.info("Loaded {} chunk claims", snapshot.getClaimsCount());
            } catch (Exception exception) {
                MainMod.LOGGER.warn("Could not load claims; retrying in 5 seconds", exception);
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(this::requestClaimsSnapshot);
            }
        }, Runnable::run);
    }

    private void requestDailyTasksSnapshot() {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;
        if (client == null) return;

        ListenableFuture<DailyTasksSnapshot> rpc = client.withDeadlineAfter(5, TimeUnit.SECONDS)
                .getDailyTasksSnapshot(GetDailyTasksSnapshotRequest.getDefaultInstance());
        rpc.addListener(() -> {
            try {
                DailyTasksSnapshot snapshot = rpc.get();
                GrpcBridge.runOnMainThread(() -> DailyTaskManager.apply(snapshot));
                MainMod.LOGGER.info("Loaded {} active daily tasks", snapshot.getTasksCount());
            } catch (Exception exception) {
                MainMod.LOGGER.warn("Could not load daily tasks; retrying in 5 seconds", exception);
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(this::requestDailyTasksSnapshot);
            }
        }, Runnable::run);
    }

    private <T> CompletableFuture<T> call(
            Function<GameplayEventsGrpc.GameplayEventsFutureStub, ListenableFuture<T>> request
    ) {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;
        if (client == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GameplayEvents gRPC client is not initialized")
            );
        }

        ListenableFuture<T> rpc = request.apply(client);
        CompletableFuture<T> result = new CompletableFuture<>();
        rpc.addListener(() -> {
            try {
                result.complete(rpc.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }, Runnable::run);
        return result;
    }
}
