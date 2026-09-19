import { BadRequestException, Injectable } from '@nestjs/common';
import { DatabaseService, users } from '../database/database.service';
import { playerProfiles } from '../database/database.service';
import { eq } from 'drizzle-orm';
import { DiscordService } from '../discord/discord.service';
import { PlayerRoleAdministrationService } from './player-role-administration.service';

interface ImportRow {
	email: string;
	discordName: string;
}

@Injectable()
export class MembershipImportService {
	constructor(
		private readonly database: DatabaseService,
		private readonly discord: DiscordService,
		private readonly roles: PlayerRoleAdministrationService,
	) {}

	private validate(input: unknown): ImportRow[] {
		if (!Array.isArray(input) || !input.length || input.length > 500)
			throw new BadRequestException('Upload between 1 and 500 membership rows');
		return input.map((row: unknown) => {
			if (!row || typeof row !== 'object')
				throw new BadRequestException('Each row needs an email and Discord name');
			const value = row as Record<string, unknown>;
			if (typeof value.email !== 'string' || typeof value.discordName !== 'string')
				throw new BadRequestException('Each row needs an email and Discord name');
			const email = value.email.trim().toLowerCase();
			const discordName = value.discordName.trim();
			if (!email.includes('@') || email.length > 254 || discordName.length > 100)
				throw new BadRequestException('Invalid email or Discord name');
			return { email, discordName };
		});
	}

	async preview(input: unknown) {
		const rows = this.validate(input);
		const accounts = this.database.connection
			.select({
				id: users.id,
				email: users.email,
				minecraftUsername: users.minecraft_username,
				isMember: users.is_member,
				profileDiscordName: playerProfiles.discord_username,
			})
			.from(users)
			.leftJoin(playerProfiles, eq(playerProfiles.user_id, users.id))
			.all();
		const byEmail = new Map(accounts.map((account) => [account.email.toLowerCase(), account]));
		let context: Awaited<ReturnType<DiscordService['membershipRoleContext']>> | null = null;
		let discordIssue: string | null = null;
		try {
			context = await this.discord.membershipRoleContext();
		} catch (error) {
			discordIssue =
				error instanceof Error ? error.message : 'Discord members are unavailable';
		}
		const normalize = (name: string) => name.trim().toLocaleLowerCase('en');
		const byName = new Map<string, string[]>();
		for (const member of context?.members.values() ?? []) {
			const key = normalize(member.user.username);
			const ids = byName.get(key) ?? [];
			ids.push(member.id);
			byName.set(key, ids);
		}
		const emailCounts = new Map<string, number>();
		const nameCounts = new Map<string, number>();
		for (const row of rows) {
			emailCounts.set(row.email, (emailCounts.get(row.email) ?? 0) + 1);
			const name = row.discordName.length
				? row.discordName
				: (byEmail.get(row.email)?.profileDiscordName ?? '');
			if (name) nameCounts.set(normalize(name), (nameCounts.get(normalize(name)) ?? 0) + 1);
		}
		return {
			discordIssue,
			rows: rows.map((row) => {
				const account = byEmail.get(row.email);
				const discordName = row.discordName.length
					? row.discordName
					: (account?.profileDiscordName ?? '');
				const ids = byName.get(normalize(discordName)) ?? [];
				const discordId =
					discordName &&
					ids.length === 1 &&
					nameCounts.get(normalize(discordName)) === 1 &&
					emailCounts.get(row.email) === 1 &&
					account
						? ids[0]
						: null;
				const member = discordId ? context?.members.get(discordId) : null;
				return {
					...row,
					discordName,
					discordNameSource: row.discordName
						? 'file'
						: account?.profileDiscordName
							? 'player profile'
							: null,
					userId: emailCounts.get(row.email) === 1 ? (account?.id ?? null) : null,
					minecraftUsername: account?.minecraftUsername ?? null,
					apiStatus:
						emailCounts.get(row.email) !== 1
							? 'duplicate email in file'
							: !account
								? 'no account for email'
								: account.isMember
									? 'already member'
									: 'ready',
					discordId,
					discordLabel: member ? `${member.user.username} (${member.id})` : null,
					discordStatus: !context
						? 'Discord unavailable'
						: !account || emailCounts.get(row.email) !== 1
							? 'email unmatched'
							: !discordName
								? 'no Discord name'
								: nameCounts.get(normalize(discordName)) !== 1
									? 'duplicate name in file'
									: ids.length === 0
										? 'no server member found'
										: ids.length > 1
											? 'ambiguous server name'
											: member?.roles.cache.has(context.role.id)
												? 'already has role'
												: 'ready',
				};
			}),
		};
	}

	async apply(input: unknown) {
		const preview = await this.preview(input);
		let context: Awaited<ReturnType<DiscordService['membershipRoleContext']>> | null = null;
		let discordIssue = preview.discordIssue;
		try {
			context = await this.discord.membershipRoleContext();
		} catch {
			/* API membership can still be imported. */
		}
		const results = [];
		for (const row of preview.rows) {
			let apiStatus = row.apiStatus;
			let discordStatus = row.discordStatus;
			if (!context && discordStatus === 'ready') discordStatus = 'Discord unavailable';
			if (row.apiStatus === 'ready' && row.userId) {
				try {
					await this.roles.setMembership(String(row.userId), true);
					apiStatus = 'updated';
				} catch {
					apiStatus = 'update failed';
				}
			}
			if (
				row.discordStatus === 'ready' &&
				row.discordId &&
				context &&
				row.userId &&
				apiStatus !== 'update failed'
			) {
				try {
					const member = await context.guild.members.fetch(row.discordId);
					if (
						member.user.username.trim().toLocaleLowerCase('en') !==
						row.discordName.trim().toLocaleLowerCase('en')
					)
						throw new Error('Discord username changed');
					await member.roles.add(context.role, '26/27 society membership import');
					discordStatus = 'role added';
				} catch {
					discordStatus = 'role update failed';
				}
			}
			results.push({ ...row, apiStatus, discordStatus });
		}
		if (!context && !discordIssue) discordIssue = 'The bot disconnected during import';
		return { rows: results, discordIssue };
	}
}
