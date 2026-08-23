import { Injectable } from '@nestjs/common';
import { Counter, Gauge, Histogram, Registry, collectDefaultMetrics } from 'prom-client';
import {
	claims,
	dailyClaims,
	DatabaseService,
	knowledgeReads,
	knowledgeUnlocks,
	playerMoneyEvents,
	playerStats,
	sessions,
	shopUnlocks,
	users,
} from '../database/database.service';
import {
	calculateLoginStreak,
	currentDailyPeriodKey,
	DAILY_COMPLETION_TASK_ID,
	LOGIN_BONUS_TASK_ID,
} from '../gameplay/dailies/daily-task-rules';
import { normalizeStatsJson, type PlayerStats } from '../players/player-statistics';

const registry = new Registry();
collectDefaultMetrics({ register: registry, prefix: 'mmucraft_api_' });

const httpRequests = counter(
	'mmucraft_api_http_requests_total',
	'HTTP requests handled by the API.',
	['method', 'route', 'status'],
);
const httpDuration = histogram(
	'mmucraft_api_http_request_duration_seconds',
	'API HTTP request duration in seconds.',
	['method', 'route', 'status'],
);
const grpcDuration = histogram(
	'mmucraft_api_grpc_request_duration_seconds',
	'Outbound API to Minecraft gRPC request duration in seconds.',
	['service', 'method', 'status'],
);
const snapshotDuration = histogram(
	'mmucraft_monitoring_database_snapshot_duration_seconds',
	'Time spent reading and aggregating monitoring data from SQLite.',
);

const totalUsers = gauge('mmucraft_users', 'Website accounts ever created.');
const activeSessions = gauge(
	'mmucraft_active_website_sessions',
	'Unexpired signed-in website sessions.',
);
const totalClaims = gauge('mmucraft_claims', 'Current claimed Minecraft chunks.');
const dailyClaimCount = gauge('mmucraft_daily_claims', 'Daily tasks claimed.', ['scope']);
const loginStreak = gauge('mmucraft_login_streak_days', 'Login streak length in days.', ['scope']);
const streakPlayers = gauge(
	'mmucraft_players_with_streak',
	'Players whose current login streak meets the threshold.',
	['days'],
);
const streakLeaderboard = gauge(
	'mmucraft_login_streak_leaderboard',
	'Current and all-time login streak leaders.',
	['scope', 'rank', 'player'],
);

const money = gauge('mmucraft_money_dabloons', 'Aggregate player economy values in dabloons.', [
	'measure',
]);
const richestPlayer = gauge('mmucraft_richest_player_dabloons', 'Largest player balance.', [
	'player',
]);
const balanceDistribution = gauge(
	'mmucraft_player_balance_distribution',
	'Players grouped into current balance bands.',
	['band'],
);
const unlocks = gauge('mmucraft_unlocks', 'Unlocks recorded by type.', ['type']);
export const charmsUpgraded = new Counter({
	name: 'mmucraft_charms_upgraded_total',
	help: 'Charm upgrades completed since API instrumentation began.',
	registers: [registry],
});

const play = gauge('mmucraft_player_play_seconds', 'Aggregate native Minecraft play-time values.', [
	'measure',
]);
const retention = gauge(
	'mmucraft_player_retention_ratio',
	'Fraction of players retained for at least the requested days after website signup.',
	['days'],
);
const churn = gauge(
	'mmucraft_player_churn_ratio',
	'Fraction of previously active players not seen for ten days.',
);
const activePlayers = gauge(
	'mmucraft_active_players',
	'Players seen within the requested number of days.',
	['days'],
);

const minecraftStat = gauge(
	'mmucraft_minecraft_stat',
	'Aggregate native Minecraft general, entity, and selected mined-block statistics.',
	['category', 'id', 'name', 'summary'],
);
const minecartDistance = gauge(
	'mmucraft_minecart_distance_meters',
	'Total native Minecraft minecart travel distance in meters.',
);
const minecartComparison = gauge(
	'mmucraft_minecart_distance_comparison',
	'Minecart distance expressed using a nearby comparison unit.',
	['unit'],
);
const topMob = gauge('mmucraft_top_mob', 'Most-killed or most-lethal mob.', ['kind', 'mob']);

interface RequestLike {
	method: string;
	routeOptions?: { url?: string };
}

@Injectable()
export class MonitoringService {
	private readonly requestStarts = new WeakMap<object, bigint>();
	private lastSnapshotAt = 0;

	constructor(private readonly database: DatabaseService) {}

	beginRequest(request: object) {
		this.requestStarts.set(request, process.hrtime.bigint());
	}

