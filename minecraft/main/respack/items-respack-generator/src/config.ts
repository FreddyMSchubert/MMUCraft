import path from 'path';
import type { ArmorMaterial, GeneratorOptions } from './types';

export const DEFAULT_NAMESPACE = 'mmu_pack';
export const DEFAULT_PACK_DESCRIPTION = 'Official MMU MC Soc Server Resource Pack. Do not disable.';

export const PACK_MCMETA = {
	pack: {
		min_format: [97, 1],
		max_format: [97, 1],
		description: DEFAULT_PACK_DESCRIPTION,
	},
} as const;

export const COMMAND_BLOCK_ITEM_ID = 'command_block';
export const CARVED_PUMPKIN_ITEM_ID = 'carved_pumpkin';

export const FISHING_SHADOW_VANILLA_ITEMS = [
	'amethyst_shard',
	'ancient_debris',
	'angler_pottery_sherd',
	'basalt',
	'beacon',
	'beetroot_seeds',
	'blackstone',
	'blaze_powder',
	'blaze_rod',
	'bone',
	'bone_block',
	'bone_meal',
	'bow',
	'bowl',
	'brown_mushroom',
	'carrot',
	'charcoal',
	'clay',
	'clay_ball',
	'clock',
	'coal',
	'coast_armor_trim_smithing_template',
	'cocoa_beans',
	'cod',
	'compass',
	'conduit',
	'copper_nugget',
	'crimson_fungus',
	'crimson_roots',
	'crimson_stem',
	'crossbow',
	'crying_obsidian',
	'dead_bush',
	'diamond',
	'diamond_block',
	'diamond_sword',
	'emerald',
	'emerald_block',
	'enchanted_book',
	'enchanted_golden_apple',
	'ender_chest',
	'ender_eye',
	'ender_pearl',
	'experience_bottle',
	'feather',
	'filled_map',
	'fire_charge',
	'fishing_rod',
	'flint',
	'ghast_tear',
	'gilded_blackstone',
	'glass_bottle',
	'glow_ink_sac',
	'glowstone',
	'glowstone_dust',
	'gold_block',
	'gold_ingot',
	'gold_nugget',
	'golden_apple',
	'golden_boots',
	'golden_carrot',
	'golden_helmet',
	'golden_sword',
	'gravel',
	'gunpowder',
	'heart_of_the_sea',
	'ink_sac',
	'iron_bars',
	'iron_ingot',
	'iron_nugget',
	'kelp',
	'leather',
	'leather_boots',
	'lily_pad',
	'lodestone',
	'magma_cream',
	'map',
	'melon_seeds',
	'music_disc_pigstep',
	'name_tag',
	'nautilus_shell',
	'nether_brick',
	'nether_brick_fence',
	'nether_bricks',
	'nether_sprouts',
	'nether_star',
	'nether_wart',
	'nether_wart_block',
	'netherite_ingot',
	'netherite_scrap',
	'netherite_upgrade_smithing_template',
	'oak_boat',
	'obsidian',
	'piglin_banner_pattern',
	'piglin_head',
	'poisonous_potato',
	'potato',
	'potion',
	'prismarine_crystals',
	'prismarine_shard',
	'pufferfish',
	'pumpkin_seeds',
	'quartz',
	'raw_copper',
	'raw_gold',
	'raw_iron',
	'red_mushroom',
	'respawn_anchor',
	'rib_armor_trim_smithing_template',
	'rotten_flesh',
	'saddle',
	'salmon',
	'sea_pickle',
	'seagrass',
	'shroomlight',
	'skeleton_skull',
	'sniffer_egg',
	'snout_armor_trim_smithing_template',
	'soul_campfire',
	'soul_lantern',
	'soul_sand',
	'soul_soil',
	'soul_torch',
	'spectral_arrow',
	'splash_potion',
	'sponge',
	'spyglass',
	'stick',
	'string',
	'sugar_cane',
	'tide_armor_trim_smithing_template',
	'tnt',
	'trident',
	'tripwire_hook',
	'tropical_fish',
	'turtle_scute',
	'twisting_vines',
	'warped_fungus',
	'warped_roots',
	'warped_stem',
	'warped_wart_block',
	'weeping_vines',
	'wet_sponge',
	'wheat',
	'wheat_seeds',
	'wither_skeleton_skull',
	'wooden_shovel',
] as const;

export const FISHING_SHADOW_MODEL_OVERRIDES: Readonly<Record<string, string>> = {
	clock: 'minecraft:item/clock_00',
	compass: 'minecraft:item/compass_16',
};

export const FISHING_SHADOW_TEXTURE_OVERRIDES: Readonly<Record<string, string>> = {
	clay: 'minecraft:block/clay',
	diamond_block: 'minecraft:block/diamond_block',
	emerald_block: 'minecraft:block/emerald_block',
	sponge: 'minecraft:block/sponge',
	wet_sponge: 'minecraft:block/wet_sponge',
};

export const CHARM_ARMOR_MATERIALS: readonly ArmorMaterial[] = [
	'chainmail',
	'leather',
	'iron',
	'gold',
	'diamond',
	'netherite',
	'enderite',
	'copper',
] as const;

export const LEATHER_UNDYED_COLOR = -6265536;

export function resolveOptionsFromCli(argv: readonly string[]): GeneratorOptions {
	const values = new Map<string, string>();

	for (let index = 0; index < argv.length; index += 1) {
		const token = argv[index];
		if (!token.startsWith('--')) {
			throw new Error(`Unexpected argument: ${token}`);
		}

		const key = token.slice(2);
		const value = argv[index + 1];
		if (!value || value.startsWith('--')) {
			throw new Error(`Missing value for --${key}`);
		}

		values.set(key, value);
		index += 1;
	}

	const cwd = process.cwd();

	return {
		sourceDir: path.resolve(cwd, values.get('source') ?? 'items'),
		armorAssetsDir: path.resolve(cwd, values.get('armor-assets') ?? '.'),
		outputDir: path.resolve(cwd, values.get('output') ?? 'generated-resource-pack'),
		namespace: values.get('namespace') ?? DEFAULT_NAMESPACE,
		packDescription: values.get('description') ?? DEFAULT_PACK_DESCRIPTION,
	};
}

export function cliUsage(): string {
	return [
		'Usage:',
		'  npm run generate -- --source ./items --armor-assets . --output ./dist-resource-pack',
		'',
		'Optional flags:',
		'  --namespace <namespace>      Resource namespace to generate under (default: mmu_pack)',
		'  --description <text>         pack.mcmeta description',
	].join('\n');
}
