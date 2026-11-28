package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import uk.co.httpsmmuminecraftsociety.mainmod.WebsiteCommand;
import uk.co.httpsmmuminecraftsociety.mainmod.bigboss.BigBossManager;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.magic.MagicFoodEffects;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class GameplayGrpcService extends GrpcHandler {
    static final GameplayGrpcService INSTANCE = new GameplayGrpcService();
    private final GameplayEventsClient eventsClient = new GameplayEventsClient();

    private GameplayGrpcService() {}

    public static void notifyPresentsOnJoin(net.minecraft.server.level.ServerPlayer player) {
        INSTANCE.eventsClient.getPresentJoinStatus(player.getName().getString()).thenAccept(status -> {
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (player.hasDisconnected() || status.getPendingCount() == 0) return;
                int count = status.getPendingCount();
                player.sendSystemMessage(Component.literal("You have " + count + " unread Christmas "
                                + (count == 1 ? "letter" : "letters") + ". ")
                        .withStyle(ChatFormatting.RED)
                        .append(WebsiteCommand.takeMeThere("presents", "Open your letters", ChatFormatting.GREEN)));
            });
        }).exceptionally(error -> null);
    }

    public static CompletableFuture<BigBossStateResponse> bigBossHeartbeat(BigBossHeartbeatRequest request) {
        return INSTANCE.eventsClient.bigBossHeartbeat(request);
    }

    public static CompletableFuture<BigBossStateResponse> bigBossJoin(BigBossPlayerRequest request) {
        return INSTANCE.eventsClient.bigBossJoin(request);
    }

    public static CompletableFuture<BigBossStateResponse> bigBossLeave(String username) {
        return INSTANCE.eventsClient.bigBossLeave(username);
    }

    public static CompletableFuture<BigBossTargetResponse> getBigBossTarget() {
        return INSTANCE.eventsClient.getBigBossTarget();
    }

    public static CompletableFuture<BigBossKillResponse> recordBigBossKill(BigBossKillRequest request) {
        return INSTANCE.eventsClient.recordBigBossKill(request);
    }

    public static CompletableFuture<AdventBanhammerResponse> useAdventBanhammer(String victimUuid, String victimUsername) {
        return INSTANCE.eventsClient.useAdventBanhammer(victimUuid, victimUsername);
    }

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

    public static CompletableFuture<GetCaughtFishIdsResponse> getCaughtFishIds(net.minecraft.server.level.ServerPlayer player) {
        return INSTANCE.eventsClient.getCaughtFishIds(player);
    }

    public static CompletableFuture<GetKnowledgeTipResponse> getKnowledgeTip(
            String minecraftUsername, String minecraftUuid
    ) {
        return INSTANCE.eventsClient.getKnowledgeTip(minecraftUsername, minecraftUuid);
    }

    public static CompletableFuture<GetAnnouncementsResponse> getAnnouncements(
            String minecraftUuid,
            boolean includeRead
    ) {
        return INSTANCE.eventsClient.getAnnouncements(minecraftUuid, includeRead);
    }

    public static CompletableFuture<MarkAnnouncementsReadResponse> markAnnouncementsRead(
            String minecraftUuid,
            List<Integer> announcementIds
    ) {
        return INSTANCE.eventsClient.markAnnouncementsRead(minecraftUuid, announcementIds);
    }

    public static CompletableFuture<SyncPlayerStatsResponse> syncPlayerStats(
            net.minecraft.server.level.ServerPlayer player, List<MinecraftStatEntry> stats,
            boolean includeRewards, boolean duckLogin
    ) {
        return INSTANCE.eventsClient.syncPlayerStats(player, stats, includeRewards, duckLogin);
    }

    public static CompletableFuture<RecordDuckEventResponse> recordDuckEvent(
            net.minecraft.server.level.ServerPlayer player, int duckId
    ) {
        return recordDuckEvent(player, duckId, "", System.currentTimeMillis());
    }

    public static CompletableFuture<RecordDuckEventResponse> recordDuckEvent(
            net.minecraft.server.level.ServerPlayer player, int duckId, String victimMinecraftUuid, long eventUnixMs
    ) {
        return INSTANCE.eventsClient.recordDuckEvent(player, duckId, victimMinecraftUuid, eventUnixMs);
    }

    public static CompletableFuture<AckDuckDeliveryResponse> ackDuckDelivery(
            net.minecraft.server.level.ServerPlayer player, DuckAward award
    ) {
        return INSTANCE.eventsClient.ackDuckDelivery(player, award);
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

    public static CompletableFuture<RecordCommandExecutionResponse> recordCommandExecution(
            RecordCommandExecutionRequest request
    ) {
        return INSTANCE.eventsClient.recordCommandExecution(request);
    }

    public static CompletableFuture<RecordDabloonInteractionResponse> recordDabloonInteraction(
            RecordDabloonInteractionRequest request
    ) {
        return INSTANCE.eventsClient.recordDabloonInteraction(request);
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
        public void previewPresentHand(PresentPlayerRequest request, StreamObserver<PresentItemResponse> responseObserver) {
            callOnMainThread(() -> GameplayPresentOperations.preview(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void capturePresentHand(CapturePresentRequest request, StreamObserver<PresentItemResponse> responseObserver) {
            callOnMainThread(() -> GameplayPresentOperations.capture(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void deliverPresentItem(DeliverPresentRequest request, StreamObserver<DeliverPresentResponse> responseObserver) {
            callOnMainThread(() -> GameplayPresentOperations.deliver(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }
        @Override
        public void broadcastBigBossNotice(BigBossNoticeRequest request,
                                           StreamObserver<BigBossNoticeResponse> responseObserver) {
            callOnMainThread(() -> {
                BigBossManager.notice(request);
                return BigBossNoticeResponse.newBuilder().setApplied(true).build();
            }).whenComplete((response, error) -> complete(responseObserver, response, error));
        }

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
        public void getAdventRewards(
                GetAdventRewardsRequest request,
                StreamObserver<GetAdventRewardsResponse> responseObserver
        ) {
            callOnMainThread(GameplayAdventOperations::getRewardsOnMainThread)
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void grantAdventReward(
                GrantAdventRewardRequest request,
                StreamObserver<GrantAdventRewardResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayAdventOperations.grantOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void grantAdventSecret(
                GrantAdventSecretRequest request,
                StreamObserver<GrantAdventRewardResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayAdventOperations.grantSecretOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void penalizeAdventCheater(
                PenalizeAdventCheaterRequest request,
                StreamObserver<PenalizeAdventCheaterResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayAdventOperations.penalizeCheaterOnMainThread(request))
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
        public void getActiveFoodEffects(
                GetActiveFoodEffectsRequest request,
                StreamObserver<GetActiveFoodEffectsResponse> responseObserver
        ) {
            callOnMainThread(() -> {
                var server = GrpcBridge.minecraftServer();
                var playerId = java.util.UUID.fromString(request.getMinecraftUuid());
                return GetActiveFoodEffectsResponse.newBuilder()
                        .setShopDiscountPercent(MagicFoodEffects.shopDiscount(server, playerId))
                        .setSundayMoney(MagicFoodEffects.has(server, playerId, MagicFoodEffects.Effect.SUNDAY_MONEY))
                        .build();
            }).whenComplete((response, error) -> complete(responseObserver, response, error));
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
        public void purchaseExternalMembership(
                PurchaseExternalMembershipRequest request,
                StreamObserver<PurchaseExternalMembershipResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayMoneyOperations.purchaseExternalMembershipOnMainThread(request))
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
        public void teleportBuildVisitor(
                TeleportBuildVisitorRequest request,
                StreamObserver<TeleportBuildVisitorResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayBuildEventOperations.teleportOnMainThread(request))
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
        public void refreshApiCaches(
                RefreshApiCachesRequest request,
                StreamObserver<RefreshApiCachesResponse> responseObserver
        ) {
            eventsClient.refreshApiCaches()
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
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
        public void applyPlayerPresentation(
                ApplyPlayerPresentationRequest request,
                StreamObserver<ApplyPlayerPresentationResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayPlayerOperations.applyPlayerPresentationOnMainThread(request))
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

        @Override
        public void broadcastAnnouncement(
                BroadcastAnnouncementRequest request,
                StreamObserver<BroadcastAnnouncementResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayAnnouncementOperations.broadcastOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void tryGrantAdvancement(
                TryGrantAdvancementRequest request,
                StreamObserver<TryGrantAdvancementResponse> responseObserver
        ) {
            callOnMainThread(() -> GameplayPlayerOperations.tryGrantAdvancementOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void applyDuckUpdate(
                ApplyDuckUpdateRequest request,
                StreamObserver<ApplyDuckUpdateResponse> responseObserver
        ) {
            callOnMainThread(() -> uk.co.httpsmmuminecraftsociety.mainmod.ducks.DuckManager.applyUpdate(request))
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
