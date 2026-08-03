import { BadRequestException, ConflictException, ForbiddenException, Injectable, NotFoundException, UnauthorizedException } from '@nestjs/common'
import { randomUUID } from 'node:crypto'
import { and, asc, desc, eq, gt, isNull, lte } from 'drizzle-orm'
import {
	AuthRequestRow,
	DatabaseService,
	UserRow,
	authRequests,
	emailWhitelist,
	sessions,
	users,
} from '../database/database.service'
import { normalizeMinecraftUuid } from '../database/minecraft-identity.service'
import { AuthGrpcService } from './auth-grpc.service'
import {
	AUTH_CODE_ITEMS,
	createAuthCode,
	createOpaqueToken,
	displayAuthCode,
	hashSecret,
	isAllowedEmail,
	isAuthRequestActive,
	isValidMinecraftUsername,
	isValidEmail,
	normalizeEmail,
	safeSecretEquals,
} from './auth.util'
import { SignupFlow, signupFlows } from './signup-flow'
import { ASSETS } from '../assets'

const EMAIL_CODE_TTL_MS = 10 * 60 * 1000
const MINECRAFT_CODE_TTL_MS = 15 * 60 * 1000
const SESSION_TTL_MS = 60 * 24 * 60 * 60 * 1000
const SIGNUP_FLOW_IDLE_TTL_MS = 60 * 60 * 1000
const SUPER_ADMIN_MINECRAFT_USERNAME = 'MerlinSpace'
const AUTH_REQUEST_HISTORY_LIMIT = 50
const MAX_AUTH_CODE_ATTEMPTS = 5
const AUTH_CODE_IMAGE_BASE = `${ASSETS.minecraft.vanilla}/textures/`
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
}

export interface AuthenticatedUser {
	id: number
	minecraftUsername: string
	isMember: boolean
	isCommittee: boolean
	isSuperAdmin: boolean
	whitelisted: true
	rulesAccepted: true
}

@Injectable()
export class AuthService {
	constructor(
		private readonly database: DatabaseService,
		private readonly grpc: AuthGrpcService,
	) { }

	async createSignup(emailInput: string) {
		const email = normalizeEmail(emailInput)

		if (!isAllowedEmail(email) && !this.isEmailWhitelisted(email)) {
			throw new BadRequestException('Use an @mmu.ac.uk address or a numeric @stu.mmu.ac.uk address')
		}

		await this.cleanupStaleSignupFlows()

		if (this.findUserByEmail(email)) {
			throw new BadRequestException('An account with this email already exists')
		}

		await this.deleteIncompleteSignupFlowsForEmail(email)

		const now = Date.now()
		this.expireActiveAuthRequests(email, 'signup', now)
		const code = createAuthCode()
		const flowId = randomUUID()

		signupFlows.set(flowId, {
			email,
			step: 'email',
			emailCodeHash: hashSecret(code),
			emailCodeExpiresAt: now + EMAIL_CODE_TTL_MS,
			updatedAt: now,
		})
		this.createAuthRequest(flowId, 'signup', email, null, code, now)
		const delivery = await this.deliverVerificationCode(email, code, 'signup')
		this.setAuthRequestDelivery(flowId, delivery)

		return {
			flowId,
			delivery,
		}
	}

	verifyEmailCode(flowId: string, code: string) {
		const flow = this.getFlow(flowId)
		const now = Date.now()

		if (flow.step !== 'email') {
			throw new BadRequestException('This signup flow is not waiting for email verification')
		}

		if (flow.emailCodeExpiresAt <= now) {
			throw new BadRequestException('Email code expired')
		}

		const request = this.getActiveAuthRequest(flowId, 'signup', now)
		if (!safeSecretEquals(code, flow.emailCodeHash) || !safeSecretEquals(code, request.code_hash)) {
			this.recordFailedAuthAttempt(request)
			throw new BadRequestException('Invalid email code')
		}

		if (this.findUserByEmail(flow.email)) {
			throw new BadRequestException('An account with this email already exists')
		}

		flow.step = 'minecraft-username'
		flow.updatedAt = now
		this.completeAuthRequest(flowId, now)

		return { ok: true }
	}

