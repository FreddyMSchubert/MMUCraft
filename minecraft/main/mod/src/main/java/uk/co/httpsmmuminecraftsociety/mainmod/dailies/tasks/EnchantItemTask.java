package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class EnchantItemTask extends CountedTask {
    private final String itemType;
    private final String taskName;
    private final String taskDescription;

    public EnchantItemTask(
            TagKey<Item> itemType,
            String taskName,
            String taskDescription,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerItem
    ) {
        this(DailyTargetId.of(itemType), taskName, taskDescription, emoji, minimum, maximum, rewardPerItem);
    }

    public EnchantItemTask(
            Item itemType,
            String taskName,
            String taskDescription,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerItem
    ) {
        this(DailyTargetId.of(itemType), taskName, taskDescription, emoji, minimum, maximum, rewardPerItem);
    }

    private EnchantItemTask(
            String itemType,
            String taskName,
            String taskDescription,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerItem
    ) {
        super("enchant_item:" + itemType, emoji, minimum, maximum, rewardPerItem, "Enchanted", "items");
        this.itemType = itemType;
        this.taskName = taskName;
        this.taskDescription = taskDescription;
    }

    @Override protected String name(int count) { return taskName; }
    @Override protected String description(int count) {
        return taskDescription.replace("{count}", Integer.toString(count));
    }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.ENCHANT_ITEM && event.subject().equals(itemType);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) {
        task.addProperty("itemType", itemType);
    }
}
