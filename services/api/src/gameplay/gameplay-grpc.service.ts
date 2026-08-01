import { Injectable, OnModuleInit } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { GrpcServerService } from '../grpc/grpc-server.service'
import { UnaryCallback } from '../grpc/grpc.types'
import { MinecraftStatInput, PlayersService } from '../players/players.service'
import { KnowledgeService } from './knowledge/knowledge.service'
import { ShopService } from './shop/shop.service'
import { FishingService } from '../fishing/fishing.service'

interface GameplayProtoRoot {
	mcstack: {
		gameplay: {
			v1: {
				GameplayEvents: grpc.ServiceClientConstructor & {
					service: grpc.ServiceDefinition
				}
			}
		}
	}
}

@Injectable()
export class GameplayGrpcService implements OnModuleInit {
	constructor(
		private readonly grpcServer: GrpcServerService,
		private readonly knowledge: KnowledgeService,
		private readonly shop: ShopService,
		private readonly players: PlayersService,
		private readonly fishing: FishingService,
	) { }

	onModuleInit() {
		const gameplayProto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto')

		this.grpcServer.addService(gameplayProto.mcstack.gameplay.v1.GameplayEvents.service, {
			UnlockNextKnowledge: this.unlockNextKnowledge.bind(this),
			GetUnlockAvailability: this.getUnlockAvailability.bind(this),
			SyncPlayerStats: this.syncPlayerStats.bind(this),
			RecordMoneyEvent: this.recordMoneyEvent.bind(this),
			RecordFishCatch: this.recordFishCatch.bind(this),
		})
	}

	private unlockNextKnowledge(
		call: grpc.ServerUnaryCall<{
			minecraft_username?: string
			minecraft_uuid?: string
			source_item_id?: string
			unix_ms?: number
			unlock_type?: string
		}, {
			unlocked: boolean
			all_unlocked: boolean
			knowledge_id: string
			unlocked_id: string
			priority: number
			topic: string
			message: string
			unlock_type: string
			has_knowledge_to_unlock: boolean
			has_charms_to_unlock: boolean
			has_cosmetics_to_unlock: boolean
		}>,
		callback: UnaryCallback<{
			unlocked: boolean
			all_unlocked: boolean
			knowledge_id: string
			unlocked_id: string
			priority: number
			topic: string
			message: string
			unlock_type: string
			has_knowledge_to_unlock: boolean
			has_charms_to_unlock: boolean
			has_cosmetics_to_unlock: boolean
		}>,
	) {
		const minecraftUsername = call.request.minecraft_username ?? ''
		const minecraftUuid = call.request.minecraft_uuid ?? ''
		const sourceItemId = call.request.source_item_id ?? 'knowledge_book'
		const unlockType = this.resolveUnlockType(call.request.unlock_type ?? '', sourceItemId)
		const result = unlockType === 'knowledge'
			? this.knowledge.unlockNextForMinecraftUsername(minecraftUuid, minecraftUsername, sourceItemId)
			: this.shop.unlockNextForMinecraftUsername(minecraftUuid, minecraftUsername, unlockType, sourceItemId)
		const availability = this.shop.getUnlockAvailabilityForMinecraftUsername(minecraftUuid, minecraftUsername)

		callback(null, {
			...result,
			unlock_type: unlockType,
			unlocked_id: 'unlocked_id' in result && typeof result.unlocked_id === 'string'
				? result.unlocked_id
				: result.knowledge_id,
			has_knowledge_to_unlock: availability.knowledge,
			has_charms_to_unlock: availability.charms,
			has_cosmetics_to_unlock: availability.cosmetics,
		})
	}

	private getUnlockAvailability(
		call: grpc.ServerUnaryCall<{
			minecraft_username?: string
			minecraft_uuid?: string
			unix_ms?: number
		}, {
			account_linked: boolean
			has_knowledge_to_unlock: boolean
			has_charms_to_unlock: boolean
			has_cosmetics_to_unlock: boolean
			message: string
		}>,
		callback: UnaryCallback<{
			account_linked: boolean
			has_knowledge_to_unlock: boolean
			has_charms_to_unlock: boolean
			has_cosmetics_to_unlock: boolean
			message: string
		}>,
	) {
		const availability = this.shop.getUnlockAvailabilityForMinecraftUsername(
			call.request.minecraft_uuid ?? '',
			call.request.minecraft_username ?? '',
		)

		callback(null, {
			account_linked: availability.accountLinked,
			has_knowledge_to_unlock: availability.knowledge,
			has_charms_to_unlock: availability.charms,
			has_cosmetics_to_unlock: availability.cosmetics,
			message: availability.message,
		})
	}

