package uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;

import java.util.*;
import java.util.stream.Collectors;

/*
RARITIES GUIDE
Common - Every single one.
Uncommon - 50 %
Rare - 25 %
Epic - 12.5 %
 */

public class EnchantmentSettingsManager
{
    private static final List<String> trialChamberRewards = List.of("chests/trial_chambers/reward", "chests/trial_chambers/reward_common", "chests/trial_chambers/reward_ominous", "chests/trial_chambers/reward_ominous_common", "chests/trial_chambers/reward_ominous_rare", "chests/trial_chambers/reward_rare");
    private static final List<String> bastionRewards = List.of("chests/bastion_bridge", "chests/bastion_hoglin_stable", "chests/bastion_other", "chests/bastion_treasure");
    public static final List<ResourceKey<Enchantment>> curses = List.of(
        Enchantments.VANISHING_CURSE,
        Enchantments.BINDING_CURSE,
        ModEnchantments.FRAGILITY_CURSE
    );

    public static final List<EnchantmentSettings> enchantmentSettings = List.of(
        // general enchantments
        new EnchantmentSettings(Enchantments.UNBREAKING).inLoottable("chests/end_city_treasure").rarity(Rarity.RARE).dupedWithVanillaItem(Items.TOTEM_OF_UNDYING),
        new EnchantmentSettings(Enchantments.MENDING).inLoottable("chests/ancient_city", "chests/ancient_city_ice_box").rarity(Rarity.RARE).dupedWithVanillaItem(Items.EXPERIENCE_BOTTLE),
        new EnchantmentSettings(ModEnchantments.SOULBOUND).dupedWithFakeItem("soul"), // obtainable via crafting

        // aquatic enchantments
        new EnchantmentSettings(Enchantments.AQUA_AFFINITY).inLoottable("entities/guardian").rarity(Rarity.RARE).dupedWithVanillaItem(Items.SEA_LANTERN),
        new EnchantmentSettings(Enchantments.DEPTH_STRIDER).inLoottable("chests/underwater_ruin_big", "chests/underwater_ruin_small").rarity(Rarity.RARE).dupedWithVanillaItem(Items.PRISMARINE_SHARD, 3),
        new EnchantmentSettings(Enchantments.RESPIRATION).inLoottable("chests/shipwreck_treasure", "chests/shipwreck_supply", "chests/shipwreck_map").rarity(Rarity.RARE).dupedWithVanillaItem(Items.TURTLE_HELMET),

        // general armour enchantments
        new EnchantmentSettings(Enchantments.FEATHER_FALLING).inLoottable("chests/desert_pyramid").rarity(Rarity.UNCOMMON), // TODO: dupeability
        new EnchantmentSettings(Enchantments.THORNS).inLoottable("chests/simple_dungeon").dupedWithVanillaItem(Items.CACTUS_FLOWER, 2),
        new EnchantmentSettings(ModEnchantments.CHARM_BOOST).dupedWithVanillaItem(Items.RAW_GOLD_BLOCK), // TODO: obtainable via archaeology loottables
        new EnchantmentSettings(Enchantments.PROTECTION).inLoottable("chests/end_city_treasure").rarity(Rarity.EPIC).dupedWithVanillaItem(Items.ARMADILLO_SCUTE, 5),
        new EnchantmentSettings(Enchantments.BLAST_PROTECTION).maxEnchTableLvl(1).dupedWithVanillaItem(Items.ARMADILLO_SCUTE, 3),
        new EnchantmentSettings(Enchantments.FIRE_PROTECTION).maxEnchTableLvl(1).dupedWithVanillaItem(Items.ARMADILLO_SCUTE, 3),
        new EnchantmentSettings(Enchantments.PROJECTILE_PROTECTION).maxEnchTableLvl(1).dupedWithVanillaItem(Items.ARMADILLO_SCUTE, 3),

        // mace enchantments
        new EnchantmentSettings(Enchantments.BREACH).inLoottable(trialChamberRewards).rarity(Rarity.EPIC).dupedWithVanillaItem(Items.POINTED_DRIPSTONE, 10),
        new EnchantmentSettings(Enchantments.DENSITY).inLoottable(trialChamberRewards).rarity(Rarity.EPIC).dupedWithVanillaItem(Items.ANVIL),
        new EnchantmentSettings(Enchantments.WIND_BURST).inLoottable(trialChamberRewards).rarity(Rarity.EPIC).dupedWithVanillaItem(Items.BREEZE_ROD, 3),

        // trident enchantments
        // TODO: maybe swap these with shipwreck stuff? give each to one shipwreck chest and put the two incompatible ones into the same chest... but then where to put respiration lol? i just dislike entities dropping books
        new EnchantmentSettings(Enchantments.CHANNELING).inLoottable("entities/zombie_nautilus").dupedWithVanillaItem(Items.WAXED_OXIDIZED_LIGHTNING_ROD),
        new EnchantmentSettings(Enchantments.IMPALING).inLoottable("entities/zombie_nautilus").dupedWithVanillaItem(Items.NAUTILUS_SHELL),
        new EnchantmentSettings(Enchantments.LOYALTY).inLoottable("entities/drowned").rarity(Rarity.EPIC), // TODO: duped by dog collar from killing a tamed dog
        new EnchantmentSettings(Enchantments.RIPTIDE).inLoottable("entities/drowned").rarity(Rarity.EPIC).dupedWithVanillaItem(Items.PHANTOM_MEMBRANE, 3),

        // tool enchantments
        new EnchantmentSettings(Enchantments.EFFICIENCY).inLoottable("chests/stronghold_corridor", "chests/stronghold_crossing", "chests/stronghold_library").rarity(Rarity.RARE).dupedWithVanillaItem(Items.TNT),
        new EnchantmentSettings(Enchantments.SILK_TOUCH).inLoottable("chests/abandoned_mineshaft").rarity(Rarity.RARE).dupedWithVanillaItem(Items.BRAIN_CORAL_BLOCK),
        new EnchantmentSettings(Enchantments.FORTUNE).dupedWithVanillaItem(Items.NETHERITE_SCRAP), // TODO: obtained by crafting

        // sword & spear enchantments
        new EnchantmentSettings(Enchantments.LOOTING).inLoottable("chests/nether_bridge").rarity(Rarity.RARE), // TODO: deupeability
        new EnchantmentSettings(Enchantments.FIRE_ASPECT).inLoottable("chests/ruined_portal").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.FIRE_CHARGE),
        new EnchantmentSettings(Enchantments.KNOCKBACK).inLoottable("chests/woodland_mansion").rarity(Rarity.EPIC).dupedWithVanillaItem(Items.SLIME_BLOCK),
        new EnchantmentSettings(Enchantments.SHARPNESS).maxEnchTableLvl(1).rarity(Rarity.EPIC), // TODO: dupeability
        new EnchantmentSettings(Enchantments.SWEEPING_EDGE).inLoottable("chests/woodland_mansion"), // TODO: dupeability
        new EnchantmentSettings(Enchantments.SMITE).maxEnchTableLvl(1).dupedWithVanillaItem(Items.NETHER_STAR),
        new EnchantmentSettings(Enchantments.BANE_OF_ARTHROPODS).maxEnchTableLvl(1).dupedWithVanillaItem(Items.FERMENTED_SPIDER_EYE, 5),
        new EnchantmentSettings(Enchantments.LUNGE).inLoottable("chests/desert_pyramid").rarity(Rarity.RARE), // TODO: dupeability

