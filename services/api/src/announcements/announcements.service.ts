import { BadRequestException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { and, asc, eq, gt, inArray, lte } from 'drizzle-orm';
import { announcementReads, announcements, DatabaseService } from '../database/database.service';
import { normalizeMinecraftUuid } from '../database/minecraft-identity.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { parseLondonDateTime } from '../gifts/countdowns.service';

export interface AnnouncementInput {
	text?: string;
	linkUrl?: string | null;
	startsAt?: string;
	endsAt?: string;
}

@Injectable()
export class AnnouncementsService {
	private readonly logger = new Logger(AnnouncementsService.name);

	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
	) {}

	listOutstanding() {
		return { announcements: this.outstandingRows().map(toAnnouncement) };
	}

	async create(input: AnnouncementInput) {
		const values = announcementValues(input);
		const last = this.database.connection
			.select({ position: announcements.position })
			.from(announcements)
			.orderBy(asc(announcements.position))
			.all()
			.at(-1);
		const id = Number(
			this.database.connection
				.insert(announcements)
				.values({
					...values,
					position: (last?.position ?? -1) + 1,
					created_at_unix_ms: Date.now(),
				})
				.run().lastInsertRowid,
		);
		const row = this.database.connection
			.select()
			.from(announcements)
			.where(eq(announcements.id, id))
			.get();
		if (!row) throw new NotFoundException('Announcement not found');

		if (row.starts_at_unix_ms <= Date.now()) await this.broadcast(row);
		return toAnnouncement(row);
	}

	move(idInput: string, direction: 'up' | 'down' | undefined) {
		const id = positiveId(idInput);
		if (direction !== 'up' && direction !== 'down')
			throw new BadRequestException('Direction must be up or down');

		this.database.connection.transaction((tx) => {
			const rows = tx
				.select()
				.from(announcements)
				.where(gt(announcements.ends_at_unix_ms, Date.now()))
				.orderBy(asc(announcements.position))
				.all();
			const index = rows.findIndex((row) => row.id === id);
			if (index === -1) throw new NotFoundException('Announcement not found');
			const current = rows[index];
			const swap = rows[index + (direction === 'up' ? -1 : 1)];
			if (!current || !swap) return;
			tx.update(announcements)
				.set({ position: swap.position })
				.where(eq(announcements.id, current.id))
				.run();
			tx.update(announcements)
				.set({ position: current.position })
				.where(eq(announcements.id, swap.id))
				.run();
		});
		return this.listOutstanding();
	}

	getActiveForPlayer(uuidInput: string, includeRead: boolean) {
		const uuid = requiredUuid(uuidInput);
		const rows = this.activeRows();
		if (includeRead || rows.length === 0)
			return { announcements: rows.map(toGrpcAnnouncement) };
		const readIds = new Set(
			this.database.connection
				.select({ id: announcementReads.announcement_id })
				.from(announcementReads)
				.where(eq(announcementReads.minecraft_uuid, uuid))
				.all()
				.map(({ id }) => id),
		);
		return {
			announcements: rows
				.filter(({ id }) => !readIds.has(id))
				.slice(0, 1)
				.map(toGrpcAnnouncement),
		};
	}

	markRead(uuidInput: string, ids: number[]) {
		const uuid = requiredUuid(uuidInput);
		const announcementIds = [...new Set(ids.filter((id) => Number.isInteger(id) && id > 0))];
		if (announcementIds.length === 0) return { marked: 0 };
		const existingIds = this.database.connection
			.select({ id: announcements.id })
			.from(announcements)
			.where(inArray(announcements.id, announcementIds))
			.all();
		if (existingIds.length === 0) return { marked: 0 };
		const result = this.database.connection
			.insert(announcementReads)
			.values(
				existingIds.map(({ id }) => ({
					announcement_id: id,
					minecraft_uuid: uuid,
					read_at_unix_ms: Date.now(),
				})),
			)
			.onConflictDoNothing()
			.run();
		return { marked: result.changes };
	}

	private async broadcast(row: typeof announcements.$inferSelect) {
		try {
			const response = await this.minecraft.gameplay<{ minecraft_uuids: string[] }>(
				'BroadcastAnnouncement',
				{ announcement: toGrpcAnnouncement(row) },
				{ deadline: Date.now() + 5_000 },
			);
			for (const uuid of response.minecraft_uuids) this.markRead(uuid, [row.id]);
		} catch (error) {
			this.logger.warn(`Could not broadcast announcement ${row.id}: ${String(error)}`);
		}
	}

	private outstandingRows() {
		return this.database.connection
			.select()
			.from(announcements)
			.where(gt(announcements.ends_at_unix_ms, Date.now()))
			.orderBy(asc(announcements.position))
			.all();
	}

	private activeRows() {
		const now = Date.now();
		return this.database.connection
			.select()
			.from(announcements)
			.where(
				and(
					lte(announcements.starts_at_unix_ms, now),
					gt(announcements.ends_at_unix_ms, now),
				),
			)
			.orderBy(asc(announcements.position))
			.all();
	}
}

function announcementValues(input: AnnouncementInput) {
	const text = input.text?.trim() ?? '';
	if (!text) throw new BadRequestException('Announcement text is required');
	if (text.length > 1_000)
		throw new BadRequestException('Announcement text must be 1000 characters or fewer');
	const startsAtUnixMs = parseLondonDateTime(input.startsAt);
	const endsAtUnixMs = parseLondonDateTime(input.endsAt);
	if (endsAtUnixMs <= startsAtUnixMs)
		throw new BadRequestException('The end date must be after the start date');
	if (endsAtUnixMs <= Date.now())
		throw new BadRequestException('The end date must be in the future');
	return {
		text,
		link_url: optionalUrl(input.linkUrl),
		starts_at_unix_ms: startsAtUnixMs,
		ends_at_unix_ms: endsAtUnixMs,
	};
}

function optionalUrl(input: string | null | undefined) {
	if (!input) return null;
	if (input.length > 2_000) throw new BadRequestException('Link URL is too long');
	try {
		const url = new URL(input);
		if (url.protocol !== 'https:' || url.username || url.password) throw new Error();
		return url.toString();
	} catch {
		throw new BadRequestException('Link must be a valid HTTPS URL');
	}
}

function requiredUuid(input: string) {
	const uuid = normalizeMinecraftUuid(input);
	if (!uuid) throw new BadRequestException('Minecraft UUID is invalid');
	return uuid;
}

function positiveId(input: string) {
	const id = Number(input);
	if (!Number.isInteger(id) || id <= 0) throw new NotFoundException('Announcement not found');
	return id;
}

function toAnnouncement(row: typeof announcements.$inferSelect) {
	return {
		id: row.id,
		text: row.text,
		linkUrl: row.link_url,
		startsAtUnixMs: row.starts_at_unix_ms,
		endsAtUnixMs: row.ends_at_unix_ms,
	};
}

function toGrpcAnnouncement(row: typeof announcements.$inferSelect) {
	return { id: row.id, text: row.text, link_url: row.link_url ?? '' };
}
