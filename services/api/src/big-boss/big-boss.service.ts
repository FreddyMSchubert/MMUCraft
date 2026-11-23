import {
	BadRequestException,
	Injectable,
	Logger,
	NotFoundException,
	OnModuleDestroy,
	OnModuleInit,
} from '@nestjs/common';
import { eq, desc, sql } from 'drizzle-orm';
import { randomUUID } from 'node:crypto';
import {
	DatabaseService,
	bigBossProgress,
	bigBossSessions,
	bigBossState,
	bigBossTransitions,
	playerProfiles,
	users,
	type BigBossStateRow,
} from '../database/database.service';
import { AuthenticatedUser } from '../auth/auth-session.service';
import { DiscordService } from '../discord/discord.service';
import { MinecraftGrpcClientService } from '../grpc/minecraft-grpc-client.service';
import { currentDailyPeriodKey } from '../gameplay/dailies/daily-task-rules';
import { playerAvatarUrl } from '../players/player-color';

const REQUIRED_MS = 30 * 60_000;
const REIGN_LIMIT_MS = 7 * 24 * 60 * 60_000;
const COOLDOWN_MS = 60 * 60_000;
const BIG_BOSS_URL = '/play/big-boss';
const USERNAME = /^[A-Za-z0-9_]{3,16}$/;

function periodStartMs(period: string) {
	const utcFour = Date.parse(`${period}T04:00:00Z`);
	const londonHour = new Intl.DateTimeFormat('en-GB', {
		timeZone: 'Europe/London',
		hour: '2-digit',
		hourCycle: 'h23',
	}).format(new Date(utcFour));
	return utcFour - (Number(londonHour) - 4) * 60 * 60_000;
}

export interface BossHeartbeat {
	holder_username: string;
	reign_started_at_unix_ms: number;
	session_id: string;
	qualifying_ms: number;
	online: boolean;
	dimension: string;
	x: number;
	y: number;
	z: number;
	in_claim: boolean;
}

export interface BossKill {
	event_id: string;
	victim_username: string;
	killer_username: string;
	death_message: string;
	victim_session_id: string;
	victim_qualifying_ms: number;
	victim_reign_started_at_unix_ms: number;
}

export interface BossPlayer {
	minecraft_username: string;
	dimension: string;
	x: number;
	y: number;
	z: number;
	in_claim: boolean;
}

type TransitionReason =
	| 'seed'
	| 'kill'
	| 'inactive'
	| 'term_limit'
	| 'random_assignment'
	| 'admin_reassigned'
	| 'admin_cleared';

@Injectable()
export class BigBossService implements OnModuleInit, OnModuleDestroy {
	private readonly logger = new Logger(BigBossService.name);
	private timer: ReturnType<typeof setInterval> | null = null;
	private reconciliation: Promise<void> | null = null;

	constructor(
		private readonly database: DatabaseService,
		private readonly minecraft: MinecraftGrpcClientService,
		private readonly discord: DiscordService,
	) {
		const now = Date.now();
		this.database.connection
			.insert(bigBossState)
			.values({
				id: 1,
				holder_username: 'BigBeanBaka',
				reign_started_at_unix_ms: now,
				activity_day: currentDailyPeriodKey(new Date(now)),
			})
			.onConflictDoNothing()
			.run();
		if (
			this.database.connection.select().from(bigBossTransitions).limit(1).all().length === 0
		) {
			this.database.connection
				.insert(bigBossTransitions)
				.values({
					event_id: 'big-boss-seed',
					at_unix_ms: now,
					previous_username: null,
					next_username: 'BigBeanBaka',
					reason: 'seed',
				})
				.run();
		}
	}

	onModuleInit() {
		this.timer = setInterval(() => void this.reconcile(), 5_000);
		void this.reconcile();
	}

	onModuleDestroy() {
		if (this.timer) clearInterval(this.timer);
	}

	private state(): BigBossStateRow {
		const state = this.database.connection
			.select()
			.from(bigBossState)
			.where(eq(bigBossState.id, 1))
			.get();
		if (!state) throw new Error('Big Boss state is missing');
		return state;
	}

