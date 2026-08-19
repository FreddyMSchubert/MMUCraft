package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class TakeDamageTask extends CountedTask {
    private final String source;
    private final boolean entity;

    private TakeDamageTask(String source, boolean entity) {
        super("damaged_by:" + (entity ? "entity:" : "type:") + source, "Damaged", "time");
        this.source = source;
        this.entity = entity;
    }

    public TakeDamageTask(ResourceKey<DamageType> source) {
        this(DailyTargetId.of(source), false);
    }

    public TakeDamageTask(EntityType<?> source) {
        this(DailyTargetId.of(source), true);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.TAKE_DAMAGE
                && event.subject().equals(entity ? "entity" : "type")
                && event.secondary().equals(source);
    }
    @Override protected void addTaskData(JsonObject task) {
        task.addProperty(entity ? "entity" : "damageType", source);
    }
}