	finishRequest(request: RequestLike, status: number | 'aborted') {
		const started = this.requestStarts.get(request);
		this.requestStarts.delete(request);
		const route = request.routeOptions?.url ?? 'unmatched';
		if (!started || route === '/internal/metrics') return;
		const labels = { method: request.method, route, status: String(status) };
		httpRequests.inc(labels);
		httpDuration.observe(labels, Number(process.hrtime.bigint() - started) / 1_000_000_000);
	}

	async render() {
		if (Date.now() - this.lastSnapshotAt >= 15_000) this.refreshSnapshot();
		return await registry.metrics();
	}

	contentType() {
		return registry.contentType;
	}

	private refreshSnapshot() {
		const stopTimer = snapshotDuration.startTimer();
		try {
			const now = Date.now();
			// ponytail: one batched full-table snapshot is simplest for this small SQLite database;
			// replace individual reads with SQL aggregates if this measured duration becomes material.
			const userRows = this.database.connection.select().from(users).all();
			const statsRows = this.database.connection.select().from(playerStats).all();
			const sessionRows = this.database.connection.select().from(sessions).all();
			const claimRows = this.database.connection.select().from(claims).all();
			const dailyRows = this.database.connection.select().from(dailyClaims).all();
			const moneyRows = this.database.connection.select().from(playerMoneyEvents).all();
			const knowledgeRows = this.database.connection.select().from(knowledgeUnlocks).all();
			const readRows = this.database.connection.select().from(knowledgeReads).all();
			const shopRows = this.database.connection.select().from(shopUnlocks).all();
			const statsByUser = new Map(
				statsRows.map((row) => [row.user_id, normalizeStatsJson(row.stats_json)]),
			);

			totalUsers.set(userRows.length);
			activeSessions.set(sessionRows.filter((row) => row.expires_at_unix_ms > now).length);
			totalClaims.set(claimRows.length);
			this.refreshDailies(userRows, dailyRows);
			this.refreshEconomy(userRows, statsByUser, moneyRows);
			this.refreshUnlocks(knowledgeRows.length, readRows.length, shopRows);
			this.refreshPlayers(userRows, statsByUser, now);
			this.refreshMinecraftStats([...statsByUser.values()]);
			this.lastSnapshotAt = now;
		} finally {
			stopTimer();
		}
	}

	private refreshDailies(
		userRows: (typeof users.$inferSelect)[],
		dailyRows: (typeof dailyClaims.$inferSelect)[],
	) {
		const currentPeriod = currentDailyPeriodKey();
		dailyClaimCount.reset();
		dailyClaimCount.set({ scope: 'all_tasks_ever' }, dailyRows.length);
		dailyClaimCount.set(
			{ scope: 'all_dailies_ever' },
			dailyRows.filter((row) => row.task_id === DAILY_COMPLETION_TASK_ID).length,
		);
		dailyClaimCount.set(
			{ scope: 'all_dailies_today' },
			dailyRows.filter(
				(row) =>
					row.task_id === DAILY_COMPLETION_TASK_ID && row.period_key === currentPeriod,
			).length,
		);

		const loginDays = new Map<number, string[]>();
		for (const row of dailyRows) {
			if (row.task_id !== LOGIN_BONUS_TASK_ID) continue;
			const days = loginDays.get(row.user_id) ?? [];
			days.push(row.period_key);
			loginDays.set(row.user_id, days);
		}
		const streaks = userRows.map((user) => {
			const days = loginDays.get(user.id) ?? [];
			return {
				player: user.minecraft_username,
				current: calculateLoginStreak(days, currentPeriod, user.is_member === 1),
				allTime: longestConsecutiveRun(days),
			};
		});
		loginStreak.reset();
		loginStreak.set(
			{ scope: 'current_max' },
			Math.max(0, ...streaks.map((row) => row.current)),
		);
		loginStreak.set(
			{ scope: 'all_time_max' },
			Math.max(0, ...streaks.map((row) => row.allTime)),
		);
		streakPlayers.reset();
		streakPlayers.set({ days: '7' }, streaks.filter((row) => row.current >= 7).length);
		streakLeaderboard.reset();
		for (const scope of ['current', 'all_time'] as const) {
			const key = scope === 'current' ? 'current' : 'allTime';
			[...streaks]
				.sort((left, right) => right[key] - left[key])
				.slice(0, 10)
				.forEach((row, index) => {
					streakLeaderboard.set(
						{ scope, rank: String(index + 1), player: row.player },
						row[key],
					);
				});
		}
	}

