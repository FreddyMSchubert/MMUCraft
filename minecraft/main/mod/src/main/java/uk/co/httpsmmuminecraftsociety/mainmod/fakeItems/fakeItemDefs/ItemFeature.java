package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public sealed interface ItemFeature
        permits CharmItemFeature,
        ConsumableItemFeature,
        DyeableItemFeature,
        DecoBlockItemFeature,
        EquippableCharmItemFeature,
        EquippableCosmeticItemFeature,
        DiscItemFeature,
        FishItemFeature
{
    void apply(ItemStack stack);

    void validate();

    static List<ItemFeature> of(JsonObject rootJson) {
        List<ItemFeature> list = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : rootJson.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonObject()) {
                continue;
            }

            JsonObject componentJson = value.getAsJsonObject();

            ItemFeature feature = switch (entry.getKey()) {
                case "charm" -> CharmItemFeature.of(rootJson, componentJson);
                case "consumable" -> ConsumableItemFeature.of(componentJson);
                case "dyeable" -> DyeableItemFeature.of(componentJson);
                case "decoBlock" -> DecoBlockItemFeature.of(componentJson);
                case "equippableCharm" -> EquippableCharmItemFeature.of(componentJson);
                case "equippableCosmetic" -> EquippableCosmeticItemFeature.of(componentJson);
                case "disc" -> DiscItemFeature.of(componentJson);
                case "fish" -> FishItemFeature.of(rootJson, componentJson);
                default -> null;
            };

            if (feature != null) {
                list.add(feature);
            }
        }

        return List.copyOf(list);
    }
}