        // boot enchantments
        new EnchantmentSettings(Enchantments.FROST_WALKER).inLoottable("chests/igloo_chest").dupedWithVanillaItem(Items.BLUE_ICE, 5),
        new EnchantmentSettings(Enchantments.SOUL_SPEED).inLoottable(bastionRewards).rarity(Rarity.RARE).dupedWithVanillaItem(Items.GILDED_BLACKSTONE, 3),
        new EnchantmentSettings(Enchantments.SWIFT_SNEAK).inLoottable("chests/ancient_city", "chests/ancient_city_ice_box").rarity(Rarity.EPIC).dupedWithVanillaItem(Items.SCULK_SENSOR),

        // bow enchantments
        new EnchantmentSettings(Enchantments.INFINITY).inLoottable("chests/jungle_temple").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
        new EnchantmentSettings(Enchantments.POWER).inLoottable(bastionRewards).rarity(Rarity.RARE), // TODO: dupeability
        new EnchantmentSettings(Enchantments.PUNCH).inLoottable("chests/jungle_temple").rarity(Rarity.UNCOMMON), // TODO: dupeability
        new EnchantmentSettings(Enchantments.FLAME).inLoottable("chests/ruined_portal").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.MAGMA_CREAM),

        // fishing enchantments
        new EnchantmentSettings(Enchantments.LUCK_OF_THE_SEA).inLoottable("chests/buried_treasure"), // TODO: dupeability
        new EnchantmentSettings(Enchantments.LURE), // TODO: From fishing // TODO: dupeability

        // crossbow enchantments
        new EnchantmentSettings(Enchantments.MULTISHOT).inLoottable("chests/pillager_outpost"), // TODO: dupeability
        new EnchantmentSettings(Enchantments.PIERCING).inLoottable("chests/pillager_outpost"), // TODO: dupeability
        new EnchantmentSettings(Enchantments.QUICK_CHARGE).inLoottable("chests/pillager_outpost").dupedWithVanillaItem(Items.FIREWORK_STAR),

        // curses
        new EnchantmentSettings(Enchantments.VANISHING_CURSE).maxEnchTableLvl(1).dupedWithVanillaItem(Items.CLOSED_EYEBLOSSOM),
        new EnchantmentSettings(Enchantments.BINDING_CURSE).maxEnchTableLvl(1).dupedWithVanillaItem(Items.ALLIUM),
        new EnchantmentSettings(ModEnchantments.FRAGILITY_CURSE).maxEnchTableLvl(1).dupedWithVanillaItem(Items.BLUE_ORCHID)
    );

    public static Map<Identifier, List<EnchantmentSettings>> byLoottable = buildByLoottable();
    private static Map<Identifier, List<EnchantmentSettings>> buildByLoottable()
    {
        Map<Identifier, List<EnchantmentSettings>> map = new HashMap<>();

        for (EnchantmentSettings settings : enchantmentSettings)
            for (Identifier loottable : settings.foundInLoottables)
                map.computeIfAbsent(loottable, key -> new ArrayList<>())
                        .add(settings);

        return Map.copyOf(map);
    }

    public static final Set<ResourceKey<Enchantment>> enchantingTableAllowed =
            enchantmentSettings.stream()
                    .filter(settings -> settings.maxEnchantingTableLevel > 0)
                    .map(settings -> settings.enchantment)
                    .collect(Collectors.toUnmodifiableSet());

    public static boolean isAllowedFromEnchantingTable(Holder<Enchantment> enchantment)
    {
        return enchantingTableAllowed.stream().anyMatch(enchantment::is);
    }

    public static Optional<EnchantmentSettings> getSettingsForEnch(Holder<Enchantment> enchantment) {
        return enchantmentSettings.stream()
                .filter(settings -> enchantment.is(settings.enchantment))
                .findFirst();
    }

    public static void validateLoottables(HolderLookup.Provider registries) {
        List<String> invalidLoottables = new ArrayList<>();

        for (EnchantmentSettings settings : enchantmentSettings) {
            List<Identifier> invalidForEnchantment = settings.validateLoottables(registries);

            if (!invalidForEnchantment.isEmpty()) {
                invalidLoottables.add(settings.enchantment.identifier() + ": " + invalidForEnchantment);
            }
        }

        byLoottable = buildByLoottable();

        if (!invalidLoottables.isEmpty()) {
            throw new IllegalStateException(
                "Enchantment settings reference unknown loot table(s): " + invalidLoottables
            );
        }
    }
}
