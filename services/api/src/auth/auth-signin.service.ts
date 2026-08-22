import {
	BadRequestException,
	ForbiddenException,
	Injectable,
	UnauthorizedException,
} from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import type { UserRow } from '../database/database.service';
import { AuthGrpcService } from './auth-grpc.service';
import { AuthSessionService } from './auth-session.service';
import { AuthUserLookupService } from './auth-user-lookup.service';
import { createAuthCode, hashSecret, normalizeEmail, safeSecretEquals } from './auth.util';
import { AuthVerificationEmailService } from './auth-verification-email.service';
import { PlayerBansService } from './player-bans.service';
import { signinFlows } from './signup-flow';

const EMAIL_CODE_TTL_MS = 10 * 60 * 1000;
const MAX_AUTH_CODE_ATTEMPTS = 5;

@Injectable()
export class AuthSigninService {
	constructor(
		private readonly grpc: AuthGrpcService,
		private readonly bans: PlayerBansService,
		private readonly sessions: AuthSessionService,
		private readonly userLookup: AuthUserLookupService,
		private readonly verificationEmails: AuthVerificationEmailService,
	) {}

	async start(emailInput: string, sourceIp: string) {
		const email = normalizeEmail(emailInput);
		const user = this.userLookup.byEmail(email);
		if (!user) throw new UnauthorizedException('No account exists for this email');
		const timeoutEnded = await this.requirePlayerNotBanned(user);

		const now = Date.now();
		this.cleanupExpiredFlows(now);
		this.verificationEmails.reserveSend(email, sourceIp, now);
		const code = createAuthCode();
		const flowId = randomUUID();
		for (const [activeFlowId, flow] of signinFlows)
			if (flow.userId === user.id) signinFlows.delete(activeFlowId);
		signinFlows.set(flowId, {
			userId: user.id,
			codeHash: hashSecret(code),
			expiresAtUnixMs: now + EMAIL_CODE_TTL_MS,
			failedAttempts: 0,
		});
		await this.verificationEmails.deliverCode(email, code, 'signin');
		return { flowId, timeoutEnded };
	}

	async verify(flowId: string, code: string) {
		const now = Date.now();
		const flow = signinFlows.get(flowId);
		if (!flow || flow.expiresAtUnixMs <= now) {
			signinFlows.delete(flowId);
			throw new BadRequestException('Email verification request is not active');
		}
		if (!safeSecretEquals(code, flow.codeHash)) {
			flow.failedAttempts++;
			if (flow.failedAttempts >= MAX_AUTH_CODE_ATTEMPTS) signinFlows.delete(flowId);
			throw new BadRequestException('Invalid email code');
		}
		const account = this.userLookup.byId(flow.userId);
		if (!account) throw new UnauthorizedException('No account exists for this email');
		await this.requirePlayerNotBanned(account);
		signinFlows.delete(flowId);
		return this.sessions.createForUser(flow.userId);
	}

	private cleanupExpiredFlows(now: number) {
		for (const [flowId, flow] of signinFlows)
			if (flow.expiresAtUnixMs <= now) signinFlows.delete(flowId);
	}

	private async requirePlayerNotBanned(user: UserRow) {
		const ban = this.bans.resolve(user.id);
		if (ban.active)
			throw new ForbiddenException(
				ban.expiresAtUnixMs === null
					? 'You are permanently banned from the MMU Minecraft Society server'
					: `Your timeout continues until ${new Date(ban.expiresAtUnixMs).toUTCString()}`,
			);
		if (ban.expired)
			await this.grpc
				.unblacklistPlayer(user.minecraft_username, user.minecraft_uuid ?? '')
				.catch(() => undefined);
		return ban.expired;
	}
}
