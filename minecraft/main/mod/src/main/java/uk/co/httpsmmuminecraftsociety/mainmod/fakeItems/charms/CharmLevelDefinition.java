package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs.StackDef;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs.StackDefs;

import java.util.ArrayList;
import java.util.List;

public record CharmLevelDefinition(
        int level,
        String abilityStatusCurrent,
        String abilityStatusRelative,
        List<StackDef> upgradeIngredients
) {
    public static final CharmLevelDefinition BROKEN_LEVEL = new CharmLevelDefinition(
            0,
            "None - Please repair to use.",
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

        List<StackDef> ingredients = new ArrayList<>();
        for (JsonElement element : json.get("upgradeIngredients").getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalStateException(filePath + ": charm level ingredient entry must be a string");
            }

            String raw = element.getAsString();
            try {
                ingredients.add(StackDefs.parse(raw));
            } catch (RuntimeException e) {
                throw new IllegalStateException(filePath + ": invalid upgrade ingredient '" + raw + "': " + e.getMessage(), e);
            }
        }

        return new CharmLevelDefinition(level, current, relative, List.copyOf(ingredients));
    }
}