import { BadRequestException, ConflictException, Injectable, NotFoundException } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { and, count, desc, eq, getTableColumns, gt, isNull, notExists, or } from 'drizzle-orm'
import { AuthenticatedUser } from '../auth/auth.service'
import {
	DatabaseService,
	GiftCodeRow,
	giftCodeRedemptions,
	giftCodes,
	playerProfiles,
	users,
} from '../database/database.service'
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
		const rows = this.database.connection.select({
			id: users.id,
			minecraft_username: users.minecraft_username,
			email: users.email,
			is_member: users.is_member,
			is_committee: users.is_committee,
			is_super_admin: users.is_super_admin,
			discord_username: playerProfiles.discord_username,
		}).from(users)
			.leftJoin(playerProfiles, eq(playerProfiles.user_id, users.id))
			.all()
			.sort((left, right) => left.minecraft_username.localeCompare(right.minecraft_username, 'en', { sensitivity: 'base' }))

		return {
			players: rows.map((row) => ({
				id: row.id,
				minecraftUsername: row.minecraft_username,
				discordUsername: row.discord_username ?? '',
				email: row.email,
				isMember: row.is_member === 1,
				isCommittee: row.is_super_admin === 1 || row.is_committee === 1,
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

		const updated = this.database.connection.update(users)
			.set({ is_member: isMember ? 1 : 0 })
			.where(eq(users.id, userId))
			.run()

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

		const target = this.database.connection.select({ is_super_admin: users.is_super_admin })
			.from(users).where(eq(users.id, userId)).get()
		if (!target) {
			throw new NotFoundException('Player not found')
		}
		if (target.is_super_admin === 1 && !isCommittee) {
			throw new BadRequestException('The permanent super-admin cannot be removed from committee')
		}

		this.database.connection.update(users)
			.set({ is_committee: isCommittee ? 1 : 0 })
			.where(eq(users.id, userId))
			.run()

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
			this.database.connection.insert(giftCodes).values({
				code,
				amount_dabloons: amountDabloons,
				redemption_mode: redemptionMode,
				expires_at_unix_ms: expiresAtUnixMs,
				created_by_user_id: admin.id,
				created_at_unix_ms: now,
				redeemed_by_user_id: null,
				redeemed_at_unix_ms: null,
			}).run()
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
		const existingRedemption = this.database.connection.select({ code: giftCodeRedemptions.code })
			.from(giftCodeRedemptions)
			.where(eq(giftCodeRedemptions.code, giftCodes.code))

		const rows = this.database.connection.select({
			...getTableColumns(giftCodes),
			redemption_count: count(giftCodeRedemptions.user_id),
		}).from(giftCodes)
			.leftJoin(giftCodeRedemptions, eq(giftCodeRedemptions.code, giftCodes.code))
			.where(and(
				or(isNull(giftCodes.expires_at_unix_ms), gt(giftCodes.expires_at_unix_ms, now)),
				or(eq(giftCodes.redemption_mode, 'per_user'), notExists(existingRedemption)),
			))
			.groupBy(giftCodes.code)
			.orderBy(desc(giftCodes.created_at_unix_ms))
			.all()

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
		const giftCode = this.database.connection.select().from(giftCodes).where(eq(giftCodes.code, code)).get()

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
		this.database.connection.delete(giftCodeRedemptions).where(and(
			eq(giftCodeRedemptions.code, code),
			eq(giftCodeRedemptions.user_id, userId),
			eq(giftCodeRedemptions.redeemed_at_unix_ms, redeemedAtUnixMs),
		)).run()
	}

	private reserveRedemption(giftCode: GiftCodeRow, userId: number, redeemedAtUnixMs: number) {
		this.database.connection.transaction((tx) => {
			if (giftCode.redemption_mode === 'single') {
				const existing = tx.select({ code: giftCodeRedemptions.code })
					.from(giftCodeRedemptions)
					.where(eq(giftCodeRedemptions.code, giftCode.code))
					.get()
				if (existing) {
					throw new BadRequestException('That gift code has already been redeemed')
				}
			}

			const inserted = tx.insert(giftCodeRedemptions).values({
				code: giftCode.code,
				user_id: userId,
				redeemed_at_unix_ms: redeemedAtUnixMs,
			}).onConflictDoNothing().run()

			if (inserted.changes !== 1) {
				throw new BadRequestException('You have already redeemed that gift code')
			}
		})
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
