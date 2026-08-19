package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

public final class FishTask extends CountedTask {
    private final String item;
    private FishTask(String id, String item) {
        super(id, "Caught", "catches");
        this.item = item;
    }

    public FishTask(Item item) {
        this("fish:" + DailyTargetId.of(item), DailyTargetId.of(item));
    }

    public FishTask() {
        this("fish_anything", "");
    }

    public static FishTask custom(String fakeItemId) {
        FakeItems.requireFakeItem(fakeItemId);
        return new FishTask("fish:custom:" + fakeItemId, fakeItemId);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.FISH
                && (item.isBlank() ? event.subject().isEmpty() : event.subject().equals(item));
    }
    @Override protected void addTaskData(JsonObject task) { if (!item.isBlank()) task.addProperty("item", item); }
}
