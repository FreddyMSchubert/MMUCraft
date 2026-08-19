package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class ReceiveEffectTask extends CountedTask {
    private final String effect;
    public ReceiveEffectTask(Holder<MobEffect> effect) {
        super("effect:" + DailyTargetId.of(effect), "Received", "effect");
        this.effect = DailyTargetId.of(effect);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.RECEIVE_EFFECT && event.subject().equals(effect);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("effect", effect); }
}
