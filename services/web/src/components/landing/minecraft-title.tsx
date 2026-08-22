import Image from 'next/image';
import Link from 'next/link';
import type { CSSProperties } from 'react';

export function MinecraftTitle({
	splash,
	href,
	className = '',
}: {
	splash: string;
	href?: string;
	className?: string;
}) {
	const title = (
		<>
			<Image
				className="minecraftLogo"
				src="/assets/landing/logo.png"
				width={2048}
				height={372}
				priority
				alt="MMU Minecraft Society"
			/>
			<span
				className="minecraftSplash"
				aria-hidden="true"
				style={
					{
						'--splash-scale': Math.max(0.72, Math.min(1, 44 / splash.length)),
					} as CSSProperties
				}
			>
				{splash}
			</span>
		</>
	);

	return (
		<h1 className={`minecraftLogoWrap ${className}`.trim()}>
			{href ? (
				<Link className="minecraftTitleLink" href={href}>
					{title}
				</Link>
			) : (
				title
			)}
		</h1>
	);
}
