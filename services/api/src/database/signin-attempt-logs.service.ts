import { BadRequestException, Injectable } from '@nestjs/common';
import { and, desc, eq, gte, lt, lte, sql, type SQL } from 'drizzle-orm';
import { DatabaseService, signinAttemptLogs } from './database.service';

const PAGE_SIZE = 50;

export type AuthJourney = 'signin' | 'signup';
export type AuthEvent =
	| 'email_send'
	| 'email_resend'
	| 'email_code_input'
	| 'minecraft_username_input'
	| 'minecraft_code_input'
	| 'rules_accept';

export interface SigninAttemptLogFilters {
	beforeId?: string;
	journey?: string;
	event?: string;
	succeeded?: string;
	fromUnixMs?: string;
	toUnixMs?: string;
	search?: string;
}

@Injectable()
export class SigninAttemptLogsService {
	constructor(private readonly database: DatabaseService) {}

	record(
		email: string | null,
		journey: AuthJourney,
		event: AuthEvent,
		succeeded: boolean | null,
		detail: string | null,
	) {
		try {
			this.database.connection
				.insert(signinAttemptLogs)
				.values({
					email,
					journey,
					event,
					succeeded: succeeded === null ? null : succeeded ? 1 : 0,
					detail: detail?.slice(0, 500) ?? null,
					created_at_unix_ms: Date.now(),
				})
				.run();
		} catch (error) {
			console.error('[signin-attempt-log] Failed to record attempt', {
				journey,
				event,
				error: error instanceof Error ? error.message : String(error),
			});
		}
	}

	list(filters: SigninAttemptLogFilters) {
		const conditions: SQL[] = [];
		const beforeId = optionalInteger(filters.beforeId, 'beforeId', 1);
		const fromUnixMs = optionalInteger(filters.fromUnixMs, 'fromUnixMs', 0);
		const toUnixMs = optionalInteger(filters.toUnixMs, 'toUnixMs', 0);

		if (beforeId !== null) conditions.push(lt(signinAttemptLogs.id, beforeId));
		if (fromUnixMs !== null)
			conditions.push(gte(signinAttemptLogs.created_at_unix_ms, fromUnixMs));
		if (toUnixMs !== null) conditions.push(lte(signinAttemptLogs.created_at_unix_ms, toUnixMs));
		if (fromUnixMs !== null && toUnixMs !== null && fromUnixMs > toUnixMs)
			throw new BadRequestException('The start time must be before the end time');

		if (filters.journey !== undefined) {
			if (filters.journey !== 'signin' && filters.journey !== 'signup')
				throw new BadRequestException('journey must be signin or signup');
			conditions.push(eq(signinAttemptLogs.journey, filters.journey));
		}
		if (filters.event !== undefined) {
			if (!AUTH_EVENTS.includes(filters.event as AuthEvent))
				throw new BadRequestException('event is not valid');
			conditions.push(eq(signinAttemptLogs.event, filters.event as AuthEvent));
		}
		if (filters.succeeded !== undefined) {
			if (filters.succeeded !== 'true' && filters.succeeded !== 'false')
				throw new BadRequestException('succeeded must be true or false');
			conditions.push(eq(signinAttemptLogs.succeeded, filters.succeeded === 'true' ? 1 : 0));
		}
		const search = filters.search?.trim().toLowerCase().slice(0, 200) ?? '';
		if (search) {
			const pattern = `%${escapeLike(search)}%`;
			conditions.push(sql`lower(${signinAttemptLogs.email}) like ${pattern} escape '\\'`);
		}

		const rows = this.database.connection
			.select()
			.from(signinAttemptLogs)
			.where(conditions.length ? and(...conditions) : undefined)
			.orderBy(desc(signinAttemptLogs.id))
			.limit(PAGE_SIZE + 1)
			.all();
		const hasMore = rows.length > PAGE_SIZE;
		const page = rows.slice(0, PAGE_SIZE);

		return {
			attempts: page.map((entry) => ({
				id: entry.id,
				email: entry.email,
				journey: entry.journey,
				event: entry.event,
				succeeded: entry.succeeded === null ? null : entry.succeeded === 1,
				detail: entry.detail,
				createdAtUnixMs: entry.created_at_unix_ms,
			})),
			hasMore,
			nextCursor: hasMore ? page.at(-1)?.id : null,
		};
	}
}

const AUTH_EVENTS: AuthEvent[] = [
	'email_send',
	'email_resend',
	'email_code_input',
	'minecraft_username_input',
	'minecraft_code_input',
	'rules_accept',
];

function optionalInteger(value: string | undefined, name: string, minimum: number) {
	if (value === undefined || value === '') return null;
	const parsed = Number(value);
	if (!Number.isSafeInteger(parsed) || parsed < minimum)
		throw new BadRequestException(`${name} must be an integer of at least ${minimum}`);
	return parsed;
}

function escapeLike(value: string) {
	return value.replace(/[\\%_]/g, '\\$&');
}
