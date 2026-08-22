'use client';

import { formatDateTime } from './admin-api';
import type { AdminTabController } from './use-admin-tab-controller';

export function DiscordCommandHistoryAdminSection({
	controller,
}: {
	controller: AdminTabController;
}) {
	const { activeSection, load, discordAdminCommands } = controller;
	return (
		<>
			{activeSection === 'discord-commands' && (
				<section className="adminSection">
					<div className="adminSectionHeader">
						<h3>Discord admin commands</h3>
						<p>Commands sent to the Minecraft console through Discord.</p>
					</div>
					<button type="button" onClick={() => void load()}>
						Refresh
					</button>
					<div className="adminTableWrap">
						<table className="adminTable">
							<thead>
								<tr>
									<th>Command</th>
									<th>Discord user</th>
									<th>Created</th>
								</tr>
							</thead>
							<tbody>
								{discordAdminCommands.map((entry, index) => (
									<tr key={`${entry.createdAtUnixMs}-${index}`}>
										<td>
											<code>{entry.command}</code>
										</td>
										<td>{entry.discordUsername}</td>
										<td>{formatDateTime(entry.createdAtUnixMs)}</td>
									</tr>
								))}
								{discordAdminCommands.length === 0 && (
									<tr>
										<td colSpan={3}>No Discord admin commands yet.</td>
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