	private syncPlayerStats(
		call: grpc.ServerUnaryCall<{
			minecraft_username?: string
			minecraft_uuid?: string
			unix_ms?: number
			balance_dabloons?: number
			stats?: MinecraftStatInput[]
		}, {
			accepted: boolean
			account_linked: boolean
			is_member: boolean
			message: string
		}>,
		callback: UnaryCallback<{
			accepted: boolean
			account_linked: boolean
			is_member: boolean
			message: string
		}>,
	) {
		const result = this.players.syncMinecraftStats(
			call.request.minecraft_uuid ?? '',
			call.request.minecraft_username ?? '',
			call.request.stats ?? [],
			typeof call.request.balance_dabloons === 'number' ? call.request.balance_dabloons : null,
			typeof call.request.unix_ms === 'number' ? call.request.unix_ms : null,
		)

		callback(null, {
			accepted: result.accepted,
			account_linked: result.accountLinked,
			is_member: result.isMember,
			message: result.message,
		})
	}

	private recordMoneyEvent(
		call: grpc.ServerUnaryCall<{
			minecraft_username?: string
			minecraft_uuid?: string
			amount_dabloons?: number
			direction?: string
			source?: string
			reference_id?: string
			unix_ms?: number
			balance_dabloons?: number
		}, {
			recorded: boolean
			duplicate: boolean
			account_linked: boolean
			message: string
		}>,
		callback: UnaryCallback<{
			recorded: boolean
			duplicate: boolean
			account_linked: boolean
			message: string
		}>,
	) {
		const result = this.players.recordMoneyForMinecraftUsername(
			call.request.minecraft_uuid ?? '',
			call.request.minecraft_username ?? '',
			call.request.direction ?? 'earned',
			call.request.source ?? 'minecraft',
			call.request.amount_dabloons ?? 0,
			typeof call.request.balance_dabloons === 'number' ? call.request.balance_dabloons : null,
			call.request.reference_id ?? '',
			typeof call.request.unix_ms === 'number' ? call.request.unix_ms : null,
		)

		callback(null, {
			recorded: result.recorded,
			duplicate: result.duplicate,
			account_linked: result.accountLinked,
			message: result.message,
		})
	}

	private resolveUnlockType(input: string, sourceItemId: string): 'knowledge' | 'charm' | 'cosmetic' {
		const explicit = input.trim().toLowerCase()
		if (explicit === 'knowledge' || explicit === 'charm' || explicit === 'cosmetic') {
			return explicit
		}

		if (sourceItemId === 'charm-magic-book') {
			return 'charm'
		}
		if (sourceItemId === 'charm-fashion-book') {
			return 'cosmetic'
		}

		return 'knowledge'
	}

	private recordFishCatch(
		call: grpc.ServerUnaryCall<{
			minecraft_username?: string
			minecraft_uuid?: string
			fish_id?: string
			length_cm?: number
			rarity?: string
			unix_ms?: number
		}, {
			recorded: boolean
			account_linked: boolean
			first_catch: boolean
			personal_size_record: boolean
			server_size_record: boolean
			server_smallest_record: boolean
			personal_smallest_record: boolean
			message: string
		}>,
		callback: UnaryCallback<{
			recorded: boolean
			account_linked: boolean
			first_catch: boolean
			personal_size_record: boolean
			server_size_record: boolean
			server_smallest_record: boolean
			personal_smallest_record: boolean
			message: string
		}>,
	) {
		callback(null, this.fishing.recordCatch({
			minecraftUuid: call.request.minecraft_uuid ?? '',
			minecraftUsername: call.request.minecraft_username ?? '',
			fishId: call.request.fish_id ?? '',
			lengthCm: call.request.length_cm ?? 0,
			rarity: call.request.rarity ?? 'common',
			caughtAtUnixMs: call.request.unix_ms ?? Date.now(),
		}))
	}
}
