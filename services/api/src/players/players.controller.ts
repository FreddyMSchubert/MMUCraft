import { Body, Controller, Get, Headers, Param, Patch, Query } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import type { PlayerProfileInput } from './player-profile';
import { OnlinePlayerPresenceService } from './online-player-presence.service';
import { PlayersService } from './players.service';

@Controller('api/players')
export class PlayersController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly players: PlayersService,
		private readonly playerPresence: OnlinePlayerPresenceService,
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
		return this.playerPresence.listOnlinePlayers();
	}

	@Patch('me/profile')
	updateOwnProfile(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: PlayerProfileInput | undefined,
	) {
		const user = this.auth.requireSession(cookieHeader);
		return this.players.updateOwnProfile(user, body ?? {});
	}

	@Patch(':userId/profile')
	updateProfile(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('userId') userId: string,
		@Body() body: PlayerProfileInput | undefined,
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
