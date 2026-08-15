import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common'
import * as grpc from '@grpc/grpc-js'
import { and, eq, gte, or } from 'drizzle-orm'
import { filter, interval, map, merge, of, Subject } from 'rxjs'
import { AuthenticatedUser } from '../../auth/auth.service'
import { DatabaseService, dailyAdvancementTargets, dailyClaims, dailyTasks, playerMoneyEvents, users } from '../../database/database.service'
import { GrpcServerService } from '../../grpc/grpc-server.service'
import { PlayersService } from '../../players/players.service'
import { ShopService } from '../shop/shop.service'

const LOGIN_BONUS_TASK_ID = 'login_bonus'
const LOGIN_BONUS_REWARDS = [3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25] as const
const ADVANCEMENT_BONUS_TASK_ID = 'advancement_bonus'
const DAILY_COMPLETION_TASK_ID = 'daily_completion'
const STATIC_DAILY_TASK_IDS = [LOGIN_BONUS_TASK_ID, ADVANCEMENT_BONUS_TASK_ID] as const
const GENERATED_TASK_COUNT = 3
const DAILY_COMPLETION_BASE_REWARD = 20
const DAILY_COMPLETION_SUNDAY_BONUS = 12
const DAILY_COMPLETION_MEMBER_BONUS = 10
const RESET_HOUR = 4
const RESET_TIME_ZONE = 'Europe/London'
const MAX_TASK_JSON_LENGTH = 16_384

interface DailyTaskJson extends Record<string, unknown> {
	id: string
	emoji: string
	name: string
	description: string
	rewardDabloons: number
	baseCost: number
	rewardPerIteration?: number
	current: number
	max: number
}

interface DailyAdvancementTarget {
	advancementId: string
	title: string
	tabTitle: string
	iconItem: string
	baseRewardDabloons: number
	bonusRewardDabloons: number
	selectedAtUnixMs: number
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
	private readonly taskEvents = new Subject<{ userId: number }>()

	constructor(
		private readonly database: DatabaseService,
		private readonly grpcServer: GrpcServerService,
		private readonly players: PlayersService,
		private readonly shop: ShopService,
	) { }

	async getStatus(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey()
		const loginBonusClaimed = this.hasClaimed(user.id, LOGIN_BONUS_TASK_ID, periodKey)
		const loginStreak = this.getLoginStreak(user.id, periodKey, user.isMember)
		const loginReward = rewardForStreak(loginBonusClaimed ? loginStreak : loginStreak + 1)
		const generatedTasks = await this.getOrGenerateTasks(user, periodKey)
		const completedTaskCount = this.completedDailyTaskCount(user.id, periodKey)
		const completionReward = dailyCompletionReward(periodKey, user.isMember)
		const advancementTask = await this.getOrPickAdvancementTarget(user, periodKey).catch((error) => ({
			target: null,
			message: error instanceof Error ? error.message : 'Daily advancement target is unavailable right now.',
		}))
		const advancementClaimed = this.hasClaimed(user.id, ADVANCEMENT_BONUS_TASK_ID, periodKey)
		const advancementCompleted = advancementClaimed || Boolean(
			advancementTask.target && await this.isAdvancementCompleted(user, periodKey, advancementTask.target),
		)

		return {
			resetHour: RESET_HOUR,
			resetTimeZone: RESET_TIME_ZONE,
			loginStreak,
			nextLoginRewardDabloons: rewardForStreak(loginStreak + 1),
			completion: {
				completedTaskCount,
				totalTaskCount: STATIC_DAILY_TASK_IDS.length + generatedTasks.length,
				eligible: completedTaskCount === STATIC_DAILY_TASK_IDS.length + generatedTasks.length,
				claimed: this.hasClaimed(user.id, DAILY_COMPLETION_TASK_ID, periodKey),
				baseRewardDabloons: DAILY_COMPLETION_BASE_REWARD,
				sundayBonusDabloons: DAILY_COMPLETION_SUNDAY_BONUS,
				memberBonusDabloons: DAILY_COMPLETION_MEMBER_BONUS,
				isSunday: completionReward.isSunday,
				isMember: user.isMember,
				rewardDabloons: completionReward.total,
			},
			tasks: [
				{
					id: LOGIN_BONUS_TASK_ID,
					emoji: '🔥',
					name: 'Login bonus',
					rewardDabloons: loginReward,
					claimed: loginBonusClaimed,
					current: loginBonusClaimed ? 1 : 0,
					max: -1,
				},
				{
					id: ADVANCEMENT_BONUS_TASK_ID,
					emoji: '🏆',
					name: 'Advancement bonus',
					rewardDabloons: advancementTask.target?.bonusRewardDabloons ?? 0,
					claimed: advancementClaimed,
					current: advancementCompleted ? 1 : 0,
					max: 1,
					advancement: advancementTask.target ? {
						advancementId: advancementTask.target.advancementId,
						title: advancementTask.target.title,
						tabTitle: advancementTask.target.tabTitle,
						iconItem: advancementTask.target.iconItem,
						baseRewardDabloons: advancementTask.target.baseRewardDabloons,
						bonusRewardDabloons: advancementTask.target.bonusRewardDabloons,
						...this.shop.getItemRenderAsset(advancementTask.target.iconItem),
					} : null,
					unavailableMessage: advancementTask.target ? undefined : advancementTask.message,
				},
				...generatedTasks.map((task) => ({
					...task,
					claimed: this.hasClaimed(user.id, task.id, periodKey),
				})),
			],
		}
	}

