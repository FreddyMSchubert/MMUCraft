package uk.co.httpsmmuminecraftsociety.mainmod.dataRead.stackDefs;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
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
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.Map;
import java.util.Optional;

public final class StackDefs
{
    public static final Codec<StackDef> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<StackDef, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getStringValue(input)
                    .flatMap(raw -> parseFromOps(ops, raw))
                    .map(value -> Pair.of(value, ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(StackDef input, DynamicOps<T> ops, T prefix) {
            return ops.mergeToPrimitive(prefix, ops.createString(input.raw()));
        }
    };

    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final String FAKE_NAMESPACE = "mainmod";
    private static final String DUMMY_COMPONENT_PARSE_ITEM = "minecraft:stone";

    private StackDefs() {}

    public static boolean matches(String description, ItemStack stack, HolderLookup.Provider registries) {
        return parse(registries, description).matches(stack);
    }

    public static ItemStack create(String description, HolderLookup.Provider registries) {
        return parse(registries, description).createStack();
    }

    public static StackDef parse(HolderLookup.Provider registries, String raw) {
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

        return switch (identifier.getNamespace()) {
            case VANILLA_NAMESPACE -> new VanillaStackDef(trimmed, ParsedVanillaItem.parse(registries, trimmed));
            case FAKE_NAMESPACE -> parseFakeItem(registries, trimmed, identifier.getPath(), suffix);
            default -> throw new IllegalArgumentException(
                    "Unsupported stack description namespace '" + identifier.getNamespace() + "' in '" + raw + "'. " +
                            "Use minecraft: for registered items, mainmod: for fake items, or #namespace:path for tags."
            );
        };
    }

    private static TagStackDef parseTag(String raw) {
        if (raw.indexOf('[') >= 0) {
            throw new IllegalArgumentException("tag stack descriptions do not support component suffixes: '" + raw + "'");
        }

        Identifier tagId = Identifier.tryParse(raw.substring(1));
        if (tagId == null) {
            throw new IllegalArgumentException("Invalid tag id in stack description: '" + raw + "'");
        }

        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
        return new TagStackDef(raw, tag);
    }

    private static FakeStackDef parseFakeItem(HolderLookup.Provider registries,
                                              String raw,
                                              String fakeItemId,
                                              String suffix) {
        if (!FakeItems.isKnownFakeItem(fakeItemId)) {
            throw new IllegalArgumentException("Unknown fakeitem id: " + fakeItemId);
        }

        DataComponentPatch patch = ParsedVanillaItem.parse(registries, DUMMY_COMPONENT_PARSE_ITEM + suffix).components();
        rejectCustomModelDataPatch(patch, raw);

        return new FakeStackDef(raw, fakeItemId, patch);
    }

    private static void rejectCustomModelDataPatch(DataComponentPatch patch, String raw) {
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

    private static <T> DataResult<StackDef> parseFromOps(DynamicOps<T> ops, String raw) {
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
}
