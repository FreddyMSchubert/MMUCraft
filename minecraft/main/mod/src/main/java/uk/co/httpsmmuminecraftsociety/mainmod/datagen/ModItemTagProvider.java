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

    public static final TagKey<Item> FISHES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fishes"));
    public static final TagKey<Item> MUSHROOMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "mushrooms"));
    public static final TagKey<Item> FUNGI = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fungi"));
    public static final TagKey<Item> FARM_ANIMAL_MEATS_RAW = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "farm_animal_meats_raw"));

    public static final TagKey<Item> CARPETS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "carpets"));

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
