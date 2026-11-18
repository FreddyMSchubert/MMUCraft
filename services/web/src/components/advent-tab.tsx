'use client';

import { useEffect, useState } from 'react';
import { AdventGrid } from '@/components/advent-grid';
import { DabloonAmount } from '@/components/dabloon-amount';
import type { AdventLayout } from '@/lib/advent';

interface CalendarState {
	layout: AdventLayout;
	year: number;
	today: number;
	claimedDays: number[];
	streakRate: number;
}

export function AdventTab() {
	const [calendar, setCalendar] = useState<CalendarState | null>(null);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');
	const [busy, setBusy] = useState(false);

	useEffect(() => {
		let cancelled = false;
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
		return () => {
			cancelled = true;
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
				`${data.message ?? `Day ${day} delivered.`}${data.amountDabloons ? ` Your streak paid ${data.amountDabloons} Dabloons${increase ? ` and grew by ${increase}` : ''}.` : ''}`,
			);
		} catch (cause) {
			setError(
				cause instanceof Error ? cause.message : 'The present could not be delivered.',
			);
		} finally {
			setBusy(false);
		}
	}

	return (
		<section className="adventPanel">
			<div className="adventHeader">
				<div>
					<p className="adventEyebrow">MMUCraft · December 2026</p>
					<h1>Advent Calendar</h1>
					<p>
						Find a numbered block. Open its door, then take the present while you are in
						game.
					</p>
				</div>
				<div className="adventStreak">
					<span>Christmas streak</span>
					<strong>
						<DabloonAmount amount={calendar?.streakRate ?? 0} />
					</strong>
					<small>
						Earn this rate for each day you open on time. A missed day resets it.
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
					layout={calendar.layout}
					today={calendar.today}
					claimedDays={calendar.claimedDays}
					onGift={(day) => void claim(day)}
				/>
			) : (
				!error && <p>Loading the calendar...</p>
			)}
			<p className="adventFootnote">
				One present per day. A late present gives its items, but it does not restore streak
				money.
			</p>
		</section>
	);
}
