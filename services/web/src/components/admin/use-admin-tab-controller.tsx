'use client';

import { type SyntheticEvent, useState } from 'react';
import type { ReactNode } from 'react';
import { PlayerName } from '@/components/player-name';
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
	const activeSection = normalizeAdminSection(section);
	const [error, setError] = useState('');
	const [message, setMessage] = useState<ReactNode>('');
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
		setError,
		setMessage,
	});
	const countdownAdministration = useCountdownAdministration({
		setCountdowns,
		reload: load,
		setError,
		setMessage,
	});

	async function setMembership(player: AdminPlayer, isMember: boolean) {
		const action = isMember ? 'mark as a society member' : 'remove society membership from';
		if (!window.confirm(`Are you sure you want to ${action} this player?`)) return;
		if (
			player.isExternal &&
			!window.confirm(`This player is external. Are you sure you want to ${action} them?`)
		)
			return;

		setBusyPlayerId(player.id);
		setError('');
		setMessage('');
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
			setMessage(
				<>
					<PlayerName name={player.minecraftUsername} color={player.color} /> is{' '}
					{isMember ? 'now' : 'no longer'} marked as a member.
				</>,
			);
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to update membership'));
		} finally {
			setBusyPlayerId(null);
		}
	}

	async function setCommittee(player: AdminPlayer, isCommittee: boolean) {
		const action = isCommittee ? 'give committee access to' : 'remove committee access from';
		if (!window.confirm(`Are you sure you want to ${action} this player?`)) return;
		if (
			player.isExternal &&
			!window.confirm(`This player is external. Are you sure you want to ${action} them?`)
		)
			return;
		setBusyPlayerId(player.id);
		setError('');
		setMessage('');
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
			setMessage(
				<>
					<PlayerName name={player.minecraftUsername} color={player.color} />{' '}
					{isCommittee ? 'now has' : 'no longer has'} committee access.
				</>,
			);
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to update committee access'));
		} finally {
			setBusyPlayerId(null);
		}
	}

	async function refreshDailies(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		const player = players.find((candidate) => candidate.id === Number(dailyPlayerId));
		if (
			!player ||
			!window.confirm(
				`Regenerate today's uncompleted dailies for ${player.minecraftUsername}?`,
			)
		)
			return;

		setRefreshingDailies(true);
		setError('');
		setMessage('');
		try {
			const response = await fetch(`/api/admin/dailies/${player.id}/refresh`, {
				method: 'POST',
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to regenerate dailies'));
			setMessage(apiMessage(body, 'Dailies regenerated.'));
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to regenerate dailies'));
		} finally {
			setRefreshingDailies(false);
		}
	}

	function addWhitelistedEmail(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		const responsiblePlayer = players.find(
			(player) =>
				player.minecraftUsername.localeCompare(responsibleUsername, 'en', {
					sensitivity: 'base',
				}) === 0 && !player.isExternal,
		);
		if (!responsiblePlayer) {
			setError('Select a responsible user from the username list');
			return;
		}
		const invitePrice = responsiblePlayer.isMember ? 150 : 250;
		if (
			!window.confirm(
				`Charge the selected player ${invitePrice} dabloons to invite ${whitelistEmail}? They must be online.`,
			)
		)
			return;
		setUpdatingWhitelist(true);
		setError('');
		setMessage('');

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
				setMessage(
					<>
						{result.email} can now sign up.{' '}
						<PlayerName
							name={responsiblePlayer.minecraftUsername}
							color={responsiblePlayer.color}
						/>{' '}
						paid {result.priceDabloons} dabloons and has {result.balanceDabloons} left.
					</>,
				);
				await load();
			} catch (caught) {
				setError(errorMessage(caught, 'Failed to whitelist the email address'));
			} finally {
				setUpdatingWhitelist(false);
			}
		})();
	}

	async function removeWhitelistedEmail(email: string) {
		if (!window.confirm(`Remove ${email} from the signup whitelist?`)) return;
		setUpdatingWhitelist(true);
		setError('');
		setMessage('');
		try {
			const response = await fetch(
				`/api/admin/email-whitelist/${encodeURIComponent(email)}`,
				{ method: 'DELETE' },
			);
			const body = await response.json().catch(() => null);
			if (!response.ok)
				throw new Error(apiMessage(body, 'Failed to remove the email address'));
			setWhitelistedEmails((current) => current.filter((entry) => entry.email !== email));
			setMessage(`${email} was removed from the signup whitelist.`);
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to remove the email address'));
		} finally {
			setUpdatingWhitelist(false);
		}
	}

	function applyPlayerBan(event: SyntheticEvent<HTMLFormElement>) {
		event.preventDefault();
		const player = players.find((candidate) => candidate.id === Number(banPlayerId));
		if (!player) {
			setError('Select a player');
			return;
		}
		const expiresAtUnixMs = banMode === 'temporary' ? new Date(timeoutEndsAt).getTime() : null;
		if (
			expiresAtUnixMs !== null &&
			(!Number.isFinite(expiresAtUnixMs) || expiresAtUnixMs <= Date.now())
		) {
			setError('Select a timeout date and time in the future');
			return;
		}

		let restriction = 'permanently ban';
		if (banMode === 'temporary') {
			if (expiresAtUnixMs === null) return;
			restriction = `put in timeout until ${formatDateTime(expiresAtUnixMs)}`;
		}
		if (
			!window.confirm(
				`Warning 1 of 3: ${player.minecraftUsername} will be signed out everywhere and unable to sign in. Continue?`,
			)
		)
			return;
		if (
			!window.confirm(
				`Warning 2 of 3: ${player.minecraftUsername} will be blacklisted from Minecraft. Check that you selected the correct player and any related external accounts. Continue?`,
			)
		)
			return;
		if (
			!window.confirm(
				`Warning 3 of 3: Apply this action and ${restriction} ${player.minecraftUsername}?`,
			)
		)
			return;

		setUpdatingBan(true);
		setError('');
		setMessage('');
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
				const result = apiBody<{ minecraftSynchronized: boolean }>(body);

				setBanPlayerId('');
				setTimeoutEndsAt('');
				setMessage(
					<>
						<PlayerName name={player.minecraftUsername} color={player.color} /> was{' '}
						{banMode === 'permanent' ? 'permanently banned' : 'put in timeout'}.
						{result.minecraftSynchronized
							? ''
							: ' Minecraft will synchronize when the player next attempts to join.'}
					</>,
				);
				await load();
			} catch (caught) {
				setError(errorMessage(caught, 'Failed to apply the ban or timeout'));
			} finally {
				setUpdatingBan(false);
			}
		})();
	}

	async function removePlayerBan(ban: ActivePlayerBan) {
		if (!window.confirm(`Remove the ban or timeout for ${ban.minecraftUsername}?`)) return;
		setUpdatingBan(true);
		setError('');
		setMessage('');
		try {
			const response = await fetch(`/api/admin/player-bans/${ban.userId}`, {
				method: 'DELETE',
			});
			const body = await response.json().catch(() => null);
			if (!response.ok)
				throw new Error(apiMessage(body, 'Failed to remove the ban or timeout'));
			const result = apiBody<{ minecraftSynchronized: boolean }>(body);
			setActivePlayerBans((current) =>
				current.filter((candidate) => candidate.userId !== ban.userId),
			);
			setMessage(
				<>
					<PlayerName name={ban.minecraftUsername} color={ban.color} /> can sign in and
					join again.
					{result.minecraftSynchronized
						? ''
						: ' Minecraft will synchronize when the player next attempts to join.'}
				</>,
			);
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to remove the ban or timeout'));
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
			setError(errorMessage(caught, 'Failed to load more claims'));
		} finally {
			setLoadingMore(null);
		}
	}

	async function removeClaim(claim: AdminClaim) {
		if (
			!window.confirm(
				`Delete ${claim.minecraftUsername}'s claim "${claim.name}" at ${claim.dimension} (${claim.chunkX}, ${claim.chunkZ})?`,
			)
		)
			return;
		setBusyClaimId(claim.id);
		setError('');
		setMessage('');
		try {
			const response = await fetch(`/api/admin/claims/${encodeURIComponent(claim.id)}`, {
				method: 'DELETE',
			});
			const body = await response.json().catch(() => null);
			if (!response.ok) throw new Error(apiMessage(body, 'Failed to delete the claim'));
			setClaims((current) => current.filter((candidate) => candidate.id !== claim.id));
			setMessage(
				<>
					Deleted <PlayerName name={claim.minecraftUsername} color={claim.color} />
					&apos;s claim &quot;{claim.name}&quot;.
				</>,
			);
		} catch (caught) {
			setError(errorMessage(caught, 'Failed to delete the claim'));
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
		message,
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
}

export type AdminTabController = ReturnType<typeof useAdminTabController>;
