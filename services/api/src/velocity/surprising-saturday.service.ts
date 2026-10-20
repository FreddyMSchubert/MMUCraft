import {
	BadRequestException,
	ConflictException,
	Injectable,
	NotFoundException,
} from '@nestjs/common';
import { and, asc, eq, gt, like, lte, ne } from 'drizzle-orm';
import {
	DatabaseService,
	playerProfiles,
	playerMoneyEvents,
	playerStats,
	SurprisingSaturdayEventRow,
	UserRow,
	surprisingSaturdayCompletions,
	surprisingSaturdayEvents,
	surprisingSaturdayParticipants,
	users,
} from '../database/database.service';
import { normalizeMinecraftUuid } from '../database/minecraft-identity.service';
import { ADMIN_SIGNUP_EMAIL } from '../auth/auth.util';
import { effectivePlayerColor, playerAvatarUrl } from '../players/player-color';
import { customPlayerEmojis } from '../players/player-emojis';
import { normalizeStatsJson } from '../players/player-statistics';

const TITLE_GRAPHEMES = new Intl.Segmenter(undefined, { granularity: 'grapheme' });

function validGoalId(value: unknown): value is string {
	return typeof value === 'string' && !!value.trim() && value.length <= 120;
}

interface CompletionTarget {
	id: string;
	name: string;
	url: string;
	points: number;
	description?: string;
	amount?: number;
}

interface EventRewards {
	first: number;
	second: number;
	third: number;
	fourth: number;
	fifth: number;
	participation: number;
	participationMinItems: number;
}

const REWARD_PLACES = ['first', 'second', 'third', 'fourth', 'fifth'] as const;
const DEFAULT_REWARDS: EventRewards = {
	first: 0,
	second: 0,
	third: 0,
	fourth: 0,
	fifth: 0,
	participation: 0,
	participationMinItems: 1,
};

function eventRewards(json: string): EventRewards {
	return { ...DEFAULT_REWARDS, ...JSON.parse(json) } as EventRewards;
}

function validRewards(value: unknown, itemCount: number): value is EventRewards {
	if (!value || typeof value !== 'object') return false;
	const rewards = value as Record<string, unknown>;
	return (
		[...REWARD_PLACES, 'participation'].every(
			(key) =>
				Number.isSafeInteger(rewards[key]) &&
				Number(rewards[key]) >= 0 &&
				Number(rewards[key]) <= 1_000_000,
		) &&
		Number.isSafeInteger(rewards.participationMinItems) &&
		Number(rewards.participationMinItems) >= 1 &&
		Number(rewards.participationMinItems) <= itemCount
	);
}

export function eventRewardAmount(rewards: EventRewards, place: number, completedCount: number) {
	const rewardPlace = REWARD_PLACES[place];
	return rewardPlace
		? rewards[rewardPlace]
		: completedCount >= rewards.participationMinItems
			? rewards.participation
			: 0;
}

function completionTargets(value: unknown): CompletionTarget[] | null {
	if (!Array.isArray(value) || value.length < 1 || value.length > 256) return null;
	const targets: CompletionTarget[] = [];
	for (const item of value) {
		const input =
			typeof item === 'object' && item !== null ? (item as Record<string, unknown>) : null;
		const id = typeof item === 'string' ? item : input?.id;
		const name =
			typeof item === 'string'
				? item
						.split(':')
						.at(-1)
						?.replaceAll('_', ' ')
						.replace(/\b\w/g, (letter) => letter.toUpperCase())
				: input?.name;
		const url = typeof item === 'string' ? '' : (input?.url ?? '');
		const points = typeof item === 'string' ? 1 : input?.points;
		if (
			!validGoalId(id) ||
			typeof name !== 'string' ||
			!name.trim() ||
			name.length > 120 ||
			typeof url !== 'string' ||
			url.length > 2048 ||
			!Number.isSafeInteger(points) ||
			(points as number) < 1 ||
			(points as number) > 1000
		)
			return null;
		if (url) {
			try {
				if (!['http:', 'https:'].includes(new URL(url).protocol)) return null;
			} catch {
				return null;
			}
		}
		targets.push({
			id: id.trim(),
			name: name.trim(),
			url: url.trim(),
			points: points as number,
		});
	}
	return new Set(targets.map((target) => target.id)).size === targets.length ? targets : null;
}

