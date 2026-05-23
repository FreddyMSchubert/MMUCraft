package uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
    private static final List<String> trialChamberRewards = List.of("trial_chambers/reward", "trial_chambers/reward_common", "trial_chambers/reward_ominous", "trial_chambers/reward_ominous_common", "trial_chambers/reward_ominous_rare", "trial_chambers/reward_rare");
    private static final List<String> bastionRewards = List.of("bastion_bridge", "bastion_hoglin_stable", "bastion_other", "bastion_treasure");

    public static final List<EnchantmentSettings> enchantmentSettings = List.of(
        // general enchantments
        new EnchantmentSettings(Enchantments.UNBREAKING).inLoottable("end_city_treasure").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.MENDING).inLoottable("ancient_city", "ancient_city_ice_box").rarity(Rarity.RARE),
        new EnchantmentSettings(ModEnchantments.SOULBOUND), // obtainable via crafting

        // aquatic enchantments
        new EnchantmentSettings(Enchantments.AQUA_AFFINITY).inLoottable("entities/guardian").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.DEPTH_STRIDER).inLoottable("underwater_ruin_big", "underwater_ruin_small").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.RESPIRATION).inLoottable("shipwreck_treasure", "shipwreck_supply", "shipwreck_map").rarity(Rarity.RARE),

        // general armour enchantments
        new EnchantmentSettings(Enchantments.FEATHER_FALLING).inLoottable("desert_pyramid").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(Enchantments.THORNS).inLoottable("simple_dungeon"),
        new EnchantmentSettings(ModEnchantments.CHARM_BOOST), // TODO: obtainable via archaeology loottables
        new EnchantmentSettings(Enchantments.PROTECTION).inLoottable("end_city_treasure").rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.BLAST_PROTECTION).maxEnchTableLvl(1),
        new EnchantmentSettings(Enchantments.FIRE_PROTECTION).maxEnchTableLvl(1),
        new EnchantmentSettings(Enchantments.PROJECTILE_PROTECTION).maxEnchTableLvl(1),

        // mace enchantments
        new EnchantmentSettings(Enchantments.BREACH).inLoottable(trialChamberRewards).rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.DENSITY).inLoottable(trialChamberRewards).rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.WIND_BURST).inLoottable(trialChamberRewards).rarity(Rarity.EPIC),

        // trident enchantments
        new EnchantmentSettings(Enchantments.CHANNELING).inLoottable("zombie_nautilus"),
        new EnchantmentSettings(Enchantments.IMPALING).inLoottable("zombie_nautilus"),
        new EnchantmentSettings(Enchantments.LOYALTY).inLoottable("entities/drowned").rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.RIPTIDE).inLoottable("entities/drowned").rarity(Rarity.EPIC),

        // tool enchantments
        new EnchantmentSettings(Enchantments.EFFICIENCY).inLoottable("stronghold_corridor", "stronghold_crossing", "stronghold_library").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.SILK_TOUCH).inLoottable("abandoned_mineshaft").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.FORTUNE), // TODO: obtained by crafting

        // sword & spear enchantments
        new EnchantmentSettings(Enchantments.LOOTING).inLoottable("nether_bridge").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.FIRE_ASPECT).inLoottable("ruined_portal").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(Enchantments.KNOCKBACK).inLoottable("woodland_mansion").rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.SHARPNESS).maxEnchTableLvl(1).rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.SWEEPING_EDGE).inLoottable("woodland_mansion"),
        new EnchantmentSettings(Enchantments.SMITE).maxEnchTableLvl(1),
        new EnchantmentSettings(Enchantments.BANE_OF_ARTHROPODS).maxEnchTableLvl(1),
        new EnchantmentSettings(Enchantments.LUNGE).inLoottable("desert_temple").rarity(Rarity.RARE),

        // boot enchantments
        new EnchantmentSettings(Enchantments.FROST_WALKER).inLoottable("igloo_chest"),
        new EnchantmentSettings(Enchantments.SOUL_SPEED).inLoottable(bastionRewards).rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.SWIFT_SNEAK).inLoottable("ancient_city", "ancient_city_ice_box").rarity(Rarity.EPIC),

        // bow enchantments
        new EnchantmentSettings(Enchantments.INFINITY).inLoottable("jungle_temple").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(Enchantments.POWER).inLoottable(bastionRewards).rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.PUNCH).inLoottable("jungle_temple").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(Enchantments.FLAME).inLoottable("ruined_portal").rarity(Rarity.UNCOMMON),

        // fishing enchantments
        new EnchantmentSettings(Enchantments.LUCK_OF_THE_SEA).inLoottable("buried_treasure"),
        new EnchantmentSettings(Enchantments.LURE), // TODO: From fishing

        // crossbow enchantments
        new EnchantmentSettings(Enchantments.MULTISHOT).inLoottable("pillager_outpost"),
        new EnchantmentSettings(Enchantments.PIERCING).inLoottable("pillager_outpost"),
        new EnchantmentSettings(Enchantments.QUICK_CHARGE).inLoottable("pillager_outpost"),

        // curses
        new EnchantmentSettings(Enchantments.VANISHING_CURSE).maxEnchTableLvl(1),
        new EnchantmentSettings(Enchantments.BINDING_CURSE).maxEnchTableLvl(1),
        new EnchantmentSettings(ModEnchantments.FRAGILITY_CURSE).maxEnchTableLvl(1)
    );

    public static final Map<Identifier, List<EnchantmentSettings>> byLoottable = buildByLoottable();
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
}
