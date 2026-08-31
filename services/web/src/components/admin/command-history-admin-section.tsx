'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState, type SyntheticEvent } from 'react';
import { PlayerName } from '@/components/player-name';
import { PlayerSelector } from '@/components/player-selector';
import { apiBody, apiMessage } from './admin-api';
import type { AdminPlayer, CommandLogEntry } from './admin-data.types';
import type { AdminTabController } from './use-admin-tab-controller';

interface Filters {
	isOperator: '' | 'true' | 'false';
	source: '' | 'minecraft' | 'discord';
	playerName: string;
	from: string;
	to: string;
	search: string;
}

interface CommandLogResponse {
	commands: CommandLogEntry[];
	hasMore: boolean;
	nextCursor: number | null;
}

const EMPTY_FILTERS: Filters = {
	isOperator: '',
	source: '',
	playerName: '',
	from: '',
	to: '',
	search: '',
};

export function CommandHistoryAdminSection({ controller }: { controller: AdminTabController }) {
	const { activeSection, players } = controller;
	const [draft, setDraft] = useState<Filters>(EMPTY_FILTERS);
	const [query, setQuery] = useState('');
	const [commands, setCommands] = useState<CommandLogEntry[]>([]);
	const [nextCursor, setNextCursor] = useState<number | null>(null);
	const [hasMore, setHasMore] = useState(false);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState('');

	const loadPage = useCallback(
		async (cursor: number | null, append: boolean) => {
			setLoading(true);
			setError('');
			try {
				const params = new URLSearchParams(query);
				if (cursor !== null) params.set('beforeId', String(cursor));
				const response = await fetch(`/api/admin/command-logs?${params}`, {
					cache: 'no-store',
				});
				const body = await response.json().catch(() => null);
				if (!response.ok)
					throw new Error(apiMessage(body, 'Failed to load command history'));
				const result = apiBody<CommandLogResponse>(body);
				setCommands((current) =>
					append ? [...current, ...result.commands] : result.commands,
				);
				setHasMore(result.hasMore);
				setNextCursor(result.nextCursor);
			} catch (caught) {
				setError(
					caught instanceof Error ? caught.message : 'Failed to load command history',
				);
			} finally {
				setLoading(false);
			}
		},
		[query],
	);

	useEffect(() => {
		if (activeSection !== 'commands') return;
		void loadPage(null, false);
	}, [activeSection, loadPage]);

	if (activeSection !== 'commands') return null;

	function applyFilters(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		const params = new URLSearchParams();
		const player = findPlayer(players, draft.playerName);
		if (draft.playerName.trim() && !player) {
			setError('Choose an exact player from the player suggestions.');
			return;
		}
		if (draft.isOperator) params.set('isOperator', draft.isOperator);
		if (draft.source) params.set('source', draft.source);
		if (player) params.set('userId', String(player.id));
		if (draft.from) params.set('fromUnixMs', String(localDateTimeToUnixMs(draft.from, false)));
		if (draft.to) params.set('toUnixMs', String(localDateTimeToUnixMs(draft.to, true)));
		if (draft.search.trim()) params.set('search', draft.search.trim());
		setQuery(params.toString());
	}

	function resetAll() {
		setDraft(EMPTY_FILTERS);
		setQuery('');
	}

	return (
		<section className="adminSection commandLogSection">
			<div className="adminSectionHeader">
				<h3>Command log</h3>
				<p>
					Every command attempt from Minecraft and Discord. History is retained
					indefinitely.
				</p>
			</div>
			<form className="commandLogFilters" onSubmit={applyFilters}>
				<label>
					<span>Operator status</span>
					<select
						value={draft.isOperator}
						onChange={(event) => {
							setDraft({
								...draft,
								isOperator: event.target.value as Filters['isOperator'],
							});
						}}
					>
						<option value="">All</option>
						<option value="true">Operator</option>
						<option value="false">Non-operator</option>
					</select>
				</label>
				<label>
					<span>Source</span>
					<select
						value={draft.source}
						onChange={(event) => {
							setDraft({ ...draft, source: event.target.value as Filters['source'] });
						}}
					>
						<option value="">Minecraft + Discord</option>
						<option value="minecraft">Minecraft</option>
						<option value="discord">Discord</option>
					</select>
				</label>
				<label>
					<span>Player</span>
					<PlayerSelector
						datalistId="command-log-players"
						options={players}
						value={draft.playerName}
						onChange={(playerName) => {
							setDraft({ ...draft, playerName });
						}}
						placeholder="All players"
					/>
				</label>
				<label className="commandLogSearch">
					<span>Search command or actor</span>
					<input
						type="search"
						value={draft.search}
						onChange={(event) => {
							setDraft({ ...draft, search: event.target.value });
						}}
						placeholder="Capitalization and spaces are ignored"
					/>
				</label>
				<DateFilter
					label="From (your local time)"
					value={draft.from}
					onChange={(from) => {
						setDraft({ ...draft, from });
					}}
				/>
				<DateFilter
					label="To (your local time)"
					value={draft.to}
					onChange={(to) => {
						setDraft({ ...draft, to });
					}}
				/>
				<div className="commandLogFilterActions">
					<button type="submit" disabled={loading}>
						Apply filters
					</button>
					<button
						type="button"
						className="secondary"
						onClick={resetAll}
						disabled={loading}
					>
						Reset all
					</button>
					<button
						type="button"
						className="secondary"
						onClick={() => void loadPage(null, false)}
						disabled={loading}
					>
						Refresh
					</button>
				</div>
			</form>

			{error && <p className="authError">{error}</p>}
			<div className="adminTableWrap commandLogTableWrap">
				<table className="adminTable">
					<thead>
						<tr>
							<th>Command</th>
							<th>Actor</th>
							<th>Source</th>
							<th>Outcome</th>
							<th>Access</th>
							<th>Created (with time zone)</th>
						</tr>
					</thead>
					<tbody>
						{commands.map((entry) => (
							<tr key={entry.id}>
								<td>
									<code>{entry.command}</code>
								</td>
								<td>
									<CommandActor entry={entry} players={players} />
								</td>
								<td>{entry.source === 'discord' ? 'Discord' : 'Minecraft'}</td>
								<td>
									<CommandOutcome entry={entry} />
								</td>
								<td>{entry.isOperator ? 'Operator' : 'Non-operator'}</td>
								<td>{formatZonedDateTime(entry.createdAtUnixMs)}</td>
							</tr>
						))}
						{commands.length === 0 && !loading && (
							<tr>
								<td colSpan={6}>No commands match these filters.</td>
							</tr>
						)}
					</tbody>
				</table>
			</div>
			<div className="commandLogLoadMore">
				{loading && <span>Loading commands…</span>}
				{hasMore && (
					<button
						type="button"
						disabled={loading || nextCursor === null}
						onClick={() => void loadPage(nextCursor, true)}
					>
						Load 50 older commands
					</button>
				)}
				{!hasMore && commands.length > 0 && (
					<span>Beginning of the filtered command history.</span>
				)}
			</div>
		</section>
	);
}

