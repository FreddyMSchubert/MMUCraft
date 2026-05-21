package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FishItemFeature;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class FishingCatches {
    private FishingCatches() {
    }

    public static Pair<ItemStack, FishingPersonality> random(FishingHook hook) {
        List<Map.Entry<FakeItem, FishItemFeature>> fishItems = FakeItems.FISH.entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().id()))
                .toList();

        if (fishItems.isEmpty()) {
            throw new IllegalStateException("No fake items with a fish component are loaded");
        }

        Map.Entry<FakeItem, FishItemFeature> catchEntry = fishItems.get(hook.getRandom().nextInt(fishItems.size()));
        return Pair.of(catchEntry.getKey().createItemStack(), catchEntry.getValue().personality());
    }
}
