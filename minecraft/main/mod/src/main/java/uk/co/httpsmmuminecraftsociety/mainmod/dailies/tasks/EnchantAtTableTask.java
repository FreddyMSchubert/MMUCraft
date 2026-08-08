package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class EnchantAtTableTask extends CountedTask {
    public EnchantAtTableTask() {
        super("enchant_at_table", "✨", 1, 3, 4.0D, "Enchanted", "items");
    }

    @Override protected String name(int count) { return "Arcane Appointments"; }
    @Override protected String description(int count) { return "Enchant " + count + " items at an enchanting table."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.ENCHANT_AT_TABLE && event.subject().isEmpty();
    }
}
