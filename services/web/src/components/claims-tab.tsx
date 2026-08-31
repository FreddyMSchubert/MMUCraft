'use client';

import Image from 'next/image';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import {
	ClaimEditorCard,
	formatDimension,
	type EditableClaim,
} from '@/components/claim-editor-card';
import { PlayerName } from '@/components/player-name';
import { PlayerSelector } from '@/components/player-selector';
import { useSiteAlert } from '@/components/site-alert';
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

interface Claim extends EditableClaim {
	members: ClaimPerson[];
}

interface ClaimsResponse {
	priceDabloons: number;
	isMember: boolean;
	nextClaimNumber: number;
	memberPriceDabloons: number;
	normalPlayerPriceDabloons: number;
	claims: Claim[];
	memberClaims: Claim[];
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
	const { confirm, showAlert } = useSiteAlert();
	const [data, setData] = useState<ClaimsResponse | null>(null);
	const [searches, setSearches] = useState<Partial<Record<string, string>>>({});
	const [error, setError] = useState('');
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

	async function run(action: () => Promise<void>, failureTitle: string) {
		setBusy(true);
		setError('');
		try {
			await action();
			await load();
			return true;
		} catch (caught) {
			await showAlert({ title: failureTitle, message: readError(caught), tone: 'danger' });
			return false;
		} finally {
			setBusy(false);
		}
	}

