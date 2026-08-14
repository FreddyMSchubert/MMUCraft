'use client'

import { FormEvent, useCallback, useEffect, useState } from 'react'

interface VelocityServer {
	id: number
	name: string
	address: string
	isDefault: boolean
	health: 'online' | 'offline' | 'unknown'
	latencyMs: number | null
	error: string | null
	playerCount: number
}

interface VelocityPlayer {
	uuid: string
	username: string
	serverName: string
}

interface VelocitySchedule {
	id: number
	name: string
	serverId: number
	serverName: string
	startsAtUnixMs: number
	endsAtUnixMs: number
}

interface VelocitySnapshot {
	nowUnixMs: number
	proxyOnline: boolean
	lastHeartbeatUnixMs: number | null
	maintenanceMode: boolean
	activeScheduleId: number | null
	servers: VelocityServer[]
	players: VelocityPlayer[]
	schedules: VelocitySchedule[]
}

export function VelocityAdmin({ section }: { section: 'servers' | 'maintenance' }) {
	const [snapshot, setSnapshot] = useState<VelocitySnapshot | null>(null)
	const [serverName, setServerName] = useState('')
	const [serverAddress, setServerAddress] = useState('')
	const [scheduleName, setScheduleName] = useState('')
	const [scheduleServerId, setScheduleServerId] = useState('')
	const [scheduleStartsAt, setScheduleStartsAt] = useState('')
	const [scheduleEndsAt, setScheduleEndsAt] = useState('')
	const [moveTargets, setMoveTargets] = useState<Record<string, string>>({})
	const [busy, setBusy] = useState(false)
	const [message, setMessage] = useState('')
	const [error, setError] = useState('')

	const load = useCallback(async () => {
		const response = await fetch('/api/admin/velocity', { cache: 'no-store' })
		const body = await response.json().catch(() => null)
		if (!response.ok) throw new Error(apiMessage(body, 'Failed to load Velocity state'))
		setSnapshot(body as VelocitySnapshot)
	}, [])

	useEffect(() => {
		let cancelled = false
		let timer: number | undefined
		async function refresh() {
			try {
				await load()
				if (!cancelled) setError('')
			} catch (caught) {
				if (!cancelled) setError(errorMessage(caught, 'Failed to load Velocity state'))
			} finally {
				if (!cancelled) timer = window.setTimeout(refresh, 3_000)
			}
		}
		void refresh()
		return () => {
			cancelled = true
			window.clearTimeout(timer)
		}
	}, [load])

	async function addServer(event: FormEvent<HTMLFormElement>) {
		event.preventDefault()
		await mutate(async () => {
			const response = await fetch('/api/admin/velocity/servers', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ name: serverName, address: serverAddress }),
			})
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to add server'))
			setServerName('')
			setServerAddress('')
			setMessage(`Added ${body.server.name}. Velocity will health-check it within a few seconds.`)
		})
	}

	async function setDefault(server: VelocityServer) {
		if (!window.confirm(`Make ${server.name} the default server for all players outside schedules?`)) return
		await mutate(async () => {
			const response = await fetch(`/api/admin/velocity/servers/${server.id}/default`, { method: 'PATCH' })
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to set the default server'))
			setMessage(`${server.name} is now the default server.`)
		})
	}

	async function removeServer(server: VelocityServer) {
		if (!window.confirm(`Remove ${server.name} from Velocity? This does not stop or delete its Docker container.`)) return
		await mutate(async () => {
			const response = await fetch(`/api/admin/velocity/servers/${server.id}`, { method: 'DELETE' })
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to remove the server'))
			setMessage(`${server.name} was removed from Velocity.`)
		})
	}

	async function addSchedule(event: FormEvent<HTMLFormElement>) {
		event.preventDefault()
		const startsAtUnixMs = new Date(scheduleStartsAt).getTime()
		const endsAtUnixMs = new Date(scheduleEndsAt).getTime()
		await mutate(async () => {
			const response = await fetch('/api/admin/velocity/schedules', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					name: scheduleName,
					serverId: Number(scheduleServerId),
					startsAtUnixMs,
					endsAtUnixMs,
				}),
			})
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to create the schedule'))
			setScheduleName('')
			setScheduleServerId('')
			setScheduleStartsAt('')
			setScheduleEndsAt('')
			setMessage('Routing schedule created.')
		})
	}

	async function removeSchedule(schedule: VelocitySchedule) {
		if (!window.confirm(`Remove the routing schedule “${schedule.name}”?`)) return
		await mutate(async () => {
			const response = await fetch(`/api/admin/velocity/schedules/${schedule.id}`, { method: 'DELETE' })
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to remove the schedule'))
			setMessage('Routing schedule removed.')
		})
	}

	async function movePlayer(player: VelocityPlayer) {
		const serverId = Number(moveTargets[player.uuid])
		if (!serverId) {
			setError('Select a target server')
			return
		}
		await mutate(async () => {
			const response = await fetch(`/api/admin/velocity/players/${encodeURIComponent(player.uuid)}/move`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ serverId }),
			})
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to move the player'))
			setMessage(`${player.username} will move within a few seconds. This manual choice overrides the active schedule for the current session.`)
		})
	}

	async function setMaintenanceMode(enabled: boolean) {
		if (enabled && !window.confirm('Enable maintenance mode? Every online player will be disconnected and all new logins will be rejected.')) return
		await mutate(async () => {
			const response = await fetch('/api/admin/velocity/maintenance', {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ enabled }),
			})
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to change maintenance mode'))
			setMessage(enabled ? 'Maintenance mode is on.' : 'Maintenance mode is off. Players can join again.')
		})
	}

	async function mutate(action: () => Promise<void>) {
		setBusy(true)
		setError('')
		setMessage('')
		try {
			await action()
			await load()
		} catch (caught) {
			setError(errorMessage(caught, 'The Velocity operation failed'))
		} finally {
			setBusy(false)
		}
	}

	if (!snapshot) {
		return <section className="adminSection"><p>{error || 'Loading Velocity state...'}</p></section>
	}

	if (section === 'maintenance') {
		return <section className="adminSection maintenanceSection">
			<div className="adminSectionHeader">
				<h3>Maintenance mode</h3>
				<p>Velocity applies this global gate to every backend server.</p>
			</div>
			<label className="maintenanceToggle">
				<input
					type="checkbox"
					checked={snapshot.maintenanceMode}
					disabled={busy}
					onChange={(event) => void setMaintenanceMode(event.target.checked)}
				/>
				<span>{snapshot.maintenanceMode ? 'Maintenance mode is on' : 'Maintenance mode is off'}</span>
			</label>
			<p className="velocityHint">When on, current players are disconnected and new logins receive the maintenance message within one control-sync interval.</p>
			<p className={snapshot.proxyOnline ? 'velocityStatus velocityStatus-online' : 'velocityStatus velocityStatus-offline'}>
				Velocity proxy: {snapshot.proxyOnline ? 'online' : 'not reporting'}
			</p>
			{message && <p className="adminMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}
		</section>
	}

	return <div className="velocityAdmin">
		<section className="adminSection">
			<div className="adminSectionHeader velocityHeading">
				<div><h3>Player server monitor</h3><p>Velocity reports backend health and current player locations every three seconds.</p></div>
				<span className={snapshot.proxyOnline ? 'velocityStatus velocityStatus-online' : 'velocityStatus velocityStatus-offline'}>
					Proxy {snapshot.proxyOnline ? 'online' : 'offline'}
				</span>
			</div>

			<form className="velocityForm" onSubmit={addServer}>
				<label>Velocity name<input value={serverName} onChange={(event) => setServerName(event.target.value)} placeholder="event" pattern="[a-z0-9][a-z0-9_-]{0,31}" required /></label>
				<label>Docker address<input value={serverAddress} onChange={(event) => setServerAddress(event.target.value)} placeholder="event-server:25565" required /></label>
				<button disabled={busy}>Add server</button>
			</form>
			<p className="velocityHint">Start the container manually without a public port, attach it to the <code>kubecraft_app</code> Docker network, configure the shared Velocity forwarding secret, then enter its Docker name and internal port here.</p>

			<div className="adminTableWrap"><table className="adminTable"><thead><tr><th>Server</th><th>Address</th><th>Health</th><th>Players</th><th>Routing</th><th></th></tr></thead><tbody>
				{snapshot.servers.map((server) => <tr key={server.id}>
					<td><strong>{server.name}</strong></td>
					<td><code>{server.address}</code></td>
					<td><span className={`velocityHealth velocityHealth-${server.health}`}>{server.health}{server.latencyMs !== null ? ` · ${server.latencyMs} ms` : ''}</span>{server.error && <small className="velocityError">{server.error}</small>}</td>
					<td>{server.playerCount}</td>
					<td>{server.isDefault ? <strong>Default</strong> : <button type="button" disabled={busy} onClick={() => void setDefault(server)}>Make default</button>}</td>
					<td><button type="button" disabled={busy || server.isDefault} onClick={() => void removeServer(server)}>Remove</button></td>
				</tr>)}
			</tbody></table></div>
		</section>

		<section className="adminSection">
			<div className="adminSectionHeader"><h3>Online players</h3><p>Manual moves override the current schedule until that schedule changes or ends.</p></div>
			<div className="adminTableWrap"><table className="adminTable"><thead><tr><th>Player</th><th>Current server</th><th>Move to</th><th></th></tr></thead><tbody>
				{snapshot.players.map((player) => <tr key={player.uuid}>
					<td>{player.username}</td><td>{player.serverName}</td>
					<td><select value={moveTargets[player.uuid] ?? ''} onChange={(event) => setMoveTargets((current) => ({ ...current, [player.uuid]: event.target.value }))}><option value="">Select a healthy server</option>{snapshot.servers.filter((server) => server.health === 'online' && server.name !== player.serverName).map((server) => <option key={server.id} value={server.id}>{server.name}</option>)}</select></td>
					<td><button type="button" disabled={busy} onClick={() => void movePlayer(player)}>Move</button></td>
				</tr>)}
				{snapshot.players.length === 0 && <tr><td colSpan={4}>No players are online.</td></tr>}
			</tbody></table></div>
		</section>

		<section className="adminSection">
			<div className="adminSectionHeader"><h3>Routing schedules</h3><p>At the start and end, every connected player moves to the newly assigned target. Overlapping schedules are not allowed.</p></div>
			<form className="velocityScheduleForm" onSubmit={addSchedule}>
				<label>Event name<input value={scheduleName} onChange={(event) => setScheduleName(event.target.value)} maxLength={80} required /></label>
				<label>Server<select value={scheduleServerId} onChange={(event) => setScheduleServerId(event.target.value)} required><option value="">Select a server</option>{snapshot.servers.map((server) => <option key={server.id} value={server.id}>{server.name}</option>)}</select></label>
				<label>Starts<input type="datetime-local" value={scheduleStartsAt} onChange={(event) => setScheduleStartsAt(event.target.value)} required /></label>
				<label>Ends<input type="datetime-local" value={scheduleEndsAt} onChange={(event) => setScheduleEndsAt(event.target.value)} required /></label>
				<button disabled={busy}>Create schedule</button>
			</form>
			<div className="adminTableWrap"><table className="adminTable"><thead><tr><th>Event</th><th>Server</th><th>Starts</th><th>Ends</th><th>Status</th><th></th></tr></thead><tbody>
				{snapshot.schedules.map((schedule) => <tr key={schedule.id}><td>{schedule.name}</td><td>{schedule.serverName}</td><td>{formatDateTime(schedule.startsAtUnixMs)}</td><td>{formatDateTime(schedule.endsAtUnixMs)}</td><td>{schedule.id === snapshot.activeScheduleId ? 'Active' : schedule.endsAtUnixMs <= snapshot.nowUnixMs ? 'Ended' : 'Upcoming'}</td><td><button type="button" disabled={busy} onClick={() => void removeSchedule(schedule)}>Remove</button></td></tr>)}
				{snapshot.schedules.length === 0 && <tr><td colSpan={6}>No routing schedules.</td></tr>}
			</tbody></table></div>
		</section>
		{message && <p className="adminMessage">{message}</p>}
		{error && <p className="authError">{error}</p>}
	</div>
}

function formatDateTime(value: number) {
	return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function apiMessage(body: unknown, fallback: string) {
	if (!body || typeof body !== 'object') return fallback
	const message = (body as { message?: unknown }).message
	return typeof message === 'string' ? message : fallback
}

function errorMessage(error: unknown, fallback: string) {
	return error instanceof Error ? error.message : fallback
}
