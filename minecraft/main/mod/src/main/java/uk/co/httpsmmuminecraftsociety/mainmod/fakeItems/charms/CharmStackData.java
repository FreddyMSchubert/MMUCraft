package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CharmStackData {
    private CharmStackData() {}

    public static final String CHARM_ABILITIES_TAG = "charm_abilities";

    private static final String CHARM_ID_TAG = "charmId";
    private static final String LEVEL_TAG = "level";

    public static List<StoredCharmData> getStoredCharms(ItemStack stack) {
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!nbt.contains(CHARM_ABILITIES_TAG) || nbt.getList(CHARM_ABILITIES_TAG).isEmpty()) {
            return List.of();
        }

        ListTag list = nbt.getListOrEmpty(CHARM_ABILITIES_TAG);
        List<StoredCharmData> result = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);

            if (entry.isEmpty()) continue;
            if (!entry.contains(CHARM_ID_TAG) || entry.getInt(CHARM_ID_TAG).isEmpty()) continue;
            if (!entry.contains(LEVEL_TAG) || entry.getInt(LEVEL_TAG).isEmpty()) continue;

            result.add(new StoredCharmData(
                    entry.getInt(CHARM_ID_TAG).get(),
                    entry.getInt(LEVEL_TAG).get()
            ));
        }

        return List.copyOf(result); // TODO: why not just return result here?
    }

    public static Optional<StoredCharmData> getSingleStoredCharm(ItemStack stack) {
        List<StoredCharmData> charms = getStoredCharms(stack);
        if (charms.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(charms.getFirst());
    }

    public static void setStoredCharms(ItemStack stack, List<StoredCharmData> charms) {
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (charms == null || charms.isEmpty()) {
            nbt.remove(CHARM_ABILITIES_TAG);
        } else {
            ListTag list = new ListTag();

            for (StoredCharmData charm : charms) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(CHARM_ID_TAG, charm.charmId());
                entry.putInt(LEVEL_TAG, charm.level());
                list.add(entry);
            }

            nbt.put(CHARM_ABILITIES_TAG, list);
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }
}