	private progress(username: string) {
		this.database.connection
			.insert(bigBossProgress)
			.values({ minecraft_username: username })
			.onConflictDoNothing()
			.run();
		const progress = this.database.connection
			.select()
			.from(bigBossProgress)
			.where(eq(bigBossProgress.minecraft_username, username))
			.get();
		if (!progress) throw new Error('Big Boss progress is missing');
		return progress;
	}

	private account(username: string) {
		return this.database.connection
			.select()
			.from(users)
			.where(sql`lower(${users.minecraft_username}) = ${username.toLowerCase()}`)
			.get();
	}

	private eligible(username: string, now: number) {
		if (!USERNAME.test(username) || username.toLowerCase() === 'mmu_admin') return false;
		const account = this.account(username);
		if (!account) return false;
		const profile = this.database.connection
			.select({ optedOut: playerProfiles.big_boss_opt_out })
			.from(playerProfiles)
			.where(eq(playerProfiles.user_id, account.id))
			.get();
		return (
			profile?.optedOut !== 1 &&
			now - this.progress(account.minecraft_username).last_held_at_unix_ms >= COOLDOWN_MS
		);
	}

	private response(username = '') {
		const state = this.state();
		const progress = username ? this.progress(username) : null;
		return {
			holder_username: state.holder_username ?? '',
			reign_started_at_unix_ms: state.reign_started_at_unix_ms ?? 0,
			qualifying_ms: progress?.qualifying_ms ?? 0,
			win_count: progress?.win_count ?? 0,
			has_lost_kill_won_reign: progress?.stopped_after_win === 1,
		};
	}

	async heartbeat(input: BossHeartbeat) {
		if (
			!/^[0-9a-f-]{36}$/i.test(input.session_id) ||
			!Number.isSafeInteger(input.qualifying_ms) ||
			input.qualifying_ms < 0 ||
			!Number.isSafeInteger(input.reign_started_at_unix_ms) ||
			(input.online && !this.validPosition(input))
		)
			throw new BadRequestException('Invalid boss heartbeat');
		await this.reconcile();
		const state = this.state();
		const username = input.holder_username.trim();
		if (
			username &&
			state.holder_username?.toLowerCase() === username.toLowerCase() &&
			state.reign_started_at_unix_ms === input.reign_started_at_unix_ms
		) {
			const now = Date.now();
			if (input.qualifying_ms > now - input.reign_started_at_unix_ms + 10_000)
				throw new BadRequestException('Boss time exceeds reign length');
			this.creditTime(state, username, input.session_id, input.qualifying_ms);
			this.database.connection
				.update(bigBossState)
				.set({
					last_sample_at_unix_ms: input.online ? now : null,
					last_dimension: input.online ? input.dimension : state.last_dimension,
					last_x: input.online ? input.x : state.last_x,
					last_y: input.online ? input.y : state.last_y,
					last_z: input.online ? input.z : state.last_z,
					last_in_claim: input.online ? (input.in_claim ? 1 : 0) : state.last_in_claim,
				})
				.where(eq(bigBossState.id, 1))
				.run();
		}
		return this.response(username);
	}

	private creditTime(
		state: BigBossStateRow,
		username: string,
		sessionId: string,
		counter: number,
	) {
		return this.database.connection.transaction((tx) => {
			const session = tx
				.select()
				.from(bigBossSessions)
				.where(eq(bigBossSessions.id, sessionId))
				.get();
			const progress = this.progress(username);
			if (
				session &&
				(session.minecraft_username.toLowerCase() !== username.toLowerCase() ||
					session.reign_started_at_unix_ms !== state.reign_started_at_unix_ms)
			)
				throw new BadRequestException('Boss session does not match the reign');
			const delta = Math.max(0, counter - (session?.qualifying_ms ?? 0));
			if (session)
				tx.update(bigBossSessions)
					.set({ qualifying_ms: Math.max(session.qualifying_ms, counter) })
					.where(eq(bigBossSessions.id, sessionId))
					.run();
			else
				tx.insert(bigBossSessions)
					.values({
						id: sessionId,
						reign_started_at_unix_ms: state.reign_started_at_unix_ms ?? 0,
						minecraft_username: username,
						qualifying_ms: counter,
					})
					.run();
			if (delta) {
				tx.update(bigBossProgress)
					.set({ qualifying_ms: progress.qualifying_ms + delta })
					.where(eq(bigBossProgress.minecraft_username, username))
					.run();
				tx.update(bigBossState)
					.set({ activity_ms: state.activity_ms + delta })
					.where(eq(bigBossState.id, 1))
					.run();
			}
			return progress.qualifying_ms + delta;
		});
	}

