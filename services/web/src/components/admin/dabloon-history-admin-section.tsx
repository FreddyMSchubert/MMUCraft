'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState, type SyntheticEvent } from 'react';
import { PlayerName } from '@/components/player-name';
import { PlayerSelector } from '@/components/player-selector';
import { apiBody, apiMessage, formatPreciseDateTime, parseManchesterInput } from './admin-api';
import type { AdminPlayer } from './admin-data.types';
import type { AdminTabController } from './use-admin-tab-controller';

interface Interaction {
	id: number;
	action: string;
	itemKind: string;
	itemId: string;
	itemCount: number;
	amountDabloons: number;
	actorName: string;
	minecraftUuid: string | null;
	userId: number | null;
	context: string;
	dimension: string;
	x: number;
	y: number;
	z: number;
	balanceBefore: number | null;
	balanceAfter: number | null;
	createdAtUnixMs: number;
}

interface Filters {
	action: string;
	itemKind: string;
	playerName: string;
	dimension: string;
	minAmount: string;
	maxAmount: string;
	from: string;
	to: string;
	search: string;
}

const EMPTY_FILTERS: Filters = {
	action: '',
	itemKind: '',
	playerName: '',
	dimension: '',
	minAmount: '',
	maxAmount: '',
	from: '',
	to: '',
	search: '',
};

const ACTIONS = [
	['craft', 'Craft'],
	['creative_edit', 'Creative inventory edit'],
	['drop', 'Drop or spawn'],
	['despawn', 'Item despawn'],
	['destroy', 'Item destroyed'],
	['pickup', 'Pickup'],
	['earn', 'Earn'],
	['grant', 'Item grant'],
	['spend', 'Spend'],
	['set', 'Set balance'],
	['right_click', 'Right click'],
	['wallet_withdraw', 'Wallet withdrawal'],
	['transfer', 'Inventory transfer'],
] as const;

