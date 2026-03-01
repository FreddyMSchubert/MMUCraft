package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.stream.Collectors;

public final class FakeItems {
    private FakeItems() {}

    public static final List<FakeItemDef> ALL = List.of(
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-1",      "1 Dabloon",    "Official MMU Minecraft Society Mint Issue", Rarity.COMMON, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-5",      "5 Dabloons",   "Official MMU Minecraft Society Mint Issue", Rarity.COMMON, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-10",     "10 Dabloons",  "Official MMU Minecraft Society Mint Issue", Rarity.COMMON, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-50",     "50 Dabloons",  "Official MMU Minecraft Society Mint Issue", Rarity.COMMON, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-100",    "100 Dabloons", "Official MMU Minecraft Society Mint Issue", Rarity.UNCOMMON, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-500",    "500 Dabloons", "Official MMU Minecraft Society Mint Issue", Rarity.UNCOMMON, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-1000",   "1k Dabloons",  "Official MMU Minecraft Society Mint Issue", Rarity.UNCOMMON, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-5000",   "5k Dabloons",  "Official MMU Minecraft Society Mint Issue", Rarity.RARE, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-10000",  "10k Dabloons", "Official MMU Minecraft Society Mint Issue", Rarity.RARE, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-50000",  "50k Dabloons", "Official MMU Minecraft Society Mint Issue", Rarity.RARE, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-100000", "100k Dabloons","Official MMU Minecraft Society Mint Issue", Rarity.EPIC, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-500000", "500k Dabloons","Official MMU Minecraft Society Mint Issue", Rarity.EPIC, 67),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-1000000","1m Dabloons",  "With all the money in the world, you still can't buy yourself a soul.", Rarity.EPIC, 67),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-armorer","Armorer Goggles",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-butcher","Butcher Headband",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-farmer","Farmer Straw hat",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-fisherman","Fisherman Hat",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-fletcher","Fletcher Hat",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-librarian","Librarian Hat",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-shepherd","Shepherd Hat",  "", Rarity.COMMON, 1)
    );
    private static final Map<String, FakeItemDef> BY_ID =
            ALL.stream().collect(Collectors.toUnmodifiableMap(FakeItemDef::id, d -> d));

    public static FakeItemDef def(String id) {
        FakeItemDef d = BY_ID.get(id);
        if (d == null) throw new IllegalArgumentException("Unknown fake item id: " + id);
        return d;
    }

    public static ItemStack createStack(String id) {
        return createStack(id, 1);
    }
    public static ItemStack createStack(String id, int amount) {
        FakeItemDef d = def(id);
        ItemStack stack = new ItemStack(d.baseItem, amount);

        applyComponents(stack, d);

        return stack;
    }


    public record FakeItemDef(
            Item baseItem,
            String id,
            Component title,
            List<Component> lore,
            Rarity rarity,
            int maxStackSize
    ) {}

    private static FakeItemDef createFakeItemDef(Item baseItem, String id, String title, String loreLine, Rarity rarity, int maxStackSize) {
        return new FakeItemDef(baseItem, id, Component.literal(title), List.of(Component.literal(loreLine)), rarity, maxStackSize);
    }


    private static void applyComponents(ItemStack stack, FakeItemDef d) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(d.id()), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, d.title());
        if (d.lore() != null && !d.lore().isEmpty())
            stack.set(DataComponents.LORE, new ItemLore(d.lore()));
        stack.set(DataComponents.RARITY, d.rarity());
        stack.set(DataComponents.MAX_STACK_SIZE, d.maxStackSize());
    }
}
