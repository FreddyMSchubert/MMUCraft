import {
	BadRequestException,
	ConflictException,
	Injectable,
	NotFoundException,
} from '@nestjs/common';
import { and, asc, eq, gt, lte, ne } from 'drizzle-orm';
import {
	DatabaseService,
	playerProfiles,
	SurprisingSaturdayEventRow,
	UserRow,
	surprisingSaturdayCompletions,
	surprisingSaturdayEvents,
	surprisingSaturdayParticipants,
	users,
} from '../database/database.service';
import { normalizeMinecraftUuid } from '../database/minecraft-identity.service';
import { effectivePlayerColor } from '../players/player-color';
import { customPlayerEmojis } from '../players/player-emojis';

const ITEM_ID = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const TITLE_GRAPHEMES = new Intl.Segmenter(undefined, { granularity: 'grapheme' });

interface CompletionTarget {
	id: string;
	name: string;
	url: string;
	points: number;
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
			typeof id !== 'string' ||
			!ITEM_ID.test(id) ||
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
		targets.push({ id, name: name.trim(), url: url.trim(), points: points as number });
	}
	return new Set(targets.map((target) => target.id)).size === targets.length ? targets : null;
}

function eventTargets(json: string) {
	return completionTargets(JSON.parse(json)) ?? [];
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
		: user?.responsible_user_id != null
			? 'External'
			: user?.is_member
				? 'Member'
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

		const items = eventTargets(event.criteria_json);
		const players = this.rankedPlayers(event, items);
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
		const players = participants.map((participant) => {
			const user = this.database.connection
				.select()
				.from(users)
				.where(eq(users.minecraft_uuid, participant.player_uuid))
				.get();
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
			return {
				uuid: participant.player_uuid,
				name: user?.minecraft_username ?? 'Unknown player',
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
				lastCompletionAtUnixMs: Math.max(0, ...completed.map((entry) => entry.atUnixMs)),
			};
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
		return {
			id,
			title: event.title,
			preDescription: event.pre_description,
			shortDescription: event.short_description,
			description: event.description,
			startsAtUnixMs: event.starts_at_unix_ms,
			endsAtUnixMs: event.ends_at_unix_ms,
			criteriaType: event.criteria_type,
			items: eventTargets(event.criteria_json),
		};
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
		const items = completionTargets(body?.items);
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
		if (body?.criteriaType !== 'list_completion' || !items)
			throw new BadRequestException(
				'Add 1-256 unique targets with valid IDs, names, links, and points',
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
			criteria_type: 'list_completion',
			criteria_json: JSON.stringify(items),
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
		const itemId = body?.itemId;
		const at = body?.occurredAtUnixMs;
		if (
			!uuid ||
			typeof itemId !== 'string' ||
			!ITEM_ID.test(itemId) ||
			(body?.completed !== undefined && body.completed !== true) ||
			!Number.isSafeInteger(at)
		)
			throw new BadRequestException('Invalid completion');
		const now = Date.now();
		if ((at as number) > now + 30_000)
			throw new BadRequestException('Completion timestamp is in the future');
		const event = this.active(at as number);
		if (event?.criteria_type !== 'list_completion')
			throw new NotFoundException('No list completion event accepts scores now');
		const targets = eventTargets(event.criteria_json);
		if (!targets.some((item) => item.id === itemId))
			throw new BadRequestException('This item is not in the event list');
		return this.database.connection.transaction(() => {
			// ponytail: Read the podium twice per kill. Batch participant reads if event size makes this slow.
			const before = this.podium(event, targets);
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
			const after = inserted ? this.podium(event, targets) : before;
			const changed =
				inserted &&
				(before.length !== after.length ||
					before.some((entry, index) => entry.uuid !== after[index]?.uuid));
			return {
				ok: true,
				accepted: inserted,
				podiumChange: changed ? { playerUuid: uuid, before, after } : null,
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
		if (event?.criteria_type !== 'list_completion')
			throw new NotFoundException('No list completion event accepts scores at this time');
		const targets = new Map(eventTargets(event.criteria_json).map((item) => [item.id, item]));
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
		return {
			uuid,
			minecraftUsername: user.minecraft_username,
			nickname: profile?.preferred_name ?? '',
			pronouns: profile?.pronouns ?? '',
			color: effectivePlayerColor(uuid, profile?.color_hex),
			role: playerRole(user),
			isMember: Boolean(user.is_member || user.is_committee || user.is_super_admin),
		};
	}
}
