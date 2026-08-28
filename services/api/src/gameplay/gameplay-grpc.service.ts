import { Injectable, OnModuleInit } from '@nestjs/common';
import * as grpc from '@grpc/grpc-js';
import { GrpcServerService } from '../grpc/grpc-server.service';
import { UnaryCallback } from '../grpc/grpc.types';
import { OnlinePlayerPresenceService } from '../players/online-player-presence.service';
import { PlayerMoneyHistoryService } from '../players/player-money-history.service';
import { PlayerStatisticsSynchronizationService } from '../players/player-statistics-synchronization.service';
import { KnowledgeService } from './knowledge/knowledge.service';
import { ShopUnlocksService } from './shop/shop-unlocks.service';
import { FishingService } from '../fishing/fishing.service';
import { ClaimsService, ClaimsSnapshot } from '../claims/claims.service';
import { DailiesService } from './dailies/dailies.service';
import { DiscordService, MinecraftDiscordEvent } from '../discord/discord.service';
import {
	FeatureTogglesService,
	type FeatureTogglesSnapshot,
} from '../toggles/feature-toggles.service';
import type {
	DailyTaskUpdateRequest,
	DailyTaskUpdateResponse,
	EmptyGrpcRequest,
	FishCatchRequest,
	FishCatchResponse,
	KnowledgeUnlockRequest,
	KnowledgeUnlockResponse,
	MoneyEventRequest,
	MoneyEventResponse,
	PlayerStatisticsSyncRequest,
	PlayerStatisticsSyncResponse,
	UnlockAvailabilityRequest,
	UnlockAvailabilityResponse,
} from './gameplay-grpc-message.types';

interface GameplayProtoRoot {
	mcstack: {
		gameplay: {
			v1: {
				GameplayEvents: grpc.ServiceClientConstructor & {
					service: grpc.ServiceDefinition;
				};
			};
		};
	};
}

@Injectable()
export class GameplayGrpcService implements OnModuleInit {
	constructor(
		private readonly grpcServer: GrpcServerService,
		private readonly knowledge: KnowledgeService,
		private readonly shopUnlocks: ShopUnlocksService,
		private readonly playerMoneyHistory: PlayerMoneyHistoryService,
		private readonly playerStatistics: PlayerStatisticsSynchronizationService,
		private readonly playerPresence: OnlinePlayerPresenceService,
		private readonly fishing: FishingService,
		private readonly claims: ClaimsService,
		private readonly dailies: DailiesService,
		private readonly discord: DiscordService,
		private readonly featureToggles: FeatureTogglesService,
	) {}

	onModuleInit() {
		const gameplayProto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto');

		this.grpcServer.addService(gameplayProto.mcstack.gameplay.v1.GameplayEvents.service, {
			UnlockNextKnowledge: this.unlockNextKnowledge.bind(this),
			GetUnlockAvailability: this.getUnlockAvailability.bind(this),
			SyncPlayerStats: this.syncPlayerStats.bind(this),
			RecordMoneyEvent: this.recordMoneyEvent.bind(this),
			RecordFishCatch: this.recordFishCatch.bind(this),
			GetClaimsSnapshot: this.getClaimsSnapshot.bind(this),
			GetFeatureToggles: this.getFeatureToggles.bind(this),
			GetDailyTasksSnapshot: this.getDailyTasksSnapshot.bind(this),
			UpdateDailyTask: this.updateDailyTask.bind(this),
			PublishDiscordEvent: this.publishDiscordEvent.bind(this),
		});
	}

	private publishDiscordEvent(
		call: grpc.ServerUnaryCall<MinecraftDiscordEvent, { accepted: boolean }>,
		callback: UnaryCallback<{ accepted: boolean }>,
	) {
		this.playerPresence.recordPresenceEvent(call.request);
		void this.discord
			.publish(call.request)
			.then((accepted) => {
				callback(null, { accepted });
			})
			.catch((error: unknown) => {
				const cause = error instanceof Error ? error : new Error('Discord publish failed');
				const serviceError: grpc.ServiceError = Object.assign(cause, {
					code: grpc.status.UNKNOWN,
					details: cause.message,
					metadata: new grpc.Metadata(),
				});
				callback(serviceError, { accepted: false });
			});
	}

	private getDailyTasksSnapshot(
		_call: grpc.ServerUnaryCall<
			EmptyGrpcRequest,
			ReturnType<DailiesService['getMinecraftSnapshot']>
		>,
		callback: UnaryCallback<ReturnType<DailiesService['getMinecraftSnapshot']>>,
	) {
		callback(null, this.dailies.getMinecraftSnapshot());
	}

	private updateDailyTask(
		call: grpc.ServerUnaryCall<DailyTaskUpdateRequest, DailyTaskUpdateResponse>,
		callback: UnaryCallback<DailyTaskUpdateResponse>,
	) {
		callback(
			null,
			this.dailies.updateTaskFromMinecraft(
				call.request.user_id ?? 0,
				call.request.period_key ?? '',
				call.request.task_json ?? '',
			),
		);
	}

	private getClaimsSnapshot(
		_call: grpc.ServerUnaryCall<EmptyGrpcRequest, ClaimsSnapshot>,
		callback: UnaryCallback<ClaimsSnapshot>,
	) {
		callback(null, this.claims.getSnapshot());
	}

