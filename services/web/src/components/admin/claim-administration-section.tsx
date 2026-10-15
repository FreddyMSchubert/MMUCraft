'use client';

import { Fragment, useEffect, useMemo, useRef, useState } from 'react';
import {
	ClaimEditorCard,
	ClaimMembersEditor,
	ClaimMembersSummary,
	type ClaimPerson,
	type EditableClaim,
} from '@/components/claim-editor-card';
import { PlayerName } from '@/components/player-name';
import { fuzzyFilter, PlayerSelector } from '@/components/player-selector';
import { useSiteAlert } from '@/components/site-alert';
import { apiMessage } from '@/lib/api-response';
import type { AdminClaim } from './admin-data.types';
import type { AdminTabController } from './use-admin-tab-controller';

interface ClaimDetail {
	claim: EditableClaim & { members: ClaimPerson[] };
	candidates: ClaimPerson[];
}

export function ClaimAdministrationSection({ controller }: { controller: AdminTabController }) {
	const {
		activeSection,
		claims,
		setClaims,
		busyClaimId,
		removeClaim,
		claimsHaveMore,
		loadingMore,
		loadMoreClaims,
		players,
	} = controller;
	const { showAlert } = useSiteAlert();
	const [search, setSearch] = useState('');
	const [detail, setDetail] = useState<ClaimDetail | null>(null);
	const [loadingClaimId, setLoadingClaimId] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);
	const autoLoadedCount = useRef(-1);
	const visibleClaims = useMemo(
		() =>
			fuzzyFilter(claims, search, [
				'minecraftUsername',
				'name',
				'dimension',
				'chunkX',
				'chunkZ',
			]),
		[claims, search],
	);

	useEffect(() => {
		// ponytail: Load every page during a search. Add server-side fuzzy search if this becomes slow.
		if (
			search.trim() &&
			claimsHaveMore &&
			loadingMore === null &&
			autoLoadedCount.current !== claims.length
		) {
			autoLoadedCount.current = claims.length;
			void loadMoreClaims();
		}
	}, [claims.length, claimsHaveMore, loadMoreClaims, loadingMore, search]);

	async function loadDetail(claimId: string) {
		const updated = await request<ClaimDetail>(`/api/admin/claims/${claimId}`);
		setDetail(updated);
		setClaims((current) =>
			current.map((claim) =>
				claim.id === claimId ? { ...claim, name: updated.claim.name } : claim,
			),
		);
	}

	async function editClaim(claim: AdminClaim) {
		if (detail?.claim.id === claim.id) {
			setDetail(null);
			return;
		}
		setLoadingClaimId(claim.id);
		try {
			await loadDetail(claim.id);
		} catch (caught) {
			await showAlert({
				title: 'Could not load the claim',
				message: readError(caught),
				tone: 'danger',
			});
		} finally {
			setLoadingClaimId(null);
		}
	}

	async function updateClaim(action: () => Promise<void>, claimId: string, failureTitle: string) {
		setSaving(true);
		try {
			await action();
			await loadDetail(claimId);
			return true;
		} catch (caught) {
			await showAlert({ title: failureTitle, message: readError(caught), tone: 'danger' });
			return false;
		} finally {
			setSaving(false);
		}
	}

	function updateAppearance(claimId: string, name: string, color: string | null) {
		return updateClaim(
			() =>
				request(`/api/admin/claims/${claimId}/appearance`, {
					method: 'PATCH',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ name, color }),
				}),
			claimId,
			'Could not save the claim appearance',
		);
	}

	function addMember(claimId: string, person: ClaimPerson) {
		return updateClaim(
			() =>
				request(`/api/admin/claims/${claimId}/members`, {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ userId: person.id }),
				}),
			claimId,
			'Could not add the claim member',
		);
	}

	function removeMember(claimId: string, person: ClaimPerson) {
		return updateClaim(
			() =>
				request(`/api/admin/claims/${claimId}/members/${person.id}`, {
					method: 'DELETE',
				}),
			claimId,
			'Could not remove the claim member',
		);
	}

	return (
		<>
			{activeSection === 'claims' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Player claims</h3>
						<p>
							Review and edit claimed chunks, or delete claims that block or grief
							other players.
						</p>
					</div>
					<PlayerSelector
						datalistId="player-claim-players"
						options={players}
						value={search}
						onChange={(value) => {
							autoLoadedCount.current = -1;
							setSearch(value);
						}}
						placeholder="Search players or claims"
						ariaLabel="Search player claims"
					/>
					<div className="adminTableWrap">
						<table className="adminTable">
							<thead>
								<tr>
									<th>Player</th>
									<th>Claim name</th>
									<th>Dimension</th>
									<th>Chunk coordinate</th>
									<th></th>
								</tr>
							</thead>
							<tbody>
								{visibleClaims.map((claim) => (
									<Fragment key={claim.id}>
										<tr>
											<td>
												<PlayerName
													name={claim.minecraftUsername}
													color={claim.color}
													isCommittee={claim.isCommittee}
													customEmojis={claim.customEmojis}
												/>
											</td>
											<td>{claim.name}</td>
											<td>
												<code>{claim.dimension}</code>
											</td>
											<td>
												<code>
													({claim.chunkX}, {claim.chunkZ})
												</code>
											</td>
											<td>
												<button
													type="button"
													disabled={
														busyClaimId !== null ||
														saving ||
														loadingClaimId !== null
													}
													onClick={() => void editClaim(claim)}
												>
													{loadingClaimId === claim.id
														? 'Loading...'
														: detail?.claim.id === claim.id
															? 'Close'
															: 'Edit'}
												</button>{' '}
												<button
													type="button"
													disabled={
														busyClaimId !== null ||
														saving ||
														loadingClaimId !== null
													}
													onClick={() => void removeClaim(claim)}
												>
													{busyClaimId === claim.id
														? 'Deleting...'
														: 'Delete'}
												</button>
											</td>
										</tr>
										{detail?.claim.id === claim.id && (
											<tr>
												<td colSpan={5}>
													<ClaimEditorCard
														key={claim.id}
														claim={detail.claim}
														busy={saving}
														initiallyEditing
														onSave={(name, color) =>
															updateAppearance(claim.id, name, color)
														}
														summary={
															<ClaimMembersSummary
																members={detail.claim.members}
															/>
														}
													>
														<ClaimMembersEditor
															claimId={claim.id}
															members={detail.claim.members}
															candidates={detail.candidates}
															busy={saving}
															onAdd={(person) =>
																addMember(claim.id, person)
															}
															onRemove={(person) =>
																void removeMember(claim.id, person)
															}
														/>
													</ClaimEditorCard>
												</td>
											</tr>
										)}
									</Fragment>
								))}
								{visibleClaims.length === 0 && loadingMore === null && (
									<tr>
										<td colSpan={5}>
											{search.trim()
												? 'No player claims match that search.'
												: 'No chunks are claimed.'}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
					{claimsHaveMore && !search.trim() && (
						<button
							type="button"
							className="loadMoreButton"
							disabled={loadingMore !== null || busyClaimId !== null}
							onClick={() => void loadMoreClaims()}
						>
							{loadingMore === 'claims' ? 'Loading...' : 'Load more'}
						</button>
					)}
				</section>
			)}
		</>
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
