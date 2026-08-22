'use client'

import Link from 'next/link'
import { PlayerName } from '@/components/player-name'

export interface PodiumEntry {
	id: number
	name: string
	color: string
	pronouns: string
	value: number | string | null
	displayValue: string
	avatarUrl?: string | null
}

export interface PodiumOption {
	key: string
	label: string
}

export interface PodiumOptionGroup {
	key: string
	label: string
	options: PodiumOption[]
}

export function LeaderboardPodium({ entries, label, optionGroups, selectedKey, onChange, onSelectPlayer, compact = false }: {
	entries: PodiumEntry[]
	label: string
	optionGroups?: PodiumOptionGroup[]
	selectedKey?: string
	onChange?: (key: string) => void
	onSelectPlayer: (playerName: string) => void
	compact?: boolean
}) {
	const ranked = entries
		.filter((entry) => Number.isFinite(Number(entry.value)) && Number(entry.value) > 0)
		.sort((left, right) => Number(right.value) - Number(left.value) || left.name.localeCompare(right.name, 'en'))
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
			{optionGroups && selectedKey && onChange ? (
				<select className="podiumMetric" aria-label="Leaderboard comparison" value={selectedKey} onChange={(event) => onChange(event.target.value)}>
					{optionGroups.map((group) => (
						<optgroup key={group.key} label={group.label}>
							{group.options.map((option) => <option key={option.key} value={option.key}>{option.label}</option>)}
						</optgroup>
					))}
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
	return <span className="playerHead playerHead-large playerHeadFallback" role="img" aria-label={label}>{entry.name.charAt(0)}</span>
}
