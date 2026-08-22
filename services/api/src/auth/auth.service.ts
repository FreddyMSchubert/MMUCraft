import {
	BadRequestException,
	ConflictException,
	ForbiddenException,
	HttpException,
	HttpStatus,
	Injectable,
	NotFoundException,
	UnauthorizedException,
} from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { and, asc, eq, gt, gte, isNull, lt } from 'drizzle-orm';
import {
	DatabaseService,
	UserRow,
	emailSendEvents,
	emailWhitelist,
	playerProfiles,
	sessions,
	users,
} from '../database/database.service';
import { normalizeMinecraftUuid } from '../database/minecraft-identity.service';
import { AuthGrpcService } from './auth-grpc.service';
import { PlayerBansService } from './player-bans.service';
import {
	AUTH_CODE_ITEMS,
	createAuthCode,
	createOpaqueToken,
	displayAuthCode,
	hashSecret,
	isAllowedEmail,
	isValidMinecraftUsername,
	isValidEmail,
	normalizeEmail,
	normalizeIpBucket,
	safeSecretEquals,
} from './auth.util';
import { SignupFlow, signinFlows, signupFlows } from './signup-flow';
import { ASSETS } from '../assets';
import { effectivePlayerColor } from '../players/player-color';

const EMAIL_CODE_TTL_MS = 10 * 60 * 1000;
const MINECRAFT_CODE_TTL_MS = 15 * 60 * 1000;
const SESSION_TTL_MS = 60 * 24 * 60 * 60 * 1000;
const SIGNUP_FLOW_IDLE_TTL_MS = 60 * 60 * 1000;
const SUPER_ADMIN_MINECRAFT_USERNAME = 'MerlinSpace';
const MAX_AUTH_CODE_ATTEMPTS = 5;
const FIVE_MINUTES_MS = 5 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const EMAIL_SEND_LIMITS = { fiveMinutes: 2, day: 8 };
const IP_SEND_LIMITS = { fiveMinutes: 10, day: 30 };
export const MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS = 150;
export const NON_MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS = 250;
const SIGNUP_ALLOWLIST_PATH = process.env.SIGNUP_ALLOWLIST_PATH ?? './data/signup-allowlist.txt';
const AUTH_CODE_IMAGE_BASE = `${ASSETS.minecraft.vanilla}/textures/`;
const AUTH_CODE_IMAGES: Partial<Record<(typeof AUTH_CODE_ITEMS)[number], string>> = {
	Apple: 'item/apple.png',
	Axe: 'item/golden_axe.png',
	Beetroot: 'item/beetroot.png',
	Coal: 'item/coal.png',
	Copper: 'item/raw_copper.png',
	Diamond: 'item/diamond.png',
	Egg: 'item/egg.png',
	Emerald: 'item/emerald.png',
	Fish: 'item/tropical_fish.png',
	'Flint and Steel': 'item/flint_and_steel.png',
	Flower: 'block/red_tulip.png',
	'Gold Ingot': 'item/gold_ingot.png',
	Iron: 'item/raw_iron.png',
	'Lapis Lazuli': 'item/lapis_lazuli.png',
	'Lava Bucket': 'item/lava_bucket.png',
	'Lily Pad': 'block/lily_pad.png',
	'Melon Slice': 'item/melon_slice.png',
	Mushroom: 'block/red_mushroom.png',
	'Music Disk': 'item/music_disc_cat.png',
	Netherite: 'item/netherite_scrap.png',
	Pickaxe: 'item/iron_pickaxe.png',
	Potato: 'item/potato.png',
	Potion: 'item/potion.png',
	Quartz: 'item/quartz.png',
	Redstone: 'item/redstone.png',
	Shovel: 'item/copper_shovel.png',
	Slimeball: 'item/slime_ball.png',
	Spear: 'item/diamond_spear.png',
	Sword: 'item/wooden_sword.png',
	Totem: 'item/totem_of_undying.png',
	Trident: 'item/trident.png',
	Wheat: 'item/wheat.png',
};

export interface AuthenticatedUser {
	id: number;
	minecraftUsername: string;
	color: string;
	isMember: boolean;
	isCommittee: boolean;
	isSuperAdmin: boolean;
	whitelisted: true;
	rulesAccepted: true;
}

