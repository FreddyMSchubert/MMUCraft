import { Body, Controller, Delete, Get, Headers, Param, Patch, Post, Query } from '@nestjs/common'
import { desc } from 'drizzle-orm'
import { AuthService } from '../auth/auth.service'
import { DatabaseService, discordAdminCommandLogs } from '../database/database.service'
import { GiftsService } from './gifts.service'
import { CountdownsService } from './countdowns.service'

interface CountdownBody {
	heading?: unknown
	target?: unknown
	description?: unknown
	headingColor?: unknown
	descriptionColor?: unknown
	backgroundColor?: unknown
	backgroundAlpha?: unknown
	backgroundImageUrl?: unknown
}

@Controller('api/admin')
export class AdminController {
	constructor(
		private readonly auth: AuthService,
		private readonly gifts: GiftsService,
		private readonly database: DatabaseService,
		private readonly countdowns: CountdownsService,
	) { }

	@Get('countdowns')
	listCountdowns(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.countdowns.list()
	}

	@Post('countdowns')
	createCountdown(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: CountdownBody | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.countdowns.create(body?.heading, body?.target, body?.description, body?.headingColor, body?.descriptionColor, body?.backgroundColor, body?.backgroundAlpha, body?.backgroundImageUrl)
	}

	@Patch('countdowns/:id')
	updateCountdown(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('id') id: string,
		@Body() body: CountdownBody | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.countdowns.update(id, body?.heading, body?.target, body?.description, body?.headingColor, body?.descriptionColor, body?.backgroundColor, body?.backgroundAlpha, body?.backgroundImageUrl)
	}

	@Patch('countdowns/:id/order')
	moveCountdown(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('id') id: string,
		@Body() body: { direction?: unknown } | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.countdowns.move(id, body?.direction)
	}

	@Delete('countdowns/:id')
	removeCountdown(@Headers('cookie') cookieHeader: string | undefined, @Param('id') id: string) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.countdowns.remove(id)
	}

	@Get('players')
	listPlayers(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.gifts.listAdminPlayers()
	}

	@Get('discord-admin-commands')
	listDiscordAdminCommands(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader)
		return {
			commands: this.database.connection.select().from(discordAdminCommandLogs)
				.orderBy(desc(discordAdminCommandLogs.created_at_unix_ms)).limit(200).all().map((entry) => ({
					command: entry.command,
					discordUsername: entry.discord_username,
					createdAtUnixMs: entry.created_at_unix_ms,
				})),
		}
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

	@Get('player-bans')
	listPlayerBans(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.auth.listPlayerBans()
	}

	@Post('player-bans')
	applyPlayerBan(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { userId?: unknown; expiresAtUnixMs?: unknown } | undefined,
	) {
		const admin = this.auth.requireCommitteeSession(cookieHeader)
		return this.auth.applyPlayerBan(admin, body?.userId, body?.expiresAtUnixMs)
	}

	@Delete('player-bans/:userId')
	removePlayerBan(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('userId') userId: string,
	) {
		this.auth.requireCommitteeSession(cookieHeader)
		return this.auth.removePlayerBan(userId)
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
