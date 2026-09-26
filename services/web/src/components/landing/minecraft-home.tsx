'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { LaunchCountdown, useLaunchLive } from '@/components/launch/launch-countdown';
import { MinecraftTitle } from '@/components/landing/minecraft-title';
import { useSiteAlert } from '@/components/site-alert';

const MEMBERSHIP_URL = 'https://www.theunionmmu.org/groups/26-3-minecraft-society';
const WHATSAPP_URL = 'https://chat.whatsapp.com/KgWzk2WBbIYLVkSrfVuwcz';

interface MinecraftHomeProps {
	panorama: { id: string; label: string };
	splash: string;
	imageVersion: string;
	imageReleaseUrl: string;
	discordUrl: string;
	instagramUrl: string;
}

export function MinecraftHome(props: MinecraftHomeProps) {
	const launchLive = useLaunchLive();
	const { showAlert } = useSiteAlert();
	const [communityExpanded, setCommunityExpanded] = useState(false);
	const communityLinks = useRef<HTMLDivElement>(null);

	return (
		<main className="minecraftHome">
			<PanoramaBackground panoramaId={props.panorama.id} />
			<div className="minecraftShade" aria-hidden="true" />

			<section className="minecraftMenu" aria-label="MMU Minecraft Society main menu">
				<MinecraftTitle splash={props.splash} />

				<nav className="minecraftButtons" aria-label="Main links">
					<Link
						className="minecraftButton playButton"
						href={launchLive ? '/play' : '/countdown'}
					>
						<span>Play Now</span>
						<LaunchCountdown compact />
					</Link>
					<div className={`communityButtons${communityExpanded ? ' expanded' : ''}`}>
						<button
							className="minecraftButton"
							type="button"
							aria-hidden={communityExpanded}
							tabIndex={communityExpanded ? -1 : 0}
							onMouseEnter={() => {
								setCommunityExpanded(true);
							}}
							onClick={() => {
								setCommunityExpanded(true);
								requestAnimationFrame(() => {
									communityLinks.current
										?.querySelector<HTMLElement>('a, button')
										?.focus();
								});
							}}
						>
							<span>Join the Community</span>
						</button>
						<div className="minecraftButtonRow communityButtonRow" ref={communityLinks}>
							<ExternalMenuLink
								href={props.discordUrl}
								label="Discord"
								missingMessage="The Discord portal is still being enchanted. Check back soon!"
								onMissing={showAlert}
							/>
							<a
								className="minecraftButton"
								href={WHATSAPP_URL}
								target="_blank"
								rel="noreferrer"
							>
								WhatsApp
							</a>
						</div>
					</div>
					<ExternalMenuLink
						href={props.instagramUrl}
						label="Instagram"
						missingMessage="The Instagram creeper ate the link. Check back soon!"
						onMissing={showAlert}
					/>
					<div className="minecraftButtonRow">
						<Link className="minecraftButton" href="/wordle">
							Wordle
						</Link>
						<a
							className="minecraftButton"
							href={MEMBERSHIP_URL}
							target="_blank"
							rel="noreferrer"
						>
							Membership
						</a>
					</div>
				</nav>
			</section>

			<a
				className="minecraftVersion"
				href={props.imageReleaseUrl || undefined}
				target={props.imageReleaseUrl ? '_blank' : undefined}
				rel={props.imageReleaseUrl ? 'noreferrer' : undefined}
			>
				{props.imageVersion}
			</a>
			<p className="minecraftLegal">
				Not an official Minecraft product. Not approved by or associated with Mojang or
				Microsoft.
			</p>
			<p className="panoramaCredit" aria-hidden="true">
				Panorama: {props.panorama.label}
			</p>
		</main>
	);
}

function ExternalMenuLink({
	href,
	label,
	missingMessage,
	onMissing,
}: {
	href: string;
	label: string;
	missingMessage: string;
	onMissing: (message: string) => Promise<unknown>;
}) {
	if (!href)
		return (
			<button
				className="minecraftButton"
				type="button"
				onClick={() => {
					void onMissing(missingMessage);
				}}
			>
				{label}
			</button>
		);
	return (
		<a className="minecraftButton" href={href} target="_blank" rel="noreferrer">
			{label}
		</a>
	);
}

function PanoramaBackground({ panoramaId }: { panoramaId: string }) {
	const cube = useRef<HTMLDivElement>(null);
	const view = useRef({ yaw: 0, pitch: -6, resumeAt: 0, pointerId: -1, x: 0, y: 0 });

	useEffect(() => {
		const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
		let frame: number;
		let previous = performance.now();
		const rotate = (now: number) => {
			const current = view.current;
			if (current.pointerId === -1 && now >= current.resumeAt && !reducedMotion.matches)
				current.yaw = (current.yaw + Math.min(now - previous, 100) * 0.003) % 360;
			if (cube.current)
				cube.current.style.transform = `rotateX(${current.pitch}deg) rotateY(${current.yaw}deg)`;
			previous = now;
			frame = requestAnimationFrame(rotate);
		};
		frame = requestAnimationFrame(rotate);
		return () => {
			cancelAnimationFrame(frame);
		};
	}, []);

	const stopDrag = (event: React.PointerEvent<HTMLDivElement>) => {
		if (view.current.pointerId !== event.pointerId) return;
		view.current.pointerId = -1;
		view.current.resumeAt = performance.now() + 1000;
	};

	return (
		<div
			className="panoramaBackground"
			aria-hidden="true"
			onPointerDown={(event) => {
				if (!event.isPrimary || event.button !== 0) return;
				const current = view.current;
				current.pointerId = event.pointerId;
				current.x = event.clientX;
				current.y = event.clientY;
				event.currentTarget.setPointerCapture(event.pointerId);
			}}
			onPointerMove={(event) => {
				const current = view.current;
				if (current.pointerId !== event.pointerId) return;
				current.yaw += (event.clientX - current.x) * 0.12;
				current.pitch = Math.max(
					-89,
					Math.min(89, current.pitch + (event.clientY - current.y) * 0.12),
				);
				current.x = event.clientX;
				current.y = event.clientY;
			}}
			onPointerUp={stopDrag}
			onPointerCancel={stopDrag}
			onLostPointerCapture={stopDrag}
		>
			<div className="panoramaCube" ref={cube}>
				{[0, 1, 2, 3, 4, 5].map((face) => (
					<div
						className={`panoramaFace panoramaFace${face}`}
						key={face}
						style={{
							backgroundImage: `url(/assets/landing/panoramas/${panoramaId}/panorama_${face}.webp)`,
						}}
					/>
				))}
			</div>
		</div>
	);
}
