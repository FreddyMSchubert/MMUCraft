package uk.co.httpsmmuminecraftsociety.mainmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DataLoader
{
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
                        "No item JSON files were found under " + RESOURCE_ROOT +
                        ". Run the item-data staging step before building the mod."
                );
            }

            result.sort(Comparator.comparing(FakeItem::id));
            return List.copyOf(result);
        } catch (Exception exc) {
            throw new IllegalStateException("Failed to load fake item JSON data.", exc);
        }
    }

    private static JsonObject parseJsonObject(ResourceEntry resource) throws IOException {
        try (InputStream inputStream = resource.openStream();
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            JsonElement parsed = JsonParser.parseReader(bufferedReader);
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException("Item JSON root must be an object: " + resource.logicalPath());
            }
            return parsed.getAsJsonObject();
        }
    }

    private static List<ResourceEntry> discoverJsonResources() throws IOException {
        List<ResourceEntry> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String classPath = System.getProperty("java.class.path", "");
        if (classPath.isBlank()) {
            throw new IllegalStateException("java.class.path is empty.");
        }

        String[] entries = classPath.split(System.getProperty("path.separator"));
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            Path path = Path.of(entry);
            if (Files.isDirectory(path)) {
                discoverFromDirectory(path, results, seen);
            } else if (entry.endsWith(".jar")) {
                discoverFromJar(path, results, seen);
            }
        }

        results.sort(Comparator.comparing(ResourceEntry::logicalPath));
        return results;
    }

    private static void discoverFromDirectory(Path classPathRoot, List<ResourceEntry> results, Set<String> seen) throws IOException {
        Path resourceRootPath = classPathRoot.resolve(RESOURCE_ROOT);
        if (!Files.isDirectory(resourceRootPath)) {
            return;
        }

        List<Path> stack = new ArrayList<>();
        stack.add(resourceRootPath);

        while (!stack.isEmpty()) {
            Path current = stack.remove(stack.size() - 1);

            try (DirectoryStream<Path> children = Files.newDirectoryStream(current)) {
                for (Path child : children) {
                    if (Files.isDirectory(child)) {
                        stack.add(child);
                    } else if (child.getFileName().toString().equals("item.json")) {
                        String logicalPath = RESOURCE_ROOT + "/" + resourceRootPath.relativize(child).toString().replace("\\", "/");
                        if (seen.add(logicalPath)) {
                            results.add(new ResourceEntry(logicalPath, () -> Files.newInputStream(child)));
                        }
                    }
                }
            }
        }
    }

    private static void discoverFromJar(Path jarPath, List<ResourceEntry> results, Set<String> seen) throws IOException {
        if (!Files.exists(jarPath)) {
            return;
        }

        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (entry.isDirectory()) {
                    continue;
                }
                if (!name.startsWith(RESOURCE_ROOT + "/")) {
                    continue;
                }
                if (!name.endsWith("/item.json")) {
                    continue;
                }

                if (seen.add(name)) {
                    results.add(new ResourceEntry(name, () -> {
                        ZipFile reopened = new ZipFile(jarPath.toFile());
                        ZipEntry reopenedEntry = reopened.getEntry(name);
                        if (reopenedEntry == null) {
                            reopened.close();
                            throw new IOException("Missing jar entry after reopen: " + name);
                        }

                        InputStream rawStream = reopened.getInputStream(reopenedEntry);
                        return new InputStream() {
                            @Override
                            public int read() throws IOException {
                                return rawStream.read();
                            }

                            @Override
                            public int read(byte[] b, int off, int len) throws IOException {
                                return rawStream.read(b, off, len);
                            }

                            @Override
                            public void close() throws IOException {
                                try {
                                    rawStream.close();
                                } finally {
                                    reopened.close();
                                }
                            }
                        };
                    }));
                }
            }
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