	async setMinecraftUsername(flowId: string, usernameInput: string) {
		await this.cleanupStaleSignupFlows()

		const flow = this.getFlow(flowId)
		const username = usernameInput.trim()
		const now = Date.now()

		if (flow.step === 'minecraft-code'
			&& flow.minecraftUsername?.localeCompare(username, 'en', { sensitivity: 'base' }) === 0) {
			return { ok: true }
		}

		if (flow.step !== 'minecraft-username') {
			throw new BadRequestException('This signup flow is not waiting for a Minecraft username')
		}

		if (!isValidMinecraftUsername(username)) {
			throw new BadRequestException('Minecraft username must be 3-16 characters and only use letters, numbers, and underscores')
		}

		const existingUser = this.findUserByMinecraftUsername(username)

		if (existingUser) {
			throw new BadRequestException('An account with this Minecraft username already exists')
		}

		const existingActiveFlow = [...signupFlows].find(([candidateId, candidate]) => (
			candidateId !== flowId
			&& candidate.minecraftUsername?.localeCompare(username, 'en', { sensitivity: 'base' }) === 0
		))

		if (existingActiveFlow) {
			throw new BadRequestException('This Minecraft username is already being used in another signup flow')
		}

		const minecraftCode = createAuthCode()
		const expiresAt = now + MINECRAFT_CODE_TTL_MS

		flow.step = 'minecraft-code'
		flow.minecraftUsername = username
		flow.minecraftUuid = undefined
		flow.minecraftCodeHash = hashSecret(minecraftCode)
		flow.minecraftCodeExpiresAt = expiresAt
		flow.minecraftCodeFailedAttempts = 0
		flow.updatedAt = now

		await this.grpc.upsertPendingJoin({
			minecraftUsername: username,
			code: minecraftCode,
			expiresAtUnixMs: expiresAt,
		})

		return { ok: true }
	}

	async verifyMinecraftCode(flowId: string, code: string) {
		const flow = this.getFlow(flowId)
		const now = Date.now()

		if (flow.step !== 'minecraft-code') {
			throw new BadRequestException('This signup flow is not waiting for a Minecraft join code')
		}

		if (!flow.minecraftUsername || !flow.minecraftCodeHash || !flow.minecraftCodeExpiresAt) {
			throw new BadRequestException('Minecraft code is not available for this signup flow')
		}

		if (flow.minecraftCodeExpiresAt <= now) {
			throw new BadRequestException('Minecraft code expired')
		}

		if (!safeSecretEquals(code.trim(), flow.minecraftCodeHash)) {
			flow.minecraftCodeFailedAttempts = (flow.minecraftCodeFailedAttempts ?? 0) + 1
			if (flow.minecraftCodeFailedAttempts >= MAX_AUTH_CODE_ATTEMPTS) {
				flow.minecraftCodeExpiresAt = now
				await this.grpc.removePendingJoin(flow.minecraftUsername).catch(() => undefined)
			}
			throw new BadRequestException('Invalid Minecraft code')
		}

		const minecraftUuid = normalizeMinecraftUuid(flow.minecraftUuid ?? '')
		if (!minecraftUuid) {
			throw new BadRequestException('Join the Minecraft server once before verifying this code')
		}
		if (this.findUserByMinecraftUuid(minecraftUuid)) {
			throw new BadRequestException('This Minecraft account is already linked to a website account')
		}

		await this.grpc.removePendingJoin(flow.minecraftUsername)

		flow.step = 'rules'
		flow.updatedAt = now
	}

