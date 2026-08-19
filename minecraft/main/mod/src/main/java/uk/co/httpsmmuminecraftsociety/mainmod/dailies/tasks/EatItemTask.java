package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class EatItemTask extends CountedTask {
    private final String item;
    public EatItemTask(Item item) {
        super("eat:" + DailyTargetId.of(item), "Eaten", "items");
        this.item = DailyTargetId.of(item);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.USE_ITEM && event.subject().equals(item) && event.secondary().equals("eat");
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("item", item); }
}