	async claimLoginBonus(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey()
		const now = Date.now()
		const loginStreak = this.getLoginStreak(user.id, periodKey, user.isMember)
		const rewardDabloons = rewardForStreak(loginStreak + 1)

		const inserted = this.insertClaim(user.id, LOGIN_BONUS_TASK_ID, periodKey, now)

		if (inserted.changes !== 1) {
			return {
				claimed: true,
				granted: false,
				message: 'You have already claimed this daily bonus.',
			}
		}

		try {
			const result = await this.grantDailyLoginBonus(user.minecraftUsername, periodKey, now, rewardDabloons, 'login')

			if (!result.granted) {
				this.deleteClaim(user.id, LOGIN_BONUS_TASK_ID, periodKey)
				throw new BadRequestException(result.message || 'You have to be online on the server to receive the money.')
			}

			this.players.recordMoneyForUser(
				user.id,
				'earned',
				'daily_login_bonus',
				rewardDabloons,
				null,
				`daily:${LOGIN_BONUS_TASK_ID}:${user.id}:${periodKey}`,
				now,
			)
			this.taskEvents.next({ userId: user.id })

			return {
				claimed: true,
				granted: true,
				message: result.message || `You received ${rewardDabloons} dabloons.`,
			}
		} catch (error) {
			this.deleteClaim(user.id, LOGIN_BONUS_TASK_ID, periodKey)
			if (error instanceof BadRequestException) {
				throw error
			}

			throw new BadRequestException('You have to be online on the server to receive the money.')
		}
	}

	async claimAdvancementBonus(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey()
		const now = Date.now()
		const picked = await this.getOrPickAdvancementTarget(user, periodKey)

		if (!picked.target) {
			throw new BadRequestException(picked.message || 'Daily advancement target is unavailable right now.')
		}

		const inserted = this.insertClaim(user.id, ADVANCEMENT_BONUS_TASK_ID, periodKey, now)

		if (inserted.changes !== 1) {
			return {
				claimed: true,
				granted: false,
				message: 'You have already claimed this daily advancement bonus.',
			}
		}

		try {
			const result = await this.claimDailyAdvancement(user.minecraftUsername, periodKey, now, picked.target)

			if (!result.claimed) {
				this.deleteClaim(user.id, ADVANCEMENT_BONUS_TASK_ID, periodKey)
				throw new BadRequestException(result.message || 'Complete the daily advancement in-game first.')
			}

			this.players.recordMoneyForUser(
				user.id,
				'earned',
				'daily_advancement_bonus',
				picked.target.bonusRewardDabloons,
				null,
				`daily:${ADVANCEMENT_BONUS_TASK_ID}:${user.id}:${periodKey}`,
				now,
			)
			this.taskEvents.next({ userId: user.id })

			return {
				claimed: true,
				granted: true,
				message: result.message || `You received ${picked.target.bonusRewardDabloons} bonus dabloons.`,
			}
		} catch (error) {
			this.deleteClaim(user.id, ADVANCEMENT_BONUS_TASK_ID, periodKey)
			if (error instanceof BadRequestException) {
				throw error
			}

			throw new BadRequestException('You have to be online on the server to claim the daily advancement bonus.')
		}
	}

