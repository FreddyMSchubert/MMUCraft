package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

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
        ItemStack inputStack = recipeInput.items().getFirst();

        String cosmeticModel = inputStack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY).getString(0);
        if (cosmeticModel == null) {
            return ItemStack.EMPTY;
        }

        ItemStack cosmetic = FakeItems.ID_MAP.get(cosmeticModel).createItemStack();

        DyedItemColor dyedColor = inputStack.get(DataComponents.DYED_COLOR);
        if (dyedColor != null) {
            cosmetic.set(DataComponents.DYED_COLOR, dyedColor);
        }
        CompoundTag nbt = inputStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (nbt.contains(CosmeticsManager.COLOR_CYCLING_BOOLEAN)) {
            CompoundTag cosmeticNbt = cosmetic.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            cosmeticNbt.putBoolean(CosmeticsManager.COLOR_CYCLING_BOOLEAN, nbt.getBooleanOr(CosmeticsManager.COLOR_CYCLING_BOOLEAN, false));
            cosmetic.set(DataComponents.CUSTOM_DATA, CustomData.of(cosmeticNbt));
        }

        return cosmetic;
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
