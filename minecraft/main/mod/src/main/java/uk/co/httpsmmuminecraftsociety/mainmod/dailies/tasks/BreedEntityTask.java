package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class BreedEntityTask extends CountedTask {
    private final String entity;
    public BreedEntityTask(EntityType<?> entity) {
        super("breed:" + DailyTargetId.of(entity), "Bred", "animals");
        this.entity = DailyTargetId.of(entity);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.BREED_ENTITY && event.subject().equals(entity);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("entity", entity); }
}
