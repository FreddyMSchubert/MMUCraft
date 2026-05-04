import { BadRequestException, Injectable, UnauthorizedException } from '@nestjs/common'
import { randomUUID } from 'node:crypto'
import { DatabaseService, SignupFlowRow, UserRow } from '../database/database.service'
import { GrpcService } from '../grpc/grpc.service'
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

@Injectable()
export class AuthService {
	constructor(
		private readonly database: DatabaseService,
		private readonly grpc: GrpcService,
	) { }

	createSignup(emailInput: string) {
		const email = normalizeEmail(emailInput)

		if (!isAllowedEmail(email)) {
			throw new BadRequestException('Only MMU email addresses are allowed')
		}

		const existingUser = this.findUserByEmail(email)
		if (existingUser) {
			throw new BadRequestException('An account with this email already exists')
		}

		const now = Date.now()
		const code = createNumericCode()

		const flowId = randomUUID()

		this.database.connection.prepare(`
            INSERT INTO signup_flows (
                id,
                email,
                status,
                email_code_hash,
                email_code_expires_at_unix_ms,
                minecraft_username,
                minecraft_code_hash,
                minecraft_code_expires_at_unix_ms,
                created_at_unix_ms,
                updated_at_unix_ms
            )
            VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, ?, ?)
        `).run(
			flowId,
			email,
			'EMAIL_PENDING',
			hashSecret(code),
			now + EMAIL_CODE_TTL_MS,
			now,
			now,
		)

		return {
			flowId,
			devEmailCode: code,
		}
	}

	verifyEmailCode(flowId: string, code: string) {
		const flow = this.getFlow(flowId)
		const now = Date.now()

		if (flow.status !== 'EMAIL_PENDING') {
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

		this.database.connection.prepare(`
            INSERT INTO users (email, minecraft_username, whitelisted_at_unix_ms, created_at_unix_ms)
            VALUES (?, NULL, NULL, ?)
        `).run(flow.email, now)

		this.database.connection.prepare(`
            UPDATE signup_flows
            SET status = ?, updated_at_unix_ms = ?
            WHERE id = ?
        `).run('MINECRAFT_USERNAME_PENDING', now, flowId)

		return { ok: true }
	}

	async setMinecraftUsername(flowId: string, usernameInput: string) {
		const flow = this.getFlow(flowId)
		const username = usernameInput.trim()
		const now = Date.now()

		if (flow.status !== 'MINECRAFT_USERNAME_PENDING') {
			throw new BadRequestException('This signup flow is not waiting for a Minecraft username')
		}

		if (!isValidMinecraftUsername(username)) {
			throw new BadRequestException('Minecraft username must be 3-16 characters and only use letters, numbers, and underscores')
		}

		const existingMinecraftUser = this.database.connection.prepare(`
            SELECT * FROM users
            WHERE lower(minecraft_username) = lower(?)
        `).get(username) as UserRow | undefined

		if (existingMinecraftUser) {
			throw new BadRequestException('An account with this Minecraft username already exists')
		}

		const user = this.findUserByEmail(flow.email)
		if (!user) {
			throw new BadRequestException('User record does not exist for this signup flow')
		}

		const minecraftCode = createMinecraftCode()
		const expiresAt = now + MINECRAFT_CODE_TTL_MS

		this.database.connection.prepare(`
            UPDATE users
            SET minecraft_username = ?
            WHERE id = ?
        `).run(username, user.id)

		this.database.connection.prepare(`
            UPDATE signup_flows
            SET
                status = ?,
                minecraft_username = ?,
                minecraft_code_hash = ?,
                minecraft_code_expires_at_unix_ms = ?,
                updated_at_unix_ms = ?
            WHERE id = ?
        `).run(
			'MINECRAFT_CODE_PENDING',
			username,
			hashSecret(minecraftCode),
			expiresAt,
			now,
			flowId,
		)

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

		if (flow.status !== 'MINECRAFT_CODE_PENDING') {
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

		await this.grpc.whitelistPlayer(flow.minecraft_username)
		await this.grpc.removePendingJoin(flow.minecraft_username)

		this.database.connection.prepare(`
            UPDATE users
            SET whitelisted_at_unix_ms = ?
            WHERE email = ?
        `).run(now, flow.email)

		this.database.connection.prepare(`
            UPDATE signup_flows
            SET status = ?, updated_at_unix_ms = ?
            WHERE id = ?
        `).run('COMPLETE', now, flowId)

		const user = this.findUserByEmail(flow.email)
		if (!user) throw new BadRequestException('User disappeared during signup completion')

		return this.createSession(user.id)
	}

	signIn(emailInput: string) {
		const email = normalizeEmail(emailInput)
		const user = this.findUserByEmail(email)

		if (!user) {
			throw new UnauthorizedException('No account exists for this email')
		}

		return this.createSession(user.id)
	}

	getSession(rawCookieHeader: string | undefined) {
		const token = this.readCookie(rawCookieHeader, 'mcstack_session')
		if (!token) return null

		const tokenHash = hashSecret(token)
		const now = Date.now()

		const row = this.database.connection.prepare(`
            SELECT users.*
            FROM sessions
            JOIN users ON users.id = sessions.user_id
            WHERE sessions.token_hash = ?
              AND sessions.expires_at_unix_ms > ?
        `).get(tokenHash, now) as UserRow | undefined

		if (!row) return null

		return {
			id: row.id,
			email: row.email,
			minecraftUsername: row.minecraft_username,
			whitelisted: row.whitelisted_at_unix_ms !== null,
		}
	}

	deleteSession(rawCookieHeader: string | undefined) {
		const token = this.readCookie(rawCookieHeader, 'mcstack_session')
		if (!token) return

		this.database.connection.prepare(`
            DELETE FROM sessions
            WHERE token_hash = ?
        `).run(hashSecret(token))
	}

	private getFlow(flowId: string): SignupFlowRow {
		const flow = this.database.connection.prepare(`
            SELECT * FROM signup_flows
            WHERE id = ?
        `).get(flowId) as SignupFlowRow | undefined

		if (!flow) {
			throw new BadRequestException('Signup flow not found')
		}

		return flow
	}

	private findUserByEmail(email: string): UserRow | null {
		return (this.database.connection.prepare(`
            SELECT * FROM users
            WHERE email = ?
        `).get(email) as UserRow | undefined) ?? null
	}

	private createSession(userId: number) {
		const now = Date.now()
		const token = createOpaqueToken()
		const sessionId = randomUUID()

		this.database.connection.prepare(`
            INSERT INTO sessions (
                id,
                user_id,
                token_hash,
                expires_at_unix_ms,
                created_at_unix_ms
            )
            VALUES (?, ?, ?, ?, ?)
        `).run(
			sessionId,
			userId,
			hashSecret(token),
			now + SESSION_TTL_MS,
			now,
		)

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
