import { BadRequestException, ConflictException, Injectable, NotFoundException } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { AuthenticatedUser } from '../auth/auth.service'
import { DatabaseService, GiftCodeRow } from '../database/database.service'
import { GrpcServerService } from '../grpc/grpc-server.service'
import { PlayersService } from '../players/players.service'

const GIFT_CODE_PATTERN = /^[A-Za-z0-9_.-]+$/
const MAX_GIFT_CODE_LENGTH = 64
const MAX_DABLOONS = 2_147_483_647

interface GameplayProtoRoot {
	mcstack: {
		gameplay: {
			v1: {
				GameplayControl: grpc.ServiceClientConstructor
			}
		}
	}
}

@Injectable()
export class GiftsService {
	private gameplayControlClient: grpc.Client | null = null

	constructor(
		private readonly database: DatabaseService,
		private readonly grpcServer: GrpcServerService,
		private readonly players: PlayersService,
	) { }

	listAdminPlayers() {
		const rows = this.database.connection.prepare(`
			SELECT
				users.id,
				users.minecraft_username,
				users.email,
				users.is_member,
				users.is_committee,
				COALESCE(player_profiles.discord_username, '') AS discord_username
			FROM users
			LEFT JOIN player_profiles ON player_profiles.user_id = users.id
			ORDER BY lower(users.minecraft_username)
		`).all() as Array<{
			id: number
			minecraft_username: string
			email: string
			is_member: number
			is_committee: number
			discord_username: string
		}>

		return {
			players: rows.map((row) => ({
				id: row.id,
				minecraftUsername: row.minecraft_username,
				discordUsername: row.discord_username,
				email: row.email,
				isMember: row.is_member === 1,
				isCommittee: isSuperAdminUsername(row.minecraft_username) || row.is_committee === 1,
			})),
		}
	}

	setMembership(userIdInput: string, isMember: unknown) {
		const userId = Number(userIdInput)
		if (!Number.isInteger(userId) || userId <= 0) {
			throw new NotFoundException('Player not found')
		}
		if (typeof isMember !== 'boolean') {
			throw new BadRequestException('isMember must be a boolean')
		}

		const updated = this.database.connection.prepare(`
			UPDATE users
			SET is_member = ?
			WHERE id = ?
		`).run(isMember ? 1 : 0, userId)

		if (updated.changes !== 1) {
			throw new NotFoundException('Player not found')
		}

		return { ok: true, userId, isMember }
	}

	setCommittee(userIdInput: string, isCommittee: unknown) {
		const userId = Number(userIdInput)
		if (!Number.isInteger(userId) || userId <= 0) {
			throw new NotFoundException('Player not found')
		}
		if (typeof isCommittee !== 'boolean') {
			throw new BadRequestException('isCommittee must be a boolean')
		}

		const target = this.database.connection.prepare(`
			SELECT minecraft_username
			FROM users
			WHERE id = ?
		`).get(userId) as { minecraft_username: string } | undefined
		if (!target) {
			throw new NotFoundException('Player not found')
		}
		if (isSuperAdminUsername(target.minecraft_username) && !isCommittee) {
			throw new BadRequestException('MerlinSpace is the permanent super-admin and cannot be removed from committee')
		}

		this.database.connection.prepare(`
			UPDATE users
			SET is_committee = ?
			WHERE id = ?
		`).run(isCommittee ? 1 : 0, userId)

		return { ok: true, userId, isCommittee }
	}

