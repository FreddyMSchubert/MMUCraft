import { BadRequestException, Injectable } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { AuthenticatedUser } from '../../auth/auth.service'
import { DatabaseService, DailyClaimRow } from '../../database/database.service'
import { GrpcServerService } from '../../grpc/grpc-server.service'

const LOGIN_BONUS_TASK_ID = 'login_bonus'
const LOGIN_BONUS_AMOUNT = 3
const RESET_HOUR = 4
const RESET_TIME_ZONE = 'Europe/London'

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
export class DailiesService {
	private gameplayControlClient: grpc.Client | null = null

	constructor(
		private readonly database: DatabaseService,
		private readonly grpcServer: GrpcServerService,
	) { }

	getStatus(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey()

		return {
			resetHour: RESET_HOUR,
			resetTimeZone: RESET_TIME_ZONE,
			tasks: [
				{
					id: LOGIN_BONUS_TASK_ID,
					number: 1,
					title: 'Login bonus',
					rewardDabloons: LOGIN_BONUS_AMOUNT,
					claimed: this.hasClaimed(user.id, LOGIN_BONUS_TASK_ID, periodKey),
				},
			],
		}
	}

	async claimLoginBonus(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey()
		const now = Date.now()

		const inserted = this.database.connection.prepare(`
			INSERT OR IGNORE INTO daily_claims (
				user_id,
				task_id,
				period_key,
				claimed_at_unix_ms
			)
			VALUES (?, ?, ?, ?)
		`).run(user.id, LOGIN_BONUS_TASK_ID, periodKey, now)

		if (inserted.changes !== 1) {
			return {
				claimed: true,
				granted: false,
				message: 'You have already claimed this daily bonus.',
			}
		}

		try {
			const result = await this.grantDailyLoginBonus(user.minecraftUsername, periodKey, now)

			if (!result.granted) {
				this.deleteClaim(user.id, LOGIN_BONUS_TASK_ID, periodKey)
				throw new BadRequestException(result.message || 'You have to be online on the server to receive the money.')
			}

			return {
				claimed: true,
				granted: true,
				message: result.message || `You received ${LOGIN_BONUS_AMOUNT} dabloons.`,
			}
		} catch (error) {
			this.deleteClaim(user.id, LOGIN_BONUS_TASK_ID, periodKey)
			if (error instanceof BadRequestException) {
				throw error
			}

			throw new BadRequestException('You have to be online on the server to receive the money.')
		}
	}

	private hasClaimed(userId: number, taskId: string, periodKey: string) {
		const row = this.database.connection.prepare(`
			SELECT *
			FROM daily_claims
			WHERE user_id = ?
			  AND task_id = ?
			  AND period_key = ?
		`).get(userId, taskId, periodKey) as DailyClaimRow | undefined

		return Boolean(row)
	}

	private deleteClaim(userId: number, taskId: string, periodKey: string) {
		this.database.connection.prepare(`
			DELETE FROM daily_claims
			WHERE user_id = ?
			  AND task_id = ?
			  AND period_key = ?
		`).run(userId, taskId, periodKey)
	}

	private async grantDailyLoginBonus(minecraftUsername: string, periodKey: string, unixMs: number) {
		const client = this.getGameplayControlClient()
		const method = (client as unknown as Record<string, unknown>).GrantDailyLoginBonus

		if (typeof method !== 'function') {
			throw new Error('Unknown GameplayControl method: GrantDailyLoginBonus')
		}

		return await new Promise<{ granted: boolean; online: boolean; message: string }>((resolve, reject) => {
			method.call(client, {
				minecraft_username: minecraftUsername,
				amount: LOGIN_BONUS_AMOUNT,
				period_key: periodKey,
				unix_ms: unixMs,
			}, (error: grpc.ServiceError | null, response: { granted: boolean; online: boolean; message: string }) => {
				if (error) {
					reject(error)
					return
				}

				resolve(response)
			})
		})
	}

	private getGameplayControlClient() {
		if (this.gameplayControlClient) {
			return this.gameplayControlClient
		}

		const gameplayProto = this.grpcServer.loadProto<GameplayProtoRoot>('gameplay.proto')
		this.gameplayControlClient = new gameplayProto.mcstack.gameplay.v1.GameplayControl(
			process.env.MOD_GRPC_TARGET ?? 'minecraft:50052',
			grpc.credentials.createInsecure(),
		)

		return this.gameplayControlClient
	}
}

function currentDailyPeriodKey(now = new Date()) {
	const parts = new Intl.DateTimeFormat('en-CA', {
		timeZone: RESET_TIME_ZONE,
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
		hour: '2-digit',
		hourCycle: 'h23',
	}).formatToParts(now)

	const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
	const londonHour = Number(values.hour)
	const londonDate = new Date(Date.UTC(
		Number(values.year),
		Number(values.month) - 1,
		Number(values.day),
	))

	if (londonHour < RESET_HOUR) {
		londonDate.setUTCDate(londonDate.getUTCDate() - 1)
	}

	return londonDate.toISOString().slice(0, 10)
}
