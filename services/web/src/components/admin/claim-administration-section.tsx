'use client';

import { PlayerName } from '@/components/player-name';
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
	} = controller;
	return (
		<>
			{activeSection === 'claims' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Claims</h3>
						<p>
							Review claimed chunks and delete claims that block or grief other
							players.
						</p>
					</div>
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
								{claims.map((claim) => (
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
								{claims.length === 0 && (
									<tr>
										<td colSpan={5}>No chunks are claimed.</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
					{claimsHaveMore && (
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
