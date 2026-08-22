import { BadRequestException, Injectable } from '@nestjs/common';
import { AuthenticatedUser } from '../../auth/auth-session.service';
import { PlayerMoneyHistoryService } from '../../players/player-money-history.service';
import { DailyMinecraftClientService } from './daily-minecraft-client.service';
import { DailyTaskAssignmentService } from './daily-task-assignment.service';
import {
	ADVANCEMENT_BONUS_TASK_ID,
	currentDailyPeriodKey,
	DAILY_COMPLETION_TASK_ID,
	dailyCompletionReward,
	GENERATED_TASK_COUNT,
	LOGIN_BONUS_TASK_ID,
	rewardForStreak,
	STATIC_DAILY_TASK_IDS,
} from './daily-task-rules';
import { DailyTaskStorageService } from './daily-task-storage.service';
import { DailyTaskUpdateEventsService } from './daily-task-update-events.service';

@Injectable()
export class DailyBonusClaimsService {
	constructor(
		private readonly dailyMinecraft: DailyMinecraftClientService,
		private readonly dailyTaskAssignment: DailyTaskAssignmentService,
		private readonly dailyStorage: DailyTaskStorageService,
		private readonly taskUpdates: DailyTaskUpdateEventsService,
		private readonly playerMoneyHistory: PlayerMoneyHistoryService,
	) {}

	async claimLoginBonus(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey();
		const now = Date.now();
		const loginStreak = this.dailyStorage.loginStreak(user.id, periodKey, user.isMember);
		const rewardDabloons = rewardForStreak(loginStreak + 1);
		const inserted = this.dailyStorage.insertClaim(
			user.id,
			LOGIN_BONUS_TASK_ID,
			periodKey,
			now,
		);

		if (inserted.changes !== 1) {
			return {
				claimed: true,
				granted: false,
				message: 'You have already claimed this daily bonus.',
			};
		}

		try {
			const result = await this.dailyMinecraft.grantLoginBonus(
				user.minecraftUsername,
				periodKey,
				now,
				rewardDabloons,
				'login',
			);
			if (!result.granted) {
				this.dailyStorage.deleteClaim(user.id, LOGIN_BONUS_TASK_ID, periodKey);
				throw new BadRequestException(
					result.message || 'You have to be online on the server to receive the money.',
				);
			}

			this.playerMoneyHistory.recordForUser(
				user.id,
				'daily_login_bonus',
				rewardDabloons,
				null,
				`daily:${LOGIN_BONUS_TASK_ID}:${user.id}:${periodKey}`,
				now,
			);
			this.taskUpdates.notifyUser(user.id);
			return {
				claimed: true,
				granted: true,
				message: result.message || `You received ${rewardDabloons} dabloons.`,
			};
		} catch (error) {
			this.dailyStorage.deleteClaim(user.id, LOGIN_BONUS_TASK_ID, periodKey);
			if (error instanceof BadRequestException) throw error;
			throw new BadRequestException(
				'You have to be online on the server to receive the money.',
			);
		}
	}

	async claimAdvancementBonus(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey();
		const now = Date.now();
		const picked = await this.dailyTaskAssignment.advancementForPeriod(user, periodKey);
		if (!picked.target) {
			throw new BadRequestException(
				picked.message || 'Daily advancement target is unavailable right now.',
			);
		}

		const inserted = this.dailyStorage.insertClaim(
			user.id,
			ADVANCEMENT_BONUS_TASK_ID,
			periodKey,
			now,
		);
		if (inserted.changes !== 1) {
			return {
				claimed: true,
				granted: false,
				message: 'You have already claimed this daily advancement bonus.',
			};
		}

		try {
			const result = await this.dailyMinecraft.claimAdvancement(
				user.minecraftUsername,
				periodKey,
				now,
				picked.target,
			);
			if (!result.claimed) {
				this.dailyStorage.deleteClaim(user.id, ADVANCEMENT_BONUS_TASK_ID, periodKey);
				throw new BadRequestException(
					result.message || 'Complete the daily advancement in-game first.',
				);
			}

			this.playerMoneyHistory.recordForUser(
				user.id,
				'daily_advancement_bonus',
				picked.target.bonusRewardDabloons,
				null,
				`daily:${ADVANCEMENT_BONUS_TASK_ID}:${user.id}:${periodKey}`,
				now,
			);
			this.taskUpdates.notifyUser(user.id);
			return {
				claimed: true,
				granted: true,
				message:
					result.message ||
					`You received ${picked.target.bonusRewardDabloons} bonus dabloons.`,
			};
		} catch (error) {
			this.dailyStorage.deleteClaim(user.id, ADVANCEMENT_BONUS_TASK_ID, periodKey);
			if (error instanceof BadRequestException) throw error;
			throw new BadRequestException(
				'You have to be online on the server to claim the daily advancement bonus.',
			);
		}
	}

	async claimDailyCompletion(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey();
		const now = Date.now();
		if (
			this.dailyStorage.completedTaskCount(user.id, periodKey) !==
			STATIC_DAILY_TASK_IDS.length + GENERATED_TASK_COUNT
		) {
			throw new BadRequestException(
				"Complete all of today's daily quests before finishing your dailies.",
			);
		}

		const rewardDabloons = dailyCompletionReward(periodKey, user.isMember).total;
		const inserted = this.dailyStorage.insertClaim(
			user.id,
			DAILY_COMPLETION_TASK_ID,
			periodKey,
			now,
		);
		if (inserted.changes !== 1) {
			return {
				claimed: true,
				granted: false,
				message: "You have already finished today's dailies.",
			};
		}

		try {
			const result = await this.dailyMinecraft.grantLoginBonus(
				user.minecraftUsername,
				periodKey,
				now,
				rewardDabloons,
				'daily_completion',
			);
			if (!result.granted) {
				this.dailyStorage.deleteClaim(user.id, DAILY_COMPLETION_TASK_ID, periodKey);
				throw new BadRequestException(
					result.message || 'You have to be online on the server to receive the money.',
				);
			}

			this.playerMoneyHistory.recordForUser(
				user.id,
				'daily_completion_bonus',
				rewardDabloons,
				null,
				`daily:${DAILY_COMPLETION_TASK_ID}:${user.id}:${periodKey}`,
				now,
			);
			this.taskUpdates.notifyUser(user.id);
			return {
				claimed: true,
				granted: true,
				message: `Dailies finished! You received ${rewardDabloons} dabloons.`,
			};
		} catch (error) {
			this.dailyStorage.deleteClaim(user.id, DAILY_COMPLETION_TASK_ID, periodKey);
			if (error instanceof BadRequestException) throw error;
			throw new BadRequestException(
				'You have to be online on the server to receive the money.',
			);
		}
	}
}