	async claimTask(user: AuthenticatedUser, taskId: string) {
		const periodKey = currentDailyPeriodKey()
		const row = this.database.connection.select().from(dailyTasks)
			.where(and(
				eq(dailyTasks.user_id, user.id),
				eq(dailyTasks.period_key, periodKey),
				eq(dailyTasks.task_id, taskId),
			)).get()
		if (!row) throw new BadRequestException('That task is not one of today\'s dailies.')

		const task = parseDailyTaskJson(row.task_json)
		if (task.max !== -1 && task.current < task.max) {
			throw new BadRequestException(`Complete ${task.name} in-game first.`)
		}

		const now = Date.now()
		const inserted = this.insertClaim(user.id, task.id, periodKey, now)
		if (inserted.changes !== 1) {
			return { claimed: true, granted: false, message: 'You have already claimed this daily.' }
		}

		try {
			const result = await this.claimMinecraftTask(user, periodKey, row.task_json)
			if (!result.claimed) {
				this.deleteClaim(user.id, task.id, periodKey)
				throw new BadRequestException(result.message || 'The Minecraft server could not claim this daily.')
			}

			if (result.task_json) this.storeTaskUpdate(user.id, periodKey, result.task_json, now)
			this.players.recordMoneyForUser(
				user.id,
				'earned',
				'daily_task',
				task.rewardDabloons,
				null,
				`daily:${task.id}:${user.id}:${periodKey}`,
				now,
			)
			this.taskEvents.next({ userId: user.id })
			return {
				claimed: true,
				granted: true,
				message: result.message || `You received ${task.rewardDabloons} dabloons.`,
			}
		} catch (error) {
			this.deleteClaim(user.id, task.id, periodKey)
			if (error instanceof BadRequestException) throw error
			throw new BadRequestException('You have to be online on the server to claim this daily.')
		}
	}

	async refreshForAdmin(userIdInput: string) {
		const userId = Number(userIdInput)
		if (!Number.isInteger(userId) || userId < 1) throw new BadRequestException('Select a valid player.')

		const row = this.database.connection.select().from(users).where(eq(users.id, userId)).get()
		if (!row) throw new NotFoundException('Player not found.')

		const user = {
			id: row.id,
			minecraftUsername: row.minecraft_username,
		}
		const periodKey = currentDailyPeriodKey()
		const currentTasks = this.database.connection.select().from(dailyTasks)
			.where(and(eq(dailyTasks.user_id, userId), eq(dailyTasks.period_key, periodKey))).all()
		const keptTasks = currentTasks.filter((task) => this.hasClaimed(userId, task.task_id, periodKey))
		const refreshedSlots = Array.from({ length: GENERATED_TASK_COUNT }, (_, slot) => slot)
			.filter((slot) => !keptTasks.some((task) => task.slot === slot))
		const currentAdvancement = this.getAdvancementTarget(userId, periodKey)
		const advancementCompleted = this.hasClaimed(userId, ADVANCEMENT_BONUS_TASK_ID, periodKey)
			|| Boolean(currentAdvancement && await this.isAdvancementCompleted(user, periodKey, currentAdvancement))
		const now = Date.now()

		const advancement = advancementCompleted
			? null
			: await this.pickDailyAdvancement(user.minecraftUsername, periodKey, now, currentAdvancement?.advancementId)
		if (advancement && !advancement.selected) {
			throw new BadRequestException(advancement.message || 'The Minecraft server could not regenerate the advancement daily.')
		}
		if (advancement?.selected && advancement.advancement_id === currentAdvancement?.advancementId) {
			throw new BadRequestException('The Minecraft server returned the same advancement daily.')
		}

		const generated = refreshedSlots.length === 0
			? { generated: true, task_json: [], message: '' }
			: await this.generateMinecraftTasks(user, periodKey, refreshedSlots.length, now, currentTasks.map((task) => task.task_id))
		if (!generated.generated || generated.task_json.length !== refreshedSlots.length) {
			throw new BadRequestException(generated.message || 'The Minecraft server could not regenerate the random dailies.')
		}
		const parsed = generated.task_json.map(parseDailyTaskJson)
		if (new Set([...currentTasks.map((task) => task.task_id), ...parsed.map((task) => task.id)]).size !== currentTasks.length + parsed.length) {
			throw new BadRequestException('The Minecraft server generated duplicate daily tasks.')
		}

		this.database.connection.transaction((tx) => {
			tx.delete(dailyTasks).where(and(eq(dailyTasks.user_id, userId), eq(dailyTasks.period_key, periodKey))).run()
			for (const task of keptTasks) tx.insert(dailyTasks).values(task).run()
			for (const [index, task] of parsed.entries()) tx.insert(dailyTasks).values({
				user_id: userId,
				period_key: periodKey,
				slot: refreshedSlots[index]!,
				task_id: task.id,
				task_json: JSON.stringify(task),
				updated_at_unix_ms: now,
			}).run()

			if (advancement?.selected) {
				tx.delete(dailyAdvancementTargets).where(and(
					eq(dailyAdvancementTargets.user_id, userId),
					eq(dailyAdvancementTargets.period_key, periodKey),
				)).run()
				tx.insert(dailyAdvancementTargets).values({
					user_id: userId,
					period_key: periodKey,
					advancement_id: advancement.advancement_id,
					title: advancement.title,
					tab_title: advancement.tab_title,
					icon_item: advancement.icon_item,
					base_reward_dabloons: advancement.base_reward_dabloons,
					bonus_reward_dabloons: dailyAdvancementBonus(advancement.base_reward_dabloons),
					selected_at_unix_ms: now,
				}).run()
			}
		})

		this.taskEvents.next({ userId })
		return {
			message: `Regenerated ${parsed.length} random ${parsed.length === 1 ? 'daily' : 'dailies'}${advancementCompleted ? '; kept the completed advancement daily' : ' and the advancement daily'}.`,
		}
	}

