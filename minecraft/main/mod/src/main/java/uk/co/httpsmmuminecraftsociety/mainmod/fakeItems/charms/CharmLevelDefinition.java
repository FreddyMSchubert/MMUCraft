package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record CharmLevelDefinition(
        int level,
        String abilityStatusCurrent,
        String abilityStatusRelative,
        List<CharmUpgradeDefinition> upgradeIngredients
) {
    public static final CharmLevelDefinition BROKEN_LEVEL = new CharmLevelDefinition(
            0,
            "Has no effect while equipped.",
            "",
            List.of()
    );

    public static CharmLevelDefinition of(JsonObject json, String filePath) {
        if (!json.has("level")) {
            throw new IllegalStateException(filePath + ": charm level entry missing required field 'level'");
        }
        if (!json.has("abilityStatusCurrent")) {
            throw new IllegalStateException(filePath + ": charm level entry missing required field 'abilityStatusCurrent'");
        }
        if (!json.has("abilityStatusRelative")) {
            throw new IllegalStateException(filePath + ": charm level entry missing required field 'abilityStatusRelative'");
        }
        if (!json.has("upgradeIngredients") || !json.get("upgradeIngredients").isJsonArray()) {
            throw new IllegalStateException(filePath + ": charm level entry missing or invalid required field 'upgradeIngredients'");
        }

        int level = json.get("level").getAsInt();
        String current = json.get("abilityStatusCurrent").getAsString();
        String relative = json.get("abilityStatusRelative").getAsString();

        List<CharmUpgradeDefinition> ingredients = new ArrayList<>();
        for (JsonElement element : json.get("upgradeIngredients").getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalStateException(filePath + ": charm level ingredient entry must be an object");
            }
            ingredients.add(CharmUpgradeDefinition.of(element.getAsJsonObject(), filePath));
        }

        return new CharmLevelDefinition(level, current, relative, List.copyOf(ingredients));
    }
}
