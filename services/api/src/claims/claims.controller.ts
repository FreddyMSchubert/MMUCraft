import { Body, Controller, Delete, Get, Headers, Param, Patch, Post } from '@nestjs/common'
import { AuthService } from '../auth/auth.service'
import { ClaimsService } from './claims.service'

@Controller('api/claims')
export class ClaimsController {
	constructor(
		private readonly auth: AuthService,
		private readonly claims: ClaimsService,
	) { }

	@Get()
	list(@Headers('cookie') cookieHeader: string | undefined) {
		return this.claims.list(this.auth.requireSession(cookieHeader))
	}

	@Get('current')
	current(@Headers('cookie') cookieHeader: string | undefined) {
		return this.claims.getCurrentChunk(this.auth.requireSession(cookieHeader))
	}

	@Post()
	create(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: Record<string, unknown> | undefined,
	) {
		return this.claims.create(this.auth.requireSession(cookieHeader), body ?? {})
	}

	@Delete(':claimId')
	remove(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('claimId') claimId: string,
	) {
		return this.claims.remove(this.auth.requireSession(cookieHeader), claimId)
	}

	@Patch(':claimId/appearance')
	updateAppearance(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('claimId') claimId: string,
		@Body() body: Record<string, unknown> | undefined,
	) {
		return this.claims.updateAppearance(this.auth.requireSession(cookieHeader), claimId, body ?? {})
	}

	@Post(':claimId/members')
	addMember(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('claimId') claimId: string,
		@Body() body: { userId?: unknown } | undefined,
	) {
		return this.claims.addMember(this.auth.requireSession(cookieHeader), claimId, body?.userId)
	}

	@Delete(':claimId/members/:userId')
	removeMember(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('claimId') claimId: string,
		@Param('userId') userId: string,
	) {
		return this.claims.removeMember(this.auth.requireSession(cookieHeader), claimId, userId)
	}
}
