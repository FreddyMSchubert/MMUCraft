'use client';

import { Fireworks } from 'fireworks-js';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { LeaderboardPodium } from '@/components/leaderboard-podium';
import { apiMessage } from '@/lib/api-response';
import { CompendiumGuide, CompendiumStats, FishGrid } from './fishing/fish-compendium-components';
import {
	errorMessage,
	launchFireworks,
	sortAndFilterFish,
	toggled,
} from './fishing/fish-compendium-format';
import {
	type CatchEvent,
	type CompendiumResponse,
	type FishSort,
	type Rarity,
} from './fishing/fish-compendium.types';

export function FishingTab({ onSelectPlayer }: { onSelectPlayer: (playerName: string) => void }) {
	const [data, setData] = useState<CompendiumResponse | null>(null);
	const [error, setError] = useState('');
	const [rarityFilters, setRarityFilters] = useState<Set<Rarity>>(new Set());
	const [tagFilters, setTagFilters] = useState<Set<string>>(new Set());
	const [sort, setSort] = useState<FishSort>('rarity');
	const fireworksHost = useRef<HTMLDivElement>(null);
	const fireworks = useRef<Fireworks | null>(null);
	const stopTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

	const load = useCallback(async () => {
		const response = await fetch('/api/fishing/compendium', { cache: 'no-store' });
		const body = await response.json().catch(() => null);
		if (!response.ok) throw new Error(apiMessage(body, 'Failed to load the fish compendium'));
		return body as CompendiumResponse;
	}, []);

	useEffect(() => {
		let cancelled = false;
		async function loadInitial() {
			try {
				const next = await load();
				if (!cancelled) setData(next);
			} catch (caught) {
				if (!cancelled) setError(errorMessage(caught));
			}
		}
		void loadInitial();
		return () => {
			cancelled = true;
		};
	}, [load]);

	useEffect(() => {
		const host = fireworksHost.current;
		if (!host) return;
		fireworks.current = new Fireworks(host, {
			autoresize: true,
			particles: 90,
			explosion: 6,
			intensity: 8,
			delay: { min: 100_000, max: 100_000 },
			sound: { enabled: false },
		});
		return () => {
			fireworks.current?.stop(true);
			fireworks.current = null;
		};
	}, []);

	useEffect(() => {
		const source = new EventSource('/api/fishing/events');
		source.onmessage = (message) => {
			const event = JSON.parse(String(message.data)) as {
				type?: string;
			} & Partial<CatchEvent>;
			if (event.type !== 'catch') return;
			void load()
				.then((next) => {
					setData(next);
				})
				.catch((caught: unknown) => {
					setError(errorMessage(caught));
				});
			launchFireworks(fireworks.current, event.rarity ?? 'common', stopTimer);
		};
		return () => {
			source.close();
		};
	}, [load]);

	const visibleFish = useMemo(
		() => sortAndFilterFish(data?.fish ?? [], rarityFilters, tagFilters, sort),
		[data?.fish, rarityFilters, tagFilters, sort],
	);

	if (!data)
		return <p className={error ? 'authError' : ''}>{error || 'Loading fish compendium...'}</p>;
	const player = data.players.find((candidate) => candidate.id === data.selectedUserId);
	const caughtTotal = data.fish.filter((fish) => fish.catch).length;
	const podiumEntries = data.players.map((candidate) => ({
		id: candidate.id,
		name: candidate.minecraftUsername,
		color: candidate.color,
		pronouns: candidate.pronouns,
		value: candidate.caughtTotal,
		displayValue: new Intl.NumberFormat().format(candidate.caughtTotal),
		avatarUrl: candidate.avatarUrl,
	}));

	return (
		<section className="fishCompendium">
			<div ref={fireworksHost} className="fishFireworks" aria-hidden="true" />
			<div className="fishTop">
				<div>
					<h3>Fish Compendium</h3>
					<p className="tabSubtitle">
						The server has its own fish, and different species appear in different
						places, weather, times and moon phases. The compendium tells you what to
						look for and records the biggest and smallest catches on the server.
					</p>
					<p className="tabNote">
						Your first catch of each species is saved, and new catches appear here live.
					</p>
				</div>
				<label className="fishSort">
					Sort
					<select
						value={sort}
						onChange={(event) => {
							setSort(event.target.value as FishSort);
						}}
					>
						<option value="rarity">Rarity first</option>
						<option value="location">Location first</option>
					</select>
				</label>
			</div>

			<div className="fishOverview">
				<CompendiumStats fish={data.fish} caughtTotal={caughtTotal} />
				<LeaderboardPodium
					entries={podiumEntries}
					label="Fish Species Caught"
					onSelectPlayer={onSelectPlayer}
					compact
				/>
			</div>
			<CompendiumGuide
				rarityFilters={rarityFilters}
				tagFilters={tagFilters}
				onToggleRarity={(rarity) => {
					setRarityFilters((current) => toggled(current, rarity));
				}}
				onToggleTag={(tag) => {
					setTagFilters((current) => toggled(current, tag));
				}}
				onReset={() => {
					setRarityFilters(new Set());
					setTagFilters(new Set());
				}}
			/>
			{error && <p className="authError">{error}</p>}
			{visibleFish.length ? (
				<FishGrid fish={visibleFish} compact={false} player={player} />
			) : (
				<p>No fish match those filters.</p>
			)}
		</section>
	);
}

export function MiniFishCompendium({ userId }: { userId: number }) {
	const [data, setData] = useState<CompendiumResponse | null>(null);
	const [error, setError] = useState('');
	const load = useCallback(async () => {
		const response = await fetch(`/api/fishing/compendium?userId=${userId}`, {
			cache: 'no-store',
		});
		const body = await response.json().catch(() => null);
		if (!response.ok) throw new Error(apiMessage(body, 'Failed to load fish'));
		return body as CompendiumResponse;
	}, [userId]);

	useEffect(() => {
		let cancelled = false;
		async function loadInitial() {
			try {
				const next = await load();
				if (!cancelled) setData(next);
			} catch (caught) {
				if (!cancelled) setError(errorMessage(caught));
			}
		}
		void loadInitial();
		const source = new EventSource('/api/fishing/events');
		source.onmessage = (message) => {
			const event = JSON.parse(String(message.data)) as { type?: string };
			if (event.type === 'catch') {
				void load()
					.then((next) => {
						setData(next);
					})
					.catch((caught: unknown) => {
						setError(errorMessage(caught));
					});
			}
		};
		return () => {
			cancelled = true;
			source.close();
		};
	}, [load]);

	return (
		<details className="miniFishCompendium" open>
			<summary>
				Fish Compendium
				{data && (
					<span>
						{data.fish.filter((fish) => fish.catch).length} / {data.fish.length} caught
					</span>
				)}
			</summary>
			{error && <p className="authError">{error}</p>}
			{data ? (
				<FishGrid
					fish={sortAndFilterFish(data.fish, new Set(), new Set(), 'rarity')}
					compact
					player={data.players.find((player) => player.id === data.selectedUserId)}
				/>
			) : (
				!error && <p>Loading fish...</p>
			)}
		</details>
	);
}