	async joined(input: BossPlayer) {
		if (!USERNAME.test(input.minecraft_username) || !this.validPosition(input))
			throw new BadRequestException('Invalid boss join');
		const username = input.minecraft_username;
		const before = this.state().holder_username;
		await this.reconcile();
		const state = this.state();
		if (state.holder_username?.toLowerCase() === username.toLowerCase()) {
			this.database.connection
				.update(bigBossState)
				.set({
					last_sample_at_unix_ms: Date.now(),
					last_dimension: input.dimension,
					last_x: input.x,
					last_y: input.y,
					last_z: input.z,
					last_in_claim: input.in_claim ? 1 : 0,
				})
				.where(eq(bigBossState.id, 1))
				.run();
			if (before?.toLowerCase() === username.toLowerCase()) {
				const message = `${state.holder_username}, the Big Boss, joined at ${input.x}, ${input.y}, ${input.z} in ${input.dimension}. Go get them: ${BIG_BOSS_URL}`;
				void this.discord.publishServer('big_boss', message).catch((error: unknown) => {
					this.logger.warn(`Could not announce Big Boss join: ${String(error)}`);
				});
				void this.minecraft
					.gameplay(
						'BroadcastBigBossNotice',
						{
							previous_username: '',
							next_username: state.holder_username,
							message,
							next_private_message: 'You are the Big Boss. Your position is live.',
							previous_private_message: '',
							presence_only: true,
						},
						{ deadline: Date.now() + 5_000 },
					)
					.catch((error: unknown) => {
						this.logger.warn(
							`Could not announce Big Boss join in game: ${String(error)}`,
						);
					});
			}
		}
		return this.response(username);
	}

	private validPosition(input: Pick<BossPlayer, 'dimension' | 'x' | 'y' | 'z'>) {
		return (
			typeof input.dimension === 'string' &&
			/^[a-z0-9_.-]+:[a-z0-9_./-]{1,100}$/.test(input.dimension) &&
			[input.x, input.y, input.z].every(
				(value) => Number.isInteger(value) && Math.abs(value) <= 30_000_000,
			)
		);
	}

	left(username: string) {
		const state = this.state();
		if (state.holder_username?.toLowerCase() === username.toLowerCase())
			this.database.connection
				.update(bigBossState)
				.set({ last_sample_at_unix_ms: null })
				.where(eq(bigBossState.id, 1))
				.run();
		return this.response(username);
	}

