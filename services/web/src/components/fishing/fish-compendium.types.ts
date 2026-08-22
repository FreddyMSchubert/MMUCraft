export type Rarity = 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary' | 'mythical';
export type TagGroup = 'climate' | 'water' | 'time' | 'height' | 'weather' | 'moon';
export type FishSort = 'rarity' | 'location';

export interface PlayerOption {
	id: number;
	minecraftUsername: string;
	color: string;
	pronouns: string;
	avatarUrl: string | null;
	caughtTotal: number;
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
	catch: FishCatch | null;
	serverLargest: ServerRecord | null;
	serverSmallest: ServerRecord | null;
}

export interface CompendiumResponse {
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
	warm: { emoji: '🔥', label: 'Warm', group: 'climate', phrase: 'hot Overworld biomes' },
	cold: { emoji: '❄️', label: 'Cold', group: 'climate', phrase: 'cold Overworld biomes' },
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

export const GROUPS: TagGroup[] = ['climate', 'water', 'time', 'height', 'weather', 'moon'];
export const GROUP_LABELS: Record<TagGroup, string> = {
	climate: 'Temperature',
	water: 'Water type',
	time: 'Daytime',
	height: 'Height',
	weather: 'Weather',
	moon: 'Moon phase',
};
