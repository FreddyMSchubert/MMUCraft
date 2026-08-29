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
import { DiscordService } from './discord/discord.service';
import { DiscordModule } from './discord/discord.module';
import { GrpcServerService } from './grpc/grpc-server.service';
import { MinecraftGrpcClientService } from './grpc/minecraft-grpc-client.service';
import { OnlinePlayerPresenceService } from './players/online-player-presence.service';
import { PlayersModule } from './players/players.module';

const DEPLOYMENT_START_MESSAGE = 'Server is down for a few seconds to make an update.';
const DEPLOYMENT_COMPLETE_MESSAGE =
	'Server update done - enjoy a server with more features and less bugs!';

@Injectable()
export class ShutdownService implements OnModuleDestroy {
	private acceptingRequests = true;
	private activeRequests = 0;
	private drained: (() => void) | null = null;
	private draining: Promise<void> | null = null;

	constructor(
		private readonly grpcServer: GrpcServerService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly discord: DiscordService,
		private readonly playerPresence: OnlinePlayerPresenceService,
	) {}

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

	async startDeployment() {
		const { players } = await this.playerPresence.listOnlinePlayers();
		if (players.length === 0) return false;
		await this.runMinecraftCommand(
			'kick @a Server update in progress. Reconnect in a few seconds.',
		);
		await this.sendDeploymentNotice('deployment_start', DEPLOYMENT_START_MESSAGE);
		return true;
	}

	completeDeployment() {
		return this.sendDeploymentNotice('deployment_complete', DEPLOYMENT_COMPLETE_MESSAGE);
	}

	async onModuleDestroy() {
		await (this.draining ??= this.drain());
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
		await this.runMinecraftCommand('save-all flush');
	}

	private async runMinecraftCommand(command: string) {
		const response = await this.minecraft.gameplay<{
			succeeded: boolean;
			output: string;
		}>('RunServerCommand', { command, discord_user: 'deployment' });
		if (!response.succeeded) {
			throw new ServiceUnavailableException(response.output || 'Minecraft command failed');
		}
	}

	private async sendDeploymentNotice(type: string, content: string) {
		if (!(await this.discord.publishServer(type, content)))
			throw new ServiceUnavailableException('Discord deployment notice was not sent');
		return true;
	}
}

@Controller('api/internal/deployment')
class DeploymentController {
	constructor(private readonly shutdown: ShutdownService) {}

	@Post('start')
	start(@Req() request: FastifyRequest) {
		requireLoopback(request);
		return this.shutdown.startDeployment();
	}

	@Post('complete')
	complete(@Req() request: FastifyRequest) {
		requireLoopback(request);
		return this.shutdown.completeDeployment();
	}
}

@Controller('api/internal/shutdown')
class ShutdownController {
	constructor(private readonly shutdown: ShutdownService) {}

	@Post()
	async prepare(@Req() request: FastifyRequest) {
		requireLoopback(request);
		await this.shutdown.prepare();
		return { ready: true };
	}
}

function requireLoopback(request: FastifyRequest) {
	if (request.ip !== '127.0.0.1' && request.ip !== '::1') throw new ForbiddenException();
}

@Module({
	imports: [DiscordModule, PlayersModule],
	controllers: [DeploymentController, ShutdownController],
	providers: [ShutdownService],
	exports: [ShutdownService],
})
export class ShutdownModule {}
