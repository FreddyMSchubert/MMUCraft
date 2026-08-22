import { Body, Controller, Get, Headers, Param, Patch, Query } from '@nestjs/common';
import { AuthService } from '../auth/auth.service';
import { PlayersService } from './players.service';

@Controller('api/players')
export class PlayersController {
	constructor(
		private readonly auth: AuthService,
		private readonly players: PlayersService,
	) {}

	@Get()
	listPlayers(
		@Headers('cookie') cookieHeader: string | undefined,
		@Query('page') page: string | undefined,
		@Query('player') player: string | undefined,
	) {
		const user = this.auth.requireSession(cookieHeader);
		return this.players.listPlayers(user, page, player);
	}

	@Get('online')
	listOnlinePlayers(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireSession(cookieHeader);
		return this.players.listOnlinePlayers();
	}

	@Patch('me/profile')
	updateOwnProfile(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: Record<string, unknown> | undefined,
	) {
		const user = this.auth.requireSession(cookieHeader);
		return this.players.updateOwnProfile(user, body ?? {});
	}

	@Patch(':userId/profile')
	updateProfile(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('userId') userId: string,
		@Body() body: Record<string, unknown> | undefined,
	) {
		const user = this.auth.requireSession(cookieHeader);
		return this.players.updateProfile(user, userId, body ?? {});
	}

	@Get(':userId')
	getPlayer(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('userId') userId: string,
	) {
		const user = this.auth.requireSession(cookieHeader);
		return this.players.getPlayer(user, userId);
	}
}
