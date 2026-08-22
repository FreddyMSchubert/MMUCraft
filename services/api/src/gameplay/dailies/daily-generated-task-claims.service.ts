import { BadRequestException, Injectable } from '@nestjs/common';
import { and, eq } from 'drizzle-orm';
import { AuthenticatedUser } from '../../auth/auth-session.service';
import { DatabaseService, dailyTasks } from '../../database/database.service';
import { PlayerMoneyHistoryService } from '../../players/player-money-history.service';
import { DailyMinecraftClientService } from './daily-minecraft-client.service';
import { currentDailyPeriodKey, parseDailyTaskJson } from './daily-task-rules';
import { DailyTaskStorageService } from './daily-task-storage.service';
import { DailyTaskUpdateEventsService } from './daily-task-update-events.service';

@Injectable()
export class DailyGeneratedTaskClaimsService {
	constructor(
		private readonly database: DatabaseService,
		private readonly dailyMinecraft: DailyMinecraftClientService,
		private readonly dailyStorage: DailyTaskStorageService,
		private readonly taskUpdates: DailyTaskUpdateEventsService,
		private readonly playerMoneyHistory: PlayerMoneyHistoryService,
	) {}

	async claimTask(user: AuthenticatedUser, taskId: string) {
		const periodKey = currentDailyPeriodKey();
		const row = this.database.connection
			.select()
			.from(dailyTasks)
			.where(
				and(
					eq(dailyTasks.user_id, user.id),
					eq(dailyTasks.period_key, periodKey),
					eq(dailyTasks.task_id, taskId),
				),
			)
			.get();
		if (!row) throw new BadRequestException("That task is not one of today's dailies.");

		const task = parseDailyTaskJson(row.task_json);
		if (task.max !== -1 && task.current < task.max) {
			throw new BadRequestException(`Complete ${task.name} in-game first.`);
		}

		const now = Date.now();
		const inserted = this.dailyStorage.insertClaim(user.id, task.id, periodKey, now);
		if (inserted.changes !== 1) {
			return {
				claimed: true,
				granted: false,
				message: 'You have already claimed this daily.',
			};
		}

		try {
			const result = await this.dailyMinecraft.claimTask(user, periodKey, row.task_json);
			if (!result.claimed) {
				this.dailyStorage.deleteClaim(user.id, task.id, periodKey);
				throw new BadRequestException(
					result.message || 'The Minecraft server could not claim this daily.',
				);
			}

			if (result.task_json) {
				this.dailyStorage.storeTaskUpdate(user.id, periodKey, result.task_json, now);
			}
			this.playerMoneyHistory.recordForUser(
				user.id,
				'daily_task',
				task.rewardDabloons,
				null,
				`daily:${task.id}:${user.id}:${periodKey}`,
				now,
			);
			this.taskUpdates.notifyUser(user.id);
			return {
				claimed: true,
				granted: true,
				message: result.message || `You received ${task.rewardDabloons} dabloons.`,
			};
		} catch (error) {
			this.dailyStorage.deleteClaim(user.id, task.id, periodKey);
			if (error instanceof BadRequestException) throw error;
			throw new BadRequestException(
				'You have to be online on the server to claim this daily.',
			);
		}
	}
}
