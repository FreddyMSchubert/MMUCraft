package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.Random;

public final class ItemSubmissionTask implements DailyTaskDefinition {
    private final String id;
    private final Item item;
    private final String customItemId;
    private final String itemId;
    private final String itemName;
    private final String description;
    private final String emoji;
    private final int minimum;
    private final int maximum;
    private final double rewardPerItem;

    public ItemSubmissionTask(
            Item item,
            String itemName,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerItem
    ) {
        this(item, itemName, null, emoji, minimum, maximum, rewardPerItem);
    }

    public ItemSubmissionTask(
            Item item,
            String itemName,
            String description,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerItem
    ) {
        this("submit:" + DailyTargetId.of(item), item, null, DailyTargetId.of(item), itemName, description, emoji, minimum, maximum, rewardPerItem);
    }

    public static ItemSubmissionTask custom(
            String fakeItemId,
            String itemName,
            String description,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerItem
    ) {
        FakeItems.requireFakeItem(fakeItemId);
        return new ItemSubmissionTask(
                "submit:fake:" + fakeItemId,
                null,
                fakeItemId,
                fakeItemId,
                itemName,
                description,
                emoji,
                minimum,
                maximum,
                rewardPerItem
        );
    }

    private ItemSubmissionTask(
            String id,
            Item item,
            String customItemId,
            String itemId,
            String itemName,
            String description,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerItem
    ) {
        if (minimum < 1 || maximum < minimum || !Double.isFinite(rewardPerItem) || rewardPerItem < 0.0D) {
            throw new IllegalArgumentException("Invalid item submission daily settings for " + itemId);
        }
        this.id = id;
        this.item = item;
        this.customItemId = customItemId;
        this.itemId = itemId;
        this.itemName = itemName;
        this.description = description;
        this.emoji = emoji;
        this.minimum = minimum;
        this.maximum = maximum;
        this.rewardPerItem = rewardPerItem;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public JsonObject create(Random random) {
        int count = random.nextInt(minimum, maximum + 1);
        String instructions = "Hold " + count + "× " + itemName
                + " in your inventory, then click Claim to trade the items in.";
        JsonObject task = CountedTask.base(
                id,
                "Submit " + itemName,
                instructions,
                emoji,
                reward(count),
                -1
        );
        task.addProperty("item", itemId);
        task.addProperty("customItem", customItemId != null);
        task.addProperty("requiredCount", count);
        task.addProperty("rewardPerIteration", rewardPerItem);
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

    private int reward(int count) {
        return Math.max(1, (int)Math.round(count * rewardPerItem));
    }

    private int count(ServerPlayer player) {
        int total = 0;
        ItemStack customTemplate = customTemplate();
        for (ItemStack stack : player.getInventory()) if (matches(stack, customTemplate)) total += stack.getCount();
        return total;
    }

    private void remove(ServerPlayer player, int count) {
        ItemStack customTemplate = customTemplate();
        for (int slot = 0; slot < player.getInventory().getContainerSize() && count > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!matches(stack, customTemplate)) continue;
            int removed = Math.min(count, stack.getCount());
            stack.shrink(removed);
            count -= removed;
        }
    }

    private ItemStack customTemplate() {
        return customItemId == null ? null : FakeItems.requireFakeItem(customItemId).createItemStack();
    }

    private boolean matches(ItemStack stack, ItemStack customTemplate) {
        return item != null ? stack.is(item) : ItemStack.isSameItemSameComponents(stack, customTemplate);
    }
}
