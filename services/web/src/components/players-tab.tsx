'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { LeaderboardPodium } from '@/components/leaderboard-podium';
import { PlayerName } from '@/components/player-name';
import { apiMessage } from '@/lib/api-response';
import {
	DEFAULT_COLUMN_KEYS,
	DEFAULT_LEADERBOARD_KEY,
	type PlayersResponse,
	type PlayerSummary,
	type SortDirection,
	type StatOption,
} from './players/player-data.types';
import {
	comparePlayers,
	fallbackOption,
	formatColumnValue,
	getSortValue,
	groupStatOptions,
} from './players/player-display-format';
import { PlayerProfilePanel } from './players/player-profile-panel';
import { mergeStatOptions, StatOptionGroups } from './players/player-statistic-options';
import { PlayerCell } from './players/player-table-cells';

export function PlayersTab({
	playerName,
	onSelectPlayer,
}: {
	playerName?: string;
	onSelectPlayer: (playerName: string | null, replace?: boolean) => void;
}) {
	const [data, setData] = useState<PlayersResponse | null>(null);
	const [error, setError] = useState('');
	const [message, setMessage] = useState('');
	const [columnKeys, setColumnKeys] = useState(DEFAULT_COLUMN_KEYS);
	const [sort, setSort] = useState<{ key: string; direction: SortDirection }>({
		key: 'minecraft.custom.minecraft:play_time',
		direction: 'desc',
	});
	const [leaderboardKey, setLeaderboardKey] = useState(DEFAULT_LEADERBOARD_KEY);
	const [loadingMore, setLoadingMore] = useState(false);

	const load = useCallback(
		async (page = 0, append = false, signal?: AbortSignal) => {
			const query = new URLSearchParams({ page: String(page) });
			if (playerName) query.set('player', playerName);
			const response = await fetch(`/api/players?${query}`, {
				cache: 'no-store',
				signal,
			});
			const body = await response.json().catch(() => null);

			if (!response.ok) {
				throw new Error(apiMessage(body, 'Failed to load players'));
			}

			const next = body as PlayersResponse;
			setData((current) => {
				if (!append || !current) return next;
				const players = new Map(current.players.map((player) => [player.id, player]));
				for (const player of next.players) players.set(player.id, player);
				return {
					...next,
					players: [...players.values()],
					statOptions: mergeStatOptions(current.statOptions, next.statOptions),
				};
			});
		},
		[playerName],
	);

	useEffect(() => {
		let cancelled = false;
		const controller = new AbortController();

		async function loadInitial() {
			try {
				await load(0, false, controller.signal);
			} catch (caught) {
				if (!cancelled) {
					setError(caught instanceof Error ? caught.message : 'Failed to load players');
				}
			}
		}

		void loadInitial();

		return () => {
			cancelled = true;
			controller.abort();
		};
	}, [load]);

	const players = useMemo(() => data?.players ?? [], [data?.players]);
	const statOptions = useMemo(() => data?.statOptions ?? [], [data?.statOptions]);
	const selectedPlayer = useMemo(() => {
		if (!playerName) return null;
		return (
			[data?.selectedPlayer, ...players].find(
				(player) =>
					player?.minecraftUsername.localeCompare(playerName, 'en', {
						sensitivity: 'base',
					}) === 0,
			) ?? null
		);
	}, [data?.selectedPlayer, playerName, players]);

	useEffect(() => {
		if (
			!data ||
			!playerName ||
			data.requestedPlayer?.localeCompare(playerName, 'en', { sensitivity: 'base' }) !== 0
		)
			return;
		if (!selectedPlayer) {
			onSelectPlayer(null, true);
			return;
		}
		if (selectedPlayer.minecraftUsername !== playerName) {
			onSelectPlayer(selectedPlayer.minecraftUsername, true);
		}
	}, [data, onSelectPlayer, playerName, selectedPlayer]);

	const selectedColumns = useMemo(
		() =>
			columnKeys
				.slice(0, 4)
				.map(
					(key) =>
						statOptions.find((option) => option.key === key) ?? fallbackOption(key),
				)
				.filter((option): option is StatOption => Boolean(option)),
		[columnKeys, statOptions],
	);
	const sortedPlayers = useMemo(() => {
		const sortOption =
			statOptions.find((option) => option.key === sort.key) ?? fallbackOption(sort.key);
		return [...players].sort((left, right) =>
			comparePlayers(left, right, sortOption, sort.direction),
		);
	}, [players, sort, statOptions]);
	const leaderboardOptions = useMemo(
		() =>
			statOptions.filter(
				(option) =>
					option.group !== 'profile' && option.key !== 'minecraft.lastPlayedAtUnixMs',
			),
		[statOptions],
	);
	const leaderboardOption =
		leaderboardOptions.find((option) => option.key === leaderboardKey) ??
		leaderboardOptions.at(0);
	const podiumEntries = leaderboardOption
		? players.map((player) => {
				const value = getSortValue(player, leaderboardOption);
				return {
					id: player.id,
					name: player.minecraftUsername,
					color: player.profile.color,
					pronouns: player.profile.pronouns,
					value: value ?? 0,
					displayValue: formatColumnValue(player, leaderboardOption),
					avatarUrl: player.avatarUrl,
				};
			})
		: [];

	function updateColumn(index: number, key: string) {
		const previousKey = columnKeys[index];
		setColumnKeys((current) =>
			current.map((value, currentIndex) => (currentIndex === index ? key : value)),
		);
		setSort((current) => (current.key === previousKey ? { ...current, key } : current));
	}

	function toggleSort(key: string) {
		setSort((current) => {
			if (current.key !== key) {
				return { key, direction: 'desc' };
			}

			return { key, direction: current.direction === 'desc' ? 'asc' : 'desc' };
		});
	}

	async function handleSaved() {
		setMessage('Profile saved.');
		await load();
	}

	async function loadMore() {
		if (!data || loadingMore || !data.hasMore) return;
		setLoadingMore(true);
		setError('');
		try {
			await load(data.page + 1, true);
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : 'Failed to load more players');
		} finally {
			setLoadingMore(false);
		}
	}

	if (error && !data) {
		return <p className="authError">{error}</p>;
	}

	if (!data) {
		return <p>Loading players...</p>;
	}

	if (selectedPlayer) {
		return (
			<div className="playersPanel">
				{message && <p className="dailyMessage">{message}</p>}
				{error && <p className="authError">{error}</p>}
				<PlayerProfilePanel
					player={selectedPlayer}
					statOptions={statOptions}
					onBack={() => {
						onSelectPlayer(null, true);
					}}
					onSaved={() => void handleSaved()}
					onError={setError}
				/>
			</div>
		);
	}

	return (
		<div className="playersPanel">
			<div className="playersTop">
				<h3>Players</h3>
				{data.currentUserMinecraftUsername && (
					<button
						type="button"
						onClick={() => {
							onSelectPlayer(data.currentUserMinecraftUsername);
						}}
					>
						View / edit own profile
					</button>
				)}
			</div>

			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}
			{leaderboardOption && (
				<LeaderboardPodium
					entries={podiumEntries}
					label={leaderboardOption.label}
					optionGroups={groupStatOptions(leaderboardOptions)}
					selectedKey={leaderboardOption.key}
					onChange={setLeaderboardKey}
					onSelectPlayer={onSelectPlayer}
				/>
			)}

			<div className="playersTableWrap">
				<table className="playersTable">
					<thead>
						<tr>
							<th scope="col">Player</th>
							{selectedColumns.map((column, index) => (
								<th scope="col" key={`${column.key}:${index}`}>
									<div className="playerColumnSelect">
										<select
											aria-label={`Column ${index + 1}`}
											value={columnKeys[index] ?? column.key}
											onChange={(event) => {
												updateColumn(index, event.target.value);
											}}
										>
											<StatOptionGroups options={statOptions} />
										</select>
										<button
											type="button"
											aria-label={`Sort by ${column.label}`}
											onClick={() => {
												toggleSort(column.key);
											}}
										>
											{sort.key === column.key
												? sort.direction === 'desc'
													? 'v'
													: '^'
												: '-'}
										</button>
									</div>
								</th>
							))}
						</tr>
					</thead>
					<tbody>
						{sortedPlayers.map((player) => (
							<tr
								key={player.id}
								onClick={() => {
									onSelectPlayer(player.minecraftUsername);
								}}
							>
								<td>
									<PlayerCell player={player} />
								</td>
								{selectedColumns.map((column, index) => (
									<td key={`${player.id}:${column.key}:${index}`}>
										{column.key === 'profile.playerName' ? (
											<PlayerName
												name={player.minecraftUsername}
												color={player.profile.color}
											/>
										) : (
											formatColumnValue(player, column)
										)}
									</td>
								))}
							</tr>
						))}
					</tbody>
				</table>
			</div>
			{data.hasMore && (
				<button
					type="button"
					className="loadMoreButton"
					disabled={loadingMore}
					onClick={() => void loadMore()}
				>
					{loadingMore ? 'Loading...' : 'Load 42 more'}
				</button>
			)}
		</div>
	);
}
