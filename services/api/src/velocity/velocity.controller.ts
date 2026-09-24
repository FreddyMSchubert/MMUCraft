import { Body, Controller, Delete, Get, Headers, Param, Patch, Post, Query } from '@nestjs/common';
import { AuthSessionService } from '../auth/auth-session.service';
import { SurprisingSaturdayService } from './surprising-saturday.service';
import { VelocityService } from './velocity.service';

@Controller('api/admin/velocity')
export class VelocityAdminController {
	constructor(
		private readonly sessions: AuthSessionService,
		private readonly velocity: VelocityService,
	) {}

	@Get()
	snapshot(@Headers('cookie') cookieHeader: string | undefined) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.velocity.adminSnapshot();
	}

	@Patch('event-override')
	setEventOverride(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { enabled?: unknown } | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.velocity.setEventOverride(body?.enabled);
	}

	@Patch('maintenance')
	setMaintenanceMode(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { enabled?: unknown } | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.velocity.setMaintenanceMode(body?.enabled);
	}

	@Post('players/:uuid/move')
	movePlayer(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('uuid') uuid: string,
		@Body() body: { serverId?: unknown } | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.velocity.movePlayer(uuid, body?.serverId);
	}
}

@Controller('api/internal/velocity')
export class VelocityInternalController {
	constructor(private readonly velocity: VelocityService) {}

	@Post('access')
	access(
		@Headers('authorization') authorization: string | undefined,
		@Body() body: { uuid?: unknown; username?: unknown } | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.velocity.authorizePlayer(body?.uuid, body?.username);
	}

	@Post('sync')
	sync(
		@Headers('authorization') authorization: string | undefined,
		@Body()
		body:
			| {
					servers?: unknown;
					players?: unknown;
					acknowledgedCommandIds?: unknown;
			  }
			| undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.velocity.synchronize(body);
	}

	@Get('event-control')
	eventControl(@Headers('authorization') authorization: string | undefined) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.velocity.eventControlState();
	}
}

@Controller('api/surprising-saturday')
export class SurprisingSaturdayController {
	constructor(
		private readonly sessions: AuthSessionService,
		private readonly events: SurprisingSaturdayService,
		private readonly velocity: VelocityService,
	) {}

	@Get()
	list() {
		return this.events.list();
	}

	@Get('me')
	me(@Headers('cookie') cookieHeader: string | undefined) {
		const user = this.sessions.requireSession(cookieHeader);
		return this.velocity.myServer(user.id);
	}

	@Post('me/server')
	moveSelf(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { serverName?: unknown } | undefined,
	) {
		const user = this.sessions.requireSession(cookieHeader);
		return this.velocity.moveSelf(user.id, body?.serverName);
	}

	@Get(':id')
	detail(@Param('id') id: string) {
		return this.events.detail(id);
	}
}

@Controller('api/admin/surprising-saturday')
export class SurprisingSaturdayAdminController {
	constructor(
		private readonly sessions: AuthSessionService,
		private readonly events: SurprisingSaturdayService,
	) {}

	@Get()
	list(@Headers('cookie') cookieHeader: string | undefined) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.events.list(true);
	}

	@Get(':id')
	detail(@Headers('cookie') cookieHeader: string | undefined, @Param('id') id: string) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.events.adminDetail(id);
	}

	@Post()
	create(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: Record<string, unknown> | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.events.create(body);
	}

	@Patch(':id')
	update(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('id') id: string,
		@Body() body: Record<string, unknown> | undefined,
	) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.events.update(id, body);
	}

	@Delete(':id')
	remove(@Headers('cookie') cookieHeader: string | undefined, @Param('id') id: string) {
		this.sessions.requireCommitteeSession(cookieHeader);
		return this.events.remove(id);
	}
}

@Controller('api/internal/surprising-saturday')
export class SurprisingSaturdayInternalController {
	constructor(
		private readonly velocity: VelocityService,
		private readonly events: SurprisingSaturdayService,
	) {}

	@Get('player-data/:uuid')
	playerData(
		@Headers('authorization') authorization: string | undefined,
		@Param('uuid') uuid: string,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.events.playerData(uuid);
	}

	@Get('score/:uuid')
	score(
		@Headers('authorization') authorization: string | undefined,
		@Param('uuid') uuid: string,
		@Query('atUnixMs') atUnixMs: string | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.events.score(uuid, atUnixMs);
	}

	@Post('list-completion')
	completion(
		@Headers('authorization') authorization: string | undefined,
		@Body() body: Record<string, unknown> | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization);
		return this.events.recordCompletion(body);
	}
}
