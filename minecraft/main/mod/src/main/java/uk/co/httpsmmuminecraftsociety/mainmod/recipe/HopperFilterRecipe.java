package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.HopperFilter;

public final class HopperFilterRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return isCreation(input) || HopperFilter.canConfigure(input.items());
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        if (isCreation(input)) return HopperFilter.create();
        return HopperFilter.configure(input.items());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        if (isCreation(input)) return CraftingRecipe.defaultCraftingReminder(input);
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty() && !HopperFilter.isFilter(stack)) remaining.set(slot, stack.copyWithCount(1));
        }
        return remaining;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.HOPPER_FILTER_SERIALIZER;
    }

    private static boolean isCreation(CraftingInput input) {
        return input.ingredientCount() == 2
                && input.items().stream().anyMatch(stack -> stack.is(Items.COPPER_GRATE.weathering().unaffected()))
                && input.items().stream().anyMatch(stack -> stack.is(Items.PAPER));
    }
}
