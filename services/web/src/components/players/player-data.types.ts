export type StatGroup = 'profile' | 'money' | 'fishing' | 'minecraft';
export type SortDirection = 'desc' | 'asc';

export interface StatOption {
	key: string;
	label: string;
	group: StatGroup;
	category?: string;
}

export interface PlayerProfile {
	preferredName: string;
	pronouns: string;
	courseYear: string;
	discordUsername: string;
	base: { x: number | null; y: number | null; z: number | null };
	bio: string;
	color: string;
	defaultColor: string;
	customColor: string | null;
	showDeathCounter: boolean;
	updatedAtUnixMs: number;
}

export interface MinecraftStatValue {
	key: string;
	category: string;
	id: string;
	label: string;
	value: number;
	updatedAtUnixMs: number;
}

interface MinecraftProfile {
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
		stats: Partial<Record<string, MinecraftStatValue>>;
		lastSyncedAtUnixMs: number | null;
		lastPlayedAtUnixMs: number | null;
	};
	minecraftProfile: MinecraftProfile | null;
}

export interface PlayerSummary {
	id: number;
	minecraftUsername: string;
	avatarUrl: string | null;
	isCurrentUser: boolean;
	canEditProfile: boolean;
	isMember: boolean;
	isCommittee: boolean;
	isExternal: boolean;
	responsibleMinecraftUsername: string | null;
	responsiblePlayerColor: string | null;
	profile: PlayerProfile;
	fishing: Record<string, number>;
	stats: PlayerStats;
}

export interface PlayersResponse {
	currentUserId: number;
	currentUserMinecraftUsername: string;
	statOptions: StatOption[];
	players: PlayerSummary[];
	selectedPlayer: PlayerSummary | null;
	requestedPlayer: string | null;
	page: number;
	pageSize: number;
	hasMore: boolean;
}

export const DEFAULT_COLUMN_KEYS = [
	'profile.preferredName',
	'minecraft.lastPlayedAtUnixMs',
	'minecraft.custom.minecraft:play_time',
	'minecraft.custom.minecraft:deaths',
];
export const DEFAULT_LEADERBOARD_KEY = 'minecraft.advancement.minecraft:earned';
export const PROFILE_TEXT_LIMITS = {
	preferredName: 16,
	pronouns: 16,
	courseYear: 64,
	discordUsername: 40,
	bio: 280,
} as const;
