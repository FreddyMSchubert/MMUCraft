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

public class EnchantmentTypeManager
{
    private static final List<String> trialChamberRewards = List.of("trial_chambers/reward", "trial_chambers/reward_common", "trial_chambers/reward_ominous", "trial_chambers/reward_ominous_common", "trial_chambers/reward_ominous_rare", "trial_chambers/reward_rare");
    private static final List<String> bastionRewards = List.of("bastion_bridge", "bastion_hoglin_stable", "bastion_other", "bastion_treasure");

    public static final List<EnchantmentType> enchantmentTypes = List.of(
            new EnchantmentType(Enchantments.AQUA_AFFINITY, false, List.of("entities/guardian"), Rarity.RARE),
            new EnchantmentType(Enchantments.DEPTH_STRIDER, false, List.of("underwater_ruin_big", "underwater_ruin_small"), Rarity.RARE),
            new EnchantmentType(Enchantments.RESPIRATION, false, List.of("shipwreck_treasure", "shipwreck_supply", "shipwreck_map"), Rarity.RARE),

            new EnchantmentType(Enchantments.BANE_OF_ARTHROPODS, true, List.of(), Rarity.COMMON),

            new EnchantmentType(Enchantments.BREACH, false, trialChamberRewards, Rarity.EPIC),
            new EnchantmentType(Enchantments.DENSITY, false, trialChamberRewards, Rarity.EPIC),
            new EnchantmentType(Enchantments.WIND_BURST, false, trialChamberRewards, Rarity.EPIC),

            new EnchantmentType(Enchantments.BLAST_PROTECTION, true, List.of(), Rarity.COMMON),
            new EnchantmentType(Enchantments.FIRE_PROTECTION, true, List.of(), Rarity.COMMON),
            new EnchantmentType(Enchantments.PROJECTILE_PROTECTION, true, List.of(), Rarity.COMMON),

            new EnchantmentType(Enchantments.CHANNELING, false, List.of("zombie_nautilus"), Rarity.COMMON),
            new EnchantmentType(Enchantments.IMPALING, false, List.of("zombie_nautilus"), Rarity.COMMON),
            // -
            new EnchantmentType(Enchantments.LOYALTY, false, List.of("entities/drowned"), Rarity.EPIC),
            new EnchantmentType(Enchantments.RIPTIDE, false, List.of("entities/drowned"), Rarity.EPIC),

            new EnchantmentType(Enchantments.EFFICIENCY, false, List.of("stronghold_corridor", "stronghold_crossing", "stronghold_library"), Rarity.RARE),
            new EnchantmentType(Enchantments.MENDING, false, List.of("ancient_city", "ancient_city_ice_box"), Rarity.RARE),
            new EnchantmentType(Enchantments.PROTECTION, false, List.of("end_city_treasure"), Rarity.EPIC),
            new EnchantmentType(Enchantments.SILK_TOUCH, false, List.of("abandoned_mineshaft"), Rarity.RARE),
            new EnchantmentType(Enchantments.UNBREAKING, false, List.of(), Rarity.COMMON),

            new EnchantmentType(Enchantments.FEATHER_FALLING, false, List.of("desert_pyramid"), Rarity.UNCOMMON),

            new EnchantmentType(Enchantments.FIRE_ASPECT, false, List.of("ruined_portal"), Rarity.UNCOMMON),
            new EnchantmentType(Enchantments.KNOCKBACK, false, List.of("woodland_mansion"), Rarity.EPIC),
            new EnchantmentType(Enchantments.SHARPNESS, true, List.of(), Rarity.EPIC),
            new EnchantmentType(Enchantments.SWEEPING_EDGE, false, List.of("woodland_mansion"), Rarity.COMMON),
            new EnchantmentType(Enchantments.SMITE, true, List.of(), Rarity.COMMON),

            new EnchantmentType(Enchantments.FORTUNE, false, List.of(), Rarity.COMMON), // TODO: obtained by crafting

            new EnchantmentType(Enchantments.FROST_WALKER, false, List.of("igloo_chest"), Rarity.COMMON),
            new EnchantmentType(Enchantments.SOUL_SPEED, false, bastionRewards, Rarity.RARE),
            new EnchantmentType(Enchantments.SWIFT_SNEAK, false, List.of("ancient_city", "ancient_city_ice_box"), Rarity.EPIC),

            new EnchantmentType(Enchantments.INFINITY, false, List.of("jungle_temple"), Rarity.UNCOMMON),
            new EnchantmentType(Enchantments.POWER, false, bastionRewards, Rarity.RARE),
            new EnchantmentType(Enchantments.PUNCH, false, List.of("jungle_temple"), Rarity.UNCOMMON),
            new EnchantmentType(Enchantments.FLAME, false, List.of("ruined_portal"), Rarity.UNCOMMON),

            new EnchantmentType(Enchantments.LOOTING, false, List.of("nether_bridge"), Rarity.RARE),

            new EnchantmentType(Enchantments.LUCK_OF_THE_SEA, false, List.of("buried_treasure"), Rarity.COMMON),
            new EnchantmentType(Enchantments.LURE, false, List.of(), Rarity.COMMON), // TODO: From fishing

            new EnchantmentType(Enchantments.LUNGE, false, List.of("desert_temple"), Rarity.RARE),

            new EnchantmentType(Enchantments.MULTISHOT, false, List.of("pillager_outpost"), Rarity.COMMON),
            new EnchantmentType(Enchantments.PIERCING, false, List.of("pillager_outpost"), Rarity.COMMON),
            new EnchantmentType(Enchantments.QUICK_CHARGE, false, List.of("pillager_outpost"), Rarity.COMMON),

            new EnchantmentType(Enchantments.THORNS, false, List.of("simple_dungeon"), Rarity.COMMON),

            new EnchantmentType(Enchantments.VANISHING_CURSE, true, List.of(), Rarity.COMMON),
            new EnchantmentType(Enchantments.BINDING_CURSE, true, List.of(), Rarity.COMMON),
            new EnchantmentType(ModEnchantments.FRAGILITY_CURSE, true, List.of(), Rarity.COMMON)
    );

    public static final Map<Identifier, List<EnchantmentType>> byLoottable = buildByLoottable();
    private static Map<Identifier, List<EnchantmentType>> buildByLoottable()
    {
        Map<Identifier, List<EnchantmentType>> map = new HashMap<>();

        for (EnchantmentType enchantmentType : enchantmentTypes)
            for (Identifier loottable : enchantmentType.foundInLoottables)
                map.computeIfAbsent(loottable, key -> new ArrayList<>())
                        .add(enchantmentType);

        return Map.copyOf(map);
    }

    public static final Set<ResourceKey<Enchantment>> enchantingTableAllowed =
            enchantmentTypes.stream()
                    .filter(type -> type.defaultEnchantingTableAvailable)
                    .map(type -> type.enchantment)
                    .collect(Collectors.toUnmodifiableSet());

    public static boolean isAllowedFromEnchantingTable(Holder<Enchantment> enchantment)
    {
        return enchantingTableAllowed.stream().anyMatch(enchantment::is);
    }
}
