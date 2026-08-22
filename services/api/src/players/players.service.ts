import { BadRequestException, ForbiddenException, Injectable, Logger, NotFoundException } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { randomUUID } from 'node:crypto'
import { eq } from 'drizzle-orm'
import { AuthenticatedUser } from '../auth/auth.service'
import { MinecraftIdentityService } from '../database/minecraft-identity.service'
import { FishingService } from '../fishing/fishing.service'
import { GrpcServerService } from '../grpc/grpc-server.service'
import { callUnary } from '../grpc/grpc.types'
import {
	DatabaseService,
	PlayerProfileRow,
	PlayerStatsRow,
	UserRow,
	playerMoneyEvents,
	playerProfiles,
	playerStats,
	users,
} from '../database/database.service'
import { effectivePlayerColor, normalizeOptionalColor, playerAvatarUrl } from './player-color'

const STATS_VERSION = 1
const MOJANG_PROFILE_TTL_MS = 7 * 24 * 60 * 60 * 1000
const MOJANG_FETCH_TIMEOUT_MS = 5_000
const ONLINE_PLAYERS_RECONCILE_MS = 5 * 60 * 1000
const PROFILE_TEXT_LIMITS = {
	preferredName: 16,
	pronouns: 16,
	courseYear: 64,
	discordUsername: 40,
	bio: 280,
} as const

type MoneyDirection = 'earned'

export interface StatOption {
	key: string
	label: string
	group: 'profile' | 'money' | 'fishing' | 'minecraft'
	category?: string
}

interface PlayerProfile {
	preferredName: string
	pronouns: string
	courseYear: string
	discordUsername: string
	base: {
		x: number | null
		y: number | null
		z: number | null
	}
	bio: string
	color: string
	defaultColor: string
	customColor: string | null
	showDeathCounter: boolean
	updatedAtUnixMs: number
}

interface GameplayControlClient extends grpc.Client {
	GetOnlinePlayers(
		request: Record<string, never>,
		options: grpc.CallOptions,
		callback: (error: grpc.ServiceError | null, response: {
			players: Array<{ minecraft_username: string; minecraft_uuid: string }>
		}) => void,
	): void
	ApplyPlayerColor(
		request: { minecraft_uuid: string; color_hex: string },
		options: grpc.CallOptions,
		callback: (error: grpc.ServiceError | null, response: { applied: boolean }) => void,
	): void
	ApplyPlayerSettings(
		request: { minecraft_uuid: string; show_death_counter: boolean },
		options: grpc.CallOptions,
		callback: (error: grpc.ServiceError | null, response: { applied: boolean }) => void,
	): void
}

interface GameplayProtoRoot {
	mcstack: { gameplay: { v1: { GameplayControl: grpc.ServiceClientConstructor } } }
}

interface MinecraftStatValue {
	key: string
	category: string
	id: string
	label: string
	value: number
	updatedAtUnixMs: number
}

interface MinecraftProfile {
	uuid: string
	name: string
	skinUrl: string | null
	model: string | null
	fetchedAtUnixMs: number
}

interface PlayerStats {
	version: number
	money: {
		earnedDabloons: number
		balanceDabloons: number | null
		lastUpdatedAtUnixMs: number | null
		sources: Record<string, {
			earnedDabloons: number
		}>
	}
	minecraft: {
		stats: Record<string, MinecraftStatValue>
		lastSyncedAtUnixMs: number | null
		lastPlayedAtUnixMs: number | null
	}
	minecraftProfile: MinecraftProfile | null
}

export interface MinecraftStatInput {
	key?: string
	category?: string
	id?: string
	label?: string
	value?: number
}

export interface PlayerSummary {
	id: number
	minecraftUsername: string
	avatarUrl: string | null
	isCurrentUser: boolean
	canEditProfile: boolean
	isMember: boolean
	isCommittee: boolean
	isExternal: boolean
	responsibleMinecraftUsername: string | null
	responsiblePlayerColor: string | null
	profile: PlayerProfile
	fishing: Record<string, number>
	stats: PlayerStats
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
]

const MONEY_OPTIONS: StatOption[] = [
	{ key: 'money.earnedDabloons', label: 'Dabloons Earned', group: 'money' },
]

const FISHING_OPTIONS: StatOption[] = [
	{ key: 'fishing.total', label: 'Fish Species Caught', group: 'fishing' },
	...['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythical'].map((rarity) => ({
		key: `fishing.${rarity}`,
		label: `${rarity.charAt(0).toUpperCase()}${rarity.slice(1)} Fish Species Caught`,
		group: 'fishing' as const,
	})),
]

