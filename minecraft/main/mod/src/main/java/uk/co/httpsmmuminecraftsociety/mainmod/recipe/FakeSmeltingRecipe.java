package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

public final class FakeSmeltingRecipe extends AbstractCookingRecipe {
    public static final MapCodec<FakeSmeltingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(recipe -> recipe.ingredient),
            Codec.STRING.fieldOf("result").forGetter(recipe -> recipe.resultId),
            Codec.FLOAT.fieldOf("experience").forGetter(recipe -> recipe.experience),
            Codec.INT.fieldOf("cookingtime").forGetter(recipe -> recipe.cookingTime)
    ).apply(instance, FakeSmeltingRecipe::new));

    private final Ingredient ingredient;
    private final String resultId;
    private final float experience;
    private final int cookingTime;

    public FakeSmeltingRecipe(Ingredient ingredient, String resultId, float experience, int cookingTime) {
        super(
                new Recipe.CommonInfo(false),
                new CookingBookInfo(CookingBookCategory.BLOCKS, ""),
                ingredient,
                new ItemStackTemplate(Items.HEART_OF_THE_SEA),
                experience,
                cookingTime
        );
        if (!resultId.startsWith("mainmod:") || cookingTime < 1 || experience < 0) {
            throw new IllegalArgumentException("invalid fake smelting recipe");
        }
        this.ingredient = ingredient;
        this.resultId = resultId;
        this.experience = experience;
        this.cookingTime = cookingTime;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return matches(input, null)
                ? FakeItems.createFakeItemStack(resultId.substring("mainmod:".length()), 1)
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
    public RecipeSerializer<FakeSmeltingRecipe> getSerializer() {
        return MainModRecipes.FAKE_SMELTING_SERIALIZER;
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
