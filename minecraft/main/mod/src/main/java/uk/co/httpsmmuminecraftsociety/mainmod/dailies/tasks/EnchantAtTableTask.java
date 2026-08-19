package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class EnchantAtTableTask extends CountedTask {
    public EnchantAtTableTask() {
        super("enchant_at_table", "Enchanted", "items");
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.ENCHANT_AT_TABLE && event.subject().isEmpty();
    }
}
