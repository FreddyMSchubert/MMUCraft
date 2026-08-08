package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class RideDistanceTask extends CountedTask {
    private final String vehicle;
    private final String vehicleName;

    public RideDistanceTask(EntityType<?> vehicle, String vehicleName, String emoji, int min, int max, double rewardPerBlock) {
        super("ride:" + DailyTargetId.of(vehicle), emoji, min, max, rewardPerBlock, "Travelled", "blocks");
        this.vehicle = DailyTargetId.of(vehicle);
        this.vehicleName = vehicleName;
    }

    @Override protected String name(int count) { return "Ride a " + vehicleName; }
    @Override protected String description(int count) { return "Travel " + count + " blocks while riding a " + vehicleName.toLowerCase() + "."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.RIDE_DISTANCE && event.subject().equals(vehicle);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("vehicle", vehicle); }
}
