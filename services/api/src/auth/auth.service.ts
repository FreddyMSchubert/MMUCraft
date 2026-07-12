import { BadRequestException, ForbiddenException, Injectable, UnauthorizedException } from '@nestjs/common'
import { randomUUID } from 'node:crypto'
import { and, eq, gt } from 'drizzle-orm'
import {
	DatabaseService,
	UserRow,
	sessions,
	users,
} from '../database/database.service'
import { normalizeMinecraftUuid } from '../database/minecraft-identity.service'
import { AuthGrpcService } from './auth-grpc.service'
import {
	createMinecraftCode,
	createNumericCode,
	createOpaqueToken,
	hashSecret,
	isAllowedEmail,
	isValidMinecraftUsername,
	normalizeEmail,
	safeSecretEquals,
} from './auth.util'
import { SignupFlow, signupFlows } from './signup-flow'

const EMAIL_CODE_TTL_MS = 10 * 60 * 1000
const MINECRAFT_CODE_TTL_MS = 15 * 60 * 1000
const SESSION_TTL_MS = 60 * 24 * 60 * 60 * 1000
const SIGNUP_FLOW_IDLE_TTL_MS = 60 * 60 * 1000
const SUPER_ADMIN_MINECRAFT_USERNAME = 'MerlinSpace'

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

		if (!isAllowedEmail(email)) {
			throw new BadRequestException('Only MMU email addresses are allowed')
		}

		await this.cleanupStaleSignupFlows()

		if (this.findUserByEmail(email)) {
			throw new BadRequestException('An account with this email already exists')
		}

		await this.deleteIncompleteSignupFlowsForEmail(email)

		const now = Date.now()
		const code = createNumericCode()
		const flowId = randomUUID()

		signupFlows.set(flowId, {
			email,
			step: 'email',
			emailCodeHash: hashSecret(code),
			emailCodeExpiresAt: now + EMAIL_CODE_TTL_MS,
			updatedAt: now,
		})

		return {
			flowId,
			devEmailCode: code,
		}
	}

	verifyEmailCode(flowId: string, code: string) {
		const flow = this.getFlow(flowId)
		const now = Date.now()

		if (flow.step !== 'email') {
			throw new BadRequestException('This signup flow is not waiting for email verification')
		}

		if (flow.emailCodeExpiresAt < now) {
			throw new BadRequestException('Email code expired')
		}

		if (!safeSecretEquals(code, flow.emailCodeHash)) {
			throw new BadRequestException('Invalid email code')
		}

		if (this.findUserByEmail(flow.email)) {
			throw new BadRequestException('An account with this email already exists')
		}

		flow.step = 'minecraft-username'
		flow.updatedAt = now

		return { ok: true }
	}

	async setMinecraftUsername(flowId: string, usernameInput: string) {
		await this.cleanupStaleSignupFlows()

		const flow = this.getFlow(flowId)
		const username = usernameInput.trim()
		const now = Date.now()

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

		const minecraftCode = createMinecraftCode()
		const expiresAt = now + MINECRAFT_CODE_TTL_MS

		flow.step = 'minecraft-code'
		flow.minecraftUsername = username
		flow.minecraftUuid = undefined
		flow.minecraftCodeHash = hashSecret(minecraftCode)
		flow.minecraftCodeExpiresAt = expiresAt
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

		if (flow.minecraftCodeExpiresAt < now) {
			throw new BadRequestException('Minecraft code expired')
		}

		if (!safeSecretEquals(code.trim().toUpperCase(), flow.minecraftCodeHash)) {
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

			signupFlows.delete(flowId)
			return this.createSession(userId)
		} catch (error) {
			await this.grpc.removePendingJoin(minecraftUsername).catch(() => undefined)
			throw error
		}
	}

	signIn(emailInput: string) {
		const email = normalizeEmail(emailInput)
		const user = this.findUserByEmail(email)

		if (!user) {
			throw new UnauthorizedException('No account exists for this email')
		}

		return this.createSession(user.id)
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
		}
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

function isSuperAdminUsername(minecraftUsername: string) {
	return minecraftUsername.localeCompare(SUPER_ADMIN_MINECRAFT_USERNAME, 'en', { sensitivity: 'base' }) === 0
}