export function DabloonHistoryAdminSection({ controller }: { controller: AdminTabController }) {
	const { activeSection, players } = controller;
	const [draft, setDraft] = useState<Filters>(EMPTY_FILTERS);
	const [query, setQuery] = useState('');
	const [rows, setRows] = useState<Interaction[]>([]);
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
				const response = await fetch(`/api/admin/dabloon-interactions?${params}`, {
					cache: 'no-store',
				});
				const body = await response.json().catch(() => null);
				if (!response.ok)
					throw new Error(apiMessage(body, 'Failed to load Dabloon history'));
				const result = apiBody<{
					interactions: Interaction[];
					hasMore: boolean;
					nextCursor: number | null;
				}>(body);
				setRows((current) =>
					append ? [...current, ...result.interactions] : result.interactions,
				);
				setHasMore(result.hasMore);
				setNextCursor(result.nextCursor);
			} catch (caught) {
				setError(
					caught instanceof Error ? caught.message : 'Failed to load Dabloon history',
				);
			} finally {
				setLoading(false);
			}
		},
		[query],
	);

	useEffect(() => {
		if (activeSection !== 'dabloons') return;
		const timeout = window.setTimeout(() => void loadPage(null, false), 0);
		return () => {
			window.clearTimeout(timeout);
		};
	}, [activeSection, loadPage]);

	if (activeSection !== 'dabloons') return null;

	function applyFilters(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		const player = draft.playerName.trim()
			? players.find(
					(entry) =>
						entry.minecraftUsername.toLowerCase() ===
						draft.playerName.trim().toLowerCase(),
				)
			: null;
		if (draft.playerName.trim() && !player) {
			setError('Choose an exact player from the player suggestions.');
			return;
		}
		const params = new URLSearchParams();
		if (player) params.set('userId', String(player.id));
		for (const name of [
			'action',
			'itemKind',
			'dimension',
			'minAmount',
			'maxAmount',
			'search',
		] as const) {
			if (draft[name].trim()) params.set(name, draft[name].trim());
		}
		if (draft.from) params.set('fromUnixMs', String(parseManchesterInput(draft.from)));
		if (draft.to) {
			const timestamp = parseManchesterInput(draft.to);
			params.set('toUnixMs', String(timestamp + (draft.to.length === 16 ? 59_999 : 0)));
		}
		setQuery(params.toString());
	}

	return (
		<section className="adminSection commandLogSection">
			<div className="adminSectionHeader">
				<h3>Dabloon interactions</h3>
				<p>
					Review coin and wallet actions. The Dabloons column shows the value in each
					action, not a running total.
				</p>
			</div>
			<form className="commandLogFilters" onSubmit={applyFilters}>
				<label>
					<span>Action</span>
					<select
						value={draft.action}
						onChange={(event) => {
							setDraft({ ...draft, action: event.target.value });
						}}
					>
						<option value="">All actions</option>
						{ACTIONS.map(([value, label]) => (
							<option key={value} value={value}>
								{label}
							</option>
						))}
					</select>
				</label>
				<label>
					<span>Item</span>
					<select
						value={draft.itemKind}
						onChange={(event) => {
							setDraft({ ...draft, itemKind: event.target.value });
						}}
					>
						<option value="">All items</option>
						<option value="coin">Coins</option>
						<option value="wallet">Wallets</option>
						<option value="container">Containers holding currency</option>
						<option value="balance">Balance changes</option>
						<option value="mixed">Coins and wallets</option>
					</select>
				</label>
				<label>
					<span>Player</span>
					<PlayerSelector
						datalistId="dabloon-log-players"
						options={players}
						value={draft.playerName}
						onChange={(playerName) => {
							setDraft({ ...draft, playerName });
						}}
						placeholder="All players"
					/>
				</label>
				<label>
					<span>Dimension</span>
					<input
						list="dabloon-log-dimensions"
						placeholder="All dimensions"
						value={draft.dimension}
						onChange={(event) => {
							setDraft({ ...draft, dimension: event.target.value });
						}}
					/>
					<datalist id="dabloon-log-dimensions">
						<option value="minecraft:overworld" />
						<option value="minecraft:the_nether" />
						<option value="minecraft:the_end" />
					</datalist>
				</label>
				<label>
					<span>Minimum Dabloons</span>
					<input
						type="number"
						min="0"
						value={draft.minAmount}
						onChange={(event) => {
							setDraft({ ...draft, minAmount: event.target.value });
						}}
					/>
				</label>
				<label>
					<span>Maximum Dabloons</span>
					<input
						type="number"
						min="0"
						value={draft.maxAmount}
						onChange={(event) => {
							setDraft({ ...draft, maxAmount: event.target.value });
						}}
					/>
				</label>
				<label className="commandLogSearch">
					<span>Search actor, item, source, or detail</span>
					<input
						type="search"
						value={draft.search}
						onChange={(event) => {
							setDraft({ ...draft, search: event.target.value });
						}}
						placeholder="For example: gift code, coin-100, or player UUID"
					/>
				</label>
				<label className="commandLogDateFilter">
					<span>From</span>
					<input
						type="datetime-local"
						value={draft.from}
						onChange={(event) => {
							setDraft({ ...draft, from: event.target.value });
						}}
					/>
				</label>
				<label className="commandLogDateFilter">
					<span>To</span>
					<input
						type="datetime-local"
						value={draft.to}
						onChange={(event) => {
							setDraft({ ...draft, to: event.target.value });
						}}
					/>
				</label>
				<div className="commandLogFilterActions">
					<button type="submit" disabled={loading}>
						Apply filters
					</button>
					<button
						type="button"
						className="secondary"
						disabled={loading}
						onClick={() => {
							setDraft(EMPTY_FILTERS);
							setQuery('');
						}}
					>
						Reset all
					</button>
					<button
						type="button"
						className="secondary"
						disabled={loading}
						onClick={() => void loadPage(null, false)}
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
							<th>Action</th>
							<th>Player</th>
							<th>Item</th>
							<th>Dabloons</th>
							<th>Balance</th>
							<th>Location</th>
							<th>Detail</th>
							<th>Created</th>
						</tr>
					</thead>
					<tbody>
						{rows.map((row) => (
							<tr key={row.id}>
								<td>
									{ACTIONS.find(([value]) => value === row.action)?.[1] ??
										row.action}
								</td>
								<td>
									<InteractionActor row={row} players={players} />
								</td>
								<td>
									{row.itemId ? <code>{row.itemId}</code> : row.itemKind}
									{row.itemCount > 0 && ` ×${row.itemCount}`}
								</td>
								<td>{row.amountDabloons.toLocaleString('en-GB')}</td>
								<td>
									{row.balanceBefore === null && row.balanceAfter === null
										? '—'
										: `${row.balanceBefore?.toLocaleString('en-GB') ?? '?'} → ${row.balanceAfter?.toLocaleString('en-GB') ?? '?'}`}
								</td>
								<td>
									<code>{row.dimension || 'Unknown'}</code>
									<br />
									{row.x}, {row.y}, {row.z}
								</td>
								<td>{row.context || '—'}</td>
								<td>{formatPreciseDateTime(row.createdAtUnixMs)}</td>
							</tr>
						))}
						{rows.length === 0 && !loading && (
							<tr>
								<td colSpan={8}>No interactions match these filters.</td>
							</tr>
						)}
					</tbody>
				</table>
			</div>
			<div className="commandLogLoadMore">
				{loading && <span>Loading interactions…</span>}
				{hasMore && (
					<button
						type="button"
						disabled={loading || nextCursor === null}
						onClick={() => void loadPage(nextCursor, true)}
					>
						Load 50 older interactions
					</button>
				)}
				{!hasMore && rows.length > 0 && (
					<span>Beginning of the filtered Dabloon history.</span>
				)}
			</div>
		</section>
	);
}

function InteractionActor({ row, players }: { row: Interaction; players: AdminPlayer[] }) {
	const player = players.find((candidate) => candidate.id === row.userId);
	if (!player) return <span title={row.minecraftUuid ?? undefined}>{row.actorName}</span>;
	return (
		<Link href={`/play/players/${encodeURIComponent(player.minecraftUsername)}`}>
			<PlayerName
				name={player.minecraftUsername}
				color={player.color}
				isCommittee={player.isCommittee}
				customEmojis={player.customEmojis}
			/>
		</Link>
	);
}
