'use client';

import { PlayerName } from '@/components/player-name';
import type { AdminTabController } from './use-admin-tab-controller';

export function MemberAccessAdminSection({ controller }: { controller: AdminTabController }) {
	const { activeSection, isSuperAdmin, players, busyPlayerId, setMembership, setCommittee } =
		controller;
	return (
		<>
			{activeSection === 'members' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Member list</h3>
						<p>
							Don&apos;t share this screen with people as it contains sensitive info
							about our members
						</p>
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
											<PlayerName
												name={player.minecraftUsername}
												color={player.color}
											/>
											{player.isCommittee && (
												<span className="committeeBadge">Committee</span>
											)}
										</td>
										<td>
											{player.discordUsername || (
												<span className="adminMissing">Not provided</span>
											)}
										</td>
										<td>{player.email}</td>
										<td className="membershipCell">
											<input
												type="checkbox"
												aria-label={`Member status for ${player.minecraftUsername}`}
												checked={player.isMember}
												disabled={busyPlayerId === player.id}
												onChange={(event) =>
													void setMembership(player, event.target.checked)
												}
											/>
										</td>
										{isSuperAdmin && (
											<td className="membershipCell">
												<input
													type="checkbox"
													aria-label={`Committee status for ${player.minecraftUsername}`}
													checked={player.isCommittee}
													disabled={
														busyPlayerId === player.id ||
														player.minecraftUsername.toLowerCase() ===
															'merlinspace'
													}
													onChange={(event) =>
														void setCommittee(
															player,
															event.target.checked,
														)
													}
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
		</>
	);
}
