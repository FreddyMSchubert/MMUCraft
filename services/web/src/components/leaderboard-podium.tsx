'use client'

import Link from 'next/link'
import { PlayerName } from '@/components/player-name'

export interface PodiumEntry {
	id: number
	name: string
	color: string
	pronouns: string
	value: number
	displayValue: string
	avatarUrl?: string | null
	skinUrl?: string | null
}

export interface PodiumOption {
	key: string
	label: string
}

export function LeaderboardPodium({ entries, label, options, selectedKey, onChange, onSelectPlayer, compact = false }: {
	entries: PodiumEntry[]
	label: string
	options?: PodiumOption[]
	selectedKey?: string
	onChange?: (key: string) => void
	onSelectPlayer: (playerName: string) => void
	compact?: boolean
}) {
	const ranked = [...entries]
		.sort((left, right) => right.value - left.value || left.name.localeCompare(right.name, 'en'))
		.slice(0, 3)
		.map((entry, index) => ({ ...entry, rank: index + 1 }))
	const podiumOrder = [2, 1, 3].flatMap((rank) => ranked.filter((entry) => entry.rank === rank))

	return (
		<section className={`leaderboardPodium${compact ? ' compact' : ''}`}>
			<div className="podiumPlaces">
				{podiumOrder.map((entry) => (
					<Link
						key={entry.id}
						className={`podiumPlace podiumPlace-${entry.rank}`}
						href={`/play/players/${encodeURIComponent(entry.name)}`}
						onNavigate={(event) => {
							event.preventDefault()
							onSelectPlayer(entry.name)
						}}
					>
						<div className="podiumIdentity">
							<strong><PlayerName name={entry.name} color={entry.color} /></strong>
							{entry.pronouns && <span> ({entry.pronouns})</span>}
						</div>
						<PodiumHead entry={entry} />
						<div className="podiumStep">
							<strong>{entry.rank}</strong>
							<span>{entry.displayValue}</span>
						</div>
					</Link>
				))}
			</div>
			{options && selectedKey && onChange ? (
				<select className="podiumMetric" aria-label="Leaderboard comparison" value={selectedKey} onChange={(event) => onChange(event.target.value)}>
					{options.map((option) => <option key={option.key} value={option.key}>{option.label}</option>)}
				</select>
			) : <p className="podiumMetric">{label}</p>}
		</section>
	)
}

function PodiumHead({ entry }: { entry: PodiumEntry }) {
	const label = `${entry.name} head`
	if (entry.avatarUrl) {
		return <img className="podiumHeadImage" src={entry.avatarUrl} alt={label} />
	}
	if (entry.skinUrl) {
		return (
			<span className="playerHead playerHead-large" role="img" aria-label={label}>
				<span className="playerHeadLayer playerHeadFace" style={{ backgroundImage: `url("${entry.skinUrl}")` }} />
				<span className="playerHeadLayer playerHeadHat" style={{ backgroundImage: `url("${entry.skinUrl}")` }} />
			</span>
		)
	}
	return <span className="playerHead playerHead-large playerHeadFallback" role="img" aria-label={label}>{entry.name.charAt(0)}</span>
}
