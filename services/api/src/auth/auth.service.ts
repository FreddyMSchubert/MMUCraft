import { BadRequestException, ForbiddenException, Injectable, UnauthorizedException } from '@nestjs/common'
import { randomUUID } from 'node:crypto'
import { and, eq, gt, lt, ne } from 'drizzle-orm'
import {
	DatabaseService,
	SignupFlowRow,
	SignupFlowStatus,
	UserRow,
	sessions,
	signupFlows,
	users,
} from '../database/database.service'
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

		this.database.connection.insert(signupFlows).values({
			id: flowId,
			email,
			status: SignupFlowStatus.EMAIL_PENDING,
			email_code_hash: hashSecret(code),
			email_code_expires_at_unix_ms: now + EMAIL_CODE_TTL_MS,
			minecraft_username: null,
			minecraft_code_hash: null,
			minecraft_code_expires_at_unix_ms: null,
			created_at_unix_ms: now,
			updated_at_unix_ms: now,
		}).run()

		return {
			flowId,
			devEmailCode: code,
		}
	}

	verifyEmailCode(flowId: string, code: string) {
		const flow = this.getFlow(flowId)
		const now = Date.now()

		if (flow.status !== SignupFlowStatus.EMAIL_PENDING) {
			throw new BadRequestException('This signup flow is not waiting for email verification')
		}

		if (flow.email_code_expires_at_unix_ms < now) {
			throw new BadRequestException('Email code expired')
		}

		if (!safeSecretEquals(code, flow.email_code_hash)) {
			throw new BadRequestException('Invalid email code')
		}

		if (this.findUserByEmail(flow.email)) {
			throw new BadRequestException('An account with this email already exists')
		}

		this.database.connection.update(signupFlows)
			.set({ status: SignupFlowStatus.MINECRAFT_USERNAME_PENDING, updated_at_unix_ms: now })
			.where(eq(signupFlows.id, flowId))
			.run()

		return { ok: true }
	}

	async setMinecraftUsername(flowId: string, usernameInput: string) {
		await this.cleanupStaleSignupFlows()

		const flow = this.getFlow(flowId)
		const username = usernameInput.trim()
		const now = Date.now()

		if (flow.status !== SignupFlowStatus.MINECRAFT_USERNAME_PENDING) {
			throw new BadRequestException('This signup flow is not waiting for a Minecraft username')
		}

		if (!isValidMinecraftUsername(username)) {
			throw new BadRequestException('Minecraft username must be 3-16 characters and only use letters, numbers, and underscores')
		}

		const existingUser = this.findUserByMinecraftUsername(username)

		if (existingUser) {
			throw new BadRequestException('An account with this Minecraft username already exists')
		}

		const existingActiveFlow = this.database.connection.select().from(signupFlows)
			.where(and(ne(signupFlows.id, flowId), ne(signupFlows.status, SignupFlowStatus.COMPLETE)))
			.all()
			.find((candidate) => candidate.minecraft_username?.localeCompare(username, 'en', { sensitivity: 'base' }) === 0)

		if (existingActiveFlow) {
			throw new BadRequestException('This Minecraft username is already being used in another signup flow')
		}

		const minecraftCode = createMinecraftCode()
		const expiresAt = now + MINECRAFT_CODE_TTL_MS

		this.database.connection.update(signupFlows).set({
			status: SignupFlowStatus.MINECRAFT_CODE_PENDING,
			minecraft_username: username,
			minecraft_code_hash: hashSecret(minecraftCode),
			minecraft_code_expires_at_unix_ms: expiresAt,
			updated_at_unix_ms: now,
		}).where(eq(signupFlows.id, flowId)).run()

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

		if (flow.status !== SignupFlowStatus.MINECRAFT_CODE_PENDING) {
			throw new BadRequestException('This signup flow is not waiting for a Minecraft join code')
		}

		if (!flow.minecraft_username || !flow.minecraft_code_hash || !flow.minecraft_code_expires_at_unix_ms) {
			throw new BadRequestException('Minecraft code is not available for this signup flow')
		}

		if (flow.minecraft_code_expires_at_unix_ms < now) {
			throw new BadRequestException('Minecraft code expired')
		}

		if (!safeSecretEquals(code.trim().toUpperCase(), flow.minecraft_code_hash)) {
			throw new BadRequestException('Invalid Minecraft code')
		}

		await this.grpc.removePendingJoin(flow.minecraft_username)

		this.database.connection.update(signupFlows)
			.set({ status: SignupFlowStatus.RULES_PENDING, updated_at_unix_ms: now })
			.where(eq(signupFlows.id, flowId))
			.run()
	}

	async acceptRules(flowId: string) {
		const flow = this.getFlow(flowId)
		const now = Date.now()

		if (flow.status !== SignupFlowStatus.RULES_PENDING) {
			throw new BadRequestException('This signup flow is not waiting for rules acceptance')
		}

		if (!flow.minecraft_username) {
			throw new BadRequestException('Minecraft username is not available for this signup flow')
		}
		const minecraftUsername = flow.minecraft_username

		if (this.findUserByEmail(flow.email)) {
			throw new BadRequestException('An account with this email already exists')
		}

		const existingMinecraftUser = this.findUserByMinecraftUsername(flow.minecraft_username)

		if (existingMinecraftUser) {
			throw new BadRequestException('An account with this Minecraft username already exists')
		}

		await this.grpc.whitelistPlayer(minecraftUsername)

		try {
			const userId = this.database.connection.transaction((tx) => {
				const created = tx.insert(users).values({
					email: flow.email,
					minecraft_username: minecraftUsername,
					is_committee: isSuperAdminUsername(minecraftUsername) ? 1 : 0,
					whitelisted_at_unix_ms: now,
					rules_accepted_at_unix_ms: now,
					created_at_unix_ms: now,
				}).returning({ id: users.id }).get()

				tx.update(signupFlows)
					.set({ status: SignupFlowStatus.COMPLETE, updated_at_unix_ms: now })
					.where(eq(signupFlows.id, flowId))
					.run()

				return created.id
			})

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

		const isSuperAdmin = isSuperAdminUsername(row.minecraft_username)
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

	private getFlow(flowId: string): SignupFlowRow {
		const flow = this.database.connection.select().from(signupFlows).where(eq(signupFlows.id, flowId)).get()

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

	private async deleteIncompleteSignupFlowsForEmail(email: string) {
		const flows = this.database.connection.select().from(signupFlows)
			.where(and(eq(signupFlows.email, email), ne(signupFlows.status, SignupFlowStatus.COMPLETE)))
			.all()

		for (const flow of flows) {
			if (flow.minecraft_username) {
				await this.grpc.removePendingJoin(flow.minecraft_username).catch(() => undefined)
			}
		}

		this.database.connection.delete(signupFlows)
			.where(and(eq(signupFlows.email, email), ne(signupFlows.status, SignupFlowStatus.COMPLETE)))
			.run()
	}

	private async cleanupStaleSignupFlows() {
		const cutoff = Date.now() - SIGNUP_FLOW_IDLE_TTL_MS

		const flows = this.database.connection.select().from(signupFlows)
			.where(and(ne(signupFlows.status, SignupFlowStatus.COMPLETE), lt(signupFlows.updated_at_unix_ms, cutoff)))
			.all()

		for (const flow of flows) {
			if (flow.minecraft_username) {
				await this.grpc.removePendingJoin(flow.minecraft_username).catch(() => undefined)
			}
		}

		this.database.connection.delete(signupFlows)
			.where(and(ne(signupFlows.status, SignupFlowStatus.COMPLETE), lt(signupFlows.updated_at_unix_ms, cutoff)))
			.run()
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
