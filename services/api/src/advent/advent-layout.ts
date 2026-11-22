import { BadRequestException } from '@nestjs/common';

// shortcut: One 2026 season, add a season record before the next Advent.
export const ADVENT_YEAR = 2026;
export const ADVENT_WIDTH = 16;
export const ADVENT_DAYS = 24;
export const TEXTURE_ROOT =
	'https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.3/assets/minecraft/textures/block/';
export const CHEST_TEXTURE_URL =
	'https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.3/assets/minecraft/textures/entity/chest/christmas.png';
export const SNAPSHOT_TEXTURE_ROOT =
	'https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.4-snapshot-5/assets/minecraft/textures/block/';

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

export function isMineableCell(cell: AdventCell | null | undefined): boolean {
	return (
		!!cell?.foreground.length &&
		!cell.foreground.some((layer) => layer.url.endsWith('/bedrock.png'))
	);
}

const STREAK_INCREASES = new Map([
	[1, 3],
	[3, 2],
	[5, 3],
	[7, 2],
	[9, 2],
	[11, 2],
	[13, 3],
	[15, 2],
	[17, 2],
	[19, 2],
	[21, 10],
	[23, 2],
]);

function layer(texture: string, rotation = 0): AdventLayer {
	return { url: `${TEXTURE_ROOT}${texture}.png`, rotation };
}

function snapshotLayer(texture: string): AdventLayer {
	return { url: `${SNAPSHOT_TEXTURE_ROOT}${texture}.png`, rotation: 0 };
}

function chestLayer(): AdventLayer {
	return { url: CHEST_TEXTURE_URL, rotation: 0 };
}

function required<T>(values: readonly T[], index: number): T {
	const value = values[index];
	if (value === undefined) throw new Error('Default Advent index is outside the grid.');
	return value;
}

