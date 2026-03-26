package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.WalletCharm;

import java.util.ArrayList;
import java.util.List;

public class WalletInsertRecipe extends CustomRecipe
{
    private record craftingInfo(boolean craftable, ItemStack wallet, List<ItemStack> coins) {}

    private craftingInfo getCraftingInfo(CraftingInput recipeInput) {
        ItemStack wallet = ItemStack.EMPTY;
        List<ItemStack> coins = new ArrayList<>();

        for (ItemStack stack : recipeInput.items()) {
            if (WalletCharm.isWallet(stack) != -1) {
                if (wallet != ItemStack.EMPTY)
                    return new craftingInfo(false, null, null);
                wallet = stack;
                continue;
            }
            if (WalletCharm.isCoin(stack) > 0) {
                coins.add(stack);
                continue;
            }
            return new craftingInfo(false, null, null);
        }

        return new craftingInfo(wallet != ItemStack.EMPTY && !coins.isEmpty(), wallet, coins);
    }

    @Override
    public boolean matches(CraftingInput recipeInput, Level level)
    {
        if (recipeInput.size() < 2) return false;
        return getCraftingInfo(recipeInput).craftable();
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        craftingInfo cinfo = getCraftingInfo(input);

        ItemStack newWallet = cinfo.wallet().copy();
        for (ItemStack coin : cinfo.coins()) {
            WalletCharm.addCoinToWallet(newWallet, coin);
        }

        return newWallet;
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
