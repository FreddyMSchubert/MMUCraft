package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class GameplayGrpcService extends GrpcHandler {
    static final GameplayGrpcService INSTANCE = new GameplayGrpcService();

    private GameplayEventsGrpc.GameplayEventsFutureStub gameplayEvents;

    private GameplayGrpcService() {
    }

    public static CompletableFuture<UnlockNextKnowledgeResponse> unlockNextKnowledge(
            String minecraftUsername,
            String minecraftUuid,
            String sourceItemId
    ) {
        return INSTANCE.unlockNextInternal(minecraftUsername, minecraftUuid, sourceItemId, "knowledge");
    }

    public static CompletableFuture<UnlockNextKnowledgeResponse> unlockNext(
            String minecraftUsername,
            String minecraftUuid,
            String sourceItemId,
            String unlockType
    ) {
        return INSTANCE.unlockNextInternal(minecraftUsername, minecraftUuid, sourceItemId, unlockType);
    }

    public static CompletableFuture<GetUnlockAvailabilityResponse> getUnlockAvailability(
            String minecraftUsername,
            String minecraftUuid
    ) {
        return INSTANCE.getUnlockAvailabilityInternal(minecraftUsername, minecraftUuid);
    }

    public static CompletableFuture<SyncPlayerStatsResponse> syncPlayerStats(
            ServerPlayer player,
            List<MinecraftStatEntry> stats
    ) {
        return INSTANCE.syncPlayerStatsInternal(
                player.getName().getString(),
                player.getUUID().toString(),
                MoneyHelper.GetBalance(player),
                stats
        );
    }

    public static CompletableFuture<RecordMoneyEventResponse> recordMoneyEvent(
            String minecraftUsername,
            String minecraftUuid,
            int amountDabloons,
            String direction,
            String source,
            String referenceId,
            int balanceDabloons
    ) {
        return INSTANCE.recordMoneyEventInternal(
                minecraftUsername,
                minecraftUuid,
                amountDabloons,
                direction,
                source,
                referenceId,
                balanceDabloons
        );
    }

    @Override
    List<BindableService> serverServices() {
        return List.of(new GameplayControlEndpoint());
    }

    @Override
    void start(ManagedChannel apiChannel) {
        gameplayEvents = GameplayEventsGrpc.newFutureStub(apiChannel);
    }

    @Override
    void stop() {
        gameplayEvents = null;
    }

    private CompletableFuture<UnlockNextKnowledgeResponse> unlockNextInternal(
            String minecraftUsername,
            String minecraftUuid,
            String sourceItemId,
            String unlockType
    ) {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;

        if (client == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GameplayEvents gRPC client is not initialized")
            );
        }

        UnlockNextKnowledgeRequest request = UnlockNextKnowledgeRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setMinecraftUuid(minecraftUuid)
                .setSourceItemId(sourceItemId)
                .setUnlockType(unlockType)
                .setUnixMs(System.currentTimeMillis())
                .build();

        var rpc = client.unlockNextKnowledge(request);
        CompletableFuture<UnlockNextKnowledgeResponse> result = new CompletableFuture<>();

        rpc.addListener(() -> {
            try {
                result.complete(rpc.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }, Runnable::run);

        return result;
    }

    private CompletableFuture<GetUnlockAvailabilityResponse> getUnlockAvailabilityInternal(
            String minecraftUsername,
            String minecraftUuid
    ) {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;

        if (client == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GameplayEvents gRPC client is not initialized")
            );
        }

        GetUnlockAvailabilityRequest request = GetUnlockAvailabilityRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setMinecraftUuid(minecraftUuid)
                .setUnixMs(System.currentTimeMillis())
                .build();

        var rpc = client.getUnlockAvailability(request);
        CompletableFuture<GetUnlockAvailabilityResponse> result = new CompletableFuture<>();

        rpc.addListener(() -> {
            try {
                result.complete(rpc.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }, Runnable::run);

        return result;
    }

    private CompletableFuture<SyncPlayerStatsResponse> syncPlayerStatsInternal(
            String minecraftUsername,
            String minecraftUuid,
            int balanceDabloons,
            List<MinecraftStatEntry> stats
    ) {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;

        if (client == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GameplayEvents gRPC client is not initialized")
            );
        }

        SyncPlayerStatsRequest request = SyncPlayerStatsRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setMinecraftUuid(minecraftUuid)
                .setUnixMs(System.currentTimeMillis())
                .setBalanceDabloons(Math.max(0, balanceDabloons))
                .addAllStats(stats)
                .build();

        var rpc = client.syncPlayerStats(request);
        CompletableFuture<SyncPlayerStatsResponse> result = new CompletableFuture<>();

        rpc.addListener(() -> {
            try {
                result.complete(rpc.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }, Runnable::run);

        return result;
    }

    private CompletableFuture<RecordMoneyEventResponse> recordMoneyEventInternal(
            String minecraftUsername,
            String minecraftUuid,
            int amountDabloons,
            String direction,
            String source,
            String referenceId,
            int balanceDabloons
    ) {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;

        if (client == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GameplayEvents gRPC client is not initialized")
            );
        }

        RecordMoneyEventRequest request = RecordMoneyEventRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setMinecraftUuid(minecraftUuid)
                .setAmountDabloons(Math.max(0, amountDabloons))
                .setDirection(direction)
                .setSource(source)
                .setReferenceId(referenceId)
                .setUnixMs(System.currentTimeMillis())
                .setBalanceDabloons(Math.max(0, balanceDabloons))
                .build();

        var rpc = client.recordMoneyEvent(request);
        CompletableFuture<RecordMoneyEventResponse> result = new CompletableFuture<>();

        rpc.addListener(() -> {
            try {
                result.complete(rpc.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }, Runnable::run);

        return result;
    }

    private GrantDailyLoginBonusResponse grantDailyLoginBonusOnMainThread(GrantDailyLoginBonusRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        String username = request.getMinecraftUsername();
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player == null || player.hasDisconnected()) {
            return GrantDailyLoginBonusResponse.newBuilder()
                    .setGranted(false)
                    .setOnline(false)
                    .setMessage("You have to be online on the server to receive the money.")
                    .build();
        }

        int amount = Math.max(0, request.getAmount());
        if (!MoneyHelper.GainMoney(player, amount)) {
            return GrantDailyLoginBonusResponse.newBuilder()
                    .setGranted(false)
                    .setOnline(true)
                    .setMessage("Could not grant the daily login bonus.")
                    .build();
        }

        return GrantDailyLoginBonusResponse.newBuilder()
                .setGranted(true)
                .setOnline(true)
                .setMessage("You received " + amount + " dabloons.")
                .build();
    }

    private GrantGiftCodeMoneyResponse grantGiftCodeMoneyOnMainThread(GrantGiftCodeMoneyRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return GrantGiftCodeMoneyResponse.newBuilder()
                    .setGranted(false)
                    .setOnline(false)
                    .setMessage("You have to be online on the server to redeem a gift code.")
                    .build();
        }

        int amount = Math.max(0, request.getAmountDabloons());
        if (amount == 0 || !MoneyHelper.GainMoney(player, amount)) {
            return GrantGiftCodeMoneyResponse.newBuilder()
                    .setGranted(false)
                    .setOnline(true)
                    .setBalanceDabloons(MoneyHelper.GetBalance(player))
                    .setMessage("Could not grant the gift code dabloons.")
                    .build();
        }

        int balance = MoneyHelper.GetBalance(player);
        return GrantGiftCodeMoneyResponse.newBuilder()
                .setGranted(true)
                .setOnline(true)
                .setBalanceDabloons(balance)
                .setMessage("Gift code redeemed for " + amount + " dabloons.")
                .build();
    }

    private SubmitDailyItemsResponse submitDailyItemsOnMainThread(SubmitDailyItemsRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        String username = request.getMinecraftUsername();
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player == null || player.hasDisconnected()) {
            return SubmitDailyItemsResponse.newBuilder()
                    .setSubmitted(false)
                    .setOnline(false)
                    .setFoundCount(0)
                    .setMessage("You have to be online on the server to submit daily items.")
                    .build();
        }

        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(request.getItem()));
        int requiredCount = Math.max(1, request.getCount());
        int foundCount = countItem(player, item);

        if (foundCount < requiredCount) {
            return SubmitDailyItemsResponse.newBuilder()
                    .setSubmitted(false)
                    .setOnline(true)
                    .setFoundCount(foundCount)
                    .setMessage("You need " + requiredCount + "x " + request.getItem() + " in your inventory. You currently have " + foundCount + ".")
                    .build();
        }

        removeItem(player, item, requiredCount);

        int reward = Math.max(0, request.getRewardDabloons());
        if (!MoneyHelper.GainMoney(player, reward)) {
            return SubmitDailyItemsResponse.newBuilder()
                    .setSubmitted(false)
                    .setOnline(true)
                    .setFoundCount(foundCount)
                    .setMessage("Could not grant the daily item reward.")
                    .build();
        }

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        return SubmitDailyItemsResponse.newBuilder()
                .setSubmitted(true)
                .setOnline(true)
                .setFoundCount(foundCount)
                .setMessage("Submitted " + requiredCount + "x " + request.getItem() + " and received " + reward + " dabloons.")
                .build();
    }

    private PickDailyAdvancementResponse pickDailyAdvancementOnMainThread(PickDailyAdvancementRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        String username = request.getMinecraftUsername();
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player == null || player.hasDisconnected()) {
            return PickDailyAdvancementResponse.newBuilder()
                    .setSelected(false)
                    .setOnline(false)
                    .setMessage("You have to be online on the server to receive a daily advancement target.")
                    .build();
        }

        PlayerAdvancements playerAdvancements = player.getAdvancements();
        Set<AdvancementHolder> visible = playerAdvancements.visible;
        List<AdvancementHolder> candidates = new ArrayList<>();

        for (AdvancementHolder holder : visible) {
            if (holder.value().display().isEmpty()) {
                continue;
            }

            AdvancementProgress progress = playerAdvancements.getOrStartProgress(holder);
            if (!progress.isDone()) {
                candidates.add(holder);
            }
        }

        if (candidates.isEmpty()) {
            return PickDailyAdvancementResponse.newBuilder()
                    .setSelected(false)
                    .setOnline(true)
                    .setMessage("No visible incomplete advancements are available right now.")
                    .build();
        }

        candidates.sort(Comparator.comparing(holder -> holder.id().toString()));
        String seed = request.getPeriodKey() + ":" + normalize(username);
        AdvancementHolder selected = candidates.get(Math.floorMod(seed.hashCode(), candidates.size()));
        DisplayInfo display = selected.value().display().orElseThrow();
        AdvancementTree tree = playerAdvancements.tree;
        AdvancementNode node = tree.get(selected);
        AdvancementNode root = node == null ? null : node.root();

        String tabTitle = root == null
                ? selected.id().getNamespace()
                : root.holder().value().display()
                        .map(rootDisplay -> rootDisplay.getTitle().getString())
                        .orElse(root.holder().id().toString());

        int baseReward = AdvancementMoney.moneyForAdvancement(selected.id(), selected.value().rewards().experience());
        int bonusReward = Math.max(5, Math.min(42, baseReward));

        return PickDailyAdvancementResponse.newBuilder()
                .setSelected(true)
                .setOnline(true)
                .setAdvancementId(selected.id().toString())
                .setTitle(display.getTitle().getString())
                .setTabTitle(tabTitle)
                .setIconItem(BuiltInRegistries.ITEM.getKey(display.getIcon().item().value()).toString())
                .setBaseRewardDabloons(baseReward)
                .setBonusRewardDabloons(bonusReward)
                .setMessage("Daily advancement target selected.")
                .build();
    }

    private ClaimDailyAdvancementResponse claimDailyAdvancementOnMainThread(ClaimDailyAdvancementRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        String username = request.getMinecraftUsername();
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player == null || player.hasDisconnected()) {
            return ClaimDailyAdvancementResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(false)
                    .setCompleted(false)
                    .setMessage("You have to be online on the server to claim the daily advancement bonus.")
                    .build();
        }

        AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(request.getAdvancementId()));
        if (holder == null) {
            return ClaimDailyAdvancementResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(true)
                    .setCompleted(false)
                    .setMessage("That daily advancement is no longer available on the server.")
                    .build();
        }

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            String title = holder.value().display()
                    .map(display -> display.getTitle().getString())
                    .orElse(request.getAdvancementId());
            return ClaimDailyAdvancementResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(true)
                    .setCompleted(false)
                    .setMessage("Complete " + title + " in-game first, then claim this daily.")
                    .build();
        }

        int reward = Math.max(0, request.getBonusRewardDabloons());
        if (!MoneyHelper.GainMoney(player, reward)) {
            return ClaimDailyAdvancementResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(true)
                    .setCompleted(true)
                    .setMessage("Could not grant the daily advancement bonus.")
                    .build();
        }

        return ClaimDailyAdvancementResponse.newBuilder()
                .setClaimed(true)
                .setOnline(true)
                .setCompleted(true)
                .setMessage("You received " + reward + " bonus dabloons for completing " + request.getAdvancementId() + ".")
                .build();
    }

    private PurchaseShopItemResponse purchaseShopItemOnMainThread(PurchaseShopItemRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        String username = request.getMinecraftUsername();
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player == null || player.hasDisconnected()) {
            return PurchaseShopItemResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(false)
                    .setBalanceDabloons(0)
                    .setMessage("You have to be online on the server to buy from the shop.")
                    .build();
        }

        int price = Math.max(0, request.getPriceDabloons());
        int balance = MoneyHelper.GetBalance(player);
        if (balance < price) {
            return PurchaseShopItemResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(balance)
                    .setMessage("You need " + price + " dabloons, but only have " + balance + ".")
                    .build();
        }

        ItemStack grantStack = createShopGrantStack(request);
        if (grantStack == null) {
            return PurchaseShopItemResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(balance)
                    .setMessage("That shop item is not available on the server.")
                    .build();
        }

        if (!MoneyHelper.ReduceMoney(player, price)) {
            return PurchaseShopItemResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(MoneyHelper.GetBalance(player))
                    .setMessage("Could not take the dabloons for this purchase.")
                    .build();
        }

        if (!grantStack.isEmpty()) {
            player.getInventory().add(grantStack);
            if (!grantStack.isEmpty()) {
                player.drop(grantStack, false);
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }

        int remaining = MoneyHelper.GetBalance(player);
        return PurchaseShopItemResponse.newBuilder()
                .setPurchased(true)
                .setOnline(true)
                .setBalanceDabloons(remaining)
                .setMessage("Purchased " + request.getItemId() + " for " + price + " dabloons.")
                .build();
    }

    private ItemStack createShopGrantStack(PurchaseShopItemRequest request) {
        String deliveryKind = request.getDeliveryKind();
        String itemId = request.getItemId();

        if ("fake_item".equals(deliveryKind)) {
            if (!FakeItems.isKnownFakeItem(itemId)) {
                return null;
            }

            return FakeItems.createFakeItemStack(itemId, 1);
        }

        if ("vanilla_item".equals(deliveryKind)) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
            if (item == Items.AIR && !"minecraft:air".equals(itemId)) {
                return null;
            }

            return new ItemStack(item, 1);
        }

        return null;
    }

    private int countItem(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeItem(ServerPlayer player, Item item, int count) {
        int remaining = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) {
                continue;
            }

            int remove = Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            remaining -= remove;

            if (stack.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private final class GameplayControlEndpoint extends GameplayControlGrpc.GameplayControlImplBase {
        @Override
        public void grantDailyLoginBonus(
                GrantDailyLoginBonusRequest request,
                StreamObserver<GrantDailyLoginBonusResponse> responseObserver
        ) {
            callOnMainThread(() -> grantDailyLoginBonusOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void grantGiftCodeMoney(
                GrantGiftCodeMoneyRequest request,
                StreamObserver<GrantGiftCodeMoneyResponse> responseObserver
        ) {
            callOnMainThread(() -> grantGiftCodeMoneyOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void submitDailyItems(
                SubmitDailyItemsRequest request,
                StreamObserver<SubmitDailyItemsResponse> responseObserver
        ) {
            callOnMainThread(() -> submitDailyItemsOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void pickDailyAdvancement(
                PickDailyAdvancementRequest request,
                StreamObserver<PickDailyAdvancementResponse> responseObserver
        ) {
            callOnMainThread(() -> pickDailyAdvancementOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void claimDailyAdvancement(
                ClaimDailyAdvancementRequest request,
                StreamObserver<ClaimDailyAdvancementResponse> responseObserver
        ) {
            callOnMainThread(() -> claimDailyAdvancementOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void purchaseShopItem(
                PurchaseShopItemRequest request,
                StreamObserver<PurchaseShopItemResponse> responseObserver
        ) {
            callOnMainThread(() -> purchaseShopItemOnMainThread(request))
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
