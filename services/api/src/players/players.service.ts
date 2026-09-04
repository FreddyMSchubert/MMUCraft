import {
	BadRequestException,
	ForbiddenException,
	Injectable,
	Logger,
	NotFoundException,
} from '@nestjs/common';
import { asc, eq, sql } from 'drizzle-orm';
import { AuthenticatedUser } from '../auth/auth-session.service';
import { FishingService } from '../fishing/fishing.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import {
	DatabaseService,
	UserRow,
	knowledgeUnlocks,
	playerProfiles,
	shopUnlocks,
	users,
} from '../database/database.service';
import { KnowledgeDocumentCatalogService } from '../gameplay/knowledge/knowledge-document-catalog.service';
import { ShopItemCatalogService } from '../gameplay/shop/shop-item-catalog.service';
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

interface UnlockProgress {
	charms: { unlocked: number; total: number };
	cosmetics: { unlocked: number; total: number };
	knowledge: { unlocked: number; total: number };
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
	unlocks: UnlockProgress;
	ranks: Record<string, number>;
}

@Injectable()
export class PlayersService {
	private readonly logger = new Logger(PlayersService.name);
	constructor(
		private readonly database: DatabaseService,
		private readonly fishing: FishingService,
		private readonly knowledgeCatalog: KnowledgeDocumentCatalogService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly profiles: PlayerProfileStorageService,
		private readonly shopCatalog: ShopItemCatalogService,
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

		const statsContext = this.getStatsContext();
		const catchCounts = this.fishing.getCatchCountsForUsers(pageRows.map((user) => user.id));
		const players = pageRows.map((user) =>
			this.serializePlayer(user, viewer, catchCounts.get(user.id), statsContext),
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
				this.serializePlayer(selectedRow, viewer, undefined, statsContext))
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

		const statsContext = this.getStatsContext();
		return {
			currentUserId: viewer.id,
			statOptions: buildStatOptions([this.playerStatistics.getForUser(user.id)]),
			player: this.serializePlayer(user, viewer, undefined, statsContext),
		};
	}

	async updateOwnProfile(user: AuthenticatedUser, input: PlayerProfileInput) {
		return await this.updateProfile(user, String(user.id), input);
	}

	async getCurrentLocation(viewer: AuthenticatedUser, userIdInput: string) {
		const target = this.requireEditablePlayer(viewer, userIdInput);
		const response = await this.minecraft
			.gameplay<{
				online: boolean;
				block_x: number;
				block_y: number;
				block_z: number;
			}>('GetCurrentClaimChunk', { minecraft_username: target.minecraft_username })
			.catch(() => null);

		if (!response?.online) {
			throw new BadRequestException(
				`${target.minecraft_username} must be online in Minecraft to use their current location.`,
			);
		}

		return { x: response.block_x, y: response.block_y, z: response.block_z };
	}

	async updateProfile(viewer: AuthenticatedUser, userIdInput: string, input: PlayerProfileInput) {
		const target = this.requireEditablePlayer(viewer, userIdInput);

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
		await this.synchronizePlayerPresentation(target.id);

		return {
			ok: true,
			profile: savedProfile,
		};
	}

