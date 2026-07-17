'use client'

import { Fireworks } from 'fireworks-js'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { LeaderboardPodium } from '@/components/leaderboard-podium'

type Rarity = 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary' | 'mythical'
type TagGroup = 'climate' | 'water' | 'time' | 'height' | 'weather' | 'moon'
type FishSort = 'rarity' | 'location'

interface PlayerOption {
	id: number
	minecraftUsername: string
	pronouns: string
	avatarUrl: string
	caughtTotal: number
}

interface CatchPoint {
	lengthCm: number
	caughtAtUnixMs: number
}

interface FishCatch {
	first: CatchPoint
	smallest: CatchPoint
	largest: CatchPoint
}

interface ServerRecord extends CatchPoint {
	player: PlayerOption
}

interface CompendiumFish {
	id: string
	title: string
	rarity: Rarity
	tags: string[]
	facts: string[]
	iconUrl: string
	catch: FishCatch | null
	serverLargest: ServerRecord | null
	serverSmallest: ServerRecord | null
}

interface CompendiumResponse {
	currentUserId: number
	selectedUserId: number
	players: PlayerOption[]
	fish: CompendiumFish[]
}

interface CatchEvent {
	type: 'catch'
	rarity: Rarity
}

const RARITIES: Array<{ id: Rarity; label: string; color: string; hue: number }> = [
	{ id: 'common', label: 'Common', color: '#ffffff', hue: 0 },
	{ id: 'uncommon', label: 'Uncommon', color: '#ffff55', hue: 60 },
	{ id: 'rare', label: 'Rare', color: '#55ffff', hue: 180 },
	{ id: 'epic', label: 'Epic', color: '#ff55ff', hue: 300 },
	{ id: 'legendary', label: 'Legendary', color: '#55ff55', hue: 120 },
	{ id: 'mythical', label: 'Mythical', color: '#ff5f00', hue: 22 },
]

const TAGS: Record<string, { emoji: string; label: string; group: TagGroup; phrase: string }> = {
	warm: { emoji: '🔥', label: 'Warm', group: 'climate', phrase: 'hot Overworld biomes' },
	cold: { emoji: '❄️', label: 'Cold', group: 'climate', phrase: 'cold Overworld biomes' },
	river: { emoji: '🌉', label: 'River', group: 'water', phrase: 'rivers' },
	ocean: { emoji: '🏖️', label: 'Ocean', group: 'water', phrase: 'oceans' },
	day: { emoji: '☀️', label: 'Day', group: 'time', phrase: 'daytime' },
	night: { emoji: '🌙', label: 'Night', group: 'time', phrase: 'nighttime' },
	deep: { emoji: '⬇️', label: 'Deep', group: 'height', phrase: 'below Y 60' },
	high: { emoji: '⬆️', label: 'High', group: 'height', phrase: 'above Y 100' },
	rainy: { emoji: '🌧️', label: 'Rainy', group: 'weather', phrase: 'rain' },
	thunderstorm: { emoji: '⛈️', label: 'Thunderstorm', group: 'weather', phrase: 'thunderstorms' },
	snowy: { emoji: '🌨️', label: 'Snowy', group: 'weather', phrase: 'snowfall' },
	waxing: { emoji: '🌒', label: 'Waxing moon', group: 'moon', phrase: 'waxing moon phases' },
	waning: { emoji: '🌘', label: 'Waning moon', group: 'moon', phrase: 'waning moon phases' },
	fullmoon: { emoji: '🌕', label: 'Full moon', group: 'moon', phrase: 'a full moon' },
	newmoon: { emoji: '🌑', label: 'New moon', group: 'moon', phrase: 'a new moon' },
}
const GROUPS: TagGroup[] = ['climate', 'water', 'time', 'height', 'weather', 'moon']
const GROUP_LABELS: Record<TagGroup, string> = {
	climate: 'Temperature',
	water: 'Water type',
	time: 'Daytime',
	height: 'Height',
	weather: 'Weather',
	moon: 'Moon phase',
}
const RARITY_RANK = new Map(RARITIES.map((rarity, index) => [rarity.id, index]))

