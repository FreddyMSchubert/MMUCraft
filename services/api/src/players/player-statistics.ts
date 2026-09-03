const STATS_VERSION = 1;

export interface StatOption {
	key: string;
	label: string;
	group: 'profile' | 'money' | 'fishing' | 'minecraft';
	category?: string;
}

export interface MinecraftStatValue {
	key: string;
	category: string;
	id: string;
	label: string;
	value: number;
	total?: number;
	updatedAtUnixMs: number;
}

export interface MinecraftProfile {
	uuid: string;
	name: string;
	skinUrl: string | null;
	model: string | null;
	fetchedAtUnixMs: number;
}

export interface PlayerStats {
	version: number;
	money: {
		earnedDabloons: number;
		balanceDabloons: number | null;
		lastUpdatedAtUnixMs: number | null;
		sources: Record<string, { earnedDabloons: number }>;
	};
	minecraft: {
		stats: Record<string, MinecraftStatValue>;
		lastSyncedAtUnixMs: number | null;
		lastPlayedAtUnixMs: number | null;
	};
	minecraftProfile: MinecraftProfile | null;
}

export interface MinecraftStatInput {
	key?: string;
	category?: string;
	id?: string;
	label?: string;
	value?: number;
	total?: number;
}

const PROFILE_OPTIONS: StatOption[] = [
	{ key: 'profile.playerName', label: 'Player Name', group: 'profile' },
	{ key: 'profile.isMember', label: 'Society Member', group: 'profile' },
	{ key: 'profile.isCommittee', label: 'Committee', group: 'profile' },
	{ key: 'profile.preferredName', label: 'Nickname', group: 'profile' },
	{ key: 'profile.pronouns', label: 'Pronouns', group: 'profile' },
	{ key: 'profile.courseYear', label: 'Course / Year', group: 'profile' },
	{ key: 'profile.discordUsername', label: 'Discord Username', group: 'profile' },
	{ key: 'profile.base', label: 'Base Location', group: 'profile' },
];

const FISHING_OPTIONS: StatOption[] = [
	{ key: 'fishing.total', label: 'Fish Species Caught', group: 'fishing' },
	...['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythical'].map((rarity) => ({
		key: `fishing.${rarity}`,
		label: `${titleCase(rarity)} Fish Species Caught`,
		group: 'fishing' as const,
	})),
];

const CUSTOM_STATS = [
	['minecraft:play_time', 'Play Time'],
	['minecraft:total_world_time', 'Total World Time'],
	['minecraft:time_since_death', 'Time Since Death'],
	['minecraft:time_since_rest', 'Time Since Rest'],
	['minecraft:sneak_time', 'Sneak Time'],
	['minecraft:crouch_time', 'Crouch Time'],
	['minecraft:walk_one_cm', 'Distance Walked'],
	['minecraft:sprint_one_cm', 'Distance Sprinted'],
	['minecraft:crouch_one_cm', 'Distance Crouched'],
	['minecraft:fall_one_cm', 'Distance Fallen'],
	['minecraft:fly_one_cm', 'Distance Flown'],
	['minecraft:swim_one_cm', 'Distance Swum'],
	['minecraft:boat_one_cm', 'Distance By Boat'],
	['minecraft:minecart_one_cm', 'Distance By Minecart'],
	['minecraft:horse_one_cm', 'Distance By Horse'],
	['minecraft:aviate_one_cm', 'Distance By Elytra'],
	['minecraft:jump', 'Times Jumped'],
	['minecraft:drop', 'Items Dropped'],
	['minecraft:damage_dealt', 'Damage Dealt'],
	['minecraft:damage_taken', 'Damage Taken'],
	['minecraft:deaths', 'Deaths'],
	['minecraft:mob_kills', 'Mob Kills'],
	['minecraft:player_kills', 'Player Kills'],
	['minecraft:animals_bred', 'Animals Bred'],
	['minecraft:fish_caught', 'Fish Caught'],
	['minecraft:talked_to_villager', 'Villagers Talked To'],
	['minecraft:traded_with_villager', 'Trades With Villagers'],
	['minecraft:raid_trigger', 'Raids Triggered'],
	['minecraft:raid_win', 'Raids Won'],
] satisfies [string, string][];

const ENTITY_IDS = `
	allay armadillo axolotl bat bee blaze bogged breeze camel cat cave_spider chicken cod cow
	creeper dolphin donkey drowned elder_guardian ender_dragon enderman endermite evoker fox frog
	ghast glow_squid goat guardian hoglin horse husk iron_golem llama magma_cube mooshroom mule
	ocelot panda parrot phantom pig piglin piglin_brute pillager polar_bear pufferfish rabbit ravager
	salmon sheep shulker silverfish skeleton skeleton_horse slime sniffer snow_golem spider squid stray
	strider tadpole trader_llama tropical_fish turtle vex villager vindicator wandering_trader warden
	witch wither wither_skeleton wolf zoglin zombie zombie_horse zombie_villager zombified_piglin
`
	.trim()
	.split(/\s+/);

