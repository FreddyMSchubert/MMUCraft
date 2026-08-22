'use client';

import type { AdminTabController } from './use-admin-tab-controller';

export function DailyRefreshAdminSection({ controller }: { controller: AdminTabController }) {
	const {
		activeSection,
		refreshDailies,
		dailyPlayerId,
		setDailyPlayerId,
		players,
		refreshingDailies,
	} = controller;
	return (
		<>
			{activeSection === 'dailies' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Regenerate dailies</h3>
						<p>
							The player must be online so the server can choose a new advancement
							daily. Completed dailies do not change.
						</p>
					</div>
					<div className="adminWarnings adminWarnings-critical" role="note">
						<strong>
							Use this only when a daily is impossible or ludicrously hard.
						</strong>
						<ul>
							<li>
								It is intentional that not everyone can complete every daily. These
								are challenges, not tasks.
							</li>
							<li>
								Reach out to Freddy afterward and tell him which daily caused the
								problem so he can fix why it appeared.
							</li>
						</ul>
					</div>
					<form className="playerBanForm" onSubmit={refreshDailies}>
						<label>
							Player
							<select
								value={dailyPlayerId}
								onChange={(event) => {
									setDailyPlayerId(event.target.value);
								}}
								required
							>
								<option value="">Select a player</option>
								{players.map((player) => (
									<option key={player.id} value={player.id}>
										{player.minecraftUsername}
									</option>
								))}
							</select>
						</label>
						<button disabled={refreshingDailies}>
							{refreshingDailies
								? 'Regenerating...'
								: 'Regenerate uncompleted dailies'}
						</button>
					</form>
				</section>
			)}
		</>
	);
}