	async acceptRules(flowId: string) {
		const flow = this.getFlow(flowId)
		const now = Date.now()

		if (flow.step !== 'rules') {
			throw new BadRequestException('This signup flow is not waiting for rules acceptance')
		}

		if (!flow.minecraftUsername) {
			throw new BadRequestException('Minecraft username is not available for this signup flow')
		}
		const minecraftUsername = flow.minecraftUsername
		const minecraftUuid = normalizeMinecraftUuid(flow.minecraftUuid ?? '')
		if (!minecraftUuid) {
			throw new BadRequestException('Minecraft identity is not available for this signup flow')
		}

		if (this.findUserByEmail(flow.email)) {
			throw new BadRequestException('An account with this email already exists')
		}

		const existingMinecraftUser = this.findUserByMinecraftUsername(flow.minecraftUsername)

		if (existingMinecraftUser) {
			throw new BadRequestException('An account with this Minecraft username already exists')
		}
		if (this.findUserByMinecraftUuid(minecraftUuid)) {
			throw new BadRequestException('This Minecraft account is already linked to a website account')
		}

		await this.grpc.whitelistPlayer(minecraftUsername)

		try {
			const userId = this.database.connection.transaction((tx) => {
				const created = tx.insert(users).values({
					email: flow.email,
					minecraft_uuid: minecraftUuid,
					minecraft_username: minecraftUsername,
					is_committee: isSuperAdminUsername(minecraftUsername) ? 1 : 0,
					is_super_admin: isSuperAdminUsername(minecraftUsername) ? 1 : 0,
					whitelisted_at_unix_ms: now,
					rules_accepted_at_unix_ms: now,
					created_at_unix_ms: now,
				}).returning({ id: users.id }).get()

				return created.id
			})
			this.database.connection.update(authRequests).set({ user_id: userId })
				.where(eq(authRequests.id, flowId)).run()

			signupFlows.delete(flowId)
			return this.createSession(userId)
		} catch (error) {
			await this.grpc.removePendingJoin(minecraftUsername).catch(() => undefined)
			throw error
		}
	}

	async signIn(emailInput: string) {
		const email = normalizeEmail(emailInput)
		const user = this.findUserByEmail(email)

		if (!user) {
			throw new UnauthorizedException('No account exists for this email')
		}

		const now = Date.now()
		const code = createAuthCode()
		const flowId = randomUUID()
		this.expireActiveAuthRequests(email, 'signin', now)
		this.createAuthRequest(flowId, 'signin', email, user.id, code, now)
		const delivery = await this.deliverVerificationCode(email, code, 'signin')
		this.setAuthRequestDelivery(flowId, delivery)

		return { flowId, delivery }
	}

	verifySignIn(flowId: string, code: string) {
		const now = Date.now()
		const request = this.getActiveAuthRequest(flowId, 'signin', now)
		if (!safeSecretEquals(code, request.code_hash)) {
			this.recordFailedAuthAttempt(request)
			throw new BadRequestException('Invalid email code')
		}
		if (!request.user_id) {
			throw new UnauthorizedException('No account exists for this email')
		}

		this.completeAuthRequest(flowId, now)
		return this.createSession(request.user_id)
	}

	listAuthRequests() {
		const now = Date.now()
		this.clearExpiredAuthRequestCodes(now)

		const requests = this.database.connection.select({ request: authRequests, user: users })
			.from(authRequests)
			.leftJoin(users, eq(users.id, authRequests.user_id))
			.orderBy(desc(authRequests.created_at_unix_ms))
			.limit(AUTH_REQUEST_HISTORY_LIMIT)
			.all()

		return {
			requests: requests.map(({ request, user }) => ({
				id: request.id,
				kind: request.kind,
				email: request.email,
				minecraftUsername: user?.minecraft_username ?? null,
				code: isAuthRequestActive(request, now) ? request.active_code : null,
				deliveryStatus: request.delivery_status,
				createdAtUnixMs: request.created_at_unix_ms,
				expiresAtUnixMs: request.expires_at_unix_ms,
				completedAtUnixMs: request.completed_at_unix_ms,
				status: request.completed_at_unix_ms !== null
					? 'verified'
					: request.expires_at_unix_ms <= now ? 'expired' : 'active',
			})),
		}
	}

