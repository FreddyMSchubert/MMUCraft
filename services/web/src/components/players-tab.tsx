'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'

type StatGroup = 'profile' | 'money' | 'minecraft'

interface StatOption {
	key: string
	label: string
	group: StatGroup
	category?: string
}

interface PlayerProfile {
	preferredName: string
	courseYear: string
	discordUsername: string
	base: {
		x: number | null
		y: number | null
		z: number | null
	}
	bio: string
	updatedAtUnixMs: number
}

interface MinecraftStatValue {
	key: string
	category: string
	id: string
	label: string
	value: number
	updatedAtUnixMs: number
}

interface MinecraftProfile {
	uuid: string
	name: string
	skinUrl: string | null
	model: string | null
	fetchedAtUnixMs: number
}

interface PlayerStats {
	version: number
	money: {
		earnedDabloons: number
		balanceDabloons: number | null
		lastUpdatedAtUnixMs: number | null
		sources: Record<string, { earnedDabloons: number }>
	}
	minecraft: {
		stats: Record<string, MinecraftStatValue>
		lastSyncedAtUnixMs: number | null
		lastPlayedAtUnixMs: number | null
	}
	minecraftProfile: MinecraftProfile | null
}

interface PlayerSummary {
	id: number
	email: string
	minecraftUsername: string
	isCurrentUser: boolean
	profile: PlayerProfile
	stats: PlayerStats
}

interface PlayersResponse {
	currentUserId: number
	statOptions: StatOption[]
	players: PlayerSummary[]
}

const DEFAULT_COLUMN_KEYS = [
	'profile.playerName',
	'minecraft.lastPlayedAtUnixMs',
	'money.earnedDabloons',
	'money.balanceDabloons',
	'minecraft.custom.minecraft:jump',
]

export function PlayersTab() {
	const [data, setData] = useState<PlayersResponse | null>(null)
	const [error, setError] = useState('')
	const [message, setMessage] = useState('')
	const [columnKeys, setColumnKeys] = useState(DEFAULT_COLUMN_KEYS)
	const [selectedPlayerId, setSelectedPlayerId] = useState<number | null>(null)

	const load = useCallback(async () => {
		const response = await fetch('/api/players', {
			cache: 'no-store',
		})
		const body = await response.json().catch(() => null)

		if (!response.ok) {
			throw new Error(body?.message ?? 'Failed to load players')
		}

		setData(body as PlayersResponse)
	}, [])

	useEffect(() => {
		let cancelled = false

		async function loadInitial() {
			try {
				await load()
			} catch (caught) {
				if (!cancelled) {
					setError(caught instanceof Error ? caught.message : 'Failed to load players')
				}
			}
		}

		void loadInitial()

		return () => {
			cancelled = true
		}
	}, [load])

	const selectedPlayer = useMemo(() => {
		const players = data?.players ?? []
		return players.find((player) => player.id === selectedPlayerId) ?? players[0] ?? null
	}, [data?.players, selectedPlayerId])

	const statOptions = data?.statOptions ?? []
	const selectedColumns = columnKeys
		.map((key) => statOptions.find((option) => option.key === key) ?? fallbackOption(key))
		.filter((option): option is StatOption => Boolean(option))

	function updateColumn(index: number, key: string) {
		setColumnKeys((current) => current.map((value, currentIndex) => currentIndex === index ? key : value))
	}

	function addColumn() {
		const used = new Set(columnKeys)
		const next = statOptions.find((option) => !used.has(option.key))
		if (next) {
			setColumnKeys((current) => [...current, next.key])
		}
	}

	function removeColumn(index: number) {
		setColumnKeys((current) => current.length <= 1
			? current
			: current.filter((_, currentIndex) => currentIndex !== index))
	}

	async function handleSaved() {
		setMessage('Profile saved.')
		await load()
	}

	if (error && !data) {
		return <p className="authError">{error}</p>
	}

	if (!data) {
		return <p>Loading players...</p>
	}

	return (
		<div className="playersPanel">
			<div className="playersTop">
				<h3>Players</h3>
				<button type="button" onClick={addColumn} disabled={columnKeys.length >= statOptions.length}>
					Add column
				</button>
			</div>

			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}

			<div className="playersTableWrap">
				<table className="playersTable">
					<thead>
						<tr>
							<th scope="col">Head</th>
							{selectedColumns.map((column, index) => (
								<th scope="col" key={`${column.key}:${index}`}>
									<div className="playerColumnSelect">
										<select
											aria-label={`Column ${index + 1}`}
											value={columnKeys[index] ?? column.key}
											onChange={(event) => updateColumn(index, event.target.value)}
										>
											<StatOptionGroups options={statOptions} />
										</select>
										<button
											type="button"
											aria-label="Remove column"
											onClick={() => removeColumn(index)}
											disabled={columnKeys.length <= 1}
										>
											x
										</button>
									</div>
								</th>
							))}
						</tr>
					</thead>
					<tbody>
						{data.players.map((player) => (
							<tr
								key={player.id}
								className={player.id === selectedPlayer?.id ? 'active' : ''}
								onClick={() => setSelectedPlayerId(player.id)}
							>
								<td>
									<PlayerHead player={player} size="small" />
								</td>
								{selectedColumns.map((column, index) => (
									<td key={`${player.id}:${column.key}:${index}`}>
										{formatColumnValue(player, column)}
									</td>
								))}
							</tr>
						))}
					</tbody>
				</table>
			</div>

			{selectedPlayer && (
				<PlayerProfilePanel
					player={selectedPlayer}
					statOptions={statOptions}
					onSaved={() => void handleSaved()}
					onError={setError}
				/>
			)}
		</div>
	)
}