	notifyAdvancementCompletion(userId: number | null, advancementId: string) {
		if (!userId) return
		const target = this.getAdvancementTarget(userId, currentDailyPeriodKey())
		if (target?.advancementId === advancementId) this.taskEvents.next({ userId })
	}

	events(userId: number) {
		return merge(
			of({ data: { type: 'ready' } }),
			this.taskEvents.pipe(
				filter((event) => event.userId === userId),
				map(() => ({ data: { type: 'daily-update' } })),
			),
			interval(15_000).pipe(map(() => ({ data: { type: 'ping' } }))),
		)
	}

	getMinecraftSnapshot() {
		const periodKey = currentDailyPeriodKey()
		const usernames = new Map(this.database.connection.select({
			id: users.id,
			minecraftUsername: users.minecraft_username,
		}).from(users).all().map((user) => [user.id, user.minecraftUsername]))

		return {
			tasks: this.database.connection.select().from(dailyTasks)
				.where(eq(dailyTasks.period_key, periodKey)).all()
				.map((row) => ({
					user_id: row.user_id,
					minecraft_username: usernames.get(row.user_id) ?? '',
					period_key: row.period_key,
					task_json: row.task_json,
					claimed: this.hasClaimed(row.user_id, row.task_id, row.period_key),
				})),
		}
	}

	updateTaskFromMinecraft(userId: number, periodKey: string, taskJson: string) {
		if (periodKey !== currentDailyPeriodKey()) return { accepted: false, message: 'That daily period has ended.' }
		try {
			const accepted = this.storeTaskUpdate(userId, periodKey, taskJson, Date.now())
			if (accepted) this.taskEvents.next({ userId })
			return {
				accepted,
				message: accepted ? 'Daily task updated.' : 'The update was stale or did not match an assigned task.',
			}
		} catch (error) {
			return { accepted: false, message: errorMessage(error) }
		}
	}

	private hasClaimed(userId: number, taskId: string, periodKey: string) {
		const row = this.database.connection.select({ user_id: dailyClaims.user_id })
			.from(dailyClaims)
			.where(and(
				eq(dailyClaims.user_id, userId),
				eq(dailyClaims.task_id, taskId),
				eq(dailyClaims.period_key, periodKey),
			)).get()

		return Boolean(row)
	}