function eventTargets(json: string) {
	return completionTargets(JSON.parse(json)) ?? [];
}

function earningTargets(value: unknown): CompletionTarget[] | null {
	if (!Array.isArray(value) || value.length < 1 || value.length > 256) return null;
	const targets: CompletionTarget[] = [];
	for (const item of value) {
		if (!item || typeof item !== 'object') return null;
		const { id, name, description, amount } = item as Record<string, unknown>;
		if (
			!validGoalId(id) ||
			typeof name !== 'string' ||
			!name.trim() ||
			name.length > 120 ||
			(description !== undefined &&
				(typeof description !== 'string' || description.length > 2000)) ||
			!Number.isSafeInteger(amount) ||
			(amount as number) < 1 ||
			(amount as number) > 1_000_000
		)
			return null;
		targets.push({
			id: id.trim(),
			name: name.trim(),
			description: description?.trim() ?? '',
			amount: amount as number,
			url: '',
			points: 0,
		});
	}
	return new Set(targets.map((target) => target.id)).size === targets.length ? targets : null;
}

function targetsForEvent(event: SurprisingSaturdayEventRow) {
	const value: unknown = JSON.parse(event.criteria_json);
	return event.criteria_type === 'joined_earning'
		? (earningTargets(value) ?? [])
		: (completionTargets(value) ?? []);
}

export function joinedEarningAmount(
	targets: { id: string; amount?: number }[],
	completed: { itemId: string }[],
) {
	const amounts = new Map(targets.map((target) => [target.id, target.amount ?? 0]));
	return completed.reduce((total, entry) => total + (amounts.get(entry.itemId) ?? 0), 0);
}

function titleWordLengths(title: string) {
	return title
		.trim()
		.split(/\s+/u)
		.map((word) => [...TITLE_GRAPHEMES.segment(word)].length);
}

function playerRole(user: UserRow | undefined) {
	return user?.is_committee || user?.is_super_admin
		? 'Committee'
		: user?.is_member
			? 'Member'
			: user?.responsible_user_id != null
				? 'External'
				: 'Player';
}

@Injectable()
export class SurprisingSaturdayService {
	constructor(private readonly database: DatabaseService) {}

	active(now = Date.now()) {
		return this.database.connection
			.select()
			.from(surprisingSaturdayEvents)
			.where(
				and(
					lte(surprisingSaturdayEvents.starts_at_unix_ms, now),
					gt(surprisingSaturdayEvents.ends_at_unix_ms, now),
				),
			)
			.get();
	}

	warmingUp(now = Date.now()) {
		return this.database.connection
			.select()
			.from(surprisingSaturdayEvents)
			.where(
				and(
					gt(surprisingSaturdayEvents.starts_at_unix_ms, now),
					lte(surprisingSaturdayEvents.starts_at_unix_ms, now + 15 * 60_000),
				),
			)
			.get();
	}

	list(revealUpcoming = false) {
		const now = Date.now();
		return this.database.connection
			.select()
			.from(surprisingSaturdayEvents)
			.orderBy(asc(surprisingSaturdayEvents.starts_at_unix_ms))
			.all()
			.map((event) => ({
				id: event.id,
				startsAtUnixMs: event.starts_at_unix_ms,
				endsAtUnixMs: event.ends_at_unix_ms,
				status:
					event.starts_at_unix_ms > now
						? 'upcoming'
						: event.ends_at_unix_ms > now
							? 'live'
							: 'ended',
				title: revealUpcoming || event.starts_at_unix_ms <= now ? event.title : null,
				shortDescription:
					revealUpcoming || event.starts_at_unix_ms <= now
						? event.short_description
						: null,
				titleWordLengths: titleWordLengths(event.title),
				description:
					revealUpcoming || event.starts_at_unix_ms <= now
						? event.description
						: event.pre_description,
			}));
	}