export function defaultAdventLayout(): AdventLayout {
	const height = 116;
	const rows: AdventCell[][] = Array.from({ length: height }, () =>
		Array.from({ length: ADVENT_WIDTH }, () => ({ background: [], foreground: [] })),
	);
	const surface = [27, 27, 27, 28, 28, 27, 27, 28, 28, 27, 27, 27, 28, 27, 27, 27];
	const deepStart = [85, 86, 86, 87, 85, 86, 87, 86, 85, 86, 86, 87, 86, 85, 86, 87];
	const tile = (x: number, y: number) => {
		const cell = rows[y]?.[x];
		if (!cell) throw new Error('Default Advent cell is outside the grid.');
		return cell;
	};
	const paint = (x: number, y: number, background: AdventLayer[], foreground: AdventLayer[]) => {
		const row = rows[y];
		if (!row || x < 0 || x >= ADVENT_WIDTH)
			throw new Error('Default Advent cell is outside the grid.');
		row[x] = { background, foreground };
	};
	const front = (x: number, y: number, ...textures: string[]) => {
		tile(x, y).foreground = textures.map((texture) => layer(texture));
	};
	const patches: { texture: string; spans: [number, number, number][] }[] = [
		{
			texture: 'granite',
			spans: [
				[30, 1, 2],
				[31, 0, 4],
				[32, 0, 3],
				[33, 1, 5],
				[47, 12, 13],
				[48, 10, 14],
				[49, 11, 15],
			],
		},
		{
			texture: 'andesite',
			spans: [
				[30, 9, 11],
				[31, 8, 12],
				[32, 9, 13],
				[33, 10, 12],
				[77, 9, 11],
				[78, 8, 12],
				[79, 9, 13],
				[80, 10, 12],
			],
		},
		{
			texture: 'diorite',
			spans: [
				[54, 2, 3],
				[55, 1, 5],
				[56, 0, 4],
				[57, 0, 5],
				[58, 2, 5],
				[59, 3, 4],
			],
		},
		{
			texture: 'tuff',
			spans: [
				[90, 1, 2],
				[91, 0, 4],
				[92, 1, 5],
				[93, 1, 4],
				[94, 2, 4],
				[98, 12, 14],
				[99, 11, 15],
				[100, 12, 15],
				[101, 13, 14],
			],
		},
	];
	const rock = (x: number, y: number) => {
		for (const patch of patches)
			for (const [row, left, right] of patch.spans)
				if (y === row && x >= left && x <= right) return patch.texture;
		return y >= required(deepStart, x) ? 'deepslate' : 'stone';
	};

	for (let y = 27; y < height; y++) {
		for (let x = 0; x < ADVENT_WIDTH; x++) {
			if (y < required(surface, x)) continue;
			const depth = y - required(surface, x);
			const material = depth === 0 ? 'grass_block_snow' : depth < 3 ? 'dirt' : rock(x, y);
			paint(x, y, [layer(material)], [layer(material)]);
			if (depth === 0) tile(x, y).foreground.unshift(layer('snow'));
		}
	}

	const crown = [0, 0, 1, 1, 2, 2, 3, 2, 3, 4, 4, 3, 4, 5, 5, 4, 5, 6, 6, 5, 6, 7, 7];
	for (let y = 0; y < crown.length; y++) {
		const reach = required(crown, y);
		const above = y ? required(crown, y - 1) : -1;
		for (let x = 7 - reach; x <= 8 + reach; x++) {
			const edge = x < 7 - above || x > 8 + above;
			const foreground = [layer('spruce_leaves'), layer('moss_block')];
			if (edge || (x + y * 3) % 19 === 0) foreground.unshift(layer('snow'));
			tile(x, y).foreground = foreground;
		}
	}
	for (let y = 23; y <= 28; y++) {
		front(7, y, 'spruce_log');
		front(8, y, 'spruce_log');
	}
	front(7, 0, 'gold_block');
	front(8, 0, 'gold_block');
	for (const [y, texture] of [
		[6, 'glowstone'],
		[9, 'redstone_lamp_on'],
		[13, 'glowstone'],
		[17, 'gold_block'],
		[20, 'redstone_lamp_on'],
		[22, 'glowstone'],
	] as const) {
		const reach = required(crown, y);
		tile(7 - reach, y).foreground.unshift(layer(texture));
		tile(8 + reach, y).foreground.unshift(layer(texture));
	}
	for (const x of [0, 1, 2, 13, 14, 15]) {
		const y = required(surface, x) - 1;
		front(x, y, 'sweet_berry_bush_stage3');
	}
	for (const x of [4, 5, 10, 11])
		tile(x, required(surface, x) - 1).background.push(layer('sweet_berry_bush_stage3'));

	const veins: [string, [number, number][]][] = [
		[
			'coal_ore',
			[
				[1, 31],
				[2, 31],
				[2, 32],
				[3, 32],
				[3, 33],
			],
		],
		[
			'coal_ore',
			[
				[0, 52],
				[1, 52],
				[1, 53],
				[2, 53],
			],
		],
		[
			'emerald_ore',
			[
				[5, 30],
				[6, 30],
			],
		],
		[
			'copper_ore',
			[
				[13, 30],
				[14, 30],
				[14, 31],
				[15, 31],
				[15, 32],
			],
		],
		[
			'iron_ore',
			[
				[1, 48],
				[2, 48],
				[2, 49],
				[3, 49],
			],
		],
		[
			'iron_ore',
			[
				[1, 59],
				[2, 59],
				[2, 60],
				[3, 60],
			],
		],
		[
			'lapis_ore',
			[
				[1, 64],
				[2, 64],
				[2, 65],
				[3, 65],
			],
		],
		[
			'gold_ore',
			[
				[11, 79],
				[12, 79],
				[12, 80],
				[13, 80],
			],
		],
		[
			'copper_ore',
			[
				[12, 83],
				[13, 83],
				[13, 84],
				[14, 84],
			],
		],
		[
			'deepslate_iron_ore',
			[
				[1, 92],
				[2, 92],
				[2, 93],
				[3, 93],
			],
		],
		[
			'deepslate_redstone_ore',
			[
				[1, 98],
				[2, 98],
				[2, 99],
				[3, 99],
			],
		],
		[
			'deepslate_redstone_ore',
			[
				[9, 99],
				[10, 99],
				[10, 100],
				[11, 99],
			],
		],
		[
			'deepslate_diamond_ore',
			[
				[5, 99],
				[6, 99],
				[6, 100],
				[7, 100],
				[7, 101],
			],
		],
	];
	for (const [ore, positions] of veins) for (const [x, y] of positions) front(x, y, ore);
	for (const [ore, positions] of [
		[
			'coal_ore',
			[
				[0, 55],
				[1, 55],
				[1, 56],
			],
		],
		[
			'iron_ore',
			[
				[1, 59],
				[2, 59],
				[2, 60],
			],
		],
		[
			'copper_ore',
			[
				[12, 82],
				[13, 82],
				[13, 83],
			],
		],
		[
			'deepslate_diamond_ore',
			[
				[4, 99],
				[4, 100],
				[5, 100],
			],
		],
	] as [string, [number, number][]][])
		for (const [x, y] of positions) tile(x, y).background.push(layer(ore));

	// The ice boundary and the open tunnel have separate, uneven contours.
	const iceOuterTop = [34, 33, 34, 35, 34, 32, 33, 35, 34, 33, 34, 32, 33, 34, 35, 34];
	const iceOuterFloor = [47, 47, 46, 48, 47, 48, 49, 47, 47, 49, 48, 47, 48, 47, 46, 47];
	const iceTop = [39, 38, 37, 38, 39, 36, 37, 39, 38, 36, 37, 39, 39, 38, 37, 38];
	const iceFloor = [43, 44, 45, 44, 43, 45, 46, 44, 45, 45, 46, 44, 43, 45, 44, 43];
	for (let x = 0; x < ADVENT_WIDTH; x++) {
		for (let y = 32; y <= 49; y++) tile(x, y).background = [layer('packed_ice')];
		for (let y = required(iceOuterTop, x); y <= required(iceOuterFloor, x); y++) {
			const calcite =
				(x <= 2 && y <= 36) ||
				(x >= 7 && x <= 9 && y <= 35) ||
				(x >= 12 && y >= 45 && y <= 47);
			const wall = layer(calcite ? 'calcite' : 'packed_ice');
			const open = y >= required(iceTop, x) && y <= required(iceFloor, x);
			paint(
				x,
				y,
				[layer(open ? 'blue_ice' : calcite ? 'calcite' : 'packed_ice')],
				open ? [] : [wall],
			);
		}
	}
	for (const x of [1, 4, 8, 11, 14]) {
		const y = required(iceTop, x);
		tile(x, y).foreground = [snapshotLayer('icicle_down_base')];
		tile(x, y + 1).foreground = [snapshotLayer('icicle_down_tip')];
	}
	for (const x of [3, 7, 10, 13])
		tile(x, required(iceFloor, x)).foreground = [snapshotLayer('ice_crystal')];
	for (const x of [2, 6, 10, 13]) {
		const top = required(iceTop, x);
		tile(x, top).background.push(snapshotLayer('icicle_down_base'));
		tile(x, top + 1).background.push(snapshotLayer('icicle_down_tip'));
	}
	for (const x of [5, 9, 15])
		tile(x, required(iceFloor, x)).background.push(snapshotLayer('ice_crystal'));
	for (const [x, y] of [
		[5, 36],
		[5, 37],
		[5, 38],
		[6, 37],
		[12, 43],
		[12, 44],
	] as const)
		front(x, y, 'packed_ice');
	for (const x of [2, 9, 15])
		tile(x, required(iceFloor, x) + 1).foreground.unshift(layer('snow'));

	// The room has complete cobblestone walls, a mossy floor, and a short entrance.
	for (let y = 51; y <= 58; y++) {
		for (let x = 7; x <= 14; x++) {
			const material = y === 58 && x % 3 !== 0 ? 'mossy_cobblestone' : 'cobblestone';
			const wall = layer(material);
			const entrance = x === 7 && (y === 54 || y === 55);
			const room = x > 7 && x < 14 && y > 51 && y < 58;
			paint(x, y, [wall], entrance || room ? [] : [wall]);
		}
	}
	for (let x = 0; x < 7; x++) for (const y of [54, 55]) tile(x, y).foreground = [];
	front(10, 57, 'spawner');
	tile(9, 57).foreground = [chestLayer()];
	tile(12, 57).foreground = [chestLayer()];
	front(8, 52, 'cobweb');
	front(13, 53, 'cobweb');
	front(8, 57, 'jukebox_side');

	// The lush cave narrows to a rounded end on the left and opens to the right.
	const lushTop = [67, 66, 64, 62, 63, 61, 62, 60, 61, 62, 63, 62];
	const lushFloor = [68, 70, 71, 72, 73, 74, 73, 74, 73, 73, 72, 72];
	for (let x = 4; x < ADVENT_WIDTH; x++) {
		const top = required(lushTop, x - 4);
		const floor = required(lushFloor, x - 4);
		for (let y = top - 2; y <= floor + 2; y++) {
			const open = y >= top && y <= floor;
			const moss = layer('moss_block');
			const nearWall = y >= top - 1 && y <= floor + 1;
			if (open) paint(x, y, [moss], []);
			else if (nearWall) paint(x, y, [moss], [moss]);
		}
	}
	for (const x of [7, 9, 11, 14]) {
		const top = required(lushTop, x - 4);
		tile(x, top).foreground = [layer('cave_vines')];
		tile(x, top + 1).foreground = [layer('cave_vines_plant')];
		tile(x, top + 2).foreground = [layer('cave_vines_plant_lit')];
		tile(x, top + 3).foreground = [layer('cave_vines_lit')];
	}
	for (const x of [5, 6, 10, 12, 13]) {
		const top = required(lushTop, x - 4);
		for (const [offset, texture] of [
			[0, 'cave_vines'],
			[1, 'cave_vines_plant'],
			[2, 'cave_vines_plant_lit'],
			[3, 'cave_vines_lit'],
		] as const)
			tile(x, top + offset).background.push(layer(texture));
	}
	front(8, 73, 'clay');
	front(9, 74, 'clay');
	front(10, 73, 'clay');
	front(9, 73, 'small_dripleaf_side');
	front(10, 70, 'big_dripleaf_top');
	front(10, 71, 'big_dripleaf_stem');
	front(10, 72, 'big_dripleaf_stem');

	// The irregular geode shell opens toward the left edge of the slice.
	for (let y = 72; y <= 86; y++) {
		for (let x = 0; x <= 9; x++) {
			const radius = ((x - 1.5) / 7.5) ** 2 + ((y - 79) / 7.5) ** 2;
			const edge = radius + Math.sin(x * 1.7 + y * 0.6) * 0.025;
			if (edge > 1) continue;
			const material =
				edge > 0.72 ? 'smooth_basalt' : edge > 0.52 ? 'calcite' : 'amethyst_block';
			const hollow = edge < 0.31;
			paint(x, y, [layer('amethyst_block')], hollow ? [] : [layer(material)]);
		}
	}
	for (const [budX, budY, crystalX, crystalY, texture, rotation] of [
		[0, 75, 0, 76, 'small_amethyst_bud', 180],
		[1, 74, 1, 75, 'medium_amethyst_bud', 180],
		[4, 75, 4, 76, 'small_amethyst_bud', 180],
		[5, 76, 5, 77, 'large_amethyst_bud', 180],
		[2, 84, 2, 83, 'large_amethyst_bud', 0],
		[3, 84, 3, 83, 'small_amethyst_bud', 0],
		[4, 83, 4, 82, 'amethyst_cluster', 0],
		[5, 82, 5, 81, 'medium_amethyst_bud', 0],
	] as const) {
		front(budX, budY, 'budding_amethyst');
		tile(crystalX, crystalY).foreground = [layer(texture, rotation)];
	}
	for (const [x, y] of [
		[6, 77],
		[6, 79],
		[6, 81],
	] as const)
		front(x, y, 'budding_amethyst');
	// Dripstone replaces the tunnel surfaces, with tips supported by those surfaces.
	const dripTop = [92, 90, 89, 87, 88, 89, 87, 88, 90, 89, 88];
	const dripFloor = [93, 95, 97, 98, 97, 98, 97, 98, 96, 98, 96];
	for (let x = 5; x < ADVENT_WIDTH; x++) {
		const top = required(dripTop, x - 5);
		const floor = required(dripFloor, x - 5);
		for (let y = top - 1; y <= floor + 1; y++) {
			const wall = layer('dripstone_block');
			const open = y >= top && y <= floor;
			const nearWall = y === top - 1 || y === floor + 1;
			if (open) paint(x, y, [layer(rock(x, y))], []);
			else if (nearWall) paint(x, y, [wall], [wall]);
		}
	}
	for (const [x, length] of [
		[7, 2],
		[10, 4],
		[14, 3],
	] as const) {
		const top = required(dripTop, x - 5);
		front(x, top, 'pointed_dripstone_down_base');
		for (let offset = 1; offset < length - 1; offset++)
			front(x, top + offset, 'pointed_dripstone_down_middle');
		front(x, top + length - 1, 'pointed_dripstone_down_tip');
	}
	for (const [x, length] of [
		[8, 3],
		[12, 2],
		[15, 4],
	] as const) {
		const floor = required(dripFloor, x - 5);
		front(x, floor, 'pointed_dripstone_up_base');
		for (let offset = 1; offset < length - 1; offset++)
			front(x, floor - offset, 'pointed_dripstone_up_middle');
		front(x, floor - length + 1, 'pointed_dripstone_up_tip');
	}
	for (const [x, length] of [
		[6, 2],
		[9, 3],
		[13, 2],
	] as const) {
		const top = required(dripTop, x - 5);
		tile(x, top).background.push(layer('pointed_dripstone_down_base'));
		if (length > 2) tile(x, top + 1).background.push(layer('pointed_dripstone_down_middle'));
		tile(x, top + length - 1).background.push(layer('pointed_dripstone_down_tip'));
	}
	for (const [x, length] of [
		[6, 2],
		[11, 3],
		[14, 2],
	] as const) {
		const floor = required(dripFloor, x - 5);
		tile(x, floor).background.push(layer('pointed_dripstone_up_base'));
		if (length > 2) tile(x, floor - 1).background.push(layer('pointed_dripstone_up_middle'));
		tile(x, floor - length + 1).background.push(layer('pointed_dripstone_up_tip'));
	}
	for (const [x, y] of [
		[13, 96],
		[13, 97],
		[13, 98],
		[14, 97],
		[9, 87],
		[9, 88],
	] as const)
		front(x, y, 'dripstone_block');

	// Sculk coats the ceiling and floor. Sensors and shriekers stand on the floor.
	const sculkCaveTop = [
		104, 104, 103, 104, 104, 103, 103, 104, 104, 103, 104, 104, 103, 104, 104, 105,
	];
	const sculkCaveFloor = [
		108, 108, 109, 109, 108, 109, 109, 108, 109, 109, 108, 109, 109, 108, 109, 108,
	];
	const bedrockTop = [
		113, 114, 114, 115, 113, 114, 115, 113, 114, 115, 113, 114, 113, 115, 114, 113,
	];
	const seeded = (x: number, y: number) => {
		const value = Math.sin((x + 1) * 127.1 + (y + ADVENT_YEAR) * 311.7) * 43758.5453;
		return value - Math.floor(value);
	};
	for (let x = 0; x < ADVENT_WIDTH; x++) {
		const top = required(sculkCaveTop, x);
		const floor = required(sculkCaveFloor, x);
		const roofThickness = seeded(x, 71) > 0.5 ? 2 : 1;
		const floorThickness = seeded(x, 83) > 0.5 ? 2 : 1;
		for (let y = top - roofThickness; y <= floor + floorThickness; y++) {
			const coat = y < top || y > floor;
			const backgroundTexture = coat ? 'sculk' : 'deepslate';
			const rotation =
				backgroundTexture === 'sculk' ? Math.floor(seeded(x + 31, y + 17) * 4) * 90 : 0;
			paint(x, y, [layer(backgroundTexture, rotation)], coat ? [layer('sculk')] : []);
		}
	}
	for (const x of [2, 6, 10, 14]) front(x, required(sculkCaveFloor, x), 'sculk_sensor_side');
	for (const x of [4, 12]) front(x, required(sculkCaveFloor, x), 'sculk_shrieker_side');
	front(8, required(sculkCaveFloor, 8), 'sculk_catalyst_side');
	for (const x of [1, 7, 11, 15])
		tile(x, required(sculkCaveFloor, x)).background.push(layer('sculk_sensor_side'));
	tile(9, required(sculkCaveFloor, 9)).background.push(layer('sculk_shrieker_side'));

	for (let x = 0; x < ADVENT_WIDTH; x++)
		for (let y = required(bedrockTop, x); y < height; y++) {
			tile(x, y).background = [layer('bedrock')];
			front(x, y, 'bedrock');
		}

	const slots: [number, number][] = [
		[7, 25],
		[10, 27],
		[5, 30],
		[2, 48],
		[13, 26],
		[2, 31],
		[14, 31],
		[2, 26],
		[3, 45],
		[8, 39],
		[12, 57],
		[7, 0],
		[2, 64],
		[10, 57],
		[7, 65],
		[4, 82],
		[9, 74],
		[11, 63],
		[15, 73],
		[12, 80],
		[10, 92],
		[12, 109],
		[6, 99],
		[8, 0],
	];

	const days = Array.from({ length: ADVENT_DAYS }, (_, index) => ({
		day: index + 1,
		x: required(slots, index)[0],
		y: required(slots, index)[1],
		lookbackDays: 0,
		streakIncrease: STREAK_INCREASES.get(index + 1) ?? 0,
	}));
	return { rows, days };
}

