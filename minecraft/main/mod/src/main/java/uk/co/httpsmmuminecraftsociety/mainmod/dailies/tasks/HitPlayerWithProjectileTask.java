package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class HitPlayerWithProjectileTask extends CountedTask {
    private final String projectile;
    public HitPlayerWithProjectileTask(EntityType<?> projectile) {
        super("hit_player:" + DailyTargetId.of(projectile), "Hits", "players");
        this.projectile = DailyTargetId.of(projectile);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.HIT_PLAYER_WITH_PROJECTILE && event.subject().equals(projectile);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("projectile", projectile); }
}
