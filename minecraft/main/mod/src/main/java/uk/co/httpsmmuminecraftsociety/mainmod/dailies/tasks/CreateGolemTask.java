package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class CreateGolemTask extends CountedTask {
    private final String golem;
    private final String taskName;
    private final String taskDescription;

    public CreateGolemTask(EntityType<?> golem, String taskName, String taskDescription, String emoji, double reward) {
        super("create:" + DailyTargetId.of(golem), emoji, 1, 1, reward, "Created", "golem");
        this.golem = DailyTargetId.of(golem);
        this.taskName = taskName;
        this.taskDescription = taskDescription;
    }

    @Override protected String name(int count) { return taskName; }
    @Override protected String description(int count) { return taskDescription; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.CREATE_GOLEM && event.subject().equals(golem);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("golem", golem); }
}