export function FishingTab() {
	const [data, setData] = useState<CompendiumResponse | null>(null)
	const [error, setError] = useState('')
	const [rarityFilters, setRarityFilters] = useState<Set<Rarity>>(new Set())
	const [tagFilters, setTagFilters] = useState<Set<string>>(new Set())
	const [sort, setSort] = useState<FishSort>('rarity')
	const fireworksHost = useRef<HTMLDivElement>(null)
	const fireworks = useRef<Fireworks | null>(null)
	const stopTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

	const load = useCallback(async () => {
		const response = await fetch('/api/fishing/compendium', { cache: 'no-store' })
		const body = await response.json().catch(() => null)
		if (!response.ok) throw new Error(body?.message ?? 'Failed to load the fish compendium')
		return body as CompendiumResponse
	}, [])

	useEffect(() => {
		let cancelled = false
		async function loadInitial() {
			try {
				const next = await load()
				if (!cancelled) setData(next)
			} catch (caught) {
				if (!cancelled) setError(errorMessage(caught))
			}
		}
		void loadInitial()
		return () => { cancelled = true }
	}, [load])

	useEffect(() => {
		const host = fireworksHost.current
		if (!host) return
		fireworks.current = new Fireworks(host, {
			autoresize: true,
			particles: 90,
			explosion: 6,
			intensity: 8,
			delay: { min: 100_000, max: 100_000 },
			sound: { enabled: false },
		})
		return () => {
			fireworks.current?.stop(true)
			fireworks.current = null
		}
	}, [])

	useEffect(() => {
		const source = new EventSource('/api/fishing/events')
		source.onmessage = (message) => {
			const event = JSON.parse(message.data) as { type?: string } & Partial<CatchEvent>
			if (event.type !== 'catch') return
			void load()
				.then((next) => setData(next))
				.catch((caught) => setError(errorMessage(caught)))
			launchFireworks(fireworks.current, event.rarity ?? 'common', stopTimer)
		}
		return () => source.close()
	}, [load])

	const visibleFish = useMemo(() => sortAndFilterFish(data?.fish ?? [], rarityFilters, tagFilters, sort), [data?.fish, rarityFilters, tagFilters, sort])

	if (!data) return <p className={error ? 'authError' : ''}>{error || 'Loading fish compendium...'}</p>
	const player = data.players.find((candidate) => candidate.id === data.selectedUserId)
	const caughtTotal = data.fish.filter((fish) => fish.catch).length
	const podiumEntries = data.players.map((candidate) => ({
		id: candidate.id,
		name: candidate.minecraftUsername,
		pronouns: candidate.pronouns,
		value: candidate.caughtTotal,
		displayValue: new Intl.NumberFormat().format(candidate.caughtTotal),
		avatarUrl: candidate.avatarUrl,
	}))

	return (
		<section className="fishCompendium">
			<div ref={fireworksHost} className="fishFireworks" aria-hidden="true" />
			<div className="fishTop">
				<div>
					<h3>Fish Compendium</h3>
					<p>First catches and personal size records, updated live.</p>
				</div>
				<label className="fishSort">
					Sort
					<select value={sort} onChange={(event) => setSort(event.target.value as FishSort)}>
						<option value="rarity">Rarity first</option>
						<option value="location">Location first</option>
					</select>
				</label>
			</div>

			<div className="fishOverview">
				<CompendiumStats fish={data.fish} caughtTotal={caughtTotal} />
				<LeaderboardPodium entries={podiumEntries} label="Fish Caught" compact />
			</div>
			<CompendiumGuide
				rarityFilters={rarityFilters}
				tagFilters={tagFilters}
				onToggleRarity={(rarity) => setRarityFilters((current) => toggled(current, rarity))}
				onToggleTag={(tag) => setTagFilters((current) => toggled(current, tag))}
				onReset={() => { setRarityFilters(new Set()); setTagFilters(new Set()) }}
			/>
			{error && <p className="authError">{error}</p>}
			{visibleFish.length
				? <FishGrid fish={visibleFish} compact={false} player={player} />
				: <p>No fish match those filters.</p>}
		</section>
	)
}

