'use client';

import Image from 'next/image';
import { useCallback, useEffect, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { PlayerName, playerNameStyle } from '@/components/player-name';
import { apiMessage } from '@/lib/api-response';

interface ClaimPerson {
	id: number;
	minecraftUsername: string;
	preferredName: string;
	pronouns: string;
	color: string;
	avatarUrl: string | null;
	isOwner?: boolean;
}

interface Claim {
	id: string;
	dimension: string;
	chunkX: number;
	chunkZ: number;
	name: string;
	color: string;
	defaultColor: string;
	customColor: string | null;
	members: ClaimPerson[];
}

interface ClaimsResponse {
	priceDabloons: number;
	isMember: boolean;
	nextClaimNumber: number;
	memberPriceDabloons: number;
	normalPlayerPriceDabloons: number;
	claims: Claim[];
	candidates: ClaimPerson[];
}

interface CurrentChunkResponse {
	dimension: string;
	chunkX: number;
	chunkZ: number;
	balanceDabloons: number;
	priceDabloons: number;
}

export function ClaimsTab() {
	const [data, setData] = useState<ClaimsResponse | null>(null);
	const [searches, setSearches] = useState<Partial<Record<string, string>>>({});
	const [error, setError] = useState('');
	const [message, setMessage] = useState<ReactNode>('');
	const [busy, setBusy] = useState(false);

	const load = useCallback(async () => {
		const response = await fetch('/api/claims', { cache: 'no-store' });
		const body = await response.json().catch(() => null);
		if (!response.ok) throw new Error(apiMessage(body, 'Failed to load claims'));
		setData(body as ClaimsResponse);
	}, []);

	useEffect(() => {
		let cancelled = false;

		async function loadInitial() {
			try {
				await load();
			} catch (caught) {
				if (!cancelled) setError(readError(caught));
			}
		}

		void loadInitial();
		return () => {
			cancelled = true;
		};
	}, [load]);

	async function run(action: () => Promise<ReactNode>) {
		setBusy(true);
		setError('');
		setMessage('');
		try {
			setMessage(await action());
			await load();
		} catch (caught) {
			setError(readError(caught));
		} finally {
			setBusy(false);
		}
	}

	async function buyClaim() {
		await run(async () => {
			const current = await request<CurrentChunkResponse>('/api/claims/current');
			const confirmed = window.confirm(
				`Claim chunk ${current.chunkX}, ${current.chunkZ} in ${formatDimension(current.dimension)} for ${current.priceDabloons} dabloons?\n\nCurrent balance: ${current.balanceDabloons} dabloons. Stay online in this chunk while buying.`,
			);
			if (!confirmed) return '';

			const result = await request<{ message?: string }>('/api/claims', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					dimension: current.dimension,
					chunkX: current.chunkX,
					chunkZ: current.chunkZ,
				}),
			});
			return result.message ?? 'Chunk claimed.';
		});
	}

	async function removeClaim(claim: Claim) {
		if (!window.confirm(`Delete the claim at chunk ${claim.chunkX}, ${claim.chunkZ}?`)) return;
		await run(async () => {
			await request(`/api/claims/${claim.id}`, { method: 'DELETE' });
			return 'Claim removed.';
		});
	}

	async function updateAppearance(claim: Claim, name: string, color: string | null) {
		await run(async () => {
			await request(`/api/claims/${claim.id}/appearance`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ name, color }),
			});
			return 'Claim appearance saved.';
		});
	}

	async function addMember(claim: Claim) {
		const value = searches[claim.id]?.trim() ?? '';
		const candidate = data?.candidates.find(
			(person) =>
				person.minecraftUsername.localeCompare(value, 'en', { sensitivity: 'base' }) === 0,
		);
		if (!candidate) {
			setError('Choose a server member from the suggestions.');
			return;
		}

		await run(async () => {
			await request(`/api/claims/${claim.id}/members`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ userId: candidate.id }),
			});
			setSearches((current) => ({ ...current, [claim.id]: '' }));
			return (
				<>
					<PlayerName name={candidate.minecraftUsername} color={candidate.color} /> can
					now use this claim.
				</>
			);
		});
	}

	async function removeMember(claim: Claim, person: ClaimPerson) {
		await run(async () => {
			await request(`/api/claims/${claim.id}/members/${person.id}`, { method: 'DELETE' });
			return (
				<>
					<PlayerName name={person.minecraftUsername} color={person.color} /> removed from
					the claim.
				</>
			);
		});
	}

	if (!data) return error ? <p className="authError">{error}</p> : <p>Loading claims...</p>;

	return (
		<div className="claimsPanel">
			<div className="claimsTop">
				<div>
					<h3>Chunk claims</h3>
					<p className="tabSubtitle">
						Minecraft divides the world into 16×16-block areas called chunks. Claiming
						one means only you and the players you choose can build, break blocks or
						interact there, keeping your home safe while you are away.
					</p>
					<p className="tabNote">
						Stand in the chunk you want to claim. Press F3 + G in Minecraft to see its
						borders.
					</p>
				</div>
				<div className="claimPurchase">
					<button type="button" disabled={busy} onClick={() => void buyClaim()}>
						Add claim · {data.priceDabloons} dabloons
					</button>
					<small>
						{data.isMember ? (
							<>
								Member claim {data.nextClaimNumber} cost: {data.memberPriceDabloons}{' '}
								·{' '}
								<del>
									Normal claim {data.nextClaimNumber} cost:{' '}
									{data.normalPlayerPriceDabloons}
								</del>
							</>
						) : (
							<>
								<del>
									Member claim {data.nextClaimNumber} cost:{' '}
									{data.memberPriceDabloons}
								</del>{' '}
								· Normal claim {data.nextClaimNumber} cost:{' '}
								{data.normalPlayerPriceDabloons}
							</>
						)}
					</small>
				</div>
			</div>
			{message && <p className="dailyMessage">{message}</p>}
			{error && <p className="authError">{error}</p>}

			{data.claims.length === 0 ? (
				<p className="claimsEmpty">You do not own any claimed chunks yet.</p>
			) : (
				<div className="claimsList">
					{data.claims.map((claim) => {
						const existingIds = new Set(claim.members.map((person) => person.id));
						const candidates = data.candidates.filter(
							(person) => !existingIds.has(person.id),
						);
						return (
							<section className="claimCard" key={claim.id}>
								<div className="claimHeader">
									<div>
										<h4
											className="claimName"
											style={
												{ '--claim-color': claim.color } as CSSProperties
											}
										>
											{claim.name}
										</h4>
										<span>
											Chunk {claim.chunkX}, {claim.chunkZ} -{' '}
											{formatDimension(claim.dimension)}
										</span>
									</div>
									<button
										type="button"
										disabled={busy}
										onClick={() => void removeClaim(claim)}
									>
										Delete claim
									</button>
								</div>

								<ClaimAppearanceForm
									claim={claim}
									busy={busy}
									onSave={(name, color) => updateAppearance(claim, name, color)}
								/>

								<div className="claimMembers">
									{claim.members.map((person) => (
										<div className="claimMember" key={person.id}>
											<PlayerHead person={person} />
											<div>
												<strong>
													{person.preferredName || (
														<PlayerName
															name={person.minecraftUsername}
															color={person.color}
														/>
													)}
												</strong>
												<span>
													@
													<PlayerName
														name={person.minecraftUsername}
														color={person.color}
													/>
													{person.pronouns ? ` - ${person.pronouns}` : ''}
												</span>
											</div>
											{person.isOwner ? (
												<small>Owner</small>
											) : (
												<button
													type="button"
													disabled={busy}
													onClick={() => void removeMember(claim, person)}
												>
													Remove
												</button>
											)}
										</div>
									))}

									<form
										className="claimMemberSearch"
										onSubmit={(event) => {
											event.preventDefault();
											void addMember(claim);
										}}
									>
										<input
											list={`claim-candidates-${claim.id}`}
											value={searches[claim.id] ?? ''}
											onChange={(event) => {
												setSearches((current) => ({
													...current,
													[claim.id]: event.target.value,
												}));
											}}
											placeholder="Search server members"
											disabled={busy || candidates.length === 0}
										/>
										<datalist id={`claim-candidates-${claim.id}`}>
											{candidates.map((person) => (
												<option
													className="playerName"
													style={playerNameStyle(person.color)}
													key={person.id}
													value={person.minecraftUsername}
												/>
											))}
										</datalist>
										<button
											type="submit"
											disabled={busy || candidates.length === 0}
										>
											Add
										</button>
									</form>
								</div>
							</section>
						);
					})}
				</div>
			)}
		</div>
	);
}

