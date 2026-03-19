package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.List;

public record FakeItem
(
    String title,
    String id,
    Rarity rarity,
    int maxStackSize,
    List<String> tooltip,
    Item baseItem,
    List<ItemFeature> features
)
{}
