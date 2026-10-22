'use client';

import { useEffect, useState } from 'react';
import { apiMessage, fetchAdmin } from './admin-api';

interface DuckAdminPlayer {
	userId: number;
	minecraftUsername: string;
	note: string;
	count: number;
	ducks: {
		id: number;
		name: string;
		condition: string;
		manual: boolean;
		unlocked: boolean;
	}[];
}

export function DucksAdminSection() {
	const [search, setSearch] = useState('');
	const [players, setPlayers] = useState<{ id: number; minecraftUsername: string }[]>([]);
	const [player, setPlayer] = useState<DuckAdminPlayer | null>(null);
	const [note, setNote] = useState('');
	const [busy, setBusy] = useState(false);
	const [message, setMessage] = useState('');

	useEffect(() => {
		const controller = new AbortController();
		const timer = window.setTimeout(() => {
			void fetch(`/api/ducks/admin/players?search=${encodeURIComponent(search)}`, {
				cache: 'no-store',
				signal: controller.signal,
			})
				.then(async (response) =>
					response.ok
						? ((await response.json()) as { id: number; minecraftUsername: string }[])
						: [],
				)
				.then((rows: { id: number; minecraftUsername: string }[]) => {
					setPlayers(rows);
				})
				.catch(() => undefined);
		}, 180);
		return () => {
			controller.abort();
			window.clearTimeout(timer);
		};
	}, [search]);

	async function select(userId: number) {
		try {
			const selected = await fetchAdmin<DuckAdminPlayer>(
				`/api/ducks/admin/players/${userId}`,
				'Could not load player ducks.',
			);
			setPlayer(selected);
			setNote(selected.note);
			setMessage('');
		} catch (error) {
			setMessage(error instanceof Error ? error.message : 'Could not load player ducks.');
		}
	}

	async function updateDuck(duckId: number, awarded: boolean) {
		if (!player) return;
		setBusy(true);
		setMessage('');
		try {
			const response = await fetch(`/api/ducks/admin/players/${player.userId}/${duckId}`, {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ awarded }),
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Could not change this duck.'));
			setPlayer(body as DuckAdminPlayer);
			setMessage(awarded ? 'Duck awarded.' : 'Duck removed. Existing physical items stay.');
		} catch (error) {
			setMessage(error instanceof Error ? error.message : 'Could not change this duck.');
		} finally {
			setBusy(false);
		}
	}

	async function saveNote() {
		if (!player) return;
		setBusy(true);
		setMessage('');
		try {
			const response = await fetch(`/api/ducks/admin/players/${player.userId}/note`, {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ note }),
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Could not save duck note.'));
			setPlayer(body as DuckAdminPlayer);
			setMessage('Duck note saved.');
		} catch (error) {
			setMessage(error instanceof Error ? error.message : 'Could not save duck note.');
		} finally {
			setBusy(false);
		}
	}

	return (
		<section className="adminSection ducksAdmin">
			<div className="adminSectionHeader">
				<h3>Ducks</h3>
				<p>Search a player, record socials and reports, or correct any duck award.</p>
			</div>
			<label className="ducksAdminSearch">
				Search players
				<input
					value={search}
					onChange={(event) => {
						setSearch(event.target.value);
					}}
					placeholder="Minecraft username"
				/>
			</label>
			<div className="ducksAdminPlayers">
				{players.map((option) => (
					<button
						type="button"
						key={option.id}
						className={player?.userId === option.id ? 'selected' : ''}
						onClick={() => void select(option.id)}
					>
						{option.minecraftUsername}
					</button>
				))}
			</div>
			{player && (
				<div className="ducksAdminDetail">
					<h4>
						{player.minecraftUsername} · {player.count}/13 ducks
					</h4>
					<label className="ducksAdminNote">
						Committee notes for {player.minecraftUsername}
						<textarea
							value={note}
							maxLength={4000}
							rows={5}
							onChange={(event) => {
								setNote(event.target.value);
							}}
							placeholder="Socials attended, joke or bug report details..."
						/>
					</label>
					<button
						type="button"
						disabled={busy || note === player.note}
						onClick={() => void saveNote()}
					>
						Save note
					</button>
					<div className="ducksAdminChecks">
						{player.ducks.map((duck) => (
							<label key={duck.id}>
								<input
									type="checkbox"
									checked={duck.unlocked}
									disabled={busy}
									onChange={(event) =>
										void updateDuck(duck.id, event.target.checked)
									}
								/>
								<span>
									<strong>{duck.name}</strong>
									<small>
										{duck.condition}
										{duck.id === 12
											? ' · Coming later'
											: duck.manual
												? ' · Committee awarded'
												: ' · Automatic'}
									</small>
								</span>
							</label>
						))}
					</div>
				</div>
			)}
			{message && (
				<p className="ducksAdminMessage" role="status">
					{message}
				</p>
			)}
		</section>
	);
}
