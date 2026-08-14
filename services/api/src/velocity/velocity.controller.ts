import { Body, Controller, Delete, Get, Headers, Param, Patch, Post } from '@nestjs/common'
import { AuthService } from '../auth/auth.service'
import { VelocityService } from './velocity.service'

@Controller('api/admin/velocity')
export class VelocityAdminController {
	constructor(
		private readonly auth: AuthService,
		private readonly velocity: VelocityService,
	) { }

	@Get()
	snapshot(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.velocity.adminSnapshot()
	}

	@Post('servers')
	createServer(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { name?: unknown; address?: unknown } | undefined,
	) {
		return this.velocity.createServer(
			this.auth.requireCommitteeSession(cookieHeader),
			body?.name,
			body?.address,
		)
	}

	@Patch('servers/:id/default')
	setDefaultServer(@Headers('cookie') cookieHeader: string | undefined, @Param('id') id: string) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.velocity.setDefaultServer(id)
	}

	@Delete('servers/:id')
	removeServer(@Headers('cookie') cookieHeader: string | undefined, @Param('id') id: string) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.velocity.removeServer(id)
	}

	@Post('schedules')
	createSchedule(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: {
			name?: unknown
			serverId?: unknown
			startsAtUnixMs?: unknown
			endsAtUnixMs?: unknown
		} | undefined,
	) {
		return this.velocity.createSchedule(this.auth.requireCommitteeSession(cookieHeader), body)
	}

	@Delete('schedules/:id')
	removeSchedule(@Headers('cookie') cookieHeader: string | undefined, @Param('id') id: string) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.velocity.removeSchedule(id)
	}

	@Patch('maintenance')
	setMaintenanceMode(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { enabled?: unknown } | undefined,
	) {
		return this.velocity.setMaintenanceMode(
			this.auth.requireCommitteeSession(cookieHeader),
			body?.enabled,
		)
	}

	@Post('players/:uuid/move')
	movePlayer(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('uuid') uuid: string,
		@Body() body: { serverId?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.velocity.movePlayer(uuid, body?.serverId)
	}
}

@Controller('api/internal/velocity')
export class VelocityInternalController {
	constructor(private readonly velocity: VelocityService) { }

	@Post('access')
	access(
		@Headers('authorization') authorization: string | undefined,
		@Body() body: { uuid?: unknown; username?: unknown } | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization)
		return this.velocity.authorizePlayer(body?.uuid, body?.username)
	}

	@Post('sync')
	sync(
		@Headers('authorization') authorization: string | undefined,
		@Body() body: {
			servers?: unknown
			players?: unknown
			acknowledgedCommandIds?: unknown
		} | undefined,
	) {
		this.velocity.verifyInternalAuthorization(authorization)
		return this.velocity.synchronize(body)
	}
}
