package uk.co.httpsmmuminecraftsociety.mainmod.dailies;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DailyAdvancementPolicy {
    private static final Identifier RESOURCE =
            Identifier.fromNamespaceAndPath("mainmod", "dailies/advancement_policy.json");

    private static Policy policy = new Policy(Set.of(), Set.of(), Set.of());

    private DailyAdvancementPolicy() {}

    public static void load(ResourceManager resourceManager) {
        Resource resource = resourceManager.getResource(RESOURCE)
                .orElseThrow(() -> new IllegalStateException("Missing daily advancement policy: " + RESOURCE));
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            policy = new Policy(
                    readIds(json, "excluded"),
                    readIds(json, "requires_nether"),
                    readIds(json, "requires_end")
            );
        } catch (IOException | IllegalStateException error) {
            throw new IllegalStateException("Could not load daily advancement policy: " + RESOURCE, error);
        }
    }

    public static boolean allows(Identifier advancementId, Identifier rootId) {
        if (matches(policy.excluded(), advancementId, rootId)) return false;
        if (!DailyTaskRegistry.NETHER_ENABLED && matches(policy.requiresNether(), advancementId, rootId)) return false;
        return DailyTaskRegistry.END_ENABLED || !matches(policy.requiresEnd(), advancementId, rootId);
    }

    private static Set<Identifier> readIds(JsonObject json, String group) {
        JsonObject entries = json.getAsJsonObject(group);
        if (entries == null) throw new IllegalStateException("Missing policy group: " + group);

        Set<Identifier> ids = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                throw new IllegalStateException("Policy reason must be text: " + entry.getKey());
            }
            ids.add(Identifier.parse(entry.getKey()));
        }
        return Set.copyOf(ids);
    }

    private static boolean matches(Set<Identifier> ids, Identifier advancementId, Identifier rootId) {
        return ids.contains(advancementId) || ids.contains(rootId);
    }

    private record Policy(
            Set<Identifier> excluded,
            Set<Identifier> requiresNether,
            Set<Identifier> requiresEnd
    ) {}
}
