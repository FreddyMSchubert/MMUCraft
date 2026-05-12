import { BadRequestException, Injectable } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { AuthenticatedUser } from '../../auth/auth.service'
import { DatabaseService, DailyClaimRow } from '../../database/database.service'
import { GrpcServerService } from '../../grpc/grpc-server.service'

const LOGIN_BONUS_TASK_ID = 'login_bonus'
const LOGIN_BONUS_AMOUNT = 3
const ITEM_SUBMISSION_TASK_ID = 'item_submission'
const RESET_HOUR = 4
const RESET_TIME_ZONE = 'Europe/London'
const DEFAULT_ITEM_SUBMISSIONS_PATH = join(process.cwd(), 'content', 'daily-item-submissions.json')

interface DailyItemConfig {
	item: string
	min: number
	max: number
	dabloons_per_item: number
}

interface DailyItemTask {
	item: string
	count: number
	rewardDabloons: number
	dabloonsPerItem: number
}

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
		const itemTask = this.pickItemTask(periodKey)

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
				{
					id: ITEM_SUBMISSION_TASK_ID,
					number: 2,
					title: 'Item submission',
					rewardDabloons: itemTask.rewardDabloons,
					claimed: this.hasClaimed(user.id, ITEM_SUBMISSION_TASK_ID, periodKey),
					item: itemTask.item,
					count: itemTask.count,
					dabloonsPerItem: itemTask.dabloonsPerItem,
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

	async claimItemSubmission(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey()
		const now = Date.now()
		const itemTask = this.pickItemTask(periodKey)

		const inserted = this.database.connection.prepare(`
			INSERT OR IGNORE INTO daily_claims (
				user_id,
				task_id,
				period_key,
				claimed_at_unix_ms
			)
			VALUES (?, ?, ?, ?)
		`).run(user.id, ITEM_SUBMISSION_TASK_ID, periodKey, now)

		if (inserted.changes !== 1) {
			return {
				claimed: true,
				submitted: false,
				message: 'You have already claimed this daily item submission.',
			}
		}

		try {
			const result = await this.submitDailyItems(user.minecraftUsername, periodKey, now, itemTask)

			if (!result.submitted) {
				this.deleteClaim(user.id, ITEM_SUBMISSION_TASK_ID, periodKey)
				throw new BadRequestException(result.message || 'You need the requested items in your inventory.')
			}

			return {
				claimed: true,
				submitted: true,
				message: result.message || `You received ${itemTask.rewardDabloons} dabloons.`,
			}
		} catch (error) {
			this.deleteClaim(user.id, ITEM_SUBMISSION_TASK_ID, periodKey)
			if (error instanceof BadRequestException) {
				throw error
			}

			throw new BadRequestException('You have to be online on the server to submit daily items.')
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

	private async submitDailyItems(
		minecraftUsername: string,
		periodKey: string,
		unixMs: number,
		itemTask: DailyItemTask,
	) {
		const client = this.getGameplayControlClient()
		const method = (client as unknown as Record<string, unknown>).SubmitDailyItems

		if (typeof method !== 'function') {
			throw new Error('Unknown GameplayControl method: SubmitDailyItems')
		}

		return await new Promise<{ submitted: boolean; online: boolean; found_count: number; message: string }>((resolve, reject) => {
			method.call(client, {
				minecraft_username: minecraftUsername,
				item: itemTask.item,
				count: itemTask.count,
				reward_dabloons: itemTask.rewardDabloons,
				period_key: periodKey,
				unix_ms: unixMs,
			}, (error: grpc.ServiceError | null, response: { submitted: boolean; online: boolean; found_count: number; message: string }) => {
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

	private pickItemTask(periodKey: string): DailyItemTask {
		const configs = this.loadItemConfigs()
		const itemIndex = deterministicInt(`${periodKey}:item`, configs.length)
		const config = configs[itemIndex]!
		const min = Math.ceil(config.min)
		const max = Math.floor(config.max)
		const count = min + deterministicInt(`${periodKey}:count:${config.item}`, max - min + 1)

		return {
			item: config.item,
			count,
			rewardDabloons: Math.ceil(count * config.dabloons_per_item),
			dabloonsPerItem: config.dabloons_per_item,
		}
	}

	private loadItemConfigs(): DailyItemConfig[] {
		const path = process.env.DAILY_ITEM_SUBMISSIONS_PATH ?? DEFAULT_ITEM_SUBMISSIONS_PATH
		if (!existsSync(path)) {
			throw new Error(`Daily item submissions file does not exist: ${path}`)
		}

		const parsed = JSON.parse(readFileSync(path, 'utf8')) as DailyItemConfig[]
		const valid = parsed.filter((config) =>
			typeof config.item === 'string'
			&& config.item.includes(':')
			&& Number.isFinite(config.min)
			&& Number.isFinite(config.max)
			&& Number.isFinite(config.dabloons_per_item)
			&& config.min >= 1
			&& config.max >= config.min
			&& config.dabloons_per_item > 0
		)

		if (valid.length === 0) {
			throw new Error('Daily item submissions file does not contain any valid entries.')
		}

		return valid
	}
}

function deterministicInt(seed: string, maxExclusive: number) {
	if (maxExclusive <= 0) {
		return 0
	}

	const hex = createHash('sha256').update(seed).digest('hex').slice(0, 12)
	return Number.parseInt(hex, 16) % maxExclusive
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