	detail(idInput: string) {
		const id = Number(idInput);
		if (!Number.isSafeInteger(id) || id <= 0) throw new NotFoundException('Event not found');
		const event = this.database.connection
			.select()
			.from(surprisingSaturdayEvents)
			.where(eq(surprisingSaturdayEvents.id, id))
			.get();
		if (!event) throw new NotFoundException('Event not found');
		const now = Date.now();
		if (event.starts_at_unix_ms > now)
			return {
				id,
				startsAtUnixMs: event.starts_at_unix_ms,
				endsAtUnixMs: event.ends_at_unix_ms,
				status: 'upcoming',
				titleWordLengths: titleWordLengths(event.title),
				description: event.pre_description,
				shortDescription: null,
			};

		const items = event.criteria_type === 'empty' ? [] : targetsForEvent(event);
		const players = event.criteria_type === 'empty' ? [] : this.rankedPlayers(event, items);
		return {
			id,
			status: event.ends_at_unix_ms > now ? 'live' : 'ended',
			title: event.title,
			shortDescription: event.short_description,
			description: event.description,
			startsAtUnixMs: event.starts_at_unix_ms,
			endsAtUnixMs: event.ends_at_unix_ms,
			criteriaType: event.criteria_type,
			items,
			players,
		};
	}

	private rankedPlayers(event: SurprisingSaturdayEventRow, items: CompletionTarget[]) {
		const pointsById = new Map(items.map((item) => [item.id, item.points]));
		const participants = this.database.connection
			.select()
			.from(surprisingSaturdayParticipants)
			.where(eq(surprisingSaturdayParticipants.event_id, event.id))
			.all();
		const completions = this.database.connection
			.select()
			.from(surprisingSaturdayCompletions)
			.where(eq(surprisingSaturdayCompletions.event_id, event.id))
			.all()
			.filter(
				(entry) =>
					entry.completed_at_unix_ms >= event.starts_at_unix_ms &&
					entry.completed_at_unix_ms < event.ends_at_unix_ms,
			);
		const players = participants.flatMap((participant) => {
			const user = this.database.connection
				.select()
				.from(users)
				.where(eq(users.minecraft_uuid, participant.player_uuid))
				.get();
			if (user?.email === ADMIN_SIGNUP_EMAIL) return [];
			const profile = user
				? this.database.connection
						.select()
						.from(playerProfiles)
						.where(eq(playerProfiles.user_id, user.id))
						.get()
				: null;
			const completed = completions
				.filter(
					(entry) =>
						entry.player_uuid === participant.player_uuid &&
						pointsById.has(entry.item_id),
				)
				.map((entry) => ({ itemId: entry.item_id, atUnixMs: entry.completed_at_unix_ms }));
			return [
				{
					uuid: participant.player_uuid,
					userId: user?.id ?? null,
					name: user?.minecraft_username ?? 'Unknown player',
					avatarUrl: user ? playerAvatarUrl(participant.player_uuid) : null,
					color: effectivePlayerColor(participant.player_uuid, profile?.color_hex),
					role: playerRole(user),
					pronouns: profile?.pronouns ?? '',
					isCommittee: Boolean(user && (user.is_committee || user.is_super_admin)),
					customEmojis: customPlayerEmojis(profile?.custom_emojis_json),
					completed,
					points: completed.reduce(
						(score, entry) => score + (pointsById.get(entry.itemId) ?? 0),
						0,
					),
					lastCompletionAtUnixMs: Math.max(
						0,
						...completed.map((entry) => entry.atUnixMs),
					),
				},
			];
		});
		players.sort(
			(a, b) =>
				b.points - a.points ||
				a.lastCompletionAtUnixMs - b.lastCompletionAtUnixMs ||
				a.name.localeCompare(b.name) ||
				a.uuid.localeCompare(b.uuid),
		);
		return players;
	}

	create(body: Record<string, unknown> | undefined) {
		return this.save(body);
	}

