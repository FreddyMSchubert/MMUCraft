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
	surprisingSaturdayCompletions,
	surprisingSaturdayEvents,
	surprisingSaturdayParticipants,
	users,
} from '../database/database.service';
import { normalizeMinecraftUuid } from '../database/minecraft-identity.service';
import { effectivePlayerColor } from '../players/player-color';

const ITEM_ID = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const TITLE_GRAPHEMES = new Intl.Segmenter(undefined, { granularity: 'grapheme' });

function titleWordLengths(title: string) {
	return title
		.trim()
		.split(/\s+/u)
		.map((word) => [...TITLE_GRAPHEMES.segment(word)].length);
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
			};

		const items = JSON.parse(event.criteria_json) as string[];
		const participants = this.database.connection
			.select()
			.from(surprisingSaturdayParticipants)
			.where(eq(surprisingSaturdayParticipants.event_id, id))
			.all();
		const completions = this.database.connection
			.select()
			.from(surprisingSaturdayCompletions)
			.where(eq(surprisingSaturdayCompletions.event_id, id))
			.all();
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
				.filter((entry) => entry.player_uuid === participant.player_uuid)
				.map((entry) => ({ itemId: entry.item_id, atUnixMs: entry.completed_at_unix_ms }));
			return {
				uuid: participant.player_uuid,
				name: profile?.preferred_name.trim()
					? profile.preferred_name
					: (user?.minecraft_username ?? 'Unknown player'),
				color: effectivePlayerColor(participant.player_uuid, profile?.color_hex),
				completed,
				lastCompletionAtUnixMs: Math.max(0, ...completed.map((entry) => entry.atUnixMs)),
			};
		});
		players.sort(
			(a, b) =>
				b.completed.length - a.completed.length ||
				a.lastCompletionAtUnixMs - b.lastCompletionAtUnixMs ||
				a.name.localeCompare(b.name),
		);
		return {
			id,
			status: event.ends_at_unix_ms > now ? 'live' : 'ended',
			title: event.title,
			description: event.description,
			startsAtUnixMs: event.starts_at_unix_ms,
			endsAtUnixMs: event.ends_at_unix_ms,
			criteriaType: event.criteria_type,
			items,
			players,
		};
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
			description: event.description,
			startsAtUnixMs: event.starts_at_unix_ms,
			endsAtUnixMs: event.ends_at_unix_ms,
			criteriaType: event.criteria_type,
			items: JSON.parse(event.criteria_json) as string[],
		};
	}

	update(idInput: string, body: Record<string, unknown> | undefined) {
		const event = this.adminDetail(idInput);
		if (event.startsAtUnixMs <= Date.now())
			throw new ConflictException('Started events cannot be edited');
		return this.save(body, event.id);
	}

	private save(body: Record<string, unknown> | undefined, id?: number) {
		const title = typeof body?.title === 'string' ? body.title.trim() : '';
		const preDescription =
			typeof body?.preDescription === 'string' ? body.preDescription.trim() : '';
		const description = typeof body?.description === 'string' ? body.description.trim() : '';
		const start = body?.startsAtUnixMs;
		const end = body?.endsAtUnixMs;
		const items = body?.items;
		if (
			!title ||
			title.length > 120 ||
			preDescription.length > 20_000 ||
			description.length > 20_000
		)
			throw new BadRequestException(
				'Enter a title of up to 120 characters and descriptions of up to 20,000 characters each',
			);
		if (
			!Number.isSafeInteger(start) ||
			!Number.isSafeInteger(end) ||
			(start as number) < Date.now() ||
			(end as number) <= (start as number)
		)
			throw new BadRequestException('Choose a future start and an end after it');
		if (
			body?.criteriaType !== 'list_completion' ||
			!Array.isArray(items) ||
			items.length === 0 ||
			items.length > 256 ||
			!items.every((item) => typeof item === 'string' && ITEM_ID.test(item)) ||
			new Set(items).size !== items.length
		)
			throw new BadRequestException('List completion needs 1-256 unique namespaced item IDs');
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
		const id = Number(idInput);
		const event = this.database.connection
			.select()
			.from(surprisingSaturdayEvents)
			.where(eq(surprisingSaturdayEvents.id, id))
			.get();
		if (!event) throw new NotFoundException('Event not found');
		if (event.starts_at_unix_ms <= Date.now())
			throw new ConflictException('Started events remain in history');
		this.database.connection
			.delete(surprisingSaturdayEvents)
			.where(eq(surprisingSaturdayEvents.id, id))
			.run();
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

	setCompletion(body: Record<string, unknown> | undefined) {
		const uuid = normalizeMinecraftUuid(
			typeof body?.playerUuid === 'string' ? body.playerUuid : '',
		);
		const itemId = body?.itemId;
		const at = body?.occurredAtUnixMs;
		if (
			!uuid ||
			typeof itemId !== 'string' ||
			!ITEM_ID.test(itemId) ||
			typeof body?.completed !== 'boolean' ||
			!Number.isSafeInteger(at)
		)
			throw new BadRequestException('Invalid completion');
		const now = Date.now();
		if ((at as number) > now + 30_000)
			throw new BadRequestException('Completion timestamp is in the future');
		const event = this.active(at as number);
		if (event?.criteria_type !== 'list_completion')
			throw new NotFoundException('No list completion event accepts scores now');
		if (!(JSON.parse(event.criteria_json) as string[]).includes(itemId))
			throw new BadRequestException('This item is not in the event list');
		this.recordParticipants([uuid], event.id, at as number);
		if (body.completed) {
			this.database.connection
				.insert(surprisingSaturdayCompletions)
				.values({
					event_id: event.id,
					player_uuid: uuid,
					item_id: itemId,
					completed_at_unix_ms: at as number,
				})
				.onConflictDoNothing()
				.run();
		} else {
			this.database.connection
				.delete(surprisingSaturdayCompletions)
				.where(
					and(
						eq(surprisingSaturdayCompletions.event_id, event.id),
						eq(surprisingSaturdayCompletions.player_uuid, uuid),
						eq(surprisingSaturdayCompletions.item_id, itemId),
					),
				)
				.run();
		}
		return { ok: true };
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
			nickname: profile?.preferred_name.trim()
				? profile.preferred_name
				: user.minecraft_username,
			pronouns: profile?.pronouns ?? '',
			color: effectivePlayerColor(uuid, profile?.color_hex),
			role:
				user.is_committee || user.is_super_admin
					? 'committee'
					: user.is_member
						? 'member'
						: 'external',
		};
	}
}
