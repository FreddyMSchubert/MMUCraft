package uk.co.httpsmmuminecraftsociety.mainmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class DataLoader implements SimpleSynchronousResourceReloadListener {
    private static final String RESOURCE_ROOT_MAINMOD = "data/mainmod/items";
    private static final String RESOURCE_ROOT_TESTDP = "data/testdp/items";
    private static final List<String> RESOURCE_ROOTS = List.of(
            RESOURCE_ROOT_MAINMOD,
            RESOURCE_ROOT_TESTDP
    );
    private static final Set<String> SUPPORTED_NAMESPACES = Set.of("mainmod", "testdp");

    private static final DataLoader INSTANCE = new DataLoader();

    private static volatile List<FakeItem> fakeItems = List.of();
    private static volatile boolean reloadSeen = false;

    private static volatile List<String> lastBootstrapResources = List.of();
    private static volatile List<String> lastReloadResources = List.of();
    private static volatile List<String> lastParsedItemIds = List.of();

    private DataLoader() {}

    public static void init() {
        bootstrapFromModResources();
        FakeItems.reloadFromJson();
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(INSTANCE);
    }

    public static List<FakeItem> loadFakeItems() {
        return fakeItems;
    }

    public static boolean hasReloadSeen() {
        return reloadSeen;
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fake_items");
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        fakeItems = loadFromResourceManager(manager);
        reloadSeen = true;
        FakeItems.reloadFromJson();
    }

    private static void bootstrapFromModResources() {
        try {
            ModContainer modContainer = FabricLoader.getInstance()
                    .getModContainer(MainMod.MOD_ID)
                    .orElseThrow(() -> new IllegalStateException("Could not find mod container for " + MainMod.MOD_ID));

            List<ResourceEntry> entries = new ArrayList<>();

            for (String root : RESOURCE_ROOTS) {
                var rootOpt = modContainer.findPath(root);
                if (rootOpt.isEmpty()) {
                    continue;
                }

                Path resourceRoot = rootOpt.get();
                try (Stream<Path> paths = Files.walk(resourceRoot)) {
                    paths.filter(Files::isRegularFile)
                            .filter(path -> isFakeItemDefinitionPath(path.toString().replace("\\", "/")))
                            .forEach(path -> {
                                String logicalPath = root + "/" + resourceRoot.relativize(path).toString().replace("\\", "/");
                                entries.add(new ResourceEntry(logicalPath, () -> Files.newInputStream(path)));
                            });
                }
            }

            entries.sort(Comparator.comparing(ResourceEntry::logicalPath));
            lastBootstrapResources = entries.stream().map(ResourceEntry::logicalPath).toList();

            List<FakeItem> loaded = parseEntries(entries);
            fakeItems = loaded;
        } catch (Exception e) {
            throw new IllegalStateException("Failed bootstrap fake item load from mod resources", e);
        }
    }

    private static List<FakeItem> loadFromResourceManager(ResourceManager manager) {
        try {
            Map<Identifier, Resource> resources = manager.listResources(
                    "items",
                    id -> SUPPORTED_NAMESPACES.contains(id.getNamespace())
                            && isFakeItemDefinitionPath(id.getPath())
            );

            List<Identifier> ids = new ArrayList<>(resources.keySet());
            ids.sort(Comparator.comparing(Identifier::toString));

            lastReloadResources = ids.stream().map(Identifier::toString).toList();

            List<ResourceEntry> entries = ids.stream()
                    .map(id -> new ResourceEntry(id.toString(), () -> resources.get(id).open()))
                    .toList();

            List<FakeItem> loaded = parseEntries(entries);

            return loaded;
        } catch (Exception e) {
            throw new IllegalStateException("Failed datapack fake item load", e);
        }
    }

    private static boolean isFakeItemDefinitionPath(String path) {
        String normalized = path.replace("\\", "/");
        return normalized.endsWith("/item.json");
    }

    private static List<FakeItem> parseEntries(List<ResourceEntry> entries) throws IOException {
        List<FakeItem> result = new ArrayList<>();
        List<String> parsedIds = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (ResourceEntry entry : entries) {
            JsonObject root = parseJsonObject(entry);

            validateFakeItemJson(root, entry.logicalPath());

            FakeItem item = FakeItem.fromJson(root, entry.logicalPath());

            if (!seenIds.add(item.id())) {
                throw new IllegalStateException("Duplicate fake item id: " + item.id());
            }

            result.add(item);
            parsedIds.add(item.id());
        }

        lastParsedItemIds = List.copyOf(parsedIds);
        result.sort(Comparator.comparing(FakeItem::id));
        return List.copyOf(result);
    }

    private static void validateFakeItemJson(JsonObject json, String filePath) {
        requireString(json, "title", filePath);
        requireString(json, "id", filePath);
        requireString(json, "rarity", filePath);

        if (!json.has("maxStackSize")) {
            throw new IllegalStateException(filePath + ": missing required field 'maxStackSize'");
        }
        if (!json.has("tooltips") || !json.get("tooltips").isJsonArray()) {
            throw new IllegalStateException(filePath + ": missing or invalid required field 'tooltips'");
        }
    }

    private static void requireString(JsonObject json, String field, String filePath) {
        if (!json.has(field) || json.get(field) == null || json.get(field).isJsonNull()) {
            throw new IllegalStateException(filePath + ": missing required field '" + field + "'");
        }
    }

    private static JsonObject parseJsonObject(ResourceEntry entry) throws IOException {
        try (
                InputStream inputStream = entry.openStream();
                Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(reader)
        ) {
            JsonElement parsed = JsonParser.parseReader(bufferedReader);
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException("Item JSON root must be an object: " + entry.logicalPath());
            }
            return parsed.getAsJsonObject();
        }
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