	adminDetail(idInput: string) {
		const id = Number(idInput);
		if (!Number.isSafeInteger(id) || id <= 0) throw new NotFoundException('Event not found');
		const event = this.database.connection
			.select()
			.from(surprisingSaturdayEvents)
			.where(eq(surprisingSaturdayEvents.id, id))
			.get();
		if (!event) throw new NotFoundException('Event not found');
		const targets = targetsForEvent(event);
		const paid = new Map(
			this.database.connection
				.select({
					userId: playerMoneyEvents.user_id,
					amount: playerMoneyEvents.amount_dabloons,
				})
				.from(playerMoneyEvents)
				.where(
					and(
						eq(playerMoneyEvents.source, 'surprising_saturday'),
						like(playerMoneyEvents.id, `%:${id}`),
					),
				)
				.all()
				.map((entry) => [entry.userId, entry.amount]),
		);
		return {
			id,
			title: event.title,
			preDescription: event.pre_description,
			shortDescription: event.short_description,
			description: event.description,
			startsAtUnixMs: event.starts_at_unix_ms,
			endsAtUnixMs: event.ends_at_unix_ms,
			criteriaType: event.criteria_type,
			items: targets,
			rewards: eventRewards(event.rewards_json),
			earnings:
				event.criteria_type === 'joined_earning'
					? this.rankedPlayers(event, targets).map((player) => ({
							name: player.name,
							uuid: player.uuid,
							amount: joinedEarningAmount(targets, player.completed),
							paid: player.userId ? (paid.get(player.userId) ?? 0) : 0,
						}))
					: [],
		};
	}

	rewardsForPlayer(uuid: string, now = Date.now()) {
		const playerUuid = normalizeMinecraftUuid(uuid);
		if (!playerUuid) return [];
		return this.database.connection
			.select()
			.from(surprisingSaturdayEvents)
			.where(lte(surprisingSaturdayEvents.ends_at_unix_ms, now))
			.all()
			.flatMap((event) => {
				if (event.criteria_type === 'empty') return [];
				if (event.criteria_type === 'joined_earning') {
					const targets = targetsForEvent(event);
					const player = this.rankedPlayers(event, targets).find(
						(entry) => entry.uuid === playerUuid,
					);
					const amount = player ? joinedEarningAmount(targets, player.completed) : 0;
					return amount > 0
						? [{ eventId: event.id, title: event.title, place: 0, amount }]
						: [];
				}
				const rewards = eventRewards(event.rewards_json);
				if (
					REWARD_PLACES.every((place) => rewards[place] === 0) &&
					rewards.participation === 0
				)
					return [];
				const ranked = this.rankedPlayers(event, eventTargets(event.criteria_json)).filter(
					(player) => !player.isCommittee,
				);
				const place = ranked.findIndex((player) => player.uuid === playerUuid);
				if (place < 0) return [];
				const amount = eventRewardAmount(
					rewards,
					place,
					ranked[place]?.completed.length ?? 0,
				);
				return amount > 0
					? [
							{
								eventId: event.id,
								title: event.title,
								place: place < 5 ? place + 1 : 0,
								amount,
							},
						]
					: [];
			});
	}

	update(idInput: string, body: Record<string, unknown> | undefined) {
		const event = this.adminDetail(idInput);
		return this.save(body, event.id);
	}

