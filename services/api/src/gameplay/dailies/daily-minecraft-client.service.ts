import { Injectable } from '@nestjs/common';
import type { AuthenticatedUser } from '../../auth/auth-session.service';
import { MinecraftGrpcClientService } from '../../grpc/minecraft-grpc-client.service';
import type { DailyAdvancementTarget } from './daily-task-rules';

interface PickDailyAdvancementResponse {
	selected: boolean;
	online: boolean;
	advancement_id: string;
	title: string;
	description: string;
	tab_title: string;
	icon_item: string;
	base_reward_dabloons: number;
	bonus_reward_dabloons: number;
	message: string;
}

@Injectable()
export class DailyMinecraftClientService {
	constructor(private readonly minecraft: MinecraftGrpcClientService) {}

	grantLoginBonus(
		minecraftUsername: string,
		periodKey: string,
		unixMs: number,
		rewardDabloons: number,
		source: string,
	) {
		return this.minecraft.gameplay<{
			granted: boolean;
			online: boolean;
			message: string;
		}>('GrantDailyLoginBonus', {
			minecraft_username: minecraftUsername,
			amount: rewardDabloons,
			period_key: periodKey,
			unix_ms: unixMs,
			source,
		});
	}

	generateTasks(
		user: Pick<AuthenticatedUser, 'id' | 'minecraftUsername'>,
		periodKey: string,
		count: number,
		unixMs: number,
		excludedTaskIds: string[] = [],
	) {
		return this.minecraft.gameplay<{
			generated: boolean;
			task_json: string[];
			message: string;
		}>('GenerateDailyTasks', {
			user_id: user.id,
			minecraft_username: user.minecraftUsername,
			period_key: periodKey,
			count,
			unix_ms: unixMs,
			excluded_task_ids: excludedTaskIds,
		});
	}

	claimTask(user: AuthenticatedUser, periodKey: string, taskJson: string) {
		return this.minecraft.gameplay<{
			claimed: boolean;
			online: boolean;
			task_json: string;
			message: string;
		}>('ClaimDailyTask', {
			user_id: user.id,
			minecraft_username: user.minecraftUsername,
			period_key: periodKey,
			task_json: taskJson,
		});
	}

	pickAdvancement(
		minecraftUsername: string,
		periodKey: string,
		unixMs: number,
		excludedAdvancementId = '',
	) {
		return this.minecraft.gameplay<PickDailyAdvancementResponse>('PickDailyAdvancement', {
			minecraft_username: minecraftUsername,
			period_key: periodKey,
			unix_ms: unixMs,
			excluded_advancement_id: excludedAdvancementId,
		});
	}

	claimAdvancement(
		minecraftUsername: string,
		periodKey: string,
		unixMs: number,
		target: DailyAdvancementTarget,
		checkOnly = false,
	) {
		return this.minecraft.gameplay<{
			claimed: boolean;
			online: boolean;
			completed: boolean;
			message: string;
		}>('ClaimDailyAdvancement', {
			minecraft_username: minecraftUsername,
			advancement_id: target.advancementId,
			bonus_reward_dabloons: target.bonusRewardDabloons,
			period_key: periodKey,
			unix_ms: unixMs,
			check_only: checkOnly,
		});
	}
}