export function MiniFishCompendium({ userId }: { userId: number }) {
	const [data, setData] = useState<CompendiumResponse | null>(null)
	const [error, setError] = useState('')
	const load = useCallback(async () => {
		const response = await fetch(`/api/fishing/compendium?userId=${userId}`, { cache: 'no-store' })
		const body = await response.json().catch(() => null)
		if (!response.ok) throw new Error(body?.message ?? 'Failed to load fish')
		return body as CompendiumResponse
	}, [userId])

	useEffect(() => {
		let cancelled = false
		async function loadInitial() {
			try {
				const next = await load()
				if (!cancelled) setData(next)
			} catch (caught) {
				if (!cancelled) setError(errorMessage(caught))
			}
		}
		void loadInitial()
		const source = new EventSource('/api/fishing/events')
		source.onmessage = (message) => {
			const event = JSON.parse(message.data) as { type?: string }
			if (event.type === 'catch') {
				void load()
					.then((next) => setData(next))
					.catch((caught) => setError(errorMessage(caught)))
			}
		}
		return () => {
			cancelled = true
			source.close()
		}
	}, [load])

	return (
		<details className="miniFishCompendium" open>
			<summary>
				Fish Compendium
				{data && <span>{data.fish.filter((fish) => fish.catch).length} / {data.fish.length} caught</span>}
			</summary>
			{error && <p className="authError">{error}</p>}
			{data ? <FishGrid fish={sortAndFilterFish(data.fish, new Set(), new Set(), 'rarity')} compact player={data.players.find((player) => player.id === data.selectedUserId)} /> : !error && <p>Loading fish...</p>}
		</details>
	)
}

function FishGrid({ fish, compact, player }: { fish: CompendiumFish[]; compact: boolean; player?: PlayerOption }) {
	return (
		<div className={compact ? 'fishGrid compact' : 'fishGrid'}>
			{fish.map((entry) => <FishCard key={entry.id} fish={entry} compact={compact} player={player} />)}
		</div>
	)
}

function FishCard({ fish, compact, player }: { fish: CompendiumFish; compact: boolean; player?: PlayerOption }) {
	const caught = fish.catch !== null
	const rarity = rarityInfo(fish.rarity)
	return (
		<article
			className={`fishCard rarity-${fish.rarity} ${caught ? 'caught' : 'unknown'}`}
			style={{ '--fish-rarity': rarity.color } as React.CSSProperties}
			tabIndex={0}
			onPointerEnter={positionTooltip}
			onFocus={positionTooltip}
		>
			<div className="fishTagLines">
				{tagLines(fish.tags).map((line, index) => <span key={index}>{line}</span>)}
			</div>
			{caught
				? <img src={fish.iconUrl} alt={fish.title} />
				: <span className="fishQuestion" aria-label="Uncaught fish">?</span>}
			<div className="fishCardName">{caught ? fish.title : '???'}</div>
			<div className="fishTooltip" role="tooltip">
				<strong>{caught ? fish.title : '???'}</strong>
				{compact
					? <CompactRarity rarity={rarity} />
					: <ConditionGuide fish={fish} rarity={rarity} />}
				{caught && !compact && (fish.facts?.length ?? 0) > 0 && (
					<div className="fishFacts">{fish.facts.map((fact) => <span key={fact}>{fact}</span>)}</div>
				)}
				{fish.catch && (
					<div className="fishPersonalRecords">
						<RecordLine label="First caught" point={fish.catch.first} player={player} />
						<RecordLine label="Personal largest" point={fish.catch.largest} player={player} />
						<RecordLine label="Personal smallest" point={fish.catch.smallest} player={player} />
					</div>
				)}
				{!fish.catch && <span>Not caught yet.</span>}
				{!compact && (
					<div className="fishServerRecords">
						<ServerRecordLine label="Server largest" record={fish.serverLargest} />
						<ServerRecordLine label="Server smallest" record={fish.serverSmallest} />
					</div>
				)}
			</div>
		</article>
	)
}