export function validateAdventLayout(input: unknown): AdventLayout {
	if (!input || typeof input !== 'object' || JSON.stringify(input).length > 650_000)
		throw new BadRequestException('The calendar layout is too large or invalid.');
	const layout = input as AdventLayout;
	if (
		!Array.isArray(layout.rows) ||
		layout.rows.length < 1 ||
		layout.rows.length > 128 ||
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
			!isMineableCell(layout.rows[day.y]?.[day.x]) ||
			occupied.has(`${day.x},${day.y}`)
		)
			throw new BadRequestException(`Day ${index + 1} needs its own foreground block.`);
		occupied.add(`${day.x},${day.y}`);
	}
	if (
		!layout.rows.some((row, y) =>
			row.some((cell, x) => isMineableCell(cell) && !occupied.has(`${x},${y}`)),
		)
	)
		throw new BadRequestException(
			'Leave at least one unnumbered foreground block for the hidden present.',
		);
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
		: url.startsWith(SNAPSHOT_TEXTURE_ROOT)
			? url.slice(SNAPSHOT_TEXTURE_ROOT.length)
			: url === CHEST_TEXTURE_URL
				? 'christmas.png'
				: url.startsWith('/assets/')
					? url.slice('/assets/'.length)
					: '';
	return /^[\w/-]+\.png$/.test(path);
}
