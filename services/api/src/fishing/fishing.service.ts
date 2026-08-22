import { Injectable, NotFoundException, OnModuleInit } from '@nestjs/common';
import { randomInt } from 'node:crypto';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { and, count, eq, inArray, max, min } from 'drizzle-orm';
import { interval, map, merge, of, Subject } from 'rxjs';
import { AuthenticatedUser } from '../auth/auth.service';
import {
	DatabaseService,
	FishCatchRow,
	fishCatches,
	playerProfiles,
	users,
} from '../database/database.service';
import { MinecraftIdentityService } from '../database/minecraft-identity.service';
import { ASSETS } from '../assets';
import { effectivePlayerColor, playerAvatarUrl } from '../players/player-color';

const ITEM_ROOTS = [
	join(process.cwd(), 'content', 'items'),
	join(process.cwd(), '..', '..', 'minecraft', 'main', 'data', 'data', 'items'),
	join(process.cwd(), 'minecraft', 'main', 'data', 'data', 'items'),
] as const;
const RARITIES = new Set(['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythical']);
const FISH_ASSET_REVISION = `${Date.now().toString(36)}-${randomInt(0x100000000).toString(36)}`;

interface FishDefinition {
	id: string;
	title: string;
	rarity: string;
	tags: string[];
	facts: string[];
	iconUrl: string;
	textureFilePath: string | null;
}

interface FishItemJson {
	id?: unknown;
	title?: unknown;
	rarity?: unknown;
	tooltips?: unknown;
	fish?: { tags?: unknown };
}

interface RecordCatchInput {
	minecraftUuid: string;
	minecraftUsername: string;
	fishId: string;
	lengthCm: number;
	rarity: string;
	caughtAtUnixMs: number;
}

const VANILLA_FISH: FishDefinition[] = [
	vanillaFish(
		'minecraft:cod',
		'Cod',
		'common',
		'Cod use the small barbel beneath their chin to help search the seabed for food.',
	),
	vanillaFish(
		'minecraft:salmon',
		'Salmon',
		'common',
		'Salmon can navigate back to the stream where they hatched after years at sea.',
	),
	vanillaFish(
		'minecraft:tropical_fish',
		'Tropical Fish',
		'uncommon',
		'Many tropical reef fish can change colour or pattern as they mature.',
	),
	vanillaFish(
		'minecraft:pufferfish',
		'Pufferfish',
		'uncommon',
		'Pufferfish inflate by rapidly swallowing water, making themselves difficult for predators to bite.',
	),
];

