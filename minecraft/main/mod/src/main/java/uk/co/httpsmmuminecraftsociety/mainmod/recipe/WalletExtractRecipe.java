package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.WalletCharm;

public class WalletExtractRecipe extends CustomRecipe
{
    @Override
    public boolean matches(CraftingInput recipeInput, Level level)
    {
        if (recipeInput.size() != 1) return false;
        return WalletCharm.isWallet(recipeInput.items().getFirst()) >= 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        ItemStack walletCopy = input.items().getFirst().copy();
        return WalletCharm.removeCoinsFromWallet(walletCopy);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input)
    {
        NonNullList<ItemStack> list = NonNullList.withSize(input.ingredientCount(), ItemStack.EMPTY);
        for (int i = 0; i < input.ingredientCount(); i++) {
            ItemStack wallet = input.items().get(i).copy();
            if (!wallet.isEmpty()) {
                WalletCharm.removeCoinsFromWallet(wallet);
                list.set(i, wallet);
                return list;
            }
        }
        return list;
    }

    @Override
    public PlacementInfo placementInfo()
    {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.SEPERATE_CHARMOR_SERIALIZER;
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
