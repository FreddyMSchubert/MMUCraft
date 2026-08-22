import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { and, eq } from 'drizzle-orm';
import {
	DatabaseService,
	dailyAdvancementTargets,
	dailyTasks,
	users,
} from '../../database/database.service';
import { DailyMinecraftClientService } from './daily-minecraft-client.service';
import {
	ADVANCEMENT_BONUS_TASK_ID,
	currentDailyPeriodKey,
	dailyAdvancementBonus,
	type DailyAdvancementTarget,
	GENERATED_TASK_COUNT,
	parseDailyTaskJson,
} from './daily-task-rules';
import { DailyTaskStorageService } from './daily-task-storage.service';

@Injectable()
export class DailyAdminRefreshService {
	constructor(
		private readonly database: DatabaseService,
		private readonly dailyMinecraft: DailyMinecraftClientService,
		private readonly dailyStorage: DailyTaskStorageService,
	) {}

	async refresh(userIdInput: string) {
		const userId = Number(userIdInput);
		if (!Number.isInteger(userId) || userId < 1)
			throw new BadRequestException('Select a valid player.');
		const row = this.database.connection.select().from(users).where(eq(users.id, userId)).get();
		if (!row) throw new NotFoundException('Player not found.');

		const user = { id: row.id, minecraftUsername: row.minecraft_username };
		const periodKey = currentDailyPeriodKey();
		const currentTasks = this.database.connection
			.select()
			.from(dailyTasks)
			.where(and(eq(dailyTasks.user_id, userId), eq(dailyTasks.period_key, periodKey)))
			.all();
		const keptTasks = currentTasks.filter((task) =>
			this.dailyStorage.hasClaimed(userId, task.task_id, periodKey),
		);
		const refreshedSlots = Array.from(
			{ length: GENERATED_TASK_COUNT },
			(_, slot) => slot,
		).filter((slot) => !keptTasks.some((task) => task.slot === slot));
		const currentAdvancement = this.dailyStorage.advancementTarget(userId, periodKey);
		const advancementCompleted =
			this.dailyStorage.hasClaimed(userId, ADVANCEMENT_BONUS_TASK_ID, periodKey) ||
			Boolean(
				currentAdvancement &&
				(await this.isAdvancementCompleted(user, periodKey, currentAdvancement)),
			);
		const now = Date.now();
		const advancement = advancementCompleted
			? null
			: await this.dailyMinecraft.pickAdvancement(
					user.minecraftUsername,
					periodKey,
					now,
					currentAdvancement?.advancementId,
				);
		if (advancement && !advancement.selected)
			throw new BadRequestException(
				advancement.message ||
					'The Minecraft server could not regenerate the advancement daily.',
			);
		if (
			advancement?.selected &&
			advancement.advancement_id === currentAdvancement?.advancementId
		)
			throw new BadRequestException(
				'The Minecraft server returned the same advancement daily.',
			);

		const generated =
			refreshedSlots.length === 0
				? { generated: true, task_json: [], message: '' }
				: await this.dailyMinecraft.generateTasks(
						user,
						periodKey,
						refreshedSlots.length,
						now,
						currentTasks.map((task) => task.task_id),
					);
		if (!generated.generated || generated.task_json.length !== refreshedSlots.length)
			throw new BadRequestException(
				generated.message ||
					'The Minecraft server could not regenerate the random dailies.',
			);
		const parsed = generated.task_json.map(parseDailyTaskJson);
		if (
			new Set([...currentTasks.map((task) => task.task_id), ...parsed.map((task) => task.id)])
				.size !==
			currentTasks.length + parsed.length
		)
			throw new BadRequestException('The Minecraft server generated duplicate daily tasks.');

		this.database.connection.transaction((tx) => {
			tx.delete(dailyTasks)
				.where(and(eq(dailyTasks.user_id, userId), eq(dailyTasks.period_key, periodKey)))
				.run();
			for (const task of keptTasks) tx.insert(dailyTasks).values(task).run();
			for (const [index, task] of parsed.entries()) {
				const slot = refreshedSlots[index];
				if (slot === undefined) throw new Error('A refreshed daily task slot is missing.');
				tx.insert(dailyTasks)
					.values({
						user_id: userId,
						period_key: periodKey,
						slot,
						task_id: task.id,
						task_json: JSON.stringify(task),
						updated_at_unix_ms: now,
					})
					.run();
			}
			if (advancement?.selected) {
				tx.delete(dailyAdvancementTargets)
					.where(
						and(
							eq(dailyAdvancementTargets.user_id, userId),
							eq(dailyAdvancementTargets.period_key, periodKey),
						),
					)
					.run();
				tx.insert(dailyAdvancementTargets)
					.values({
						user_id: userId,
						period_key: periodKey,
						advancement_id: advancement.advancement_id,
						title: advancement.title,
						tab_title: advancement.tab_title,
						icon_item: advancement.icon_item,
						base_reward_dabloons: advancement.base_reward_dabloons,
						bonus_reward_dabloons: dailyAdvancementBonus(
							advancement.base_reward_dabloons,
						),
						selected_at_unix_ms: now,
					})
					.run();
			}
		});

		return {
			userId,
			message: `Regenerated ${parsed.length} random ${parsed.length === 1 ? 'daily' : 'dailies'}${advancementCompleted ? '; kept the completed advancement daily' : ' and the advancement daily'}.`,
		};
	}

	private async isAdvancementCompleted(
		user: { id: number; minecraftUsername: string },
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
