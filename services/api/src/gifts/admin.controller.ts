import { Body, Controller, Delete, Get, Headers, Param, Patch, Post, Query } from '@nestjs/common';
import { AuthAccountAdministrationService } from '../auth/auth-account-administration.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { CommandLogsService } from '../database/command-logs.service';
import { SigninAttemptLogsService } from '../database/signin-attempt-logs.service';
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
		private readonly commandLogs: CommandLogsService,
		private readonly signinAttempts: SigninAttemptLogsService,
		private readonly countdowns: CountdownsService,
	) {}

	@Get('countdowns')
	listCountdowns(@Headers('cookie') cookieHeader: string | undefined) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.countdowns.list();
	}

	@Get('signin-attempt-logs')
	listSigninAttemptLogs(
		@Headers('cookie') cookieHeader: string | undefined,
		@Query('beforeId') beforeId: string | undefined,
		@Query('journey') journey: string | undefined,
		@Query('event') event: string | undefined,
		@Query('succeeded') succeeded: string | undefined,
		@Query('fromUnixMs') fromUnixMs: string | undefined,
		@Query('toUnixMs') toUnixMs: string | undefined,
		@Query('search') search: string | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.signinAttempts.list({
			beforeId,
			journey,
			event,
			succeeded,
			fromUnixMs,
			toUnixMs,
			search,
		});
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

	@Get('command-logs')
	listCommandLogs(
		@Headers('cookie') cookieHeader: string | undefined,
		@Query('beforeId') beforeId: string | undefined,
		@Query('isOperator') isOperator: string | undefined,
		@Query('source') source: string | undefined,
		@Query('userId') userId: string | undefined,
		@Query('fromUnixMs') fromUnixMs: string | undefined,
		@Query('toUnixMs') toUnixMs: string | undefined,
		@Query('search') search: string | undefined,
	) {
		this.auth.requireCommitteeSession(cookieHeader);
		return this.commandLogs.list({
			beforeId,
			isOperator,
			source,
			userId,
			fromUnixMs,
			toUnixMs,
			search,
		});
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
