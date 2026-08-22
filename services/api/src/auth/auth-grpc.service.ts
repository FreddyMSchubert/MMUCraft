import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import * as grpc from '@grpc/grpc-js';
import {
	MinecraftIdentityService,
	normalizeMinecraftUuid,
} from '../database/minecraft-identity.service';
import { GrpcServerService } from '../grpc/grpc-server.service';
import { callUnary, UnaryCallback } from '../grpc/grpc.types';
import { isValidMinecraftUsername } from './auth.util';
import { signupFlows } from './signup-flow';
import { PlayerBansService } from './player-bans.service';

interface AuthProtoRoot {
	mcstack: {
		auth: {
			v1: {
				ModControl: grpc.ServiceClientConstructor;
				AuthEvents: grpc.ServiceClientConstructor & {
					service: grpc.ServiceDefinition;
				};
			};
		};
	};
}

@Injectable()
export class AuthGrpcService implements OnModuleInit, OnModuleDestroy {
	private readonly logger = new Logger(AuthGrpcService.name);
	private modControlClient: grpc.Client | null = null;
	private gameplayControlClient: grpc.Client | null = null;

	constructor(
		private readonly grpcServer: GrpcServerService,
		private readonly identities: MinecraftIdentityService,
		private readonly bans: PlayerBansService,
	) {}

	onModuleInit() {
		const authProto = this.grpcServer.loadProto<AuthProtoRoot>('auth.proto');

		this.modControlClient = new authProto.mcstack.auth.v1.ModControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		);
		const gameplayProto = this.grpcServer.loadProto<{
			mcstack: { gameplay: { v1: { GameplayControl: grpc.ServiceClientConstructor } } };
		}>('gameplay.proto');
		this.gameplayControlClient = new gameplayProto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		);

		this.grpcServer.addService(authProto.mcstack.auth.v1.AuthEvents.service, {
			Ping: this.ping.bind(this),
			ReportLoginAttempt: this.reportLoginAttempt.bind(this),
			CheckPlayerBan: this.checkPlayerBan.bind(this),
		});
	}

	onModuleDestroy() {
		this.modControlClient?.close();
		this.gameplayControlClient?.close();
	}

	async upsertPendingJoin(input: {
		minecraftUsername: string;
		code: string;
		expiresAtUnixMs: number;
	}): Promise<void> {
		await this.callMod('UpsertPendingJoin', {
			minecraft_username: input.minecraftUsername,
			code: input.code,
			expires_at_unix_ms: input.expiresAtUnixMs,
		});
	}

	async whitelistPlayer(minecraftUsername: string): Promise<void> {
		await this.callMod('WhitelistPlayer', {
			minecraft_username: minecraftUsername,
		});
	}

	async blacklistPlayer(minecraftUsername: string, uuid: string): Promise<void> {
		await this.callMod('BlacklistPlayer', {
			minecraft_username: minecraftUsername,
			uuid,
		});
	}

	async unblacklistPlayer(minecraftUsername: string, uuid: string): Promise<void> {
		await this.callMod('UnblacklistPlayer', {
			minecraft_username: minecraftUsername,
			uuid,
		});
	}

	async removePendingJoin(minecraftUsername: string): Promise<void> {
		await this.callMod('RemovePendingJoin', {
			minecraft_username: minecraftUsername,
		});
	}

	async purchaseExternalPlayerInvite(minecraftUsername: string) {
		return await this.callClient<{
			purchased: boolean;
			online: boolean;
			balance_dabloons: number;
			message: string;
		}>(this.gameplayControlClient, 'PurchaseExternalPlayerInvite', {
			minecraft_username: minecraftUsername,
		});
	}

	private async callMod(methodName: string, request: Record<string, unknown>): Promise<unknown> {
		return await this.callClient(this.modControlClient, methodName, request);
	}

	private async callClient<T>(
		client: grpc.Client | null,
		methodName: string,
		request: Record<string, unknown>,
	): Promise<T> {
		return await callUnary<T>(client, methodName, request);
	}

	private ping(
		call: grpc.ServerUnaryCall<{ service?: string }, { service: string; unix_ms: number }>,
		callback: UnaryCallback<{ service: string; unix_ms: number }>,
	) {
		callback(null, {
			service: 'api',
			unix_ms: Date.now(),
		});
	}

	private reportLoginAttempt(
		call: grpc.ServerUnaryCall<
			{
				minecraft_username?: string;
				uuid?: string;
				whitelisted?: boolean;
				unix_ms?: number;
			},
			{ received: boolean }
		>,
		callback: UnaryCallback<{ received: boolean }>,
	) {
		const username = (call.request.minecraft_username ?? '').trim();
		const uuid = normalizeMinecraftUuid(call.request.uuid ?? '');

		if (!uuid || !isValidMinecraftUsername(username)) {
			this.logger.warn('Ignored Minecraft login attempt with an invalid username or UUID');
			callback(null, { received: false });
			return;
		}

		const existingUser = this.identities.resolveAndRefresh(uuid, username);
		if (!existingUser) {
			this.attachIdentityToPendingSignup(uuid, username);
		}

		this.logger.log(
			`Minecraft login attempt: username=${username} uuid=${uuid} whitelisted=${Boolean(call.request.whitelisted)}`,
		);

		callback(null, { received: true });
	}

	private checkPlayerBan(
		call: grpc.ServerUnaryCall<
			{
				minecraft_username?: string;
				uuid?: string;
			},
			{ banned: boolean; permanent: boolean; expires_at_unix_ms: number }
		>,
		callback: UnaryCallback<{
			banned: boolean;
			permanent: boolean;
			expires_at_unix_ms: number;
		}>,
	) {
		const username = (call.request.minecraft_username ?? '').trim();
		const uuid = normalizeMinecraftUuid(call.request.uuid ?? '');
		if (!uuid || !isValidMinecraftUsername(username)) {
			callback(null, { banned: false, permanent: false, expires_at_unix_ms: 0 });
			return;
		}

		const user = this.identities.resolveAndRefresh(uuid, username);
		if (!user) {
			callback(null, { banned: false, permanent: false, expires_at_unix_ms: 0 });
			return;
		}

		const ban = this.bans.resolve(user.id);
		callback(null, {
			banned: ban.active,
			permanent: ban.active && ban.expiresAtUnixMs === null,
			expires_at_unix_ms: ban.active ? (ban.expiresAtUnixMs ?? 0) : 0,
		});
	}

	private attachIdentityToPendingSignup(uuid: string, username: string) {
		if (this.identities.findByUuid(uuid)) return;

		const flow = [...signupFlows.values()].find(
			(candidate) =>
				candidate.step === 'minecraft-code' &&
				candidate.minecraftUsername?.toLowerCase() === username.toLowerCase(),
		);
		if (!flow) return;

		const uuidInAnotherFlow = [...signupFlows.values()].some(
			(candidate) => candidate !== flow && candidate.minecraftUuid === uuid,
		);
		if (uuidInAnotherFlow) return;

		flow.minecraftUuid = uuid;
		flow.minecraftUsername = username;
		flow.updatedAt = Date.now();
	}
}