function StatOptionGroups({ options }: { options: StatOption[] }) {
	const grouped = groupStatOptions(options)

	return (
		<>
			{grouped.map((group) => (
				<optgroup key={group.group} label={formatGroup(group.group)}>
					{group.options.map((option) => (
						<option key={option.key} value={option.key}>{option.label}</option>
					))}
				</optgroup>
			))}
		</>
	)
}

function PlayerProfilePanel({
	player,
	statOptions,
	onSaved,
	onError,
}: {
	player: PlayerSummary
	statOptions: StatOption[]
	onSaved: () => void
	onError: (message: string) => void
}) {
	const [editing, setEditing] = useState(false)

	return (
		<section className="playerProfilePanel">
			<div className="playerProfileTop">
				<PlayerHead player={player} size="large" />
				<div className="playerProfileIdentity">
					<h4>{player.minecraftUsername}</h4>
					<ProfileFacts player={player} />
				</div>
				{player.isCurrentUser && (
					<button type="button" onClick={() => setEditing((current) => !current)}>
						{editing ? 'Cancel' : 'Edit profile'}
					</button>
				)}
			</div>

			{editing && player.isCurrentUser ? (
				<PlayerProfileForm
					player={player}
					onCancel={() => setEditing(false)}
					onSaved={() => {
						setEditing(false)
						onSaved()
					}}
					onError={onError}
				/>
			) : (
				<p className="playerBio">{player.profile.bio || 'No bio yet.'}</p>
			)}

			<PlayerStatsList player={player} statOptions={statOptions} />
		</section>
	)
}

function ProfileFacts({ player }: { player: PlayerSummary }) {
	const profile = player.profile
	const facts = [
		profile.preferredName ? { label: 'Name', value: profile.preferredName } : null,
		profile.courseYear ? { label: 'Course / Year', value: profile.courseYear } : null,
		profile.discordUsername ? { label: 'Discord', value: profile.discordUsername } : null,
		hasBase(profile) ? { label: 'Base', value: formatBase(profile.base) } : null,
	].filter((fact): fact is { label: string; value: string } => Boolean(fact))

	if (facts.length === 0) {
		return <p className="playerProfileEmpty">No profile details yet.</p>
	}

	return (
		<dl className="playerFacts">
			{facts.map((fact) => (
				<div key={fact.label}>
					<dt>{fact.label}</dt>
					<dd>{fact.value}</dd>
				</div>
			))}
		</dl>
	)
}

