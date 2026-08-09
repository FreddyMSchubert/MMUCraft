package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class PlayTimeTask extends CountedTask {
    public PlayTimeTask() {
        super("play_time", "⏰", 10, 60, 0.3D, "Played", "minutes");
    }

    @Override protected String name(int count) { return "Stay a While"; }
    @Override protected String description(int count) { return "Play on the server for at least " + count + " minutes today."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.PLAY_TIME;
    }
}
