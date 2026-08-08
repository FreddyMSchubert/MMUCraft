package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class KillWithItemTask extends CountedTask {
    private final String item;
    private final String itemName;

    private KillWithItemTask(String item, String itemName, int min, int max, double rewardPerKill) {
        super("kill_with:" + item, "⚔️", min, max, rewardPerKill, "Defeated", "mobs");
        this.item = item;
        this.itemName = itemName;
    }

    public KillWithItemTask(Item item, String itemName, int min, int max, double rewardPerKill) {
        this(DailyTargetId.of(item), itemName, min, max, rewardPerKill);
    }

    public KillWithItemTask(TagKey<Item> item, String itemName, int min, int max, double rewardPerKill) {
        this(DailyTargetId.of(item), itemName, min, max, rewardPerKill);
    }

    @Override protected String name(int count) { return "Defeat a Mob with " + itemName; }
    @Override protected String description(int count) { return "Defeat " + count + " mobs using " + itemName.toLowerCase() + "."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.KILL_ENTITY && event.secondary().equals(item);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("item", item); }
}
