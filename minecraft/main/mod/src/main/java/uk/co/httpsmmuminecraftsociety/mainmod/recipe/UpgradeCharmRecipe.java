package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs.StackDef;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmLevelDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UpgradeCharmRecipe extends CustomRecipe
{
    private record OfferedStack(int slot, ItemStack stack) {}

    private record UpgradeInfo(
            boolean craftable,
            int charmSlot,
            ItemStack charmStack,
            CharmItemFeature charmFeature,
            int targetLevel,
            int[] consumeCounts
    ) {}

    private UpgradeInfo getUpgradeInfo(CraftingInput input)
    {
        int charmSlot = -1;
        ItemStack charmStack = ItemStack.EMPTY;
        CharmItemFeature charmFeature = null;
        int currentLevel = -1;

        List<OfferedStack> offeredStacks = new ArrayList<>();
        int totalOfferedUnits = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            StoredCharmData storedCharm = CharmStackData.getSingleStoredCharm(stack).orElse(null);
            if (storedCharm != null) {
                FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(storedCharm.charmId());
                if (fakeItem != null) {
                    CharmItemFeature feature = fakeItem.getFeature(CharmItemFeature.class);
                    if (feature != null) {
                        if (charmSlot != -1) {
                            return invalid(input.size());
                        }

                        charmSlot = i;
                        charmStack = stack;
                        charmFeature = feature;
                        currentLevel = storedCharm.level();
                        continue;
                    }
                }
            }

            offeredStacks.add(new OfferedStack(i, stack));
            totalOfferedUnits++;
        }

        if (charmSlot == -1 || charmFeature == null) {
            return invalid(input.size());
        }

        if (!charmFeature.hasNextLevel(currentLevel)) {
            return invalid(input.size());
        }

        int targetLevel = currentLevel + 1;
        CharmLevelDefinition targetLevelDef = charmFeature.getLevelDefinition(targetLevel);
        List<StackDef> requiredIngredients = targetLevelDef.upgradeIngredients();

        if (requiredIngredients.size() != totalOfferedUnits) {
            return invalid(input.size());
        }

        List<StackDef> orderedRequired = requiredIngredients.stream()
                .sorted(Comparator.comparingInt(StackDef::specificity).reversed())
                .toList();

        int[] remainingCounts = new int[input.size()];
        int[] consumeCounts = new int[input.size()];
        for (OfferedStack offered : offeredStacks) {
            remainingCounts[offered.slot()] = offered.stack().getCount();
        }

        if (!assignIngredients(orderedRequired, 0, offeredStacks, remainingCounts, consumeCounts)) {
            return invalid(input.size());
        }

        return new UpgradeInfo(
                true,
                charmSlot,
                charmStack,
                charmFeature,
                targetLevel,
                consumeCounts
        );
    }

    private static boolean assignIngredients(List<StackDef> requiredIngredients,
                                             int ingredientIndex,
                                             List<OfferedStack> offeredStacks,
                                             int[] remainingCounts,
                                             int[] consumeCounts) {
        if (ingredientIndex >= requiredIngredients.size()) {
            return true;
        }

        StackDef required = requiredIngredients.get(ingredientIndex);

        for (OfferedStack offered : offeredStacks) {
            int slot = offered.slot();

            if (remainingCounts[slot] <= 0) {
                continue;
            }
            if (!required.matches(offered.stack())) {
                continue;
            }

            remainingCounts[slot]--;
            consumeCounts[slot]++;

            if (assignIngredients(requiredIngredients, ingredientIndex + 1, offeredStacks, remainingCounts, consumeCounts)) {
                return true;
            }

            remainingCounts[slot]++;
            consumeCounts[slot]--;
        }

        return false;
    }

    private static UpgradeInfo invalid(int inputSize) {
        return new UpgradeInfo(false, -1, ItemStack.EMPTY, null, -1, new int[inputSize]);
    }

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        return getUpgradeInfo(input).craftable();
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        UpgradeInfo info = getUpgradeInfo(input);
        if (!info.craftable()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = info.charmStack().copy();
        info.charmFeature().setLevel(result, info.targetLevel());
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input)
    {
        UpgradeInfo info = getUpgradeInfo(input);
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        if (!info.craftable()) {
            return remaining;
        }

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (i == info.charmSlot()) {
                remaining.set(i, ItemStack.EMPTY);
                continue;
            }

            int consumed = info.consumeCounts()[i];
            if (consumed <= 0) {
                remaining.set(i, stack.copy());
                continue;
            }

            int leftover = stack.getCount() - consumed;
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
