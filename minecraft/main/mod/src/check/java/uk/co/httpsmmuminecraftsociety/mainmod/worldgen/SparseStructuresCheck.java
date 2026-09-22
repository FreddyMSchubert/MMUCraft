package uk.co.httpsmmuminecraftsociety.mainmod.worldgen;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public final class SparseStructuresCheck {
    private SparseStructuresCheck() {
    }

    public static void main(String[] args) {
        JsonObject modded = randomSpreadPlacement(32, 8);
        SparseStructures.apply(structureSet("example", "ruins"), modded);
        assert modded.getAsJsonObject("placement").get("spacing").getAsInt() == 96;
        assert modded.getAsJsonObject("placement").get("separation").getAsInt() == 24;

        checkFactor("explorify", 3);
        checkFactor("qrafty", 4);
        checkFactor("structory", 2);
        checkFactor("structory_towers", 5);

        JsonObject vanilla = randomSpreadPlacement(32, 8);
        SparseStructures.apply(structureSet("minecraft", "villages"), vanilla);
        assert vanilla.getAsJsonObject("placement").get("spacing").getAsInt() == 32;
        assert vanilla.getAsJsonObject("placement").get("separation").getAsInt() == 8;

        JsonObject concentric = new JsonObject();
        JsonObject concentricPlacement = new JsonObject();
        concentricPlacement.addProperty("type", "minecraft:concentric_rings");
        concentric.add("placement", concentricPlacement);
        SparseStructures.apply(structureSet("example", "stronghold_like"), concentric);
        assert !concentricPlacement.has("spacing");
    }

    private static JsonObject randomSpreadPlacement(int spacing, int separation) {
        JsonObject root = new JsonObject();
        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("spacing", spacing);
        placement.addProperty("separation", separation);
        root.add("placement", placement);
        return root;
    }

    private static void checkFactor(String namespace, int factor) {
        JsonObject structure = randomSpreadPlacement(32, 8);
        SparseStructures.apply(structureSet(namespace, "example"), structure);
        assert structure.getAsJsonObject("placement").get("spacing").getAsInt() == 32 * factor;
        assert structure.getAsJsonObject("placement").get("separation").getAsInt() == 8 * factor;
    }

    private static ResourceKey<StructureSet> structureSet(String namespace, String path) {
        return ResourceKey.create(
                Registries.STRUCTURE_SET,
                Identifier.fromNamespaceAndPath(namespace, path)
        );
    }
}