function CompendiumStats({ fish, caughtTotal }: { fish: CompendiumFish[]; caughtTotal: number }) {
	const percentage = fish.length ? Math.round(caughtTotal / fish.length * 100) : 0
	return (
		<section className="fishStats">
			<div className="fishStatsHeading">
				<div><span>Your collection</span><strong>{caughtTotal} / {fish.length} caught</strong></div>
				<b>{percentage}%</b>
			</div>
			<progress value={caughtTotal} max={fish.length || 1}>{percentage}%</progress>
			<div className="fishRarityBars">
				{RARITIES.map((rarity) => {
					const entries = fish.filter((entry) => entry.rarity === rarity.id)
					const caught = entries.filter((entry) => entry.catch).length
					const width = entries.length ? caught / entries.length * 100 : 0
					return (
						<div className="fishRarityProgress" key={rarity.id}>
							<b style={{ color: rarity.color }}>{rarity.label}</b>
							<span><i style={{ width: `${width}%`, background: rarity.color }} /></span>
							<small>{caught}/{entries.length}</small>
						</div>
					)
				})}
			</div>
		</section>
	)
}

function CompendiumGuide({ rarityFilters, tagFilters, onToggleRarity, onToggleTag, onReset }: {
	rarityFilters: Set<Rarity>
	tagFilters: Set<string>
	onToggleRarity: (rarity: Rarity) => void
	onToggleTag: (tag: string) => void
	onReset: () => void
}) {
	const filtered = rarityFilters.size > 0 || tagFilters.size > 0
	return (
		<div className="fishGuide">
			<button type="button" className={!filtered ? 'active fishAllFilter' : 'fishAllFilter'} aria-pressed={!filtered} onClick={onReset}>All</button>
			<div className="fishFilterGroups">
				<FilterGroup label="Rarity">
					{RARITIES.map((rarity) => (
						<button type="button" key={rarity.id} className={rarityFilters.has(rarity.id) ? 'active' : ''} aria-pressed={rarityFilters.has(rarity.id)} onClick={() => onToggleRarity(rarity.id)}>
							<i style={{ background: rarity.color }} />{rarity.label}
						</button>
					))}
				</FilterGroup>
				{GROUPS.map((group) => (
					<FilterGroup key={group} label={GROUP_LABELS[group]}>
						{Object.entries(TAGS).filter(([, info]) => info.group === group).map(([tag, info]) => (
							<button type="button" key={tag} className={tagFilters.has(tag) ? 'active' : ''} aria-pressed={tagFilters.has(tag)} onClick={() => onToggleTag(tag)}>
								<b>{info.emoji}</b>{info.label}
							</button>
						))}
					</FilterGroup>
				))}
			</div>
		</div>
	)
}

function FilterGroup({ label, children }: { label: string; children: React.ReactNode }) {
	return <div className="fishFilterGroup"><span>{label}</span><div>{children}</div></div>
}

function ConditionGuide({ fish, rarity }: { fish: CompendiumFish; rarity: (typeof RARITIES)[number] }) {
	return (
		<div className="fishConditions">
			<span>The {fish.catch ? fish.title : '???'} is a <b style={{ color: rarity.color }}>{rarity.label}</b> fish catchable {conditionSentence(fish.tags)}.</span>
			{isLuckRarity(rarity.id) && <span className="fishLuckWarning">Highly unlikely to be caught without a high Luck level.</span>}
		</div>
	)
}

function CompactRarity({ rarity }: { rarity: (typeof RARITIES)[number] }) {
	return (
		<div className="fishConditions">
			<span>Rarity: <b style={{ color: rarity.color }}>{rarity.label}</b></span>
			{isLuckRarity(rarity.id) && <span className="fishLuckWarning">Highly unlikely to be caught without a high Luck level.</span>}
		</div>
	)
}

function RecordLine({ label, point, player }: { label: string; point: CatchPoint; player?: PlayerOption }) {
	return (
		<span className="fishOwnRecord">
			{player && <img src={player.avatarUrl} alt="" />}
			<span><b>{label}:</b> {formatLength(point.lengthCm)} · {formatDate(point.caughtAtUnixMs)}</span>
		</span>
	)
}

function ServerRecordLine({ label, record }: { label: string; record: ServerRecord | null }) {
	if (!record) return <span><b>{label}:</b> No catches yet</span>
	return (
		<span className="fishServerRecord">
			<img src={record.player.avatarUrl} alt="" />
			<b>{label}:</b> {record.player.minecraftUsername} · {formatLength(record.lengthCm)}
		</span>
	)
}

function toggled<T>(values: Set<T>, value: T) {
	const next = new Set(values)
	if (next.has(value)) next.delete(value)
	else next.add(value)
	return next
}

