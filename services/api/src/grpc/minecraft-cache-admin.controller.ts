import { Controller, Headers, Logger, Post, ServiceUnavailableException } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { MinecraftGrpcClientService } from './minecraft-grpc-client.service';

@Controller('api/admin/minecraft/cache')
export class MinecraftCacheAdminController {
	private readonly logger = new Logger(MinecraftCacheAdminController.name);

	constructor(
		private readonly auth: AuthSessionService,
		private readonly minecraft: MinecraftGrpcClientService,
	) {}

	@Post('refresh')
	async refresh(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		try {
			const response = await this.minecraft.gameplay<{
				refreshed: boolean;
				online_players: number;
			}>('RefreshApiCaches', {}, { deadline: Date.now() + 30_000 });
			if (!response.refreshed) throw new Error('Minecraft did not confirm the refresh');
			return { ok: true, onlinePlayers: response.online_players };
		} catch (error) {
			this.logger.warn(`Could not refresh Minecraft data: ${String(error)}`);
			throw new ServiceUnavailableException(
				'Could not refresh Minecraft data. Try again shortly.',
			);
		}
	}
}
