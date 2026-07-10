'use client'

import { FormEvent, useCallback, useEffect, useState } from 'react'

interface AdminPlayer {
	id: number
	minecraftUsername: string
	discordUsername: string
	email: string
	isMember: boolean
	isCommittee: boolean
}

interface GiftCode {
	code: string
	amountDabloons: number
	redemptionMode: 'single' | 'per_user'
	expiresAtUnixMs: number | null
	createdAtUnixMs: number
	redemptionCount: number
}

const CODE_ADJECTIVES = [
	'ancient', 'blocky', 'creeping', 'enchanted', 'ender', 'golden', 'hidden', 'nether', 'pixelated', 'redstone', 'shimmering', 'square', 'verdant', 'cute', 'creepy', 'gorgeous', 'pretty', 'speedy', 'rough', 'angry', 'anxious', 'attacking'
]
const CODE_NOUNS = [
	'allay', 'axolotl', 'beacon', 'bee', 'creeper', 'elytra', 'fox', 'golem', 'minecart', 'pickaxe', 'shulker', 'slime', 'sniffer', 'warden', 'armadillo', 'bat', 'camel', 'cat', 'moobloom', 'ghast', 'ocelot', 'parrot', 'squid', 'salmon', 'horse', 'villager', 'turtle', 'dolphin', 'enderman', 'alpaka', 'panda', 'pufferfish', 'spider', 'blaze', 'creeper', 'pillager', 'vindicator', 'witch', 'silverfish', 'dragon', 'wither', 'cobblestone', 'pickaxe', 'sword', 'axe', 'spear', 'redstone', 'diamond', 'gold'
]
const CODE_JOINERS = ['-', '_', '.']

