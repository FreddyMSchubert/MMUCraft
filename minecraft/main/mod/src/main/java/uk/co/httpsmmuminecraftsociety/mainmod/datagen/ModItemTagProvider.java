package uk.co.httpsmmuminecraftsociety.mainmod.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends FabricTagsProvider.ItemTagsProvider
{
    public static final TagKey<Item> CHARM_COMBINABLE_ARMOR_ITEMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_combinable_armor_items"));
    public static final TagKey<Item> SINGLE_CHARM_COMBINABLE_ARMOR_ITEMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "single_combinable_armor_items"));
    public static final TagKey<Item> DOUBLE_CHARM_COMBINABLE_ARMOR_ITEMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "double_combinable_armor_items"));
    public static final TagKey<Item> TRIPLE_CHARM_COMBINABLE_ARMOR_ITEMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "triple_combinable_armor_items"));
    public static final TagKey<Item> CHARM_COMBINABLE_ARMOR_ITEMS_LEATHER = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_combinable_armor_items/leather"));
    public static final TagKey<Item> CHARM_COMBINABLE_ARMOR_ITEMS_COPPER = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_combinable_armor_items/copper"));
    public static final TagKey<Item> CHARM_COMBINABLE_ARMOR_ITEMS_GOLD = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_combinable_armor_items/gold"));
    public static final TagKey<Item> CHARM_COMBINABLE_ARMOR_ITEMS_CHAINMAIL = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_combinable_armor_items/chainmail"));
    public static final TagKey<Item> CHARM_COMBINABLE_ARMOR_ITEMS_IRON = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_combinable_armor_items/iron"));
    public static final TagKey<Item> CHARM_COMBINABLE_ARMOR_ITEMS_DIAMOND = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_combinable_armor_items/diamond"));
    public static final TagKey<Item> CHARM_COMBINABLE_ARMOR_ITEMS_NETHERITE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_combinable_armor_items/netherite"));

    public static final TagKey<Item> COSMETIC_COMBINABLE_ARMOR_ITEMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "cosmetic_combinable_armor_items"));

    public static final TagKey<Item> CHARM_DROPPING_CHESTS_HAVE_ITEMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "charm_dropping_chests_have_items"));

    public static final TagKey<Item> FISHES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fishes"));
    public static final TagKey<Item> MUSHROOMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "mushrooms"));
    public static final TagKey<Item> FUNGI = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fungi"));
    public static final TagKey<Item> FARM_ANIMAL_MEATS_RAW = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "farm_animal_meats_raw"));

    public static final TagKey<Item> CARPETS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "carpets"));

    public static final TagKey<Item> PICKAXE_HEATER_LEVEL1_SMELTABLE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "pickaxe_heater_level1_smeltable"));
    public static final TagKey<Item> PICKAXE_HEATER_LEVEL2_SMELTABLE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "pickaxe_heater_level2_smeltable"));

    public ModItemTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture)
    {
        super(output, registryLookupFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider)
    {
        // Dyeability

        builder(ItemTags.CAULDRON_CAN_REMOVE_DYE)
                .add(key(Items.CARVED_PUMPKIN));

        // Charm Stuff

        builder(CHARM_COMBINABLE_ARMOR_ITEMS)
                .add(key(Items.LEATHER_BOOTS))
                .add(key(Items.LEATHER_LEGGINGS))
                .add(key(Items.LEATHER_CHESTPLATE))
                .add(key(Items.COPPER_BOOTS))
                .add(key(Items.COPPER_LEGGINGS))
                .add(key(Items.COPPER_CHESTPLATE))
                .add(key(Items.GOLDEN_BOOTS))
                .add(key(Items.GOLDEN_LEGGINGS))
                .add(key(Items.GOLDEN_CHESTPLATE))
                .add(key(Items.CHAINMAIL_BOOTS))
                .add(key(Items.CHAINMAIL_LEGGINGS))
                .add(key(Items.CHAINMAIL_CHESTPLATE))
                .add(key(Items.IRON_BOOTS))
                .add(key(Items.IRON_LEGGINGS))
                .add(key(Items.IRON_CHESTPLATE))
                .add(key(Items.DIAMOND_BOOTS))
                .add(key(Items.DIAMOND_LEGGINGS))
                .add(key(Items.DIAMOND_CHESTPLATE))
                .add(key(Items.NETHERITE_BOOTS))
                .add(key(Items.NETHERITE_LEGGINGS))
                .add(key(Items.NETHERITE_CHESTPLATE));

        builder(CHARM_DROPPING_CHESTS_HAVE_ITEMS)
                .add(key(Items.POTION))
                .add(key(Items.SPLASH_POTION))
                .add(key(Items.LINGERING_POTION))
                .add(key(Items.ENCHANTED_BOOK))
                .add(key(Items.ECHO_SHARD))
                .add(key(Items.MUSIC_DISC_5))
                .add(key(Items.MUSIC_DISC_11))
                .add(key(Items.MUSIC_DISC_13))
                .add(key(Items.MUSIC_DISC_BLOCKS))
                .add(key(Items.MUSIC_DISC_CAT))
                .add(key(Items.MUSIC_DISC_BOUNCE))
                .add(key(Items.MUSIC_DISC_CHIRP))
                .add(key(Items.MUSIC_DISC_CREATOR))
                .add(key(Items.MUSIC_DISC_CREATOR_MUSIC_BOX))
                .add(key(Items.MUSIC_DISC_FAR))
                .add(key(Items.MUSIC_DISC_LAVA_CHICKEN))
                .add(key(Items.MUSIC_DISC_MALL))
                .add(key(Items.MUSIC_DISC_MELLOHI))
                .add(key(Items.MUSIC_DISC_OTHERSIDE))
                .add(key(Items.MUSIC_DISC_PIGSTEP))
                .add(key(Items.MUSIC_DISC_PRECIPICE))
                .add(key(Items.MUSIC_DISC_RELIC))
                .add(key(Items.MUSIC_DISC_STAL))
                .add(key(Items.MUSIC_DISC_STRAD))
                .add(key(Items.MUSIC_DISC_TEARS))
                .add(key(Items.MUSIC_DISC_WAIT))
                .add(key(Items.MUSIC_DISC_WARD))
                .add(key(Items.DISC_FRAGMENT_5))
                .add(key(Items.DIAMOND))
                .add(key(Items.DIAMOND_BLOCK))
                .add(key(Items.DIAMOND_HELMET))
                .add(key(Items.DIAMOND_CHESTPLATE))
                .add(key(Items.DIAMOND_LEGGINGS))
                .add(key(Items.DIAMOND_BOOTS))
                .add(key(Items.DIAMOND_AXE))
                .add(key(Items.DIAMOND_PICKAXE))
                .add(key(Items.DIAMOND_SWORD))
                .add(key(Items.DIAMOND_HOE))
                .add(key(Items.DIAMOND_SHOVEL))
                .add(key(Items.DIAMOND_SPEAR))
                .add(key(Items.DIAMOND_NAUTILUS_ARMOR))
                .add(key(Items.DIAMOND_HORSE_ARMOR))
                .add(key(Items.GOLD_NUGGET))
                .add(key(Items.GOLDEN_APPLE))
                .add(key(Items.GOLD_INGOT))
                .add(key(Items.GOLD_BLOCK))
                .add(key(Items.ENCHANTED_GOLDEN_APPLE))
                .add(key(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE))
                .add(key(Items.HEART_OF_THE_SEA))
                .add(key(Items.TRIDENT))
                .add(key(Items.EXPERIENCE_BOTTLE))
                .add(key(Items.GILDED_BLACKSTONE))
                .add(key(Items.ANCIENT_DEBRIS))
                .add(key(Items.NETHERITE_INGOT))
                .add(key(Items.NETHERITE_SCRAP))
                .add(key(Items.NETHERITE_AXE))
                .add(key(Items.NETHERITE_PICKAXE))
                .add(key(Items.NETHERITE_SWORD))
                .add(key(Items.NETHERITE_HOE))
                .add(key(Items.NETHERITE_SHOVEL))
                .add(key(Items.NETHERITE_CHESTPLATE))
                .add(key(Items.NETHERITE_BOOTS))
                .add(key(Items.NETHERITE_HELMET))
                .add(key(Items.NETHERITE_LEGGINGS))
                .add(key(Items.NETHERITE_BLOCK))
                .add(key(Items.NETHERITE_HORSE_ARMOR))
                .add(key(Items.NETHERITE_NAUTILUS_ARMOR))
                .add(key(Items.NETHERITE_SPEAR));

        builder(PICKAXE_HEATER_LEVEL1_SMELTABLE)
                // Iron
                .add(key(Items.IRON_ORE))
                .add(key(Items.DEEPSLATE_IRON_ORE))
                .add(key(Items.RAW_IRON))

                // Copper
                .add(key(Items.COPPER_ORE))
                .add(key(Items.DEEPSLATE_COPPER_ORE))
                .add(key(Items.RAW_COPPER))

                // Gold
                .add(key(Items.GOLD_ORE))
                .add(key(Items.DEEPSLATE_GOLD_ORE))
                .add(key(Items.NETHER_GOLD_ORE))
                .add(key(Items.RAW_GOLD))

                // Netherite
                .add(key(Items.ANCIENT_DEBRIS));

        builder(PICKAXE_HEATER_LEVEL2_SMELTABLE)
                // Miscellaneous
                .add(key(Items.WET_SPONGE))
                .add(key(Items.NETHERRACK))
                .add(key(Items.RESIN_CLUMP))
                .add(key(Items.CLAY_BALL))
                .add(key(Items.CACTUS))
                .add(key(Items.SEA_PICKLE))
                .add(key(Items.CHORUS_FRUIT))
                .add(key(Items.SAND))
                .add(key(Items.RED_SAND))
                .add(key(Items.STONE))
                .add(key(Items.DEEPSLATE))

                // Logs
                .add(key(Items.OAK_LOG))
                .add(key(Items.SPRUCE_LOG))
                .add(key(Items.BIRCH_LOG))
                .add(key(Items.JUNGLE_LOG))
                .add(key(Items.ACACIA_LOG))
                .add(key(Items.DARK_OAK_LOG))
                .add(key(Items.MANGROVE_LOG))
                .add(key(Items.CHERRY_LOG))
                .add(key(Items.PALE_OAK_LOG))

                // Stripped logs
                .add(key(Items.STRIPPED_OAK_LOG))
                .add(key(Items.STRIPPED_SPRUCE_LOG))
                .add(key(Items.STRIPPED_BIRCH_LOG))
                .add(key(Items.STRIPPED_JUNGLE_LOG))
                .add(key(Items.STRIPPED_ACACIA_LOG))
                .add(key(Items.STRIPPED_DARK_OAK_LOG))
                .add(key(Items.STRIPPED_MANGROVE_LOG))
                .add(key(Items.STRIPPED_CHERRY_LOG))
                .add(key(Items.STRIPPED_PALE_OAK_LOG))

                // Wood
                .add(key(Items.OAK_WOOD))
                .add(key(Items.SPRUCE_WOOD))
                .add(key(Items.BIRCH_WOOD))
                .add(key(Items.JUNGLE_WOOD))
                .add(key(Items.ACACIA_WOOD))
                .add(key(Items.DARK_OAK_WOOD))
                .add(key(Items.MANGROVE_WOOD))
                .add(key(Items.CHERRY_WOOD))
                .add(key(Items.PALE_OAK_WOOD))

                // Stripped wood
                .add(key(Items.STRIPPED_OAK_WOOD))
                .add(key(Items.STRIPPED_SPRUCE_WOOD))
                .add(key(Items.STRIPPED_BIRCH_WOOD))
                .add(key(Items.STRIPPED_JUNGLE_WOOD))
                .add(key(Items.STRIPPED_ACACIA_WOOD))
                .add(key(Items.STRIPPED_DARK_OAK_WOOD))
                .add(key(Items.STRIPPED_MANGROVE_WOOD))
                .add(key(Items.STRIPPED_CHERRY_WOOD))
                .add(key(Items.STRIPPED_PALE_OAK_WOOD));

        // single: leather, copper, chainmail, iron, everything else unless they have charm boost enchantment
        // double: diamond
        // triple: golden, netherite

        builder(SINGLE_CHARM_COMBINABLE_ARMOR_ITEMS)
                .add(key(Items.LEATHER_BOOTS))
                .add(key(Items.LEATHER_LEGGINGS))
                .add(key(Items.LEATHER_CHESTPLATE))
                .add(key(Items.COPPER_BOOTS))
                .add(key(Items.COPPER_LEGGINGS))
                .add(key(Items.COPPER_CHESTPLATE))
                .add(key(Items.CHAINMAIL_BOOTS))
                .add(key(Items.CHAINMAIL_LEGGINGS))
                .add(key(Items.CHAINMAIL_CHESTPLATE))
                .add(key(Items.IRON_BOOTS))
                .add(key(Items.IRON_LEGGINGS))
                .add(key(Items.IRON_CHESTPLATE));
        builder(DOUBLE_CHARM_COMBINABLE_ARMOR_ITEMS)
                .add(key(Items.DIAMOND_BOOTS))
                .add(key(Items.DIAMOND_LEGGINGS))
                .add(key(Items.DIAMOND_CHESTPLATE));
        builder(TRIPLE_CHARM_COMBINABLE_ARMOR_ITEMS)
                .add(key(Items.GOLDEN_BOOTS))
                .add(key(Items.GOLDEN_LEGGINGS))
                .add(key(Items.GOLDEN_CHESTPLATE))
                .add(key(Items.NETHERITE_BOOTS))
                .add(key(Items.NETHERITE_LEGGINGS))
                .add(key(Items.NETHERITE_CHESTPLATE));

        builder(CHARM_COMBINABLE_ARMOR_ITEMS_DIAMOND)
                .add(key(Items.DIAMOND_BOOTS))
                .add(key(Items.DIAMOND_LEGGINGS))
                .add(key(Items.DIAMOND_CHESTPLATE));
        builder(CHARM_COMBINABLE_ARMOR_ITEMS_GOLD)
                .add(key(Items.GOLDEN_BOOTS))
                .add(key(Items.GOLDEN_LEGGINGS))
                .add(key(Items.GOLDEN_CHESTPLATE));
        builder(CHARM_COMBINABLE_ARMOR_ITEMS_IRON)
                .add(key(Items.IRON_BOOTS))
                .add(key(Items.IRON_LEGGINGS))
                .add(key(Items.IRON_CHESTPLATE));
        builder(CHARM_COMBINABLE_ARMOR_ITEMS_CHAINMAIL)
                .add(key(Items.CHAINMAIL_BOOTS))
                .add(key(Items.CHAINMAIL_LEGGINGS))
                .add(key(Items.CHAINMAIL_CHESTPLATE));
        builder(CHARM_COMBINABLE_ARMOR_ITEMS_COPPER)
                .add(key(Items.COPPER_BOOTS))
                .add(key(Items.COPPER_LEGGINGS))
                .add(key(Items.COPPER_CHESTPLATE));
        builder(CHARM_COMBINABLE_ARMOR_ITEMS_LEATHER)
                .add(key(Items.LEATHER_BOOTS))
                .add(key(Items.LEATHER_LEGGINGS))
                .add(key(Items.LEATHER_CHESTPLATE));
        builder(CHARM_COMBINABLE_ARMOR_ITEMS_NETHERITE)
                .add(key(Items.NETHERITE_BOOTS))
                .add(key(Items.NETHERITE_LEGGINGS))
                .add(key(Items.NETHERITE_CHESTPLATE));

        // Cosmetics Stuff

        builder(COSMETIC_COMBINABLE_ARMOR_ITEMS)
                .add(key(Items.DIAMOND_HELMET))
                .add(key(Items.GOLDEN_HELMET))
                .add(key(Items.TURTLE_HELMET))
                .add(key(Items.IRON_HELMET))
                .add(key(Items.CHAINMAIL_HELMET))
                .add(key(Items.COPPER_HELMET))
                .add(key(Items.LEATHER_HELMET))
                .add(key(Items.NETHERITE_HELMET));

        // Random tags that aren't in vanilla

        builder(FISHES)
                .add(key(Items.SALMON))
                .add(key(Items.COD))
                .add(key(Items.TROPICAL_FISH))
                .add(key(Items.PUFFERFISH));
        builder(MUSHROOMS)
                .add(key(Items.BROWN_MUSHROOM))
                .add(key(Items.RED_MUSHROOM));
        builder(FUNGI)
                .add(key(Items.CRIMSON_FUNGUS))
                .add(key(Items.WARPED_FUNGUS));
        builder(FARM_ANIMAL_MEATS_RAW)
                .add(key(Items.MUTTON))
                .add(key(Items.PORKCHOP))
                .add(key(Items.BEEF))
                .add(key(Items.RABBIT))
                .add(key(Items.CHICKEN))
                .add(key(Items.ROTTEN_FLESH));

        builder(CARPETS)
                .add(key(Items.CARPET.black()))
                .add(key(Items.CARPET.cyan()))
                .add(key(Items.CARPET.blue()))
                .add(key(Items.CARPET.gray()))
                .add(key(Items.CARPET.brown()))
                .add(key(Items.CARPET.green()))
                .add(key(Items.CARPET.lightBlue()))
                .add(key(Items.CARPET.lightGray()))
                .add(key(Items.CARPET.lime()))
                .add(key(Items.CARPET.magenta()))
                .add(key(Items.CARPET.orange()))
                .add(key(Items.CARPET.pink()))
                .add(key(Items.CARPET.purple()))
                .add(key(Items.CARPET.red()))
                .add(key(Items.CARPET.white()))
                .add(key(Items.CARPET.yellow()));
    }

    private static ResourceKey<Item> key(Item item)
    {
        return item.builtInRegistryHolder().key();
    }
}
