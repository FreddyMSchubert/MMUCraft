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

            // handheld - 2
            // chest - 3
            // leggings - 7
            // boots - 4

            new CharmFakeItem(1, "Staff of Crafting",          Rarity.COMMON, new CraftingStaffCharm(),   "Crafting on the go!"),
            new CharmFakeItem(2, "Staff of Soulbound Storage", Rarity.COMMON, new EnderChestStaffCharm(), "For all your soulbound item needs!"),
            new EquippableCharmFakeItem(3,  "Heart on your Sleeve Charm", Rarity.UNCOMMON, "heart_sleeve__charm",         EquipmentSlot.CHEST, new HeartCharm(0),        "Sgt. Pepper's Lonely Hearts Club Charm",     "Grants the user extra life."),
            new EquippableCharmFakeItem(4,  "Heart of Gold Charm",        Rarity.UNCOMMON, "heart_gold__charm",           EquipmentSlot.CHEST, new HeartCharm(1),        "Blessed be the pacemakers",                  "Grants the user more extra life."),
            new EquippableCharmFakeItem(5,  "Heart of Diamond Charm",     Rarity.UNCOMMON, "heart_diamond__charm",        EquipmentSlot.CHEST, new HeartCharm(2),        "Slightly wet and pulsing.",                  "Grants the user significantly more extra life."),
            new EquippableCharmFakeItem(6,  "Heart of Netherite Charm",   Rarity.UNCOMMON, "heart_netherite__charm",      EquipmentSlot.CHEST, new HeartCharm(3),        "Pretty serious heartware.",                  "Grants the user a stupidly unbalanced amount of extra life."),
            new EquippableCharmFakeItem(7,  "Running Shoes Charm",        Rarity.UNCOMMON, "running_shoes__charm",        EquipmentSlot.FEET,  new RunningShoesCharm(),       "Been there, run that.",                      "Enhances the user's mobility."),
            new EquippableCharmFakeItem(8,  "Candle of the Deep Charm",   Rarity.UNCOMMON, "candle_of_the_deep__charm",   EquipmentSlot.LEGS,  new CandleOfTheDeepCharm(),    "Light on your feet.",                        "Illuminates the area around the user."),
            new EquippableCharmFakeItem(9,  "Hiking Boots Charm",         Rarity.UNCOMMON, "hiking_boots__charm",         EquipmentSlot.FEET,  new HikingBootsCharm(0),  "That's one pretty big step for man.",        "Allows the user walk up one-block high obstacles without jumping."),
            new EquippableCharmFakeItem(10, "Golden Hiking Boots Charm",  Rarity.UNCOMMON, "golden_hiking_boots__charm",  EquipmentSlot.FEET,  new HikingBootsCharm(1),  "Ever heard of a shortcut?",                  "Allows the user walk up fence-high obstacles without jumping."),
            new EquippableCharmFakeItem(11, "Diamond Hiking Boots Charm", Rarity.UNCOMMON, "diamond_hiking_boots__charm", EquipmentSlot.FEET,  new HikingBootsCharm(2),  "You don't ever-rest do you...",              "Allows the user walk up two-block high obstacles without jumping."),
            new EquippableCharmFakeItem(12, "Giant's Boots Charm",        Rarity.UNCOMMON, "giants_boots__charm",         EquipmentSlot.FEET,  new GiantsBootsCharm(),        "These boots are made for walkin'",           "Grants the user the lost power of the ancient giants."),
            new EquippableCharmFakeItem(13, "Leprechaun Boots Charm",     Rarity.UNCOMMON, "leprechaun_boots__charm",     EquipmentSlot.FEET,  new LeprechaunBootsCharm(),    "I'm feeling lucky.",                         "Irish people are weird dude."),
            new EquippableCharmFakeItem(14, "Mermaid Scales Charm",       Rarity.UNCOMMON, "mermaid_scales__charm",       EquipmentSlot.LEGS,  new MermaidScalesCharm(),      "Be your own little mermaid.",                "Makes you faster in water."),
            new EquippableCharmFakeItem(15, "Strider Shales Charm",       Rarity.UNCOMMON, "strider_shales__charm",       EquipmentSlot.LEGS,  new StriderShalesCharm(),      "Don't get cold feet.",                       "You can swim in lava as if it were water."),
            new EquippableCharmFakeItem(16, "Extendo Grip Charm",         Rarity.UNCOMMON, "extendo_grip__charm",         EquipmentSlot.CHEST, new ExtendoGripCharm(),        "Saves you a round trip to turkey.",          "Stretches your arms in a very humane way."),
            new EquippableCharmFakeItem(17, "Bunny Pajamas Charm",        Rarity.UNCOMMON, "bunny_pajamas__charm",        EquipmentSlot.LEGS,  new BunnyPajamasCharm(),       "Perfect for some Eggsercise and Hareobics.", "You'll jump like LeBron and take less fall damage. The bunnies might also get a bit snacky."),
            new EquippableCharmFakeItem(18, "Kitty Pajamas Charm",        Rarity.UNCOMMON, "kitty_pajamas__charm",        EquipmentSlot.LEGS,  new KittyPajamasCharm(),       "They look nice and purrple.",                "The wearer won't take fall damage and scare away creepers."),
            new EquippableCharmFakeItem(19, "Spider Pajamas Charm",       Rarity.UNCOMMON, "spider_pajamas__charm",       EquipmentSlot.LEGS,  new SpiderPajamasCharm(),      "Oohhhh how emo... /s",                       "Let's you climb up the sides of blocks."),
            new EquippableCharmFakeItem(20, "Cave Spider Pajamas Charm",  Rarity.UNCOMMON, "cave_spider_pajamas__charm",  EquipmentSlot.LEGS,  new CaveSpiderPajamasCharm(),  "Poison not included.",                       "Let's you climb straight across the ceiling."),
            new EquippableCharmFakeItem(21, "Goop Hand Charm",            Rarity.UNCOMMON, "goop_hand__charm",            EquipmentSlot.CHEST, new GoopHandCharm(),           "Calums patented formula.",                   "Bounces the victims of your attacks further away")
    );
    public static final Map<String, FakeItem> MODEL_ID_MAP = ALL.stream().collect(Collectors.toUnmodifiableMap(FakeItem::getModelId, d -> d));
    public static final Map<Integer, CharmFakeItem> CHARM_EFFECT_ID_MAP = ALL.stream().filter(CharmFakeItem.class::isInstance).map(CharmFakeItem.class::cast).collect(Collectors.toUnmodifiableMap(CharmFakeItem::getEffectId, Function.identity()));
}
