import { Injectable, NotFoundException } from '@nestjs/common'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { eq } from 'drizzle-orm'
import { interval, map, merge, of, Subject } from 'rxjs'
import { AuthenticatedUser } from '../auth/auth.service'
import { DatabaseService, FishCatchRow, fishCatches, users } from '../database/database.service'
import { MinecraftIdentityService } from '../database/minecraft-identity.service'

const ITEM_ROOTS = [
	join(process.cwd(), 'content', 'items'),
	join(process.cwd(), '..', '..', 'minecraft', 'main', 'data', 'data', 'items'),
	join(process.cwd(), 'minecraft', 'main', 'data', 'data', 'items'),
]
const RARITIES = new Set(['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythical'])

interface FishDefinition {
	id: string
	title: string
	rarity: string
	tags: string[]
	iconUrl: string
	textureFilePath: string | null
}

interface FishItemJson {
	id?: unknown
	title?: unknown
	rarity?: unknown
	fish?: { tags?: unknown }
}

interface RecordCatchInput {
	minecraftUuid: string
	minecraftUsername: string
	fishId: string
	lengthCm: number
	rarity: string
	caughtAtUnixMs: number
}

const VANILLA_FISH: FishDefinition[] = [
	vanillaFish('minecraft:cod', 'Cod', 'common'),
	vanillaFish('minecraft:salmon', 'Salmon', 'common'),
	vanillaFish('minecraft:tropical_fish', 'Tropical Fish', 'uncommon'),
	vanillaFish('minecraft:pufferfish', 'Pufferfish', 'uncommon'),
]

