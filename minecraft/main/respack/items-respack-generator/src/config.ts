import path from 'path';
import type { ArmorMaterial, GeneratorOptions } from './types';

export const DEFAULT_NAMESPACE = 'mmu_pack';
export const DEFAULT_PACK_DESCRIPTION = 'Official MMU MC Soc Server Resource Pack. Do not disable.';

export const PACK_MCMETA = {
	pack: {
		min_format: [75, 0],
		max_format: 75,
		description: DEFAULT_PACK_DESCRIPTION,
	},
} as const;

export const COMMAND_BLOCK_ITEM_ID = 'command_block';
export const CARVED_PUMPKIN_ITEM_ID = 'carved_pumpkin';

export const FISHING_SHADOW_VANILLA_ITEMS = [
	'amethyst_shard',
	'angler_pottery_sherd',
	'beetroot_seeds',
	'bone',
	'bow',
	'bowl',
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
	'dead_bush',
	'diamond',
	'diamond_block',
	'emerald',
	'emerald_block',
	'enchanted_book',
	'experience_bottle',
	'feather',
	'filled_map',
	'fishing_rod',
	'flint',
	'glass_bottle',
	'glow_ink_sac',
	'golden_sword',
	'heart_of_the_sea',
	'ink_sac',
	'iron_nugget',
	'kelp',
	'leather',
	'leather_boots',
	'lily_pad',
	'map',
	'melon_seeds',
	'name_tag',
	'nautilus_shell',
	'oak_boat',
	'poisonous_potato',
	'potato',
	'potion',
	'prismarine_crystals',
	'prismarine_shard',
	'pufferfish',
	'pumpkin_seeds',
	'raw_copper',
	'raw_gold',
	'raw_iron',
	'rotten_flesh',
	'saddle',
	'salmon',
	'sea_pickle',
	'seagrass',
	'sniffer_egg',
	'splash_potion',
	'sponge',
	'spyglass',
	'stick',
	'string',
	'sugar_cane',
	'tide_armor_trim_smithing_template',
	'trident',
	'tripwire_hook',
	'tropical_fish',
	'turtle_scute',
	'wet_sponge',
	'wheat',
	'wheat_seeds',
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
		vanillaArmorAssetsDir: path.resolve(
			cwd,
			values.get('vanilla-armor') ?? 'vanilla_armor_assets',
		),
		outputDir: path.resolve(cwd, values.get('output') ?? 'generated-resource-pack'),
		namespace: values.get('namespace') ?? DEFAULT_NAMESPACE,
		packDescription: values.get('description') ?? DEFAULT_PACK_DESCRIPTION,
	};
}

export function cliUsage(): string {
	return [
		'Usage:',
		'  npm run generate -- --source ./items --vanilla-armor ./vanilla_armor_assets --output ./dist-resource-pack',
		'',
		'Optional flags:',
		'  --namespace <namespace>      Resource namespace to generate under (default: mmu_pack)',
		'  --description <text>         pack.mcmeta description',
	].join('\n');
}