	recordKill(input: BossKill) {
		if (
			typeof input.victim_username !== 'string' ||
			typeof input.killer_username !== 'string' ||
			typeof input.event_id !== 'string' ||
			typeof input.death_message !== 'string'
		)
			throw new BadRequestException('Invalid boss kill');
		const victim = input.victim_username.trim();
		const killer = input.killer_username.trim();
		if (
			!/^[0-9a-f-]{36}$/i.test(input.event_id) ||
			!USERNAME.test(victim) ||
			!USERNAME.test(killer) ||
			victim.toLowerCase() === killer.toLowerCase()
		)
			throw new BadRequestException('Invalid boss kill');
		const existing = this.database.connection
			.select()
			.from(bigBossTransitions)
			.where(eq(bigBossTransitions.event_id, input.event_id))
			.get();
		if (existing) {
			if (
				existing.reason !== 'kill' ||
				existing.previous_username?.toLowerCase() !== victim.toLowerCase() ||
				existing.next_username?.toLowerCase() !== killer.toLowerCase()
			)
				throw new BadRequestException('Kill event ID was already used');
			return {
				transferred: true,
				holder_username: existing.next_username,
				reign_started_at_unix_ms: existing.at_unix_ms,
				killer_win_count: this.progress(killer).win_count,
				victim_had_kill_won_reign: this.progress(victim).stopped_after_win === 1,
				victim_total_qualifying_ms: this.progress(victim).qualifying_ms,
			};
		}
		const now = Date.now();
		const state = this.state();
		if (
			state.holder_username?.toLowerCase() !== victim.toLowerCase() ||
			!this.eligible(killer, now)
		)
			return {
				transferred: false,
				holder_username: state.holder_username ?? '',
				reign_started_at_unix_ms: state.reign_started_at_unix_ms ?? 0,
				killer_win_count: 0,
				victim_had_kill_won_reign: false,
				victim_total_qualifying_ms: 0,
			};
		if (
			!/^[0-9a-f-]{36}$/i.test(input.victim_session_id) ||
			!Number.isSafeInteger(input.victim_qualifying_ms) ||
			input.victim_qualifying_ms < 0 ||
			state.reign_started_at_unix_ms !== input.victim_reign_started_at_unix_ms ||
			input.victim_qualifying_ms > now - input.victim_reign_started_at_unix_ms + 10_000
		)
			throw new BadRequestException('Invalid boss time on kill');
		const victimTotal = this.creditTime(
			state,
			victim,
			input.victim_session_id,
			input.victim_qualifying_ms,
		);
		const winCount = this.progress(killer).win_count + 1;
		const death = input.death_message.trim().slice(0, 350);
		const transition = this.change(killer, 'kill', death, null, input.event_id, now, winCount);
		void this.discord
			.publishServer('big_boss', `${death} ${killer} is now the Big Boss.`)
			.catch((error: unknown) => {
				this.logger.warn(`Could not announce boss kill: ${String(error)}`);
			});
		return {
			transferred: true,
			holder_username: killer,
			reign_started_at_unix_ms: transition.at_unix_ms,
			killer_win_count: winCount,
			victim_had_kill_won_reign: state.acquired_by_kill === 1,
			victim_total_qualifying_ms: victimTotal,
		};
	}

	private change(
		next: string | null,
		reason: TransitionReason,
		detail = '',
		adminUserId: number | null = null,
		eventId: string = randomUUID(),
		now = Date.now(),
		winCount?: number,
	) {
		const previous = this.state();
		const transition = this.database.connection.transaction((tx) => {
			if (previous.holder_username) {
				this.progress(previous.holder_username);
				tx.update(bigBossProgress)
					.set({
						last_held_at_unix_ms: now,
						stopped_after_win: previous.acquired_by_kill
							? 1
							: this.progress(previous.holder_username).stopped_after_win,
					})
					.where(eq(bigBossProgress.minecraft_username, previous.holder_username))
					.run();
			}
			if (next) this.progress(next);
			if (next && winCount !== undefined)
				tx.update(bigBossProgress)
					.set({ win_count: winCount })
					.where(eq(bigBossProgress.minecraft_username, next))
					.run();
			tx.update(bigBossState)
				.set({
					holder_username: next,
					reign_started_at_unix_ms: next ? now : null,
					acquired_by_kill: reason === 'kill' ? 1 : 0,
					activity_day: currentDailyPeriodKey(new Date(now)),
					activity_ms: 0,
					last_sample_at_unix_ms: null,
					last_dimension: null,
					last_x: null,
					last_y: null,
					last_z: null,
					last_in_claim: 0,
				})
				.where(eq(bigBossState.id, 1))
				.run();
			return tx
				.insert(bigBossTransitions)
				.values({
					event_id: eventId,
					at_unix_ms: now,
					previous_username: previous.holder_username,
					next_username: next,
					reason,
					detail,
					admin_user_id: adminUserId,
				})
				.returning()
				.get();
		});
		return transition;
	}

	private async onlineEligible(now: number) {
		try {
			const response = await this.minecraft.gameplay<{
				players: { minecraft_username: string }[];
			}>('GetOnlinePlayers', {}, { deadline: Date.now() + 4_000 });
			return response.players
				.map((player) => player.minecraft_username)
				.filter((username) => this.eligible(username, now));
		} catch (error) {
			this.logger.warn(`Could not find online boss candidates: ${String(error)}`);
			return [];
		}
	}

