'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactElement } from 'react'
import { MiniFishCompendium } from '@/components/fishing-tab'
import { LeaderboardPodium } from '@/components/leaderboard-podium'
import { PlayerName } from '@/components/player-name'

type StatGroup = 'profile' | 'money' | 'fishing' | 'minecraft'

interface StatOption {
	key: string
	label: string
	group: StatGroup
	category?: string
}

interface PlayerProfile {
	preferredName: string
	pronouns: string
	courseYear: string
	discordUsername: string
	base: {
		x: number | null
		y: number | null
		z: number | null
	}
	bio: string
	color: string
	defaultColor: string
	customColor: string | null
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
	minecraftUsername: string
	isCurrentUser: boolean
	isMember: boolean
	isCommittee: boolean
	isExternal: boolean
	responsibleMinecraftUsername: string | null
	responsiblePlayerColor: string | null
	profile: PlayerProfile
	fishing: Record<string, number>
	stats: PlayerStats
}

interface PlayersResponse {
	currentUserId: number
	statOptions: StatOption[]
	players: PlayerSummary[]
}

const DEFAULT_COLUMN_KEYS = [
	'profile.preferredName',
	'minecraft.lastPlayedAtUnixMs',
	'minecraft.custom.minecraft:play_time',
	'minecraft.custom.minecraft:deaths',
]
const DEFAULT_LEADERBOARD_KEY = 'minecraft.advancement.minecraft:earned'
const PAGE_SIZE = 42
const PROFILE_TEXT_LIMITS = {
	preferredName: 16,
	pronouns: 16,
	courseYear: 64,
	discordUsername: 40,
	bio: 280,
} as const

type SortDirection = 'desc' | 'asc'

export function PlayersTab({ playerName, onSelectPlayer }: {
	playerName?: string
	onSelectPlayer: (playerName: string | null, replace?: boolean) => void
}) {
	const [data, setData] = useState<PlayersResponse | null>(null)
	const [error, setError] = useState('')
	const [message, setMessage] = useState('')
	const [columnKeys, setColumnKeys] = useState(DEFAULT_COLUMN_KEYS)
	const [sort, setSort] = useState<{ key: string; direction: SortDirection }>({
		key: 'minecraft.custom.minecraft:play_time',
		direction: 'desc',
	})
	const [leaderboardKey, setLeaderboardKey] = useState(DEFAULT_LEADERBOARD_KEY)
	const [visiblePlayerCount, setVisiblePlayerCount] = useState(PAGE_SIZE)

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

	const players = useMemo(() => data?.players ?? [], [data?.players])
	const currentPlayer = players.find((player) => player.isCurrentUser)
	const statOptions = useMemo(() => data?.statOptions ?? [], [data?.statOptions])
	const selectedPlayer = useMemo(() => {
		if (!playerName) return null
		return players.find((player) => player.minecraftUsername.localeCompare(playerName, 'en', { sensitivity: 'base' }) === 0) ?? null
	}, [playerName, players])

	useEffect(() => {
		if (!data || !playerName) return
		if (!selectedPlayer) {
			onSelectPlayer(null, true)
			return
		}
		if (selectedPlayer.minecraftUsername !== playerName) {
			onSelectPlayer(selectedPlayer.minecraftUsername, true)
		}
	}, [data, onSelectPlayer, playerName, selectedPlayer])

	const selectedColumns = useMemo(() => columnKeys
		.slice(0, 4)
		.map((key) => statOptions.find((option) => option.key === key) ?? fallbackOption(key))
		.filter((option): option is StatOption => Boolean(option)), [columnKeys, statOptions])
	const sortedPlayers = useMemo(() => {
		const sortOption = statOptions.find((option) => option.key === sort.key) ?? fallbackOption(sort.key)
		return [...players].sort((left, right) => comparePlayers(left, right, sortOption, sort.direction))
	}, [players, sort, statOptions])
	const visiblePlayers = sortedPlayers.slice(0, visiblePlayerCount)
	const leaderboardOptions = useMemo(() => statOptions
		.filter((option) => option.group !== 'profile' && option.key !== 'minecraft.lastPlayedAtUnixMs'), [statOptions])
	const leaderboardOption = leaderboardOptions.find((option) => option.key === leaderboardKey)
		?? leaderboardOptions[0]
	const podiumEntries = leaderboardOption ? players.map((player) => {
		const value = getSortValue(player, leaderboardOption)
		return {
			id: player.id,
			name: player.minecraftUsername,
			color: player.profile.color,
			pronouns: player.profile.pronouns,
			value: typeof value === 'number' ? value : 0,
			displayValue: formatColumnValue(player, leaderboardOption),
			skinUrl: player.stats.minecraftProfile?.skinUrl,
		}
	}) : []

	function updateColumn(index: number, key: string) {
		const previousKey = columnKeys[index]
		setColumnKeys((current) => current.map((value, currentIndex) => currentIndex === index ? key : value))
		setSort((current) => current.key === previousKey ? { ...current, key } : current)
	}

	function toggleSort(key: string) {
		setSort((current) => {
			if (current.key !== key) {
				return { key, direction: 'desc' }
			}

			return { key, direction: current.direction === 'desc' ? 'asc' : 'desc' }
		})
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

	if (selectedPlayer) {
		return (
			<div className="playersPanel">
				{message && <p className="dailyMessage">{message}</p>}
				{error && <p className="authError">{error}</p>}
				<PlayerProfilePanel
					player={selectedPlayer}
					statOptions={statOptions}
					onBack={() => onSelectPlayer(null, true)}
					onSaved={() => void handleSaved()}
					onError={setError}
				/>
			</div>
		)
	}

	return (
		<div className="playersPanel">
			<div className="playersTop">
				<h3>Players</h3>
				{currentPlayer && (
					<button type="button" onClick={() => onSelectPlayer(currentPlayer.minecraftUsername)}>
						View / edit own profile
					</button>
				)}
			</div>

			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}
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

			<div className="playersTableWrap">
				<table className="playersTable">
					<thead>
						<tr>
							<th scope="col">Player</th>
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
											aria-label={`Sort by ${column.label}`}
											onClick={() => toggleSort(column.key)}
										>
											{sort.key === column.key ? (sort.direction === 'desc' ? 'v' : '^') : '-'}
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
								onClick={() => onSelectPlayer(player.minecraftUsername)}
							>
								<td>
									<PlayerCell player={player} />
								</td>
								{selectedColumns.map((column, index) => (
									<td key={`${player.id}:${column.key}:${index}`}>
										{column.key === 'profile.playerName'
											? <PlayerName name={player.minecraftUsername} color={player.profile.color} />
											: formatColumnValue(player, column)}
									</td>
								))}
							</tr>
						))}
					</tbody>
				</table>
			</div>
			{visiblePlayerCount < sortedPlayers.length && (
				<button type="button" className="loadMoreButton" onClick={() => setVisiblePlayerCount((current) => current + PAGE_SIZE)}>
					Load more
				</button>
			)}
		</div>
	)
}

