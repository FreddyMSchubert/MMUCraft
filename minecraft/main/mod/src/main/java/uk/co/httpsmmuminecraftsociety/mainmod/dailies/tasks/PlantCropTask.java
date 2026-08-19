package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class PlantCropTask extends CountedTask {
    private final String seed;
    public PlantCropTask(Item seed) {
        super("plant:" + DailyTargetId.of(seed), "Planted", "crops");
        this.seed = DailyTargetId.of(seed);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.PLANT_CROP && event.subject().equals(seed);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("seed", seed); }
}