function ClaimAppearanceForm({
	claim,
	busy,
	onSave,
}: {
	claim: Claim;
	busy: boolean;
	onSave: (name: string, color: string | null) => Promise<void>;
}) {
	const [color, setColor] = useState<string | null>(claim.customColor);
	return (
		<form
			className="claimAppearanceForm"
			onSubmit={(event) => {
				event.preventDefault();
				const name = new FormData(event.currentTarget).get('name');
				void onSave(typeof name === 'string' ? name : '', color);
			}}
		>
			<label>
				<span>Claim name (20 characters maximum)</span>
				<input
					name="name"
					defaultValue={claim.name}
					maxLength={20}
					required
					disabled={busy}
				/>
			</label>
			<label className="claimColorInput">
				<span>Color</span>
				<input
					type="color"
					value={color ?? claim.defaultColor}
					onChange={(event) => {
						setColor(event.target.value);
					}}
					disabled={busy}
				/>
			</label>
			<button
				type="button"
				disabled={busy || color === null}
				onClick={() => {
					setColor(null);
				}}
			>
				Reset color
			</button>
			<button type="submit" disabled={busy}>
				Save
			</button>
		</form>
	);
}

function PlayerHead({ person }: { person: ClaimPerson }) {
	const label = `${person.minecraftUsername} head`;
	if (!person.avatarUrl) {
		return (
			<span
				className="playerHead playerHead-small playerHeadFallback"
				role="img"
				aria-label={label}
			>
				{person.minecraftUsername[0]}
			</span>
		);
	}
	return (
		<Image
			unoptimized
			className="playerHead playerHead-small"
			src={person.avatarUrl}
			alt={label}
			width={42}
			height={42}
		/>
	);
}

async function request<T = Record<string, unknown>>(url: string, init?: RequestInit): Promise<T> {
	const response = await fetch(url, init);
	const body = await response.json().catch(() => null);
	if (!response.ok) throw new Error(apiMessage(body, 'Request failed'));
	return body as T;
}

function formatDimension(dimension: string) {
	return dimension.replace('minecraft:', '').replaceAll('_', ' ');
}

function readError(caught: unknown) {
	return caught instanceof Error ? caught.message : 'Request failed';
}
