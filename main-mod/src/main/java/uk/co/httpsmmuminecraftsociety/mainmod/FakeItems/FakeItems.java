package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Rarity;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.*;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.*;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class FakeItems {
    private FakeItems() {}

    public static final List<FakeItem> ALL = List.of(
            new BasicFakeItem("coin-1",      "1 Dabloon",     Rarity.COMMON,   50,  "Official MMU Minecraft Society Mint Issue", "\"Might turn to a million and we all rich\""),
            new BasicFakeItem("coin-5",      "5 Dabloons",    Rarity.COMMON,   50,  "Official MMU Minecraft Society Mint Issue", "\"I ain't no fortunate one, no\""),
            new BasicFakeItem("coin-10",     "10 Dabloons",   Rarity.COMMON,   50,  "Official MMU Minecraft Society Mint Issue", "\"I want to buy you somethin' - But I don't have any money\""),
            new BasicFakeItem("coin-50",     "50 Dabloons",   Rarity.COMMON,   50,  "Official MMU Minecraft Society Mint Issue", "\"We just wanna make the world dance\""),
            new BasicFakeItem("coin-100",    "100 Dabloons",  Rarity.COMMON,   50,  "Official MMU Minecraft Society Mint Issue", "\"Mo money, mo problems.\""),
            new BasicFakeItem("coin-500",    "500 Dabloons",  Rarity.COMMON,   50,  "Official MMU Minecraft Society Mint Issue", "\"It's a crime.\""),
            new BasicFakeItem("coin-1000",   "1k Dabloons",   Rarity.UNCOMMON, 50,  "Official MMU Minecraft Society Mint Issue", "\"If I was a rich girl\""),
            new BasicFakeItem("coin-5000",   "5k Dabloons",   Rarity.UNCOMMON, 50,  "Official MMU Minecraft Society Mint Issue", "\"Must be funny\""),
            new BasicFakeItem("coin-10000",  "10k Dabloons",  Rarity.RARE,     50,  "Official MMU Minecraft Society Mint Issue", "\"that's money, honey\""),
            new BasicFakeItem("coin-50000",  "50k Dabloons",  Rarity.RARE,     50,  "Official MMU Minecraft Society Mint Issue", "\"If you catch me at the border, I got visas in my name\""),
            new BasicFakeItem("coin-100000", "100k Dabloons", Rarity.EPIC,     50,  "Official MMU Minecraft Society Mint Issue", "\"I want it, I got it, I want it, I got it (baby)\""),
            new BasicFakeItem("coin-500000", "500k Dabloons", Rarity.EPIC,     50,  "Official MMU Minecraft Society Mint Issue", "\"'Cause we are living in a material world\""),
            new BasicFakeItem("coin-1000000","1m Dabloons",   Rarity.EPIC,     50,  "Official MMU Minecraft Society Mint Issue", "\"Money is the anthem of success - So before we go out, what's your address?\""),
            new CosmeticFakeItem("cosmetic-hat-villager-armorer",  "Armorer Goggles",   Rarity.COMMON),
            new CosmeticFakeItem("cosmetic-hat-villager-butcher",  "Butcher Headband",  Rarity.COMMON),
            new CosmeticFakeItem("cosmetic-hat-villager-farmer",   "Farmer Straw hat",  Rarity.COMMON),
            new CosmeticFakeItem("cosmetic-hat-villager-fisherman","Fisherman Hat",     Rarity.COMMON),
            new CosmeticFakeItem("cosmetic-hat-villager-fletcher", "Fletcher Hat",      Rarity.COMMON),
            new CosmeticFakeItem("cosmetic-hat-villager-librarian","Librarian Hat",     Rarity.COMMON),
            new CosmeticFakeItem("cosmetic-hat-villager-shepherd", "Shepherd Hat",      Rarity.COMMON),
            new CharmFakeItem(1, "Staff of Crafting",          Rarity.COMMON, new CraftingStaffCharm(),   "Crafting on the go!"),
            new CharmFakeItem(2, "Staff of Soulbound Storage", Rarity.COMMON, new EnderChestStaffCharm(), "For all your soulbound item needs!"),
            new EquippableCharmFakeItem(3, "Open Heart Charm",           Rarity.UNCOMMON, "open_heart__charm",           EquipmentSlot.CHEST, new OpenHeartCharm(),          "Blessed be the pacemakers",           "Grants the user extra life."),
            new EquippableCharmFakeItem(4, "Running Shoes Charm",        Rarity.UNCOMMON, "running_shoes__charm",        EquipmentSlot.FEET,  new RunningShoesCharm(),       "Been there, run that.",               "Enhances the user's mobility."),
            new EquippableCharmFakeItem(5, "Candle of the Deep Charm",   Rarity.UNCOMMON, "candle_of_the_deep__charm",   EquipmentSlot.LEGS,  new CandleOfTheDeepCharm(),    "Light on your feet.",                "Illuminates the area around the user."),
            new EquippableCharmFakeItem(6, "Hiking Boots Charm",         Rarity.UNCOMMON, "hiking_boots__charm",         EquipmentSlot.FEET,  new HikingBootsCharm(0),  "That's one pretty big step for man.", "Allows the user walk up one-block high obstacles without jumping."),
            new EquippableCharmFakeItem(7, "Golden Hiking Boots Charm",  Rarity.UNCOMMON, "golden_hiking_boots__charm",  EquipmentSlot.FEET,  new HikingBootsCharm(1),  "Ever heard of a shortcut?",           "Allows the user walk up fence-high obstacles without jumping."),
            new EquippableCharmFakeItem(8, "Diamond Hiking Boots Charm", Rarity.UNCOMMON, "diamond_hiking_boots__charm", EquipmentSlot.FEET,  new HikingBootsCharm(2),  "You don't ever-rest do you...",       "Allows the user walk up two-block high obstacles without jumping."),
            new EquippableCharmFakeItem(9, "Giant's Boots Charm",        Rarity.UNCOMMON, "giants_boots__charm",         EquipmentSlot.FEET,  new GiantsBootsCharm(),        "These boots are made for walkin'",     "Grants the user the lost power of the ancient giants."),
            new EquippableCharmFakeItem(10,"Leprechaun Boots Charm",     Rarity.UNCOMMON, "leprechaun_boots__charm",     EquipmentSlot.FEET,  new LeprechaunBootsCharm(),    "I'm feeling lucky.",                   "Irish people are weird dude.")
    );
    public static final Map<String, FakeItem> MODEL_ID_MAP = ALL.stream().collect(Collectors.toUnmodifiableMap(FakeItem::getModelId, d -> d));
    public static final Map<Integer, CharmFakeItem> CHARM_EFFECT_ID_MAP = ALL.stream().filter(CharmFakeItem.class::isInstance).map(CharmFakeItem.class::cast).collect(Collectors.toUnmodifiableMap(CharmFakeItem::getEffectId, Function.identity()));
}
