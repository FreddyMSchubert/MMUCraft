package uk.co.httpsmmuminecraftsociety.mainmod.miniblocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MiniBlockCatalog {
    private static final String RESOURCE_PATH = "/data/mainmod/miniblocks.json";
    private static final List<MiniBlockDefinition> DEFINITIONS = load();
    private static final Map<String, MiniBlockDefinition> DEFINITIONS_BY_ID = indexById(DEFINITIONS);

    private MiniBlockCatalog() {}

    public static List<MiniBlockDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Optional<MiniBlockDefinition> find(String id) {
        return Optional.ofNullable(DEFINITIONS_BY_ID.get(id));
    }

    private static List<MiniBlockDefinition> load() {
        try (InputStream stream = MiniBlockCatalog.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Missing mini block catalog: " + RESOURCE_PATH);
            }

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader).getAsJsonArray());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read mini block catalog", exception);
        }
    }

    private static List<MiniBlockDefinition> parse(JsonArray catalog) {
        List<MiniBlockDefinition> definitions = new ArrayList<>(catalog.size());
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();

        for (JsonElement element : catalog) {
            JsonObject definition = element.getAsJsonObject();
            String id = requiredString(definition, "id");
            Identifier inputId = Identifier.parse(requiredString(definition, "input"));
            String name = requiredString(definition, "name");
            String texture = requiredString(definition, "texture");

            if (!id.matches("[a-z0-9_]+")) {
                throw new IllegalStateException("Invalid mini block id: " + id);
            }
            if (!ids.add(id)) {
                throw new IllegalStateException("Duplicate mini block id: " + id);
            }
            if (!names.add(name)) {
                throw new IllegalStateException("Duplicate mini block name: " + name);
            }
            if (!BuiltInRegistries.ITEM.containsKey(inputId)) {
                throw new IllegalStateException("Unknown mini block input item: " + inputId);
            }
            validateTexture(id, texture);

            definitions.add(new MiniBlockDefinition(id, inputId, name, texture));
        }

        if (definitions.isEmpty()) {
            throw new IllegalStateException("Mini block catalog must not be empty");
        }

        return List.copyOf(definitions);
    }

    private static Map<String, MiniBlockDefinition> indexById(List<MiniBlockDefinition> definitions) {
        Map<String, MiniBlockDefinition> byId = new LinkedHashMap<>();
        for (MiniBlockDefinition definition : definitions) {
            byId.put(definition.id(), definition);
        }
        return Map.copyOf(byId);
    }

    private static String requiredString(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
            throw new IllegalStateException("Mini block definition is missing string field '" + field + "'");
        }

        String value = object.get(field).getAsString();
        if (value.isBlank()) {
            throw new IllegalStateException("Mini block field '" + field + "' must not be blank");
        }
        return value;
    }

    private static void validateTexture(String id, String texture) {
        try {
            String decoded = new String(Base64.getDecoder().decode(texture), StandardCharsets.UTF_8);
            String url = JsonParser.parseString(decoded)
                    .getAsJsonObject()
                    .getAsJsonObject("textures")
                    .getAsJsonObject("SKIN")
                    .get("url")
                    .getAsString();
            if (!url.startsWith("http://textures.minecraft.net/texture/")
                    && !url.startsWith("https://textures.minecraft.net/texture/")) {
                throw new IllegalStateException("Unsupported texture URL");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid texture data for mini block " + id, exception);
        }
    }
}
