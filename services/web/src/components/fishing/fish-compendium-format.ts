import type { Fireworks } from 'fireworks-js';
import type React from 'react';
import {
	GROUPS,
	RARITIES,
	TAGS,
	type CompendiumFish,
	type FishSort,
	type Rarity,
} from './fish-compendium.types';

const RARITY_RANK = new Map(RARITIES.map((rarity, index) => [rarity.id, index]));

export function toggled<T>(values: Set<T>, value: T) {
	const next = new Set(values);
	if (next.has(value)) next.delete(value);
	else next.add(value);
	return next;
}

export function sortAndFilterFish(
	fish: CompendiumFish[],
	rarities: Set<Rarity>,
	tags: Set<string>,
	sort: FishSort,
) {
	return fish
		.filter((entry) => {
			if (rarities.size && !rarities.has(entry.rarity)) return false;
			return GROUPS.every((group) => {
				const selected = [...tags].filter((tag) => TAGS[tag].group === group);
				return !selected.length || selected.some((tag) => entry.tags.includes(tag));
			});
		})
		.sort((left, right) => {
			const rarity =
				(RARITY_RANK.get(left.rarity) ?? 0) - (RARITY_RANK.get(right.rarity) ?? 0);
			const location = locationKey(left).localeCompare(locationKey(right), 'en');
			return (
				(sort === 'rarity' ? rarity || location : location || rarity) ||
				left.title.localeCompare(right.title, 'en')
			);
		});
}

export function locationKey(fish: CompendiumFish) {
	return GROUPS.map((group) =>
		Object.keys(TAGS)
			.filter((tag) => TAGS[tag].group === group && fish.tags.includes(tag))
			.join('+'),
	).join('|');
}

export function positionTooltip(event: React.SyntheticEvent<HTMLElement>) {
	const box = event.currentTarget.getBoundingClientRect();
	const halfTooltip =
		Math.min(event.currentTarget.closest('.compact') ? 270 : 330, window.innerWidth - 46) / 2;
	event.currentTarget.classList.toggle(
		'tooltipAbove',
		box.top + box.height / 2 > window.innerHeight / 2,
	);
	event.currentTarget.classList.toggle(
		'tooltipAlignLeft',
		box.left + box.width / 2 < halfTooltip + 23,
	);
	event.currentTarget.classList.toggle(
		'tooltipAlignRight',
		window.innerWidth - box.left - box.width / 2 < halfTooltip + 23,
	);
}

export function tagLines(tags: string[]) {
	return GROUPS.flatMap((group) => {
		const emojis = tags.flatMap((tag) => (TAGS[tag].group === group ? [TAGS[tag].emoji] : []));
		return emojis.length ? [emojis.join('/')] : [];
	});
}

export function conditionSentence(tags: string[]) {
	const clauses = GROUPS.flatMap((group) => {
		const matches = tags.flatMap((tag) => (TAGS[tag].group === group ? [TAGS[tag]] : []));
		if (!matches.length) return [];
		const prefix =
			group === 'climate' || group === 'water' || group === 'height' ? 'in' : 'during';
		const alternatives = matches.map((match) => `${match.emoji} ${prefix} ${match.phrase}`);
		return [alternatives.length > 1 ? `(${joinOr(alternatives)})` : alternatives[0]];
	});
	return clauses.length ? clauses.join(' ') : 'anywhere and at any time';
}

export function joinOr(values: string[]) {
	return values.length < 2 ? values[0] : `${values.slice(0, -1).join(', ')} or ${values.at(-1)}`;
}

export function rarityInfo(rarity: Rarity) {
	return RARITIES.find((candidate) => candidate.id === rarity) ?? RARITIES[0];
}

export function isLuckRarity(rarity: Rarity) {
	return rarity === 'legendary' || rarity === 'mythical';
}

export function launchFireworks(
	fireworks: Fireworks | null,
	rarity: Rarity,
	stopTimer: React.RefObject<ReturnType<typeof setTimeout> | null>,
) {
	if (!fireworks) return;
	const hue = rarityInfo(rarity).hue;
	fireworks.updateOptions({
		hue: { min: hue - 4, max: hue + 4 },
		brightness: rarity === 'common' ? { min: 100, max: 100 } : { min: 50, max: 80 },
	});
	fireworks.start();
	fireworks.launch(10);
	if (stopTimer.current) clearTimeout(stopTimer.current);
	stopTimer.current = setTimeout(() => {
		fireworks.stop();
	}, 3_600);
}

export function formatLength(lengthCm: number) {
	return `${lengthCm.toFixed(1)} cm`;
}

export function formatDate(unixMs: number) {
	return new Intl.DateTimeFormat('en-GB', {
		day: '2-digit',
		month: '2-digit',
		year: '2-digit',
	})
		.format(new Date(unixMs))
		.replaceAll('/', '.');
}

export function errorMessage(error: unknown) {
	return error instanceof Error ? error.message : 'Failed to load fish compendium';
}
