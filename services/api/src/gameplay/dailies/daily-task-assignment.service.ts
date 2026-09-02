import { BadRequestException, Injectable } from '@nestjs/common';
import {
	DatabaseService,
	dailyAdvancementTargets,
	dailyTasks,
} from '../../database/database.service';
import type { AuthenticatedUser } from '../../auth/auth-session.service';
import { DailyMinecraftClientService } from './daily-minecraft-client.service';
import {
	dailyAdvancementBonus,
	type DailyAdvancementTarget,
	GENERATED_TASK_COUNT,
	parseDailyTaskJson,
} from './daily-task-rules';
import { DailyTaskStorageService } from './daily-task-storage.service';

@Injectable()
export class DailyTaskAssignmentService {
	constructor(
		private readonly database: DatabaseService,
		private readonly dailyMinecraft: DailyMinecraftClientService,
		private readonly dailyStorage: DailyTaskStorageService,
	) {}

	async tasksForPeriod(user: AuthenticatedUser, periodKey: string) {
		let tasks = this.dailyStorage.storedTasks(user.id, periodKey);
		if (tasks.length === GENERATED_TASK_COUNT) return tasks;

		const generated = await this.dailyMinecraft.generateTasks(
			user,
			periodKey,
			GENERATED_TASK_COUNT,
			Date.now(),
		);
		if (!generated.generated || generated.task_json.length !== GENERATED_TASK_COUNT)
			throw new BadRequestException(
				generated.message || "The Minecraft server could not generate today's dailies.",
			);
		const parsed = generated.task_json.map(parseDailyTaskJson);
		if (new Set(parsed.map((task) => task.id)).size !== GENERATED_TASK_COUNT)
			throw new BadRequestException('The Minecraft server generated duplicate daily tasks.');

		const now = Date.now();
		this.database.connection.transaction((tx) => {
			for (const [slot, task] of parsed.entries())
				tx.insert(dailyTasks)
					.values({
						user_id: user.id,
						period_key: periodKey,
						slot,
						task_id: task.id,
						task_json: JSON.stringify(task),
						updated_at_unix_ms: now,
					})
					.onConflictDoNothing()
					.run();
		});
		tasks = this.dailyStorage.storedTasks(user.id, periodKey);
		if (tasks.length !== GENERATED_TASK_COUNT)
			throw new BadRequestException("Today's generated dailies could not be saved.");
		return tasks;
	}

	async advancementForPeriod(user: AuthenticatedUser, periodKey: string) {
		const existing = this.dailyStorage.advancementTarget(user.id, periodKey);
		if (existing) return { target: existing, message: '' };

		const now = Date.now();
		const result = await this.dailyMinecraft.pickAdvancement(
			user.minecraftUsername,
			periodKey,
			now,
		);
		if (!result.selected)
			return {
				target: null,
				message: result.message || 'Daily advancement target is unavailable right now.',
			};

		this.database.connection
			.insert(dailyAdvancementTargets)
			.values({
				user_id: user.id,
				period_key: periodKey,
				advancement_id: result.advancement_id,
				title: result.title,
				description: result.description,
				tab_title: result.tab_title,
				icon_item: result.icon_item,
				base_reward_dabloons: result.base_reward_dabloons,
				bonus_reward_dabloons: dailyAdvancementBonus(result.base_reward_dabloons),
				selected_at_unix_ms: now,
			})
			.onConflictDoNothing()
			.run();
		return { target: this.dailyStorage.advancementTarget(user.id, periodKey), message: '' };
	}

	async isAdvancementCompleted(
		user: Pick<AuthenticatedUser, 'id' | 'minecraftUsername'>,
		periodKey: string,
		target: DailyAdvancementTarget,
	) {
		if (this.dailyStorage.hasRecordedAdvancement(user.id, target)) return true;
		return this.dailyMinecraft
			.claimAdvancement(user.minecraftUsername, periodKey, Date.now(), target, true)
			.then((result) => result.completed)
			.catch(() => false);
	}
}
