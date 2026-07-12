import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { MinecraftIdentityService, normalizeMinecraftUuid } from '../database/minecraft-identity.service'
import { GrpcServerService } from '../grpc/grpc-server.service'
import { UnaryCallback } from '../grpc/grpc.types'
import { isValidMinecraftUsername } from './auth.util'
import { signupFlows } from './signup-flow'

interface AuthProtoRoot {
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

@Injectable()
export class AuthGrpcService implements OnModuleInit, OnModuleDestroy {
	private readonly logger = new Logger(AuthGrpcService.name)
	private modControlClient: grpc.Client | null = null

	constructor(
		private readonly grpcServer: GrpcServerService,
		private readonly identities: MinecraftIdentityService,
	) { }

	onModuleInit() {
		const authProto = this.grpcServer.loadProto<AuthProtoRoot>('auth.proto')

		this.modControlClient = new authProto.mcstack.auth.v1.ModControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		)

		this.grpcServer.addService(authProto.mcstack.auth.v1.AuthEvents.service, {
			Ping: this.ping.bind(this),
			ReportLoginAttempt: this.reportLoginAttempt.bind(this),
		})
	}

	onModuleDestroy() {
		this.modControlClient?.close()
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
		const username = (call.request.minecraft_username ?? '').trim()
		const uuid = normalizeMinecraftUuid(call.request.uuid ?? '')

		if (!uuid || !isValidMinecraftUsername(username)) {
			this.logger.warn('Ignored Minecraft login attempt with an invalid username or UUID')
			callback(null, { received: false })
			return
		}

		const existingUser = this.identities.resolveAndRefresh(uuid, username)
		if (!existingUser) {
			this.attachIdentityToPendingSignup(uuid, username)
		}

		this.logger.log(
			`Minecraft login attempt: username=${username} uuid=${uuid} whitelisted=${Boolean(call.request.whitelisted)}`,
		)

		callback(null, { received: true })
	}

	private attachIdentityToPendingSignup(uuid: string, username: string) {
		if (this.identities.findByUuid(uuid)) return

		const flow = [...signupFlows.values()].find((candidate) => (
			candidate.step === 'minecraft-code'
			&& candidate.minecraftUsername?.toLowerCase() === username.toLowerCase()
		))
		if (!flow) return

		const uuidInAnotherFlow = [...signupFlows.values()]
			.some((candidate) => candidate !== flow && candidate.minecraftUuid === uuid)
		if (uuidInAnotherFlow) return

		flow.minecraftUuid = uuid
		flow.minecraftUsername = username
		flow.updatedAt = Date.now()
	}
}