	createGiftCode(
		admin: AuthenticatedUser,
		codeInput: unknown,
		amountInput: unknown,
		redemptionModeInput: unknown,
		expiresAtUnixMsInput: unknown,
	) {
		const code = normalizeGiftCode(codeInput)
		const amountDabloons = normalizeAmount(amountInput)
		const now = Date.now()
		const redemptionMode = normalizeRedemptionMode(redemptionModeInput)
		const expiresAtUnixMs = normalizeExpiry(expiresAtUnixMsInput, now)

		try {
			this.database.connection.prepare(`
				INSERT INTO gift_codes (
					code,
					amount_dabloons,
					redemption_mode,
					expires_at_unix_ms,
					created_by_user_id,
					created_at_unix_ms,
					redeemed_by_user_id,
					redeemed_at_unix_ms
				)
				VALUES (?, ?, ?, ?, ?, ?, NULL, NULL)
			`).run(code, amountDabloons, redemptionMode, expiresAtUnixMs, admin.id, now)
		} catch (error) {
			if (isSqliteConstraint(error)) {
				throw new ConflictException('A gift code with that name already exists')
			}
			throw error
		}

		return { code, amountDabloons, redemptionMode, expiresAtUnixMs, createdAtUnixMs: now }
	}

	listGiftCodes() {
		const now = Date.now()
		const rows = this.database.connection.prepare(`
			SELECT gift_codes.*, COUNT(gift_code_redemptions.user_id) AS redemption_count
			FROM gift_codes
			LEFT JOIN gift_code_redemptions ON gift_code_redemptions.code = gift_codes.code
			WHERE (gift_codes.expires_at_unix_ms IS NULL OR gift_codes.expires_at_unix_ms > ?)
			  AND (
				gift_codes.redemption_mode = 'per_user'
				OR NOT EXISTS (
					SELECT 1
					FROM gift_code_redemptions AS existing_redemption
					WHERE existing_redemption.code = gift_codes.code
				)
			  )
			GROUP BY gift_codes.code
			ORDER BY created_at_unix_ms DESC
		`).all(now) as Array<GiftCodeRow & { redemption_count: number }>

		return {
			giftCodes: rows.map((row) => ({
				code: row.code,
				amountDabloons: row.amount_dabloons,
				redemptionMode: row.redemption_mode,
				expiresAtUnixMs: row.expires_at_unix_ms,
				createdAtUnixMs: row.created_at_unix_ms,
				redemptionCount: row.redemption_count,
			})),
		}
	}

	async redeem(user: AuthenticatedUser, codeInput: unknown) {
		const code = normalizeGiftCode(codeInput)
		const giftCode = this.database.connection.prepare(`
			SELECT *
			FROM gift_codes
			WHERE code = ? COLLATE NOCASE
		`).get(code) as GiftCodeRow | undefined

		if (!giftCode) {
			throw new BadRequestException('That gift code does not exist')
		}
		const now = Date.now()
		if (giftCode.expires_at_unix_ms !== null && giftCode.expires_at_unix_ms <= now) {
			throw new BadRequestException('That gift code has expired')
		}
		this.reserveRedemption(giftCode, user.id, now)
		let moneyGranted = false

		try {
			const result = await this.grantGiftCodeMoney(
				user.minecraftUsername,
				giftCode.code,
				giftCode.amount_dabloons,
				now,
			)

			if (!result.granted) {
				this.releaseReservation(giftCode.code, user.id, now)
				throw new BadRequestException(result.message || 'You have to be online on the server to redeem a gift code.')
			}
			moneyGranted = true

			this.players.recordMoneyForUser(
				user.id,
				'earned',
				'gift_code',
				giftCode.amount_dabloons,
				result.balance_dabloons,
				`gift:${giftCode.code.toLowerCase()}:${user.id}`,
				now,
			)

			return {
				redeemed: true,
				amountDabloons: giftCode.amount_dabloons,
				message: result.message || `Gift code redeemed for ${giftCode.amount_dabloons} dabloons.`,
			}
		} catch (error) {
			if (!moneyGranted && !(error instanceof BadRequestException)) {
				this.releaseReservation(giftCode.code, user.id, now)
				throw new BadRequestException('The Minecraft server could not redeem the code. Make sure you are online and try again.')
			}
			if (moneyGranted && !(error instanceof BadRequestException)) {
				throw new BadRequestException('The dabloons were granted, but the website record could not be updated. Contact committee if your balance looks wrong.')
			}
			throw error
		}
	}