	listEmailWhitelist() {
		const usernamesById = new Map(this.database.connection.select({
			id: users.id,
			minecraftUsername: users.minecraft_username,
		}).from(users).all().map((user) => [user.id, user.minecraftUsername]))

		return {
			entries: this.database.connection.select().from(emailWhitelist)
				.orderBy(asc(emailWhitelist.email)).all()
				.map((entry) => ({
					email: entry.email,
					addedByMinecraftUsername: usernamesById.get(entry.added_by_user_id) ?? 'Unknown user',
					responsibleMinecraftUsername: entry.responsible_user_id === null
						? null
						: usernamesById.get(entry.responsible_user_id) ?? 'Unknown user',
					createdAtUnixMs: entry.created_at_unix_ms,
				})),
		}
	}

	addEmailToWhitelist(admin: AuthenticatedUser, emailInput: unknown, responsibleUserIdInput: unknown) {
		if (typeof emailInput !== 'string') {
			throw new BadRequestException('Email is required')
		}
		const email = normalizeEmail(emailInput)
		if (!isValidEmail(email)) {
			throw new BadRequestException('Enter a valid email address')
		}
		if (isAllowedEmail(email)) {
			throw new BadRequestException('MMU email addresses are already allowed')
		}
		if (typeof responsibleUserIdInput !== 'number' || !Number.isInteger(responsibleUserIdInput) || responsibleUserIdInput <= 0) {
			throw new BadRequestException('Select a responsible user')
		}
		const responsibleUserId = responsibleUserIdInput
		if (!this.database.connection.select({ id: users.id }).from(users)
			.where(eq(users.id, responsibleUserId)).get()) {
			throw new BadRequestException('Select a responsible user')
		}

		const result = this.database.connection.insert(emailWhitelist).values({
			email,
			added_by_user_id: admin.id,
			responsible_user_id: responsibleUserId,
			created_at_unix_ms: Date.now(),
		}).onConflictDoNothing().run()
		if (result.changes !== 1) {
			throw new ConflictException('That email address is already whitelisted')
		}

		return { email }
	}

	removeEmailFromWhitelist(emailInput: string) {
		const email = normalizeEmail(emailInput)
		const result = this.database.connection.delete(emailWhitelist)
			.where(eq(emailWhitelist.email, email)).run()
		if (result.changes !== 1) {
			throw new NotFoundException('Whitelisted email address not found')
		}
		return { ok: true }
	}

	requireSession(rawCookieHeader: string | undefined): AuthenticatedUser {
		const user = this.getSession(rawCookieHeader)

		if (!user) {
			throw new UnauthorizedException('Not signed in')
		}

		return user
	}

	requireCommitteeSession(rawCookieHeader: string | undefined): AuthenticatedUser {
		const user = this.requireSession(rawCookieHeader)

		if (!user.isCommittee) {
			throw new ForbiddenException('Committee access is required')
		}

		return user
	}

	requireSuperAdminSession(rawCookieHeader: string | undefined): AuthenticatedUser {
		const user = this.requireSession(rawCookieHeader)

		if (!user.isSuperAdmin) {
			throw new ForbiddenException('Super-admin access is required')
		}

		return user
	}
	getSession(rawCookieHeader: string | undefined): AuthenticatedUser | null {
		const token = this.readCookie(rawCookieHeader, 'mcstack_session')
		if (!token) return null

		const tokenHash = hashSecret(token)
		const now = Date.now()

		const row = this.database.connection.select({ user: users })
			.from(sessions)
			.innerJoin(users, eq(users.id, sessions.user_id))
			.where(and(eq(sessions.token_hash, tokenHash), gt(sessions.expires_at_unix_ms, now)))
			.get()?.user

		if (!row) return null

		const isSuperAdmin = row.is_super_admin === 1
		return {
			id: row.id,
			minecraftUsername: row.minecraft_username,
			isMember: row.is_member === 1,
			isCommittee: isSuperAdmin || row.is_committee === 1,
			isSuperAdmin,
			whitelisted: true,
			rulesAccepted: true,
		}
	}