const SESSION_OPTIONS: StatOption[] = [
	{ key: 'minecraft.lastPlayedAtUnixMs', label: 'Last Played', group: 'minecraft', category: 'session' },
]

const CUSTOM_MINECRAFT_STATS = ([
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
] satisfies Array<[string, string]>).map(([id, label]) => ({ id, label }))

const ENTITY_IDS = [
	'allay',
	'armadillo',
	'axolotl',
	'bat',
	'bee',
	'blaze',
	'bogged',
	'breeze',
	'camel',
	'cat',
	'cave_spider',
	'chicken',
	'cod',
	'cow',
	'creeper',
	'dolphin',
	'donkey',
	'drowned',
	'elder_guardian',
	'ender_dragon',
	'enderman',
	'endermite',
	'evoker',
	'fox',
	'frog',
	'ghast',
	'glow_squid',
	'goat',
	'guardian',
	'hoglin',
	'horse',
	'husk',
	'iron_golem',
	'llama',
	'magma_cube',
	'mooshroom',
	'mule',
	'ocelot',
	'panda',
	'parrot',
	'phantom',
	'pig',
	'piglin',
	'piglin_brute',
	'pillager',
	'polar_bear',
	'pufferfish',
	'rabbit',
	'ravager',
	'salmon',
	'sheep',
	'shulker',
	'silverfish',
	'skeleton',
	'skeleton_horse',
	'slime',
	'sniffer',
	'snow_golem',
	'spider',
	'squid',
	'stray',
	'strider',
	'tadpole',
	'trader_llama',
	'tropical_fish',
	'turtle',
	'vex',
	'villager',
	'vindicator',
	'wandering_trader',
	'warden',
	'witch',
	'wither',
	'wither_skeleton',
	'wolf',
	'zoglin',
	'zombie',
	'zombie_horse',
	'zombie_villager',
	'zombified_piglin',
]

const KNOWN_MINECRAFT_OPTIONS: StatOption[] = [
	{
		key: minecraftStatKey('advancement', 'minecraft:earned'),
		label: 'Advancements Earned',
		group: 'minecraft',
		category: 'advancement',
	},
	...CUSTOM_MINECRAFT_STATS.map((stat) => ({
		key: minecraftStatKey('custom', stat.id),
		label: stat.label,
		group: 'minecraft' as const,
		category: 'custom',
	})),
	...ENTITY_IDS.flatMap((entityId) => {
		const id = `minecraft:${entityId}`
		const label = humanizeResourcePath(entityId)

		return [
			{
				key: minecraftStatKey('killed', id),
				label: `${label} Killed`,
				group: 'minecraft' as const,
				category: 'killed',
			},
			{
				key: minecraftStatKey('killed_by', id),
				label: `Killed By ${label}`,
				group: 'minecraft' as const,
				category: 'killed_by',
			},
		]
	}),
]