	private releaseReservation(code: string, userId: number, redeemedAtUnixMs: number) {
		this.database.connection.prepare(`
			DELETE FROM gift_code_redemptions
			WHERE code = ? COLLATE NOCASE
			  AND user_id = ?
			  AND redeemed_at_unix_ms = ?
		`).run(code, userId, redeemedAtUnixMs)
	}

	private reserveRedemption(giftCode: GiftCodeRow, userId: number, redeemedAtUnixMs: number) {
		this.database.connection.transaction(() => {
			if (giftCode.redemption_mode === 'single') {
				const existing = this.database.connection.prepare(`
					SELECT 1
					FROM gift_code_redemptions
					WHERE code = ? COLLATE NOCASE
					LIMIT 1
				`).get(giftCode.code)
				if (existing) {
					throw new BadRequestException('That gift code has already been redeemed')
				}
			}

			const inserted = this.database.connection.prepare(`
				INSERT OR IGNORE INTO gift_code_redemptions (code, user_id, redeemed_at_unix_ms)
				VALUES (?, ?, ?)
			`).run(giftCode.code, userId, redeemedAtUnixMs)

			if (inserted.changes !== 1) {
				throw new BadRequestException('You have already redeemed that gift code')
			}
		})()
	}

	private async grantGiftCodeMoney(
		minecraftUsername: string,
		code: string,
		amountDabloons: number,
		unixMs: number,
	) {
		const client = this.getGameplayControlClient()
		const method = (client as unknown as Record<string, unknown>).GrantGiftCodeMoney

		if (typeof method !== 'function') {
			throw new Error('Unknown GameplayControl method: GrantGiftCodeMoney')
		}

		return await new Promise<{
			granted: boolean
			online: boolean
			balance_dabloons: number
			message: string
		}>((resolve, reject) => {
			method.call(client, {
				minecraft_username: minecraftUsername,
				amount_dabloons: amountDabloons,
				code,
				unix_ms: unixMs,
			}, (error: grpc.ServiceError | null, response: {
				granted: boolean
				online: boolean
				balance_dabloons: number
				message: string
			}) => {
				if (error) {
					reject(error)
					return
				}
				resolve(response)
			})
		})
	}

	private getGameplayControlClient() {
		if (this.gameplayControlClient) return this.gameplayControlClient

		const gameplayProto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto')
		this.gameplayControlClient = new gameplayProto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		)
		return this.gameplayControlClient
	}
}

function normalizeGiftCode(value: unknown) {
	if (typeof value !== 'string') {
		throw new BadRequestException('Gift code is required')
	}

	const code = value.trim().toLowerCase()
	if (!code || code.length > MAX_GIFT_CODE_LENGTH || !GIFT_CODE_PATTERN.test(code)) {
		throw new BadRequestException('Gift code must be 1-64 characters using only letters, numbers, -, _ or .')
	}
	return code
}

function normalizeAmount(value: unknown) {
	const amount = typeof value === 'number' ? value : Number(value)
	if (!Number.isInteger(amount) || amount <= 0 || amount > MAX_DABLOONS) {
		throw new BadRequestException(`Amount must be a whole number between 1 and ${MAX_DABLOONS}`)
	}
	return amount
}

function normalizeRedemptionMode(value: unknown): 'single' | 'per_user' {
	if (value === 'single' || value === 'per_user') return value
	throw new BadRequestException('Redemption mode must be single or per_user')
}

function normalizeExpiry(value: unknown, now: number): number | null {
	if (value === null || value === undefined || value === '') return null
	const expiresAtUnixMs = typeof value === 'number' ? value : Number(value)
	if (!Number.isSafeInteger(expiresAtUnixMs) || expiresAtUnixMs <= now) {
		throw new BadRequestException('Expiry must be a valid date and time in the future')
	}
	return expiresAtUnixMs
}

function isSqliteConstraint(error: unknown) {
	return error instanceof Error && 'code' in error && String((error as Error & { code?: unknown }).code).startsWith('SQLITE_CONSTRAINT')
}

function isSuperAdminUsername(minecraftUsername: string) {
	return minecraftUsername.localeCompare('MerlinSpace', 'en', { sensitivity: 'base' }) === 0
}
