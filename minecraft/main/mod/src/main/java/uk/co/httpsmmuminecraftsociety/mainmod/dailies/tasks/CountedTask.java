package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

import java.util.Random;

public abstract class CountedTask implements DailyTaskDefinition {
    private final String id;
    private final String emoji;
    private final int minimum;
    private final int maximum;
    private final double rewardPerIteration;
    private final String progressLabel;
    private final String progressUnit;

    protected CountedTask(
            String id,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerIteration,
            String progressLabel,
            String progressUnit
    ) {
        if (minimum < 1 || maximum < minimum || !Double.isFinite(rewardPerIteration) || rewardPerIteration < 0.0D) {
            throw new IllegalArgumentException("Invalid counted daily task settings for " + id);
        }
        this.id = id;
        this.emoji = emoji;
        this.minimum = minimum;
        this.maximum = maximum;
        this.rewardPerIteration = rewardPerIteration;
        this.progressLabel = progressLabel;
        this.progressUnit = progressUnit;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final JsonObject create(Random random) {
        int count = random.nextInt(minimum, maximum + 1);
        JsonObject task = base(id, name(count), description(count), emoji, reward(count), count);
        task.addProperty("rewardPerIteration", rewardPerIteration);
        if (progressLabel != null && !progressLabel.isBlank()) task.addProperty("progressLabel", progressLabel);
        if (progressUnit != null && !progressUnit.isBlank()) task.addProperty("progressUnit", progressUnit);
        addTaskData(task, random);
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

    protected abstract String name(int count);

    protected abstract String description(int count);

    protected abstract boolean matches(JsonObject task, DailyTaskEvent event);

    protected void addTaskData(JsonObject task, Random random) {
    }

    protected static JsonObject base(
            String id,
            String name,
            String description,
            String emoji,
            int reward,
            int max
    ) {
        JsonObject task = new JsonObject();
        task.addProperty("id", id);
        task.addProperty("name", name);
        task.addProperty("description", description);
        task.addProperty("emoji", emoji);
        task.addProperty("rewardDabloons", reward);
        task.addProperty("current", 0);
        task.addProperty("max", max);
        return task;
    }

    private int reward(int count) {
        return Math.max(1, (int)Math.round(count * rewardPerIteration));
    }
}
