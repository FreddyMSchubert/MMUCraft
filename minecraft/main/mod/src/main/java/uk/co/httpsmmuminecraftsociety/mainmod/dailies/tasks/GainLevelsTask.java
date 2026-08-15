package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class GainLevelsTask extends CountedTask {
    public GainLevelsTask() {
        super("gain_levels", "🟢", 5, 15, 1D, "Gained", "levels");
    }

    @Override protected String name(int count) { return "Learn Something New"; }
    @Override protected String description(int count) { return "Gain " + count + " experience levels."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.GAIN_LEVEL;
    }
}