function sortAndFilterFish(fish: CompendiumFish[], rarities: Set<Rarity>, tags: Set<string>, sort: FishSort) {
	return fish.filter((entry) => {
		if (rarities.size && !rarities.has(entry.rarity)) return false
		return GROUPS.every((group) => {
			const selected = [...tags].filter((tag) => TAGS[tag]?.group === group)
			return !selected.length || selected.some((tag) => entry.tags.includes(tag))
		})
	}).sort((left, right) => {
		const rarity = (RARITY_RANK.get(left.rarity) ?? 0) - (RARITY_RANK.get(right.rarity) ?? 0)
		const location = locationKey(left).localeCompare(locationKey(right), 'en')
		return (sort === 'rarity' ? rarity || location : location || rarity)
			|| left.title.localeCompare(right.title, 'en')
	})
}

function locationKey(fish: CompendiumFish) {
	return GROUPS.map((group) => Object.keys(TAGS)
		.filter((tag) => TAGS[tag]?.group === group && fish.tags.includes(tag))
		.join('+')).join('|')
}

function positionTooltip(event: React.SyntheticEvent<HTMLElement>) {
	const box = event.currentTarget.getBoundingClientRect()
	const halfTooltip = Math.min(event.currentTarget.closest('.compact') ? 270 : 330, window.innerWidth - 46) / 2
	event.currentTarget.classList.toggle('tooltipAbove', box.top + box.height / 2 > window.innerHeight / 2)
	event.currentTarget.classList.toggle('tooltipAlignLeft', box.left + box.width / 2 < halfTooltip + 23)
	event.currentTarget.classList.toggle('tooltipAlignRight', window.innerWidth - box.left - box.width / 2 < halfTooltip + 23)
}

function tagLines(tags: string[]) {
	return GROUPS.flatMap((group) => {
		const emojis = tags.flatMap((tag) => TAGS[tag]?.group === group ? [TAGS[tag]!.emoji] : [])
		return emojis.length ? [emojis.join('/')] : []
	})
}

function conditionSentence(tags: string[]) {
	const clauses = GROUPS.flatMap((group) => {
		const matches = tags.flatMap((tag) => TAGS[tag]?.group === group ? [TAGS[tag]!] : [])
		if (!matches.length) return []
		const prefix = group === 'climate' || group === 'water' || group === 'height' ? 'in' : 'during'
		const alternatives = matches.map((match) => `${match.emoji} ${prefix} ${match.phrase}`)
		return [alternatives.length > 1 ? `(${joinOr(alternatives)})` : alternatives[0]!]
	})
	return clauses.length ? clauses.join(' ') : 'anywhere and at any time'
}

function joinOr(values: string[]) {
	return values.length < 2 ? values[0]! : `${values.slice(0, -1).join(', ')} or ${values.at(-1)}`
}

function rarityInfo(rarity: Rarity) {
	return RARITIES.find((candidate) => candidate.id === rarity) ?? RARITIES[0]!
}

function isLuckRarity(rarity: Rarity) {
	return rarity === 'legendary' || rarity === 'mythical'
}

function launchFireworks(
	fireworks: Fireworks | null,
	rarity: Rarity,
	stopTimer: React.MutableRefObject<ReturnType<typeof setTimeout> | null>,
) {
	if (!fireworks) return
	const hue = rarityInfo(rarity).hue
	fireworks.updateOptions({
		hue: { min: hue - 4, max: hue + 4 },
		brightness: rarity === 'common' ? { min: 100, max: 100 } : { min: 50, max: 80 },
	})
	fireworks.start()
	fireworks.launch(10)
	if (stopTimer.current) clearTimeout(stopTimer.current)
	stopTimer.current = setTimeout(() => fireworks.stop(), 3_600)
}

function formatLength(lengthCm: number) {
	return `${lengthCm.toFixed(1)} cm`
}

function formatDate(unixMs: number) {
	return new Intl.DateTimeFormat('en-GB', {
		day: '2-digit',
		month: '2-digit',
		year: '2-digit',
	}).format(new Date(unixMs)).replaceAll('/', '.')
}

function errorMessage(error: unknown) {
	return error instanceof Error ? error.message : 'Failed to load fish compendium'
}
