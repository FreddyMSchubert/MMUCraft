package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public abstract class CountedTask implements DailyTaskDefinition {
    private final String id;
    private final String progressLabel;
    private final String progressUnit;

    protected CountedTask(String id, String progressLabel, String progressUnit) {
        this.id = id;
        this.progressLabel = progressLabel;
        this.progressUnit = progressUnit;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final JsonObject create(int count) {
        JsonObject task = base(id, count);
        if (progressLabel != null && !progressLabel.isBlank()) task.addProperty("progressLabel", progressLabel);
        if (progressUnit != null && !progressUnit.isBlank()) task.addProperty("progressUnit", progressUnit);
        addTaskData(task);
        return task;
    }

    @Override
    public final int getReward(JsonObject task) {
        return task.get("rewardDabloons").getAsInt();
    }

    @Override
    public final int progress(JsonObject task, DailyTaskEvent event) {
        return matches(task, event) ? event.amount() : 0;
    }

    protected abstract boolean matches(JsonObject task, DailyTaskEvent event);

    protected void addTaskData(JsonObject task) {
    }

    protected static JsonObject base(String id, int max) {
        JsonObject task = new JsonObject();
        task.addProperty("id", id);
        task.addProperty("current", 0);
        task.addProperty("max", max);
        return task;
    }
}
