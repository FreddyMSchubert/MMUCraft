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
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.util.FakeRecipeUtil;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.StackStringUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class FakeShapedCraftingRecipe extends CustomRecipe {
    private static final Codec<List<String>> PATTERN_CODEC = Codec.list(Codec.STRING)
            .comapFlatMap(FakeShapedCraftingRecipe::validatePattern, Function.identity());

    private static final Codec<Map<String, StackStringUtil.StackSpec>> KEY_CODEC =
            Codec.unboundedMap(Codec.STRING, StackStringUtil.StackSpec.CODEC)
                    .comapFlatMap(FakeShapedCraftingRecipe::validateKey, Function.identity());

    private static final Codec<ResultSpec> RESULT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StackStringUtil.StackSpec.CODEC.fieldOf("stack").forGetter(ResultSpec::stack),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(ResultSpec::count)
    ).apply(instance, ResultSpec::new));

    public static final MapCodec<FakeShapedCraftingRecipe> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    PATTERN_CODEC.fieldOf("pattern").forGetter(r -> r.pattern),
                    KEY_CODEC.fieldOf("key").forGetter(r -> r.key),
                    RESULT_CODEC.fieldOf("result").forGetter(r -> r.result)
            ).apply(instance, FakeShapedCraftingRecipe::new));

    private final List<String> pattern;
    private final Map<String, StackStringUtil.StackSpec> key;
    private final ResultSpec result;

    private final int width;
    private final int height;
    private final StackStringUtil.StackSpec[] cells;

    public FakeShapedCraftingRecipe(List<String> pattern,
                                    Map<String, StackStringUtil.StackSpec> key,
                                    ResultSpec result) {
        if (!result.stack().canCreateStack()) {
            throw new IllegalArgumentException("shaped recipe result cannot be a tag");
        }

        List<String> trimmed = FakeRecipeUtil.trimPattern(pattern);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("pattern must contain at least one non-space symbol");
        }
        if (trimmed.size() > 3) {
            throw new IllegalArgumentException("pattern can have at most 3 rows");
        }

        int rowWidth = trimmed.getFirst().length();
        if (rowWidth < 1 || rowWidth > 3) {
            throw new IllegalArgumentException("pattern width must be between 1 and 3");
        }

        for (String row : trimmed) {
            if (row.length() != rowWidth) {
                throw new IllegalArgumentException("all pattern rows must have the same width");
            }
        }

        Set<String> usedSymbols = new HashSet<>();
        StackStringUtil.StackSpec[] flat = new StackStringUtil.StackSpec[rowWidth * trimmed.size()];
        int index = 0;

        for (String row : trimmed) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c == ' ') {
                    flat[index++] = null;
                    continue;
                }

                String symbol = String.valueOf(c);
                StackStringUtil.StackSpec ingredient = key.get(symbol);
                if (ingredient == null) {
                    throw new IllegalArgumentException("pattern uses undefined key symbol '" + symbol + "'");
                }

                usedSymbols.add(symbol);
                flat[index++] = ingredient;
            }
        }

        for (String symbol : key.keySet()) {
            if (!usedSymbols.contains(symbol)) {
                throw new IllegalArgumentException("key defines unused symbol '" + symbol + "'");
            }
        }

        this.pattern = List.copyOf(trimmed);
        this.key = Map.copyOf(key);
        this.result = result;
        this.width = rowWidth;
        this.height = trimmed.size();
        this.cells = flat;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() < width || input.height() < height) {
            return false;
        }

        for (int offY = 0; offY <= input.height() - height; offY++) {
            for (int offX = 0; offX <= input.width() - width; offX++) {
                if (matchesAt(input, offX, offY, false) || matchesAt(input, offX, offY, true)) {
                    return true;
                }
            }
        }

        return false;
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
        return MainModRecipes.FAKE_CRAFTING_SHAPED_SERIALIZER;
    }

    private boolean matchesAt(CraftingInput input, int offX, int offY, boolean mirrored) {
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                StackStringUtil.StackSpec expected = ingredientAtGridPosition(x, y, offX, offY, mirrored);
                ItemStack actual = input.getItem(x + y * input.width());

                if (expected == null) {
                    if (!actual.isEmpty()) {
                        return false;
                    }
                } else if (!expected.matches(actual)) {
                    return false;
                }
            }
        }

        return true;
    }

    private StackStringUtil.StackSpec ingredientAtGridPosition(int gridX,
                                                               int gridY,
                                                               int offX,
                                                               int offY,
                                                               boolean mirrored) {
        int localX = gridX - offX;
        int localY = gridY - offY;

        if (localX < 0 || localY < 0 || localX >= width || localY >= height) {
            return null;
        }

        int recipeX = mirrored ? (width - 1 - localX) : localX;
        return cells[recipeX + localY * width];
    }

    private static DataResult<List<String>> validatePattern(List<String> pattern) {
        if (pattern.isEmpty()) {
            return DataResult.error(() -> "pattern must have at least 1 row");
        }
        if (pattern.size() > 3) {
            return DataResult.error(() -> "pattern can have at most 3 rows");
        }

        int expectedWidth = -1;
        for (String row : pattern) {
            if (row.isEmpty()) {
                return DataResult.error(() -> "pattern rows must not be empty");
            }
            if (row.length() > 3) {
                return DataResult.error(() -> "pattern rows can have at most 3 columns");
            }
            if (expectedWidth == -1) {
                expectedWidth = row.length();
            } else if (row.length() != expectedWidth) {
                return DataResult.error(() -> "all pattern rows must have the same width");
            }
        }

        return DataResult.success(List.copyOf(pattern));
    }

    private static DataResult<Map<String, StackStringUtil.StackSpec>> validateKey(Map<String, StackStringUtil.StackSpec> key) {
        for (String symbol : key.keySet()) {
            if (symbol.length() != 1) {
                return DataResult.error(() -> "recipe key symbols must be exactly 1 character: '" + symbol + "'");
            }
            if (symbol.charAt(0) == ' ') {
                return DataResult.error(() -> "space cannot be used as a recipe key symbol");
            }
        }

        return DataResult.success(Map.copyOf(key));
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
