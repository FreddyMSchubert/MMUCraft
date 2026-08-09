package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs.StackDef;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs.TagStackDef;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmLevelDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.discord.DiscordBridge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GameplayGrpcService extends GrpcHandler {
    static final GameplayGrpcService INSTANCE = new GameplayGrpcService();
    private static final int EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS = 100;

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

    public static CompletableFuture<RecordFishCatchResponse> recordFishCatch(
            ServerPlayer player,
            String fishId,
            double lengthCm,
            String rarity
    ) {
        return INSTANCE.recordFishCatchInternal(
                player.getName().getString(),
                player.getUUID().toString(),
                fishId,
                lengthCm,
                rarity
        );
    }

    public static CompletableFuture<UpdateDailyTaskResponse> updateDailyTask(
            int userId,
            String periodKey,
            String taskJson
    ) {
        return INSTANCE.updateDailyTaskInternal(userId, periodKey, taskJson);
    }

    public static CompletableFuture<PublishDiscordEventResponse> publishDiscordEvent(PublishDiscordEventRequest request) {
        return INSTANCE.publishDiscordEventInternal(request);
    }

    @Override
    List<BindableService> serverServices() {
        return List.of(new GameplayControlEndpoint());
    }

    @Override
    void start(ManagedChannel apiChannel) {
        gameplayEvents = GameplayEventsGrpc.newFutureStub(apiChannel);
        requestClaimsSnapshot();
        requestDailyTasksSnapshot();
    }

    @Override
    void stop() {
        gameplayEvents = null;
    }

    private void requestClaimsSnapshot() {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;
        if (client == null) return;

        var rpc = client.withDeadlineAfter(5, TimeUnit.SECONDS)
                .getClaimsSnapshot(GetClaimsSnapshotRequest.getDefaultInstance());
        rpc.addListener(() -> {
            try {
                ClaimsSnapshot snapshot = rpc.get();
                runOnMainThread(() -> ClaimsManager.apply(snapshot));
                MainMod.LOGGER.info("Loaded {} chunk claims", snapshot.getClaimsCount());
            } catch (Exception exception) {
                MainMod.LOGGER.warn("Could not load claims; retrying in 5 seconds", exception);
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(this::requestClaimsSnapshot);
            }
        }, Runnable::run);
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

        var rpc = client.withDeadlineAfter(5, TimeUnit.SECONDS).syncPlayerStats(request);
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

    private CompletableFuture<RecordFishCatchResponse> recordFishCatchInternal(
            String minecraftUsername,
            String minecraftUuid,
            String fishId,
            double lengthCm,
            String rarity
    ) {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;
        if (client == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GameplayEvents gRPC client is not initialized")
            );
        }

        RecordFishCatchRequest request = RecordFishCatchRequest.newBuilder()
                .setMinecraftUsername(minecraftUsername)
                .setMinecraftUuid(minecraftUuid)
                .setFishId(fishId)
                .setLengthCm(lengthCm)
                .setRarity(rarity)
                .setUnixMs(System.currentTimeMillis())
                .build();
        var rpc = client.recordFishCatch(request);
        CompletableFuture<RecordFishCatchResponse> result = new CompletableFuture<>();
        rpc.addListener(() -> {
            try {
                result.complete(rpc.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }, Runnable::run);
        return result;
    }

    private CompletableFuture<PublishDiscordEventResponse> publishDiscordEventInternal(PublishDiscordEventRequest request) {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;
        if (client == null) return CompletableFuture.failedFuture(new IllegalStateException("GameplayEvents gRPC client is not initialized"));
        var rpc = client.publishDiscordEvent(request);
        CompletableFuture<PublishDiscordEventResponse> result = new CompletableFuture<>();
        rpc.addListener(() -> {
            try {
                result.complete(rpc.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }, Runnable::run);
        return result;
    }

    private void requestDailyTasksSnapshot() {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;
        if (client == null) return;

        var rpc = client.withDeadlineAfter(5, TimeUnit.SECONDS)
                .getDailyTasksSnapshot(GetDailyTasksSnapshotRequest.getDefaultInstance());
        rpc.addListener(() -> {
            try {
                DailyTasksSnapshot snapshot = rpc.get();
                runOnMainThread(() -> DailyTaskManager.apply(snapshot));
                MainMod.LOGGER.info("Loaded {} active daily tasks", snapshot.getTasksCount());
            } catch (Exception exception) {
                MainMod.LOGGER.warn("Could not load daily tasks; retrying in 5 seconds", exception);
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(this::requestDailyTasksSnapshot);
            }
        }, Runnable::run);
    }

    private CompletableFuture<UpdateDailyTaskResponse> updateDailyTaskInternal(
            int userId,
            String periodKey,
            String taskJson
    ) {
        GameplayEventsGrpc.GameplayEventsFutureStub client = gameplayEvents;
        if (client == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("GameplayEvents gRPC client is not initialized"));
        }

        var rpc = client.withDeadlineAfter(5, TimeUnit.SECONDS).updateDailyTask(
                UpdateDailyTaskRequest.newBuilder()
                        .setUserId(userId)
                        .setPeriodKey(periodKey)
                        .setTaskJson(taskJson)
                        .setUnixMs(System.currentTimeMillis())
                        .build()
        );
        CompletableFuture<UpdateDailyTaskResponse> result = new CompletableFuture<>();
        rpc.addListener(() -> {
            try {
                result.complete(rpc.get());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }, Runnable::run);
        return result;
    }

    private record OfferedStack(int slot, ItemStack stack) {}
    private record CountedIngredient(StackDef ingredient, int count) {}

    private GetCharmInventoryResponse getCharmInventoryOnMainThread(GetCharmInventoryRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return GetCharmInventoryResponse.newBuilder()
                    .setOnline(false)
                    .setMessage("You have to be online on the server to view your charms.")
                    .build();
        }

        GetCharmInventoryResponse.Builder response = GetCharmInventoryResponse.newBuilder()
                .setOnline(true)
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setMessage("Charm inventory loaded.");

        int slot = player.getInventory().getSelectedSlot();
        ItemStack stack = player.getInventory().getItem(slot);
        StoredCharmData stored = CharmStackData.getSingleStoredCharm(stack).orElse(null);
        FakeItem item = stored == null ? null : FakeItems.CHARM_ID_MAP.get(stored.charmId());
        CharmItemFeature feature = item == null ? null : item.getFeature(CharmItemFeature.class);
        if (feature != null) {
            response.addCharms(buildInventoryCharm(player, slot, item, feature, stored.level()));
        } else {
            response.setMessage("Hold one charm in your main hand, then refresh the forge.");
        }

        return response.build();
    }

    private InventoryCharm buildInventoryCharm(
            ServerPlayer player,
            int slot,
            FakeItem item,
            CharmItemFeature feature,
            int currentLevel
    ) {
        InventoryCharm.Builder charm = InventoryCharm.newBuilder()
                .setItemId(item.id())
                .setTitle(item.title())
                .setCurrentLevel(currentLevel)
                .setMaxLevel(feature.maxLevel())
                .setCurrentAbility(feature.getLevelDefinition(currentLevel).abilityStatusCurrent());

        if (!feature.hasNextLevel(currentLevel)) {
            return charm.setTargetLevel(currentLevel).build();
        }

        int targetLevel = currentLevel + 1;
        CharmLevelDefinition target = feature.getLevelDefinition(targetLevel);
        charm.setTargetLevel(targetLevel)
                .setPriceDabloons(target.dabloons())
                .setNextAbility(target.abilityStatusRelative());

        Map<String, CountedIngredient> grouped = new LinkedHashMap<>();
        for (StackDef ingredient : target.upgradeIngredients()) {
            grouped.merge(
                    ingredient.raw(),
                    new CountedIngredient(ingredient, 1),
                    (left, right) -> new CountedIngredient(left.ingredient(), left.count() + 1)
            );
        }

        for (CountedIngredient counted : grouped.values()) {
            StackDef ingredient = counted.ingredient();
            ItemStack iconStack = firstMatchingStack(player, slot, ingredient);
            if (iconStack.isEmpty() && ingredient.canCreateStack()) {
                iconStack = ingredient.createStack();
            }
            String displayName = ingredient instanceof TagStackDef && !ingredient.hasDisplayNameOverride()
                    ? "Any " + ingredient.getDisplayName()
                    : ingredient.getDisplayName();

            charm.addIngredients(CharmUpgradeIngredient.newBuilder()
                    .setRaw(ingredient.raw())
                    .setDisplayName(displayName)
                    .setIconItemId(iconStack.isEmpty() ? "" : iconItemId(iconStack))
                    .setRequiredCount(counted.count())
                    .setInventoryCount(countMatchingItems(player, slot, ingredient))
                    .build());
        }

        return charm.build();
    }

    private UpgradeCharmResponse upgradeCharmOnMainThread(UpgradeCharmRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return UpgradeCharmResponse.newBuilder()
                    .setOnline(false)
                    .setMessage("You have to be online on the server to upgrade a charm.")
                    .build();
        }

        int slot = player.getInventory().getSelectedSlot();
        ItemStack stack = player.getInventory().getItem(slot);
        StoredCharmData stored = CharmStackData.getSingleStoredCharm(stack).orElse(null);
        FakeItem item = stored == null ? null : FakeItems.CHARM_ID_MAP.get(stored.charmId());
        CharmItemFeature feature = item == null ? null : item.getFeature(CharmItemFeature.class);
        if (stored == null || item == null || feature == null
                || !item.id().equals(request.getItemId())
                || stored.level() != request.getExpectedLevel()) {
            return failedCharmUpgrade(player, "Your main-hand charm changed. Refresh the forge and try again.");
        }
        if (!feature.hasNextLevel(stored.level())) {
            return failedCharmUpgrade(player, "That charm is already at its maximum level.");
        }

        int targetLevel = stored.level() + 1;
        CharmLevelDefinition target = feature.getLevelDefinition(targetLevel);
        int[] consumeCounts = findIngredientAssignment(player, slot, target.upgradeIngredients());
        if (consumeCounts == null) {
            return failedCharmUpgrade(player, "You no longer have all the required ingredients.");
        }
        if (MoneyHelper.GetBalance(player) < target.dabloons()) {
            return failedCharmUpgrade(player, "You need " + target.dabloons() + " dabloons for this upgrade.");
        }

        NonNullList<ItemStack> backup = NonNullList.withSize(player.getInventory().getContainerSize(), ItemStack.EMPTY);
        for (int inventorySlot = 0; inventorySlot < backup.size(); inventorySlot++) {
            backup.set(inventorySlot, player.getInventory().getItem(inventorySlot).copy());
        }

        try {
            for (int inventorySlot = 0; inventorySlot < consumeCounts.length; inventorySlot++) {
                int count = consumeCounts[inventorySlot];
                if (count > 0) player.getInventory().getItem(inventorySlot).shrink(count);
            }
            if (target.dabloons() > 0 && !MoneyHelper.ReduceMoney(player, target.dabloons())) {
                restoreInventory(player, backup);
                return failedCharmUpgrade(player, "Could not take the dabloons for this upgrade.");
            }

            feature.setLevel(stack, targetLevel);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        } catch (RuntimeException exception) {
            restoreInventory(player, backup);
            throw exception;
        }

        DiscordBridge.playerEvent("charm", player,
                "upgraded " + item.title() + " to level " + targetLevel + ". [New effect: "
                        + target.abilityStatusCurrent() + "]");

        return UpgradeCharmResponse.newBuilder()
                .setUpgraded(true)
                .setOnline(true)
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setNewLevel(targetLevel)
                .setMessage(item.title() + " reached level " + targetLevel + ".")
                .build();
    }

    private UpgradeCharmResponse failedCharmUpgrade(ServerPlayer player, String message) {
        return UpgradeCharmResponse.newBuilder()
                .setOnline(true)
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setMessage(message)
                .build();
    }

    private void restoreInventory(ServerPlayer player, List<ItemStack> backup) {
        for (int slot = 0; slot < backup.size(); slot++) {
            player.getInventory().setItem(slot, backup.get(slot).copy());
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private int[] findIngredientAssignment(ServerPlayer player, int charmSlot, List<StackDef> ingredients) {
        List<OfferedStack> offered = new ArrayList<>();
        int[] remainingCounts = new int[player.getInventory().getContainerSize()];
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (slot == charmSlot || stack.isEmpty()) continue;
            offered.add(new OfferedStack(slot, stack));
            remainingCounts[slot] = stack.getCount();
        }

        int[] consumeCounts = new int[remainingCounts.length];
        List<StackDef> ordered = ingredients.stream()
                .sorted(Comparator.comparingInt(StackDef::specificity).reversed())
                .toList();
        return assignIngredients(ordered, 0, offered, remainingCounts, consumeCounts)
                ? consumeCounts
                : null;
    }

    private boolean assignIngredients(
            List<StackDef> ingredients,
            int ingredientIndex,
            List<OfferedStack> offered,
            int[] remainingCounts,
            int[] consumeCounts
    ) {
        if (ingredientIndex >= ingredients.size()) return true;
        StackDef required = ingredients.get(ingredientIndex);

        for (OfferedStack candidate : offered) {
            int slot = candidate.slot();
            if (remainingCounts[slot] <= 0 || !required.matches(candidate.stack())) continue;

            remainingCounts[slot]--;
            consumeCounts[slot]++;
            if (assignIngredients(ingredients, ingredientIndex + 1, offered, remainingCounts, consumeCounts)) {
                return true;
            }
            remainingCounts[slot]++;
            consumeCounts[slot]--;
        }
        return false;
    }

    private int countMatchingItems(ServerPlayer player, int excludedSlot, StackDef ingredient) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (slot != excludedSlot && ingredient.matches(stack)) count += stack.getCount();
        }
        return count;
    }

    private ItemStack firstMatchingStack(ServerPlayer player, int excludedSlot, StackDef ingredient) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (slot != excludedSlot && ingredient.matches(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private String iconItemId(ItemStack stack) {
        FakeItem fakeItem = FakeItems.getFakeItemFromStack(stack);
        return fakeItem == null
                ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                : "mainmod:" + fakeItem.id();
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

        if ("daily_completion".equals(request.getSource())) {
            DiscordBridge.playerEvent("dailies", player,
                    "completed all of today's dailies.");
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

    private GenerateDailyTasksResponse generateDailyTasksOnMainThread(GenerateDailyTasksRequest request) {
        List<String> tasks = DailyTaskManager.generate(
                request.getUserId(),
                request.getMinecraftUsername(),
                request.getPeriodKey(),
                request.getCount()
        );
        return GenerateDailyTasksResponse.newBuilder()
                .setGenerated(true)
                .addAllTaskJson(tasks)
                .setMessage("Daily tasks generated.")
                .build();
    }

    private ClaimDailyTaskResponse claimDailyTaskOnMainThread(ClaimDailyTaskRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return ClaimDailyTaskResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(false)
                    .setMessage("You have to be online on the server to claim this daily.")
                    .build();
        }

        DailyTaskDefinition.ClaimResult result = DailyTaskManager.claim(
                player,
                request.getUserId(),
                request.getPeriodKey(),
                request.getTaskJson()
        );
        return ClaimDailyTaskResponse.newBuilder()
                .setClaimed(result.claimed())
                .setOnline(true)
                .setTaskJson(request.getTaskJson())
                .setMessage(result.message())
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
				.setIconItem(iconItemId(display.getIcon().create()))
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
        DiscordBridge.playerEvent("shop", player,
                "bought the " + request.getRarity() + " " + request.getDisplayName() + " "
                        + request.getItemType() + " from the shop for " + price + " dabloons.");
        return PurchaseShopItemResponse.newBuilder()
                .setPurchased(true)
                .setOnline(true)
                .setBalanceDabloons(remaining)
                .setMessage("Purchased " + request.getItemId() + " for " + price + " dabloons.")
                .build();
    }

    private PurchaseExternalPlayerInviteResponse purchaseExternalPlayerInviteOnMainThread(
            PurchaseExternalPlayerInviteRequest request
    ) {
        MinecraftServer server = minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return PurchaseExternalPlayerInviteResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(false)
                    .setMessage("The responsible player must be online to pay for this invitation.")
                    .build();
        }

        int balance = MoneyHelper.GetBalance(player);
        if (balance < EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS
                || !MoneyHelper.ReduceMoney(player, EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS)) {
            return PurchaseExternalPlayerInviteResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(balance)
                    .setMessage("The responsible player needs 100 dabloons for this invitation.")
                    .build();
        }

        int remaining = MoneyHelper.GetBalance(player);
        return PurchaseExternalPlayerInviteResponse.newBuilder()
                .setPurchased(true)
                .setOnline(true)
                .setBalanceDabloons(remaining)
                .setMessage("External player invitation purchased for 100 dabloons.")
                .build();
    }

    private GetCurrentClaimChunkResponse getCurrentClaimChunkOnMainThread(GetCurrentClaimChunkRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return GetCurrentClaimChunkResponse.newBuilder()
                    .setOnline(false)
                    .setMessage("You have to be online on the server to claim a chunk.")
                    .build();
        }

        ChunkPos chunk = player.chunkPosition();
        return GetCurrentClaimChunkResponse.newBuilder()
                .setOnline(true)
                .setDimension(player.level().dimension().identifier().toString())
                .setChunkX(chunk.x())
                .setChunkZ(chunk.z())
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setMessage("Current chunk loaded.")
                .build();
    }

    private PurchaseClaimResponse purchaseClaimOnMainThread(PurchaseClaimRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return PurchaseClaimResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(false)
                    .setMessage("You have to be online on the server to buy a claim.")
                    .build();
        }

        ChunkPos current = player.chunkPosition();
        String dimension = player.level().dimension().identifier().toString();
        if (!dimension.equals(request.getDimension())
                || current.x() != request.getChunkX()
                || current.z() != request.getChunkZ()) {
            return PurchaseClaimResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(MoneyHelper.GetBalance(player))
                    .setMessage("Stay in the displayed chunk until the purchase finishes.")
                    .build();
        }

        int price = request.getPriceDabloons();
        if (price <= 0) {
            return PurchaseClaimResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(MoneyHelper.GetBalance(player))
                    .setMessage("The claim price is invalid.")
                    .build();
        }

        int balance = MoneyHelper.GetBalance(player);
        if (balance < price || !MoneyHelper.ReduceMoney(player, price)) {
            return PurchaseClaimResponse.newBuilder()
                    .setPurchased(false)
                    .setOnline(true)
                    .setBalanceDabloons(balance)
                    .setMessage("You need " + price + " dabloons to buy this claim.")
                    .build();
        }

        return PurchaseClaimResponse.newBuilder()
                .setPurchased(true)
                .setOnline(true)
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setMessage("Chunk claimed for " + price + " dabloons.")
                .build();
    }

    private ApplyPlayerColorResponse applyPlayerColorOnMainThread(ApplyPlayerColorRequest request) {
        if (!request.getColorHex().matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Invalid player color");
        }
        UUID playerId = UUID.fromString(request.getMinecraftUuid());
        int color = Integer.parseInt(request.getColorHex().replace("#", ""), 16);
        ClaimsManager.updateOwnerColor(playerId, color);
        ServerPlayer player = minecraftServer().getPlayerList().getPlayer(playerId);
        if (player != null && !player.hasDisconnected()) PlayerStatsSync.applyColor(player, color);
        return ApplyPlayerColorResponse.newBuilder().setApplied(true).build();
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

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private BroadcastDiscordMessageResponse broadcastDiscordMessageOnMainThread(BroadcastDiscordMessageRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        String name = request.getDiscordName().strip().replaceAll("[\\r\\n]", " ");
        String content = request.getContent().strip().replaceAll("[\\r\\n]+", " ");
        Component message = Component.literal("[Discord] ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(name + ": ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(content));
        DiscordBridge.broadcastFromDiscord(server, message);
        return BroadcastDiscordMessageResponse.newBuilder().setBroadcast(true).build();
    }

    private RunServerCommandResponse runServerCommandOnMainThread(RunServerCommandRequest request) {
        MinecraftServer server = minecraftServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        List<String> output = new ArrayList<>();
        AtomicBoolean succeeded = new AtomicBoolean();
        AtomicInteger result = new AtomicInteger();
        CommandSource capture = new CommandSource() {
            @Override public void sendSystemMessage(Component message) {
                if (output.stream().mapToInt(String::length).sum() < 16_000) output.add(message.getString());
            }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        String command = request.getCommand().strip().replaceFirst("^/+", "");
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSource(capture).withCallback((success, value) -> {
                    succeeded.set(success);
                    result.set(value);
                }),
                command
        );
        MainMod.LOGGER.info("Discord admin {} ran server command: {}", request.getDiscordUser(), command);
        return RunServerCommandResponse.newBuilder()
                .setSucceeded(succeeded.get())
                .setResult(result.get())
                .setOutput(output.isEmpty() ? "Command returned " + result.get() + "." : String.join("\n", output))
                .build();
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
        public void generateDailyTasks(
                GenerateDailyTasksRequest request,
                StreamObserver<GenerateDailyTasksResponse> responseObserver
        ) {
            callOnMainThread(() -> generateDailyTasksOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void claimDailyTask(
                ClaimDailyTaskRequest request,
                StreamObserver<ClaimDailyTaskResponse> responseObserver
        ) {
            callOnMainThread(() -> claimDailyTaskOnMainThread(request))
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

        @Override
        public void purchaseExternalPlayerInvite(
                PurchaseExternalPlayerInviteRequest request,
                StreamObserver<PurchaseExternalPlayerInviteResponse> responseObserver
        ) {
            callOnMainThread(() -> purchaseExternalPlayerInviteOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void getCurrentClaimChunk(
                GetCurrentClaimChunkRequest request,
                StreamObserver<GetCurrentClaimChunkResponse> responseObserver
        ) {
            callOnMainThread(() -> getCurrentClaimChunkOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void purchaseClaim(
                PurchaseClaimRequest request,
                StreamObserver<PurchaseClaimResponse> responseObserver
        ) {
            callOnMainThread(() -> purchaseClaimOnMainThread(request))
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
        public void applyPlayerColor(
                ApplyPlayerColorRequest request,
                StreamObserver<ApplyPlayerColorResponse> responseObserver
        ) {
            callOnMainThread(() -> applyPlayerColorOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void getCharmInventory(
                GetCharmInventoryRequest request,
                StreamObserver<GetCharmInventoryResponse> responseObserver
        ) {
            callOnMainThread(() -> getCharmInventoryOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void upgradeCharm(
                UpgradeCharmRequest request,
                StreamObserver<UpgradeCharmResponse> responseObserver
        ) {
            callOnMainThread(() -> upgradeCharmOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void broadcastDiscordMessage(
                BroadcastDiscordMessageRequest request,
                StreamObserver<BroadcastDiscordMessageResponse> responseObserver
        ) {
            callOnMainThread(() -> broadcastDiscordMessageOnMainThread(request))
                    .whenComplete((response, error) -> complete(responseObserver, response, error));
        }

        @Override
        public void runServerCommand(
                RunServerCommandRequest request,
                StreamObserver<RunServerCommandResponse> responseObserver
        ) {
            callOnMainThread(() -> runServerCommandOnMainThread(request))
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
