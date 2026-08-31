import { BadRequestException, Injectable } from '@nestjs/common';
import { and, desc, eq, gte, lt, lte, or, sql, type SQL } from 'drizzle-orm';
import { commandLogs, DatabaseService } from './database.service';
import { MinecraftIdentityService } from './minecraft-identity.service';

const PAGE_SIZE = 50;

export interface CommandLogFilters {
	beforeId?: string;
	isOperator?: string;
	source?: string;
	userId?: string;
	fromUnixMs?: string;
	toUnixMs?: string;
	search?: string;
}

export interface CommandExecutionInput {
	command?: string;
	source?: string;
	actor_name?: string;
	minecraft_uuid?: string;
	is_operator?: boolean;
	succeeded?: boolean;
	result?: number;
	unix_ms?: number;
}

@Injectable()
export class CommandLogsService {
	constructor(
		private readonly database: DatabaseService,
		private readonly identities: MinecraftIdentityService,
	) {}

	record(input: CommandExecutionInput) {
		const command = input.command?.trim() ?? '';
		const source = input.source === 'discord' ? 'discord' : 'minecraft';
		const actorName = input.actor_name?.trim().slice(0, 100) ?? '';
		if (!command || !actorName) return { accepted: false };

		const user =
			source === 'minecraft'
				? this.identities.resolveAndRefresh(input.minecraft_uuid ?? '', actorName)
				: null;
		this.database.connection
			.insert(commandLogs)
			.values({
				command,
				source,
				actor_name: actorName,
				user_id: user?.id ?? null,
				is_operator: input.is_operator ? 1 : 0,
				succeeded: typeof input.succeeded === 'boolean' ? (input.succeeded ? 1 : 0) : null,
				result:
					typeof input.result === 'number' && Number.isSafeInteger(input.result)
						? input.result
						: null,
				created_at_unix_ms:
					typeof input.unix_ms === 'number' && Number.isFinite(input.unix_ms)
						? Math.floor(input.unix_ms)
						: Date.now(),
			})
			.run();
		return { accepted: true };
	}

	list(filters: CommandLogFilters) {
		const conditions: SQL[] = [];
		const beforeId = optionalPositiveInteger(filters.beforeId, 'beforeId');
		const userId = optionalPositiveInteger(filters.userId, 'userId');
		const fromUnixMs = optionalNonNegativeInteger(filters.fromUnixMs, 'fromUnixMs');
		const toUnixMs = optionalNonNegativeInteger(filters.toUnixMs, 'toUnixMs');

		if (beforeId !== null) conditions.push(lt(commandLogs.id, beforeId));
		if (userId !== null) conditions.push(eq(commandLogs.user_id, userId));
		if (fromUnixMs !== null) conditions.push(gte(commandLogs.created_at_unix_ms, fromUnixMs));
		if (toUnixMs !== null) conditions.push(lte(commandLogs.created_at_unix_ms, toUnixMs));
		if (fromUnixMs !== null && toUnixMs !== null && fromUnixMs > toUnixMs) {
			throw new BadRequestException('The start time must be before the end time');
		}

		if (filters.isOperator !== undefined) {
			if (filters.isOperator !== 'true' && filters.isOperator !== 'false') {
				throw new BadRequestException('isOperator must be true or false');
			}
			conditions.push(eq(commandLogs.is_operator, filters.isOperator === 'true' ? 1 : 0));
		}

		if (filters.source !== undefined) {
			if (filters.source !== 'minecraft' && filters.source !== 'discord') {
				throw new BadRequestException('source must be minecraft or discord');
			}
			conditions.push(eq(commandLogs.source, filters.source));
		}

		const search = filters.search?.replace(/\s/g, '').toLowerCase().slice(0, 200) ?? '';
		if (search) {
			const pattern = `%${escapeLike(search)}%`;
			const searchCondition = or(
				sql`lower(replace(replace(${commandLogs.command}, ' ', ''), char(9), '')) like ${pattern} escape '\\'`,
				sql`lower(replace(${commandLogs.actor_name}, ' ', '')) like ${pattern} escape '\\'`,
			);
			if (searchCondition) conditions.push(searchCondition);
		}

		const rows = this.database.connection
			.select()
			.from(commandLogs)
			.where(conditions.length ? and(...conditions) : undefined)
			.orderBy(desc(commandLogs.id))
			.limit(PAGE_SIZE + 1)
			.all();
		const hasMore = rows.length > PAGE_SIZE;
		const page = rows.slice(0, PAGE_SIZE);

		return {
			commands: page.map((entry) => ({
				id: entry.id,
				command: entry.command,
				source: entry.source as 'minecraft' | 'discord',
				actorName: entry.actor_name,
				userId: entry.user_id,
				isOperator: entry.is_operator === 1,
				succeeded: entry.succeeded === null ? null : entry.succeeded === 1,
				result: entry.result,
				createdAtUnixMs: entry.created_at_unix_ms,
			})),
			hasMore,
			nextCursor: hasMore ? page.at(-1)?.id : null,
		};
	}
}

function optionalPositiveInteger(value: string | undefined, name: string): number | null {
	if (value === undefined || value === '') return null;
	const parsed = Number(value);
	if (!Number.isSafeInteger(parsed) || parsed <= 0) {
		throw new BadRequestException(`${name} must be a positive integer`);
	}
	return parsed;
}

function optionalNonNegativeInteger(value: string | undefined, name: string): number | null {
	if (value === undefined || value === '') return null;
	const parsed = Number(value);
	if (!Number.isSafeInteger(parsed) || parsed < 0) {
		throw new BadRequestException(`${name} must be a non-negative integer`);
	}
	return parsed;
}

function escapeLike(value: string) {
	return value.replace(/[\\%_]/g, '\\$&');
}
