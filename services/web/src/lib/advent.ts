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

export function londonAdventDay() {
	const parts = new Intl.DateTimeFormat('en-GB', {
		timeZone: 'Europe/London',
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
	}).formatToParts(new Date());
	const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
	return Number(values.year) === 2026 && Number(values.month) === 12 ? Number(values.day) : 0;
}

export function perfectStreakTotal(days: AdventDay[]) {
	return days.reduce((total, day) => total + day.streakIncrease * (25 - day.day), 0);
}
