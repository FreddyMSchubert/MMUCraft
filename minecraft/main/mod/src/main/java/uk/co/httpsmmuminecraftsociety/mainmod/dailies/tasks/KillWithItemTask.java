package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class KillWithItemTask extends CountedTask {
    private final String item;
    private KillWithItemTask(String item) {
        super("kill_with:" + item, "Defeated", "mobs");
        this.item = item;
    }

    public KillWithItemTask(Item item) {
        this(DailyTargetId.of(item));
    }

    public KillWithItemTask(TagKey<Item> item) {
        this(DailyTargetId.of(item));
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.KILL_ENTITY && event.secondary().equals(item);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("item", item); }
}