	private getFeatureToggles(
		_call: grpc.ServerUnaryCall<EmptyGrpcRequest, FeatureTogglesSnapshot>,
		callback: UnaryCallback<FeatureTogglesSnapshot>,
	) {
		callback(null, this.featureToggles.getSnapshot());
	}

	private unlockNextKnowledge(
		call: grpc.ServerUnaryCall<KnowledgeUnlockRequest, KnowledgeUnlockResponse>,
		callback: UnaryCallback<KnowledgeUnlockResponse>,
	) {
		const minecraftUsername = call.request.minecraft_username ?? '';
		const minecraftUuid = call.request.minecraft_uuid ?? '';
		const sourceItemId = call.request.source_item_id ?? 'knowledge_book';
		const unlockType = this.resolveUnlockType(call.request.unlock_type ?? '', sourceItemId);
		const result =
			unlockType === 'knowledge'
				? this.knowledge.unlockNextForMinecraftUsername(
						minecraftUuid,
						minecraftUsername,
						sourceItemId,
					)
				: this.shopUnlocks.unlockNextForMinecraftPlayer(
						minecraftUuid,
						minecraftUsername,
						unlockType,
						sourceItemId,
					);
		const availability = this.shopUnlocks.availabilityForMinecraftPlayer(
			minecraftUuid,
			minecraftUsername,
		);

		callback(null, {
			...result,
			unlock_type: unlockType,
			unlocked_id:
				'unlocked_id' in result && typeof result.unlocked_id === 'string'
					? result.unlocked_id
					: result.knowledge_id,
			has_knowledge_to_unlock: availability.knowledge,
			has_charms_to_unlock: availability.charms,
			has_cosmetics_to_unlock: availability.cosmetics,
		});
	}

	private getUnlockAvailability(
		call: grpc.ServerUnaryCall<UnlockAvailabilityRequest, UnlockAvailabilityResponse>,
		callback: UnaryCallback<UnlockAvailabilityResponse>,
	) {
		const availability = this.shopUnlocks.availabilityForMinecraftPlayer(
			call.request.minecraft_uuid ?? '',
			call.request.minecraft_username ?? '',
		);

		callback(null, {
			account_linked: availability.accountLinked,
			has_knowledge_to_unlock: availability.knowledge,
			has_charms_to_unlock: availability.charms,
			has_cosmetics_to_unlock: availability.cosmetics,
			message: availability.message,
		});
	}

	private syncPlayerStats(
		call: grpc.ServerUnaryCall<PlayerStatisticsSyncRequest, PlayerStatisticsSyncResponse>,
		callback: UnaryCallback<PlayerStatisticsSyncResponse>,
	) {
		const result = this.playerStatistics.synchronizeFromMinecraft(
			call.request.minecraft_uuid ?? '',
			call.request.minecraft_username ?? '',
			call.request.stats ?? [],
			typeof call.request.balance_dabloons === 'number'
				? call.request.balance_dabloons
				: null,
			typeof call.request.unix_ms === 'number' ? call.request.unix_ms : null,
		);

		callback(null, {
			accepted: result.accepted,
			account_linked: result.accountLinked,
			is_member: result.isMember,
			is_committee: result.isCommittee,
			is_external: result.isExternal,
			nickname: result.nickname,
			pronouns: result.pronouns,
			color_hex: result.color,
			show_death_counter: result.showDeathCounter,
			message: result.message,
		});
	}

	private recordMoneyEvent(
		call: grpc.ServerUnaryCall<MoneyEventRequest, MoneyEventResponse>,
		callback: UnaryCallback<MoneyEventResponse>,
	) {
		const result = this.playerMoneyHistory.recordForMinecraftPlayer(
			call.request.minecraft_uuid ?? '',
			call.request.minecraft_username ?? '',
			call.request.source ?? 'minecraft',
			call.request.amount_dabloons ?? 0,
			typeof call.request.balance_dabloons === 'number'
				? call.request.balance_dabloons
				: null,
			call.request.reference_id ?? '',
			typeof call.request.unix_ms === 'number' ? call.request.unix_ms : null,
		);
		if (call.request.source === 'advancement') {
			this.dailies.notifyAdvancementCompletion(
				result.userId,
				call.request.reference_id ?? '',
			);
		}

		callback(null, {
			recorded: result.recorded,
			duplicate: result.duplicate,
			account_linked: result.accountLinked,
			message: result.message,
		});
	}

	private resolveUnlockType(
		input: string,
		sourceItemId: string,
	): 'knowledge' | 'charm' | 'cosmetic' {
		const explicit = input.trim().toLowerCase();
		if (explicit === 'knowledge' || explicit === 'charm' || explicit === 'cosmetic') {
			return explicit;
		}

		if (sourceItemId === 'charm-magic-book') {
			return 'charm';
		}
		if (sourceItemId === 'charm-fashion-book') {
			return 'cosmetic';
		}

		return 'knowledge';
	}

	private recordFishCatch(
		call: grpc.ServerUnaryCall<FishCatchRequest, FishCatchResponse>,
		callback: UnaryCallback<FishCatchResponse>,
	) {
		callback(
			null,
			this.fishing.recordCatch({
				minecraftUuid: call.request.minecraft_uuid ?? '',
				minecraftUsername: call.request.minecraft_username ?? '',
				fishId: call.request.fish_id ?? '',
				lengthCm: call.request.length_cm ?? 0,
				rarity: call.request.rarity ?? 'common',
				caughtAtUnixMs: call.request.unix_ms ?? Date.now(),
			}),
		);
	}
}
