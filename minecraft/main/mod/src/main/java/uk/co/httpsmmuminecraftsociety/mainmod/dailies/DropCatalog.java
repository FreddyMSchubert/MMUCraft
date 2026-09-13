package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

final class DropCatalog {
    static final String RESOURCE_PATH = "data/mainmod/gameplay-toggles.json";

    private DropCatalog() {
    }

    static Set<String> load(Path path) {
        try {
            JsonElement json = JsonParser.parseString(Files.readString(path));
            if (!json.isJsonArray()) throw invalid(path);
            Set<String> drops = new HashSet<>();
            for (JsonElement element : json.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw invalid(path);
                String drop = element.getAsString();
                if (!drop.matches("[a-z0-9._-]+(?:/[a-z0-9._-]+)*") || !drops.add(drop)) throw invalid(path);
            }
            if (drops.isEmpty()) throw invalid(path);
            return Set.copyOf(drops);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read drop catalogue " + path, exception);
        }
    }

    private static IllegalStateException invalid(Path path) {
        return new IllegalStateException("Invalid drop catalogue: " + path);
    }
}
