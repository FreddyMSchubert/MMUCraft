package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class BreedEntityTask extends CountedTask {
    private final String entity;
    private final String entityName;

    public BreedEntityTask(EntityType<?> entity, String entityName, String emoji, int min, int max, double rewardPerBaby) {
        super("breed:" + DailyTargetId.of(entity), emoji, min, max, rewardPerBaby, "Bred", entityName.toLowerCase());
        this.entity = DailyTargetId.of(entity);
        this.entityName = entityName;
    }

    @Override protected String name(int count) { return "Breed " + entityName; }
    @Override protected String description(int count) { return "Breed " + count + " " + entityName.toLowerCase() + "."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.BREED_ENTITY && event.subject().equals(entity);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("entity", entity); }
}