@Injectable()
export class FishingService {
	private readonly catchEvents = new Subject<{ data: unknown }>()
	private cachedDefinitions: { mtimeMs: number; definitions: FishDefinition[] } | null = null

	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
	) { }

	recordCatch(input: RecordCatchInput) {
		const definition = this.loadDefinitions().find((fish) => fish.id === input.fishId)
		const user = this.identities.resolveAndRefresh(input.minecraftUuid, input.minecraftUsername)
		if (!definition || !user || !Number.isFinite(input.lengthCm) || input.lengthCm <= 0) {
			return this.unrecorded(Boolean(user), definition ? 'Invalid fish length.' : 'Unknown fish.')
		}

		const caughtAtUnixMs = normalizeUnixMs(input.caughtAtUnixMs)
		const result = this.database.connection.transaction((tx) => {
			const previous = tx.select().from(fishCatches)
				.where(eq(fishCatches.user_id, user.id))
				.all()
				.find((catchRow) => catchRow.fish_id === definition.id) ?? null
			const serverRows = tx.select().from(fishCatches)
				.where(eq(fishCatches.fish_id, definition.id)).all()
			const serverLargest = serverRows.reduce((largest, row) => Math.max(largest, row.largest_length_cm), -Infinity)
			const serverSmallest = serverRows.reduce((smallest, row) => Math.min(smallest, row.smallest_length_cm), Infinity)

			const firstCatch = previous === null
			const personalSizeRecord = firstCatch || input.lengthCm > previous.largest_length_cm
			const personalSmallestRecord = firstCatch || input.lengthCm < previous.smallest_length_cm
			const serverSizeRecord = input.lengthCm > serverLargest
			const serverSmallestRecord = input.lengthCm < serverSmallest
			const values = {
				user_id: user.id,
				fish_id: definition.id,
				first_length_cm: previous?.first_length_cm ?? input.lengthCm,
				first_caught_at_unix_ms: previous?.first_caught_at_unix_ms ?? caughtAtUnixMs,
				smallest_length_cm: personalSmallestRecord ? input.lengthCm : previous!.smallest_length_cm,
				smallest_caught_at_unix_ms: personalSmallestRecord ? caughtAtUnixMs : previous!.smallest_caught_at_unix_ms,
				largest_length_cm: personalSizeRecord ? input.lengthCm : previous!.largest_length_cm,
				largest_caught_at_unix_ms: personalSizeRecord ? caughtAtUnixMs : previous!.largest_caught_at_unix_ms,
			}

			tx.insert(fishCatches).values(values).onConflictDoUpdate({
				target: [fishCatches.user_id, fishCatches.fish_id],
				set: values,
			}).run()

			return { firstCatch, personalSizeRecord, personalSmallestRecord, serverSizeRecord, serverSmallestRecord }
		})

		const response = {
			recorded: true,
			account_linked: true,
			first_catch: result.firstCatch,
			personal_size_record: result.personalSizeRecord,
			personal_smallest_record: result.personalSmallestRecord,
			server_size_record: result.serverSizeRecord,
			server_smallest_record: result.serverSmallestRecord,
			message: 'Fish catch recorded.',
		}
		if (Object.values(result).some(Boolean)) {
			this.catchEvents.next({ data: {
				type: 'catch',
				userId: user.id,
				minecraftUsername: user.minecraft_username,
				fishId: definition.id,
				rarity: definition.rarity,
				lengthCm: input.lengthCm,
				caughtAtUnixMs,
				...result,
			} })
		}
		return response
	}

	getCompendium(viewer: AuthenticatedUser, userIdInput?: string) {
		const requestedUserId = Number(userIdInput ?? viewer.id)
		const selectedUserId = Number.isInteger(requestedUserId) && requestedUserId > 0 ? requestedUserId : viewer.id
		const playerRows = this.database.connection.select().from(users).all()
			.sort((left, right) => left.minecraft_username.localeCompare(right.minecraft_username, 'en', { sensitivity: 'base' }))
		if (!playerRows.some((player) => player.id === selectedUserId)) {
			throw new NotFoundException('Player not found')
		}

		const allCatches = this.database.connection.select().from(fishCatches).all()
		const selectedCatches = new Map(allCatches
			.filter((fishCatch) => fishCatch.user_id === selectedUserId)
			.map((fishCatch) => [fishCatch.fish_id, fishCatch]))
		const playersById = new Map(playerRows.map((player) => [player.id, player]))

		return {
			currentUserId: viewer.id,
			selectedUserId,
			players: playerRows.map((player) => ({
				id: player.id,
				minecraftUsername: player.minecraft_username,
				avatarUrl: avatarUrl(player.minecraft_username),
			})),
			fish: this.loadDefinitions().map((definition) => {
				const serverRows = allCatches.filter((fishCatch) => fishCatch.fish_id === definition.id)
				return {
					id: definition.id,
					title: definition.title,
					rarity: definition.rarity,
					tags: definition.tags,
					iconUrl: definition.iconUrl,
					catch: serializeCatch(selectedCatches.get(definition.id) ?? null),
					serverLargest: serializeServerRecord(serverRows, playersById, 'largest'),
					serverSmallest: serializeServerRecord(serverRows, playersById, 'smallest'),
				}
			}),
		}
	}

	getCatchCounts(userId: number) {
		const rarityByFish = new Map(this.loadDefinitions().map((fish) => [fish.id, fish.rarity]))
		const counts: Record<string, number> = {
			total: 0,
			common: 0,
			uncommon: 0,
			rare: 0,
			epic: 0,
			legendary: 0,
			mythical: 0,
		}
		for (const row of this.database.connection.select().from(fishCatches)
			.where(eq(fishCatches.user_id, userId)).all()) {
			const rarity = rarityByFish.get(row.fish_id)
			if (!rarity) continue
			counts.total = (counts.total ?? 0) + 1
			counts[rarity] = (counts[rarity] ?? 0) + 1
		}
		return counts
	}

	events() {
		return merge(
			of({ data: { type: 'ready' } }),
			this.catchEvents,
			interval(15_000).pipe(map(() => ({ data: { type: 'ping' } }))),
		)
	}

	getTextureFilePath(fishId: string) {
		const definition = this.loadDefinitions().find((fish) => fish.id === fishId)
		if (!definition?.textureFilePath) throw new NotFoundException('Fish texture not found')
		return definition.textureFilePath
	}

	private loadDefinitions() {
		const itemRoot = ITEM_ROOTS.find((candidate) => existsSync(candidate)) ?? ITEM_ROOTS[0]!
		const fishRoot = join(itemRoot, 'fish')
		if (!existsSync(fishRoot)) return VANILLA_FISH
		const mtimeMs = treeMtime(fishRoot)
		if (this.cachedDefinitions?.mtimeMs === mtimeMs) return this.cachedDefinitions.definitions

		const customDefinitions = findItemFiles(fishRoot).flatMap((filePath): FishDefinition[] => {
			const json = JSON.parse(readFileSync(filePath, 'utf8')) as FishItemJson
			const directory = dirname(filePath)
			const textureFilePath = join(directory, 'texture.png')
			if (typeof json.id !== 'string' || typeof json.title !== 'string' || !existsSync(textureFilePath)) return []
			return [{
				id: json.id,
				title: json.title,
				rarity: typeof json.rarity === 'string' && RARITIES.has(json.rarity) ? json.rarity : 'common',
				tags: Array.isArray(json.fish?.tags) ? json.fish.tags.filter((tag): tag is string => typeof tag === 'string') : [],
				iconUrl: `/api/fishing/texture/${encodeURIComponent(json.id)}?v=${mtimeMs}`,
				textureFilePath,
			}]
		})
		const definitions = [...VANILLA_FISH, ...customDefinitions]
			.sort((left, right) => left.title.localeCompare(right.title, 'en'))

		this.cachedDefinitions = { mtimeMs, definitions }
		return definitions
	}

	private unrecorded(accountLinked: boolean, message: string) {
		return {
			recorded: false,
			account_linked: accountLinked,
			first_catch: false,
			personal_size_record: false,
			personal_smallest_record: false,
			server_size_record: false,
			server_smallest_record: false,
			message,
		}
	}
}

