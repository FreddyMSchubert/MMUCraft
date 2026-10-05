import type { Metadata } from 'next';
import { Suspense, type ReactNode } from 'react';
import { SiteAlertProvider } from '@/components/site-alert';
import { ReferralTracking } from '@/components/referral-tracking';
import { SiteSettingsEffects } from '@/lib/site-settings';
import './globals.css';

export const metadata: Metadata = {
	title: 'MMU Minecraft Society',
	description: 'MMU Minecraft Society',
};

export default function RootLayout({ children }: { children: ReactNode }) {
	return (
		<html lang="en" suppressHydrationWarning>
			<head>
				<script
					dangerouslySetInnerHTML={{
						__html: `try { if (JSON.parse(localStorage.getItem('mmu-site-settings-v1') || '{}').dyslexiaMode) document.documentElement.setAttribute('data-dyslexia-mode', ''); } catch {}`,
					}}
				/>
			</head>
			<body>
				<SiteSettingsEffects />
				<Suspense fallback={null}>
					<ReferralTracking />
				</Suspense>
				<SiteAlertProvider>{children}</SiteAlertProvider>
			</body>
		</html>
	);
}
