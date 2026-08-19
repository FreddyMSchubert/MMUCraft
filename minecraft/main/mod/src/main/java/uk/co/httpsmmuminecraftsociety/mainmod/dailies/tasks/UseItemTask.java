package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class UseItemTask extends CountedTask {
    private final String item;
    public UseItemTask(Item item) {
        super("use:" + DailyTargetId.of(item), "Used", "times");
        this.item = DailyTargetId.of(item);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.USE_ITEM && event.subject().equals(item) && event.secondary().isEmpty();
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("item", item); }
}
