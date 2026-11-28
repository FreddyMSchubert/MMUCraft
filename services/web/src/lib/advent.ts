export interface AdventLayer {
	url: string;
	rotation: number;
}

export interface AdventCell {
	background: AdventLayer[];
	foreground: AdventLayer[];
}

export interface AdventDay {
	day: number;
	x: number;
	y: number;
	lookbackDays: number;
	streakIncrease: number;
}

export interface AdventLayout {
	rows: (AdventCell | null)[][];
	days: AdventDay[];
}

export interface AdventRewardPreview {
	day: number;
	title: string;
	note: string;
	alternate_note: string;
}

export function londonAdventDay(now = new Date()) {
	const parts = new Intl.DateTimeFormat('en-GB', {
		timeZone: 'Europe/London',
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
		hour: '2-digit',
		hourCycle: 'h23',
	}).formatToParts(now);
	const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
	const date = new Date(
		Date.UTC(
			Number(values.year),
			Number(values.month) - 1,
			Number(values.day) - (Number(values.hour) < 4 ? 1 : 0),
		),
	);
	return date.getUTCFullYear() === 2026 && date.getUTCMonth() === 11 ? date.getUTCDate() : 0;
}

export function perfectStreakTotal(days: AdventDay[]) {
	return days.reduce((total, day) => total + day.streakIncrease * (25 - day.day), 0);
}
