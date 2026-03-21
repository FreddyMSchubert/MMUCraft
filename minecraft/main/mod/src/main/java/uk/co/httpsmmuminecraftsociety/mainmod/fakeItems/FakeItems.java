package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import uk.co.httpsmmuminecraftsociety.mainmod.DataLoader;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class FakeItems {
    private FakeItems() {}

    public static List<FakeItem> ALL = List.of();
    public static Map<String, FakeItem> MODEL_ID_MAP = Map.of();
    public static Map<Integer, FakeItem> CHARM_EFFECT_ID_MAP = Map.of();

    public static synchronized void reloadFromJson() {
        ALL = List.copyOf(DataLoader.loadFakeItems());
        MODEL_ID_MAP = ALL.stream().collect(Collectors.toUnmodifiableMap(FakeItem::id, Function.identity()));
        CHARM_EFFECT_ID_MAP = ALL.stream()
                .flatMap(item -> item.features().stream()
                        .filter(CharmItemFeature.class::isInstance)
                        .map(CharmItemFeature.class::cast)
                        .map(feature -> Map.entry(feature.charmId(), item)))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        MainMod.LOGGER.info("[fake-items] Loaded {} fake items from JSON", ALL.size());
        MainMod.LOGGER.info("[fake-items] MODEL_ID_MAP keys={}", MODEL_ID_MAP.keySet().stream().sorted().toList());
    }
}
