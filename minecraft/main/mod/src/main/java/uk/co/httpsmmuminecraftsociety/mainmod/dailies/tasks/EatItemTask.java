package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class EatItemTask extends CountedTask {
    private final String item;
    private final String itemName;

    public EatItemTask(Item item, String itemName, String emoji, int min, int max, double rewardPerItem) {
        super("eat:" + DailyTargetId.of(item), emoji, min, max, rewardPerItem, "Eaten", itemName.toLowerCase());
        this.item = DailyTargetId.of(item);
        this.itemName = itemName;
    }

    @Override protected String name(int count) { return "Eat " + itemName; }
    @Override protected String description(int count) { return "Eat " + count + " " + itemName.toLowerCase() + "."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.USE_ITEM && event.subject().equals(item) && event.secondary().equals("eat");
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("item", item); }
}
