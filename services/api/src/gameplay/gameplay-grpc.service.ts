import { Injectable, OnModuleInit } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { GrpcServerService } from '../grpc/grpc-server.service'
import { UnaryCallback } from '../grpc/grpc.types'
import { KnowledgeService } from './knowledge/knowledge.service'
import { ShopService } from './shop/shop.service'

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
	) { }

	onModuleInit() {
		const gameplayProto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto')

		this.grpcServer.addService(gameplayProto.mcstack.gameplay.v1.GameplayEvents.service, {
			UnlockNextKnowledge: this.unlockNextKnowledge.bind(this),
			GetUnlockAvailability: this.getUnlockAvailability.bind(this),
		})
	}

	private unlockNextKnowledge(
		call: grpc.ServerUnaryCall<{
			minecraft_username?: string
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
		const sourceItemId = call.request.source_item_id ?? 'knowledge_book'
		const unlockType = this.resolveUnlockType(call.request.unlock_type ?? '', sourceItemId)
		const result = unlockType === 'knowledge'
			? this.knowledge.unlockNextForMinecraftUsername(minecraftUsername, sourceItemId)
			: this.shop.unlockNextForMinecraftUsername(minecraftUsername, unlockType, sourceItemId)
		const availability = this.shop.getUnlockAvailabilityForMinecraftUsername(minecraftUsername)

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
		const availability = this.shop.getUnlockAvailabilityForMinecraftUsername(call.request.minecraft_username ?? '')

		callback(null, {
			account_linked: availability.accountLinked,
			has_knowledge_to_unlock: availability.knowledge,
			has_charms_to_unlock: availability.charms,
			has_cosmetics_to_unlock: availability.cosmetics,
			message: availability.message,
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
}