	async claimDailyCompletion(user: AuthenticatedUser) {
		const periodKey = currentDailyPeriodKey()
		const now = Date.now()

		if (this.completedDailyTaskCount(user.id, periodKey) !== STATIC_DAILY_TASK_IDS.length + GENERATED_TASK_COUNT) {
			throw new BadRequestException('Complete all of today\'s daily quests before finishing your dailies.')
		}

		const rewardDabloons = dailyCompletionReward(periodKey, user.isMember).total
		const inserted = this.insertClaim(user.id, DAILY_COMPLETION_TASK_ID, periodKey, now)

		if (inserted.changes !== 1) {
			return {
				claimed: true,
				granted: false,
				message: 'You have already finished today\'s dailies.',
			}
		}

		try {
			const result = await this.grantDailyLoginBonus(user.minecraftUsername, periodKey, now, rewardDabloons, 'daily_completion')

			if (!result.granted) {
				this.deleteClaim(user.id, DAILY_COMPLETION_TASK_ID, periodKey)
				throw new BadRequestException(result.message || 'You have to be online on the server to receive the money.')
			}

			this.players.recordMoneyForUser(
				user.id,
				'earned',
				'daily_completion_bonus',
				rewardDabloons,
				null,
				`daily:${DAILY_COMPLETION_TASK_ID}:${user.id}:${periodKey}`,
				now,
			)
			this.taskEvents.next({ userId: user.id })

			return {
				claimed: true,
				granted: true,
				message: `Dailies finished! You received ${rewardDabloons} dabloons.`,
			}
		} catch (error) {
			this.deleteClaim(user.id, DAILY_COMPLETION_TASK_ID, periodKey)
			if (error instanceof BadRequestException) {
				throw error
			}

			throw new BadRequestException('You have to be online on the server to receive the money.')
		}
	}

	private getLoginStreak(userId: number, periodKey: string, isMember: boolean) {
		const claimedPeriodKeys = this.database.connection
			.select({ period_key: dailyClaims.period_key })
			.from(dailyClaims)
			.where(and(
				eq(dailyClaims.user_id, userId),
				eq(dailyClaims.task_id, LOGIN_BONUS_TASK_ID),
			))
			.all()
			.map((claim) => claim.period_key)

		return calculateLoginStreak(claimedPeriodKeys, periodKey, isMember)
	}

	private completedDailyTaskCount(userId: number, periodKey: string) {
		const generatedIds = this.database.connection.select({ taskId: dailyTasks.task_id })
			.from(dailyTasks)
			.where(and(eq(dailyTasks.user_id, userId), eq(dailyTasks.period_key, periodKey)))
			.all()
			.map((task) => task.taskId)
		return [...STATIC_DAILY_TASK_IDS, ...generatedIds]
			.filter((taskId) => this.hasClaimed(userId, taskId, periodKey)).length
	}

	private deleteClaim(userId: number, taskId: string, periodKey: string) {
		this.database.connection.delete(dailyClaims).where(and(
			eq(dailyClaims.user_id, userId),
			eq(dailyClaims.task_id, taskId),
			eq(dailyClaims.period_key, periodKey),
		)).run()
	}

	private insertClaim(userId: number, taskId: string, periodKey: string, claimedAtUnixMs: number) {
		return this.database.connection.insert(dailyClaims).values({
			user_id: userId,
			task_id: taskId,
			period_key: periodKey,
			claimed_at_unix_ms: claimedAtUnixMs,
		}).onConflictDoNothing().run()
	}

	private async getOrGenerateTasks(user: AuthenticatedUser, periodKey: string) {
		let tasks = this.getStoredTasks(user.id, periodKey)
		if (tasks.length === GENERATED_TASK_COUNT) return tasks

		const generated = await this.generateMinecraftTasks(user, periodKey)
		if (!generated.generated || generated.task_json.length !== GENERATED_TASK_COUNT) {
			throw new BadRequestException(generated.message || 'The Minecraft server could not generate today\'s dailies.')
		}

		const parsed = generated.task_json.map(parseDailyTaskJson)
		if (new Set(parsed.map((task) => task.id)).size !== GENERATED_TASK_COUNT) {
			throw new BadRequestException('The Minecraft server generated duplicate daily tasks.')
		}

		const now = Date.now()
		this.database.connection.transaction((tx) => {
			for (const [slot, task] of parsed.entries()) {
				tx.insert(dailyTasks).values({
					user_id: user.id,
					period_key: periodKey,
					slot,
					task_id: task.id,
					task_json: JSON.stringify(task),
					updated_at_unix_ms: now,
				}).onConflictDoNothing().run()
			}
		})
		tasks = this.getStoredTasks(user.id, periodKey)
		if (tasks.length !== GENERATED_TASK_COUNT) {
			throw new BadRequestException('Today\'s generated dailies could not be saved.')
		}
		return tasks
	}