	deleteSession(rawCookieHeader: string | undefined) {
		const token = this.readCookie(rawCookieHeader, 'mcstack_session')
		if (!token) return

		this.database.connection.delete(sessions).where(eq(sessions.token_hash, hashSecret(token))).run()
	}

	private getFlow(flowId: string): SignupFlow {
		const flow = signupFlows.get(flowId)

		if (!flow) {
			throw new BadRequestException('Signup flow not found')
		}

		return flow
	}

	private findUserByEmail(email: string): UserRow | null {
		return this.database.connection.select().from(users).where(eq(users.email, email)).get() ?? null
	}

	private isEmailWhitelisted(email: string): boolean {
		return this.database.connection.select({ email: emailWhitelist.email }).from(emailWhitelist)
			.where(eq(emailWhitelist.email, email)).get() !== undefined
	}

	private findUserByMinecraftUsername(minecraftUsername: string): UserRow | null {
		return this.database.connection.select().from(users).all()
			.find((user) => user.minecraft_username.localeCompare(minecraftUsername, 'en', { sensitivity: 'base' }) === 0) ?? null
	}

	private findUserByMinecraftUuid(minecraftUuid: string): UserRow | null {
		return this.database.connection.select().from(users)
			.where(eq(users.minecraft_uuid, minecraftUuid)).get() ?? null
	}

	private async deleteIncompleteSignupFlowsForEmail(email: string) {
		for (const [flowId, flow] of signupFlows) {
			if (flow.email !== email) continue
			if (flow.minecraftUsername) {
				await this.grpc.removePendingJoin(flow.minecraftUsername).catch(() => undefined)
			}
			signupFlows.delete(flowId)
			this.expireAuthRequest(flowId)
		}
	}

	private async cleanupStaleSignupFlows() {
		const cutoff = Date.now() - SIGNUP_FLOW_IDLE_TTL_MS

		for (const [flowId, flow] of signupFlows) {
			if (flow.updatedAt >= cutoff) continue
			if (flow.minecraftUsername) {
				await this.grpc.removePendingJoin(flow.minecraftUsername).catch(() => undefined)
			}
			signupFlows.delete(flowId)
			this.expireAuthRequest(flowId)
		}
		this.clearExpiredAuthRequestCodes(Date.now())
	}

	private createAuthRequest(
		id: string,
		kind: 'signup' | 'signin',
		email: string,
		userId: number | null,
		code: string,
		now: number,
	) {
		this.database.connection.insert(authRequests).values({
			id,
			kind,
			email,
			user_id: userId,
			code_hash: hashSecret(code),
			active_code: code,
			delivery_status: 'manual',
			expires_at_unix_ms: now + EMAIL_CODE_TTL_MS,
			created_at_unix_ms: now,
		}).run()
	}

	private getActiveAuthRequest(id: string, kind: 'signup' | 'signin', now: number): AuthRequestRow {
		const request = this.database.connection.select().from(authRequests)
			.where(and(eq(authRequests.id, id), eq(authRequests.kind, kind))).get()

		if (!request || !isAuthRequestActive(request, now)) {
			throw new BadRequestException('Email verification request is not active')
		}
		return request
	}

	private completeAuthRequest(id: string, now: number) {
		this.database.connection.update(authRequests).set({
			active_code: null,
			completed_at_unix_ms: now,
		}).where(eq(authRequests.id, id)).run()
	}

	private recordFailedAuthAttempt(request: AuthRequestRow) {
		const failedAttempts = request.failed_attempts + 1
		this.database.connection.update(authRequests).set({
			failed_attempts: failedAttempts,
			...(failedAttempts >= MAX_AUTH_CODE_ATTEMPTS ? {
				active_code: null,
				expires_at_unix_ms: Date.now(),
			} : {}),
		}).where(eq(authRequests.id, request.id)).run()
	}

