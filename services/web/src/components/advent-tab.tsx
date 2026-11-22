'use client';

import { useEffect, useState } from 'react';
import { AdventCalendarView } from '@/components/advent-calendar-view';
import type { MinedBlock } from '@/components/advent-grid';
import { useSiteAlert } from '@/components/site-alert';
import type { AdventLayout } from '@/lib/advent';

interface CalendarState {
	layout: AdventLayout;
	revision: number;
	year: number;
	today: number;
	claimedDays: number[];
	streakRate: number;
	secretClaimed: boolean;
}

export function AdventTab() {
	const [calendar, setCalendar] = useState<CalendarState | null>(null);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');
	const [busy, setBusy] = useState(false);
	const { showAlert } = useSiteAlert();

	useEffect(() => {
		let cancelled = false;
		function refresh() {
			void fetch('/api/advent', { cache: 'no-store' })
				.then(async (response) => {
					if (!response.ok) throw new Error('The Advent calendar is unavailable.');
					return (await response.json()) as CalendarState;
				})
				.then((value) => {
					if (!cancelled) setCalendar(value);
				})
				.catch((cause: unknown) => {
					if (!cancelled)
						setError(
							cause instanceof Error ? cause.message : 'Could not load the calendar.',
						);
				});
		}
		refresh();
		const timer = window.setInterval(refresh, 60_000);
		return () => {
			cancelled = true;
			window.clearInterval(timer);
		};
	}, []);

	async function claim(day: number) {
		if (!calendar || busy) return;
		setBusy(true);
		setError('');
		setNotice('');
		try {
			const response = await fetch(`/api/advent/${day}/claim`, { method: 'POST' });
			const data = (await response.json()) as {
				message?: string;
				amountDabloons?: number;
				streakRate?: number;
			};
			if (!response.ok)
				throw new Error(data.message ?? 'The present could not be delivered.');
			setCalendar({
				...calendar,
				claimedDays: [...calendar.claimedDays, day],
				streakRate: data.streakRate ?? calendar.streakRate,
			});
			const increase = calendar.layout.days[day - 1].streakIncrease;
			setNotice(
				increase && data.amountDabloons ? '' : (data.message ?? `Day ${day} delivered.`),
			);
			if (increase && data.amountDabloons)
				void showAlert({
					title: `Christmas streak +${increase}`,
					message: `${data.amountDabloons} Dabloons paid. Daily rate: ${data.streakRate ?? calendar.streakRate}.`,
					tone: 'success',
				});
		} catch (cause) {
			const message =
				cause instanceof Error ? cause.message : 'The present could not be delivered.';
			setError(message);
			void showAlert({ title: 'Present unavailable', message, tone: 'danger' });
		} finally {
			setBusy(false);
		}
	}

	async function mine(x: number, y: number): Promise<MinedBlock> {
		try {
			const response = await fetch('/api/advent/mine', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ x, y }),
			});
			const result = (await response.json()) as MinedBlock & { message?: string };
			if (!response.ok)
				throw new Error(result.message ?? 'Could not inspect the mined block.');
			return result;
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : 'Could not inspect the mined block.');
			return { secret: false };
		}
	}

	async function claimSecret(x: number, y: number) {
		if (!calendar || busy) return;
		setBusy(true);
		setError('');
		try {
			const response = await fetch('/api/advent/secret/claim', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ x, y }),
			});
			const result = (await response.json()) as { message?: string; amountDabloons?: number };
			if (!response.ok)
				throw new Error(result.message ?? 'The hidden present could not be claimed.');
			setCalendar({ ...calendar, secretClaimed: true });
			void showAlert({
				title: 'Hidden present found',
				message:
					result.message ?? `You received ${result.amountDabloons} Dabloons in game.`,
				tone: 'success',
			});
		} catch (cause) {
			const message =
				cause instanceof Error ? cause.message : 'The hidden present could not be claimed.';
			setError(message);
			void showAlert({ title: 'Hidden present unavailable', message, tone: 'danger' });
		} finally {
			setBusy(false);
		}
	}

	async function futureDay(day: number) {
		try {
			const response = await fetch(`/api/advent/${day}/cheat`, { method: 'POST' });
			const result = (await response.json()) as { penalized?: boolean; message?: string };
			if (response.ok && result.penalized && result.message)
				void showAlert({
					title: 'Caught mining ahead',
					message: result.message,
					tone: 'danger',
				});
		} catch {
			// A failed request never displays a penalty that was not confirmed.
		}
	}

	if (!calendar) return <p>{error || 'Loading the calendar...'}</p>;
	return (
		<AdventCalendarView
			streakRate={calendar.streakRate}
			error={error}
			notice={notice}
			gridKey={`${calendar.today}-${calendar.revision}`}
			grid={{
				layout: calendar.layout,
				today: calendar.today,
				claimedDays: calendar.claimedDays,
				secretClaimed: calendar.secretClaimed,
				onGift: (day) => void claim(day),
				onSecretGift: (x, y) => void claimSecret(x, y),
				onFutureDay: futureDay,
				onFullyMined: mine,
			}}
		/>
	);
}
