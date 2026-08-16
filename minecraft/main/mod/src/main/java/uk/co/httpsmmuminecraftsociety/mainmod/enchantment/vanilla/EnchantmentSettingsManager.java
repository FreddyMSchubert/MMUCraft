package uk.co.httpsmmuminecraftsociety.mainmod.enchantment.vanilla;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.ModEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;

import java.util.*;

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
    private static final List<String> archaeologyRewards = List.of("archaeology/desert_pyramid", "archaeology/desert_well", "archaeology/ocean_ruin_cold", "archaeology/ocean_ruin_warm", "archaeology/trail_ruins_common", "archaeology/trail_ruins_rare");

    public static final List<EnchantmentSettings> enchantmentSettings = List.of(
        // general enchantments
        new EnchantmentSettings(Enchantments.UNBREAKING).maxLevels(3, 5).inLoottable("chests/end_city_treasure").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.MENDING).maxLevels(1, 1).inLoottable("chests/ancient_city", "chests/ancient_city_ice_box").rarity(Rarity.RARE),
        new EnchantmentSettings(ModEnchantments.SOULBOUND).maxLevels(1, 1), // obtainable via crafting

        // aquatic enchantments
        new EnchantmentSettings(Enchantments.AQUA_AFFINITY).maxLevels(1, 1).inLoottable("entities/guardian").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.DEPTH_STRIDER).maxLevels(3, 4).inLoottable("entities/drowned").rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.RESPIRATION).maxLevels(3, 10).inLoottable("entities/zombie_nautilus").rarity(Rarity.RARE),

        // general armor enchantments
        new EnchantmentSettings(Enchantments.FEATHER_FALLING).maxLevels(3, 68).inLoottable("chests/desert_pyramid").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(Enchantments.THORNS).maxLevels(3, 5).inLoottable("chests/simple_dungeon").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(ModEnchantments.CHARM_BOOST).maxLevels(1, 1).inLoottable(archaeologyRewards).rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.PROTECTION).maxLevels(2, 4).inLoottable("chests/end_city_treasure").rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.BLAST_PROTECTION).maxLevels(3, 5),
        new EnchantmentSettings(Enchantments.FIRE_PROTECTION).maxLevels(3, 5),
        new EnchantmentSettings(Enchantments.PROJECTILE_PROTECTION).maxLevels(3, 5),

        // mace enchantments
        new EnchantmentSettings(Enchantments.BREACH).maxLevels(4, 4).inLoottable(trialChamberRewards).rarity(Rarity.COMMON),
        new EnchantmentSettings(Enchantments.DENSITY).maxLevels(5, 5).inLoottable(trialChamberRewards).rarity(Rarity.COMMON),
        new EnchantmentSettings(Enchantments.WIND_BURST).maxLevels(3, 3).inLoottable(trialChamberRewards).rarity(Rarity.RARE),

        // trident enchantments
        new EnchantmentSettings(Enchantments.CHANNELING).maxLevels(1, 1).inLoottable("chests/shipwreck_supply"),
        new EnchantmentSettings(Enchantments.IMPALING).maxLevels(5, 5).inLoottable("chests/shipwreck_treasure"),
        new EnchantmentSettings(Enchantments.LOYALTY).maxLevels(3, 3).inLoottable("chests/shipwreck_treasure").rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.RIPTIDE).maxLevels(3, 3).inLoottable("chests/shipwreck_map").rarity(Rarity.EPIC),

        // tool enchantments
        new EnchantmentSettings(Enchantments.EFFICIENCY).maxLevels(5, 8).inLoottable("chests/stronghold_corridor", "chests/stronghold_crossing", "chests/stronghold_library").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.SILK_TOUCH).maxLevels(1, 1).inLoottable("chests/abandoned_mineshaft").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.FORTUNE).maxLevels(3, 4), // obtainable via crafting

        // sword & spear enchantments
        new EnchantmentSettings(Enchantments.LOOTING).maxLevels(3, 5).inLoottable("chests/nether_bridge").rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.FIRE_ASPECT).maxLevels(1, 2).inLoottable("chests/ruined_portal").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(Enchantments.KNOCKBACK).maxLevels(2, 3).inLoottable("chests/woodland_mansion").rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.SHARPNESS).maxLevels(5, 11).rarity(Rarity.EPIC),
        new EnchantmentSettings(Enchantments.SWEEPING_EDGE).maxLevels(3, 5).inLoottable("chests/woodland_mansion"),
        new EnchantmentSettings(Enchantments.SMITE).maxLevels(4, 6),
        new EnchantmentSettings(Enchantments.BANE_OF_ARTHROPODS).maxLevels(100, 100),
        new EnchantmentSettings(Enchantments.LUNGE).maxLevels(3, 5).inLoottable("chests/desert_pyramid").rarity(Rarity.RARE),

        // boot enchantments
        new EnchantmentSettings(Enchantments.FROST_WALKER).maxLevels(2, 3).inLoottable("chests/igloo_chest"),
        new EnchantmentSettings(Enchantments.SOUL_SPEED).maxLevels(2, 3).inLoottable(bastionRewards).rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.SWIFT_SNEAK).maxLevels(3, 4).inLoottable("chests/ancient_city", "chests/ancient_city_ice_box").rarity(Rarity.EPIC),

        // bow enchantments
        new EnchantmentSettings(Enchantments.INFINITY).maxLevels(1, 1).inLoottable("chests/jungle_temple").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(Enchantments.POWER).maxLevels(5, 5).inLoottable(bastionRewards).rarity(Rarity.RARE),
        new EnchantmentSettings(Enchantments.PUNCH).maxLevels(3, 3).inLoottable("chests/jungle_temple").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(Enchantments.FLAME).maxLevels(1, 1).inLoottable("chests/ruined_portal").rarity(Rarity.UNCOMMON),

        // fishing enchantments
        new EnchantmentSettings(Enchantments.LUCK_OF_THE_SEA).maxLevels(3, 3).inLoottable("chests/buried_treasure"),
        new EnchantmentSettings(Enchantments.LURE).maxLevels(3, 3), // obtainable via fishing

        // crossbow enchantments
        new EnchantmentSettings(Enchantments.MULTISHOT).maxLevels(1, 1).inLoottable("chests/pillager_outpost").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(Enchantments.PIERCING).maxLevels(4, 4).inLoottable("chests/pillager_outpost").rarity(Rarity.UNCOMMON),
        new EnchantmentSettings(Enchantments.QUICK_CHARGE).maxLevels(3, 3).inLoottable("chests/pillager_outpost").rarity(Rarity.UNCOMMON),

        // curses
        new EnchantmentSettings(Enchantments.VANISHING_CURSE).maxLevels(1, 1),
        new EnchantmentSettings(Enchantments.BINDING_CURSE).maxLevels(1, 1)
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

    public static Optional<EnchantmentSettings> getSettingsForEnch(Holder<Enchantment> enchantment) {
        return enchantmentSettings.stream()
                .filter(settings -> enchantment.is(settings.enchantment))
                .findFirst();
    }

    public static int getMaxAnvilLevel(Holder<Enchantment> enchantment, ItemStack stack) {
        return getSettingsForEnch(enchantment)
                .map(settings -> stack.is(Items.ENCHANTED_BOOK) || CharmorManager.isEnderite(stack)
                        ? settings.maxEnderiteLevel
                        : settings.maxNormalGearLevel)
                .orElse(0);
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
