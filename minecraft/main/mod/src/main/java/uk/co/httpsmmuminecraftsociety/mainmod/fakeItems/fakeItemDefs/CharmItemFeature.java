package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs.StackDef;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs.TagStackDef;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.*;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

import java.util.*;
import java.util.stream.Collectors;

public record CharmItemFeature(
        Charm charm,
        int charmId,
        int minLevel,
        int maxLevel,
        String baseTitle,
        Map<Integer, CharmLevelDefinition> levelDefinitions
) implements ItemFeature
{
    public static ItemFeature of(JsonObject rootJson, JsonObject json)
    {
        int charmId = json.get("charmId").getAsInt();
        Charm charm = CharmsManager.charmFromId(charmId);
        if (charm == null) {
            throw new IllegalStateException("Unknown charmId: " + charmId);
        }

        String baseTitle = rootJson.get("title").getAsString();

        int minLevel = json.get("minLevel").getAsInt();
        int maxLevel = json.get("maxLevel").getAsInt();

        if (minLevel < 0) {
            throw new IllegalStateException("minLevel cannot be negative for charmId " + charmId);
        }
        if (maxLevel < minLevel) {
            throw new IllegalStateException("maxLevel cannot be less than minLevel for charmId " + charmId);
        }

        if (!json.has("levels") || !json.get("levels").isJsonArray()) {
            throw new IllegalStateException("Charm component missing required array field 'levels' for charmId " + charmId);
        }

        Map<Integer, CharmLevelDefinition> levelDefinitions = new HashMap<>();
        for (JsonElement element : json.get("levels").getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalStateException("Charm level entry must be a JSON object for charmId " + charmId);
            }

            CharmLevelDefinition def = CharmLevelDefinition.of(element.getAsJsonObject(), "charmId " + charmId);

            if (def.level() == 0) {
                throw new IllegalStateException(
                        "Level 0 must not be defined in JSON for charmId " + charmId
                                + "; it is implicit and always means a broken charm"
                );
            }
            if (def.level() < minLevel || def.level() > maxLevel) {
                throw new IllegalStateException(
                        "Charm level " + def.level() + " is outside the valid range "
                                + minLevel + ".." + maxLevel + " for charmId " + charmId
                );
            }

            CharmLevelDefinition previous = levelDefinitions.putIfAbsent(def.level(), def);
            if (previous != null) {
                throw new IllegalStateException("Duplicate charm level " + def.level() + " for charmId " + charmId);
            }
        }

        for (int level = Math.max(1, minLevel); level <= maxLevel; level++) {
            if (!levelDefinitions.containsKey(level)) {
                throw new IllegalStateException("Missing charm level definition for level " + level + " on charmId " + charmId);
            }
        }

        return new CharmItemFeature(
                charm,
                charmId,
                minLevel,
                maxLevel,
                baseTitle,
                Map.copyOf(levelDefinitions)
        );
    }

    public void validate() {
        for (CharmLevelDefinition definition : levelDefinitions.values()) {
            for (StackDef ingredient : definition.upgradeIngredients()) {
                ingredient.createStack();
            }
        }
    }

    @Override
    public void apply(ItemStack stack)
    {
        apply(stack, minLevel);
    }

    public void apply(ItemStack stack, int startingLevel)
    {
        validateLevel(startingLevel);

        CharmStackData.setStoredCharms(stack, List.of(new StoredCharmData(charmId, startingLevel)));
        applyPresentation(stack, startingLevel);

        if (startingLevel > 0 && charm instanceof BaseItemChangeCallbackCharm baseItemChangeCallbackCharm) {
            baseItemChangeCallbackCharm.enableEffectForItem(stack, startingLevel);
        }
    }

    public void setLevel(ItemStack stack, int newLevel)
    {
        validateLevel(newLevel);

        int oldLevel = CharmStackData.getSingleStoredCharm(stack)
                .map(StoredCharmData::level)
                .orElse(minLevel);

        if (oldLevel > 0 && charm instanceof BaseItemChangeCallbackCharm baseItemChangeCallbackCharm) {
            baseItemChangeCallbackCharm.disableEffectForItem(stack, oldLevel);
        }

        CharmStackData.setStoredCharms(stack, List.of(new StoredCharmData(charmId, newLevel)));

        if (newLevel > 0 && charm instanceof BaseItemChangeCallbackCharm baseItemChangeCallbackCharm) {
            baseItemChangeCallbackCharm.enableEffectForItem(stack, newLevel);
        }

        applyPresentation(stack, newLevel);
        FakeItems.wrapTooltip(stack);
    }

    public boolean hasNextLevel(int level) {
        return level < maxLevel;
    }

    public void applyPresentation(ItemStack stack, int level)
    {
        validateLevel(level);

        stack.set(DataComponents.CUSTOM_NAME, Component.literal(getDisplayTitle(level)));
        stack.set(DataComponents.LORE, new ItemLore(buildTooltip(level)));
    }

    private void validateLevel(int level) {
        if (level < minLevel || level > maxLevel) {
            throw new IllegalStateException(
                    "Invalid charm level " + level + " for charmId " + charmId
                            + ", expected " + minLevel + ".." + maxLevel
            );
        }
    }

    public String getDisplayTitle(int level) {
        validateLevel(level);

        if (minLevel == maxLevel) {
            return baseTitle;
        }

        if (level == 0) {
            return "Broken " + baseTitle;
        }

        return baseTitle + " " + toRoman(level);
    }

    public List<Component> buildTooltip(int level) {
        validateLevel(level);

        CharmLevelDefinition current = getLevelDefinition(level);
        CharmLevelDefinition next = hasNextLevel(level) ? getLevelDefinition(level + 1) : null;

        List<Component> lines = new ArrayList<>();

        FakeItem defaultFakeItem = FakeItems.CHARM_ID_MAP.get(charmId);
        if (defaultFakeItem != null && defaultFakeItem.tooltip() != null && !defaultFakeItem.tooltip().isEmpty()) {
            lines.addAll(defaultFakeItem.tooltip());
        }

        lines.add(toAbilityComponent("Ability: " + current.abilityStatusCurrent()));

        if (next != null) {
            if (!next.abilityStatusRelative().isBlank()) {
                lines.add(toAbilityComponent("Next Level: " + next.abilityStatusRelative()));
            }
            if (!next.upgradeIngredients().isEmpty()) {
                lines.add(toAbilityComponent("To Upgrade: " + formatUpgradeIngredients(next.upgradeIngredients())));
            }
        }

        return List.copyOf(lines);
    }

    public CharmLevelDefinition getLevelDefinition(int level) {
        if (level == 0) {
            if (minLevel > 0) {
                throw new IllegalStateException("Level 0 is illegal for charmId " + charmId + " because minLevel is " + minLevel);
            }
            return CharmLevelDefinition.BROKEN_LEVEL;
        }

        CharmLevelDefinition def = levelDefinitions.get(level);
        if (def == null) {
            throw new IllegalStateException("No level definition " + level + " for charmId " + charmId);
        }
        return def;
    }

    private record CountedIngredient(StackDef ingredient, int count) {}

    private static String formatUpgradeIngredients(List<StackDef> ingredients) {
        Map<String, CountedIngredient> grouped = new HashMap<>();

        for (StackDef ingredient : ingredients) {
            grouped.merge(
                    ingredient.raw(),
                    new CountedIngredient(ingredient, 1),
                    (left, right) -> new CountedIngredient(left.ingredient(), left.count() + 1)
            );
        }

        return grouped.values().stream()
                .sorted(
                        Comparator.<CountedIngredient>comparingInt(CountedIngredient::count).reversed()
                                .thenComparing(
                                        (CountedIngredient entry) -> entry.ingredient().specificity(),
                                        Comparator.reverseOrder()
                                )
                                .thenComparing(entry -> entry.ingredient().raw())
                )
                .map(entry -> formatIngredient(entry.ingredient(), entry.count()))
                .collect(Collectors.joining(", "));
    }

    private static String formatIngredient(StackDef ingredient, int count) {
        String displayName = ingredient.getDisplayName();
        if (ingredient instanceof TagStackDef && !ingredient.hasDisplayNameOverride()) {
            displayName = "Any " + displayName;
        }

        if (count > 1) {
            return count + "x " + displayName;
        }
        return displayName;
    }

    private static String toRoman(int value) {
        if (value <= 0) {
            throw new IllegalStateException("Roman numeral conversion requires a positive value, got " + value);
        }

        int[] values =    {1000, 900, 500, 400, 100,  90,  50,  40,  10,   9,   5,   4,   1};
        String[] numerals = {"M", "CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};

        StringBuilder out = new StringBuilder();
        int remaining = value;

        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                out.append(numerals[i]);
                remaining -= values[i];
            }
        }

        return out.toString();
    }

    private static Component toAbilityComponent(String in) {
        return Component.literal(in).withStyle(ChatFormatting.RESET).withStyle(ChatFormatting.WHITE);
    }
}
