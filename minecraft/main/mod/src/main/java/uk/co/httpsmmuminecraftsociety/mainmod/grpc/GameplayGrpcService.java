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
            String sourceItemId
    ) {
        return INSTANCE.unlockNextKnowledgeInternal(minecraftUsername, sourceItemId);
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

    private CompletableFuture<UnlockNextKnowledgeResponse> unlockNextKnowledgeInternal(
            String minecraftUsername,
            String sourceItemId
    ) {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;

        if (client == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GameplayEvents gRPC client is not initialized")
            );
        }

        UnlockNextKnowledgeRequest request = UnlockNextKnowledgeRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setSourceItemId(sourceItemId)
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

        int baseReward = AdvancementMoney.moneyForExperience(selected.value().rewards().experience());

        return PickDailyAdvancementResponse.newBuilder()
                .setSelected(true)
                .setOnline(true)
                .setAdvancementId(selected.id().toString())
                .setTitle(display.getTitle().getString())
                .setTabTitle(tabTitle)
                .setIconItem(BuiltInRegistries.ITEM.getKey(display.getIcon().item().value()).toString())
                .setBaseRewardDabloons(baseReward)
                .setBonusRewardDabloons(baseReward * 2)
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
