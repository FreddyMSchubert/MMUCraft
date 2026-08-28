package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class GameplayGrpcService extends GrpcHandler {
    static final GameplayGrpcService INSTANCE = new GameplayGrpcService();
    private final GameplayEventsClient eventsClient = new GameplayEventsClient();

    private GameplayGrpcService() {}

    public static CompletableFuture<UnlockNextKnowledgeResponse> unlockNextKnowledge(
            String minecraftUsername, String minecraftUuid, String sourceItemId
    ) {
        return INSTANCE.eventsClient.unlockNext(minecraftUsername, minecraftUuid, sourceItemId, "knowledge");
    }

    public static CompletableFuture<UnlockNextKnowledgeResponse> unlockNext(
            String minecraftUsername, String minecraftUuid, String sourceItemId, String unlockType
    ) {
        return INSTANCE.eventsClient.unlockNext(minecraftUsername, minecraftUuid, sourceItemId, unlockType);
    }

    public static CompletableFuture<GetUnlockAvailabilityResponse> getUnlockAvailability(
            String minecraftUsername, String minecraftUuid
    ) {
        return INSTANCE.eventsClient.getUnlockAvailability(minecraftUsername, minecraftUuid);
    }

    public static CompletableFuture<SyncPlayerStatsResponse> syncPlayerStats(
            net.minecraft.server.level.ServerPlayer player, List<MinecraftStatEntry> stats
    ) {
        return INSTANCE.eventsClient.syncPlayerStats(player, stats);
    }

    public static CompletableFuture<RecordMoneyEventResponse> recordMoneyEvent(
            String minecraftUsername, String minecraftUuid, int amountDabloons,
            String source, String referenceId, int balanceDabloons
    ) {
        return INSTANCE.eventsClient.recordMoneyEvent(
                minecraftUsername, minecraftUuid, amountDabloons, source, referenceId, balanceDabloons
        );
    }

    public static CompletableFuture<RecordFishCatchResponse> recordFishCatch(
            net.minecraft.server.level.ServerPlayer player, String fishId, double lengthCm, String rarity
    ) {
        return INSTANCE.eventsClient.recordFishCatch(player, fishId, lengthCm, rarity);
    }

    public static CompletableFuture<UpdateDailyTaskResponse> updateDailyTask(
            int userId, String periodKey, String taskJson
    ) {
        return INSTANCE.eventsClient.updateDailyTask(userId, periodKey, taskJson);
    }

    public static CompletableFuture<PublishDiscordEventResponse> publishDiscordEvent(
            PublishDiscordEventRequest request
    ) {
        return INSTANCE.eventsClient.publishDiscordEvent(request);
    }

    @Override
    List<BindableService> serverServices() {
        return List.of(new GameplayControlEndpoint());
    }

    @Override
    void start(ManagedChannel apiChannel) {
        eventsClient.start(apiChannel);
    }

    @Override
    void stop() {
        eventsClient.stop();
    }

private final class GameplayControlEndpoint extends GameplayControlGrpc.GameplayControlImplBase {
        @Override
        public void getOnlinePlayers(
                GetOnlinePlayersRequest request,
                StreamObserver<GetOnlinePlayersResponse> responseObserver
        ) {
            callOnMainThread(GameplayPlayerOperations::getOnlinePlayersOnMainThread)
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void grantDailyLoginBonus(
                GrantDailyLoginBonusRequest request,
                StreamObserver<GrantDailyLoginBonusResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayMoneyOperations.grantDailyLoginBonusOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void grantGiftCodeMoney(
                GrantGiftCodeMoneyRequest request,
                StreamObserver<GrantGiftCodeMoneyResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayMoneyOperations.grantGiftCodeMoneyOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void grantKnowledgeReadMoney(
                GrantKnowledgeReadMoneyRequest request,
                StreamObserver<GrantKnowledgeReadMoneyResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayMoneyOperations.grantKnowledgeReadMoneyOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void generateDailyTasks(
                GenerateDailyTasksRequest request,
                StreamObserver<GenerateDailyTasksResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayDailyOperations.generateDailyTasksOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void claimDailyTask(
                ClaimDailyTaskRequest request,
                StreamObserver<ClaimDailyTaskResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayDailyOperations.claimDailyTaskOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void pickDailyAdvancement(
                PickDailyAdvancementRequest request,
                StreamObserver<PickDailyAdvancementResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayDailyOperations.pickDailyAdvancementOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void claimDailyAdvancement(
                ClaimDailyAdvancementRequest request,
                StreamObserver<ClaimDailyAdvancementResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayDailyOperations.claimDailyAdvancementOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void purchaseShopItem(
                PurchaseShopItemRequest request,
                StreamObserver<PurchaseShopItemResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayShopOperations.purchaseShopItemOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void purchaseExternalPlayerInvite(
                PurchaseExternalPlayerInviteRequest request,
                StreamObserver<PurchaseExternalPlayerInviteResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayMoneyOperations.purchaseExternalPlayerInviteOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void getCurrentClaimChunk(
                GetCurrentClaimChunkRequest request,
                StreamObserver<GetCurrentClaimChunkResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayClaimOperations.getCurrentClaimChunkOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void purchaseClaim(
                PurchaseClaimRequest request,
                StreamObserver<PurchaseClaimResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayClaimOperations.purchaseClaimOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void applyClaimsSnapshot(
                ClaimsSnapshot request,
                StreamObserver<ApplyClaimsSnapshotResponse> responseObserver
        ) {
            callOnMainThread(() -> {
                ClaimsManager.apply(request);
                return ApplyClaimsSnapshotResponse.newBuilder().setApplied(true).build();
            }).whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void applyFeatureToggles(
                FeatureTogglesSnapshot request,
                StreamObserver<ApplyFeatureTogglesResponse> responseObserver
        ) {
            callOnMainThread(() -> {
                FeatureToggles.apply(request);
                return ApplyFeatureTogglesResponse.newBuilder().setApplied(true).build();
            }).whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void applyPlayerColor(
                ApplyPlayerColorRequest request,
                StreamObserver<ApplyPlayerColorResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayPlayerOperations.applyPlayerColorOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void applyPlayerSettings(
                ApplyPlayerSettingsRequest request,
                StreamObserver<ApplyPlayerSettingsResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayPlayerOperations.applyPlayerSettingsOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void getCharmInventory(
                GetCharmInventoryRequest request,
                StreamObserver<GetCharmInventoryResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayCharmOperations.getCharmInventoryOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void upgradeCharm(
                UpgradeCharmRequest request,
                StreamObserver<UpgradeCharmResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayCharmOperations.upgradeCharmOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void broadcastDiscordMessage(
                BroadcastDiscordMessageRequest request,
                StreamObserver<BroadcastDiscordMessageResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayDiscordOperations.broadcastDiscordMessageOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void runServerCommand(
                RunServerCommandRequest request,
                StreamObserver<RunServerCommandResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayDiscordOperations.runServerCommandOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        private <T> void complete(StreamObserver<T> responseObserver, T response, Throwable error) {
            if (error != null) {
                responseObserver.onError(error);
                return;
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
