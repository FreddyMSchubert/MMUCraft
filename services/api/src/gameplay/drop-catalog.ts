import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

export interface DropDefinition {
	id: string;
	name: string;
	date: string;
	description: string;
	emoji: string;
	colors: { percentage: number; color: string }[];
}

const roots = [
	join(process.cwd(), 'content'),
	join(process.cwd(), '..', '..', 'minecraft', 'main', 'data'),
	join(process.cwd(), 'minecraft', 'main', 'data'),
];

export function loadDrops(): DropDefinition[] {
	const root = roots.find((candidate) => existsSync(join(candidate, 'drops.json')));
	if (!root) throw new Error('drops.json was not found');
	const drops = JSON.parse(readFileSync(join(root, 'drops.json'), 'utf8')) as DropDefinition[];
	const ids = new Set<string>();
	for (const drop of drops) {
		if (ids.has(drop.id)) throw new Error(`Duplicate drop id: ${drop.id}`);
		ids.add(drop.id);
		if (!drop.emoji || !Array.isArray(drop.colors) || drop.colors.length < 2)
			throw new Error(`Missing indicator for drop ${drop.id}`);
		let previous = -1;
		for (const stop of drop.colors) {
			if (
				!Number.isInteger(stop.percentage) ||
				stop.percentage < 0 ||
				stop.percentage > 100 ||
				stop.percentage <= previous ||
				!/^#[0-9a-fA-F]{6}$/.test(stop.color)
			)
				throw new Error(`Invalid color stop for drop ${drop.id}`);
			previous = stop.percentage;
		}
		if (drop.colors[0]?.percentage !== 0 || previous !== 100)
			throw new Error(`Drop ${drop.id} must fade from 0 to 100`);
	}
	return drops;
}

export function itemDropId(
	item: { craftable?: unknown; shopPurchasable?: unknown },
	drops: readonly DropDefinition[],
): string | null {
	const candidates: unknown[] = [];
	if (
		item.shopPurchasable &&
		typeof item.shopPurchasable === 'object' &&
		'gameplayToggle' in item.shopPurchasable
	)
		candidates.push(item.shopPurchasable.gameplayToggle);
	if (item.craftable && typeof item.craftable === 'object' && 'recipes' in item.craftable) {
		const recipes = item.craftable.recipes;
		if (Array.isArray(recipes)) {
			for (const recipe of recipes) {
				if (recipe && typeof recipe === 'object' && 'gameplayToggle' in recipe)
					candidates.push((recipe as { gameplayToggle: unknown }).gameplayToggle);
			}
		}
	}
	const byId = new Map(drops.map((drop) => [drop.id, drop]));
	const matches = candidates.filter((candidate): candidate is string => {
		if (candidate === undefined || candidate === null || candidate === '') return false;
		if (typeof candidate !== 'string' || !byId.has(candidate))
			throw new Error('Unknown item drop in item definition');
		return true;
	});
	matches.sort(
		(a, b) =>
			(byId.get(a)?.date ?? '').localeCompare(byId.get(b)?.date ?? '') || a.localeCompare(b),
	);
	return matches[0] ?? null;
}