	async reconcile() {
		if (this.reconciliation) return this.reconciliation;
		const work = this.reconcileNow();
		this.reconciliation = work;
		try {
			await work;
		} finally {
			this.reconciliation = null;
		}
	}

	private async reconcileNow() {
		try {
			const now = Date.now();
			const first = this.state();
			const period = currentDailyPeriodKey(new Date(now));
			const expired: TransitionReason | null = first.holder_username
				? first.reign_started_at_unix_ms !== null &&
					now - first.reign_started_at_unix_ms >= REIGN_LIMIT_MS
					? 'term_limit'
					: first.activity_day !== period &&
						  first.activity_ms < REQUIRED_MS &&
						  periodStartMs(period) - (first.reign_started_at_unix_ms ?? 0) >=
								REQUIRED_MS
						? 'inactive'
						: null
				: null;
			if (!expired && first.holder_username) {
				if (first.activity_day !== period)
					this.database.connection
						.update(bigBossState)
						.set({ activity_day: period, activity_ms: 0 })
						.where(eq(bigBossState.id, 1))
						.run();
				return;
			}
			const eligible = await this.onlineEligible(now);
			const state = this.state();
			if (state.holder_username !== first.holder_username) return;
			const candidates = eligible.filter(
				(username) => username.toLowerCase() !== state.holder_username?.toLowerCase(),
			);
			const next = candidates.length
				? (candidates[Math.floor(Math.random() * candidates.length)] ?? null)
				: null;
			if (state.holder_username) {
				const reason = expired ?? 'inactive';
				this.change(next, reason);
				this.announce(
					this.message(state.holder_username, next, reason),
					state.holder_username,
					next,
					undefined,
					state.acquired_by_kill === 1,
				);
			} else if (next) {
				this.change(next, 'random_assignment');
				this.announce(this.message(null, next, 'random_assignment'), null, next);
			}
		} catch (error) {
			this.logger.warn(`Could not reconcile Big Boss title: ${String(error)}`);
		}
	}

	private message(
		previous: string | null,
		next: string | null,
		reason: TransitionReason,
		detail = '',
	) {
		const change =
			reason === 'inactive'
				? `${previous} missed the daily 30 minutes outside claims.`
				: reason === 'term_limit'
					? `${previous} reached the seven-day reign limit.`
					: reason === 'admin_reassigned'
						? `An admin reassigned the Big Boss title to ${next}.`
						: reason === 'admin_cleared'
							? 'An admin cleared the Big Boss title.'
							: `${next} was assigned the vacant Big Boss title at random.`;
		return (
			`${change}${next && previous && reason !== 'admin_reassigned' ? ` ${next} was chosen at random and is now the Big Boss.` : ''}` +
			(!next ? ' The title is vacant.' : '') +
			(detail ? ` Reason: ${detail}` : '')
		);
	}

	private announce(
		message: string,
		previous: string | null,
		next: string | null,
		nextPrivate = next ? `You are now the Big Boss. ${message}` : '',
		previousEarnedStop = false,
	) {
		void this.discord.publishServer('big_boss', message).catch((error: unknown) => {
			this.logger.warn(`Could not announce Big Boss on Discord: ${String(error)}`);
		});
		void this.minecraft
			.gameplay(
				'BroadcastBigBossNotice',
				{
					previous_username: previous ?? '',
					next_username: next ?? '',
					message,
					next_private_message: nextPrivate,
					previous_private_message: previous
						? `Your Big Boss reign ended. ${message}`
						: '',
					previous_earned_stop: previousEarnedStop,
					reign_started_at_unix_ms: this.state().reign_started_at_unix_ms ?? 0,
				},
				{ deadline: Date.now() + 5_000 },
			)
			.catch((error: unknown) => {
				this.logger.warn(`Could not announce Big Boss in game: ${String(error)}`);
			});
	}

