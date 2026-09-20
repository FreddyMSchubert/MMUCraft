package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.RedstoneRemoteCharm;

public final class RedstoneRemoteResetRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 1) return false;
        return input.items().stream().anyMatch(RedstoneRemoteCharm::isRemote);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        if (input.ingredientCount() != 1) return ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (!RedstoneRemoteCharm.isRemote(stack)) continue;
            ItemStack result = stack.copyWithCount(1);
            RedstoneRemoteCharm.clearLink(result);
            return result;
        }
        return ItemStack.EMPTY;
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
        return MainModRecipes.REDSTONE_REMOTE_RESET_SERIALIZER;
    }
}