@Injectable()
export class PlayersService {
	private readonly logger = new Logger(PlayersService.name)
	private gameplayControlClient: GameplayControlClient | null = null
	private readonly onlinePlayers = new Map<string, { minecraft_username: string; minecraft_uuid: string }>()
	private onlinePlayersReconciledAt = 0

	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
		private readonly fishing: FishingService,
		private readonly grpcServer: GrpcServerService,
	) { }

	async listPlayers(viewer: AuthenticatedUser) {
		const userRows = this.database.connection.select().from(users).all()
			.sort((left, right) => left.minecraft_username.localeCompare(right.minecraft_username, 'en', { sensitivity: 'base' }))

		const players = await Promise.all(userRows.map((user) => this.serializePlayer(user, viewer, true)))

		return {
			currentUserId: viewer.id,
			statOptions: this.getStatOptions(players.map((player) => player.stats)),
			players,
		}
	}

	async listOnlinePlayers() {
		if (Date.now() - this.onlinePlayersReconciledAt >= ONLINE_PLAYERS_RECONCILE_MS) {
			await this.reconcileOnlinePlayers()
		}

		return {
			players: [...this.onlinePlayers.values()]
				.sort((left, right) => left.minecraft_username.localeCompare(right.minecraft_username, 'en', { sensitivity: 'base' }))
				.map((player) => {
					const presentation = this.discordPresentation(player.minecraft_uuid)
					return {
						minecraftUsername: presentation.minecraftUsername || player.minecraft_username,
						color: presentation.colorHex,
						role: presentation.role,
					}
				}),
		}
	}

	discordPresentation(minecraftUuid: string) {
		const user = this.identities.findByUuid(minecraftUuid)
		if (!user) return {
			minecraftUsername: '', role: 'Player', nickname: '', pronouns: '',
			colorHex: effectivePlayerColor(minecraftUuid),
		}
		const profile = this.getProfile(user.id)
		return {
			minecraftUsername: user.minecraft_username,
			role: user.is_super_admin === 1 || user.is_committee === 1 ? 'Committee'
				: user.is_member === 1 ? 'Member'
					: user.responsible_user_id !== null ? 'External' : 'Player',
			nickname: profile.preferredName,
			pronouns: profile.pronouns,
			colorHex: profile.color,
		}
	}

	recordPresenceEvent(event: { type: string; minecraft_username: string; minecraft_uuid: string }) {
		if (event.type !== 'join' && event.type !== 'first_join' && event.type !== 'leave') return
		const key = onlinePlayerKey(event.minecraft_uuid, event.minecraft_username)
		if (event.type === 'leave') this.onlinePlayers.delete(key)
		else this.onlinePlayers.set(key, {
			minecraft_username: event.minecraft_username,
			minecraft_uuid: event.minecraft_uuid,
		})
	}

	async getPlayer(viewer: AuthenticatedUser, userIdInput: string) {
		const userId = Number(userIdInput)
		if (!Number.isInteger(userId) || userId <= 0) {
			throw new NotFoundException('Player not found')
		}

		const user = this.findUserById(userId)
		if (!user) {
			throw new NotFoundException('Player not found')
		}

		return {
			currentUserId: viewer.id,
			statOptions: this.getStatOptions([this.getStats(user.id)]),
			player: await this.serializePlayer(user, viewer, true),
		}
	}

	async updateOwnProfile(user: AuthenticatedUser, input: Record<string, unknown>) {
		return await this.updateProfile(user, String(user.id), input)
	}

	async updateProfile(viewer: AuthenticatedUser, userIdInput: string, input: Record<string, unknown>) {
		const userId = Number(userIdInput)
		if (!Number.isInteger(userId) || userId <= 0) {
			throw new NotFoundException('Player not found')
		}
		if (userId !== viewer.id && !viewer.isCommittee) {
			throw new ForbiddenException('Committee access is required to edit another player profile')
		}
		const target = this.findUserById(userId)
		if (!target) throw new NotFoundException('Player not found')

		const now = Date.now()
		const profile = this.normalizeProfileInput(input, this.getProfile(target.id).showDeathCounter)

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
		}
		this.database.connection.insert(playerProfiles).values(values)
			.onConflictDoUpdate({ target: playerProfiles.user_id, set: values })
			.run()

		const savedProfile = this.getProfile(target.id)
		const minecraftUuid = target.minecraft_uuid
		if (minecraftUuid) {
			await this.applyPlayerColor(minecraftUuid, savedProfile.color).catch((error) => {
				this.logger.warn(`Could not immediately synchronize player color to Minecraft: ${String(error)}`)
			})
			await this.applyPlayerSettings(minecraftUuid, savedProfile.showDeathCounter).catch((error) => {
				this.logger.warn(`Could not immediately synchronize player settings to Minecraft: ${String(error)}`)
			})
		}

		return {
			ok: true,
			profile: savedProfile,
		}
	}

	syncMinecraftStats(
		minecraftUuidInput: string,
		minecraftUsernameInput: string,
		statsInput: MinecraftStatInput[],
		balanceDabloonsInput: number | null,
		unixMsInput: number | null,
	) {
		const user = this.identities.resolveAndRefresh(minecraftUuidInput, minecraftUsernameInput)
		if (!user) {
			return {
				accepted: false,
				accountLinked: false,
				isMember: false,
				isCommittee: false,
				isExternal: false,
				nickname: '',
				pronouns: '',
				color: effectivePlayerColor(minecraftUuidInput),
				showDeathCounter: true,
				message: 'No website account is linked to this Minecraft username yet.',
			}
		}

		const unixMs = normalizeUnixMs(unixMsInput)
		const stats = this.getStats(user.id)
		const nextMinecraftStats: Record<string, MinecraftStatValue> = { ...stats.minecraft.stats }

		for (const statInput of statsInput) {
			const stat = this.normalizeMinecraftStatInput(statInput, unixMs)
			if (stat) {
				nextMinecraftStats[stat.key] = stat
			}
		}

		stats.minecraft = {
			stats: nextMinecraftStats,
			lastSyncedAtUnixMs: unixMs,
			lastPlayedAtUnixMs: unixMs,
		}

		const balanceDabloons = normalizeNullableInteger(balanceDabloonsInput)
		if (balanceDabloons !== null && balanceDabloons >= 0) {
			stats.money.balanceDabloons = balanceDabloons
			stats.money.lastUpdatedAtUnixMs = unixMs
		}

		this.saveStats(user.id, stats, unixMs)

		const profile = this.getProfile(user.id)
		return {
			accepted: true,
			accountLinked: true,
			isMember: user.is_member === 1,
			isCommittee: user.is_super_admin === 1 || user.is_committee === 1,
			isExternal: user.responsible_user_id !== null,
			nickname: profile.preferredName.slice(0, PROFILE_TEXT_LIMITS.preferredName),
			pronouns: profile.pronouns.slice(0, PROFILE_TEXT_LIMITS.pronouns),
			color: profile.color,
			showDeathCounter: profile.showDeathCounter,
			message: 'Stats synced.',
		}
	}

	recordMoneyForUser(
		userId: number,
		direction: MoneyDirection,
		source: string,
		amountDabloonsInput: number,
		balanceDabloonsInput: number | null,
		eventIdInput?: string,
		unixMsInput?: number,
	) {
		const amountDabloons = normalizePositiveInteger(amountDabloonsInput)
		if (amountDabloons <= 0) {
			return { recorded: false, duplicate: false }
		}

		const user = this.findUserById(userId)
		if (!user) {
			return { recorded: false, duplicate: false }
		}

		return this.recordMoneyEvent(
			user.id,
			direction,
			source,
			amountDabloons,
			normalizeNullableInteger(balanceDabloonsInput),
			eventIdInput,
			unixMsInput,
		)
	}

	async grantKnowledgeReadMoney(minecraftUsername: string, amountDabloons: number) {
		return await callUnary<{ granted: boolean; balance_dabloons: number; message: string }>(
			this.getGameplayControlClient(), 'GrantKnowledgeReadMoney', {
				minecraft_username: minecraftUsername,
				amount_dabloons: amountDabloons,
				message: `Knowledge read: you received ${amountDabloons} dabloons.`,
			},
		)
	}

	recordMoneyForMinecraftUsername(
		minecraftUuidInput: string,
		minecraftUsernameInput: string,
		directionInput: string,
		sourceInput: string,
		amountDabloonsInput: number,
		balanceDabloonsInput: number | null,
		referenceIdInput: string,
		unixMsInput: number | null,
	) {
		const user = this.identities.resolveAndRefresh(minecraftUuidInput, minecraftUsernameInput)
		if (!user) {
			return {
				recorded: false,
				duplicate: false,
				accountLinked: false,
				userId: null,
				message: 'No website account is linked to this Minecraft username yet.',
			}
		}

		const direction = normalizeDirection(directionInput)
		const source = sanitizeToken(sourceInput, 'minecraft')
		const referenceId = sanitizeToken(referenceIdInput, '')
		const eventId = referenceId ? `${source}:${user.id}:${referenceId}` : randomUUID()
		const result = this.recordMoneyEvent(
			user.id,
			direction,
			source,
			normalizePositiveInteger(amountDabloonsInput),
			normalizeNullableInteger(balanceDabloonsInput),
			eventId,
			normalizeUnixMs(unixMsInput),
		)

		return {
			...result,
			accountLinked: true,
			userId: user.id,
			message: result.duplicate ? 'Money event already recorded.' : 'Money event recorded.',
		}
	}

	private async serializePlayer(user: UserRow, viewer: AuthenticatedUser, includeMojangProfile: boolean): Promise<PlayerSummary> {
		let stats = this.getStats(user.id)
		if (includeMojangProfile) {
			stats = await this.ensureMojangProfile(user, stats)
		}

		const responsible = user.responsible_user_id === null ? null : this.findUserById(user.responsible_user_id)
		const profile = this.getProfile(user.id)
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
			responsiblePlayerColor: responsible
				? this.getProfile(responsible.id).color
				: null,
			profile,
			fishing: this.fishing.getCatchCounts(user.id),
			stats,
		}
	}

	private getProfile(userId: number): PlayerProfile {
		const row = this.database.connection.select().from(playerProfiles)
			.where(eq(playerProfiles.user_id, userId)).get()
		const minecraftUuid = this.findUserById(userId)?.minecraft_uuid ?? null

		if (!row) {
			return {
				preferredName: '',
				pronouns: '',
				courseYear: '',
				discordUsername: '',
				base: { x: null, y: null, z: null },
				bio: '',
				color: effectivePlayerColor(minecraftUuid),
				defaultColor: effectivePlayerColor(minecraftUuid),
				customColor: null,
				showDeathCounter: true,
				updatedAtUnixMs: 0,
			}
		}

		return {
			preferredName: row.preferred_name,
			pronouns: row.pronouns,
			courseYear: row.course_year,
			discordUsername: row.discord_username,
			base: {
				x: row.base_x,
				y: row.base_y,
				z: row.base_z,
			},
			bio: row.bio,
			color: effectivePlayerColor(minecraftUuid, row.color_hex),
			defaultColor: effectivePlayerColor(minecraftUuid),
			customColor: row.color_hex,
			showDeathCounter: row.show_death_counter === 1,
			updatedAtUnixMs: row.updated_at_unix_ms,
		}
	}

	private getStats(userId: number): PlayerStats {
		const row = this.database.connection.select().from(playerStats)
			.where(eq(playerStats.user_id, userId)).get()

		if (!row) {
			return defaultStats()
		}

		return normalizeStatsJson(row.stats_json)
	}

	private saveStats(userId: number, stats: PlayerStats, unixMs = Date.now()) {
		const values = {
			user_id: userId,
			stats_json: JSON.stringify(stats),
			updated_at_unix_ms: unixMs,
		}
		this.database.connection.insert(playerStats).values(values)
			.onConflictDoUpdate({ target: playerStats.user_id, set: values })
			.run()
	}

	private recordMoneyEvent(
		userId: number,
		direction: MoneyDirection,
		sourceInput: string,
		amountDabloons: number,
		balanceDabloons: number | null,
		eventIdInput?: string,
		unixMsInput?: number,
	) {
		if (amountDabloons <= 0) {
			return { recorded: false, duplicate: false }
		}

		const source = sanitizeToken(sourceInput, 'website')
		const eventId = sanitizeEventId(eventIdInput) ?? randomUUID()
		const unixMs = normalizeUnixMs(unixMsInput)

		return this.database.connection.transaction((tx) => {
			const inserted = tx.insert(playerMoneyEvents).values({
				id: eventId,
				user_id: userId,
				direction,
				source,
				amount_dabloons: amountDabloons,
				balance_dabloons: balanceDabloons,
				created_at_unix_ms: unixMs,
			}).onConflictDoNothing().run()

			if (inserted.changes !== 1) {
				return { recorded: false, duplicate: true }
			}

			const stats = this.getStats(userId)
			const sourceStats = stats.money.sources[source] ?? {
				earnedDabloons: 0,
			}

			stats.money.earnedDabloons += amountDabloons
			sourceStats.earnedDabloons += amountDabloons

			if (balanceDabloons !== null && balanceDabloons >= 0) {
				stats.money.balanceDabloons = balanceDabloons
			}

			stats.money.sources[source] = sourceStats
			stats.money.lastUpdatedAtUnixMs = unixMs
			this.saveStats(userId, stats, unixMs)

			return { recorded: true, duplicate: false }
		})
	}

	private getStatOptions(statsObjects: PlayerStats[]): StatOption[] {
		const byKey = new Map<string, StatOption>()

		for (const option of [...PROFILE_OPTIONS, ...MONEY_OPTIONS, ...FISHING_OPTIONS, ...SESSION_OPTIONS, ...KNOWN_MINECRAFT_OPTIONS]) {
			byKey.set(option.key, option)
		}

		for (const stats of statsObjects) {
			for (const stat of Object.values(stats.minecraft.stats)) {
				byKey.set(stat.key, {
					key: stat.key,
					label: stat.label || humanizeResourcePath(stat.id),
					group: 'minecraft',
					category: stat.category,
				})
			}
		}

		return [...byKey.values()].sort((left, right) => {
			const groupOrder = groupRank(left.group) - groupRank(right.group)
			if (groupOrder !== 0) return groupOrder
			return left.label.localeCompare(right.label, 'en')
		})
	}

	private normalizeProfileInput(input: Record<string, unknown>, currentShowDeathCounter: boolean): PlayerProfile {
		if (input.showDeathCounter !== undefined && typeof input.showDeathCounter !== 'boolean') {
			throw new BadRequestException('Show death counter must be true or false')
		}

		return {
			preferredName: sanitizeProfileText(input.preferredName, PROFILE_TEXT_LIMITS.preferredName, 'Nickname'),
			pronouns: sanitizeProfileText(input.pronouns, PROFILE_TEXT_LIMITS.pronouns, 'Pronouns'),
			courseYear: sanitizeProfileText(input.courseYear, PROFILE_TEXT_LIMITS.courseYear, 'Course / Year'),
			discordUsername: sanitizeProfileText(input.discordUsername, PROFILE_TEXT_LIMITS.discordUsername, 'Discord username'),
			base: {
				x: normalizeCoordinate(input.baseX, 'Base X'),
				y: normalizeCoordinate(input.baseY, 'Base Y'),
				z: normalizeCoordinate(input.baseZ, 'Base Z'),
			},
			bio: sanitizeProfileText(input.bio, PROFILE_TEXT_LIMITS.bio, 'Bio'),
			color: '',
			defaultColor: '',
			customColor: normalizeOptionalColor(input.color),
			showDeathCounter: input.showDeathCounter ?? currentShowDeathCounter,
			updatedAtUnixMs: Date.now(),
		}
	}

	private async applyPlayerColor(minecraftUuid: string, color: string) {
		const response = await callUnary<{ applied: boolean }>(
			this.getGameplayControlClient(), 'ApplyPlayerColor', { minecraft_uuid: minecraftUuid, color_hex: color },
		)
		if (!response.applied) throw new Error('Minecraft server refused the player color')
	}

	private async applyPlayerSettings(minecraftUuid: string, showDeathCounter: boolean) {
		const response = await callUnary<{ applied: boolean }>(
			this.getGameplayControlClient(), 'ApplyPlayerSettings', {
				minecraft_uuid: minecraftUuid,
				show_death_counter: showDeathCounter,
			},
		)
		if (!response.applied) throw new Error('Minecraft server refused the player settings')
	}

	private getGameplayControlClient() {
		if (this.gameplayControlClient) return this.gameplayControlClient
		const gameplayProto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto')
		this.gameplayControlClient = new gameplayProto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		) as unknown as GameplayControlClient
		return this.gameplayControlClient
	}

	private async reconcileOnlinePlayers() {
		this.onlinePlayersReconciledAt = Date.now()
		try {
			const response = await callUnary<{ players: Array<{ minecraft_username: string; minecraft_uuid: string }> }>(
				this.getGameplayControlClient(), 'GetOnlinePlayers', {}, { deadline: Date.now() + 5_000 },
			)
			this.onlinePlayers.clear()
			for (const player of response.players) {
				this.onlinePlayers.set(onlinePlayerKey(player.minecraft_uuid, player.minecraft_username), player)
			}
		} catch (error) {
			this.logger.warn(`Could not reconcile online players with Minecraft: ${String(error)}`)
		}
	}

	private normalizeMinecraftStatInput(input: MinecraftStatInput, unixMs: number): MinecraftStatValue | null {
		const category = sanitizeToken(input.category, '')
		const id = sanitizeResourceId(input.id)
		const value = normalizeNullableInteger(input.value)
		const key = sanitizeToken(input.key, '') || minecraftStatKey(category, id)

		if (!category || !id || !key || value === null || value < 0) {
			return null
		}

		return {
			key,
			category,
			id,
			label: sanitizeText(input.label, 120) || defaultMinecraftStatLabel(category, id),
			value,
			updatedAtUnixMs: unixMs,
		}
	}

	private findUserById(userId: number): UserRow | null {
		return this.database.connection.select().from(users).where(eq(users.id, userId)).get() ?? null
	}

	private async ensureMojangProfile(user: UserRow, stats: PlayerStats): Promise<PlayerStats> {
		const profile = stats.minecraftProfile
		const now = Date.now()
		if (profile) {
			this.identities.resolveAndRefresh(profile.uuid, profile.name)
		}
		if (profile && now - profile.fetchedAtUnixMs < MOJANG_PROFILE_TTL_MS) {
			return stats
		}

		try {
			const nextProfile = await fetchMojangProfile(user.minecraft_username)
			this.identities.resolveAndRefresh(nextProfile.uuid, nextProfile.name)
			const nextStats = {
				...stats,
				minecraftProfile: nextProfile,
			}

			this.saveStats(user.id, nextStats, now)
			return nextStats
		} catch {
			return stats
		}
	}
}