function serializeCatch(row: FishCatchRow | null) {
	if (!row) return null
	return {
		first: { lengthCm: row.first_length_cm, caughtAtUnixMs: row.first_caught_at_unix_ms },
		smallest: { lengthCm: row.smallest_length_cm, caughtAtUnixMs: row.smallest_caught_at_unix_ms },
		largest: { lengthCm: row.largest_length_cm, caughtAtUnixMs: row.largest_caught_at_unix_ms },
	}
}

function serializeServerRecord(
	rows: FishCatchRow[],
	players: Map<number, { id: number; minecraft_username: string }>,
	kind: 'largest' | 'smallest',
) {
	const row = rows.reduce<FishCatchRow | null>((record, candidate) => {
		if (!record) return candidate
		return kind === 'largest'
			? candidate.largest_length_cm > record.largest_length_cm ? candidate : record
			: candidate.smallest_length_cm < record.smallest_length_cm ? candidate : record
	}, null)
	if (!row) return null
	const player = players.get(row.user_id)
	if (!player) return null
	return {
		lengthCm: kind === 'largest' ? row.largest_length_cm : row.smallest_length_cm,
		caughtAtUnixMs: kind === 'largest' ? row.largest_caught_at_unix_ms : row.smallest_caught_at_unix_ms,
		player: {
			id: player.id,
			minecraftUsername: player.minecraft_username,
			avatarUrl: avatarUrl(player.minecraft_username),
		},
	}
}

function findItemFiles(directory: string): string[] {
	return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const path = join(directory, entry.name)
		return entry.isDirectory() ? findItemFiles(path) : entry.name === 'item.json' ? [path] : []
	})
}

function treeMtime(path: string): number {
	const stat = statSync(path)
	if (!stat.isDirectory()) return stat.mtimeMs
	return readdirSync(path, { withFileTypes: true })
		.reduce((mtime, child) => Math.max(mtime, treeMtime(join(path, child.name))), stat.mtimeMs)
}

function normalizeUnixMs(value: number) {
	return Number.isFinite(value) && value > 0 ? Math.trunc(value) : Date.now()
}

function avatarUrl(username: string) {
	return `https://mc-heads.net/avatar/${encodeURIComponent(username)}/32`
}

function vanillaFish(id: string, title: string, rarity: string): FishDefinition {
	const textureName = id.slice('minecraft:'.length)
	return {
		id,
		title,
		rarity,
		tags: [],
		iconUrl: `/assets/mc_respack/assets/minecraft/textures/item/${textureName}.png`,
		textureFilePath: null,
	}
}
