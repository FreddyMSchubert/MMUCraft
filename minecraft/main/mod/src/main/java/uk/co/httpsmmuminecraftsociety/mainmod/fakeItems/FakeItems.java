package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import uk.co.httpsmmuminecraftsociety.mainmod.DataLoader;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class FakeItems {
    private FakeItems() {}

    public static List<FakeItem> ALL = List.of();
    public static Map<String, FakeItem> ID_MAP = Map.of();
    public static Map<Integer, FakeItem> CHARM_ID_MAP = Map.of();

    public static synchronized void reloadFromJson() {
        ALL = List.copyOf(DataLoader.loadFakeItems());
        ID_MAP = ALL.stream().collect(Collectors.toUnmodifiableMap(FakeItem::id, Function.identity()));
        CHARM_ID_MAP = ALL.stream()
                .flatMap(item -> item.features().stream()
                        .filter(CharmItemFeature.class::isInstance)
                        .map(CharmItemFeature.class::cast)
                        .map(feature -> Map.entry(feature.charmId(), item)))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        MainMod.LOGGER.info("[FakeItems init] Loaded {} fake items from JSON: {}", ALL.size(), ID_MAP.keySet().stream().sorted().toList());
    }

    public static FakeItem getFakeItemFromStack(ItemStack stack) {
        StoredCharmData storedCharm = CharmStackData.getSingleStoredCharm(stack).orElse(null);
        if (storedCharm != null) {
            FakeItem canonicalCharm = CHARM_ID_MAP.get(storedCharm.charmId());
            if (canonicalCharm != null) {
                return canonicalCharm;
            }
        }

        CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        if (cmd.strings().isEmpty()) {
            return null;
        }

        FakeItem byId = ID_MAP.get(cmd.strings().getFirst());
        if (byId == null) {
            return null;
        }

        if (byId.getFeature(CharmItemFeature.class) != null) {
            return null;
        }

        return byId;
    }
}
