import { Body, Controller, Get, Headers, Param, Patch, Post } from '@nestjs/common'
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

	@Post('gift-codes')
	createGiftCode(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: {
			code?: unknown
			amountDabloons?: unknown
			redemptionMode?: unknown
			expiresAtUnixMs?: unknown
		} | undefined,
	) {
		const admin = this.auth.requireCommitteeSession(cookieHeader)
		return this.gifts.createGiftCode(
			admin,
			body?.code,
			body?.amountDabloons,
			body?.redemptionMode,
			body?.expiresAtUnixMs,
		)
	}
}
