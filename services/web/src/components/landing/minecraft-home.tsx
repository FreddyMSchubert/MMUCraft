'use client'

import Link from 'next/link'
import { LaunchCountdown } from '@/components/launch/launch-countdown'
import { MinecraftTitle } from '@/components/landing/minecraft-title'

const MEMBERSHIP_URL = 'https://www.theunionmmu.org/groups/26-2-minecraft-society'

interface MinecraftHomeProps {
	panorama: { id: string; label: string }
	splash: string
	imageVersion: string
	discordUrl: string
	instagramUrl: string
}

export function MinecraftHome(props: MinecraftHomeProps) {
	return <main className="minecraftHome">
		<PanoramaBackground panoramaId={props.panorama.id} />
		<div className="minecraftShade" aria-hidden="true" />

		<section className="minecraftMenu" aria-label="MMU Minecraft Society main menu">
			<MinecraftTitle splash={props.splash} />

			<nav className="minecraftButtons" aria-label="Main links">
				<Link className="minecraftButton playButton" href="/countdown">
					<span>Play Now</span>
					<LaunchCountdown compact />
				</Link>
				<ExternalMenuLink href={props.discordUrl} label="Discord" missingMessage="The Discord portal is still being enchanted. Check back soon!" />
				<ExternalMenuLink href={props.instagramUrl} label="Instagram" missingMessage="The Instagram creeper ate the link. Check back soon!" />
				<div className="minecraftButtonRow">
					<Link className="minecraftButton" href="/wordle">Wordle</Link>
					<a className="minecraftButton" href={MEMBERSHIP_URL} target="_blank" rel="noreferrer">Membership</a>
				</div>
			</nav>
		</section>

		<p className="minecraftVersion">{props.imageVersion}</p>
		<p className="minecraftLegal">Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.</p>
		<p className="panoramaCredit" aria-hidden="true">Panorama: {props.panorama.label}</p>
	</main>
}

function ExternalMenuLink({ href, label, missingMessage }: { href: string; label: string; missingMessage: string }) {
	if (!href) return <button className="minecraftButton" type="button" onClick={() => window.alert(missingMessage)}>{label}</button>
	return <a className="minecraftButton" href={href} target="_blank" rel="noreferrer">{label}</a>
}

function PanoramaBackground({ panoramaId }: { panoramaId: string }) {
	return <div className="panoramaBackground" aria-hidden="true">
		<div className="panoramaCube">
			{[0, 1, 2, 3, 4, 5].map((face) => <div
				className={`panoramaFace panoramaFace${face}`}
				key={face}
				style={{ backgroundImage: `url(/assets/landing/panoramas/${panoramaId}/panorama_${face}.webp)` }}
			/>)}
		</div>
	</div>
}
