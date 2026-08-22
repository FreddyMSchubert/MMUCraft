import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { Injectable, Logger, OnApplicationBootstrap, OnModuleDestroy } from '@nestjs/common';
import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';

@Injectable()
export class GrpcServerService implements OnApplicationBootstrap, OnModuleDestroy {
	private readonly logger = new Logger(GrpcServerService.name);
	private readonly server = new grpc.Server();
	private listening = false;

	addService(service: grpc.ServiceDefinition, implementation: grpc.UntypedServiceImplementation) {
		if (this.listening) {
			throw new Error('Cannot register gRPC service after the server has started');
		}

		this.server.addService(service, implementation);
	}

	loadProto<TProtoRoot>(protoFileName: string): TProtoRoot {
		const protoPath = this.resolveProtoPath(protoFileName);

		const packageDefinition = protoLoader.loadSync(protoPath, {
			keepCase: true,
			longs: Number,
			enums: String,
			defaults: true,
			oneofs: true,
		});

		return grpc.loadPackageDefinition(packageDefinition) as unknown as TProtoRoot;
	}

	private resolveProtoPath(protoFileName: string): string {
		const candidates = [
			join(process.cwd(), 'proto', protoFileName),
			join(process.cwd(), '..', '..', 'proto', protoFileName),
		];

		const protoPath = candidates.find((candidate) => existsSync(candidate));
		if (!protoPath) {
			throw new Error(`Unable to find proto file: ${protoFileName}`);
		}

		return protoPath;
	}

	async onApplicationBootstrap() {
		const host = process.env.API_GRPC_HOST ?? '0.0.0.0';
		const port = Number(process.env.API_GRPC_PORT ?? 50051);

		await new Promise<void>((resolve, reject) => {
			this.server.bindAsync(
				`${host}:${port}`,
				grpc.ServerCredentials.createInsecure(),
				(error) => {
					if (error) {
						reject(error);
						return;
					}

					this.listening = true;
					this.logger.log(`API gRPC server listening on ${host}:${port}`);
					resolve();
				},
			);
		});
	}

	onModuleDestroy() {
		this.server.forceShutdown();
	}
}
