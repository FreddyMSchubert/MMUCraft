package uk.co.httpsmmuminecraftsociety.mainmod.recipe.dataDriven;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

public abstract class AbstractFakeCraftingRecipe extends CustomRecipe
{
    protected final FakeResult result;

    protected AbstractFakeCraftingRecipe(FakeResult result) {
        this.result = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        return null;
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
    public boolean isSpecial() {
        return true;
    }
}