export function AdminTab({ isSuperAdmin }: { isSuperAdmin: boolean }) {
	const [activeSection, setActiveSection] = useState<'members' | 'gifts'>('members')
	const [players, setPlayers] = useState<AdminPlayer[]>([])
	const [giftCodes, setGiftCodes] = useState<GiftCode[]>([])
	const [suggestion, setSuggestion] = useState('enchanted-pickaxe')
	const [code, setCode] = useState('')
	const [amount, setAmount] = useState('')
	const [redemptionMode, setRedemptionMode] = useState<'single' | 'per_user'>('single')
	const [expiresAt, setExpiresAt] = useState('')
	const [showAllGiftCodes, setShowAllGiftCodes] = useState(false)
	const [busyPlayerId, setBusyPlayerId] = useState<number | null>(null)
	const [creating, setCreating] = useState(false)
	const [error, setError] = useState('')
	const [message, setMessage] = useState('')

	const load = useCallback(async () => {
		const [playersResponse, codesResponse] = await Promise.all([
			fetch('/api/admin/players', { cache: 'no-store' }),
			fetch('/api/admin/gift-codes', { cache: 'no-store' }),
		])
		const playersBody = await playersResponse.json().catch(() => null)
		const codesBody = await codesResponse.json().catch(() => null)

		if (!playersResponse.ok) throw new Error(apiMessage(playersBody, 'Failed to load the member list'))
		if (!codesResponse.ok) throw new Error(apiMessage(codesBody, 'Failed to load gift codes'))

		setPlayers(playersBody.players as AdminPlayer[])
		setGiftCodes(codesBody.giftCodes as GiftCode[])
	}, [])

	useEffect(() => {
		let cancelled = false
		const refreshSuggestion = () => {
			setSuggestion((current) => makeDifferentSuggestion(current))
		}
		const initialSuggestionTimer = window.setTimeout(refreshSuggestion, 0)
		const suggestionInterval = window.setInterval(refreshSuggestion, 5_000)

		async function loadInitial() {
			try {
				await load()
			} catch (caught) {
				if (!cancelled) setError(errorMessage(caught, 'Failed to load admin tools'))
			}
		}

		void loadInitial()
		return () => {
			cancelled = true
			window.clearTimeout(initialSuggestionTimer)
			window.clearInterval(suggestionInterval)
		}
	}, [load])

	async function setMembership(player: AdminPlayer, isMember: boolean) {
		const action = isMember ? 'mark as a society member' : 'remove society membership from'
		if (!window.confirm(`Are you sure you want to ${action} ${player.minecraftUsername}?`)) return

		setBusyPlayerId(player.id)
		setError('')
		setMessage('')
		try {
			const response = await fetch(`/api/admin/players/${player.id}/membership`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ isMember }),
			})
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to update membership'))

			setPlayers((current) => current.map((candidate) => (
				candidate.id === player.id ? { ...candidate, isMember } : candidate
			)))
			setMessage(`${player.minecraftUsername} is ${isMember ? 'now' : 'no longer'} marked as a member.`)
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to update membership'))
		} finally {
			setBusyPlayerId(null)
		}
	}

	async function setCommittee(player: AdminPlayer, isCommittee: boolean) {
		const action = isCommittee ? 'give committee access to' : 'remove committee access from'
		if (!window.confirm(`Are you sure you want to ${action} ${player.minecraftUsername}?`)) return

		setBusyPlayerId(player.id)
		setError('')
		setMessage('')
		try {
			const response = await fetch(`/api/admin/players/${player.id}/committee`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ isCommittee }),
			})
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to update committee access'))

			setPlayers((current) => current.map((candidate) => (
				candidate.id === player.id ? { ...candidate, isCommittee } : candidate
			)))
			setMessage(`${player.minecraftUsername} ${isCommittee ? 'now has' : 'no longer has'} committee access.`)
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to update committee access'))
		} finally {
			setBusyPlayerId(null)
		}
	}

	function createGiftCode(event: FormEvent) {
		event.preventDefault()
		setCreating(true)
		setError('')
		setMessage('')

		void (async () => {
			try {
				const response = await fetch('/api/admin/gift-codes', {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({
						code,
						amountDabloons: Number(amount),
						redemptionMode,
						expiresAtUnixMs: expiresAt ? new Date(expiresAt).getTime() : null,
					}),
				})
				const body = await response.json().catch(() => null)
				if (!response.ok) throw new Error(apiMessage(body, 'Failed to create gift code'))

				setCode('')
				setAmount('')
				setRedemptionMode('single')
				setExpiresAt('')
				setSuggestion(makeSuggestion())
				setMessage(`Created ${body.code} for ${body.amountDabloons} dabloons.`)
				await load()
			} catch (caught) {
				setError(errorMessage(caught, 'Failed to create gift code'))
			} finally {
				setCreating(false)
			}
		})()
	}

	return (
		<div className="adminPanel">
			<nav className="adminSubTabs" aria-label="Admin sections">
				<button
					type="button"
					className={activeSection === 'members' ? 'active' : ''}
					onClick={() => setActiveSection('members')}
				>
					Member list
				</button>
				<button
					type="button"
					className={activeSection === 'gifts' ? 'active' : ''}
					onClick={() => setActiveSection('gifts')}
				>
					Gift codes
				</button>
			</nav>

			{activeSection === 'members' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Member list</h3>
						<p>Private signup details are shown here only to help committee members verify society membership.</p>
					</div>

					<div className="adminWarnings adminWarnings-critical" role="alert">
						<strong>Verify before changing anything.</strong>
						<ul>
							<li>Match the Minecraft, Discord and email identities.</li>
							<li>Check the official membership or payment record; ask committee if unsure.</li>
						</ul>
					</div>

					<div className="adminTableWrap">
						<table className="adminTable">
							<thead>
								<tr>
								<th>Minecraft name</th>
								<th>Discord name</th>
								<th>Signup email</th>
								<th>Member</th>
								{isSuperAdmin && <th>Committee</th>}
								</tr>
							</thead>
							<tbody>
							{players.map((player) => (
								<tr key={player.id}>
									<td>
										{player.minecraftUsername}
										{player.isCommittee && <span className="committeeBadge">Committee</span>}
									</td>
									<td>{player.discordUsername || <span className="adminMissing">Not provided</span>}</td>
									<td>{player.email}</td>
									<td className="membershipCell">
										<input
											type="checkbox"
											aria-label={`Member status for ${player.minecraftUsername}`}
											checked={player.isMember}
											disabled={busyPlayerId === player.id}
											onChange={(event) => void setMembership(player, event.target.checked)}
										/>
									</td>
									{isSuperAdmin && (
										<td className="membershipCell">
											<input
												type="checkbox"
												aria-label={`Committee status for ${player.minecraftUsername}`}
												checked={player.isCommittee}
												disabled={busyPlayerId === player.id || player.minecraftUsername.toLowerCase() === 'merlinspace'}
												onChange={(event) => void setCommittee(player, event.target.checked)}
											/>
										</td>
									)}
								</tr>
							))}
							</tbody>
						</table>
					</div>
				</section>
			)}

			{activeSection === 'gifts' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Gift codes</h3>
						<p>Create a code that gives dabloons to eligible signed-in players who redeem it while online.</p>
					</div>

					<div className="adminWarnings" role="note" aria-label="Gift code warnings">
						<strong>Use gift codes carefully.</strong>
						<ul>
							<li>Use them for controlled promotions such as Freshers&apos; Fair.</li>
							<li>Ask whoever balances the economy before setting a value. Code names cannot be recreated.</li>
						</ul>
					</div>

					<form className="giftCodeForm" onSubmit={createGiftCode}>
						<label>
							Code
							<input
								value={code}
								onChange={(event) => setCode(event.target.value)}
								placeholder={suggestion}
								pattern="[A-Za-z0-9_.-]+"
								maxLength={64}
								required
							/>
						</label>
						<label>
							Dabloons
							<input
								value={amount}
								onChange={(event) => setAmount(event.target.value)}
								placeholder="20"
								type="number"
								min="1"
								max="2147483647"
								step="1"
								required
							/>
						</label>
						<label>
							Expires (optional)
							<input
								value={expiresAt}
								onChange={(event) => setExpiresAt(event.target.value)}
								type="datetime-local"
							/>
						</label>
						<fieldset className="giftCodeMode">
							<legend>Who can redeem it?</legend>
							<label>
								<input
									type="radio"
									name="redemption-mode"
									value="single"
									checked={redemptionMode === 'single'}
									onChange={() => setRedemptionMode('single')}
								/>
								<span>One redemption total</span>
							</label>
							<label>
								<input
									type="radio"
									name="redemption-mode"
									value="per_user"
									checked={redemptionMode === 'per_user'}
									onChange={() => setRedemptionMode('per_user')}
								/>
								<span>Once per player</span>
							</label>
						</fieldset>
						<button disabled={creating}>{creating ? 'Creating...' : 'Create gift code'}</button>
					</form>

					{giftCodes.length > 0 && (
						<div className="giftCodeHistory">
							<div className="giftCodeHistoryHeader">
								<h4>Active codes</h4>
								{giftCodes.length > 5 && (
									<button type="button" onClick={() => setShowAllGiftCodes((current) => !current)}>
										{showAllGiftCodes ? 'Show latest 5' : `Show all ${giftCodes.length}`}
									</button>
								)}
							</div>
							<ul>
								{(showAllGiftCodes ? giftCodes : giftCodes.slice(0, 5)).map((giftCode) => (
									<li key={giftCode.code}>
										<code>{giftCode.code}</code>
										<span>{giftCode.amountDabloons} dabloons</span>
										<span>
											{giftCode.redemptionMode === 'per_user' ? 'Once per player' : 'One total'}
											{giftCode.expiresAtUnixMs
								? ` - until ${formatExpiry(giftCode.expiresAtUnixMs)}`
								: ' - no expiry'}
										</span>
										<span className="giftCodeReady">
											{`${giftCode.redemptionCount} redemption${giftCode.redemptionCount === 1 ? '' : 's'}`}
										</span>
									</li>
								))}
							</ul>
						</div>
					)}
				</section>
			)}

			{message && <p className="adminMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}
		</div>
	)
}

function makeSuggestion() {
	const adjective = CODE_ADJECTIVES[Math.floor(Math.random() * CODE_ADJECTIVES.length)]
	const noun = CODE_NOUNS[Math.floor(Math.random() * CODE_NOUNS.length)]
	const joiner = CODE_JOINERS[Math.floor(Math.random() * CODE_JOINERS.length)]
	return `${adjective}${joiner}${noun}`
}

function makeDifferentSuggestion(current: string) {
	let suggestion = makeSuggestion()
	while (suggestion === current) suggestion = makeSuggestion()
	return suggestion
}

function formatExpiry(expiresAtUnixMs: number) {
	return new Intl.DateTimeFormat(undefined, {
		dateStyle: 'medium',
		timeStyle: 'short',
	}).format(new Date(expiresAtUnixMs))
}

function apiMessage(body: unknown, fallback: string) {
	if (!body || typeof body !== 'object' || !('message' in body)) return fallback
	const message = (body as { message?: unknown }).message
	return Array.isArray(message) ? message.join(', ') : typeof message === 'string' ? message : fallback
}

function errorMessage(error: unknown, fallback: string) {
	return error instanceof Error ? error.message : fallback
}
