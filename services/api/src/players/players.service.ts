import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common'
import { randomUUID } from 'node:crypto'
import { eq } from 'drizzle-orm'
import { AuthenticatedUser } from '../auth/auth.service'
import { MinecraftIdentityService } from '../database/minecraft-identity.service'
import { FishingService } from '../fishing/fishing.service'
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

const STATS_VERSION = 1
const MOJANG_PROFILE_TTL_MS = 7 * 24 * 60 * 60 * 1000
const PROFILE_TEXT_LIMITS = {
	preferredName: 40,
	pronouns: 32,
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
	updatedAtUnixMs: number
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
	isCurrentUser: boolean
	isMember: boolean
	isCommittee: boolean
	profile: PlayerProfile
	fishing: Record<string, number>
	stats: PlayerStats
}

const PROFILE_OPTIONS: StatOption[] = [
	{ key: 'profile.playerName', label: 'Player Name', group: 'profile' },
	{ key: 'profile.isMember', label: 'Society Member', group: 'profile' },
	{ key: 'profile.isCommittee', label: 'Committee', group: 'profile' },
	{ key: 'profile.preferredName', label: 'Preferred Name', group: 'profile' },
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
		label: `${rarity.charAt(0).toUpperCase()}${rarity.slice(1)} Fish Caught`,
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
	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
		private readonly fishing: FishingService,
	) { }

	async listPlayers(viewer: AuthenticatedUser) {
		const userRows = this.database.connection.select().from(users).all()
			.sort((left, right) => left.minecraft_username.localeCompare(right.minecraft_username, 'en', { sensitivity: 'base' }))

		const players = await Promise.all(userRows.map((user) => this.serializePlayer(user, viewer.id, true)))

		return {
			currentUserId: viewer.id,
			statOptions: this.getStatOptions(players.map((player) => player.stats)),
			players,
		}
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
			player: await this.serializePlayer(user, viewer.id, true),
		}
	}

	updateOwnProfile(user: AuthenticatedUser, input: Record<string, unknown>) {
		const now = Date.now()
		const profile = this.normalizeProfileInput(input)

		const values = {
			user_id: user.id,
			preferred_name: profile.preferredName,
			pronouns: profile.pronouns,
			course_year: profile.courseYear,
			discord_username: profile.discordUsername,
			base_x: profile.base.x,
			base_y: profile.base.y,
			base_z: profile.base.z,
			bio: profile.bio,
			updated_at_unix_ms: now,
		}
		this.database.connection.insert(playerProfiles).values(values)
			.onConflictDoUpdate({ target: playerProfiles.user_id, set: values })
			.run()

		return {
			ok: true,
			profile: this.getProfile(user.id),
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

		return {
			accepted: true,
			accountLinked: true,
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
				message: 'No website account is linked to this Minecraft username yet.',
			}
		}

		const direction = normalizeDirection(directionInput)
		const source = sanitizeToken(sourceInput, 'minecraft')
		const referenceId = sanitizeToken(referenceIdInput, '')
		const eventId = referenceId ? `${source}:${referenceId}` : randomUUID()
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
			message: result.duplicate ? 'Money event already recorded.' : 'Money event recorded.',
		}
	}

	private async serializePlayer(user: UserRow, currentUserId: number, includeMojangProfile: boolean): Promise<PlayerSummary> {
		let stats = this.getStats(user.id)
		if (includeMojangProfile) {
			stats = await this.ensureMojangProfile(user, stats)
		}

		return {
			id: user.id,
			minecraftUsername: user.minecraft_username,
			isCurrentUser: user.id === currentUserId,
			isMember: user.is_member === 1,
			isCommittee: user.is_committee === 1,
			profile: this.getProfile(user.id),
			fishing: this.fishing.getCatchCounts(user.id),
			stats,
		}
	}

	private getProfile(userId: number): PlayerProfile {
		const row = this.database.connection.select().from(playerProfiles)
			.where(eq(playerProfiles.user_id, userId)).get()

		if (!row) {
			return {
				preferredName: '',
				pronouns: '',
				courseYear: '',
				discordUsername: '',
				base: { x: null, y: null, z: null },
				bio: '',
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

	private normalizeProfileInput(input: Record<string, unknown>): PlayerProfile {
		return {
			preferredName: sanitizeProfileText(input.preferredName, PROFILE_TEXT_LIMITS.preferredName, 'Preferred name'),
			pronouns: sanitizeProfileText(input.pronouns, PROFILE_TEXT_LIMITS.pronouns, 'Pronouns'),
			courseYear: sanitizeProfileText(input.courseYear, PROFILE_TEXT_LIMITS.courseYear, 'Course / Year'),
			discordUsername: sanitizeProfileText(input.discordUsername, PROFILE_TEXT_LIMITS.discordUsername, 'Discord username'),
			base: {
				x: normalizeCoordinate(input.baseX, 'Base X'),
				y: normalizeCoordinate(input.baseY, 'Base Y'),
				z: normalizeCoordinate(input.baseZ, 'Base Z'),
			},
			bio: sanitizeProfileText(input.bio, PROFILE_TEXT_LIMITS.bio, 'Bio'),
			updatedAtUnixMs: Date.now(),
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

async function fetchMojangProfile(minecraftUsername: string): Promise<MinecraftProfile> {
	const uuidResponse = await fetch(
		`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(minecraftUsername)}`,
		{ cache: 'no-store' },
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

	const profileResponse = await fetch(
		`https://sessionserver.mojang.com/session/minecraft/profile/${encodeURIComponent(uuid)}`,
		{ cache: 'no-store' },
	)

	if (!profileResponse.ok) {
		throw new Error('Mojang profile lookup failed')
	}

	const profileBody = await profileResponse.json().catch(() => null) as {
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
		uuid,
		name,
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
