import { Body, Controller, Delete, Get, Headers, Param, Patch, Post, Query } from '@nestjs/common'
import { AuthService } from '../auth/auth.service'
import { GiftsService } from './gifts.service'

@Controller('api/admin')
export class AdminController {
	constructor(
		private readonly auth: AuthService,
		private readonly gifts: GiftsService,
	) { }

	@Get('players')
	listPlayers(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.gifts.listAdminPlayers()
	}

	@Patch('players/:userId/membership')
	setMembership(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('userId') userId: string,
		@Body() body: { isMember?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.gifts.setMembership(userId, body?.isMember)
	}

	@Patch('players/:userId/committee')
	setCommittee(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('userId') userId: string,
		@Body() body: { isCommittee?: unknown } | undefined,
	) {
		this.auth.requireSuperAdminSession(cookieHeader)
		return this.gifts.setCommittee(userId, body?.isCommittee)
	}

	@Get('gift-codes')
	listGiftCodes(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.gifts.listGiftCodes()
	}

	@Get('signins')
	listSignins(
		@Headers('cookie') cookieHeader: string | undefined,
		@Query('offset') offset: string | undefined,
		@Query('limit') limit: string | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.auth.listAuthRequests(offset, limit)
	}

	@Get('email-whitelist')
	listEmailWhitelist(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.auth.listEmailWhitelist()
	}

	@Post('email-whitelist')
	addEmailToWhitelist(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { email?: unknown; responsibleUserId?: unknown } | undefined,
	) {
		const admin = this.auth.requireCommitteeSession(cookieHeader)
		return this.auth.addEmailToWhitelist(admin, body?.email, body?.responsibleUserId)
	}

	@Delete('email-whitelist/:email')
	removeEmailFromWhitelist(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('email') email: string,
	) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.auth.removeEmailFromWhitelist(email)
	}

	@Post('gift-codes')
	createGiftCode(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: {
			code?: unknown
			amountDabloons?: unknown
			redemptionMode?: unknown
			membersOnly?: unknown
			expiresAtUnixMs?: unknown
		} | undefined,
	) {
		const admin = this.auth.requireCommitteeSession(cookieHeader)
		return this.gifts.createGiftCode(
			admin,
			body?.code,
			body?.amountDabloons,
			body?.redemptionMode,
			body?.membersOnly,
			body?.expiresAtUnixMs,
		)
	}
}
