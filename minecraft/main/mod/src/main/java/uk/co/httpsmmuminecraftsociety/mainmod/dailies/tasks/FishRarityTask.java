package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishRarity;

import java.util.Locale;

public final class FishRarityTask extends CountedTask {
    private final String rarity;

    public FishRarityTask(FishRarity rarity) {
        super("fish_rarity:" + id(rarity), "Caught", "catches");
        this.rarity = id(rarity);
    }

    private static String id(FishRarity rarity) {
        return rarity.name().toLowerCase(Locale.ROOT);
    }

    @Override
    protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.FISH && event.secondary().equals(rarity);
    }

    @Override
    protected void addTaskData(JsonObject task) {
        task.addProperty("rarity", rarity);
    }
}
