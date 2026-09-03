import { DabloonAmount, DabloonText } from '@/components/dabloon-amount';
import type { ReactNode } from 'react';
import {
	formatCategory,
	formatMinecraftStatValue,
	formatTimestamp,
	groupMinecraftStats,
} from './player-display-format';
import type { MinecraftStatValue, PlayerSummary, StatOption } from './player-data.types';

export function PlayerStatsList({
	player,
	statOptions,
}: {
	player: PlayerSummary;
	statOptions: StatOption[];
}) {
	const optionByKey = new Map(statOptions.map((option) => [option.key, option]));
	const advancement = Object.values(player.stats.minecraft.stats).find(
		(stat) => stat?.category === 'advancement',
	);
	const statGroups = groupMinecraftStats(
		Object.values(player.stats.minecraft.stats).filter(
			(stat): stat is MinecraftStatValue =>
				stat !== undefined && stat.category !== 'advancement',
		),
	);

	return (
		<div className="playerStatsPanel">
			<h5>Stats</h5>
			<div className="playerStatsCore">
				<StatLine
					label="Last Played"
					value={formatTimestamp(player.stats.minecraft.lastPlayedAtUnixMs)}
				/>
				<StatLine
					label={<DabloonText>Dabloons Earned</DabloonText>}
					value={<DabloonAmount amount={player.stats.money.earnedDabloons} />}
					rank={player.ranks['money.earnedDabloons']}
					rankLabel="Dabloons Earned"
				/>
				{advancement && (
					<StatLine
						label={optionByKey.get(advancement.key)?.label ?? advancement.label}
						value={formatMinecraftStatValue(advancement)}
						rank={player.ranks[advancement.key]}
						rankLabel={advancement.label}
					/>
				)}
			</div>
			<div className="playerStatsCore">
				{(['charms', 'cosmetics', 'knowledge'] as const).map((type) => {
					const progress = player.unlocks[type];
					const label =
						type === 'knowledge'
							? 'Knowledge Pages Unlocked'
							: `${type.charAt(0).toUpperCase()}${type.slice(1)} Unlocked`;
					return (
						<StatLine
							key={type}
							label={label}
							value={`${progress.unlocked.toLocaleString()}/${progress.total.toLocaleString()}`}
							rank={player.ranks[`unlocks.${type}`]}
							rankLabel={label}
						/>
					);
				})}
			</div>
			<p className="playerStatsSyncHint">
				Stats update occasionally. Manually refresh by leaving and rejoining the server.
			</p>

			{statGroups.length === 0 ? (
				<p className="playerProfileEmpty">No Minecraft stats synced yet.</p>
			) : (
				<div className="playerStatGroups">
					{statGroups.map((group) => (
						<details
							key={group.category}
							open={group.category === 'custom' || group.category === 'killed_by'}
						>
							<summary>{formatCategory(group.category)}</summary>
							<div className="playerStatGrid">
								{group.stats.map((stat) => (
									<StatLine
										key={stat.key}
										label={optionByKey.get(stat.key)?.label ?? stat.label}
										value={formatMinecraftStatValue(stat)}
										rank={player.ranks[stat.key]}
										rankLabel={stat.label}
									/>
								))}
							</div>
						</details>
					))}
				</div>
			)}
		</div>
	);
}

export function StatLine({
	label,
	value,
	rank,
	rankLabel,
}: {
	label: ReactNode;
	value: ReactNode;
	rank?: number;
	rankLabel?: string;
}) {
	return (
		<div className="playerStatLine">
			<span>{label}</span>
			<strong>{value}</strong>
			{rank && rankLabel && (
				<span
					className={`playerStatRank rank-${rankRarity(rank)}`}
					title={`#${rank} on the server for ${rankLabel}`}
					aria-label={`Rank ${rank} on the server for ${rankLabel}`}
				>
					#{rank}
				</span>
			)}
		</div>
	);
}

function rankRarity(rank: number) {
	if (rank === 1) return 'mythical';
	if (rank === 2) return 'legendary';
	if (rank === 3) return 'epic';
	if (rank <= 5) return 'rare';
	if (rank <= 7) return 'uncommon';
	return 'common';
}