	setByAdmin(admin: AuthenticatedUser, targetUsername: string | null, detailInput: string) {
		if (
			typeof detailInput !== 'string' ||
			(targetUsername !== null && typeof targetUsername !== 'string')
		)
			throw new BadRequestException('Invalid admin reassignment');
		const detail = detailInput.trim();
		if (detail.length > 200 || /[\r\n]/.test(detail))
			throw new BadRequestException('Reason is invalid');
		const target = targetUsername ? this.account(targetUsername.trim()) : null;
		if (targetUsername && (!target || target.minecraft_username.toLowerCase() === 'mmu_admin'))
			throw new NotFoundException('Player not found');
		const state = this.state();
		if (!target && !state.holder_username)
			throw new BadRequestException('The title is already vacant');
		if (state.holder_username?.toLowerCase() === target?.minecraft_username.toLowerCase())
			throw new BadRequestException('That player is already the Big Boss');
		const reason = target ? 'admin_reassigned' : 'admin_cleared';
		const transition = this.change(
			target?.minecraft_username ?? null,
			reason,
			detail,
			admin.id,
		);
		this.announce(
			this.message(transition.previous_username, transition.next_username, reason, detail),
			transition.previous_username,
			transition.next_username,
			undefined,
			state.acquired_by_kill === 1,
		);
		return this.publicState();
	}

	adminWarnings(targetUsername: string) {
		const target = this.account(targetUsername);
		if (!target) throw new NotFoundException('Player not found');
		const profile = this.database.connection
			.select({ optedOut: playerProfiles.big_boss_opt_out })
			.from(playerProfiles)
			.where(eq(playerProfiles.user_id, target.id))
			.get();
		const cooldown =
			Date.now() - this.progress(target.minecraft_username).last_held_at_unix_ms <
			COOLDOWN_MS;
		return { optedOut: profile?.optedOut === 1, cooldown };
	}

	holderName() {
		return { minecraftUsername: this.state().holder_username ?? '' };
	}

	publicState() {
		const state = this.state();
		const accounts = this.database.connection.select().from(users).all();
		const profiles = this.database.connection.select().from(playerProfiles).all();
		const byName = new Map(
			accounts.map((user) => [user.minecraft_username.toLowerCase(), user]),
		);
		const byId = new Map(profiles.map((profile) => [profile.user_id, profile]));
		const person = (username: string | null) => {
			if (!username) return null;
			const account = byName.get(username.toLowerCase());
			const profile = account ? byId.get(account.id) : null;
			return {
				minecraftUsername: username,
				userId: account?.id ?? null,
				avatarUrl: playerAvatarUrl(account?.minecraft_uuid ?? null),
				nickname: profile?.preferred_name ?? '',
			};
		};
		const transitions = this.database.connection
			.select()
			.from(bigBossTransitions)
			.orderBy(desc(bigBossTransitions.at_unix_ms), desc(bigBossTransitions.id))
			.all();
		let endedAt: number | null = null;
		const reigns = [] as {
			holder: ReturnType<typeof person>;
			startedAtUnixMs: number;
			endedAtUnixMs: number | null;
			transitionId: number;
		}[];
		for (const transition of transitions) {
			if (transition.next_username)
				reigns.push({
					holder: person(transition.next_username),
					startedAtUnixMs: transition.at_unix_ms,
					endedAtUnixMs: endedAt,
					transitionId: transition.id,
				});
			endedAt = transition.at_unix_ms;
		}
		return {
			holder: person(state.holder_username),
			reignStartedAtUnixMs: state.reign_started_at_unix_ms,
			online:
				!!state.last_sample_at_unix_ms &&
				Date.now() - state.last_sample_at_unix_ms < 10_000,
			position:
				state.last_dimension &&
				state.last_x !== null &&
				state.last_y !== null &&
				state.last_z !== null
					? {
							dimension: state.last_dimension,
							x: state.last_x,
							y: state.last_y,
							z: state.last_z,
							inClaim: state.last_in_claim === 1,
							sampledAtUnixMs: state.last_sample_at_unix_ms,
						}
					: null,
			transitions: transitions.map((entry) => ({
				id: entry.id,
				atUnixMs: entry.at_unix_ms,
				previous: person(entry.previous_username),
				next: person(entry.next_username),
				reason: entry.reason,
				detail: entry.detail,
				adminUserId: entry.admin_user_id,
			})),
			reigns,
		};
	}
}
