package uk.co.httpsmmuminecraftsociety.mainmod.recipe.dataDriven;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CodecUtils {
    private CodecUtils() {}

    static final Codec<List<String>> PATTERN_CODEC = Codec.list(Codec.STRING)
            .comapFlatMap(CodecUtils::validatePattern, pattern -> pattern);

    static final Codec<Map<String, FakeIngredient>> KEY_CODEC = Codec.unboundedMap(Codec.STRING, FakeIngredient.CODEC)
            .comapFlatMap(CodecUtils::validateKey, key -> key);

    static final Codec<List<FakeIngredient>> SHAPELESS_INGREDIENTS_CODEC = Codec.list(FakeIngredient.CODEC)
            .comapFlatMap(CodecUtils::validateShapelessIngredientList, list -> list);

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

    private static DataResult<Map<String, FakeIngredient>> validateKey(Map<String, FakeIngredient> key) {
        Map<String, FakeIngredient> copy = new HashMap<>();
        for (Map.Entry<String, FakeIngredient> entry : key.entrySet()) {
            String symbol = entry.getKey();
            if (symbol.length() != 1) {
                return DataResult.error(() -> "recipe key symbols must be exactly 1 character: '" + symbol + "'");
            }
            if (symbol.charAt(0) == ' ') {
                return DataResult.error(() -> "space cannot be used as a recipe key symbol");
            }
            copy.put(symbol, entry.getValue());
        }
        return DataResult.success(Map.copyOf(copy));
    }

    private static DataResult<List<FakeIngredient>> validateShapelessIngredientList(List<FakeIngredient> ingredients) {
        if (ingredients.isEmpty()) {
            return DataResult.error(() -> "shapeless recipe must have at least 1 ingredient");
        }
        if (ingredients.size() > 9) {
            return DataResult.error(() -> "shapeless recipe can have at most 9 ingredients");
        }
        return DataResult.success(List.copyOf(ingredients));
    }
}
