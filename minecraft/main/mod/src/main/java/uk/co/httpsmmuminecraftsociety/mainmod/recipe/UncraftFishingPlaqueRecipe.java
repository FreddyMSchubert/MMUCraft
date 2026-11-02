package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingPlaques;

public final class UncraftFishingPlaqueRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 1) return false;
        return input.items().stream().anyMatch(stack ->
                FishingPlaques.isPlaque(stack) && !FishingPlaques.fish(stack).isEmpty());
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        if (!matches(input, null)) return ItemStack.EMPTY;
        ItemStack plaque = input.items().stream().filter(FishingPlaques::isPlaque).findFirst().orElseThrow();
        return FishingPlaques.fish(plaque);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        if (!matches(input, null)) return remaining;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack plaque = input.getItem(slot);
            if (FishingPlaques.isPlaque(plaque)) {
                remaining.set(slot, FishingPlaques.withFish(plaque, ItemStack.EMPTY));
                break;
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.UNCRAFT_FISHING_PLAQUE_SERIALIZER;
    }
}