function PlayerProfileForm({
	player,
	onCancel,
	onSaved,
	onError,
}: {
	player: PlayerSummary
	onCancel: () => void
	onSaved: () => void
	onError: (message: string) => void
}) {
	const [preferredName, setPreferredName] = useState(player.profile.preferredName)
	const [courseYear, setCourseYear] = useState(player.profile.courseYear)
	const [discordUsername, setDiscordUsername] = useState(player.profile.discordUsername)
	const [baseX, setBaseX] = useState(player.profile.base.x?.toString() ?? '')
	const [baseY, setBaseY] = useState(player.profile.base.y?.toString() ?? '')
	const [baseZ, setBaseZ] = useState(player.profile.base.z?.toString() ?? '')
	const [bio, setBio] = useState(player.profile.bio)
	const [saving, setSaving] = useState(false)

	async function save() {
		setSaving(true)
		onError('')

		try {
			const response = await fetch('/api/players/me/profile', {
				method: 'PATCH',
				headers: {
					'content-type': 'application/json',
				},
				body: JSON.stringify({
					preferredName,
					courseYear,
					discordUsername,
					baseX: baseX === '' ? null : Number(baseX),
					baseY: baseY === '' ? null : Number(baseY),
					baseZ: baseZ === '' ? null : Number(baseZ),
					bio,
				}),
			})
			const body = await response.json().catch(() => null)

			if (!response.ok) {
				throw new Error(body?.message ?? 'Failed to save profile')
			}

			onSaved()
		} catch (caught) {
			onError(caught instanceof Error ? caught.message : 'Failed to save profile')
		} finally {
			setSaving(false)
		}
	}

	return (
		<form
			className="playerProfileForm"
			onSubmit={(event) => {
				event.preventDefault()
				void save()
			}}
		>
			<label>
				<span>Name</span>
				<input value={preferredName} onChange={(event) => setPreferredName(event.target.value)} maxLength={80} />
			</label>
			<label>
				<span>Course / Year</span>
				<input value={courseYear} onChange={(event) => setCourseYear(event.target.value)} maxLength={80} />
			</label>
			<label>
				<span>Discord</span>
				<input value={discordUsername} onChange={(event) => setDiscordUsername(event.target.value)} maxLength={80} />
			</label>
			<div className="playerBaseInputs">
				<label>
					<span>X</span>
					<input type="number" value={baseX} onChange={(event) => setBaseX(event.target.value)} />
				</label>
				<label>
					<span>Y</span>
					<input type="number" value={baseY} onChange={(event) => setBaseY(event.target.value)} />
				</label>
				<label>
					<span>Z</span>
					<input type="number" value={baseZ} onChange={(event) => setBaseZ(event.target.value)} />
				</label>
			</div>
			<label className="playerBioInput">
				<span>Bio</span>
				<textarea value={bio} onChange={(event) => setBio(event.target.value)} maxLength={500} rows={4} />
			</label>
			<div className="playerProfileActions">
				<button type="submit" disabled={saving}>{saving ? 'Saving...' : 'Save'}</button>
				<button type="button" onClick={onCancel} disabled={saving}>Cancel</button>
			</div>
		</form>
	)
}

function PlayerStatsList({ player, statOptions }: { player: PlayerSummary; statOptions: StatOption[] }) {
	const optionByKey = new Map(statOptions.map((option) => [option.key, option]))
	const statGroups = groupMinecraftStats(Object.values(player.stats.minecraft.stats))

	return (
		<div className="playerStatsPanel">
			<h5>Stats</h5>
			<div className="playerStatsCore">
				<StatLine label="Last Played" value={formatTimestamp(player.stats.minecraft.lastPlayedAtUnixMs)} />
				<StatLine label="Dabloons Earned" value={formatNumber(player.stats.money.earnedDabloons)} />
				<StatLine label="Current Balance" value={formatNullableNumber(player.stats.money.balanceDabloons)} />
				<StatLine label="Last Sync" value={formatTimestamp(player.stats.minecraft.lastSyncedAtUnixMs)} />
			</div>

			{statGroups.length === 0 ? (
				<p className="playerProfileEmpty">No Minecraft stats synced yet.</p>
			) : (
				<div className="playerStatGroups">
					{statGroups.map((group) => (
						<details key={group.category} open={group.category === 'custom'}>
							<summary>{formatCategory(group.category)}</summary>
							<div className="playerStatGrid">
								{group.stats.map((stat) => (
									<StatLine
										key={stat.key}
										label={optionByKey.get(stat.key)?.label ?? stat.label}
										value={formatMinecraftStatValue(stat)}
									/>
								))}
							</div>
						</details>
					))}
				</div>
			)}
		</div>
	)
}

function StatLine({ label, value }: { label: string; value: string }) {
	return (
		<div className="playerStatLine">
			<span>{label}</span>
			<strong>{value}</strong>
		</div>
	)
}

function PlayerHead({ player, size }: { player: PlayerSummary; size: 'small' | 'large' }) {
	const skinUrl = player.stats.minecraftProfile?.skinUrl
	const label = `${player.minecraftUsername} head`

	if (!skinUrl) {
		return (
			<span className={`playerHead playerHead-${size} playerHeadFallback`} role="img" aria-label={label}>
				{player.minecraftUsername.charAt(0).toUpperCase()}
			</span>
		)
	}

	return (
		<span className={`playerHead playerHead-${size}`} role="img" aria-label={label}>
			<span className="playerHeadLayer playerHeadFace" style={{ backgroundImage: `url("${skinUrl}")` }} />
			<span className="playerHeadLayer playerHeadHat" style={{ backgroundImage: `url("${skinUrl}")` }} />
		</span>
	)
}

