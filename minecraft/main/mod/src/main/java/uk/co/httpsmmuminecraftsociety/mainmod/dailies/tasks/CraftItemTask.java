package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class CraftItemTask extends CountedTask {
    private final String item;
    public CraftItemTask(Item item) {
        super("craft:" + DailyTargetId.of(item), "Crafted", "items");
        this.item = DailyTargetId.of(item);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.CRAFT_ITEM && event.subject().equals(item);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("item", item); }
}
