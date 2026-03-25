package uk.co.httpsmmuminecraftsociety.mainmod.recipe.dataDriven;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MainModRecipes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FakeShapelessCraftingRecipe extends AbstractFakeCraftingRecipe {
    public static final MapCodec<FakeShapelessCraftingRecipe> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    CodecUtils.SHAPELESS_INGREDIENTS_CODEC.fieldOf("ingredients").forGetter(r -> r.ingredients),
                    FakeResult.CODEC.fieldOf("result").forGetter(r -> r.result)
            ).apply(instance, FakeShapelessCraftingRecipe::new));

    private final List<FakeIngredient> ingredients;

    public FakeShapelessCraftingRecipe(List<FakeIngredient> ingredients, FakeResult result) {
        super(result);
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("shapeless recipe must have at least 1 ingredient");
        }
        if (ingredients.size() > 9) {
            throw new IllegalArgumentException("shapeless recipe can have at most 9 ingredients");
        }
        this.ingredients = List.copyOf(ingredients);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        List<ItemStack> presentStacks = new ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) {
                presentStacks.add(stack);
            }
        }

        if (presentStacks.size() != ingredients.size()) {
            return false;
        }

        List<FakeIngredient> sortedIngredients = ingredients.stream()
                .sorted(Comparator.comparingInt(FakeIngredient::specificity).reversed())
                .toList();

        return matchBacktracking(sortedIngredients, 0, presentStacks, new boolean[presentStacks.size()]);
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        return result.createStack();
    }

    private static boolean matchBacktracking(List<FakeIngredient> ingredients,
                                             int ingredientIndex,
                                             List<ItemStack> stacks,
                                             boolean[] used) {
        if (ingredientIndex == ingredients.size()) {
            return true;
        }

        FakeIngredient ingredient = ingredients.get(ingredientIndex);
        for (int i = 0; i < stacks.size(); i++) {
            if (used[i]) continue;
            if (!ingredient.matches(stacks.get(i))) continue;

            used[i] = true;
            if (matchBacktracking(ingredients, ingredientIndex + 1, stacks, used)) {
                return true;
            }
            used[i] = false;
        }

        return false;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.FAKE_CRAFTING_SHAPELESS_SERIALIZER;
    }
}