@Injectable()
export class FishingService implements OnModuleInit {
	private readonly catchEvents = new Subject<{ data: unknown }>();
	private cachedDefinitions: {
		mtimeMs: number;
		definitions: FishDefinition[];
		byId: Map<string, FishDefinition>;
	} | null = null;

	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
	) {}

	onModuleInit() {
		if (process.env.NODE_ENV === 'production') this.loadDefinitions();
	}

	recordCatch(input: RecordCatchInput) {
		const definition = this.loadDefinitions().find((fish) => fish.id === input.fishId);
		const user = this.identities.resolveAndRefresh(
			input.minecraftUuid,
			input.minecraftUsername,
		);
		if (!definition || !user || !Number.isFinite(input.lengthCm) || input.lengthCm <= 0) {
			return this.unrecorded(
				Boolean(user),
				definition ? 'Invalid fish length.' : 'Unknown fish.',
			);
		}

		const caughtAtUnixMs = normalizeUnixMs(input.caughtAtUnixMs);
		const result = this.database.connection.transaction((tx) => {
			const previous =
				tx
					.select()
					.from(fishCatches)
					.where(
						and(
							eq(fishCatches.user_id, user.id),
							eq(fishCatches.fish_id, definition.id),
						),
					)
					.get() ?? null;
			const serverRecords = tx
				.select({
					count: count(),
					largest: max(fishCatches.largest_length_cm),
					smallest: min(fishCatches.smallest_length_cm),
				})
				.from(fishCatches)
				.where(eq(fishCatches.fish_id, definition.id))
				.get();

			const firstCatch = previous === null;
			const firstServerCatch = !serverRecords?.count;
			const personalSizeRecord = firstCatch || input.lengthCm > previous.largest_length_cm;
			const personalSmallestRecord =
				firstCatch || input.lengthCm < previous.smallest_length_cm;
			const serverSizeRecord =
				serverRecords?.largest == null || input.lengthCm > serverRecords.largest;
			const serverSmallestRecord =
				serverRecords?.smallest == null || input.lengthCm < serverRecords.smallest;
			const values = {
				user_id: user.id,
				fish_id: definition.id,
				first_length_cm: previous?.first_length_cm ?? input.lengthCm,
				first_caught_at_unix_ms: previous?.first_caught_at_unix_ms ?? caughtAtUnixMs,
				smallest_length_cm: personalSmallestRecord
					? input.lengthCm
					: previous.smallest_length_cm,
				smallest_caught_at_unix_ms: personalSmallestRecord
					? caughtAtUnixMs
					: previous.smallest_caught_at_unix_ms,
				largest_length_cm: personalSizeRecord ? input.lengthCm : previous.largest_length_cm,
				largest_caught_at_unix_ms: personalSizeRecord
					? caughtAtUnixMs
					: previous.largest_caught_at_unix_ms,
			};

			tx.insert(fishCatches)
				.values(values)
				.onConflictDoUpdate({
					target: [fishCatches.user_id, fishCatches.fish_id],
					set: values,
				})
				.run();

			return {
				firstCatch,
				firstServerCatch,
				personalSizeRecord,
				personalSmallestRecord,
				serverSizeRecord,
				serverSmallestRecord,
			};
		});
		const firstServerCatchAnnouncement =
			result.firstServerCatch && ['rare', 'epic'].includes(definition.rarity);
		const announce =
			firstServerCatchAnnouncement || ['legendary', 'mythical'].includes(definition.rarity);

		const response = {
			recorded: true,
			account_linked: true,
			first_catch: result.firstCatch,
			personal_size_record: result.personalSizeRecord,
			personal_smallest_record: result.personalSmallestRecord,
			server_size_record: result.serverSizeRecord,
			server_smallest_record: result.serverSmallestRecord,
			announce,
			first_server_catch_announcement: firstServerCatchAnnouncement,
			message: 'Fish catch recorded.',
		};
		if (Object.values(result).some(Boolean)) {
			this.catchEvents.next({
				data: {
					type: 'catch',
					userId: user.id,
					minecraftUsername: user.minecraft_username,
					fishId: definition.id,
					rarity: definition.rarity,
					lengthCm: input.lengthCm,
					caughtAtUnixMs,
					...result,
				},
			});
		}
		return response;
	}

	getCompendium(viewer: AuthenticatedUser, userIdInput?: string) {
		const requestedUserId = Number(userIdInput ?? viewer.id);
		const selectedUserId =
			Number.isInteger(requestedUserId) && requestedUserId > 0 ? requestedUserId : viewer.id;
		const playerRows = this.database.connection
			.select()
			.from(users)
			.all()
			.sort((left, right) =>
				left.minecraft_username.localeCompare(right.minecraft_username, 'en', {
					sensitivity: 'base',
				}),
			);
		const profilesById = new Map(
			this.database.connection
				.select()
				.from(playerProfiles)
				.all()
				.map((profile) => [profile.user_id, profile]),
		);
		if (!playerRows.some((player) => player.id === selectedUserId)) {
			throw new NotFoundException('Player not found');
		}

		const allCatches = this.database.connection.select().from(fishCatches).all();
		const selectedCatches = new Map<string, FishCatchRow>();
		const catchCountByUserId = new Map<number, number>();
		const catchesByFishId = new Map<string, FishCatchRow[]>();
		for (const fishCatch of allCatches) {
			if (fishCatch.user_id === selectedUserId) {
				selectedCatches.set(fishCatch.fish_id, fishCatch);
			}
			catchCountByUserId.set(
				fishCatch.user_id,
				(catchCountByUserId.get(fishCatch.user_id) ?? 0) + 1,
			);
			const serverRows = catchesByFishId.get(fishCatch.fish_id) ?? [];
			serverRows.push(fishCatch);
			catchesByFishId.set(fishCatch.fish_id, serverRows);
		}
		const playersById = new Map(playerRows.map((player) => [player.id, player]));

		return {
			currentUserId: viewer.id,
			selectedUserId,
			players: playerRows.map((player) => {
				const color = effectivePlayerColor(
					player.minecraft_uuid,
					profilesById.get(player.id)?.color_hex,
				);
				return {
					id: player.id,
					minecraftUsername: player.minecraft_username,
					pronouns: profilesById.get(player.id)?.pronouns ?? '',
					color,
					avatarUrl: playerAvatarUrl(player.minecraft_uuid),
					caughtTotal: catchCountByUserId.get(player.id) ?? 0,
				};
			}),
			fish: this.loadDefinitions().map((definition) => {
				const serverRows = catchesByFishId.get(definition.id) ?? [];
				return {
					id: definition.id,
					title: definition.title,
					rarity: definition.rarity,
					tags: definition.tags,
					facts: definition.facts,
					iconUrl: definition.iconUrl,
					catch: serializeCatch(selectedCatches.get(definition.id) ?? null),
					serverLargest: serializeServerRecord(
						serverRows,
						playersById,
						profilesById,
						'largest',
					),
					serverSmallest: serializeServerRecord(
						serverRows,
						playersById,
						profilesById,
						'smallest',
					),
				};
			}),
		};
	}

	getCatchCounts(userId: number) {
		return this.getCatchCountsForUsers([userId]).get(userId) ?? emptyCatchCounts();
	}

	getCatchCountsForUsers(userIds: number[]) {
		const rarityByFish = new Map(this.loadDefinitions().map((fish) => [fish.id, fish.rarity]));
		const countsByUserId = new Map(userIds.map((userId) => [userId, emptyCatchCounts()]));
		if (userIds.length === 0) return countsByUserId;

		const rows = this.database.connection
			.select()
			.from(fishCatches)
			.where(inArray(fishCatches.user_id, userIds))
			.all();
		for (const row of rows) {
			const rarity = rarityByFish.get(row.fish_id);
			if (!rarity) continue;
			const counts = countsByUserId.get(row.user_id);
			if (!counts) continue;
			counts.total = (counts.total ?? 0) + 1;
			counts[rarity] = (counts[rarity] ?? 0) + 1;
		}
		return countsByUserId;
	}

	events() {
		return merge(
			of({ data: { type: 'ready' } }),
			this.catchEvents,
			interval(15_000).pipe(map(() => ({ data: { type: 'ping' } }))),
		);
	}

	getTextureFilePath(fishId: string) {
		const definition = this.loadDefinitionsCache().byId.get(fishId);
		if (!definition?.textureFilePath) throw new NotFoundException('Fish texture not found');
		return definition.textureFilePath;
	}

	private loadDefinitions() {
		return this.loadDefinitionsCache().definitions;
	}

	private loadDefinitionsCache() {
		if (process.env.NODE_ENV === 'production' && this.cachedDefinitions)
			return this.cachedDefinitions;

		const itemRoot = ITEM_ROOTS.find((candidate) => existsSync(candidate)) ?? ITEM_ROOTS[0];
		const fishRoot = join(itemRoot, 'fish');
		if (!existsSync(fishRoot)) {
			this.cachedDefinitions = {
				mtimeMs: 0,
				definitions: VANILLA_FISH,
				byId: new Map(VANILLA_FISH.map((fish) => [fish.id, fish])),
			};
			return this.cachedDefinitions;
		}
		const mtimeMs = treeMtime(fishRoot);
		if (this.cachedDefinitions?.mtimeMs === mtimeMs) return this.cachedDefinitions;
		const revision =
			process.env.NODE_ENV === 'production' ? FISH_ASSET_REVISION : String(mtimeMs);

		const customDefinitions = findItemFiles(fishRoot).flatMap((filePath): FishDefinition[] => {
			const json = JSON.parse(readFileSync(filePath, 'utf8')) as FishItemJson;
			const directory = dirname(filePath);
			const textureFilePath = join(directory, 'texture.png');
			if (
				typeof json.id !== 'string' ||
				typeof json.title !== 'string' ||
				!existsSync(textureFilePath)
			)
				return [];
			return [
				{
					id: json.id,
					title: json.title,
					rarity:
						typeof json.rarity === 'string' && RARITIES.has(json.rarity)
							? json.rarity
							: 'common',
					tags: Array.isArray(json.fish?.tags)
						? json.fish.tags.filter((tag): tag is string => typeof tag === 'string')
						: [],
					facts: Array.isArray(json.tooltips)
						? json.tooltips.filter(
								(fact): fact is string =>
									typeof fact === 'string' && fact.trim().length > 0,
							)
						: [],
					iconUrl: `/api/fishing/texture/${encodeURIComponent(json.id)}?v=${revision}`,
					textureFilePath,
				},
			];
		});
		const definitions = [...VANILLA_FISH, ...customDefinitions].sort((left, right) =>
			left.title.localeCompare(right.title, 'en'),
		);

		this.cachedDefinitions = {
			mtimeMs,
			definitions,
			byId: new Map(definitions.map((fish) => [fish.id, fish])),
		};
		return this.cachedDefinitions;
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
			announce: false,
			first_server_catch_announcement: false,
			message,
		};
	}
}

