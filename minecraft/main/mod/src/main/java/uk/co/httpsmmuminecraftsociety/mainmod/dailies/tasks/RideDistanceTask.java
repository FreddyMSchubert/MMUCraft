package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class RideDistanceTask extends CountedTask {
    private final String vehicle;
    public RideDistanceTask(EntityType<?> vehicle) {
        super("ride:" + DailyTargetId.of(vehicle), "Travelled", "blocks");
        this.vehicle = DailyTargetId.of(vehicle);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.RIDE_DISTANCE && event.subject().equals(vehicle);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("vehicle", vehicle); }
}
