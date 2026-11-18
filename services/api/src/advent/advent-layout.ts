import { BadRequestException } from '@nestjs/common';

// shortcut: One 2026 season, add a season record before the next Advent.
export const ADVENT_YEAR = 2026;
export const ADVENT_WIDTH = 12;
export const ADVENT_DAYS = 24;
export const TEXTURE_ROOT =
	'https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.3/assets/minecraft/textures/block/';

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

const STREAK_INCREASES = new Map([
	[1, 3],
	[5, 4],
	[9, 5],
	[13, 6],
	[17, 7],
	[21, 10],
]);

function layer(texture: string, rotation = 0): AdventLayer {
	return { url: `${TEXTURE_ROOT}${texture}.png`, rotation };
}

export function defaultAdventLayout(): AdventLayout {
	const rows: (AdventCell | null)[][] = Array.from({ length: 12 }, (_, y) =>
		Array.from({ length: ADVENT_WIDTH }, (_, x) => {
			if (y < 3) {
				const trunk = (x === 1 || x === 5 || x === 9) && y === 2;
				const crown =
					Math.abs(x - 5) <= y ||
					(y === 1 && (x === 1 || x === 9)) ||
					(y === 2 && (Math.abs(x - 1) <= 1 || Math.abs(x - 9) <= 1));
				return {
					background: [],
					foreground: trunk
						? [layer('spruce_log')]
						: crown
							? [
									layer('spruce_leaves'),
									...(x === 4 && y === 1 ? [layer('snow')] : []),
								]
							: [],
				};
			}
			const base =
				y === 3
					? 'snow'
					: y === 4
						? 'grass_block_top'
						: y === 5
							? 'dirt'
							: y <= 7
								? 'stone'
								: y <= 9
									? 'deepslate'
									: y === 10
										? 'sculk'
										: 'bedrock';
			const detail =
				y === 3 && x % 4 === 0
					? 'spruce_leaves'
					: y === 6 && x % 5 === 0
						? 'coal_ore'
						: y === 8 && x % 5 === 2
							? 'deepslate_diamond_ore'
							: y === 10 && x % 4 === 0
								? 'sculk_catalyst_top'
								: base;
			return { background: [layer(base)], foreground: [layer(detail)] };
		}),
	);
	const slots = Array.from({ length: ADVENT_DAYS }, (_, index) => ({
		x: [1, 5, 9, 2, 7, 10, 0, 4, 8, 3, 6, 11, 1, 5, 9, 2, 6, 10, 0, 4, 8, 3, 7, 11][index] ?? 0,
		y: 3 + Math.floor(index / 3),
	}));
	const days = Array.from({ length: ADVENT_DAYS }, (_, index) => ({
		day: index + 1,
		...(slots[(index * 7) % ADVENT_DAYS] ?? { x: 0, y: 0 }),
		lookbackDays: 0,
		streakIncrease: STREAK_INCREASES.get(index + 1) ?? 0,
	}));
	return { rows, days };
}

export function validateAdventLayout(input: unknown): AdventLayout {
	if (!input || typeof input !== 'object' || JSON.stringify(input).length > 200_000)
		throw new BadRequestException('The calendar layout is too large or invalid.');
	const layout = input as AdventLayout;
	if (
		!Array.isArray(layout.rows) ||
		layout.rows.length < 1 ||
		layout.rows.length > 48 ||
		!layout.rows.every(
			(row) =>
				Array.isArray(row) &&
				row.length === ADVENT_WIDTH &&
				row.every((cell) => cell === null || validCell(cell)),
		) ||
		!Array.isArray(layout.days) ||
		layout.days.length !== ADVENT_DAYS
	)
		throw new BadRequestException('The calendar grid or days are invalid.');
	const occupied = new Set<string>();
	for (let index = 0; index < ADVENT_DAYS; index++) {
		const day = layout.days[index];
		if (
			day?.day !== index + 1 ||
			!Number.isInteger(day.x) ||
			!Number.isInteger(day.y) ||
			!Number.isInteger(day.lookbackDays) ||
			day.lookbackDays < 0 ||
			day.lookbackDays > 31 ||
			!Number.isInteger(day.streakIncrease) ||
			day.streakIncrease < 0 ||
			day.streakIncrease > 100
		)
			throw new BadRequestException(`Day ${index + 1} has invalid settings.`);
		if (day.x === -1 && day.y === -1)
			throw new BadRequestException(`Place day ${index + 1} before saving.`);
		if (
			day.x < 0 ||
			day.x >= ADVENT_WIDTH ||
			day.y < 0 ||
			day.y >= layout.rows.length ||
			!layout.rows[day.y]?.[day.x]?.foreground.length ||
			occupied.has(`${day.x},${day.y}`)
		)
			throw new BadRequestException(`Day ${index + 1} needs its own foreground block.`);
		occupied.add(`${day.x},${day.y}`);
	}
	const perfectTotal = layout.days.reduce(
		(total, day) => total + day.streakIncrease * (ADVENT_DAYS - day.day + 1),
		0,
	);
	if (perfectTotal !== 400)
		throw new BadRequestException('A perfect streak must pay exactly 400 Dabloons.');
	return layout;
}

function validCell(value: unknown): boolean {
	if (!value || typeof value !== 'object') return false;
	const cell = value as AdventCell;
	return (
		Array.isArray(cell.background) &&
		Array.isArray(cell.foreground) &&
		[...cell.background, ...cell.foreground].every((layer: unknown) => {
			if (!layer || typeof layer !== 'object') return false;
			const entry = layer as AdventLayer;
			return (
				typeof entry.url === 'string' &&
				validTextureUrl(entry.url) &&
				Number.isFinite(entry.rotation) &&
				Math.abs(entry.rotation) <= 360
			);
		})
	);
}

function validTextureUrl(url: string) {
	const path = url.startsWith(TEXTURE_ROOT)
		? url.slice(TEXTURE_ROOT.length)
		: url.startsWith('/assets/')
			? url.slice('/assets/'.length)
			: '';
	return /^[\w/-]+\.png$/.test(path);
}