	private refreshEconomy(
		userRows: (typeof users.$inferSelect)[],
		statsByUser: Map<number, PlayerStats>,
		moneyRows: (typeof playerMoneyEvents.$inferSelect)[],
	) {
		const issued = moneyRows
			.filter((row) => row.direction === 'earned')
			.reduce((total, row) => total + row.amount_dabloons, 0);
		const balances = userRows.map(
			(user) => statsByUser.get(user.id)?.money.balanceDabloons ?? 0,
		);
		const supply = sum(balances);
		money.reset();
		money.set({ measure: 'issued' }, issued);
		money.set({ measure: 'returned_or_removed' }, Math.max(0, issued - supply));
		money.set({ measure: 'player_supply' }, supply);
		money.set({ measure: 'median_balance' }, median(balances));

		const richest = userRows
			.map((user) => ({
				player: user.minecraft_username,
				balance: statsByUser.get(user.id)?.money.balanceDabloons ?? 0,
			}))
			.sort((left, right) => right.balance - left.balance)[0];
		richestPlayer.reset();
		if (richest) richestPlayer.set({ player: richest.player }, richest.balance);
		balanceDistribution.reset();
		for (const [band, minimum, maximum] of [
			['0', 0, 1],
			['1-9', 1, 10],
			['10-99', 10, 100],
			['100-999', 100, 1_000],
			['1k-9k', 1_000, 10_000],
			['10k+', 10_000, Number.POSITIVE_INFINITY],
		] as const) {
			balanceDistribution.set(
				{ band },
				balances.filter((balance) => balance >= minimum && balance < maximum).length,
			);
		}
	}

	private refreshUnlocks(
		knowledgeCount: number,
		readCount: number,
		shopRows: (typeof shopUnlocks.$inferSelect)[],
	) {
		unlocks.reset();
		unlocks.set({ type: 'knowledge_unlocked' }, knowledgeCount);
		unlocks.set({ type: 'knowledge_read' }, readCount);
		for (const type of ['charm', 'cosmetic']) {
			unlocks.set(
				{ type: `${type}_unlocked` },
				shopRows.filter((row) => row.unlock_type === type).length,
			);
		}
	}

	private refreshPlayers(
		userRows: (typeof users.$inferSelect)[],
		statsByUser: Map<number, PlayerStats>,
		now: number,
	) {
		const playSeconds = userRows.map(
			(user) => stat(statsByUser.get(user.id), 'custom', 'minecraft:play_time') / 20,
		);
		const sessions = userRows.map((user) =>
			stat(statsByUser.get(user.id), 'custom', 'minecraft:leave_game'),
		);
		const totalPlaySeconds = sum(playSeconds);
		play.reset();
		play.set({ measure: 'total' }, totalPlaySeconds);
		play.set({ measure: 'average_per_player' }, average(playSeconds));
		play.set({ measure: 'median_per_player' }, median(playSeconds));
		play.set({ measure: 'average_session' }, totalPlaySeconds / Math.max(1, sum(sessions)));
		play.set({ measure: 'sessions_ever' }, sum(sessions));

		const seenPlayers = userRows.flatMap((user) => {
			const lastPlayed = statsByUser.get(user.id)?.minecraft.lastPlayedAtUnixMs;
			return lastPlayed ? [{ user, lastPlayed }] : [];
		});
		retention.reset();
		for (const days of [1, 7, 30]) {
			retention.set(
				{ days: String(days) },
				ratio(
					seenPlayers.filter(
						({ user, lastPlayed }) =>
							lastPlayed - user.created_at_unix_ms >= days * 86_400_000,
					).length,
					seenPlayers.length,
				),
			);
		}
		churn.set(
			ratio(
				seenPlayers.filter(({ lastPlayed }) => now - lastPlayed >= 10 * 86_400_000).length,
				seenPlayers.length,
			),
		);
		activePlayers.reset();
		for (const days of [1, 7, 30]) {
			activePlayers.set(
				{ days: String(days) },
				seenPlayers.filter(({ lastPlayed }) => now - lastPlayed < days * 86_400_000).length,
			);
		}
	}

	private refreshMinecraftStats(playerStatistics: PlayerStats[]) {
		const definitions = new Map<string, { category: string; id: string; name: string }>();
		for (const player of playerStatistics) {
			for (const value of Object.values(player.minecraft.stats)) {
				if (['custom', 'killed', 'killed_by', 'mined'].includes(value.category)) {
					definitions.set(value.key, {
						category: value.category,
						id: value.id,
						name: value.label,
					});
				}
			}
		}

		minecraftStat.reset();
		const totals = new Map<string, number>();
		for (const [key, definition] of definitions) {
			const values = playerStatistics.map(
				(player) => player.minecraft.stats[key]?.value ?? 0,
			);
			const labels = definition;
			const total = sum(values);
			totals.set(key, total);
			minecraftStat.set({ ...labels, summary: 'total' }, total);
			minecraftStat.set({ ...labels, summary: 'average' }, average(values));
			minecraftStat.set({ ...labels, summary: 'median' }, median(values));
		}

		const minecartCm = totals.get('minecraft.custom.minecraft:minecart_one_cm') ?? 0;
		const minecartMeters = minecartCm / 100;
		minecartDistance.set(minecartMeters);
		const comparison = comparisonForMeters(minecartMeters);
		minecartComparison.reset();
		minecartComparison.set({ unit: comparison.name }, comparison.count);

		topMob.reset();
		for (const [kind, category] of [
			['most_killed', 'killed'],
			['most_player_deaths', 'killed_by'],
		] as const) {
			const winner = [...definitions.entries()]
				.filter(([, definition]) => definition.category === category)
				.map(([key, definition]) => ({ mob: definition.name, value: totals.get(key) ?? 0 }))
				.sort((left, right) => right.value - left.value)[0];
			if (winner?.value) topMob.set({ kind, mob: winner.mob }, winner.value);
		}
	}
}

