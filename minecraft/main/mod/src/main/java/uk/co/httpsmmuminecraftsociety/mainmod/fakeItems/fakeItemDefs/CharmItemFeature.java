package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.*;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.JsonUtils;

import java.util.*;

public record CharmItemFeature(
        Charm charm,
        int charmId,
        int minLevel,
        int maxLevel,
        int defaultLevel,
        Map<Integer, CharmLevelDefinition> levelDefinitions
) implements ItemFeature
{
    public static ItemFeature of(JsonObject json)
    {
        int charmId = json.get("charmId").getAsInt();
        Charm charm = CharmsManager.charmFromId(charmId);
        if (charm == null) {
            throw new IllegalStateException("Unknown charmId: " + charmId);
        }

        int minLevel = json.get("minLevel").getAsInt();
        int maxLevel = json.get("maxLevel").getAsInt();
        int defaultLevel = json.get("defaultLevel").getAsInt();

        if (minLevel > maxLevel) {
            throw new IllegalStateException("minLevel cannot be greater than maxLevel for charmId " + charmId);
        }
        if (defaultLevel < minLevel || defaultLevel > maxLevel) {
            throw new IllegalStateException("defaultLevel must be within minLevel..maxLevel for charmId " + charmId);
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
            CharmLevelDefinition previous = levelDefinitions.putIfAbsent(def.level(), def);
            if (previous != null) {
                throw new IllegalStateException("Duplicate charm level " + def.level() + " for charmId " + charmId);
            }
        }

        for (int level = minLevel; level <= maxLevel; level++) {
            if (!levelDefinitions.containsKey(level)) {
                throw new IllegalStateException("Missing charm level definition for level " + level + " on charmId " + charmId);
            }
        }

        return new CharmItemFeature(
                charm,
                charmId,
                minLevel,
                maxLevel,
                defaultLevel,
                Map.copyOf(levelDefinitions)
        );
    }

    @Override
    public void apply(ItemStack stack)
    {
        apply(stack, defaultLevel);
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
                .orElse(defaultLevel);

        if (oldLevel > 0 && charm instanceof BaseItemChangeCallbackCharm baseItemChangeCallbackCharm) {
            baseItemChangeCallbackCharm.disableEffectForItem(stack, oldLevel);
        }

        CharmStackData.setStoredCharms(stack, List.of(new StoredCharmData(charmId, newLevel)));

        if (newLevel > 0 && charm instanceof BaseItemChangeCallbackCharm baseItemChangeCallbackCharm) {
            baseItemChangeCallbackCharm.enableEffectForItem(stack, newLevel);
        }

        applyPresentation(stack, newLevel);
    }
    public boolean hasNextLevel(int level) {
        return level < maxLevel;
    }

    public void applyPresentation(ItemStack stack, int level)
    {
        validateLevel(level);

        stack.set(DataComponents.CUSTOM_NAME, Component.literal(getDisplayTitle(stack, level)));
        stack.set(DataComponents.LORE, new ItemLore(buildTooltip(stack, level)));
    }

    private void validateLevel(int level) {
        if (level < minLevel || level > maxLevel) {
            throw new IllegalStateException(
                    "Invalid charm level " + level + " for charmId " + charmId
                            + ", expected " + minLevel + ".." + maxLevel
            );
        }
    }

    public String getDisplayTitle(ItemStack stack, int level) {
        String charmName = stack.getOrDefault(DataComponents.CUSTOM_NAME, "Charm").toString();
        return level == 0
                ? "Broken " + charmName
                : "Level " + level + " " + charmName;
    }
    public List<Component> buildTooltip(ItemStack stack, int level) {
        CharmLevelDefinition current = getLevelDefinition(level);
        CharmLevelDefinition next = level < maxLevel ? getLevelDefinition(level + 1) : null;

        List<Component> lines = new ArrayList<>();
        lines.addAll(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lines.add(Component.literal(""));

        lines.add(Component.literal("Current: " + current.abilityStatusCurrent()));

        if (next != null) {
            lines.add(Component.literal(""));
            if (!next.abilityStatusRelative().isBlank()) {
                lines.add(Component.literal("Next: " + next.abilityStatusRelative()));
            }
            if (!next.upgradeIngredients().isEmpty()) {
                lines.add(Component.literal("Upgrade: " + formatUpgradeIngredients(next.upgradeIngredients())));
            }
        }

        return List.copyOf(lines);
    }
    public CharmLevelDefinition getLevelDefinition(int level) {
        CharmLevelDefinition def = levelDefinitions.get(level);
        if (def == null) {
            throw new IllegalStateException("No level definition " + level + " for charmId " + charmId);
        }
        return def;
    }
    private static String formatUpgradeIngredients(List<CharmUpgradeDefinition> ingredients) {
        return ingredients.stream()
                .map(CharmItemFeature::formatIngredient)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
    private static String formatIngredient(CharmUpgradeDefinition ingredient) {
        String displayName;

        if (ingredient.isVanillaItemId()) {
            Optional<Item> item = JsonUtils.resolveItem(ingredient.id());
            if (item.isEmpty()) return "Empty";
            displayName = new ItemStack(item.get()).getHoverName().getString();
        } else {
            FakeItem fakeItem = FakeItems.ID_MAP.get(ingredient.id());
            displayName = fakeItem != null ? fakeItem.title() : ingredient.id();
        }

        return ingredient.count() + "x " + displayName;
    }
}
