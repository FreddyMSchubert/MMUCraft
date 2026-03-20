package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public sealed interface ItemFeature
        permits CharmItemFeature, ConsumableItemFeature, DyeableItemFeature, EquippableCharmItemFeature, EquippableCosmeticItemFeature
{
    void apply(ItemStack stack);

    List<Function<JsonObject, ? extends ItemFeature>> PARSERS = List.of(
            CharmItemFeature::of,
            ConsumableItemFeature::of,
            DyeableItemFeature::of,
            EquippableCharmItemFeature::of,
            EquippableCosmeticItemFeature::of
    );
    static List<ItemFeature> of(JsonObject json) {
        List<ItemFeature> list = new ArrayList<>();
        for (Function<JsonObject, ? extends ItemFeature> parser : PARSERS) {
            ItemFeature feature = parser.apply(json);
            if (feature != null) {
                list.add(feature);
            }
        }
        return List.copyOf(list);
    };
}
