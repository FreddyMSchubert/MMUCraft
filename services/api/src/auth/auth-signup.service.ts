import { BadRequestException, ForbiddenException, Injectable } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { normalizeMinecraftUuid } from '../database/minecraft-identity.service';
import { AuthGrpcService } from './auth-grpc.service';
import {
	createAuthCode,
	hashSecret,
	isAllowedEmail,
	isValidMinecraftUsername,
	normalizeEmail,
	safeSecretEquals,
} from './auth.util';
import { SignupFlow, signupFlows } from './signup-flow';
import { AuthVerificationEmailService } from './auth-verification-email.service';
import { AuthUserLookupService } from './auth-user-lookup.service';
import { AuthSignupAccountRegistrationService } from './auth-signup-account-registration.service';

const EMAIL_CODE_TTL_MS = 10 * 60 * 1000;
const MINECRAFT_CODE_TTL_MS = 15 * 60 * 1000;
const SIGNUP_FLOW_IDLE_TTL_MS = 60 * 60 * 1000;
const MAX_AUTH_CODE_ATTEMPTS = 5;
const SIGNUP_ALLOWLIST_PATH = process.env.SIGNUP_ALLOWLIST_PATH ?? './data/signup-allowlist.txt';

@Injectable()
export class AuthSignupService {
	constructor(
		private readonly grpc: AuthGrpcService,
		private readonly userLookup: AuthUserLookupService,
		private readonly verificationEmails: AuthVerificationEmailService,
		private readonly accountRegistration: AuthSignupAccountRegistrationService,
	) {}

	async createSignup(emailInput: string, sourceIp: string) {
		const email = normalizeEmail(emailInput);
		let signupAllowlist = new Set<string>();
		try {
			signupAllowlist = new Set(
				readFileSync(SIGNUP_ALLOWLIST_PATH, 'utf8')
					.split(/\r?\n/)
					.map(normalizeEmail)
					.filter(Boolean),
			);
		} catch {
			/* A missing or unreadable allowlist closes signup. */
		}

		if (!signupAllowlist.has('*') && !signupAllowlist.has(email)) {
			throw new ForbiddenException('Signups are not currently open for this email');
		}

		if (!isAllowedEmail(email) && !this.userLookup.isEmailWhitelisted(email)) {
			throw new BadRequestException(
				'Use an @mmu.ac.uk address or a numeric @stu.mmu.ac.uk address',
			);
		}

		await this.cleanupStaleSignupFlows();

		if (this.userLookup.byEmail(email)) {
			throw new BadRequestException('An account with this email already exists');
		}

		const now = Date.now();
		this.verificationEmails.reserveSend(email, sourceIp, now);
		await this.deleteIncompleteSignupFlowsForEmail(email);
		const code = createAuthCode();
		const flowId = randomUUID();

		signupFlows.set(flowId, {
			email,
			step: 'email',
			emailCodeHash: hashSecret(code),
			emailCodeExpiresAt: now + EMAIL_CODE_TTL_MS,
			updatedAt: now,
		});
		await this.verificationEmails.deliverCode(email, code, 'signup');

		return { flowId };
	}

	verifyEmailCode(flowId: string, code: string) {
		const flow = this.getFlow(flowId);
		const now = Date.now();

		if (flow.step !== 'email') {
			throw new BadRequestException('This signup flow is not waiting for email verification');
		}

		if (flow.emailCodeExpiresAt <= now) {
			throw new BadRequestException('Email code expired');
		}

		if (!safeSecretEquals(code, flow.emailCodeHash)) {
			flow.emailCodeFailedAttempts = (flow.emailCodeFailedAttempts ?? 0) + 1;
			if (flow.emailCodeFailedAttempts >= MAX_AUTH_CODE_ATTEMPTS)
				flow.emailCodeExpiresAt = now;
			throw new BadRequestException('Invalid email code');
		}

		if (this.userLookup.byEmail(flow.email)) {
			throw new BadRequestException('An account with this email already exists');
		}

		flow.step = 'minecraft-username';
		flow.updatedAt = now;

		return { ok: true };
	}

