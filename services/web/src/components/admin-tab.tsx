'use client'

import Link from 'next/link'
import { FormEvent, useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { PlayerName, playerNameStyle } from '@/components/player-name'
import type { Countdown } from '@/components/dynamic-countdowns'

interface AdminPlayer {
	id: number
	minecraftUsername: string
	color: string
	discordUsername: string
	email: string
	isMember: boolean
	isCommittee: boolean
	isExternal: boolean
}

interface GiftCode {
	code: string
	amountDabloons: number
	redemptionMode: 'single' | 'per_user'
	membersOnly: boolean
	expiresAtUnixMs: number | null
	createdAtUnixMs: number
	redemptionCount: number
}

interface AuthRequest {
	id: string
	kind: 'signup' | 'signin'
	email: string
	minecraftUsername: string | null
	color: string | null
	code: string | null
	deliveryStatus: 'sent' | 'manual'
	createdAtUnixMs: number
	expiresAtUnixMs: number
	completedAtUnixMs: number | null
	status: 'active' | 'verified' | 'expired'
}

interface WhitelistedEmail {
	email: string
	addedByMinecraftUsername: string
	addedByColor: string
	responsibleMinecraftUsername: string | null
	responsiblePlayerColor: string | null
	createdAtUnixMs: number
}

interface AdminClaim {
	id: string
	name: string
	dimension: string
	chunkX: number
	chunkZ: number
	minecraftUsername: string
	color: string
}

interface ActivePlayerBan {
	userId: number
	minecraftUsername: string
	color: string
	bannedByMinecraftUsername: string
	expiresAtUnixMs: number | null
	createdAtUnixMs: number
}

interface DiscordAdminCommand {
	command: string
	discordUsername: string
	createdAtUnixMs: number
}

const PAGE_SIZE = 42

const CODE_ADJECTIVES = [
	'ancient', 'blocky', 'creeping', 'enchanted', 'ender', 'golden', 'hidden', 'nether', 'pixelated', 'redstone', 'shimmering', 'square', 'verdant', 'cute', 'creepy', 'gorgeous', 'pretty', 'speedy', 'rough', 'angry', 'anxious', 'attacking'
]
const CODE_NOUNS = [
	'allay', 'axolotl', 'beacon', 'bee', 'creeper', 'elytra', 'fox', 'golem', 'minecart', 'pickaxe', 'shulker', 'slime', 'sniffer', 'warden', 'armadillo', 'bat', 'camel', 'cat', 'moobloom', 'ghast', 'ocelot', 'parrot', 'squid', 'salmon', 'horse', 'villager', 'turtle', 'dolphin', 'enderman', 'alpaka', 'panda', 'pufferfish', 'spider', 'blaze', 'creeper', 'pillager', 'vindicator', 'witch', 'silverfish', 'dragon', 'wither', 'cobblestone', 'pickaxe', 'sword', 'axe', 'spear', 'redstone', 'diamond', 'gold'
]
const CODE_JOINERS = ['-', '_', '.']

export function AdminTab({ isSuperAdmin, section }: { isSuperAdmin: boolean; section?: string }) {
	const activeSection = section === 'signins' || section === 'claims' || section === 'whitelist' || section === 'bans' || section === 'gifts' || section === 'countdowns' || section === 'discord-commands' ? section : 'members'
	const [players, setPlayers] = useState<AdminPlayer[]>([])
	const [countdowns, setCountdowns] = useState<Countdown[]>([])
	const [giftCodes, setGiftCodes] = useState<GiftCode[]>([])
	const [authRequests, setAuthRequests] = useState<AuthRequest[]>([])
	const [authRequestsHaveMore, setAuthRequestsHaveMore] = useState(false)
	const [claims, setClaims] = useState<AdminClaim[]>([])
	const [claimsHaveMore, setClaimsHaveMore] = useState(false)
	const [whitelistedEmails, setWhitelistedEmails] = useState<WhitelistedEmail[]>([])
	const [activePlayerBans, setActivePlayerBans] = useState<ActivePlayerBan[]>([])
	const [discordAdminCommands, setDiscordAdminCommands] = useState<DiscordAdminCommand[]>([])
	const [whitelistEmail, setWhitelistEmail] = useState('')
	const [responsibleUsername, setResponsibleUsername] = useState('')
	const [banPlayerId, setBanPlayerId] = useState('')
	const [banMode, setBanMode] = useState<'temporary' | 'permanent'>('temporary')
	const [timeoutEndsAt, setTimeoutEndsAt] = useState('')
	const [suggestion, setSuggestion] = useState('enchanted-pickaxe')
	const [code, setCode] = useState('')
	const [amount, setAmount] = useState('')
	const [redemptionMode, setRedemptionMode] = useState<'single' | 'per_user'>('single')
	const [membersOnly, setMembersOnly] = useState(false)
	const [expiresAt, setExpiresAt] = useState('')
	const [countdownHeading, setCountdownHeading] = useState('')
	const [countdownTarget, setCountdownTarget] = useState('')
	const [countdownDescription, setCountdownDescription] = useState('')
	const [countdownHeadingColor, setCountdownHeadingColor] = useState('#ffffff')
	const [countdownDescriptionColor, setCountdownDescriptionColor] = useState('#ffffff')
	const [countdownBackgroundColor, setCountdownBackgroundColor] = useState('#000000')
	const [countdownBackgroundAlpha, setCountdownBackgroundAlpha] = useState(78)
	const [countdownBackgroundImageUrl, setCountdownBackgroundImageUrl] = useState('')
	const [editingCountdownId, setEditingCountdownId] = useState<number | null>(null)
	const [showAllGiftCodes, setShowAllGiftCodes] = useState(false)
	const [busyPlayerId, setBusyPlayerId] = useState<number | null>(null)
	const [busyClaimId, setBusyClaimId] = useState<string | null>(null)
	const [busyCountdownId, setBusyCountdownId] = useState<number | null>(null)
	const [loadingMore, setLoadingMore] = useState<'signins' | 'claims' | null>(null)
	const [creating, setCreating] = useState(false)
	const [updatingWhitelist, setUpdatingWhitelist] = useState(false)
	const [updatingBan, setUpdatingBan] = useState(false)
	const [error, setError] = useState('')
	const [message, setMessage] = useState<ReactNode>('')

	const load = useCallback(async () => {
		const [playersResponse, codesResponse, countdownsResponse, signinsResponse, claimsResponse, whitelistResponse, bansResponse, commandsResponse] = await Promise.all([
			fetch('/api/admin/players', { cache: 'no-store' }),
			fetch('/api/admin/gift-codes', { cache: 'no-store' }),
			fetch('/api/admin/countdowns', { cache: 'no-store' }),
			fetch(`/api/admin/signins?limit=${PAGE_SIZE}`, { cache: 'no-store' }),
			fetch(`/api/admin/claims?limit=${PAGE_SIZE}`, { cache: 'no-store' }),
			fetch('/api/admin/email-whitelist', { cache: 'no-store' }),
			fetch('/api/admin/player-bans', { cache: 'no-store' }),
			fetch('/api/admin/discord-admin-commands', { cache: 'no-store' }),
		])
		const playersBody = await playersResponse.json().catch(() => null)
		const codesBody = await codesResponse.json().catch(() => null)
		const countdownsBody = await countdownsResponse.json().catch(() => null)
		const signinsBody = await signinsResponse.json().catch(() => null)
		const claimsBody = await claimsResponse.json().catch(() => null)
		const whitelistBody = await whitelistResponse.json().catch(() => null)
		const bansBody = await bansResponse.json().catch(() => null)
		const commandsBody = await commandsResponse.json().catch(() => null)

		if (!playersResponse.ok) throw new Error(apiMessage(playersBody, 'Failed to load the member list'))
		if (!codesResponse.ok) throw new Error(apiMessage(codesBody, 'Failed to load gift codes'))
		if (!countdownsResponse.ok) throw new Error(apiMessage(countdownsBody, 'Failed to load countdowns'))
		if (!signinsResponse.ok) throw new Error(apiMessage(signinsBody, 'Failed to load signins'))
		if (!claimsResponse.ok) throw new Error(apiMessage(claimsBody, 'Failed to load claims'))
		if (!whitelistResponse.ok) throw new Error(apiMessage(whitelistBody, 'Failed to load the email whitelist'))
		if (!bansResponse.ok) throw new Error(apiMessage(bansBody, 'Failed to load player bans'))
		if (!commandsResponse.ok) throw new Error(apiMessage(commandsBody, 'Failed to load Discord admin commands'))

		setPlayers(playersBody.players as AdminPlayer[])
		setGiftCodes(codesBody.giftCodes as GiftCode[])
		setCountdowns(countdownsBody.countdowns as Countdown[])
		setAuthRequests(signinsBody.requests as AuthRequest[])
		setAuthRequestsHaveMore(Boolean(signinsBody.hasMore))
		setClaims(claimsBody.claims as AdminClaim[])
		setClaimsHaveMore(Boolean(claimsBody.hasMore))
		setWhitelistedEmails(whitelistBody.entries as WhitelistedEmail[])
		setActivePlayerBans(bansBody.bans as ActivePlayerBan[])
		setDiscordAdminCommands(commandsBody.commands as DiscordAdminCommand[])
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
		if (!window.confirm(`Are you sure you want to ${action} this player?`)) return
		if (player.isExternal && !window.confirm(`This player is external. Are you sure you want to ${action} them?`)) return

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
			setMessage(<><PlayerName name={player.minecraftUsername} color={player.color} /> is {isMember ? 'now' : 'no longer'} marked as a member.</>)
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to update membership'))
		} finally {
			setBusyPlayerId(null)
		}
	}

	async function setCommittee(player: AdminPlayer, isCommittee: boolean) {
		const action = isCommittee ? 'give committee access to' : 'remove committee access from'
		if (!window.confirm(`Are you sure you want to ${action} this player?`)) return
		if (player.isExternal && !window.confirm(`This player is external. Are you sure you want to ${action} them?`)) return
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
			setMessage(<><PlayerName name={player.minecraftUsername} color={player.color} /> {isCommittee ? 'now has' : 'no longer has'} committee access.</>)
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
						membersOnly,
						expiresAtUnixMs: expiresAt ? new Date(expiresAt).getTime() : null,
					}),
				})
				const body = await response.json().catch(() => null)
				if (!response.ok) throw new Error(apiMessage(body, 'Failed to create gift code'))

				setCode('')
				setAmount('')
				setRedemptionMode('single')
				setMembersOnly(false)
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

	function addWhitelistedEmail(event: FormEvent) {
		event.preventDefault()
		const responsiblePlayer = players.find((player) => (
			player.minecraftUsername.localeCompare(responsibleUsername, 'en', { sensitivity: 'base' }) === 0
			&& !player.isExternal
		))
		if (!responsiblePlayer) {
			setError('Select a responsible user from the username list')
			return
		}
		if (!window.confirm(`Charge the selected player 100 dabloons to invite ${whitelistEmail}? They must be online.`)) return
		setUpdatingWhitelist(true)
		setError('')
		setMessage('')

		void (async () => {
			try {
				const response = await fetch('/api/admin/email-whitelist', {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ email: whitelistEmail, responsibleUserId: responsiblePlayer.id }),
				})
				const body = await response.json().catch(() => null)
				if (!response.ok) throw new Error(apiMessage(body, 'Failed to whitelist the email address'))

				setWhitelistEmail('')
				setResponsibleUsername('')
				setMessage(<>{body.email} can now sign up. <PlayerName name={responsiblePlayer.minecraftUsername} color={responsiblePlayer.color} /> paid {body.priceDabloons} dabloons and has {body.balanceDabloons} left.</>)
				await load()
			} catch (caught) {
				setError(errorMessage(caught, 'Failed to whitelist the email address'))
			} finally {
				setUpdatingWhitelist(false)
			}
		})()
	}

	async function removeWhitelistedEmail(email: string) {
		if (!window.confirm(`Remove ${email} from the signup whitelist?`)) return
		setUpdatingWhitelist(true)
		setError('')
		setMessage('')
		try {
			const response = await fetch(`/api/admin/email-whitelist/${encodeURIComponent(email)}`, { method: 'DELETE' })
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to remove the email address'))
			setWhitelistedEmails((current) => current.filter((entry) => entry.email !== email))
			setMessage(`${email} was removed from the signup whitelist.`)
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to remove the email address'))
		} finally {
			setUpdatingWhitelist(false)
		}
	}

	function applyPlayerBan(event: FormEvent) {
		event.preventDefault()
		const player = players.find((candidate) => candidate.id === Number(banPlayerId))
		if (!player) {
			setError('Select a player')
			return
		}
		const expiresAtUnixMs = banMode === 'temporary' ? new Date(timeoutEndsAt).getTime() : null
		if (expiresAtUnixMs !== null && (!Number.isFinite(expiresAtUnixMs) || expiresAtUnixMs <= Date.now())) {
			setError('Select a timeout date and time in the future')
			return
		}

		const restriction = banMode === 'permanent' ? 'permanently ban' : `put in timeout until ${formatDateTime(expiresAtUnixMs as number)}`
		if (!window.confirm(`Warning 1 of 3: ${player.minecraftUsername} will be signed out everywhere and unable to sign in. Continue?`)) return
		if (!window.confirm(`Warning 2 of 3: ${player.minecraftUsername} will be blacklisted from Minecraft. Check that you selected the correct player and any related external accounts. Continue?`)) return
		if (!window.confirm(`Warning 3 of 3: Apply this action and ${restriction} ${player.minecraftUsername}?`)) return

		setUpdatingBan(true)
		setError('')
		setMessage('')
		void (async () => {
			try {
				const response = await fetch('/api/admin/player-bans', {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ userId: player.id, expiresAtUnixMs }),
				})
				const body = await response.json().catch(() => null)
				if (!response.ok) throw new Error(apiMessage(body, 'Failed to apply the ban or timeout'))

				setBanPlayerId('')
				setTimeoutEndsAt('')
				setMessage(<><PlayerName name={player.minecraftUsername} color={player.color} /> was {banMode === 'permanent' ? 'permanently banned' : 'put in timeout'}.{body.minecraftSynchronized ? '' : ' Minecraft will synchronize when the player next attempts to join.'}</>)
				await load()
			} catch (caught) {
				setError(errorMessage(caught, 'Failed to apply the ban or timeout'))
			} finally {
				setUpdatingBan(false)
			}
		})()
	}

	async function removePlayerBan(ban: ActivePlayerBan) {
		if (!window.confirm(`Remove the ban or timeout for ${ban.minecraftUsername}?`)) return
		setUpdatingBan(true)
		setError('')
		setMessage('')
		try {
			const response = await fetch(`/api/admin/player-bans/${ban.userId}`, { method: 'DELETE' })
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to remove the ban or timeout'))
			setActivePlayerBans((current) => current.filter((candidate) => candidate.userId !== ban.userId))
			setMessage(<><PlayerName name={ban.minecraftUsername} color={ban.color} /> can sign in and join again.{body.minecraftSynchronized ? '' : ' Minecraft will synchronize when the player next attempts to join.'}</>)
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to remove the ban or timeout'))
		} finally {
			setUpdatingBan(false)
		}
	}

	async function loadMoreSignins() {
		setLoadingMore('signins')
		setError('')
		try {
			const response = await fetch(`/api/admin/signins?offset=${authRequests.length}&limit=${PAGE_SIZE}`, { cache: 'no-store' })
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to load more signins'))
			setAuthRequests((current) => [...current, ...(body.requests as AuthRequest[])])
			setAuthRequestsHaveMore(Boolean(body.hasMore))
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to load more signins'))
		} finally {
			setLoadingMore(null)
		}
	}

	async function loadMoreClaims() {
		setLoadingMore('claims')
		setError('')
		try {
			const response = await fetch(`/api/admin/claims?offset=${claims.length}&limit=${PAGE_SIZE}`, { cache: 'no-store' })
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to load more claims'))
			setClaims((current) => [...current, ...(body.claims as AdminClaim[])])
			setClaimsHaveMore(Boolean(body.hasMore))
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to load more claims'))
		} finally {
			setLoadingMore(null)
		}
	}

	async function removeClaim(claim: AdminClaim) {
		if (!window.confirm(`Delete ${claim.minecraftUsername}'s claim "${claim.name}" at ${claim.dimension} (${claim.chunkX}, ${claim.chunkZ})?`)) return
		setBusyClaimId(claim.id)
		setError('')
		setMessage('')
		try {
			const response = await fetch(`/api/admin/claims/${encodeURIComponent(claim.id)}`, { method: 'DELETE' })
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to delete the claim'))
			setClaims((current) => current.filter((candidate) => candidate.id !== claim.id))
			setMessage(<>Deleted <PlayerName name={claim.minecraftUsername} color={claim.color} />&apos;s claim &quot;{claim.name}&quot;.</>)
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to delete the claim'))
		} finally {
			setBusyClaimId(null)
		}
	}

	function saveCountdown(event: FormEvent) {
		event.preventDefault()
		setCreating(true)
		setError('')
		setMessage('')
		void (async () => {
			try {
				const editing = editingCountdownId !== null
				const response = await fetch(editingCountdownId === null ? '/api/admin/countdowns' : `/api/admin/countdowns/${editingCountdownId}`, {
					method: editingCountdownId === null ? 'POST' : 'PATCH',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({
						heading: countdownHeading,
						target: countdownTarget,
						description: countdownDescription,
						headingColor: countdownHeadingColor,
						descriptionColor: countdownDescriptionColor,
						backgroundColor: countdownBackgroundColor,
						backgroundAlpha: countdownBackgroundAlpha,
						backgroundImageUrl: countdownBackgroundImageUrl,
					}),
				})
				const body = await response.json().catch(() => null)
				if (!response.ok) throw new Error(apiMessage(body, `Failed to ${editing ? 'update' : 'create'} the countdown`))
				setMessage(`${editing ? 'Updated' : 'Created'} the “${body.heading}” countdown.`)
				resetCountdownForm()
				await load()
				window.dispatchEvent(new Event('countdowns-change'))
			} catch (caught) {
				setError(errorMessage(caught, `Failed to ${editingCountdownId === null ? 'create' : 'update'} the countdown`))
			} finally {
				setCreating(false)
			}
		})()
	}

	function editCountdown(countdown: Countdown) {
		setEditingCountdownId(countdown.id)
		setCountdownHeading(countdown.heading)
		setCountdownTarget(formatLondonInput(countdown.targetAtUnixMs))
		setCountdownDescription(countdown.description)
		setCountdownHeadingColor(countdown.headingColor)
		setCountdownDescriptionColor(countdown.descriptionColor)
		setCountdownBackgroundColor(countdown.backgroundColor)
		setCountdownBackgroundAlpha(countdown.backgroundAlpha)
		setCountdownBackgroundImageUrl(countdown.backgroundImageUrl ?? '')
		setError('')
		setMessage('')
	}

	function resetCountdownForm() {
		setEditingCountdownId(null)
		setCountdownHeading('')
		setCountdownTarget('')
		setCountdownDescription('')
		setCountdownHeadingColor('#ffffff')
		setCountdownDescriptionColor('#ffffff')
		setCountdownBackgroundColor('#000000')
		setCountdownBackgroundAlpha(78)
		setCountdownBackgroundImageUrl('')
	}

	async function moveCountdown(countdown: Countdown, direction: 'up' | 'down') {
		setBusyCountdownId(countdown.id)
		setError('')
		try {
			const response = await fetch(`/api/admin/countdowns/${countdown.id}/order`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ direction }),
			})
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to reorder the countdown'))
			setCountdowns(body.countdowns as Countdown[])
			window.dispatchEvent(new Event('countdowns-change'))
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to reorder the countdown'))
		} finally {
			setBusyCountdownId(null)
		}
	}

	async function removeCountdown(countdown: Countdown) {
		if (!window.confirm(`Delete the “${countdown.heading}” countdown?`)) return
		setBusyCountdownId(countdown.id)
		setError('')
		try {
			const response = await fetch(`/api/admin/countdowns/${countdown.id}`, { method: 'DELETE' })
			const body = await response.json().catch(() => null)
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to delete the countdown'))
			setCountdowns((current) => current.filter((candidate) => candidate.id !== countdown.id))
			if (editingCountdownId === countdown.id) resetCountdownForm()
			setMessage(`Deleted the “${countdown.heading}” countdown.`)
			window.dispatchEvent(new Event('countdowns-change'))
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to delete the countdown'))
		} finally {
			setBusyCountdownId(null)
		}
	}

	return (
		<div className="adminPanel">
			<nav className="adminSubTabs" aria-label="Admin sections">
				<Link
					className={activeSection === 'signins' ? 'active' : ''}
					href="/play/admin/signins"
				>
					Signins
				</Link>
				<Link
					className={activeSection === 'members' ? 'active' : ''}
					href="/play/admin/members"
				>
					Member list
				</Link>
				<Link
					className={activeSection === 'claims' ? 'active' : ''}
					href="/play/admin/claims"
				>
					Claims
				</Link>
				<Link
					className={activeSection === 'whitelist' ? 'active' : ''}
					href="/play/admin/whitelist"
				>
					Email whitelist
				</Link>
				<Link
					className={activeSection === 'gifts' ? 'active' : ''}
					href="/play/admin/gifts"
				>
					Gift codes
				</Link>
				<Link
					className={activeSection === 'countdowns' ? 'active' : ''}
					href="/play/admin/countdowns"
				>
					Countdowns
				</Link>
				<Link
					className={activeSection === 'discord-commands' ? 'active' : ''}
					href="/play/admin/discord-commands"
				>
					Discord commands
				</Link>
				<Link
					className={activeSection === 'bans' ? 'active' : ''}
					href="/play/admin/bans"
				>
					Ban / timeout
				</Link>
			</nav>

			{activeSection === 'countdowns' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Countdowns</h3>
						<p>Create up to four countdowns. Enter the date and time in British time.</p>
					</div>
					<form className="countdownForm" onSubmit={saveCountdown}>
						<label>
							Heading
							<input value={countdownHeading} onChange={(event) => setCountdownHeading(event.target.value)} maxLength={80} required />
						</label>
						<label>
							Date and time (UK)
							<input type="datetime-local" value={countdownTarget} onChange={(event) => setCountdownTarget(event.target.value)} required />
						</label>
						<label className="countdownAbstractInput">
							Abstract
							<textarea value={countdownDescription} onChange={(event) => setCountdownDescription(event.target.value)} maxLength={500} rows={3} required />
						</label>
						<label className="countdownImageInput">
							Background image URL (optional)
							<input type="url" value={countdownBackgroundImageUrl} onChange={(event) => setCountdownBackgroundImageUrl(event.target.value)} placeholder="https://example.com/event.jpg" pattern="https://.*" maxLength={2000} />
						</label>
						<fieldset className="countdownColorOptions">
							<legend>Colors</legend>
							<label>Heading <input type="color" value={countdownHeadingColor} onChange={(event) => setCountdownHeadingColor(event.target.value)} /><code>{countdownHeadingColor}</code></label>
							<label>Abstract <input type="color" value={countdownDescriptionColor} onChange={(event) => setCountdownDescriptionColor(event.target.value)} /><code>{countdownDescriptionColor}</code></label>
							<label>Background <input type="color" value={countdownBackgroundColor} onChange={(event) => setCountdownBackgroundColor(event.target.value)} /><code>{countdownBackgroundColor}</code></label>
							<label>Background opacity <input type="range" min="0" max="100" step="1" value={countdownBackgroundAlpha} onChange={(event) => setCountdownBackgroundAlpha(Number(event.target.value))} /><output>{countdownBackgroundAlpha}%</output></label>
						</fieldset>
						<div className="countdownFormActions">
							<button disabled={creating || (editingCountdownId === null && countdowns.length >= 4)}>{creating ? 'Saving...' : editingCountdownId === null ? countdowns.length >= 4 ? 'Maximum of 4 reached' : 'Create countdown' : 'Save changes'}</button>
							{editingCountdownId !== null && <button type="button" onClick={resetCountdownForm}>Cancel editing</button>}
						</div>
					</form>

					<div className="countdownAdminList">
						{countdowns.map((countdown, index) => <article key={countdown.id}>
							<div>
								<strong>{countdown.heading}</strong>
								<span>{formatLondonDateTime(countdown.targetAtUnixMs)}</span>
								{countdown.backgroundImageUrl && <span>Background image: {countdown.backgroundImageUrl}</span>}
								<p>{countdown.description}</p>
							</div>
							<div className="countdownAdminActions">
								<button type="button" disabled={busyCountdownId !== null} onClick={() => editCountdown(countdown)}>Edit</button>
								<button type="button" aria-label={`Move ${countdown.heading} up`} disabled={index === 0 || busyCountdownId !== null} onClick={() => void moveCountdown(countdown, 'up')}>↑</button>
								<button type="button" aria-label={`Move ${countdown.heading} down`} disabled={index === countdowns.length - 1 || busyCountdownId !== null} onClick={() => void moveCountdown(countdown, 'down')}>↓</button>
								<button type="button" disabled={busyCountdownId !== null} onClick={() => void removeCountdown(countdown)}>Delete</button>
							</div>
						</article>)}
						{countdowns.length === 0 && <p>No countdowns are active.</p>}
					</div>
				</section>
			)}

			{activeSection === 'discord-commands' && (
				<section className="adminSection">
					<div className="adminSectionHeader"><h3>Discord admin commands</h3><p>Commands sent to the Minecraft console through Discord.</p></div>
					<button type="button" onClick={() => void load()}>Refresh</button>
					<div className="adminTableWrap"><table className="adminTable"><thead><tr><th>Command</th><th>Discord user</th><th>Created</th></tr></thead><tbody>
						{discordAdminCommands.map((entry, index) => <tr key={`${entry.createdAtUnixMs}-${index}`}><td><code>{entry.command}</code></td><td>{entry.discordUsername}</td><td>{formatDateTime(entry.createdAtUnixMs)}</td></tr>)}
						{discordAdminCommands.length === 0 && <tr><td colSpan={3}>No Discord admin commands yet.</td></tr>}
					</tbody></table></div>
				</section>
			)}

			{activeSection === 'members' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Member list</h3>
						<p>Don&apos;t share this screen with people as it contains sensitive info about our members</p>
					</div>

					<div className="adminWarnings adminWarnings-critical" role="alert">
						<strong>Verify before changing anything.</strong>
						<ul>
							<li>Match the Minecraft, Discord and email identities.</li>
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
										<PlayerName name={player.minecraftUsername} color={player.color} />
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

			{activeSection === 'claims' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Claims</h3>
						<p>Review claimed chunks and delete claims that block or grief other players.</p>
					</div>
					<div className="adminTableWrap">
						<table className="adminTable">
							<thead>
								<tr><th>Player</th><th>Claim name</th><th>Dimension</th><th>Chunk coordinate</th><th></th></tr>
							</thead>
							<tbody>
								{claims.map((claim) => (
									<tr key={claim.id}>
										<td><PlayerName name={claim.minecraftUsername} color={claim.color} /></td>
										<td>{claim.name}</td>
										<td><code>{claim.dimension}</code></td>
										<td><code>({claim.chunkX}, {claim.chunkZ})</code></td>
										<td><button type="button" disabled={busyClaimId !== null} onClick={() => void removeClaim(claim)}>{busyClaimId === claim.id ? 'Deleting...' : 'Delete'}</button></td>
									</tr>
								))}
								{claims.length === 0 && <tr><td colSpan={5}>No chunks are claimed.</td></tr>}
							</tbody>
						</table>
					</div>
					{claimsHaveMore && (
						<button type="button" className="loadMoreButton" disabled={loadingMore !== null || busyClaimId !== null} onClick={() => void loadMoreClaims()}>
							{loadingMore === 'claims' ? 'Loading...' : 'Load more'}
						</button>
					)}
				</section>
			)}

			{activeSection === 'signins' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Signup and signin requests</h3>
						<p>Active codes are sensitive and disappear after use, expiry, or replacement.</p>
					</div>
					<button type="button" onClick={() => void load()}>Refresh</button>
					<div className="adminTableWrap">
						<table className="adminTable">
							<thead>
								<tr>
									<th>Type</th>
									<th>Account</th>
									<th>Requested</th>
									<th>Delivery</th>
									<th>Status</th>
									<th>Active code</th>
								</tr>
							</thead>
							<tbody>
								{authRequests.map((request) => (
									<tr key={request.id}>
										<td>{request.kind === 'signup' ? 'Signup' : 'Signin'}</td>
										<td>
											{request.minecraftUsername && request.color && <><strong><PlayerName name={request.minecraftUsername} color={request.color} /></strong><br /></>}
											{request.email}
										</td>
										<td>{formatDateTime(request.createdAtUnixMs)}</td>
										<td>{request.deliveryStatus === 'sent' ? 'Email sent' : 'Manual help needed'}</td>
										<td>{request.status === 'verified' ? 'Verified' : request.status === 'expired' ? 'Expired' : `Active until ${formatDateTime(request.expiresAtUnixMs)}`}</td>
										<td>{request.code ? <code>{request.code.replaceAll('|', ' → ')}</code> : '—'}</td>
									</tr>
								))}
								{authRequests.length === 0 && (
									<tr><td colSpan={6}>No signup or signin requests yet.</td></tr>
								)}
							</tbody>
						</table>
					</div>
					{authRequestsHaveMore && (
						<button type="button" className="loadMoreButton" disabled={loadingMore !== null} onClick={() => void loadMoreSignins()}>
							{loadingMore === 'signins' ? 'Loading...' : 'Load more'}
						</button>
					)}
				</section>
			)}

			{activeSection === 'whitelist' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Email whitelist</h3>
						<p>Allow a non-MMU email address to sign up. MMU email addresses are always allowed.</p>
						<p>Inviting an external player costs the responsible player 100 dabloons. They must be online when you add the email.</p>
					</div>
					<form className="emailWhitelistForm" onSubmit={addWhitelistedEmail}>
						<label>
							Email address
							<input
								type="email"
								value={whitelistEmail}
								onChange={(event) => setWhitelistEmail(event.target.value)}
								placeholder="person@example.com"
								required
							/>
						</label>
						<label>
							Responsible user
							<select
								value={responsibleUsername}
								onChange={(event) => setResponsibleUsername(event.target.value)}
								required
							>
								<option value="">Select a player</option>
								{players.map((player) => (
									<option className="playerName" style={playerNameStyle(player.color)} key={player.id} value={player.minecraftUsername} disabled={player.isExternal}>
										{player.minecraftUsername}
									</option>
								))}
							</select>
						</label>
						<button disabled={updatingWhitelist}>Add email</button>
					</form>
					<div className="adminTableWrap">
						<table className="adminTable">
							<thead><tr><th>Email</th><th>Responsible user</th><th>Added by</th><th>Added</th><th></th></tr></thead>
							<tbody>
								{whitelistedEmails.map((entry) => (
									<tr key={entry.email}>
										<td>{entry.email}</td>
										<td>{entry.responsibleMinecraftUsername && entry.responsiblePlayerColor ? <PlayerName name={entry.responsibleMinecraftUsername} color={entry.responsiblePlayerColor} /> : <span className="adminMissing">Not assigned</span>}</td>
										<td><PlayerName name={entry.addedByMinecraftUsername} color={entry.addedByColor} /></td>
										<td>{formatDateTime(entry.createdAtUnixMs)}</td>
										<td><button type="button" disabled={updatingWhitelist} onClick={() => void removeWhitelistedEmail(entry.email)}>Remove</button></td>
									</tr>
								))}
								{whitelistedEmails.length === 0 && <tr><td colSpan={5}>No extra email addresses are whitelisted.</td></tr>}
							</tbody>
						</table>
					</div>
				</section>
			)}

			{activeSection === 'bans' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Ban / timeout a player</h3>
						<p>Putting a player in timeout temporarily or banning them permanently has the following effects during that time period:</p>
						<ul>
							<li>They are signed out from the website on all devices.</li>
							<li>They can no longer sign in on any devices.</li>
							<li>They are added to the server blacklist, making it impossible for them to join.</li>
						</ul>
						<p>If you are banning an external player, you should also ban / timeout the player responsible for them (see this on the profiles / on the email whitelist tab) and then all the other externals that player was responsible for.</p>
					</div>

					<form className="playerBanForm" onSubmit={applyPlayerBan}>
						<label>
							Player
							<select value={banPlayerId} onChange={(event) => setBanPlayerId(event.target.value)} required>
								<option value="">Select a player</option>
								{players.map((player) => (
									<option className="playerName" style={playerNameStyle(player.color)} key={player.id} value={player.id}>
										{player.minecraftUsername}{player.isExternal ? ' (external)' : ''}
									</option>
								))}
							</select>
						</label>
						<fieldset>
							<legend>Duration</legend>
							<label><input type="radio" name="banMode" checked={banMode === 'temporary'} onChange={() => setBanMode('temporary')} /> Temporary timeout</label>
							<label><input type="radio" name="banMode" checked={banMode === 'permanent'} onChange={() => setBanMode('permanent')} /> Permanent ban</label>
						</fieldset>
						<label>
							Timeout ends
							<input type="datetime-local" value={timeoutEndsAt} onChange={(event) => setTimeoutEndsAt(event.target.value)} disabled={banMode === 'permanent'} required={banMode === 'temporary'} />
						</label>
						<button disabled={updatingBan}>{updatingBan ? 'Applying...' : 'Apply ban / timeout'}</button>
					</form>

					<div className="adminTableWrap">
						<table className="adminTable">
							<thead><tr><th>Player</th><th>Restriction</th><th>Applied by</th><th>Applied</th><th></th></tr></thead>
							<tbody>
								{activePlayerBans.map((ban) => (
									<tr key={ban.userId}>
										<td><PlayerName name={ban.minecraftUsername} color={ban.color} /></td>
										<td>{ban.expiresAtUnixMs === null ? 'Permanent ban' : `Timeout until ${formatDateTime(ban.expiresAtUnixMs)}`}</td>
										<td>{ban.bannedByMinecraftUsername}</td>
										<td>{formatDateTime(ban.createdAtUnixMs)}</td>
										<td><button type="button" disabled={updatingBan} onClick={() => void removePlayerBan(ban)}>Remove</button></td>
									</tr>
								))}
								{activePlayerBans.length === 0 && <tr><td colSpan={5}>No players are banned or in timeout.</td></tr>}
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
						<label>
							<input
								type="checkbox"
								checked={membersOnly}
								onChange={(event) => setMembersOnly(event.target.checked)}
							/>
							Members only
						</label>
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
											{giftCode.membersOnly ? ' - members only' : ''}
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

function formatDateTime(timestamp: number) {
	return new Intl.DateTimeFormat(undefined, {
		dateStyle: 'medium',
		timeStyle: 'short',
	}).format(new Date(timestamp))
}

function formatLondonDateTime(timestamp: number) {
	return new Intl.DateTimeFormat('en-GB', {
		dateStyle: 'medium',
		timeStyle: 'short',
		timeZone: 'Europe/London',
	}).format(new Date(timestamp))
}

function formatLondonInput(timestamp: number) {
	const values = Object.fromEntries(new Intl.DateTimeFormat('en-GB', {
		timeZone: 'Europe/London',
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
		hour: '2-digit',
		minute: '2-digit',
		hourCycle: 'h23',
	}).formatToParts(new Date(timestamp)).map((part) => [part.type, part.value]))
	return `${values.year}-${values.month}-${values.day}T${values.hour}:${values.minute}`
}

function apiMessage(body: unknown, fallback: string) {
	if (!body || typeof body !== 'object' || !('message' in body)) return fallback
	const message = (body as { message?: unknown }).message
	return Array.isArray(message) ? message.join(', ') : typeof message === 'string' ? message : fallback
}

function errorMessage(error: unknown, fallback: string) {
	return error instanceof Error ? error.message : fallback
}
