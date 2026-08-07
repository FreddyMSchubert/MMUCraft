package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

public final class FishTask extends CountedTask {
    private final String item;
    private final String itemName;

    private FishTask(String id, String item, String itemName, int min, int max, double rewardPerCatch) {
        super(id, "🎣", min, max, rewardPerCatch, "Caught", item.isBlank() ? "catches" : itemName.toLowerCase());
        this.item = item;
        this.itemName = itemName;
    }

    public FishTask(Item item, String itemName, int min, int max, double rewardPerCatch) {
        this("fish:" + DailyTargetId.of(item), DailyTargetId.of(item), itemName, min, max, rewardPerCatch);
    }

    public FishTask(int min, int max, double rewardPerCatch) {
        this("fish_anything", "", "", min, max, rewardPerCatch);
    }

    public static FishTask custom(String fakeItemId, String itemName, int min, int max, double rewardPerCatch) {
        FakeItems.requireFakeItem(fakeItemId);
        return new FishTask("fish:custom:" + fakeItemId, fakeItemId, itemName, min, max, rewardPerCatch);
    }

    @Override protected String name(int count) { return item.isBlank() ? "Gone Fishing" : "Catch " + itemName; }
    @Override protected String description(int count) {
        return item.isBlank() ? "Catch something while fishing " + count + " times." : "Catch " + count + " " + itemName.toLowerCase() + " while fishing.";
    }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.FISH
                && (item.isBlank() ? event.subject().isEmpty() : event.subject().equals(item));
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { if (!item.isBlank()) task.addProperty("item", item); }
}
