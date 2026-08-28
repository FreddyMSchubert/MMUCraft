'use client';

import { type SyntheticEvent, useCallback, useEffect, useState } from 'react';
import { useSiteAlert } from '@/components/site-alert';
import { apiBody, apiMessage, errorMessage, fetchAdmin, formatDateTime } from './admin-api';

interface VelocityServer {
	id: number;
	name: string;
	address: string;
	isDefault: boolean;
	health: 'online' | 'offline' | 'unknown';
	latencyMs: number | null;
	error: string | null;
	playerCount: number;
}

interface VelocityPlayer {
	uuid: string;
	username: string;
	serverName: string;
}

interface VelocitySchedule {
	id: number;
	name: string;
	serverId: number;
	serverName: string;
	startsAtUnixMs: number;
	endsAtUnixMs: number;
}

interface VelocitySnapshot {
	nowUnixMs: number;
	proxyOnline: boolean;
	maintenanceMode: boolean;
	activeScheduleId: number | null;
	servers: VelocityServer[];
	players: VelocityPlayer[];
	schedules: VelocitySchedule[];
}

export function VelocityAdminSection({ section }: { section: 'servers' | 'maintenance' }) {
	const { confirm, showAlert } = useSiteAlert();
	const [snapshot, setSnapshot] = useState<VelocitySnapshot | null>(null);
	const [serverName, setServerName] = useState('');
	const [serverAddress, setServerAddress] = useState('');
	const [scheduleName, setScheduleName] = useState('');
	const [scheduleServerId, setScheduleServerId] = useState('');
	const [scheduleStartsAt, setScheduleStartsAt] = useState('');
	const [scheduleEndsAt, setScheduleEndsAt] = useState('');
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

	async function mutate(title: string, action: () => Promise<string>) {
		setBusy(true);
		setError('');
		try {
			const message = await action();
			await load();
			await showAlert({ title, message, tone: 'success' });
		} catch (caught) {
			await showAlert({
				title: 'Velocity operation failed',
				message: errorMessage(caught, 'The Velocity operation failed'),
				tone: 'danger',
			});
		} finally {
			setBusy(false);
		}
	}

	async function addServer(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		await mutate('Server added', async () => {
			const result = await velocityRequest<{ server: VelocityServer }>(
				'/api/admin/velocity/servers',
				{
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ name: serverName, address: serverAddress }),
				},
				'Failed to add server',
			);
			setServerName('');
			setServerAddress('');
			return `${result.server.name} was added. Velocity will check it within a few seconds.`;
		});
	}

	async function setDefault(server: VelocityServer) {
		if (
			!(await confirm({
				title: 'Change the default server?',
				message: `${server.name} will be used whenever no routing schedule is active. If no schedule is active now, connected players will move there within a few seconds. During an active schedule, players stay on its server and move to ${server.name} when it ends.`,
				confirmLabel: 'Change default',
			}))
		)
			return;
		await mutate('Default server changed', async () => {
			await velocityRequest(
				`/api/admin/velocity/servers/${server.id}/default`,
				{ method: 'PATCH' },
				'Failed to set the default server',
			);
			return `${server.name} is now the default server used outside routing schedules.`;
		});
	}

	async function removeServer(server: VelocityServer) {
		if (
			!(await confirm({
				title: 'Remove this server from Velocity?',
				message: `This removes ${server.name} from routing. It does not stop or delete its container.`,
				confirmLabel: 'Remove server',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;
		await mutate('Server removed', async () => {
			await velocityRequest(
				`/api/admin/velocity/servers/${server.id}`,
				{ method: 'DELETE' },
				'Failed to remove the server',
			);
			return `${server.name} was removed from Velocity.`;
		});
	}

	async function addSchedule(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		await mutate('Routing schedule created', async () => {
			await velocityRequest(
				'/api/admin/velocity/schedules',
				{
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({
						name: scheduleName,
						serverId: Number(scheduleServerId),
						startsAtUnixMs: new Date(scheduleStartsAt).getTime(),
						endsAtUnixMs: new Date(scheduleEndsAt).getTime(),
					}),
				},
				'Failed to create the schedule',
			);
			setScheduleName('');
			setScheduleServerId('');
			setScheduleStartsAt('');
			setScheduleEndsAt('');
			return 'At the start, connected players will move to the scheduled server. At the end, they will move to the default server.';
		});
	}

	async function removeSchedule(schedule: VelocitySchedule) {
		if (
			!(await confirm({
				title: 'Remove this routing schedule?',
				message: `The schedule “${schedule.name}” will stop controlling the route. If it is active, connected players will return to the default server within a few seconds.`,
				confirmLabel: 'Remove schedule',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;
		await mutate('Routing schedule removed', async () => {
			await velocityRequest(
				`/api/admin/velocity/schedules/${schedule.id}`,
				{ method: 'DELETE' },
				'Failed to remove the schedule',
			);
			return `The schedule “${schedule.name}” was removed.`;
		});
	}

	async function movePlayer(player: VelocityPlayer) {
		const serverId = Number(moveTargets[player.uuid]);
		if (!serverId) {
			await showAlert({
				title: 'Select a target server',
				message: 'Choose one of the healthy servers before you move this player.',
				tone: 'danger',
			});
			return;
		}
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
			return `${player.username} will move within a few seconds. This choice lasts until the player disconnects or the default or scheduled route changes.`;
		});
	}

	async function setMaintenanceMode(enabled: boolean) {
		if (
			enabled &&
			!(await confirm({
				title: 'Enable maintenance mode?',
				message:
					'Every online player will disconnect. Velocity will reject all new logins until you turn maintenance mode off.',
				confirmLabel: 'Enable maintenance',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;
		await mutate(enabled ? 'Maintenance enabled' : 'Maintenance disabled', async () => {
			await velocityRequest(
				'/api/admin/velocity/maintenance',
				{
					method: 'PATCH',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ enabled }),
				},
				'Failed to change maintenance mode',
			);
			return enabled
				? 'Velocity will disconnect current players and reject new logins within a few seconds.'
				: 'Players can join again.';
		});
	}

	if (!snapshot)
		return (
			<section className="adminSection">
				<p>{error || 'Loading Velocity state...'}</p>
			</section>
		);

	if (section === 'maintenance') {
		return (
			<section className="adminSection maintenanceSection">
				<div className="adminSectionHeader">
					<h3>Maintenance mode</h3>
					<p>Velocity applies this gate before a player reaches any backend server.</p>
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
				<p className="velocityHint">
					A change reaches Velocity during its next three-second control sync.
				</p>
				<p
					className={`velocityStatus velocityStatus-${snapshot.proxyOnline ? 'online' : 'offline'}`}
				>
					Velocity proxy: {snapshot.proxyOnline ? 'online' : 'not reporting'}
				</p>
				{error && <p className="authError">{error}</p>}
			</section>
		);
	}

	return (
		<div className="velocityAdmin">
			<section className="adminSection">
				<div className="adminSectionHeader velocityHeading">
					<div>
						<h3>Server monitor</h3>
						<p>
							Velocity reports backend health and player locations every three
							seconds. The default server receives players whenever no schedule is
							active.
						</p>
					</div>
					<span
						className={`velocityStatus velocityStatus-${snapshot.proxyOnline ? 'online' : 'offline'}`}
					>
						Proxy {snapshot.proxyOnline ? 'online' : 'offline'}
					</span>
				</div>

				<form className="velocityForm" onSubmit={addServer}>
					<label>
						Velocity name
						<input
							value={serverName}
							onChange={(event) => {
								setServerName(event.target.value);
							}}
							placeholder="event"
							pattern="[a-z0-9][a-z0-9_-]{0,31}"
							required
						/>
					</label>
					<label>
						Docker address
						<input
							value={serverAddress}
							onChange={(event) => {
								setServerAddress(event.target.value);
							}}
							placeholder="event-server:25565"
							required
						/>
					</label>
					<button disabled={busy || snapshot.servers.length >= 2}>Add server</button>
				</form>
				<p className="velocityHint">
					{snapshot.servers.length >= 2
						? 'The current setup supports the main server and one additional server.'
						: 'Start the backend without a public port, attach it to the kubecraft_app Docker network, and give it the shared forwarding secret. Then enter its Docker name and internal port here.'}
				</p>

				<div className="adminTableWrap">
					<table className="adminTable">
						<thead>
							<tr>
								<th>Server</th>
								<th>Address</th>
								<th>Health</th>
								<th>Players</th>
								<th>Routing</th>
								<th></th>
							</tr>
						</thead>
						<tbody>
							{snapshot.servers.map((server) => (
								<tr key={server.id}>
									<td>
										<strong>{server.name}</strong>
									</td>
									<td>
										<code>{server.address}</code>
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
									<td>
										{server.isDefault ? (
											<strong>Default</strong>
										) : (
											<button
												type="button"
												disabled={busy}
												onClick={() => void setDefault(server)}
											>
												Make default
											</button>
										)}
									</td>
									<td>
										<button
											type="button"
											disabled={
												busy || server.isDefault || server.name === 'main'
											}
											onClick={() => void removeServer(server)}
										>
											Remove
										</button>
									</td>
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
						A manual move overrides the default or scheduled route for one player. It
						ends when the player disconnects or that route changes.
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
									<td>{player.username}</td>
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
											disabled={busy}
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

			<section className="adminSection">
				<div className="adminSectionHeader">
					<h3>Routing schedules</h3>
					<p>
						While a schedule is active, new players join its server instead of the
						default server. At its start, connected players move to the scheduled
						server. At its end, they move to the current default server. Only one
						scheduled route can be active, so schedules cannot overlap. There is no
						automatic fallback if the scheduled server is offline.
					</p>
				</div>
				<form className="velocityScheduleForm" onSubmit={addSchedule}>
					<label>
						Event name
						<input
							value={scheduleName}
							onChange={(event) => {
								setScheduleName(event.target.value);
							}}
							maxLength={80}
							required
						/>
					</label>
					<label>
						Server
						<select
							value={scheduleServerId}
							onChange={(event) => {
								setScheduleServerId(event.target.value);
							}}
							required
						>
							<option value="">Select a server</option>
							{snapshot.servers.map((server) => (
								<option key={server.id} value={server.id}>
									{server.name}
								</option>
							))}
						</select>
					</label>
					<label>
						Starts
						<input
							type="datetime-local"
							value={scheduleStartsAt}
							onChange={(event) => {
								setScheduleStartsAt(event.target.value);
							}}
							required
						/>
					</label>
					<label>
						Ends
						<input
							type="datetime-local"
							value={scheduleEndsAt}
							onChange={(event) => {
								setScheduleEndsAt(event.target.value);
							}}
							required
						/>
					</label>
					<button disabled={busy}>Create schedule</button>
				</form>
				<div className="adminTableWrap">
					<table className="adminTable">
						<thead>
							<tr>
								<th>Event</th>
								<th>Server</th>
								<th>Starts</th>
								<th>Ends</th>
								<th>Status</th>
								<th></th>
							</tr>
						</thead>
						<tbody>
							{snapshot.schedules.map((schedule) => (
								<tr key={schedule.id}>
									<td>{schedule.name}</td>
									<td>{schedule.serverName}</td>
									<td>{formatDateTime(schedule.startsAtUnixMs)}</td>
									<td>{formatDateTime(schedule.endsAtUnixMs)}</td>
									<td>
										{schedule.id === snapshot.activeScheduleId
											? 'Active'
											: schedule.endsAtUnixMs <= snapshot.nowUnixMs
												? 'Ended'
												: 'Upcoming'}
									</td>
									<td>
										<button
											type="button"
											disabled={busy}
											onClick={() => void removeSchedule(schedule)}
										>
											Remove
										</button>
									</td>
								</tr>
							))}
							{snapshot.schedules.length === 0 && (
								<tr>
									<td colSpan={6}>No routing schedules.</td>
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

async function velocityRequest<T extends object = { ok: boolean }>(
	path: string,
	init: RequestInit,
	fallback: string,
) {
	const response = await fetch(path, init);
	const body = await response.json().catch(() => null);
	if (!response.ok) throw new Error(apiMessage(body, fallback));
	return apiBody<T>(body);
}
