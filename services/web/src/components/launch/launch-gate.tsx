'use client'

import Link from 'next/link'
import { useRef } from 'react'
import { LaunchCountdown } from '@/components/launch/launch-countdown'
import { SitePage } from '@/components/site-page'
import { LAUNCH_TIME_LABEL } from '@/lib/launch'

export function LaunchGate({ discordUrl, instagramUrl, background, splash }: { discordUrl: string; instagramUrl: string; background: string; splash: string }) {
	const clicks = useRef<number[]>([])

	function tryPreviewBypass() {
		const now = Date.now()
		clicks.current = [...clicks.current.filter((time) => now - time <= 3000), now]
		if (clicks.current.length >= 3) window.location.assign('/play/signin')
	}

	return <SitePage background={background} splash={splash} className="launchGatePage" contentClassName="launchGateContent">
		<section className="authCard launchGateCard">
			<h2>Server launch</h2>
			<p>The server is not open just yet. Thanks for being <button className="secretWord" type="button" onClick={tryPreviewBypass}>patient</button> with us.</p>
			<LaunchCountdown reloadAtZero />
			<p className="launchTimeLabel">{LAUNCH_TIME_LABEL}</p>
			<p className="launchFairNote">That&apos;s the evening of the first day of Freshers&apos; Fair.</p>
			<p>While you wait, join us on Discord or Instagram, or have a go at today&apos;s Wordle.</p>
			<div className="launchGateActions">
				<SocialLink href={discordUrl} label="Join Discord" />
				<SocialLink href={instagramUrl} label="Instagram" />
				<Link href="/wordle">Play Wordle</Link>
			</div>
		</section>
	</SitePage>
}

function SocialLink({ href, label }: { href: string; label: string }) {
	if (!href) return <button type="button" onClick={() => window.alert(`${label} link coming soon!`)}>{label}</button>
	return <a href={href} target="_blank" rel="noreferrer">{label}</a>
}