	private expireAuthRequest(id: string) {
		this.database.connection.update(authRequests).set({
			active_code: null,
			expires_at_unix_ms: Date.now(),
		}).where(and(eq(authRequests.id, id), isNull(authRequests.completed_at_unix_ms))).run()
	}

	private expireActiveAuthRequests(email: string, kind: 'signup' | 'signin', now: number) {
		this.database.connection.update(authRequests).set({
			active_code: null,
			expires_at_unix_ms: now,
		}).where(and(
			eq(authRequests.email, email),
			eq(authRequests.kind, kind),
			isNull(authRequests.completed_at_unix_ms),
		)).run()
	}

	private clearExpiredAuthRequestCodes(now: number) {
		this.database.connection.update(authRequests).set({ active_code: null })
			.where(and(lte(authRequests.expires_at_unix_ms, now), isNull(authRequests.completed_at_unix_ms))).run()
	}

	private setAuthRequestDelivery(id: string, deliveryStatus: 'sent' | 'manual') {
		this.database.connection.update(authRequests).set({ delivery_status: deliveryStatus })
			.where(eq(authRequests.id, id)).run()
	}

	private async deliverVerificationCode(email: string, code: string, kind: 'signup' | 'signin'): Promise<'sent' | 'manual'> {
		const apiKey = process.env.RESEND_API_KEY
		const from = process.env.RESEND_FROM ?? 'MMU Minecraft Society <onboarding@resend.dev>'
		const recipientDomain = email.split('@')[1] ?? 'invalid'
		if (!apiKey) {
			console.warn('[auth-email] Delivery skipped', {
				kind,
				recipientDomain,
				reason: 'RESEND_API_KEY is missing from the API process environment',
			})
			return 'manual'
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
			})

			if (response.ok) {
				console.info('[auth-email] Resend accepted verification email', {
					kind,
					recipientDomain,
					status: response.status,
				})
				return 'sent'
			}
			console.error('[auth-email] Resend rejected verification email', {
				kind,
				recipientDomain,
				from,
				status: response.status,
				response: await response.text(),
			})
		} catch (error) {
			console.error('[auth-email] Resend request failed', {
				kind,
				recipientDomain,
				from,
				error: error instanceof Error ? `${error.name}: ${error.message}` : String(error),
			})
		}
		return 'manual'
	}

	private createSession(userId: number) {
		const now = Date.now()
		const token = createOpaqueToken()
		const sessionId = randomUUID()

		this.database.connection.insert(sessions).values({
			id: sessionId,
			user_id: userId,
			token_hash: hashSecret(token),
			expires_at_unix_ms: now + SESSION_TTL_MS,
			created_at_unix_ms: now,
		}).run()

		return {
			token,
			maxAgeSeconds: Math.floor(SESSION_TTL_MS / 1000),
		}
	}

	private readCookie(rawCookieHeader: string | undefined, name: string): string | null {
		if (!rawCookieHeader) return null

		const cookies = rawCookieHeader.split(';').map((cookie) => cookie.trim())
		const match = cookies.find((cookie) => cookie.startsWith(`${name}=`))

		if (!match) return null

		return decodeURIComponent(match.slice(name.length + 1))
	}
}

function verificationCodeEmailHtml(code: string) {
	const items = code.split('|').map((item) => {
		const image = AUTH_CODE_IMAGES[item as keyof typeof AUTH_CODE_IMAGES]
		return `<td style="padding: 8px; text-align: center; font-weight: bold">${image ? `<img src="${AUTH_CODE_IMAGE_BASE}${image}" alt="" width="40" height="40" style="display: block; margin: 0 auto 4px; image-rendering: pixelated; object-fit: contain">` : ''}${item}</td>`
	}).join('')

	return `<p>Your verification code is:</p><table role="presentation"><tr>${items}</tr></table><p>It expires in 10 minutes. If you did not request this, you can ignore this email.</p>`
}

function isSuperAdminUsername(minecraftUsername: string) {
	return minecraftUsername.localeCompare(SUPER_ADMIN_MINECRAFT_USERNAME, 'en', { sensitivity: 'base' }) === 0
}
