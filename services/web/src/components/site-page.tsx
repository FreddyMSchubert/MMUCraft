import type { ReactNode } from 'react';
import { MinecraftTitle } from '@/components/landing/minecraft-title';

export function SitePage({
	children,
	className = '',
	contentClassName = '',
	headerActions,
	overlay,
	background,
	splash,
}: {
	children: ReactNode;
	className?: string;
	contentClassName?: string;
	headerActions?: ReactNode;
	overlay?: ReactNode;
	background: string;
	splash: string;
}) {
	return (
		<main className={`page ${className}`.trim()}>
			<div
				className="siteBackground"
				style={{ backgroundImage: `url(${background})` }}
				aria-hidden="true"
			/>
			{overlay}
			<div className={`content ${contentClassName}`.trim()}>
				<header className="siteHeader">
					<MinecraftTitle className="siteMinecraftTitle" href="/" splash={splash} />
					{headerActions}
				</header>
				{children}
			</div>
		</main>
	);
}
