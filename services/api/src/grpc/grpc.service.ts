import { join } from 'node:path'
import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import * as protoLoader from '@grpc/proto-loader'

interface ProtoRoot {
	mcstack: {
		auth: {
			v1: {
				ModControl: grpc.ServiceClientConstructor
				AuthEvents: grpc.ServiceClientConstructor & {
					service: grpc.ServiceDefinition
				}
			}
		}
	}
}

interface UnaryCallback<T> {
	(error: grpc.ServiceError | null, response: T): void
}

@Injectable()
export class GrpcService implements OnModuleInit, OnModuleDestroy {
	private readonly logger = new Logger(GrpcService.name)
	private server: grpc.Server | null = null
	private modControlClient: grpc.Client | null = null

	async onModuleInit() {
		const proto = this.loadProto()

		this.modControlClient = new proto.mcstack.auth.v1.ModControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		)

		this.server = new grpc.Server()
		this.server.addService(proto.mcstack.auth.v1.AuthEvents.service, {
			Ping: this.ping.bind(this),
			ReportLoginAttempt: this.reportLoginAttempt.bind(this),
		})

		const host = process.env.API_GRPC_HOST ?? '0.0.0.0'
		const port = Number(process.env.API_GRPC_PORT ?? 50051)

		await new Promise<void>((resolve, reject) => {
			this.server?.bindAsync(
				`${host}:${port}`,
				grpc.ServerCredentials.createInsecure(),
				(error) => {
					if (error) {
						reject(error)
						return
					}

					this.logger.log(`API gRPC server listening on ${host}:${port}`)
					resolve()
				},
			)
		})
	}

	onModuleDestroy() {
		this.modControlClient?.close()
		this.server?.forceShutdown()
	}

	async upsertPendingJoin(input: {
		minecraftUsername: string
		code: string
		expiresAtUnixMs: number
	}): Promise<void> {
		await this.callMod('UpsertPendingJoin', {
			minecraft_username: input.minecraftUsername,
			code: input.code,
			expires_at_unix_ms: input.expiresAtUnixMs,
		})
	}

	async whitelistPlayer(minecraftUsername: string): Promise<void> {
		await this.callMod('WhitelistPlayer', {
			minecraft_username: minecraftUsername,
		})
	}

	async removePendingJoin(minecraftUsername: string): Promise<void> {
		await this.callMod('RemovePendingJoin', {
			minecraft_username: minecraftUsername,
		})
	}

	private async callMod(methodName: string, request: Record<string, unknown>): Promise<unknown> {
		if (!this.modControlClient) {
			throw new Error('ModControl gRPC client is not initialized')
		}

		const method = this.modControlClient[methodName as keyof grpc.Client] as unknown

		if (typeof method !== 'function') {
			throw new Error(`Unknown ModControl method: ${methodName}`)
		}

		return await new Promise((resolve, reject) => {
			method.call(this.modControlClient, request, (error: grpc.ServiceError | null, response: unknown) => {
				if (error) {
					reject(error)
					return
				}

				resolve(response)
			})
		})
	}

	private ping(
		call: grpc.ServerUnaryCall<{ service?: string }, { service: string; unix_ms: number }>,
		callback: UnaryCallback<{ service: string; unix_ms: number }>,
	) {
		callback(null, {
			service: 'api',
			unix_ms: Date.now(),
		})
	}

	private reportLoginAttempt(
		call: grpc.ServerUnaryCall<{
			minecraft_username?: string
			uuid?: string
			whitelisted?: boolean
			unix_ms?: number
		}, { received: boolean }>,
		callback: UnaryCallback<{ received: boolean }>,
	) {
		this.logger.log(
			`Minecraft login attempt: username=${call.request.minecraft_username ?? 'unknown'} whitelisted=${Boolean(call.request.whitelisted)}`,
		)

		callback(null, { received: true })
	}

	private loadProto(): ProtoRoot {
		const protoPath = join(process.cwd(), 'proto', 'auth.proto')

		const packageDefinition = protoLoader.loadSync(protoPath, {
			keepCase: true,
			longs: Number,
			enums: String,
			defaults: true,
			oneofs: true,
		})

		return grpc.loadPackageDefinition(packageDefinition) as unknown as ProtoRoot
	}
}
