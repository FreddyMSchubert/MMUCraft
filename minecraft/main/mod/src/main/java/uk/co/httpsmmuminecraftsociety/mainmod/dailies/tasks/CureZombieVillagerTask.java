package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class CureZombieVillagerTask extends CountedTask {
    public CureZombieVillagerTask() {
        super("cure_zombie_villager", "🧟", 1, 1, 15.0D, "Cured", "villager");
    }

    @Override protected String name(int count) { return "Zombie Doctor"; }
    @Override protected String description(int count) { return "Cure " + count + " zombie villagers."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.CURE_ZOMBIE_VILLAGER;
    }
}
