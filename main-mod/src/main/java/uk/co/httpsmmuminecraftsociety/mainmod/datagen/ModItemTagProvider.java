package uk.co.httpsmmuminecraftsociety.mainmod.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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

    public ModItemTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture)
    {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider)
    {
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
    }
}
