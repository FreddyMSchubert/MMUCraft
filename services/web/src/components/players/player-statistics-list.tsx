import {
	formatCategory,
	formatMinecraftStatValue,
	formatNumber,
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
	const statGroups = groupMinecraftStats(
		Object.values(player.stats.minecraft.stats).filter(
			(stat): stat is MinecraftStatValue => stat !== undefined,
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
					label="Dabloons Earned"
					value={formatNumber(player.stats.money.earnedDabloons)}
				/>
				<StatLine
					label="Last Sync"
					value={formatTimestamp(player.stats.minecraft.lastSyncedAtUnixMs)}
				/>
			</div>

			{statGroups.length === 0 ? (
				<p className="playerProfileEmpty">No Minecraft stats synced yet.</p>
			) : (
				<div className="playerStatGroups">
					{statGroups.map((group) => (
						<details key={group.category} open={group.category === 'custom'}>
							<summary>{formatCategory(group.category)}</summary>
							<div className="playerStatGrid">
								{group.stats.map((stat) => (
									<StatLine
										key={stat.key}
										label={optionByKey.get(stat.key)?.label ?? stat.label}
										value={formatMinecraftStatValue(stat)}
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

export function StatLine({ label, value }: { label: string; value: string }) {
	return (
		<div className="playerStatLine">
			<span>{label}</span>
			<strong>{value}</strong>
		</div>
	);
}
