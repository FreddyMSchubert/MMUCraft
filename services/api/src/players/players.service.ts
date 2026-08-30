import { ForbiddenException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { asc, eq, sql } from 'drizzle-orm';
import { AuthenticatedUser } from '../auth/auth-session.service';
import { FishingService } from '../fishing/fishing.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { DatabaseService, UserRow, playerProfiles, users } from '../database/database.service';
import { playerAvatarUrl } from './player-color';
import {
	normalizeProfileInput,
	type PlayerProfile,
	type PlayerProfileInput,
} from './player-profile';
import { PlayerProfileStorageService } from './player-profile-storage.service';
import { buildStatOptions, type PlayerStats } from './player-statistics';
import { PlayerStatisticsSynchronizationService } from './player-statistics-synchronization.service';

const PLAYER_PAGE_SIZE = 42;

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

@Injectable()
export class PlayersService {
	private readonly logger = new Logger(PlayersService.name);
	constructor(
		private readonly database: DatabaseService,
		private readonly fishing: FishingService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly profiles: PlayerProfileStorageService,
		private readonly playerStatistics: PlayerStatisticsSynchronizationService,
	) {}

	listPlayers(
		viewer: AuthenticatedUser,
		pageInput?: string,
		selectedMinecraftUsernameInput?: string,
	) {
		const page = normalizePage(pageInput);
		const userRows = this.database.connection
			.select()
			.from(users)
			.orderBy(asc(sql`lower(${users.minecraft_username})`))
			.limit(PLAYER_PAGE_SIZE + 1)
			.offset(page * PLAYER_PAGE_SIZE)
			.all();
		const hasMore = userRows.length > PLAYER_PAGE_SIZE;
		const pageRows = userRows.slice(0, PLAYER_PAGE_SIZE);

		const catchCounts = this.fishing.getCatchCountsForUsers(pageRows.map((user) => user.id));
		const players = pageRows.map((user) =>
			this.serializePlayer(user, viewer, catchCounts.get(user.id)),
		);
		const selectedMinecraftUsername = selectedMinecraftUsernameInput?.trim() ?? '';
		const selectedRow = selectedMinecraftUsername
			? (this.database.connection
					.select()
					.from(users)
					.where(
						sql`lower(${users.minecraft_username}) = ${selectedMinecraftUsername.toLowerCase()}`,
					)
					.get() ?? null)
			: null;
		const selectedPlayer = selectedRow
			? (players.find((player) => player.id === selectedRow.id) ??
				this.serializePlayer(selectedRow, viewer))
			: null;
		const stats = selectedPlayer
			? [...players.map((player) => player.stats), selectedPlayer.stats]
			: players.map((player) => player.stats);

		return {
			currentUserId: viewer.id,
			currentUserMinecraftUsername: viewer.minecraftUsername,
			statOptions: buildStatOptions(stats),
			players,
			selectedPlayer,
			requestedPlayer: selectedMinecraftUsername || null,
			page,
			pageSize: PLAYER_PAGE_SIZE,
			hasMore,
		};
	}

	getPlayer(viewer: AuthenticatedUser, userIdInput: string) {
		const userId = Number(userIdInput);
		if (!Number.isInteger(userId) || userId <= 0) {
			throw new NotFoundException('Player not found');
		}

		const user = this.findUserById(userId);
		if (!user) {
			throw new NotFoundException('Player not found');
		}

		return {
			currentUserId: viewer.id,
			statOptions: buildStatOptions([this.playerStatistics.getForUser(user.id)]),
			player: this.serializePlayer(user, viewer),
		};
	}

	async updateOwnProfile(user: AuthenticatedUser, input: PlayerProfileInput) {
		return await this.updateProfile(user, String(user.id), input);
	}

	async updateProfile(viewer: AuthenticatedUser, userIdInput: string, input: PlayerProfileInput) {
		const userId = Number(userIdInput);
		if (!Number.isInteger(userId) || userId <= 0) {
			throw new NotFoundException('Player not found');
		}
		if (userId !== viewer.id && !viewer.isCommittee) {
			throw new ForbiddenException(
				'Committee access is required to edit another player profile',
			);
		}
		const target = this.findUserById(userId);
		if (!target) throw new NotFoundException('Player not found');

		const now = Date.now();
		const profile = normalizeProfileInput(input, this.profiles.get(target.id).showDeathCounter);

		const values = {
			user_id: target.id,
			preferred_name: profile.preferredName,
			pronouns: profile.pronouns,
			course_year: profile.courseYear,
			discord_username: profile.discordUsername,
			base_x: profile.base.x,
			base_y: profile.base.y,
			base_z: profile.base.z,
			bio: profile.bio,
			color_hex: profile.customColor,
			show_death_counter: profile.showDeathCounter ? 1 : 0,
			updated_at_unix_ms: now,
		};
		this.database.connection
			.insert(playerProfiles)
			.values(values)
			.onConflictDoUpdate({ target: playerProfiles.user_id, set: values })
			.run();

		const savedProfile = this.profiles.get(target.id);
		const minecraftUuid = target.minecraft_uuid;
		if (minecraftUuid) {
			await this.applyPlayerPresentation(target, savedProfile).catch((error: unknown) => {
				this.logger.warn(
					`Could not immediately synchronize player presentation to Minecraft: ${String(error)}`,
				);
			});
		}

		return {
			ok: true,
			profile: savedProfile,
		};
	}

	private serializePlayer(
		user: UserRow,
		viewer: AuthenticatedUser,
		fishing = this.fishing.getCatchCounts(user.id),
	): PlayerSummary {
		const responsible =
			user.responsible_user_id === null ? null : this.findUserById(user.responsible_user_id);
		const profile = this.profiles.get(user.id);
		return {
			id: user.id,
			minecraftUsername: user.minecraft_username,
			avatarUrl: playerAvatarUrl(user.minecraft_uuid),
			isCurrentUser: user.id === viewer.id,
			canEditProfile: user.id === viewer.id || viewer.isCommittee,
			isMember: user.is_member === 1,
			isCommittee: user.is_committee === 1,
			isExternal: user.responsible_user_id !== null,
			responsibleMinecraftUsername: responsible?.minecraft_username ?? null,
			responsiblePlayerColor: responsible ? this.profiles.get(responsible.id).color : null,
			profile,
			fishing,
			stats: this.playerStatistics.getForUser(user.id),
		};
	}

	private async applyPlayerPresentation(target: UserRow, profile: PlayerProfile) {
		const response = await this.minecraft.gameplay<{ applied: boolean }>(
			'ApplyPlayerPresentation',
			{
				minecraft_uuid: target.minecraft_uuid,
				nickname: profile.preferredName,
				pronouns: profile.pronouns,
				color_hex: profile.color,
				show_death_counter: profile.showDeathCounter,
				is_member: target.is_member === 1,
				is_committee: target.is_super_admin === 1 || target.is_committee === 1,
				is_external: target.responsible_user_id !== null,
			},
		);
		if (!response.applied) throw new Error('Minecraft server refused the player presentation');
	}

	private findUserById(userId: number): UserRow | null {
		return (
			this.database.connection.select().from(users).where(eq(users.id, userId)).get() ?? null
		);
	}
}

function normalizePage(value: string | undefined): number {
	const page = Number(value);
	return Number.isSafeInteger(page) && page >= 0 && Number.isSafeInteger(page * PLAYER_PAGE_SIZE)
		? page
		: 0;
}