@Injectable()
export class AuthService {
	constructor(
		private readonly database: DatabaseService,
		private readonly grpc: AuthGrpcService,
		private readonly bans: PlayerBansService,
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

		if (!isAllowedEmail(email) && !this.isEmailWhitelisted(email)) {
			throw new BadRequestException(
				'Use an @mmu.ac.uk address or a numeric @stu.mmu.ac.uk address',
			);
		}

		await this.cleanupStaleSignupFlows();

		if (this.findUserByEmail(email)) {
			throw new BadRequestException('An account with this email already exists');
		}

		const now = Date.now();
		this.reserveEmailSend(email, sourceIp, now);
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
		await this.deliverVerificationCode(email, code, 'signup');

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

		if (this.findUserByEmail(flow.email)) {
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

		const existingUser = this.findUserByMinecraftUsername(username);

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
		if (this.findUserByMinecraftUuid(minecraftUuid)) {
			throw new BadRequestException(
				'This Minecraft account is already linked to a website account',
			);
		}

		await this.grpc.removePendingJoin(flow.minecraftUsername);

		flow.step = 'rules';
		flow.updatedAt = now;
	}

	async acceptRules(flowId: string) {
		const flow = this.getFlow(flowId);
		const now = Date.now();

		if (flow.step !== 'rules') {
			throw new BadRequestException('This signup flow is not waiting for rules acceptance');
		}

		if (!flow.minecraftUsername) {
			throw new BadRequestException(
				'Minecraft username is not available for this signup flow',
			);
		}
		const minecraftUsername = flow.minecraftUsername;
		const minecraftUuid = normalizeMinecraftUuid(flow.minecraftUuid ?? '');
		if (!minecraftUuid) {
			throw new BadRequestException(
				'Minecraft identity is not available for this signup flow',
			);
		}

		if (this.findUserByEmail(flow.email)) {
			throw new BadRequestException('An account with this email already exists');
		}

		const existingMinecraftUser = this.findUserByMinecraftUsername(flow.minecraftUsername);

		if (existingMinecraftUser) {
			throw new BadRequestException('An account with this Minecraft username already exists');
		}
		if (this.findUserByMinecraftUuid(minecraftUuid)) {
			throw new BadRequestException(
				'This Minecraft account is already linked to a website account',
			);
		}
		const responsibleUserId = isAllowedEmail(flow.email)
			? null
			: this.database.connection
					.select({ responsibleUserId: emailWhitelist.responsible_user_id })
					.from(emailWhitelist)
					.where(eq(emailWhitelist.email, flow.email))
					.get()?.responsibleUserId;
		if (!isAllowedEmail(flow.email) && !responsibleUserId) {
			throw new ForbiddenException('This external player invitation is no longer active');
		}

		await this.grpc.whitelistPlayer(minecraftUsername);

		try {
			const userId = this.database.connection.transaction((tx) => {
				const created = tx
					.insert(users)
					.values({
						email: flow.email,
						minecraft_uuid: minecraftUuid,
						minecraft_username: minecraftUsername,
						responsible_user_id: responsibleUserId,
						is_committee: isSuperAdminUsername(minecraftUsername) ? 1 : 0,
						is_super_admin: isSuperAdminUsername(minecraftUsername) ? 1 : 0,
						whitelisted_at_unix_ms: now,
						rules_accepted_at_unix_ms: now,
						created_at_unix_ms: now,
					})
					.returning({ id: users.id })
					.get();

				return created.id;
			});
			signupFlows.delete(flowId);
			return this.createSession(userId);
		} catch (error) {
			await this.grpc.removePendingJoin(minecraftUsername).catch(() => undefined);
			throw error;
		}
	}

	async signIn(emailInput: string, sourceIp: string) {
		const email = normalizeEmail(emailInput);
		const user = this.findUserByEmail(email);

		if (!user) {
			throw new UnauthorizedException('No account exists for this email');
		}
		const timeoutEnded = await this.requirePlayerNotBanned(user);

		const now = Date.now();
		this.cleanupSigninFlows(now);
		this.reserveEmailSend(email, sourceIp, now);
		const code = createAuthCode();
		const flowId = randomUUID();
		for (const [activeFlowId, flow] of signinFlows) {
			if (flow.userId === user.id) signinFlows.delete(activeFlowId);
		}
		signinFlows.set(flowId, {
			userId: user.id,
			codeHash: hashSecret(code),
			expiresAtUnixMs: now + EMAIL_CODE_TTL_MS,
			failedAttempts: 0,
		});
		await this.deliverVerificationCode(email, code, 'signin');

		return { flowId, timeoutEnded };
	}

	async verifySignIn(flowId: string, code: string) {
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
		const user = this.database.connection
			.select()
			.from(users)
			.where(eq(users.id, flow.userId))
			.get();
		if (!user) throw new UnauthorizedException('No account exists for this email');
		await this.requirePlayerNotBanned(user);

		signinFlows.delete(flowId);
		return this.createSession(flow.userId);
	}

	listPlayerBans() {
		return this.bans.list();
	}

	async applyPlayerBan(
		admin: AuthenticatedUser,
		userIdInput: unknown,
		expiresAtUnixMsInput: unknown,
	) {
		if (typeof userIdInput !== 'number' || !Number.isInteger(userIdInput) || userIdInput <= 0) {
			throw new BadRequestException('Select a player');
		}
		if (
			expiresAtUnixMsInput !== null &&
			(typeof expiresAtUnixMsInput !== 'number' ||
				!Number.isSafeInteger(expiresAtUnixMsInput) ||
				expiresAtUnixMsInput <= Date.now())
		) {
			throw new BadRequestException('Select a timeout date and time in the future');
		}

		const target = this.database.connection
			.select()
			.from(users)
			.where(eq(users.id, userIdInput))
			.get();
		if (!target) throw new NotFoundException('Player not found');
		if (target.id === admin.id)
			throw new BadRequestException('You cannot ban your own account');
		if (target.is_super_admin === 1)
			throw new BadRequestException('The permanent super-admin cannot be banned');

		this.bans.set(target.id, admin.id, expiresAtUnixMsInput);

		let minecraftSynchronized = true;
		try {
			await this.grpc.blacklistPlayer(target.minecraft_username, target.minecraft_uuid ?? '');
		} catch {
			minecraftSynchronized = false;
		}

		return {
			ok: true,
			userId: target.id,
			minecraftUsername: target.minecraft_username,
			expiresAtUnixMs: expiresAtUnixMsInput,
			minecraftSynchronized,
		};
	}

	async removePlayerBan(userIdInput: string) {
		const userId = Number(userIdInput);
		if (!Number.isInteger(userId) || userId <= 0)
			throw new NotFoundException('Player not found');
		const target = this.database.connection
			.select()
			.from(users)
			.where(eq(users.id, userId))
			.get();
		if (!target || !this.bans.remove(userId))
			throw new NotFoundException('Active ban not found');

		let minecraftSynchronized = true;
		try {
			await this.grpc.unblacklistPlayer(
				target.minecraft_username,
				target.minecraft_uuid ?? '',
			);
		} catch {
			minecraftSynchronized = false;
		}

		return {
			ok: true,
			userId,
			minecraftUsername: target.minecraft_username,
			minecraftSynchronized,
		};
	}

	listEmailWhitelist() {
		const profilesById = new Map(
			this.database.connection
				.select()
				.from(playerProfiles)
				.all()
				.map((profile) => [profile.user_id, profile]),
		);
		const usernamesById = new Map(
			this.database.connection
				.select({
					id: users.id,
					minecraftUsername: users.minecraft_username,
					minecraftUuid: users.minecraft_uuid,
				})
				.from(users)
				.all()
				.map((user) => [
					user.id,
					{
						name: user.minecraftUsername,
						color: effectivePlayerColor(
							user.minecraftUuid,
							profilesById.get(user.id)?.color_hex,
						),
					},
				]),
		);

		return {
			entries: this.database.connection
				.select()
				.from(emailWhitelist)
				.orderBy(asc(emailWhitelist.email))
				.all()
				.map((entry) => ({
					email: entry.email,
					addedByMinecraftUsername:
						usernamesById.get(entry.added_by_user_id)?.name ?? 'Unknown user',
					addedByColor: usernamesById.get(entry.added_by_user_id)?.color ?? '#E6E6E6',
					responsibleMinecraftUsername:
						entry.responsible_user_id === null
							? null
							: (usernamesById.get(entry.responsible_user_id)?.name ??
								'Unknown user'),
					responsiblePlayerColor:
						entry.responsible_user_id === null
							? null
							: (usernamesById.get(entry.responsible_user_id)?.color ?? '#E6E6E6'),
					createdAtUnixMs: entry.created_at_unix_ms,
				})),
		};
	}

	async addEmailToWhitelist(
		admin: AuthenticatedUser,
		emailInput: unknown,
		responsibleUserIdInput: unknown,
	) {
		if (typeof emailInput !== 'string') {
			throw new BadRequestException('Email is required');
		}
		const email = normalizeEmail(emailInput);
		if (!isValidEmail(email)) {
			throw new BadRequestException('Enter a valid email address');
		}
		if (isAllowedEmail(email)) {
			throw new BadRequestException('MMU email addresses are already allowed');
		}
		if (
			typeof responsibleUserIdInput !== 'number' ||
			!Number.isInteger(responsibleUserIdInput) ||
			responsibleUserIdInput <= 0
		) {
			throw new BadRequestException('Select a responsible user');
		}
		const responsibleUserId = responsibleUserIdInput;
		const responsibleUser = this.database.connection
			.select()
			.from(users)
			.where(and(eq(users.id, responsibleUserId), isNull(users.responsible_user_id)))
			.get();
		if (!responsibleUser) {
			throw new BadRequestException(
				'External players cannot be responsible for another external player',
			);
		}

		const result = this.database.connection
			.insert(emailWhitelist)
			.values({
				email,
				added_by_user_id: admin.id,
				responsible_user_id: responsibleUserId,
				created_at_unix_ms: Date.now(),
			})
			.onConflictDoNothing()
			.run();
		if (result.changes !== 1) {
			throw new ConflictException('That email address is already whitelisted');
		}

		try {
			const priceDabloons =
				responsibleUser.is_member === 1
					? MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS
					: NON_MEMBER_EXTERNAL_PLAYER_INVITE_PRICE_DABLOONS;
			const purchase = await this.grpc.purchaseExternalPlayerInvite(
				responsibleUser.minecraft_username,
			);
			if (!purchase.purchased) {
				throw new BadRequestException(
					purchase.message ||
						`The responsible player must be online with ${priceDabloons} dabloons`,
				);
			}
			return {
				email,
				priceDabloons,
				balanceDabloons: purchase.balance_dabloons,
			};
		} catch (error) {
			this.database.connection
				.delete(emailWhitelist)
				.where(eq(emailWhitelist.email, email))
				.run();
			if (error instanceof BadRequestException) throw error;
			throw new BadRequestException(
				'The responsible player must be online to pay for this invitation',
			);
		}
	}

	removeEmailFromWhitelist(emailInput: string) {
		const email = normalizeEmail(emailInput);
		const result = this.database.connection
			.delete(emailWhitelist)
			.where(eq(emailWhitelist.email, email))
			.run();
		if (result.changes !== 1) {
			throw new NotFoundException('Whitelisted email address not found');
		}
		return { ok: true };
	}

	requireSession(rawCookieHeader: string | undefined): AuthenticatedUser {
		const user = this.getSession(rawCookieHeader);

		if (!user) {
			throw new UnauthorizedException('Not signed in');
		}

		return user;
	}

	requireCommitteeSession(rawCookieHeader: string | undefined): AuthenticatedUser {
		const user = this.requireSession(rawCookieHeader);

		if (!user.isCommittee) {
			throw new ForbiddenException('Committee access is required');
		}

		return user;
	}

	requireSuperAdminSession(rawCookieHeader: string | undefined): AuthenticatedUser {
		const user = this.requireSession(rawCookieHeader);

		if (!user.isSuperAdmin) {
			throw new ForbiddenException('Super-admin access is required');
		}

		return user;
	}
	getSession(rawCookieHeader: string | undefined): AuthenticatedUser | null {
		const token = this.readCookie(rawCookieHeader, 'mcstack_session');
		if (!token) return null;

		const tokenHash = hashSecret(token);
		const now = Date.now();

		const row = this.database.connection
			.select({ user: users })
			.from(sessions)
			.innerJoin(users, eq(users.id, sessions.user_id))
			.where(and(eq(sessions.token_hash, tokenHash), gt(sessions.expires_at_unix_ms, now)))
			.get()?.user;

		if (!row) return null;

		const isSuperAdmin = row.is_super_admin === 1;
		const profile = this.database.connection
			.select()
			.from(playerProfiles)
			.where(eq(playerProfiles.user_id, row.id))
			.get();
		return {
			id: row.id,
			minecraftUsername: row.minecraft_username,
			color: effectivePlayerColor(row.minecraft_uuid, profile?.color_hex),
			isMember: row.is_member === 1,
			isCommittee: isSuperAdmin || row.is_committee === 1,
			isSuperAdmin,
			whitelisted: true,
			rulesAccepted: true,
		};
	}

	deleteSession(rawCookieHeader: string | undefined) {
		const token = this.readCookie(rawCookieHeader, 'mcstack_session');
		if (!token) return;

		this.database.connection
			.delete(sessions)
			.where(eq(sessions.token_hash, hashSecret(token)))
			.run();
	}

	private getFlow(flowId: string): SignupFlow {
		const flow = signupFlows.get(flowId);

		if (!flow) {
			throw new BadRequestException('Signup flow not found');
		}

		return flow;
	}

	private findUserByEmail(email: string): UserRow | null {
		return (
			this.database.connection.select().from(users).where(eq(users.email, email)).get() ??
			null
		);
	}

	private isEmailWhitelisted(email: string): boolean {
		return (
			this.database.connection
				.select({ email: emailWhitelist.email })
				.from(emailWhitelist)
				.where(eq(emailWhitelist.email, email))
				.get() !== undefined
		);
	}

	private findUserByMinecraftUsername(minecraftUsername: string): UserRow | null {
		return (
			this.database.connection
				.select()
				.from(users)
				.all()
				.find(
					(user) =>
						user.minecraft_username.localeCompare(minecraftUsername, 'en', {
							sensitivity: 'base',
						}) === 0,
				) ?? null
		);
	}

	private findUserByMinecraftUuid(minecraftUuid: string): UserRow | null {
		return (
			this.database.connection
				.select()
				.from(users)
				.where(eq(users.minecraft_uuid, minecraftUuid))
				.get() ?? null
		);
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

	private reserveEmailSend(email: string, sourceIp: string, now: number) {
		const emailHash = hashSecret(`email:${email}`);
		const ipHash = hashSecret(`ip:${normalizeIpBucket(sourceIp)}`);
		const dayCutoff = now - DAY_MS;

		this.database.connection.transaction((tx) => {
			tx.delete(emailSendEvents).where(lt(emailSendEvents.sent_at_unix_ms, dayCutoff)).run();
			const emailEvents = tx
				.select({ sentAt: emailSendEvents.sent_at_unix_ms })
				.from(emailSendEvents)
				.where(
					and(
						eq(emailSendEvents.email_hash, emailHash),
						gte(emailSendEvents.sent_at_unix_ms, dayCutoff),
					),
				)
				.all();
			const ipEvents = tx
				.select({ sentAt: emailSendEvents.sent_at_unix_ms })
				.from(emailSendEvents)
				.where(
					and(
						eq(emailSendEvents.ip_hash, ipHash),
						gte(emailSendEvents.sent_at_unix_ms, dayCutoff),
					),
				)
				.all();
			const emailFiveMinuteRetry = retryAtForLimit(
				emailEvents,
				EMAIL_SEND_LIMITS.fiveMinutes,
				FIVE_MINUTES_MS,
				now,
			);
			const emailDayRetry = retryAtForLimit(emailEvents, EMAIL_SEND_LIMITS.day, DAY_MS, now);
			const ipFiveMinuteRetry = retryAtForLimit(
				ipEvents,
				IP_SEND_LIMITS.fiveMinutes,
				FIVE_MINUTES_MS,
				now,
			);
			const ipDayRetry = retryAtForLimit(ipEvents, IP_SEND_LIMITS.day, DAY_MS, now);
			const emailRetries = [emailFiveMinuteRetry, emailDayRetry].filter(
				(retry): retry is number => retry !== null,
			);
			const ipRetries = [ipFiveMinuteRetry, ipDayRetry].filter(
				(retry): retry is number => retry !== null,
			);
			const tooManyEmails = emailRetries.length > 0;
			const tooManyFromIp = ipRetries.length > 0;

			if (tooManyEmails || tooManyFromIp) {
				const retryAt = Math.max(...emailRetries, ...ipRetries);
				const retryAfterSeconds = Math.max(1, Math.ceil((retryAt - now) / 1000));
				throw new HttpException(
					{
						statusCode: HttpStatus.TOO_MANY_REQUESTS,
						error: 'Too Many Requests',
						message: emailRateLimitMessage({
							emailFiveMinutes: emailFiveMinuteRetry !== null,
							emailDay: emailDayRetry !== null,
							ipFiveMinutes: ipFiveMinuteRetry !== null,
							ipDay: ipDayRetry !== null,
							retryAfterSeconds,
						}),
						rateLimit:
							tooManyEmails && tooManyFromIp
								? 'email-and-network'
								: tooManyEmails
									? 'email'
									: 'network',
						retryAfterSeconds,
					},
					HttpStatus.TOO_MANY_REQUESTS,
				);
			}

			tx.insert(emailSendEvents)
				.values({
					id: randomUUID(),
					email_hash: emailHash,
					ip_hash: ipHash,
					sent_at_unix_ms: now,
				})
				.run();
		});
	}

	private cleanupSigninFlows(now: number) {
		for (const [flowId, flow] of signinFlows) {
			if (flow.expiresAtUnixMs <= now) signinFlows.delete(flowId);
		}
	}

	private async deliverVerificationCode(email: string, code: string, kind: 'signup' | 'signin') {
		const apiKey = process.env.RESEND_API_KEY;
		const from = process.env.RESEND_FROM ?? 'MMU Minecraft Society <onboarding@resend.dev>';
		const recipientDomain = email.split('@')[1] ?? 'invalid';
		if (!apiKey) {
			console.warn('[auth-email] Delivery skipped', {
				kind,
				recipientDomain,
				reason: 'RESEND_API_KEY is missing from the API process environment',
			});
			return;
		}

		try {
			const response = await fetch('https://api.resend.com/emails', {
				method: 'POST',
				signal: AbortSignal.timeout(10_000),
				headers: {
					Authorization: `Bearer ${apiKey}`,
					'Content-Type': 'application/json',
				},
				body: JSON.stringify({
					from,
					to: [email],
					subject: `Your MMU Minecraft Society ${kind === 'signup' ? 'signup' : 'signin'} code`,
					text: `Your verification code is ${displayAuthCode(code)}. It expires in 10 minutes. If you did not request this, you can ignore this email.`,
					html: verificationCodeEmailHtml(code),
				}),
			});

			if (response.ok) {
				console.info('[auth-email] Resend accepted verification email', {
					kind,
					recipientDomain,
					status: response.status,
				});
				return;
			}
			console.error('[auth-email] Resend rejected verification email', {
				kind,
				recipientDomain,
				from,
				status: response.status,
				response: await response.text(),
			});
		} catch (error) {
			console.error('[auth-email] Resend request failed', {
				kind,
				recipientDomain,
				from,
				error: error instanceof Error ? `${error.name}: ${error.message}` : String(error),
			});
		}
	}

	private createSession(userId: number) {
		const now = Date.now();
		const token = createOpaqueToken();
		const sessionId = randomUUID();

		this.database.connection
			.insert(sessions)
			.values({
				id: sessionId,
				user_id: userId,
				token_hash: hashSecret(token),
				expires_at_unix_ms: now + SESSION_TTL_MS,
				created_at_unix_ms: now,
			})
			.run();

		return {
			token,
			maxAgeSeconds: Math.floor(SESSION_TTL_MS / 1000),
		};
	}

	private readCookie(rawCookieHeader: string | undefined, name: string): string | null {
		if (!rawCookieHeader) return null;

		const cookies = rawCookieHeader.split(';').map((cookie) => cookie.trim());
		const match = cookies.find((cookie) => cookie.startsWith(`${name}=`));

		if (!match) return null;

		return decodeURIComponent(match.slice(name.length + 1));
	}

	private async requirePlayerNotBanned(user: UserRow) {
		const ban = this.bans.resolve(user.id);
		if (ban.active) {
			const message =
				ban.expiresAtUnixMs === null
					? 'You are permanently banned from the MMU Minecraft Society server'
					: `Your timeout continues until ${new Date(ban.expiresAtUnixMs).toUTCString()}`;
			throw new ForbiddenException(message);
		}

		if (ban.expired) {
			await this.grpc
				.unblacklistPlayer(user.minecraft_username, user.minecraft_uuid ?? '')
				.catch(() => undefined);
		}
		return ban.expired;
	}
}

function verificationCodeEmailHtml(code: string) {
	const items = code
		.split('|')
		.map((item) => {
			const image = AUTH_CODE_IMAGES[item as keyof typeof AUTH_CODE_IMAGES];
			return `<td style="padding: 8px; text-align: center; font-weight: bold">${image ? `<img src="${AUTH_CODE_IMAGE_BASE}${image}" alt="" width="40" height="40" style="display: block; margin: 0 auto 4px; image-rendering: pixelated; object-fit: contain">` : ''}${item}</td>`;
		})
		.join('');

	return `<p>Your verification code is:</p><table role="presentation"><tr>${items}</tr></table><p>It expires in 10 minutes. If you did not request this, you can ignore this email.</p>`;
}

function isSuperAdminUsername(minecraftUsername: string) {
	return (
		minecraftUsername.localeCompare(SUPER_ADMIN_MINECRAFT_USERNAME, 'en', {
			sensitivity: 'base',
		}) === 0
	);
}

function retryAtForLimit(
	events: { sentAt: number }[],
	limit: number,
	windowMs: number,
	now: number,
) {
	const eventsInWindow = events.filter((event) => event.sentAt >= now - windowMs);
	if (eventsInWindow.length < limit) return null;
	return Math.min(...eventsInWindow.map((event) => event.sentAt)) + windowMs + 1;
}

function emailRateLimitMessage(limits: {
	emailFiveMinutes: boolean;
	emailDay: boolean;
	ipFiveMinutes: boolean;
	ipDay: boolean;
	retryAfterSeconds: number;
}) {
	const emailLimits = [
		limits.emailFiveMinutes ? '2 emails in 5 minutes' : '',
		limits.emailDay ? '8 emails in 24 hours' : '',
	].filter(Boolean);
	const networkLimits = [
		limits.ipFiveMinutes ? '10 emails in 5 minutes' : '',
		limits.ipDay ? '30 emails in 24 hours' : '',
	].filter(Boolean);
	const retry = `Try again in ${formatDuration(limits.retryAfterSeconds)}.`;

	if (emailLimits.length && networkLimits.length) {
		return `Both limits were reached: this email address reached ${emailLimits.join(' and ')}, and this network reached ${networkLimits.join(' and ')}. ${retry} A different network will not clear the email-address limit. If you still need help, contact the committee.`;
	}
	if (emailLimits.length) {
		return `Email-address limit reached: this address reached ${emailLimits.join(' and ')}. ${retry} If you did not make these requests or still need help, contact the committee.`;
	}
	return `Network limit reached: this network reached ${networkLimits.join(' and ')}. ${retry} This can happen on shared university, accommodation, workplace, or public Wi-Fi. You can wait or try a different trusted connection, such as mobile data. If you still need help, contact the committee.`;
}

function formatDuration(totalSeconds: number) {
	const hours = Math.floor(totalSeconds / 3600);
	const minutes = Math.floor((totalSeconds % 3600) / 60);
	const seconds = totalSeconds % 60;
	return [
		hours ? `${hours} hour${hours === 1 ? '' : 's'}` : '',
		minutes ? `${minutes} minute${minutes === 1 ? '' : 's'}` : '',
		!hours && seconds ? `${seconds} second${seconds === 1 ? '' : 's'}` : '',
	]
		.filter(Boolean)
		.join(' ');
}
