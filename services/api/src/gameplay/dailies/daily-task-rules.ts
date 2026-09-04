export const LOGIN_BONUS_TASK_ID = 'login_bonus';
export const ADVANCEMENT_BONUS_TASK_ID = 'advancement_bonus';
export const DAILY_COMPLETION_TASK_ID = 'daily_completion';
export const STATIC_DAILY_TASK_IDS = [LOGIN_BONUS_TASK_ID, ADVANCEMENT_BONUS_TASK_ID] as const;
export const GENERATED_TASK_COUNT = 3;
export const DAILY_COMPLETION_BASE_REWARD = 20;
export const DAILY_COMPLETION_SUNDAY_BONUS = 12;
export const DAILY_COMPLETION_MEMBER_BONUS = 10;
export const DAILY_RESET_HOUR = 4;
export const DAILY_RESET_TIME_ZONE = 'Europe/London';
const MAX_TASK_JSON_LENGTH = 16_384;

export interface DailyTaskJson extends Record<string, unknown> {
	id: string;
	emoji: string;
	name: string;
	description: string;
	rewardDabloons: number;
	baseCost: number;
	rewardPerIteration?: number;
	current: number;
	max: number;
}

export interface DailyAdvancementTarget {
	advancementId: string;
	title: string;
	description: string;
	tabTitle: string;
	iconItem: string;
	baseRewardDabloons: number;
	bonusRewardDabloons: number;
	selectedAtUnixMs: number;
}

export function parseDailyTaskJson(json: string): DailyTaskJson {
	if (json.length > MAX_TASK_JSON_LENGTH) throw new Error('Daily task JSON is too large.');
	const value = JSON.parse(json) as unknown;
	if (!value || typeof value !== 'object') throw new Error('Daily task JSON is invalid.');
	const record = value as Record<string, unknown>;
	if (
		typeof record.id !== 'string' ||
		!/^[a-z0-9_:.-]{1,160}$/.test(record.id) ||
		typeof record.emoji !== 'string' ||
		record.emoji.length === 0 ||
		record.emoji.length > 16 ||
		typeof record.name !== 'string' ||
		record.name.length === 0 ||
		record.name.length > 120 ||
		typeof record.description !== 'string' ||
		record.description.length > 500 ||
		('progressLabel' in record &&
			(typeof record.progressLabel !== 'string' || record.progressLabel.length > 80)) ||
		('progressUnit' in record &&
			(typeof record.progressUnit !== 'string' || record.progressUnit.length > 80)) ||
		('rewardPerIteration' in record &&
			(typeof record.rewardPerIteration !== 'number' ||
				!Number.isFinite(record.rewardPerIteration) ||
				record.rewardPerIteration < 0)) ||
		typeof record.baseCost !== 'number' ||
		!Number.isInteger(record.baseCost) ||
		record.baseCost < 0 ||
		typeof record.rewardDabloons !== 'number' ||
		!Number.isInteger(record.rewardDabloons) ||
		record.rewardDabloons < 0 ||
		typeof record.current !== 'number' ||
		!Number.isInteger(record.current) ||
		record.current < 0 ||
		typeof record.max !== 'number' ||
		!Number.isInteger(record.max) ||
		record.max === 0 ||
		record.max < -1 ||
		(record.max > 0 && record.current > record.max)
	)
		throw new Error('Daily task JSON is invalid.');
	return record as DailyTaskJson;
}

export function currentDailyPeriodKey(now = new Date()) {
	const parts = new Intl.DateTimeFormat('en-CA', {
		timeZone: DAILY_RESET_TIME_ZONE,
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
		hour: '2-digit',
		hourCycle: 'h23',
	}).formatToParts(now);
	const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
	const londonDate = new Date(
		Date.UTC(Number(values.year), Number(values.month) - 1, Number(values.day)),
	);
	if (Number(values.hour) < DAILY_RESET_HOUR) londonDate.setUTCDate(londonDate.getUTCDate() - 1);
	return londonDate.toISOString().slice(0, 10);
}

export function calculateLoginStreak(
	claimedPeriodKeys: string[],
	currentPeriodKey: string,
	isMember: boolean,
) {
	const sortedKeys = [...new Set(claimedPeriodKeys)]
		.filter((key) => key <= currentPeriodKey)
		.sort();
	let streak = 0;
	let previousKey: string | null = null;
	for (const key of sortedKeys) {
		if (previousKey === null) streak = 1;
		else {
			const missedDays = Math.max(0, daysBetweenPeriodKeys(previousKey, key) - 1);
			streak =
				missedDays === 0 ? streak + 1 : isMember ? halveStreak(streak, missedDays) + 1 : 1;
		}
		previousKey = key;
	}
	if (previousKey === null || previousKey === currentPeriodKey) return streak;
	const missedDays = Math.max(0, daysBetweenPeriodKeys(previousKey, currentPeriodKey) - 1);
	return missedDays === 0 ? streak : isMember ? halveStreak(streak, missedDays) : 0;
}

export function rewardForStreak(streak: number) {
	return streak <= 1 ? 3 : streak + 3;
}

export function dailyAdvancementBonus(baseRewardDabloons: number) {
	const bounded = Math.max(5, Math.min(39, baseRewardDabloons));
	return Math.max(10, Math.ceil(bounded / 10) * 10 - bounded);
}

export function dailyCompletionReward(periodKey: string, isMember: boolean) {
	const isSunday = new Date(`${periodKey}T00:00:00Z`).getUTCDay() === 0;
	return {
		isSunday,
		total:
			DAILY_COMPLETION_BASE_REWARD +
			(isSunday ? DAILY_COMPLETION_SUNDAY_BONUS : 0) +
			(isMember ? DAILY_COMPLETION_MEMBER_BONUS : 0),
	};
}

function halveStreak(streak: number, days: number) {
	return Math.floor(streak / 2 ** days);
}

function daysBetweenPeriodKeys(from: string, to: string) {
	return Math.round(
		(Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000,
	);
}
