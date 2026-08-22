'use client';

import { useCallback, useEffect, useState } from 'react';
import type { CSSProperties } from 'react';

export interface Countdown {
	id: number;
	heading: string;
	description: string;
	targetAtUnixMs: number;
	visibleUntilUnixMs: number;
	headingColor: string;
	descriptionColor: string;
	backgroundColor: string;
	backgroundAlpha: number;
	backgroundImageUrl: string | null;
}

export function DynamicCountdowns() {
	const [countdowns, setCountdowns] = useState<Countdown[]>([]);
	const [now, setNow] = useState(() => Date.now());
	const load = useCallback(async () => {
		const response = await fetch('/api/countdowns', { cache: 'no-store' });
		if (!response.ok) return;
		const body = (await response.json()) as { countdowns: Countdown[] };
		setCountdowns(body.countdowns);
	}, []);

	useEffect(() => {
		const initialLoad = window.setTimeout(() => void load(), 0);
		const timer = window.setInterval(() => {
			setNow(Date.now());
		}, 1000);
		window.addEventListener('countdowns-change', load);
		return () => {
			window.clearTimeout(initialLoad);
			window.clearInterval(timer);
			window.removeEventListener('countdowns-change', load);
		};
	}, [load]);

	const visible = countdowns.filter((countdown) => countdown.visibleUntilUnixMs > now);
	if (visible.length === 0) return null;

	return (
		<section
			className={`countdownGrid countdownGrid-${visible.length}`}
			aria-label="Upcoming events"
		>
			{visible.map((countdown) => {
				const finished = countdown.targetAtUnixMs <= now;
				const style = {
					'--countdown-heading-color': countdown.headingColor,
					'--countdown-description-color': countdown.descriptionColor,
					'--countdown-background': hexWithAlpha(
						countdown.backgroundColor,
						countdown.backgroundAlpha,
					),
					'--countdown-image': countdown.backgroundImageUrl
						? `url(${JSON.stringify(countdown.backgroundImageUrl)})`
						: 'none',
				} as CSSProperties;
				return (
					<article
						className={`countdownCard${finished ? ' countdownCard-now' : ''}`}
						key={countdown.id}
						style={style}
					>
						<h2>{countdown.heading}</h2>
						{finished ? (
							<strong className="countdownNow" role="status">
								NOW!
							</strong>
						) : (
							<time
								className="countdownTime"
								dateTime={new Date(countdown.targetAtUnixMs).toISOString()}
							>
								{formatRemaining(countdown.targetAtUnixMs - now)}
								<small>({formatEndTime(countdown.targetAtUnixMs)})</small>
							</time>
						)}
						<p>{countdown.description}</p>
					</article>
				);
			})}
		</section>
	);
}

function formatEndTime(timestamp: number) {
	return new Intl.DateTimeFormat('en-GB', {
		dateStyle: 'full',
		timeStyle: 'short',
		timeZone: 'Europe/London',
	}).format(timestamp);
}

function hexWithAlpha(hex: string, alpha: number) {
	return `${hex}${Math.round(alpha * 2.55)
		.toString(16)
		.padStart(2, '0')}`;
}

function formatRemaining(milliseconds: number) {
	const seconds = Math.max(0, Math.ceil(milliseconds / 1000));
	const days = Math.floor(seconds / 86_400);
	const hours = Math.floor((seconds % 86_400) / 3_600);
	const minutes = Math.floor((seconds % 3_600) / 60);
	const remainder = seconds % 60;
	return `${days ? `${days}d ` : ''}${String(hours).padStart(2, '0')}h ${String(minutes).padStart(2, '0')}m ${String(remainder).padStart(2, '0')}s`;
}
