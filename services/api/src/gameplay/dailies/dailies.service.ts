import { Injectable } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import { AuthenticatedUser } from '../../auth/auth-session.service';
import { DatabaseService, dailyTasks, users } from '../../database/database.service';
import { ShopItemCatalogService } from '../shop/shop-item-catalog.service';
import { DailyAdminRefreshService } from './daily-admin-refresh.service';
import { DailyBonusClaimsService } from './daily-bonus-claims.service';
import { DailyGeneratedTaskClaimsService } from './daily-generated-task-claims.service';
import { DailyTaskAssignmentService } from './daily-task-assignment.service';
import {
	ADVANCEMENT_BONUS_TASK_ID,
	currentDailyPeriodKey,
	DAILY_COMPLETION_BASE_REWARD,
	DAILY_COMPLETION_MEMBER_BONUS,
	DAILY_COMPLETION_SUNDAY_BONUS,
	DAILY_COMPLETION_TASK_ID,
	dailyCompletionReward,
	DAILY_RESET_HOUR,
	DAILY_RESET_TIME_ZONE,
	LOGIN_BONUS_TASK_ID,
	rewardForStreak,
	STATIC_DAILY_TASK_IDS,
} from './daily-task-rules';
import { DailyTaskStorageService } from './daily-task-storage.service';
import { DailyTaskUpdateEventsService } from './daily-task-update-events.service';

function errorMessage(error: unknown) {
	return error instanceof Error ? error.message : String(error);
}

@Injectable()
export class DailiesService {
	constructor(
		private readonly database: DatabaseService,
		private readonly dailyAdminRefresh: DailyAdminRefreshService,
		private readonly dailyBonusClaims: DailyBonusClaimsService,
		private readonly dailyGeneratedTaskClaims: DailyGeneratedTaskClaimsService,
		private readonly dailyTaskAssignment: DailyTaskAssignmentService,
		private readonly dailyStorage: DailyTaskStorageService,
		private readonly taskUpdates: DailyTaskUpdateEventsService,
		private readonly shopItemCatalog: ShopItemCatalogService,
	) {}

	async getStatus(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey();
		const loginBonusClaimed = this.dailyStorage.hasClaimed(
			user.id,
			LOGIN_BONUS_TASK_ID,
			periodKey,
		);
		const loginStreak = this.dailyStorage.loginStreak(user.id, periodKey, user.isMember);
		const loginReward = rewardForStreak(loginBonusClaimed ? loginStreak : loginStreak + 1);
		const generatedTasks = await this.dailyTaskAssignment.tasksForPeriod(user, periodKey);
		const completedTaskCount = this.dailyStorage.completedTaskCount(user.id, periodKey);
		const completionReward = dailyCompletionReward(periodKey, user.isMember);
		const advancementTask = await this.dailyTaskAssignment
			.advancementForPeriod(user, periodKey)
			.catch(() => ({
				target: null,
				message: 'The advancement daily is unavailable right now. Please try again soon.',
			}));
		const advancementClaimed = this.dailyStorage.hasClaimed(
			user.id,
			ADVANCEMENT_BONUS_TASK_ID,
			periodKey,
		);
		const advancementCompleted =
			advancementClaimed ||
			Boolean(
				advancementTask.target &&
				(await this.dailyTaskAssignment.isAdvancementCompleted(
					user,
					periodKey,
					advancementTask.target,
				)),
			);

		return {
			resetHour: DAILY_RESET_HOUR,
			resetTimeZone: DAILY_RESET_TIME_ZONE,
			loginStreak,
			nextLoginRewardDabloons: rewardForStreak(loginStreak + 1),
			completion: {
				completedTaskCount,
				totalTaskCount: STATIC_DAILY_TASK_IDS.length + generatedTasks.length,
				eligible:
					completedTaskCount === STATIC_DAILY_TASK_IDS.length + generatedTasks.length,
				claimed: this.dailyStorage.hasClaimed(user.id, DAILY_COMPLETION_TASK_ID, periodKey),
				baseRewardDabloons: DAILY_COMPLETION_BASE_REWARD,
				sundayBonusDabloons: DAILY_COMPLETION_SUNDAY_BONUS,
				memberBonusDabloons: DAILY_COMPLETION_MEMBER_BONUS,
				isSunday: completionReward.isSunday,
				isMember: user.isMember,
				rewardDabloons: completionReward.total,
			},
			tasks: [
				{
					id: LOGIN_BONUS_TASK_ID,
					emoji: '🔥',
					name: 'Login bonus',
					rewardDabloons: loginReward,
					claimed: loginBonusClaimed,
					current: loginBonusClaimed ? 1 : 0,
					max: -1,
				},
				{
					id: ADVANCEMENT_BONUS_TASK_ID,
					emoji: '🏆',
					name: 'Advancement bonus',
					rewardDabloons: advancementTask.target?.bonusRewardDabloons ?? 0,
					claimed: advancementClaimed,
					current: advancementCompleted ? 1 : 0,
					max: 1,
					advancement: advancementTask.target
						? {
								advancementId: advancementTask.target.advancementId,
								title: advancementTask.target.title,
								description: advancementTask.target.description,
								tabTitle: advancementTask.target.tabTitle,
								iconItem: advancementTask.target.iconItem,
								baseRewardDabloons: advancementTask.target.baseRewardDabloons,
								bonusRewardDabloons: advancementTask.target.bonusRewardDabloons,
								...this.itemRenderAsset(advancementTask.target.iconItem),
							}
						: null,
					unavailableMessage: advancementTask.target
						? undefined
						: advancementTask.message,
				},
				...generatedTasks.map((task) => ({
					...task,
					claimed: this.dailyStorage.hasClaimed(user.id, task.id, periodKey),
				})),
			],
		};
	}

