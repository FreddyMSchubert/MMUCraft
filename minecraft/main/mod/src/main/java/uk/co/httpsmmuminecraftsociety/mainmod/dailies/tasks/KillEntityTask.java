package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class KillEntityTask extends CountedTask {
    private final String entity;
    public KillEntityTask(EntityType<?> entity) {
        super("kill:" + DailyTargetId.of(entity), "Defeated", "mobs");
        this.entity = DailyTargetId.of(entity);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.KILL_ENTITY
                && event.subject().equals(entity)
                && event.secondary().isEmpty();
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("entity", entity); }
}
