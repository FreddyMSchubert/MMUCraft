package uk.co.httpsmmuminecraftsociety.mainmod.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;

public final class SparseStructures {
    static final int NON_VANILLA_SPREAD_FACTOR = 3;

    private SparseStructures() {
    }

    public static void apply(ResourceKey<?> elementKey, JsonElement json) {
        if (!elementKey.registryKey().identifier().getPath().equals("worldgen/structure_set")
                || elementKey.identifier().getNamespace().equals("minecraft")
                || !json.isJsonObject()) {
            return;
        }

        JsonObject placement = json.getAsJsonObject().getAsJsonObject("placement");
        if (placement == null
                || !placement.has("type")
                || !placement.get("type").getAsString().equals("minecraft:random_spread")
                || !placement.has("spacing")
                || !placement.has("separation")) {
            return;
        }

        placement.addProperty(
                "spacing",
                Math.multiplyExact(placement.get("spacing").getAsInt(), NON_VANILLA_SPREAD_FACTOR)
        );
        placement.addProperty(
                "separation",
                Math.multiplyExact(placement.get("separation").getAsInt(), NON_VANILLA_SPREAD_FACTOR)
        );
    }
}