function formatColumnValue(player: PlayerSummary, option: StatOption) {
	if (option.key === 'profile.playerName') return player.minecraftUsername
	if (option.key === 'profile.preferredName') return player.profile.preferredName || '-'
	if (option.key === 'profile.courseYear') return player.profile.courseYear || '-'
	if (option.key === 'profile.discordUsername') return player.profile.discordUsername || '-'
	if (option.key === 'profile.base') return hasBase(player.profile) ? formatBase(player.profile.base) : '-'
	if (option.key === 'money.earnedDabloons') return formatNumber(player.stats.money.earnedDabloons)
	if (option.key === 'money.balanceDabloons') return formatNullableNumber(player.stats.money.balanceDabloons)
	if (option.key === 'minecraft.lastPlayedAtUnixMs') return formatTimestamp(player.stats.minecraft.lastPlayedAtUnixMs)

	const stat = player.stats.minecraft.stats[option.key]
	return stat ? formatMinecraftStatValue(stat) : '0'
}

function formatMinecraftStatValue(stat: MinecraftStatValue) {
	if (stat.id.endsWith('_one_cm')) {
		const meters = stat.value / 100
		if (meters >= 1000) return `${formatNumber(meters / 1000)} km`
		return `${formatNumber(meters)} m`
	}

	if (stat.id.includes('_time') || stat.id.endsWith(':play_time')) {
		return formatTicks(stat.value)
	}

	return formatNumber(stat.value)
}

function formatTicks(value: number) {
	const seconds = Math.floor(value / 20)
	const hours = Math.floor(seconds / 3600)
	const minutes = Math.floor((seconds % 3600) / 60)

	if (hours > 0) return `${hours}h ${minutes}m`
	if (minutes > 0) return `${minutes}m`
	return `${seconds}s`
}

function formatTimestamp(value: number | null) {
	if (!value) return 'Never'

	return new Intl.DateTimeFormat(undefined, {
		dateStyle: 'medium',
		timeStyle: 'short',
	}).format(new Date(value))
}

function formatNullableNumber(value: number | null) {
	return value === null ? '-' : formatNumber(value)
}

function formatNumber(value: number) {
	return new Intl.NumberFormat().format(value)
}

function formatBase(base: PlayerProfile['base']) {
	return [base.x, base.y, base.z].map((value) => value ?? '?').join(', ')
}

function hasBase(profile: PlayerProfile) {
	return profile.base.x !== null || profile.base.y !== null || profile.base.z !== null
}

function groupStatOptions(options: StatOption[]) {
	return (['profile', 'money', 'minecraft'] as const).map((group) => ({
		group,
		options: options.filter((option) => option.group === group),
	})).filter((group) => group.options.length > 0)
}

function groupMinecraftStats(stats: MinecraftStatValue[]) {
	const visible = stats
		.filter((stat) => stat.value > 0 || stat.category === 'custom')
		.sort((left, right) => {
			const category = categoryRank(left.category) - categoryRank(right.category)
			if (category !== 0) return category
			return left.label.localeCompare(right.label, 'en')
		})
	const groups = new Map<string, MinecraftStatValue[]>()

	for (const stat of visible) {
		const group = groups.get(stat.category) ?? []
		group.push(stat)
		groups.set(stat.category, group)
	}

	return [...groups.entries()].map(([category, groupStats]) => ({
		category,
		stats: groupStats,
	}))
}

function categoryRank(category: string) {
	if (category === 'custom') return 0
	if (category === 'killed') return 1
	if (category === 'killed_by') return 2
	return 3
}

function formatCategory(category: string) {
	if (category === 'custom') return 'General'
	if (category === 'killed') return 'Mobs Killed'
	if (category === 'killed_by') return 'Killed By'
	return category
		.split(/[_-]+/)
		.map((part) => part.charAt(0).toUpperCase() + part.slice(1))
		.join(' ')
}

function formatGroup(group: StatGroup) {
	if (group === 'profile') return 'Profile'
	if (group === 'money') return 'Dabloons'
	return 'Minecraft Stats'
}

function fallbackOption(key: string): StatOption {
	return {
		key,
		label: key,
		group: key.startsWith('money.') ? 'money' : key.startsWith('profile.') ? 'profile' : 'minecraft',
	}
}
