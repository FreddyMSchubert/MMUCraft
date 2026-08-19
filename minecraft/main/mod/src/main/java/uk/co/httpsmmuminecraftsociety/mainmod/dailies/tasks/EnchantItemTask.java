package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class EnchantItemTask extends CountedTask {
    private final String itemType;
    public EnchantItemTask(TagKey<Item> itemType) {
        this(DailyTargetId.of(itemType));
    }

    public EnchantItemTask(Item itemType) {
        this(DailyTargetId.of(itemType));
    }

    private EnchantItemTask(String itemType) {
        super("enchant_item:" + itemType, "Enchanted", "items");
        this.itemType = itemType;
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.ENCHANT_ITEM && event.subject().equals(itemType);
    }
    @Override protected void addTaskData(JsonObject task) {
        task.addProperty("itemType", itemType);
    }
}
