'use client';

import { useEffect, useSyncExternalStore } from 'react';
import { LAUNCH_TIME } from '@/lib/launch';

function splitDuration(milliseconds: number) {
	const seconds = Math.max(0, Math.floor(milliseconds / 1000));
	return {
		days: Math.floor(seconds / 86400),
		hours: Math.floor((seconds % 86400) / 3600),
		minutes: Math.floor((seconds % 3600) / 60),
		seconds: seconds % 60,
	};
}

export function LaunchCountdown({
	compact = false,
	reloadAtZero = false,
}: {
	compact?: boolean;
	reloadAtZero?: boolean;
}) {
	const remainingSeconds = useLaunchRemainingSeconds();

	useEffect(() => {
		if (remainingSeconds !== null && remainingSeconds <= 0 && reloadAtZero)
			window.location.reload();
	}, [reloadAtZero, remainingSeconds]);

	if (remainingSeconds !== null && remainingSeconds <= 0)
		return <span className={compact ? 'menuCountdown' : 'launchLive'}>Server is live!</span>;

	const parts = splitDuration((remainingSeconds ?? 0) * 1000);
	if (compact) {
		return (
			<span
				className="menuCountdown"
				aria-label={`Launch countdown: ${parts.days} days, ${parts.hours} hours, ${parts.minutes} minutes, ${parts.seconds} seconds`}
			>
				{parts.days}d {String(parts.hours).padStart(2, '0')}:
				{String(parts.minutes).padStart(2, '0')}:{String(parts.seconds).padStart(2, '0')}
			</span>
		);
	}

	return (
		<div className="launchCountdown" aria-label="Countdown to server launch">
			{Object.entries(parts).map(([label, value]) => (
				<div className="launchCountdownSegment" key={label}>
					<strong>
						{label === 'days'
							? String(value).padStart(3, '0')
							: String(value).padStart(2, '0')}
					</strong>
					<span>{label}</span>
				</div>
			))}
		</div>
	);
}

export function useLaunchLive() {
	const remainingSeconds = useLaunchRemainingSeconds();
	return remainingSeconds !== null && remainingSeconds <= 0;
}

function useLaunchRemainingSeconds() {
	return useSyncExternalStore(subscribeToClock, readRemainingSeconds, () => null);
}

function subscribeToClock(callback: () => void) {
	const timer = window.setInterval(callback, 1000);
	return () => {
		window.clearInterval(timer);
	};
}

function readRemainingSeconds() {
	return Math.max(0, Math.ceil((LAUNCH_TIME - Date.now()) / 1000));
}