	private getStoredTasks(userId: number, periodKey: string) {
		return this.database.connection.select().from(dailyTasks)
			.where(and(eq(dailyTasks.user_id, userId), eq(dailyTasks.period_key, periodKey)))
			.all()
			.sort((left, right) => left.slot - right.slot)
			.map((row) => parseDailyTaskJson(row.task_json))
	}

	private storeTaskUpdate(userId: number, periodKey: string, taskJson: string, unixMs: number) {
		const next = parseDailyTaskJson(taskJson)
		const row = this.database.connection.select().from(dailyTasks)
			.where(and(
				eq(dailyTasks.user_id, userId),
				eq(dailyTasks.period_key, periodKey),
				eq(dailyTasks.task_id, next.id),
			)).get()
		if (!row) return false

		const current = parseDailyTaskJson(row.task_json)
		if (current.max !== next.max || next.current < current.current || this.hasClaimed(userId, next.id, periodKey)) {
			return false
		}

		this.database.connection.update(dailyTasks).set({
			task_json: JSON.stringify(next),
			updated_at_unix_ms: unixMs,
		}).where(and(
			eq(dailyTasks.user_id, userId),
			eq(dailyTasks.period_key, periodKey),
			eq(dailyTasks.task_id, next.id),
		)).run()
		return true
	}

	private async getOrPickAdvancementTarget(user: AuthenticatedUser, periodKey: string) {
		const existing = this.getAdvancementTarget(user.id, periodKey)
		if (existing) {
			return {
				target: existing,
				message: '',
			}
		}

		const now = Date.now()
		const result = await this.pickDailyAdvancement(user.minecraftUsername, periodKey, now)

		if (!result.selected) {
			return {
				target: null,
				message: result.message || 'Daily advancement target is unavailable right now.',
			}
		}

		this.database.connection.insert(dailyAdvancementTargets).values({
			user_id: user.id,
			period_key: periodKey,
			advancement_id: result.advancement_id,
			title: result.title,
			tab_title: result.tab_title,
			icon_item: result.icon_item,
			base_reward_dabloons: result.base_reward_dabloons,
			bonus_reward_dabloons: dailyAdvancementBonus(result.base_reward_dabloons),
			selected_at_unix_ms: now,
		}).onConflictDoNothing().run()

		return {
			target: this.getAdvancementTarget(user.id, periodKey),
			message: '',
		}
	}

	private getAdvancementTarget(userId: number, periodKey: string): DailyAdvancementTarget | null {
		const row = this.database.connection.select().from(dailyAdvancementTargets)
			.where(and(
				eq(dailyAdvancementTargets.user_id, userId),
				eq(dailyAdvancementTargets.period_key, periodKey),
			)).get()

		if (!row) {
			return null
		}

		return {
			advancementId: row.advancement_id,
			title: row.title,
			tabTitle: row.tab_title,
			iconItem: row.icon_item,
			baseRewardDabloons: row.base_reward_dabloons,
			bonusRewardDabloons: dailyAdvancementBonus(row.base_reward_dabloons),
			selectedAtUnixMs: row.selected_at_unix_ms,
		}
	}

	private async isAdvancementCompleted(
		user: Pick<AuthenticatedUser, 'id' | 'minecraftUsername'>,
		periodKey: string,
		target: DailyAdvancementTarget,
	) {
		const recorded = this.database.connection.select({ id: playerMoneyEvents.id })
			.from(playerMoneyEvents)
			.where(and(
				eq(playerMoneyEvents.user_id, user.id),
				eq(playerMoneyEvents.source, 'advancement'),
				gte(playerMoneyEvents.created_at_unix_ms, target.selectedAtUnixMs),
				or(
					eq(playerMoneyEvents.id, `advancement:${target.advancementId}`),
					eq(playerMoneyEvents.id, `advancement:${user.id}:${target.advancementId}`),
				),
			)).get()
		if (recorded) return true

		return this.claimDailyAdvancement(user.minecraftUsername, periodKey, Date.now(), target, true)
			.then((result) => result.completed)
			.catch(() => false)
	}

