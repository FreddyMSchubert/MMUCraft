import type {
	MinecraftStatValue,
	PlayerProfile,
	PlayerSummary,
	SortDirection,
	StatOption,
} from './player-data.types';
import { dabloonizeWords, formatDabloons } from '@/lib/dabloons';

export function formatColumnValue(player: PlayerSummary, option: StatOption) {
	if (option.key === 'profile.playerName') return player.minecraftUsername;
	if (option.key === 'profile.isMember') return player.isMember ? 'Yes' : 'No';
	if (option.key === 'profile.isCommittee') return player.isCommittee ? 'Yes' : 'No';
	if (option.key === 'profile.preferredName') return player.profile.preferredName || '-';
	if (option.key === 'profile.pronouns') return player.profile.pronouns || '-';
	if (option.key === 'profile.courseYear') return player.profile.courseYear || '-';
	if (option.key === 'profile.discordUsername') return player.profile.discordUsername || '-';
	if (option.key === 'profile.base')
		return hasBase(player.profile) ? formatBase(player.profile.base) : '-';
	if (option.key === 'money.earnedDabloons')
		return formatDabloons(player.stats.money.earnedDabloons);
	if (option.key.startsWith('unlocks.')) {
		const progress = player.unlocks[option.key.slice(8) as keyof PlayerSummary['unlocks']];
		return `${formatNumber(progress.unlocked)}/${formatNumber(progress.total)}`;
	}
	if (option.key.startsWith('fishing.'))
		return formatNumber(player.fishing[option.key.slice(8)] ?? 0);
	if (option.key === 'minecraft.lastPlayedAtUnixMs')
		return formatTimestamp(player.stats.minecraft.lastPlayedAtUnixMs);

	const stat = player.stats.minecraft.stats[option.key];
	return stat ? formatMinecraftStatValue(stat) : '0';
}

export function comparePlayers(
	left: PlayerSummary,
	right: PlayerSummary,
	option: StatOption,
	direction: SortDirection,
) {
	const ordered = compareSortValues(
		getSortValue(left, option),
		getSortValue(right, option),
		direction,
	);
	return ordered || left.minecraftUsername.localeCompare(right.minecraftUsername, 'en');
}

export function getSortValue(player: PlayerSummary, option: StatOption): number | string | null {
	if (option.key === 'profile.playerName') return player.minecraftUsername;
	if (option.key === 'profile.isMember') return player.isMember ? 1 : 0;
	if (option.key === 'profile.isCommittee') return player.isCommittee ? 1 : 0;
	if (option.key === 'profile.preferredName')
		return player.profile.preferredName || player.minecraftUsername;
	if (option.key === 'profile.pronouns') return player.profile.pronouns || null;
	if (option.key === 'profile.courseYear') return player.profile.courseYear || null;
	if (option.key === 'profile.discordUsername') return player.profile.discordUsername || null;
	if (option.key === 'profile.base')
		return hasBase(player.profile) ? formatBase(player.profile.base) : null;
	if (option.key === 'money.earnedDabloons') return player.stats.money.earnedDabloons;
	if (option.key.startsWith('unlocks.'))
		return player.unlocks[option.key.slice(8) as keyof PlayerSummary['unlocks']].unlocked;
	if (option.key.startsWith('fishing.')) return player.fishing[option.key.slice(8)] ?? 0;
	if (option.key === 'minecraft.lastPlayedAtUnixMs')
		return player.stats.minecraft.lastPlayedAtUnixMs;

	return player.stats.minecraft.stats[option.key]?.value ?? 0;
}

export function compareSortValues(
	left: number | string | null,
	right: number | string | null,
	direction: SortDirection,
) {
	const leftMissing = left === null || left === '';
	const rightMissing = right === null || right === '';

	if (leftMissing && rightMissing) return 0;
	if (leftMissing) return 1;
	if (rightMissing) return -1;

	let valueOrder: number;
	if (typeof left === 'number' && typeof right === 'number') {
		valueOrder = left - right;
	} else {
		valueOrder = String(left).localeCompare(String(right), 'en', {
			numeric: true,
			sensitivity: 'base',
		});
	}

	return direction === 'desc' ? -valueOrder : valueOrder;
}

export function formatMinecraftStatValue(stat: MinecraftStatValue) {
	if (stat.category === 'advancement' && stat.total !== undefined) {
		return `${formatNumber(stat.value)}/${formatNumber(stat.total)}`;
	}
	if (stat.id.endsWith('_one_cm')) {
		const meters = stat.value / 100;
		if (meters >= 1000) return `${formatNumber(meters / 1000)} km`;
		return `${formatNumber(meters)} m`;
	}

	if (stat.id.includes('_time') || stat.id.endsWith(':play_time')) {
		return formatTicks(stat.value);
	}

	return formatNumber(stat.value);
}

