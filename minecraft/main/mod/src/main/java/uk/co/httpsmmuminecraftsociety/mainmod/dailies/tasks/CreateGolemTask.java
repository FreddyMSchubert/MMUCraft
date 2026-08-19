package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class CreateGolemTask extends CountedTask {
    private final String golem;
    public CreateGolemTask(EntityType<?> golem) {
        super("create:" + DailyTargetId.of(golem), "Created", "golem");
        this.golem = DailyTargetId.of(golem);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.CREATE_GOLEM && event.subject().equals(golem);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("golem", golem); }
}
