package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class CreateGolemTask extends CountedTask {
    private final String golem;
    private final String golemName;

    public CreateGolemTask(EntityType<?> golem, String golemName, String emoji, double reward) {
        super("create:" + DailyTargetId.of(golem), emoji, 1, 1, reward, "Created", "golem");
        this.golem = DailyTargetId.of(golem);
        this.golemName = golemName;
    }

    @Override protected String name(int count) { return "Welcome a " + golemName; }
    @Override protected String description(int count) { return "Be the closest player when a " + golemName.toLowerCase() + " is created."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.CREATE_GOLEM && event.subject().equals(golem);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("golem", golem); }
}
