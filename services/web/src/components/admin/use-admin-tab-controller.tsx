'use client';

import { type SyntheticEvent, useState } from 'react';
import { PlayerName } from '@/components/player-name';
import { useSiteAlert } from '@/components/site-alert';
import { apiBody, apiMessage, errorMessage, formatDateTime } from './admin-api';
import {
	ADMIN_PAGE_SIZE,
	normalizeAdminSection,
	type ActivePlayerBan,
	type AdminClaim,
	type AdminPlayer,
} from './admin-data.types';
import { useAdminSectionData } from './use-admin-section-data';
import { useCountdownAdministration } from './use-countdown-administration';
import { useGiftCodeAdministration } from './use-gift-code-administration';

export function useAdminTabController({
	isSuperAdmin,
	section,
}: {
	isSuperAdmin: boolean;
	section?: string;
}) {
	const { confirm, showAlert } = useSiteAlert();
	const activeSection = normalizeAdminSection(section);
	const [error, setError] = useState('');
	const adminSectionData = useAdminSectionData(activeSection, setError);
	const {
		players,
		setPlayers,
		setCountdowns,
		claims,
		setClaims,
		setClaimsHaveMore,
		setWhitelistedEmails,
		setActivePlayerBans,
		load,
	} = adminSectionData;
	const [whitelistEmail, setWhitelistEmail] = useState('');
	const [responsibleUsername, setResponsibleUsername] = useState('');
	const [banPlayerId, setBanPlayerId] = useState('');
	const [banMode, setBanMode] = useState<'temporary' | 'permanent'>('temporary');
	const [timeoutEndsAt, setTimeoutEndsAt] = useState('');
	const [busyPlayerId, setBusyPlayerId] = useState<number | null>(null);
	const [busyClaimId, setBusyClaimId] = useState<string | null>(null);
	const [loadingMore, setLoadingMore] = useState<'claims' | null>(null);
	const [updatingWhitelist, setUpdatingWhitelist] = useState(false);
	const [updatingBan, setUpdatingBan] = useState(false);
	const [dailyPlayerId, setDailyPlayerId] = useState('');
	const [refreshingDailies, setRefreshingDailies] = useState(false);
	const giftCodeAdministration = useGiftCodeAdministration({
		activeSection,
		reload: load,
	});
	const countdownAdministration = useCountdownAdministration({
		setCountdowns,
		reload: load,
	});

	async function setMembership(player: AdminPlayer, isMember: boolean) {
		const action = isMember ? 'mark as a society member' : 'remove society membership from';
		if (
			!(await confirm({
				title: `${isMember ? 'Grant' : 'Remove'} society membership?`,
				message: `This will ${action} ${player.minecraftUsername}.${player.isExternal ? ' This is an external player, so verify that you selected the correct account.' : ''}`,
				confirmLabel: isMember ? 'Grant membership' : 'Remove membership',
				confirmTone: isMember ? 'primary' : 'danger',
				tone: isMember ? 'info' : 'danger',
			}))
		)
			return;

		setBusyPlayerId(player.id);
		setError('');
		try {
			const response = await fetch(`/api/admin/players/${player.id}/membership`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ isMember }),
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to update membership'));

			setPlayers((current) =>
				current.map((candidate) =>
					candidate.id === player.id ? { ...candidate, isMember } : candidate,
				),
			);
		} catch (caught) {
			await showFailure(
				'Could not update membership',
				caught,
				'No membership changes were made.',
			);
		} finally {
			setBusyPlayerId(null);
		}
	}

	async function setCommittee(player: AdminPlayer, isCommittee: boolean) {
		const action = isCommittee ? 'give committee access to' : 'remove committee access from';
		if (
			!(await confirm({
				title: `${isCommittee ? 'Grant' : 'Remove'} committee access?`,
				message: `This will ${action} ${player.minecraftUsername}.${player.isExternal ? ' This is an external player, so verify that you selected the correct account.' : ''}`,
				confirmLabel: isCommittee ? 'Grant access' : 'Remove access',
				confirmTone: isCommittee ? 'primary' : 'danger',
				tone: isCommittee ? 'info' : 'danger',
			}))
		)
			return;
		setBusyPlayerId(player.id);
		setError('');
		try {
			const response = await fetch(`/api/admin/players/${player.id}/committee`, {
				method: 'PATCH',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ isCommittee }),
			});
			const body = await response.json().catch(() => null);
			if (!response.ok)
				throw new Error(apiMessage(body, 'Failed to update committee access'));

			setPlayers((current) =>
				current.map((candidate) =>
					candidate.id === player.id ? { ...candidate, isCommittee } : candidate,
				),
			);
		} catch (caught) {
			await showFailure(
				'Could not update committee access',
				caught,
				'No committee-access changes were made.',
			);
		} finally {
			setBusyPlayerId(null);
		}
	}

	async function refreshDailies(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		const player = players.find((candidate) => candidate.id === Number(dailyPlayerId));
		if (
			!player ||
			!(await confirm({
				title: 'Regenerate this player’s dailies?',
				message: `Today’s unfinished daily tasks for ${player.minecraftUsername} will be replaced. Completed tasks and claimed rewards will stay unchanged.`,
				confirmLabel: 'Regenerate dailies',
			}))
		)
			return;

		setRefreshingDailies(true);
		setError('');
		try {
			const response = await fetch(`/api/admin/dailies/${player.id}/refresh`, {
				method: 'POST',
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to regenerate dailies'));
			await showAlert({
				title: 'Dailies regenerated',
				message: apiMessage(
					body,
					`${player.minecraftUsername} now has a new set of unfinished daily tasks.`,
				),
				tone: 'success',
			});
		} catch (caught) {
			await showFailure(
				'Could not regenerate dailies',
				caught,
				'The player’s current daily tasks were not changed.',
			);
		} finally {
			setRefreshingDailies(false);
		}
	}

	async function addWhitelistedEmail(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		const responsiblePlayer = players.find(
			(player) =>
				player.minecraftUsername.localeCompare(responsibleUsername, 'en', {
					sensitivity: 'base',
				}) === 0 && !player.isExternal,
		);
		if (!responsiblePlayer) {
			await showAlert({
				title: 'Choose a responsible player',
				message:
					'Select an internal server player from the username suggestions. This account will pay for and be responsible for the external invitation.',
				tone: 'danger',
			});
			return;
		}
		const invitePrice = responsiblePlayer.isMember ? 150 : 250;
		if (
			!(await confirm({
				title: 'Add this signup invitation?',
				message: `${responsiblePlayer.minecraftUsername} will pay ${invitePrice} dabloons so ${whitelistEmail} can create an account. The responsible player must stay online in Minecraft until the charge finishes.`,
				confirmLabel: `Charge ${invitePrice} dabloons`,
			}))
		)
			return;
		setUpdatingWhitelist(true);
		setError('');

		void (async () => {
			try {
				const response = await fetch('/api/admin/email-whitelist', {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({
						email: whitelistEmail,
						responsibleUserId: responsiblePlayer.id,
					}),
				});
				const body = await response.json().catch(() => null);
				if (!response.ok)
					throw new Error(apiMessage(body, 'Failed to whitelist the email address'));
				const result = apiBody<{
					email: string;
					priceDabloons: number;
					balanceDabloons: number;
				}>(body);

				setWhitelistEmail('');
				setResponsibleUsername('');
				await showAlert({
					title: 'Signup invitation added',
					tone: 'success',
					message: (
						<>
							{result.email} can now sign up.{' '}
							<PlayerName
								name={responsiblePlayer.minecraftUsername}
								color={responsiblePlayer.color}
							/>{' '}
							paid {result.priceDabloons} dabloons and has {result.balanceDabloons}{' '}
							left.
						</>
					),
				});
				await load();
			} catch (caught) {
				await showFailure(
					'Could not add the signup invitation',
					caught,
					'The email was not added and the responsible player was not charged.',
				);
			} finally {
				setUpdatingWhitelist(false);
			}
		})();
	}

	async function removeWhitelistedEmail(email: string) {
		if (
			!(await confirm({
				title: 'Remove this signup invitation?',
				message: `${email} will no longer be able to use this invitation to create an account.`,
				confirmLabel: 'Remove invitation',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;
		setUpdatingWhitelist(true);
		setError('');
		try {
			const response = await fetch(
				`/api/admin/email-whitelist/${encodeURIComponent(email)}`,
				{ method: 'DELETE' },
			);
			const body = await response.json().catch(() => null);
			if (!response.ok)
				throw new Error(apiMessage(body, 'Failed to remove the email address'));
			setWhitelistedEmails((current) => current.filter((entry) => entry.email !== email));
		} catch (caught) {
			await showFailure(
				'Could not remove the signup invitation',
				caught,
				`${email} is still on the signup whitelist.`,
			);
		} finally {
			setUpdatingWhitelist(false);
		}
	}

	async function applyPlayerBan(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		const player = players.find((candidate) => candidate.id === Number(banPlayerId));
		if (!player) {
			await showAlert({
				title: 'Choose a player',
				message: 'Select the exact Minecraft account that should receive the restriction.',
				tone: 'danger',
			});
			return;
		}
		const expiresAtUnixMs = banMode === 'temporary' ? new Date(timeoutEndsAt).getTime() : null;
		if (
			expiresAtUnixMs !== null &&
			(!Number.isFinite(expiresAtUnixMs) || expiresAtUnixMs <= Date.now())
		) {
			await showAlert({
				title: 'Choose a future timeout end',
				message: 'A temporary timeout must end later than the current date and time.',
				tone: 'danger',
			});
			return;
		}

		let restriction = 'permanently ban';
		if (banMode === 'temporary') {
			if (expiresAtUnixMs === null) return;
			restriction = `put in timeout until ${formatDateTime(expiresAtUnixMs)}`;
		}
		if (
			!(await confirm({
				title: 'Apply this player restriction?',
				message: `${player.minecraftUsername} will be signed out everywhere, blocked from website sign-in, and blocked by Velocity. Check the selected player and any related external accounts before you apply this action: ${restriction} ${player.minecraftUsername}.`,
				confirmLabel: banMode === 'permanent' ? 'Permanently ban' : 'Apply timeout',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;

		setUpdatingBan(true);
		setError('');
		void (async () => {
			try {
				const response = await fetch('/api/admin/player-bans', {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ userId: player.id, expiresAtUnixMs }),
				});
				const body = await response.json().catch(() => null);
				if (!response.ok)
					throw new Error(apiMessage(body, 'Failed to apply the ban or timeout'));
				setBanPlayerId('');
				setTimeoutEndsAt('');
				await showAlert({
					title: banMode === 'permanent' ? 'Player banned' : 'Player timed out',
					tone: 'success',
					message: (
						<>
							<PlayerName name={player.minecraftUsername} color={player.color} /> was{' '}
							{banMode === 'permanent' ? 'permanently banned' : 'put in timeout'}.
						</>
					),
				});
				await load();
			} catch (caught) {
				await showFailure(
					'Could not apply the player restriction',
					caught,
					'The player’s access was not changed.',
				);
			} finally {
				setUpdatingBan(false);
			}
		})();
	}

	async function removePlayerBan(ban: ActivePlayerBan) {
		if (
			!(await confirm({
				title: 'Restore this player’s access?',
				message: `${ban.minecraftUsername} will be able to sign in to the website and join the Minecraft server again.`,
				confirmLabel: 'Restore access',
			}))
		)
			return;
		setUpdatingBan(true);
		setError('');
		try {
			const response = await fetch(`/api/admin/player-bans/${ban.userId}`, {
				method: 'DELETE',
			});
			const body = await response.json().catch(() => null);
			if (!response.ok)
				throw new Error(apiMessage(body, 'Failed to remove the ban or timeout'));
			setActivePlayerBans((current) =>
				current.filter((candidate) => candidate.userId !== ban.userId),
			);
			await showAlert({
				title: 'Player access restored',
				tone: 'success',
				message: (
					<>
						<PlayerName name={ban.minecraftUsername} color={ban.color} /> can sign in
						and join again.
					</>
				),
			});
		} catch (caught) {
			await showFailure(
				'Could not restore player access',
				caught,
				'The ban or timeout is still active.',
			);
		} finally {
			setUpdatingBan(false);
		}
	}

	async function loadMoreClaims() {
		setLoadingMore('claims');
		setError('');
		try {
			const response = await fetch(
				`/api/admin/claims?offset=${claims.length}&limit=${ADMIN_PAGE_SIZE}`,
				{ cache: 'no-store' },
			);
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to load more claims'));
			const result = apiBody<{ claims: AdminClaim[]; hasMore?: boolean }>(body);
			setClaims((current) => [...current, ...result.claims]);
			setClaimsHaveMore(Boolean(result.hasMore));
		} catch (caught) {
			await showFailure(
				'Could not load more claims',
				caught,
				'The next page of claims could not be loaded. Please try again.',
			);
		} finally {
			setLoadingMore(null);
		}
	}

	async function removeClaim(claim: AdminClaim) {
		if (
			!(await confirm({
				title: 'Delete this player claim?',
				message: `${claim.minecraftUsername}’s claim “${claim.name}” at ${claim.dimension} (${claim.chunkX}, ${claim.chunkZ}) will lose all protection. The player will not receive a refund.`,
				confirmLabel: 'Delete claim',
				confirmTone: 'danger',
				tone: 'danger',
			}))
		)
			return;
		setBusyClaimId(claim.id);
		setError('');
		try {
			const response = await fetch(`/api/admin/claims/${encodeURIComponent(claim.id)}`, {
				method: 'DELETE',
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to delete the claim'));
			setClaims((current) => current.filter((candidate) => candidate.id !== claim.id));
		} catch (caught) {
			await showFailure(
				'Could not delete the player claim',
				caught,
				'The claim is still protected.',
			);
		} finally {
			setBusyClaimId(null);
		}
	}
	return {
		activeSection,
		isSuperAdmin,
		...adminSectionData,
		...giftCodeAdministration,
		...countdownAdministration,
		whitelistEmail,
		setWhitelistEmail,
		responsibleUsername,
		setResponsibleUsername,
		banPlayerId,
		setBanPlayerId,
		banMode,
		setBanMode,
		timeoutEndsAt,
		setTimeoutEndsAt,
		busyPlayerId,
		busyClaimId,
		loadingMore,
		updatingWhitelist,
		updatingBan,
		dailyPlayerId,
		setDailyPlayerId,
		refreshingDailies,
		error,
		setMembership,
		setCommittee,
		refreshDailies,
		addWhitelistedEmail,
		removeWhitelistedEmail,
		applyPlayerBan,
		removePlayerBan,
		loadMoreClaims,
		removeClaim,
	};

	function showFailure(title: string, caught: unknown, fallback: string) {
		return showAlert({ title, message: errorMessage(caught, fallback), tone: 'danger' });
	}
}

export type AdminTabController = ReturnType<typeof useAdminTabController>;
