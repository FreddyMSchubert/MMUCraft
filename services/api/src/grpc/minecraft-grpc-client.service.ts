import { Injectable, OnModuleDestroy } from '@nestjs/common';
import * as grpc from '@grpc/grpc-js';
import { GrpcServerService } from './grpc-server.service';
import { callUnary } from './grpc.types';

interface GameplayProtoRoot {
	mcstack: { gameplay: { v1: { GameplayControl: grpc.ServiceClientConstructor } } };
}

interface AuthProtoRoot {
	mcstack: { auth: { v1: { ModControl: grpc.ServiceClientConstructor } } };
}

@Injectable()
export class MinecraftGrpcClientService implements OnModuleDestroy {
	private gameplayClient: grpc.Client | null = null;
	private modClient: grpc.Client | null = null;

	constructor(private readonly grpcServer: GrpcServerService) {}

	gameplay<T>(methodName: string, request: object, options?: grpc.CallOptions) {
		return callUnary<T>(this.getGameplayClient(), methodName, request, options);
	}

	mod<T>(methodName: string, request: object, options?: grpc.CallOptions) {
		return callUnary<T>(this.getModClient(), methodName, request, options);
	}

	onModuleDestroy() {
		this.gameplayClient?.close();
		this.modClient?.close();
	}

	private getGameplayClient() {
		if (this.gameplayClient) return this.gameplayClient;
		const proto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto');
		return (this.gameplayClient = new proto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		));
	}

	private getModClient() {
		if (this.modClient) return this.modClient;
		const proto = this.grpcServer.loadProto<AuthProtoRoot>('auth.proto');
		return (this.modClient = new proto.mcstack.auth.v1.ModControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		));
	}
}
