package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;

public class CharmorVisualResetRecipe extends CustomRecipe
{
    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        ItemStack armor = ItemStack.EMPTY;
        boolean hasGlass = false;

        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS) && armor.isEmpty()) {
                armor = stack;
            } else if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains("glass") && !hasGlass) {
                hasGlass = true;
            } else {
                return false;
            }
        }

        return input.ingredientCount() == 2 && hasGlass && !armor.isEmpty()
                && !CharmStackData.getStoredCharms(armor).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        for (ItemStack stack : input.items()) {
            if (stack.is(ModItemTagProvider.CHARM_COMBINABLE_ARMOR_ITEMS)) {
                ItemStack result = stack.copy();
                result.set(DataComponents.EQUIPPABLE, result.getItem().components().get(DataComponents.EQUIPPABLE));
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.CHARMOR_VISUAL_RESET_SERIALIZER;
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
