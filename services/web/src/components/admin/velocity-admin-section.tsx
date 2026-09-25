'use client';

import { useCallback, useEffect, useState } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import { PlayerName, type PlayerEmoji } from '@/components/player-name';
import { apiBody, apiMessage, errorMessage, fetchAdmin } from './admin-api';

interface VelocityServer {
	id: number;
	name: string;
	address: string;
	health: 'online' | 'offline' | 'unknown';
	latencyMs: number | null;
	error: string | null;
	playerCount: number;
}

interface VelocityPlayer {
	uuid: string;
	username: string;
	serverName: string;
	color: string;
	isCommittee: boolean;
	customEmojis: PlayerEmoji[];
}

interface VelocitySnapshot {
	proxyOnline: boolean;
	maintenanceMode: boolean;
	eventOverride: number | null;
	eventActive: boolean;
	servers: VelocityServer[];
	players: VelocityPlayer[];
}

export function VelocityAdminSection({ section }: { section: 'servers' | 'maintenance' }) {
	const { confirm, showAlert } = useSiteAlert();
	const [snapshot, setSnapshot] = useState<VelocitySnapshot | null>(null);
	const [moveTargets, setMoveTargets] = useState<Record<string, string>>({});
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');

	const load = useCallback(async () => {
		setSnapshot(
			await fetchAdmin<VelocitySnapshot>(
				'/api/admin/velocity',
				'Failed to load Velocity state',
			),
		);
	}, []);

	useEffect(() => {
		let cancelled = false;
		let timer: number | undefined;
		async function refresh() {
			try {
				await load();
				if (!cancelled) setError('');
			} catch (caught) {
				if (!cancelled) setError(errorMessage(caught, 'Failed to load Velocity state'));
			} finally {
				if (!cancelled) timer = window.setTimeout(refresh, 3_000);
			}
		}
		void refresh();
		return () => {
			cancelled = true;
			window.clearTimeout(timer);
		};
	}, [load]);

	async function mutate(title: string, action: () => Promise<void>) {
		setBusy(true);
		try {
			await action();
			await load();
			await showAlert({ title, message: 'The change is being applied.', tone: 'success' });
		} catch (caught) {
			await showAlert({
				title: 'Server operation failed',
				message: errorMessage(caught, 'The operation failed'),
				tone: 'danger',
			});
		} finally {
			setBusy(false);
		}
	}

	async function setEventOverride(enabled: boolean | null) {
		if (
			enabled === false &&
			snapshot?.servers.some(
				(server) => server.name === 'surprising-saturday' && server.playerCount > 0,
			)
		) {
			const accepted = await confirm({
				title: 'Stop Surprising Saturday?',
				message:
					'Players on the event server will move to main before the event server stops.',
				confirmLabel: 'Stop server',
				confirmTone: 'danger',
				tone: 'danger',
			});
			if (!accepted) return;
		}
		await mutate('Surprising Saturday control updated', async () => {
			await velocityRequest(
				'/api/admin/velocity/event-override',
				{
					method: 'PATCH',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ enabled }),
				},
				'Failed to update the event server',
			);
		});
	}

	async function movePlayer(player: VelocityPlayer) {
		const serverId = Number(moveTargets[player.uuid]);
		if (!serverId) return;
		await mutate('Player move requested', async () => {
			await velocityRequest(
				`/api/admin/velocity/players/${encodeURIComponent(player.uuid)}/move`,
				{
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ serverId }),
				},
				'Failed to move the player',
			);
		});
	}

	async function setMaintenanceMode(enabled: boolean) {
		if (
			enabled &&
			!(await confirm({
				title: 'Enable maintenance mode?',
				message: 'Every online player will disconnect. New logins will be rejected.',
				confirmLabel: 'Enable maintenance',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;
		await mutate('Maintenance updated', async () => {
			await velocityRequest(
				'/api/admin/velocity/maintenance',
				{
					method: 'PATCH',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ enabled }),
				},
				'Failed to change maintenance mode',
			);
		});
	}

	if (!snapshot)
		return (
			<section className="adminSection">
				<p>{error || 'Loading server state...'}</p>
			</section>
		);
	if (section === 'maintenance')
		return (
			<section className="adminSection maintenanceSection">
				<div className="adminSectionHeader">
					<h3>Maintenance mode</h3>
					<p>Velocity blocks entry before a player reaches a backend server.</p>
				</div>
				<label className="maintenanceToggle">
					<input
						type="checkbox"
						checked={snapshot.maintenanceMode}
						disabled={busy}
						onChange={(event) => void setMaintenanceMode(event.target.checked)}
					/>
					<span>
						{snapshot.maintenanceMode
							? 'Maintenance mode is on'
							: 'Maintenance mode is off'}
					</span>
				</label>
				<p
					className={`velocityStatus velocityStatus-${snapshot.proxyOnline ? 'online' : 'offline'}`}
				>
					Velocity proxy: {snapshot.proxyOnline ? 'online' : 'not reporting'}
				</p>
				{error && <p className="authError">{error}</p>}
			</section>
		);

	const eventEnabled =
		snapshot.eventOverride === 1 || (snapshot.eventOverride === null && snapshot.eventActive);
	const eventHealth = snapshot.servers.find(
		(server) => server.name === 'surprising-saturday',
	)?.health;
	return (
		<div className="velocityAdmin">
			<section className="adminSection">
				<div className="adminSectionHeader velocityHeading">
					<div>
						<h3>Servers</h3>
						<p>
							Main stays online. Surprising Saturday starts during a scheduled event.
							You can override it here for testing.
						</p>
					</div>
					<span
						className={`velocityStatus velocityStatus-${snapshot.proxyOnline ? 'online' : 'offline'}`}
					>
						Proxy {snapshot.proxyOnline ? 'online' : 'offline'}
					</span>
				</div>
				<label className="maintenanceToggle">
					<input
						type="checkbox"
						checked={eventEnabled}
						disabled={busy}
						onChange={(event) => void setEventOverride(event.target.checked)}
					/>
					<span>
						{eventEnabled
							? 'Surprising Saturday is requested to run'
							: 'Surprising Saturday is requested to stop'}
					</span>
				</label>
				{snapshot.eventOverride !== null && (
					<button
						type="button"
						disabled={busy}
						onClick={() => void setEventOverride(null)}
					>
						Follow event schedule
					</button>
				)}
				<p className="velocityHint">
					{snapshot.eventOverride === null
						? 'Following the event schedule.'
						: 'Committee override is active.'}{' '}
					{eventEnabled && eventHealth !== 'online'
						? 'The event server is not ready yet. Check its health and logs if it stays offline.'
						: 'The health below shows when startup or shutdown finishes.'}
				</p>
				<div className="adminTableWrap">
					<table className="adminTable">
						<thead>
							<tr>
								<th>Server</th>
								<th>Health</th>
								<th>Players</th>
							</tr>
						</thead>
						<tbody>
							{snapshot.servers.map((server) => (
								<tr key={server.id}>
									<td>
										<strong>
											{server.name === 'main'
												? 'Main'
												: 'Surprising Saturday'}
										</strong>
									</td>
									<td>
										<span
											className={`velocityHealth velocityHealth-${server.health}`}
										>
											{server.health}
											{server.latencyMs === null
												? ''
												: ` · ${server.latencyMs} ms`}
										</span>
										{server.error && (
											<small className="velocityError">{server.error}</small>
										)}
									</td>
									<td>{server.playerCount}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			</section>
			<section className="adminSection">
				<div className="adminSectionHeader">
					<h3>Online players</h3>
					<p>
						Save an online player’s server choice in the API. The choice stays after
						disconnect. A server that is starting receives the player when it is ready.
					</p>
				</div>
				<div className="adminTableWrap">
					<table className="adminTable">
						<thead>
							<tr>
								<th>Player</th>
								<th>Current server</th>
								<th>Move to</th>
								<th></th>
							</tr>
						</thead>
						<tbody>
							{snapshot.players.map((player) => (
								<tr key={player.uuid}>
									<td>
										<PlayerName
											name={player.username}
											color={player.color}
											isCommittee={player.isCommittee}
											customEmojis={player.customEmojis}
										/>
									</td>
									<td>{player.serverName}</td>
									<td>
										<select
											value={moveTargets[player.uuid] ?? ''}
											onChange={(event) => {
												setMoveTargets((current) => ({
													...current,
													[player.uuid]: event.target.value,
												}));
											}}
										>
											<option value="">Select a healthy server</option>
											{snapshot.servers
												.filter(
													(server) =>
														server.health === 'online' &&
														server.name !== player.serverName,
												)
												.map((server) => (
													<option key={server.id} value={server.id}>
														{server.name}
													</option>
												))}
										</select>
									</td>
									<td>
										<button
											type="button"
											disabled={busy || !moveTargets[player.uuid]}
											onClick={() => void movePlayer(player)}
										>
											Move
										</button>
									</td>
								</tr>
							))}
							{snapshot.players.length === 0 && (
								<tr>
									<td colSpan={4}>No players are online.</td>
								</tr>
							)}
						</tbody>
					</table>
				</div>
			</section>
			{error && <p className="authError">{error}</p>}
		</div>
	);
}

async function velocityRequest(path: string, init: RequestInit, fallback: string) {
	const response = await fetch(path, init);
	const body = await response.json().catch(() => null);
	if (!response.ok) throw new Error(apiMessage(body, fallback));
	return apiBody<{ ok: boolean }>(body);
}
