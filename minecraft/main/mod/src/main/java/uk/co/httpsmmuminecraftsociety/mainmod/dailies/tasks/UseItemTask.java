package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class UseItemTask extends CountedTask {
    private final String item;
    private final String itemName;

    public UseItemTask(Item item, String itemName, String emoji, int min, int max, double rewardPerUse) {
        super("use:" + DailyTargetId.of(item), emoji, min, max, rewardPerUse, "Used", "times");
        this.item = DailyTargetId.of(item);
        this.itemName = itemName;
    }

    @Override protected String name(int count) { return "Use " + itemName; }
    @Override protected String description(int count) { return "Use " + itemName.toLowerCase() + " " + count + " times."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.USE_ITEM && event.subject().equals(item) && event.secondary().isEmpty();
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("item", item); }
}