export function formatTicks(value: number) {
	const seconds = Math.floor(value / 20);
	const hours = Math.floor(seconds / 3600);
	const minutes = Math.floor((seconds % 3600) / 60);

	if (hours > 0) return `${hours}h ${minutes}m`;
	if (minutes > 0) return `${minutes}m`;
	return `${seconds}s`;
}

export function formatTimestamp(value: number | null) {
	if (!value) return 'Never';

	return new Intl.DateTimeFormat(undefined, {
		dateStyle: 'medium',
		timeStyle: 'short',
	}).format(new Date(value));
}

export function formatNumber(value: number) {
	return new Intl.NumberFormat().format(value);
}

export function formatBase(base: PlayerProfile['base']) {
	return [base.x, base.y, base.z].map((value) => value ?? '?').join(', ');
}

export function hasBase(profile: PlayerProfile) {
	return profile.base.x !== null || profile.base.y !== null || profile.base.z !== null;
}

export function groupStatOptions(options: StatOption[]) {
	const displayOptions = options.map((option) => ({
		...option,
		label: dabloonizeWords(option.label),
	}));
	const groups = [
		{
			key: 'profile',
			label: 'Profile',
			options: displayOptions.filter((option) => option.group === 'profile'),
		},
		{
			key: 'money',
			label: dabloonizeWords('Dabloons'),
			options: displayOptions.filter((option) => option.group === 'money'),
		},
		{
			key: 'fishing',
			label: 'Fishing Compendium',
			options: displayOptions.filter((option) => option.group === 'fishing'),
		},
		{
			key: 'minecraft-session',
			label: 'Minecraft - Session',
			options: displayOptions.filter(
				(option) => option.group === 'minecraft' && option.category === 'session',
			),
		},
		{
			key: 'minecraft-advancement',
			label: 'Minecraft - Advancements',
			options: displayOptions.filter(
				(option) => option.group === 'minecraft' && option.category === 'advancement',
			),
		},
		{
			key: 'minecraft-custom',
			label: 'Minecraft - General Stats',
			options: displayOptions.filter(
				(option) => option.group === 'minecraft' && option.category === 'custom',
			),
		},
		{
			key: 'minecraft-killed',
			label: 'Minecraft - Mobs Killed',
			options: displayOptions.filter(
				(option) => option.group === 'minecraft' && option.category === 'killed',
			),
		},
		{
			key: 'minecraft-killed-by',
			label: 'Minecraft - Deaths by Mob',
			options: displayOptions.filter(
				(option) => option.group === 'minecraft' && option.category === 'killed_by',
			),
		},
	];

	return groups
		.map((group) => ({
			...group,
			options: [...group.options].sort((left, right) =>
				left.label.localeCompare(right.label, 'en'),
			),
		}))
		.filter((group) => group.options.length > 0);
}

export function groupMinecraftStats(stats: MinecraftStatValue[]) {
	const visible = stats
		.filter((stat) => stat.value > 0)
		.sort((left, right) => {
			const category = categoryRank(left.category) - categoryRank(right.category);
			if (category !== 0) return category;
			return left.label.localeCompare(right.label, 'en');
		});
	const groups = new Map<string, MinecraftStatValue[]>();

	for (const stat of visible) {
		const group = groups.get(stat.category) ?? [];
		group.push(stat);
		groups.set(stat.category, group);
	}

	return [...groups.entries()].map(([category, groupStats]) => ({
		category,
		stats: groupStats,
	}));
}

export function categoryRank(category: string) {
	if (category === 'custom') return 0;
	if (category === 'killed') return 1;
	if (category === 'killed_by') return 2;
	return 3;
}

export function formatCategory(category: string) {
	if (category === 'custom') return 'General';
	if (category === 'killed') return 'Killed mob';
	if (category === 'killed_by') return 'Killed by mob';
	return category
		.split(/[_-]+/)
		.map((part) => part.charAt(0).toUpperCase() + part.slice(1))
		.join(' ');
}

export function fallbackOption(key: string): StatOption {
	return {
		key,
		label: key,
		group: key.startsWith('money.')
			? 'money'
			: key.startsWith('profile.')
				? 'profile'
				: key.startsWith('fishing.')
					? 'fishing'
					: 'minecraft',
	};
}
