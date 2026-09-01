'use client';

import { PlayerName, playerNameStyle } from '@/components/player-name';
import { DabloonAmount } from '@/components/dabloon-amount';
import { formatDateTime } from './admin-api';
import type { AdminTabController } from './use-admin-tab-controller';

export function EmailWhitelistAdminSection({ controller }: { controller: AdminTabController }) {
	const {
		activeSection,
		addWhitelistedEmail,
		whitelistEmail,
		setWhitelistEmail,
		responsibleUsername,
		setResponsibleUsername,
		players,
		updatingWhitelist,
		whitelistedEmails,
		removeWhitelistedEmail,
	} = controller;
	return (
		<>
			{activeSection === 'whitelist' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Email whitelist</h3>
						<p>
							Allow a non-MMU email address to sign up. MMU email addresses are always
							allowed.
						</p>
						<p>
							Inviting an external player costs the responsible player{' '}
							<DabloonAmount amount={150} format="full" />
							for members or <DabloonAmount amount={250} format="full" /> for
							non-members. They must be online when you add the email.
						</p>
					</div>
					<form className="emailWhitelistForm" onSubmit={addWhitelistedEmail}>
						<label>
							Email address
							<input
								type="email"
								value={whitelistEmail}
								onChange={(event) => {
									setWhitelistEmail(event.target.value);
								}}
								placeholder="person@example.com"
								required
							/>
						</label>
						<label>
							Responsible user
							<select
								value={responsibleUsername}
								onChange={(event) => {
									setResponsibleUsername(event.target.value);
								}}
								required
							>
								<option value="">Select a player</option>
								{players.map((player) => (
									<option
										className="playerName"
										style={playerNameStyle(player.color)}
										key={player.id}
										value={player.minecraftUsername}
										disabled={player.isExternal}
									>
										{player.minecraftUsername}
									</option>
								))}
							</select>
						</label>
						<button disabled={updatingWhitelist}>Add email</button>
					</form>
					<div className="adminTableWrap">
						<table className="adminTable">
							<thead>
								<tr>
									<th>Email</th>
									<th>Responsible user</th>
									<th>Added by</th>
									<th>Added</th>
									<th></th>
								</tr>
							</thead>
							<tbody>
								{whitelistedEmails.map((entry) => (
									<tr key={entry.email}>
										<td>{entry.email}</td>
										<td>
											{entry.responsibleMinecraftUsername &&
											entry.responsiblePlayerColor ? (
												<PlayerName
													name={entry.responsibleMinecraftUsername}
													color={entry.responsiblePlayerColor}
												/>
											) : (
												<span className="adminMissing">Not assigned</span>
											)}
										</td>
										<td>
											<PlayerName
												name={entry.addedByMinecraftUsername}
												color={entry.addedByColor}
											/>
										</td>
										<td>{formatDateTime(entry.createdAtUnixMs)}</td>
										<td>
											<button
												type="button"
												disabled={updatingWhitelist}
												onClick={() =>
													void removeWhitelistedEmail(entry.email)
												}
											>
												Remove
											</button>
										</td>
									</tr>
								))}
								{whitelistedEmails.length === 0 && (
									<tr>
										<td colSpan={5}>
											No extra email addresses are whitelisted.
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</section>
			)}
		</>
	);
}
