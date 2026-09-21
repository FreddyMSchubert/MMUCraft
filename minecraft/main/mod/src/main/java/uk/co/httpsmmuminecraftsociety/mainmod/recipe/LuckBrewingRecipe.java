package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

public final class LuckBrewingRecipe extends BrewingRecipe {
    public static final RecipeSerializer<BrewingRecipe> SERIALIZER = MainModRecipes.codecBacked(
            BrewingRecipe.MAP_CODEC.xmap(LuckBrewingRecipe::new, recipe -> recipe));

    private LuckBrewingRecipe(BrewingRecipe recipe) {
        super(recipe.getInput(), recipe.getReagent(), recipe.getOutput());
    }

    @Override
    public boolean matches(BrewingInput input) {
        return super.matches(input) && FakeItems.isSpecificFakeItem(input.reagent(), "4-leaf-clover");
    }

    @Override
    public RecipeSerializer<BrewingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
