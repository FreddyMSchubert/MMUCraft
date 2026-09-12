import type { PlayerEmoji } from '@/components/player-name';

export type AdminSection =
	| 'members'
	| 'announcements'
	| 'emojis'
	| 'claims'
	| 'server-claims'
	| 'whitelist'
	| 'bans'
	| 'gifts'
	| 'countdowns'
	| 'commands'
	| 'signin-attempts'
	| 'dailies'
	| 'toggles'
	| 'servers'
	| 'maintenance'
	| 'launch';

export interface AdminPlayer {
	id: number;
	minecraftUsername: string;
	color: string;
	discordUsername: string;
	email: string;
	isMember: boolean;
	isCommittee: boolean;
	isExternal: boolean;
	customEmojis: PlayerEmoji[];
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
	responsibleIsCommittee: boolean;
	responsibleCustomEmojis: PlayerEmoji[];
	addedByIsCommittee: boolean;
	addedByCustomEmojis: PlayerEmoji[];
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
	isCommittee: boolean;
	customEmojis: PlayerEmoji[];
}

export interface ActivePlayerBan {
	userId: number;
	minecraftUsername: string;
	color: string;
	isCommittee: boolean;
	customEmojis: PlayerEmoji[];
	bannedByMinecraftUsername: string;
	bannedByIsCommittee: boolean;
	bannedByCustomEmojis: PlayerEmoji[];
	expiresAtUnixMs: number | null;
	createdAtUnixMs: number;
}

export interface CommandLogEntry {
	id: number;
	command: string;
	source: 'minecraft' | 'discord';
	actorName: string;
	userId: number | null;
	isOperator: boolean;
	succeeded: boolean | null;
	result: number | null;
	createdAtUnixMs: number;
}

export interface SigninAttemptLogEntry {
	id: number;
	email: string | null;
	journey: 'signin' | 'signup';
	event:
		| 'email_send'
		| 'email_resend'
		| 'email_code_input'
		| 'minecraft_username_input'
		| 'minecraft_code_input'
		| 'rules_accept';
	succeeded: boolean | null;
	detail: string | null;
	createdAtUnixMs: number;
}

export const ADMIN_PAGE_SIZE = 42;

export function normalizeAdminSection(section: string | undefined): AdminSection {
	return section === 'emojis' ||
		section === 'announcements' ||
		section === 'claims' ||
		section === 'server-claims' ||
		section === 'whitelist' ||
		section === 'bans' ||
		section === 'gifts' ||
		section === 'countdowns' ||
		section === 'commands' ||
		section === 'signin-attempts' ||
		section === 'dailies' ||
		section === 'toggles' ||
		section === 'servers' ||
		section === 'maintenance' ||
		section === 'launch'
		? section
		: 'members';
}
