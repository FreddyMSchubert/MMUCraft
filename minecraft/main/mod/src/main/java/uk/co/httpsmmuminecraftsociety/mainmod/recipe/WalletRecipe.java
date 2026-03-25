package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.WalletUtils;

public class WalletRecipe extends CustomRecipe
{
    @Override
    public boolean matches(CraftingInput inputs, Level level)
    {
        if (inputs.size() != 2) return false;

        boolean hasRabbitHide = false;
        boolean hasString = false;

        for (ItemStack item : inputs.items()) {
            if (item.isEmpty()) continue;

            if (item.getItem() == Items.RABBIT_HIDE)
                hasRabbitHide = true;
            if (item.getItem() == Items.STRING)
                hasString = true;
        }

        return hasRabbitHide && hasString;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        return WalletUtils.createWalletStack();
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.WALLET_SERIALIZER;
    }

    @Override
    public CraftingBookCategory category()
    {
        return CraftingBookCategory.MISC;
    }

    @Override
    public RecipeBookCategory recipeBookCategory()
    {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
