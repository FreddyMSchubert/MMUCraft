package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class FeedEntityTask extends CountedTask {
    private final String entity;
    private final String entityName;

    public FeedEntityTask(EntityType<?> entity, String entityName, String emoji, int min, int max, double rewardPerFeeding) {
        super("feed:" + DailyTargetId.of(entity), emoji, min, max, rewardPerFeeding, "Fed", entityName.toLowerCase());
        this.entity = DailyTargetId.of(entity);
        this.entityName = entityName;
    }

    @Override protected String name(int count) { return "Feed " + entityName; }
    @Override protected String description(int count) {
        return "Offer food to " + count + " " + entityName.toLowerCase()
                + "; each right-click with valid food counts, even if the animal is full.";
    }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.FEED_ENTITY && event.subject().equals(entity);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("entity", entity); }
}
