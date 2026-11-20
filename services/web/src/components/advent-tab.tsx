'use client';

import { useEffect, useState } from 'react';
import { AdventGrid, type MinedBlock } from '@/components/advent-grid';
import { DabloonAmount } from '@/components/dabloon-amount';
import { useSiteAlert } from '@/components/site-alert';
import type { AdventLayout } from '@/lib/advent';

interface CalendarState {
	layout: AdventLayout;
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
			setError(
				cause instanceof Error ? cause.message : 'The present could not be delivered.',
			);
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
			setError(
				cause instanceof Error ? cause.message : 'The hidden present could not be claimed.',
			);
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

	return (
		<section className="adventPanel">
			<div className="adventHeader">
				<h3>Advent Calendar</h3>
				<div className="adventStreak">
					<span>Christmas streak</span>
					<strong>
						<DabloonAmount amount={calendar?.streakRate ?? 0} />
					</strong>
					<small>
						Resets if you miss a day. Earned when you receive your daily gift.
					</small>
				</div>
			</div>
			{error && (
				<p className="authError" role="alert">
					{error}
				</p>
			)}
			{notice && (
				<p className="adventNotice" role="status">
					{notice}
				</p>
			)}
			{calendar ? (
				<AdventGrid
					key={calendar.today}
					layout={calendar.layout}
					today={calendar.today}
					claimedDays={calendar.claimedDays}
					secretClaimed={calendar.secretClaimed}
					onGift={(day) => void claim(day)}
					onSecretGift={(x, y) => void claimSecret(x, y)}
					onFutureDay={futureDay}
					onFullyMined={mine}
				/>
			) : (
				!error && <p>Loading the calendar...</p>
			)}
		</section>
	);
}
