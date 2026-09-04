'use client';

import { useCallback, useEffect, useState } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import type { Countdown } from '@/components/dynamic-countdowns';
import { errorMessage, fetchAdmin } from './admin-api';
import {
	ADMIN_PAGE_SIZE,
	type ActivePlayerBan,
	type AdminClaim,
	type AdminPlayer,
	type AdminSection,
	type GiftCode,
	type WhitelistedEmail,
} from './admin-data.types';

export function useAdminSectionData(
	activeSection: AdminSection,
	setError: Dispatch<SetStateAction<string>>,
) {
	const [players, setPlayers] = useState<AdminPlayer[]>([]);
	const [countdowns, setCountdowns] = useState<Countdown[]>([]);
	const [giftCodes, setGiftCodes] = useState<GiftCode[]>([]);
	const [claims, setClaims] = useState<AdminClaim[]>([]);
	const [claimsHaveMore, setClaimsHaveMore] = useState(false);
	const [whitelistedEmails, setWhitelistedEmails] = useState<WhitelistedEmail[]>([]);
	const [activePlayerBans, setActivePlayerBans] = useState<ActivePlayerBan[]>([]);

	const load = useCallback(async () => {
		const jobs: Promise<void>[] = [];
		if (
			['members', 'claims', 'whitelist', 'bans', 'dailies', 'commands'].includes(
				activeSection,
			)
		) {
			jobs.push(
				fetchAdmin<{ players: AdminPlayer[] }>(
					'/api/admin/players',
					'Failed to load the member list',
				).then((body) => {
					setPlayers(body.players);
				}),
			);
		}
		if (activeSection === 'gifts') {
			jobs.push(
				fetchAdmin<{ giftCodes: GiftCode[] }>(
					'/api/admin/gift-codes',
					'Failed to load gift codes',
				).then((body) => {
					setGiftCodes(body.giftCodes);
				}),
			);
		}
		if (activeSection === 'countdowns') {
			jobs.push(
				fetchAdmin<{ countdowns: Countdown[] }>(
					'/api/admin/countdowns',
					'Failed to load countdowns',
				).then((body) => {
					setCountdowns(body.countdowns);
				}),
			);
		}
		if (activeSection === 'claims') {
			jobs.push(
				fetchAdmin<{ claims: AdminClaim[]; hasMore?: boolean }>(
					`/api/admin/claims?limit=${ADMIN_PAGE_SIZE}`,
					'Failed to load claims',
				).then((body) => {
					setClaims(body.claims);
					setClaimsHaveMore(Boolean(body.hasMore));
				}),
			);
		}
		if (activeSection === 'whitelist') {
			jobs.push(
				fetchAdmin<{ entries: WhitelistedEmail[] }>(
					'/api/admin/email-whitelist',
					'Failed to load the email whitelist',
				).then((body) => {
					setWhitelistedEmails(body.entries);
				}),
			);
		}
		if (activeSection === 'bans') {
			jobs.push(
				fetchAdmin<{ bans: ActivePlayerBan[] }>(
					'/api/admin/player-bans',
					'Failed to load player bans',
				).then((body) => {
					setActivePlayerBans(body.bans);
				}),
			);
		}
		await Promise.all(jobs);
	}, [activeSection]);

	useEffect(() => {
		let cancelled = false;
		void load().catch((caught: unknown) => {
			if (!cancelled) setError(errorMessage(caught, 'Failed to load admin tools'));
		});
		return () => {
			cancelled = true;
		};
	}, [load, setError]);

	return {
		players,
		setPlayers,
		countdowns,
		setCountdowns,
		giftCodes,
		claims,
		setClaims,
		claimsHaveMore,
		setClaimsHaveMore,
		whitelistedEmails,
		setWhitelistedEmails,
		activePlayerBans,
		setActivePlayerBans,
		load,
	};
}
