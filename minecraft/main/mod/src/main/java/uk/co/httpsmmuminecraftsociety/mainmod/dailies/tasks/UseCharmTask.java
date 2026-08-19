package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class UseCharmTask extends CountedTask {
    private final String charm;
    public UseCharmTask(DailyCharm charm) {
        super("use:" + charm.id(), "Used", "potion");
        this.charm = charm.id();
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.USE_CHARM && event.subject().equals(charm);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("charm", charm); }
}
