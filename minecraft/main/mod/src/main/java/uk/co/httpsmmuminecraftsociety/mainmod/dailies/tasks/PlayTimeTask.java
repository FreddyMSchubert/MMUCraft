package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class PlayTimeTask extends CountedTask {
    public PlayTimeTask() {
        super("play_time", "Played", "minutes");
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.PLAY_TIME;
    }
}
