package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class UseCharmTask extends CountedTask {
    private final String charm;
    private final String charmName;

    public UseCharmTask(DailyCharm charm, String emoji, double reward) {
        super("use:" + charm.id(), emoji, 1, 1, reward, "Used", "potion");
        this.charm = charm.id();
        this.charmName = charm.displayName();
    }

    @Override protected String name(int count) { return "Use " + charmName; }
    @Override protected String description(int count) { return "Successfully use " + charmName + "."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.USE_CHARM && event.subject().equals(charm);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("charm", charm); }
}
