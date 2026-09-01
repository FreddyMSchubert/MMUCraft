export type ShopItemType = 'charm' | 'cosmetic' | 'generic';
export type ShopOrder =
	| 'random'
	| 'alphabetical'
	| 'alphabetical-desc'
	| 'rarity'
	| 'rarity-desc'
	| 'price-desc'
	| 'price-asc';
export type ShopTagFilter = 'all' | 'dyeable' | 'animated' | 'discounted' | 'sold-out';

interface CharmLevel {
	level: number;
	abilityStatusCurrent: string;
	upgradeIngredients: string[];
}

export interface ShopItem {
	id: string;
	title: string;
	type: ShopItemType;
	modelType: string;
	rarity: string;
	priceDabloons: number;
	originalPriceDabloons: number;
	discountedPriceDabloons: number;
	isDailyDeal: boolean;
	discountPercent: number;
	dealMessage: string | null;
	description: string;
	tooltips: string[];
	unlockMessage: string | null;
	unlockWeight: number;
	iconUrl: string | null;
	renderMode: 'texture' | 'model';
	modelUrl: string | null;
	textureUrl: string | null;
	animated: boolean;
	dyeable: boolean;
	animation: { frameDelayMs: number; frames: number[] | null } | null;
	charmDetails: { minLevel: number; maxLevel: number; levels: CharmLevel[] } | null;
	unlocked: boolean;
	available: boolean;
}

export interface ShopResponse {
	dealDate: string;
	shoppingSunday: boolean;
	availability: { knowledge: boolean; charms: boolean; cosmetics: boolean };
	items: ShopItem[];
}

export const TYPE_OPTIONS: { value: 'all' | ShopItemType; label: string }[] = [
	{ value: 'all', label: 'All' },
	{ value: 'charm', label: 'Charms' },
	{ value: 'cosmetic', label: 'Cosmetics' },
	{ value: 'generic', label: 'Items' },
];

export const RARITY_OPTIONS = [
	'all',
	'common',
	'uncommon',
	'rare',
	'epic',
	'legendary',
	'mythical',
] as const;

export const TAG_OPTIONS: { value: ShopTagFilter; label: string }[] = [
	{ value: 'all', label: 'All' },
	{ value: 'dyeable', label: 'Dyeable' },
	{ value: 'animated', label: 'Animated' },
	{ value: 'discounted', label: 'Discounted' },
	{ value: 'sold-out', label: 'Sold out' },
];

export const ORDER_OPTIONS: { value: ShopOrder; label: string }[] = [
	{ value: 'random', label: 'Random' },
	{ value: 'alphabetical', label: 'Alphabetical: A–Z' },
	{ value: 'alphabetical-desc', label: 'Alphabetical: Z–A' },
	{ value: 'rarity-desc', label: 'Rarity: highest first' },
	{ value: 'rarity', label: 'Rarity: lowest first' },
	{ value: 'price-desc', label: 'Price: high to low' },
	{ value: 'price-asc', label: 'Price: low to high' },
];

const RARITY_RANK = new Map(RARITY_OPTIONS.map((rarity, index) => [rarity, index]));
export { DABLOON_SYMBOL, formatDabloons } from '@/lib/dabloons';

export function effectivePrice(item: ShopItem) {
	return item.isDailyDeal ? item.discountedPriceDabloons : item.priceDabloons;
}

export function shouldHidePreview(item: ShopItem, arachnophobiaMode: boolean) {
	return arachnophobiaMode && /(spider|arach)/i.test(item.title);
}

export function isSoldOut(item: ShopItem) {
	return item.type === 'generic' && !item.available;
}

export function compareTitles(left: ShopItem, right: ShopItem) {
	return left.title.localeCompare(right.title, 'en');
}

export function rarityRank(item: ShopItem) {
	return RARITY_RANK.get(item.rarity as (typeof RARITY_OPTIONS)[number]) ?? 0;
}

export function formatOption(value: string) {
	return value
		.split(/[-_ ]+/)
		.filter(Boolean)
		.map((part) => part.charAt(0).toUpperCase() + part.slice(1))
		.join(' ');
}

export function formatIngredient(value: string) {
	const customLabel = value.includes('=') ? value.slice(value.lastIndexOf('=') + 1) : '';
	if (customLabel) return customLabel;
	const id = value
		.split('[', 1)[0]
		.replace(/^minecraft:/, '')
		.replace(/^.*:/, '');
	return formatOption(id);
}

export function seededRank(value: string) {
	let hash = 2166136261;
	for (let index = 0; index < value.length; index++) {
		hash ^= value.charCodeAt(index);
		hash = Math.imul(hash, 16777619);
	}
	return hash >>> 0;
}
