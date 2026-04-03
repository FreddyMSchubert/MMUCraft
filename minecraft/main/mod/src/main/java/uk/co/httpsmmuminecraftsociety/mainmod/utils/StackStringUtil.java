package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.util.FakeRecipeUtil;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StackStringUtil {
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final String FAKE_NAMESPACE = "mainmod";

    private static final String DUMMY_COMPONENT_PARSE_ITEM = "minecraft:stone";

    private StackStringUtil() {}

    public static boolean matches(String description, ItemStack stack, HolderLookup.Provider registries) {
        return parse(registries, description).matches(stack);
    }

    public static ItemStack create(String description, HolderLookup.Provider registries) {
        return parse(registries, description).createStack();
    }

    static StackSpec parse(HolderLookup.Provider registries, String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("stack description must not be null");
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("stack description must not be blank");
        }

        if (trimmed.startsWith("#")) {
            return parseTag(trimmed);
        }

        String baseId = extractBaseId(trimmed);
        String suffix = trimmed.substring(baseId.length());

        Identifier identifier = Identifier.tryParse(baseId);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid stack description id: '" + raw + "'");
        }

        String namespace = identifier.getNamespace();
        if (FAKE_NAMESPACE.equals(namespace)) {
            return parseFakeItem(registries, trimmed, identifier.getPath(), suffix);
        }

        if (VANILLA_NAMESPACE.equals(namespace)) {
            return new ItemSpec(trimmed, parseVanillaItem(registries, trimmed));
        }

        throw new IllegalArgumentException(
                "Unsupported stack description namespace '" + namespace + "' in '" + raw + "'. " +
                        "Use minecraft: for registered items, mainmod: for fake items, or #namespace:path for tags."
        );
    }

    private static StackSpec parseTag(String raw) {
        if (raw.indexOf('[') >= 0) {
            throw new IllegalArgumentException("tag stack descriptions do not support component suffixes: '" + raw + "'");
        }

        String tagId = raw.substring(1);
        Identifier location = Identifier.tryParse(tagId);
        if (location == null) {
            throw new IllegalArgumentException("Invalid tag id in stack description: '" + raw + "'");
        }

        return new TagSpec(raw, TagKey.create(Registries.ITEM, location));
    }

    private static StackSpec parseFakeItem(HolderLookup.Provider registries,
                                           String raw,
                                           String fakeItemId,
                                           String suffix) {
        if (!FakeRecipeUtil.isKnownFakeItem(fakeItemId)) {
            throw new IllegalArgumentException("Unknown fakeitem id: " + fakeItemId);
        }

        DataComponentPatch components = parseComponentsOnly(registries, suffix, raw);
        validateFakePatch(components, raw);

        return new FakeItemSpec(raw, fakeItemId, components);
    }

    private static DataComponentPatch parseComponentsOnly(HolderLookup.Provider registries,
                                                          String suffix,
                                                          String raw) {
        ParsedItem parsed = parseVanillaItem(registries, DUMMY_COMPONENT_PARSE_ITEM + suffix);
        return parsed.components();
    }

    private static void validateFakePatch(DataComponentPatch patch, String raw) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            if (entry.getKey() == DataComponents.CUSTOM_MODEL_DATA) {
                throw new IllegalArgumentException(
                        "Fake item stack descriptions must not override or remove minecraft:custom_model_data: '" + raw + "'"
                );
            }
        }
    }

    private static String extractBaseId(String raw) {
        int bracketIndex = raw.indexOf('[');
        if (bracketIndex < 0) {
            return raw;
        }

        if (!raw.endsWith("]")) {
            throw new IllegalArgumentException("Invalid stack description, missing closing ']': '" + raw + "'");
        }

        return raw.substring(0, bracketIndex);
    }

    private static ParsedItem parseVanillaItem(HolderLookup.Provider registries, String raw) {
        try {
            ItemInput parsed = new ItemParser(registries).parse(new StringReader(raw));
            return new ParsedItem(raw, parsed.item(), parsed.components());
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Invalid item stack description '" + raw + "': " + e.getMessage(), e);
        }
    }

    private static <T> DataResult<StackSpec> parseFromOps(DynamicOps<T> ops, String raw) {
        return extractRegistries(ops).flatMap(registries -> {
            try {
                return DataResult.success(parse(registries, raw));
            } catch (IllegalArgumentException e) {
                return DataResult.error(e::getMessage);
            }
        });
    }

    private static <T> DataResult<HolderLookup.Provider> extractRegistries(DynamicOps<T> ops) {
        if (ops instanceof RegistryOps<?> registryOps
                && registryOps.lookupProvider instanceof RegistryOps.HolderLookupAdapter adapter) {
            return DataResult.success(adapter.lookupProvider);
        }

        return DataResult.error(() -> "Missing registry context for stack-string decoding");
    }

    private static boolean matchesPatchedComponents(ItemStack stack, DataComponentPatch patch) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            if (!matchesComponentUnchecked(stack, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean matchesComponentUnchecked(ItemStack stack,
                                                     DataComponentType<?> type,
                                                     Optional<?> expected) {
        return matchesComponent(stack, (DataComponentType) type, (Optional) expected);
    }

    private static <T> boolean matchesComponent(ItemStack stack,
                                                DataComponentType<T> type,
                                                Optional<T> expected) {
        if (expected.isPresent()) {
            return Objects.equals(stack.get(type), expected.get());
        }
        return !stack.has(type);
    }

    private static void applyPatch(ItemStack stack, DataComponentPatch patch) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            applyComponentUnchecked(stack, entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyComponentUnchecked(ItemStack stack,
                                                DataComponentType<?> type,
                                                Optional<?> value) {
        applyComponent(stack, (DataComponentType) type, (Optional) value);
    }

    private static <T> void applyComponent(ItemStack stack,
                                           DataComponentType<T> type,
                                           Optional<T> value) {
        if (value.isPresent()) {
            stack.set(type, value.get());
        } else {
            stack.remove(type);
        }
    }

    public sealed interface StackSpec permits ItemSpec, FakeItemSpec, TagSpec {
        Codec<StackSpec> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<StackSpec, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getStringValue(input)
                        .flatMap(raw -> parseFromOps(ops, raw))
                        .map(value -> Pair.of(value, ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(StackSpec input, DynamicOps<T> ops, T prefix) {
                return ops.mergeToPrimitive(prefix, ops.createString(input.raw()));
            }
        };

        String raw();

        boolean matches(ItemStack stack);

        ItemStack createStack();

        int specificity();

        default boolean canCreateStack() {
            return true;
        }
    }

    public record ItemSpec(String raw, ParsedItem item) implements StackSpec {
        public ItemSpec {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("raw must not be blank");
            }
            if (item == null) {
                throw new IllegalArgumentException("item must not be null");
            }
        }

        @Override
        public boolean matches(ItemStack stack) {
            return item.matches(stack);
        }

        @Override
        public ItemStack createStack() {
            return item.createStack(1);
        }

        @Override
        public int specificity() {
            return 2 + item.componentCount();
        }
    }

    public record FakeItemSpec(String raw, String fakeItemId, DataComponentPatch components) implements StackSpec {
        public FakeItemSpec {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("raw must not be blank");
            }
            if (fakeItemId == null || fakeItemId.isBlank()) {
                throw new IllegalArgumentException("fakeItemId must not be blank");
            }
            if (components == null) {
                throw new IllegalArgumentException("components must not be null");
            }
        }

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty()
                    && FakeRecipeUtil.isFakeItem(stack, fakeItemId)
                    && matchesPatchedComponents(stack, components);
        }

        @Override
        public ItemStack createStack() {
            ItemStack stack = FakeRecipeUtil.createFakeItemStack(fakeItemId, 1);
            applyPatch(stack, components);
            stack.setCount(1);
            return stack;
        }

        @Override
        public int specificity() {
            return 3 + components.size();
        }
    }

    public record TagSpec(String raw, TagKey<Item> tag) implements StackSpec {
        public TagSpec {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("raw must not be blank");
            }
            if (tag == null) {
                throw new IllegalArgumentException("tag must not be null");
            }
        }

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.is(tag);
        }

        @Override
        public ItemStack createStack() {
            throw new IllegalStateException("Cannot create an ItemStack from tag '" + tag.location() + "'");
        }

        @Override
        public int specificity() {
            return 1;
        }

        @Override
        public boolean canCreateStack() {
            return false;
        }
    }

    public record ParsedItem(String raw, Holder<Item> item, DataComponentPatch components) {
        public ParsedItem {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("raw item string must not be blank");
            }
            if (item == null) {
                throw new IllegalArgumentException("item must not be null");
            }
            if (components == null) {
                throw new IllegalArgumentException("components must not be null");
            }
        }

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (stack.getItem() != item.value()) {
                return false;
            }
            return matchesPatchedComponents(stack, components);
        }

        public ItemStack createStack(int count) {
            try {
                return new ItemInput(item, components).createItemStack(count);
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException("Invalid item result '" + raw + "'", e);
            }
        }

        public int componentCount() {
            return components.size();
        }
    }
}
