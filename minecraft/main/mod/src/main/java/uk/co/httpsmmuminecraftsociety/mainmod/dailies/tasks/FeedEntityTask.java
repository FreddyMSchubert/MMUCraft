package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class FeedEntityTask extends CountedTask {
    private final String entity;
    public FeedEntityTask(EntityType<?> entity) {
        super("feed:" + DailyTargetId.of(entity), "Fed", "animals");
        this.entity = DailyTargetId.of(entity);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.FEED_ENTITY && event.subject().equals(entity);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("entity", entity); }
}
