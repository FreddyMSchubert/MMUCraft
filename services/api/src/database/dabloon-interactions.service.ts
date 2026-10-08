import { BadRequestException, Injectable } from '@nestjs/common';
import { and, desc, eq, gte, lte, lt, or, sql, type SQL } from 'drizzle-orm';
import { DatabaseService, dabloonInteractions } from './database.service';
import { MinecraftIdentityService } from './minecraft-identity.service';

const PAGE_SIZE = 50;
const ACTIONS = [
	'craft',
	'creative_edit',
	'drop',
	'despawn',
	'destroy',
	'pickup',
	'earn',
	'grant',
	'spend',
	'set',
	'right_click',
	'wallet_withdraw',
	'transfer',
];
const ITEM_KINDS = ['coin', 'wallet', 'container', 'balance', 'mixed'];

export interface DabloonInteractionInput {
	action?: string;
	item_kind?: string;
	item_id?: string;
	item_count?: number;
	amount_dabloons?: number;
	actor_name?: string;
	minecraft_uuid?: string;
	context?: string;
	dimension?: string;
	x?: number;
	y?: number;
	z?: number;
	balance_before?: number;
	balance_after?: number;
	unix_ms?: number;
}

export interface DabloonInteractionFilters {
	beforeId?: string;
	userId?: string;
	action?: string;
	itemKind?: string;
	dimension?: string;
	minAmount?: string;
	maxAmount?: string;
	fromUnixMs?: string;
	toUnixMs?: string;
	search?: string;
}

@Injectable()
export class DabloonInteractionsService {
	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
	) {}

	record(input: DabloonInteractionInput) {
		const action = input.action ?? '';
		const itemKind = input.item_kind ?? '';
		const actorName = input.actor_name?.trim().slice(0, 100) ?? '';
		const amount = input.amount_dabloons;
		if (
			!ACTIONS.includes(action) ||
			!ITEM_KINDS.includes(itemKind) ||
			!actorName ||
			!Number.isSafeInteger(amount) ||
			amount === undefined ||
			amount < 0
		)
			return { accepted: false };

		const trimmedUuid = input.minecraft_uuid?.trim();
		const uuid = trimmedUuid?.length ? trimmedUuid : null;
		const user = uuid ? this.identities.findByUuid(uuid) : null;
		this.database.connection
			.insert(dabloonInteractions)
			.values({
				action,
				item_kind: itemKind,
				item_id: input.item_id?.slice(0, 120) ?? '',
				item_count: safeNonNegative(input.item_count),
				amount_dabloons: amount,
				actor_name: actorName,
				minecraft_uuid: uuid,
				user_id: user?.id ?? null,
				context: input.context?.slice(0, 8000) ?? '',
				dimension: input.dimension?.slice(0, 120) ?? '',
				x: safeInteger(input.x),
				y: safeInteger(input.y),
				z: safeInteger(input.z),
				balance_before: optionalSafeInteger(input.balance_before),
				balance_after: optionalSafeInteger(input.balance_after),
				created_at_unix_ms: safeNonNegative(input.unix_ms) || Date.now(),
			})
			.run();
		return { accepted: true };
	}

	list(filters: DabloonInteractionFilters) {
		const conditions: SQL[] = [];
		const beforeId = filterInteger(filters.beforeId, 'beforeId', 1);
		const userId = filterInteger(filters.userId, 'userId', 1);
		const minAmount = filterInteger(filters.minAmount, 'minAmount', 0);
		const maxAmount = filterInteger(filters.maxAmount, 'maxAmount', 0);
		const from = filterInteger(filters.fromUnixMs, 'fromUnixMs', 0);
		const to = filterInteger(filters.toUnixMs, 'toUnixMs', 0);
		if (beforeId !== null) conditions.push(lt(dabloonInteractions.id, beforeId));
		if (userId !== null) conditions.push(eq(dabloonInteractions.user_id, userId));
		if (minAmount !== null)
			conditions.push(gte(dabloonInteractions.amount_dabloons, minAmount));
		if (maxAmount !== null)
			conditions.push(lte(dabloonInteractions.amount_dabloons, maxAmount));
		if (from !== null) conditions.push(gte(dabloonInteractions.created_at_unix_ms, from));
		if (to !== null) conditions.push(lte(dabloonInteractions.created_at_unix_ms, to));
		if (minAmount !== null && maxAmount !== null && minAmount > maxAmount)
			throw new BadRequestException('Minimum amount must not exceed maximum amount');
		if (from !== null && to !== null && from > to)
			throw new BadRequestException('The start time must be before the end time');
		if (filters.action) {
			if (!ACTIONS.includes(filters.action)) throw new BadRequestException('Invalid action');
			conditions.push(eq(dabloonInteractions.action, filters.action));
		}
		if (filters.itemKind) {
			if (!ITEM_KINDS.includes(filters.itemKind))
				throw new BadRequestException('Invalid item kind');
			conditions.push(eq(dabloonInteractions.item_kind, filters.itemKind));
		}
		if (filters.dimension)
			conditions.push(eq(dabloonInteractions.dimension, filters.dimension.slice(0, 120)));
		const search = filters.search?.trim().slice(0, 200).toLowerCase();
		if (search) {
			const pattern = `%${search.replace(/[\\%_]/g, '\\$&')}%`;
			const match = or(
				sql`lower(${dabloonInteractions.actor_name}) like ${pattern} escape '\\'`,
				sql`lower(${dabloonInteractions.item_id}) like ${pattern} escape '\\'`,
				sql`lower(${dabloonInteractions.context}) like ${pattern} escape '\\'`,
				sql`lower(${dabloonInteractions.minecraft_uuid}) like ${pattern} escape '\\'`,
			);
			if (match) conditions.push(match);
		}
		const rows = this.database.connection
			.select()
			.from(dabloonInteractions)
			.where(conditions.length ? and(...conditions) : undefined)
			.orderBy(desc(dabloonInteractions.id))
			.limit(PAGE_SIZE + 1)
			.all();
		const page = rows.slice(0, PAGE_SIZE);
		return {
			interactions: page.map((row) => ({
				id: row.id,
				action: row.action,
				itemKind: row.item_kind,
				itemId: row.item_id,
				itemCount: row.item_count,
				amountDabloons: row.amount_dabloons,
				actorName: row.actor_name,
				minecraftUuid: row.minecraft_uuid,
				userId: row.user_id,
				context: row.context,
				dimension: row.dimension,
				x: row.x,
				y: row.y,
				z: row.z,
				balanceBefore: row.balance_before,
				balanceAfter: row.balance_after,
				createdAtUnixMs: row.created_at_unix_ms,
			})),
			hasMore: rows.length > PAGE_SIZE,
			nextCursor: rows.length > PAGE_SIZE ? page.at(-1)?.id : null,
		};
	}
}

function safeInteger(value: number | undefined) {
	return value !== undefined && Number.isSafeInteger(value) ? value : 0;
}

function safeNonNegative(value: number | undefined) {
	return Math.max(0, safeInteger(value));
}

function optionalSafeInteger(value: number | undefined) {
	return value !== undefined && Number.isSafeInteger(value) ? value : null;
}

function filterInteger(value: string | undefined, name: string, minimum: number): number | null {
	if (value === undefined || value === '') return null;
	const parsed = Number(value);
	if (!Number.isSafeInteger(parsed) || parsed < minimum)
		throw new BadRequestException(`${name} must be an integer of at least ${minimum}`);
	return parsed;
}
