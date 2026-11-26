import { randomUUID } from 'node:crypto';
import {
	BadRequestException,
	ConflictException,
	Injectable,
	NotFoundException,
	OnModuleDestroy,
	OnModuleInit,
} from '@nestjs/common';
import { and, eq, getTableColumns, gte, lt, or, sql } from 'drizzle-orm';
import type { AuthenticatedUser } from '../auth/auth-session.service';
import {
	christmasPresents,
	christmasPresentSettings,
	DatabaseService,
	playerProfiles,
	SUPER_ADMIN_MINECRAFT_UUID,
	users,
} from '../database/database.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';

const START = Date.UTC(2026, 11, 1);
const RELEASE = Date.UTC(2026, 11, 25, 9);
const DAY_MS = 86_400_000;
const LETTER_COLORS = ['red', 'green', 'yellow', 'orange', 'blue', 'light-blue', 'purple', 'pink'];
type Present = typeof christmasPresents.$inferSelect;
type PresentSummary = Omit<Present, 'item_data'>;
const { item_data: _itemData, ...presentColumns } = getTableColumns(christmasPresents);
interface ItemResponse {
	success: boolean;
	message: string;
	item_data?: Buffer;
	item_name: string;
	item_count: number;
}
interface DeliveryResponse {
	delivered: boolean;
	already_delivered: boolean;
	message: string;
}

