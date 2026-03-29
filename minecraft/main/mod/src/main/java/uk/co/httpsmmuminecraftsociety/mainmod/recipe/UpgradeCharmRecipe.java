package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmLevelDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmUpgradeDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.HashMap;
import java.util.Map;

public class UpgradeCharmRecipe extends CustomRecipe
{
    private record UpgradeInfo(
            boolean craftable,
            ItemStack charmStack,
            FakeItem charmFakeItem,
            CharmItemFeature charmFeature,
            int currentLevel,
            int targetLevel,
            Map<String, Integer> requiredIngredientCounts
    ) {}

    private UpgradeInfo getUpgradeInfo(CraftingInput input)
    {
        ItemStack charmStack = null;
        FakeItem charmFakeItem = null;
        CharmItemFeature charmFeature = null;
        int currentLevel = -1;

        Map<String, Integer> offeredCounts = new HashMap<>();

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            StoredCharmData storedCharm = CharmStackData.getSingleStoredCharm(stack).orElse(null);
            if (storedCharm != null) {
                FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(storedCharm.charmId());
                if (fakeItem != null && fakeItem.getFeature(CharmItemFeature.class) != null) {
                    if (charmStack != null) {
                        return invalid();
                    }

                    charmStack = stack;
                    charmFakeItem = fakeItem;
                    charmFeature = fakeItem.getFeature(CharmItemFeature.class);
                    currentLevel = storedCharm.level();
                    continue;
                }
            }

            String ingredientKey = getIngredientKey(stack);
            if (ingredientKey == null) {
                return invalid();
            }

            offeredCounts.merge(ingredientKey, stack.getCount(), Integer::sum);
        }

        if (charmStack == null || charmFakeItem == null || charmFeature == null) {
            return invalid();
        }

        if (!charmFeature.hasNextLevel(currentLevel)) {
            return invalid();
        }

        int targetLevel = currentLevel + 1;
        CharmLevelDefinition targetLevelDef = charmFeature.getLevelDefinition(targetLevel);

        Map<String, Integer> requiredCounts = new HashMap<>();
        for (CharmUpgradeDefinition ingredient : targetLevelDef.upgradeIngredients()) {
            requiredCounts.merge(ingredient.id(), ingredient.count(), Integer::sum);
        }

        for (String offeredKey : offeredCounts.keySet()) {
            if (!requiredCounts.containsKey(offeredKey)) {
                return invalid();
            }
        }

        for (Map.Entry<String, Integer> required : requiredCounts.entrySet()) {
            if (offeredCounts.getOrDefault(required.getKey(), 0) < required.getValue()) {
                return invalid();
            }
        }

        return new UpgradeInfo(
                true,
                charmStack,
                charmFakeItem,
                charmFeature,
                currentLevel,
                targetLevel,
                requiredCounts
        );
    }

    private static UpgradeInfo invalid() {
        return new UpgradeInfo(false, ItemStack.EMPTY, null, null, -1, -1, Map.of());
    }

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        return getUpgradeInfo(input).craftable;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        UpgradeInfo info = getUpgradeInfo(input);
        if (!info.craftable) {
            return ItemStack.EMPTY;
        }

        ItemStack result = info.charmStack.copy();
        info.charmFeature.setLevel(result, info.targetLevel);
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input)
    {
        UpgradeInfo info = getUpgradeInfo(input);
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        if (!info.craftable) {
            return remaining;
        }

        Map<String, Integer> toConsume = new HashMap<>(info.requiredIngredientCounts);

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack == info.charmStack) {
                remaining.set(i, ItemStack.EMPTY);
                continue;
            }

            String ingredientKey = getIngredientKey(stack);
            if (ingredientKey == null) {
                remaining.set(i, stack.copy());
                continue;
            }

            int stillNeeded = toConsume.getOrDefault(ingredientKey, 0);
            if (stillNeeded <= 0) {
                remaining.set(i, stack.copy());
                continue;
            }

            int consumeCount = Math.min(stillNeeded, stack.getCount());
            int leftover = stack.getCount() - consumeCount;

            toConsume.put(ingredientKey, stillNeeded - consumeCount);

            if (leftover > 0) {
                ItemStack copy = stack.copy();
                copy.setCount(leftover);
                remaining.set(i, copy);
            } else {
                remaining.set(i, ItemStack.EMPTY);
            }
        }

        return remaining;
    }

    private String getIngredientKey(ItemStack stack) {
        FakeItem fakeItem = FakeItems.getFakeItemFromStack(stack);
        if (fakeItem != null) {
            return fakeItem.id();
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null ? itemId.toString() : null;
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.UPGRADE_CHARM_SERIALIZER;
    }

    @Override
    public CraftingBookCategory category()
    {
        return CraftingBookCategory.EQUIPMENT;
    }

    @Override
    public RecipeBookCategory recipeBookCategory()
    {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
