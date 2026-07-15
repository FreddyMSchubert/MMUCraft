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
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FishItemFeature;

public final class FishCookingRecipe extends AbstractCookingRecipe {
    public FishCookingRecipe() {
        super(
                new Recipe.CommonInfo(false),
                new CookingBookInfo(CookingBookCategory.FOOD, ""),
                Ingredient.of(Items.COMMAND_BLOCK),
                new ItemStackTemplate(Items.COOKED_COD),
                0.35F,
                200
        );
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return fishFeature(input.item()) != null;
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        FishItemFeature fish = fishFeature(input.item());
        return fish == null
                ? ItemStack.EMPTY
                : FakeItems.createFakeItemStack(fish.furnaceResult().itemId(), 1);
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
    public RecipeSerializer<FishCookingRecipe> getSerializer() {
        return MainModRecipes.FISH_COOKING_SERIALIZER;
    }

    @Override
    public RecipeType<? extends AbstractCookingRecipe> getType() {
        return RecipeType.SMELTING;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.FURNACE_FOOD;
    }

    private static FishItemFeature fishFeature(ItemStack stack) {
        FakeItem item = FakeItems.getFakeItemFromStack(stack);
        return item == null ? null : item.getFeature(FishItemFeature.class);
    }
}
