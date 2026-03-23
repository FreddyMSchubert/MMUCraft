package uk.co.httpsmmuminecraftsociety.mainmod.recipe.dataDriven;

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
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ComponentAwareItem(String raw, Holder<Item> item, DataComponentPatch components) {
    public static final Codec<ComponentAwareItem> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<ComponentAwareItem, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getStringValue(input)
                    .flatMap(raw -> ComponentAwareItem.parse(ops, raw))
                    .map(value -> Pair.of(value, ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(ComponentAwareItem input, DynamicOps<T> ops, T prefix) {
            return encodeString(input, ops)
                    .flatMap(encoded -> ops.mergeToPrimitive(prefix, ops.createString(encoded)));
        }
    };

    public ComponentAwareItem(Holder<Item> item, DataComponentPatch components) {
        this(null, item, components);
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
            return new ItemInput(item, components).createItemStack(count, false);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("Invalid item result '" + asStringForErrors() + "'", e);
        }
    }

    public int specificityBonus() {
        return components.size();
    }

    private String asStringForErrors() {
        return raw != null ? raw : item.unwrapKey().map(Object::toString).orElse("<unknown>");
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

    private static <T> DataResult<ComponentAwareItem> parse(DynamicOps<T> ops, String raw) {
        return extractRegistries(ops).flatMap(registries -> {
            try {
                ItemParser.ItemResult parsed = new ItemParser(registries).parse(new StringReader(raw));
                return DataResult.success(new ComponentAwareItem(raw, parsed.item(), parsed.components()));
            } catch (CommandSyntaxException e) {
                return DataResult.error(() -> "Invalid item string '" + raw + "': " + e.getMessage());
            }
        });
    }

    private static <T> DataResult<String> encodeString(ComponentAwareItem input, DynamicOps<T> ops) {
        if (input.raw != null) {
            return DataResult.success(input.raw);
        }

        return extractRegistries(ops)
                .map(registries -> new ItemInput(input.item, input.components).serialize(registries));
    }

    private static <T> DataResult<HolderLookup.Provider> extractRegistries(DynamicOps<T> ops) {
        if (ops instanceof RegistryOps<?> registryOps
                && registryOps.lookupProvider instanceof RegistryOps.HolderLookupAdapter adapter) {
            return DataResult.success(adapter.lookupProvider);
        }

        return DataResult.error(() -> "Missing registry context for component-aware item decoding");
    }
}
