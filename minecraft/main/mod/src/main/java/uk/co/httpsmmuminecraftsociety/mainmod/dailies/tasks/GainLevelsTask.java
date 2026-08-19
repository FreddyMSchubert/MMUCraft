package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class GainLevelsTask extends CountedTask {
    public GainLevelsTask() {
        super("gain_levels", "Gained", "levels");
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.GAIN_LEVEL;
    }
}
