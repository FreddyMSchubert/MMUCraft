import {
	Controller,
	ForbiddenException,
	Injectable,
	Module,
	OnModuleDestroy,
	Post,
	Req,
	ServiceUnavailableException,
} from '@nestjs/common';
import type { FastifyRequest } from 'fastify';
import * as grpc from '@grpc/grpc-js';
import { DiscordService } from './discord/discord.service';
import { DiscordModule } from './discord/discord.module';
import { GrpcServerService } from './grpc/grpc-server.service';
import { callUnary } from './grpc/grpc.types';

interface GameplayProtoRoot {
	mcstack: { gameplay: { v1: { GameplayControl: grpc.ServiceClientConstructor } } };
}

@Injectable()
export class ShutdownService implements OnModuleDestroy {
	private readonly minecraft: grpc.Client;
	private acceptingRequests = true;
	private activeRequests = 0;
	private drained: (() => void) | null = null;
	private draining: Promise<void> | null = null;

	constructor(
		private readonly grpcServer: GrpcServerService,
		private readonly discord: DiscordService,
	) {
		const gameplayProto = grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto');
		this.minecraft = new gameplayProto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		);
	}

	beginRequest() {
		if (!this.acceptingRequests) return false;
		this.activeRequests++;
		return true;
	}

	endRequest() {
		this.activeRequests--;
		if (this.activeRequests === 0) this.drained?.();
	}

	async prepare() {
		await (this.draining ??= this.drain());
		await this.saveMinecraft();
	}

	async onModuleDestroy() {
		await (this.draining ??= this.drain());
		this.minecraft.close();
	}

	private async drain() {
		this.acceptingRequests = false;
		const httpDrained =
			this.activeRequests === 0
				? Promise.resolve()
				: new Promise<void>((resolve) => (this.drained = resolve));
		await Promise.all([httpDrained, this.grpcServer.drain(), this.discord.drain()]);
	}

	private async saveMinecraft() {
		const response = await callUnary<{ succeeded: boolean; output: string }>(
			this.minecraft,
			'RunServerCommand',
			{ command: 'save-all flush', discord_user: 'deployment' },
		);
		if (!response.succeeded) {
			throw new ServiceUnavailableException(response.output || 'Minecraft save failed');
		}
	}
}

@Controller('api/internal/shutdown')
class ShutdownController {
	constructor(private readonly shutdown: ShutdownService) {}

	@Post()
	async prepare(@Req() request: FastifyRequest) {
		if (request.ip !== '127.0.0.1' && request.ip !== '::1') throw new ForbiddenException();
		await this.shutdown.prepare();
		return { ready: true };
	}
}

@Module({
	imports: [DiscordModule],
	controllers: [ShutdownController],
	providers: [ShutdownService],
	exports: [ShutdownService],
})
export class ShutdownModule {}
