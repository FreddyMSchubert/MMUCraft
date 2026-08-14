package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;

public class SeperateCosmeticRecipe extends CustomRecipe
{
    @Override
    public boolean matches(CraftingInput recipeInput, Level level)
    {
        if (recipeInput.ingredientCount() > 1) return false;

        ItemStack stack = recipeInput.items().getFirst();
        CosmeticsManager.CosmeticsInfo cinfo = CosmeticsManager.determineCosmeticType(stack);
        return cinfo.isCosmetic() && cinfo.isHelmet();
    }

    @Override
    public ItemStack assemble(CraftingInput recipeInput)
    {
        return CosmeticsManager.cosmeticFromHelmetReplica(recipeInput.items().getFirst());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput craftingInput)
    {
        NonNullList<ItemStack> list = NonNullList.withSize(craftingInput.ingredientCount(), ItemStack.EMPTY);
        for (int i = 0; i < craftingInput.ingredientCount(); i++) {
            ItemStack stack = craftingInput.items().get(i).copy();
            if (!stack.isEmpty()) {
                list.set(i, CosmeticsManager.pumpkinReplicaToHelmet(stack));
                continue;
            }
            list.set(i, stack);
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
        return MainModRecipes.SEPERATE_COSMETIC_SERIALIZER;
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
