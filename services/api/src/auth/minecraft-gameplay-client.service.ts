import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { GrpcServerService } from '../grpc/grpc-server.service'

@Injectable()
export class MinecraftGameplayClient implements OnModuleInit, OnModuleDestroy {
	private client: grpc.Client | null = null

	constructor(private readonly grpcServer: GrpcServerService) { }

	onModuleInit() {
		const gameplayProto = this.grpcServer.loadProto<{
			mcstack: { gameplay: { v1: { GameplayControl: grpc.ServiceClientConstructor } } }
		}>('gameplay.proto')
		this.client = new gameplayProto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		)
	}

	onModuleDestroy() {
		this.client?.close()
	}

	async purchaseExternalPlayerInvite(minecraftUsername: string) {
		return await this.call<{
			purchased: boolean
			online: boolean
			balance_dabloons: number
			message: string
		}>('PurchaseExternalPlayerInvite', {
			minecraft_username: minecraftUsername,
		})
	}

	private async call<T>(methodName: string, request: Record<string, unknown>): Promise<T> {
		if (!this.client) throw new Error('Minecraft gameplay client is not initialized')
		const method = this.client[methodName as keyof grpc.Client] as unknown
		if (typeof method !== 'function') throw new Error(`Unknown gRPC method: ${methodName}`)

		return await new Promise<T>((resolve, reject) => {
			method.call(this.client, request, (error: grpc.ServiceError | null, response: T) => {
				if (error) reject(error)
				else resolve(response)
			})
		})
	}
}
