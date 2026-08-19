package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class CureZombieVillagerTask extends CountedTask {
    public CureZombieVillagerTask() {
        super("cure_zombie_villager", "Cured", "villager");
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.CURE_ZOMBIE_VILLAGER;
    }
}
