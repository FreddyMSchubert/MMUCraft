package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.world.item.alchemy.Potion;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class BrewPotionTask extends CountedTask {
    private final String potion;
    private final String potionName;

    public BrewPotionTask(Holder<Potion> potion, String potionName, int min, int max, double rewardPerPotion) {
        super("brew:" + DailyTargetId.of(potion), "🧪", min, max, rewardPerPotion, "Brewed", "potions");
        this.potion = DailyTargetId.of(potion);
        this.potionName = potionName;
    }

    @Override protected String name(int count) { return "Brew " + potionName; }
    @Override protected String description(int count) { return "Brew and collect " + count + " potions of " + potionName + "."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.BREW_POTION && event.subject().equals(potion);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("potion", potion); }
}
