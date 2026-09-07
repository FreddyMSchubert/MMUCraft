'use client';

import { useMemo, useState } from 'react';
import { PlayerName, type PlayerEmoji } from '@/components/player-name';
import { fuzzyFilter, PlayerSelector } from '@/components/player-selector';
import { useSiteAlert } from '@/components/site-alert';
import { apiMessage } from './admin-api';
import type { AdminPlayer } from './admin-data.types';
import type { AdminTabController } from './use-admin-tab-controller';

export function PlayerEmojiAdminSection({ controller }: { controller: AdminTabController }) {
	const { activeSection, players, setPlayers } = controller;
	const { showAlert } = useSiteAlert();
	const [search, setSearch] = useState('');
	const [editingId, setEditingId] = useState<number | null>(null);
	const [draft, setDraft] = useState<PlayerEmoji[]>([]);
	const [busyId, setBusyId] = useState<number | null>(null);
	const visiblePlayers = useMemo(
		() => fuzzyFilter(players, search, ['minecraftUsername', 'discordUsername']),
		[players, search],
	);

	async function save(player: AdminPlayer, emojis: PlayerEmoji[] | null) {
		setBusyId(player.id);
		try {
			const response = await fetch(`/api/admin/players/${player.id}/emojis`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ emojis }),
			});
			const body = (await response.json().catch(() => null)) as {
				customEmojis?: PlayerEmoji[];
			} | null;
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to update emojis'));
			setPlayers((current) =>
				current.map((candidate) =>
					candidate.id === player.id
						? {
								...candidate,
								customEmojis: body?.customEmojis ?? [],
							}
						: candidate,
				),
			);
			setEditingId(null);
		} catch (caught) {
			await showAlert({
				title: 'Could not update emojis',
				message: caught instanceof Error ? caught.message : 'No emoji changes were made.',
				tone: 'danger',
			});
		} finally {
			setBusyId(null);
		}
	}

	if (activeSection !== 'emojis') return null;
	return (
		<section className="adminSection">
			<div className="adminSectionHeader">
				<h3>Player emojis</h3>
				<p>
					Add optional emojis. Committee, member and external badges stay controlled by
					player status.
				</p>
			</div>
			<PlayerSelector
				datalistId="emoji-list-players"
				options={players}
				value={search}
				onChange={setSearch}
				placeholder="Search players"
				ariaLabel="Search the player emoji list"
			/>
			<div className="adminTableWrap">
				<table className="adminTable">
					<thead>
						<tr>
							<th>Player</th>
							<th>Discord name</th>
							<th>Emojis</th>
							<th>Actions</th>
						</tr>
					</thead>
					<tbody>
						{visiblePlayers.map((player) => (
							<tr key={player.id}>
								<td>
									<PlayerName
										name={player.minecraftUsername}
										color={player.color}
										isCommittee={player.isCommittee}
										customEmojis={player.customEmojis}
									/>
								</td>
								<td>{player.discordUsername || 'Not provided'}</td>
								<td>
									{editingId === player.id ? (
										<div className="emojiEditor">
											{draft.map((entry, index) => (
												<div key={index} className="emojiEditorRow">
													<input
														aria-label={`Emoji ${index + 1}`}
														value={entry.emoji}
														onChange={(event) => {
															setDraft((current) =>
																current.map((item, itemIndex) =>
																	itemIndex === index
																		? {
																				...item,
																				emoji: event.target
																					.value,
																			}
																		: item,
																),
															);
														}}
													/>
													<input
														aria-label={`Emoji ${index + 1} explanation`}
														value={entry.explanation}
														onChange={(event) => {
															setDraft((current) =>
																current.map((item, itemIndex) =>
																	itemIndex === index
																		? {
																				...item,
																				explanation:
																					event.target
																						.value,
																			}
																		: item,
																),
															);
														}}
													/>
													<button
														type="button"
														onClick={() => {
															setDraft((current) =>
																current.filter(
																	(_, itemIndex) =>
																		itemIndex !== index,
																),
															);
														}}
													>
														Remove
													</button>
												</div>
											))}
											{draft.length < 8 && (
												<button
													type="button"
													onClick={() => {
														setDraft((current) => [
															...current,
															{ emoji: '', explanation: '' },
														]);
													}}
												>
													Add emoji
												</button>
											)}
										</div>
									) : (
										player.customEmojis.map(({ emoji, explanation }) => (
											<span
												key={`${emoji}:${explanation}`}
												className="emojiSummary"
											>
												{emoji} {explanation}
											</span>
										))
									)}
								</td>
								<td>
									<div className="adminActions">
										{editingId === player.id ? (
											<>
												<button
													type="button"
													disabled={busyId === player.id}
													onClick={() => void save(player, draft)}
												>
													Save
												</button>
												<button
													type="button"
													onClick={() => {
														setEditingId(null);
													}}
												>
													Cancel
												</button>
											</>
										) : (
											<button
												type="button"
												onClick={() => {
													setDraft(player.customEmojis);
													setEditingId(player.id);
												}}
											>
												Edit
											</button>
										)}
										<button
											type="button"
											disabled={busyId === player.id}
											onClick={() => void save(player, null)}
										>
											Reset emojis to default
										</button>
									</div>
								</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>
		</section>
	);
}
