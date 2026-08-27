'use client';

import Link from 'next/link';
import { useEffect, useRef } from 'react';
import { Fireworks } from 'fireworks-js';
import { LaunchCountdown, useLaunchLive } from '@/components/launch/launch-countdown';
import { SitePage } from '@/components/site-page';
import { useSiteAlert } from '@/components/site-alert';
import { LAUNCH_TIME_LABEL } from '@/lib/launch';

export function LaunchGate({
	discordUrl,
	instagramUrl,
	background,
	splash,
}: {
	discordUrl: string;
	instagramUrl: string;
	background: string;
	splash: string;
}) {
	const clicks = useRef<number[]>([]);
	const fireworksStage = useRef<HTMLDivElement>(null);
	const launchLive = useLaunchLive();
	const { showAlert } = useSiteAlert();

	useEffect(() => {
		const stage = fireworksStage.current;
		if (!launchLive || !stage) return;
		const fireworks = new Fireworks(stage, {
			autoresize: true,
			opacity: 0.82,
			particles: 260,
			explosion: 14,
			intensity: 96,
			hue: { min: 0, max: 360 },
		});
		fireworks.start();
		return () => {
			fireworks.stop(true);
			fireworks.clear();
		};
	}, [launchLive]);

	function tryPreviewBypass() {
		const now = Date.now();
		clicks.current = [...clicks.current.filter((time) => now - time <= 3000), now];
		if (clicks.current.length >= 3) window.location.assign('/play');
	}

	return (
		<SitePage
			background={background}
			splash={splash}
			className="launchGatePage"
			contentClassName="launchGateContent"
			overlay={
				launchLive ? (
					<div className="fireworksStage" ref={fireworksStage} aria-hidden="true" />
				) : undefined
			}
		>
			<section className="authCard launchGateCard">
				{launchLive ? (
					<>
						<h2>We&apos;re live!</h2>
						<p>The server is open. Create your account and join us now.</p>
						<Link className="launchJoinNow" href="/play">
							Join now
						</Link>
					</>
				) : (
					<>
						<h2>Server launch</h2>
						<p>
							The server is not open just yet. Thanks for being{' '}
							<button className="secretWord" type="button" onClick={tryPreviewBypass}>
								patient
							</button>{' '}
							with us.
						</p>
						<LaunchCountdown />
						<p className="launchTimeLabel">{LAUNCH_TIME_LABEL}</p>
						<p className="launchFairNote">
							That&apos;s the evening of the first day of Freshers&apos; Fair.
						</p>
						<p>
							While you wait, join us on Discord or Instagram, or have a go at
							today&apos;s Wordle.
						</p>
						<div className="launchGateActions">
							<SocialLink
								href={discordUrl}
								label="Join Discord"
								onMissing={showAlert}
							/>
							<SocialLink
								href={instagramUrl}
								label="Instagram"
								onMissing={showAlert}
							/>
							<Link href="/wordle">Play Wordle</Link>
						</div>
					</>
				)}
			</section>
		</SitePage>
	);
}

function SocialLink({
	href,
	label,
	onMissing,
}: {
	href: string;
	label: string;
	onMissing: (message: string) => Promise<unknown>;
}) {
	if (!href)
		return (
			<button
				type="button"
				onClick={() => {
					void onMissing(`${label} link coming soon!`);
				}}
			>
				{label}
			</button>
		);
	return (
		<a href={href} target="_blank" rel="noreferrer">
			{label}
		</a>
	);
}
