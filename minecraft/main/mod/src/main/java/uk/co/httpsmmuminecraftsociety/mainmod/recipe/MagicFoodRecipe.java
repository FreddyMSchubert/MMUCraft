package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.magic.MagicFoodEffects;

public final class MagicFoodRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        FakeItem food = null;
        int dust = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (FakeItems.isSpecificFakeItem(stack, "magic-dust")) {
                dust++;
                continue;
            }
            FakeItem candidate = FakeItems.getFakeItemFromStack(stack);
            if (food != null || candidate == null || candidate.magicEffect() == null
                    || MagicFoodEffects.isActivated(stack)) return false;
            food = candidate;
        }
        return food != null && dust == food.magicEffect().dust();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        if (!matches(input, null)) return ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            FakeItem food = FakeItems.getFakeItemFromStack(stack);
            if (food != null && food.magicEffect() != null) return MagicFoodEffects.activate(food);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.MAGIC_FOOD_SERIALIZER;
    }
}
