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

    public static final List<EnchantmentSettings> enchantmentSettings = List.of(
        // general enchantments
        new EnchantmentSettings(Enchantments.UNBREAKING).maxLevels(2, 3, 5).inLoottable("chests/end_city_treasure").rarity(Rarity.RARE).dupedWithVanillaItem(Items.TOTEM_OF_UNDYING),
        new EnchantmentSettings(Enchantments.MENDING).maxLevels(-1, 1, 1).inLoottable("chests/ancient_city", "chests/ancient_city_ice_box").rarity(Rarity.RARE).dupedWithVanillaItem(Items.EXPERIENCE_BOTTLE),
        new EnchantmentSettings(ModEnchantments.SOULBOUND).maxLevels(-1, 1, 1).dupedWithFakeItem("soul"), // obtainable via crafting

        // aquatic enchantments
        new EnchantmentSettings(Enchantments.AQUA_AFFINITY).maxLevels(-1, 1, 1).inLoottable("entities/guardian").rarity(Rarity.RARE).dupedWithVanillaItem(Items.SEA_LANTERN),
        new EnchantmentSettings(Enchantments.DEPTH_STRIDER).maxLevels(2, 3, 4).inLoottable("entities/drowned").rarity(Rarity.EPIC).dupedWithVanillaItem(Items.PRISMARINE_SHARD, 3),
        new EnchantmentSettings(Enchantments.RESPIRATION).maxLevels(2, 3, 10).inLoottable("entities/zombie_nautilus").rarity(Rarity.RARE).dupedWithVanillaItem(Items.TURTLE_HELMET),

        // general armor enchantments
        new EnchantmentSettings(Enchantments.FEATHER_FALLING).maxLevels(1, 3, 68).inLoottable("chests/desert_pyramid").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.ANVIL),
        new EnchantmentSettings(Enchantments.THORNS).maxLevels(1, 3, 5).inLoottable("chests/simple_dungeon").dupedWithVanillaItem(Items.CACTUS_FLOWER, 2),
        new EnchantmentSettings(ModEnchantments.CHARM_BOOST).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.RAW_GOLD_BLOCK), // TODO: obtainable via archaeology loottables
        new EnchantmentSettings(Enchantments.PROTECTION).maxLevels(-1, 2, 4).inLoottable("chests/end_city_treasure").rarity(Rarity.EPIC).dupedWithVanillaItem(Items.ARMADILLO_SCUTE, 5),
        new EnchantmentSettings(Enchantments.BLAST_PROTECTION).maxLevels(3, 3, 5).dupedWithVanillaItem(Items.ARMADILLO_SCUTE, 3),
        new EnchantmentSettings(Enchantments.FIRE_PROTECTION).maxLevels(3, 3, 5).dupedWithVanillaItem(Items.ARMADILLO_SCUTE, 3),
        new EnchantmentSettings(Enchantments.PROJECTILE_PROTECTION).maxLevels(3, 3, 5).dupedWithVanillaItem(Items.ARMADILLO_SCUTE, 3),

        // mace enchantments
        new EnchantmentSettings(Enchantments.BREACH).maxLevels(3, 4, 4).inLoottable(trialChamberRewards).rarity(Rarity.COMMON).dupedWithVanillaItem(Items.POINTED_DRIPSTONE, 10),
        new EnchantmentSettings(Enchantments.DENSITY).maxLevels(4, 5, 5).inLoottable(trialChamberRewards).rarity(Rarity.COMMON).dupedWithVanillaItem(Items.ANVIL),
        new EnchantmentSettings(Enchantments.WIND_BURST).maxLevels(2, 3, 3).inLoottable(trialChamberRewards).rarity(Rarity.COMMON).dupedWithVanillaItem(Items.BREEZE_ROD, 3),

        // trident enchantments
        new EnchantmentSettings(Enchantments.CHANNELING).maxLevels(-1, 1, 1).inLoottable("chests/shipwreck_supply").dupedWithVanillaItem(Items.LIGHTNING_ROD.waxed().oxidized()),
        new EnchantmentSettings(Enchantments.IMPALING).maxLevels(3, 5, 5).inLoottable("chests/shipwreck_treasure").dupedWithVanillaItem(Items.NAUTILUS_SHELL),
        new EnchantmentSettings(Enchantments.LOYALTY).maxLevels(1, 3, 3).inLoottable("chests/shipwreck_treasure").rarity(Rarity.EPIC), // TODO: duped by dog collar from killing a tamed dog
        new EnchantmentSettings(Enchantments.RIPTIDE).maxLevels(1, 3, 3).inLoottable("chests/shipwreck_map").rarity(Rarity.EPIC).dupedWithVanillaItem(Items.PHANTOM_MEMBRANE, 3),

        // tool enchantments
        new EnchantmentSettings(Enchantments.EFFICIENCY).maxLevels(3, 5, 8).inLoottable("chests/stronghold_corridor", "chests/stronghold_crossing", "chests/stronghold_library").rarity(Rarity.RARE).dupedWithVanillaItem(Items.TNT),
        new EnchantmentSettings(Enchantments.SILK_TOUCH).maxLevels(1, 1, 1).inLoottable("chests/abandoned_mineshaft").rarity(Rarity.RARE).dupedWithVanillaItem(Items.BRAIN_CORAL_BLOCK),
        new EnchantmentSettings(Enchantments.FORTUNE).maxLevels(1, 3, 4).dupedWithVanillaItem(Items.NETHERITE_SCRAP), // TODO: obtained by crafting

        // sword & spear enchantments
        new EnchantmentSettings(Enchantments.LOOTING).maxLevels(1, 3, 5).inLoottable("chests/nether_bridge").rarity(Rarity.RARE).dupedWithVanillaItem(Items.PIGLIN_HEAD),
        new EnchantmentSettings(Enchantments.FIRE_ASPECT).maxLevels(-1, 1, 2).inLoottable("chests/ruined_portal").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.FIRE_CHARGE),
        new EnchantmentSettings(Enchantments.KNOCKBACK).maxLevels(1, 2, 3).inLoottable("chests/woodland_mansion").rarity(Rarity.EPIC).dupedWithVanillaItem(Items.SLIME_BLOCK),
        new EnchantmentSettings(Enchantments.SHARPNESS).maxLevels(3, 5, 11).rarity(Rarity.EPIC).dupedWithVanillaItem(Items.AMETHYST_CLUSTER),
        new EnchantmentSettings(Enchantments.SWEEPING_EDGE).maxLevels(1, 3, 5).inLoottable("chests/woodland_mansion").dupedWithVanillaItem(Items.AMETHYST_SHARD, 10),
        new EnchantmentSettings(Enchantments.SMITE).maxLevels(2, 4, 6).dupedWithVanillaItem(Items.NETHER_STAR),
        new EnchantmentSettings(Enchantments.BANE_OF_ARTHROPODS).maxLevels(100, 100, 100).dupedWithVanillaItem(Items.FERMENTED_SPIDER_EYE),
        new EnchantmentSettings(Enchantments.LUNGE).maxLevels(1, 3, 5).inLoottable("chests/desert_pyramid").rarity(Rarity.RARE).dupedWithVanillaItem(Items.RABBIT_FOOT),

        // boot enchantments
        new EnchantmentSettings(Enchantments.FROST_WALKER).maxLevels(-1, 2, 3).inLoottable("chests/igloo_chest").dupedWithVanillaItem(Items.BLUE_ICE, 5),
        new EnchantmentSettings(Enchantments.SOUL_SPEED).maxLevels(-1, 2, 3).inLoottable(bastionRewards).rarity(Rarity.RARE).dupedWithVanillaItem(Items.GILDED_BLACKSTONE, 3),
        new EnchantmentSettings(Enchantments.SWIFT_SNEAK).maxLevels(-1, 3, 4).inLoottable("chests/ancient_city", "chests/ancient_city_ice_box").rarity(Rarity.EPIC).dupedWithVanillaItem(Items.SCULK_SENSOR),

        // bow enchantments
        new EnchantmentSettings(Enchantments.INFINITY).maxLevels(1, 1, 1).inLoottable("chests/jungle_temple").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
        new EnchantmentSettings(Enchantments.POWER).maxLevels(2, 5, 5).inLoottable(bastionRewards).rarity(Rarity.RARE).dupedWithVanillaItem(Items.SKELETON_SKULL),
        new EnchantmentSettings(Enchantments.PUNCH).maxLevels(1, 3, 3).inLoottable("chests/jungle_temple").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.SLIME_BLOCK),
        new EnchantmentSettings(Enchantments.FLAME).maxLevels(1, 1, 1).inLoottable("chests/ruined_portal").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.MAGMA_CREAM),

        // fishing enchantments
        new EnchantmentSettings(Enchantments.LUCK_OF_THE_SEA).maxLevels(2, 3, 3).inLoottable("chests/buried_treasure").dupedWithVanillaItem(Items.SALMON, 6),
        new EnchantmentSettings(Enchantments.LURE).maxLevels(2, 3, 3).dupedWithVanillaItem(Items.PUFFERFISH, 3),

        // crossbow enchantments
        new EnchantmentSettings(Enchantments.MULTISHOT).maxLevels(1, 1, 1).inLoottable("chests/pillager_outpost").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.TRIPWIRE_HOOK, 3),
        new EnchantmentSettings(Enchantments.PIERCING).maxLevels(3, 4, 4).inLoottable("chests/pillager_outpost").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.TIPPED_ARROW),
        new EnchantmentSettings(Enchantments.QUICK_CHARGE).maxLevels(2, 3, 3).inLoottable("chests/pillager_outpost").rarity(Rarity.UNCOMMON).dupedWithVanillaItem(Items.FIREWORK_STAR),

        // curses
        // unused dupe items: oxeye daisy, poppy, wildflowers, cherry leaves
        new EnchantmentSettings(Enchantments.VANISHING_CURSE).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.CLOSED_EYEBLOSSOM),
        new EnchantmentSettings(Enchantments.BINDING_CURSE).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.ALLIUM),

        new EnchantmentSettings(ModEnchantments.CURSE_WEAKNESS_FIRE).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.RED_TULIP),
        new EnchantmentSettings(ModEnchantments.CURSE_WEAKNESS_FALL).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.PINK_TULIP),
        new EnchantmentSettings(ModEnchantments.CURSE_WEAKNESS_PROJECTILE).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.WHITE_TULIP),
        new EnchantmentSettings(ModEnchantments.CURSE_WEAKNESS_BLAST).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.ORANGE_TULIP),

        new EnchantmentSettings(ModEnchantments.CURSE_FRAGILITY).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.BLUE_ORCHID),
        new EnchantmentSettings(ModEnchantments.CURSE_STORMCALLING).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.LILAC),
        new EnchantmentSettings(ModEnchantments.CURSE_NECROSIS).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.LILY_OF_THE_VALLEY),
        new EnchantmentSettings(ModEnchantments.CURSE_DECAY).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.PEONY),
        new EnchantmentSettings(ModEnchantments.CURSE_DROUGHT).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.ROSE_BUSH),
        new EnchantmentSettings(ModEnchantments.CURSE_IRREPERABILITY).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.AZURE_BLUET),
        new EnchantmentSettings(ModEnchantments.CURSE_OMEN).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.OPEN_EYEBLOSSOM),
        new EnchantmentSettings(ModEnchantments.CURSE_WASTEFULNESS).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.CORNFLOWER),
        new EnchantmentSettings(ModEnchantments.CURSE_WEARINESS).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.GOLDEN_DANDELION),
        new EnchantmentSettings(ModEnchantments.CURSE_DULLNESS).maxLevels(-1, 1, 1).dupedWithVanillaItem(Items.SUNFLOWER)
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

    public static int getMaxEnchantingTableLevel(Holder<Enchantment> enchantment) {
        return getSettingsForEnch(enchantment)
                .map(settings -> settings.maxEnchantingTableLevel)
                .orElse(0);
    }

    public static int getMaxAnvilLevel(Holder<Enchantment> enchantment) {
        return getSettingsForEnch(enchantment)
                .map(settings -> settings.maxEnderiteLevel)
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
