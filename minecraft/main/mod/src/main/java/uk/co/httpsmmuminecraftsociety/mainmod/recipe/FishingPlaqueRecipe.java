package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingPlaques;

public final class FishingPlaqueRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        int plaques = 0;
        int fish = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (FishingPlaques.isPlaque(stack) && FishingPlaques.fish(stack).isEmpty()) plaques++;
            else if (FishingPlaques.isFish(stack)) fish++;
            else return false;
        }
        return plaques == 1 && fish == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        if (!matches(input, null)) return ItemStack.EMPTY;
        ItemStack plaque = input.items().stream().filter(FishingPlaques::isPlaque).findFirst().orElseThrow();
        ItemStack fish = input.items().stream().filter(FishingPlaques::isFish).findFirst().orElseThrow();
        return FishingPlaques.withFish(plaque, fish);
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.FISHING_PLAQUE_SERIALIZER;
    }
}
