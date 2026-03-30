package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public sealed interface ItemFeature
        permits CharmItemFeature,
        ConsumableItemFeature,
        DyeableItemFeature,
        EquippableCharmItemFeature,
        EquippableCosmeticItemFeature,
        DiscItemFeature
{
    void apply(ItemStack stack);

    record ComponentParser(
            String key,
            Function<JsonObject, ? extends ItemFeature> parser
    ) {}

    List<ComponentParser> COMPONENT_PARSERS = List.of(
            new ComponentParser("charm", CharmItemFeature::of),
            new ComponentParser("consumable", ConsumableItemFeature::of),
            new ComponentParser("dyeable", DyeableItemFeature::of),
            new ComponentParser("equippableCharm", EquippableCharmItemFeature::of),
            new ComponentParser("equippableCosmetic", EquippableCosmeticItemFeature::of),
            new ComponentParser("disc", DiscItemFeature::of)
    );

    static List<ItemFeature> of(JsonObject json) {
        List<ItemFeature> list = new ArrayList<>();

        for (ComponentParser componentParser : COMPONENT_PARSERS) {
            JsonElement component = json.get(componentParser.key());
            if (component == null || !component.isJsonObject()) {
                continue;
            }

            ItemFeature feature = componentParser.parser().apply(component.getAsJsonObject());
            if (feature != null) {
                list.add(feature);
            }
        }

        return List.copyOf(list);
    }
}
