import type { Metadata } from 'next';
import { Suspense, type ReactNode } from 'react';
import { SiteAlertProvider } from '@/components/site-alert';
import { ReferralTracking } from '@/components/referral-tracking';
import './globals.css';

export const metadata: Metadata = {
	title: 'MMU Minecraft Society',
	description: 'MMU Minecraft Society',
};

export default function RootLayout({ children }: { children: ReactNode }) {
	return (
		<html lang="en">
			<body>
				<Suspense fallback={null}>
					<ReferralTracking />
				</Suspense>
				<SiteAlertProvider>{children}</SiteAlertProvider>
			</body>
		</html>
	);
}
