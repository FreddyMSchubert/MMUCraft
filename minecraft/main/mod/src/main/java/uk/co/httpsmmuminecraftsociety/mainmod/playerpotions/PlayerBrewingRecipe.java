package uk.co.httpsmmuminecraftsociety.mainmod.playerpotions;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MainModRecipes;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

public final class PlayerBrewingRecipe extends BrewingRecipe {
    public static final RecipeSerializer<BrewingRecipe> SERIALIZER = MainModRecipes.codecBacked(
            BrewingRecipe.MAP_CODEC.xmap(PlayerBrewingRecipe::new, recipe -> recipe));

    private PlayerBrewingRecipe(BrewingRecipe recipe) {
        super(recipe.getInput(), recipe.getReagent(), recipe.getOutput());
    }

    @Override
    public boolean matches(BrewingInput input) {
        if (!FeatureToggles.isEnabled(FeatureToggles.CIRCUS) || !super.matches(input)) return false;
        ItemStack reagent = input.reagent();
        if (reagent.is(Items.PLAYER_HEAD)) {
            return PlayerPotions.isDeathHead(reagent)
                    && SkinColors.ready(reagent.get(net.minecraft.core.component.DataComponents.PROFILE).partialProfile());
        }
        if (!PlayerPotions.isDisguise(input.input())) return false;
        if (reagent.is(Items.REDSTONE)) {
            return PlayerPotions.duration(PlayerPotions.identity(input.input())) == PlayerPotions.DEFAULT_TICKS;
        }
        return true;
    }

    @Override
    public ItemStack assemble(BrewingInput input) {
        return PlayerPotions.brew(input.input(), input.reagent());
    }

    @Override
    public RecipeSerializer<BrewingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