function onlinePlayerKey(minecraftUuid: string, minecraftUsername: string) {
	return minecraftUuid.toLowerCase().replaceAll('-', '') || minecraftUsername.toLowerCase()
}

async function fetchMojangProfile(minecraftUsername: string): Promise<MinecraftProfile> {
	const signal = AbortSignal.timeout(MOJANG_FETCH_TIMEOUT_MS)
	const uuidResponse = await fetch(
		`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(minecraftUsername)}`,
		{ cache: 'no-store', signal },
	)

	if (!uuidResponse.ok) {
		throw new Error('Mojang username lookup failed')
	}

	const uuidBody = await uuidResponse.json().catch(() => null) as { id?: unknown; name?: unknown } | null
	const uuid = typeof uuidBody?.id === 'string' ? uuidBody.id : ''
	const name = typeof uuidBody?.name === 'string' ? uuidBody.name : minecraftUsername
	if (!uuid) {
		throw new Error('Mojang UUID lookup returned no UUID')
	}

	return fetchMojangProfileByUuid(uuid, name, signal)
}

export async function fetchMojangProfileByUuid(uuidInput: string, fallbackName = '', signal?: AbortSignal): Promise<MinecraftProfile> {
	const uuid = uuidInput.replaceAll('-', '')
	if (!/^[0-9a-f]{32}$/i.test(uuid)) throw new Error('Invalid Mojang UUID')
	const profileResponse = await fetch(
		`https://sessionserver.mojang.com/session/minecraft/profile/${encodeURIComponent(uuid)}`,
		{ cache: 'no-store', signal },
	)

	if (!profileResponse.ok) {
		throw new Error('Mojang profile lookup failed')
	}

	const profileBody = await profileResponse.json().catch(() => null) as {
		id?: unknown
		name?: unknown
		properties?: Array<{ name?: unknown; value?: unknown }>
	} | null
	const texturesProperty = profileBody?.properties?.find((property) => property.name === 'textures')
	const textureValue = typeof texturesProperty?.value === 'string' ? texturesProperty.value : ''
	const decoded = textureValue
		? JSON.parse(Buffer.from(textureValue, 'base64').toString('utf8')) as {
			textures?: {
				SKIN?: {
					url?: unknown
					metadata?: { model?: unknown }
				}
			}
		}
		: null
	const skinUrl = typeof decoded?.textures?.SKIN?.url === 'string'
		? decoded.textures.SKIN.url.replace(/^http:\/\//, 'https://')
		: null
	const model = typeof decoded?.textures?.SKIN?.metadata?.model === 'string'
		? decoded.textures.SKIN.metadata.model
		: null

	return {
		uuid: typeof profileBody?.id === 'string' ? profileBody.id : uuid,
		name: typeof profileBody?.name === 'string' ? profileBody.name : fallbackName,
		skinUrl,
		model,
		fetchedAtUnixMs: Date.now(),
	}
}

function defaultStats(): PlayerStats {
	return {
		version: STATS_VERSION,
		money: {
			earnedDabloons: 0,
			balanceDabloons: null,
			lastUpdatedAtUnixMs: null,
			sources: {},
		},
		minecraft: {
			stats: {},
			lastSyncedAtUnixMs: null,
			lastPlayedAtUnixMs: null,
		},
		minecraftProfile: null,
	}
}

function normalizeStatsJson(value: string): PlayerStats {
	try {
		const parsed = JSON.parse(value) as Partial<PlayerStats>
		const stats = defaultStats()

		stats.version = STATS_VERSION
		stats.money.earnedDabloons = normalizePositiveInteger(parsed.money?.earnedDabloons)
		stats.money.balanceDabloons = normalizeNullableInteger(parsed.money?.balanceDabloons)
		stats.money.lastUpdatedAtUnixMs = normalizeNullableInteger(parsed.money?.lastUpdatedAtUnixMs)
		stats.money.sources = normalizeMoneySources(parsed.money?.sources)
		stats.minecraft.stats = normalizeMinecraftStats(parsed.minecraft?.stats)
		stats.minecraft.lastSyncedAtUnixMs = normalizeNullableInteger(parsed.minecraft?.lastSyncedAtUnixMs)
		stats.minecraft.lastPlayedAtUnixMs = normalizeNullableInteger(parsed.minecraft?.lastPlayedAtUnixMs)
		stats.minecraftProfile = normalizeMinecraftProfile(parsed.minecraftProfile)

		return stats
	} catch {
		return defaultStats()
	}
}

function normalizeMoneySources(value: unknown): PlayerStats['money']['sources'] {
	if (!value || typeof value !== 'object') {
		return {}
	}

	const sources: PlayerStats['money']['sources'] = {}
	for (const [key, raw] of Object.entries(value)) {
		if (!raw || typeof raw !== 'object') continue
		const source = sanitizeToken(key, '')
		if (!source) continue
		const candidate = raw as { earnedDabloons?: unknown }
		sources[source] = {
			earnedDabloons: normalizePositiveInteger(candidate.earnedDabloons),
		}
	}

	return sources
}

function normalizeMinecraftStats(value: unknown): Record<string, MinecraftStatValue> {
	if (!value || typeof value !== 'object') {
		return {}
	}

	const stats: Record<string, MinecraftStatValue> = {}
	for (const [key, raw] of Object.entries(value)) {
		if (!raw || typeof raw !== 'object') continue
		const candidate = raw as Partial<MinecraftStatValue>
		const safeKey = sanitizeToken(candidate.key ?? key, '')
		const category = sanitizeToken(candidate.category, '')
		const id = sanitizeResourceId(candidate.id)
		const statValue = normalizeNullableInteger(candidate.value)
		if (!safeKey || !category || !id || statValue === null || statValue < 0) continue

		stats[safeKey] = {
			key: safeKey,
			category,
			id,
			label: sanitizeText(candidate.label, 120) || defaultMinecraftStatLabel(category, id),
			value: statValue,
			updatedAtUnixMs: normalizePositiveInteger(candidate.updatedAtUnixMs),
		}
	}

	return stats
}

function normalizeMinecraftProfile(value: unknown): MinecraftProfile | null {
	if (!value || typeof value !== 'object') {
		return null
	}

	const candidate = value as Partial<MinecraftProfile>
	const uuid = sanitizeText(candidate.uuid, 40)
	if (!uuid) {
		return null
	}

	return {
		uuid,
		name: sanitizeText(candidate.name, 32),
		skinUrl: typeof candidate.skinUrl === 'string' && candidate.skinUrl.startsWith('https://')
			? candidate.skinUrl
			: null,
		model: sanitizeText(candidate.model, 16) || null,
		fetchedAtUnixMs: normalizePositiveInteger(candidate.fetchedAtUnixMs),
	}
}

function normalizeDirection(_value: string): MoneyDirection {
	return 'earned'
}

function normalizePositiveInteger(value: unknown): number {
	const number = typeof value === 'number' ? value : Number(value)
	if (!Number.isFinite(number)) return 0
	return Math.max(0, Math.trunc(number))
}

function normalizeNullableInteger(value: unknown): number | null {
	if (value === null || value === undefined || value === '') {
		return null
	}

	const number = typeof value === 'number' ? value : Number(value)
	if (!Number.isFinite(number)) return null
	return Math.trunc(number)
}

function normalizeUnixMs(value: unknown): number {
	const unixMs = normalizeNullableInteger(value)
	return unixMs && unixMs > 0 ? unixMs : Date.now()
}

function normalizeCoordinate(value: unknown, label: string): number | null {
	const coordinate = normalizeNullableInteger(value)
	if (coordinate === null) {
		return null
	}

	if (Math.abs(coordinate) > 30_000_000) {
		throw new BadRequestException(`${label} is outside Minecraft world bounds.`)
	}

	return coordinate
}

function sanitizeText(value: unknown, maxLength: number): string {
	if (typeof value !== 'string') {
		return ''
	}

	return value.trim().slice(0, maxLength)
}

function sanitizeProfileText(value: unknown, maxLength: number, label: string): string {
	if (typeof value !== 'string') {
		return ''
	}

	const trimmed = value.trim()
	if (trimmed.length > maxLength) {
		throw new BadRequestException(`${label} must be ${maxLength} characters or fewer.`)
	}

	return trimmed
}

function sanitizeToken(value: unknown, fallback: string): string {
	if (typeof value !== 'string') {
		return fallback
	}

	const cleaned = value.trim().toLowerCase().replace(/[^a-z0-9_.:-]/g, '_').slice(0, 160)
	return cleaned || fallback
}

function sanitizeEventId(value: unknown): string | null {
	if (typeof value !== 'string') {
		return null
	}

	const cleaned = value.trim().slice(0, 220)
	return cleaned || null
}

function sanitizeResourceId(value: unknown): string {
	if (typeof value !== 'string') {
		return ''
	}

	const trimmed = value.trim().toLowerCase()
	return /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(trimmed) ? trimmed : ''
}

function minecraftStatKey(category: string, id: string): string {
	return `minecraft.${sanitizeToken(category, 'custom')}.${id}`
}

function defaultMinecraftStatLabel(category: string, id: string): string {
	const name = humanizeResourcePath(id.split(':').pop() ?? id)
	if (category === 'killed') return `${name} Killed`
	if (category === 'killed_by') return `Killed By ${name}`
	return name
}

function humanizeResourcePath(value: string): string {
	return value
		.replace(/^minecraft:/, '')
		.split(/[_/.-]+/)
		.filter(Boolean)
		.map((part) => part.charAt(0).toUpperCase() + part.slice(1))
		.join(' ')
}

function groupRank(group: StatOption['group']) {
	if (group === 'profile') return 0
	if (group === 'money') return 1
	if (group === 'fishing') return 2
	return 3
}
