package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs.StackDef;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs.TagStackDef;
import uk.co.httpsmmuminecraftsociety.mainmod.discord.DiscordBridge;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmLevelDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
final class GameplayCharmOperations {
    private GameplayCharmOperations() {}

    private record OfferedStack(int slot, ItemStack stack) {}
    private record CountedIngredient(StackDef ingredient, int count) {}

    static GetCharmInventoryResponse getCharmInventoryOnMainThread(GetCharmInventoryRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
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

	private static InventoryCharm buildInventoryCharm(
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
                    .setIconItemId(iconStack.isEmpty() ? "" : MinecraftItemIdentifier.forStack(iconStack))
                    .setRequiredCount(counted.count())
                    .setInventoryCount(countMatchingItems(player, slot, ingredient))
                    .build());
        }

        return charm.build();
    }

    static UpgradeCharmResponse upgradeCharmOnMainThread(UpgradeCharmRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
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

        if (target.dabloons() > 0) {
            MoneyHelper.SendBalanceMessage(player, item.title() + " reached level " + targetLevel + ".");
        }

        return UpgradeCharmResponse.newBuilder()
                .setUpgraded(true)
                .setOnline(true)
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setNewLevel(targetLevel)
                .setMessage(item.title() + " reached level " + targetLevel + ".")
                .build();
    }

	private static UpgradeCharmResponse failedCharmUpgrade(ServerPlayer player, String message) {
        return UpgradeCharmResponse.newBuilder()
                .setOnline(true)
                .setBalanceDabloons(MoneyHelper.GetBalance(player))
                .setMessage(message)
                .build();
    }

	private static void restoreInventory(ServerPlayer player, List<ItemStack> backup) {
        for (int slot = 0; slot < backup.size(); slot++) {
            player.getInventory().setItem(slot, backup.get(slot).copy());
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

	private static int[] findIngredientAssignment(ServerPlayer player, int charmSlot, List<StackDef> ingredients) {
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

	private static boolean assignIngredients(
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

	private static int countMatchingItems(ServerPlayer player, int excludedSlot, StackDef ingredient) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (slot != excludedSlot && ingredient.matches(stack)) count += stack.getCount();
        }
        return count;
    }

	private static ItemStack firstMatchingStack(ServerPlayer player, int excludedSlot, StackDef ingredient) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (slot != excludedSlot && ingredient.matches(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }
}
