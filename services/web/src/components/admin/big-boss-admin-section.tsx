'use client';

import { useEffect, useState } from 'react';
import { PlayerSelector } from '@/components/player-selector';
import type { AdminPlayer } from './admin-data.types';

interface BossAdminState {
	holder: { minecraftUsername: string } | null;
}

export function BigBossAdminSection({ players }: { players: AdminPlayer[] }) {
	const [holder, setHolder] = useState<string | null>(null);
	const [target, setTarget] = useState('');
	const [reason, setReason] = useState('');
	const [warnings, setWarnings] = useState<{ optedOut: boolean; cooldown: boolean } | null>(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');
	const selected = players.find(
		(player) => player.minecraftUsername.toLowerCase() === target.trim().toLowerCase(),
	);
	const selectedUsername = selected?.minecraftUsername;

	useEffect(() => {
		let cancelled = false;
		void fetch('/api/big-boss', { cache: 'no-store' })
			.then((response) => {
				if (!response.ok) throw new Error();
				return response.json() as Promise<BossAdminState>;
			})
			.then((state) => {
				if (!cancelled) setHolder(state.holder?.minecraftUsername ?? null);
			})
			.catch(() => {
				if (!cancelled) setError('Could not load the current Big Boss.');
			});
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		if (!selectedUsername) return;
		let cancelled = false;
		void fetch(`/api/big-boss/admin/${encodeURIComponent(selectedUsername)}/warnings`)
			.then((response) => {
				if (!response.ok) throw new Error();
				return response.json() as Promise<{ optedOut: boolean; cooldown: boolean }>;
			})
			.then((value) => {
				if (!cancelled) setWarnings(value);
			})
			.catch(() => {
				if (!cancelled) setWarnings(null);
			});
		return () => {
			cancelled = true;
		};
	}, [selectedUsername]);

	async function change(minecraftUsername: string | null) {
		setBusy(true);
		setError('');
		setNotice('');
		try {
			const response = await fetch('/api/big-boss/admin/assign', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ minecraftUsername, reason }),
			});
			const body = (await response.json()) as BossAdminState & { message?: string };
			if (!response.ok) throw new Error(body.message ?? 'Could not change the Big Boss.');
			setHolder(body.holder?.minecraftUsername ?? null);
			setTarget('');
			setWarnings(null);
			setReason('');
			setNotice(
				minecraftUsername
					? `${minecraftUsername} is now the Big Boss.`
					: 'The title is vacant.',
			);
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : 'Could not change the Big Boss.');
		} finally {
			setBusy(false);
		}
	}

	return (
		<section className="adminSection bigBossAdmin">
			<div className="adminSectionHeader">
				<h3>Big Boss</h3>
				<p>Current holder: {holder ?? 'Vacant'}</p>
			</div>
			<label>
				New holder
				<PlayerSelector
					datalistId="big-boss-admin-players"
					options={players}
					value={target}
					onChange={(value) => {
						setTarget(value);
						setWarnings(null);
					}}
					disabled={busy}
				/>
			</label>
			{selected && warnings?.optedOut && (
				<p role="status">
					This player opted out. An admin assignment overrides that choice.
				</p>
			)}
			{selected && warnings?.cooldown && (
				<p role="status">
					This player held the title within the last hour. An admin assignment overrides
					the cooldown.
				</p>
			)}
			<label>
				Reason (optional)
				<input
					value={reason}
					onChange={(event) => {
						setReason(event.target.value);
					}}
					maxLength={200}
					disabled={busy}
				/>
			</label>
			<div>
				<button
					type="button"
					disabled={
						busy ||
						!selected ||
						selected.minecraftUsername.toLowerCase() === holder?.toLowerCase()
					}
					onClick={() => selected && void change(selected.minecraftUsername)}
				>
					Assign title
				</button>
				<button type="button" disabled={busy || !holder} onClick={() => void change(null)}>
					Clear title
				</button>
			</div>
			{error && (
				<p role="alert" className="bigBossError">
					{error}
				</p>
			)}
			{notice && <p role="status">{notice}</p>}
		</section>
	);
}
