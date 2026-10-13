package dev.freddy.chunklocked;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.resources.Identifier;

/** Reads Delta Lore and Quillbee's Chunklock 2.1.0 loot tables (Apache-2.0). */
final class LootRoller {
    record Offer(Identifier item, int count) { }

    private final Map<String, JsonObject> tables = new HashMap<>();

    Offer roll(String biome, Random random) {
        // These biomes arrived after the source pack's last release.
        if (biome.equals("pale_garden")) biome = "dark_forest";
        if (biome.equals("dappled_forest")) biome = "forest";
        String table = "biome/" + biome;
        if (get(table) == null) table = "biome/all_biome";
        return rollTable(table, random, 0);
    }

    private Offer rollTable(String path, Random random, int depth) {
        if (depth > 16) throw new IllegalStateException("Chunklock loot reference cycle: " + path);
        JsonObject table = get(path);
        if (table == null) throw new IllegalStateException("Missing Chunklock loot table " + path);
        JsonArray entries = table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
        int weight = 0;
        for (JsonElement element : entries) weight += element.getAsJsonObject().get("weight").getAsInt();
        int choice = random.nextInt(weight);
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            choice -= entry.get("weight").getAsInt();
            if (choice >= 0) continue;
            String type = entry.get("type").getAsString();
            Offer offer;
            if (type.endsWith("loot_table")) {
                String value = entry.get("value").getAsString();
                // The original pack intentionally references vanilla gravel's 10% flint drop.
                offer = value.equals("minecraft:blocks/gravel")
                        ? new Offer(Identifier.withDefaultNamespace(random.nextInt(10) == 0 ? "flint" : "gravel"), 1)
                        : rollTable(value.substring(value.indexOf(':') + 1), random, depth + 1);
            } else if (type.endsWith("item")) {
                offer = new Offer(Identifier.parse(entry.get("name").getAsString()), 1);
            } else {
                throw new IllegalStateException("Unknown Chunklock loot entry " + type);
            }
            if (entry.has("functions")) {
                for (JsonElement function : entry.getAsJsonArray("functions")) {
                    JsonObject modifier = function.getAsJsonObject();
                    if (!modifier.get("function").getAsString().endsWith("set_count")) continue;
                    JsonElement count = modifier.get("count");
                    int amount = count.isJsonObject()
                            ? count.getAsJsonObject().get("min").getAsInt()
                                + random.nextInt(count.getAsJsonObject().get("max").getAsInt()
                                - count.getAsJsonObject().get("min").getAsInt() + 1)
                            : count.getAsInt();
                    offer = new Offer(offer.item(), amount);
                }
            }
            return offer;
        }
        throw new IllegalStateException("Empty Chunklock loot table " + path);
    }

    private JsonObject get(String path) {
        if (tables.containsKey(path)) return tables.get(path);
        var stream = getClass().getResourceAsStream("/chunklocked/loot/" + path + ".json");
        if (stream == null) return null;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject table = JsonParser.parseReader(reader).getAsJsonObject();
            tables.put(path, table);
            return table;
        } catch (Exception error) {
            throw new IllegalStateException("Invalid Chunklock loot table " + path, error);
        }
    }
}
