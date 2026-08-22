import { Body, Controller, Delete, Get, Headers, Param, Patch, Post } from '@nestjs/common';
import { desc } from 'drizzle-orm';
import { AuthAccountAdministrationService } from '../auth/auth-account-administration.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { DatabaseService, discordAdminCommandLogs } from '../database/database.service';
import { CountdownInput, CountdownsService } from './countdowns.service';
import { GiftCodeAdministrationService } from './gift-code-administration.service';
import { GiftCodeInput } from './gift-code-validation';
import { PlayerRoleAdministrationService } from './player-role-administration.service';

@Controller('api/admin')
export class AdminController {
	constructor(
		private readonly auth: AuthSessionService,
		private readonly authAdministration: AuthAccountAdministrationService,
		private readonly giftCodes: GiftCodeAdministrationService,
		private readonly playerRoles: PlayerRoleAdministrationService,
		private readonly database: DatabaseService,
		private readonly countdowns: CountdownsService,
	) {}

	@Get('countdowns')
	listCountdowns(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.countdowns.list();
	}

	@Post('countdowns')
	createCountdown(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: CountdownInput | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.countdowns.create(body ?? {});
	}

	@Patch('countdowns/:id')
	updateCountdown(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('id') id: string,
		@Body() body: CountdownInput | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.countdowns.update(id, body ?? {});
	}

	@Patch('countdowns/:id/order')
	moveCountdown(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('id') id: string,
		@Body() body: { direction?: 'up' | 'down' } | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.countdowns.move(id, body?.direction);
	}

	@Delete('countdowns/:id')
	removeCountdown(@Headers('cookie') cookieHeader: string | undefined, @Param('id') id: string) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.countdowns.remove(id);
	}

	@Get('players')
	listPlayers(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.playerRoles.listPlayers();
	}

	@Get('discord-admin-commands')
	listDiscordAdminCommands(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		return {
			commands: this.database.connection
				.select()
				.from(discordAdminCommandLogs)
				.orderBy(desc(discordAdminCommandLogs.created_at_unix_ms))
				.limit(200)
				.all()
				.map((entry) => ({
					command: entry.command,
					discordUsername: entry.discord_username,
					createdAtUnixMs: entry.created_at_unix_ms,
				})),
		};
	}

	@Patch('players/:userId/membership')
	setMembership(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('userId') userId: string,
		@Body() body: { isMember?: boolean } | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.playerRoles.setMembership(userId, body?.isMember);
	}

	@Patch('players/:userId/committee')
	setCommittee(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('userId') userId: string,
		@Body() body: { isCommittee?: boolean } | undefined,
	) {
		this.auth.requireSuperAdminSession(cookieHeader);
		return this.playerRoles.setCommittee(userId, body?.isCommittee);
	}

	@Get('gift-codes')
	listGiftCodes(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.giftCodes.listActive();
	}

	@Get('email-whitelist')
	listEmailWhitelist(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.authAdministration.listEmailWhitelist();
	}

	@Get('player-bans')
	listPlayerBans(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.authAdministration.listPlayerBans();
	}

	@Post('player-bans')
	applyPlayerBan(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { userId?: number; expiresAtUnixMs?: number | null } | undefined,
	) {
		const admin = this.auth.requireCommitteeSession(cookieHeader);
		return this.authAdministration.applyPlayerBan(admin, body?.userId, body?.expiresAtUnixMs);
	}

	@Delete('player-bans/:userId')
	removePlayerBan(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('userId') userId: string,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.authAdministration.removePlayerBan(userId);
	}

	@Post('email-whitelist')
	addEmailToWhitelist(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: { email?: string; responsibleUserId?: number } | undefined,
	) {
		const admin = this.auth.requireCommitteeSession(cookieHeader);
		return this.authAdministration.addEmailToWhitelist(
			admin,
			body?.email,
			body?.responsibleUserId,
		);
	}

	@Delete('email-whitelist/:email')
	removeEmailFromWhitelist(
		@Headers('cookie') cookieHeader: string | undefined,
		@Param('email') email: string,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.authAdministration.removeEmailFromWhitelist(email);
	}

	@Post('gift-codes')
	createGiftCode(
		@Headers('cookie') cookieHeader: string | undefined,
		@Body() body: GiftCodeInput | undefined,
	) {
		const admin = this.auth.requireCommitteeSession(cookieHeader);
		return this.giftCodes.create(admin, body ?? {});
	}
}
