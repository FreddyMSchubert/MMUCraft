package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class CraftItemTask extends CountedTask {
    private final String item;
    private final String itemName;

    public CraftItemTask(Item item, String itemName, String emoji, int min, int max, double rewardPerItem) {
        super("craft:" + DailyTargetId.of(item), emoji, min, max, rewardPerItem, "Crafted", itemName.toLowerCase());
        this.item = DailyTargetId.of(item);
        this.itemName = itemName;
    }

    @Override protected String name(int count) { return "Craft " + itemName; }
    @Override protected String description(int count) { return "Craft " + count + " " + itemName.toLowerCase() + "."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.CRAFT_ITEM && event.subject().equals(item);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("item", item); }
}