function serializeCatch(row: FishCatchRow | null) {
	if (!row) return null;
	return {
		first: { lengthCm: row.first_length_cm, caughtAtUnixMs: row.first_caught_at_unix_ms },
		smallest: {
			lengthCm: row.smallest_length_cm,
			caughtAtUnixMs: row.smallest_caught_at_unix_ms,
		},
		largest: { lengthCm: row.largest_length_cm, caughtAtUnixMs: row.largest_caught_at_unix_ms },
	};
}

function emptyCatchCounts(): Record<string, number> {
	return {
		total: 0,
		common: 0,
		uncommon: 0,
		rare: 0,
		epic: 0,
		legendary: 0,
		mythical: 0,
	};
}

function serializeServerRecord(
	rows: FishCatchRow[],
	players: Map<number, { id: number; minecraft_username: string; minecraft_uuid: string | null }>,
	profiles: Map<number, { color_hex: string | null }>,
	kind: 'largest' | 'smallest',
) {
	const row = rows.reduce<FishCatchRow | null>((record, candidate) => {
		if (!record) return candidate;
		return kind === 'largest'
			? candidate.largest_length_cm > record.largest_length_cm
				? candidate
				: record
			: candidate.smallest_length_cm < record.smallest_length_cm
				? candidate
				: record;
	}, null);
	if (!row) return null;
	const player = players.get(row.user_id);
	if (!player) return null;
	const color = effectivePlayerColor(player.minecraft_uuid, profiles.get(player.id)?.color_hex);
	return {
		lengthCm: kind === 'largest' ? row.largest_length_cm : row.smallest_length_cm,
		caughtAtUnixMs:
			kind === 'largest' ? row.largest_caught_at_unix_ms : row.smallest_caught_at_unix_ms,
		player: {
			id: player.id,
			minecraftUsername: player.minecraft_username,
			color,
			avatarUrl: playerAvatarUrl(player.minecraft_uuid),
		},
	};
}

function findItemFiles(directory: string): string[] {
	return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const path = join(directory, entry.name);
		return entry.isDirectory() ? findItemFiles(path) : entry.name === 'item.json' ? [path] : [];
	});
}

function treeMtime(path: string): number {
	const stat = statSync(path);
	if (!stat.isDirectory()) return stat.mtimeMs;
	return readdirSync(path, { withFileTypes: true }).reduce(
		(mtime, child) => Math.max(mtime, treeMtime(join(path, child.name))),
		stat.mtimeMs,
	);
}

function normalizeUnixMs(value: number) {
	return Number.isFinite(value) && value > 0 ? Math.trunc(value) : Date.now();
}

function vanillaFish(id: string, title: string, rarity: string, fact: string): FishDefinition {
	const textureName = id.slice('minecraft:'.length);
	return {
		id,
		title,
		rarity,
		tags: [],
		facts: [fact],
		iconUrl: `${ASSETS.minecraft.vanilla}/textures/item/${textureName}.png`,
		textureFilePath: null,
	};
}