@Injectable()
export class PresentsService implements OnModuleInit, OnModuleDestroy {
	private timer?: NodeJS.Timeout;
	private returning = false;

	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
	) {}

	onModuleInit() {
		void this.deliverReturns();
		this.timer = setInterval(() => void this.deliverReturns(), 30_000);
	}

	onModuleDestroy() {
		if (this.timer) clearInterval(this.timer);
	}

	clock() {
		const simulatedAt =
			this.database.connection
				.select()
				.from(christmasPresentSettings)
				.where(eq(christmasPresentSettings.id, 1))
				.get()?.simulated_at_unix_ms ?? null;
		const now = simulatedAt ?? Date.now();
		return {
			now,
			simulatedAt,
			sending: now >= START && now < RELEASE,
			receiving: now >= RELEASE,
		};
	}

	joinStatus(username: string) {
		if (!this.clock().receiving) return { pending_count: 0 };
		const user = this.database.connection
			.select({ id: users.id })
			.from(users)
			.where(sql`lower(${users.minecraft_username}) = ${username.toLowerCase()}`)
			.get();
		if (!user) return { pending_count: 0 };
		const pending = this.database.connection
			.select({ id: christmasPresents.id })
			.from(christmasPresents)
			.where(
				and(
					eq(christmasPresents.recipient_user_id, user.id),
					sql`${christmasPresents.status} in ('sent', 'claiming', 'claimed')`,
					sql`${christmasPresents.opened_at_unix_ms} is null`,
				),
			)
			.all();
		return { pending_count: pending.length };
	}

	setClock(value: unknown) {
		let simulatedAt: number | null = null;
		if (value !== null) {
			if (typeof value !== 'string' || !/^2026-(11|12)-\d\dT\d\d:\d\d$/.test(value))
				throw new BadRequestException(
					'Choose a date and time in November or December 2026.',
				);
			simulatedAt = Date.parse(`${value}:00Z`);
			if (
				!Number.isFinite(simulatedAt) ||
				new Date(simulatedAt).toISOString().slice(0, 16) !== value
			)
				throw new BadRequestException('Choose a valid date and time.');
		}
		this.database.connection
			.insert(christmasPresentSettings)
			.values({ id: 1, simulated_at_unix_ms: simulatedAt })
			.onConflictDoUpdate({
				target: christmasPresentSettings.id,
				set: { simulated_at_unix_ms: simulatedAt },
			})
			.run();
		void this.deliverReturns();
		return this.clock();
	}

	get(user: AuthenticatedUser) {
		this.expireDrafts();
		const clock = this.clock();
		const rows = this.database.connection
			.select(presentColumns)
			.from(christmasPresents)
			.where(
				or(
					eq(christmasPresents.sender_user_id, user.id),
					eq(christmasPresents.recipient_user_id, user.id),
				),
			)
			.all();
		const names = this.names();
		const draft = this.findDraft(user.id) ?? null;
		const incoming = rows
			.filter(
				(row) =>
					row.recipient_user_id === user.id &&
					['sent', 'claiming', 'claimed'].includes(row.status) &&
					clock.receiving,
			)
			.map((row) => this.view(row, names, user.id));
		const sent = rows
			.filter(
				(row) =>
					row.sender_user_id === user.id &&
					row.sent_at_unix_ms !== null &&
					row.status !== 'deleted',
			)
			.map((row) => this.view(row, names, user.id));
		// December in London is GMT, so its day boundaries match UTC.
		const dayStart = Math.floor(clock.now / DAY_MS) * DAY_MS;
		const sentTodayRecipientIds = user.isCommittee
			? []
			: [
					...new Set(
						rows
							.filter(
								(row) =>
									row.sender_user_id === user.id &&
									row.sent_at_unix_ms !== null &&
									row.sent_at_unix_ms >= dayStart &&
									row.sent_at_unix_ms < dayStart + DAY_MS,
							)
							.map((row) => row.recipient_user_id)
							.filter((id): id is number => id !== null),
					),
				];
		const returned = rows
			.filter(
				(row) =>
					row.sender_user_id === user.id &&
					['returning', 'returned'].includes(row.status) &&
					row.admin_reason,
			)
			.map((row) => ({ id: row.id, reason: row.admin_reason }));
		const pendingReturns = rows
			.filter(
				(row) =>
					row.sender_user_id === user.id &&
					['returning', 'expired'].includes(row.status) &&
					!row.admin_reason,
			)
			.map((row) => ({ id: row.id, itemName: row.item_name }));
		return {
			clock,
			visible:
				clock.sending ||
				!!draft ||
				incoming.length > 0 ||
				sent.length > 0 ||
				returned.length > 0 ||
				pendingReturns.length > 0,
			draft: draft ? this.view(draft, names, user.id) : null,
			incoming,
			sent,
			sentTodayRecipientIds,
			returned,
			pendingReturns,
			players: this.database.connection
				.select({
					id: users.id,
					minecraftUsername: users.minecraft_username,
					nickname: playerProfiles.preferred_name,
				})
				.from(users)
				.leftJoin(playerProfiles, eq(playerProfiles.user_id, users.id))
				.all()
				.map((player) => ({
					...player,
					nickname: player.nickname?.trim() ?? '',
					color: '#ffffff',
				})),
		};
	}

	async preview(user: AuthenticatedUser) {
		this.requireSending();
		const result = await this.minecraft.gameplay<ItemResponse>('PreviewPresentHand', {
			minecraft_username: user.minecraftUsername,
		});
		if (!result.success) throw new BadRequestException(result.message);
		return { itemName: result.item_name, itemCount: result.item_count };
	}

	saveDraft(
		user: AuthenticatedUser,
		input: {
			recipient?: unknown;
			anonymous?: unknown;
			title?: unknown;
			body?: unknown;
			color?: unknown;
		},
	) {
		this.requireSending();
		const recipientName = typeof input.recipient === 'string' ? input.recipient.trim() : '';
		const recipient = recipientName
			? this.database.connection
					.select()
					.from(users)
					.where(sql`lower(${users.minecraft_username}) = ${recipientName.toLowerCase()}`)
					.get()
			: null;
		if (recipientName && !recipient)
			throw new BadRequestException('Choose a player from the list.');
		if (typeof input.title !== 'string' || input.title.length > 80)
			throw new BadRequestException('The title must be 80 characters or fewer.');
		if (typeof input.anonymous !== 'boolean')
			throw new BadRequestException('Choose whether to send anonymously.');
		if (typeof input.body !== 'string' || input.body.length > 5000)
			throw new BadRequestException('The message must be 5000 characters or fewer.');
		if (typeof input.color !== 'string' || !LETTER_COLORS.includes(input.color))
			throw new BadRequestException('Choose a letter colour.');
		const draft = this.draft(user.id);
		this.database.connection
			.update(christmasPresents)
			.set({
				recipient_user_id: recipient?.id ?? null,
				anonymous: input.anonymous ? 1 : 0,
				title: input.title.trim() || null,
				body: input.body.trim() || null,
				color: input.color,
			})
			.where(eq(christmasPresents.id, draft.id))
			.run();
		return this.get(user);
	}

	async capture(user: AuthenticatedUser) {
		this.requireSending();
		const draft = this.draft(user.id);
		if (draft.item_data) throw new ConflictException('Remove the current gift first.');
		if (!['draft', 'capturing'].includes(draft.status))
			throw new ConflictException('This gift is being returned. Try again shortly.');
		if (draft.status === 'draft')
			this.database.connection
				.update(christmasPresents)
				.set({ status: 'capturing' })
				.where(
					and(eq(christmasPresents.id, draft.id), eq(christmasPresents.status, 'draft')),
				)
				.run();
		const result = await this.minecraft.gameplay<ItemResponse>('CapturePresentHand', {
			minecraft_username: user.minecraftUsername,
			present_id: draft.id,
		});
		if (!result.success) {
			this.database.connection
				.update(christmasPresents)
				.set({ status: this.clock().sending ? 'draft' : 'expired-empty' })
				.where(
					and(
						eq(christmasPresents.id, draft.id),
						eq(christmasPresents.status, 'capturing'),
					),
				)
				.run();
			throw new BadRequestException(result.message);
		}
		if (!result.item_data?.length || result.item_data.length > 2_097_152)
			throw new BadRequestException('The gift could not be added. Try again.');
		this.database.connection
			.update(christmasPresents)
			.set({
				item_data: Buffer.from(result.item_data).toString('base64'),
				item_name: result.item_name,
				item_count: result.item_count,
				status: this.clock().sending ? 'draft' : 'returning',
			})
			.where(
				and(
					eq(christmasPresents.id, draft.id),
					sql`${christmasPresents.status} in ('capturing', 'expired')`,
				),
			)
			.run();
		return this.get(user);
	}

	async takeOut(user: AuthenticatedUser) {
		const draft = this.findDraft(user.id);
		if (!draft?.item_data) throw new BadRequestException('There is no stack in the letter.');
		if (draft.status === 'draft') {
			const reserved = this.database.connection
				.update(christmasPresents)
				.set({ status: 'withdrawing' })
				.where(
					and(eq(christmasPresents.id, draft.id), eq(christmasPresents.status, 'draft')),
				)
				.run();
			if (reserved.changes !== 1)
				throw new ConflictException('This letter changed. Refresh and try again.');
		}
		const delivered = await this.deliver(
			draft,
			user.minecraftUsername,
			`sender-return:${draft.id}`,
			'Your stack has been taken out of the Christmas letter.',
		);
		if (!delivered) {
			this.database.connection
				.update(christmasPresents)
				.set({ status: 'draft' })
				.where(
					and(
						eq(christmasPresents.id, draft.id),
						eq(christmasPresents.status, 'withdrawing'),
					),
				)
				.run();
			throw new BadRequestException('Join the Minecraft server to take the stack out.');
		}
		this.database.connection.transaction((tx) => {
			const finished = tx
				.update(christmasPresents)
				.set({ status: 'returned' })
				.where(
					and(
						eq(christmasPresents.id, draft.id),
						sql`${christmasPresents.status} in ('withdrawing', 'returning')`,
					),
				)
				.run();
			if (finished.changes === 1 && this.clock().sending)
				tx.insert(christmasPresents)
					.values({
						id: randomUUID(),
						sender_user_id: user.id,
						recipient_user_id: draft.recipient_user_id,
						anonymous: draft.anonymous,
						title: draft.title,
						body: draft.body,
						color: draft.color,
						created_at_unix_ms: Date.now(),
					})
					.run();
		});
		return this.get(user);
	}

	send(user: AuthenticatedUser) {
		const now = this.requireSending();
		this.database.connection.transaction((tx) => {
			const draft = tx
				.select()
				.from(christmasPresents)
				.where(
					and(
						eq(christmasPresents.sender_user_id, user.id),
						eq(christmasPresents.status, 'draft'),
					),
				)
				.get();
			if (!draft?.recipient_user_id) throw new BadRequestException('Choose a recipient.');
			if (!draft.body?.trim() && !draft.item_data)
				throw new BadRequestException('Write a message or add a gift before sending.');
			if (!user.isCommittee) {
				const dayStart = Math.floor(now / DAY_MS) * DAY_MS;
				const alreadySent = tx
					.select({ id: christmasPresents.id })
					.from(christmasPresents)
					.where(
						and(
							eq(christmasPresents.sender_user_id, user.id),
							eq(christmasPresents.recipient_user_id, draft.recipient_user_id),
							gte(christmasPresents.sent_at_unix_ms, dayStart),
							lt(christmasPresents.sent_at_unix_ms, dayStart + DAY_MS),
						),
					)
					.get();
				if (alreadySent)
					throw new ConflictException(
						'You already sent a letter to this player today. You can send one per player per day.',
					);
			}
			tx.update(christmasPresents)
				.set({ status: 'sent', sent_at_unix_ms: now })
				.where(eq(christmasPresents.id, draft.id))
				.run();
		});
		return this.get(user);
	}

	open(user: AuthenticatedUser, id: string) {
		if (!this.clock().receiving)
			throw new BadRequestException('Letters arrive at 9am on Christmas Day.');
		const present = this.incoming(user.id, id);
		const openedAt = Date.now();
		const firstOpen =
			this.database.connection
				.update(christmasPresents)
				.set({ opened_at_unix_ms: openedAt })
				.where(
					and(
						eq(christmasPresents.id, id),
						sql`${christmasPresents.opened_at_unix_ms} is null`,
					),
				)
				.run().changes === 1;
		return {
			firstOpen,
			present: this.view(
				{ ...present, opened_at_unix_ms: present.opened_at_unix_ms ?? openedAt },
				this.names(),
				user.id,
			),
		};
	}

	read(user: AuthenticatedUser, id: string) {
		if (!this.clock().receiving)
			throw new BadRequestException('Letters arrive at 9am on Christmas Day.');
		const present = this.incoming(user.id, id);
		if (!present.opened_at_unix_ms) throw new BadRequestException('Open the letter first.');
		const readAt = Date.now();
		this.database.connection
			.update(christmasPresents)
			.set({ read_at_unix_ms: readAt })
			.where(
				and(
					eq(christmasPresents.id, id),
					sql`${christmasPresents.read_at_unix_ms} is null`,
				),
			)
			.run();
		return {
			present: this.view(
				{ ...present, read_at_unix_ms: present.read_at_unix_ms ?? readAt },
				this.names(),
				user.id,
			),
		};
	}

	async claim(user: AuthenticatedUser, id: string) {
		if (!this.clock().receiving)
			throw new BadRequestException('Letters arrive at 9am on Christmas Day.');
		const present = this.incoming(user.id, id);
		if (!present.opened_at_unix_ms) throw new BadRequestException('Open the letter first.');
		if (!present.item_data)
			throw new BadRequestException('This letter has no gift to receive.');
		if (present.status === 'claimed')
			throw new ConflictException('You already received this stack.');
		if (present.status === 'sent') {
			const reserved = this.database.connection
				.update(christmasPresents)
				.set({ status: 'claiming' })
				.where(and(eq(christmasPresents.id, id), eq(christmasPresents.status, 'sent')))
				.run();
			if (reserved.changes !== 1)
				throw new ConflictException('This letter changed. Refresh and try again.');
		}
		const delivered = await this.deliver(
			present,
			user.minecraftUsername,
			`present:${id}`,
			'Your Christmas present has arrived!',
		);
		if (!delivered) {
			this.database.connection
				.update(christmasPresents)
				.set({ status: 'sent' })
				.where(and(eq(christmasPresents.id, id), eq(christmasPresents.status, 'claiming')))
				.run();
			throw new BadRequestException('Join the Minecraft server to receive your present.');
		}
		this.database.connection
			.update(christmasPresents)
			.set({ status: 'claimed', claimed_at_unix_ms: Date.now() })
			.where(and(eq(christmasPresents.id, id), eq(christmasPresents.status, 'claiming')))
			.run();
		return this.get(user);
	}

	admin() {
		const names = this.names();
		return {
			clock: this.clock(),
			presents: this.database.connection
				.select(presentColumns)
				.from(christmasPresents)
				.all()
				.filter((row) => row.sent_at_unix_ms !== null)
				.map((row) => this.view(row, names, -1)),
		};
	}

	async adminAction(id: string, action: string, reason: unknown) {
		const row = this.database.connection
			.select()
			.from(christmasPresents)
			.where(eq(christmasPresents.id, id))
			.get();
		if (!row?.sent_at_unix_ms) throw new NotFoundException('Present not found.');
		if (action === 'copy') {
			if (!row.item_data) throw new BadRequestException('This letter has no gift to copy.');
			if (row.admin_copy_at_unix_ms)
				throw new ConflictException('MMU admin already has a copy.');
			const admin = this.database.connection
				.select()
				.from(users)
				.all()
				.find(
					(user) =>
						user.minecraft_uuid?.replaceAll('-', '').toLowerCase() ===
						SUPER_ADMIN_MINECRAFT_UUID,
				);
			if (!admin) throw new NotFoundException('MMU admin account not found.');
			if (
				!(await this.deliver(
					row,
					admin.minecraft_username,
					`admin-copy:${id}`,
					'A Christmas present inspection copy was delivered.',
				))
			)
				throw new BadRequestException('MMU admin must be online to receive the copy.');
			this.database.connection
				.update(christmasPresents)
				.set({ admin_copy_at_unix_ms: Date.now() })
				.where(eq(christmasPresents.id, id))
				.run();
		} else if (action === 'delete' && row.status === 'sent') {
			const changed = this.database.connection
				.update(christmasPresents)
				.set({ status: 'deleted' })
				.where(and(eq(christmasPresents.id, id), eq(christmasPresents.status, 'sent')))
				.run();
			if (changed.changes !== 1)
				throw new ConflictException('This present changed. Refresh and try again.');
		} else if (action === 'undo' && row.status === 'deleted') {
			const changed = this.database.connection
				.update(christmasPresents)
				.set({ status: 'sent' })
				.where(and(eq(christmasPresents.id, id), eq(christmasPresents.status, 'deleted')))
				.run();
			if (changed.changes !== 1)
				throw new ConflictException('This present changed. Refresh and try again.');
		} else if (action === 'return' && ['sent', 'deleted'].includes(row.status)) {
			if (typeof reason !== 'string' || !reason.trim() || reason.length > 300)
				throw new BadRequestException('Write a return reason of 1 to 300 characters.');
			const changed = this.database.connection
				.update(christmasPresents)
				.set({
					status: row.item_data ? 'returning' : 'returned',
					admin_reason: reason.trim(),
				})
				.where(
					and(
						eq(christmasPresents.id, id),
						sql`${christmasPresents.status} in ('sent', 'deleted')`,
					),
				)
				.run();
			if (changed.changes !== 1)
				throw new ConflictException('This present changed. Refresh and try again.');
			void this.deliverReturns();
		} else throw new ConflictException('That action is unavailable for this present.');
		return this.admin();
	}

	private async deliver(row: Present, username: string, receipt: string, message: string) {
		if (!row.item_data) return false;
		const result = await this.minecraft.gameplay<DeliveryResponse>('DeliverPresentItem', {
			minecraft_username: username,
			receipt_id: receipt,
			item_data: Buffer.from(row.item_data, 'base64'),
			message,
		});
		return result.delivered || result.already_delivered;
	}

	private expireDrafts() {
		if (this.clock().now < RELEASE) return;
		this.database.connection
			.update(christmasPresents)
			.set({ status: 'returning' })
			.where(
				and(
					sql`${christmasPresents.status} in ('draft', 'withdrawing')`,
					sql`${christmasPresents.item_data} is not null`,
				),
			)
			.run();
		this.database.connection
			.update(christmasPresents)
			.set({ status: 'expired' })
			.where(eq(christmasPresents.status, 'capturing'))
			.run();
		this.database.connection
			.update(christmasPresents)
			.set({ status: 'expired-empty' })
			.where(
				and(
					eq(christmasPresents.status, 'draft'),
					sql`${christmasPresents.item_data} is null`,
				),
			)
			.run();
	}

	private async deliverReturns() {
		if (this.returning) return;
		this.returning = true;
		try {
			this.expireDrafts();
			const names = this.names();
			const expired = this.database.connection
				.select()
				.from(christmasPresents)
				.where(eq(christmasPresents.status, 'expired'))
				.all();
			for (const row of expired) {
				try {
					const result = await this.minecraft.gameplay<ItemResponse>(
						'CapturePresentHand',
						{
							minecraft_username: names.get(row.sender_user_id) ?? '',
							present_id: row.id,
							recover_only: true,
						},
					);
					if (result.success && result.item_data?.length)
						this.database.connection
							.update(christmasPresents)
							.set({
								item_data: Buffer.from(result.item_data).toString('base64'),
								item_name: result.item_name,
								item_count: result.item_count,
								status: 'returning',
							})
							.where(eq(christmasPresents.id, row.id))
							.run();
				} catch {
					/* The Minecraft server may be unavailable. */
				}
			}
			const rows = this.database.connection
				.select()
				.from(christmasPresents)
				.where(eq(christmasPresents.status, 'returning'))
				.all();
			for (const row of rows) {
				const username = names.get(row.sender_user_id);
				if (!username) continue;
				const message = row.admin_reason
					? `An admin returned your Christmas present: ${row.admin_reason}`
					: 'Your unsent Christmas letter was returned after the deadline.';
				try {
					if (await this.deliver(row, username, `sender-return:${row.id}`, message))
						this.database.connection
							.update(christmasPresents)
							.set({ status: 'returned' })
							.where(eq(christmasPresents.id, row.id))
							.run();
				} catch {
					/* Try again when the player is online. */
				}
			}
		} finally {
			this.returning = false;
		}
	}

	private requireSending() {
		const clock = this.clock();
		if (!clock.sending)
			throw new BadRequestException(
				'Christmas letters can be sent from 1 December until 9am on 25 December 2026.',
			);
		return clock.now;
	}

	private findDraft(userId: number) {
		return this.database.connection
			.select()
			.from(christmasPresents)
			.where(
				and(
					eq(christmasPresents.sender_user_id, userId),
					sql`${christmasPresents.status} in ('draft', 'capturing', 'withdrawing')`,
				),
			)
			.get();
	}

	private draft(userId: number) {
		const found = this.findDraft(userId);
		if (found) return found;
		const id = randomUUID();
		this.database.connection
			.insert(christmasPresents)
			.values({
				id,
				sender_user_id: userId,
				created_at_unix_ms: Date.now(),
			})
			.run();
		const created = this.findDraft(userId);
		if (!created) throw new ConflictException('Could not create the letter. Try again.');
		return created;
	}

	private incoming(userId: number, id: string) {
		const row = this.database.connection
			.select()
			.from(christmasPresents)
			.where(
				and(eq(christmasPresents.id, id), eq(christmasPresents.recipient_user_id, userId)),
			)
			.get();
		if (!row || !['sent', 'claiming', 'claimed'].includes(row.status))
			throw new NotFoundException('Letter not found.');
		return row;
	}

	private names() {
		return new Map(
			this.database.connection
				.select({ id: users.id, name: users.minecraft_username })
				.from(users)
				.all()
				.map((user) => [user.id, user.name]),
		);
	}

	private view(row: PresentSummary, names: Map<number, string>, viewerId: number) {
		const sender = names.get(row.sender_user_id) ?? 'Unknown player';
		const admin = viewerId === -1;
		const recipient = viewerId === row.recipient_user_id;
		return {
			id: row.id,
			from: !recipient || admin || !row.anonymous ? sender : null,
			to: names.get(row.recipient_user_id ?? -1) ?? null,
			title:
				row.status === 'draft'
					? (row.title ?? '')
					: (row.title ??
						(row.anonymous && recipient && !admin
							? 'A Christmas letter'
							: `Letter from ${sender}`)),
			anonymous: !!row.anonymous,
			body: !recipient || admin || row.opened_at_unix_ms ? row.body : null,
			color: row.color,
			itemName: !recipient || admin || row.opened_at_unix_ms ? row.item_name : null,
			itemCount: !recipient || admin || row.opened_at_unix_ms ? row.item_count : null,
			status: row.status,
			opened: row.opened_at_unix_ms !== null,
			read: row.read_at_unix_ms !== null,
			sentAt: row.sent_at_unix_ms,
			claimedAt: row.claimed_at_unix_ms,
			adminCopyAt: admin ? row.admin_copy_at_unix_ms : undefined,
			adminReason: !recipient || admin ? row.admin_reason : null,
		};
	}
}
