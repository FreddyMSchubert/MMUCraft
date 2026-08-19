package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

public final class EnderiteScrapSmeltingRecipe extends AbstractCookingRecipe {
    public EnderiteScrapSmeltingRecipe() {
        super(
                new Recipe.CommonInfo(false),
                new CookingBookInfo(CookingBookCategory.BLOCKS, ""),
                Ingredient.of(Items.TEST_BLOCK),
                new ItemStackTemplate(Items.COMMAND_BLOCK),
                2.0F,
                200
        );
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return input.item().is(Items.TEST_BLOCK);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return matches(input, null)
                ? FakeItems.createFakeItemStack("enderite-scrap", 1)
                : ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    protected Item furnaceIcon() {
        return Items.FURNACE;
    }

    @Override
    public RecipeSerializer<EnderiteScrapSmeltingRecipe> getSerializer() {
        return MainModRecipes.ENDERITE_SCRAP_SMELTING_SERIALIZER;
    }

    @Override
    public RecipeType<? extends AbstractCookingRecipe> getType() {
        return RecipeType.SMELTING;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.FURNACE_BLOCKS;
    }
}