export async function observeGrpc<T>(
	service: string,
	method: string,
	operation: () => Promise<T>,
): Promise<T> {
	const started = process.hrtime.bigint();
	try {
		const result = await operation();
		grpcDuration.observe(
			{ service, method, status: 'ok' },
			Number(process.hrtime.bigint() - started) / 1_000_000_000,
		);
		return result;
	} catch (error) {
		grpcDuration.observe(
			{ service, method, status: 'error' },
			Number(process.hrtime.bigint() - started) / 1_000_000_000,
		);
		throw error;
	}
}

function gauge(name: string, help: string, labelNames: string[] = []) {
	return new Gauge({ name, help, labelNames, registers: [registry] });
}

function counter(name: string, help: string, labelNames: string[] = []) {
	return new Counter({ name, help, labelNames, registers: [registry] });
}

function histogram(name: string, help: string, labelNames: string[] = []) {
	return new Histogram({
		name,
		help,
		labelNames,
		buckets: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
		registers: [registry],
	});
}

function stat(stats: PlayerStats | undefined, category: string, id: string) {
	return stats?.minecraft.stats[`minecraft.${category}.${id}`]?.value ?? 0;
}

function sum(values: number[]) {
	return values.reduce((total, value) => total + value, 0);
}

function average(values: number[]) {
	return values.length ? sum(values) / values.length : 0;
}

function median(values: number[]) {
	if (!values.length) return 0;
	const sorted = [...values].sort((left, right) => left - right);
	const middle = Math.floor(sorted.length / 2);
	return sorted.length % 2 === 0
		? ((sorted[middle - 1] ?? 0) + (sorted[middle] ?? 0)) / 2
		: (sorted[middle] ?? 0);
}

function ratio(numerator: number, denominator: number) {
	return denominator ? numerator / denominator : 0;
}

function longestConsecutiveRun(days: string[]) {
	const sorted = [...new Set(days)].sort();
	let longest = 0;
	let current = 0;
	let previous = 0;
	for (const day of sorted) {
		const value = Date.parse(`${day}T00:00:00Z`);
		current = previous && value - previous === 86_400_000 ? current + 1 : 1;
		longest = Math.max(longest, current);
		previous = value;
	}
	return longest;
}

function comparisonForMeters(meters: number) {
	const units = [
		['atom widths', 0.000_000_000_2],
		['iPod nano lengths', 0.0907],
		['hot dogs', 0.15],
		['banana lengths', 0.2],
		['T. rexes', 12],
		['Harry Potter Basilisks', 15.24],
		['blue whales', 30.5],
		['Olympic swimming pools', 50],
		['Statues of Liberty', 92.99],
		['football pitches', 105],
		['ISS widths', 108.5],
		['Titanic lengths', 269],
		['Eiffel Towers', 330],
		['Tokyo Towers', 333],
		['world-record Frisbee throws', 338],
		['Burj Khalifas', 828],
		['Vatican City widths', 850],
		['Golden Gate Bridges', 2_737],
		['Mount Everests', 8_849],
		['Manhattan lengths', 21_600],
		['English Channels', 33_300],
		['marathons', 42_195],
		['Death Star diameters', 160_000],
		['Grand Canyons', 446_000],
		['Route 66s', 3_940_000],
		['Amazon Rivers', 6_400_000],
		['trips to the Minecraft Far Lands', 12_550_821],
		['Great Walls of China', 21_196_000],
		['trips around Earth', 40_075_000],
		['trips to the Moon', 384_400_000],
		['trips to the Sun', 149_600_000_000],
	] as const;
	const upperIndex = units.findIndex(([, length]) => length > meters);
	let candidates: readonly (typeof units)[number][];
	if (upperIndex === 0) candidates = units.slice(0, 1);
	else if (upperIndex === -1) candidates = units.slice(-2);
	else candidates = units.slice(upperIndex - 1, upperIndex + 1);
	const [name, length] = candidates[Math.floor(Math.random() * candidates.length)] ?? units[0];
	return { name, count: meters / length };
}