	async setMinecraftUsername(flowId: string, usernameInput: string) {
		await this.cleanupStaleSignupFlows();

		const flow = this.getFlow(flowId);
		const username = usernameInput.trim();
		const now = Date.now();

		if (
			flow.step === 'minecraft-code' &&
			flow.minecraftUsername?.localeCompare(username, 'en', { sensitivity: 'base' }) === 0
		) {
			return { ok: true };
		}

		if (flow.step !== 'minecraft-username') {
			throw new BadRequestException(
				'This signup flow is not waiting for a Minecraft username',
			);
		}

		if (!isValidMinecraftUsername(username)) {
			throw new BadRequestException(
				'Minecraft username must be 3-16 characters and only use letters, numbers, and underscores',
			);
		}

		const existingUser = this.userLookup.byMinecraftUsername(username);

		if (existingUser) {
			throw new BadRequestException('An account with this Minecraft username already exists');
		}

		const existingActiveFlow = [...signupFlows].find(
			([candidateId, candidate]) =>
				candidateId !== flowId &&
				candidate.minecraftUsername?.localeCompare(username, 'en', {
					sensitivity: 'base',
				}) === 0,
		);

		if (existingActiveFlow) {
			throw new BadRequestException(
				'This Minecraft username is already being used in another signup flow',
			);
		}

		const minecraftCode = createAuthCode();
		const expiresAt = now + MINECRAFT_CODE_TTL_MS;

		flow.step = 'minecraft-code';
		flow.minecraftUsername = username;
		flow.minecraftUuid = undefined;
		flow.minecraftCodeHash = hashSecret(minecraftCode);
		flow.minecraftCodeExpiresAt = expiresAt;
		flow.minecraftCodeFailedAttempts = 0;
		flow.updatedAt = now;

		await this.grpc.upsertPendingJoin({
			minecraftUsername: username,
			code: minecraftCode,
			expiresAtUnixMs: expiresAt,
		});

		return { ok: true };
	}

	async verifyMinecraftCode(flowId: string, code: string) {
		const flow = this.getFlow(flowId);
		const now = Date.now();

		if (flow.step !== 'minecraft-code') {
			throw new BadRequestException(
				'This signup flow is not waiting for a Minecraft join code',
			);
		}

		if (!flow.minecraftUsername || !flow.minecraftCodeHash || !flow.minecraftCodeExpiresAt) {
			throw new BadRequestException('Minecraft code is not available for this signup flow');
		}

		if (flow.minecraftCodeExpiresAt <= now) {
			throw new BadRequestException('Minecraft code expired');
		}

		if (!safeSecretEquals(code.trim(), flow.minecraftCodeHash)) {
			flow.minecraftCodeFailedAttempts = (flow.minecraftCodeFailedAttempts ?? 0) + 1;
			if (flow.minecraftCodeFailedAttempts >= MAX_AUTH_CODE_ATTEMPTS) {
				flow.minecraftCodeExpiresAt = now;
				await this.grpc.removePendingJoin(flow.minecraftUsername).catch(() => undefined);
			}
			throw new BadRequestException('Invalid Minecraft code');
		}

		const minecraftUuid = normalizeMinecraftUuid(flow.minecraftUuid ?? '');
		if (!minecraftUuid) {
			throw new BadRequestException(
				'Join the Minecraft server once before verifying this code',
			);
		}
		if (this.userLookup.byMinecraftUuid(minecraftUuid)) {
			throw new BadRequestException(
				'This Minecraft account is already linked to a website account',
			);
		}

		await this.grpc.removePendingJoin(flow.minecraftUsername);

		flow.step = 'rules';
		flow.updatedAt = now;
	}

	async acceptRules(flowId: string) {
		return this.accountRegistration.register(flowId, this.getFlow(flowId));
	}

	private getFlow(flowId: string): SignupFlow {
		const flow = signupFlows.get(flowId);

		if (!flow) {
			throw new BadRequestException('Signup flow not found');
		}

		return flow;
	}

	private async deleteIncompleteSignupFlowsForEmail(email: string) {
		for (const [flowId, flow] of signupFlows) {
			if (flow.email !== email) continue;
			if (flow.minecraftUsername) {
				await this.grpc.removePendingJoin(flow.minecraftUsername).catch(() => undefined);
			}
			signupFlows.delete(flowId);
		}
	}

	private async cleanupStaleSignupFlows() {
		const cutoff = Date.now() - SIGNUP_FLOW_IDLE_TTL_MS;

		for (const [flowId, flow] of signupFlows) {
			if (flow.updatedAt >= cutoff) continue;
			if (flow.minecraftUsername) {
				await this.grpc.removePendingJoin(flow.minecraftUsername).catch(() => undefined);
			}
			signupFlows.delete(flowId);
		}
	}
}
