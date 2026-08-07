package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class KillEntityTask extends CountedTask {
    private final String entity;
    private final String singularName;
    private final String pluralName;
    private final String description;

    public KillEntityTask(EntityType<?> entity, String singularName, String pluralName, String emoji, int min, int max, double rewardPerKill) {
        this(entity, singularName, pluralName, null, emoji, min, max, rewardPerKill);
    }

    public KillEntityTask(EntityType<?> entity, String singularName, String pluralName, String description,
                          String emoji, int min, int max, double rewardPerKill) {
        super("kill:" + DailyTargetId.of(entity), emoji, min, max, rewardPerKill, "Defeated", pluralName.toLowerCase());
        this.entity = DailyTargetId.of(entity);
        this.singularName = singularName;
        this.pluralName = pluralName;
        this.description = description;
    }

    @Override protected String name(int count) { return "Defeat " + (count == 1 ? singularName : pluralName); }
    @Override protected String description(int count) {
        if (description != null) return description.replace("{count}", Integer.toString(count));
        return "Defeat " + (count == 1 ? singularName.toLowerCase() : count + " " + pluralName.toLowerCase()) + ".";
    }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.KILL_ENTITY
                && event.subject().equals(entity)
                && event.secondary().isEmpty();
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("entity", entity); }
}
