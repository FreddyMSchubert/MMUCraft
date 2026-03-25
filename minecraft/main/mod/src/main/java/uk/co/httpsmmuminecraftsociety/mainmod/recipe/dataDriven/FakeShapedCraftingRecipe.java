package uk.co.httpsmmuminecraftsociety.mainmod.recipe.dataDriven;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.MainModRecipes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FakeShapedCraftingRecipe extends AbstractFakeCraftingRecipe {

    public static final MapCodec<FakeShapedCraftingRecipe> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    CodecUtils.PATTERN_CODEC.fieldOf("pattern").forGetter(r -> r.pattern),
                    CodecUtils.KEY_CODEC.fieldOf("key").forGetter(r -> r.key),
                    FakeResult.CODEC.fieldOf("result").forGetter(r -> r.result)
            ).apply(instance, FakeShapedCraftingRecipe::new));

    private final List<String> pattern;
    private final Map<String, FakeIngredient> key;
    private final int width;
    private final int height;
    private final List<FakeIngredient> cells;

    public FakeShapedCraftingRecipe(List<String> pattern, Map<String, FakeIngredient> key, FakeResult result) {
        super(result);

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
        List<FakeIngredient> flat = new ArrayList<>(rowWidth * trimmed.size());
        for (String row : trimmed) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c == ' ') {
                    flat.add(null);
                    continue;
                }

                String symbol = String.valueOf(c);
                FakeIngredient ingredient = key.get(symbol);
                if (ingredient == null) {
                    throw new IllegalArgumentException("pattern uses undefined key symbol '" + symbol + "'");
                }
                usedSymbols.add(symbol);
                flat.add(ingredient);
            }
        }

        for (String symbol : key.keySet()) {
            if (!usedSymbols.contains(symbol)) {
                throw new IllegalArgumentException("key defines unused symbol '" + symbol + "'");
            }
        }

        this.pattern = List.copyOf(trimmed);
        this.key = Map.copyOf(key);
        this.width = rowWidth;
        this.height = trimmed.size();
        this.cells = java.util.Collections.unmodifiableList(new ArrayList<>(flat));
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
    public ItemStack assemble(CraftingInput input)
    {
        return result.createStack();
    }

    private boolean matchesAt(CraftingInput input, int offX, int offY, boolean mirrored) {
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                FakeIngredient expected = ingredientAtGridPosition(x, y, offX, offY, mirrored);
                ItemStack actual = input.getItem(x + y * input.width());

                if (expected == null) {
                    if (!actual.isEmpty()) return false;
                } else if (!expected.matches(actual)) {
                    return false;
                }
            }
        }
        return true;
    }

    private FakeIngredient ingredientAtGridPosition(int gridX, int gridY, int offX, int offY, boolean mirrored) {
        int localX = gridX - offX;
        int localY = gridY - offY;
        if (localX < 0 || localY < 0 || localX >= width || localY >= height) {
            return null;
        }

        int recipeX = mirrored ? (width - 1 - localX) : localX;
        return cells.get(recipeX + localY * width);
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer()
    {
        return MainModRecipes.FAKE_CRAFTING_SHAPED_SERIALIZER;
    }
}
