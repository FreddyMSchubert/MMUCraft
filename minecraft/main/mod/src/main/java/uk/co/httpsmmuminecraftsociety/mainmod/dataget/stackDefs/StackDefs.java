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

        ParsedStackDescription parsed = splitDisplayNameOverride(trimmed);
        String stackDescription = parsed.stackDescription();

        if (stackDescription.startsWith("#")) {
            return TagStackDef.parse(trimmed, stackDescription, parsed.displayNameOverride());
        }

        String baseId = extractBaseId(stackDescription);
        String suffix = stackDescription.substring(baseId.length());

        Identifier identifier = Identifier.tryParse(baseId);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid stack description id: '" + raw + "'");
        }

        return switch (identifier.getNamespace()) {
            case VANILLA_NAMESPACE -> VanillaStackDef.parse(trimmed, baseId, suffix, parsed.displayNameOverride());
            case FAKE_NAMESPACE -> FakeStackDef.parse(trimmed, identifier.getPath(), suffix, parsed.displayNameOverride());
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

    private record ParsedStackDescription(String stackDescription, String displayNameOverride) {}

    private static ParsedStackDescription splitDisplayNameOverride(String raw) {
        int bracketDepth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth--;
                if (bracketDepth < 0) {
                    throw new IllegalArgumentException("Invalid stack description, unexpected ']': '" + raw + "'");
                }
            } else if (c == '=' && bracketDepth == 0) {
                String stackDescription = raw.substring(0, i).trim();
                String displayNameOverride = raw.substring(i + 1).trim();
                if (stackDescription.isEmpty()) {
                    throw new IllegalArgumentException("Invalid stack description, missing item before display name override: '" + raw + "'");
                }
                if (displayNameOverride.isEmpty()) {
                    throw new IllegalArgumentException("Invalid stack description, display name override must not be blank: '" + raw + "'");
                }
                return new ParsedStackDescription(stackDescription, displayNameOverride);
            }
        }

        if (bracketDepth != 0) {
            throw new IllegalArgumentException("Invalid stack description, missing closing ']': '" + raw + "'");
        }
        return new ParsedStackDescription(raw, "");
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
