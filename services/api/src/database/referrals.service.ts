import { BadRequestException, Injectable } from '@nestjs/common';
import { randomBytes } from 'node:crypto';
import { and, eq, isNull, sql } from 'drizzle-orm';
import { DatabaseService, referralLinks } from './database.service';

export const REFERRAL_JOIN_DABLOONS = 25;
export const REFERRAL_MEMBER_DABLOONS = 100;

@Injectable()
export class ReferralsService {
	constructor(private readonly database: DatabaseService) {}

	list(userId: number) {
		return {
			links: this.database.connection
				.select({ code: referralLinks.code })
				.from(referralLinks)
				.where(
					and(
						eq(referralLinks.referrer_user_id, userId),
						isNull(referralLinks.referred_user_id),
					),
				)
				.all(),
		};
	}

	create(userId: number) {
		return this.database.connection.transaction((tx) => {
			const active = tx
				.select({ code: referralLinks.code })
				.from(referralLinks)
				.where(
					and(
						eq(referralLinks.referrer_user_id, userId),
						isNull(referralLinks.referred_user_id),
					),
				)
				.all();
			if (active.length >= 3)
				throw new BadRequestException('You can have up to three active referral links');
			const code = randomBytes(16).toString('hex');
			tx.insert(referralLinks)
				.values({
					code,
					referrer_user_id: userId,
					created_at_unix_ms: Date.now(),
				})
				.run();
			return { code };
		});
	}

	totalReward(userId: number) {
		const result = this.database.connection
			.select({
				total: sql<number>`coalesce(sum(${referralLinks.join_reward_dabloons} + ${referralLinks.membership_reward_dabloons}), 0)`,
			})
			.from(referralLinks)
			.where(eq(referralLinks.referrer_user_id, userId))
			.get();
		return result?.total ?? 0;
	}
}