const KNOWN_OPTIONS: StatOption[] = [
	...PROFILE_OPTIONS,
	{ key: 'money.earnedDabloons', label: 'Dabloons Earned', group: 'money' },
	{ key: 'unlocks.charms', label: 'Charms Unlocked', group: 'money' },
	{ key: 'unlocks.cosmetics', label: 'Cosmetics Unlocked', group: 'money' },
	{ key: 'unlocks.knowledge', label: 'Knowledge Pages Unlocked', group: 'money' },
	...FISHING_OPTIONS,
	{
		key: 'minecraft.lastPlayedAtUnixMs',
		label: 'Last Played',
		group: 'minecraft',
		category: 'session',
	},
	{
		key: minecraftStatKey('advancement', 'minecraft:earned'),
		label: 'Advancements Earned',
		group: 'minecraft',
		category: 'advancement',
	},
	...CUSTOM_STATS.map(([id, label]) => ({
		key: minecraftStatKey('custom', id),
		label,
		group: 'minecraft' as const,
		category: 'custom',
	})),
	...ENTITY_IDS.flatMap((entityId): StatOption[] => {
		const id = `minecraft:${entityId}`;
		const label = humanizeResourcePath(entityId);
		return [
			{
				key: minecraftStatKey('killed', id),
				label: `${label} Killed`,
				group: 'minecraft',
				category: 'killed',
			},
			{
				key: minecraftStatKey('killed_by', id),
				label: `Killed By ${label}`,
				group: 'minecraft',
				category: 'killed_by',
			},
		];
	}),
];

export function buildStatOptions(playerStatistics: PlayerStats[]): StatOption[] {
	const byKey = new Map(KNOWN_OPTIONS.map((option) => [option.key, option]));
	for (const stats of playerStatistics) {
		for (const stat of Object.values(stats.minecraft.stats)) {
			byKey.set(stat.key, {
				key: stat.key,
				label: stat.label || humanizeResourcePath(stat.id),
				group: 'minecraft',
				category: stat.category,
			});
		}
	}
	return [...byKey.values()].sort(
		(left, right) =>
			groupRank(left.group) - groupRank(right.group) ||
			left.label.localeCompare(right.label, 'en'),
	);
}

export function normalizeMinecraftStat(
	input: MinecraftStatInput,
	unixMs: number,
): MinecraftStatValue | null {
	const category = sanitizeToken(input.category, '');
	const id = sanitizeResourceId(input.id);
	const value = normalizeNullableInteger(input.value);
	const total = normalizeNullableInteger(input.total);
	const key = sanitizeToken(input.key, '') || minecraftStatKey(category, id);
	if (!category || !id || !key || value === null || value < 0) return null;
	return {
		key,
		category,
		id,
		label: sanitizeText(input.label, 120) || defaultMinecraftStatLabel(category, id),
		value,
		...(total !== null && total >= value ? { total } : {}),
		updatedAtUnixMs: unixMs,
	};
}

export function defaultStats(): PlayerStats {
	return {
		version: STATS_VERSION,
		money: {
			earnedDabloons: 0,
			balanceDabloons: null,
			lastUpdatedAtUnixMs: null,
			sources: {},
		},
		minecraft: { stats: {}, lastSyncedAtUnixMs: null, lastPlayedAtUnixMs: null },
		minecraftProfile: null,
	};
}

export function normalizeStatsJson(value: string): PlayerStats {
	try {
		const parsed = JSON.parse(value) as Partial<PlayerStats>;
		const stats = defaultStats();
		stats.money.earnedDabloons = normalizePositiveInteger(parsed.money?.earnedDabloons);
		stats.money.balanceDabloons = normalizeNullableInteger(parsed.money?.balanceDabloons);
		stats.money.lastUpdatedAtUnixMs = normalizeNullableInteger(
			parsed.money?.lastUpdatedAtUnixMs,
		);
		stats.money.sources = normalizeMoneySources(parsed.money?.sources);
		stats.minecraft.stats = normalizeMinecraftStats(parsed.minecraft?.stats);
		stats.minecraft.lastSyncedAtUnixMs = normalizeNullableInteger(
			parsed.minecraft?.lastSyncedAtUnixMs,
		);
		stats.minecraft.lastPlayedAtUnixMs = normalizeNullableInteger(
			parsed.minecraft?.lastPlayedAtUnixMs,
		);
		stats.minecraftProfile = normalizeMinecraftProfile(parsed.minecraftProfile);
		return stats;
	} catch {
		return defaultStats();
	}
}

