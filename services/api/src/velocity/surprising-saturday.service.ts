import { Injectable, NotFoundException } from '@nestjs/common';
import { asc, eq, lte } from 'drizzle-orm';
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
import { ADMIN_SIGNUP_EMAIL } from '../auth/auth.util';
import { effectivePlayerColor, playerAvatarUrl } from '../players/player-color';
import { customPlayerEmojis } from '../players/player-emojis';

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

	list() {
		const now = Date.now();
		return this.database.connection
			.select()
			.from(surprisingSaturdayEvents)
			.where(lte(surprisingSaturdayEvents.ends_at_unix_ms, now))
			.orderBy(asc(surprisingSaturdayEvents.starts_at_unix_ms))
			.all()
			.map((event) => ({
				id: event.id,
				startsAtUnixMs: event.starts_at_unix_ms,
				endsAtUnixMs: event.ends_at_unix_ms,
				status: 'ended',
				title: event.title,
				shortDescription: event.short_description,
				titleWordLengths: titleWordLengths(event.title),
				description: event.description,
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
		if (!event || event.ends_at_unix_ms > Date.now())
			throw new NotFoundException('Event not found');

		const items = event.criteria_type === 'empty' ? [] : targetsForEvent(event);
		const players = event.criteria_type === 'empty' ? [] : this.rankedPlayers(event, items);
		return {
			id,
			status: 'ended',
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
}
