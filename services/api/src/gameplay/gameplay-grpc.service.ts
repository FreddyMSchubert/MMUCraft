import { Injectable, OnModuleInit } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { GrpcServerService } from '../grpc/grpc-server.service'
import { UnaryCallback } from '../grpc/grpc.types'
import { KnowledgeService } from './knowledge/knowledge.service'

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
	) { }

	onModuleInit() {
		const gameplayProto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto')

		this.grpcServer.addService(gameplayProto.mcstack.gameplay.v1.GameplayEvents.service, {
			UnlockNextKnowledge: this.unlockNextKnowledge.bind(this),
		})
	}

	private unlockNextKnowledge(
		call: grpc.ServerUnaryCall<{
			minecraft_username?: string
			source_item_id?: string
			unix_ms?: number
		}, {
			unlocked: boolean
			all_unlocked: boolean
			knowledge_id: string
			priority: number
			topic: string
			message: string
		}>,
		callback: UnaryCallback<{
			unlocked: boolean
			all_unlocked: boolean
			knowledge_id: string
			priority: number
			topic: string
			message: string
		}>,
	) {
		const result = this.knowledge.unlockNextForMinecraftUsername(
			call.request.minecraft_username ?? '',
			call.request.source_item_id ?? 'knowledge_book',
		)

		callback(null, result)
	}
}
