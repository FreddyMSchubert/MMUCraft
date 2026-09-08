package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class DailyTaskCatalogCheck {
    private DailyTaskCatalogCheck() {
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path project = Path.of(args[0]);
        Set<String> fakeItemIds = new HashSet<>();
        try (var paths = Files.walk(project.resolve("../data/data/items"))) {
            for (Path path : paths.filter(file -> file.getFileName().toString().equals("item.json")).toList()) {
                String id = JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("id").getAsString();
                if (!fakeItemIds.add(id)) throw new IllegalStateException("Duplicate fake item id: " + id);
            }
        }

        var catalog = DailyTaskCatalog.load(
                project.resolve("src/main/resources").resolve(DailyTaskCatalog.RESOURCE_PATH),
                VanillaRegistries.createLookup(),
                fakeItemIds
        );
        DailyTaskRegistry.validate(catalog);
        int taskCount = DailyTaskRegistry.optionCount(catalog);
        System.out.println("Daily task catalogue check passed: " + taskCount + " tasks in " + catalog.size() + " families.");
    }
}
