'use client';

import { PlayerName } from '@/components/player-name';
import { PlayerSelector } from '@/components/player-selector';
import { formatDateTime } from './admin-api';
import type { AdminTabController } from './use-admin-tab-controller';

export function PlayerBanAdminSection({ controller }: { controller: AdminTabController }) {
	const {
		activeSection,
		applyPlayerBan,
		banPlayerId,
		setBanPlayerId,
		players,
		banMode,
		setBanMode,
		timeoutEndsAt,
		setTimeoutEndsAt,
		updatingBan,
		activePlayerBans,
		removePlayerBan,
	} = controller;
	return (
		<>
			{activeSection === 'bans' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Ban / timeout a player</h3>
						<p>
							Putting a player in timeout temporarily or banning them permanently has
							the following effects during that time period:
						</p>
						<ul>
							<li>They are signed out from the website on all devices.</li>
							<li>They can no longer sign in on any devices.</li>
							<li>
								Velocity blocks new joins and disconnects them if they are online.
							</li>
						</ul>
						<p>
							If you are banning an external player, you should also ban / timeout the
							player responsible for them (see this on the profiles / on the email
							whitelist tab) and then all the other externals that player was
							responsible for.
						</p>
					</div>

					<form className="playerBanForm" onSubmit={applyPlayerBan}>
						<label>
							Player
							<PlayerSelector
								datalistId="ban-players"
								options={players}
								value={banPlayerId}
								onChange={setBanPlayerId}
								placeholder="Search server players"
								disabled={updatingBan}
								required
							/>
						</label>
						<fieldset>
							<legend>Duration</legend>
							<label>
								<input
									type="radio"
									name="banMode"
									checked={banMode === 'temporary'}
									onChange={() => {
										setBanMode('temporary');
									}}
								/>{' '}
								Temporary timeout
							</label>
							<label>
								<input
									type="radio"
									name="banMode"
									checked={banMode === 'permanent'}
									onChange={() => {
										setBanMode('permanent');
									}}
								/>{' '}
								Permanent ban
							</label>
						</fieldset>
						<label>
							Timeout ends
							<input
								type="datetime-local"
								value={timeoutEndsAt}
								onChange={(event) => {
									setTimeoutEndsAt(event.target.value);
								}}
								disabled={banMode === 'permanent'}
								required={banMode === 'temporary'}
							/>
						</label>
						<button disabled={updatingBan}>
							{updatingBan ? 'Applying...' : 'Apply ban / timeout'}
						</button>
					</form>

					<div className="adminTableWrap">
						<table className="adminTable">
							<thead>
								<tr>
									<th>Player</th>
									<th>Restriction</th>
									<th>Applied by</th>
									<th>Applied</th>
									<th></th>
								</tr>
							</thead>
							<tbody>
								{activePlayerBans.map((ban) => (
									<tr key={ban.userId}>
										<td>
											<PlayerName
												name={ban.minecraftUsername}
												color={ban.color}
											/>
										</td>
										<td>
											{ban.expiresAtUnixMs === null
												? 'Permanent ban'
												: `Timeout until ${formatDateTime(ban.expiresAtUnixMs)}`}
										</td>
										<td>{ban.bannedByMinecraftUsername}</td>
										<td>{formatDateTime(ban.createdAtUnixMs)}</td>
										<td>
											<button
												type="button"
												disabled={updatingBan}
												onClick={() => void removePlayerBan(ban)}
											>
												Remove
											</button>
										</td>
									</tr>
								))}
								{activePlayerBans.length === 0 && (
									<tr>
										<td colSpan={5}>No players are banned or in timeout.</td>
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