	private async grantDailyLoginBonus(minecraftUsername: string, periodKey: string, unixMs: number, rewardDabloons: number, source: string) {
		const client = this.getGameplayControlClient()
		const method = (client as unknown as Record<string, unknown>).GrantDailyLoginBonus

		if (typeof method !== 'function') {
			throw new Error('Unknown GameplayControl method: GrantDailyLoginBonus')
		}

		return await new Promise<{ granted: boolean; online: boolean; message: string }>((resolve, reject) => {
			method.call(client, {
				minecraft_username: minecraftUsername,
				amount: rewardDabloons,
				period_key: periodKey,
				unix_ms: unixMs,
				source,
			}, (error: grpc.ServiceError | null, response: { granted: boolean; online: boolean; message: string }) => {
				if (error) {
					reject(error)
					return
				}

				resolve(response)
			})
		})
	}

	private async generateMinecraftTasks(user: Pick<AuthenticatedUser, 'id' | 'minecraftUsername'>, periodKey: string, count = GENERATED_TASK_COUNT, unixMs = Date.now(), excludedTaskIds: string[] = []) {
		const client = this.getGameplayControlClient()
		const method = (client as unknown as Record<string, unknown>).GenerateDailyTasks

		if (typeof method !== 'function') {
			throw new Error('Unknown GameplayControl method: GenerateDailyTasks')
		}

		return await new Promise<{ generated: boolean; task_json: string[]; message: string }>((resolve, reject) => {
			method.call(client, {
				user_id: user.id,
				minecraft_username: user.minecraftUsername,
				period_key: periodKey,
				count,
				unix_ms: unixMs,
				excluded_task_ids: excludedTaskIds,
			}, (error: grpc.ServiceError | null, response: { generated: boolean; task_json: string[]; message: string }) => {
				if (error) {
					reject(error)
					return
				}

				resolve(response)
			})
		})
	}

	private async claimMinecraftTask(user: AuthenticatedUser, periodKey: string, taskJson: string) {
		const client = this.getGameplayControlClient()
		const method = (client as unknown as Record<string, unknown>).ClaimDailyTask
		if (typeof method !== 'function') throw new Error('Unknown GameplayControl method: ClaimDailyTask')

		return await new Promise<{ claimed: boolean; online: boolean; task_json: string; message: string }>((resolve, reject) => {
			method.call(client, {
				user_id: user.id,
				minecraft_username: user.minecraftUsername,
				period_key: periodKey,
				task_json: taskJson,
			}, (error: grpc.ServiceError | null, response: { claimed: boolean; online: boolean; task_json: string; message: string }) => {
				if (error) return reject(error)
				resolve(response)
			})
		})
	}