export function normalizePositiveInteger(value: unknown): number {
	const number = typeof value === 'number' ? value : Number(value);
	return Number.isFinite(number) ? Math.max(0, Math.trunc(number)) : 0;
}

export function normalizeNullableInteger(value: unknown): number | null {
	if (value === null || value === undefined || value === '') return null;
	const number = typeof value === 'number' ? value : Number(value);
	return Number.isFinite(number) ? Math.trunc(number) : null;
}

export function normalizeUnixMs(value: unknown): number {
	const unixMs = normalizeNullableInteger(value);
	return unixMs && unixMs > 0 ? unixMs : Date.now();
}

export function sanitizeToken(value: unknown, fallback: string): string {
	if (typeof value !== 'string') return fallback;
	return (
		value
			.trim()
			.toLowerCase()
			.replace(/[^a-z0-9_.:-]/g, '_')
			.slice(0, 160) || fallback
	);
}

export function sanitizeEventId(value: unknown): string | null {
	if (typeof value !== 'string') return null;
	return value.trim().slice(0, 220) || null;
}

function normalizeMoneySources(value: unknown): PlayerStats['money']['sources'] {
	if (!value || typeof value !== 'object') return {};
	const sources: PlayerStats['money']['sources'] = {};
	for (const [key, raw] of Object.entries(value)) {
		if (!raw || typeof raw !== 'object') continue;
		const source = sanitizeToken(key, '');
		if (!source) continue;
		sources[source] = {
			earnedDabloons: normalizePositiveInteger(
				(raw as { earnedDabloons?: unknown }).earnedDabloons,
			),
		};
	}
	return sources;
}

function normalizeMinecraftStats(value: unknown): Record<string, MinecraftStatValue> {
	if (!value || typeof value !== 'object') return {};
	const stats: Record<string, MinecraftStatValue> = {};
	for (const [key, raw] of Object.entries(value)) {
		if (!raw || typeof raw !== 'object') continue;
		const candidate = raw as Partial<MinecraftStatValue>;
		const safeKey = sanitizeToken(candidate.key ?? key, '');
		const category = sanitizeToken(candidate.category, '');
		const id = sanitizeResourceId(candidate.id);
		const statValue = normalizeNullableInteger(candidate.value);
		const total = normalizeNullableInteger(candidate.total);
		if (!safeKey || !category || !id || statValue === null || statValue < 0) continue;
		stats[safeKey] = {
			key: safeKey,
			category,
			id,
			label: sanitizeText(candidate.label, 120) || defaultMinecraftStatLabel(category, id),
			value: statValue,
			...(total !== null && total >= statValue ? { total } : {}),
			updatedAtUnixMs: normalizePositiveInteger(candidate.updatedAtUnixMs),
		};
	}
	return stats;
}

function normalizeMinecraftProfile(value: unknown): MinecraftProfile | null {
	if (!value || typeof value !== 'object') return null;
	const candidate = value as Partial<MinecraftProfile>;
	const uuid = sanitizeText(candidate.uuid, 40);
	if (!uuid) return null;
	return {
		uuid,
		name: sanitizeText(candidate.name, 32),
		skinUrl:
			typeof candidate.skinUrl === 'string' && candidate.skinUrl.startsWith('https://')
				? candidate.skinUrl
				: null,
		model: sanitizeText(candidate.model, 16) || null,
		fetchedAtUnixMs: normalizePositiveInteger(candidate.fetchedAtUnixMs),
	};
}

function sanitizeText(value: unknown, maxLength: number): string {
	return typeof value === 'string' ? value.trim().slice(0, maxLength) : '';
}

function sanitizeResourceId(value: unknown): string {
	if (typeof value !== 'string') return '';
	const trimmed = value.trim().toLowerCase();
	return /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(trimmed) ? trimmed : '';
}

function minecraftStatKey(category: string, id: string): string {
	return `minecraft.${sanitizeToken(category, 'custom')}.${id}`;
}

function defaultMinecraftStatLabel(category: string, id: string): string {
	const name = humanizeResourcePath(id.split(':').pop() ?? id);
	if (category === 'killed') return `${name} Killed`;
	if (category === 'killed_by') return `Killed By ${name}`;
	return name;
}

function humanizeResourcePath(value: string): string {
	return value
		.replace(/^minecraft:/, '')
		.split(/[_/.-]+/)
		.filter(Boolean)
		.map(titleCase)
		.join(' ');
}

function titleCase(value: string): string {
	return value ? value.charAt(0).toUpperCase() + value.slice(1) : value;
}

function groupRank(group: StatOption['group']): number {
	return { profile: 0, money: 1, fishing: 2, minecraft: 3 }[group];
}
