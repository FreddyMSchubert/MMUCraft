package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.dataget.DataLoader;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.StoredCharmData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FishItemFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class FakeItems {
        private static final int TOOLTIP_LINE_LENGTH = 50;

        private FakeItems() {}

        public static List<FakeItem> ALL = List.of();
        public static Map<String, FakeItem> ID_MAP = Map.of();
        public static Map<Integer, FakeItem> CHARM_ID_MAP = Map.of();
        public static Map<FakeItem, FishItemFeature> FISH = Map.of();

        public static synchronized void reloadFromJson() {
            ALL = List.copyOf(DataLoader.loadFakeItems());
            ID_MAP = ALL.stream().collect(Collectors.toUnmodifiableMap(FakeItem::id, Function.identity()));
            CHARM_ID_MAP = ALL.stream()
                    .flatMap(item -> item.features().stream()
                            .filter(CharmItemFeature.class::isInstance)
                            .map(CharmItemFeature.class::cast)
                            .map(feature -> Map.entry(feature.charmId(), item)))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            FISH = ALL.stream()
                    .flatMap(item -> item.features().stream()
                            .filter(FishItemFeature.class::isInstance)
                            .map(FishItemFeature.class::cast)
                            .map(feature -> Map.entry(item, feature)))
                    .collect(Collectors.toUnmodifiableMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue
                    ));

            MainMod.LOGGER.info("[FakeItems init] Loaded {} fake items from JSON: {}", ALL.size(), ID_MAP.keySet().stream().sorted().toList());
        }

    public static void validate() {
        for (FakeItem item : ALL) {
            item.validate();
        }
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

    public static boolean isSpecificFakeItem(ItemStack stack, String fakeItemId) {
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return cmd != null && cmd.strings().contains(fakeItemId);
    }

    public static boolean isKnownFakeItem(String fakeItemId) {
        return FakeItems.ID_MAP.containsKey(fakeItemId);
    }

    public static FakeItem requireFakeItem(String fakeItemId) {
        FakeItem fakeItem = FakeItems.ID_MAP.get(fakeItemId);
        if (fakeItem == null) {
            throw new IllegalArgumentException("Unknown fakeitem id: " + fakeItemId);
        }
        return fakeItem;
    }

    public static ItemStack createFakeItemStack(String fakeItemId, int count) {
        ItemStack stack = requireFakeItem(fakeItemId).createItemStack();
        stack.setCount(count);
        return stack;
    }

    public static void wrapTooltip(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return;

        List<Component> wrapped = lore.lines().stream()
                .flatMap(line -> wrapTooltipLine(line).stream())
                .toList();
        if (wrapped.size() != lore.lines().size()) {
            stack.set(DataComponents.LORE, new ItemLore(wrapped));
        }
    }

    private static List<Component> wrapTooltipLine(Component line) {
        List<String> wrappedText = wrapTooltipLine(line.getString());
        if (wrappedText.size() == 1) return List.of(line);

        return wrappedText.stream()
                .map(text -> Component.literal(text).setStyle(line.getStyle()))
                .map(Component.class::cast)
                .toList();
    }

    static List<String> wrapTooltipLine(String text) {
        if (text.length() <= TOOLTIP_LINE_LENGTH) return List.of(text);

        List<String> wrapped = new ArrayList<>();
        int start = 0;
        while (text.length() - start > TOOLTIP_LINE_LENGTH) {
            int end = start + TOOLTIP_LINE_LENGTH;
            int space = text.lastIndexOf(' ', end);
            if (space > start) end = space;

            wrapped.add(text.substring(start, end));
            start = end;
            while (start < text.length() && text.charAt(start) == ' ') start++;
        }
        if (start < text.length()) {
            wrapped.add(text.substring(start));
        }
        return wrapped;
    }
}
