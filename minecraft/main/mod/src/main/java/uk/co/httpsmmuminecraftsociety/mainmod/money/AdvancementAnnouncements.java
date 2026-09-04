package uk.co.httpsmmuminecraftsociety.mainmod.money;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class AdvancementAnnouncements {
    private static final Identifier RESOURCE =
            Identifier.fromNamespaceAndPath("mainmod", "money/silenced_advancement_announcements.json");

    private static Set<Identifier> silencedAdvancements = Set.of();

    private AdvancementAnnouncements() {}

    public static void load(ResourceManager resourceManager) {
        Resource resource = resourceManager.getResource(RESOURCE)
                .orElseThrow(() -> new IllegalStateException("Missing advancement announcement policy: " + RESOURCE));
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) {
                throw new IllegalStateException("Advancement announcement policy must be an array: " + RESOURCE);
            }

            silencedAdvancements = readIds(root.getAsJsonArray());
        } catch (IOException | IllegalStateException error) {
            throw new IllegalStateException("Could not load advancement announcement policy: " + RESOURCE, error);
        }
    }

    public static boolean isSilenced(Identifier advancementId) {
        return silencedAdvancements.contains(advancementId);
    }

    private static Set<Identifier> readIds(JsonArray entries) {
        Set<Identifier> ids = new HashSet<>();
        for (JsonElement entry : entries) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new IllegalStateException("Silenced advancement ID must be text: " + entry);
            }

            Identifier id = Identifier.parse(entry.getAsString());
            if (!ids.add(id)) {
                throw new IllegalStateException("Duplicate silenced advancement ID: " + id);
            }
        }
        return Set.copyOf(ids);
    }
}