function DateFilter({
	label,
	value,
	onChange,
}: {
	label: string;
	value: string;
	onChange: (value: string) => void;
}) {
	return (
		<label className="commandLogDateFilter">
			<span>{label}</span>
			<div>
				<input
					type="datetime-local"
					value={value}
					onChange={(event) => {
						onChange(event.target.value);
					}}
				/>
				<button
					type="button"
					className="secondary"
					onClick={() => {
						onChange('');
					}}
					disabled={!value}
				>
					Reset
				</button>
			</div>
		</label>
	);
}

function CommandActor({ entry, players }: { entry: CommandLogEntry; players: AdminPlayer[] }) {
	const player =
		entry.userId === null ? null : players.find((candidate) => candidate.id === entry.userId);
	if (!player) return entry.actorName;
	return (
		<Link href={`/play/players/${encodeURIComponent(player.minecraftUsername)}`}>
			<PlayerName name={player.minecraftUsername} color={player.color} />
		</Link>
	);
}

function CommandOutcome({ entry }: { entry: CommandLogEntry }) {
	if (entry.succeeded === null)
		return <span title="Outcome was not recorded for this historical command">—</span>;
	return entry.succeeded ? (
		<span
			className="commandOutcome commandOutcome-success"
			title={`Succeeded · result ${entry.result ?? 0}`}
		>
			✅
		</span>
	) : (
		<span
			className="commandOutcome commandOutcome-failure"
			title={`Failed · result ${entry.result ?? 0}`}
		>
			❌
		</span>
	);
}

function findPlayer(players: AdminPlayer[], value: string) {
	const normalized = value.trim();
	if (!normalized) return null;
	return (
		players.find(
			(player) =>
				player.minecraftUsername.localeCompare(normalized, 'en', {
					sensitivity: 'base',
				}) === 0,
		) ?? null
	);
}

function formatZonedDateTime(timestamp: number) {
	return new Intl.DateTimeFormat(undefined, {
		year: 'numeric',
		month: 'short',
		day: 'numeric',
		hour: '2-digit',
		minute: '2-digit',
		second: '2-digit',
		timeZoneName: 'short',
	}).format(new Date(timestamp));
}

function localDateTimeToUnixMs(value: string, endOfMinute: boolean) {
	const timestamp = new Date(value).getTime();
	return endOfMinute && value.length === 16 ? timestamp + 59_999 : timestamp;
}