	claimLoginBonus(user: AuthenticatedUser) {
		return this.dailyBonusClaims.claimLoginBonus(user);
	}

	claimAdvancementBonus(user: AuthenticatedUser) {
		return this.dailyBonusClaims.claimAdvancementBonus(user);
	}

	claimTask(user: AuthenticatedUser, taskId: string) {
		return this.dailyGeneratedTaskClaims.claimTask(user, taskId);
	}

	async refreshForAdmin(userIdInput: string) {
		const result = await this.dailyAdminRefresh.refresh(userIdInput);
		this.taskUpdates.notifyUser(result.userId);
		return { message: result.message };
	}

	notifyAdvancementCompletion(userId: number | null, advancementId: string) {
		if (!userId) return;
		const target = this.dailyStorage.advancementTarget(userId, currentDailyPeriodKey());
		if (target?.advancementId === advancementId) this.taskUpdates.notifyUser(userId);
	}

	events(userId: number) {
		return this.taskUpdates.forUser(userId);
	}

	getMinecraftSnapshot() {
		const periodKey = currentDailyPeriodKey();
		const usernames = new Map(
			this.database.connection
				.select({
					id: users.id,
					minecraftUsername: users.minecraft_username,
				})
				.from(users)
				.all()
				.map((user) => [user.id, user.minecraftUsername]),
		);

		return {
			tasks: this.database.connection
				.select()
				.from(dailyTasks)
				.where(eq(dailyTasks.period_key, periodKey))
				.all()
				.map((row) => ({
					user_id: row.user_id,
					minecraft_username: usernames.get(row.user_id) ?? '',
					period_key: row.period_key,
					task_json: row.task_json,
					claimed: this.dailyStorage.hasClaimed(row.user_id, row.task_id, row.period_key),
				})),
		};
	}

	updateTaskFromMinecraft(userId: number, periodKey: string, taskJson: string) {
		if (periodKey !== currentDailyPeriodKey())
			return { accepted: false, message: 'That daily period has ended.' };
		try {
			const accepted = this.dailyStorage.storeTaskUpdate(
				userId,
				periodKey,
				taskJson,
				Date.now(),
			);
			if (accepted) this.taskUpdates.notifyUser(userId);
			return {
				accepted,
				message: accepted
					? 'Daily task updated.'
					: 'The update was stale or did not match an assigned task.',
			};
		} catch (error) {
			return { accepted: false, message: errorMessage(error) };
		}
	}

	private itemRenderAsset(itemId: string) {
		const asset = this.shopItemCatalog.gameItemAsset(itemId);
		return { modelUrl: asset?.modelUrl ?? null, textureUrl: asset?.textureUrl ?? null };
	}

	claimDailyCompletion(user: AuthenticatedUser) {
		return this.dailyBonusClaims.claimDailyCompletion(user);
	}
}
