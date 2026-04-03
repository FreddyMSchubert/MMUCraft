package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.StackStringUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class FakeShapelessCraftingRecipe extends CustomRecipe {
    private static final Codec<List<StackStringUtil.StackSpec>> INGREDIENTS_CODEC =
            Codec.list(StackStringUtil.StackSpec.CODEC)
                    .comapFlatMap(FakeShapelessCraftingRecipe::validateIngredients, Function.identity());

    private static final Codec<ResultSpec> RESULT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StackStringUtil.StackSpec.CODEC.fieldOf("stack").forGetter(ResultSpec::stack),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(ResultSpec::count)
    ).apply(instance, ResultSpec::new));

    public static final MapCodec<FakeShapelessCraftingRecipe> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    INGREDIENTS_CODEC.fieldOf("ingredients").forGetter(r -> r.ingredients),
                    RESULT_CODEC.fieldOf("result").forGetter(r -> r.result)
            ).apply(instance, FakeShapelessCraftingRecipe::new));

    private final List<StackStringUtil.StackSpec> ingredients;
    private final ResultSpec result;

    public FakeShapelessCraftingRecipe(List<StackStringUtil.StackSpec> ingredients,
                                       ResultSpec result) {
        if (!result.stack().canCreateStack()) {
            throw new IllegalArgumentException("shapeless recipe result cannot be a tag");
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("shapeless recipe must have at least 1 ingredient");
        }
        if (ingredients.size() > 9) {
            throw new IllegalArgumentException("shapeless recipe can have at most 9 ingredients");
        }

        this.ingredients = List.copyOf(ingredients);
        this.result = result;
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

        List<StackStringUtil.StackSpec> sortedIngredients = ingredients.stream()
                .sorted(Comparator.comparingInt(StackStringUtil.StackSpec::specificity).reversed())
                .toList();

        return matchBacktracking(sortedIngredients, 0, presentStacks, new boolean[presentStacks.size()]);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack stack = result.stack().createStack();
        stack.setCount(result.count());
        return stack;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return MainModRecipes.FAKE_CRAFTING_SHAPELESS_SERIALIZER;
    }

    private static boolean matchBacktracking(List<StackStringUtil.StackSpec> ingredients,
                                             int ingredientIndex,
                                             List<ItemStack> stacks,
                                             boolean[] used) {
        if (ingredientIndex == ingredients.size()) {
            return true;
        }

        StackStringUtil.StackSpec ingredient = ingredients.get(ingredientIndex);
        for (int i = 0; i < stacks.size(); i++) {
            if (used[i]) {
                continue;
            }
            if (!ingredient.matches(stacks.get(i))) {
                continue;
            }

            used[i] = true;
            if (matchBacktracking(ingredients, ingredientIndex + 1, stacks, used)) {
                return true;
            }
            used[i] = false;
        }

        return false;
    }

    private static DataResult<List<StackStringUtil.StackSpec>> validateIngredients(List<StackStringUtil.StackSpec> ingredients) {
        if (ingredients.isEmpty()) {
            return DataResult.error(() -> "shapeless recipe must have at least 1 ingredient");
        }
        if (ingredients.size() > 9) {
            return DataResult.error(() -> "shapeless recipe can have at most 9 ingredients");
        }

        return DataResult.success(List.copyOf(ingredients));
    }

    public record ResultSpec(StackStringUtil.StackSpec stack, int count) {
        public ResultSpec {
            if (stack == null) {
                throw new IllegalArgumentException("result stack must not be null");
            }
            if (count < 1) {
                throw new IllegalArgumentException("result count must be at least 1");
            }
        }
    }
}