	private async pickDailyAdvancement(minecraftUsername: string, periodKey: string, unixMs: number, excludedAdvancementId = '') {
		const client = this.getGameplayControlClient()
		const method = (client as unknown as Record<string, unknown>).PickDailyAdvancement

		if (typeof method !== 'function') {
			throw new Error('Unknown GameplayControl method: PickDailyAdvancement')
		}

		type PickDailyAdvancementResponse = {
			selected: boolean
			online: boolean
			advancement_id: string
			title: string
			tab_title: string
			icon_item: string
			base_reward_dabloons: number
			bonus_reward_dabloons: number
			message: string
		}

		return await new Promise<PickDailyAdvancementResponse>((resolve, reject) => {
			method.call(client, {
				minecraft_username: minecraftUsername,
				period_key: periodKey,
				unix_ms: unixMs,
				excluded_advancement_id: excludedAdvancementId,
			}, (error: grpc.ServiceError | null, response: PickDailyAdvancementResponse) => {
				if (error) {
					reject(error)
					return
				}

				resolve(response)
			})
		})
	}
	private async claimDailyAdvancement(
		minecraftUsername: string,
		periodKey: string,
		unixMs: number,
		target: DailyAdvancementTarget,
		checkOnly = false,
	) {
		const client = this.getGameplayControlClient()
		const method = (client as unknown as Record<string, unknown>).ClaimDailyAdvancement

		if (typeof method !== 'function') {
			throw new Error('Unknown GameplayControl method: ClaimDailyAdvancement')
		}

		return await new Promise<{ claimed: boolean; online: boolean; completed: boolean; message: string }>((resolve, reject) => {
			method.call(client, {
				minecraft_username: minecraftUsername,
				advancement_id: target.advancementId,
				bonus_reward_dabloons: target.bonusRewardDabloons,
				period_key: periodKey,
				unix_ms: unixMs,
				check_only: checkOnly,
			}, (error: grpc.ServiceError | null, response: { claimed: boolean; online: boolean; completed: boolean; message: string }) => {
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

function parseDailyTaskJson(json: string): DailyTaskJson {
	if (json.length > MAX_TASK_JSON_LENGTH) throw new Error('Daily task JSON is too large.')
	const value = JSON.parse(json) as Partial<DailyTaskJson>
	if (!value || typeof value !== 'object'
		|| typeof value.id !== 'string' || !/^[a-z0-9_:.-]{1,160}$/.test(value.id)
		|| typeof value.emoji !== 'string' || value.emoji.length === 0 || value.emoji.length > 16
		|| typeof value.name !== 'string' || value.name.length === 0 || value.name.length > 120
		|| typeof value.description !== 'string' || value.description.length > 500
		|| ('progressLabel' in value && (typeof value.progressLabel !== 'string' || value.progressLabel.length > 80))
		|| ('progressUnit' in value && (typeof value.progressUnit !== 'string' || value.progressUnit.length > 80))
		|| ('rewardPerIteration' in value && (typeof value.rewardPerIteration !== 'number'
			|| !Number.isFinite(value.rewardPerIteration) || value.rewardPerIteration < 0))
		|| !Number.isInteger(value.baseCost) || value.baseCost! < 0
		|| !Number.isInteger(value.rewardDabloons) || value.rewardDabloons! < 0
		|| !Number.isInteger(value.current) || value.current! < 0
		|| !Number.isInteger(value.max) || value.max === 0 || value.max! < -1
		|| (value.max! > 0 && value.current! > value.max!)) {
		throw new Error('Daily task JSON is invalid.')
	}
	return value as DailyTaskJson
}

function errorMessage(error: unknown) {
	return error instanceof Error ? error.message : String(error)
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

function calculateLoginStreak(claimedPeriodKeys: string[], currentPeriodKey: string, isMember: boolean) {
	const sortedKeys = [...new Set(claimedPeriodKeys)]
		.filter((key) => key <= currentPeriodKey)
		.sort()
	let streak = 0
	let previousKey: string | null = null

	for (const key of sortedKeys) {
		if (previousKey === null) {
			streak = 1
		} else {
			const missedDays = Math.max(0, daysBetweenPeriodKeys(previousKey, key) - 1)
			streak = missedDays === 0
				? streak + 1
				: isMember
					? halveStreak(streak, missedDays) + 1
					: 1
		}

		previousKey = key
	}

	if (previousKey === null || previousKey === currentPeriodKey) {
		return streak
	}

	const missedDaysBeforeToday = Math.max(0, daysBetweenPeriodKeys(previousKey, currentPeriodKey) - 1)
	if (missedDaysBeforeToday === 0) {
		return streak
	}

	return isMember ? halveStreak(streak, missedDaysBeforeToday) : 0
}

function halveStreak(streak: number, days: number) {
	return Math.floor(streak / (2 ** days))
}

function daysBetweenPeriodKeys(from: string, to: string) {
	return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000)
}

function rewardForStreak(streak: number) {
	const rewardIndex = Math.min(Math.max(streak, 1), LOGIN_BONUS_REWARDS.length) - 1
	return LOGIN_BONUS_REWARDS[rewardIndex]!
}

// Round the total advancement reward to the next 10, with a 10-dabloon daily minimum.
function dailyAdvancementBonus(baseRewardDabloons: number) {
	const bounded = Math.max(5, Math.min(39, baseRewardDabloons));
	const toNextMultipleOf10 = Math.ceil(bounded / 10) * 10;
	return Math.max(10, toNextMultipleOf10 - bounded);
}

function dailyCompletionReward(periodKey: string, isMember: boolean) {
	const isSunday = new Date(`${periodKey}T00:00:00Z`).getUTCDay() === 0
	return {
		isSunday,
		total: DAILY_COMPLETION_BASE_REWARD
			+ (isSunday ? DAILY_COMPLETION_SUNDAY_BONUS : 0)
			+ (isMember ? DAILY_COMPLETION_MEMBER_BONUS : 0),
	}
}