	private save(body: Record<string, unknown> | undefined, id?: number) {
		const title = typeof body?.title === 'string' ? body.title.trim() : '';
		const shortDescription =
			typeof body?.shortDescription === 'string' ? body.shortDescription.trim() : '';
		const preDescription =
			typeof body?.preDescription === 'string' ? body.preDescription.trim() : '';
		const description = typeof body?.description === 'string' ? body.description.trim() : '';
		const start = body?.startsAtUnixMs;
		const end = body?.endsAtUnixMs;
		const criteriaType = body?.criteriaType;
		const joinedEarning = criteriaType === 'joined_earning';
		const empty = criteriaType === 'empty';
		const items = empty
			? []
			: joinedEarning
				? earningTargets(body?.items)
				: completionTargets(body?.items);
		const rewards = body?.rewards;
		if (
			!title ||
			title.length > 120 ||
			!shortDescription ||
			shortDescription.length > 280 ||
			preDescription.length > 20_000 ||
			description.length > 20_000
		)
			throw new BadRequestException(
				'Enter a title, a short description of up to 280 characters, and full descriptions of up to 20,000 characters each',
			);
		if (
			!Number.isSafeInteger(start) ||
			!Number.isSafeInteger(end) ||
			(id === undefined && (start as number) < Date.now()) ||
			(end as number) <= (start as number)
		)
			throw new BadRequestException('Choose a valid start and an end after it');
		if (
			!['list_completion', 'joined_earning', 'empty'].includes(String(criteriaType)) ||
			!items
		)
			throw new BadRequestException(
				'Add 1-256 unique targets with valid IDs, names, and rewards',
			);
		if (!joinedEarning && !empty && !validRewards(rewards, items.length))
			throw new BadRequestException(
				'Set rewards from 0 to 1,000,000 Dabloons and a participation target within the completion list',
			);
		const overlap = this.database.connection
			.select({ id: surprisingSaturdayEvents.id })
			.from(surprisingSaturdayEvents)
			.where(
				and(
					lte(surprisingSaturdayEvents.starts_at_unix_ms, (end as number) - 1),
					gt(surprisingSaturdayEvents.ends_at_unix_ms, start as number),
					id ? ne(surprisingSaturdayEvents.id, id) : undefined,
				),
			)
			.get();
		if (overlap) throw new ConflictException('This event overlaps another event');
		const values = {
			title,
			pre_description: preDescription,
			short_description: shortDescription,
			description,
			starts_at_unix_ms: start as number,
			ends_at_unix_ms: end as number,
			criteria_type: empty ? 'empty' : joinedEarning ? 'joined_earning' : 'list_completion',
			criteria_json: JSON.stringify(items),
			rewards_json: JSON.stringify(joinedEarning || empty ? DEFAULT_REWARDS : rewards),
		} as const;
		const event = id
			? this.database.connection
					.update(surprisingSaturdayEvents)
					.set(values)
					.where(eq(surprisingSaturdayEvents.id, id))
					.returning({ id: surprisingSaturdayEvents.id })
					.get()
			: this.database.connection
					.insert(surprisingSaturdayEvents)
					.values(values)
					.returning({ id: surprisingSaturdayEvents.id })
					.get();
		return { ok: true, eventId: event.id };
	}

	remove(idInput: string) {
		const id = this.adminDetail(idInput).id;
		this.database.connection.transaction((tx) => {
			tx.delete(surprisingSaturdayCompletions)
				.where(eq(surprisingSaturdayCompletions.event_id, id))
				.run();
			tx.delete(surprisingSaturdayParticipants)
				.where(eq(surprisingSaturdayParticipants.event_id, id))
				.run();
			tx.delete(surprisingSaturdayEvents).where(eq(surprisingSaturdayEvents.id, id)).run();
		});
		return { ok: true };
	}

	recordParticipants(uuids: string[], eventId: number, now: number) {
		for (const uuid of uuids)
			this.database.connection
				.insert(surprisingSaturdayParticipants)
				.values({
					event_id: eventId,
					player_uuid: uuid,
					joined_at_unix_ms: now,
				})
				.onConflictDoNothing()
				.run();
	}

	recordCompletion(body: Record<string, unknown> | undefined) {
		const uuid = normalizeMinecraftUuid(
			typeof body?.playerUuid === 'string' ? body.playerUuid : '',
		);
		const itemId = typeof body?.itemId === 'string' ? body.itemId.trim() : '';
		const at = body?.occurredAtUnixMs;
		if (
			!uuid ||
			!validGoalId(itemId) ||
			(body?.completed !== undefined && body.completed !== true) ||
			!Number.isSafeInteger(at)
		)
			throw new BadRequestException('Invalid completion');
		const now = Date.now();
		if ((at as number) > now + 30_000)
			throw new BadRequestException('Completion timestamp is in the future');
		const event = this.active(at as number);
		if (!event || !['list_completion', 'joined_earning'].includes(event.criteria_type))
			throw new NotFoundException('No list completion event accepts scores now');
		const targets = targetsForEvent(event);
		if (!targets.some((item) => item.id === itemId))
			return {
				ok: true,
				accepted: false,
				podiumChange: null,
				score: this.score(uuid, String(at)),
			};
		return this.database.connection.transaction(() => {
			const earning = event.criteria_type === 'joined_earning';
			// ponytail: Read the podium twice per kill. Batch participant reads if event size makes this slow.
			const before = earning ? [] : this.podium(event, targets);
			this.recordParticipants([uuid], event.id, at as number);
			const inserted =
				this.database.connection
					.insert(surprisingSaturdayCompletions)
					.values({
						event_id: event.id,
						player_uuid: uuid,
						item_id: itemId,
						completed_at_unix_ms: at as number,
					})
					.onConflictDoNothing()
					.returning({ itemId: surprisingSaturdayCompletions.item_id })
					.all().length > 0;
			const after = inserted && !earning ? this.podium(event, targets) : before;
			const changed =
				inserted &&
				(before.length !== after.length ||
					before.some((entry, index) => entry.uuid !== after[index]?.uuid));
			return {
				ok: true,
				accepted: inserted,
				podiumChange: changed ? { playerUuid: uuid, before, after } : null,
				score: this.score(uuid, String(at)),
			};
		});
	}

