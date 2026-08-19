package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.world.item.alchemy.Potion;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class BrewPotionTask extends CountedTask {
    private final String potion;
    public BrewPotionTask(Holder<Potion> potion) {
        super("brew:" + DailyTargetId.of(potion), "Brewed", "potions");
        this.potion = DailyTargetId.of(potion);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.BREW_POTION && event.subject().equals(potion);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("potion", potion); }
}
