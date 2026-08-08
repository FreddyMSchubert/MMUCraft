package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class PlantCropTask extends CountedTask {
    private final String seed;
    private final String cropName;

    public PlantCropTask(Item seed, String cropName, String emoji, int min, int max, double rewardPerCrop) {
        super("plant:" + DailyTargetId.of(seed), emoji, min, max, rewardPerCrop, "Planted", cropName.toLowerCase());
        this.seed = DailyTargetId.of(seed);
        this.cropName = cropName;
    }

    @Override protected String name(int count) { return "Plant " + cropName; }
    @Override protected String description(int count) { return "Plant " + count + " " + cropName.toLowerCase() + "."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.PLANT_CROP && event.subject().equals(seed);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("seed", seed); }
}