function StatOptionGroups({ options }: { options: StatOption[] }) {
	const grouped = groupStatOptions(options)

	return (
		<>
			{grouped.map((group) => (
				<optgroup key={group.key} label={group.label}>
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
	onBack,
	onSaved,
	onError,
}: {
	player: PlayerSummary
	statOptions: StatOption[]
	onBack: () => void
	onSaved: () => void
	onError: (message: string) => void
}) {
	const [editing, setEditing] = useState(false)
	const [previewColor, setPreviewColor] = useState(player.profile.color)

	return (
		<section className="playerProfilePanel">
			<div className="playerProfileNav">
				<button type="button" onClick={onBack}>Back</button>
			</div>
			<div className="playerProfileTop">
				<PlayerHead player={player} size="large" />
				<div className="playerProfileIdentity">
					<h4><PlayerName name={player.minecraftUsername} color={previewColor} /></h4>
					<ProfileFacts player={player} />
				</div>
				{player.isCurrentUser && (
					<button type="button" onClick={() => {
						setPreviewColor(player.profile.color)
						setEditing((current) => !current)
					}}>
						{editing ? 'Cancel' : 'Edit profile'}
					</button>
				)}
			</div>

			{editing && player.isCurrentUser ? (
				<PlayerProfileForm
					player={player}
					onCancel={() => {
						setPreviewColor(player.profile.color)
						setEditing(false)
					}}
					onColorChange={setPreviewColor}
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
			<MiniFishCompendium userId={player.id} />
		</section>
	)
}

function ProfileFacts({ player }: { player: PlayerSummary }) {
	const profile = player.profile
	const facts = [
		player.isExternal ? { label: 'MMU affiliation', value: 'External player (not at MMU)' } : null,
		player.isExternal ? {
			label: 'Responsible player',
			value: player.responsibleMinecraftUsername && player.responsiblePlayerColor
				? <PlayerName name={player.responsibleMinecraftUsername} color={player.responsiblePlayerColor} />
				: 'Unknown player',
		} : null,
		{ label: 'Society member', value: player.isMember ? 'Yes' : 'No' },
		{ label: 'Committee', value: player.isCommittee ? 'Yes' : 'No' },
		profile.preferredName ? { label: 'Nickname', value: profile.preferredName } : null,
		profile.pronouns ? { label: 'Pronouns', value: profile.pronouns } : null,
		profile.courseYear ? { label: 'Course / Year', value: profile.courseYear } : null,
		profile.discordUsername ? { label: 'Discord username', value: profile.discordUsername } : null,
		hasBase(profile) ? { label: 'Base location', value: formatBase(profile.base) } : null,
	].filter((fact): fact is { label: string; value: string | ReactElement } => Boolean(fact))

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
	onColorChange,
}: {
	player: PlayerSummary
	onCancel: () => void
	onSaved: () => void
	onError: (message: string) => void
	onColorChange: (color: string) => void
}) {
	const [preferredName, setPreferredName] = useState(player.profile.preferredName)
	const [pronouns, setPronouns] = useState(player.profile.pronouns)
	const [courseYear, setCourseYear] = useState(player.profile.courseYear)
	const [discordUsername, setDiscordUsername] = useState(player.profile.discordUsername)
	const [baseX, setBaseX] = useState(player.profile.base.x?.toString() ?? '')
	const [baseY, setBaseY] = useState(player.profile.base.y?.toString() ?? '')
	const [baseZ, setBaseZ] = useState(player.profile.base.z?.toString() ?? '')
	const [bio, setBio] = useState(player.profile.bio)
	const [color, setColor] = useState<string | null>(player.profile.customColor)
	const [saving, setSaving] = useState(false)
	const displayedColor = color ?? player.profile.defaultColor

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
					pronouns,
					courseYear,
					discordUsername,
					baseX: baseX === '' ? null : Number(baseX),
					baseY: baseY === '' ? null : Number(baseY),
					baseZ: baseZ === '' ? null : Number(baseZ),
					bio,
					color,
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
			<p className="playerProfileHint">
				All of this is optional, but it can help people know who you are, how to reach out, and how to find you.
			</p>
			<label>
				<span>Nickname</span>
				<input value={preferredName} onChange={(event) => setPreferredName(event.target.value)} maxLength={PROFILE_TEXT_LIMITS.preferredName} />
			</label>
			<label>
				<span>Pronouns</span>
				<input value={pronouns} onChange={(event) => setPronouns(event.target.value)} maxLength={PROFILE_TEXT_LIMITS.pronouns} />
			</label>
			<label>
				<span>Course / Year</span>
				<input value={courseYear} onChange={(event) => setCourseYear(event.target.value)} maxLength={PROFILE_TEXT_LIMITS.courseYear} />
			</label>
			<label>
				<span>Discord username</span>
				<input value={discordUsername} onChange={(event) => setDiscordUsername(event.target.value)} maxLength={PROFILE_TEXT_LIMITS.discordUsername} />
			</label>
			<div className="playerBaseInputs">
				<span>Base location (XYZ)</span>
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
				<textarea value={bio} onChange={(event) => setBio(event.target.value)} maxLength={PROFILE_TEXT_LIMITS.bio} rows={4} />
			</label>
			<div className="playerColorField">
				<label>
					<span>Player color</span>
					<input type="color" value={displayedColor} onChange={(event) => {
						setColor(event.target.value)
						onColorChange(event.target.value)
					}} />
				</label>
				<button type="button" disabled={saving || color === null} onClick={() => {
					setColor(null)
					onColorChange(player.profile.defaultColor)
				}}>Reset color</button>
			</div>
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

function PlayerCell({ player }: { player: PlayerSummary }) {
	return (
		<div className="playerCell">
			<PlayerHead player={player} size="small" />
			<span className="playerCellName">
				<PlayerName name={player.minecraftUsername} color={player.profile.color} />
				{player.profile.pronouns && (
					<span className="playerCellPronouns"> ({player.profile.pronouns})</span>
				)}
			</span>
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
	if (option.key === 'profile.isMember') return player.isMember ? 'Yes' : 'No'
	if (option.key === 'profile.isCommittee') return player.isCommittee ? 'Yes' : 'No'
	if (option.key === 'profile.preferredName') return player.profile.preferredName || '-'
	if (option.key === 'profile.pronouns') return player.profile.pronouns || '-'
	if (option.key === 'profile.courseYear') return player.profile.courseYear || '-'
	if (option.key === 'profile.discordUsername') return player.profile.discordUsername || '-'
	if (option.key === 'profile.base') return hasBase(player.profile) ? formatBase(player.profile.base) : '-'
	if (option.key === 'money.earnedDabloons') return formatNumber(player.stats.money.earnedDabloons)
	if (option.key.startsWith('fishing.')) return formatNumber(player.fishing[option.key.slice(8)] ?? 0)
	if (option.key === 'minecraft.lastPlayedAtUnixMs') return formatTimestamp(player.stats.minecraft.lastPlayedAtUnixMs)

	const stat = player.stats.minecraft.stats[option.key]
	return stat ? formatMinecraftStatValue(stat) : '0'
}

function comparePlayers(
	left: PlayerSummary,
	right: PlayerSummary,
	option: StatOption,
	direction: SortDirection,
) {
	const ordered = compareSortValues(getSortValue(left, option), getSortValue(right, option), direction)
	return ordered || left.minecraftUsername.localeCompare(right.minecraftUsername, 'en')
}

function getSortValue(player: PlayerSummary, option: StatOption): number | string | null {
	if (option.key === 'profile.playerName') return player.minecraftUsername
	if (option.key === 'profile.isMember') return player.isMember ? 1 : 0
	if (option.key === 'profile.isCommittee') return player.isCommittee ? 1 : 0
	if (option.key === 'profile.preferredName') return player.profile.preferredName || player.minecraftUsername
	if (option.key === 'profile.pronouns') return player.profile.pronouns || null
	if (option.key === 'profile.courseYear') return player.profile.courseYear || null
	if (option.key === 'profile.discordUsername') return player.profile.discordUsername || null
	if (option.key === 'profile.base') return hasBase(player.profile) ? formatBase(player.profile.base) : null
	if (option.key === 'money.earnedDabloons') return player.stats.money.earnedDabloons
	if (option.key.startsWith('fishing.')) return player.fishing[option.key.slice(8)] ?? 0
	if (option.key === 'minecraft.lastPlayedAtUnixMs') return player.stats.minecraft.lastPlayedAtUnixMs

	return player.stats.minecraft.stats[option.key]?.value ?? 0
}

function compareSortValues(left: number | string | null, right: number | string | null, direction: SortDirection) {
	const leftMissing = left === null || left === ''
	const rightMissing = right === null || right === ''

	if (leftMissing && rightMissing) return 0
	if (leftMissing) return 1
	if (rightMissing) return -1

	let valueOrder: number
	if (typeof left === 'number' && typeof right === 'number') {
		valueOrder = left - right
	} else {
		valueOrder = String(left).localeCompare(String(right), 'en', {
			numeric: true,
			sensitivity: 'base',
		})
	}

	return direction === 'desc' ? -valueOrder : valueOrder
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
	const groups = [
		{
			key: 'profile',
			label: 'Profile',
			options: options.filter((option) => option.group === 'profile'),
		},
		{
			key: 'money',
			label: 'Dabloons',
			options: options.filter((option) => option.group === 'money'),
		},
		{
			key: 'fishing',
			label: 'Fishing Compendium',
			options: options.filter((option) => option.group === 'fishing'),
		},
		{
			key: 'minecraft-session',
			label: 'Minecraft - Session',
			options: options.filter((option) => option.group === 'minecraft' && option.category === 'session'),
		},
		{
			key: 'minecraft-advancement',
			label: 'Minecraft - Advancements',
			options: options.filter((option) => option.group === 'minecraft' && option.category === 'advancement'),
		},
		{
			key: 'minecraft-custom',
			label: 'Minecraft - General Stats',
			options: options.filter((option) => option.group === 'minecraft' && option.category === 'custom'),
		},
		{
			key: 'minecraft-killed',
			label: 'Minecraft - Mobs Killed',
			options: options.filter((option) => option.group === 'minecraft' && option.category === 'killed'),
		},
		{
			key: 'minecraft-killed-by',
			label: 'Minecraft - Deaths by Mob',
			options: options.filter((option) => option.group === 'minecraft' && option.category === 'killed_by'),
		},
	]

	return groups
		.map((group) => ({
			...group,
			options: [...group.options].sort((left, right) => left.label.localeCompare(right.label, 'en')),
		}))
		.filter((group) => group.options.length > 0)
}

function groupMinecraftStats(stats: MinecraftStatValue[]) {
	const visible = stats
		.filter((stat) => stat.value > 0)
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
	if (category === 'killed') return 'Killed mob'
	if (category === 'killed_by') return 'Killed by mob'
	return category
		.split(/[_-]+/)
		.map((part) => part.charAt(0).toUpperCase() + part.slice(1))
		.join(' ')
}

function fallbackOption(key: string): StatOption {
	return {
		key,
		label: key,
		group: key.startsWith('money.') ? 'money' : key.startsWith('profile.') ? 'profile' : key.startsWith('fishing.') ? 'fishing' : 'minecraft',
	}
}
