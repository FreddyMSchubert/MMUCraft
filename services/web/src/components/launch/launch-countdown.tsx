'use client';

import { useEffect, useSyncExternalStore } from 'react';
import { DEFAULT_LAUNCH_TIME } from '@/lib/launch';

let launchTime = DEFAULT_LAUNCH_TIME;
let launchRequest: Promise<void> | null = null;
const launchListeners = new Set<() => void>();

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

export function useLaunchTime() {
	return useSyncExternalStore(subscribeToClock, readLaunchTime, () => DEFAULT_LAUNCH_TIME);
}

export function useLaunchLive() {
	const remainingSeconds = useLaunchRemainingSeconds();
	return remainingSeconds !== null && remainingSeconds <= 0;
}

function useLaunchRemainingSeconds() {
	return useSyncExternalStore(subscribeToClock, readRemainingSeconds, () => null);
}

function subscribeToClock(callback: () => void) {
	launchListeners.add(callback);
	void refreshLaunchTime();
	const timer = window.setInterval(callback, 1000);
	const refreshTimer = window.setInterval(() => void refreshLaunchTime(), 30_000);
	window.addEventListener('launch-settings-change', refreshLaunchTime);
	return () => {
		launchListeners.delete(callback);
		window.clearInterval(timer);
		window.clearInterval(refreshTimer);
		window.removeEventListener('launch-settings-change', refreshLaunchTime);
	};
}

function readRemainingSeconds() {
	return Math.max(0, Math.ceil((launchTime - Date.now()) / 1000));
}

function readLaunchTime() {
	return launchTime;
}

function refreshLaunchTime() {
	if (launchRequest) return launchRequest;
	launchRequest = fetch('/api/launch', { cache: 'no-store' })
		.then(async (response) => {
			if (!response.ok) return;
			const body = (await response.json()) as { launchAtUnixMs?: unknown };
			if (typeof body.launchAtUnixMs !== 'number' || !Number.isFinite(body.launchAtUnixMs))
				return;
			if (launchTime === body.launchAtUnixMs) return;
			launchTime = body.launchAtUnixMs;
			launchListeners.forEach((listener) => {
				listener();
			});
		})
		.catch(() => undefined)
		.finally(() => {
			launchRequest = null;
		});
	return launchRequest;
}
