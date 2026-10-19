import { Body, Controller, Get, Header, Headers, Param, Patch, Post, Query } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { DucksService } from './ducks.service';

function numericId(input: string) {
	const value = Number(input);
	return Number.isSafeInteger(value) && value > 0 ? value : 0;
}

@Controller('api/ducks')
export class DucksController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly ducks: DucksService,
	) {}

	@Get('availability')
	@Header('Cache-Control', 'no-store')
	availability() {
		return { enabled: this.ducks.isOpen() };
	}

	@Get()
	getMine(@Headers('cookie') cookie: string | undefined) {
		return this.ducks.getForUser(this.auth.requireSession(cookie).id);
	}

	@Post(':duckId/purchase')
	purchase(@Headers('cookie') cookie: string | undefined, @Param('duckId') duckId: string) {
		return this.ducks.purchase(this.auth.requireSession(cookie), numericId(duckId));
	}

	@Get('admin/players')
	players(@Headers('cookie') cookie: string | undefined, @Query('search') search?: string) {
		this.auth.requireCommitteeSession(cookie);
		return this.ducks.listPlayers(search);
	}

	@Get('admin/players/:userId')
	player(@Headers('cookie') cookie: string | undefined, @Param('userId') userId: string) {
		this.auth.requireCommitteeSession(cookie);
		return this.ducks.getAdminPlayer(numericId(userId));
	}

	@Patch('admin/players/:userId/note')
	note(
		@Headers('cookie') cookie: string | undefined,
		@Param('userId') userId: string,
		@Body() body: { note?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.ducks.setNote(numericId(userId), body?.note);
	}

	@Patch('admin/players/:userId/:duckId')
	setAward(
		@Headers('cookie') cookie: string | undefined,
		@Param('userId') userId: string,
		@Param('duckId') duckId: string,
		@Body() body: { awarded?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookie);
		return this.ducks.setAward(numericId(userId), numericId(duckId), body?.awarded);
	}
}
