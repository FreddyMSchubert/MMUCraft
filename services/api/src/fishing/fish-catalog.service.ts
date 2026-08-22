import { Injectable, NotFoundException, OnModuleInit } from '@nestjs/common';
import { randomInt } from 'node:crypto';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { ASSETS } from '../assets';

const ITEM_ROOTS = [
	join(process.cwd(), 'content', 'items'),
	join(process.cwd(), '..', '..', 'minecraft', 'main', 'data', 'data', 'items'),
	join(process.cwd(), 'minecraft', 'main', 'data', 'data', 'items'),
] as const;
const RARITIES = new Set(['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythical']);
const FISH_ASSET_REVISION = `${Date.now().toString(36)}-${randomInt(0x100000000).toString(36)}`;

export interface FishDefinition {
	id: string;
	title: string;
	rarity: string;
	tags: string[];
	facts: string[];
	iconUrl: string;
	textureFilePath: string | null;
}

interface FishItemJson {
	id?: unknown;
	title?: unknown;
	rarity?: unknown;
	tooltips?: unknown;
	fish?: { tags?: unknown };
}

const VANILLA_FISH: FishDefinition[] = [
	vanillaFish(
		'minecraft:cod',
		'Cod',
		'common',
		'Cod use the small barbel beneath their chin to help search the seabed for food.',
	),
	vanillaFish(
		'minecraft:salmon',
		'Salmon',
		'common',
		'Salmon can navigate back to the stream where they hatched after years at sea.',
	),
	vanillaFish(
		'minecraft:tropical_fish',
		'Tropical Fish',
		'uncommon',
		'Many tropical reef fish can change colour or pattern as they mature.',
	),
	vanillaFish(
		'minecraft:pufferfish',
		'Pufferfish',
		'uncommon',
		'Pufferfish inflate by rapidly swallowing water, making themselves difficult for predators to bite.',
	),
];

@Injectable()
export class FishCatalogService implements OnModuleInit {
	private cache: {
		mtimeMs: number;
		definitions: FishDefinition[];
		byId: Map<string, FishDefinition>;
	} | null = null;

	onModuleInit() {
		if (process.env.NODE_ENV === 'production') this.definitions();
	}

	definitions() {
		return this.load().definitions;
	}

	textureFilePath(fishId: string) {
		const path = this.load().byId.get(fishId)?.textureFilePath;
		if (!path) throw new NotFoundException('Fish texture not found');
		return path;
	}

	private load() {
		if (process.env.NODE_ENV === 'production' && this.cache) return this.cache;
		const itemRoot = ITEM_ROOTS.find((candidate) => existsSync(candidate)) ?? ITEM_ROOTS[0];
		const fishRoot = join(itemRoot, 'fish');
		if (!existsSync(fishRoot)) return this.setCache(0, VANILLA_FISH);
		const mtimeMs = treeMtime(fishRoot);
		if (this.cache?.mtimeMs === mtimeMs) return this.cache;
		const revision =
			process.env.NODE_ENV === 'production' ? FISH_ASSET_REVISION : String(mtimeMs);
		const customDefinitions = findItemFiles(fishRoot).flatMap((filePath): FishDefinition[] => {
			const json = JSON.parse(readFileSync(filePath, 'utf8')) as FishItemJson;
			const textureFilePath = join(dirname(filePath), 'texture.png');
			if (
				typeof json.id !== 'string' ||
				typeof json.title !== 'string' ||
				!existsSync(textureFilePath)
			)
				return [];
			return [
				{
					id: json.id,
					title: json.title,
					rarity:
						typeof json.rarity === 'string' && RARITIES.has(json.rarity)
							? json.rarity
							: 'common',
					tags: Array.isArray(json.fish?.tags)
						? json.fish.tags.filter((tag): tag is string => typeof tag === 'string')
						: [],
					facts: Array.isArray(json.tooltips)
						? json.tooltips.filter(
								(fact): fact is string =>
									typeof fact === 'string' && fact.trim().length > 0,
							)
						: [],
					iconUrl: `/api/fishing/texture/${encodeURIComponent(json.id)}?v=${revision}`,
					textureFilePath,
				},
			];
		});
		return this.setCache(
			mtimeMs,
			[...VANILLA_FISH, ...customDefinitions].sort((left, right) =>
				left.title.localeCompare(right.title, 'en'),
			),
		);
	}

	private setCache(mtimeMs: number, definitions: FishDefinition[]) {
		return (this.cache = {
			mtimeMs,
			definitions,
			byId: new Map(definitions.map((fish) => [fish.id, fish])),
		});
	}
}

function findItemFiles(directory: string): string[] {
	return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const path = join(directory, entry.name);
		return entry.isDirectory() ? findItemFiles(path) : entry.name === 'item.json' ? [path] : [];
	});
}

function treeMtime(path: string): number {
	const stat = statSync(path);
	if (!stat.isDirectory()) return stat.mtimeMs;
	return readdirSync(path, { withFileTypes: true }).reduce(
		(mtime, child) => Math.max(mtime, treeMtime(join(path, child.name))),
		stat.mtimeMs,
	);
}

function vanillaFish(id: string, title: string, rarity: string, fact: string): FishDefinition {
	const textureName = id.slice('minecraft:'.length);
	return {
		id,
		title,
		rarity,
		tags: [],
		facts: [fact],
		iconUrl: `${ASSETS.minecraft.vanilla}/textures/item/${textureName}.png`,
		textureFilePath: null,
	};
}
