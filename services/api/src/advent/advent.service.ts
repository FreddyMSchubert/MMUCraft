import { randomUUID } from 'node:crypto';
import { BadRequestException, ConflictException, Injectable } from '@nestjs/common';
import { and, eq } from 'drizzle-orm';
import type { AuthenticatedUser } from '../auth/auth-session.service';
import { adventClaims, adventLayouts, DatabaseService } from '../database/database.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { PlayerMoneyHistoryService } from '../players/player-money-history.service';
import {
	ADVENT_DAYS,
	ADVENT_YEAR,
	defaultAdventLayout,
	type AdventLayout,
	validateAdventLayout,
} from './advent-layout';

interface GrantResult {
	granted: boolean;
	already_granted: boolean;
	message: string;
	balance_dabloons: number;
}

@Injectable()
export class AdventService {
	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly moneyHistory: PlayerMoneyHistoryService,
	) {}

	getLayout() {
		const row = this.database.connection
			.select()
			.from(adventLayouts)
			.where(eq(adventLayouts.id, 1))
			.get();
		return {
			revision: row?.revision ?? 0,
			layout: row ? (JSON.parse(row.layout_json) as AdventLayout) : defaultAdventLayout(),
		};
	}

	getCalendar(user: AuthenticatedUser) {
		const date = londonDate();
		const claims = this.database.connection
			.select({ day: adventClaims.day, rate: adventClaims.streak_rate_after })
			.from(adventClaims)
			.where(
				and(
					eq(adventClaims.user_id, user.id),
					eq(adventClaims.year, ADVENT_YEAR),
					eq(adventClaims.status, 'delivered'),
				),
			)
			.all();
		const today = date.year === ADVENT_YEAR && date.month === 12 ? date.day : 0;
		const latest =
			claims.find((claim) => claim.day === today) ??
			claims.find((claim) => claim.day === today - 1);
		return {
			...this.getLayout(),
			year: ADVENT_YEAR,
			today,
			claimedDays: claims.map((claim) => claim.day),
			streakRate: latest?.rate ?? 0,
		};
	}

	async getAdmin() {
		let rewards: unknown = null;
		try {
			rewards = await this.minecraft.gameplay('GetAdventRewards', {});
		} catch {
			// The layout editor remains available while Minecraft is offline.
		}
		const streakLocked = !!this.database.connection
			.select({ claimId: adventClaims.claim_id })
			.from(adventClaims)
			.where(eq(adventClaims.year, ADVENT_YEAR))
			.limit(1)
			.get();
		return { ...this.getLayout(), year: ADVENT_YEAR, rewards, streakLocked };
	}

	saveLayout(input: unknown, expectedRevision: unknown) {
		const layout = validateAdventLayout(input);
		if (!Number.isInteger(expectedRevision) || (expectedRevision as number) < 0)
			throw new BadRequestException('A valid layout revision is required.');
		return this.database.connection.transaction((tx) => {
			const old = tx.select().from(adventLayouts).where(eq(adventLayouts.id, 1)).get();
			if ((old?.revision ?? 0) !== expectedRevision)
				throw new ConflictException(
					'Another editor saved the calendar. Reload before editing.',
				);
			const previous = old
				? (JSON.parse(old.layout_json) as AdventLayout)
				: defaultAdventLayout();
			if (
				layout.days.some(
					(day, index) => day.streakIncrease !== previous.days[index]?.streakIncrease,
				) &&
				tx
					.select({ claimId: adventClaims.claim_id })
					.from(adventClaims)
					.where(eq(adventClaims.year, ADVENT_YEAR))
					.limit(1)
					.get()
			)
				throw new ConflictException('Streak boosts cannot change after the first claim.');
			const revision = (old?.revision ?? 0) + 1;
			if (old)
				tx.update(adventLayouts)
					.set({ revision, layout_json: JSON.stringify(layout) })
					.where(eq(adventLayouts.id, 1))
					.run();
			else
				tx.insert(adventLayouts)
					.values({ id: 1, revision, layout_json: JSON.stringify(layout) })
					.run();
			return { revision, layout };
		});
	}

	async claim(user: AuthenticatedUser, dayInput: unknown) {
		const day = Number(dayInput);
		if (!Number.isInteger(day) || day < 1 || day > ADVENT_DAYS)
			throw new BadRequestException('Choose a valid Advent day.');
		const date = londonDate();
		if (date.year !== ADVENT_YEAR || date.month !== 12)
			throw new BadRequestException('The Advent calendar opens in December 2026.');
		const offset = dayOffset(date);
		const settings = this.getLayout().layout.days[day - 1];
		if (!settings) throw new BadRequestException('Choose a valid Advent day.');
		if (offset < day - 1 || offset > day - 1 + settings.lookbackDays || settings.x < 0)
			throw new BadRequestException('This Advent present is not available today.');

		const existing = this.database.connection
			.select()
			.from(adventClaims)
			.where(
				and(
					eq(adventClaims.user_id, user.id),
					eq(adventClaims.year, ADVENT_YEAR),
					eq(adventClaims.day, day),
				),
			)
			.get();
		if (existing?.status === 'delivered')
			throw new ConflictException('You already opened this present.');
		if (existing?.status === 'pending' && Date.now() - existing.created_at_unix_ms < 15_000)
			throw new ConflictException(
				'This present is still opening. Try again in a few seconds.',
			);
		const todayClaim = offset === day - 1;
		const yesterday =
			todayClaim && day > 1
				? this.database.connection
						.select()
						.from(adventClaims)
						.where(
							and(
								eq(adventClaims.user_id, user.id),
								eq(adventClaims.year, ADVENT_YEAR),
								eq(adventClaims.day, day - 1),
								eq(adventClaims.status, 'delivered'),
							),
						)
						.get()
				: null;
		const rate = todayClaim ? (yesterday?.streak_rate_after ?? 0) + settings.streakIncrease : 0;
		const amount = todayClaim ? rate : 0;
		const claimId = existing?.claim_id ?? randomUUID();
		if (!existing) {
			const inserted = this.database.connection
				.insert(adventClaims)
				.values({
					user_id: user.id,
					year: ADVENT_YEAR,
					day,
					claim_id: claimId,
					status: 'pending',
					amount_dabloons: amount,
					streak_rate_after: rate,
					created_at_unix_ms: Date.now(),
				})
				.onConflictDoNothing()
				.run();
			if (inserted.changes !== 1)
				throw new ConflictException('This present is being opened. Try again.');
		}
		const grant = await this.minecraft.gameplay<GrantResult>('GrantAdventReward', {
			minecraft_username: user.minecraftUsername,
			day,
			claim_id: claimId,
			amount_dabloons: existing?.amount_dabloons ?? amount,
			preview: false,
		});
		if (!grant.granted && !grant.already_granted) {
			this.database.connection
				.delete(adventClaims)
				.where(eq(adventClaims.claim_id, claimId))
				.run();
			throw new BadRequestException(
				grant.message || 'Join the Minecraft server and try again.',
			);
		}
		this.database.connection
			.update(adventClaims)
			.set({ status: 'delivered' })
			.where(eq(adventClaims.claim_id, claimId))
			.run();
		const grantedAmount = existing?.amount_dabloons ?? amount;
		if (grantedAmount > 0)
			this.moneyHistory.recordForUser(
				user.id,
				'advent',
				grantedAmount,
				grant.balance_dabloons,
				`advent:${claimId}`,
			);
		return {
			claimed: true,
			day,
			amountDabloons: grantedAmount,
			streakRate: this.getCalendar(user).streakRate,
			message: grant.message,
		};
	}

	async previewGift(dayInput: unknown) {
		const day = Number(dayInput);
		if (!Number.isInteger(day) || day < 1 || day > ADVENT_DAYS)
			throw new BadRequestException('Choose a valid Advent day.');
		const grant = await this.minecraft.gameplay<GrantResult>('GrantAdventReward', {
			minecraft_username: 'MMUAdmin',
			day,
			claim_id: randomUUID(),
			amount_dabloons: 0,
			preview: true,
		});
		if (!grant.granted)
			throw new BadRequestException(grant.message || 'MMUAdmin must be online.');
		return { granted: true, message: grant.message };
	}
}

function londonDate() {
	const parts = new Intl.DateTimeFormat('en-GB', {
		timeZone: 'Europe/London',
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
	}).formatToParts(new Date());
	const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
	return { year: Number(values.year), month: Number(values.month), day: Number(values.day) };
}

function dayOffset(date: ReturnType<typeof londonDate>) {
	return Math.round(
		(Date.UTC(date.year, date.month - 1, date.day) - Date.UTC(ADVENT_YEAR, 11, 1)) / 86_400_000,
	);
}
