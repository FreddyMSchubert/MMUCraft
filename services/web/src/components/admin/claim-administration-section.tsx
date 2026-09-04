'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { PlayerName } from '@/components/player-name';
import { fuzzyFilter, PlayerSelector } from '@/components/player-selector';
import type { AdminTabController } from './use-admin-tab-controller';

export function ClaimAdministrationSection({ controller }: { controller: AdminTabController }) {
	const {
		activeSection,
		claims,
		busyClaimId,
		removeClaim,
		claimsHaveMore,
		loadingMore,
		loadMoreClaims,
		players,
	} = controller;
	const [search, setSearch] = useState('');
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
	return (
		<>
			{activeSection === 'claims' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Player claims</h3>
						<p>
							Review claimed chunks and delete claims that block or grief other
							players.
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
									<tr key={claim.id}>
										<td>
											<PlayerName
												name={claim.minecraftUsername}
												color={claim.color}
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
												disabled={busyClaimId !== null}
												onClick={() => void removeClaim(claim)}
											>
												{busyClaimId === claim.id
													? 'Deleting...'
													: 'Delete'}
											</button>
										</td>
									</tr>
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
