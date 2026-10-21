import type { PlayerEmoji } from '@/components/player-name';
import type { TextureAnimationOptions } from '@/lib/minecraft-model-renderer';

export type Rarity = 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary' | 'mythical';
export type TagGroup =
	'dimension' | 'netherBiome' | 'climate' | 'water' | 'time' | 'height' | 'weather' | 'moon';
export type FishSort = 'rarity' | 'location';

export interface PlayerOption {
	id: number;
	minecraftUsername: string;
	color: string;
	pronouns: string;
	avatarUrl: string | null;
	caughtTotal: number;
	isCommittee: boolean;
	isRanked: boolean;
	customEmojis: PlayerEmoji[];
}

export interface CatchPoint {
	lengthCm: number;
	caughtAtUnixMs: number;
}

interface FishCatch {
	first: CatchPoint;
	smallest: CatchPoint;
	largest: CatchPoint;
}

export interface ServerRecord extends CatchPoint {
	player: PlayerOption;
}

export interface CompendiumFish {
	id: string;
	title: string;
	rarity: Rarity;
	tags: string[];
	facts: string[];
	iconUrl: string;
	animation: TextureAnimationOptions | null;
	catch: FishCatch | null;
	serverLargest: ServerRecord | null;
	serverSmallest: ServerRecord | null;
}

export interface CompendiumResponse {
	netherOpen: boolean;
	currentUserId: number;
	selectedUserId: number;
	players: PlayerOption[];
	fish: CompendiumFish[];
}

export interface CatchEvent {
	type: 'catch';
	rarity: Rarity;
}

export const RARITIES: { id: Rarity; label: string; color: string; hue: number }[] = [
	{ id: 'common', label: 'Common', color: '#ffffff', hue: 0 },
	{ id: 'uncommon', label: 'Uncommon', color: '#ffff55', hue: 60 },
	{ id: 'rare', label: 'Rare', color: '#55ffff', hue: 180 },
	{ id: 'epic', label: 'Epic', color: '#ff55ff', hue: 300 },
	{ id: 'legendary', label: 'Legendary', color: '#55ff55', hue: 120 },
	{ id: 'mythical', label: 'Mythical', color: '#ff5f00', hue: 22 },
];

export const TAGS: Record<
	string,
	{ emoji: string; label: string; group: TagGroup; phrase: string }
> = {
	overworld: { emoji: '🌍', label: 'Overworld', group: 'dimension', phrase: 'the Overworld' },
	nether: { emoji: '🔥', label: 'Nether', group: 'dimension', phrase: 'the Nether' },
	nether_wastes: {
		emoji: '🌋',
		label: 'Nether Wastes',
		group: 'netherBiome',
		phrase: 'the Nether Wastes',
	},
	soul_sand_valley: {
		emoji: '💀',
		label: 'Soul Sand Valley',
		group: 'netherBiome',
		phrase: 'Soul Sand Valleys',
	},
	crimson_forest: {
		emoji: '🍄',
		label: 'Crimson Forest',
		group: 'netherBiome',
		phrase: 'Crimson Forests',
	},
	warped_forest: {
		emoji: '🟦',
		label: 'Warped Forest',
		group: 'netherBiome',
		phrase: 'Warped Forests',
	},
	basalt_deltas: {
		emoji: '🪨',
		label: 'Basalt Deltas',
		group: 'netherBiome',
		phrase: 'the Basalt Deltas',
	},
	warm: { emoji: '🔥', label: 'Warm', group: 'climate', phrase: 'hot biomes' },
	cold: { emoji: '❄️', label: 'Cold', group: 'climate', phrase: 'cold biomes' },
	river: { emoji: '🌉', label: 'River', group: 'water', phrase: 'rivers' },
	ocean: { emoji: '🏖️', label: 'Ocean', group: 'water', phrase: 'oceans' },
	day: { emoji: '☀️', label: 'Day', group: 'time', phrase: 'daytime' },
	night: { emoji: '🌙', label: 'Night', group: 'time', phrase: 'nighttime' },
	deep: { emoji: '⬇️', label: 'Deep', group: 'height', phrase: 'below Y 60' },
	high: { emoji: '⬆️', label: 'High', group: 'height', phrase: 'above Y 100' },
	rainy: { emoji: '🌧️', label: 'Rainy', group: 'weather', phrase: 'rain' },
	thunderstorm: {
		emoji: '⛈️',
		label: 'Thunderstorm',
		group: 'weather',
		phrase: 'thunderstorms',
	},
	snowy: { emoji: '🌨️', label: 'Snowy', group: 'weather', phrase: 'snowfall' },
	waxing: { emoji: '🌒', label: 'Waxing moon', group: 'moon', phrase: 'waxing moon phases' },
	waning: { emoji: '🌘', label: 'Waning moon', group: 'moon', phrase: 'waning moon phases' },
	fullmoon: { emoji: '🌕', label: 'Full moon', group: 'moon', phrase: 'a full moon' },
	newmoon: { emoji: '🌑', label: 'New moon', group: 'moon', phrase: 'a new moon' },
};

export const GROUPS: TagGroup[] = [
	'dimension',
	'netherBiome',
	'climate',
	'water',
	'time',
	'height',
	'weather',
	'moon',
];
export const GROUP_LABELS: Record<TagGroup, string> = {
	dimension: 'Dimension',
	netherBiome: 'Nether biome',
	climate: 'Temperature',
	water: 'Water type',
	time: 'Daytime',
	height: 'Height',
	weather: 'Weather',
	moon: 'Moon phase',
};
