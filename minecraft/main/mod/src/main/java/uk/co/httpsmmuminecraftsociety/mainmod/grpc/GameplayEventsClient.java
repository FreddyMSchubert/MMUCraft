package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.UnlockBookLoot;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class GameplayEventsClient {
    private GameplayEventsGrpc.GameplayEventsFutureStub gameplayEvents;

    void start(ManagedChannel apiChannel) {
        gameplayEvents = GameplayEventsGrpc.newFutureStub(apiChannel);
        requestClaimsSnapshot();
        requestFeatureToggles();
        requestDailyTasksSnapshot();
    }

    void stop() {
        gameplayEvents = null;
    }

    CompletableFuture<RefreshApiCachesResponse> refreshApiCaches() {
        CompletableFuture<Void> flushed = new CompletableFuture<>();
        GrpcBridge.runOnMainThread(() -> DailyTaskManager.flush().whenComplete((done, error) -> {
            if (error == null) flushed.complete(null);
            else flushed.completeExceptionally(error);
        }));
        return flushed.thenCompose(ignored -> refreshApiCachesAfterFlush());
    }

    private CompletableFuture<RefreshApiCachesResponse> refreshApiCachesAfterFlush() {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;
        if (client == null) return CompletableFuture.failedFuture(new IllegalStateException("API client is unavailable"));

        CompletableFuture<ClaimsSnapshot> claims = call(stub -> stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .getClaimsSnapshot(GetClaimsSnapshotRequest.getDefaultInstance()));
        CompletableFuture<FeatureTogglesSnapshot> toggles = call(stub -> stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .getFeatureToggles(GetFeatureTogglesRequest.getDefaultInstance()));
        CompletableFuture<DailyTasksSnapshot> dailies = call(stub -> stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .getDailyTasksSnapshot(GetDailyTasksSnapshotRequest.getDefaultInstance()));

        return CompletableFuture.allOf(claims, toggles, dailies).thenCompose(ignored -> {
            CompletableFuture<RefreshApiCachesResponse> result = new CompletableFuture<>();
            GrpcBridge.runOnMainThread(() -> {
                try {
                    MinecraftServer server = GrpcBridge.minecraftServer();
                    if (server == null || gameplayEvents != client) {
                        throw new IllegalStateException("Minecraft or API client is unavailable");
                    }
                    ClaimsManager.apply(claims.join());
                    FeatureToggles.apply(toggles.join());
                    DailyTaskManager.apply(dailies.join());
                    UnlockBookLoot.clearAvailabilityCache();

                    List<ServerPlayer> online = List.copyOf(server.getPlayerList().getPlayers());
                    CompletableFuture<Void> players = PlayerStatsSync.refreshOnlinePlayers(server);
                    CompletableFuture<?>[] availability = online.stream()
                            .map(UnlockBookLoot::refreshAvailability)
                            .toArray(CompletableFuture[]::new);
                    CompletableFuture.allOf(players, CompletableFuture.allOf(availability))
                            .whenComplete((done, error) -> {
                                if (error != null) result.completeExceptionally(error);
                                else result.complete(RefreshApiCachesResponse.newBuilder()
                                        .setRefreshed(true).setOnlinePlayers(online.size()).build());
                            });
                } catch (Exception error) {
                    result.completeExceptionally(error);
                }
            });
            return result;
        });
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

    CompletableFuture<GetKnowledgeTipResponse> getKnowledgeTip(
            String minecraftUsername,
            String minecraftUuid
    ) {
        GetKnowledgeTipRequest request = GetKnowledgeTipRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setMinecraftUuid(minecraftUuid)
                .build();
        return call(client -> client.withDeadlineAfter(5, TimeUnit.SECONDS).getKnowledgeTip(request));
    }

    CompletableFuture<GetAnnouncementsResponse> getAnnouncements(String minecraftUuid, boolean includeRead) {
        GetAnnouncementsRequest request = GetAnnouncementsRequest.newBuilder()
                .setMinecraftUuid(minecraftUuid)
                .setIncludeRead(includeRead)
                .build();
        return call(client -> client.withDeadlineAfter(5, TimeUnit.SECONDS).getAnnouncements(request));
    }

    CompletableFuture<MarkAnnouncementsReadResponse> markAnnouncementsRead(
            String minecraftUuid,
            List<Integer> announcementIds
    ) {
        MarkAnnouncementsReadRequest request = MarkAnnouncementsReadRequest.newBuilder()
                .setMinecraftUuid(minecraftUuid)
                .addAllAnnouncementIds(announcementIds)
                .build();
        return call(client -> client.withDeadlineAfter(5, TimeUnit.SECONDS).markAnnouncementsRead(request));
    }

    CompletableFuture<SyncPlayerStatsResponse> syncPlayerStats(
            ServerPlayer player,
            List<MinecraftStatEntry> stats,
            boolean includeRewards
    ) {
        SyncPlayerStatsRequest request = SyncPlayerStatsRequest.newBuilder()
                .setMinecraftUsername(player.getName().getString())
                .setMinecraftUuid(player.getUUID().toString())
                .setUnixMs(System.currentTimeMillis())
                .setBalanceDabloons(Math.max(0, MoneyHelper.GetBalance(player)))
                .setIncludeSurprisingSaturdayRewards(includeRewards)
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

    CompletableFuture<RecordCommandExecutionResponse> recordCommandExecution(RecordCommandExecutionRequest request) {
        return call(client -> client.recordCommandExecution(request));
    }

    CompletableFuture<RecordDabloonInteractionResponse> recordDabloonInteraction(RecordDabloonInteractionRequest request) {
        return call(client -> client.recordDabloonInteraction(request));
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

    private void requestFeatureToggles() {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;
        if (client == null) return;

        long revision = FeatureToggles.revision();
        ListenableFuture<FeatureTogglesSnapshot> rpc = client.withDeadlineAfter(5, TimeUnit.SECONDS)
                .getFeatureToggles(GetFeatureTogglesRequest.getDefaultInstance());
        rpc.addListener(() -> {
            try {
                FeatureTogglesSnapshot snapshot = rpc.get();
                GrpcBridge.runOnMainThread(() -> {
                    if (gameplayEvents == client && FeatureToggles.revision() == revision) FeatureToggles.apply(snapshot);
                });
                MainMod.LOGGER.info("Loaded {} feature toggles", snapshot.getTogglesCount());
                CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute(() -> {
                    if (gameplayEvents == client) requestFeatureToggles();
                });
            } catch (Exception exception) {
                MainMod.LOGGER.warn("Could not load feature toggles; retrying in 5 seconds", exception);
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
                    if (gameplayEvents == client) requestFeatureToggles();
                });
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
