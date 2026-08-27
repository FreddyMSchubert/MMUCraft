export type TileResult = 'correct' | 'present' | 'absent' | 'skipped';
export interface WordleGuess {
	word: string;
	result: TileResult[];
}

export const MIN_WORD_LENGTH = 3;
export const BASE_GUESS_COUNT = 6;
export const WORDLE_STORAGE_PREFIX = 'mmu-mcsoc-wordle';
const WORD_SHUFFLE_KEY = '7d16841a9c3e35e4cc58cd892c6525d7';
const WORD_CYCLE_DAY_OFFSET = 82;
const MS_PER_DAY = 86_400_000;
const UK_TIME_ZONE = 'Europe/London';

export function getMaxGuesses(wordLength: number) {
	if (wordLength === 4) return BASE_GUESS_COUNT + 1;
	return BASE_GUESS_COUNT + Math.ceil(Math.max(0, wordLength - 5) / 2);
}

export function getDifficulty(wordLength: number) {
	if (wordLength <= 3 || wordLength === 7) return { label: 'Hard', emoji: '⚔️', tone: 'hard' };
	if (wordLength === 4 || wordLength === 6)
		return { label: 'Tricky', emoji: '🧩', tone: 'tricky' };
	if (wordLength === 5) return { label: 'Normal', emoji: '🙂', tone: 'normal' };
	if (wordLength === 8) return { label: 'Difficult', emoji: '🧗', tone: 'difficult' };
	if (wordLength === 9) return { label: 'Extreme', emoji: '🔥', tone: 'extreme' };
	if (wordLength === 10) return { label: 'Ultra', emoji: '⚡', tone: 'ultra' };
	return { label: 'Impossible', emoji: '☠️', tone: 'impossible' };
}

export function getDailyAnswer(words: readonly string[], dateKey: string) {
	const shuffled = seededShuffle(words, WORD_SHUFFLE_KEY);
	const [year, month, day] = dateKey.split('-').map(Number);
	const dayNumber =
		Math.floor(Date.UTC(year, month - 1, day) / MS_PER_DAY) + WORD_CYCLE_DAY_OFFSET;
	return shuffled[dayNumber % shuffled.length];
}

export function scoreGuess(guess: string, answer: string): TileResult[] {
	const result = Array<TileResult>(answer.length).fill('skipped');
	const remaining: Record<string, number> = {};

	for (let index = 0; index < answer.length; index += 1) {
		if (index < guess.length && guess[index] === answer[index]) result[index] = 'correct';
		else remaining[answer[index]] = (remaining[answer[index]] ?? 0) + 1;
	}

	for (let index = 0; index < guess.length; index += 1) {
		if (result[index] === 'correct') continue;
		if ((remaining[guess[index]] ?? 0) > 0) {
			result[index] = 'present';
			remaining[guess[index]] -= 1;
		} else result[index] = 'absent';
	}

	return result;
}

export function getUKDateKey(date: Date) {
	const parts = getZonedParts(date);
	return `${parts.year}-${String(parts.month).padStart(2, '0')}-${String(parts.day).padStart(2, '0')}`;
}

export function formatDisplayDate(dateKey: string) {
	const [year, month, day] = dateKey.split('-').map(Number);
	return new Intl.DateTimeFormat('en-GB', {
		day: 'numeric',
		month: 'long',
		year: 'numeric',
	}).format(new Date(Date.UTC(year, month - 1, day, 12)));
}

export function getNextUKMidnight(date: Date) {
	const parts = getZonedParts(date);
	const nextDate = new Date(Date.UTC(parts.year, parts.month - 1, parts.day + 1));
	return zonedTimeToUtc({
		year: nextDate.getUTCFullYear(),
		month: nextDate.getUTCMonth() + 1,
		day: nextDate.getUTCDate(),
		hour: 0,
		minute: 0,
	});
}

export function getStorageKey(dateKey: string, answer: string) {
	return `${WORDLE_STORAGE_PREFIX}:${dateKey}:${answer}`;
}

function seededShuffle(words: readonly string[], key: string) {
	const shuffled = [...words];
	const random = createSeededRandom(hashString(key));
	for (let index = shuffled.length - 1; index > 0; index -= 1) {
		const swapIndex = Math.floor(random() * (index + 1));
		[shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]];
	}
	return shuffled;
}

function createSeededRandom(seed: number) {
	let value = seed;
	return () => {
		value += 0x6d2b79f5;
		let mixed = value;
		mixed = Math.imul(mixed ^ (mixed >>> 15), mixed | 1);
		mixed ^= mixed + Math.imul(mixed ^ (mixed >>> 7), mixed | 61);
		return ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296;
	};
}

function hashString(input: string) {
	let hash = 2166136261;
	for (let index = 0; index < input.length; index += 1) {
		hash ^= input.charCodeAt(index);
		hash = Math.imul(hash, 16777619);
	}
	return hash >>> 0;
}

function zonedTimeToUtc(parts: {
	year: number;
	month: number;
	day: number;
	hour: number;
	minute: number;
	second?: number;
}) {
	const base = Date.UTC(
		parts.year,
		parts.month - 1,
		parts.day,
		parts.hour,
		parts.minute,
		parts.second ?? 0,
	);
	let utc = base;
	for (let pass = 0; pass < 3; pass += 1) utc = base - getTimeZoneOffset(new Date(utc));
	return utc;
}

function getTimeZoneOffset(date: Date) {
	const parts = getZonedParts(date);
	const asUTC = Date.UTC(
		parts.year,
		parts.month - 1,
		parts.day,
		parts.hour,
		parts.minute,
		parts.second,
	);
	return asUTC - Math.floor(date.getTime() / 1000) * 1000;
}

function getZonedParts(date: Date) {
	const formatter = new Intl.DateTimeFormat('en-GB', {
		timeZone: UK_TIME_ZONE,
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
		hour: '2-digit',
		minute: '2-digit',
		second: '2-digit',
		hourCycle: 'h23',
	});
	const parts = Object.fromEntries(
		formatter.formatToParts(date).map((part) => [part.type, part.value]),
	);
	return {
		year: Number(parts.year),
		month: Number(parts.month),
		day: Number(parts.day),
		hour: Number(parts.hour),
		minute: Number(parts.minute),
		second: Number(parts.second),
	};
}
