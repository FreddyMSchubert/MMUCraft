package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class TakeDamageTask extends CountedTask {
    private final String source;
    private final String sourceName;
    private final boolean entity;

    private TakeDamageTask(String source, String sourceName, String emoji, boolean entity, double reward) {
        super("damaged_by:" + (entity ? "entity:" : "type:") + source, emoji, 1, 1, reward, "Damaged", "time");
        this.source = source;
        this.sourceName = sourceName;
        this.entity = entity;
    }

    public TakeDamageTask(ResourceKey<DamageType> source, String sourceName, String emoji, double reward) {
        this(DailyTargetId.of(source), sourceName, emoji, false, reward);
    }

    public TakeDamageTask(EntityType<?> source, String sourceName, String emoji, double reward) {
        this(DailyTargetId.of(source), sourceName, emoji, true, reward);
    }

    @Override protected String name(int count) { return "Get Hurt by " + sourceName; }
    @Override protected String description(int count) { return "Take damage from " + sourceName.toLowerCase() + " without dying."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.TAKE_DAMAGE
                && event.subject().equals(entity ? "entity" : "type")
                && event.secondary().equals(source);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) {
        task.addProperty(entity ? "entity" : "damageType", source);
    }
}
