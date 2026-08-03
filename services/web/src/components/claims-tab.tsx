'use client'

import { useCallback, useEffect, useState } from 'react'

interface ClaimPerson {
	id: number
	minecraftUsername: string
	preferredName: string
	pronouns: string
	skinUrl: string | null
	isOwner?: boolean
}

interface Claim {
	id: string
	dimension: string
	chunkX: number
	chunkZ: number
	members: ClaimPerson[]
}

interface ClaimsResponse {
	priceDabloons: number
	claims: Claim[]
	candidates: ClaimPerson[]
}

interface CurrentChunkResponse {
	dimension: string
	chunkX: number
	chunkZ: number
	balanceDabloons: number
	priceDabloons: number
}

export function ClaimsTab() {
	const [data, setData] = useState<ClaimsResponse | null>(null)
	const [searches, setSearches] = useState<Record<string, string>>({})
	const [error, setError] = useState('')
	const [message, setMessage] = useState('')
	const [busy, setBusy] = useState(false)

	const load = useCallback(async () => {
		const response = await fetch('/api/claims', { cache: 'no-store' })
		const body = await response.json().catch(() => null)
		if (!response.ok) throw new Error(body?.message ?? 'Failed to load claims')
		setData(body as ClaimsResponse)
	}, [])

	useEffect(() => {
		let cancelled = false

		async function loadInitial() {
			try {
				await load()
			} catch (caught) {
				if (!cancelled) setError(readError(caught))
			}
		}

		void loadInitial()
		return () => {
			cancelled = true
		}
	}, [load])

	async function run(action: () => Promise<string>) {
		setBusy(true)
		setError('')
		setMessage('')
		try {
			setMessage(await action())
			await load()
		} catch (caught) {
			setError(readError(caught))
		} finally {
			setBusy(false)
		}
	}

	async function buyClaim() {
		await run(async () => {
			const current = await request<CurrentChunkResponse>('/api/claims/current')
			const confirmed = window.confirm(
				`Claim chunk ${current.chunkX}, ${current.chunkZ} in ${formatDimension(current.dimension)} for ${current.priceDabloons} dabloons?\n\nCurrent balance: ${current.balanceDabloons} dabloons. Stay online in this chunk while buying.`,
			)
			if (!confirmed) return ''

			const result = await request<{ message?: string }>('/api/claims', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					dimension: current.dimension,
					chunkX: current.chunkX,
					chunkZ: current.chunkZ,
				}),
			})
			return result.message ?? 'Chunk claimed.'
		})
	}

	async function removeClaim(claim: Claim) {
		if (!window.confirm(`Delete the claim at chunk ${claim.chunkX}, ${claim.chunkZ}?`)) return
		await run(async () => {
			await request(`/api/claims/${claim.id}`, { method: 'DELETE' })
			return 'Claim removed.'
		})
	}

	async function addMember(claim: Claim) {
		const value = searches[claim.id]?.trim() ?? ''
		const candidate = data?.candidates.find((person) =>
			person.minecraftUsername.localeCompare(value, 'en', { sensitivity: 'base' }) === 0)
		if (!candidate) {
			setError('Choose a server member from the suggestions.')
			return
		}

		await run(async () => {
			await request(`/api/claims/${claim.id}/members`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ userId: candidate.id }),
			})
			setSearches((current) => ({ ...current, [claim.id]: '' }))
			return `${candidate.minecraftUsername} can now use this claim.`
		})
	}

	async function removeMember(claim: Claim, person: ClaimPerson) {
		await run(async () => {
			await request(`/api/claims/${claim.id}/members/${person.id}`, { method: 'DELETE' })
			return `${person.minecraftUsername} removed from the claim.`
		})
	}

	if (!data) return error ? <p className="authError">{error}</p> : <p>Loading claims...</p>

	return (
		<div className="claimsPanel">
			<div className="claimsTop">
				<div>
					<h3>Chunk claims</h3>
					<p>Each claim protects one whole chunk, from the bottom of the world to the top.</p>
				</div>
				<button type="button" disabled={busy} onClick={() => void buyClaim()}>
					Add claim - {data.priceDabloons} dabloons
				</button>
			</div>

			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}

			{data.claims.length === 0 ? (
				<p className="claimsEmpty">You do not own any claimed chunks yet.</p>
			) : (
				<div className="claimsList">
					{data.claims.map((claim) => {
						const existingIds = new Set(claim.members.map((person) => person.id))
						const candidates = data.candidates.filter((person) => !existingIds.has(person.id))
						return (
							<section className="claimCard" key={claim.id}>
								<div className="claimHeader">
									<div>
										<h4>Chunk {claim.chunkX}, {claim.chunkZ}</h4>
										<span>{formatDimension(claim.dimension)}</span>
									</div>
									<button type="button" disabled={busy} onClick={() => void removeClaim(claim)}>Delete claim</button>
								</div>

								<div className="claimMembers">
									{claim.members.map((person) => (
										<div className="claimMember" key={person.id}>
											<PlayerHead person={person} />
											<div>
												<strong>{person.preferredName || person.minecraftUsername}</strong>
												<span>@{person.minecraftUsername}{person.pronouns ? ` - ${person.pronouns}` : ''}</span>
											</div>
											{person.isOwner ? <small>Owner</small> : (
												<button type="button" disabled={busy} onClick={() => void removeMember(claim, person)}>Remove</button>
											)}
										</div>
									))}

									<form className="claimMemberSearch" onSubmit={(event) => {
										event.preventDefault()
										void addMember(claim)
									}}>
										<input
											list={`claim-candidates-${claim.id}`}
											value={searches[claim.id] ?? ''}
											onChange={(event) => setSearches((current) => ({ ...current, [claim.id]: event.target.value }))}
											placeholder="Search server members"
											disabled={busy || candidates.length === 0}
										/>
										<datalist id={`claim-candidates-${claim.id}`}>
											{candidates.map((person) => (
												<option key={person.id} value={person.minecraftUsername}>
													{person.preferredName}{person.pronouns ? ` (${person.pronouns})` : ''}
												</option>
											))}
										</datalist>
										<button type="submit" disabled={busy || candidates.length === 0}>Add</button>
									</form>
								</div>
							</section>
						)
					})}
				</div>
			)}
		</div>
	)
}

function PlayerHead({ person }: { person: ClaimPerson }) {
	const label = `${person.minecraftUsername} head`
	if (!person.skinUrl) {
		return <span className="playerHead playerHead-small playerHeadFallback" role="img" aria-label={label}>{person.minecraftUsername[0]}</span>
	}
	return (
		<span className="playerHead playerHead-small" role="img" aria-label={label}>
			<span className="playerHeadLayer playerHeadFace" style={{ backgroundImage: `url("${person.skinUrl}")` }} />
			<span className="playerHeadLayer playerHeadHat" style={{ backgroundImage: `url("${person.skinUrl}")` }} />
		</span>
	)
}

async function request<T = Record<string, unknown>>(url: string, init?: RequestInit): Promise<T> {
	const response = await fetch(url, init)
	const body = await response.json().catch(() => null)
	if (!response.ok) throw new Error(body?.message ?? 'Request failed')
	return body as T
}

function formatDimension(dimension: string) {
	return dimension.replace('minecraft:', '').replaceAll('_', ' ')
}

function readError(caught: unknown) {
	return caught instanceof Error ? caught.message : 'Request failed'
}
