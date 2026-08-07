package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailySimpleEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class SimpleEventTask extends CountedTask {
    private final String eventId;
    private final String singularName;
    private final String description;

    public SimpleEventTask(
            DailySimpleEvent event,
            String name,
            String description,
            String emoji,
            int minimum,
            int maximum,
            double rewardPerIteration,
            String progressLabel,
            String progressUnit
    ) {
        super(event.id(), emoji, minimum, maximum, rewardPerIteration, progressLabel, progressUnit);
        this.eventId = event.id();
        this.singularName = name;
        this.description = description;
    }

    @Override
    protected String name(int count) {
        return singularName;
    }

    @Override
    protected String description(int count) {
        return description.replace("{count}", Integer.toString(count));
    }

    @Override
    protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.SIMPLE && event.subject().equals(eventId);
    }
}
