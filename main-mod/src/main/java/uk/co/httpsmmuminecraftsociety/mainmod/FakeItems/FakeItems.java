package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.*;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

import java.util.*;
import java.util.stream.Collectors;

public final class FakeItems {
    private FakeItems() {}

    public static final List<FakeItemDef> ALL = List.of(
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-1",      "1 Dabloon",    "Official MMU Minecraft Society Mint Issue", Rarity.COMMON, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-5",      "5 Dabloons",   "Official MMU Minecraft Society Mint Issue", Rarity.COMMON, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-10",     "10 Dabloons",  "Official MMU Minecraft Society Mint Issue", Rarity.COMMON, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-50",     "50 Dabloons",  "Official MMU Minecraft Society Mint Issue", Rarity.COMMON, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-100",    "100 Dabloons", "Official MMU Minecraft Society Mint Issue", Rarity.COMMON, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-500",    "500 Dabloons", "Official MMU Minecraft Society Mint Issue", Rarity.COMMON, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-1000",   "1k Dabloons",  "Official MMU Minecraft Society Mint Issue", Rarity.UNCOMMON, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-5000",   "5k Dabloons",  "Official MMU Minecraft Society Mint Issue", Rarity.UNCOMMON, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-10000",  "10k Dabloons", "Official MMU Minecraft Society Mint Issue", Rarity.RARE, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-50000",  "50k Dabloons", "Official MMU Minecraft Society Mint Issue", Rarity.RARE, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-100000", "100k Dabloons","Official MMU Minecraft Society Mint Issue", Rarity.EPIC, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-500000", "500k Dabloons","Official MMU Minecraft Society Mint Issue", Rarity.EPIC, 50),
            createFakeItemDef(Items.COMMAND_BLOCK, "coin-1000000","1m Dabloons",  "With all the money in the world, you still can't buy yourself a soul.", Rarity.EPIC, 50),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-armorer","Armorer Goggles",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-butcher","Butcher Headband",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-farmer","Farmer Straw hat",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-fisherman","Fisherman Hat",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-fletcher","Fletcher Hat",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-librarian","Librarian Hat",  "", Rarity.COMMON, 1),
            createFakeItemDef(Items.CARVED_PUMPKIN, "cosmetic-hat-villager-shepherd","Shepherd Hat",  "", Rarity.COMMON, 1),
            createFakeEquippableItemDef(Items.COMMAND_BLOCK, "Open Heart Charm", "Blessed be the pacemakers", Rarity.UNCOMMON, 1, Equippable.builder(EquipmentSlot.CHEST).setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, "open_heart__charm"))).setSwappable(true).setDispensable(true).setDamageOnHurt(false).build(), new OpenHeartCharm()),
            createFakeEquippableItemDef(Items.COMMAND_BLOCK, "Running Shoes", "Been there, run that.", Rarity.UNCOMMON, 1, Equippable.builder(EquipmentSlot.FEET).setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, "running_shoes__charm"))).setSwappable(true).setDispensable(true).setDamageOnHurt(false).build(), new RunningShoesCharm()),
            createFakeEquippableItemDef(Items.COMMAND_BLOCK, "Candle of the Deep Charm", "Light on your feet.", Rarity.UNCOMMON, 1, Equippable.builder(EquipmentSlot.LEGS).setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, "candle_of_the_deep__charm"))).setSwappable(true).setDispensable(true).setDamageOnHurt(false).build(), new CandleOfTheDeepCharm()),
            createFakeEquippableItemDef(Items.COMMAND_BLOCK, "Hiking Boots Charm", "Ever heard of a shortcut?", Rarity.UNCOMMON, 1, Equippable.builder(EquipmentSlot.FEET).setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, "hiking_boots__charm"))).setSwappable(true).setDispensable(true).setDamageOnHurt(false).build(), new HikingBootsCharm(0)),
            createFakeEquippableItemDef(Items.COMMAND_BLOCK, "Golden Hiking Boots Charm", "That's one pretty big step for man.", Rarity.UNCOMMON, 1, Equippable.builder(EquipmentSlot.FEET).setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, "golden_hiking_boots__charm"))).setSwappable(true).setDispensable(true).setDamageOnHurt(false).build(), new HikingBootsCharm(1)),
            createFakeEquippableItemDef(Items.COMMAND_BLOCK, "Diamond Hiking Boots Charm", "You don't ever-rest do you...", Rarity.UNCOMMON, 1, Equippable.builder(EquipmentSlot.FEET).setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MainMod.RESOURCE_PACK_ID, "diamond_hiking_boots__charm"))).setSwappable(true).setDispensable(true).setDamageOnHurt(false).build(), new HikingBootsCharm(2))
    );
    private static final Map<String, FakeItemDef> BY_ID =
            ALL.stream().collect(Collectors.toUnmodifiableMap(FakeItemDef::id, d -> d));

    public static FakeItemDef def(String id) {
        FakeItemDef d = BY_ID.get(id);
        if (d == null) throw new IllegalArgumentException("Unknown fake item id: " + id);
        return d;
    }

    public static ItemStack createStack(String id, int amount) {
        FakeItemDef d = def(id);
        ItemStack stack = new ItemStack(d.baseItem, amount);

        stack = applyComponents(stack, d);

        return stack;
    }

    public record FakeItemDef(
            Item baseItem,
            String id,
            Component title,
            List<Component> lore,
            Rarity rarity,
            int maxStackSize,
            Optional<Equippable> equippableSettings,
            Optional<Charm> charm
    ) {}

    private static FakeItemDef createFakeItemDef(Item baseItem, String id, String title, String loreLine, Rarity rarity, int maxStackSize) {
        return new FakeItemDef(baseItem, id, Component.literal(title), List.of(Component.literal(loreLine)), rarity, maxStackSize, Optional.empty(), Optional.empty());
    }
    private static FakeItemDef createFakeEquippableItemDef(Item baseItem, String title, String loreLine, Rarity rarity, int maxStackSize, Equippable equippableSettings, Charm charm) {
        return new FakeItemDef(baseItem, charm.id(), Component.literal(title), List.of(Component.literal(loreLine)), rarity, maxStackSize, Optional.of(equippableSettings), Optional.of(charm));
    }


    private static ItemStack applyComponents(ItemStack stack, FakeItemDef d) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(d.id()), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, d.title());
        if (d.lore() != null && !d.lore().isEmpty() && !d.lore().stream().allMatch(Objects::isNull))
            stack.set(DataComponents.LORE, new ItemLore(d.lore()));
        stack.set(DataComponents.RARITY, d.rarity());
        stack.set(DataComponents.MAX_STACK_SIZE, d.maxStackSize());
        if (d.equippableSettings().isPresent())
            stack.set(DataComponents.EQUIPPABLE, d.equippableSettings.get());

        if (d.charm().isPresent()) {
            stack = d.charm().get().onCreation(stack);

            if (d.charm().get().subcribeToOnTick())
            {
                CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                CompoundTag tag = cd.copyTag();
                tag.putBoolean(Utils.TAG_TICK, true);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
        }

        return stack;
    }
}