	async function buyClaim() {
		await run(async () => {
			const current = await request<CurrentChunkResponse>('/api/claims/current');
			const confirmed = await confirm({
				title: 'Buy this chunk claim?',
				message: `Chunk ${current.chunkX}, ${current.chunkZ} in ${formatDimension(current.dimension)} costs ${current.priceDabloons} dabloons. Your current balance is ${current.balanceDabloons} dabloons.\n\nStay online and remain inside this chunk until the purchase finishes.`,
				confirmLabel: `Buy for ${current.priceDabloons}`,
			});
			if (!confirmed) return;

			const result = await request<{ message?: string }>('/api/claims', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					dimension: current.dimension,
					chunkX: current.chunkX,
					chunkZ: current.chunkZ,
				}),
			});
			await showAlert({
				title: 'Chunk claimed',
				message:
					result.message ??
					`Chunk ${current.chunkX}, ${current.chunkZ} is now protected and only accessible to you and the members you add.`,
				tone: 'success',
			});
		}, 'Could not claim this chunk');
	}

	async function removeClaim(claim: Claim) {
		if (
			!(await confirm({
				title: 'Delete this claim?',
				message: `Chunk ${claim.chunkX}, ${claim.chunkZ} will no longer be protected. This does not refund the claim price.`,
				confirmLabel: 'Delete claim',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;
		await run(async () => {
			await request(`/api/claims/${claim.id}`, { method: 'DELETE' });
		}, 'Could not delete the claim');
	}

	async function updateAppearance(claim: Claim, name: string, color: string | null) {
		return run(async () => {
			await request(`/api/claims/${claim.id}/appearance`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ name, color }),
			});
		}, 'Could not save the claim appearance');
	}

	async function addMember(claim: Claim) {
		const value = searches[claim.id]?.trim() ?? '';
		const candidate = data?.candidates.find(
			(person) =>
				person.minecraftUsername.localeCompare(value, 'en', { sensitivity: 'base' }) === 0,
		);
		if (!candidate) {
			await showAlert({
				title: 'Choose a valid player',
				message:
					'Select a player from the suggestions so the correct Minecraft account receives access.',
				tone: 'danger',
			});
			return;
		}

		await run(async () => {
			await request(`/api/claims/${claim.id}/members`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ userId: candidate.id }),
			});
			setSearches((current) => ({ ...current, [claim.id]: '' }));
		}, 'Could not add the claim member');
	}

	async function removeMember(claim: Claim, person: ClaimPerson) {
		await run(async () => {
			await request(`/api/claims/${claim.id}/members/${person.id}`, { method: 'DELETE' });
		}, 'Could not remove the claim member');
	}

	if (!data) return error ? <p className="authError">{error}</p> : <p>Loading claims...</p>;

	return (
		<div className="claimsPanel">
			<div className="claimsTop">
				<div>
					<h3>Chunk claims</h3>
					<p className="tabSubtitle">
						Minecraft divides the world into 16x16-block areas called{' '}
						<a href="https://minecraft.wiki/w/Chunk" target="_blank">
							chunks
						</a>
						. You can claim a chunk as your own. That means that in the claim only you
						and players you choose can:
						<ul>
							<li>Place & break blocks</li>
							<li>Open Chests, Barrels etc</li>
							<li>Interact with anything at all (e.g. doors)</li>
						</ul>
						This also includes other mobs like creepers, which can&apos;t damage the
						things in your claims! Claims are perfect to{' '}
						<strong>protect your items and builds</strong>!
					</p>
					<p className="tabNote">
						Stand in the chunk you want to claim, then press &quot;Add Claim&quot;.
						Press F3 + G in Minecraft to see chunks in-game. You can see the coordinates
						of the chunk you are in on the F3 debug screen.
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
			<ClaimSection
				title="My claims"
				claims={data.claims}
				emptyMessage="You do not own any claimed chunks yet."
				renderClaim={(claim) => {
					const existingIds = new Set(claim.members.map((person) => person.id));
					const candidates = data.candidates.filter(
						(person) => !existingIds.has(person.id),
					);
					return (
						<ClaimEditorCard
							key={claim.id}
							claim={claim}
							busy={busy}
							onDelete={() => void removeClaim(claim)}
							onSave={(name, color) => updateAppearance(claim, name, color)}
							summary={<ClaimMembersSummary members={claim.members} />}
						>
							<div className="claimMembers">
								{claim.members.map((person) => (
									<div className="claimMember" key={person.id}>
										<PlayerHead person={person} />
										<div>
											<strong>
												<PlayerName
													name={person.minecraftUsername}
													color={person.color}
												/>
											</strong>
											{person.pronouns && <span>{person.pronouns}</span>}
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
									<PlayerSelector
										datalistId={`claim-candidates-${claim.id}`}
										options={candidates}
										value={searches[claim.id] ?? ''}
										onChange={(value) => {
											setSearches((current) => ({
												...current,
												[claim.id]: value,
											}));
										}}
										placeholder="Search server players"
										disabled={busy || candidates.length === 0}
									/>
									<button
										type="submit"
										disabled={busy || candidates.length === 0}
									>
										Add
									</button>
								</form>
							</div>
						</ClaimEditorCard>
					);
				}}
			/>
			<ClaimSection
				title="Other claims I’m a member of"
				claims={data.memberClaims}
				emptyMessage="You are not a member of anyone else’s claims."
				renderClaim={(claim) => (
					<ClaimEditorCard
						key={claim.id}
						claim={claim}
						busy={busy}
						summary={<ClaimMembersSummary members={claim.members} />}
					/>
				)}
			/>
		</div>
	);
}

function ClaimSection({
	title,
	claims,
	emptyMessage,
	renderClaim,
}: {
	title: string;
	claims: Claim[];
	emptyMessage: string;
	renderClaim: (claim: Claim) => ReactNode;
}) {
	return (
		<section className="claimsSection">
			<h3>{title}</h3>
			<small className="claimsCoordinateHelp">
				To convert chunk coordinates to block coordinates,{' '}
				<a
					href="https://minecraft.wiki/w/Calculators/Chunk_coordinates"
					target="_blank"
					rel="noreferrer"
				>
					click here
				</a>
				.
			</small>
			{claims.length === 0 ? (
				<p className="claimsEmpty">{emptyMessage}</p>
			) : (
				<div className="claimsList">{claims.map(renderClaim)}</div>
			)}
		</section>
	);
}

function ClaimMembersSummary({ members }: { members: ClaimPerson[] }) {
	return (
		<div className="claimMembersSummary">
			<span>Members</span>
			<div className="claimMemberInlineList">
				{members.map((person) => (
					<span className="claimMemberInline" key={person.id}>
						<PlayerHead person={person} />
						<PlayerName name={person.minecraftUsername} color={person.color} />
					</span>
				))}
			</div>
		</div>
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

function readError(caught: unknown) {
	return caught instanceof Error ? caught.message : 'Request failed';
}