	score(uuidInput: string, atUnixMsInput?: string) {
		const uuid = normalizeMinecraftUuid(uuidInput);
		if (!uuid) throw new NotFoundException('Player not found');
		const at = atUnixMsInput === undefined ? Date.now() : Number(atUnixMsInput);
		if (
			(atUnixMsInput !== undefined && !/^\d+$/.test(atUnixMsInput)) ||
			!Number.isSafeInteger(at)
		)
			throw new BadRequestException('Invalid score time');
		const event = this.active(at);
		if (!event || !['list_completion', 'joined_earning'].includes(event.criteria_type))
			throw new NotFoundException('No list completion event accepts scores at this time');
		const targets = new Map(targetsForEvent(event).map((item) => [item.id, item]));
		const completed = this.database.connection
			.select()
			.from(surprisingSaturdayCompletions)
			.where(
				and(
					eq(surprisingSaturdayCompletions.event_id, event.id),
					eq(surprisingSaturdayCompletions.player_uuid, uuid),
				),
			)
			.orderBy(asc(surprisingSaturdayCompletions.completed_at_unix_ms))
			.all()
			.flatMap((entry) => {
				const target = targets.get(entry.item_id);
				return target &&
					entry.completed_at_unix_ms >= event.starts_at_unix_ms &&
					entry.completed_at_unix_ms < event.ends_at_unix_ms
					? [target]
					: [];
			});
		return {
			eventId: event.id,
			score: completed.reduce((total, item) => total + item.points, 0),
			completed: completed.map((item) => ({
				id: item.id,
				name: item.name,
				points: item.points,
			})),
		};
	}

	private podium(event: SurprisingSaturdayEventRow, targets: CompletionTarget[]) {
		return this.rankedPlayers(event, targets)
			.filter((player) => !player.isCommittee)
			.slice(0, 3)
			.map((player) => ({
				uuid: player.uuid,
				name: player.name,
				color: player.color,
				role: player.role,
				score: player.points,
			}));
	}

	playerData(uuidInput: string) {
		const uuid = normalizeMinecraftUuid(uuidInput);
		if (!uuid) throw new NotFoundException('Player not found');
		const user = this.database.connection
			.select()
			.from(users)
			.where(eq(users.minecraft_uuid, uuid))
			.get();
		if (!user) throw new NotFoundException('Player not found');
		const profile = this.database.connection
			.select()
			.from(playerProfiles)
			.where(eq(playerProfiles.user_id, user.id))
			.get();
		const savedStats = this.database.connection
			.select({ json: playerStats.stats_json })
			.from(playerStats)
			.where(eq(playerStats.user_id, user.id))
			.get();
		const stats = savedStats ? normalizeStatsJson(savedStats.json).minecraft.stats : {};
		const dangerCount =
			(stats['minecraft.custom.minecraft:deaths']?.value ?? 0) +
			(stats['minecraft.used.minecraft:totem_of_undying']?.value ?? 0);
		const name = [profile?.preferred_name, profile?.pronouns].filter(Boolean).join(' - ');
		const dangerText = profile?.show_death_counter === 0 ? '' : `${dangerCount}☠`;
		return {
			uuid,
			minecraftUsername: user.minecraft_username,
			nickname: profile?.preferred_name ?? '',
			pronouns: profile?.pronouns ?? '',
			belowName: [name, dangerText].filter(Boolean).join(' - '),
			showDeathCounter: profile?.show_death_counter !== 0,
			color: effectivePlayerColor(uuid, profile?.color_hex),
			role: playerRole(user),
			isMember: Boolean(user.is_member || user.is_committee || user.is_super_admin),
		};
	}
}
