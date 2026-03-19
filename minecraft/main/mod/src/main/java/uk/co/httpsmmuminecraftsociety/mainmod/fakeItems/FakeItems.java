package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.itemdata.ItemDataLoader;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class FakeItems {
    private FakeItems() {}

    public static List<FakeItem> ALL = List.of();
    public static Map<String, FakeItem> MODEL_ID_MAP = Map.of();
    public static Map<Integer, CharmFakeItem> CHARM_EFFECT_ID_MAP = Map.of();
    public static Map<Integer, ConsumableFakeItem> CONSUMABLE_MODEL_ID_MAP = Map.of();

    static {
        reloadFromJson();
    }

    public static synchronized void reloadFromJson() {
        ALL = List.copyOf(ItemDataLoader.loadAll());
        MODEL_ID_MAP = ALL.stream().collect(Collectors.toUnmodifiableMap(FakeItem::getModelId, Function.identity()));
        CHARM_EFFECT_ID_MAP = ALL.stream()
                .filter(CharmFakeItem.class::isInstance)
                .map(CharmFakeItem.class::cast)
                .collect(Collectors.toUnmodifiableMap(CharmFakeItem::getEffectId, Function.identity()));
        CONSUMABLE_MODEL_ID_MAP = ALL.stream()
                .filter(ConsumableFakeItem.class::isInstance)
                .map(ConsumableFakeItem.class::cast)
                .collect(Collectors.toUnmodifiableMap(ConsumableFakeItem::getConsumableId, Function.identity()));

        MainMod.LOGGER.info("Loaded {} fake items from JSON.", ALL.size());
    }
}
