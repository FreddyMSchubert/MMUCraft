package uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.Identifier;

public final class StackDefs
{
    public static final Codec<StackDef> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<StackDef, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getStringValue(input)
                    .flatMap(raw -> {
                        try {
                            return DataResult.success(Pair.of(StackDefs.parse(raw), ops.empty()));
                        } catch (IllegalArgumentException e) {
                            return DataResult.error(e::getMessage);
                        }
                    });
        }

        @Override
        public <T> DataResult<T> encode(StackDef input, DynamicOps<T> ops, T prefix) {
            return ops.mergeToPrimitive(prefix, ops.createString(input.raw()));
        }
    };

    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final String FAKE_NAMESPACE = "mainmod";

    public static StackDef parse(String raw)
    {
        if (raw == null) {
            throw new IllegalArgumentException("stack description must not be null");
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("stack description must not be blank");
        }

        if (trimmed.startsWith("#")) {
            return TagStackDef.parse(trimmed);
        }

        String baseId = extractBaseId(trimmed);
        String suffix = trimmed.substring(baseId.length());

        Identifier identifier = Identifier.tryParse(baseId);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid stack description id: '" + raw + "'");
        }

        return switch (identifier.getNamespace()) {
            case VANILLA_NAMESPACE -> VanillaStackDef.parse(trimmed, baseId, suffix);
            case FAKE_NAMESPACE -> FakeStackDef.parse(trimmed, identifier.getPath(), suffix);
            default -> throw new IllegalArgumentException(
                    "Unsupported stack description namespace '" + identifier.getNamespace() + "' in '" + raw + "'. " +
                            "Use minecraft: for registered items, mainmod: for fake items, or #namespace:path for tags."
            );
        };
    }

    public static boolean matches(String description, net.minecraft.world.item.ItemStack stack) {
        return parse(description).matches(stack);
    }

    public static net.minecraft.world.item.ItemStack create(String description) {
        return parse(description).createStack();
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
}