	private serializePlayer(
		user: UserRow,
		viewer: AuthenticatedUser,
		fishing = this.fishing.getCatchCounts(user.id),
		statsContext = this.getStatsContext(),
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
			stats: statsContext.stats.get(user.id) ?? this.playerStatistics.getForUser(user.id),
			unlocks: statsContext.unlocks.get(user.id) ?? emptyUnlockProgress(),
			ranks: statsContext.ranks.get(user.id) ?? {},
		};
	}

	private getStatsContext() {
		const userIds = this.database.connection
			.select({ id: users.id })
			.from(users)
			.all()
			.map((user) => user.id);
		const stats = new Map(userIds.map((id) => [id, this.playerStatistics.getForUser(id)]));
		const unlocks = this.getUnlockProgress(userIds);
		const values = new Map<number, Record<string, number>>();

		for (const id of userIds) {
			const playerStats = stats.get(id);
			const progress = unlocks.get(id);
			if (!playerStats || !progress) continue;
			values.set(id, {
				'money.earnedDabloons': playerStats.money.earnedDabloons,
				'unlocks.charms': progress.charms.unlocked,
				'unlocks.cosmetics': progress.cosmetics.unlocked,
				'unlocks.knowledge': progress.knowledge.unlocked,
				...Object.fromEntries(
					Object.values(playerStats.minecraft.stats).map((stat) => [
						stat.key,
						stat.value,
					]),
				),
			});
		}

		return { stats, unlocks, ranks: rankPlayers(values) };
	}

	private getUnlockProgress(userIds: number[]) {
		const shopItems = this.shopCatalog.load().items;
		const charmIds = new Set(
			shopItems.filter((item) => item.type === 'charm').map((item) => item.id),
		);
		const cosmeticIds = new Set(
			shopItems.filter((item) => item.type === 'cosmetic').map((item) => item.id),
		);
		const knowledge = this.knowledgeCatalog.loadDocument();
		const knowledgeIds = new Set(knowledge.unlockable.map((page) => page.id));
		const publicKnowledge = knowledge.pages.length - knowledge.unlockable.length;
		const unlocked = new Map(userIds.map((id) => [id, emptyUnlockProgress()]));

		for (const row of this.database.connection
			.select({ userId: shopUnlocks.user_id, itemId: shopUnlocks.item_id })
			.from(shopUnlocks)
			.all()) {
			const progress = unlocked.get(row.userId);
			if (!progress) continue;
			if (charmIds.has(row.itemId)) progress.charms.unlocked++;
			if (cosmeticIds.has(row.itemId)) progress.cosmetics.unlocked++;
		}
		for (const row of this.database.connection
			.select({
				userId: knowledgeUnlocks.user_id,
				knowledgeId: knowledgeUnlocks.knowledge_id,
			})
			.from(knowledgeUnlocks)
			.all()) {
			const progress = unlocked.get(row.userId);
			if (progress && knowledgeIds.has(row.knowledgeId)) progress.knowledge.unlocked++;
		}
		for (const progress of unlocked.values()) {
			progress.charms.total = charmIds.size;
			progress.cosmetics.total = cosmeticIds.size;
			progress.knowledge.unlocked += publicKnowledge;
			progress.knowledge.total = knowledge.pages.length;
		}
		return unlocked;
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

	async synchronizePlayerPresentation(userId: number) {
		const target = this.findUserById(userId);
		if (!target?.minecraft_uuid) return;

		await this.applyPlayerPresentation(target, this.profiles.get(userId)).catch(
			(error: unknown) => {
				this.logger.warn(
					`Could not immediately synchronize player presentation to Minecraft: ${String(error)}`,
				);
			},
		);
	}

	private findUserById(userId: number): UserRow | null {
		return (
			this.database.connection.select().from(users).where(eq(users.id, userId)).get() ?? null
		);
	}

	private requireEditablePlayer(viewer: AuthenticatedUser, userIdInput: string) {
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
		return target;
	}
}

function emptyUnlockProgress(): UnlockProgress {
	return {
		charms: { unlocked: 0, total: 0 },
		cosmetics: { unlocked: 0, total: 0 },
		knowledge: { unlocked: 0, total: 0 },
	};
}

function rankPlayers(values: Map<number, Record<string, number>>) {
	const byStat = new Map<string, { id: number; value: number }[]>();
	for (const [id, playerValues] of values) {
		for (const [key, value] of Object.entries(playerValues)) {
			const entries = byStat.get(key) ?? [];
			entries.push({ id, value });
			byStat.set(key, entries);
		}
	}
	const ranks = new Map<number, Record<string, number>>([...values.keys()].map((id) => [id, {}]));
	for (const [key, entries] of byStat) {
		entries.sort((left, right) => right.value - left.value);
		let previousRank = 0;
		entries.forEach((entry, index) => {
			const rank =
				index > 0 && entry.value === entries[index - 1]?.value ? previousRank : index + 1;
			previousRank = rank;
			const playerRanks = ranks.get(entry.id);
			if (rank <= 10 && playerRanks) playerRanks[key] = rank;
		});
	}
	return ranks;
}

function normalizePage(value: string | undefined): number {
	const page = Number(value);
	return Number.isSafeInteger(page) && page >= 0 && Number.isSafeInteger(page * PLAYER_PAGE_SIZE)
		? page
		: 0;
}
