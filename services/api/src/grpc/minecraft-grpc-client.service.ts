import { Injectable, OnModuleDestroy } from '@nestjs/common';
import * as grpc from '@grpc/grpc-js';
import { GrpcServerService } from './grpc-server.service';
import { callUnary } from './grpc.types';
import { observeGrpc } from '../monitoring/monitoring.service';

interface GameplayProtoRoot {
	mcstack: { gameplay: { v1: { GameplayControl: grpc.ServiceClientConstructor } } };
}

@Injectable()
export class MinecraftGrpcClientService implements OnModuleDestroy {
	private gameplayClient: grpc.Client | null = null;

	constructor(private readonly grpcServer: GrpcServerService) {}

	gameplay<T>(methodName: string, request: object, options?: grpc.CallOptions) {
		return observeGrpc('gameplay', methodName, () =>
			callUnary<T>(this.getGameplayClient(), methodName, request, options),
		);
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
