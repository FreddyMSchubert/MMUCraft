import { Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
import { createHash } from 'node:crypto';
import * as grpc from '@grpc/grpc-js';
import { GrpcServerService } from './grpc-server.service';
import { callUnary } from './grpc.types';
import { observeGrpc } from '../monitoring/monitoring.service';

interface GameplayProtoRoot {
	mcstack: { gameplay: { v1: { GameplayControl: grpc.ServiceClientConstructor } } };
}

const ECONOMY_METHODS = new Set([
	'GrantDailyLoginBonus',
	'GrantGiftCodeMoney',
	'GrantKnowledgeReadMoney',
	'ClaimDailyTask',
	'ClaimDailyAdvancement',
	'PurchaseShopItem',
	'PurchaseExternalPlayerInvite',
	'PurchaseClaim',
	'UpgradeCharm',
]);
const LOG_FIELDS =
	'minecraft_username user_id dimension chunk_x chunk_z price_dabloons amount_dabloons amount item_id delivery_kind expected_level period_key source advancement_id bonus_reward_dabloons check_only granted claimed purchased upgraded online completed balance_dabloons new_level'.split(
		' ',
	);

function logFields(value: object) {
	const fields = value as Record<string, unknown>;
	const selected = Object.fromEntries(
		LOG_FIELDS.filter((key) =>
			['string', 'number', 'boolean'].includes(typeof fields[key]),
		).map((key) => [key, fields[key]]),
	);
	if (typeof fields.code === 'string')
		selected.code_sha256 = createHash('sha256').update(fields.code).digest('hex');
	if (typeof fields.task_json === 'string') {
		try {
			const task: unknown = JSON.parse(fields.task_json);
			if (task && typeof task === 'object' && 'id' in task && typeof task.id === 'string')
				selected.task_id = task.id;
		} catch {
			selected.task_id = 'invalid';
		}
	}
	return selected;
}

@Injectable()
export class MinecraftGrpcClientService implements OnModuleDestroy {
	private gameplayClient: grpc.Client | null = null;
	private readonly logger = new Logger('EconomyRpc');

	constructor(private readonly grpcServer: GrpcServerService) {}

	gameplay<T>(methodName: string, request: object, options?: grpc.CallOptions) {
		const tracked = ECONOMY_METHODS.has(methodName);
		const started = Date.now();
		const log = (phase: string, details: object) => {
			this.logger.log(
				JSON.stringify({
					at_unix_ms: Date.now(),
					phase,
					method: methodName,
					...details,
				}),
			);
		};
		if (tracked) log('request', logFields(request));
		return observeGrpc('gameplay', methodName, () =>
			callUnary<T>(this.getGameplayClient(), methodName, request, options),
		).then(
			(result) => {
				if (tracked)
					log('response', {
						...logFields(request),
						...logFields(result as object),
						duration_ms: Date.now() - started,
					});
				return result;
			},
			(error: unknown) => {
				if (tracked)
					log('error', {
						...logFields(request),
						duration_ms: Date.now() - started,
						status_code: error instanceof Object && 'code' in error ? error.code : null,
						error: error instanceof Error ? error.message : String(error),
					});
				throw error;
			},
		);
	}

	tryGrantAdvancement(
		minecraftUuid: string | null | undefined,
		minecraftUsername: string,
		path: string,
	) {
		return this.gameplay('TryGrantAdvancement', {
			minecraft_uuid: minecraftUuid ?? '',
			minecraft_username: minecraftUsername,
			advancement_id: `mainmod:mastery/${path}`,
		});
	}

	onModuleDestroy() {
		this.gameplayClient?.close();
	}

	private getGameplayClient() {
		if (this.gameplayClient) return this.gameplayClient;
		const proto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto');
		return (this.gameplayClient = new proto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		));
	}
}
