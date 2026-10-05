'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { LeaderboardPodium } from '@/components/leaderboard-podium';
import { PlayerName } from '@/components/player-name';
import { fuzzyFilter, PlayerSelector } from '@/components/player-selector';
import { useSiteAlert } from '@/components/site-alert';
import { apiMessage } from '@/lib/api-response';
import {
	DEFAULT_COLUMN_KEYS,
	DEFAULT_LEADERBOARD_KEY,
	type PlayersResponse,
	type SortDirection,
	type StatOption,
} from './players/player-data.types';
import {
	comparePlayers,
	fallbackOption,
	formatColumnValue,
	getSortValue,
	groupStatOptions,
	rankPlayers,
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
	const { showAlert } = useSiteAlert();
	const [data, setData] = useState<PlayersResponse | null>(null);
	const [error, setError] = useState('');
	const [columnKeys, setColumnKeys] = useState(DEFAULT_COLUMN_KEYS);
	const [sort, setSort] = useState<{ key: string; direction: SortDirection }>({
		key: 'minecraft.custom.minecraft:play_time',
		direction: 'desc',
	});
	const [leaderboardKey, setLeaderboardKey] = useState(DEFAULT_LEADERBOARD_KEY);
	const [search, setSearch] = useState('');
	const [retry, setRetry] = useState(0);

	const load = useCallback(
		async (signal?: AbortSignal) => {
			const query = new URLSearchParams();
			if (playerName) query.set('player', playerName);
			let result: PlayersResponse | null = null;
			for (let page = 0; ; page++) {
				query.set('page', String(page));
				const response = await fetch(`/api/players?${query}`, {
					cache: 'no-store',
					signal,
				});
				const body = await response.json().catch(() => null);
				if (!response.ok) throw new Error(apiMessage(body, 'Failed to load players'));

				const next = body as PlayersResponse;
				if (!result) result = next;
				else {
					result = {
						...next,
						players: [...result.players, ...next.players],
						statOptions: mergeStatOptions(result.statOptions, next.statOptions),
					};
				}
				if (!next.hasMore) break;
			}
			setData(result);
		},
		[playerName],
	);

	useEffect(() => {
		let cancelled = false;
		const controller = new AbortController();

		async function loadInitial() {
			try {
				setError('');
				setData(null);
				await load(controller.signal);
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
	}, [load, retry]);

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
		// ponytail: The directory loads every player. Add server-side search if this becomes slow.
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
	const ranks = useMemo(() => {
		const sortOption =
			statOptions.find((option) => option.key === sort.key) ?? fallbackOption(sort.key);
		return rankPlayers(sortedPlayers, sortOption);
	}, [sortedPlayers, sort.key, statOptions]);
	const visiblePlayers = useMemo(
		() =>
			fuzzyFilter(sortedPlayers, search, [
				'minecraftUsername',
				'profile.preferredName',
				'profile.discordUsername',
			]),
		[search, sortedPlayers],
	);
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
		? players
				.filter((player) => player.isRanked)
				.map((player) => {
					const value = getSortValue(player, leaderboardOption);
					return {
						id: player.id,
						name: player.minecraftUsername,
						color: player.profile.color,
						pronouns: player.profile.pronouns,
						isCommittee: player.isCommittee,
						customEmojis: player.customEmojis,
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
		try {
			await load();
		} catch (caught) {
			await showAlert({
				title: 'Profile saved, but refresh failed',
				message:
					caught instanceof Error
						? caught.message
						: 'Reload the page to see the saved profile.',
				tone: 'danger',
			});
		}
	}

	if (error && !data) {
		return (
			<div className="playersLoading" role="alert">
				<p className="authError">{error}</p>
				<button
					type="button"
					onClick={() => {
						setRetry((current) => current + 1);
					}}
				>
					Retry loading players
				</button>
			</div>
		);
	}

	if (selectedPlayer) {
		return (
			<div className="playersPanel">
				<PlayerProfilePanel
					key={selectedPlayer.id}
					player={selectedPlayer}
					statOptions={statOptions}
					onBack={() => {
						onSelectPlayer(null, true);
					}}
					onSaved={() => void handleSaved()}
					onError={(message) => {
						if (message)
							void showAlert({
								title: 'Could not save the profile',
								message,
								tone: 'danger',
							});
					}}
				/>
			</div>
		);
	}

	if (!data) {
		return (
			<div className="playersLoading" role="status" aria-live="polite">
				<span className="playersLoadingSpinner" aria-hidden="true" />
				<p>Loading players...</p>
			</div>
		);
	}

	return (
		<div className="playersPanel">
			<div className="playersTop">
				<div>
					<h3>Players</h3>
					<p className="tabSubtitle">
						See who else plays on the server, learn a little about them, find their
						bases and compare your progress. Fill out your own profile so everyone can
						get to know you too.
					</p>
				</div>
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

			<div className="playerDirectorySearch">
				<PlayerSelector
					datalistId="player-directory-search"
					options={[]}
					value={search}
					onChange={(value) => {
						setSearch(value);
					}}
					placeholder="Search by Minecraft name, preferred name or Discord name"
					showSuggestions={false}
					ariaLabel="Search players"
				/>
				{search.trim() && <span>{visiblePlayers.length} found</span>}
			</div>

			<div className="playersTableWrap">
				<table className="playersTable">
					<thead>
						<tr>
							<th scope="col" className="playerRankColumn" aria-label="Place">
								#
							</th>
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
						{visiblePlayers.map((player) => (
							<tr
								key={player.id}
								onClick={() => {
									onSelectPlayer(player.minecraftUsername);
								}}
							>
								<td className="playerRankColumn">{ranks.get(player.id) ?? '-'}</td>
								<td>
									<PlayerCell player={player} />
								</td>
								{selectedColumns.map((column, index) => (
									<td key={`${player.id}:${column.key}:${index}`}>
										{column.key === 'profile.playerName' ? (
											<PlayerName
												name={player.minecraftUsername}
												color={player.profile.color}
												isCommittee={player.isCommittee}
												customEmojis={player.customEmojis}
											/>
										) : (
											formatColumnValue(player, column)
										)}
									</td>
								))}
							</tr>
						))}
						{visiblePlayers.length === 0 && (
							<tr>
								<td colSpan={selectedColumns.length + 2}>
									No players match that search.
								</td>
							</tr>
						)}
					</tbody>
				</table>
			</div>
		</div>
	);
}
