package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class ReceiveEffectTask extends CountedTask {
    private final String effect;
    private final String effectName;

    public ReceiveEffectTask(Holder<MobEffect> effect, String effectName, String emoji, double reward) {
        super("effect:" + DailyTargetId.of(effect), emoji, 1, 1, reward, "Received", "effect");
        this.effect = DailyTargetId.of(effect);
        this.effectName = effectName;
    }

    @Override protected String name(int count) { return "Experience " + effectName; }
    @Override protected String description(int count) { return "Get the " + effectName + " effect from any source."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.RECEIVE_EFFECT && event.subject().equals(effect);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("effect", effect); }
}
