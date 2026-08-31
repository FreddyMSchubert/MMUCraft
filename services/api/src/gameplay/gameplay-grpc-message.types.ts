import type { MinecraftStatInput } from '../players/player-statistics';

export type EmptyGrpcRequest = Record<string, never>;

export interface CommandExecutionRequest {
	command?: string;
	source?: string;
	actor_name?: string;
	minecraft_uuid?: string;
	is_operator?: boolean;
	succeeded?: boolean;
	result?: number;
	unix_ms?: number;
}

export interface DailyTaskUpdateRequest {
	user_id?: number;
	period_key?: string;
	task_json?: string;
	unix_ms?: number;
}

export interface DailyTaskUpdateResponse {
	accepted: boolean;
	message: string;
}

export interface KnowledgeUnlockRequest {
	minecraft_username?: string;
	minecraft_uuid?: string;
	source_item_id?: string;
	unix_ms?: number;
	unlock_type?: string;
}

export interface KnowledgeUnlockResponse {
	unlocked: boolean;
	all_unlocked: boolean;
	knowledge_id: string;
	unlocked_id: string;
	priority: number;
	topic: string;
	message: string;
	unlock_type: string;
	has_knowledge_to_unlock: boolean;
	has_charms_to_unlock: boolean;
	has_cosmetics_to_unlock: boolean;
}

export interface KnowledgeTipRequest {
	minecraft_username?: string;
	minecraft_uuid?: string;
}

export interface KnowledgeTipResponse {
	found: boolean;
	knowledge_id: string;
	tip: string;
}

export interface UnlockAvailabilityRequest {
	minecraft_username?: string;
	minecraft_uuid?: string;
	unix_ms?: number;
}

export interface UnlockAvailabilityResponse {
	account_linked: boolean;
	has_knowledge_to_unlock: boolean;
	has_charms_to_unlock: boolean;
	has_cosmetics_to_unlock: boolean;
	message: string;
}

export interface PlayerStatisticsSyncRequest {
	minecraft_username?: string;
	minecraft_uuid?: string;
	unix_ms?: number;
	balance_dabloons?: number;
	stats?: MinecraftStatInput[];
}

export interface PlayerStatisticsSyncResponse {
	accepted: boolean;
	account_linked: boolean;
	is_member: boolean;
	is_committee: boolean;
	is_external: boolean;
	nickname: string;
	pronouns: string;
	color_hex: string;
	show_death_counter: boolean;
	previous_last_played_at_unix_ms: number;
	message: string;
}

export interface MoneyEventRequest {
	minecraft_username?: string;
	minecraft_uuid?: string;
	amount_dabloons?: number;
	source?: string;
	reference_id?: string;
	unix_ms?: number;
	balance_dabloons?: number;
}

export interface MoneyEventResponse {
	recorded: boolean;
	duplicate: boolean;
	account_linked: boolean;
	message: string;
}

export interface FishCatchRequest {
	minecraft_username?: string;
	minecraft_uuid?: string;
	fish_id?: string;
	length_cm?: number;
	rarity?: string;
	unix_ms?: number;
}

export interface FishCatchResponse {
	recorded: boolean;
	account_linked: boolean;
	first_catch: boolean;
	personal_size_record: boolean;
	server_size_record: boolean;
	server_smallest_record: boolean;
	personal_smallest_record: boolean;
	message: string;
}
