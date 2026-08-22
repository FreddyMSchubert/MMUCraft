import { Injectable } from '@nestjs/common';
import { and, eq, gte, or } from 'drizzle-orm';
import {
	DatabaseService,
	dailyAdvancementTargets,
	dailyClaims,
	dailyTasks,
	playerMoneyEvents,
} from '../../database/database.service';
import {
	calculateLoginStreak,
	dailyAdvancementBonus,
	type DailyAdvancementTarget,
	LOGIN_BONUS_TASK_ID,
	parseDailyTaskJson,
	STATIC_DAILY_TASK_IDS,
} from './daily-task-rules';

@Injectable()
export class DailyTaskStorageService {
	constructor(private readonly database: DatabaseService) {}

	hasClaimed(userId: number, taskId: string, periodKey: string) {
		return Boolean(
			this.database.connection
				.select({ user_id: dailyClaims.user_id })
				.from(dailyClaims)
				.where(
					and(
						eq(dailyClaims.user_id, userId),
						eq(dailyClaims.task_id, taskId),
						eq(dailyClaims.period_key, periodKey),
					),
				)
				.get(),
		);
	}

	loginStreak(userId: number, periodKey: string, isMember: boolean) {
		const claimedPeriodKeys = this.database.connection
			.select({ period_key: dailyClaims.period_key })
			.from(dailyClaims)
			.where(
				and(eq(dailyClaims.user_id, userId), eq(dailyClaims.task_id, LOGIN_BONUS_TASK_ID)),
			)
			.all()
			.map((claim) => claim.period_key);
		return calculateLoginStreak(claimedPeriodKeys, periodKey, isMember);
	}

	completedTaskCount(userId: number, periodKey: string) {
		const generatedIds = this.database.connection
			.select({ taskId: dailyTasks.task_id })
			.from(dailyTasks)
			.where(and(eq(dailyTasks.user_id, userId), eq(dailyTasks.period_key, periodKey)))
			.all()
			.map((task) => task.taskId);
		return [...STATIC_DAILY_TASK_IDS, ...generatedIds].filter((taskId) =>
			this.hasClaimed(userId, taskId, periodKey),
		).length;
	}

	deleteClaim(userId: number, taskId: string, periodKey: string) {
		this.database.connection
			.delete(dailyClaims)
			.where(
				and(
					eq(dailyClaims.user_id, userId),
					eq(dailyClaims.task_id, taskId),
					eq(dailyClaims.period_key, periodKey),
				),
			)
			.run();
	}

	insertClaim(userId: number, taskId: string, periodKey: string, claimedAtUnixMs: number) {
		return this.database.connection
			.insert(dailyClaims)
			.values({
				user_id: userId,
				task_id: taskId,
				period_key: periodKey,
				claimed_at_unix_ms: claimedAtUnixMs,
			})
			.onConflictDoNothing()
			.run();
	}

	storedTasks(userId: number, periodKey: string) {
		return this.database.connection
			.select()
			.from(dailyTasks)
			.where(and(eq(dailyTasks.user_id, userId), eq(dailyTasks.period_key, periodKey)))
			.all()
			.sort((left, right) => left.slot - right.slot)
			.map((row) => parseDailyTaskJson(row.task_json));
	}

	storeTaskUpdate(userId: number, periodKey: string, taskJson: string, unixMs: number) {
		const next = parseDailyTaskJson(taskJson);
		const row = this.database.connection
			.select()
			.from(dailyTasks)
			.where(
				and(
					eq(dailyTasks.user_id, userId),
					eq(dailyTasks.period_key, periodKey),
					eq(dailyTasks.task_id, next.id),
				),
			)
			.get();
		if (!row) return false;

		const current = parseDailyTaskJson(row.task_json);
		if (
			current.max !== next.max ||
			next.current < current.current ||
			this.hasClaimed(userId, next.id, periodKey)
		)
			return false;

		this.database.connection
			.update(dailyTasks)
			.set({ task_json: JSON.stringify(next), updated_at_unix_ms: unixMs })
			.where(
				and(
					eq(dailyTasks.user_id, userId),
					eq(dailyTasks.period_key, periodKey),
					eq(dailyTasks.task_id, next.id),
				),
			)
			.run();
		return true;
	}

	advancementTarget(userId: number, periodKey: string): DailyAdvancementTarget | null {
		const row = this.database.connection
			.select()
			.from(dailyAdvancementTargets)
			.where(
				and(
					eq(dailyAdvancementTargets.user_id, userId),
					eq(dailyAdvancementTargets.period_key, periodKey),
				),
			)
			.get();
		return row
			? {
					advancementId: row.advancement_id,
					title: row.title,
					tabTitle: row.tab_title,
					iconItem: row.icon_item,
					baseRewardDabloons: row.base_reward_dabloons,
					bonusRewardDabloons: dailyAdvancementBonus(row.base_reward_dabloons),
					selectedAtUnixMs: row.selected_at_unix_ms,
				}
			: null;
	}

	hasRecordedAdvancement(userId: number, target: DailyAdvancementTarget) {
		return Boolean(
			this.database.connection
				.select({ id: playerMoneyEvents.id })
				.from(playerMoneyEvents)
				.where(
					and(
						eq(playerMoneyEvents.user_id, userId),
						eq(playerMoneyEvents.source, 'advancement'),
						gte(playerMoneyEvents.created_at_unix_ms, target.selectedAtUnixMs),
						or(
							eq(playerMoneyEvents.id, `advancement:${target.advancementId}`),
							eq(
								playerMoneyEvents.id,
								`advancement:${userId}:${target.advancementId}`,
							),
						),
					),
				)
				.get(),
		);
	}
}
