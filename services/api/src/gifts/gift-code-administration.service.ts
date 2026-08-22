import { ConflictException, Injectable } from '@nestjs/common';
import { and, count, desc, eq, getTableColumns, gt, isNull, notExists, or } from 'drizzle-orm';
import { AuthenticatedUser } from '../auth/auth-session.service';
import {
	DatabaseService,
	giftCodeRedemptions,
	giftCodes,
} from '../database/database.service';
import {
	GiftCodeInput,
	normalizeGiftCode,
	normalizeGiftCodeAmount,
	normalizeGiftCodeExpiry,
	normalizeGiftCodeMembersOnly,
	normalizeGiftCodeRedemptionMode,
} from './gift-code-validation';

@Injectable()
export class GiftCodeAdministrationService {
	constructor(private readonly database: DatabaseService) {}

	create(admin: AuthenticatedUser, input: GiftCodeInput) {
		const code = normalizeGiftCode(input.code);
		const amountDabloons = normalizeGiftCodeAmount(input.amountDabloons);
		const now = Date.now();
		const redemptionMode = normalizeGiftCodeRedemptionMode(input.redemptionMode);
		const membersOnly = normalizeGiftCodeMembersOnly(input.membersOnly);
		const expiresAtUnixMs = normalizeGiftCodeExpiry(input.expiresAtUnixMs, now);

		try {
			this.database.connection
				.insert(giftCodes)
				.values({
					code,
					amount_dabloons: amountDabloons,
					redemption_mode: redemptionMode,
					members_only: membersOnly ? 1 : 0,
					expires_at_unix_ms: expiresAtUnixMs,
					created_by_user_id: admin.id,
					created_at_unix_ms: now,
					redeemed_by_user_id: null,
					redeemed_at_unix_ms: null,
				})
				.run();
		} catch (error) {
			if (isSqliteConstraint(error)) {
				throw new ConflictException('A gift code with that name already exists');
			}
			throw error;
		}

		return {
			code,
			amountDabloons,
			redemptionMode,
			membersOnly,
			expiresAtUnixMs,
			createdAtUnixMs: now,
		};
	}

	listActive() {
		const now = Date.now();
		const existingRedemption = this.database.connection
			.select({ code: giftCodeRedemptions.code })
			.from(giftCodeRedemptions)
			.where(eq(giftCodeRedemptions.code, giftCodes.code));

		const rows = this.database.connection
			.select({
				...getTableColumns(giftCodes),
				redemption_count: count(giftCodeRedemptions.user_id),
			})
			.from(giftCodes)
			.leftJoin(giftCodeRedemptions, eq(giftCodeRedemptions.code, giftCodes.code))
			.where(
				and(
					or(isNull(giftCodes.expires_at_unix_ms), gt(giftCodes.expires_at_unix_ms, now)),
					or(eq(giftCodes.redemption_mode, 'per_user'), notExists(existingRedemption)),
				),
			)
			.groupBy(giftCodes.code)
			.orderBy(desc(giftCodes.created_at_unix_ms))
			.all();

		return {
			giftCodes: rows.map((row) => ({
				code: row.code,
				amountDabloons: row.amount_dabloons,
				redemptionMode: row.redemption_mode,
				membersOnly: row.members_only === 1,
				expiresAtUnixMs: row.expires_at_unix_ms,
				createdAtUnixMs: row.created_at_unix_ms,
				redemptionCount: row.redemption_count,
			})),
		};
	}
}

function isSqliteConstraint(error: unknown) {
	return (
		error instanceof Error &&
		'code' in error &&
		String((error as Error & { code?: unknown }).code).startsWith('SQLITE_CONSTRAINT')
	);
}
