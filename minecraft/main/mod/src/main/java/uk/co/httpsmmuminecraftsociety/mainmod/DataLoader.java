package uk.co.httpsmmuminecraftsociety.mainmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class DataLoader {
    private static final String RESOURCE_ROOT = "data/mainmod/items";

    private DataLoader() {}

    public static List<FakeItem> loadFakeItems() {
        try {
            List<ResourceEntry> resources = discoverJsonResources();
            List<FakeItem> result = new ArrayList<>();

            for (ResourceEntry resource : resources) {
                JsonObject root = parseJsonObject(resource);
                result.add(FakeItem.fromJson(root, resource.logicalPath()));
            }

            if (result.isEmpty()) {
                throw new IllegalStateException(
                        "No item JSON files were found under " + RESOURCE_ROOT + "."
                );
            }

            result.sort(Comparator.comparing(FakeItem::id));
            return List.copyOf(result);
        } catch (Exception exc) {
            throw new IllegalStateException("Failed to load fake item JSON data.", exc);
        }
    }

    private static JsonObject parseJsonObject(ResourceEntry resource) throws IOException {
        try (
                InputStream inputStream = resource.openStream();
                InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(reader)
        ) {
            JsonElement parsed = JsonParser.parseReader(bufferedReader);
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException("Item JSON root must be an object: " + resource.logicalPath());
            }
            return parsed.getAsJsonObject();
        }
    }

    private static List<ResourceEntry> discoverJsonResources() throws IOException {
        ModContainer modContainer = FabricLoader.getInstance()
                .getModContainer(MainMod.MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Could not find mod container for " + MainMod.MOD_ID));

        Path resourceRoot = modContainer.findPath(RESOURCE_ROOT)
                .orElseThrow(() -> new IllegalStateException("Could not find resource root: " + RESOURCE_ROOT));

        List<ResourceEntry> results = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(resourceRoot)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("item.json"))
                    .sorted()
                    .forEach(path -> {
                        String logicalPath =
                                RESOURCE_ROOT + "/" + resourceRoot.relativize(path).toString().replace("\\", "/");
                        results.add(new ResourceEntry(logicalPath, () -> Files.newInputStream(path)));
                    });
        }

        return results;
    }

    @FunctionalInterface
    private interface InputStreamSupplier {
        InputStream open() throws IOException;
    }

    private record ResourceEntry(String logicalPath, InputStreamSupplier supplier) {
        InputStream openStream() throws IOException {
            return supplier.open();
        }
    }
}
