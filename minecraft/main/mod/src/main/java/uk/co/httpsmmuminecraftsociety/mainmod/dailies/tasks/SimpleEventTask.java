package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class SimpleEventTask extends CountedTask {
    private final String eventId;
    public SimpleEventTask(DailySimpleEvent event, String progressLabel, String progressUnit) {
        super(event.id(), progressLabel, progressUnit);
        this.eventId = event.id();
    }

    @Override
    protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.SIMPLE && event.subject().equals(eventId);
    }
}
