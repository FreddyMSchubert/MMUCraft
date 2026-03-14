package uk.co.httpsmmuminecraftsociety.mainmod.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends FabricTagProvider.ItemTagProvider
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
    public static final TagKey<Item> FUNGI = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "fungy"));
    public static final TagKey<Item> FARM_ANIMAL_MEATS_RAW = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "farm_animal_meats_raw"));

    public ModItemTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture)
    {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider)
    {
        valueLookupBuilder(ItemTags.DYEABLE)
                .add(Items.CARVED_PUMPKIN);

        valueLookupBuilder(CHARM_COMBINABLE_ARMOR_ITEMS)
                .add(Items.LEATHER_BOOTS)
                .add(Items.LEATHER_LEGGINGS)
                .add(Items.LEATHER_CHESTPLATE)
                .add(Items.COPPER_BOOTS)
                .add(Items.COPPER_LEGGINGS)
                .add(Items.COPPER_CHESTPLATE)
                .add(Items.GOLDEN_BOOTS)
                .add(Items.GOLDEN_LEGGINGS)
                .add(Items.GOLDEN_CHESTPLATE)
                .add(Items.CHAINMAIL_BOOTS)
                .add(Items.CHAINMAIL_LEGGINGS)
                .add(Items.CHAINMAIL_CHESTPLATE)
                .add(Items.IRON_BOOTS)
                .add(Items.IRON_LEGGINGS)
                .add(Items.IRON_CHESTPLATE)
                .add(Items.DIAMOND_BOOTS)
                .add(Items.DIAMOND_LEGGINGS)
                .add(Items.DIAMOND_CHESTPLATE)
                .add(Items.NETHERITE_BOOTS)
                .add(Items.NETHERITE_LEGGINGS)
                .add(Items.NETHERITE_CHESTPLATE);

        // single: leather, copper, chainmail, iron, everything else unless they have charm boost enchantment
        // double: diamond
        // triple: golden, netherite

        valueLookupBuilder(SINGLE_CHARM_COMBINABLE_ARMOR_ITEMS)
                .add(Items.LEATHER_BOOTS)
                .add(Items.LEATHER_LEGGINGS)
                .add(Items.LEATHER_CHESTPLATE)
                .add(Items.COPPER_BOOTS)
                .add(Items.COPPER_LEGGINGS)
                .add(Items.COPPER_CHESTPLATE)
                .add(Items.CHAINMAIL_BOOTS)
                .add(Items.CHAINMAIL_LEGGINGS)
                .add(Items.CHAINMAIL_CHESTPLATE)
                .add(Items.IRON_BOOTS)
                .add(Items.IRON_LEGGINGS)
                .add(Items.IRON_CHESTPLATE);
        valueLookupBuilder(DOUBLE_CHARM_COMBINABLE_ARMOR_ITEMS)
                .add(Items.DIAMOND_BOOTS)
                .add(Items.DIAMOND_LEGGINGS)
                .add(Items.DIAMOND_CHESTPLATE);
        valueLookupBuilder(TRIPLE_CHARM_COMBINABLE_ARMOR_ITEMS)
                .add(Items.GOLDEN_BOOTS)
                .add(Items.GOLDEN_LEGGINGS)
                .add(Items.GOLDEN_CHESTPLATE)
                .add(Items.NETHERITE_BOOTS)
                .add(Items.NETHERITE_LEGGINGS)
                .add(Items.NETHERITE_CHESTPLATE);

        valueLookupBuilder(CHARM_COMBINABLE_ARMOR_ITEMS_DIAMOND)
                .add(Items.DIAMOND_BOOTS)
                .add(Items.DIAMOND_LEGGINGS)
                .add(Items.DIAMOND_CHESTPLATE);
        valueLookupBuilder(CHARM_COMBINABLE_ARMOR_ITEMS_GOLD)
                .add(Items.GOLDEN_BOOTS)
                .add(Items.GOLDEN_LEGGINGS)
                .add(Items.GOLDEN_CHESTPLATE);
        valueLookupBuilder(CHARM_COMBINABLE_ARMOR_ITEMS_IRON)
                .add(Items.IRON_BOOTS)
                .add(Items.IRON_LEGGINGS)
                .add(Items.IRON_CHESTPLATE);
        valueLookupBuilder(CHARM_COMBINABLE_ARMOR_ITEMS_CHAINMAIL)
                .add(Items.CHAINMAIL_BOOTS)
                .add(Items.CHAINMAIL_LEGGINGS)
                .add(Items.CHAINMAIL_CHESTPLATE);
        valueLookupBuilder(CHARM_COMBINABLE_ARMOR_ITEMS_COPPER)
                .add(Items.COPPER_BOOTS)
                .add(Items.COPPER_LEGGINGS)
                .add(Items.COPPER_CHESTPLATE);
        valueLookupBuilder(CHARM_COMBINABLE_ARMOR_ITEMS_LEATHER)
                .add(Items.LEATHER_BOOTS)
                .add(Items.LEATHER_LEGGINGS)
                .add(Items.LEATHER_CHESTPLATE);
        valueLookupBuilder(CHARM_COMBINABLE_ARMOR_ITEMS_NETHERITE)
                .add(Items.NETHERITE_BOOTS)
                .add(Items.NETHERITE_LEGGINGS)
                .add(Items.NETHERITE_CHESTPLATE);


        valueLookupBuilder(COSMETIC_COMBINABLE_ARMOR_ITEMS)
                .add(Items.DIAMOND_HELMET)
                .add(Items.GOLDEN_HELMET)
                .add(Items.TURTLE_HELMET)
                .add(Items.IRON_HELMET)
                .add(Items.CHAINMAIL_HELMET)
                .add(Items.COPPER_HELMET)
                .add(Items.LEATHER_HELMET)
                .add(Items.NETHERITE_HELMET);

        valueLookupBuilder(FISHES)
                .add(Items.SALMON)
                .add(Items.COD)
                .add(Items.TROPICAL_FISH)
                .add(Items.PUFFERFISH);
        valueLookupBuilder(MUSHROOMS)
                .add(Items.BROWN_MUSHROOM)
                .add(Items.RED_MUSHROOM);
        valueLookupBuilder(FUNGI)
                .add(Items.CRIMSON_FUNGUS)
                .add(Items.WARPED_FUNGUS);
        valueLookupBuilder(FARM_ANIMAL_MEATS_RAW)
                .add(Items.MUTTON)
                .add(Items.PORKCHOP)
                .add(Items.BEEF)
                .add(Items.RABBIT)
                .add(Items.CHICKEN)
                .add(Items.ROTTEN_FLESH);
    }
}
