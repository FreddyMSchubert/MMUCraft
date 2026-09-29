'use client';

import { type SyntheticEvent, useEffect, useMemo, useState } from 'react';
import { PlayerName } from '@/components/player-name';
import { fuzzyFilter, PlayerSelector } from '@/components/player-selector';
import type { AdminTabController } from './use-admin-tab-controller';
import { MembershipImportAdmin } from './membership-import-admin';
import { apiMessage, errorMessage } from './admin-api';

interface PendingMembership {
	email: string;
	created_at_unix_ms: number;
}

function PendingMembershipAdmin({
	refresh,
	onApplied,
}: {
	refresh: number;
	onApplied: () => Promise<void>;
}) {
	const [emails, setEmails] = useState<PendingMembership[]>([]);
	const [email, setEmail] = useState('');
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');
	const [revision, setRevision] = useState(0);

	useEffect(() => {
		let active = true;
		void fetch('/api/admin/pending-memberships', { cache: 'no-store' })
			.then(async (response) => {
				const body = await response.json().catch(() => null);
				if (!response.ok)
					throw new Error(apiMessage(body, 'Could not load pending memberships'));
				return body as { emails: PendingMembership[] };
			})
			.then((body) => {
				if (active) setEmails(body.emails);
			})
			.catch((caught: unknown) => {
				if (active) setError(errorMessage(caught, 'Could not load pending memberships'));
			});
		return () => {
			active = false;
		};
	}, [refresh, revision]);

	async function add(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		setBusy(true);
		setError('');
		setNotice('');
		try {
			const response = await fetch('/api/admin/pending-memberships', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ email }),
			});
			const body = (await response.json().catch(() => null)) as { status?: string } | null;
			if (!response.ok) throw new Error(apiMessage(body, 'Could not add membership'));
			setNotice(
				body?.status === 'pending signup'
					? 'Added for signup.'
					: body?.status === 'already member'
						? 'Already a member.'
						: 'Membership granted.',
			);
			setEmail('');
			setRevision((value) => value + 1);
			if (body?.status === 'updated') await onApplied();
		} catch (caught) {
			setError(errorMessage(caught, 'Could not add membership'));
		} finally {
			setBusy(false);
		}
	}

	async function remove(entry: PendingMembership) {
		setBusy(true);
		setError('');
		setNotice('');
		try {
			const response = await fetch(
				`/api/admin/pending-memberships/${encodeURIComponent(entry.email)}`,
				{ method: 'DELETE' },
			);
			if (!response.ok) {
				const body = await response.json().catch(() => null);
				throw new Error(apiMessage(body, 'Could not remove pending membership'));
			}
			setRevision((value) => value + 1);
		} catch (caught) {
			setError(errorMessage(caught, 'Could not remove pending membership'));
		} finally {
			setBusy(false);
		}
	}

	return (
		<div className="adminSection">
			<h3>Memberships waiting for signup</h3>
			<p>
				These emails become members when they finish signup. Adding an existing account
				grants membership now.
			</p>
			<form className="pendingMembershipForm" onSubmit={(event) => void add(event)}>
				<input
					type="email"
					aria-label="Email to grant membership"
					placeholder="person@example.com"
					value={email}
					onChange={(event) => {
						setEmail(event.target.value);
					}}
					disabled={busy}
					required
				/>
				<button disabled={busy}>Add membership</button>
			</form>
			{notice && <p role="status">{notice}</p>}
			{error && <p role="alert">{error}</p>}
			<div className="adminTableWrap">
				<table className="adminTable">
					<thead>
						<tr>
							<th>Email</th>
							<th>Action</th>
						</tr>
					</thead>
					<tbody>
						{emails.map((entry) => (
							<tr key={entry.email}>
								<td>{entry.email}</td>
								<td>
									<button
										type="button"
										disabled={busy}
										onClick={() => void remove(entry)}
									>
										Remove
									</button>
								</td>
							</tr>
						))}
						{emails.length === 0 && (
							<tr>
								<td colSpan={2}>No memberships are waiting for signup.</td>
							</tr>
						)}
					</tbody>
				</table>
			</div>
		</div>
	);
}

export function MemberAccessAdminSection({ controller }: { controller: AdminTabController }) {
	const { activeSection, isSuperAdmin, players, busyPlayerId, setMembership, setCommittee } =
		controller;
	const [search, setSearch] = useState('');
	const [pendingRefresh, setPendingRefresh] = useState(0);
	const visiblePlayers = useMemo(
		() => fuzzyFilter(players, search, ['minecraftUsername', 'discordUsername', 'email']),
		[players, search],
	);
	return (
		<>
			{activeSection === 'members' && (
				<section className="adminSection">
					<MembershipImportAdmin
						onApplied={async () => {
							await controller.load();
							setPendingRefresh((value) => value + 1);
						}}
					/>
					<PendingMembershipAdmin refresh={pendingRefresh} onApplied={controller.load} />
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
					<PlayerSelector
						datalistId="member-list-players"
						options={players}
						value={search}
						onChange={setSearch}
						placeholder="Search members"
						ariaLabel="Search the member list"
					/>

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
								{visiblePlayers.length === 0 && (
									<tr>
										<td colSpan={isSuperAdmin ? 5 : 4}>
											No members match that search.
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
