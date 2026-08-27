export type AdminSection =
	| 'members'
	| 'claims'
	| 'server-claims'
	| 'whitelist'
	| 'bans'
	| 'gifts'
	| 'countdowns'
	| 'discord-commands'
	| 'dailies'
	| 'servers'
	| 'maintenance';

export interface AdminPlayer {
	id: number;
	minecraftUsername: string;
	color: string;
	discordUsername: string;
	email: string;
	isMember: boolean;
	isCommittee: boolean;
	isExternal: boolean;
}

export interface GiftCode {
	code: string;
	amountDabloons: number;
	redemptionMode: 'single' | 'per_user';
	membersOnly: boolean;
	expiresAtUnixMs: number | null;
	createdAtUnixMs: number;
	redemptionCount: number;
}

export interface WhitelistedEmail {
	email: string;
	addedByMinecraftUsername: string;
	addedByColor: string;
	responsibleMinecraftUsername: string | null;
	responsiblePlayerColor: string | null;
	createdAtUnixMs: number;
}

export interface AdminClaim {
	id: string;
	name: string;
	dimension: string;
	chunkX: number;
	chunkZ: number;
	minecraftUsername: string;
	color: string;
}

export interface ActivePlayerBan {
	userId: number;
	minecraftUsername: string;
	color: string;
	bannedByMinecraftUsername: string;
	expiresAtUnixMs: number | null;
	createdAtUnixMs: number;
}

export interface DiscordAdminCommand {
	command: string;
	discordUsername: string;
	createdAtUnixMs: number;
}

export const ADMIN_PAGE_SIZE = 42;

export function normalizeAdminSection(section: string | undefined): AdminSection {
	return section === 'claims' ||
		section === 'server-claims' ||
		section === 'whitelist' ||
		section === 'bans' ||
		section === 'gifts' ||
		section === 'countdowns' ||
		section === 'discord-commands' ||
		section === 'dailies' ||
		section === 'servers' ||
		section === 'maintenance'
		? section
		: 'members';
}
