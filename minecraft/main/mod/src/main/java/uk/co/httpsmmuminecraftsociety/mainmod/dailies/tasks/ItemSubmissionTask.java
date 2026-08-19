package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.function.BiPredicate;

public final class ItemSubmissionTask implements DailyTaskDefinition {
    private final String id;
    private final Item item;
    private final String customItemId;
    private final String itemId;
    private final BiPredicate<ServerPlayer, ItemStack> matcher;
    public ItemSubmissionTask(Item item) {
        this("submit:" + DailyTargetId.of(item), item, null, DailyTargetId.of(item), (player, stack) -> true);
    }

    public static ItemSubmissionTask matching(String idSuffix, Item item, BiPredicate<ServerPlayer, ItemStack> matcher) {
        return new ItemSubmissionTask("submit:" + DailyTargetId.of(item) + ":" + idSuffix, item, null, DailyTargetId.of(item), matcher);
    }

    public static ItemSubmissionTask custom(String fakeItemId) {
        FakeItems.requireFakeItem(fakeItemId);
        return new ItemSubmissionTask(
                "submit:fake:" + fakeItemId,
                null,
                fakeItemId,
                fakeItemId,
                (player, stack) -> true
        );
    }

    private ItemSubmissionTask(
            String id,
            Item item,
            String customItemId,
            String itemId,
            BiPredicate<ServerPlayer, ItemStack> matcher
    ) {
        this.id = id;
        this.item = item;
        this.customItemId = customItemId;
        this.itemId = itemId;
        this.matcher = matcher;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public JsonObject create(int count) {
        JsonObject task = CountedTask.base(id, -1);
        task.addProperty("item", itemId);
        task.addProperty("customItem", customItemId != null);
        task.addProperty("requiredCount", count);
        return task;
    }

    @Override
    public int getReward(JsonObject task) {
        return task.get("rewardDabloons").getAsInt();
    }

    @Override
    public ClaimResult claim(ServerPlayer player, JsonObject task) {
        int requiredCount = task.get("requiredCount").getAsInt();
        int found = count(player);
        if (found < requiredCount) {
            return ClaimResult.failure("You need " + requiredCount + " items, but you only have " + found + ".");
        }

        ClaimResult reward = DailyTaskDefinition.super.claim(player, task);
        if (!reward.claimed()) return reward;
        remove(player, requiredCount);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return ClaimResult.success("Submitted " + requiredCount + " items and received " + getReward(task) + " dabloons.");
    }

    private int count(ServerPlayer player) {
        int total = 0;
        ItemStack customTemplate = customTemplate();
        for (ItemStack stack : player.getInventory()) if (matches(player, stack, customTemplate)) total += stack.getCount();
        return total;
    }

    private void remove(ServerPlayer player, int count) {
        ItemStack customTemplate = customTemplate();
        for (int slot = 0; slot < player.getInventory().getContainerSize() && count > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!matches(player, stack, customTemplate)) continue;
            int removed = Math.min(count, stack.getCount());
            stack.shrink(removed);
            count -= removed;
        }
    }

    private ItemStack customTemplate() {
        return customItemId == null ? null : FakeItems.requireFakeItem(customItemId).createItemStack();
    }

    private boolean matches(ServerPlayer player, ItemStack stack, ItemStack customTemplate) {
        return (item != null ? stack.is(item) : ItemStack.isSameItemSameComponents(stack, customTemplate))
                && matcher.test(player, stack);
    }
}
